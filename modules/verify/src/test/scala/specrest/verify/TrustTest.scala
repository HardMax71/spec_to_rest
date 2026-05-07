package specrest.verify

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class TrustTest extends CatsEffectSuite:

  private val enums: List[String] = List("Color", "Status")

  private def lit(b: Boolean): expr_full = BoolLitF(b, None)
  private def i(n: Int): expr_full       = IntLitF(int_of_integer(BigInt(n)), None)
  private def id(s: String): expr_full   = IdentifierF(s, None)

  private val classifyCases: List[(String, expr_full, TrustLevel)] = List(
    ("Sound: in-subset BoolBin", BinaryOpF(BAnd(), lit(true), lit(false), None), TrustLevel.Sound),
    ("Sound: integer Cmp", BinaryOpF(BLt(), i(1), i(2), None), TrustLevel.Sound),
    (
      "Sound: QuantifierF QAll over enum domain (BkIn)",
      QuantifierF(
        QAll(),
        List(QuantifierBindingFull("c", id("Color"), BkIn(), None)),
        lit(true),
        None
      ),
      TrustLevel.Sound
    ),
    (
      "Sound: QuantifierF QAll over relation domain",
      QuantifierF(
        QAll(),
        List(QuantifierBindingFull("u", id("users"), BkIn(), None)),
        lit(true),
        None
      ),
      TrustLevel.Sound
    ),
    (
      "Sound: multi-field WithF over Identifier",
      WithF(
        id("user"),
        List(
          FieldAssignFull("name", id("nv"), None),
          FieldAssignFull("active", lit(true), None)
        ),
        None
      ),
      TrustLevel.Sound
    ),
    ("BestEffort: UPower", UnaryOpF(UPower(), id("users"), None), TrustLevel.BestEffort),
    ("BestEffort: BSubset", BinaryOpF(BSubset(), id("a"), id("b"), None), TrustLevel.BestEffort),
    (
      "BestEffort: CallF (predicate inlining not yet covered)",
      CallF(id("isPositive"), List(i(1)), None),
      TrustLevel.BestEffort
    ),
    (
      "Sound: pre(rel)[k] (IndexF over PreF) — issue #210 widened carrier",
      IndexF(PreF(id("orders"), None), id("k"), None),
      TrustLevel.Sound
    ),
    (
      "Sound: rel'[k] (IndexF over PrimeF) — issue #210 widened carrier",
      IndexF(PrimeF(id("orders"), None), id("k"), None),
      TrustLevel.Sound
    ),
    (
      "BestEffort: IndexF over a non-relation-ref base (e.g. arithmetic)",
      IndexF(BinaryOpF(BAdd(), i(1), i(2), None), id("k"), None),
      TrustLevel.BestEffort
    ),
    (
      "BestEffort: TheF (definite description)",
      TheF("s", id("sessions"), lit(true), None),
      TrustLevel.BestEffort
    ),
    (
      "BestEffort: IfF (no If ctor in subset)",
      IfF(lit(true), i(1), i(2), None),
      TrustLevel.BestEffort
    )
  )

  classifyCases.foreach: (name, e, expected) =>
    test(name):
      assertEquals(Trust.classify(enums, e), expected)

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
