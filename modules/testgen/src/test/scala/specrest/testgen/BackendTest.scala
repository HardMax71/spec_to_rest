package specrest.testgen

import munit.CatsEffectSuite

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
