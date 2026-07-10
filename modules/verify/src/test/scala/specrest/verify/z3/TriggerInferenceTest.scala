package specrest.verify.z3

class TriggerInferenceTest extends munit.CatsEffectSuite:

  private val S                    = Z3Sort.Uninterp("S")
  private def v(n: String): Z3Expr = Z3Expr.Var(n, S)
  private def bind(n: String)      = Z3Binding(n, S)

  private val cases: List[(String, List[Z3Binding], Z3Expr, List[Z3Expr])] = List(
    (
      "flat body triggers on its minimal covering app",
      List(bind("u")),
      Z3Expr.Cmp(CmpOp.Ge, Z3Expr.App("age", List(v("u"))), Z3Expr.IntLit(BigInt(0))),
      List(Z3Expr.App("age", List(v("u"))))
    ),
    (
      "a guarded universal triggers on the guard, not the body",
      List(bind("k")),
      Z3Expr.Implies(Z3Expr.App("dom", List(v("k"))), Z3Expr.App("p", List(v("k")))),
      List(Z3Expr.App("dom", List(v("k"))))
    ),
    (
      "a covering app buried under an inner binder is still a valid trigger",
      List(bind("i1")),
      Z3Expr.Quantifier(
        QKind.ForAll,
        List(bind("i2")),
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("sku", List(v("i1"))), Z3Expr.App("sku", List(v("i2"))))
      ),
      List(Z3Expr.App("sku", List(v("i1"))))
    ),
    (
      "an app that also mentions an inner-bound var is not a trigger",
      List(bind("k")),
      Z3Expr.Quantifier(QKind.Exists, List(bind("j")), Z3Expr.App("rel", List(v("k"), v("j")))),
      Nil
    ),
    (
      "a shadowing inner binder does not cover the outer name",
      List(bind("k")),
      Z3Expr.Quantifier(QKind.Exists, List(bind("k")), Z3Expr.App("g", List(v("k")))),
      Nil
    )
  )

  cases.foreach: (name, bindings, body, expected) =>
    test(name):
      assertEquals(Z3Trigger.infer(bindings, body), expected)
