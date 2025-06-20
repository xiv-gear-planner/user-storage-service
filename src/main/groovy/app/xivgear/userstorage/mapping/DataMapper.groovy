package app.xivgear.userstorage.mapping

import app.xivgear.userstorage.compression.Compressor
import app.xivgear.userstorage.dto.SheetMetadata
import app.xivgear.userstorage.dto.UserPreferences
import app.xivgear.userstorage.nosql.SheetCol
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Singleton
import oracle.nosql.driver.values.BinaryValue
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.MapValue

@Context
@Singleton
@CompileStatic
class DataMapper {

	private final ObjectMapper mapper
	private final Compressor compressor

	DataMapper(ObjectMapper mapper, Compressor compressor) {
		this.mapper = mapper
		this.compressor = compressor
	}

	UserPreferences toPreferences(MapValue preferencesObject) {
		return mapper.readValue(preferencesObject.toJson(), UserPreferences)
	}

	FieldValue mapPreferences(UserPreferences preferences) {
		return FieldValue.createFromJson(mapper.writeValueAsString(preferences), null)
	}

	static SheetMetadata toSheetMetadata(MapValue setMeta) {
		return new SheetMetadata().tap {
			name = setMeta.getString(SheetCol.sheet_name.name())
			saveKey = setMeta.getString(SheetCol.sheet_save_key.name())
			FieldValue sortOrderValue = setMeta.get(SheetCol.sheet_sort_order.name())
			if (sortOrderValue != null && !sortOrderValue.isNull()) {
				sortOrder = sortOrderValue.getInt()
			}
			version = setMeta.getInt(SheetCol.sheet_version.name())
		}
	}

	FieldValue mapToFieldValue(Map<String, ?> map) {
		return FieldValue.createFromJson(mapper.writeValueAsString(map), null)
	}

	Map<String, ?> fieldValueToMap(FieldValue value) {
		return mapper.readValue(value.toJson(), Map)
	}

	BinaryValue mapToGzipBin(Map<String, ?> map) {
		return new BinaryValue(compressor.compress(mapper.writeValueAsBytes(map)))
	}

	Map<String, ?> gzipBinToMap(BinaryValue binVal) {
		return gzipBinToMap(binVal.value)
	}

	Map<String, ?> gzipBinToMap(byte[] bin) {
		return mapper.readValue(compressor.decompress(bin), Map)
	}


}
