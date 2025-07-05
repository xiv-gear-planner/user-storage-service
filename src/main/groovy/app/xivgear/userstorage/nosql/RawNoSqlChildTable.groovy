package app.xivgear.userstorage.nosql

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Nullable
import jakarta.annotation.PostConstruct
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.TableNotFoundException
import oracle.nosql.driver.Version
import oracle.nosql.driver.ops.*
import oracle.nosql.driver.values.FieldValue
import oracle.nosql.driver.values.MapValue

@CompileStatic
@Slf4j
abstract class RawNoSqlChildTable<ColType extends Enum<ColType>, PkType, ParentColType extends Enum<ParentColType>, ParentPkType> {

	protected final ColType primaryKeyCol
	protected final String primaryKeyColName
	protected final String tblName
	protected final NoSQLHandle handle
	protected final RawNoSqlTable<ParentColType, ParentPkType> parTbl
	private boolean initialized

	protected abstract FieldValue pkToFieldValue(PkType pk)

	protected RawNoSqlChildTable(String tblName, ColType primaryKeyCol, NoSQLHandle handle, RawNoSqlTable<ParentColType, ParentPkType> parTbl) {
		this.primaryKeyCol = primaryKeyCol
		this.primaryKeyColName = primaryKeyCol.name()
		this.tblName = tblName
		this.handle = handle
		this.parTbl = parTbl
	}

	/**
	 * Get a single item by primary key. If the item does not exist, {@code GetResult.value} will return null.
	 *
	 * @param primaryKey
	 * @return
	 */
	GetResult get(ParentPkType parentPk, PkType primaryKey) {
		return get(parTbl.pkToFieldValue(parentPk), pkToFieldValue(primaryKey))
	}

