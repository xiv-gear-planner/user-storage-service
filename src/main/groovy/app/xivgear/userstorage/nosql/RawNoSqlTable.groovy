package app.xivgear.userstorage.nosql

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Nullable
import jakarta.annotation.PostConstruct
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.TableNotFoundException
import oracle.nosql.driver.ops.*
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.MapValue

@CompileStatic
@Slf4j
abstract class RawNoSqlTable<ColType extends Enum<ColType>, PkType> {

	protected final ColType primaryKeyCol
	protected final String primaryKeyColName
	protected final String tableName
	protected final NoSQLHandle handle
	private boolean initialized

	protected abstract FieldValue pkToFieldValue(PkType pk)

	protected RawNoSqlTable(String tableName, ColType primaryKeyCol, NoSQLHandle handle) {
		this.primaryKeyCol = primaryKeyCol
		this.primaryKeyColName = primaryKeyCol.name()
		this.tableName = tableName
		this.handle = handle
	}

	/**
	 * Get a single item by primary key. If the item does not exist, {@code GetResult.value} will return null.
	 *
	 * @param primaryKey
	 * @return
	 */
	GetResult get(PkType primaryKey) {
		return get(pkToFieldValue(primaryKey))
	}

	/**
	 * Get a single item by primary key. If the item does not exist, {@code GetResult.value} will return null.
	 *
	 * @param primaryKey
	 * @return
	 */
	GetResult get(FieldValue primaryKey) {
		String col = primaryKeyColName
		var req = new GetRequest().tap {
			tableName = this.tableName
			key = new MapValue().tap {
				put col, primaryKey
			}
		}
		return handle.get(req)
	}

	/**
	 * Query a single item by a map of columns to values. Returns null if nothing found.
	 *
	 * @param where Query conditions
	 * @return
	 */
	@Nullable
	MapValue queryOne(Map<ColType, ? extends FieldValue> where) {
		var wherePart = where.entrySet().collect {
			return "${it.key} = \$whr_${it.key}"
		}.join(" AND ")
		// Why does this require variables to be explicitly declared, but update doesn't?
		var declPart = where.entrySet().collect {
			return "declare \$whr_${it.key} ${it.value.type.name().toLowerCase()}; "
		}.join('')
		PrepareRequest pr = new PrepareRequest().tap {
			tableName = this.tableName
			statement = "${declPart} SELECT * FROM ${this.tableName} WHERE ${wherePart}"
		}
		PrepareResult prepare = handle.prepare pr
		where.entrySet().forEach {
			prepare.preparedStatement.setVariable "\$whr_${it.key}", it.value
		}
		try (QueryRequest qr = new QueryRequest()) {
			qr.preparedStatement = prepare
			QueryIterableResult iterable = handle.queryIterable qr
			for (MapValue row : iterable) {
				return row
			}
		}
		return null
	}

	/**
	 * Update an item.
	 * @param where Conditions for finding an item. Should contain the primary key.
	 * @param updates Fields to change.
	 * @return
	 */
	private QueryResult update(Map<ColType, ? extends FieldValue> where, Map<ColType, ? extends FieldValue> updates) {
		// Idea: this should automatically do queryIterable if the `where` does not include the primary key
		var updatePart = updates.entrySet().collect {
			return "${it.key} = \$upd_${it.key}"
		}.join(", ")
		var wherePart = where.entrySet().collect {
			return "${it.key} = \$whr_${it.key}"
		}.join(" AND ")
		PrepareRequest pr = new PrepareRequest().tap {
			tableName = this.tableName
			statement = "UPDATE ${this.tableName} SET ${updatePart} WHERE ${wherePart}"
		}
		PrepareResult prepare = handle.prepare pr
		updates.entrySet().forEach {
			prepare.preparedStatement.setVariable "\$upd_${it.key}", it.value
		}
		where.entrySet().forEach {
			prepare.preparedStatement.setVariable "\$whr_${it.key}", it.value
		}
		try (QueryRequest qr = new QueryRequest()) {
			qr.preparedStatement = prepare
			QueryResult result = handle.query qr
			return result
		}
	}

	/**
	 * Update an item by primary key.
	 *
	 * @param pk The primary key.
	 * @param updates Fields to change.
	 * @return
	 */
	QueryResult updateByPk(PkType pk, Map<ColType, ? extends FieldValue> updates) {
		return update([(primaryKeyCol): pkToFieldValue(pk)], updates)
	}

	/**
	 * Put a single item via primary key. Overwrites if it already exists.
	 *
	 * @param pk The primary key
	 * @param values The values to put, other than the PK
	 * @return
	 */
	PutResult putByPK(PkType pk, Map<ColType, ? extends FieldValue> values) {
		Map<ColType, FieldValue> combined = new HashMap()
		combined[primaryKeyCol] = pkToFieldValue pk
		combined.putAll values
		return put(combined)
	}

