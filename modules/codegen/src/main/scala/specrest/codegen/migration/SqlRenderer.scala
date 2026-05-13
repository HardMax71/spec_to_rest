package specrest.codegen.migration

import specrest.codegen.migration.MigrationOp.*
import specrest.codegen.migration.SchemaDiff.fkName
import specrest.codegen.migration.SchemaDiff.namedChecks
import specrest.convention.ColumnSpec
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec

object SqlRenderer:

  def upgrade(ops: List[MigrationOp]): List[String] =
    ops.flatMap(forward)

  def downgrade(ops: List[MigrationOp]): List[String] =
    ops.reverse.flatMap(op => forward(op.inverse))

  private def forward(op: MigrationOp): List[String] = op match
    case CreateTable(t)     => renderCreateTable(t)
    case DropTable(t)       => List(s"DROP TABLE ${t.name};")
    case AddColumn(tbl, c)  => List(s"ALTER TABLE $tbl ADD COLUMN ${renderColumnDef(c)};")
    case DropColumn(tbl, c) => List(s"ALTER TABLE $tbl DROP COLUMN ${c.name};")
    case AlterColumnType(tbl, n, _, newT) =>
      List(s"ALTER TABLE $tbl ALTER COLUMN $n TYPE ${stripAutoIncrement(newT)};")
    case AlterColumnNullable(tbl, n, _, newNullable) =>
      val verb = if newNullable then "DROP NOT NULL" else "SET NOT NULL"
      List(s"ALTER TABLE $tbl ALTER COLUMN $n $verb;")
    case AlterColumnDefault(tbl, n, _, newDefault) =>
      newDefault match
        case Some(d) => List(s"ALTER TABLE $tbl ALTER COLUMN $n SET DEFAULT $d;")
        case None    => List(s"ALTER TABLE $tbl ALTER COLUMN $n DROP DEFAULT;")
    case AddCheck(tbl, name, sql) =>
      List(s"ALTER TABLE $tbl ADD CONSTRAINT $name CHECK ($sql);")
    case DropCheck(tbl, name, _) =>
      List(s"ALTER TABLE $tbl DROP CONSTRAINT $name;")
    case AddForeignKey(tbl, fk) =>
      List(renderAddForeignKey(tbl, fk))
    case DropForeignKey(tbl, fk) =>
      List(s"ALTER TABLE $tbl DROP CONSTRAINT ${fkName(tbl, fk)};")
    case AddIndex(tbl, ix) =>
      List(renderCreateIndex(tbl, ix))
    case DropIndex(_, ix) =>
      List(s"DROP INDEX ${ix.name};")
    case AddTrigger(t) =>
      List(TriggerSql.functionBody(t), TriggerSql.triggerStatement(t))
    case DropTrigger(t) =>
      TriggerSql.dropStatements(t)

  private def renderCreateTable(t: TableSpec): List[String] =
    val columnLines = t.columns.map(c => "    " + renderColumnDef(c))
    val pkLine      = s"    CONSTRAINT pk_${t.name} PRIMARY KEY (${t.primaryKey})"
    val fkLines = t.foreignKeys.map: fk =>
      "    " + renderForeignKeyInline(t.name, fk)
    val checkLines = namedChecks(t).map: (name, sql) =>
      s"    CONSTRAINT $name CHECK ($sql)"
    val bodyLines = (columnLines ++ List(pkLine) ++ fkLines ++ checkLines)
      .mkString(",\n")
    val createStmt = s"CREATE TABLE ${t.name} (\n$bodyLines\n);"
    val indexStmts = t.indexes.map(ix => renderCreateIndex(t.name, ix))
    createStmt :: indexStmts

  private def renderColumnDef(c: ColumnSpec): String =
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    parts += c.name
    parts += c.sqlType
    c.defaultValue.foreach(d => parts += s"DEFAULT $d")
    parts += (if c.nullable then "NULL" else "NOT NULL")
    parts.mkString(" ")

  private def renderForeignKeyInline(tableName: String, fk: ForeignKeySpec): String =
    s"CONSTRAINT ${fkName(tableName, fk)} FOREIGN KEY (${fk.column}) " +
      s"REFERENCES ${fk.refTable}(${fk.refColumn}) ON DELETE ${fk.onDelete}"

  private def renderAddForeignKey(tableName: String, fk: ForeignKeySpec): String =
    s"ALTER TABLE $tableName ADD CONSTRAINT ${fkName(tableName, fk)} " +
      s"FOREIGN KEY (${fk.column}) REFERENCES ${fk.refTable}(${fk.refColumn}) " +
      s"ON DELETE ${fk.onDelete};"

  private def renderCreateIndex(tableName: String, ix: IndexSpec): String =
    val unique = if ix.unique then "UNIQUE " else ""
    val where  = ix.filterClause.fold("")(f => s" WHERE $f")
    s"CREATE ${unique}INDEX ${ix.name} ON $tableName (${ix.columns.mkString(", ")})$where;"

  private def stripAutoIncrement(sqlType: String): String = sqlType match
    case "BIGSERIAL" => "BIGINT"
    case "SERIAL"    => "INTEGER"
    case other       => other
