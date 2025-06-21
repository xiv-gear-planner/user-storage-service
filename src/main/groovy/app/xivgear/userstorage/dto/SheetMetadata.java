package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class SheetMetadata {

	public String saveKey;
	public String name;
	public int version;
	public @Nullable Double sortOrder;
	public boolean deleted;

}
