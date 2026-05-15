package specrest.codegen.migration

object AlembicRenderer:

  def upgrade(ops: List[MigrationOp], dialect: Dialect = Postgres): List[String] =
    ops.flatMap(op => Renderers.render(op, dialect).alembic())

  def downgrade(ops: List[MigrationOp], dialect: Dialect = Postgres): List[String] =
    ops.reverse.flatMap(op => Renderers.render(op.inverse, dialect).alembic())
