package specrest.testgen

class BehavioralStatusRestrictionTest extends BehavioralTestSupport:

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
        t.body.contains("seed = client.post(\"/admin/seed/order\""),
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
