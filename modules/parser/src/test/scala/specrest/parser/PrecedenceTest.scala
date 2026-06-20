package specrest.parser

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.testutil.SpecFixtures

class PrecedenceTest extends CatsEffectSuite:

  private def serviceWith(invariant: String): String =
    s"""|service PrecDemo {
        |  state {
        |    count: Int
        |  }
        |  operation Noop {
        |    ensures:
        |      count' = count
        |  }
        |  invariant probe:
        |    $invariant
        |}
        |""".stripMargin

  private def probeBody(name: String, invariant: String) =
    SpecFixtures.buildFromSource(name, serviceWith(invariant)).map: ir =>
      invBody(svcInvariants(ir).head)

  test("`and` binds tighter than `implies`: `a and b implies c` = `(a and b) implies c`"):
    probeBody("and-implies", "count > 0 and count < 9 implies count > 5").map:
      case BinaryOpF(BImplies(), BinaryOpF(BAnd(), _, _, _), _, _) => ()
      case other                                                   => fail(s"expected Implies(And(_,_), _), got: $other")

  test("`implies` binds looser on the right too: `a implies b and c` = `a implies (b and c)`"):
    probeBody("implies-and", "count > 5 implies count > 0 and count < 9").map:
      case BinaryOpF(BImplies(), _, BinaryOpF(BAnd(), _, _, _), _) => ()
      case other                                                   => fail(s"expected Implies(_, And(_,_)), got: $other")

  test("`or` binds tighter than `implies`: `a or b implies c` = `(a or b) implies c`"):
    probeBody("or-implies", "count > 0 or count > 9 implies count > 5").map:
      case BinaryOpF(BImplies(), BinaryOpF(BOr(), _, _, _), _, _) => ()
      case other                                                  => fail(s"expected Implies(Or(_,_), _), got: $other")

  test("`not` binds tighter than `and`: `not a and b` = `(not a) and b`"):
    probeBody("not-and", "not count > 0 and count < 9").map:
      case BinaryOpF(BAnd(), UnaryOpF(UNot(), _, _), _, _) => ()
      case other                                           => fail(s"expected And(Not(_), _), got: $other")
