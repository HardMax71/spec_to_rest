package specrest.codegen.migration

import specrest.ir.generated.SpecRestGenerated.*

object AlembicRenderer:

  def upgrade(ops: List[migration_op], dialect: Dialect = Postgres): List[String] =
    ops.flatMap(op => Renderers.render(op, dialect).alembic())

  def downgrade(ops: List[migration_op], dialect: Dialect = Postgres): List[String] =
    downList(ops).flatMap(op => Renderers.render(op, dialect).alembic())
