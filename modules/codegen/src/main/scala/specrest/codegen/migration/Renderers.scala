package specrest.codegen.migration

import specrest.codegen.migration.AlembicSyntax.mapServerDefault
import specrest.codegen.migration.AlembicSyntax.mapSqlTypeToSa
import specrest.codegen.migration.AlembicSyntax.pythonStringLiteral
import specrest.codegen.migration.MigrationOp.*
import specrest.codegen.migration.SchemaDiff.fkName
import specrest.codegen.migration.SchemaDiff.namedChecks
import specrest.codegen.migration.SchemaDiff.rewriteCheck
import specrest.convention.ColumnSpec
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec

object Renderers:

  final case class Rendered(sql: () => List[String], alembic: () => List[String])

  def render(op: MigrationOp, dialect: Dialect = Postgres): Rendered = op match
    case CreateTable(t) =>
      Rendered(
        sql = () => sqlCreateTable(t, dialect),
        alembic = () => alembicCreateTable(t, dialect)
      )

    case DropTable(t) =>
      Rendered(
        sql = () => List(s"DROP TABLE ${t.name};"),
        alembic = () => List(s"""op.drop_table("${t.name}")""")
      )

    case AddColumn(tbl, c) =>
      val isSerial = c.sqlType == "BIGSERIAL" || c.sqlType == "SERIAL"
      Rendered(
        sql = () => List(s"ALTER TABLE $tbl ADD COLUMN ${sqlColumnDef(c, dialect)};"),
        alembic = () =>
          List(s"""op.add_column("$tbl", ${alembicColumn(
              c,
              primaryKey = false,
              autoincrement = isSerial,
              dialect = dialect
            )})""")
      )

    case DropColumn(tbl, c) =>
      Rendered(
        sql = () => List(s"ALTER TABLE $tbl DROP COLUMN ${c.name};"),
        alembic = () => List(s"""op.drop_column("$tbl", "${c.name}")""")
      )

    case AlterColumnType(tbl, n, _, newT) =>
      Rendered(
        sql = () => List(s"ALTER TABLE $tbl ALTER COLUMN $n TYPE ${stripAutoIncrement(newT)};"),
        alembic = () =>
          List(s"""op.alter_column("$tbl", "$n", type_=${mapSqlTypeToSa(newT, dialect)})""")
      )

    case AlterColumnNullable(tbl, n, _, newNullable) =>
      val sqlVerb     = if newNullable then "DROP NOT NULL" else "SET NOT NULL"
      val alembicFlag = if newNullable then "True" else "False"
      Rendered(
        sql = () => List(s"ALTER TABLE $tbl ALTER COLUMN $n $sqlVerb;"),
        alembic = () => List(s"""op.alter_column("$tbl", "$n", nullable=$alembicFlag)""")
      )

    case AlterColumnDefault(tbl, n, _, newDefault) =>
      Rendered(
        sql = () =>
          newDefault match
            case Some(d) =>
              List(s"ALTER TABLE $tbl ALTER COLUMN $n SET DEFAULT ${dialect.sqlServerDefault(d)};")
            case None => List(s"ALTER TABLE $tbl ALTER COLUMN $n DROP DEFAULT;"),
        alembic = () =>
          val alembicRendered =
            mapServerDefault(newDefault.map(dialect.alembicServerDefault)).getOrElse("None")
          List(s"""op.alter_column("$tbl", "$n", server_default=$alembicRendered)""")
      )

    case AddCheck(tbl, name, sql) =>
      // Apply the same dialect-aware regex rewrite as the initial-schema path (namedChecks):
      // Postgres `~`, MySQL `REGEXP`, SQLite -> None (the AddCheck becomes a no-op).
      Rendered(
        sql = () =>
          rewriteCheck(sql, dialect).toList
            .map(s => s"ALTER TABLE $tbl ADD CONSTRAINT $name CHECK ($s);"),
        alembic = () =>
          rewriteCheck(sql, dialect).toList
            .map(s => s"""op.create_check_constraint("$name", "$tbl", ${pythonStringLiteral(s)})""")
      )

    case DropCheck(tbl, name, _) =>
      Rendered(
        sql = () => List(s"ALTER TABLE $tbl DROP CONSTRAINT $name;"),
        alembic = () => List(s"""op.drop_constraint("$name", "$tbl", type_="check")""")
      )

    case AddForeignKey(tbl, fk) =>
      Rendered(
        sql = () => List(sqlAddForeignKey(tbl, fk)),
        alembic = () => List(alembicCreateFk(tbl, fk))
      )

    case DropForeignKey(tbl, fk) =>
      Rendered(
        sql = () => List(s"ALTER TABLE $tbl DROP CONSTRAINT ${fkName(tbl, fk)};"),
        alembic = () =>
          List(s"""op.drop_constraint("${fkName(tbl, fk)}", "$tbl", type_="foreignkey")""")
      )

    case AddIndex(tbl, ix) =>
      Rendered(
        sql = () => List(sqlCreateIndex(tbl, ix, dialect)),
        alembic = () => List(alembicCreateIndex(tbl, ix, dialect))
      )

    case DropIndex(tbl, ix) =>
      Rendered(
        sql = () => List(s"DROP INDEX ${ix.name};"),
        alembic = () => List(s"""op.drop_index("${ix.name}", table_name="$tbl")""")
      )

    case AddTrigger(t) =>
      Rendered(
        sql = () => dialect.rawTrigger(t).upgrade,
        alembic = () => dialect.renderTrigger(t).upgrade
      )

    case DropTrigger(t) =>
      Rendered(
        sql = () => dialect.rawTrigger(t).downgrade,
        alembic = () => dialect.renderTrigger(t).downgrade
      )

  private def isSerial(sqlType: String): Boolean =
    CanonicalType.parse(sqlType) match
      case Some(CanonicalType.Serial4) | Some(CanonicalType.Serial8) => true
      case _                                                         => false

  private def sqlCreateTable(t: TableSpec, dialect: Dialect): List[String] =
    val columnLines = t.columns.map(c => "    " + sqlColumnDef(c, dialect))
    val pkIsSerial  = t.columns.find(_.name == t.primaryKey).exists(c => isSerial(c.sqlType))
    val pkLines =
      if pkIsSerial && !dialect.serialUsesSeparatePk then Nil
      else List(s"    CONSTRAINT pk_${t.name} PRIMARY KEY (${t.primaryKey})")
    val fkLines = t.foreignKeys.map: fk =>
      "    " + sqlForeignKeyInline(t.name, fk)
    val rawAutoIncPk = if pkIsSerial then Some(t.primaryKey) else None
    val checkLines = namedChecks(t, dialect, rawAutoIncPk).map: (name, sql) =>
      s"    CONSTRAINT $name CHECK ($sql)"
    val bodyLines  = (columnLines ++ pkLines ++ fkLines ++ checkLines).mkString(",\n")
    val createStmt = s"CREATE TABLE ${t.name} (\n$bodyLines\n);"
    val indexStmts = t.indexes.map(ix => sqlCreateIndex(t.name, ix, dialect))
    createStmt :: indexStmts

  private def sqlColumnDef(c: ColumnSpec, dialect: Dialect): String =
    if isSerial(c.sqlType) then dialect.serialColumnDef(c.name, c.sqlType)
    else
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      parts += c.name
      parts += dialect.sqlColumnType(c.sqlType)
      c.defaultValue.foreach(d => parts += s"DEFAULT ${dialect.sqlServerDefault(d)}")
      parts += (if c.nullable then "NULL" else "NOT NULL")
      parts.mkString(" ")

  private def sqlForeignKeyInline(tableName: String, fk: ForeignKeySpec): String =
    s"CONSTRAINT ${fkName(tableName, fk)} FOREIGN KEY (${fk.column}) " +
      s"REFERENCES ${fk.refTable}(${fk.refColumn}) ON DELETE ${fk.onDelete}"

  private def sqlAddForeignKey(tableName: String, fk: ForeignKeySpec): String =
    s"ALTER TABLE $tableName ADD CONSTRAINT ${fkName(tableName, fk)} " +
      s"FOREIGN KEY (${fk.column}) REFERENCES ${fk.refTable}(${fk.refColumn}) " +
      s"ON DELETE ${fk.onDelete};"

  private def sqlCreateIndex(tableName: String, ix: IndexSpec, dialect: Dialect): String =
    // MySQL has no partial indexes. A partial index degrades to a plain index; crucially the
    // `UNIQUE` is also dropped, because a *full* unique index over-enforces (it would reject
    // rows the partial predicate excluded). A non-partial unique index is unaffected.
    val partialDropped = ix.filterClause.isDefined && !dialect.caps.supportsPartialIndex
    val unique         = if ix.unique && !partialDropped then "UNIQUE " else ""
    val where =
      if partialDropped then "" else ix.filterClause.fold("")(f => s" WHERE $f")
    s"CREATE ${unique}INDEX ${ix.name} ON $tableName (${ix.columns.mkString(", ")})$where;"

  private def stripAutoIncrement(sqlType: String): String = sqlType match
    case "BIGSERIAL" => "BIGINT"
    case "SERIAL"    => "INTEGER"
    case other       => other

  private def alembicCreateTable(t: TableSpec, dialect: Dialect): List[String] =
    val columnArgs = t.columns.map: c =>
      val isPk     = c.name == t.primaryKey
      val isSerial = c.sqlType == "BIGSERIAL" || c.sqlType == "SERIAL"
      alembicColumn(c, primaryKey = isPk, autoincrement = isSerial, dialect = dialect)
    val fkArgs = t.foreignKeys.map(fk => alembicForeignKeyConstraint(t.name, fk))
    val checkArgs = namedChecks(t, dialect, SchemaDiff.sqlalchemyAutoIncrementPk(t)).map:
      (name, sql) => s"""sa.CheckConstraint(${pythonStringLiteral(sql)}, name="$name")"""
    val allArgs = (columnArgs ++ fkArgs ++ checkArgs).map(a => s"    $a,").mkString("\n")
    val createStmt =
      s"""op.create_table(
         |    "${t.name}",
         |$allArgs
         |)""".stripMargin
    val indexStmts = t.indexes.map(ix => alembicCreateIndex(t.name, ix, dialect))
    createStmt :: indexStmts

  private def alembicColumn(
      c: ColumnSpec,
      primaryKey: Boolean,
      autoincrement: Boolean,
      dialect: Dialect
  ): String =
    val parts = List.newBuilder[String]
    parts += s""""${c.name}""""
    parts += mapSqlTypeToSa(c.sqlType, dialect)
    if primaryKey then parts += "primary_key=True"
    if autoincrement then parts += "autoincrement=True"
    mapServerDefault(c.defaultValue.map(dialect.alembicServerDefault))
      .foreach(d => parts += s"server_default=$d")
    parts += s"nullable=${if c.nullable then "True" else "False"}"
    s"sa.Column(${parts.result().mkString(", ")})"

  private def alembicForeignKeyConstraint(tableName: String, fk: ForeignKeySpec): String =
    s"""sa.ForeignKeyConstraint(["${fk.column}"], ["${fk.refTable}.${fk.refColumn}"], """ +
      s"""ondelete="${fk.onDelete}", name="${fkName(tableName, fk)}")"""

  private def alembicCreateFk(tableName: String, fk: ForeignKeySpec): String =
    s"""op.create_foreign_key("${fkName(tableName, fk)}", "$tableName", "${fk.refTable}", """ +
      s"""["${fk.column}"], ["${fk.refColumn}"], ondelete="${fk.onDelete}")"""

  private def alembicCreateIndex(tableName: String, ix: IndexSpec, dialect: Dialect): String =
    val cols    = ix.columns.map(c => s""""$c"""").mkString(", ")
    val unique  = if ix.unique then "True" else "False"
    val partial = dialect.partialIndex(ix).value
    s"""op.create_index("${ix.name}", "$tableName", [$cols], unique=$unique$partial)"""
