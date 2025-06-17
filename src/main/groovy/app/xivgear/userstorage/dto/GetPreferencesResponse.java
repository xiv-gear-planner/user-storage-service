package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
public class GetPreferencesResponse {
	public boolean found;
	@Nullable
	public UserPreferences preferences;

	public int nextSetId;

	public static final GetPreferencesResponse NO_PREFS_INST = new GetPreferencesResponse();
}
