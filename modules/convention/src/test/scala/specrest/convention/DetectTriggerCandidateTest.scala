package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class DetectTriggerCandidateTest extends CatsEffectSuite:

  private def named(t: String): NamedTypeF = NamedTypeF(t, None)

  private def fieldD(name: String, ty: type_expr_full): FieldDeclFull =
    FieldDeclFull(name, ty, None, None)

  private def entityD(
      name: String,
      fields: List[field_decl_full]
  ): EntityDeclFull =
    EntityDeclFull(name, None, fields, Nil, None)

  private def col(name: String, sqlType: String): ColumnSpec =
    ColumnSpec(name, sqlType, false, None)

  private def fk(col: String, refTable: String): ForeignKeySpec =
    ForeignKeySpec(col, refTable, "id", "CASCADE")

  private def table(
      name: String,
      entity: String,
      cols: List[column_spec],
      fks: List[foreign_key_spec] = Nil
  ): TableSpec =
    TableSpec(name, entity, cols, "id", fks, Nil, Nil)

  private val parent = entityD(
    "Order",
    fields = List(fieldD("total", named("Int")), fieldD("items", SetTypeF(named("Item"), None)))
  )

  private val child = entityD(
    "Item",
    fields = List(fieldD("order_id", named("Int")), fieldD("amount", named("Int")))
  )

  private val orderTable =
    table("orders", "Order", List(col("id", "BIGSERIAL"), col("total", "INTEGER")))
  private val itemTable = table(
    "items",
    "Item",
    List(col("id", "BIGSERIAL"), col("order_id", "BIGINT"), col("amount", "INTEGER")),
    fks = List(fk("order_id", "orders"))
  )

  private val parentFields = parent.c.collect { case f: FieldDeclFull => f }

  test("happy path: parent has target + child has unique back-FK + source field exists → Some"):
    val result = validateTrigger(
      orderTable,
      parentFields,
      itemTable,
      child,
      "total",
      SumAgg(),
      Some("amount")
    )
    assertEquals(
      result,
      Some(TriggerCandidate("orders", "total", "items", "order_id", SumAgg(), Some("amount")))
    )

  test("happy path with no source field (COUNT-style)"):
    val result = validateTrigger(
      orderTable,
      parentFields,
      itemTable,
      child,
      "total",
      CountAgg(),
      None
    )
    assertEquals(
      result,
      Some(TriggerCandidate("orders", "total", "items", "order_id", CountAgg(), None))
    )

  test("returns None if target field doesn't exist on parent"):
    val noTarget = List(fieldD("items", SetTypeF(named("Item"), None)))
    val result = validateTrigger(
      orderTable,
      noTarget,
      itemTable,
      child,
      "total",
      SumAgg(),
      Some("amount")
    )
    assertEquals(result, None)

  test("returns None if child has multiple FKs to parent (ambiguous)"):
    val multiFkChild = table(
      "items",
      "Item",
      List(col("id", "BIGSERIAL"), col("order_id", "BIGINT"), col("alt_order_id", "BIGINT")),
      fks = List(fk("order_id", "orders"), fk("alt_order_id", "orders"))
    )
    val result = validateTrigger(
      orderTable,
      parentFields,
      multiFkChild,
      child,
      "total",
      SumAgg(),
      Some("amount")
    )
    assertEquals(result, None)

  test("returns None if child has no FK to parent"):
    val noFkChild = table("items", "Item", List(col("id", "BIGSERIAL")), fks = Nil)
    val result = validateTrigger(
      orderTable,
      parentFields,
      noFkChild,
      child,
      "total",
      SumAgg(),
      Some("amount")
    )
    assertEquals(result, None)

  test("returns None if source-projection field missing from child"):
    val childMissingSrc = entityD("Item", fields = List(fieldD("order_id", named("Int"))))
    val result = validateTrigger(
      orderTable,
      parentFields,
      itemTable,
      childMissingSrc,
      "total",
      SumAgg(),
      Some("amount")
    )
    assertEquals(result, None)

  test("uniqueBackFkColumn returns the column when exactly one FK matches"):
    val fks = List(fk("order_id", "orders"), fk("user_id", "users"))
    assertEquals(uniqueBackFkColumn(fks, "orders"), Some("order_id"))
    assertEquals(uniqueBackFkColumn(fks, "users"), Some("user_id"))
    assertEquals(uniqueBackFkColumn(fks, "nonexistent"), None)
    assertEquals(uniqueBackFkColumn(List(fk("a", "x"), fk("b", "x")), "x"), None)

  test("collectionElementEntityName extracts the inner NamedType"):
    assertEquals(collectionElementEntityName(SetTypeF(named("User"), None)), Some("User"))
    assertEquals(collectionElementEntityName(SeqTypeF(named("Order"), None)), Some("Order"))
    assertEquals(collectionElementEntityName(named("User")), None)
    assertEquals(collectionElementEntityName(SetTypeF(SetTypeF(named("X"), None), None)), None)
