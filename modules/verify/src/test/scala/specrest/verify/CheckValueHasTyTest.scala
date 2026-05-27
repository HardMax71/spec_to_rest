package specrest.verify

import munit.CatsEffectSuite
import specrest.ir.generated.*

class CheckValueHasTyTest extends CatsEffectSuite:

  private val schemaOpt: Option[span_t] = None

  private def mkInt(n: Int): int_of_integer = int_of_integer(BigInt(n))

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
    assert(check_value_has_ty(ctx, VInt(int_of_integer(BigInt(42))), TInt()))
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
