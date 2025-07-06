package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class PutSheetRequest {
	public int lastSyncedVersion;
	public int newSheetVersion;
	public @Nullable Integer newSheetVersionKey;
	public @Nullable Double sortOrder;
	public @NotNull Map<String, ?> sheetData;
	public @NotNull SheetSummary sheetSummary;
}
