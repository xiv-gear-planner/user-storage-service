package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Size;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class UserPreferences {
	public boolean lightMode;
	public @Nullable @Size(max = 64) String languageOverride;
}
