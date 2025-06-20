package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class PutSheetRequest {
	public int lastSyncedVersion;
	public int newSheetVersion;
	public @Size(max = 128) String sheetName;
	public @Nullable Double sortOrder;
	public Map<String, ?> sheetData;
}
