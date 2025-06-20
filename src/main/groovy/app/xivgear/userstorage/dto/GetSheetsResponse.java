package app.xivgear.userstorage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetSheetsResponse {

	public List<SheetMetadata> sheets;
	public List<String> deletedSheets;
}
