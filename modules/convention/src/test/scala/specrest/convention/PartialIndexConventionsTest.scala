package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

object PartialIndexConventionsTest:
  private def rule(
      target: String,
      prop: String,
      col: Option[String],
      v: expr_full
  ): ConventionRuleFull =
    ConventionRuleFull(target, prop, col, v, None)
  private def stringL(s: String): StringLitF = StringLitF(s, None)
  private def intL(n: Int): IntLitF          = IntLitF(int_of_integer(BigInt(n)), None)
  private def conv(rs: List[convention_rule_full]): conventions_decl_full =
    ConventionsDeclFull(rs, None)

  // Minimal TableSpec for index-append tests.
  private def table(name: String, indexes: List[index_spec] = Nil): TableSpec =
    TableSpec(name, name + "_entity", Nil, "id", Nil, Nil, indexes)

class PartialIndexConventionsTest extends CatsEffectSuite:

  import PartialIndexConventionsTest.*

  // -- extractPartialIndexRules --

  test("None convention block → empty rules"):
    assertEquals(extractPartialIndexRules(None), Nil)

  test("empty rule list → empty"):
    assertEquals(extractPartialIndexRules(Some(conv(Nil))), Nil)

  test("non-partial_index rules are skipped"):
    val rs = List(
      rule("Order", "db_table", None, stringL("orders")),
      rule("User", "http_path", None, stringL("/users"))
    )
    assertEquals(extractPartialIndexRules(Some(conv(rs))), Nil)

  test("partial_index with String filter is captured"):
    val rs = List(rule("Order", "partial_index", Some("status"), stringL("status = 'active'")))
    assertEquals(
      extractPartialIndexRules(Some(conv(rs))),
      List(("Order", ("status", "status = 'active'")))
    )

  test("partial_index without column is dropped"):
    val rs = List(rule("Order", "partial_index", None, stringL("status = 'active'")))
    assertEquals(extractPartialIndexRules(Some(conv(rs))), Nil)

  test("partial_index with non-String filter is dropped"):
    val rs = List(rule("Order", "partial_index", Some("status"), intL(1)))
    assertEquals(extractPartialIndexRules(Some(conv(rs))), Nil)

  test("multiple partial_index rules preserved in order, interleaved with others"):
    val rs = List(
      rule("Order", "partial_index", Some("status"), stringL("p1")),
      rule("Order", "db_table", None, stringL("orders")),
      rule("Order", "partial_index", Some("priority"), stringL("p2")),
      rule("User", "partial_index", Some("active"), stringL("p3"))
    )
    assertEquals(
      extractPartialIndexRules(Some(conv(rs))),
      List(
        ("Order", ("status", "p1")),
        ("Order", ("priority", "p2")),
        ("User", ("active", "p3"))
      )
    )

  // -- appendPartialIndexes --

  test("empty colFilters leaves the table indexes unchanged"):
    val t = table("orders")
    assertEquals(appendPartialIndexes(t, Nil), t)

  test("one (col, filter) appends one IndexSpec with canonical name"):
    val t      = table("orders")
    val result = appendPartialIndexes(t, List(("status", "status = 'open'")))
    val expected =
      IndexSpec("idx_orders_status_partial", List("status"), false, Some("status = 'open'"))
    assertEquals(tableIndexes(result), List(expected))

  test("multiple (col, filter) entries append multiple indexes in order"):
    val t      = table("orders")
    val result = appendPartialIndexes(t, List(("status", "f1"), ("priority", "f2")))
    assertEquals(
      tableIndexes(result),
      List(
        IndexSpec("idx_orders_status_partial", List("status"), false, Some("f1")),
        IndexSpec("idx_orders_priority_partial", List("priority"), false, Some("f2"))
      )
    )

  test("existing indexes preserved with partials appended after"):
    val existing = IndexSpec("idx_orders_id", List("id"), true, None)
    val t        = table("orders", indexes = List(existing))
    val result   = appendPartialIndexes(t, List(("status", "f")))
    assertEquals(
      tableIndexes(result),
      List(
        existing,
        IndexSpec("idx_orders_status_partial", List("status"), false, Some("f"))
      )
    )

  test("appendPartialIndexes preserves entity/PK/FK/checks fields"):
    val t      = table("orders")
    val result = appendPartialIndexes(t, List(("status", "f")))
    assertEquals(tableName(result), tableName(t))
    assertEquals(tableEntityName(result), tableEntityName(t))
    assertEquals(tablePrimaryKey(result), tablePrimaryKey(t))
    assertEquals(tableColumns(result), tableColumns(t))
    assertEquals(tableForeignKeys(result), tableForeignKeys(t))
    assertEquals(tableChecks(result), tableChecks(t))
