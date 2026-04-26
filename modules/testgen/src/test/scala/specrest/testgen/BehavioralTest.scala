package specrest.testgen

import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

class BehavioralTest extends CatsEffectSuite:

  private def loadProfiled(path: String) =
    val src = scala.io.Source.fromFile(path).getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => Annotate.buildProfiledService(ir, "python-fastapi-postgres")
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("safe_counter: Increment positive ensures test, Decrement skipped"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out      = Behavioral.emitFor(profiled)
      val incTests = out.tests.filter(_.name.contains("increment"))
      assert(incTests.exists(_.name == "test_increment_ensures_0"), out.tests.map(_.name).toString)
      val decSkips = out.skips.filter(_.operation == "Decrement")
      assert(
        decSkips.exists(_.reason.contains("state-dependent precondition")),
        decSkips.toString
      )

  test("Increment ensures body has expected structure"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out  = Behavioral.emitFor(profiled)
      val test = out.tests.find(_.name == "test_increment_ensures_0").getOrElse(fail("missing"))
      assert(test.body.contains("client.post(\"/__test_admin__/reset\")"))
      assert(test.body.contains("pre_state = client.get(\"/__test_admin__/state\")"))
      assert(test.body.contains("post_state = client.get(\"/__test_admin__/state\")"))
      assert(test.body.contains("response = client."), s"body=${test.body}")
      assert(test.body.contains("post_state[\"count\"]"))
      assert(test.body.contains("pre_state[\"count\"]"))

  test("url_shortener: Shorten ensures generated, Resolve+Delete state-dep skipped"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out        = Behavioral.emitFor(profiled)
      val shortenEns = out.tests.filter(_.name.startsWith("test_shorten_ensures"))
      assert(shortenEns.nonEmpty, out.tests.map(_.name).toString)
      val resolveEns = out.tests.filter(_.name.startsWith("test_resolve_ensures"))
      assertEquals(resolveEns, Nil, "Resolve has state-dep requires; ensures must skip")
      val deleteEns = out.tests.filter(_.name.startsWith("test_delete_ensures"))
      assertEquals(deleteEns, Nil)

  test("url_shortener: Resolve and Delete get key-existence negative tests"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val resolveNeg = out.tests
        .find(_.name == "test_resolve_negative_code_not_in_store")
        .getOrElse(fail(s"missing resolve negative; tests=${out.tests.map(_.name)}"))
      assert(resolveNeg.body.contains("assume(code not in pre_state.get(\"store\""))
      assert(resolveNeg.body.contains("(404, 409, 422)"))
      val deleteNeg = out.tests.find(_.name == "test_delete_negative_code_not_in_store")
      assert(deleteNeg.isDefined)

  test("invariants emitted for ops without state-dep requires"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out      = Behavioral.emitFor(profiled)
      val invTests = out.tests.filter(_.name.contains("_invariant_"))
      assert(
        invTests.exists(_.name.contains("shorten_invariant_all_ur_ls_valid")) ||
          invTests.exists(_.name.contains("shorten_invariant_all_urls_valid")) ||
          invTests.exists(_.name.contains("shorten_invariant_allurlsvalid")) ||
          invTests.exists(t => t.name.startsWith("test_shorten_invariant_")),
        s"no shorten invariant tests; got ${invTests.map(_.name)}"
      )

  test("HTTP method + path are correctly translated"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val resolveNeg = out.tests
        .find(_.name == "test_resolve_negative_code_not_in_store")
        .getOrElse(fail("missing"))
      assert(resolveNeg.body.contains("client.get(f\"/{code}\")"), resolveNeg.body)

  test("Operation with no inputs has no @given decorator"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out  = Behavioral.emitFor(profiled)
      val test = out.tests.find(_.name == "test_increment_ensures_0").getOrElse(fail("missing"))
      assert(
        !test.body.contains("@given("),
        s"Increment has no inputs; should not have @given:\n${test.body}"
      )
