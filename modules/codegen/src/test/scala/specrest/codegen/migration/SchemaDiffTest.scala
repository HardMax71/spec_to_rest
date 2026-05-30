package specrest.codegen.migration

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class SchemaDiffTest extends CatsEffectSuite:

  private given CanEqual[migration_op, migration_op]             = CanEqual.derived
  private given CanEqual[List[migration_op], List[migration_op]] = CanEqual.derived

  private def table(
      name: String,
      cols: List[column_spec] = List(ColumnSpec("id", "BIGSERIAL", false, None)),
      fks: List[foreign_key_spec] = Nil,
      checks: List[String] = Nil,
      indexes: List[index_spec] = Nil
  ): table_spec =
    TableSpec(
      name,
      name.capitalize,
      cols,
      "id",
      fks,
      checks,
      indexes
    )

  test("identical schemas produce empty diff"):
    val schema = DatabaseSchema(List(table("users")), Nil)
    assertEquals(SchemaDiff.compute(schema, schema), Nil)

  test("added table produces CreateTable"):
    val before = DatabaseSchema(Nil, Nil)
    val after  = DatabaseSchema(List(table("users")), Nil)
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(ops.size, 1)
    assert(ops.head.isInstanceOf[CreateTable])

  test("removed table produces DropTable"):
    val before = DatabaseSchema(List(table("users")), Nil)
    val after  = DatabaseSchema(Nil, Nil)
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(ops, List(DropTable(table("users"))))

  test("added column produces AddColumn"):
    val cols0  = List(ColumnSpec("id", "BIGSERIAL", false, None))
    val cols1  = cols0 :+ ColumnSpec("email", "TEXT", false, None)
    val before = DatabaseSchema(List(table("users", cols = cols0)), Nil)
    val after  = DatabaseSchema(List(table("users", cols = cols1)), Nil)
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(
      ops,
      List(AddColumn("users", ColumnSpec("email", "TEXT", false, None)))
    )

  test("removed column produces DropColumn"):
    val cols0 = List(
      ColumnSpec("id", "BIGSERIAL", false, None),
      ColumnSpec("email", "TEXT", false, None)
    )
    val cols1  = cols0.take(1)
    val before = DatabaseSchema(List(table("users", cols = cols0)), Nil)
    val after  = DatabaseSchema(List(table("users", cols = cols1)), Nil)
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(
      ops,
      List(DropColumn("users", ColumnSpec("email", "TEXT", false, None)))
    )

  test("type change produces AlterColumnType"):
    val cols0  = List(ColumnSpec("email", "TEXT", false, None))
    val cols1  = List(ColumnSpec("email", "VARCHAR(255)", false, None))
    val before = DatabaseSchema(List(table("users", cols = cols0)), Nil)
    val after  = DatabaseSchema(List(table("users", cols = cols1)), Nil)
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(ops, List(AlterColumnType("users", "email", "TEXT", "VARCHAR(255)")))

  // Adding or removing an explicit `id: Int` PK toggles between BIGINT (application-supplied,
  // see Schema.widenExplicitIdPkSqlType) and BIGSERIAL (synthesized). The transition cannot be
  // expressed as an in-place ALTER on any supported dialect — SQLite has no answer at all — and
  // a silent rewrite would orphan FKs that target the PK. So the diff layer rejects it loud and
  // requires the user to perform a manual data-preserving migration.
  private def autoIncRejectionCases: List[(String, String)] = List(
    "BIGINT"    -> "BIGSERIAL",
    "BIGSERIAL" -> "BIGINT",
    "INTEGER"   -> "SERIAL",
    "SERIAL"    -> "INTEGER",
    "BIGINT"    -> "SERIAL",
    "BIGSERIAL" -> "INTEGER"
  )

  autoIncRejectionCases.foreach: (oldT, newT) =>
    test(s"AlterColumnType from $oldT to $newT is rejected at the diff layer"):
      val before = DatabaseSchema(
        List(table("users", cols = List(ColumnSpec("id", oldT, false, None)))),
        Nil
      )
      val after = DatabaseSchema(
        List(table("users", cols = List(ColumnSpec("id", newT, false, None)))),
        Nil
      )
      val ex = intercept[RuntimeException](SchemaDiff.compute(before, after))
      assert(ex.getMessage.contains("auto-increment identity supply"), ex.getMessage)
      assert(ex.getMessage.contains("users.id"), ex.getMessage)

  test("nullability change produces AlterColumnNullable"):
    val cols0 = List(ColumnSpec("email", "TEXT", false, None))
    val cols1 = List(ColumnSpec("email", "TEXT", true, None))
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("users", cols = cols0)), Nil),
      DatabaseSchema(List(table("users", cols = cols1)), Nil)
    )
    assertEquals(ops, List(AlterColumnNullable("users", "email", false, true)))

  test("default change produces AlterColumnDefault"):
    val cols0 = List(ColumnSpec("count", "INTEGER", false, None))
    val cols1 = List(ColumnSpec("count", "INTEGER", false, Some("0")))
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("counters", cols = cols0)), Nil),
      DatabaseSchema(List(table("counters", cols = cols1)), Nil)
    )
    assertEquals(ops, List(AlterColumnDefault("counters", "count", None, Some("0"))))

  test("added CHECK produces AddCheck"):
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("users")), Nil),
      DatabaseSchema(List(table("users", checks = List("length(name) > 0"))), Nil)
    )
    assertEquals(ops, List(AddCheck("users", "ck_users_0", "length(name) > 0")))

  test("added foreign key produces AddForeignKey"):
    val fk = ForeignKeySpec("user_id", "users", "id", "CASCADE")
    val ops = SchemaDiff.compute(
      DatabaseSchema(
        List(
          table("users"),
          table("posts", cols = List(ColumnSpec("user_id", "BIGINT", false, None)))
        ),
        Nil
      ),
      DatabaseSchema(
        List(
          table("users"),
          table("posts", cols = List(ColumnSpec("user_id", "BIGINT", false, None)), fks = List(fk))
        ),
        Nil
      )
    )
    assertEquals(ops, List(AddForeignKey("posts", fk)))

  test("added index produces AddIndex"):
    val ix   = IndexSpec("ix_users_email", List("email"), true, None)
    val cols = List(ColumnSpec("email", "TEXT", false, None))
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("users", cols = cols)), Nil),
      DatabaseSchema(List(table("users", cols = cols, indexes = List(ix))), Nil)
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
      DatabaseSchema(Nil, Nil),
      DatabaseSchema(List(posts, users), Nil)
    )
    val names = ops.collect { case CreateTable(t) => tableName(t) }
    assertEquals(names, List("users", "posts"))

  test("destructive() returns only DropTable / DropColumn ops"):
    val ops = List[migration_op](
      DropTable(table("a")),
      AddColumn("b", ColumnSpec("c", "TEXT", false, None)),
      DropColumn("b", ColumnSpec("d", "TEXT", false, None)),
      AddIndex("b", IndexSpec("ix", List("c"), false, None))
    )
    assertEquals(SchemaDiff.destructive(ops).size, 2)

  test("topoSort throws on FK cycle"):
    val a = TableSpec(
      "a",
      "A",
      List(ColumnSpec("id", "BIGSERIAL", false, None)),
      "id",
      List(ForeignKeySpec("b_id", "b", "id", "CASCADE")),
      Nil,
      Nil
    )
    val b = TableSpec(
      "b",
      "B",
      List(ColumnSpec("id", "BIGSERIAL", false, None)),
      "id",
      List(ForeignKeySpec("a_id", "a", "id", "CASCADE")),
      Nil,
      Nil
    )
    intercept[RuntimeException](SchemaDiff.topoSort(List(a, b)))

  test("FK target change with stable column name produces Drop+Add ops"):
    val cols   = List(ColumnSpec("user_id", "BIGINT", false, None))
    val oldFk  = ForeignKeySpec("user_id", "users_old", "id", "CASCADE")
    val newFk  = ForeignKeySpec("user_id", "users_new", "id", "CASCADE")
    val before = DatabaseSchema(List(table("posts", cols = cols, fks = List(oldFk))), Nil)
    val after  = DatabaseSchema(List(table("posts", cols = cols, fks = List(newFk))), Nil)
    val ops    = SchemaDiff.compute(before, after)
    assertEquals(
      ops,
      List(DropForeignKey("posts", oldFk), AddForeignKey("posts", newFk))
    )

  test("Index uniqueness flip with same name produces Drop+Add ops"):
    val cols  = List(ColumnSpec("email", "TEXT", false, None))
    val oldIx = IndexSpec("ix_users_email", List("email"), false, None)
    val newIx = IndexSpec("ix_users_email", List("email"), true, None)
    val ops = SchemaDiff.compute(
      DatabaseSchema(List(table("users", cols = cols, indexes = List(oldIx))), Nil),
      DatabaseSchema(List(table("users", cols = cols, indexes = List(newIx))), Nil)
    )
    assertEquals(ops, List(DropIndex("users", oldIx), AddIndex("users", newIx)))

  test("DropTable ops are reverse-topologically sorted (child before parent)"):
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
      DatabaseSchema(List(users, posts), Nil),
      DatabaseSchema(Nil, Nil)
    )
    val names = ops.collect { case DropTable(t) => tableName(t) }
    assertEquals(names, List("posts", "users"))

  test("inverse of inverse is identity"):
    val ops = List[migration_op](
      CreateTable(table("a")),
      DropTable(table("b")),
      AddColumn("t", ColumnSpec("c", "TEXT", false, None)),
      AlterColumnType("t", "c", "TEXT", "VARCHAR(10)"),
      AlterColumnNullable("t", "c", false, true),
      AddCheck("t", "ck_t_0", "x > 0"),
      AddForeignKey("t", ForeignKeySpec("c", "u", "id", "CASCADE")),
      AddIndex("t", IndexSpec("ix", List("c"), false, None))
    )
    ops.foreach: op =>
      assertEquals(inverseOp(inverseOp(op)), op)
