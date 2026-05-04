package specrest.verify.cert

import munit.FunSuite
import specrest.ir.*

class EmitIsabelleTest extends FunSuite:

  private def mkIR(invariants: List[InvariantDecl]): ServiceIR =
    ServiceIR(
      name = "TestService",
      enums = List(EnumDecl("Color", List("Red", "Green", "Blue"))),
      entities = Nil,
      typeAliases = Nil,
      state = Some(StateDecl(Nil)),
      operations = Nil,
      transitions = Nil,
      invariants = invariants,
      temporals = Nil,
      facts = Nil,
      functions = Nil,
      predicates = Nil,
      conventions = None
    )

  test("renderExpr emits BoolLit"):
    assertEquals(
      EmitIsabelle.renderExpr(Expr.BoolLit(true), Set.empty),
      Some("BoolLit True")
    )
    assertEquals(
      EmitIsabelle.renderExpr(Expr.BoolLit(false), Set.empty),
      Some("BoolLit False")
    )

  test("renderExpr emits IntLit"):
    assertEquals(
      EmitIsabelle.renderExpr(Expr.IntLit(42L), Set.empty),
      Some("IntLit (42)")
    )

  test("renderExpr emits Identifier with String.literal"):
    assertEquals(
      EmitIsabelle.renderExpr(Expr.Identifier("count"), Set.empty),
      Some("Ident (STR ''count'')")
    )

  test("renderExpr emits BoolBin (And)"):
    val e = Expr.BinaryOp(BinOp.And, Expr.BoolLit(true), Expr.BoolLit(false))
    assertEquals(
      EmitIsabelle.renderExpr(e, Set.empty),
      Some("BoolBin AndOp (BoolLit True) (BoolLit False)")
    )

  test("renderExpr emits Arith (Add)"):
    val e = Expr.BinaryOp(BinOp.Add, Expr.IntLit(1L), Expr.IntLit(2L))
    assertEquals(
      EmitIsabelle.renderExpr(e, Set.empty),
      Some("Arith AddOp (IntLit (1)) (IntLit (2))")
    )

  test("renderExpr emits Cmp (Ge)"):
    val e = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0L))
    assertEquals(
      EmitIsabelle.renderExpr(e, Set.empty),
      Some("Cmp GeOp (Ident (STR ''count'')) (IntLit (0))")
    )

  test("renderExpr emits Quantifier(All) over enum as ForallEnum"):
    val e = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
      Expr.BoolLit(true)
    )
    assertEquals(
      EmitIsabelle.renderExpr(e, Set("Color")),
      Some("ForallEnum (STR ''c'') (STR ''Color'') (BoolLit True)")
    )

  test("renderExpr emits Quantifier(All) over relation as ForallRel"):
    val e = Expr.Quantifier(
      QuantKind.All,
      List(QuantifierBinding("u", Expr.Identifier("users"), BindingKind.In)),
      Expr.BoolLit(true)
    )
    assertEquals(
      EmitIsabelle.renderExpr(e, Set("Color")),
      Some("ForallRel (STR ''u'') (STR ''users'') (BoolLit True)")
    )

  test("emit produces a well-formed theory header"):
    val inv = InvariantDecl(
      name = Some("count_nonneg"),
      expr = Expr.BinaryOp(BinOp.Ge, Expr.Identifier("count"), Expr.IntLit(0L))
    )
    val bundle = EmitIsabelle.emit(mkIR(List(inv)))
    val theory = bundle.renderTheory
    assert(theory.startsWith("theory TestService_Cert"))
    assert(theory.contains("imports SpecRest.Codegen"))
    assert(theory.endsWith("end\n"))
    assert(theory.contains("cert_invariant_0_count_nonneg"))
    assert(bundle.summary.totalChecks == 1)
    assert(bundle.summary.certifiedChecks == 1)
    assert(bundle.summary.stubbedChecks == 0)

  test("emit stubs out-of-subset invariants"):
    val inv = InvariantDecl(
      name = Some("partial"),
      expr = Expr.SeqLiteral(List(Expr.IntLit(1L)))
    )
    val bundle = EmitIsabelle.emit(mkIR(List(inv)))
    val theory = bundle.renderTheory
    assert(theory.contains("OUT OF M_L.1 VERIFIED SUBSET"))
    assertEquals(bundle.summary.stubbedChecks, 1)
    assertEquals(bundle.summary.certifiedChecks, 0)
