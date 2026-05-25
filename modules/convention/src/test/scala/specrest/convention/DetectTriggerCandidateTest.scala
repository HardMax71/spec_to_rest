package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class DetectTriggerCandidateTest extends CatsEffectSuite:

  private def named(t: String): NamedTypeF = NamedTypeF(t, None)

  private def fieldD(name: String, ty: type_expr_full): FieldDeclFull =
    FieldDeclFull(name, ty, None, None)

  private def entityD(
      name: String,
      fields: List[field_decl_full],
      invariants: List[expr_full] = Nil
  ): EntityDeclFull =
    EntityDeclFull(name, None, fields, invariants, None)

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

  // sum(items, lambda i: i.amount) where parent has `total: Int` and field `items: Set[Item]`.
  private def sumInvariant(target: String, coll: String, src: String): expr_full =
    BinaryOpF(
      BEq(),
      IdentifierF(target, None),
      CallF(
        IdentifierF("sum", None),
        List(
          IdentifierF(coll, None),
          LambdaF("i", FieldAccessF(IdentifierF("i", None), src, None), None)
        ),
        None
      ),
      None
    )

  private val parent = entityD(
    "Order",
    fields = List(fieldD("total", named("Int")), fieldD("items", SetTypeF(named("Item"), None))),
    invariants = List(sumInvariant("total", "items", "amount"))
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

  test("happy path: parent + child + unique back-FK + valid source field → Some candidate"):
    val result = detectTriggerCandidate(
      parent,
      parent.d.head,
      List(parent, child),
      List(orderTable, itemTable)
    )
    assertEquals(
      result,
      Some(TriggerCandidate("orders", "total", "items", "order_id", SumAgg(), Some("amount")))
    )

  test("returns None if invariant doesn't match aggregate shape"):
    val nonAgg = BoolLitF(true, None)
    val result = detectTriggerCandidate(
      parent,
      nonAgg,
      List(parent, child),
      List(orderTable, itemTable)
    )
    assertEquals(result, None)

  test("returns None if parent table missing from schema"):
    val result = detectTriggerCandidate(
      parent,
      parent.d.head,
      List(parent, child),
      List(itemTable)
    )
    assertEquals(result, None)

  test("returns None if target field doesn't exist on parent"):
    val badParent = entityD(
      "Order",
      fields = List(fieldD("items", SetTypeF(named("Item"), None))),
      invariants = List(sumInvariant("total", "items", "amount"))
    )
    val result = detectTriggerCandidate(
      badParent,
      badParent.d.head,
      List(badParent, child),
      List(orderTable, itemTable)
    )
    assertEquals(result, None)

  test("returns None if collection field's element type is not an entity"):
    val flatColl = entityD(
      "Order",
      fields = List(fieldD("total", named("Int")), fieldD("items", SetTypeF(named("Int"), None))),
      invariants = List(sumInvariant("total", "items", "amount"))
    )
    val intTable = table("orders", "Order", List(col("id", "BIGSERIAL")))
    val result = detectTriggerCandidate(
      flatColl,
      flatColl.d.head,
      List(flatColl),
      List(intTable)
    )
    assertEquals(result, None)

  test("returns None if child has multiple FKs to parent (ambiguous)"):
    val multiFkChild = table(
      "items",
      "Item",
      List(col("id", "BIGSERIAL"), col("order_id", "BIGINT"), col("alt_order_id", "BIGINT")),
      fks = List(fk("order_id", "orders"), fk("alt_order_id", "orders"))
    )
    val result = detectTriggerCandidate(
      parent,
      parent.d.head,
      List(parent, child),
      List(orderTable, multiFkChild)
    )
    assertEquals(result, None)

  test("returns None if source-projection field missing from child"):
    val childMissingSrc = entityD(
      "Item",
      fields = List(fieldD("order_id", named("Int")))
    )
    val result = detectTriggerCandidate(
      parent,
      parent.d.head,
      List(parent, childMissingSrc),
      List(orderTable, itemTable)
    )
    assertEquals(result, None)

  test("Seq element type also resolves (parallel to Set)"):
    val seqParent = entityD(
      "Order",
      fields = List(fieldD("total", named("Int")), fieldD("items", SeqTypeF(named("Item"), None))),
      invariants = List(sumInvariant("total", "items", "amount"))
    )
    val result = detectTriggerCandidate(
      seqParent,
      seqParent.d.head,
      List(seqParent, child),
      List(orderTable, itemTable)
    )
    assertEquals(
      result,
      Some(TriggerCandidate("orders", "total", "items", "order_id", SumAgg(), Some("amount")))
    )

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
