package specrest.verify

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

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

  private val ctx = SpecRestGenerated.tyctxFromService(ir)

  test("tyctxFromService populates entities and enums"):
    assertEquals(SpecRestGenerated.tc_entities(ctx).size, 1)
    assertEquals(SpecRestGenerated.tc_enums(ctx), List("Status"))

  test("check_value_has_ty accepts the primitive shapes"):
    assert(SpecRestGenerated.check_value_has_ty(ctx, VBool(true), TBool()))
    assert(SpecRestGenerated.check_value_has_ty(ctx, VInt(int_of_integer(BigInt(42))), TInt()))
    assert(SpecRestGenerated.check_value_has_ty(ctx, VEnum("Status", "Open"), TEnum("Status")))
    assert(SpecRestGenerated.check_value_has_ty(ctx, VEntity("Order", "0"), TEntity("Order")))

  test("check_value_has_ty rejects shape mismatches"):
    assert(!SpecRestGenerated.check_value_has_ty(ctx, VBool(true), TInt()))
    assert(!SpecRestGenerated.check_value_has_ty(ctx, VInt(mkInt(0)), TBool()))
    assert(!SpecRestGenerated.check_value_has_ty(ctx, VEnum("Status", "X"), TEnum("Other")))

  test("vt_entity_with tightening: well-typed override accepted"):
    val typedActive = VEntityWith(VEntity("Order", "0"), "active", VBool(true))
    assert(SpecRestGenerated.check_value_has_ty(ctx, typedActive, TEntity("Order")))

  test("vt_entity_with tightening: mistyped override rejected"):
    val mistypedActive = VEntityWith(VEntity("Order", "0"), "active", VInt(mkInt(7)))
    assert(!SpecRestGenerated.check_value_has_ty(ctx, mistypedActive, TEntity("Order")))

  test("vt_entity_with tightening: unknown field rejected"):
    val unknownField = VEntityWith(VEntity("Order", "0"), "ghost", VBool(true))
    assert(!SpecRestGenerated.check_value_has_ty(ctx, unknownField, TEntity("Order")))

  test("VSet: every element must satisfy element type"):
    val okSet  = VSet(List(VInt(mkInt(1)), VInt(mkInt(2))))
    val badSet = VSet(List(VInt(mkInt(1)), VBool(true)))
    assert(SpecRestGenerated.check_value_has_ty(ctx, okSet, TSet(TInt())))
    assert(!SpecRestGenerated.check_value_has_ty(ctx, badSet, TSet(TInt())))
