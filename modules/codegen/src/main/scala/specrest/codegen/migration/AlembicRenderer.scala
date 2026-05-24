package specrest.codegen.migration

import specrest.ir.generated.SpecRestGenerated.*

object AlembicRenderer:

  def upgrade(ops: List[migration_op], dialect: Dialect = Postgres): List[String] =
    ops.flatMap(op => Renderers.render(op, dialect).alembic())

  def downgrade(ops: List[migration_op], dialect: Dialect = Postgres): List[String] =
    ops.reverse.flatMap(op => Renderers.render(inverse_op(op), dialect).alembic())
