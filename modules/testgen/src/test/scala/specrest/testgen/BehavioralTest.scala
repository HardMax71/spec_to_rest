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
      assert(resolveNeg.body.contains("client.get(f\"/{code}\")"), resolveNeg.body)

  test("Operation with no inputs has no @given decorator"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out  = Behavioral.emitFor(profiled)
      val test = out.tests.find(_.name == "test_increment_ensures_0").getOrElse(fail("missing"))
      assert(
        !test.body.contains("@given("),
        s"Increment has no inputs; should not have @given:\n${test.body}"
      )

  test("invariant test captures post_state via a separate /state call after the op"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val invariantTest = out.tests
        .find(_.name.startsWith("test_shorten_invariant_"))
        .getOrElse(fail("no invariant test for shorten"))
      val preIdx  = invariantTest.body.indexOf("pre_state = client.get")
      val reqIdx  = invariantTest.body.indexOf("response = client.")
      val postIdx = invariantTest.body.indexOf("post_state = client.get")
      assert(preIdx >= 0 && reqIdx >= 0 && postIdx >= 0, invariantTest.body)
      assert(
        preIdx < reqIdx && reqIdx < postIdx,
        s"expected pre_state < response < post_state ordering"
      )
      assert(
        !invariantTest.body.contains("post_state = pre_state"),
        s"post_state must not alias pre_state"
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

  private def sensitiveInputSpec(inputName: String, conventionsBlock: String): String =
    s"""|service AuthLite {
        |  state {}
        |
        |  entity User {
        |    id: Id
        |    email: String
        |  }
        |
        |  operation Register {
        |    input: email: String, $inputName: String
        |    requires: true
        |    ensures: true
        |  }
        |
        |$conventionsBlock
        |}
        |""".stripMargin

  private def profileSource(label: String, src: String) =
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => Annotate.buildProfiledService(ir, "python-fastapi-postgres")
          case Left(err) => fail(s"build error for $label: $err")
      case Left(err) => fail(s"parse error for $label: $err")

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
        s"raw st.text() must not appear for sensitive password by default"
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
        registerTests.exists(t => t.body.contains("password=st.text()")),
        s"expected bare st.text() under live override; tests=\n${registerTests.map(_.body).mkString("\n---\n")}"
      )
      assert(
        registerTests.forall(t => !t.body.contains("password=redact(")),
        s"redact() should NOT appear on Register.password under live override"
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

  // ---------- M5.9: TransitionDecl-aware property tests ----------

  test("M5.9: todo_list StartWork emits 1 positive (TODO->IN_PROGRESS) + 3 illegal-from tests"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out        = Behavioral.emitFor(profiled)
      val startTests = out.tests.filter(_.name.startsWith("test_start_work_transition_"))
      val names      = startTests.map(_.name).sorted
      assertEquals(
        names,
        List(
          "test_start_work_transition_illegal_from_archived",
          "test_start_work_transition_illegal_from_done",
          "test_start_work_transition_illegal_from_in_progress",
          "test_start_work_transition_todo_to_in_progress"
        )
      )

  test("M5.9: todo_list Archive emits 2 positive + 2 illegal-from tests"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out          = Behavioral.emitFor(profiled)
      val archiveTests = out.tests.filter(_.name.startsWith("test_archive_transition_"))
      val names        = archiveTests.map(_.name).sorted
      assertEquals(
        names,
        List(
          "test_archive_transition_done_to_archived",
          "test_archive_transition_illegal_from_archived",
          "test_archive_transition_illegal_from_in_progress",
          "test_archive_transition_todo_to_archived"
        )
      )

  test("#152: todo_list Reopen guard recognized: positive test emitted with shift fix-up"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out         = Behavioral.emitFor(profiled)
      val reopenTests = out.tests.filter(_.name.startsWith("test_reopen_transition_"))
      val names       = reopenTests.map(_.name).sorted
      assertEquals(
        names,
        List(
          "test_reopen_transition_done_to_in_progress",
          "test_reopen_transition_illegal_from_archived",
          "test_reopen_transition_illegal_from_in_progress",
          "test_reopen_transition_illegal_from_todo"
        )
      )
      val positive = reopenTests
        .find(_.name == "test_reopen_transition_done_to_in_progress")
        .getOrElse(fail("missing positive Reopen test"))
      assert(
        positive.body.contains("if row[\"completed_at\"] is None: row[\"completed_at\"] ="),
        s"expected Option-anchor for completed_at; body=${positive.body}"
      )
      assert(
        positive.body.contains(
          "row[\"updated_at\"] = (datetime.datetime.fromisoformat(row[\"completed_at\"])"
        ),
        s"expected datetime arithmetic shift; body=${positive.body}"
      )
      assert(
        positive.body.contains("+ datetime.timedelta(seconds=1)).isoformat()"),
        s"expected +1s shift; body=${positive.body}"
      )
      val guardSkips = out.skips.filter: s =>
        s.operation == "Reopen" && s.kind.startsWith("transition[")
      assertEquals(guardSkips, Nil, s"expected no guard skip after #152; got=$guardSkips")

  test("M5.9: positive transition test seeds entity, asserts post-state field"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val test = out.tests
        .find(_.name == "test_start_work_transition_todo_to_in_progress")
        .getOrElse(fail("missing positive transition test"))
      assert(test.body.contains("@given(row=strategy_todo())"), s"body=${test.body}")
      assert(test.body.contains("client.post(\"/__test_admin__/reset\")"))
      assert(test.body.contains("row[\"status\"] = \"TODO\""), s"body=${test.body}")
      assert(
        test.body.contains("client.post(\"/__test_admin__/seed/todo\""),
        s"body=${test.body}"
      )
      assert(test.body.contains("seeded_id = seed.json()[\"id\"]"))
      assert(
        test.body.contains("client.post(f\"/todos/{seeded_id}/start\")"),
        s"body=${test.body}"
      )
      assert(test.body.contains("post_state.get(\"todos\""), s"body=${test.body}")
      assert(test.body.contains("\"IN_PROGRESS\""), s"body=${test.body}")

  test("M5.9: negative transition test asserts 4xx on illegal from"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val test = out.tests
        .find(_.name == "test_start_work_transition_illegal_from_done")
        .getOrElse(fail("missing negative transition test"))
      assert(test.body.contains("row[\"status\"] = \"DONE\""), s"body=${test.body}")
      assert(test.body.contains("400 <= response.status_code < 500"), s"body=${test.body}")

  test("M5.9: safe_counter (no transitions) emits no transition tests"):
    loadProfiled("fixtures/spec/safe_counter.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      assertEquals(out.tests.filter(_.name.contains("_transition_")), Nil)

  test("M5.9: url_shortener (no transitions) emits no transition tests"):
    loadProfiled("fixtures/spec/url_shortener.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      assertEquals(out.tests.filter(_.name.contains("_transition_")), Nil)

  test("M5.9: state-dep skip on a transition op now reads 'covered by transition tests'"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val startWorkSkips = out.skips.filter: s =>
        s.operation == "StartWork" && s.kind == "ensures"
      assert(
        startWorkSkips.exists(_.reason.contains("covered by transition tests (M5.9)")),
        s"expected updated skip text on StartWork.ensures; got=$startWorkSkips"
      )
      val getTodoSkips = out.skips.filter: s =>
        s.operation == "GetTodo" && s.kind == "ensures"
      assert(
        getTodoSkips.exists(_.reason.contains("deferred to M5.9")),
        s"non-transition op should retain deferred-to-M5.9 skip; got=$getTodoSkips"
      )

  // ---------- M5.9 PR #154 review fixes ----------

  test("M5.9 fix D: unknown-via skip uses viaName (not TransitionDecl name) as operation"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out     = Behavioral.emitFor(profiled)
      val pwSkips = out.skips.filter(s => s.kind == "transition[PauseWork]")
      assert(pwSkips.nonEmpty, s"expected PauseWork transition skip; got skips=${out.skips}")
      assertEquals(
        pwSkips.map(_.operation).distinct,
        List("PauseWork"),
        "skip's operation field must be the via name, not the TransitionDecl name"
      )

  test("M5.9 fix E: ecommerce RecordPayment (body input) skips with #155 reference"):
    loadProfiled("fixtures/spec/ecommerce.spec").map: profiled =>
      val out     = Behavioral.emitFor(profiled)
      val rpTests = out.tests.filter(_.name.startsWith("test_record_payment_transition_"))
      assertEquals(
        rpTests,
        Nil,
        s"RecordPayment has body input; transition tests must be skipped, got: ${rpTests.map(_.name)}"
      )
      val rpSkip = out.skips.find: s =>
        s.operation == "RecordPayment" && s.kind == "transition[RecordPayment]"
      assert(
        rpSkip.nonEmpty,
        s"expected RecordPayment transition skip; got=${out.skips.map(s => s.operation -> s.kind)}"
      )
      assert(
        rpSkip.get.reason.contains("path input") && rpSkip.get.reason.contains("#155"),
        s"skip reason must reference path-input shape and #155; got=${rpSkip.get.reason}"
      )

  test("M5.9 fix E: todo_list (path-only via ops) is unaffected by the skip"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out        = Behavioral.emitFor(profiled)
      val startTests = out.tests.filter(_.name.startsWith("test_start_work_transition_"))
      assert(
        startTests.nonEmpty,
        "todo_list StartWork has only path input; should still emit tests"
      )
      assert(
        out.skips.forall(s => !s.reason.contains("require exactly one path input")),
        s"todo_list shouldn't trigger path-input-shape skip; got=${out.skips}"
      )

  test("M5.9 fix H: filtered-out via op does NOT claim 'covered by transition tests'"):
    loadProfiled("fixtures/spec/ecommerce.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val rpEnsuresSkip = out.skips.find: s =>
        s.operation == "RecordPayment" && s.kind == "ensures"
      assert(rpEnsuresSkip.nonEmpty, s"expected RecordPayment ensures skip; got=${out.skips}")
      assert(
        !rpEnsuresSkip.get.reason.contains("covered by transition tests"),
        s"RecordPayment was filtered out — must NOT claim coverage; got=${rpEnsuresSkip.get.reason}"
      )
      assert(
        rpEnsuresSkip.get.reason.contains("deferred to M5.9"),
        s"filtered-out via op should fall back to deferred reason; got=${rpEnsuresSkip.get.reason}"
      )

  test("M5.9 fix H: emitted transition op DOES claim 'covered by transition tests'"):
    loadProfiled("fixtures/spec/todo_list.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val swEnsuresSkip = out.skips.find: s =>
        s.operation == "StartWork" && s.kind == "ensures"
      assert(swEnsuresSkip.nonEmpty, s"expected StartWork ensures skip; got=${out.skips}")
      assert(
        swEnsuresSkip.get.reason.contains("covered by transition tests (M5.9)"),
        s"StartWork emits transition tests — should claim coverage; got=${swEnsuresSkip.get.reason}"
      )

  // ---------- #152: guarded positive transition tests ----------

  private def loadProfiledFromSpec(spec: String) =
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => Annotate.buildProfiledService(ir, "python-fastapi-postgres")
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("#152: numeric > guard yields row[a] = row[b] + 1 fix-up"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |    a: Int
         |    b: Int
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when a > b
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in counters
         |    ensures: counters'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/counters/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; tests=${out.tests.map(_.name)}; skips=${out.skips}"))
      assert(pos.body.contains("row[\"a\"] = row[\"b\"] + 1"), s"body=${pos.body}")

  test("#152: enum equality guard yields row[a] = \"VALUE\" fix-up"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  enum Tier  { GOLD, SILVER }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |    tier: Tier
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when tier = GOLD
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in counters
         |    ensures: counters'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/counters/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; tests=${out.tests.map(_.name)}; skips=${out.skips}"))
      assert(pos.body.contains("row[\"tier\"] = \"GOLD\""), s"body=${pos.body}")

  test("#152: conjunction recognized when both sides are recognized shapes"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  enum Tier  { GOLD, SILVER }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |    tier: Tier
         |    a: Int
         |    b: Int
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when a > b and tier = GOLD
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in counters
         |    ensures: counters'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/counters/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; tests=${out.tests.map(_.name)}; skips=${out.skips}"))
      assert(pos.body.contains("row[\"a\"] = row[\"b\"] + 1"), s"body=${pos.body}")
      assert(pos.body.contains("row[\"tier\"] = \"GOLD\""), s"body=${pos.body}")

  test("#152: unrecognized guard shape still skips with #152 reason"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |    a: Int
         |    b: Int
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when a + 1 > b
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in counters
         |    ensures: counters'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/counters/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out  = Behavioral.emitFor(profiled)
      val skip = out.skips.find(s => s.operation == "Promote" && s.kind.startsWith("transition["))
      assert(skip.nonEmpty, s"expected guard skip; skips=${out.skips}")
      assert(skip.get.reason.contains("#152"), s"reason=${skip.get.reason}")

  test("#152: guard touching the transition field itself is rejected (skipped)"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |    other: Phase
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when phase = LOW
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in counters
         |    ensures: counters'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/counters/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out  = Behavioral.emitFor(profiled)
      val skip = out.skips.find(s => s.operation == "Promote" && s.kind.startsWith("transition["))
      assert(skip.nonEmpty, s"expected skip when guard touches transition field; got=${out.skips}")

  test("#152: not-none guard yields if-None anchor for Option[DateTime]"):
    val spec =
      """|service Demo {
         |  enum Phase { OPEN, CLOSED }
         |  entity Ticket {
         |    id: Int
         |    phase: Phase
         |    closed_at: Option[DateTime]
         |  }
         |  state {
         |    tickets: Int -> lone Ticket
         |  }
         |  transition TicketLifecycle {
         |    entity: Ticket
         |    field: phase
         |    OPEN -> CLOSED via Close when closed_at != none
         |  }
         |  operation Close {
         |    input: id: Int
         |    requires: id in tickets
         |    ensures: tickets'[id].phase = CLOSED
         |  }
         |  conventions {
         |    Close.http_method = "POST"
         |    Close.http_path   = "/tickets/{id}/close"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_close_transition_open_to_closed")
        .getOrElse(fail(s"missing positive; tests=${out.tests.map(_.name)}; skips=${out.skips}"))
      assert(
        pos.body.contains("if row[\"closed_at\"] is None: row[\"closed_at\"] ="),
        s"body=${pos.body}"
      )
