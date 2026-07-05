package specrest.testgen

class BehavioralViaOpTest extends BehavioralTestSupport:

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
        pos.body.contains(
          "@given(row=strategy_item(), value=st.integers(min_value=-2147483648, max_value=2147483647))\n"
        ),
        s"missing bounded value strategy in @given; body=${pos.body}"
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
