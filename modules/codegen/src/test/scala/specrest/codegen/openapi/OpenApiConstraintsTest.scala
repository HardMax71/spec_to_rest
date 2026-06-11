package specrest.codegen.openapi

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class OpenApiConstraintsTest extends CatsEffectSuite:

  private def ident(n: String): IdentifierF = IdentifierF(n, None)
  private def intL(n: Int): IntLitF         = IntLitF(BigInt(n), None)
  private def floatL(v: String): FloatLitF  = FloatLitF(v, None)
  private val valueRef: IdentifierF         = ident("value")
  private val lenOfValue: expr              = CallF(ident("len"), List(valueRef), None)

  private def binOp(op: bin_op, l: expr, r: expr): BinaryOpF =
    BinaryOpF(op, l, r, None)

  private def walk(e: expr): openapi_bounds =
    visitConstraintOpenApi(e, emptyOpenApiBounds)

  private def i(n: Int): BigInt                = BigInt(n)
  private def dec(m: Int, e: Int): decimal_lit = DecimalLit(i(m), i(e))

  // Named accessors over the positional Isabelle-extracted case class.
  extension (b: openapi_bounds)
    private def minLength: Option[BigInt] = b match
      case OpenApiBounds(v, _, _, _, _, _, _) => v
    private def maxLength: Option[BigInt] = b match
      case OpenApiBounds(_, v, _, _, _, _, _) => v
    private def minimum: Option[decimal_lit] = b match
      case OpenApiBounds(_, _, v, _, _, _, _) => v
    private def maximum: Option[decimal_lit] = b match
      case OpenApiBounds(_, _, _, v, _, _, _) => v
    private def exclusiveMinimum: Option[decimal_lit] = b match
      case OpenApiBounds(_, _, _, _, v, _, _) => v
    private def exclusiveMaximum: Option[decimal_lit] = b match
      case OpenApiBounds(_, _, _, _, _, v, _) => v
    private def pattern: Option[String] = b match
      case OpenApiBounds(_, _, _, _, _, _, v) => v

  test("emptyOpenApiBounds is all None"):
    val b = emptyOpenApiBounds
    assertEquals(b.minLength, None)
    assertEquals(b.maxLength, None)
    assertEquals(b.minimum, None)
    assertEquals(b.maximum, None)
    assertEquals(b.exclusiveMinimum, None)
    assertEquals(b.exclusiveMaximum, None)
    assertEquals(b.pattern, None)

  // -- parseDecimalLit ----------------------------------------------------

  List[(String, String, Option[decimal_lit])](
    ("integer", "5", Some(dec(5, 0))),
    ("integer-large", "100", Some(dec(100, 0))),
    ("zero", "0", Some(dec(0, 0))),
    ("fractional", "3.14", Some(dec(314, -2))),
    ("fractional-leading-zero", "0.5", Some(dec(5, -1))),
    ("fractional-trailing-zero", "1.0", Some(dec(10, -1))),
    ("negative-integer", "-5", Some(dec(-5, 0))),
    ("negative-fractional", "-3.14", Some(dec(-314, -2))),
    ("empty", "", None),
    ("non-numeric", "abc", None),
    ("scientific-rejected", "1.5e2", None),
    ("trailing-dot", "1.", None),
    ("leading-dot", ".5", None),
    ("sign-only", "-", None),
    ("internal-letter", "1a", None)
  ).foreach: (name, input, expected) =>
    test(s"parseDecimalLit: $name"):
      assertEquals(parseDecimalLit(input), expected)

  // -- decimalToNonNegInt -------------------------------------------------

  List[(String, decimal_lit, Option[BigInt])](
    ("plain integer", dec(5, 0), Some(i(5))),
    ("zero", dec(0, 0), Some(i(0))),
    ("trailing-zero-fractional", dec(10, -1), Some(i(1))), // 1.0
    ("multiple-trailing-zeros", dec(100, -2), Some(i(1))), // 1.00
    ("non-exact-fractional", dec(15, -1), None),           // 1.5
    ("pi-fractional", dec(314, -2), None),                 // 3.14
    ("negative-integer", dec(-5, 0), None),
    ("positive-exponent", dec(3, 2), Some(i(300))) // 3e2 = 300
  ).foreach: (name, input, expected) =>
    test(s"decimalToNonNegInt: $name"):
      assertEquals(decimalToNonNegInt(input), expected)

  // -- RaValueCmp (Int path) ----------------------------------------------

  List[(String, bin_op, Int, openapi_bounds => Option[decimal_lit], decimal_lit)](
    ("BGe → inclusive min", BGe(), 10, _.minimum, dec(10, 0)),
    ("BGt → exclusive min (NOT +1)", BGt(), 10, _.exclusiveMinimum, dec(10, 0)),
    ("BLe → inclusive max", BLe(), 100, _.maximum, dec(100, 0)),
    ("BLt → exclusive max", BLt(), 100, _.exclusiveMaximum, dec(100, 0))
  ).foreach: (name, op, n, field, expected) =>
    test(s"RaValueCmp: $name"):
      assertEquals(field(walk(binOp(op, valueRef, intL(n)))), Some(expected))

  test("RaValueCmp BEq pins both inclusive bounds"):
    val res = walk(binOp(BEq(), valueRef, intL(42)))
    assertEquals(res.minimum, Some(dec(42, 0)))
    assertEquals(res.maximum, Some(dec(42, 0)))

  // -- RaLenCmp (Int path) ------------------------------------------------

  List[(String, bin_op, Int, openapi_bounds => Option[BigInt], Int)](
    ("BGe → minLength", BGe(), 3, _.minLength, 3),
    ("BGt → minLength +1 (length is integer-valued)", BGt(), 3, _.minLength, 4),
    ("BLe → maxLength", BLe(), 10, _.maxLength, 10),
    ("BLt → maxLength -1", BLt(), 10, _.maxLength, 9)
  ).foreach: (name, op, n, field, expected) =>
    test(s"RaLenCmp: $name"):
      assertEquals(field(walk(binOp(op, lenOfValue, intL(n)))), Some(i(expected)))

  test("RaLenCmp BLt n=0 dropped (would go negative)"):
    assertEquals(walk(binOp(BLt(), lenOfValue, intL(0))).maxLength, None)

  test("RaLenCmp negative literal silently dropped"):
    assertEquals(walk(binOp(BGe(), lenOfValue, intL(-5))).minLength, None)

  // -- Float literal numeric bounds --------------------------------------

  List[(String, bin_op, String, openapi_bounds => Option[decimal_lit], decimal_lit)](
    ("BGe → minimum", BGe(), "1.5", _.minimum, dec(15, -1)),
    ("BGt → exclusiveMinimum", BGt(), "3.14", _.exclusiveMinimum, dec(314, -2)),
    ("BLe → maximum", BLe(), "100.5", _.maximum, dec(1005, -1)),
    ("BLt → exclusiveMaximum", BLt(), "99.9", _.exclusiveMaximum, dec(999, -1))
  ).foreach: (name, op, v, field, expected) =>
    test(s"Float-literal numeric: $name"):
      assertEquals(field(walk(binOp(op, valueRef, floatL(v)))), Some(expected))

  // -- Float literal length bounds (integer-coerce or drop) --------------

  List[(String, String, Option[Int])](
    ("integer-valued .0 accepted", "3.0", Some(3)),
    ("multiple zeros accepted", "5.00", Some(5)),
    ("fractional rejected", "3.5", None),
    ("negative rejected", "-1.0", None)
  ).foreach: (name, v, expected) =>
    test(s"Float-literal length: $name"):
      val got = walk(binOp(BGe(), lenOfValue, floatL(v))).minLength
      assertEquals(got, expected.map(i))

  // -- Pattern -----------------------------------------------------------

  test("MatchesF on value sets pattern"):
    val res = walk(MatchesF(valueRef, "^foo$", None))
    assertEquals(res.pattern, Some("^foo$"))

  // -- AND-chain accumulation --------------------------------------------

  test("AND-chain: int + float tighter inclusive minimum wins"):
    val e = binOp(
      BAnd(),
      binOp(BGe(), valueRef, intL(2)),
      binOp(BGe(), valueRef, floatL("3.5"))
    )
    // 3.5 > 2, so 3.5 wins
    assertEquals(walk(e).minimum, Some(dec(35, -1)))

  test("AND-chain: two float bounds, tighter (lower) max wins"):
    val e = binOp(
      BAnd(),
      binOp(BLe(), valueRef, floatL("100.0")),
      binOp(BLe(), valueRef, floatL("50.5"))
    )
    assertEquals(walk(e).maximum, Some(dec(505, -1)))

  test("AND-chain: inclusive and exclusive minima tracked independently"):
    val e = binOp(
      BAnd(),
      binOp(BGe(), valueRef, floatL("1.5")),
      binOp(BGt(), valueRef, intL(20))
    )
    val res = walk(e)
    assertEquals(res.minimum, Some(dec(15, -1)))
    assertEquals(res.exclusiveMinimum, Some(dec(20, 0)))
