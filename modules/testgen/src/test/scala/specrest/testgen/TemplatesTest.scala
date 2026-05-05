package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse

class TemplatesTest extends CatsEffectSuite:

  private def loadIR(specSrc: String) =
    Parse.parseSpec(specSrc).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("conftest template loads and contains the admin-availability fixture"):
    assert(Templates.conftest.contains("_admin_endpoint_available"))
    assert(Templates.conftest.contains("/__test_admin__/reset"))
    assert(Templates.conftest.contains("ENABLE_TEST_ADMIN"))

  test("predicates(ir) renders preamble predicates (is_valid_uri / is_valid_email) + _powerset"):
    loadIR("service Empty {}").map: ir =>
      val out = Templates.predicates(ir)
      assert(out.contains("def is_valid_uri"), s"missing is_valid_uri:\n$out")
      assert(out.contains("def is_valid_email"), s"missing is_valid_email:\n$out")
      assert(out.contains("def _powerset"), s"missing _powerset:\n$out")
      assert(out.contains("import re"), s"missing re import:\n$out")
      assert(out.contains("import itertools"), s"missing itertools import:\n$out")
      assert(out.contains("re.fullmatch"), s"expected regex translation:\n$out")

  test("predicates(ir) appends user-defined functions and predicates as Python defs"):
    IO:
      val fn = FunctionDeclFull(
        name = "doubleIt",
        params = List(ParamDeclFull("n", NamedTypeF("Int"))),
        returnType = NamedTypeF("Int"),
        body = BinaryOpF(BMul(), IdentifierF("n"), IntLitF(int_of_integer(BigInt(2)), None))
      )
      val pr = PredicateDeclFull(
        name = "isPositive",
        params = List(ParamDeclFull("x", NamedTypeF("Int"))),
        body = BinaryOpF(BGt(), IdentifierF("x"), IntLitF(int_of_integer(BigInt(0)), None))
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn), predicates = List(pr))
      val out = Templates.predicates(ir)
      assert(out.contains("def double_it(n):\n    return ((n) * (2))"))
      assert(out.contains("def is_positive(x):\n    return ((x) > (0))"))

  test("predicates(ir) preserves original parameter names (no snake_case mismatch)"):
    IO:
      val fn = FunctionDeclFull(
        name = "double",
        params = List(ParamDeclFull("camelCase", NamedTypeF("Int"))),
        returnType = NamedTypeF("Int"),
        body = BinaryOpF(BMul(), IdentifierF("camelCase"), IntLitF(int_of_integer(BigInt(2)), None))
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn))
      val out = Templates.predicates(ir)
      assert(
        out.contains("def double(camelCase):\n    return ((camelCase) * (2))"),
        s"signature/body identifier mismatch:\n$out"
      )

  test("predicates(ir) stubs functions whose snake-cased name is a Python keyword"):
    IO:
      val fn = FunctionDeclFull(
        name = "Match",
        params = List(ParamDeclFull("s", NamedTypeF("String"))),
        returnType = NamedTypeF("Bool"),
        body = BoolLitF(true)
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn))
      val out = Templates.predicates(ir)
      assert(out.contains("def match_("), s"expected reserved-name escape:\n$out")
      assert(out.contains("Python-reserved name"))

  test("predicates(ir) stubs functions whose parameter is a Python keyword"):
    IO:
      val fn = FunctionDeclFull(
        name = "Foo",
        params = List(ParamDeclFull("class", NamedTypeF("Int"))),
        returnType = NamedTypeF("Int"),
        body = IntLitF(int_of_integer(BigInt(0)), None)
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn))
      val out = Templates.predicates(ir)
      assert(out.contains("def foo(class_):"), s"expected escaped param name in stub:\n$out")
      assert(out.contains("raise NotImplementedError"))
      assert(out.contains("Python-reserved name"))

  test("predicates(ir) escapes both reserved fn name AND reserved params (no SyntaxError)"):
    IO:
      val fn = FunctionDeclFull(
        name = "Match",
        params = List(
          ParamDeclFull("class", NamedTypeF("Int")),
          ParamDeclFull("ok", NamedTypeF("Int"))
        ),
        returnType = NamedTypeF("Int"),
        body = IntLitF(int_of_integer(BigInt(0)), None)
      )
      val ir  = ServiceIR(name = "Demo", functions = List(fn))
      val out = Templates.predicates(ir)
      assert(
        out.contains("def match_(class_, ok):"),
        s"both fn name and reserved param must be escaped in stub:\n$out"
      )

  test("predicates(ir) emits NotImplementedError stub for untranslatable bodies"):
    IO:
      val pr = PredicateDeclFull(
        name = "weird",
        params = List(ParamDeclFull("x", NamedTypeF("Int"))),
        body = IdentifierF("undeclared_global")
      )
      val ir  = ServiceIR(name = "Demo", predicates = List(pr))
      val out = Templates.predicates(ir)
      assert(out.contains("def weird(x):"))
      assert(out.contains("raise NotImplementedError"))

  test("pytest.ini disables xdist parallelism (matches plan risk #4 mitigation)"):
    assert(Templates.pytestIni.contains("-p no:xdist"))
