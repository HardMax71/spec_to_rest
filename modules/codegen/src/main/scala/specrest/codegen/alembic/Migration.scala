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
    val sorted               = SchemaDiff.topoSort(schema_tables(schema))
    val tables               = sorted.map(buildAlembicTable(_, dialect))
    val triggers             = schema_triggers(schema).map(buildAlembicTrigger(_, dialect))
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
      name = trigger_name(t),
      upgradeStatements = emission.upgrade,
      downgradeStatements = emission.downgrade
    )

  private def buildAlembicTable(t: table_spec, dialect: Dialect): AlembicTable =
    val tname   = table_name(t)
    val columns = table_columns(t).map(buildColumn(_, t, dialect))
    val foreignKeys = table_foreign_keys(t).map: fk =>
      AlembicForeignKey(
        name = s"fk_${tname}_${fk_column(fk)}",
        column = fk_column(fk),
        refTable = fk_ref_table(fk),
        refColumn = fk_ref_column(fk),
        onDelete = fk_on_delete(fk)
      )
    val checks =
      SchemaDiff.namedChecks(t, dialect, SchemaDiff.autoIncrementPk(t)).map: (name, sql) =>
        AlembicCheck(name = name, sql = sql)
    val indexes = table_indexes(t).map: ix =>
      // Mirror sqlCreateIndex: when a partial index degrades on a dialect without partial-index
      // support, drop UNIQUE too (a full unique index over-enforces). Keeps the Alembic and raw
      // SQL renderers consistent — no cross-renderer schema drift.
      val partialDropped = index_filter_clause(ix).isDefined && !dialect.caps.supportsPartialIndex
      AlembicIndex(
        name = index_name(ix),
        table = tname,
        columns = index_columns(ix),
        unique = index_unique(ix) && !partialDropped,
        postgresqlWhereSuffix = dialect.partialIndex(ix).value
      )
    val tableArgs =
      columns.map(renderColumnCall) ++
        foreignKeys.map(renderForeignKeyCall) ++
        checks.map(renderCheckCall)
    AlembicTable(
      name = tname,
      entityName = table_entity_name(t),
      columns = columns,
      foreignKeys = foreignKeys,
      checks = checks,
      indexes = indexes,
      tableArgs = tableArgs
    )

  private def buildColumn(c: column_spec, t: table_spec, dialect: Dialect): AlembicColumn =
    val sqlT     = column_sql_type(c)
    val isPk     = column_name(c) == table_primary_key(t)
    val isSerial = CanonicalType.isAutoIncrementType(sqlT)
    AlembicColumn(
      name = column_name(c),
      saType = AlembicSyntax.mapSqlTypeToSa(sqlT, dialect),
      nullable = column_nullable(c),
      primaryKey = isPk,
      autoincrement = isSerial,
      serverDefault =
        AlembicSyntax.mapServerDefault(column_default_value(c).map(dialect.alembicServerDefault))
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
