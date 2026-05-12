package specrest.codegen.migration

import specrest.codegen.migration.AlembicSyntax.mapServerDefault
import specrest.codegen.migration.AlembicSyntax.mapSqlTypeToSa
import specrest.codegen.migration.AlembicSyntax.pythonStringLiteral
import specrest.codegen.migration.MigrationOp.*
import specrest.codegen.migration.SchemaDiff.fkName
import specrest.codegen.migration.SchemaDiff.namedChecks
import specrest.convention.ColumnSpec
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec

object AlembicRenderer:

  def upgrade(ops: List[MigrationOp]): List[String] =
    ops.flatMap(forward)

  def downgrade(ops: List[MigrationOp]): List[String] =
    ops.reverse.flatMap(op => forward(op.inverse))

  private def forward(op: MigrationOp): List[String] = op match
    case CreateTable(t) => renderCreateTable(t)
    case DropTable(t)   => List(s"""op.drop_table("${t.name}")""")
    case AddColumn(tbl, c) =>
      List(s"""op.add_column("$tbl", ${renderColumn(
          c,
          primaryKey = false,
          autoincrement = false
        )})""")
    case DropColumn(tbl, c) =>
      List(s"""op.drop_column("$tbl", "${c.name}")""")
    case AlterColumnType(tbl, n, _, newT) =>
      val saType = mapSqlTypeToSa(newT)
      List(s"""op.alter_column("$tbl", "$n", type_=$saType)""")
    case AlterColumnNullable(tbl, n, _, newNullable) =>
      val flag = if newNullable then "True" else "False"
      List(s"""op.alter_column("$tbl", "$n", nullable=$flag)""")
    case AlterColumnDefault(tbl, n, _, newDefault) =>
      val rendered = mapServerDefault(newDefault).getOrElse("None")
      List(s"""op.alter_column("$tbl", "$n", server_default=$rendered)""")
    case AddCheck(tbl, name, sql) =>
      List(s"""op.create_check_constraint("$name", "$tbl", ${pythonStringLiteral(sql)})""")
    case DropCheck(tbl, name, _) =>
      List(s"""op.drop_constraint("$name", "$tbl", type_="check")""")
    case AddForeignKey(tbl, fk) =>
      List(renderCreateFk(tbl, fk))
    case DropForeignKey(tbl, fk) =>
      List(s"""op.drop_constraint("${fkName(tbl, fk)}", "$tbl", type_="foreignkey")""")
    case AddIndex(tbl, ix)  => List(renderCreateIndex(tbl, ix))
    case DropIndex(tbl, ix) => List(s"""op.drop_index("${ix.name}", table_name="$tbl")""")

  private def renderCreateTable(t: TableSpec): List[String] =
    val columnArgs = t.columns.map: c =>
      val isPk     = c.name == t.primaryKey
      val isSerial = c.sqlType == "BIGSERIAL" || c.sqlType == "SERIAL"
      renderColumn(c, primaryKey = isPk, autoincrement = isSerial)
    val fkArgs = t.foreignKeys.map: fk =>
      renderForeignKeyConstraint(t.name, fk)
    val checkArgs = namedChecks(t).map: (name, sql) =>
      s"""sa.CheckConstraint(${pythonStringLiteral(sql)}, name="$name")"""
    val allArgs = (columnArgs ++ fkArgs ++ checkArgs).map(a => s"    $a,").mkString("\n")
    val createStmt =
      s"""op.create_table(
         |    "${t.name}",
         |$allArgs
         |)""".stripMargin
    val indexStmts = t.indexes.map(ix => renderCreateIndex(t.name, ix))
    createStmt :: indexStmts

  private def renderColumn(
      c: ColumnSpec,
      primaryKey: Boolean,
      autoincrement: Boolean
  ): String =
    val parts = List.newBuilder[String]
    parts += s""""${c.name}""""
    parts += mapSqlTypeToSa(c.sqlType)
    if primaryKey then parts += "primary_key=True"
    if autoincrement then parts += "autoincrement=True"
    mapServerDefault(c.defaultValue).foreach(d => parts += s"server_default=$d")
    parts += s"nullable=${if c.nullable then "True" else "False"}"
    s"sa.Column(${parts.result().mkString(", ")})"

  private def renderForeignKeyConstraint(tableName: String, fk: ForeignKeySpec): String =
    s"""sa.ForeignKeyConstraint(["${fk.column}"], ["${fk.refTable}.${fk.refColumn}"], """ +
      s"""ondelete="${fk.onDelete}", name="${fkName(tableName, fk)}")"""

  private def renderCreateFk(tableName: String, fk: ForeignKeySpec): String =
    s"""op.create_foreign_key("${fkName(tableName, fk)}", "$tableName", "${fk.refTable}", """ +
      s"""["${fk.column}"], ["${fk.refColumn}"], ondelete="${fk.onDelete}")"""

  private def renderCreateIndex(tableName: String, ix: IndexSpec): String =
    val cols   = ix.columns.map(c => s""""$c"""").mkString(", ")
    val unique = if ix.unique then "True" else "False"
    s"""op.create_index("${ix.name}", "$tableName", [$cols], unique=$unique)"""