	/**
	 * Get a single item by primary key. If the item does not exist, {@code GetResult.value} will return null.
	 *
	 * @param primaryKey
	 * @return
	 */
	GetResult get(FieldValue parentPrimaryKey, FieldValue primaryKey) {
		String col = primaryKeyColName
		var req = new GetRequest().tap {
			tableName = this.combinedTableName
			key = new MapValue().tap {
				put this.parTbl.primaryKeyColName, parentPrimaryKey
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
	MapValue queryOne(Map<? extends Enum, ? extends FieldValue> where) {
		var wherePart = where.entrySet().collect {
			return "${it.key} = \$whr_${it.key}"
		}.join(" AND ")
		// Why does this require variables to be explicitly declared, but update doesn't?
		var declPart = where.entrySet().collect {
			return "declare \$whr_${it.key} ${it.value.type.name().toLowerCase()}; "
		}.join('')
		PrepareRequest pr = new PrepareRequest().tap {
			tableName = this.combinedTableName
			statement = "${declPart} SELECT * FROM ${this.combinedTableName} WHERE ${wherePart}"
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

	List<MapValue> getAllForParent(ParentPkType parentPk, @Nullable List<? extends Enum> includeCols = null) {
		return queryMany([
				(this.parTbl.primaryKeyCol): this.parTbl.pkToFieldValue(parentPk)
		], includeCols)
	}

	List<MapValue> queryMany(Map<? extends Enum, ? extends FieldValue> where, @Nullable List<? extends Enum> includeCols = null) {
		// Idea: this should automatically do queryIterable if the `where` does not include the primary key
		var wherePart = where.entrySet().collect {
			return "${it.key} = \$whr_${it.key}"
		}.join(" AND ")
		var declPart = where.entrySet().collect {
			return "declare \$whr_${it.key} ${it.value.type.name().toLowerCase()}; "
		}.join('')
		String colsPart = includeCols == null ? '*' : includeCols.collect {
			return it.name()
		}.join(',')
		PrepareRequest pr = new PrepareRequest().tap {
			tableName = this.combinedTableName
			statement = "${declPart} SELECT ${colsPart} FROM ${this.combinedTableName} WHERE ${wherePart}"
		}
		PrepareResult prepare = handle.prepare pr
		where.entrySet().forEach {
			prepare.preparedStatement.setVariable "\$whr_${it.key}", it.value
		}
		List<MapValue> out = []
		try (QueryRequest qr = new QueryRequest()) {
			qr.preparedStatement = prepare
			QueryIterableResult iterable = handle.queryIterable qr
			for (MapValue result : iterable) {
				out << result
			}
		}
		return out
	}

	/**
	 * Update an item.
	 * @param where Conditions for finding an item. Should contain the primary key.
	 * @param updates Fields to change.
	 * @return
	 */
	private QueryResult update(Map<? extends Enum, ? extends FieldValue> where, Map<ColType, ? extends FieldValue> updates) {
		// Idea: this should automatically do queryIterable if the `where` does not include the primary key
		var updatePart = updates.entrySet().collect {
			return "${it.key} = \$upd_${it.key}"
		}.join(", ")
		var wherePart = where.entrySet().collect {
			return "${it.key} = \$whr_${it.key}"
		}.join(" AND ")
		PrepareRequest pr = new PrepareRequest().tap {
			tableName = this.combinedTableName
			statement = "UPDATE ${this.combinedTableName} SET ${updatePart} WHERE ${wherePart}"
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
	QueryResult updateByPk(ParentPkType parentPk, PkType pk, Map<ColType, ? extends FieldValue> updates) {
		return update([
				(primaryKeyCol)       : pkToFieldValue(pk),
				(parTbl.primaryKeyCol): parTbl.pkToFieldValue(parentPk),
		], updates)
	}

	/**
	 * Put a single item via primary key. Overwrites if it already exists.
	 *
	 * @param pk The primary key
	 * @param values The values to put, other than the PK
	 * @return
	 */
	PutResult putByPK(ParentPkType parentPk, PkType pk, Map<ColType, ? extends FieldValue> values, Version version = null) {
		Map<Enum, FieldValue> combined = new HashMap()
		combined[primaryKeyCol] = pkToFieldValue pk
		combined.putAll values
		return put(parentPk, combined)
	}

	/**
	 * Puts a single item without necessarily specifying primary key. Useful for when the primary
	 * key is auto-generated.
	 *
	 * @param values The values. Need not contain the primary key.
	 * @return
	 */
	PutResult put(ParentPkType parentPk, Map<ColType, ? extends FieldValue> values, Version version = null) {
		PutRequest pr = new PutRequest().tap {
			if (version != null) {
				matchVersion = version
			}
			tableName = this.combinedTableName
			value = new MapValue().tap {
				values.each {
					put it.key.name(), it.value
				}
				put this.parTbl.primaryKeyColName, this.parTbl.pkToFieldValue(parentPk)
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
	DeleteResult deleteByPk(ParentPkType parentPk, PkType pk) {
		String col = primaryKeyColName
		DeleteRequest dr = new DeleteRequest().tap {
			tableName = this.combinedTableName
			key = new MapValue().tap {
				put this.parTbl.primaryKeyColName, this.parTbl.pkToFieldValue(parentPk)
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
	int deleteMany(Map<Enum, ? extends FieldValue> where) {
		var wherePart = where.entrySet().collect {
			return "${it.key} = \$whr_${it.key}"
		}.join(" AND ")
		// Why does this require variables to be explicitly declared, but update doesn't?
		var declPart = where.entrySet().collect {
			return "declare \$whr_${it.key} ${it.value.type.name().toLowerCase()}; "
		}.join('')
		PrepareRequest pr = new PrepareRequest().tap {
			tableName = this.combinedTableName
			statement = "${declPart} DELETE FROM ${this.combinedTableName} WHERE ${wherePart} RETURNING 1"
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
			log.info "init table ${combinedTableName}"
			initTable()
		}
		initialized = true
	}

	boolean isInitialized() {
		return this.initialized
	}

	String getCombinedTableName() {
		"${parTbl.tableName}.${tblName}"
	}

	protected void initTable() {
		// Check if table exists first

		String name = combinedTableName
		try {
			handle.getTable(new GetTableRequest().tap {
				tableName = name
			})
			log.info "Table ${name} already exists"
		}
		catch (TableNotFoundException ignored) {
			log.info "Table not found, will create: ${name}"
			var tr = new TableRequest().tap {
				statement = tableDdl
			}
			TableResult result = handle.tableRequest tr
			result.waitForCompletion handle, 30_000, 500
		}

		Set<String> existing = handle.getIndexes(new GetIndexesRequest().tap {
			tableName = name
		}).indexes.collect { it.indexName }.toSet()

		tableIndicesDdl.each { entry ->
			String indexName = entry.key
			if (existing.contains(indexName)) {
				log.info "Index ${indexName} already exists"
				return
			}
			log.info "Index create: ${indexName} -> ${entry.value}"
			var itr = new TableRequest().tap {
				statement = "CREATE INDEX IF NOT EXISTS ${indexName} ON ${name}(${entry.value})"
			}
			TableResult result = handle.tableRequest itr
			result.waitForCompletion handle, 30_000, 500
		}
	}

	protected abstract String getTableDdl()

	protected abstract Map<String, ColType> getTableIndicesDdl()
}
