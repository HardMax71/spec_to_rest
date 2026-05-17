package specrest.codegen.migration

import specrest.codegen.migration.MigrationOp.*
import specrest.convention.DatabaseSchema
import specrest.convention.ForeignKeySpec
import specrest.convention.TableSpec

import scala.collection.mutable

object SchemaDiff:

  def compute(prev: DatabaseSchema, next: DatabaseSchema): List[MigrationOp] =
    val prevByName = prev.tables.map(t => t.name -> t).toMap
    val nextByName = next.tables.map(t => t.name -> t).toMap

    val intraDrops = next.tables.flatMap: n =>
      prevByName.get(n.name).toList.flatMap(p => intraTableDrops(p, n))

    val droppedTables = topoSort(
      prev.tables.filterNot(t => nextByName.contains(t.name))
    ).reverse.map(MigrationOp.DropTable.apply)

    val createdTables = topoSort(
      next.tables.filterNot(t => prevByName.contains(t.name))
    ).map(MigrationOp.CreateTable.apply)

    val intraAlters = next.tables.flatMap: n =>
      prevByName.get(n.name).toList.flatMap(p => intraTableAlters(p, n))

    val intraAdds = next.tables.flatMap: n =>
      prevByName.get(n.name).toList.flatMap(p => intraTableAdds(p, n))

    val prevTriggers = prev.triggers.toSet
    val nextTriggers = next.triggers.toSet
    val droppedTriggers = prev.triggers
      .filterNot(nextTriggers.contains)
      .map(MigrationOp.DropTrigger.apply)
    val addedTriggers = next.triggers
      .filterNot(prevTriggers.contains)
      .map(MigrationOp.AddTrigger.apply)

    droppedTriggers ++ intraDrops ++ droppedTables ++ createdTables ++
      intraAlters ++ intraAdds ++ addedTriggers

  def destructive(ops: List[MigrationOp]): List[MigrationOp] =
    ops.filter(_.isDestructive)

  def describeDestructive(op: MigrationOp): String = op match
    case DropTable(t)         => s"drops table '${t.name}'"
    case DropColumn(tbl, col) => s"drops column '$tbl.${col.name}'"
    case _                    => "destructive change"

  def fkName(tableName: String, fk: ForeignKeySpec): String =
    s"fk_${tableName}_${fk.column}"

  // `autoIncrementPk` is the PK column the renderers emit as DB-generated — now a single notion
  // across raw SQL and SQLAlchemy (`autoIncrementPk` below), since Alembic pins an explicit
  // integer PK to `autoincrement=False`. When set and the dialect forbids it, a CHECK referencing
  // that column is dropped (MySQL error 3818 — only synthesized serial PKs are affected; an
  // explicit `id: Int` keeps its `id > 0` CHECK on every dialect).
  def namedChecks(
      t: TableSpec,
      dialect: Dialect = Postgres,
      autoIncrementPk: Option[String] = None
  ): List[(String, String)] =
    val dropsAutoIncCheck = (sql: String) =>
      !dialect.caps.supportsCheckOnAutoIncrement &&
        autoIncrementPk.exists(col => referencesColumn(sql, col))
    t.checks.distinct.zipWithIndex
      .filterNot((sql, _) => dropsAutoIncCheck(sql))
      .flatMap((sql, i) => rewriteCheck(sql, dialect).map(s => (s"ck_${t.name}_$i", s)))

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
  def autoIncrementPk(t: TableSpec): Option[String] =
    t.columns
      .find(c => c.name == t.primaryKey && CanonicalType.isAutoIncrementType(c.sqlType))
      .map(_.name)

  private def referencesColumn(sql: String, column: String): Boolean =
    ("\\b" + java.util.regex.Pattern.quote(column) + "\\b").r.findFirstIn(sql).isDefined

  private def intraTableDrops(prev: TableSpec, next: TableSpec): List[MigrationOp] =
    val nextColNames = next.columns.map(_.name).toSet
    val droppedCols = prev.columns
      .filterNot(c => nextColNames.contains(c.name))
      .map(c => MigrationOp.DropColumn(prev.name, c))

    val nextIndexes = next.indexes.toSet
    val droppedIndexes = prev.indexes
      .filterNot(nextIndexes.contains)
      .map(i => MigrationOp.DropIndex(prev.name, i))

    val nextFks = next.foreignKeys.toSet
    val droppedFks = prev.foreignKeys
      .filterNot(nextFks.contains)
      .map(fk => MigrationOp.DropForeignKey(prev.name, fk))

    val nextChecks = namedChecks(next).toSet
    val droppedChecks = namedChecks(prev)
      .filterNot(nextChecks.contains)
      .map((name, sql) => MigrationOp.DropCheck(prev.name, name, sql))

    droppedIndexes ++ droppedChecks ++ droppedFks ++ droppedCols

  private def intraTableAlters(prev: TableSpec, next: TableSpec): List[MigrationOp] =
    val prevByName = prev.columns.map(c => c.name -> c).toMap
    next.columns.flatMap: nc =>
      prevByName.get(nc.name).toList.flatMap: pc =>
        val typeChange =
          if pc.sqlType != nc.sqlType then
            List(MigrationOp.AlterColumnType(next.name, nc.name, pc.sqlType, nc.sqlType))
          else Nil
        val nullableChange =
          if pc.nullable != nc.nullable then
            List(MigrationOp.AlterColumnNullable(next.name, nc.name, pc.nullable, nc.nullable))
          else Nil
        val defaultChange =
          if pc.defaultValue != nc.defaultValue then
            List(MigrationOp.AlterColumnDefault(
              next.name,
              nc.name,
              pc.defaultValue,
              nc.defaultValue
            ))
          else Nil
        typeChange ++ nullableChange ++ defaultChange

  private def intraTableAdds(prev: TableSpec, next: TableSpec): List[MigrationOp] =
    val prevColNames = prev.columns.map(_.name).toSet
    val addedCols = next.columns
      .filterNot(c => prevColNames.contains(c.name))
      .map(c => MigrationOp.AddColumn(next.name, c))

    val prevIndexes = prev.indexes.toSet
    val addedIndexes = next.indexes
      .filterNot(prevIndexes.contains)
      .map(i => MigrationOp.AddIndex(next.name, i))

    val prevFks = prev.foreignKeys.toSet
    val addedFks = next.foreignKeys
      .filterNot(prevFks.contains)
      .map(fk => MigrationOp.AddForeignKey(next.name, fk))

    val prevChecks = namedChecks(prev).toSet
    val addedChecks = namedChecks(next)
      .filterNot(prevChecks.contains)
      .map((name, sql) => MigrationOp.AddCheck(next.name, name, sql))

    addedCols ++ addedFks ++ addedChecks ++ addedIndexes

  private enum TopoColor derives CanEqual:
    case White, Gray, Black

  def topoSort(tables: List[TableSpec]): List[TableSpec] =
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
