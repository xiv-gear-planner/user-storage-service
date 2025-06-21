package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class DeleteSheetRequest {
	public int lastSyncedVersion;
	public int newSheetVersion;
}
