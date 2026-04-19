package specrest.verify

import specrest.parser.Builder
import specrest.parser.Parse

class TranslatorSetOpsTest extends munit.FunSuite:

  private def scriptOf(spec: String): Z3Script =
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    Translator.translate(ir)

  private def smtOf(spec: String): String =
    SmtLib.renderSmtLib(scriptOf(spec))

  private def specWithInvariant(stateBlock: String, inv: String): String =
    s"""service S {
       |  state {
       |    $stateBlock
       |  }
       |  invariant inv:
       |    $inv
       |}""".stripMargin

  test("`id in {0, 1, 2}` lowers to a disjunction of equalities"):
    val out = smtOf(
      specWithInvariant(
        "counters: Int -> lone Int",
        "all id in counters | counters[id] in {0, 1, 2}"
      )
    )
    assert(out.contains("(or "), s"expected a disjunction in SMT; got:\n$out")
    assert(out.contains("(= "), s"expected equalities; got:\n$out")

  test("`x not in {5}` lowers to (not (= …)) with singleton not wrapped in or"):
    val out = smtOf(
      specWithInvariant(
        "counters: Int -> lone Int",
        "all id in counters | counters[id] not in {5}"
      )
    )
    assert(
      out.contains("(not (= ") || out.contains("(distinct"),
      s"expected negation-of-equality; got:\n$out"
    )

  test("`x in {a}` singleton collapses to a single equality (no Or wrapper)"):
    val out = smtOf(
      specWithInvariant(
        "counters: Int -> lone Int",
        "all id in counters | counters[id] in {7}"
      )
    )
    val membershipLine = out.linesIterator.find(_.contains("7")).getOrElse("")
    assert(
      !membershipLine.contains("(or "),
      s"should not wrap singleton in or; got line:\n$membershipLine"
    )

  test("`a union b` — set-typed state and invariant emits (union ...)"):
    val out = smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a union b) implies x in a or x in b"
      )
    )
    assert(out.contains("(union "), s"expected (union ...); got:\n$out")
    assert(out.contains("(select "), s"expected (select ...) membership; got:\n$out")

  test("`a intersect b` emits (intersection ...)"):
    val out = smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a intersect b) implies x in a"
      )
    )
    assert(out.contains("(intersection "), s"expected (intersection ...); got:\n$out")

  test("`a minus b` emits (setminus ...)"):
    val out = smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a minus b) implies x in a"
      )
    )
    assert(out.contains("(setminus "), s"expected (setminus ...); got:\n$out")

  test("`a subset b` emits (subset ...)"):
    val out = smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]",
        "a subset b implies b subset a or not (a subset b)"
      )
    )
    assert(out.contains("(subset "), s"expected (subset ...); got:\n$out")

  test("`Set[Int]`-typed state field declares a (Set Int)-valued function"):
    val out = smtOf(
      specWithInvariant(
        "a: Set[Int]",
        "true"
      )
    )
    assert(
      out.contains("(declare-fun state_a () (Set Int))"),
      s"expected (Set Int)-typed state decl; got:\n$out"
    )

  test("membership against a set-sorted expression falls through to (select)"):
    val out = smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a intersect b) implies x in (a union b)"
      )
    )
    assert(out.contains("(select "), s"expected (select ...) membership; got:\n$out")

  test("nested set sort — Set[Set[Int]] renders as (Set (Set Int))"):
    val out = smtOf(
      specWithInvariant(
        "ss: Set[Set[Int]]",
        "true"
      )
    )
    assert(
      out.contains("(declare-fun state_ss () (Set (Set Int)))"),
      s"expected nested set sort; got:\n$out"
    )

  test("empty set literal '{}' raises a sharp TranslatorError"):
    val spec = specWithInvariant(
      "a: Set[Int]",
      "a subset {}"
    )
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[TranslatorError]:
      val _ = Translator.translate(ir)
    assert(
      err.getMessage.contains("empty set literal"),
      s"expected empty-set error; got: ${err.getMessage}"
    )

  test("set operator on non-set operands raises a TranslatorError"):
    val spec = specWithInvariant(
      "a: Set[Int]\n    n: Int",
      "a subset n"
    )
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[TranslatorError]:
      val _ = Translator.translate(ir)
    assert(
      err.getMessage.contains("requires both operands to be sets"),
      s"expected non-set operand error; got: ${err.getMessage}"
    )

  test("set operator on mismatched element sorts raises a TranslatorError"):
    val spec = specWithInvariant(
      "a: Set[Int]\n    b: Set[Bool]",
      "a union b subset a"
    )
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[TranslatorError]:
      val _ = Translator.translate(ir)
    assert(
      err.getMessage.contains("same element sort"),
      s"expected element-sort mismatch error; got: ${err.getMessage}"
    )

  test("heterogeneous standalone set literal raises a TranslatorError"):
    val spec = specWithInvariant(
      "a: Set[Int]",
      "{1, true} subset a"
    )
    val parsed = Parse.parseSpec(spec)
    if parsed.errors.isEmpty then
      val ir = Builder.buildIR(parsed.tree)
      val err = intercept[TranslatorError]:
        val _ = Translator.translate(ir)
      assert(
        err.getMessage.contains("must all have the same sort"),
        s"expected hetero-sort error; got: ${err.getMessage}"
      )

  test("heterogeneous set literal in membership raises a TranslatorError"):
    val spec = specWithInvariant(
      "a: Set[Int]\n    x: Int",
      "x in {1, 2, true}"
    )
    val parsed = Parse.parseSpec(spec)
    if parsed.errors.isEmpty then
      val ir = Builder.buildIR(parsed.tree)
      val err = intercept[TranslatorError]:
        val _ = Translator.translate(ir)
      assert(
        err.getMessage.contains("must match the membership LHS sort") ||
          err.getMessage.contains("must all have the same sort"),
        s"expected hetero-sort error; got: ${err.getMessage}"
      )

  test("membership against a set-sorted expression enforces LHS element-sort match"):
    val spec = specWithInvariant(
      "a: Set[Int]\n    b: Set[Int]\n    flag: Bool",
      "flag in (a union b) implies true"
    )
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[TranslatorError]:
      val _ = Translator.translate(ir)
    assert(
      err.getMessage.contains("left-hand side sort to match") ||
        err.getMessage.contains("element sort"),
      s"expected LHS/elem mismatch error; got: ${err.getMessage}"
    )

  test("empty set literal error message does not mention 'typed receiver'"):
    val spec = specWithInvariant(
      "a: Set[Int]",
      "a subset {}"
    )
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[TranslatorError]:
      val _ = Translator.translate(ir)
    assert(
      !err.getMessage.contains("typed receiver"),
      s"error message should not suggest typed receiver; got: ${err.getMessage}"
    )

  test("powerset operator raises a sharp TranslatorError"):
    val spec = specWithInvariant(
      "a: Set[Int]\n    b: Set[Set[Int]]",
      "b subset ^a"
    )
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[TranslatorError]:
      val _ = Translator.translate(ir)
    assert(
      err.getMessage.contains("powerset"),
      s"expected powerset error; got: ${err.getMessage}"
    )
