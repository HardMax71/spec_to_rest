package specrest.codegen.migration

object AlembicRenderer:

  def upgrade(ops: List[MigrationOp]): List[String] =
    ops.flatMap(op => Renderers.render(op).alembic)

  def downgrade(ops: List[MigrationOp]): List[String] =
    ops.reverse.flatMap(op => Renderers.render(op.inverse).alembic)
