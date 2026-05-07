package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class StrategyHeuristicTest extends CatsEffectSuite:

  private val state                    = Set("store")
  private val intT                     = NamedTypeF("Int", None)
  private def id(n: String): expr_full = IdentifierF(n, None)
  private def lit(n: Int): expr_full   = IntLitF(int_of_integer(BigInt(n)), None)
  private def in_(n: String): ParamDeclFull =
    ParamDeclFull(n, intT, None)

  private def op(
      name: String,
      ensures: List[expr_full],
      inputs: List[ParamDeclFull] = Nil,
      outputs: List[ParamDeclFull] = Nil
  ): OperationDeclFull =
    OperationDeclFull(name, inputs, outputs, Nil, ensures, None)

  test("empty ensures → LlmSynthesis (regression: no longer vacuously DirectEmit)"):
    val o = op("Mystery", Nil)
    assertEquals(Classify.classifyStrategy(o, state), SynthesisStrategy.LlmSynthesis)

  test("computed index `s'[x + 1] = 0` → LlmSynthesis (index must be a leaf)"):
    val computedIdx =
      BinaryOpF(BAdd(), id("x"), lit(1), None)
    val clause = BinaryOpF(
      BEq(),
      IndexF(PrimeF(id("store"), None), computedIdx, None),
      lit(0),
      None
    )
    val o = op("BadIdx", List(clause), inputs = List(in_("x")))
    assertEquals(Classify.classifyStrategy(o, state), SynthesisStrategy.LlmSynthesis)

  test("computed field-access index `s'[x + 1].field = v` → LlmSynthesis"):
    val computedIdx =
      BinaryOpF(BAdd(), id("x"), lit(1), None)
    val clause = BinaryOpF(
      BEq(),
      FieldAccessF(IndexF(PrimeF(id("store"), None), computedIdx, None), "field", None),
      id("v"),
      None
    )
    val o = op("BadFieldIdx", List(clause), inputs = List(in_("x"), in_("v")))
    assertEquals(Classify.classifyStrategy(o, state), SynthesisStrategy.LlmSynthesis)

  test("leaf-index pointwise update `s'[k] = v` still DirectEmit"):
    val clause = BinaryOpF(
      BEq(),
      IndexF(PrimeF(id("store"), None), id("k"), None),
      id("v"),
      None
    )
    val o = op("GoodIdx", List(clause), inputs = List(in_("k"), in_("v")))
    assertEquals(Classify.classifyStrategy(o, state), SynthesisStrategy.DirectEmit)
