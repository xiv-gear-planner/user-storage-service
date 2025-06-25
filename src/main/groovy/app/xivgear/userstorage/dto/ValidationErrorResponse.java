package app.xivgear.userstorage.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class ValidationErrorResponse {
	public List<ValidationErrorSingle> validationErrors;
}
