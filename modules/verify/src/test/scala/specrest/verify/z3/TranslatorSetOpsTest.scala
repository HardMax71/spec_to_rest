package specrest.verify.z3

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.ir.VerifyError
import specrest.verify.testutil.SpecFixtures

class TranslatorSetOpsTest extends CatsEffectSuite:

  private def scriptOf(spec: String): IO[Z3Script] =
    SpecFixtures.buildFromSource("spec", spec).flatMap: ir =>
      Translator.translate(ir).map(_.toOption.get)

  private def smtOf(spec: String): IO[String] =
    scriptOf(spec).map(SmtLib.renderSmtLib(_))

  private def translatorErrorOf(spec: String): IO[VerifyError.Translator] =
    SpecFixtures.buildFromSource("spec", spec).flatMap: ir =>
      Translator.translate(ir).map:
        case Left(e)  => e
        case Right(_) => fail("expected translator error")

  private def specWithInvariant(stateBlock: String, inv: String): String =
    s"""service S {
       |  state {
       |    $stateBlock
       |  }
       |  invariant inv:
       |    $inv
       |}""".stripMargin

  test("`id in {0, 1, 2}` lowers to a disjunction of equalities"):
    smtOf(
      specWithInvariant(
        "counters: Int -> lone Int",
        "all id in counters | counters[id] in {0, 1, 2}"
      )
    ).map: out =>
      assert(out.contains("(or "), s"expected a disjunction in SMT; got:\n$out")
      assert(out.contains("(= "), s"expected equalities; got:\n$out")

  test("`x not in {5}` lowers to (not (= …)) with singleton not wrapped in or"):
    smtOf(
      specWithInvariant(
        "counters: Int -> lone Int",
        "all id in counters | counters[id] not in {5}"
      )
    ).map: out =>
      assert(
        out.contains("(not (= ") || out.contains("(distinct"),
        s"expected negation-of-equality; got:\n$out"
      )

  test("`x in {a}` singleton collapses to a single equality (no Or wrapper)"):
    smtOf(
      specWithInvariant(
        "counters: Int -> lone Int",
        "all id in counters | counters[id] in {7}"
      )
    ).map: out =>
      val membershipLine = out.linesIterator.find(_.contains("7")).getOrElse("")
      assert(
        !membershipLine.contains("(or "),
        s"should not wrap singleton in or; got line:\n$membershipLine"
      )

  test("`a union b` — set-typed state and invariant emits (union ...)"):
    smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a union b) implies x in a or x in b"
      )
    ).map: out =>
      assert(out.contains("(union "), s"expected (union ...); got:\n$out")
      assert(out.contains("(select "), s"expected (select ...) membership; got:\n$out")

  test("`a intersect b` emits (intersection ...)"):
    smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a intersect b) implies x in a"
      )
    ).map: out =>
      assert(out.contains("(intersection "), s"expected (intersection ...); got:\n$out")

  test("`a minus b` emits (setminus ...)"):
    smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a minus b) implies x in a"
      )
    ).map: out =>
      assert(out.contains("(setminus "), s"expected (setminus ...); got:\n$out")

  test("`a subset b` emits (subset ...)"):
    smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]",
        "a subset b implies b subset a or not (a subset b)"
      )
    ).map: out =>
      assert(out.contains("(subset "), s"expected (subset ...); got:\n$out")

  test("`Set[Int]`-typed state field declares a (Set Int)-valued function"):
    smtOf(specWithInvariant("a: Set[Int]", "true")).map: out =>
      assert(
        out.contains("(declare-fun state_a () (Set Int))"),
        s"expected (Set Int)-typed state decl; got:\n$out"
      )

  test("membership against a set-sorted expression falls through to (select)"):
    smtOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    x: Int",
        "x in (a intersect b) implies x in (a union b)"
      )
    ).map: out =>
      assert(out.contains("(select "), s"expected (select ...) membership; got:\n$out")

  test("nested set sort — Set[Set[Int]] renders as (Set (Set Int))"):
    smtOf(specWithInvariant("ss: Set[Set[Int]]", "true")).map: out =>
      assert(
        out.contains("(declare-fun state_ss () (Set (Set Int)))"),
        s"expected nested set sort; got:\n$out"
      )

  test("empty set literal '{}' raises a sharp TranslatorError"):
    translatorErrorOf(specWithInvariant("a: Set[Int]", "a subset {}")).map: err =>
      assert(
        err.message.contains("empty set literal"),
        s"expected empty-set error; got: ${err.message}"
      )

  test("set operator on non-set operands raises a TranslatorError"):
    translatorErrorOf(specWithInvariant("a: Set[Int]\n    n: Int", "a subset n")).map: err =>
      assert(
        err.message.contains("requires both operands to be sets"),
        s"expected non-set operand error; got: ${err.message}"
      )

  test("set operator on mismatched element sorts raises a TranslatorError"):
    translatorErrorOf(
      specWithInvariant("a: Set[Int]\n    b: Set[Bool]", "a union b subset a")
    ).map: err =>
      assert(
        err.message.contains("same element sort"),
        s"expected element-sort mismatch error; got: ${err.message}"
      )

  test("heterogeneous standalone set literal raises a TranslatorError"):
    translatorErrorOf(specWithInvariant("a: Set[Int]", "{1, true} subset a")).map: err =>
      assert(
        err.message.contains("must all have the same sort"),
        s"expected hetero-sort error; got: ${err.message}"
      )

  test("heterogeneous set literal in membership raises a TranslatorError"):
    translatorErrorOf(specWithInvariant("a: Set[Int]\n    x: Int", "x in {1, 2, true}")).map: err =>
      assert(
        err.message.contains("must match the membership LHS sort") ||
          err.message.contains("must all have the same sort"),
        s"expected hetero-sort error; got: ${err.message}"
      )

  test("membership against a set-sorted expression enforces LHS element-sort match"):
    translatorErrorOf(
      specWithInvariant(
        "a: Set[Int]\n    b: Set[Int]\n    flag: Bool",
        "flag in (a union b) implies true"
      )
    ).map: err =>
      assert(
        err.message.contains("left-hand side sort to match") ||
          err.message.contains("element sort"),
        s"expected LHS/elem mismatch error; got: ${err.message}"
      )

  test("empty set literal error message does not mention 'typed receiver'"):
    translatorErrorOf(specWithInvariant("a: Set[Int]", "a subset {}")).map: err =>
      assert(
        !err.message.contains("typed receiver"),
        s"error message should not suggest typed receiver; got: ${err.message}"
      )

  test("powerset operator raises a sharp TranslatorError"):
    translatorErrorOf(
      specWithInvariant("a: Set[Int]\n    b: Set[Set[Int]]", "b subset ^a")
    ).map: err =>
      assert(
        err.message.contains("powerset"),
        s"expected powerset error; got: ${err.message}"
      )
