package specrest.verify

import cats.effect.IO
import cats.effect.Resource
import com.microsoft.z3.Context
import com.microsoft.z3.Sort
import com.microsoft.z3.Symbol
import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.verify.z3.Z3Sort

class IrValueDecoderTest extends CatsEffectSuite:

  private val ctxR: Resource[IO, Context] =
    Resource.make(IO.blocking(new Context()))(c => IO.blocking(c.close()))

  test("decodes none/some option datatype values"):
    ctxR.use: ctx =>
      IO.blocking:
        val noneC = ctx.mkConstructor[Sort](
          "none_Int",
          "isNone_Int",
          Array.empty[String],
          Array.empty[Sort],
          Array.empty[Int]
        )
        val someC = ctx.mkConstructor[Sort](
          "some_Int",
          "isSome_Int",
          Array("valOf_Int"),
          Array[Sort](ctx.getIntSort),
          Array(0)
        )
        val _     = ctx.mkDatatypeSort[Sort]("Option_Int", Array(noneC, someC))
        val sort  = Z3Sort.OptionOf(Z3Sort.Int)
        val noneV = ctx.mkApp(noneC.ConstructorDecl())
        val someV = ctx.mkApp(someC.ConstructorDecl(), ctx.mkInt(7))
        assertEquals(IrValueDecoder.decodeZ3(noneV, sort, Map.empty), Some(VNone()))
        assertEquals(
          IrValueDecoder.decodeZ3(someV, sort, Map.empty),
          Some(VSome(VInt(BigInt(7))))
        )

  test("decodes seq values built from empty/unit/concat"):
    ctxR.use: ctx =>
      IO.blocking:
        val sort  = Z3Sort.SeqOf(Z3Sort.Int)
        val empty = ctx.mkEmptySeq(ctx.mkSeqSort(ctx.getIntSort))
        val one   = ctx.mkUnit(ctx.mkInt(1))
        val cat   = ctx.mkConcat(ctx.mkUnit(ctx.mkInt(1)), ctx.mkUnit(ctx.mkInt(2)))
        assertEquals(IrValueDecoder.decodeZ3(empty, sort, Map.empty), Some(VSeq(Nil)))
        assertEquals(
          IrValueDecoder.decodeZ3(one, sort, Map.empty),
          Some(VSeq(List(VInt(BigInt(1)))))
        )
        assertEquals(
          IrValueDecoder.decodeZ3(cat, sort, Map.empty),
          Some(VSeq(List(VInt(BigInt(1)), VInt(BigInt(2)))))
        )

  test("decodes map values as seqs of entry tuples"):
    ctxR.use: ctx =>
      IO.blocking:
        val sort = Z3Sort.MapOf(Z3Sort.Str, Z3Sort.Int)
        val tup = ctx.mkTupleSort(
          ctx.mkSymbol("MapEntry_String_Int"),
          Array[Symbol](ctx.mkSymbol("key_String_Int"), ctx.mkSymbol("val_String_Int")),
          Array[Sort](ctx.getStringSort, ctx.getIntSort)
        )
        val entry = ctx.mkApp(tup.mkDecl(), ctx.mkString("a"), ctx.mkInt(1))
        val seq1  = ctx.mkUnit(entry)
        assertEquals(
          IrValueDecoder.decodeZ3(seq1, sort, Map.empty),
          Some(VMap(List((VStr("a"), VInt(BigInt(1))))))
        )

  test("rejects shapes that do not match the expected sort"):
    ctxR.use: ctx =>
      IO.blocking:
        assertEquals(
          IrValueDecoder.decodeZ3(ctx.mkInt(3), Z3Sort.OptionOf(Z3Sort.Int), Map.empty),
          None
        )
        assertEquals(
          IrValueDecoder.decodeZ3(ctx.mkInt(3), Z3Sort.SeqOf(Z3Sort.Int), Map.empty),
          None
        )
