package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class StrategyHeuristicTest extends CatsEffectSuite:

  private given CanEqual[synthesis_strategy, synthesis_strategy] = CanEqual.derived

  private val state                    = List("store")
  private def id(n: String): expr_full = IdentifierF(n, None)
  private def lit(n: Int): expr_full   = IntLitF(int_of_integer(BigInt(n)), None)

  private def strategyOf(ensures: List[expr_full]): synthesis_strategy =
    classifyStrategy(ensures, state, Nil)

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
