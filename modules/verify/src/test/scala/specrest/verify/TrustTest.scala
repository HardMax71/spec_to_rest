package specrest.verify

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class TrustTest extends CatsEffectSuite:

  private val enums: List[String] = List("Color", "Status")

  private def lit(b: Boolean): expr_full = BoolLitF(b, None)
  private def i(n: Int): expr_full       = IntLitF(int_of_integer(BigInt(n)), None)
  private def id(s: String): expr_full   = IdentifierF(s, None)

  test("Sound: in-subset BoolBin"):
    val e = BinaryOpF(BAnd(), lit(true), lit(false), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.Sound)

  test("Sound: integer Cmp"):
    val e = BinaryOpF(BLt(), i(1), i(2), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.Sound)

  test("Sound: QuantifierF QAll over enum domain (BkIn)"):
    val e = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", id("Color"), BkIn(), None)),
      lit(true),
      None
    )
    assertEquals(Trust.classify(enums, e), TrustLevel.Sound)

  test("Sound: QuantifierF QAll over relation domain"):
    val e = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("u", id("users"), BkIn(), None)),
      lit(true),
      None
    )
    assertEquals(Trust.classify(enums, e), TrustLevel.Sound)

  test("Sound: multi-field WithF over Identifier"):
    val e = WithF(
      id("user"),
      List(
        FieldAssignFull("name", id("nv"), None),
        FieldAssignFull("active", lit(true), None)
      ),
      None
    )
    assertEquals(Trust.classify(enums, e), TrustLevel.Sound)

  test("BestEffort: UPower"):
    val e = UnaryOpF(UPower(), id("users"), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.BestEffort)

  test("BestEffort: BSubset"):
    val e = BinaryOpF(BSubset(), id("a"), id("b"), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.BestEffort)

  test("BestEffort: CallF (predicate inlining not yet covered)"):
    val e = CallF(id("isPositive"), List(i(1)), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.BestEffort)

  test("BestEffort: pre(rel)[k] (IndexF over PreF)"):
    val e = IndexF(PreF(id("orders"), None), id("k"), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.BestEffort)

  test("BestEffort: TheF (definite description)"):
    val e = TheF("s", id("sessions"), lit(true), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.BestEffort)

  test("BestEffort: IfF (no If ctor in subset)"):
    val e = IfF(lit(true), i(1), i(2), None)
    assertEquals(Trust.classify(enums, e), TrustLevel.BestEffort)

  test("classify list aggregates: any BestEffort → BestEffort"):
    val ok  = lit(true)
    val bad = UnaryOpF(UPower(), id("users"), None)
    assertEquals(Trust.classify(enums, List(ok, ok)), TrustLevel.Sound)
    assertEquals(Trust.classify(enums, List(ok, bad)), TrustLevel.BestEffort)
    assertEquals(Trust.classify(enums, Nil), TrustLevel.Sound)

  test("enumNames extracts from ServiceIRFull.d"):
    val ir = empty_service_ir_full("svc") match
      case s: ServiceIRFull => s.copy(d =
          List(
            EnumDeclFull("Color", List("Red", "Green"), None),
            EnumDeclFull("Status", List("On", "Off"), None)
          )
        )
    assertEquals(Trust.enumNames(ir), List("Color", "Status"))

  test("TrustLevel.token"):
    assertEquals(TrustLevel.token(TrustLevel.Sound), "sound")
    assertEquals(TrustLevel.token(TrustLevel.BestEffort), "best-effort")
