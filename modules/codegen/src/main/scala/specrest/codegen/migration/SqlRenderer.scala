package specrest.codegen.migration

object SqlRenderer:

  def upgrade(ops: List[MigrationOp]): List[String] =
    ops.flatMap(op => Renderers.render(op).sql())

  def downgrade(ops: List[MigrationOp]): List[String] =
    ops.reverse.flatMap(op => Renderers.render(op.inverse).sql())
