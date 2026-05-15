package specrest.codegen.migration

import specrest.convention.ColumnSpec
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec
import specrest.convention.TriggerSpec

enum MigrationOp derives CanEqual:
  case CreateTable(table: TableSpec)
  case DropTable(oldTable: TableSpec)
  case AddColumn(table: String, column: ColumnSpec)
  case DropColumn(table: String, oldColumn: ColumnSpec)
  case AlterColumnType(table: String, name: String, oldSqlType: String, newSqlType: String)
  case AlterColumnNullable(table: String, name: String, oldNullable: Boolean, newNullable: Boolean)
  case AlterColumnDefault(
      table: String,
      name: String,
      oldDefault: Option[String],
      newDefault: Option[String]
  )
  case AddCheck(table: String, name: String, sql: String)
  case DropCheck(table: String, name: String, oldSql: String)
  case AddForeignKey(table: String, fk: ForeignKeySpec)
  case DropForeignKey(table: String, oldFk: ForeignKeySpec)
  case AddIndex(table: String, index: IndexSpec)
  case DropIndex(table: String, oldIndex: IndexSpec)
  case AddTrigger(trigger: TriggerSpec)
  case DropTrigger(oldTrigger: TriggerSpec)

  def inverse: MigrationOp = this match
    case CreateTable(t)                    => DropTable(t)
    case DropTable(t)                      => CreateTable(t)
    case AddColumn(tbl, c)                 => DropColumn(tbl, c)
    case DropColumn(tbl, c)                => AddColumn(tbl, c)
    case AlterColumnType(tbl, n, o, x)     => AlterColumnType(tbl, n, x, o)
    case AlterColumnNullable(tbl, n, o, x) => AlterColumnNullable(tbl, n, x, o)
    case AlterColumnDefault(tbl, n, o, x)  => AlterColumnDefault(tbl, n, x, o)
    case AddCheck(tbl, n, sql)             => DropCheck(tbl, n, sql)
    case DropCheck(tbl, n, sql)            => AddCheck(tbl, n, sql)
    case AddForeignKey(tbl, fk)            => DropForeignKey(tbl, fk)
    case DropForeignKey(tbl, fk)           => AddForeignKey(tbl, fk)
    case AddIndex(tbl, ix)                 => DropIndex(tbl, ix)
    case DropIndex(tbl, ix)                => AddIndex(tbl, ix)
    case AddTrigger(t)                     => DropTrigger(t)
    case DropTrigger(t)                    => AddTrigger(t)

  def isDestructive: Boolean = this match
    case _: (DropTable | DropColumn) => true
    case _                           => false

object MigrationOp:
  def hasPostgresDialectTypes(ops: List[MigrationOp], dialect: Dialect = Postgres): Boolean =
    def needsDialectImport(sqlType: String): Boolean =
      CanonicalType.parse(sqlType).exists(dialect.saType(_).importModule.isDefined)
    ops.exists:
      case CreateTable(t)   => t.columns.exists(c => needsDialectImport(c.sqlType))
      case DropTable(t)     => t.columns.exists(c => needsDialectImport(c.sqlType))
      case AddColumn(_, c)  => needsDialectImport(c.sqlType)
      case DropColumn(_, c) => needsDialectImport(c.sqlType)
      case AlterColumnType(_, _, o, n) =>
        needsDialectImport(o) || needsDialectImport(n)
      case _ => false
