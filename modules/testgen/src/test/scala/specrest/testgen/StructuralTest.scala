package specrest.testgen

import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

class StructuralTest extends CatsEffectSuite:

  private def loadProfiled(path: String) =
    val src = scala.util.Using.resource(scala.io.Source.fromFile(path)): source =>
      source.getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => Annotate.buildProfiledService(ir, "python-fastapi-postgres")
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("url_shortener: imports schemathesis and re-uses M5.1 client + predicates"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(out.file.contains("import schemathesis"), out.file)
      assert(out.file.contains("from tests.conftest import client"))
      assert(out.file.contains("from tests.predicates import is_valid_email, is_valid_uri"))
      assert(out.file.contains("schema = schemathesis.openapi.from_path(\"openapi.yaml\")"))

  test("url_shortener: profile env-var with three named tiers"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(out.file.contains("PROFILE = os.environ.get(\"SPEC_TEST_PROFILE\", \"thorough\")"))
      assert(out.file.contains("\"smoke\":"))
      assert(out.file.contains("\"thorough\":"))
      assert(out.file.contains("\"exhaustive\":"))
      assert(out.file.contains("\"max_examples\": 1000"))
      assert(out.file.contains("\"stateful_step_count\": 25"))

  test("url_shortener: parametrize body resets state then validates with checks"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out         = Structural.emitFor(profiled)
      val resetIdx    = out.file.indexOf("client.post(\"/__test_admin__/reset\")")
      val callIdx     = out.file.indexOf("response = case.call(base_url=BASE_URL)")
      val validateIdx = out.file.indexOf("case.validate_response(response, checks=_ALL_CHECKS)")
      assert(resetIdx >= 0, s"missing reset call:\n${out.file}")
      assert(callIdx > resetIdx, "reset must precede the call")
      assert(validateIdx > callIdx, "validate must follow the call")

  test("url_shortener: emits one global-invariant check per translatable invariant"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(out.file.contains("def _check_invariant_all_ur_ls_valid(response, case):"))
      assert(out.file.contains("def _check_invariant_metadata_consistent(response, case):"))
      assert(out.file.contains("def _check_invariant_click_count_non_negative(response, case):"))
      assert(out.file.contains("post_state = client.get(\"/__test_admin__/state\").json()"))

  test("url_shortener: invariant checks gated by status < 500"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(
        out.file.contains("if response.status_code >= 500:"),
        "invariant check must skip when SUT returned 5xx (no useful state)"
      )

  test("todo_list: ensures-checks for Create operations are gated by (path, method, status)"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(
        out.file.contains("def _check_create_todo_ensures_"),
        s"expected at least one _check_create_todo_ensures_<i>; got file:\n${out.file}"
      )
      val createTodoChecks = out.file.linesIterator
        .dropWhile(!_.contains("def _check_create_todo_ensures_"))
        .takeWhile(l => !l.startsWith("def ") || l.contains("_check_create_todo_ensures_"))
        .mkString("\n")
      assert(createTodoChecks.nonEmpty, "create_todo check region should not be empty")
      assert(
        createTodoChecks.contains("_path_matches(case,"),
        s"create_todo ensures must gate via _path_matches:\n$createTodoChecks"
      )
      assert(
        createTodoChecks.contains("if response.status_code != "),
        s"create_todo ensures must gate on success status:\n$createTodoChecks"
      )

  test("url_shortener: non-pure-output ensures clauses appear in skips with a reason"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      val shortenSkips = out.skips.filter(s =>
        s.operation == "Shorten" && s.kind.startsWith("structural_ensures")
      )
      assert(
        shortenSkips.nonEmpty,
        s"Shorten has 6 ensures clauses, all reference pre()/prime()/state and should be skipped:\n${out.skips}"
      )
      assert(
        shortenSkips.exists(_.reason.contains("pre()/prime()")),
        s"at least one Shorten skip should cite pre()/prime():\n$shortenSkips"
      )

  test("url_shortener: emits an as_state_machine() Links class"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(out.file.contains("schema.as_state_machine()"))
      assert(out.file.contains("TestStructuralLinksUrlShortener = "))

  test("safe_counter: no entities → no Links state machine emitted, but file is valid"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(
        !out.file.contains("schema.as_state_machine()"),
        s"safe_counter has no entities; should not emit a Links state machine:\n${out.file}"
      )
      assert(out.file.contains("def test_api_structural(case):"))
      assert(out.file.contains("def _check_invariant_count_non_negative(response, case):"))

  test("invariant skips use <invariants> sentinel, not <service>"):
    val ir = specrest.ir.ServiceIR(
      name = "X",
      invariants = List(
        specrest.ir.InvariantDecl(
          name = Some("badInv"),
          expr = specrest.ir.Expr.SetComprehension(
            "x",
            specrest.ir.Expr.Identifier("nonsense"),
            specrest.ir.Expr.BoolLit(true)
          )
        )
      )
    )
    val profile  = specrest.profile.Annotate.buildProfiledService(ir, "python-fastapi-postgres")
    val out      = Structural.emitFor(profile)
    val invSkips = out.skips.filter(_.kind.startsWith("structural_invariant"))
    assert(invSkips.nonEmpty, s"expected at least one invariant skip; got ${out.skips}")
    assert(
      invSkips.forall(_.operation == "<invariants>"),
      s"all invariant skips should use <invariants> sentinel; got ${invSkips}"
    )

  test("base_url plumbed from SPEC_TEST_BASE_URL env-var with localhost default"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(
        out.file.contains(
          "BASE_URL = os.environ.get(\"SPEC_TEST_BASE_URL\", \"http://localhost:8000\")"
        )
      )

  test("invalid SPEC_TEST_PROFILE raises a helpful ValueError, not a KeyError"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Structural.emitFor(profiled)
      assert(
        out.file.contains("if PROFILE not in PROFILES:"),
        s"missing explicit-validation guard:\n${out.file}"
      )
      assert(
        out.file.contains("raise ValueError("),
        s"missing ValueError raise:\n${out.file}"
      )
      assert(
        out.file.contains("Invalid SPEC_TEST_PROFILE="),
        s"ValueError message should name the env-var:\n${out.file}"
      )
