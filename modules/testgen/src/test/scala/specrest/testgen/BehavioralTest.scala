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
        registerTests.exists(t => t.body.contains("password=st.text()")),
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
        getTodoSkips.exists(_.reason.contains("covered by stateful tests")),
        s"non-transition op should reference stateful-tests coverage; got=$getTodoSkips"
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

  test("#155: ecommerce RecordPayment illegal-from negatives now emit with @given(amount=…)"):
    loadProfiled("fixtures/spec/ecommerce.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val negNames = out.tests
        .map(_.name)
        .filter(_.startsWith("test_record_payment_transition_illegal_from_"))
        .toSet
      val expected = Set(
        "test_record_payment_transition_illegal_from_draft",
        "test_record_payment_transition_illegal_from_paid",
        "test_record_payment_transition_illegal_from_shipped",
        "test_record_payment_transition_illegal_from_delivered",
        "test_record_payment_transition_illegal_from_cancelled",
        "test_record_payment_transition_illegal_from_returned"
      )
      assertEquals(negNames, expected, s"actual=$negNames")
      val sample = out.tests
        .find(_.name == "test_record_payment_transition_illegal_from_draft")
        .getOrElse(fail("draft negative missing"))
      assert(
        sample.body.contains("@given(row=strategy_order(), amount=strategy_money())"),
        s"missing strategy_money() in @given; body=${sample.body}"
      )
      assert(
        sample.body.contains("def test_record_payment_transition_illegal_from_draft(row, amount):"),
        s"signature missing 'amount' param; body=${sample.body}"
      )
      assert(
        sample.body.contains(
          "client.post(f\"/orders/{seeded_id}/payments\", json={\"amount\": amount})"
        ),
        s"missing json={'amount': amount}; body=${sample.body}"
      )

  test("#155: RecordPayment positive PLACED→PAID skip references #152 (guard), not #155"):
    loadProfiled("fixtures/spec/ecommerce.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      assertEquals(
        out.tests.filter(_.name == "test_record_payment_transition_placed_to_paid"),
        Nil,
        "guarded positive should still skip via #152"
      )
      val rpPositiveSkip = out.skips.find: s =>
        s.operation == "RecordPayment" && s.kind == "transition[PLACED_to_PAID]"
      assert(
        rpPositiveSkip.nonEmpty,
        s"expected RecordPayment positive skip; got=${out.skips.map(s => s.operation -> s.kind)}"
      )
      assert(
        rpPositiveSkip.get.reason.contains("paymentCaptured") &&
          rpPositiveSkip.get.reason.contains("seed-dict recognizer"),
        s"reason should cite paymentCaptured + recognizer doc pointer; got=${rpPositiveSkip.get.reason}"
      )
      assert(
        !rpPositiveSkip.get.reason.contains("#155") &&
          !rpPositiveSkip.get.reason.contains("#152"),
        s"closed-issue refs (#152, #155) must not be cited; got=${rpPositiveSkip.get.reason}"
      )
      assert(
        out.skips.forall(s => !s.reason.contains("#155")),
        s"no skip should cite #155 after this fix; got=${out.skips.map(_.reason)}"
      )

  test("#155: todo_list (path-only via ops) is unaffected by the change"):
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
      val pos = out.tests
        .find(_.name == "test_start_work_transition_todo_to_in_progress")
        .getOrElse(fail("missing positive"))
      assert(
        pos.body.contains("@given(row=strategy_todo())\n"),
        s"path-only @given line drift; body=${pos.body}"
      )
      assert(
        pos.body.contains("def test_start_work_transition_todo_to_in_progress(row):\n"),
        s"path-only signature drift; body=${pos.body}"
      )
      assert(
        pos.body.contains("response = client.post(f\"/todos/{seeded_id}/start\")\n"),
        s"path-only request call drift; body=${pos.body}"
      )

  test("M5.9 fix H: filtered-out via op does NOT claim 'covered by transition tests'"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote
         |  }
         |  operation Promote {
         |    input: id: Int, payload: Map[String, Int]
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/items/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out     = Behavioral.emitFor(profiled)
      val ensSkip = out.skips.find(s => s.operation == "Promote" && s.kind == "ensures")
      assert(ensSkip.nonEmpty, s"expected Promote ensures skip; got=${out.skips}")
      assert(
        !ensSkip.get.reason.contains("covered by transition tests"),
        s"Promote was filtered out — must NOT claim coverage; got=${ensSkip.get.reason}"
      )
      assert(
        ensSkip.get.reason.contains("covered by stateful tests"),
        s"filtered-out via op should fall back to stateful-tests reference; got=${ensSkip.get.reason}"
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

  test("#152: unrecognized guard shape still skips with recognizer-doc reason"):
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
      assert(
        skip.get.reason.contains("seed-dict recognizer"),
        s"reason=${skip.get.reason}"
      )
      assert(
        !skip.get.reason.contains("#152"),
        s"closed-issue ref leaked; reason=${skip.get.reason}"
      )

  test("#152: transition-field guard matching `from` is auto-satisfied (no extra fix)"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
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
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; tests=${out.tests.map(_.name)}; skips=${out.skips}"))
      val mutationLineCount = pos.body.linesIterator
        .count(l =>
          l.trim.startsWith("row[") && !l.trim.startsWith("row = dict")
        )
      assertEquals(
        mutationLineCount,
        1,
        s"only the `row[\"phase\"] = \"LOW\"` step-3 line; body=${pos.body}"
      )

  test("#152: transition-field guard contradicting `from` is rejected (skipped)"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when phase = HIGH
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
      assert(
        skip.nonEmpty,
        s"expected skip when guard contradicts rule's from; got=${out.skips}"
      )

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

  // ---------- #152 round 2: extended recognizer ----------

  private def cardinalitySpec =
    """|service Demo {
       |  enum Phase { LOW, HIGH }
       |  entity Item {
       |    id: Int
       |    phase: Phase
       |    tags: Set[String]
       |  }
       |  state {
       |    items: Int -> lone Item
       |  }
       |  transition ItemLifecycle {
       |    entity: Item
       |    field: phase
       |    LOW -> HIGH via Promote when GUARD
       |  }
       |  operation Promote {
       |    input: id: Int
       |    requires: id in items
       |    ensures: items'[id].phase = HIGH
       |  }
       |  conventions {
       |    Promote.http_method = "POST"
       |    Promote.http_path   = "/items/{id}/promote"
       |  }
       |}
       |""".stripMargin

  test("#152 ext: #tags > 2 yields a 3-element list literal"):
    loadProfiledFromSpec(cardinalitySpec.replace("GUARD", "#tags > 2")).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; tests=${out.tests.map(_.name)}; skips=${out.skips}"))
      assert(
        pos.body.contains("row[\"tags\"] = [\"x0\", \"x1\", \"x2\"]"),
        s"body=${pos.body}"
      )

  test("#152 ext: #tags >= 1 yields a 1-element list literal"):
    loadProfiledFromSpec(cardinalitySpec.replace("GUARD", "#tags >= 1")).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(pos.body.contains("row[\"tags\"] = [\"x0\"]"), s"body=${pos.body}")

  test("#152 ext: #tags = 0 yields the empty list"):
    loadProfiledFromSpec(cardinalitySpec.replace("GUARD", "#tags = 0")).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(pos.body.contains("row[\"tags\"] = []"), s"body=${pos.body}")

  test("#152 ext: literal in <field> yields list-append fix-up"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |    tags: Set[String]
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote when "URGENT" in tags
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/items/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(
        pos.body.contains("row[\"tags\"] = list(row[\"tags\"]) + [\"URGENT\"]"),
        s"body=${pos.body}"
      )

  test("#152 ext: a > <int_literal> yields row[a] = literal + 1"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |    score: Int
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when score > 10
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
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(pos.body.contains("row[\"score\"] = 10 + 1"), s"body=${pos.body}")

  test("#152 ext: not (a > b) reduces via De Morgan to a <= b"):
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
         |    LOW -> HIGH via Promote when not (a > b)
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
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(pos.body.contains("row[\"a\"] = row[\"b\"]"), s"body=${pos.body}")

  test("#152 ext: a = none on Option field yields row[a] = None"):
    val spec =
      """|service Demo {
         |  enum Phase { OPEN, ARCHIVED }
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
         |    OPEN -> ARCHIVED via Archive when closed_at = none
         |  }
         |  operation Archive {
         |    input: id: Int
         |    requires: id in tickets
         |    ensures: tickets'[id].phase = ARCHIVED
         |  }
         |  conventions {
         |    Archive.http_method = "POST"
         |    Archive.http_path   = "/tickets/{id}/archive"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_archive_transition_open_to_archived")
        .getOrElse(fail(s"missing positive; tests=${out.tests.map(_.name)}; skips=${out.skips}"))
      assert(pos.body.contains("row[\"closed_at\"] = None"), s"body=${pos.body}")

  // ---------- #152 PR #156 review round ----------

  test("#156 R1: a > b and b > c emits writes in dependency order (b before a)"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Counter {
         |    id: Int
         |    phase: Phase
         |    a: Int
         |    b: Int
         |    c: Int
         |  }
         |  state {
         |    counters: Int -> lone Counter
         |  }
         |  transition CounterLifecycle {
         |    entity: Counter
         |    field: phase
         |    LOW -> HIGH via Promote when a > b and b > c
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
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      val body = pos.body
      val bIdx = body.indexOf("row[\"b\"] = row[\"c\"] + 1")
      val aIdx = body.indexOf("row[\"a\"] = row[\"b\"] + 1")
      assert(bIdx > 0 && aIdx > bIdx, s"b-write must precede a-write; b@$bIdx a@$aIdx; body=$body")

  test("#156 R1: cyclic dependency in conjunction (a > b and b > a) skips with recognizer reason"):
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
         |    LOW -> HIGH via Promote when a > b and b > a
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
      assert(skip.nonEmpty, s"expected skip on cyclic guard; got=${out.skips}")
      assert(
        skip.get.reason.contains("seed-dict recognizer"),
        s"reason=${skip.get.reason}"
      )
      assert(
        !skip.get.reason.contains("#152"),
        s"closed-issue ref leaked; reason=${skip.get.reason}"
      )

  test("#156 R5: literal in Option[Set[X]] adds None-anchor before list arithmetic"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |    tags: Option[Set[String]]
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote when "URGENT" in tags
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/items/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(
        pos.body.contains("if row[\"tags\"] is None: row[\"tags\"] = []"),
        s"expected None-anchor for Option[Set]; body=${pos.body}"
      )
      val anchorIdx = pos.body.indexOf("if row[\"tags\"] is None")
      val appendIdx = pos.body.indexOf("row[\"tags\"] = list(row[\"tags\"])")
      assert(
        anchorIdx > 0 && appendIdx > anchorIdx,
        s"None-anchor must precede list arithmetic; body=${pos.body}"
      )

  test("#156 R2: aliased Option type detected by isOptionalType — no list crash"):
    val spec =
      """|service Demo {
         |  type Maybe = Option[Set[String]]
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |    tags: Maybe
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote when "URGENT" in tags
         |  }
         |  operation Promote {
         |    input: id: Int
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/items/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(
        pos.body.contains("if row[\"tags\"] is None: row[\"tags\"] = []"),
        s"expected None-anchor for aliased Option[Set]; body=${pos.body}"
      )

  // ---------- #155: transition tests for via ops with body/query inputs ----------

  test("#155: inline spec — guardless via with body input emits positive with @given(value=…)"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote
         |  }
         |  operation Promote {
         |    input: id: Int, value: Int
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/items/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(
        pos.body.contains("@given(row=strategy_item(), value=st.integers())\n"),
        s"missing value=st.integers() in @given; body=${pos.body}"
      )
      assert(
        pos.body.contains("def test_promote_transition_low_to_high(row, value):\n"),
        s"signature missing 'value'; body=${pos.body}"
      )
      assert(
        pos.body.contains(
          "response = client.post(f\"/items/{seeded_id}/promote\", json={\"value\": value})\n"
        ),
        s"missing json={'value': value}; body=${pos.body}"
      )

  test("#155: inline spec — query input routed to params={…}"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote
         |  }
         |  operation Promote {
         |    input: id: Int, q: String
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "GET"
         |    Promote.http_path   = "/items/{id}/promote"
         |    Promote.http_query  = "q"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(
        pos.body.contains("@given(row=strategy_item(), q="),
        s"missing q strategy in @given; body=${pos.body}"
      )
      assert(
        pos.body.contains("def test_promote_transition_low_to_high(row, q):\n"),
        s"signature missing 'q'; body=${pos.body}"
      )
      assert(
        pos.body.contains(
          "response = client.get(f\"/items/{seeded_id}/promote\", params={\"q\": q})\n"
        ),
        s"missing params={'q': q}; body=${pos.body}"
      )

  test("#155: inline spec — non-generable body input (Map) skips with refined reason"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote
         |  }
         |  operation Promote {
         |    input: id: Int, payload: Map[String, Int]
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/items/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      assertEquals(
        out.tests.filter(_.name.startsWith("test_promote_transition_")),
        Nil,
        "non-generable body input must skip via op"
      )
      val skip = out.skips.find(_.kind == "transition[Promote]").getOrElse(fail("missing skip"))
      assert(
        skip.reason.contains("payload") && skip.reason.contains("MapType"),
        s"skip reason should name the input and the un-generable type; got=${skip.reason}"
      )
      assert(
        !skip.reason.contains("#155"),
        s"#155 must not be cited; got=${skip.reason}"
      )

  // ---------- audit cleanup: status-restriction negative tests + closed-issue refs ----------

  test("audit C1: ecommerce AddLineItem gets a status-restriction negative for status=DRAFT"):
    loadProfiled("fixtures/spec/ecommerce.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val t = out.tests
        .find(_.name == "test_add_line_item_negative_orders_status_not_draft")
        .getOrElse(
          fail(s"missing status-restriction negative; tests=${out.tests.map(_.name)}")
        )
      assert(
        t.body.contains(
          "@given(row=strategy_order(), wrong_status=st.sampled_from(["
        ),
        s"missing wrong_status sampled_from; body=${t.body}"
      )
      assert(
        !t.body.contains("\"DRAFT\""),
        s"DRAFT (the required status) must NOT appear in sampled_from; body=${t.body}"
      )
      assert(
        t.body.contains("seed = client.post(\"/__test_admin__/seed/order\""),
        s"missing seed call; body=${t.body}"
      )
      assert(
        t.body.contains("row[\"status\"] = wrong_status"),
        s"missing wrong_status assignment; body=${t.body}"
      )

  test("audit C1: ecommerce RecordPayment gets status-restriction negative with amount strategy"):
    loadProfiled("fixtures/spec/ecommerce.spec").map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val t = out.tests
        .find(_.name == "test_record_payment_negative_orders_status_not_placed")
        .getOrElse(fail("missing"))
      assert(
        t.body.contains(", amount=strategy_money())"),
        s"amount strategy must flow through; body=${t.body}"
      )
      assert(
        t.body.contains(
          "def test_record_payment_negative_orders_status_not_placed(row, wrong_status, amount):"
        ),
        s"signature must include amount; body=${t.body}"
      )
      assert(
        t.body.contains(
          "client.post(f\"/orders/{seeded_id}/payments\", json={\"amount\": amount})"
        ),
        s"missing body in request call; body=${t.body}"
      )

  test(
    "audit C1: status-restriction negative is M5.1-skipped when entity is not in any TransitionDecl"
  ):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  operation Read {
         |    input: id: Int
         |    requires:
         |      id in items
         |      items[id].phase = LOW
         |    ensures: true
         |  }
         |  conventions {
         |    Read.http_method = "GET"
         |    Read.http_path   = "/items/{id}"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      assertEquals(
        out.tests.filter(_.name.contains("_negative_items_phase_not_")),
        Nil,
        "no transition entity → no /seed/<entity> → status-restriction negative cannot be emitted"
      )
      val skip = out.skips.find(s => s.operation == "Read" && s.kind.startsWith("requires["))
      assert(
        skip.exists(_.reason.contains("M5.1: only")),
        s"expected M5.1 fallback skip with updated wording; got=${skip.map(_.reason)}"
      )

  test("audit B1: invariant_inputs single skip when op input is non-generable"):
    val spec =
      """|service Demo {
         |  state {
         |    counts: Int -> lone Int
         |  }
         |  invariant invA: 0 = 0
         |  invariant invB: 1 = 1
         |  operation Foo {
         |    input: payload: Map[String, Int]
         |    requires: true
         |    ensures: true
         |  }
         |  conventions {
         |    Foo.http_method = "POST"
         |    Foo.http_path   = "/foo"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val invInputSkips =
        out.skips.filter(s => s.operation == "Foo" && s.kind == "invariant_inputs")
      assertEquals(
        invInputSkips.size,
        1,
        s"hoisted check must produce exactly one skip per op, not N (per invariant); got ${invInputSkips.size}: $invInputSkips"
      )
      assertEquals(
        out.skips.count(s => s.operation == "Foo" && s.kind == "invariant"),
        0,
        s"old indistinguishable 'invariant' kind must be gone; got=${out.skips}"
      )

  test(
    "PR review R1: non-path input named 'row' is aliased to avoid collision with seed-row local"
  ):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Item {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    items: Int -> lone Item
         |  }
         |  transition ItemLifecycle {
         |    entity: Item
         |    field: phase
         |    LOW -> HIGH via Promote
         |  }
         |  operation Promote {
         |    input: id: Int, row: Int, seed: Int, seeded_id: Int
         |    requires: id in items
         |    ensures: items'[id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/items/{id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      val pos = out.tests
        .find(_.name == "test_promote_transition_low_to_high")
        .getOrElse(fail(s"missing positive; skips=${out.skips}"))
      assert(
        pos.body.contains("_arg_row=st.integers()") &&
          pos.body.contains("_arg_seed=st.integers()") &&
          pos.body.contains("_arg_seeded_id=st.integers()"),
        s"reserved-name inputs must be aliased to _arg_<name>; body=${pos.body}"
      )
      assert(
        !pos.body.contains("@given(row=strategy_item(), row="),
        s"raw 'row' kwarg must not collide with the seed row strategy; body=${pos.body}"
      )
      assert(
        pos.body.contains(
          "def test_promote_transition_low_to_high(row, _arg_row, _arg_seed, _arg_seeded_id):"
        ),
        s"signature must use aliased names; body=${pos.body}"
      )
      assert(
        pos.body.contains(
          "json={\"row\": _arg_row, \"seed\": _arg_seed, \"seeded_id\": _arg_seeded_id}"
        ),
        s"JSON keys must use the original parameter names, values use the alias; body=${pos.body}"
      )

  test("PR review R2: multi-path/zero-path skip reason no longer cites #155"):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Outer {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    outers: Int -> lone Outer
         |  }
         |  transition OuterLifecycle {
         |    entity: Outer
         |    field: phase
         |    LOW -> HIGH via Promote
         |  }
         |  operation Promote {
         |    input: outer_id: Int, inner_id: Int
         |    requires: outer_id in outers
         |    ensures: outers'[outer_id].phase = HIGH
         |  }
         |  conventions {
         |    Promote.http_method = "POST"
         |    Promote.http_path   = "/outers/{outer_id}/inner/{inner_id}/promote"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out  = Behavioral.emitFor(profiled)
      val skip = out.skips.find(_.kind == "transition[Promote]").getOrElse(fail("missing skip"))
      assert(
        skip.reason.contains("multi-path"),
        s"skip should describe multi-path constraint; got=${skip.reason}"
      )
      assert(!skip.reason.contains("#155"), s"#155 must not be cited; got=${skip.reason}")

  test(
    "PR review R3: status-restriction recognized but multi-path emits explicit skip (not silent drop)"
  ):
    val spec =
      """|service Demo {
         |  enum Phase { LOW, HIGH }
         |  entity Outer {
         |    id: Int
         |    phase: Phase
         |  }
         |  state {
         |    outers: Int -> lone Outer
         |  }
         |  transition OuterLifecycle {
         |    entity: Outer
         |    field: phase
         |    LOW -> HIGH via Tick
         |  }
         |  operation Update {
         |    input: outer_id: Int, inner_id: Int
         |    requires:
         |      outer_id in outers
         |      outers[outer_id].phase = LOW
         |    ensures: true
         |  }
         |  operation Tick {
         |    input: id: Int
         |    requires: id in outers
         |    ensures: outers'[id].phase = HIGH
         |  }
         |  conventions {
         |    Update.http_method = "POST"
         |    Update.http_path   = "/outers/{outer_id}/inner/{inner_id}"
         |    Tick.http_method   = "POST"
         |    Tick.http_path     = "/outers/{id}/tick"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out = Behavioral.emitFor(profiled)
      assertEquals(
        out.tests.filter(_.name.contains("update_negative_outers_phase_not_low")),
        Nil,
        "multi-path Update cannot get a status-restriction negative"
      )
      val updSkips =
        out.skips.filter(s => s.operation == "Update" && s.kind.startsWith("requires["))
      assert(
        updSkips.exists(_.reason.contains("multi-path")),
        s"explicit skip with multi-path reason expected; got=$updSkips"
      )

  test("audit A3: ExprToPython unknown function reference cites #138"):
    val spec =
      """|service Demo {
         |  state {
         |    n: Int
         |  }
         |  operation Foo {
         |    input: x: Int
         |    requires: true
         |    ensures: someUnknownFn(x)
         |  }
         |  conventions {
         |    Foo.http_method = "POST"
         |    Foo.http_path   = "/foo"
         |  }
         |}
         |""".stripMargin
    loadProfiledFromSpec(spec).map: profiled =>
      val out      = Behavioral.emitFor(profiled)
      val ensSkips = out.skips.filter(s => s.operation == "Foo" && s.kind.startsWith("ensures"))
      assert(
        ensSkips.exists(_.reason.contains("#138")),
        s"unknown function skip should cite #138; got=${ensSkips.map(_.reason)}"
      )
