package specrest.codegen.alembic

import specrest.codegen.migration.AlembicSyntax
import specrest.codegen.migration.Dialect
import specrest.codegen.migration.Postgres
import specrest.codegen.migration.SchemaDiff
import specrest.convention.ColumnSpec
import specrest.convention.DatabaseSchema
import specrest.convention.TableSpec
import specrest.convention.TriggerSpec

import scala.collection.mutable

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
      schema: DatabaseSchema,
      opts: BuildMigrationOptions = BuildMigrationOptions(),
      dialect: Dialect = Postgres
  ): AlembicMigration =
    val sorted               = topoSortTables(schema.tables)
    val tables               = sorted.map(buildAlembicTable(_, dialect))
    val triggers             = schema.triggers.map(buildAlembicTrigger(_, dialect))
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

  private def buildAlembicTrigger(t: TriggerSpec, dialect: Dialect): AlembicTrigger =
    val emission = dialect.renderTrigger(t)
    AlembicTrigger(
      name = t.name,
      upgradeStatements = emission.upgrade,
      downgradeStatements = emission.downgrade
    )

  private enum TopoColor derives CanEqual:
    case White, Gray, Black

  private def topoSortTables(tables: List[TableSpec]): List[TableSpec] =
    val byName = tables.map(t => t.name -> t).toMap
    val color  = mutable.Map.empty[String, TopoColor]
    for t <- tables do color(t.name) = TopoColor.White
    val result = mutable.ArrayBuffer.empty[TableSpec]

    def visit(t: TableSpec, stack: mutable.ArrayBuffer[String]): Unit =
      color.getOrElse(t.name, TopoColor.White) match
        case TopoColor.Black => ()
        case TopoColor.Gray =>
          val cycleStart = stack.indexOf(t.name)
          val cycle      = (stack.slice(cycleStart, stack.length) :+ t.name).mkString(" -> ")
          throw new RuntimeException(s"Foreign-key cycle detected: $cycle")
        case TopoColor.White =>
          color(t.name) = TopoColor.Gray
          stack += t.name
          for fk <- t.foreignKeys do
            byName.get(fk.refTable).filter(_.name != t.name).foreach(visit(_, stack))
          val _ = stack.remove(stack.length - 1)
          color(t.name) = TopoColor.Black
          result += t

    for t <- tables do visit(t, mutable.ArrayBuffer.empty)
    result.toList

  private def buildAlembicTable(t: TableSpec, dialect: Dialect): AlembicTable =
    val columns = t.columns.map(buildColumn(_, t, dialect))
    val foreignKeys = t.foreignKeys.map: fk =>
      AlembicForeignKey(
        name = s"fk_${t.name}_${fk.column}",
        column = fk.column,
        refTable = fk.refTable,
        refColumn = fk.refColumn,
        onDelete = fk.onDelete
      )
    val checks =
      SchemaDiff.namedChecks(t, dialect, SchemaDiff.sqlalchemyAutoIncrementPk(t)).map:
        (name, sql) => AlembicCheck(name = name, sql = sql)
    val indexes = t.indexes.map: ix =>
      AlembicIndex(
        name = ix.name,
        table = t.name,
        columns = ix.columns,
        unique = ix.unique,
        postgresqlWhereSuffix = dialect.partialIndex(ix).value
      )
    val tableArgs =
      columns.map(renderColumnCall) ++
        foreignKeys.map(renderForeignKeyCall) ++
        checks.map(renderCheckCall)
    AlembicTable(
      name = t.name,
      entityName = t.entityName,
      columns = columns,
      foreignKeys = foreignKeys,
      checks = checks,
      indexes = indexes,
      tableArgs = tableArgs
    )

  private def buildColumn(c: ColumnSpec, t: TableSpec, dialect: Dialect): AlembicColumn =
    val isPk     = c.name == t.primaryKey
    val isSerial = c.sqlType == "BIGSERIAL" || c.sqlType == "SERIAL"
    AlembicColumn(
      name = c.name,
      saType = AlembicSyntax.mapSqlTypeToSa(c.sqlType, dialect),
      nullable = c.nullable,
      primaryKey = isPk,
      autoincrement = isSerial,
      serverDefault =
        AlembicSyntax.mapServerDefault(c.defaultValue.map(dialect.alembicServerDefault))
    )

  private def renderColumnCall(c: AlembicColumn): String =
    val parts = List.newBuilder[String]
    parts += s""""${c.name}""""
    parts += c.saType
    if c.primaryKey then parts += "primary_key=True"
    if c.autoincrement then parts += "autoincrement=True"
    c.serverDefault.foreach(d => parts += s"server_default=$d")
    parts += s"nullable=${if c.nullable then "True" else "False"}"
    s"sa.Column(${parts.result().mkString(", ")})"

  private def renderForeignKeyCall(fk: AlembicForeignKey): String =
    s"""sa.ForeignKeyConstraint(["${fk.column}"], ["${fk.refTable}.${fk.refColumn}"], ondelete="${fk
        .onDelete}", name="${fk.name}")"""

  private def renderCheckCall(c: AlembicCheck): String =
    s"""sa.CheckConstraint(${AlembicSyntax.pythonStringLiteral(c.sql)}, name="${c.name}")"""
