package specrest.verify.cert.generated

import munit.FunSuite
import specrest.ir.generated.SpecRestGenerated.*

class A8RoundTripOracleTest extends FunSuite:

  private val enumNames: List[String] = List("Color", "Status")

  test("A8: extracted translate projects every in-subset probe to a non-empty term"):
    var translated = 0
    var skipped    = 0
    SpecRestGeneratedTestProbes.allProbes.foreach: (shape, e) =>
      translate(enumNames, e) match
        case Some(smtTerm) =>
          assert(smtTerm.toString.nonEmpty, s"$shape: translate produced empty term")
          translated += 1
        case None =>
          skipped += 1
    assert(translated > 0, "no probes translated; corpus issue?")
    println(s"[A8 oracle] translated=$translated  skipped(direct punt)=$skipped")

  test("A8: extracted translate produces shape-correct SmtTerm headers"):
    val cases: List[(expr, String)] = List(
      BoolLitF(true, None)                                                 -> "BLit(true)",
      IntLitF(BigInt(7), None)                                             -> "ILit(",
      IdentifierF("foo", None)                                             -> "TVar(foo)",
      UnaryOpF(UNot(), BoolLitF(true, None), None)                         -> "TNot(",
      BinaryOpF(BAnd(), BoolLitF(true, None), BoolLitF(false, None), None) -> "TAnd(",
      BinaryOpF(
        BAdd(),
        IntLitF(BigInt(1), None),
        IntLitF(BigInt(2), None),
        None
      ) -> "TAdd(",
      BinaryOpF(
        BLt(),
        IntLitF(BigInt(1), None),
        IntLitF(BigInt(2), None),
        None
      ) -> "TLt("
    )
    cases.foreach: (e, expectedPrefix) =>
      val out =
        translate(enumNames, e).getOrElse(fail(s"translation failed on $e")).toString
      assert(out.startsWith(expectedPrefix), s"shape: expected prefix $expectedPrefix; got $out")

  test("A8: direct translation covers QuantifierF QAll over both enum and relation domains"):
    val enumQ = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", IdentifierF("Color", None), BkIn(), None)),
      BoolLitF(true, None),
      None
    )
    val relQ = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("u", IdentifierF("users", None), BkIn(), None)),
      BoolLitF(true, None),
      None
    )
    translate(enumNames, enumQ) match
      case Some(TForallEnum("c", "Color", _)) => ()
      case other                              => fail(s"expected TForallEnum; got $other")
    translate(enumNames, relQ) match
      case Some(TForallRel("u", "users", _)) => ()
      case other                             => fail(s"expected TForallRel; got $other")

  test("A8: direct translation accepts both BkIn and BkColon bindings (surface-syntax shorthand)"):
    val colonQ = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", IdentifierF("Color", None), BkColon(), None)),
      BoolLitF(true, None),
      None
    )
    translate(enumNames, colonQ) match
      case Some(TForallEnum("c", "Color", _)) => ()
      case other                              => fail(s"expected TForallEnum (BkColon); got $other")

object SpecRestGeneratedTestProbes:

  val allProbes: List[(String, expr)] = List(
    "BoolLit"             -> BoolLitF(true, None),
    "IntLit"              -> IntLitF(BigInt(42), None),
    "Identifier"          -> IdentifierF("x", None),
    "UnaryOp.Not"         -> UnaryOpF(UNot(), BoolLitF(true, None), None),
    "UnaryOp.Negate"      -> UnaryOpF(UNegate(), IntLitF(BigInt(1), None), None),
    "UnaryOp.Cardinality" -> UnaryOpF(UCardinality(), IdentifierF("rel", None), None),
    "BinaryOp.And"        -> BinaryOpF(BAnd(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Or"         -> BinaryOpF(BOr(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Implies"    -> BinaryOpF(BImplies(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Iff"        -> BinaryOpF(BIff(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Eq" -> BinaryOpF(
      BEq(),
      IntLitF(BigInt(1), None),
      IntLitF(BigInt(2), None),
      None
    ),
    "BinaryOp.Lt" -> BinaryOpF(
      BLt(),
      IntLitF(BigInt(1), None),
      IntLitF(BigInt(2), None),
      None
    ),
    "BinaryOp.Ge" -> BinaryOpF(
      BGe(),
      IntLitF(BigInt(1), None),
      IntLitF(BigInt(2), None),
      None
    ),
    "BinaryOp.Add" -> BinaryOpF(
      BAdd(),
      IntLitF(BigInt(1), None),
      IntLitF(BigInt(2), None),
      None
    ),
    "BinaryOp.In" -> BinaryOpF(BIn(), IdentifierF("v", None), IdentifierF("rel", None), None),
    "Let"         -> LetF("x", IntLitF(BigInt(1), None), IdentifierF("x", None), None),
    "EnumAccess"  -> EnumAccessF(IdentifierF("Color", None), "Red", None),
    "Prime"       -> PrimeF(IdentifierF("count", None), None),
    "Pre"         -> PreF(IdentifierF("count", None), None),
    "FieldAccess" -> FieldAccessF(IdentifierF("u", None), "name", None),
    "Index"       -> IndexF(IdentifierF("arr", None), IntLitF(BigInt(0), None), None),
    "Index.Pre" -> IndexF(
      PreF(IdentifierF("arr", None), None),
      IntLitF(BigInt(0), None),
      None
    ),
    "Index.Prime" -> IndexF(
      PrimeF(IdentifierF("arr", None), None),
      IntLitF(BigInt(0), None),
      None
    ),
    "Quantifier.All.enum" -> QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", IdentifierF("Color", None), BkIn(), None)),
      BoolLitF(true, None),
      None
    ),
    "Quantifier.All.rel" -> QuantifierF(
      QAll(),
      List(QuantifierBindingFull("u", IdentifierF("users", None), BkIn(), None)),
      BoolLitF(true, None),
      None
    ),
    "Quantifier.All.colon" -> QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", IdentifierF("Color", None), BkColon(), None)),
      BoolLitF(true, None),
      None
    ),
    "SetEmpty" -> SetLiteralF(Nil, None)
  )
