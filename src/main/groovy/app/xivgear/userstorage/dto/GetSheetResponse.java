package app.xivgear.userstorage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetSheetResponse {
	public SheetMetadata metadata;
	public Map<String, ?> sheetData;
}
