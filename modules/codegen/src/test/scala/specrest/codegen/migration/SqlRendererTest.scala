package specrest.codegen.migration

import munit.CatsEffectSuite
import specrest.codegen.migration.MigrationOp.*
import specrest.convention.ColumnSpec
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec

class SqlRendererTest extends CatsEffectSuite:

  test("CreateTable renders columns, PK, FKs, checks, indexes"):
    val t = TableSpec(
      name = "posts",
      entityName = "Post",
      columns = List(
        ColumnSpec("id", "BIGSERIAL", nullable = false, None),
        ColumnSpec("author_id", "BIGINT", nullable = false, None),
        ColumnSpec("title", "VARCHAR(200)", nullable = false, None)
      ),
      primaryKey = "id",
      foreignKeys = List(ForeignKeySpec("author_id", "users", "id", "CASCADE")),
      checks = List("length(title) > 0"),
      indexes = List(IndexSpec("ix_posts_author", List("author_id"), unique = false))
    )
    val out = SqlRenderer.upgrade(List(CreateTable(t))).mkString("\n")
    assert(out.contains("CREATE TABLE posts ("), out)
    assert(out.contains("id BIGSERIAL NOT NULL"), out)
    assert(out.contains("author_id BIGINT NOT NULL"), out)
    assert(out.contains("CONSTRAINT pk_posts PRIMARY KEY (id)"), out)
    assert(
      out.contains(
        "CONSTRAINT fk_posts_author_id FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE"
      ),
      out
    )
    assert(out.contains("CONSTRAINT ck_posts_0 CHECK (length(title) > 0)"), out)
    assert(out.contains("CREATE INDEX ix_posts_author ON posts (author_id);"), out)

  test("AddColumn renders ALTER TABLE ADD COLUMN with default"):
    val col = ColumnSpec("created_at", "TIMESTAMPTZ", nullable = false, Some("NOW()"))
    assertEquals(
      SqlRenderer.upgrade(List(AddColumn("posts", col))),
      List("ALTER TABLE posts ADD COLUMN created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL;")
    )

  test("DropColumn renders ALTER TABLE DROP COLUMN"):
    val col = ColumnSpec("legacy", "TEXT", nullable = true, None)
    assertEquals(
      SqlRenderer.upgrade(List(DropColumn("posts", col))),
      List("ALTER TABLE posts DROP COLUMN legacy;")
    )

  test("AlterColumnType strips BIGSERIAL to BIGINT"):
    assertEquals(
      SqlRenderer.upgrade(List(AlterColumnType("posts", "id", "INTEGER", "BIGSERIAL"))),
      List("ALTER TABLE posts ALTER COLUMN id TYPE BIGINT;")
    )

  test("AlterColumnNullable emits SET/DROP NOT NULL"):
    val toNullable = SqlRenderer.upgrade(
      List(AlterColumnNullable("posts", "title", oldNullable = false, newNullable = true))
    )
    val toRequired = SqlRenderer.upgrade(
      List(AlterColumnNullable("posts", "title", oldNullable = true, newNullable = false))
    )
    assertEquals(toNullable, List("ALTER TABLE posts ALTER COLUMN title DROP NOT NULL;"))
    assertEquals(toRequired, List("ALTER TABLE posts ALTER COLUMN title SET NOT NULL;"))

  test("AlterColumnDefault emits SET/DROP DEFAULT"):
    val setDef =
      SqlRenderer.upgrade(List(AlterColumnDefault("posts", "x", None, Some("0"))))
    val dropDef =
      SqlRenderer.upgrade(List(AlterColumnDefault("posts", "x", Some("0"), None)))
    assertEquals(setDef, List("ALTER TABLE posts ALTER COLUMN x SET DEFAULT 0;"))
    assertEquals(dropDef, List("ALTER TABLE posts ALTER COLUMN x DROP DEFAULT;"))

  test("AddCheck / DropCheck render ADD/DROP CONSTRAINT"):
    assertEquals(
      SqlRenderer.upgrade(List(AddCheck("posts", "ck_posts_x", "x > 0"))),
      List("ALTER TABLE posts ADD CONSTRAINT ck_posts_x CHECK (x > 0);")
    )
    assertEquals(
      SqlRenderer.upgrade(List(DropCheck("posts", "ck_posts_x", "x > 0"))),
      List("ALTER TABLE posts DROP CONSTRAINT ck_posts_x;")
    )

  test("AddForeignKey / DropForeignKey render proper SQL"):
    val fk   = ForeignKeySpec("author_id", "users", "id", "CASCADE")
    val add  = SqlRenderer.upgrade(List(AddForeignKey("posts", fk)))
    val drop = SqlRenderer.upgrade(List(DropForeignKey("posts", fk)))
    assertEquals(
      add,
      List(
        "ALTER TABLE posts ADD CONSTRAINT fk_posts_author_id FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE;"
      )
    )
    assertEquals(drop, List("ALTER TABLE posts DROP CONSTRAINT fk_posts_author_id;"))

  test("AddIndex / DropIndex render proper SQL"):
    val ix = IndexSpec("ix_posts_title", List("title"), unique = true)
    assertEquals(
      SqlRenderer.upgrade(List(AddIndex("posts", ix))),
      List("CREATE UNIQUE INDEX ix_posts_title ON posts (title);")
    )
    assertEquals(
      SqlRenderer.upgrade(List(DropIndex("posts", ix))),
      List("DROP INDEX ix_posts_title;")
    )

  test("AddIndex with filterClause renders WHERE suffix"):
    val ix = IndexSpec(
      "ix_products_active",
      List("active"),
      unique = false,
      filterClause = Some("active = true")
    )
    assertEquals(
      SqlRenderer.upgrade(List(AddIndex("products", ix))),
      List("CREATE INDEX ix_products_active ON products (active) WHERE active = true;")
    )

  test("AddTrigger emits CREATE FUNCTION + CREATE TRIGGER as plain SQL"):
    val t = specrest.convention.TriggerSpec(
      name = "trg_recalc_order_subtotal",
      functionName = "recalc_order_subtotal",
      targetTable = "orders",
      targetColumn = "subtotal",
      sourceTable = "line_items",
      sourceForeignKey = "order_id",
      aggregate = specrest.convention.TriggerAggregate.Sum,
      sourceColumn = Some("line_total")
    )
    val out = SqlRenderer.upgrade(List(AddTrigger(t))).mkString("\n")
    assert(out.contains("CREATE OR REPLACE FUNCTION recalc_order_subtotal()"), out)
    assert(out.contains("$$ LANGUAGE plpgsql;"), out)
    assert(out.contains("COALESCE(SUM(line_total), 0)"), out)
    assert(out.contains("CREATE TRIGGER trg_recalc_order_subtotal"), out)

  test("DropTrigger emits DROP TRIGGER + DROP FUNCTION"):
    val t = specrest.convention.TriggerSpec(
      name = "trg_x",
      functionName = "fn_x",
      targetTable = "p",
      targetColumn = "c",
      sourceTable = "child",
      sourceForeignKey = "p_id",
      aggregate = specrest.convention.TriggerAggregate.Count,
      sourceColumn = None
    )
    assertEquals(
      SqlRenderer.upgrade(List(DropTrigger(t))),
      List(
        "DROP TRIGGER IF EXISTS trg_x ON child;",
        "DROP FUNCTION IF EXISTS fn_x();"
      )
    )

  test("downgrade reverses the op list and inverts each op"):
    val ops = List(
      AddColumn("t", ColumnSpec("c", "TEXT", false, None)),
      AddCheck("t", "ck_t_0", "x > 0")
    )
    val down = SqlRenderer.downgrade(ops)
    assertEquals(
      down,
      List(
        "ALTER TABLE t DROP CONSTRAINT ck_t_0;",
        "ALTER TABLE t DROP COLUMN c;"
      )
    )
