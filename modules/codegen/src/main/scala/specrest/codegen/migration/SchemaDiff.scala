package specrest.codegen.migration

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

object SchemaDiff:

  private given CanEqual[migration_op, migration_op] = CanEqual.derived
  private given CanEqual[trigger_spec, trigger_spec] = CanEqual.derived

  def compute(prev: database_schema, next: database_schema): List[migration_op] =
    val prevTables = schema_tables(prev)
    val nextTables = schema_tables(next)
    val prevByName = prevTables.map(t => table_name(t) -> t).toMap
    val nextByName = nextTables.map(t => table_name(t) -> t).toMap

    val intraDrops = nextTables.flatMap: n =>
      prevByName.get(table_name(n)).toList.flatMap(p => intraTableDrops(p, n))

    val droppedTables = topoSort(
      prevTables.filterNot(t => nextByName.contains(table_name(t)))
    ).reverse.map(DropTable.apply)

    val createdTables = topoSort(
      nextTables.filterNot(t => prevByName.contains(table_name(t)))
    ).map(CreateTable.apply)

    val intraAlters = nextTables.flatMap: n =>
      prevByName.get(table_name(n)).toList.flatMap(p => intraTableAlters(p, n))

    val intraAdds = nextTables.flatMap: n =>
      prevByName.get(table_name(n)).toList.flatMap(p => intraTableAdds(p, n))

    val prevTriggers = schema_triggers(prev).toSet
    val nextTriggers = schema_triggers(next).toSet
    val droppedTriggers = schema_triggers(prev)
      .filterNot(nextTriggers.contains)
      .map(DropTrigger.apply)
    val addedTriggers = schema_triggers(next)
      .filterNot(prevTriggers.contains)
      .map(AddTrigger.apply)

    droppedTriggers ++ intraDrops ++ droppedTables ++ createdTables ++
      intraAlters ++ intraAdds ++ addedTriggers

  def destructive(ops: List[migration_op]): List[migration_op] =
    ops.filter(is_destructive_op)

  def describeDestructive(op: migration_op): String = op match
    case DropTable(t)         => s"drops table '${table_name(t)}'"
    case DropColumn(tbl, col) => s"drops column '$tbl.${column_name(col)}'"
    case _                    => "destructive change"

  def fkName(tableName: String, fk: foreign_key_spec): String =
    s"fk_${tableName}_${fk_column(fk)}"

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
    table_checks(t).distinct.zipWithIndex
      .filterNot((sql, _) => dropsAutoIncCheck(sql))
      .flatMap((sql, i) => rewriteCheck(sql, dialect).map(s => (s"ck_${table_name(t)}_$i", s)))

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
    table_columns(t)
      .find(c =>
        column_name(c) == table_primary_key(t) && CanonicalType.isAutoIncrementType(
          column_sql_type(c)
        )
      )
      .map(column_name)

  private def referencesColumn(sql: String, column: String): Boolean =
    ("\\b" + java.util.regex.Pattern.quote(column) + "\\b").r.findFirstIn(sql).isDefined

  private def intraTableDrops(prev: table_spec, next: table_spec): List[migration_op] =
    val nextColNames = table_columns(next).map(column_name).toSet
    val droppedCols = table_columns(prev)
      .filterNot(c => nextColNames.contains(column_name(c)))
      .map(c => DropColumn(table_name(prev), c))

    val nextIndexes = table_indexes(next).toSet
    val droppedIndexes = table_indexes(prev)
      .filterNot(nextIndexes.contains)
      .map(i => DropIndex(table_name(prev), i))

    val nextFks = table_foreign_keys(next).toSet
    val droppedFks = table_foreign_keys(prev)
      .filterNot(nextFks.contains)
      .map(fk => DropForeignKey(table_name(prev), fk))

    val nextChecks = namedChecks(next).toSet
    val droppedChecks = namedChecks(prev)
      .filterNot(nextChecks.contains)
      .map((name, sql) => DropCheck(table_name(prev), name, sql))

    droppedIndexes ++ droppedChecks ++ droppedFks ++ droppedCols

  private def intraTableAlters(prev: table_spec, next: table_spec): List[migration_op] =
    val prevByName = table_columns(prev).map(c => column_name(c) -> c).toMap
    table_columns(next).flatMap: nc =>
      prevByName.get(column_name(nc)).toList.flatMap: pc =>
        val pcSql = column_sql_type(pc)
        val ncSql = column_sql_type(nc)
        val typeChange =
          if pcSql == ncSql then Nil
          else if CanonicalType.isAutoIncrementType(pcSql) ||
            CanonicalType.isAutoIncrementType(ncSql)
          then
            throw new RuntimeException(
              s"Cannot ALTER ${table_name(next)}.${column_name(nc)} from $pcSql to $ncSql: " +
                "auto-increment identity supply (SERIAL/BIGSERIAL) cannot be added or removed " +
                "in place. The transition changes default-value generation, sequence ownership, " +
                "and FK semantics; perform a manual data-preserving migration."
            )
          else List(AlterColumnType(table_name(next), column_name(nc), pcSql, ncSql))
        val nullableChange =
          if column_nullable(pc) != column_nullable(nc) then
            List(AlterColumnNullable(
              table_name(next),
              column_name(nc),
              column_nullable(pc),
              column_nullable(nc)
            ))
          else Nil
        val pcDef = column_default_value(pc)
        val ncDef = column_default_value(nc)
        val defaultChange =
          if pcDef != ncDef then
            List(AlterColumnDefault(
              table_name(next),
              column_name(nc),
              pcDef,
              ncDef
            ))
          else Nil
        typeChange ++ nullableChange ++ defaultChange

  private def intraTableAdds(prev: table_spec, next: table_spec): List[migration_op] =
    val prevColNames = table_columns(prev).map(column_name).toSet
    val addedCols = table_columns(next)
      .filterNot(c => prevColNames.contains(column_name(c)))
      .map(c => AddColumn(table_name(next), c))

    val prevIndexes = table_indexes(prev).toSet
    val addedIndexes = table_indexes(next)
      .filterNot(prevIndexes.contains)
      .map(i => AddIndex(table_name(next), i))

    val prevFks = table_foreign_keys(prev).toSet
    val addedFks = table_foreign_keys(next)
      .filterNot(prevFks.contains)
      .map(fk => AddForeignKey(table_name(next), fk))

    val prevChecks = namedChecks(prev).toSet
    val addedChecks = namedChecks(next)
      .filterNot(prevChecks.contains)
      .map((name, sql) => AddCheck(table_name(next), name, sql))

    addedCols ++ addedFks ++ addedChecks ++ addedIndexes

  def topoSort(tables: List[table_spec]): List[table_spec] =
    val pairs = tables.map(t => (table_name(t), table_foreign_keys(t).map(fk_ref_table)))
    SpecRestGenerated.topo_sort_names(pairs) match
      case None =>
        val names = tables.map(table_name).mkString(", ")
        throw new RuntimeException(s"Foreign-key cycle detected among tables: $names")
      case Some(sortedNames) =>
        val byName = tables.map(t => table_name(t) -> t).toMap
        sortedNames.flatMap(byName.get)
