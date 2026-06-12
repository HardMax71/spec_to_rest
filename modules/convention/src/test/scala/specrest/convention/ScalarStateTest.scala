package specrest.convention

import munit.CatsEffectSuite
import specrest.convention.testutil.SpecFixtures
import specrest.ir.generated.SpecRestGenerated.*

class ScalarStateTest extends CatsEffectSuite:

  private def counterSpec(invariant: String) =
    s"""service SeedProbe {
       |
       |  state {
       |    level: Int
       |  }
       |
       |  operation Bump {
       |    requires:
       |      true
       |
       |    ensures:
       |      level' = level + 1
       |  }
       |
       |  invariant levelBounds:
       |    $invariant
       |}""".stripMargin

  List(
    ("level >= 0", BigInt(0)),
    ("level > 0", BigInt(1)),
    ("level < 0", BigInt(-1)),
    ("level <= -5", BigInt(-5)),
    ("level != 0", BigInt(1))
  ).foreach: (inv, expected) =>
    test(s"seed for `$inv` is $expected"):
      SpecFixtures.buildFromSource(inv, counterSpec(inv)).map: ir =>
        assertEquals(seedOf(ir), Some(expected))

  test("non-atom invariant mention conservatively unbacks the field"):
    val spec =
      """service SeedProbe {
        |
        |  state {
        |    level: Int
        |    others: Int -> lone Int
        |  }
        |
        |  operation Bump {
        |    requires:
        |      true
        |
        |    ensures:
        |      level' = level + 1
        |  }
        |
        |  invariant levelFresh:
        |    level not in others
        |}""".stripMargin
    SpecFixtures.buildFromSource("levelFresh", spec).map: ir =>
      assertEquals(ScalarState.fieldNames(ir), Nil)

  private def seedOf(ir: ServiceIRFull): Option[BigInt] =
    ScalarState.fieldsWithSeeds(ir).collectFirst:
      case (sf, seed) if stfName(sf) == "level" => seed
