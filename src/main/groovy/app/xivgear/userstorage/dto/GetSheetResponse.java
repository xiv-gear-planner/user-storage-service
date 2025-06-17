package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class GetSheetResponse {
	public SheetMetadata metadata;
	public Map<String, ?> sheetData;
}
