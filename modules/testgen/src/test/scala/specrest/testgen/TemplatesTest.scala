package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.BinOp
import specrest.ir.Expr
import specrest.ir.FunctionDecl
import specrest.ir.ParamDecl
import specrest.ir.PredicateDecl
import specrest.ir.ServiceIR
import specrest.ir.TypeExpr

class TemplatesTest extends CatsEffectSuite:

  test("conftest template loads and contains the admin-availability fixture"):
    assert(Templates.conftest.contains("_admin_endpoint_available"))
    assert(Templates.conftest.contains("/__test_admin__/reset"))
    assert(Templates.conftest.contains("ENABLE_TEST_ADMIN"))

  test("predicates static template provides is_valid_uri / is_valid_email / _powerset"):
    assert(Templates.predicatesStaticTemplate.contains("def is_valid_uri"))
    assert(Templates.predicatesStaticTemplate.contains("def is_valid_email"))
    assert(Templates.predicatesStaticTemplate.contains("urlparse"))
    assert(Templates.predicatesStaticTemplate.contains("def _powerset"))

  test("predicates(ir) returns the static template when no user defs exist"):
    val ir = ServiceIR(name = "Empty")
    assertEquals(Templates.predicates(ir), Templates.predicatesStaticTemplate)

  test("predicates(ir) appends user-defined functions and predicates as Python defs"):
    val fn = FunctionDecl(
      name = "doubleIt",
      params = List(ParamDecl("n", TypeExpr.NamedType("Int"))),
      returnType = TypeExpr.NamedType("Int"),
      body = Expr.BinaryOp(BinOp.Mul, Expr.Identifier("n"), Expr.IntLit(2))
    )
    val pr = PredicateDecl(
      name = "isPositive",
      params = List(ParamDecl("x", TypeExpr.NamedType("Int"))),
      body = Expr.BinaryOp(BinOp.Gt, Expr.Identifier("x"), Expr.IntLit(0))
    )
    val ir  = ServiceIR(name = "Demo", functions = List(fn), predicates = List(pr))
    val out = Templates.predicates(ir)
    assert(out.contains("def double_it(n):\n    return ((n) * (2))"))
    assert(out.contains("def is_positive(x):\n    return ((x) > (0))"))

  test("predicates(ir) emits NotImplementedError stub for untranslatable bodies"):
    val pr = PredicateDecl(
      name = "weird",
      params = List(ParamDecl("x", TypeExpr.NamedType("Int"))),
      body = Expr.Identifier("undeclared_global")
    )
    val ir  = ServiceIR(name = "Demo", predicates = List(pr))
    val out = Templates.predicates(ir)
    assert(out.contains("def weird(x):"))
    assert(out.contains("raise NotImplementedError"))

  test("pytest.ini disables xdist parallelism (matches plan risk #4 mitigation)"):
    assert(Templates.pytestIni.contains("-p no:xdist"))
