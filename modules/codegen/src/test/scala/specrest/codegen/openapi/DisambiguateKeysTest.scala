package specrest.codegen.openapi

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

// Helpers in a companion object so the test-data-construction closures
// (foreach over case-tables at class-init time) don't capture `this` and
// trip Scala's safe-initialization checker.
object DisambiguateKeysTest:
  private def n(i: Int): nat                = Nata(BigInt(i))
  private def ident(s: String): IdentifierF = IdentifierF(s, None)
  private def call(name: String, arg: expr_full): CallF =
    CallF(ident(name), List(arg), None)
  private val dummyArg: expr_full = BoolLitF(true, None)

class DisambiguateKeysTest extends CatsEffectSuite:

  import DisambiguateKeysTest.*

  // -- showNat --

  List[(String, Int, String)](
    ("zero", 0, "0"),
    ("single-digit", 7, "7"),
    ("two-digit", 42, "42"),
    ("three-digit", 100, "100"),
    ("large", 12345, "12345")
  ).foreach: (name, input, expected) =>
    test(s"showNat: $name"):
      assertEquals(showNat(n(input)), expected)

  // -- anonInvariantName --

  List[(Int, String)](
    (0, "anon_0"),
    (1, "anon_1"),
    (42, "anon_42")
  ).foreach: (idx, expected) =>
    test(s"anonInvariantName($idx) = $expected"):
      assertEquals(anonInvariantName(n(idx)), expected)

  // -- disambiguateKeys (no collisions) --

  test("disambiguateKeys: empty input → empty output"):
    assertEquals(disambiguateKeys[Int](Nil), Nil)

  test("disambiguateKeys: unique names pass through unchanged, preserving order"):
    val input = List("a" -> 1, "b" -> 2, "c" -> 3)
    assertEquals(disambiguateKeys(input), input)

  // -- disambiguateKeys (collisions) --

  test("disambiguateKeys: duplicate name gets _0 suffix"):
    val input = List("foo" -> 1, "foo" -> 2)
    assertEquals(disambiguateKeys(input), List("foo" -> 1, "foo_0" -> 2))

  test("disambiguateKeys: triple duplicate gets _0, _1"):
    val input = List("foo" -> 1, "foo" -> 2, "foo" -> 3)
    assertEquals(disambiguateKeys(input), List("foo" -> 1, "foo_0" -> 2, "foo_1" -> 3))

  test("disambiguateKeys: existing _0 in input forces collision to skip to _1"):
    // matches the pathological case the original Scala asStableMap was
    // engineered to handle: ["foo", "foo_0", "foo"] should NOT collide
    // foo (#3) with the explicit foo_0 (#2) — should advance to foo_1
    val input = List("foo" -> 1, "foo_0" -> 2, "foo" -> 3)
    assertEquals(disambiguateKeys(input), List("foo" -> 1, "foo_0" -> 2, "foo_1" -> 3))

  test("disambiguateKeys: order preserved with mixed collisions"):
    val input = List("a" -> 1, "b" -> 2, "a" -> 3, "b" -> 4, "c" -> 5)
    assertEquals(
      disambiguateKeys(input),
      List("a" -> 1, "b" -> 2, "a_0" -> 3, "b_0" -> 4, "c" -> 5)
    )

  // -- parseTemporalBody --

  List[(String, expr_full, temporal_body)](
    ("always", call("always", dummyArg), TbAlways(dummyArg)),
    ("eventually", call("eventually", dummyArg), TbEventually(dummyArg)),
    ("fairness", call("fairness", dummyArg), TbFairness(dummyArg)),
    ("unknown call name", call("nope", dummyArg), TbInvalid(call("nope", dummyArg))),
    (
      "call with 0 args",
      CallF(ident("always"), Nil, None),
      TbInvalid(CallF(ident("always"), Nil, None))
    ),
    (
      "call with 2 args",
      CallF(ident("always"), List(dummyArg, dummyArg), None),
      TbInvalid(CallF(ident("always"), List(dummyArg, dummyArg), None))
    ),
    ("non-call expr", dummyArg, TbInvalid(dummyArg))
  ).foreach: (name, input, expected) =>
    test(s"parseTemporalBody: $name"):
      assertEquals(parseTemporalBody(input), expected)
