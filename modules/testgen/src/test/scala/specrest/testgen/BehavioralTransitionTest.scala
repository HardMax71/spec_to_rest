package specrest.testgen

class BehavioralTransitionTest extends BehavioralTestSupport:

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
      assert(test.body.contains("client.post(\"/admin/reset\")"))
      assert(test.body.contains("row[\"status\"] = \"TODO\""), s"body=${test.body}")
      assert(
        test.body.contains("client.post(\"/admin/seed/todo\""),
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
