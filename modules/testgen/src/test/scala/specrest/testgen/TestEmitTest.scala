package specrest.testgen

import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

class TestEmitTest extends CatsEffectSuite:

  private def loadProfiled(path: String) =
    val src = scala.io.Source.fromFile(path).getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => Annotate.buildProfiledService(ir, "python-fastapi-postgres")
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("emit produces 13 files at the locked paths"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val files = TestEmit.emit(profiled)
      val paths = files.map(_.path).toSet
      assertEquals(
        paths,
        Set(
          "app/routers/test_admin.py",
          "tests/__init__.py",
          "tests/conftest.py",
          "tests/predicates.py",
          "tests/redaction.py",
          "tests/strategies.py",
          "tests/strategies_user.py",
          "tests/test_behavioral_url_shortener.py",
          "tests/test_stateful_url_shortener.py",
          "tests/test_structural_url_shortener.py",
          "tests/run_conformance.py",
          "tests/_testgen_skips.json",
          "pytest.ini"
        )
      )

  test("strategies.py imports predicates and defines one strategy per type-alias/enum"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val files      = TestEmit.emit(profiled)
      val strategies = files.find(_.path == "tests/strategies.py").get.content
      assert(strategies.contains("from tests.predicates import is_valid_email, is_valid_uri"))
      assert(strategies.contains("def strategy_short_code():"))
      assert(strategies.contains("def strategy_long_url():"))
      assert(strategies.contains("def strategy_base_url():"))

  test("behavioral test file imports the strategies and conftest client"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val files = TestEmit.emit(profiled)
      val beh   = files.find(_.path == "tests/test_behavioral_url_shortener.py").get.content
      assert(beh.contains("from tests.conftest import client"))
      assert(beh.contains("from tests.strategies import"))
      assert(beh.contains("strategy_short_code"))
      assert(beh.contains("def test_shorten_ensures_"), s"no shorten ensures test:\n$beh")

  test("behavioral file for safe_counter contains Increment ensures, no @given"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val files = TestEmit.emit(profiled)
      val beh   = files.find(_.path == "tests/test_behavioral_safe_counter.py").get.content
      assert(beh.contains("def test_increment_ensures_0("))
      assert(!beh.contains("@given("), "Increment has no inputs; should have no @given")

  test("_testgen_skips.json is well-formed and contains no ExprToPython coverage skips"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val files = TestEmit.emit(profiled)
      val skips = files.find(_.path == "tests/_testgen_skips.json").get.content
      assert(skips.startsWith("{"))
      assert(skips.contains("\"service\": \"UrlShortener\""))
      assert(skips.contains("\"behavioral_skipped\""))
      assert(skips.contains("\"structural_skipped\""))
      assert(
        !skips.contains("MapLiteral") && !skips.contains("SetComprehension") &&
          !skips.contains("With (record update)") && !skips.contains("Constructor("),
        s"ExprToPython coverage skips reappeared:\n$skips"
      )
      val parsed = io.circe.parser.parse(skips)
      assert(parsed.isRight, s"not valid JSON: ${parsed.left.toOption}")

  test("admin router emitted at the expected file path with /__test_admin__ prefix"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val files = TestEmit.emit(profiled)
      val admin = files.find(_.path == "app/routers/test_admin.py").get.content
      assert(admin.contains("prefix=\"/__test_admin__\""))
      assert(admin.contains("ENABLE_TEST_ADMIN"))

  test("conftest, predicates, pytest.ini, run_conformance are byte-identical to bundled templates"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val files = TestEmit.emit(profiled)
      assertEquals(files.find(_.path == "tests/conftest.py").get.content, Templates.conftest)
      assertEquals(
        files.find(_.path == "tests/predicates.py").get.content,
        Templates.predicates(profiled.ir)
      )
      assertEquals(files.find(_.path == "pytest.ini").get.content, Templates.pytestIni)
      assertEquals(
        files.find(_.path == "tests/run_conformance.py").get.content,
        Templates.runConformance
      )

  test("run_conformance.py orchestrates all three phases with JUnit XML"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val files  = TestEmit.emit(profiled)
      val runner = files.find(_.path == "tests/run_conformance.py").get.content
      assert(runner.contains("tests/test_structural_*.py"))
      assert(runner.contains("tests/test_behavioral_*.py"))
      assert(runner.contains("tests/test_stateful_*.py"))
      assert(runner.contains("--junitxml="))
      assert(runner.contains("SPEC_TEST_PROFILE"))
      assert(runner.contains("/__test_admin__/reset"))

  test("run_conformance.py distinguishes infra failures (exit 2) from test failures (exit 1)"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val files  = TestEmit.emit(profiled)
      val runner = files.find(_.path == "tests/run_conformance.py").get.content
      assert(runner.contains("class Outcome"))
      assert(runner.contains("INFRA"))
      assert(runner.contains("return 2"))
      assert(runner.contains("return 1"))
      assert(runner.contains("return 0"))

  test("safe_counter has no strategies (no type aliases or enums)"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val files      = TestEmit.emit(profiled)
      val strategies = files.find(_.path == "tests/strategies.py").get.content
      assert(strategies.contains("# No strategies generated"))
      val beh = files.find(_.path == "tests/test_behavioral_safe_counter.py").get.content
      assert(
        !beh.contains("from tests.strategies import"),
        "no strategies, so no import line should appear"
      )
