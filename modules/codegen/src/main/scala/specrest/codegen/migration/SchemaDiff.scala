package specrest.codegen.migration

import specrest.ir.generated.SpecRestGenerated.*

object SchemaDiff:

  private given CanEqual[migration_op, migration_op] = CanEqual.derived

  def compute(prev: database_schema, next: database_schema): List[migration_op] =
    validateAutoIncrementAlters(prev, next)
    val prevChecks = checkAssign(schemaTables(prev))
    val nextChecks = checkAssign(schemaTables(next))
    computeDiff(prev, next, prevChecks, nextChecks) match
      case Some(ops) => ops
      case None =>
        val names = (schemaTables(prev) ++ schemaTables(next))
          .map(tableName).distinct.mkString(", ")
        throw new RuntimeException(s"Foreign-key cycle detected among tables: $names")

  def destructive(ops: List[migration_op]): List[migration_op] =
    ops.filter(isDestructiveOp)

  def describeDestructive(op: migration_op): String = op match
    case DropTable(t)         => s"drops table '${tableName(t)}'"
    case DropColumn(tbl, col) => s"drops column '$tbl.${columnName(col)}'"
    case _                    => "destructive change"

  def fkName(tableName: String, fk: foreign_key_spec): String =
    s"fk_${tableName}_${fkColumn(fk)}"

  // `autoIncrementPk` is the PK column the renderers emit as DB-generated — now a single notion
  // across raw SQL and SQLAlchemy (`autoIncrementPk` below), since Alembic pins an explicit
  // integer PK to `autoincrement=False`. When set and the dialect forbids it, a CHECK referencing
  // that column is dropped (MySQL error 3818 — only synthesized serial PKs are affected; an
  // explicit `id: Int` keeps its `id > 0` CHECK on every dialect).
  def namedChecks(
      t: table_spec,
      dialect: Dialect = Postgres,
      autoIncrementPk: Option[String] = None
  ): List[(String, String)] =
    val dropsAutoIncCheck = (sql: String) =>
      !dialect.caps.supportsCheckOnAutoIncrement &&
        autoIncrementPk.exists(col => referencesColumn(sql, col))
    tableChecks(t).distinct.zipWithIndex
      .filterNot((sql, _) => dropsAutoIncCheck(sql))
      .flatMap((sql, i) => rewriteCheck(sql, dialect).map(s => (s"ck_${tableName(t)}_$i", s)))

  // A `value matches /re/` refinement is emitted canonically as the Postgres `col ~ 'pat'`.
  // Rewrite it per dialect (Postgres identity, MySQL REGEXP, SQLite -> dropped).
  private val regexCheckPattern = """^(\w+) ~ '(.*)'$""".r

  private[migration] def rewriteCheck(sql: String, dialect: Dialect): Option[String] = sql match
    case regexCheckPattern(col, pat) => dialect.regexCheck(col, pat)
    case _                           => Some(sql)

  // The PK column that the renderers emit as DB-generated, i.e. only a synthesized serial PK.
  // Derived from the one canonical predicate so raw SQL, Alembic and Prisma cannot disagree:
  // an explicitly-declared `id: Int` is application-supplied (Alembic pins autoincrement=False),
  // so it is *not* returned here and its CHECK is kept on MySQL.
  def autoIncrementPk(t: table_spec): Option[String] =
    tableColumns(t)
      .find(c =>
        columnName(c) == tablePrimaryKey(t) && CanonicalType.isAutoIncrementType(
          columnSqlType(c)
        )
      )
      .map(columnName)

  private def referencesColumn(sql: String, column: String): Boolean =
    ("\\b" + java.util.regex.Pattern.quote(column) + "\\b").r.findFirstIn(sql).isDefined

  def topoSort(tables: List[table_spec]): List[table_spec] =
    sortTablesByFk(tables) match
      case Some(sorted) => sorted
      case None =>
        val names = tables.map(tableName).mkString(", ")
        throw new RuntimeException(s"Foreign-key cycle detected among tables: $names")

  private def checkAssign(tables: List[table_spec]): List[(String, List[(String, String)])] =
    tables.map(t => tableName(t) -> namedChecks(t))

  // The auto-increment ALTER precondition stays in Scala: the lifted diff would otherwise
  // silently emit an AlterColumnType that is impossible to apply (sequence/identity transition
  // requires a manual data-preserving migration). Validating up front gives the caller a
  // proper error before any ops are produced.
  private def validateAutoIncrementAlters(
      prev: database_schema,
      next: database_schema
  ): Unit =
    val prevByName = schemaTables(prev).map(t => tableName(t) -> t).toMap
    schemaTables(next).foreach: nxt =>
      prevByName.get(tableName(nxt)).foreach: p =>
        val pCols = tableColumns(p).map(c => columnName(c) -> c).toMap
        tableColumns(nxt).foreach: nc =>
          pCols.get(columnName(nc)).foreach: pc =>
            val pSql = columnSqlType(pc)
            val nSql = columnSqlType(nc)
            if pSql != nSql && (CanonicalType.isAutoIncrementType(pSql) ||
                CanonicalType.isAutoIncrementType(nSql))
            then
              throw new RuntimeException(
                s"Cannot ALTER ${tableName(nxt)}.${columnName(nc)} from $pSql to $nSql: " +
                  "auto-increment identity supply (SERIAL/BIGSERIAL) cannot be added or removed " +
                  "in place. The transition changes default-value generation, sequence ownership, " +
                  "and FK semantics; perform a manual data-preserving migration."
              )
