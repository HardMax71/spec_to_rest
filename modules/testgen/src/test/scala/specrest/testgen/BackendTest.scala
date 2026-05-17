package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

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