	/**
	 * Puts a single item without necessarily specifying primary key. Useful for when the primary
	 * key is auto-generated.
	 *
	 * @param values The values. Need not contain the primary key.
	 * @return
	 */
	PutResult put(Map<ColType, ? extends FieldValue> values) {
		PutRequest pr = new PutRequest().tap {
			tableName = this.tableName
			value = new MapValue().tap {
				values.each {
					put it.key.name(), it.value
				}
			}
		}
		return handle.put(pr)
	}

	/**
	 * Delete an item by PK
	 *
	 * @param pk
	 * @return
	 */
	DeleteResult deleteByPk(PkType pk) {
		String col = primaryKeyColName
		DeleteRequest dr = new DeleteRequest().tap {
			tableName = this.tableName
			key = new MapValue().tap {
				put col, pkToFieldValue(pk)
			}
		}
		return handle.delete(dr)
	}

	/**
	 * Delete many items. This supports cross-shard deletion.
	 *
	 * @param where Conditions to delete.
	 * @return count of deleted rows
	 */
	int deleteMany(Map<ColType, ? extends FieldValue> where) {
		var wherePart = where.entrySet().collect {
			return "${it.key} = \$whr_${it.key}"
		}.join(" AND ")
		// Why does this require variables to be explicitly declared, but update doesn't?
		var declPart = where.entrySet().collect {
			return "declare \$whr_${it.key} ${it.value.type.name().toLowerCase()}; "
		}.join('')
		PrepareRequest pr = new PrepareRequest().tap {
			tableName = this.tableName
			statement = "${declPart} DELETE FROM ${this.tableName} WHERE ${wherePart} RETURNING 1"
		}
		PrepareResult prepare = handle.prepare pr
		where.entrySet().forEach {
			prepare.preparedStatement.setVariable "\$whr_${it.key}", it.value
		}
		int deletedCount = 0
		try (QueryRequest qr = new QueryRequest()) {
			qr.preparedStatement = prepare
			QueryIterableResult iterable = handle.queryIterable qr
			for (MapValue result : iterable) {
				deletedCount++
			}
		}
		return deletedCount
	}

//	MapValue deleteMany(Map<ColType, ? extends FieldValue> where) {
//		var wherePart = where.entrySet().collect {
//			return "${it.key} = \$whr_${it.key}"
//		}.join(" AND ")
//		// Why does this require variables to be explicitly declared, but update doesn't?
//		var declPart = where.entrySet().collect {
//			return "declare \$whr_${it.key} ${it.value.type.name().toLowerCase()}; "
//		}.join('')
//		PrepareRequest pr = new PrepareRequest().tap {
//			tableName = this.tableName
////			statement = "${declPart} SELECT * FROM ${this.tableName} WHERE ${wherePart}"
//			statement = "DELETE FROM ${this.tableName} WHERE ${wherePart}"
//		}
//		PrepareResult prepare = handle.prepare pr
//		where.entrySet().forEach {
//			prepare.preparedStatement.setVariable "\$whr_${it.key}", it.value
//		}
//		try (QueryRequest qr = new QueryRequest()) {
//			qr.preparedStatement = prepare
//			QueryIterableResult iterable = handle.queryIterable qr
//			for (MapValue row : iterable) {
//				return row
//			}
//		}
//		return null
//	}

	@PostConstruct
	void init(@Property(name = 'oracle-nosql.createTables', defaultValue = 'false') boolean create) {
		if (create) {
			log.info "init table ${tableName}"
			initTable()
		}
		initialized = true
	}

	boolean isInitialized() {
		return this.initialized
	}

	protected void initTable() {
		// Check if table exists first
		try {
			handle.getTable(new GetTableRequest().tap {
				tableName = this.tableName
			})
			log.info "Table ${tableName} already exists"
		}
		catch (TableNotFoundException ignored) {
			log.info "Table not found, will create: ${tableDdl}"
			var tr = new TableRequest().tap {
				statement = tableDdl
				tableLimits = Objects.requireNonNull this.tableLimits
			}
			TableResult result = handle.tableRequest tr
			result.waitForCompletion handle, 30_000, 500
		}

		Set<String> existing = handle.getIndexes(new GetIndexesRequest().tap {
			tableName = this.tableName
		}).indexes.collect { it.indexName }.toSet()

		tableIndicesDdl.each { entry ->
			String indexName = entry.key
			if (existing.contains(indexName)) {
				log.info "Index ${indexName} already exists"
				return
			}
			log.info "Index create: ${indexName} -> ${entry.value}"
			var itr = new TableRequest().tap {
				statement = "CREATE INDEX IF NOT EXISTS ${indexName} ON ${this.tableName}(${entry.value})"
			}
			TableResult result = handle.tableRequest itr
			result.waitForCompletion handle, 30_000, 500
		}
	}

	protected abstract TableLimits getTableLimits()

	protected abstract String getTableDdl()

	protected abstract Map<String, ColType> getTableIndicesDdl()

}
