package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class StrategyHeuristicTest extends CatsEffectSuite:

  private given CanEqual[synthesis_strategy, synthesis_strategy] = CanEqual.derived

  private val state               = List("store")
  private def id(n: String): expr = IdentifierF(n, None)
  private def lit(n: Int): expr   = IntLitF(BigInt(n), None)

  private def strategyOf(ensures: List[expr]): synthesis_strategy =
    classifyStrategy(ensures, Nil, Nil, state, Nil, Nil)

  private def scalarStrategyOf(
      ensures: List[expr],
      requires: List[expr] = Nil,
      inputs: List[String] = Nil
  ): synthesis_strategy =
    classifyStrategy(ensures, requires, inputs, List("count"), List("count"), Nil)

  private def prime(e: expr): expr         = PrimeF(e, None)
  private def beq(l: expr, r: expr): expr  = BinaryOpF(BEq(), l, r, None)
  private def badd(l: expr, r: expr): expr = BinaryOpF(BAdd(), l, r, None)
  private def bsub(l: expr, r: expr): expr = BinaryOpF(BSub(), l, r, None)
  private def bgt(l: expr, r: expr): expr  = BinaryOpF(BGt(), l, r, None)
  private def scalarInc: expr              = beq(prime(id("count")), badd(id("count"), lit(1)))

  test("scalar `count' = count + 1` → DirectEmit"):
    assertEquals(scalarStrategyOf(List(scalarInc)), DirectEmit(): synthesis_strategy)

  test("scalar `count' = count - 1` with guardable requires → DirectEmit"):
    val dec = beq(prime(id("count")), bsub(id("count"), lit(1)))
    assertEquals(
      scalarStrategyOf(List(dec), requires = List(bgt(id("count"), lit(0)))),
      DirectEmit(): synthesis_strategy
    )

  test("scalar update with unguardable requires → LlmSynthesis"):
    val req = bgt(badd(id("count"), lit(1)), lit(0))
    assertEquals(
      scalarStrategyOf(List(scalarInc), requires = List(req)),
      LlmSynthesis(): synthesis_strategy
    )

  test("scalar update over input arithmetic `count' = count + n` → LlmSynthesis"):
    val clause = beq(prime(id("count")), badd(id("count"), id("n")))
    assertEquals(scalarStrategyOf(List(clause)), LlmSynthesis(): synthesis_strategy)

  test("mixed scalar + relation clauses → LlmSynthesis (purity rule)"):
    val rel = beq(prime(id("store")), id("store"))
    assertEquals(
      classifyStrategy(
        List(scalarInc, rel),
        Nil,
        Nil,
        List("count", "store"),
        List("count"),
        Nil
      ),
      LlmSynthesis(): synthesis_strategy
    )

  test("conflicting duplicate scalar assignments → LlmSynthesis"):
    val other = beq(prime(id("count")), badd(id("count"), lit(2)))
    assertEquals(
      scalarStrategyOf(List(scalarInc, other)),
      LlmSynthesis(): synthesis_strategy
    )

  test("identical duplicate scalar assignments → DirectEmit"):
    assertEquals(
      scalarStrategyOf(List(scalarInc, scalarInc)),
      DirectEmit(): synthesis_strategy
    )

  test("scalar update with declared inputs → LlmSynthesis"):
    assertEquals(
      scalarStrategyOf(List(scalarInc), inputs = List("x")),
      LlmSynthesis(): synthesis_strategy
    )

  test("empty ensures → LlmSynthesis (regression: no longer vacuously DirectEmit)"):
    assertEquals(strategyOf(Nil), LlmSynthesis(): synthesis_strategy)

  test("computed index `s'[x + 1] = 0` → LlmSynthesis (index must be a leaf)"):
    val computedIdx =
      BinaryOpF(BAdd(), id("x"), lit(1), None)
    val clause = BinaryOpF(
      BEq(),
      IndexF(PrimeF(id("store"), None), computedIdx, None),
      lit(0),
      None
    )
    assertEquals(strategyOf(List(clause)), LlmSynthesis(): synthesis_strategy)

  test("computed field-access index `s'[x + 1].field = v` → LlmSynthesis"):
    val computedIdx =
      BinaryOpF(BAdd(), id("x"), lit(1), None)
    val clause = BinaryOpF(
      BEq(),
      FieldAccessF(IndexF(PrimeF(id("store"), None), computedIdx, None), "field", None),
      id("v"),
      None
    )
    assertEquals(strategyOf(List(clause)), LlmSynthesis(): synthesis_strategy)

  test("leaf-index pointwise update `s'[k] = v` still DirectEmit"):
    val clause = BinaryOpF(
      BEq(),
      IndexF(PrimeF(id("store"), None), id("k"), None),
      id("v"),
      None
    )
    assertEquals(strategyOf(List(clause)), DirectEmit(): synthesis_strategy)
