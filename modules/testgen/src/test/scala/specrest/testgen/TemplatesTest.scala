package specrest.testgen

import cats.effect.IO
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
    IO:
      val ir = ServiceIR(name = "Empty")
      assertEquals(Templates.predicates(ir), Templates.predicatesStaticTemplate)

  test("predicates(ir) appends user-defined functions and predicates as Python defs"):
    IO:
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

  test("predicates(ir) preserves original parameter names (no snake_case mismatch)"):
    IO:
      val fn = FunctionDecl(
        name = "double",
        params = List(ParamDecl("camelCase", TypeExpr.NamedType("Int"))),
        returnType = TypeExpr.NamedType("Int"),
        body = Expr.BinaryOp(BinOp.Mul, Expr.Identifier("camelCase"), Expr.IntLit(2))
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn))
      val out = Templates.predicates(ir)
      assert(
        out.contains("def double(camelCase):\n    return ((camelCase) * (2))"),
        s"signature/body identifier mismatch:\n$out"
      )

  test("predicates(ir) stubs functions whose snake-cased name is a Python keyword"):
    IO:
      val fn = FunctionDecl(
        name = "Match",
        params = List(ParamDecl("s", TypeExpr.NamedType("String"))),
        returnType = TypeExpr.NamedType("Bool"),
        body = Expr.BoolLit(true)
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn))
      val out = Templates.predicates(ir)
      assert(out.contains("def match_("), s"expected reserved-name escape:\n$out")
      assert(out.contains("Python-reserved name"))

  test("predicates(ir) stubs functions whose parameter is a Python keyword"):
    IO:
      val fn = FunctionDecl(
        name = "Foo",
        params = List(ParamDecl("class", TypeExpr.NamedType("Int"))),
        returnType = TypeExpr.NamedType("Int"),
        body = Expr.IntLit(0)
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn))
      val out = Templates.predicates(ir)
      assert(out.contains("raise NotImplementedError"))
      assert(out.contains("Python-reserved name"))

  test("predicates(ir) emits NotImplementedError stub for untranslatable bodies"):
    IO:
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
