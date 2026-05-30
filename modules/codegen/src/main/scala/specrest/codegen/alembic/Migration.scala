package specrest.codegen.alembic

import specrest.codegen.migration.AlembicSyntax
import specrest.codegen.migration.CanonicalType
import specrest.codegen.migration.Dialect
import specrest.codegen.migration.Postgres
import specrest.codegen.migration.SchemaDiff
import specrest.ir.generated.SpecRestGenerated.*

final case class AlembicColumn(
    name: String,
    saType: String,
    nullable: Boolean,
    primaryKey: Boolean,
    autoincrement: Boolean,
    serverDefault: Option[String]
)

final case class AlembicForeignKey(
    name: String,
    column: String,
    refTable: String,
    refColumn: String,
    onDelete: String
)

final case class AlembicCheck(name: String, sql: String)

final case class AlembicIndex(
    name: String,
    table: String,
    columns: List[String],
    unique: Boolean,
    postgresqlWhereSuffix: String
)

final case class AlembicTrigger(
    name: String,
    upgradeStatements: List[String],
    downgradeStatements: List[String]
)

final case class AlembicTable(
    name: String,
    entityName: String,
    columns: List[AlembicColumn],
    foreignKeys: List[AlembicForeignKey],
    checks: List[AlembicCheck],
    indexes: List[AlembicIndex],
    tableArgs: List[String]
)

final case class AlembicMigration(
    revision: String,
    createdDate: String,
    tables: List[AlembicTable],
    tablesReversed: List[AlembicTable],
    triggers: List[AlembicTrigger],
    triggersReversed: List[AlembicTrigger],
    needsPostgresDialect: Boolean
)

final case class BuildMigrationOptions(
    revision: Option[String] = None,
    createdDate: Option[String] = None
)

object Migration:

  def buildAlembicMigration(
      schema: database_schema,
      opts: BuildMigrationOptions = BuildMigrationOptions(),
      dialect: Dialect = Postgres
  ): AlembicMigration =
    val sorted               = SchemaDiff.topoSort(schemaTables(schema))
    val tables               = sorted.map(buildAlembicTable(_, dialect))
    val triggers             = schemaTriggers(schema).map(buildAlembicTrigger(_, dialect))
    val needsPostgresDialect = tables.exists(_.columns.exists(_.saType.startsWith("postgresql.")))
    AlembicMigration(
      revision = opts.revision.getOrElse("001"),
      createdDate = opts.createdDate.getOrElse(java.time.LocalDate.now.toString),
      tables = tables,
      tablesReversed = tables.reverse,
      triggers = triggers,
      triggersReversed = triggers.reverse,
      needsPostgresDialect = needsPostgresDialect
    )

  private def buildAlembicTrigger(t: trigger_spec, dialect: Dialect): AlembicTrigger =
    val emission = dialect.renderTrigger(t)
    AlembicTrigger(
      name = triggerName(t),
      upgradeStatements = emission.upgrade,
      downgradeStatements = emission.downgrade
    )

  private def buildAlembicTable(t: table_spec, dialect: Dialect): AlembicTable =
    val tname       = tableName(t)
    val columns     = tableColumns(t).map(buildColumn(_, t, dialect))
    val foreignKeys = tableForeignKeys(t).map: fk =>
      AlembicForeignKey(
        name = s"fk_${tname}_${fkColumn(fk)}",
        column = fkColumn(fk),
        refTable = fkRefTable(fk),
        refColumn = fkRefColumn(fk),
        onDelete = fkOnDelete(fk)
      )
    val checks =
      SchemaDiff.namedChecks(t, dialect, SchemaDiff.autoIncrementPk(t)).map: (name, sql) =>
        AlembicCheck(name = name, sql = sql)
    val indexes = tableIndexes(t).map: ix =>
      // Mirror sqlCreateIndex: when a partial index degrades on a dialect without partial-index
      // support, drop UNIQUE too (a full unique index over-enforces). Keeps the Alembic and raw
      // SQL renderers consistent — no cross-renderer schema drift.
      val partialDropped = indexFilterClause(ix).isDefined && !dialect.caps.supportsPartialIndex
      AlembicIndex(
        name = indexName(ix),
        table = tname,
        columns = indexColumns(ix),
        unique = indexUnique(ix) && !partialDropped,
        postgresqlWhereSuffix = dialect.partialIndex(ix).value
      )
    val tableArgs =
      columns.map(renderColumnCall) ++
        foreignKeys.map(renderForeignKeyCall) ++
        checks.map(renderCheckCall)
    AlembicTable(
      name = tname,
      entityName = tableEntityName(t),
      columns = columns,
      foreignKeys = foreignKeys,
      checks = checks,
      indexes = indexes,
      tableArgs = tableArgs
    )

  private def buildColumn(c: column_spec, t: table_spec, dialect: Dialect): AlembicColumn =
    val sqlT     = columnSqlType(c)
    val isPk     = columnName(c) == tablePrimaryKey(t)
    val isSerial = CanonicalType.isAutoIncrementType(sqlT)
    AlembicColumn(
      name = columnName(c),
      saType = AlembicSyntax.mapSqlTypeToSa(sqlT, dialect),
      nullable = columnNullable(c),
      primaryKey = isPk,
      autoincrement = isSerial,
      serverDefault =
        AlembicSyntax.mapServerDefault(columnDefaultValue(c).map(dialect.alembicServerDefault))
    )

  private def renderColumnCall(c: AlembicColumn): String =
    val parts = List.newBuilder[String]
    parts += s""""${c.name}""""
    parts += c.saType
    if c.primaryKey then parts += "primary_key=True"
    // An explicit integer PK is application-supplied (spec `next_id`), not DB-generated. SQLAlchemy
    // defaults a lone integer PK to `autoincrement="auto"` (-> SERIAL/AUTO_INCREMENT), so the
    // non-serial PK must pin `autoincrement=False` to stay consistent with the raw-SQL/Prisma
    // renderers and the spec. Synthesized serial PKs keep `autoincrement=True`.
    if c.primaryKey then parts += s"autoincrement=${if c.autoincrement then "True" else "False"}"
    else if c.autoincrement then parts += "autoincrement=True"
    c.serverDefault.foreach(d => parts += s"server_default=$d")
    parts += s"nullable=${if c.nullable then "True" else "False"}"
    s"sa.Column(${parts.result().mkString(", ")})"

  private def renderForeignKeyCall(fk: AlembicForeignKey): String =
    s"""sa.ForeignKeyConstraint(["${fk.column}"], ["${fk.refTable}.${fk.refColumn}"], ondelete="${fk
        .onDelete}", name="${fk.name}")"""

  private def renderCheckCall(c: AlembicCheck): String =
    s"""sa.CheckConstraint(${AlembicSyntax.pythonStringLiteral(c.sql)}, name="${c.name}")"""
