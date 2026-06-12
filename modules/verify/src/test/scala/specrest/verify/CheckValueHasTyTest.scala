package specrest.verify

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class CheckValueHasTyTest extends CatsEffectSuite:

  private val schemaOpt: Option[span_t] = None

  private def mkInt(n: Int): BigInt = BigInt(n)

  private val orderEntity = EntityDeclFull(
    "Order",
    None,
    List(
      FieldDeclFull("id", NamedTypeF("Int", schemaOpt), None, schemaOpt),
      FieldDeclFull("active", NamedTypeF("Bool", schemaOpt), None, schemaOpt)
    ),
    Nil,
    schemaOpt
  )

  private val statusEnum =
    EnumDeclFull("Status", List("Open", "Closed"), schemaOpt)

  private val ir: ServiceIRFull =
    ServiceIRFull(
      "Svc",
      Nil,
      List(orderEntity),
      List(statusEnum),
      Nil,
      None,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      None,
      None
    )

  private val ctx = tyctxFromService(ir)

  test("tyctxFromService populates entities and enums"):
    assertEquals(tc_entities(ctx).size, 1)
    assertEquals(tc_enums(ctx), List("Status"))

  test("check_value_has_ty accepts the primitive shapes"):
    assert(check_value_has_ty(ctx, VBool(true), TBool()))
    assert(check_value_has_ty(ctx, VInt(BigInt(42)), TInt()))
    assert(check_value_has_ty(ctx, VEnum("Status", "Open"), TEnum("Status")))
    assert(check_value_has_ty(ctx, VEntity("Order", "0"), TEntity("Order")))

  test("check_value_has_ty rejects shape mismatches"):
    assert(!check_value_has_ty(ctx, VBool(true), TInt()))
    assert(!check_value_has_ty(ctx, VInt(mkInt(0)), TBool()))
    assert(!check_value_has_ty(ctx, VEnum("Status", "X"), TEnum("Other")))

  test("vt_entity_with tightening: well-typed override accepted"):
    val typedActive = VEntityWith(VEntity("Order", "0"), "active", VBool(true))
    assert(check_value_has_ty(ctx, typedActive, TEntity("Order")))

  test("vt_entity_with tightening: mistyped override rejected"):
    val mistypedActive = VEntityWith(VEntity("Order", "0"), "active", VInt(mkInt(7)))
    assert(!check_value_has_ty(ctx, mistypedActive, TEntity("Order")))

  test("vt_entity_with tightening: unknown field rejected"):
    val unknownField = VEntityWith(VEntity("Order", "0"), "ghost", VBool(true))
    assert(!check_value_has_ty(ctx, unknownField, TEntity("Order")))

  test("VSet: every element must satisfy element type"):
    val okSet  = VSet(List(VInt(mkInt(1)), VInt(mkInt(2))))
    val badSet = VSet(List(VInt(mkInt(1)), VBool(true)))
    assert(check_value_has_ty(ctx, okSet, TSet(TInt())))
    assert(!check_value_has_ty(ctx, badSet, TSet(TInt())))

  List(
    ("VStr at TStr", VStr("hello"): ir_value, TStr(): ty, true),
    ("VStr at TInt", VStr("hello"), TInt(), false),
    ("VNone at any TOption", VNone(), TOption(TInt()), true),
    ("VNone at non-option", VNone(), TInt(), false),
    ("VSome wraps element type", VSome(VInt(mkInt(1))), TOption(TInt()), true),
    ("VSome with mismatched element", VSome(VBool(true)), TOption(TInt()), false),
    ("VSeq: all elements typed", VSeq(List(VStr("a"), VStr("b"))), TSeq(TStr()), true),
    ("VSeq: mixed elements rejected", VSeq(List(VStr("a"), VInt(mkInt(1)))), TSeq(TStr()), false),
    ("VSeq at TSet rejected", VSeq(List(VInt(mkInt(1)))), TSet(TInt()), false),
    (
      "VMap: keys and values typed",
      VMap(List((VStr("k"), VInt(mkInt(1))))),
      TMap(TStr(), TInt()),
      true
    ),
    (
      "VMap: mistyped value rejected",
      VMap(List((VStr("k"), VBool(true)))),
      TMap(TStr(), TInt()),
      false
    ),
    (
      "VMap: mistyped key rejected",
      VMap(List((VInt(mkInt(1)), VInt(mkInt(2))))),
      TMap(TStr(), TInt()),
      false
    )
  ).foreach:
    case (name, value, expected, ok) =>
      test(s"native sorts: $name"):
        assertEquals(check_value_has_ty(ctx, value, expected), ok)

  test("nested native sorts compose"):
    val v = VSome(VMap(List((VStr("k"), VSeq(List(VInt(mkInt(1))))))))
    val t = TOption(TMap(TStr(), TSeq(TInt())))
    assert(check_value_has_ty(ctx, v, t))
    assert(!check_value_has_ty(ctx, v, TOption(TMap(TStr(), TSeq(TBool())))))
