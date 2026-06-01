package specrest.verify

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.decimalToRat
import specrest.ir.generated.SpecRestGenerated.floatLitRat
import specrest.ir.generated.SpecRestGenerated.quotient_of

class FloatLiteralTest extends CatsEffectSuite:

  private def parse(s: String): Option[(BigInt, BigInt)] =
    decimalToRat(s).map(quotient_of)

  List(
    ("9.5", Some((BigInt(19), BigInt(2)))),
    ("-3.25", Some((BigInt(-13), BigInt(4)))),
    ("100", Some((BigInt(100), BigInt(1)))),
    ("0.0", Some((BigInt(0), BigInt(1)))),
    ("0.5", Some((BigInt(1), BigInt(2)))),
    ("-0.25", Some((BigInt(-1), BigInt(4)))),
    ("12.50", Some((BigInt(25), BigInt(2)))),
    ("abc", None),
    ("9.5.3", None),
    ("", None)
  ).foreach: (input, expected) =>
    test(s"decimalToRat parses '$input'"):
      assertEquals(parse(input), expected)

  test("floatLitRat defaults unparseable input to 0"):
    assertEquals(quotient_of(floatLitRat("not-a-number")), (BigInt(0), BigInt(1)))
    assertEquals(quotient_of(floatLitRat("9.5")), (BigInt(19), BigInt(2)))
