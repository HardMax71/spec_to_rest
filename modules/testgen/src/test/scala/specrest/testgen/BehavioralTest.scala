package specrest.testgen

import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

class BehavioralTest extends BehavioralTestSupport:

  test("Finding 1: a fail-loud stub op (no synthesized body) is skipped and recorded"):
    val src = scala.io.Source.fromFile("fixtures/spec/url_shortener.spec").getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            val raw = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
            val out = Behavioral.emitFor(raw)
            assert(
              out.skips.exists(s =>
                s.operation == "Shorten" && s.reason.contains("fail-loud stub")
              ),
              s"expected Shorten stub-skip; skips=${out.skips}"
            )
            assert(
              !out.tests.exists(_.name.contains("shorten")),
              s"stub op must not emit positive tests; tests=${out.tests.map(_.name)}"
            )
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("safe_counter: Increment ensures emits via backed scalar (#407), Decrement state-dep"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      // `count` is an Int scalar backed by service_state since #407, so the
      // ensures asserts post_state["count"] black-box instead of honest-skipping.
      assert(
        out.tests.exists(_.name.startsWith("test_increment_ensures")),
        s"Increment.ensures must emit against the backed scalar: ${out.tests.map(_.name)}"
      )
      assert(
        !out.skips.exists(_.reason.contains("not backed by an entity table")),
        s"no unbacked-state skips expected; got ${out.skips}"
      )
      val decSkips = out.skips.filter(_.operation == "Decrement")
      assert(
        decSkips.exists(_.reason.contains("state-dependent precondition")),
        decSkips.toString
      )

  test("ensures body has expected admin-reset / state-capture / request structure"):
    loadProfiled("fixtures/spec/edge_cases.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val test = out.tests
        .find(_.name == "test_no_input_ensures_0")
        .getOrElse(fail(s"missing test_no_input_ensures_0; got ${out.tests.map(_.name)}"))
      assert(test.body.contains("client.post(\"/admin/reset\")"))
      assert(test.body.contains("pre_state = state_snapshot(_INT_KEYED_STATE)"))
      assert(test.body.contains("post_state = state_snapshot(_INT_KEYED_STATE)"))
      assert(test.body.contains("response = client."), s"body=${test.body}")

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
      assert(resolveNeg.body.contains("400 <= response.status_code < 500"))
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
      assert(
        resolveNeg.body.contains("request_without_redirects(client.get, f\"/{code}\")"),
        resolveNeg.body
      )

  test("Operation with no inputs has no @given decorator"):
    loadProfiled("fixtures/spec/edge_cases.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val test = out.tests
        .find(_.name == "test_no_input_ensures_0")
        .getOrElse(fail(s"missing test_no_input_ensures_0; got ${out.tests.map(_.name)}"))
      assert(
        !test.body.contains("@given("),
        s"NoInput has no inputs; should not have @given:\n${test.body}"
      )

  test("invariant test captures post_state via a separate /state call after the op"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val invariantTest = out.tests
        .find(_.name.startsWith("test_shorten_invariant_"))
        .getOrElse(fail("no invariant test for shorten"))
      val preIdx  = invariantTest.body.indexOf("pre_state = state_snapshot(")
      val reqIdx  = invariantTest.body.indexOf("response = client.")
      val postIdx = invariantTest.body.indexOf("post_state = state_snapshot(")
      assert(preIdx >= 0 && reqIdx >= 0 && postIdx >= 0, invariantTest.body)
      assert(
        preIdx < reqIdx && reqIdx < postIdx,
        "expected pre_state < response < post_state ordering"
      )
      assert(
        !invariantTest.body.contains("post_state = pre_state"),
        "post_state must not alias pre_state"
      )

  test("state-dep precondition records invariant skips per-invariant (not dropped silently)"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val resolveInvSkips =
        out.skips.filter(s => s.operation == "Resolve" && s.kind.startsWith("invariant["))
      assert(
        resolveInvSkips.nonEmpty,
        s"expected per-invariant skip records for Resolve; got: ${out.skips}"
      )

  test("negative-test 4xx assertion covers full 400..499 range, not the narrow set"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val test = out.tests
        .find(_.name == "test_resolve_negative_code_not_in_store")
        .getOrElse(fail("missing"))
      assert(test.body.contains("400 <= response.status_code < 500"), test.body)
      assert(!test.body.contains("(404, 409, 422)"))
  test("M5.8: sensitive operation input is wrapped in redact() in the @given decorator"):
    val src = sensitiveInputSpec("password", "")
    profileSource("sensitive-default", src).map: profiled =>
      val out           = Behavioral.emitFor(profiled)
      val registerTests = out.tests.filter(_.name.startsWith("test_register"))
      assert(
        registerTests.exists(t => t.body.contains("password=redact(")),
        s"expected redact() wrap on sensitive password input; tests=\n${registerTests.map(_.body).mkString("\n---\n")}"
      )
      assert(
        registerTests.forall(t => !t.body.contains("password=st.text()")),
        "raw st.text() must not appear for sensitive password by default"
      )

  test("M5.8: 'live' override removes redact wrapper for that operation"):
    val conventions =
      """|  conventions {
         |    Register.password.test_strategy = "live"
         |  }""".stripMargin
    val src = sensitiveInputSpec("password", conventions)
    profileSource("sensitive-live", src).map: profiled =>
      val out           = Behavioral.emitFor(profiled)
      val registerTests = out.tests.filter(_.name.startsWith("test_register"))
      assert(
        registerTests.exists(t =>
          t.body.contains("password=st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
        ),
        s"expected bare st.text() under live override; tests=\n${registerTests.map(_.body).mkString("\n---\n")}"
      )
      assert(
        registerTests.forall(t => !t.body.contains("password=redact(")),
        "redact() should NOT appear on Register.password under live override"
      )

  test("M5.8: 'redacted' override emits placeholder st.just literal on non-sensitive name"):
    val conventions =
      """|  conventions {
         |    Register.opaque.test_strategy = "redacted"
         |  }""".stripMargin
    val src = sensitiveInputSpec("opaque", conventions)
    profileSource("non-sensitive-redacted", src).map: profiled =>
      val out           = Behavioral.emitFor(profiled)
      val registerTests = out.tests.filter(_.name.startsWith("test_register"))
      assert(
        registerTests.exists(t => t.body.contains("opaque=st.just(\"***REDACTED***\")")),
        s"expected st.just placeholder under redacted override; tests=\n${registerTests.map(_.body).mkString("\n---\n")}"
      )
