package app.xivgear.userstorage.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.StringValue

import static SheetCol.*

@Context
@Singleton
@CompileStatic
class SheetsTable extends RawNoSqlChildTable<SheetCol, String, UserDataCol, Integer> {
	SheetsTable(@Property(name = 'oracle-nosql.tables.sheets.name') String tableName, NoSQLHandle handle, UserDataTable users) {
		super(tableName, sheet_save_key, handle, users)
	}

	@Override
	protected FieldValue pkToFieldValue(String pk) {
		return new StringValue(pk)
	}

	@Override
	protected String getTableDdl() {
		return """CREATE TABLE IF NOT EXISTS ${combinedTableName} (
${sheet_save_key} STRING,
${sheet_version} INTEGER,
${sheet_data_compressed} BINARY,
${sheet_is_deleted} BOOLEAN NOT NULL DEFAULT false,
${sheet_sort_order} DOUBLE,
${sheet_summary} JSON,
PRIMARY KEY(${sheet_save_key})
)"""
	}

	@Override
	protected Map<String, SheetCol> getTableIndicesDdl() {
		return [:]
	}
}
