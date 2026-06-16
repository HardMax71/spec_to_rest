package specrest.verify

import munit.CatsEffectSuite
import specrest.verify.testutil.SpecFixtures

class ConsistencyTest extends CatsEffectSuite:

  test("url_shortener passes all consistency checks"):
    for
      ir     <- SpecFixtures.loadIR("url_shortener")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield assert(
      report.ok,
      s"expected ok=true; failing checks: ${report.checks.filter(c =>
          c.status != CheckOutcome.Sat && c.status != CheckOutcome.Skipped
        ).map(_.id)}"
    )

  // Each verified-subset lift: a focused spec whose every check must verify (Sat), not skip.
  List(
    (
      "definite description (the v in rel | P) verifies via Z3 (TheF lifted to the verified subset)",
      "the_demo",
      """service TheDemo {
        |  state {
        |    scores: Map[Int, Int]
        |  }
        |  invariant pivotNonNegative:
        |    (the k in scores | scores[k] = 100) >= 0
        |}""".stripMargin,
      "the must verify, not skip"
    ),
    (
      "ordering on Duration verifies via Z3 (Duration shares the Int sort, completing the #377 temporal lift)",
      "duration_demo",
      """service DurationDemo {
        |  state {
        |    timeouts: Map[Int, Duration]
        |  }
        |  invariant timeoutsNonNegative:
        |    (the k in timeouts | timeouts[k] >= 0) >= 0
        |}""".stripMargin,
      "Duration ordering must verify, not skip"
    ),
    (
      "lexicographic ordering on String verifies via Z3 (T_Cmp_Str_Ord, str.< encoding)",
      "string_order_demo",
      """service StringOrderDemo {
        |  state {
        |    names: Map[Int, String]
        |  }
        |  invariant namesBelowM:
        |    (the k in names | names[k] < "m") >= 0
        |}""".stripMargin,
      "String ordering must verify, not skip"
    ),
    (
      "string concatenation verifies via Z3 (T_Str_Concat, str.++ encoding)",
      "string_concat_demo",
      """service StringConcatDemo {
        |  state {
        |    names: Map[Int, String]
        |  }
        |  invariant concatMatches:
        |    (the k in names | "a" + names[k] = "ab") >= 0
        |}""".stripMargin,
      "String concat must verify, not skip"
    ),
    (
      "cardinality on primed/pre-state relations verifies via Z3 (#rel' / #pre(rel) translate)",
      "card_demo",
      """service CardDemo {
        |  state {
        |    items: Int -> lone Int
        |  }
        |  operation Touch {
        |    ensures:
        |      #items' = #pre(items)
        |  }
        |  invariant cardNonNeg:
        |    #items >= 0
        |}""".stripMargin,
      "cardinality on primed/pre must verify, not skip"
    ),
    (
      "relation literal-insert verifies via Z3 (rel' = pre(rel) + {k -> v} desugars to rel'[k] = v)",
      "insert_demo",
      """service InsertDemo {
        |  state {
        |    store: Int -> lone Int
        |  }
        |  operation Put {
        |    input:  v: Int
        |    output: k: Int
        |    ensures:
        |      k not in pre(store)
        |      store' = pre(store) + {k -> v}
        |      #store' = #pre(store) + 1
        |  }
        |  invariant cardNonNeg:
        |    #store >= 0
        |}""".stripMargin,
      "relation insert must verify, not skip"
    ),
    (
      "String-refinement alias relations verify on the native String sort (E-matching triggers keep them tractable)",
      "string_alias_demo",
      """service StringAliasDemo {
        |  type Code = String where len(value) >= 3 and len(value) <= 8
        |  state {
        |    store: Code -> lone String
        |  }
        |  operation Put {
        |    input:  v: String
        |    output: k: Code, label: String
        |    ensures:
        |      k not in pre(store)
        |      store' = pre(store) + {k -> v}
        |      label = "id:" + k
        |  }
        |  invariant cardNonNeg:
        |    #store >= 0
        |}""".stripMargin,
      "String-refinement alias on relations must verify on the String sort, not time out"
    ),
    (
      "value-projection comprehension verifies via Z3 (entries = { m in rel | true } projects to rel's range)",
      "range_comp_demo",
      """service RangeCompDemo {
        |  entity Item {
        |    size: Int
        |  }
        |  state {
        |    rel: Int -> lone Item
        |  }
        |  operation ListAll {
        |    output: entries: Set[Item]
        |    requires:
        |      true
        |    ensures:
        |      entries = { m in rel | true }
        |      rel' = rel
        |  }
        |  invariant cardNonNeg:
        |    #rel >= 0
        |}""".stripMargin,
      "value-projection comprehension must verify, not skip"
    ),
    (
      "entity construction (Entity{...}) verifies via Z3 (ConstructorF lifted to the verified subset)",
      "constructor_demo",
      """service ConstructorDemo {
        |  entity Point {
        |    x: Int
        |    y: Int
        |  }
        |  invariant ctorFieldRoundtrips:
        |    (Point { x = 5, y = 7 }).x = 5
        |}""".stripMargin,
      "Entity{...} must verify, not skip"
    ),
    (
      "function/predicate calls inline + verify via Z3 (CallF lifted to the verified subset)",
      "call_demo",
      """service CallDemo {
        |  function dbl(n: Int): Int = n + n
        |  predicate isPos(n: Int) = n > 0
        |  invariant callsInline:
        |    dbl(3) = 6 and isPos(dbl(1))
        |}""".stripMargin,
      "calls must inline + verify, not skip"
    ),
    (
      "0-arg reserved builtin now() verifies via Z3 (uninterpreted Int constant)",
      "now_demo",
      """service NowDemo {
        |  state {
        |    log: Int -> lone Int
        |  }
        |  operation Stamp {
        |    output: t: Int
        |    requires:
        |      true
        |    ensures:
        |      t = now()
        |      log' = log
        |  }
        |  invariant cardNonNeg:
        |    #log >= 0
        |}""".stripMargin,
      "now() must verify, not skip"
    ),
    (
      "chained relation literal-insert verifies via Z3 (rel' = pre(rel) + {a->x} + {b->y})",
      "chain_insert_demo",
      """service ChainInsertDemo {
        |  state {
        |    store: Int -> lone Int
        |  }
        |  operation Put2 {
        |    input:  a: Int, b: Int, va: Int, vb: Int
        |    requires:
        |      a != b
        |    ensures:
        |      store' = pre(store) + {a -> va} + {b -> vb}
        |  }
        |  invariant cardNonNeg:
        |    #store >= 0
        |}""".stripMargin,
      "chained relation insert must verify, not skip"
    ),
    (
      "chained insert with a repeated key is last-write-wins, not UNSAT",
      "dup_key_insert_demo",
      """service DupKeyDemo {
        |  state {
        |    store: Int -> lone Int
        |  }
        |  operation Put {
        |    input:  a: Int, va: Int, vb: Int
        |    requires:
        |      va != vb
        |    ensures:
        |      store' = pre(store) + {a -> va} + {a -> vb}
        |  }
        |  invariant cardNonNeg:
        |    #store >= 0
        |}""".stripMargin,
      "repeated-key chained insert must be last-write-wins (Sat), not UNSAT"
    ),
    (
      "bare enum member resolves to the enum sort (c = RED, not c = Uninterp(Any))",
      "bare_enum_demo",
      """service EnumDemo {
        |  enum Color {
        |    RED,
        |    GREEN,
        |    BLUE
        |  }
        |  state {
        |    c: Color
        |  }
        |  invariant pinned:
        |    c = RED implies c != GREEN
        |}""".stripMargin,
      "bare enum member must verify, not crash on Sorts Color/Any"
    ),
    (
      "base-vs-optional comparison verifies (chosen = fallback where chosen: Option[Int])",
      "optional_eq_demo",
      """service OptDemo {
        |  state {
        |    chosen: Option[Int]
        |    fallback: Int
        |  }
        |  invariant chosenOrFallback:
        |    chosen = none or chosen = fallback
        |}""".stripMargin,
      "base-vs-optional equality must verify, not crash on incompatible sorts"
    ),
    (
      "none in a with-record-update verifies (the target field's Option sort supplies the element sort)",
      "with_none_demo",
      """service WithNoneDemo {
        |  enum Status {
        |    OPEN,
        |    CLOSED
        |  }
        |  entity Rec {
        |    id: Int
        |    status: Status
        |    done_at: Option[Int]
        |  }
        |  state {
        |    recs: Int -> lone Rec
        |  }
        |  operation Reopen {
        |    input: id: Int
        |    output: r: Rec
        |    requires:
        |      id in recs
        |    ensures:
        |      r = pre(recs)[id] with {
        |        status = OPEN,
        |        done_at = none
        |      }
        |      recs' = pre(recs) + {id -> r}
        |  }
        |  invariant cardNonNeg:
        |    #recs >= 0
        |}""".stripMargin,
      "none in a with-update must verify (field Option sort gives the element sort), not skip"
    ),
    (
      "1-arg builtin hash(x) verifies via Z3 as an uninterpreted String function (determinism preserved)",
      "hash_demo",
      """service HashDemo {
        |  state {
        |    a: String
        |    b: String
        |  }
        |  operation Both {
        |    input: pw: String
        |    ensures:
        |      a' = hash(pw)
        |      b' = hash(pw)
        |  }
        |  invariant equalHashes:
        |    a = b
        |}""".stripMargin,
      "hash(pw) must verify as a deterministic uninterpreted function (a' = b'), not skip"
    ),
    (
      "sequence-append (seq + [elem]) verifies via Z3 (BAdd on seqs -> seq.++, the auth login_attempts shape)",
      "seq_append_demo",
      """service SeqAppendDemo {
        |  entity Ev {
        |    tag: Int
        |  }
        |  state {
        |    log: Seq[Ev]
        |  }
        |  operation Record {
        |    input: t: Int
        |    ensures:
        |      log' = pre(log) + [Ev { tag = t }]
        |  }
        |  invariant trivial:
        |    true
        |}""".stripMargin,
      "seq-append (log' = pre(log) + [Ev{...}]) must verify, not skip on 'addition requires numeric'"
    ),
    (
      "ran(rel) verifies via Z3 (the user-facing value-set builtin reuses #428's range projection)",
      "ran_demo",
      """service RanDemo {
        |  entity It {
        |    v: Int
        |  }
        |  state {
        |    rel: Int -> lone It
        |  }
        |  operation Values {
        |    output: xs: Set[It]
        |    requires:
        |      true
        |    ensures:
        |      xs = ran(rel)
        |      rel' = rel
        |  }
        |  invariant cardNonNeg:
        |    #rel >= 0
        |}""".stripMargin,
      "ran(rel) must verify (reuses #428 range projection), not skip as an unrecognized builtin"
    ),
    (
      "universal quantification over a Seq output verifies via Z3 (all t in xs | P, the ListTodos shape)",
      "seq_forall_demo",
      """service SeqForallDemo {
        |  enum St { A, B }
        |  entity It {
        |    st: St
        |    tags: Set[String]
        |  }
        |  state {
        |    ctr: Int
        |  }
        |  operation Emit {
        |    input: sf: Option[St], tf: String
        |    output: xs: Seq[It]
        |    requires:
        |      true
        |    ensures:
        |      all t in xs |
        |        t.st = A
        |        and (sf = none or t.st = sf)
        |        and tf in t.tags
        |      ctr' = pre(ctr)
        |  }
        |  invariant nonneg:
        |    ctr >= 0
        |}""".stripMargin,
      "universal-over-Seq (all t in xs | ...) must verify, not skip on 'field access requires entity sort'"
    ),
    (
      "the ListTodos filter shape verifies: range-membership + optional-vs-base membership under all-in-Seq",
      "list_filter_demo",
      """service ListFilterDemo {
        |  entity It {
        |    tags: Set[String]
        |  }
        |  state {
        |    rel: Int -> lone It
        |  }
        |  operation Filter {
        |    input: tag_filter: Option[String]
        |    output: results: Seq[It]
        |    requires:
        |      true
        |    ensures:
        |      all t in results |
        |        t in ran(rel)
        |        and (tag_filter = none or tag_filter in t.tags)
        |      rel' = rel
        |  }
        |  invariant cardNonNeg:
        |    #rel >= 0
        |}""".stripMargin,
      "ListTodos shape (range-membership `t in ran(rel)` + optional membership `tag_filter in t.tags`) must verify, not skip"
    ),
    (
      "sum(coll, i => i.field) aggregate verifies via Z3 as an uninterpreted function (the ecommerce subtotal shape)",
      "sum_demo",
      """service SumDemo {
        |  entity LineItem {
        |    line_total: Int
        |  }
        |  state {
        |    items: Set[LineItem]
        |    subtotal: Int
        |  }
        |  operation Relabel {
        |    input: x: Int
        |    requires:
        |      true
        |    ensures:
        |      items' = pre(items)
        |      subtotal' = pre(subtotal)
        |  }
        |  invariant subtotalIsSum:
        |    subtotal = sum(items, i => i.line_total)
        |}""".stripMargin,
      "sum(items, i => i.line_total) must verify: as an uninterpreted function keyed by collection and field, the invariant `subtotal = sum(items, _)` is preserved when items is unchanged (same collection => same sum)"
    ),
    (
      "#contents on an entity-field Set verifies via Z3 (the ecommerce #items shape)",
      "entity_set_card_demo",
      """service EntitySetCardDemo {
        |  entity Bag {
        |    contents: Set[Int]
        |    count: Int
        |    invariant: #contents > 0 implies count > 0
        |  }
        |  state {
        |    n: Int
        |  }
        |  operation Bump {
        |    input: x: Int
        |    requires:
        |      true
        |    ensures:
        |      n' = pre(n) + x
        |  }
        |  invariant nonneg:
        |    true
        |}""".stripMargin,
      "#contents on an entity-field Set (an Order's `#items` shape) must verify, not skip on 'cardinality requires a state relation' - it gets the uninterpreted setCard model"
    ),
    (
      "#(expr) on a set-valued field-access verifies via Z3 (the ecommerce #(orders[oid].items) shape)",
      "set_term_card_demo",
      """service SetTermCardDemo {
        |  entity Box {
        |    contents: Set[Int]
        |  }
        |  state {
        |    boxes: Int -> lone Box
        |  }
        |  operation Touch {
        |    input: bid: Int
        |    requires:
        |      bid in boxes
        |      #(boxes[bid].contents) >= 0
        |    ensures:
        |      boxes' = pre(boxes)
        |  }
        |  invariant t:
        |    true
        |}""".stripMargin,
      "#(boxes[bid].contents) - cardinality of a set-valued field-access (not a bare relation identifier) - must verify: `#expr` lowers to TCard and gets the uninterpreted setCard model"
    ),
    (
      "universal quantification over a field-access set verifies via Z3 (the ecommerce lineItemTotalConsistency shape)",
      "set_forall_field_demo",
      """service SetForallFieldDemo {
        |  entity Item {
        |    qty: Int
        |  }
        |  entity Box {
        |    contents: Set[Item]
        |  }
        |  state {
        |    boxes: Int -> lone Box
        |  }
        |  operation Touch {
        |    input: bid: Int
        |    requires:
        |      true
        |    ensures:
        |      boxes' = pre(boxes)
        |  }
        |  invariant allQtyNonNeg:
        |    all bid in boxes | all it in boxes[bid].contents | it.qty >= 0
        |}""".stripMargin,
      "`all it in boxes[bid].contents | it.qty >= 0` - a universal whose domain is a field-access set (not a bare identifier) - must verify: it lowers to TForallSet and the binder resolves to the Item entity sort"
    ),
    (
      "set union via `+` verifies via Z3 (the ecommerce `items + {item}` shape)",
      "set_union_demo",
      """service SetUnionDemo {
        |  entity It {
        |    v: Int
        |  }
        |  state {
        |    seen: Set[It]
        |  }
        |  operation Add {
        |    input: x: It
        |    requires:
        |      true
        |    ensures:
        |      seen' = pre(seen) + {x}
        |  }
        |  invariant t:
        |    true
        |}""".stripMargin,
      "`seen' = pre(seen) + {x}` - `+` on two Set operands - must verify as set union, not skip on 'addition requires numeric'"
    ),
    (
      "existential over a primed relation verifies via Z3 (the ecommerce `some p in payments'` shape)",
      "primed_exists_demo",
      """service PrimedExistsDemo {
        |  enum PayStatus {
        |    PENDING,
        |    REFUNDED
        |  }
        |  entity Payment {
        |    id: Int
        |    status: PayStatus
        |  }
        |  state {
        |    payments: Int -> lone Payment
        |  }
        |  operation Refund {
        |    input: pid: Int
        |    requires:
        |      true
        |    ensures:
        |      some p in payments' |
        |        p not in pre(payments)
        |        and payments'[p].status = REFUNDED
        |  }
        |  invariant t:
        |    true
        |}""".stripMargin,
      "`some p in payments' | p not in pre(payments) and payments'[p].status = REFUNDED` - an existential whose domain is a primed state relation - must verify, not skip: it lowers to TNot(TPrime(TForallRel ...)) and the encoder composes the post-state domain via stateMode"
    ),
    (
      "existential over a field-access set verifies via Z3 (the ecommerce `some i in orders[oid].items` shape)",
      "set_exists_demo",
      """service SetExistsDemo {
        |  entity Item {
        |    id: Int
        |  }
        |  entity Box {
        |    contents: Set[Item]
        |  }
        |  state {
        |    boxes: Int -> lone Box
        |  }
        |  operation Find {
        |    input: bid: Int, target: Int
        |    requires:
        |      bid in boxes
        |      some it in boxes[bid].contents | it.id = target
        |    ensures:
        |      boxes' = pre(boxes)
        |  }
        |  invariant t:
        |    true
        |}""".stripMargin,
      "`some it in boxes[bid].contents | it.id = target` - an existential whose domain is a field-access set (not an identifier) - must verify, not skip: QSome over a non-identifier domain lowers to TNot(TForallSet ...), mirroring #440's QAll path"
    )
  ).foreach: (name, fixture, spec, reason) =>
    test(name):
      for
        ir     <- SpecFixtures.buildFromSource(fixture, spec)
        report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
      yield assert(
        report.checks.nonEmpty && report.checks.forall(_.status == CheckOutcome.Sat),
        s"expected every check Sat ($reason); got: ${report.checks.map(c => s"${c.id}->${c.status}")}"
      )

  test("ambiguous bare enum member is a translator limit, not a solver crash"):
    val spec =
      """service AmbiguousEnumDemo {
        |  enum Color {
        |    RED,
        |    GREEN
        |  }
        |  enum Signal {
        |    RED,
        |    AMBER
        |  }
        |  state {
        |    c: Color
        |  }
        |  invariant pinned:
        |    c = RED
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("ambiguous_enum_demo", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val crashed =
        report.checks.filter(_.diagnostic.exists(_.category == DiagnosticCategory.BackendError))
      assert(
        crashed.isEmpty,
        s"ambiguous bare enum must not crash the solver; got: ${crashed.map(c => s"${c.id}->${c.diagnostic.map(_.message)}")}"
      )
      assert(
        report.checks.exists(
          _.diagnostic.exists(_.category == DiagnosticCategory.TranslatorLimitation)
        ),
        s"expected a TranslatorLimitation skip; got: ${report.checks.map(c => s"${c.id}->${c.status}/${c.diagnostic.map(_.category)}")}"
      )

  test(
    "bare enum member as a call argument is never mis-resolved (no crash, no false contradiction)"
  ):
    val spec =
      """service CallEnumDemo {
        |  enum Status {
        |    DONE,
        |    TODO
        |  }
        |  predicate isDone(s: Status) = s = DONE
        |  state {
        |    cur: Status
        |  }
        |  invariant pinned:
        |    isDone(DONE)
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("call_enum_demo", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      // isDone(DONE) inlines to the tautology DONE = DONE, which is outside the verified subset, so
      // the check is soundness-skipped rather than Sat. Guard the real failure modes instead: a solver
      // crash (BackendError) or a false contradiction (Unsat) from mis-sorting the bare enum argument.
      assert(report.checks.nonEmpty, "expected at least one check")
      val bad = report.checks.filter(c =>
        c.status == CheckOutcome.Unsat ||
          c.diagnostic.exists(_.category == DiagnosticCategory.BackendError)
      )
      assert(
        bad.isEmpty,
        s"bare enum call-arg must not crash or be mis-resolved to Unsat; got: ${report.checks.map(c => s"${c.id}->${c.status}")}"
      )

  test("unsat_invariants has contradictory_invariants diagnostic"):
    for
      ir     <- SpecFixtures.loadIR("unsat_invariants")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      assert(!report.ok)
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.status, CheckOutcome.Unsat)
      assertEquals(
        global.diagnostic.map(_.category),
        Some(DiagnosticCategory.ContradictoryInvariants)
      )

  test("dead_op detects unsatisfiable_precondition"):
    for
      ir     <- SpecFixtures.loadIR("dead_op")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val deadReq = report.checks.find(_.id == "DeadOp.requires")
      assert(deadReq.isDefined, s"missing DeadOp.requires in checks: ${report.checks.map(_.id)}")
      assertEquals(deadReq.get.status, CheckOutcome.Unsat)
      assertEquals(
        deadReq.get.diagnostic.map(_.category),
        Some(DiagnosticCategory.UnsatisfiablePrecondition)
      )

  test("unreachable_op detects unreachable_operation"):
    for
      ir     <- SpecFixtures.loadIR("unreachable_op")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val unreachable = report.checks.find(_.id == "UnreachableOp.enabled")
      assert(unreachable.isDefined)
      assertEquals(unreachable.get.status, CheckOutcome.Unsat)
      assertEquals(
        unreachable.get.diagnostic.map(_.category),
        Some(DiagnosticCategory.UnreachableOperation)
      )

  test("broken_url_shortener detects invariant violation"):
    for
      ir     <- SpecFixtures.loadIR("broken_url_shortener")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val violations = report.checks.filter(c =>
        c.kind == CheckKind.Preservation && c.status == CheckOutcome.Unsat
      )
      assert(violations.nonEmpty, "expected at least one preservation violation")
      violations.foreach: v =>
        assertEquals(
          v.diagnostic.map(_.category),
          Some(DiagnosticCategory.InvariantViolationByOperation)
        )

  test("safe_counter — every check passes"):
    for
      ir     <- SpecFixtures.loadIR("safe_counter")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield assert(
      report.ok,
      s"safe_counter should be fully consistent; failing: ${report.checks.filter(_.status != CheckOutcome.Sat).map(c => s"${c.id}->${c.status}")}"
    )

  test("set_ops — every check is sat or soundness-skipped (no unsoundness)"):
    for
      ir     <- SpecFixtures.loadIR("set_ops")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val nonOk = report.checks.filter: c =>
        c.status != CheckOutcome.Sat && c.status != CheckOutcome.Skipped
      assert(
        nonOk.isEmpty,
        s"set_ops should have no failing checks; got: ${nonOk.map(c => s"${c.id}->${c.status}")}"
      )
      val unexpectedSkips = report.checks.filter: c =>
        c.status == CheckOutcome.Skipped &&
          !c.diagnostic.exists: d =>
            d.category == DiagnosticCategory.SoundnessLimitation
      assert(
        unexpectedSkips.isEmpty,
        s"set_ops skips must be soundness-limitation only; got: ${unexpectedSkips.map(_.id)}"
      )

  test("set_comp_demo — global verifies via Z3 (set comprehension now in the verified subset)"):
    for
      ir     <- SpecFixtures.loadIR("set_comp_demo")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val global = report.checks.find(_.id == "global").getOrElse(
        fail("expected a global check")
      )
      assertEquals(global.status, CheckOutcome.Sat)

  test("powerset_demo — global invariant routes to Alloy and solves sat"):
    for
      ir     <- SpecFixtures.loadIR("powerset_demo")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val global = report.checks.find(_.id == "global").getOrElse(fail("no global check"))
      assertEquals(
        global.tool,
        VerifierTool.Alloy,
        s"global should route to Alloy; got ${global.tool}"
      )
      assertEquals(global.status, CheckOutcome.Sat, s"global should be sat; got ${global.status}")
      assert(
        report.ok,
        s"powerset_demo should pass; got: ${report.checks.map(c => s"${c.id}->${c.status}")}"
      )

  test("temporal_demo — always/eventually temporal properties route to Alloy and pass"):
    for
      ir     <- SpecFixtures.loadIR("temporal_demo")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporalChecks = report.checks.filter(_.kind == CheckKind.Temporal)
      assertEquals(
        temporalChecks.size,
        2,
        s"expected 2 temporal checks; got ${temporalChecks.map(_.id)}"
      )
      assert(
        temporalChecks.forall(_.tool == VerifierTool.Alloy),
        s"expected all Alloy-routed; got ${temporalChecks.map(c => s"${c.id}->${c.tool}")}"
      )
      assert(
        temporalChecks.forall(_.status == CheckOutcome.Sat),
        s"expected all sat; got ${temporalChecks.map(c => s"${c.id}->${c.status}")}"
      )
      assert(report.ok)

  test("unreachable `eventually(...)` returns Unsat under contradictory invariants"):
    val spec =
      """service BrokenTemporal {
        |  entity User {
        |  }
        |  state {
        |    users: Set[User]
        |  }
        |  invariant noUsers:
        |    all u in users | u != u
        |  temporal someUserExists:
        |    eventually(some u in users | u = u)
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("BrokenTemporal", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.tool, VerifierTool.Alloy)
      assertEquals(
        temporal.status,
        CheckOutcome.Unsat,
        s"expected unreachable; got ${temporal.status} (${temporal.detail})"
      )

  test("violated `always(...)` returns Unsat when P can be falsified"):
    val spec =
      """service BrokenAlways {
        |  entity User {}
        |  state {
        |    users: Set[User]
        |  }
        |  temporal alwaysFalse:
        |    always(all u in users | u != u)
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("BrokenAlways", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.tool, VerifierTool.Alloy)
      assertEquals(
        temporal.status,
        CheckOutcome.Unsat,
        s"expected violation; got ${temporal.status}"
      )

  test("fairness(...) raises an Alloy translator error surfaced as Skipped"):
    val spec =
      """service FairnessSpec {
        |  operation Step {
        |    requires: true
        |    ensures: true
        |  }
        |  temporal fairStep:
        |    fairness(Step)
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("FairnessSpec", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.status, CheckOutcome.Skipped)
      assert(
        temporal.diagnostic.exists(_.message.contains("fairness")),
        s"expected fairness error; got: ${temporal.diagnostic.map(_.message)}"
      )

  test("powerset_ops — Alloy-routed requires/enabled/preservation all solve"):
    for
      ir     <- SpecFixtures.loadIR("powerset_ops")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val reqCheck = report.checks.find(_.id == "AddUser.requires")
        .getOrElse(fail("no AddUser.requires check"))
      assertEquals(reqCheck.tool, VerifierTool.Alloy)
      assertEquals(reqCheck.status, CheckOutcome.Sat)
      val enCheck = report.checks.find(_.id == "AddUser.enabled")
        .getOrElse(fail("no AddUser.enabled check"))
      assertEquals(enCheck.tool, VerifierTool.Alloy)
      assertEquals(enCheck.status, CheckOutcome.Sat)
      val presCheck = report.checks.find(_.id == "AddUser.preserves.allValid")
        .getOrElse(fail("no AddUser.preserves.allValid check"))
      assertEquals(presCheck.tool, VerifierTool.Alloy)
      assertEquals(
        presCheck.status,
        CheckOutcome.Sat,
        s"preservation should be preserved; got ${presCheck.status}"
      )
      assert(
        report.ok,
        s"powerset_ops should pass; got: ${report.checks.map(c => s"${c.id}->${c.tool}->${c.status}")}"
      )
