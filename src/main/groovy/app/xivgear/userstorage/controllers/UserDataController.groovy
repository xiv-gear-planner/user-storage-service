package app.xivgear.userstorage.controllers

import app.xivgear.userstorage.dto.*
import app.xivgear.userstorage.mapping.DataMapper
import app.xivgear.userstorage.nosql.SheetCol
import app.xivgear.userstorage.nosql.SheetsTable
import app.xivgear.userstorage.nosql.UserDataCol
import app.xivgear.userstorage.nosql.UserDataTable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Singleton
import jakarta.validation.Valid
import oracle.nosql.driver.Version
import oracle.nosql.driver.ops.GetResult
import oracle.nosql.driver.ops.PutResult
import oracle.nosql.driver.values.*

@Controller("/userdata")
@Context
@Singleton
@CompileStatic
@ExecuteOn(TaskExecutors.BLOCKING)
@SecurityRequirement(name = 'AntiCsrfHeaderAuth')
@Slf4j
@Secured('verified')
class UserDataController {

	private final UserDataTable users
	private final SheetsTable sheets
	private final DataMapper dm

	UserDataController(UserDataTable users, SheetsTable sheets, DataMapper dm) {
		this.users = users
		this.sheets = sheets
		this.dm = dm
	}

	@Get("/preferences")
	GetPreferencesResponse getPrefs(Authentication auth) {
		int uid = Integer.parseInt auth.name
		GetResult get = users.get uid
		if (get.value == null) {
			// no prefs
			return GetPreferencesResponse.NO_PREFS_INST
		}
		else {
			FieldValue prefs = get.value.get(UserDataCol.preferences.name())
			if (prefs.isNull()) {
				return GetPreferencesResponse.NO_PREFS_INST
			}
			return new GetPreferencesResponse().tap {
				it.found = true
				it.preferences = dm.toPreferences((MapValue) prefs)
				it.nextSetId = get.value.getInt(UserDataCol.next_set_id.name())
			}
		}
	}

	/*
	Set sync strategy:
	Every time the client modifies a set, it increments the local version number.
	Client also tracks which version was last synchronized to the server.
	If client version == client synced version == server version, nothing to do.
	if client version == client synced version < server version, client should download
	If client version > client synced version == server version, client should upload
	If client version > client synced version < server version, there is a conflict and the user must choose
		- the user should have the option to save the local set to a new key

	Note that top-level preferences also need versioning, but this can be done with timestamping
	*/

	@Put("/preferences")
	void putPrefs(Authentication auth, @Body @Valid PutPreferencesRequest prefs) {
		int uid = Integer.parseInt auth.name
		int nextSetId = prefs.nextSetId
		GetResult current = users.get uid
		if (current.value != null) {
			// This technically has race conditions but realistically shouldn't be a problem since the system does
			// not assume that these numbers are unique
			int existingNext = current.value.getInt(UserDataCol.next_set_id.name())
			if (existingNext > nextSetId) {
				nextSetId = existingNext
			}
		}
		users.putByPK uid, [
				(UserDataCol.preferences): dm.mapPreferences(prefs.preferences),
				(UserDataCol.next_set_id): new IntegerValue(nextSetId)
		]
	}

	@Get("/sheets")
	GetSheetsResponse getSheetsList(Authentication auth) {
		int uid = Integer.parseInt auth.name
		// TODO: this does not need to query the 'data' column
		// Fortunately, RUs are much cheaper than WUs, so for now we can just crank up the RUs on the DB
		List<MapValue> sheetValues = sheets.getAllForParent(uid)
		GetSheetsResponse tap = new GetSheetsResponse().tap { resp ->
			resp.sheets = sheetValues.collect {
				return dm.toSheetMetadata(it)
			}
		}
		return tap
	}

