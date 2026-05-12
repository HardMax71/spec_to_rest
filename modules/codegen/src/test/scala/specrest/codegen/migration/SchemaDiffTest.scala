package specrest.codegen.migration

import munit.CatsEffectSuite
import specrest.codegen.migration.MigrationOp.*
import specrest.convention.ColumnSpec
import specrest.convention.DatabaseSchema
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec

class SchemaDiffTest extends CatsEffectSuite:

  private def table(
      name: String,
      cols: List[ColumnSpec] = List(ColumnSpec("id", "BIGSERIAL", nullable = false, None)),
      fks: List[ForeignKeySpec] = Nil,
      checks: List[String] = Nil,
      indexes: List[IndexSpec] = Nil
  ): TableSpec =
    TableSpec(
      name = name,
      entityName = name.capitalize,
      columns = cols,
      primaryKey = "id",
      foreignKeys = fks,
      checks = checks,
      indexes = indexes
    )

  test("identical schemas produce empty diff"):
    val schema = DatabaseSchema(List(table("users")))
    assertEquals(SchemaDiff.compute(schema, schema), Nil)

  test("added table produces CreateTable"):
    val before = DatabaseSchema(Nil)
    val after  = DatabaseSchema(List(table("users")))
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(ops.size, 1)
    assert(ops.head.isInstanceOf[CreateTable])

  test("removed table produces DropTable"):
    val before = DatabaseSchema(List(table("users")))
    val after  = DatabaseSchema(Nil)
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(ops, List(DropTable(table("users"))))

  test("added column produces AddColumn"):
    val cols0  = List(ColumnSpec("id", "BIGSERIAL", nullable = false, None))
    val cols1  = cols0 :+ ColumnSpec("email", "TEXT", nullable = false, None)
    val before = DatabaseSchema(List(table("users", cols = cols0)))
    val after  = DatabaseSchema(List(table("users", cols = cols1)))
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(
      ops,
      List(AddColumn("users", ColumnSpec("email", "TEXT", nullable = false, None)))
    )

  test("removed column produces DropColumn"):
    val cols0 = List(
      ColumnSpec("id", "BIGSERIAL", nullable = false, None),
      ColumnSpec("email", "TEXT", nullable = false, None)
    )
    val cols1  = cols0.take(1)
    val before = DatabaseSchema(List(table("users", cols = cols0)))
    val after  = DatabaseSchema(List(table("users", cols = cols1)))
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(
      ops,
      List(DropColumn("users", ColumnSpec("email", "TEXT", nullable = false, None)))
    )

  test("type change produces AlterColumnType"):
    val cols0  = List(ColumnSpec("email", "TEXT", nullable = false, None))
    val cols1  = List(ColumnSpec("email", "VARCHAR(255)", nullable = false, None))
    val before = DatabaseSchema(List(table("users", cols = cols0)))
    val after  = DatabaseSchema(List(table("users", cols = cols1)))
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(ops, List(AlterColumnType("users", "email", "TEXT", "VARCHAR(255)")))

  test("nullability change produces AlterColumnNullable"):
    val cols0 = List(ColumnSpec("email", "TEXT", nullable = false, None))
    val cols1 = List(ColumnSpec("email", "TEXT", nullable = true, None))
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("users", cols = cols0))),
      DatabaseSchema(List(table("users", cols = cols1)))
    )
    assertEquals(ops, List(AlterColumnNullable("users", "email", false, true)))

  test("default change produces AlterColumnDefault"):
    val cols0 = List(ColumnSpec("count", "INTEGER", nullable = false, None))
    val cols1 = List(ColumnSpec("count", "INTEGER", nullable = false, Some("0")))
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("counters", cols = cols0))),
      DatabaseSchema(List(table("counters", cols = cols1)))
    )
    assertEquals(ops, List(AlterColumnDefault("counters", "count", None, Some("0"))))

  test("added CHECK produces AddCheck"):
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("users"))),
      DatabaseSchema(List(table("users", checks = List("length(name) > 0"))))
    )
    assertEquals(ops, List(AddCheck("users", "ck_users_0", "length(name) > 0")))

  test("added foreign key produces AddForeignKey"):
    val fk = ForeignKeySpec("user_id", "users", "id", "CASCADE")
    val ops = SchemaDiff.compute(
      DatabaseSchema(
        List(
          table("users"),
          table("posts", cols = List(ColumnSpec("user_id", "BIGINT", false, None)))
        )
      ),
      DatabaseSchema(
        List(
          table("users"),
          table("posts", cols = List(ColumnSpec("user_id", "BIGINT", false, None)), fks = List(fk))
        )
      )
    )
    assertEquals(ops, List(AddForeignKey("posts", fk)))

  test("added index produces AddIndex"):
    val ix   = IndexSpec("ix_users_email", List("email"), unique = true)
    val cols = List(ColumnSpec("email", "TEXT", false, None))
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("users", cols = cols))),
      DatabaseSchema(List(table("users", cols = cols, indexes = List(ix))))
    )
    assertEquals(ops, List(AddIndex("users", ix)))

  test("CreateTable ops are topologically sorted by FK dependency"):
    val users =
      table("users", cols = List(ColumnSpec("id", "BIGSERIAL", false, None)))
    val posts = table(
      "posts",
      cols = List(
        ColumnSpec("id", "BIGSERIAL", false, None),
        ColumnSpec("author_id", "BIGINT", false, None)
      ),
      fks = List(ForeignKeySpec("author_id", "users", "id", "CASCADE"))
    )
    val ops = SchemaDiff.compute(
      DatabaseSchema(Nil),
      DatabaseSchema(List(posts, users))
    )
    val names = ops.collect { case CreateTable(t) => t.name }
    assertEquals(names, List("users", "posts"))

  test("destructive() returns only DropTable / DropColumn ops"):
    val ops = List(
      DropTable(table("a")),
      AddColumn("b", ColumnSpec("c", "TEXT", false, None)),
      DropColumn("b", ColumnSpec("d", "TEXT", false, None)),
      AddIndex("b", IndexSpec("ix", List("c"), unique = false))
    )
    assertEquals(SchemaDiff.destructive(ops).size, 2)

  test("inverse of inverse is identity"):
    val ops = List(
      CreateTable(table("a")),
      DropTable(table("b")),
      AddColumn("t", ColumnSpec("c", "TEXT", false, None)),
      AlterColumnType("t", "c", "TEXT", "VARCHAR(10)"),
      AlterColumnNullable("t", "c", false, true),
      AddCheck("t", "ck_t_0", "x > 0"),
      AddForeignKey("t", ForeignKeySpec("c", "u", "id", "CASCADE")),
      AddIndex("t", IndexSpec("ix", List("c"), unique = false))
    )
    ops.foreach: op =>
      assertEquals(op.inverse.inverse, op)
