package app.xivgear.userstorage.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.ops.TableLimits
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.IntegerValue

import static app.xivgear.userstorage.nosql.UserDataCol.*


@Context
@Singleton
@CompileStatic
class UserDataTable extends RawNoSqlTable<UserDataCol, Integer> {
	UserDataTable(
			@Property(name = 'oracle-nosql.tables.userData.name') String tableName,
			NoSQLHandle handle
	) {
		super(tableName, user_id, handle)
	}

	@Override
	protected FieldValue pkToFieldValue(Integer pk) {
		return new IntegerValue(pk)
	}

	@Override
	protected TableLimits getTableLimits() {
		return new TableLimits(25, 5, 1)
	}

	@Override
	protected String getTableDdl() {
		return """CREATE TABLE IF NOT EXISTS ${tableName} (
${user_id} INTEGER,
${next_set_id} INTEGER NOT NULL DEFAULT 1,
${preferences} JSON,
PRIMARY KEY(SHARD(${user_id}))
)"""
	}

	@Override
	protected Map<String, UserDataCol> getTableIndicesDdl() {
		return [:]
	}
}
