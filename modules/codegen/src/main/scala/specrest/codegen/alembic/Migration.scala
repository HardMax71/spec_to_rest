package specrest.codegen.alembic

import specrest.convention.ColumnSpec
import specrest.convention.DatabaseSchema
import specrest.convention.TableSpec

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
    unique: Boolean
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
    needsPostgresDialect: Boolean
)

final case class BuildMigrationOptions(
    revision: Option[String] = None,
    createdDate: Option[String] = None
)

object Migration:

  private val DirectSaTypes: Map[String, String] = Map(
    "TEXT"             -> "sa.Text()",
    "BIGSERIAL"        -> "sa.BigInteger()",
    "BIGINT"           -> "sa.BigInteger()",
    "INTEGER"          -> "sa.Integer()",
    "SERIAL"           -> "sa.Integer()",
    "BOOLEAN"          -> "sa.Boolean()",
    "DOUBLE PRECISION" -> "sa.Float()",
    "DATE"             -> "sa.Date()",
    "TIMESTAMPTZ"      -> "sa.DateTime(timezone=True)",
    "UUID"             -> "sa.Uuid()",
    "BYTEA"            -> "sa.LargeBinary()",
    "JSONB"            -> "postgresql.JSONB()"
  )

  private val NumericWithScalePattern = """^NUMERIC\((\d+)\s*,\s*(\d+)\)$""".r
  private val NumericNoScalePattern   = """^NUMERIC\((\d+)\)$""".r
  private val VarcharPattern          = """^VARCHAR\((\d+)\)$""".r

  def buildAlembicMigration(
      schema: DatabaseSchema,
      opts: BuildMigrationOptions = BuildMigrationOptions()
  ): AlembicMigration =
    val sorted               = topoSortTables(schema.tables)
    val tables               = sorted.map(buildAlembicTable)
    val needsPostgresDialect = tables.exists(_.columns.exists(_.saType.startsWith("postgresql.")))
    AlembicMigration(
      revision = opts.revision.getOrElse("001"),
      createdDate = opts.createdDate.getOrElse(java.time.LocalDate.now.toString),
      tables = tables,
      tablesReversed = tables.reverse,
      needsPostgresDialect = needsPostgresDialect
    )

  private enum TopoColor:
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
            byName.get(fk.refTable).filter(_ != t).foreach(visit(_, stack))
          val _ = stack.remove(stack.length - 1)
          color(t.name) = TopoColor.Black
          result += t

    for t <- tables do visit(t, mutable.ArrayBuffer.empty)
    result.toList

  private def buildAlembicTable(t: TableSpec): AlembicTable =
    val columns = t.columns.map(buildColumn(_, t))
    val foreignKeys = t.foreignKeys.map: fk =>
      AlembicForeignKey(
        name = s"fk_${t.name}_${fk.column}",
        column = fk.column,
        refTable = fk.refTable,
        refColumn = fk.refColumn,
        onDelete = fk.onDelete
      )
    val uniqueCheckSqls = t.checks.distinct
    val checks = uniqueCheckSqls.zipWithIndex.map: (sql, i) =>
      AlembicCheck(name = s"ck_${t.name}_$i", sql = sql)
    val indexes = t.indexes.map: ix =>
      AlembicIndex(
        name = ix.name,
        table = t.name,
        columns = ix.columns,
        unique = ix.unique
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

  private def buildColumn(c: ColumnSpec, t: TableSpec): AlembicColumn =
    val isPk     = c.name == t.primaryKey
    val isSerial = c.sqlType == "BIGSERIAL" || c.sqlType == "SERIAL"
    AlembicColumn(
      name = c.name,
      saType = mapSqlTypeToSa(c.sqlType),
      nullable = c.nullable,
      primaryKey = isPk,
      autoincrement = isSerial,
      serverDefault = mapServerDefault(c.defaultValue)
    )

  private def mapSqlTypeToSa(sqlType: String): String =
    DirectSaTypes.get(sqlType) match
      case Some(direct) => direct
      case None =>
        sqlType match
          case NumericWithScalePattern(p, s) => s"sa.Numeric($p, $s)"
          case NumericNoScalePattern(p)      => s"sa.Numeric($p)"
          case VarcharPattern(len)           => s"sa.String(length=$len)"
          case _ =>
            throw new RuntimeException(s"Unsupported SQL type in Alembic migration: $sqlType")

  private def mapServerDefault(value: Option[String]): Option[String] = value match
    case None          => None
    case Some("NOW()") => Some("sa.func.now()")
    case Some(v)       => Some(s"sa.text(${pythonStringLiteral(v)})")

  private def pythonStringLiteral(s: String): String =
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    if !escaped.contains("'") then s"'$escaped'"
    else if !escaped.contains("\"") then s"\"$escaped\""
    else s"\"${escaped.replace("\"", "\\\"")}\""

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
    s"""sa.CheckConstraint(${pythonStringLiteral(c.sql)}, name="${c.name}")"""
