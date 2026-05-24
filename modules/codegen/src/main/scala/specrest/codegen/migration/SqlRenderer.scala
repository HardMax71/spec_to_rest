package specrest.codegen.migration

import specrest.ir.generated.SpecRestGenerated.*

object SqlRenderer:

  def upgrade(ops: List[migration_op], dialect: Dialect = Postgres): List[String] =
    ops.flatMap(op => Renderers.render(op, dialect).sql())

  def downgrade(ops: List[migration_op], dialect: Dialect = Postgres): List[String] =
    ops.reverse.flatMap(op => Renderers.render(inverse_op(op), dialect).sql())