	@Get("/sheets/{sheetId}")
	HttpResponse<GetSheetResponse> getSheet(Authentication auth, String sheetId) {
		int uid = Integer.parseInt auth.name
		MapValue sheet = sheets.get(uid, sheetId).value

		if (sheet == null) {
			return HttpResponse.notFound()
		}

		return HttpResponse.ok(new GetSheetResponse().tap {
			metadata = dm.toSheetMetadata(sheet)
			sheetData = dm.gzipBinToMap(sheet.getBinary(SheetCol.sheet_data_compressed.name()))
		})
	}

	@Put("/sheets/{sheetId}")
	HttpResponse<PutSheetResponse> putSheet(Authentication auth, String sheetId, @Body @Valid PutSheetRequest reqBody) {
		int uid = Integer.parseInt auth.name
		GetResult getResult = sheets.get uid, sheetId
		MapValue existingSheet = getResult.value
		Version version
		// Since the ID is a randomly generated u32, we most likely do not need a concurrency check
		if (existingSheet != null) {
			int existingVersion = existingSheet.getInt(SheetCol.sheet_version.name())
			if (reqBody.lastSyncedVersion < existingVersion) {
				// CONFLICT - both sides modified
				return HttpResponse.status(HttpStatus.CONFLICT).body(new PutSheetResponse().tap {
					success = false
					conflict = true
				})
			}
			version = getResult.version
		}
		else {
			version = null
		}
		BinaryValue bin = dm.mapToGzipBin(reqBody.sheetData)
		if (bin.value.length > 20_000) {
			return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
		}
		PutResult pr = sheets.putByPK(uid, sheetId, [
				(SheetCol.sheet_version)        : new IntegerValue(reqBody.newSheetVersion),
				(SheetCol.sheet_name)           : new StringValue(reqBody.sheetName),
				(SheetCol.sheet_data_compressed): bin,
				(SheetCol.sheet_sort_order)     : reqBody.sortOrder == null ? NullValue.instance : new DoubleValue(reqBody.sortOrder),
				(SheetCol.sheet_is_deleted)     : BooleanValue.falseInstance()
		], version)
		if (pr.version == null) {
			return HttpResponse.status(HttpStatus.CONFLICT).body(new PutSheetResponse().tap {
				success = false
				conflict = true
			})
		}
		return HttpResponse.ok(new PutSheetResponse().tap {
			success = true
			conflict = false
		})
	}

	@Delete("/sheets/{sheetId}")
	HttpResponse<DeleteSheetResponse> deleteSheet(Authentication auth, String sheetId, @Body DeleteSheetRequest reqBody) {
		int uid = Integer.parseInt auth.name
		GetResult getResult = sheets.get uid, sheetId
		MapValue existingSheet = getResult.value
		Version version
		// Since the ID is a randomly generated u32, we most likely do not need a concurrency check
		if (existingSheet != null) {
			int existingVersion = existingSheet.getInt(SheetCol.sheet_version.name())
			if (reqBody.lastSyncedVersion < existingVersion) {
				// CONFLICT - both sides modified
				return HttpResponse.status(HttpStatus.CONFLICT).body(new DeleteSheetResponse().tap {
					success = false
					conflict = true
				})
			}
			version = getResult.version
		}
		else {
			version = null
		}
		BinaryValue bin = new BinaryValue(new byte[]{})
		// We don't actually delete sheets - we just update the record to indicate that the sheet is deleted

		Map<SheetCol, FieldValue> newValues = [
				(SheetCol.sheet_version)        : new IntegerValue(reqBody.newSheetVersion),
				(SheetCol.sheet_data_compressed): bin,
				(SheetCol.sheet_is_deleted)     : BooleanValue.trueInstance()
		]
		PutResult pr = sheets.putByPK(uid, sheetId, newValues, version)
		if (pr.version == null) {
			return HttpResponse.status(HttpStatus.CONFLICT).body(new DeleteSheetResponse().tap {
				success = false
				conflict = true
			})
		}
		return HttpResponse.ok(new DeleteSheetResponse().tap {
			success = true
			conflict = false
		})
	}


}
