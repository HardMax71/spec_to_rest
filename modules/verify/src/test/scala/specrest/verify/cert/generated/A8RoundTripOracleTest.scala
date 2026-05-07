package specrest.verify.cert.generated

import munit.FunSuite
import specrest.ir.generated.SpecRestGenerated.*

class A8RoundTripOracleTest extends FunSuite:

  private val enumNames: List[String] = List("Color", "Status")

  test("A8: extracted lower projects every in-subset probe; translate produces a non-empty term"):
    var translated     = 0
    var skippedByLower = 0
    SpecRestGeneratedTestProbes.allProbes.foreach: (shape, e) =>
      lower(enumNames, e) match
        case Some(verified) =>
          val smtTerm = translate(verified)
          assert(smtTerm.toString.nonEmpty, s"$shape: translate produced empty term")
          translated += 1
        case None =>
          skippedByLower += 1
    assert(translated > 0, "no probes translated; corpus issue?")
    println(s"[A8 oracle] translated=$translated  skipped(lower v2 punt)=$skippedByLower")

  test("A8: extracted translate produces shape-correct SmtTerm headers"):
    val cases: List[(expr_full, String)] = List(
      BoolLitF(true, None)                                                 -> "BLit(true)",
      IntLitF(int_of_integer(BigInt(7)), None)                             -> "ILit(int_of_integer(",
      IdentifierF("foo", None)                                             -> "TVar(foo)",
      UnaryOpF(UNot(), BoolLitF(true, None), None)                         -> "TNot(",
      BinaryOpF(BAnd(), BoolLitF(true, None), BoolLitF(false, None), None) -> "TAnd(",
      BinaryOpF(
        BAdd(),
        IntLitF(int_of_integer(BigInt(1)), None),
        IntLitF(int_of_integer(BigInt(2)), None),
        None
      ) -> "TAdd(",
      BinaryOpF(
        BLt(),
        IntLitF(int_of_integer(BigInt(1)), None),
        IntLitF(int_of_integer(BigInt(2)), None),
        None
      ) -> "TLt("
    )
    cases.foreach: (e, expectedPrefix) =>
      val verified = lower(enumNames, e).getOrElse(fail(s"lower failed on $e"))
      val out      = translate(verified).toString
      assert(out.startsWith(expectedPrefix), s"shape: expected prefix $expectedPrefix; got $out")

  test("A8: lower v2 covers QuantifierF QAll over both enum and relation domains"):
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
    lower(enumNames, enumQ) match
      case Some(ForallEnum("c", "Color", _, _)) => ()
      case other                                => fail(s"expected ForallEnum; got $other")
    lower(enumNames, relQ) match
      case Some(ForallRel("u", "users", _, _)) => ()
      case other                               => fail(s"expected ForallRel; got $other")

  test("A8: lower v2 accepts both BkIn and BkColon bindings (surface-syntax shorthand)"):
    val colonQ = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", IdentifierF("Color", None), BkColon(), None)),
      BoolLitF(true, None),
      None
    )
    lower(enumNames, colonQ) match
      case Some(ForallEnum("c", "Color", _, _)) => ()
      case other                                => fail(s"expected ForallEnum (BkColon); got $other")

object SpecRestGeneratedTestProbes:

  val allProbes: List[(String, expr_full)] = List(
    "BoolLit"             -> BoolLitF(true, None),
    "IntLit"              -> IntLitF(int_of_integer(BigInt(42)), None),
    "Identifier"          -> IdentifierF("x", None),
    "UnaryOp.Not"         -> UnaryOpF(UNot(), BoolLitF(true, None), None),
    "UnaryOp.Negate"      -> UnaryOpF(UNegate(), IntLitF(int_of_integer(BigInt(1)), None), None),
    "UnaryOp.Cardinality" -> UnaryOpF(UCardinality(), IdentifierF("rel", None), None),
    "BinaryOp.And"        -> BinaryOpF(BAnd(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Or"         -> BinaryOpF(BOr(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Implies"    -> BinaryOpF(BImplies(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Iff"        -> BinaryOpF(BIff(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Eq" -> BinaryOpF(
      BEq(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.Lt" -> BinaryOpF(
      BLt(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.Ge" -> BinaryOpF(
      BGe(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.Add" -> BinaryOpF(
      BAdd(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.In" -> BinaryOpF(BIn(), IdentifierF("v", None), IdentifierF("rel", None), None),
    "Let"         -> LetF("x", IntLitF(int_of_integer(BigInt(1)), None), IdentifierF("x", None), None),
    "EnumAccess"  -> EnumAccessF(IdentifierF("Color", None), "Red", None),
    "Prime"       -> PrimeF(IdentifierF("count", None), None),
    "Pre"         -> PreF(IdentifierF("count", None), None),
    "FieldAccess" -> FieldAccessF(IdentifierF("u", None), "name", None),
    "Index"       -> IndexF(IdentifierF("arr", None), IntLitF(int_of_integer(BigInt(0)), None), None),
    "Index.Pre" -> IndexF(
      PreF(IdentifierF("arr", None), None),
      IntLitF(int_of_integer(BigInt(0)), None),
      None
    ),
    "Index.Prime" -> IndexF(
      PrimeF(IdentifierF("arr", None), None),
      IntLitF(int_of_integer(BigInt(0)), None),
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
