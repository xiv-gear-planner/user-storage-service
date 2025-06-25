package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class SheetSummary {
	public @NotNull
	@Size(max = 64) String job;
	public @NotNull
	@Size(max = 128) String name;
	public boolean multiJob;
	public int level;
	public @Nullable
	@Min(0) Integer isync;
}
