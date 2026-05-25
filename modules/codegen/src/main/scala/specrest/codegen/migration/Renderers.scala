package specrest.codegen.migration

import specrest.codegen.migration.AlembicSyntax.mapServerDefault
import specrest.codegen.migration.AlembicSyntax.mapSqlTypeToSa
import specrest.codegen.migration.AlembicSyntax.pythonStringLiteral
import specrest.codegen.migration.SchemaDiff.fkName
import specrest.codegen.migration.SchemaDiff.namedChecks
import specrest.codegen.migration.SchemaDiff.rewriteCheck
import specrest.ir.generated.SpecRestGenerated.*

object Renderers:

  final case class Rendered(sql: () => List[String], alembic: () => List[String])

  def render(op: migration_op, dialect: Dialect = Postgres): Rendered = op match
    case CreateTable(t) =>
      Rendered(
        sql = () => sqlCreateTable(t, dialect),
        alembic = () => alembicCreateTable(t, dialect)
      )

    case DropTable(t) =>
      Rendered(
        sql = () => List(s"DROP TABLE ${tableName(t)};"),
        alembic = () => List(s"""op.drop_table("${tableName(t)}")""")
      )

    case AddColumn(tbl, c) =>
      val isSerial = CanonicalType.isAutoIncrementType(columnSqlType(c))
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
        sql = () => List(s"ALTER TABLE $tbl DROP COLUMN ${columnName(c)};"),
        alembic = () => List(s"""op.drop_column("$tbl", "${columnName(c)}")""")
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
        sql = () => List(s"DROP INDEX ${indexName(ix)};"),
        alembic = () => List(s"""op.drop_index("${indexName(ix)}", table_name="$tbl")""")
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
    CanonicalType.isAutoIncrementType(sqlType)

  private def sqlCreateTable(t: table_spec, dialect: Dialect): List[String] =
    val cols        = tableColumns(t)
    val pk          = tablePrimaryKey(t)
    val tname       = tableName(t)
    val columnLines = cols.map(c => "    " + sqlColumnDef(c, dialect))
    val pkIsSerial  = cols.find(c => columnName(c) == pk).exists(c => isSerial(columnSqlType(c)))
    val pkLines =
      if pkIsSerial && !dialect.serialUsesSeparatePk then Nil
      else List(s"    CONSTRAINT pk_$tname PRIMARY KEY ($pk)")
    val fkLines = tableForeignKeys(t).map: fk =>
      "    " + sqlForeignKeyInline(tname, fk)
    val checkLines = namedChecks(t, dialect, SchemaDiff.autoIncrementPk(t)).map: (name, sql) =>
      s"    CONSTRAINT $name CHECK ($sql)"
    val bodyLines  = (columnLines ++ pkLines ++ fkLines ++ checkLines).mkString(",\n")
    val createStmt = s"CREATE TABLE $tname (\n$bodyLines\n);"
    val indexStmts = tableIndexes(t).map(ix => sqlCreateIndex(tname, ix, dialect))
    createStmt :: indexStmts

  private def sqlColumnDef(c: column_spec, dialect: Dialect): String =
    val sqlT = columnSqlType(c)
    if isSerial(sqlT) then dialect.serialColumnDef(columnName(c), sqlT)
    else
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      parts += columnName(c)
      parts += dialect.sqlColumnType(sqlT)
      columnDefaultValue(c).foreach(d => parts += s"DEFAULT ${dialect.sqlServerDefault(d)}")
      parts += (if columnNullable(c) then "NULL" else "NOT NULL")
      parts.mkString(" ")

  private def sqlForeignKeyInline(tableName: String, fk: foreign_key_spec): String =
    s"CONSTRAINT ${fkName(tableName, fk)} FOREIGN KEY (${fkColumn(fk)}) " +
      s"REFERENCES ${fkRefTable(fk)}(${fkRefColumn(fk)}) ON DELETE ${fkOnDelete(fk)}"

  private def sqlAddForeignKey(tableName: String, fk: foreign_key_spec): String =
    s"ALTER TABLE $tableName ADD CONSTRAINT ${fkName(tableName, fk)} " +
      s"FOREIGN KEY (${fkColumn(fk)}) REFERENCES ${fkRefTable(fk)}(${fkRefColumn(fk)}) " +
      s"ON DELETE ${fkOnDelete(fk)};"

  private def sqlCreateIndex(tableName: String, ix: index_spec, dialect: Dialect): String =
    // MySQL has no partial indexes. A partial index degrades to a plain index; crucially the
    // `UNIQUE` is also dropped, because a *full* unique index over-enforces (it would reject
    // rows the partial predicate excluded). A non-partial unique index is unaffected.
    val filter         = indexFilterClause(ix)
    val partialDropped = filter.isDefined && !dialect.caps.supportsPartialIndex
    val unique         = if indexUnique(ix) && !partialDropped then "UNIQUE " else ""
    val where =
      if partialDropped then "" else filter.fold("")(f => s" WHERE $f")
    s"CREATE ${unique}INDEX ${indexName(ix)} ON $tableName (${indexColumns(ix).mkString(", ")})$where;"

  private def stripAutoIncrement(sqlType: String): String = sqlType match
    case "BIGSERIAL" => "BIGINT"
    case "SERIAL"    => "INTEGER"
    case other       => other

  private def alembicCreateTable(t: table_spec, dialect: Dialect): List[String] =
    val tname = tableName(t)
    val pk    = tablePrimaryKey(t)
    val columnArgs = tableColumns(t).map: c =>
      val isPk     = columnName(c) == pk
      val sqlT     = columnSqlType(c)
      val isSerial = sqlT == "BIGSERIAL" || sqlT == "SERIAL"
      alembicColumn(c, primaryKey = isPk, autoincrement = isSerial, dialect = dialect)
    val fkArgs = tableForeignKeys(t).map(fk => alembicForeignKeyConstraint(tname, fk))
    val checkArgs = namedChecks(t, dialect, SchemaDiff.autoIncrementPk(t)).map: (name, sql) =>
      s"""sa.CheckConstraint(${pythonStringLiteral(sql)}, name="$name")"""
    val allArgs = (columnArgs ++ fkArgs ++ checkArgs).map(a => s"    $a,").mkString("\n")
    val createStmt =
      s"""op.create_table(
         |    "$tname",
         |$allArgs
         |)""".stripMargin
    val indexStmts = tableIndexes(t).map(ix => alembicCreateIndex(tname, ix, dialect))
    createStmt :: indexStmts

  private def alembicColumn(
      c: column_spec,
      primaryKey: Boolean,
      autoincrement: Boolean,
      dialect: Dialect
  ): String =
    val parts = List.newBuilder[String]
    parts += s""""${columnName(c)}""""
    parts += mapSqlTypeToSa(columnSqlType(c), dialect)
    if primaryKey then parts += "primary_key=True"
    // See Migration.renderColumnCall: pin a non-serial PK to autoincrement=False so SQLAlchemy's
    // default "auto" cannot turn an application-supplied integer PK into SERIAL/AUTO_INCREMENT.
    if primaryKey then parts += s"autoincrement=${if autoincrement then "True" else "False"}"
    else if autoincrement then parts += "autoincrement=True"
    mapServerDefault(columnDefaultValue(c).map(dialect.alembicServerDefault))
      .foreach(d => parts += s"server_default=$d")
    parts += s"nullable=${if columnNullable(c) then "True" else "False"}"
    s"sa.Column(${parts.result().mkString(", ")})"

  private def alembicForeignKeyConstraint(tableName: String, fk: foreign_key_spec): String =
    s"""sa.ForeignKeyConstraint(["${fkColumn(fk)}"], ["${fkRefTable(fk)}.${fkRefColumn(
        fk
      )}"], """ +
      s"""ondelete="${fkOnDelete(fk)}", name="${fkName(tableName, fk)}")"""

  private def alembicCreateFk(tableName: String, fk: foreign_key_spec): String =
    s"""op.create_foreign_key("${fkName(tableName, fk)}", "$tableName", "${fkRefTable(fk)}", """ +
      s"""["${fkColumn(fk)}"], ["${fkRefColumn(fk)}"], ondelete="${fkOnDelete(fk)}")"""

  private def alembicCreateIndex(tableName: String, ix: index_spec, dialect: Dialect): String =
    val cols    = indexColumns(ix).map(c => s""""$c"""").mkString(", ")
    val unique  = if indexUnique(ix) then "True" else "False"
    val partial = dialect.partialIndex(ix).value
    s"""op.create_index("${indexName(ix)}", "$tableName", [$cols], unique=$unique$partial)"""
