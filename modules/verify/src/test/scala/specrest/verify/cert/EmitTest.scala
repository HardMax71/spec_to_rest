package specrest.verify.cert

import munit.FunSuite
import specrest.ir.*

class EmitTest extends FunSuite:

  private def stubState(field: StateFieldDecl): StateDecl =
    StateDecl(fields = List(field))

  test("safe_counter-shaped IR yields a verified-subset certificate"):
    val invariant = InvariantDecl(
      name = Some("countNonNegative"),
      expr = Expr.BinaryOp(
        BinOp.Ge,
        Expr.Identifier("count"),
        Expr.IntLit(0)
      )
    )
    val ir = ServiceIR(
      name = "SafeCounter",
      state = Some(
        stubState(StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int")))
      ),
      invariants = List(invariant)
    )

    val bundle   = Emit.emit(ir)
    val rendered = bundle.render

    assertEquals(bundle.summary.totalInvariants, 1)
    assertEquals(bundle.summary.certifiedInvariants, 1)
    assertEquals(bundle.summary.stubbedInvariants, 0)

    assert(
      rendered.contains("import SpecRest.Cert"),
      s"expected `import SpecRest.Cert` in output:\n$rendered"
    )
    assert(
      rendered.contains("theorem cert_invariant_0_countNonNegative"),
      s"expected named cert theorem:\n$rendered"
    )
    assert(
      rendered.contains("cert_decide"),
      s"expected `cert_decide` tactic invocation:\n$rendered"
    )
    assert(
      rendered.contains(".cmp .ge"),
      s"expected `.cmp .ge` IR rendering:\n$rendered"
    )

  test("out-of-subset invariant emits a stub theorem with TODO marker"):
    val invariant = InvariantDecl(
      name = Some("hasField"),
      expr = Expr.FieldAccess(Expr.Identifier("user"), "id")
    )
    val ir = ServiceIR(
      name = "Demo",
      invariants = List(invariant)
    )

    val bundle   = Emit.emit(ir)
    val rendered = bundle.render

    assertEquals(bundle.summary.totalInvariants, 1)
    assertEquals(bundle.summary.certifiedInvariants, 0)
    assertEquals(bundle.summary.stubbedInvariants, 1)

    assert(
      rendered.contains("OUT OF M_L.1 VERIFIED SUBSET"),
      s"expected out-of-subset banner:\n$rendered"
    )
    assert(
      rendered.contains("TODO[M_L.4]"),
      s"expected TODO marker:\n$rendered"
    )
    assert(
      bundle.theorems.forall(t => !t.contains("cert_decide")),
      s"out-of-subset theorem must NOT call `cert_decide`:\n${bundle.theorems.mkString("\n---\n")}"
    )
    assert(
      bundle.theorems.exists(t => t.contains("trivial")),
      s"out-of-subset theorem should fall back to `trivial`:\n${bundle.theorems.mkString("\n---\n")}"
    )

  test("anonymous invariant gets a synthetic name"):
    val invariant = InvariantDecl(
      name = None,
      expr = Expr.BoolLit(true)
    )
    val ir = ServiceIR(
      name = "Anon",
      invariants = List(invariant)
    )

    val bundle   = Emit.emit(ir)
    val rendered = bundle.render
    assert(
      rendered.contains("cert_invariant_0_anon_0"),
      s"expected synthetic name `anon_0`:\n$rendered"
    )

  test("VerifiedSubset.classify accepts the §6.1 minimum"):
    val verifiedSamples = List(
      Expr.BoolLit(true),
      Expr.IntLit(42),
      Expr.Identifier("x"),
      Expr.UnaryOp(UnOp.Not, Expr.BoolLit(false)),
      Expr.UnaryOp(UnOp.Negate, Expr.IntLit(3)),
      Expr.BinaryOp(BinOp.And, Expr.BoolLit(true), Expr.BoolLit(true)),
      Expr.BinaryOp(BinOp.Or, Expr.BoolLit(true), Expr.BoolLit(false)),
      Expr.BinaryOp(BinOp.Implies, Expr.BoolLit(true), Expr.BoolLit(true)),
      Expr.BinaryOp(BinOp.Eq, Expr.IntLit(1), Expr.IntLit(1)),
      Expr.BinaryOp(BinOp.Lt, Expr.IntLit(1), Expr.IntLit(2)),
      Expr.BinaryOp(
        BinOp.In,
        Expr.Identifier("u"),
        Expr.Identifier("active")
      ),
      Expr.Let("x", Expr.IntLit(1), Expr.Identifier("x")),
      Expr.EnumAccess(Expr.Identifier("Color"), "Red"),
      Expr.Quantifier(
        QuantKind.All,
        List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
        Expr.BoolLit(true)
      )
    )
    verifiedSamples.foreach: sample =>
      assert(
        VerifiedSubset.isInSubset(sample),
        s"expected $sample to be classified `InSubset`"
      )

  test("VerifiedSubset.classify rejects out-of-subset cases with a reason"):
    val rejected = List(
      Expr.BinaryOp(BinOp.Add, Expr.IntLit(1), Expr.IntLit(2)) -> "BinaryOp.Add",
      Expr.UnaryOp(UnOp.Power, Expr.Identifier("x"))           -> "UnaryOp.Power",
      Expr.Prime(Expr.Identifier("count"))                     -> "Prime",
      Expr.FieldAccess(Expr.Identifier("u"), "id")             -> "FieldAccess",
      Expr.Quantifier(
        QuantKind.Some,
        List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
        Expr.BoolLit(true)
      ) -> "Quantifier(Some|No|Exists)"
    )
    rejected.foreach:
      case (sample, expectedFragment) =>
        VerifiedSubset.classify(sample) match
          case VerifiedSubset.SubsetStatus.OutOfSubset(reason) =>
            assert(
              reason.contains(expectedFragment),
              s"expected reason for $sample to mention `$expectedFragment`, got `$reason`"
            )
          case VerifiedSubset.SubsetStatus.InSubset =>
            fail(s"expected $sample to be OutOfSubset, but was InSubset")
