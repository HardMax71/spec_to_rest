package specrest.codegen.migration

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class AlembicRendererTest extends CatsEffectSuite:

  test("CreateTable produces op.create_table with PK + FKs + checks + indexes"):
    val t = TableSpec(
      "posts",
      "Post",
      List(
        ColumnSpec("id", "BIGSERIAL", false, None),
        ColumnSpec("author_id", "BIGINT", false, None),
        ColumnSpec("title", "VARCHAR(200)", false, None)
      ),
      "id",
      List(ForeignKeySpec("author_id", "users", "id", "CASCADE")),
      List("length(title) > 0"),
      List(IndexSpec("ix_posts_author", List("author_id"), false, None))
    )
    val out = AlembicRenderer.upgrade(List(CreateTable(t))).mkString("\n")
    assert(out.contains("""op.create_table("""), out)
    assert(out.contains("""sa.Column("id", sa.BigInteger(), primary_key=True"""), out)
    assert(out.contains("""sa.ForeignKeyConstraint(["author_id"]"""), out)
    assert(out.contains("""sa.CheckConstraint('length(title) > 0', name="ck_posts_0")"""), out)
    assert(
      out.contains(
        """op.create_index("ix_posts_author", "posts", ["author_id"], unique=False)"""
      ),
      out
    )

  test("AddColumn / DropColumn render op.add_column / op.drop_column"):
    val col = ColumnSpec("created_at", "TIMESTAMPTZ", false, Some("NOW()"))
    assertEquals(
      AlembicRenderer.upgrade(List(AddColumn("posts", col))),
      List(
        """op.add_column("posts", sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False))"""
      )
    )
    assertEquals(
      AlembicRenderer.upgrade(List(DropColumn("posts", col))),
      List("""op.drop_column("posts", "created_at")""")
    )

  test("AlterColumnType / Nullable / Default render op.alter_column"):
    assertEquals(
      AlembicRenderer.upgrade(List(AlterColumnType("t", "x", "TEXT", "VARCHAR(50)"))),
      List("""op.alter_column("t", "x", type_=sa.String(length=50))""")
    )
    assertEquals(
      AlembicRenderer.upgrade(List(AlterColumnNullable("t", "x", false, true))),
      List("""op.alter_column("t", "x", nullable=True)""")
    )
    assertEquals(
      AlembicRenderer.upgrade(List(AlterColumnDefault("t", "x", None, Some("0")))),
      List("""op.alter_column("t", "x", server_default=sa.text('0'))""")
    )
    assertEquals(
      AlembicRenderer.upgrade(List(AlterColumnDefault("t", "x", Some("0"), None))),
      List("""op.alter_column("t", "x", server_default=None)""")
    )

  test("AddCheck / DropCheck render proper Alembic ops"):
    assertEquals(
      AlembicRenderer.upgrade(List(AddCheck("t", "ck_t_0", "x > 0"))),
      List("""op.create_check_constraint("ck_t_0", "t", 'x > 0')""")
    )
    assertEquals(
      AlembicRenderer.upgrade(List(DropCheck("t", "ck_t_0", "x > 0"))),
      List("""op.drop_constraint("ck_t_0", "t", type_="check")""")
    )

  test("AddForeignKey / DropForeignKey render op.create_foreign_key / op.drop_constraint"):
    val fk = ForeignKeySpec("author_id", "users", "id", "CASCADE")
    assertEquals(
      AlembicRenderer.upgrade(List(AddForeignKey("posts", fk))),
      List(
        """op.create_foreign_key("fk_posts_author_id", "posts", "users", ["author_id"], ["id"], ondelete="CASCADE")"""
      )
    )
    assertEquals(
      AlembicRenderer.upgrade(List(DropForeignKey("posts", fk))),
      List("""op.drop_constraint("fk_posts_author_id", "posts", type_="foreignkey")""")
    )

  test("AddIndex / DropIndex render op.create_index / op.drop_index"):
    val ix = IndexSpec("ix_t_x", List("x", "y"), true, None)
    assertEquals(
      AlembicRenderer.upgrade(List(AddIndex("t", ix))),
      List("""op.create_index("ix_t_x", "t", ["x", "y"], unique=True)""")
    )
    assertEquals(
      AlembicRenderer.upgrade(List(DropIndex("t", ix))),
      List("""op.drop_index("ix_t_x", table_name="t")""")
    )

  test("AddIndex with filterClause renders postgresql_where=sa.text(...)"):
    val ix = IndexSpec(
      "ix_products_active",
      List("active"),
      false,
      Some("active = true")
    )
    assertEquals(
      AlembicRenderer.upgrade(List(AddIndex("products", ix))),
      List(
        """op.create_index("ix_products_active", "products", ["active"], unique=False, postgresql_where=sa.text('active = true'))"""
      )
    )

  test("AddTrigger emits CREATE FUNCTION + CREATE TRIGGER via op.execute"):
    val t = TriggerSpec(
      "trg_recalc_order_subtotal",
      "recalc_order_subtotal",
      "orders",
      "subtotal",
      "line_items",
      "order_id",
      SumAgg(),
      Some("line_total")
    )
    val out = AlembicRenderer.upgrade(List(AddTrigger(t))).mkString("\n")
    assert(out.contains("op.execute("), out)
    assert(out.contains("CREATE OR REPLACE FUNCTION recalc_order_subtotal()"), out)
    assert(out.contains("COALESCE(SUM(line_total), 0)"), out)
    assert(out.contains("CREATE TRIGGER trg_recalc_order_subtotal"), out)
    assert(out.contains("AFTER INSERT OR UPDATE OR DELETE ON line_items"), out)

  test("DropTrigger emits DROP TRIGGER + DROP FUNCTION via op.execute"):
    val t = TriggerSpec(
      "trg_x",
      "fn_x",
      "p",
      "c",
      "child",
      "p_id",
      CountAgg(),
      None
    )
    assertEquals(
      AlembicRenderer.upgrade(List(DropTrigger(t))),
      List(
        """op.execute("DROP TRIGGER IF EXISTS trg_x ON child;")""",
        """op.execute("DROP FUNCTION IF EXISTS fn_x();")"""
      )
    )

  test("downgrade reverses + inverts the op list"):
    val ops = List[migration_op](
      AddColumn("t", ColumnSpec("c", "TEXT", false, None)),
      AddCheck("t", "ck_t_0", "x > 0")
    )
    val down = AlembicRenderer.downgrade(ops)
    assertEquals(
      down,
      List(
        """op.drop_constraint("ck_t_0", "t", type_="check")""",
        """op.drop_column("t", "c")"""
      )
    )
