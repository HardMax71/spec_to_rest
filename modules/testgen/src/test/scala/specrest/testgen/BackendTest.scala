package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.Builder
import specrest.parser.Parse

class BackendTest extends CatsEffectSuite:

  // The seam contract: the same shared StrategyBackend trait renders the same IR
  // shapes into each target language. These cases pin the Python (oracle) and TS
  // renderings side by side so a drift in either is caught.
  private val py = PythonHypothesisStrategy
  private val ts = TsFastCheckStrategy

  List(
    ("string", (b: StrategyBackend) => b.string, "st.text()", "fc.string()"),
    ("int", (b: StrategyBackend) => b.int, "st.integers()", "fc.integer()"),
    ("bool", (b: StrategyBackend) => b.bool, "st.booleans()", "fc.boolean()"),
    ("id", (b: StrategyBackend) => b.id, "st.uuids().map(str)", "fc.uuid()"),
    (
      "option",
      (b: StrategyBackend) => b.option("X"),
      "st.one_of(st.none(), X)",
      "fc.option(X, { nil: null })"
    ),
    (
      "seq",
      (b: StrategyBackend) => b.seq("X"),
      "st.lists(X, max_size=5)",
      "fc.array(X, { maxLength: 5 })"
    ),
    (
      "enum",
      (b: StrategyBackend) => b.enumSampled(List("A", "B")),
      "st.sampled_from([\"A\", \"B\"])",
      "fc.constantFrom(\"A\", \"B\")"
    )
  ).foreach: (label, sel, pyExpected, tsExpected) =>
    test(s"StrategyBackend renders '$label' per-language"):
      assertEquals(sel(py), pyExpected)
      assertEquals(sel(ts), tsExpected)

  test("constrainedInt bounds render in both backends"):
    val c = IntConstraint(minValue = Some(1), maxValue = Some(10))
    assertEquals(py.constrainedInt(c), "st.integers(min_value=1, max_value=10)")
    assertEquals(ts.constrainedInt(c), "fc.integer({ min: 1, max: 10 })")

  test("constrainedString regex is full-match anchored in TS, fullmatch in Python"):
    val c = StringConstraint(regexes = List("[a-z]+"))
    assert(py.constrainedString(c).contains("st.from_regex"), py.constrainedString(c))
    assert(py.constrainedString(c).contains("fullmatch=True"), py.constrainedString(c))
    val tsR = ts.constrainedString(c)
    assert(tsR.contains("fc.stringMatching"), tsR)
    assert(tsR.contains("^(?:[a-z]+)$"), tsR)

  test("fixedDict + redaction differ structurally per backend"):
    val entries = List(("name", "ARB"))
    assert(py.fixedDict(entries).startsWith("st.fixed_dictionaries({"), py.fixedDict(entries))
    assert(ts.fixedDict(entries).startsWith("fc.record({"), ts.fixedDict(entries))
    assertEquals(py.redactedPlaceholder, "st.just(\"***REDACTED***\")")
    assertEquals(ts.redactedPlaceholder, "fc.constant(\"***REDACTED***\")")

  test("functionName: Python snake_case vs TS camel-prefixed"):
    assertEquals(py.functionName("ShortCode"), "strategy_short_code")
    assertEquals(ts.functionName("ShortCode"), "strategyShortCode")

  // ---- ExprBackend: the same shared recursion/scoping renders per-language ----

  private def ctx(
      inputs: Set[String] = Set.empty,
      stateFields: Set[String] = Set.empty,
      unbacked: Set[String] = Set.empty
  ): TestCtx =
    TestCtx(
      inputs = inputs,
      outputs = Set.empty,
      stateFields = stateFields,
      mapStateFields = Set.empty,
      enumValues = Map.empty,
      userFunctions = Map.empty,
      userPredicates = Map.empty,
      boundVars = Set.empty,
      capture = CaptureMode.PostState,
      unbackedStateFields = unbacked
    )

  private def i1(n: Int): expr_full = IntLitF(int_of_integer(BigInt(n)), None)
  private def pyText(e: expr_full, c: TestCtx): String = ExprToPython.translate(e, c) match
    case ExprPy.Py(t)      => t
    case ExprPy.Skip(r, _) => s"<skip:$r>"
  private def tsText(e: expr_full, c: TestCtx): String = TsExprBackend.translate(e, c) match
    case ExprPy.Py(t)      => t
    case ExprPy.Skip(r, _) => s"<skip:$r>"

  List(
    ("bool", (BoolLitF(true, None): expr_full), "True", "true"),
    ("none", (NoneLitF(None): expr_full), "None", "null"),
    ("empty-set", (SetLiteralF(Nil, None): expr_full), "set()", "new Set()")
  ).foreach: (label, e, pyE, tsE) =>
    test(s"ExprBackend renders literal '$label' per-language"):
      assertEquals(pyText(e, ctx()), pyE)
      assertEquals(tsText(e, ctx()), tsE)

  test("equality/membership: Python operators vs TS structural helpers"):
    val c  = ctx(inputs = Set("x"), stateFields = Set("s"))
    val eq = BinaryOpF(BEq(), IdentifierF("x", None), i1(1), None)
    assertEquals(pyText(eq, c), "((x) == (1))")
    assertEquals(tsText(eq, c), "_eq((x), (1))")
    val mem = BinaryOpF(BIn(), IdentifierF("x", None), IdentifierF("s", None), None)
    assertEquals(pyText(mem, c), "((x) in (post_state[\"s\"]))")
    assertEquals(tsText(mem, c), "_in((x), (postState[\"s\"]))")

  test("cardinality + universal quantifier render structurally per-language"):
    val c    = ctx(stateFields = Set("s"))
    val card = UnaryOpF(UCardinality(), IdentifierF("s", None), None)
    assertEquals(pyText(card, c), "len(post_state[\"s\"])")
    assertEquals(tsText(card, c), "_len(postState[\"s\"])")
    val q = QuantifierF(
      QAll(),
      List(QuantifierBindingFull("e", IdentifierF("s", None), BkIn(), None)),
      BoolLitF(true, None),
      None
    )
    assert(pyText(q, c).startsWith("all("), pyText(q, c))
    assertEquals(
      tsText(q, c),
      "Array.from(post_state[\"s\"]).every((e) => (true))".replace("post_state", "postState")
    )

  test("unbacked-state skip is shared scoping logic — both backends skip"):
    val c = ctx(stateFields = Set("count"), unbacked = Set("count"))
    val e = IdentifierF("count", None)
    assert(pyText(e, c).contains("not backed by an entity table"), pyText(e, c))
    assert(tsText(e, c).contains("not backed by an entity table"), tsText(e, c))

  // ---- TsVitestHarness: TS scaffold + the _runtime.ts contract ----

  private def loadIR(path: String) =
    val src = scala.io.Source.fromFile(path).getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("TsVitestHarness emits the vitest scaffold with TS-shaped paths"):
    loadIR("fixtures/spec/url_shortener.spec").map: ir =>
      val files = TsVitestHarness.scaffoldFiles(ir)
      val paths = files.map(_.path).toSet
      assert(paths.contains("tests/_runtime.ts"), paths.toString)
      assert(paths.contains("tests/_client.ts"), paths.toString)
      assert(paths.contains("tests/_redaction.ts"), paths.toString)
      assert(paths.contains("tests/_predicates.ts"), paths.toString)
      assert(paths.contains("vitest.config.ts"), paths.toString)
      assert(paths.contains("tests/run_conformance.mjs"), paths.toString)
      assertEquals(TsVitestHarness.strategiesPath, "tests/_strategies.ts")
      assertEquals(TsVitestHarness.skipsPath, "tests/_testgen_skips.json")
      assertEquals(
        TsVitestHarness.behavioralTestPath("url_shortener"),
        "tests/url_shortener.behavioral.test.ts"
      )

  test("_runtime.ts exports exactly the helpers TsExprBackend emits"):
    loadIR("fixtures/spec/url_shortener.spec").map: ir =>
      val rt = TsVitestHarness
        .scaffoldFiles(ir)
        .find(_.path == "tests/_runtime.ts")
        .get
        .content
      for h <- List("_len", "_in", "_eq", "_union", "_inter", "_diff", "_subset", "_powerset")
      do assert(rt.contains(s"export function $h"), s"missing $h in _runtime.ts")

  test("_predicates.ts imports _runtime and renders user predicates via TsExprBackend"):
    loadIR("fixtures/spec/url_shortener.spec").map: ir =>
      val preds = TsVitestHarness
        .scaffoldFiles(ir)
        .find(_.path == "tests/_predicates.ts")
        .get
        .content
      assert(preds.contains("from \"./_runtime.js\""), preds.take(300))
      // url_shortener declares isValidURI; it must be emitted (real or honest stub).
      assert(preds.contains("export function isValidURI("), preds)

  test("TsBehavioral emits vitest+fast-check for the positive-ensures path"):
    loadIR("fixtures/spec/edge_cases.spec").map: ir =>
      val out = TsBehavioral.emitFor(SynthFixture.profiled(ir))
      val noInput = out.tests
        .find(_.name == "test_no_input_ensures_0")
        .getOrElse(fail(s"missing test_no_input_ensures_0; got ${out.tests.map(_.name)}"))
      assert(noInput.body.contains("client.post(\"/__test_admin__/reset\")"), noInput.body)
      assert(noInput.body.contains("const preState = "), noInput.body)
      assert(noInput.body.contains("const postState = "), noInput.body)
      assert(noInput.body.contains("const responseData = "), noInput.body)
      assert(noInput.body.contains("expect(response.status).toBe("), noInput.body)
      assert(noInput.body.contains("if (!("), noInput.body)
      // NoInput has no inputs -> the no-arbitrary branch (no fc.asyncProperty).
      assert(!noInput.body.contains("fc.asyncProperty"), noInput.body)
      // Honest-skip of the not-yet-ported kinds is recorded.
      assert(
        out.skips.exists(_.kind == "behavioral_ts_pending"),
        s"expected a behavioral_ts_pending skip; got ${out.skips.map(_.kind)}"
      )
      val mod = TsBehavioral.renderModule(ir, out.tests)
      assert(mod.contains("import { expect, test } from \"vitest\";"), mod.take(400))
      assert(mod.contains("import fc from \"fast-check\";"), mod.take(400))
      assert(mod.contains("import { client } from \"./_client.js\";"), mod.take(400))
      assert(mod.contains("const NUM_RUNS ="), mod.take(600))

  test("TsStateful emits a fast-check random-op-sequence with invariant checks"):
    loadIR("fixtures/spec/url_shortener.spec").map: ir =>
      val out = TsStateful.emitFor(SynthFixture.profiled(ir))
      val f   = out.file
      assert(f.contains("import fc from \"fast-check\";"), f.take(300))
      assert(f.contains("fc.asyncProperty("), f)
      assert(f.contains("fc.oneof("), f)
      assert(f.contains("client.post(\"/__test_admin__/reset\")"), f)
      assert(f.contains("async function dispatch(step: any)"), f)
      assert(f.contains("const STEP_COUNT ="), f)
      assert(f.contains("postState["), f)
      assert(f.contains("invariant violated:"), f)

  test("TsStateful honest-skips when invariants are unbacked (safe_counter)"):
    loadIR("fixtures/spec/safe_counter.spec").map: ir =>
      val out = TsStateful.emitFor(SynthFixture.profiled(ir))
      assert(out.file.contains("test.skip("), out.file)
      assert(out.file.contains("no assertable rules/invariants"), out.file)
      assert(
        out.skips.exists(s =>
          s.kind.startsWith("stateful_invariant") &&
            s.reason.contains("not backed by an entity table")
        ),
        s"expected unbacked-state invariant skip; got ${out.skips}"
      )

  test("Strategies.forIR is backend-parameterized: same IR, per-language specs"):
    loadIR("fixtures/spec/url_shortener.spec").map: ir =>
      val pySpecs = Strategies.forIR(ir) // default = PythonHypothesisStrategy
      val tsSpecs = Strategies.forIR(ir, TsFastCheckStrategy)
      assertEquals(pySpecs.map(_.typeName).toSet, tsSpecs.map(_.typeName).toSet)
      val pyShort = pySpecs.find(_.typeName == "ShortCode").get
      val tsShort = tsSpecs.find(_.typeName == "ShortCode").get
      assertEquals(pyShort.functionName, "strategy_short_code")
      assertEquals(tsShort.functionName, "strategyShortCode")
      assert(pyShort.body.contains("st.from_regex"), pyShort.body)
      assert(tsShort.body.contains("fc.stringMatching"), tsShort.body)
