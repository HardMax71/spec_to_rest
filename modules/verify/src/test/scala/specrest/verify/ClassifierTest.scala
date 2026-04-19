package specrest.verify

import specrest.ir.*

class ClassifierTest extends munit.FunSuite:

  private def intLit(n: Long): Expr = Expr.IntLit(n)

  test("pure FOL invariant classifies to Z3"):
    val inv = InvariantDecl(
      name = Some("positive"),
      expr = Expr.BinaryOp(BinOp.Gt, Expr.Identifier("x"), intLit(0))
    )
    assertEquals(Classifier.classifyInvariant(inv), VerifierTool.Z3)

  test("invariant with powerset routes to Alloy"):
    val inner = Expr.UnaryOp(UnOp.Power, Expr.Identifier("users"))
    val inv = InvariantDecl(
      name = Some("pow"),
      expr = Expr.BinaryOp(BinOp.Subset, Expr.Identifier("s"), inner)
    )
    assertEquals(Classifier.classifyInvariant(inv), VerifierTool.Alloy)

  test("powerset nested inside a quantifier body still routes to Alloy"):
    val body = Expr.BinaryOp(
      BinOp.Subset,
      Expr.Identifier("t"),
      Expr.UnaryOp(UnOp.Power, Expr.Identifier("s"))
    )
    val q = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("t", Expr.Identifier("T"), BindingKind.In)),
      body
    )
    val inv = InvariantDecl(name = Some("qpow"), expr = q)
    assertEquals(Classifier.classifyInvariant(inv), VerifierTool.Alloy)

  test("global classifier respects any invariant in the set"):
    val ir = ServiceIR(
      name = "S",
      invariants = List(
        InvariantDecl(Some("a"), Expr.BoolLit(true)),
        InvariantDecl(
          Some("b"),
          Expr.BinaryOp(
            BinOp.Subset,
            Expr.Identifier("x"),
            Expr.UnaryOp(UnOp.Power, Expr.Identifier("y"))
          )
        )
      )
    )
    assertEquals(Classifier.classifyGlobal(ir), VerifierTool.Alloy)

  test("preservation classifies Alloy when any of inv/requires/ensures uses powerset"):
    val op = OperationDecl(
      name = "op",
      requires = List(Expr.BoolLit(true)),
      ensures = List(
        Expr.BinaryOp(
          BinOp.Eq,
          Expr.Identifier("x"),
          Expr.UnaryOp(UnOp.Power, Expr.Identifier("y"))
        )
      )
    )
    val inv = InvariantDecl(Some("p"), Expr.BoolLit(true))
    assertEquals(Classifier.classifyPreservation(op, inv), VerifierTool.Alloy)

  test("VerifierTool.token renders tool names"):
    assertEquals(VerifierTool.token(VerifierTool.Z3), "z3")
    assertEquals(VerifierTool.token(VerifierTool.Alloy), "alloy")
