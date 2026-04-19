package specrest.verify

class SmtLibRenderTest extends munit.FunSuite:

  test("empty script renders minimal SMT-LIB"):
    val script = Z3Script(
      sorts = Nil,
      funcs = Nil,
      assertions = Nil,
      artifact = TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, hasPostState = false)
    )
    val out = SmtLib.renderSmtLib(script)
    assertEquals(
      out,
      """(set-logic ALL)
        |(set-option :produce-models true)
        |(check-sat)
        |""".stripMargin
    )

  test("timeout option emitted"):
    val script = Z3Script(Nil, Nil, Nil, TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, false))
    val out    = SmtLib.renderSmtLib(script, timeoutMs = Some(5000L))
    assert(out.contains("(set-option :timeout 5000)"))

  test("uninterp sort + func decl + simple assertion"):
    val userSort = Z3Sort.Uninterp("User")
    val script = Z3Script(
      sorts = List(userSort),
      funcs = List(Z3FunctionDecl("age", List(userSort), Z3Sort.Int)),
      assertions = List(
        Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding("u", userSort)),
          Z3Expr.Cmp(
            CmpOp.Ge,
            Z3Expr.App("age", List(Z3Expr.Var("u", userSort))),
            Z3Expr.IntLit(0)
          )
        )
      ),
      artifact = TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, false)
    )
    val out = SmtLib.renderSmtLib(script)
    assert(out.contains("(declare-sort User 0)"))
    assert(out.contains("(declare-fun age (User) Int)"))
    assert(out.contains("(assert (forall ((u User)) (>= (age u) 0)))"))
    assert(out.endsWith("(check-sat)\n"))

  test("renderExpr covers every Z3Expr kind"):
    import Z3Expr.*
    assertEquals(SmtLib.renderExpr(IntLit(42)), "42")
    assertEquals(SmtLib.renderExpr(IntLit(-7)), "(- 7)")
    assertEquals(SmtLib.renderExpr(BoolLit(true)), "true")
    assertEquals(SmtLib.renderExpr(BoolLit(false)), "false")
    assertEquals(SmtLib.renderExpr(And(Nil)), "true")
    assertEquals(SmtLib.renderExpr(Or(Nil)), "false")
    assertEquals(SmtLib.renderExpr(And(List(BoolLit(true)))), "true")
    assertEquals(
      SmtLib.renderExpr(And(List(BoolLit(true), BoolLit(false)))),
      "(and true false)"
    )
    assertEquals(SmtLib.renderExpr(Not(BoolLit(true))), "(not true)")
    assertEquals(
      SmtLib.renderExpr(Implies(BoolLit(true), BoolLit(false))),
      "(=> true false)"
    )
    assertEquals(
      SmtLib.renderExpr(Cmp(CmpOp.Eq, IntLit(1), IntLit(2))),
      "(= 1 2)"
    )
    assertEquals(
      SmtLib.renderExpr(Cmp(CmpOp.Neq, IntLit(1), IntLit(2))),
      "(distinct 1 2)"
    )
    assertEquals(
      SmtLib.renderExpr(Arith(ArithOp.Add, List(IntLit(1), IntLit(2), IntLit(3)))),
      "(+ 1 2 3)"
    )
    assertEquals(SmtLib.renderExpr(App("foo", Nil)), "foo")
    assertEquals(SmtLib.renderExpr(App("foo", List(IntLit(5)))), "(foo 5)")
