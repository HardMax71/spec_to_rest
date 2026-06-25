package specrest.testgen

class BehavioralGuardedTransitionTest extends BehavioralTestSupport:

  // ---------- #152: guarded positive transition tests ----------

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
