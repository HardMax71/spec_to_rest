package specrest.verify

import specrest.ir.generated.SpecRestGenerated.*

class ClassifierTest extends munit.CatsEffectSuite:

  private def intLit(n: Long): expr_full = IntLitF(BigInt(n), None)
  private def id(s: String): expr_full   = IdentifierF(s, None)
  private def bb(v: Boolean): expr_full  = BoolLitF(v, None)

  test("pure FOL invariant classifies to Z3"):
    val inv = InvariantDeclFull(
      Some("positive"),
      BinaryOpF(BGt(), id("x"), intLit(0), None),
      None
    )
    assertEquals(Classifier.classifyInvariant(inv), VerifierTool.Z3)

  test("invariant with powerset routes to Alloy"):
    val inner = UnaryOpF(UPower(), id("users"), None)
    val inv = InvariantDeclFull(
      Some("pow"),
      BinaryOpF(BSubset(), id("s"), inner, None),
      None
    )
    assertEquals(Classifier.classifyInvariant(inv), VerifierTool.Alloy)

  test("powerset nested inside a quantifier body still routes to Alloy"):
    val body = BinaryOpF(
      BSubset(),
      id("t"),
      UnaryOpF(UPower(), id("s"), None),
      None
    )
    val q = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("t", id("T"), BkIn(), None)),
      body,
      None
    )
    val inv = InvariantDeclFull(Some("qpow"), q, None)
    assertEquals(Classifier.classifyInvariant(inv), VerifierTool.Alloy)

  test("global classifier respects any invariant in the set"):
    val ir = ServiceIRFull(
      a = "S",
      b = Nil,
      c = Nil,
      d = Nil,
      e = Nil,
      f = None,
      g = Nil,
      h = Nil,
      i = List(
        InvariantDeclFull(Some("a"), bb(true), None),
        InvariantDeclFull(
          Some("b"),
          BinaryOpF(
            BSubset(),
            id("x"),
            UnaryOpF(UPower(), id("y"), None),
            None
          ),
          None
        )
      ),
      j = Nil,
      k = Nil,
      l = Nil,
      m = Nil,
      n = None,
      o = None
    )
    assertEquals(Classifier.classifyGlobal(ir), VerifierTool.Alloy)

  test("preservation classifies Alloy when any of inv/requires/ensures uses powerset"):
    val op = OperationDeclFull(
      "op",
      Nil,
      Nil,
      List(bb(true)),
      List(
        BinaryOpF(
          BEq(),
          id("x"),
          UnaryOpF(UPower(), id("y"), None),
          None
        )
      ),
      None
    )
    val inv = InvariantDeclFull(Some("p"), bb(true), None)
    assertEquals(Classifier.classifyPreservation(op, inv), VerifierTool.Alloy)

  test("VerifierTool.token renders tool names"):
    assertEquals(VerifierTool.token(VerifierTool.Z3), "z3")
    assertEquals(VerifierTool.token(VerifierTool.Alloy), "alloy")
