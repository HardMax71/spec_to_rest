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

    val droppedTables = prev.tables
      .filterNot(t => nextByName.contains(t.name))
      .map(MigrationOp.DropTable.apply)

    val createdTables = topoSort(
      next.tables.filterNot(t => prevByName.contains(t.name))
    ).map(MigrationOp.CreateTable.apply)

    val intraAlters = next.tables.flatMap: n =>
      prevByName.get(n.name).toList.flatMap(p => intraTableAlters(p, n))

    val intraAdds = next.tables.flatMap: n =>
      prevByName.get(n.name).toList.flatMap(p => intraTableAdds(p, n))

    intraDrops ++ droppedTables ++ createdTables ++ intraAlters ++ intraAdds

  def destructive(ops: List[MigrationOp]): List[MigrationOp] =
    ops.filter(_.isDestructive)

  def describeDestructive(op: MigrationOp): String = op match
    case DropTable(t)         => s"drops table '${t.name}'"
    case DropColumn(tbl, col) => s"drops column '$tbl.${col.name}'"
    case _                    => "destructive change"

  def fkName(tableName: String, fk: ForeignKeySpec): String =
    s"fk_${tableName}_${fk.column}"

  def namedChecks(t: TableSpec): List[(String, String)] =
    t.checks.distinct.zipWithIndex.map: (sql, i) =>
      (s"ck_${t.name}_$i", sql)

  private def intraTableDrops(prev: TableSpec, next: TableSpec): List[MigrationOp] =
    val nextColNames = next.columns.map(_.name).toSet
    val droppedCols = prev.columns
      .filterNot(c => nextColNames.contains(c.name))
      .map(c => MigrationOp.DropColumn(prev.name, c))

    val nextIndexNames = next.indexes.map(_.name).toSet
    val droppedIndexes = prev.indexes
      .filterNot(i => nextIndexNames.contains(i.name))
      .map(i => MigrationOp.DropIndex(prev.name, i))

    val nextFkNames =
      next.foreignKeys.map(fk => fkName(next.name, fk)).toSet
    val droppedFks = prev.foreignKeys
      .filterNot(fk => nextFkNames.contains(fkName(prev.name, fk)))
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

    val prevIndexNames = prev.indexes.map(_.name).toSet
    val addedIndexes = next.indexes
      .filterNot(i => prevIndexNames.contains(i.name))
      .map(i => MigrationOp.AddIndex(next.name, i))

    val prevFkNames =
      prev.foreignKeys.map(fk => fkName(prev.name, fk)).toSet
    val addedFks = next.foreignKeys
      .filterNot(fk => prevFkNames.contains(fkName(next.name, fk)))
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

    def visit(t: TableSpec): Unit =
      color.getOrElse(t.name, TopoColor.White) match
        case TopoColor.Black | TopoColor.Gray => ()
        case TopoColor.White =>
          color(t.name) = TopoColor.Gray
          for fk <- t.foreignKeys do
            byName.get(fk.refTable).filter(_.name != t.name).foreach(visit)
          color(t.name) = TopoColor.Black
          result += t

    for t <- tables do visit(t)
    result.toList
