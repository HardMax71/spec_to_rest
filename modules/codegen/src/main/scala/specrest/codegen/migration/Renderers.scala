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
        sql = () => List(s"DROP TABLE ${table_name(t)};"),
        alembic = () => List(s"""op.drop_table("${table_name(t)}")""")
      )

    case AddColumn(tbl, c) =>
      val isSerial = CanonicalType.isAutoIncrementType(column_sql_type(c))
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
        sql = () => List(s"ALTER TABLE $tbl DROP COLUMN ${column_name(c)};"),
        alembic = () => List(s"""op.drop_column("$tbl", "${column_name(c)}")""")
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
        sql = () => List(s"DROP INDEX ${index_name(ix)};"),
        alembic = () => List(s"""op.drop_index("${index_name(ix)}", table_name="$tbl")""")
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
    val cols        = table_columns(t)
    val pk          = table_primary_key(t)
    val tname       = table_name(t)
    val columnLines = cols.map(c => "    " + sqlColumnDef(c, dialect))
    val pkIsSerial  = cols.find(c => column_name(c) == pk).exists(c => isSerial(column_sql_type(c)))
    val pkLines =
      if pkIsSerial && !dialect.serialUsesSeparatePk then Nil
      else List(s"    CONSTRAINT pk_$tname PRIMARY KEY ($pk)")
    val fkLines = table_foreign_keys(t).map: fk =>
      "    " + sqlForeignKeyInline(tname, fk)
    val checkLines = namedChecks(t, dialect, SchemaDiff.autoIncrementPk(t)).map: (name, sql) =>
      s"    CONSTRAINT $name CHECK ($sql)"
    val bodyLines  = (columnLines ++ pkLines ++ fkLines ++ checkLines).mkString(",\n")
    val createStmt = s"CREATE TABLE $tname (\n$bodyLines\n);"
    val indexStmts = table_indexes(t).map(ix => sqlCreateIndex(tname, ix, dialect))
    createStmt :: indexStmts

  private def sqlColumnDef(c: column_spec, dialect: Dialect): String =
    val sqlT = column_sql_type(c)
    if isSerial(sqlT) then dialect.serialColumnDef(column_name(c), sqlT)
    else
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      parts += column_name(c)
      parts += dialect.sqlColumnType(sqlT)
      column_default_value(c).foreach(d => parts += s"DEFAULT ${dialect.sqlServerDefault(d)}")
      parts += (if column_nullable(c) then "NULL" else "NOT NULL")
      parts.mkString(" ")

  private def sqlForeignKeyInline(tableName: String, fk: foreign_key_spec): String =
    s"CONSTRAINT ${fkName(tableName, fk)} FOREIGN KEY (${fk_column(fk)}) " +
      s"REFERENCES ${fk_ref_table(fk)}(${fk_ref_column(fk)}) ON DELETE ${fk_on_delete(fk)}"

  private def sqlAddForeignKey(tableName: String, fk: foreign_key_spec): String =
    s"ALTER TABLE $tableName ADD CONSTRAINT ${fkName(tableName, fk)} " +
      s"FOREIGN KEY (${fk_column(fk)}) REFERENCES ${fk_ref_table(fk)}(${fk_ref_column(fk)}) " +
      s"ON DELETE ${fk_on_delete(fk)};"

  private def sqlCreateIndex(tableName: String, ix: index_spec, dialect: Dialect): String =
    // MySQL has no partial indexes. A partial index degrades to a plain index; crucially the
    // `UNIQUE` is also dropped, because a *full* unique index over-enforces (it would reject
    // rows the partial predicate excluded). A non-partial unique index is unaffected.
    val filter         = index_filter_clause(ix)
    val partialDropped = filter.isDefined && !dialect.caps.supportsPartialIndex
    val unique         = if index_unique(ix) && !partialDropped then "UNIQUE " else ""
    val where =
      if partialDropped then "" else filter.fold("")(f => s" WHERE $f")
    s"CREATE ${unique}INDEX ${index_name(ix)} ON $tableName (${index_columns(ix).mkString(", ")})$where;"

  private def stripAutoIncrement(sqlType: String): String = sqlType match
    case "BIGSERIAL" => "BIGINT"
    case "SERIAL"    => "INTEGER"
    case other       => other

  private def alembicCreateTable(t: table_spec, dialect: Dialect): List[String] =
    val tname = table_name(t)
    val pk    = table_primary_key(t)
    val columnArgs = table_columns(t).map: c =>
      val isPk     = column_name(c) == pk
      val sqlT     = column_sql_type(c)
      val isSerial = sqlT == "BIGSERIAL" || sqlT == "SERIAL"
      alembicColumn(c, primaryKey = isPk, autoincrement = isSerial, dialect = dialect)
    val fkArgs = table_foreign_keys(t).map(fk => alembicForeignKeyConstraint(tname, fk))
    val checkArgs = namedChecks(t, dialect, SchemaDiff.autoIncrementPk(t)).map: (name, sql) =>
      s"""sa.CheckConstraint(${pythonStringLiteral(sql)}, name="$name")"""
    val allArgs = (columnArgs ++ fkArgs ++ checkArgs).map(a => s"    $a,").mkString("\n")
    val createStmt =
      s"""op.create_table(
         |    "$tname",
         |$allArgs
         |)""".stripMargin
    val indexStmts = table_indexes(t).map(ix => alembicCreateIndex(tname, ix, dialect))
    createStmt :: indexStmts

  private def alembicColumn(
      c: column_spec,
      primaryKey: Boolean,
      autoincrement: Boolean,
      dialect: Dialect
  ): String =
    val parts = List.newBuilder[String]
    parts += s""""${column_name(c)}""""
    parts += mapSqlTypeToSa(column_sql_type(c), dialect)
    if primaryKey then parts += "primary_key=True"
    // See Migration.renderColumnCall: pin a non-serial PK to autoincrement=False so SQLAlchemy's
    // default "auto" cannot turn an application-supplied integer PK into SERIAL/AUTO_INCREMENT.
    if primaryKey then parts += s"autoincrement=${if autoincrement then "True" else "False"}"
    else if autoincrement then parts += "autoincrement=True"
    mapServerDefault(column_default_value(c).map(dialect.alembicServerDefault))
      .foreach(d => parts += s"server_default=$d")
    parts += s"nullable=${if column_nullable(c) then "True" else "False"}"
    s"sa.Column(${parts.result().mkString(", ")})"

  private def alembicForeignKeyConstraint(tableName: String, fk: foreign_key_spec): String =
    s"""sa.ForeignKeyConstraint(["${fk_column(fk)}"], ["${fk_ref_table(fk)}.${fk_ref_column(
        fk
      )}"], """ +
      s"""ondelete="${fk_on_delete(fk)}", name="${fkName(tableName, fk)}")"""

  private def alembicCreateFk(tableName: String, fk: foreign_key_spec): String =
    s"""op.create_foreign_key("${fkName(tableName, fk)}", "$tableName", "${fk_ref_table(fk)}", """ +
      s"""["${fk_column(fk)}"], ["${fk_ref_column(fk)}"], ondelete="${fk_on_delete(fk)}")"""

  private def alembicCreateIndex(tableName: String, ix: index_spec, dialect: Dialect): String =
    val cols    = index_columns(ix).map(c => s""""$c"""").mkString(", ")
    val unique  = if index_unique(ix) then "True" else "False"
    val partial = dialect.partialIndex(ix).value
    s"""op.create_index("${index_name(ix)}", "$tableName", [$cols], unique=$unique$partial)"""
