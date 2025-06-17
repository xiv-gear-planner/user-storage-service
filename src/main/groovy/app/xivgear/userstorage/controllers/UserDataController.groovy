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
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Singleton
import jakarta.validation.Valid
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
@Secured(SecurityRule.IS_AUTHENTICATED)
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
		// TODO: need patch operation if we want to support partial updates in the future
		// TODO: timestamp check
		var qr = users.putByPK uid, [
				(UserDataCol.preferences): dm.mapPreferences(prefs.preferences)
		]
		log.info "qr: ${qr.properties}"
	}

	@Get("/sheets")
	GetSheetsResponse getSheetsList(Authentication auth) {
		int uid = Integer.parseInt auth.name
		List<MapValue> sheetValues = sheets.getAllForParent(uid)

		GetSheetsResponse tap = new GetSheetsResponse().tap { resp ->
			resp.sheets = []
			resp.deletedSheets = []
			sheetValues.forEach { sheet ->
				if (sheet.getBoolean(SheetCol.sheet_is_deleted.name())) {
					resp.deletedSheets << sheet.getString(SheetCol.sheet_save_key.name())
				}
				else {
					resp.sheets << dm.toSheetMetadata(sheet)
				}
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
			sheetData = dm.fieldValueToMap(sheet.get(SheetCol.sheet_data.name()).asMap())
		})
	}

	@Put("/sheets/{sheetId}")
	HttpResponse<PutSheetResponse> putSheet(Authentication auth, String sheetId, @Body @Valid PutSheetRequest reqBody) {
		int uid = Integer.parseInt auth.name
		GetResult getResult = sheets.get uid, sheetId
		MapValue existingSheet = getResult.value
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
		}
		// TODO: check for a concurrent write
		PutResult pr = sheets.putByPK(uid, sheetId, [
				(SheetCol.sheet_version)   : new IntegerValue(reqBody.newSheetVersion),
				(SheetCol.sheet_name)      : new StringValue(reqBody.sheetName),
				(SheetCol.sheet_data)      : dm.mapToFieldValue(reqBody.sheetData),
				(SheetCol.sheet_sort_order): reqBody.sortOrder == null ? NullValue.instance : new DoubleValue(reqBody.sortOrder)
		])
		if (pr.version == null) {
			// ???
		}
		return HttpResponse.ok(new PutSheetResponse().tap {
			success = true
			conflict = false
		})
	}

	@Delete("/sheets/{sheetId}")
	DeleteSheetResponse deleteSheet(Authentication auth, String sheetId) {
		int uid = Integer.parseInt auth.name
		var dr = sheets.deleteByPk uid, sheetId
		return new DeleteSheetResponse().tap {
			deleted = dr.success
		}
	}


}
