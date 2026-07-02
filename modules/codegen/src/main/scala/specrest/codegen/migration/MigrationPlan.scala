package specrest.codegen.migration

import specrest.ir.generated.SpecRestGenerated.database_schema
import specrest.ir.generated.SpecRestGenerated.migration_op

enum MigrationPlan derives CanEqual:
  case Initial
  case Delta(ops: List[migration_op], nextRev: String)
  case UpToDate

object MigrationPlan:
  def of(
      previous: Option[database_schema],
      existingRevisions: List[String],
      current: database_schema
  ): MigrationPlan =
    previous match
      case None                                 => Initial
      case Some(_) if existingRevisions.isEmpty => Initial
      case Some(prev) =>
        val ops = SchemaDiff.compute(prev, current)
        if ops.isEmpty then UpToDate
        else Delta(ops, Revision.next(existingRevisions))
