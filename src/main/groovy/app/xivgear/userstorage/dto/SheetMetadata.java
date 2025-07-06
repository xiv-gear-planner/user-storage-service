package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class SheetMetadata {

	public String saveKey;
	public int version;
	public int versionKey;
	public @Nullable Double sortOrder;
	public boolean deleted;
	public SheetSummary summary;

}
