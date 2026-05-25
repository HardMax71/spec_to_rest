package specrest.codegen.openapi

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class OpenApiConstraintsTest extends CatsEffectSuite:

  private def ident(n: String): IdentifierF = IdentifierF(n, None)
  private def intL(n: Int): IntLitF         = IntLitF(int_of_integer(BigInt(n)), None)
  private def valueRef: IdentifierF         = ident("value")
  private def lenOfValue: expr_full         = CallF(ident("len"), List(valueRef), None)

  private def binOp(op: bin_op_full, l: expr_full, r: expr_full): BinaryOpF =
    BinaryOpF(op, l, r, None)

  private def walk(e: expr_full): openapi_int_bounds =
    visitConstraintOpenApi(e, emptyOpenApiIntBounds)

  private def fields(b: openapi_int_bounds) = b match
    case OpenApiIntBounds(ml, mxl, mn, mx, emn, emx, p) =>
      (ml, mxl, mn, mx, emn, emx, p)

  test("emptyOpenApiIntBounds is all None"):
    val (ml, mxl, mn, mx, emn, emx, p) = fields(emptyOpenApiIntBounds)
    assertEquals((ml, mxl, mn, mx, emn, emx, p), (None, None, None, None, None, None, None))

  test("RaValueCmp BGe sets inclusive minimum"):
    val res = walk(binOp(BGe(), valueRef, intL(10)))
    assertEquals(fields(res)._3, Some(int_of_integer(BigInt(10))))
    assertEquals(fields(res)._5, None) // exclusiveMinimum untouched

  test("RaValueCmp BGt sets exclusive minimum (preserved, not +1)"):
    val res = walk(binOp(BGt(), valueRef, intL(10)))
    assertEquals(fields(res)._3, None) // inclusive minimum untouched
    assertEquals(fields(res)._5, Some(int_of_integer(BigInt(10))))

  test("RaValueCmp BLe sets inclusive maximum"):
    val res = walk(binOp(BLe(), valueRef, intL(100)))
    assertEquals(fields(res)._4, Some(int_of_integer(BigInt(100))))

  test("RaValueCmp BLt sets exclusive maximum"):
    val res = walk(binOp(BLt(), valueRef, intL(100)))
    assertEquals(fields(res)._6, Some(int_of_integer(BigInt(100))))

  test("RaValueCmp BEq pins both bounds inclusive"):
    val res = walk(binOp(BEq(), valueRef, intL(42)))
    assertEquals(fields(res)._3, Some(int_of_integer(BigInt(42))))
    assertEquals(fields(res)._4, Some(int_of_integer(BigInt(42))))

  test("RaLenCmp BGe sets minLength"):
    val res = walk(binOp(BGe(), lenOfValue, intL(3)))
    assertEquals(fields(res)._1, Some(int_of_integer(BigInt(3))))

  test("RaLenCmp BGt collapses to +1 (length is integer-bound)"):
    val res = walk(binOp(BGt(), lenOfValue, intL(3)))
    assertEquals(fields(res)._1, Some(int_of_integer(BigInt(4))))

  test("RaLenCmp BLt skipped when n-1 would be negative"):
    val res = walk(binOp(BLt(), lenOfValue, intL(0)))
    assertEquals(fields(res)._2, None)

  test("negative length bound is rejected silently"):
    val res = walk(binOp(BGe(), lenOfValue, intL(-5)))
    assertEquals(fields(res)._1, None)

  test("MatchesF on value sets pattern via RaMatches"):
    val matches = MatchesF(valueRef, "^foo$", None)
    val r       = walk(matches)
    assertEquals(fields(r)._7, Some("^foo$"))

  test("AND-chain accumulates tighter bounds"):
    val e = binOp(
      BAnd(),
      binOp(BGe(), valueRef, intL(10)),
      binOp(BLe(), valueRef, intL(100))
    )
    val res = walk(e)
    assertEquals(fields(res)._3, Some(int_of_integer(BigInt(10))))
    assertEquals(fields(res)._4, Some(int_of_integer(BigInt(100))))

  test("conflicting min picks the tighter (higher) bound"):
    val e = binOp(
      BAnd(),
      binOp(BGe(), valueRef, intL(5)),
      binOp(BGe(), valueRef, intL(20))
    )
    val res = walk(e)
    assertEquals(fields(res)._3, Some(int_of_integer(BigInt(20))))

  test("conflicting max picks the tighter (lower) bound"):
    val e = binOp(
      BAnd(),
      binOp(BLe(), valueRef, intL(100)),
      binOp(BLe(), valueRef, intL(50))
    )
    val res = walk(e)
    assertEquals(fields(res)._4, Some(int_of_integer(BigInt(50))))

  test("inclusive and exclusive minimum tracked independently"):
    val e = binOp(
      BAnd(),
      binOp(BGe(), valueRef, intL(10)),
      binOp(BGt(), valueRef, intL(20))
    )
    val res = walk(e)
    assertEquals(fields(res)._3, Some(int_of_integer(BigInt(10))))
    assertEquals(fields(res)._5, Some(int_of_integer(BigInt(20))))
