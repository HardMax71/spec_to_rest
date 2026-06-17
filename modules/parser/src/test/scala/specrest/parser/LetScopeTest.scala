package specrest.parser

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.testutil.SpecFixtures

class LetScopeTest extends CatsEffectSuite:

  private def service(op: String): String =
    s"""|service Demo {
        |  state {
        |    count: Int
        |  }
        |
        |$op
        |}
        |""".stripMargin

  private def ensuresOf(ir: service_ir): List[expr] =
    operEnsures(svcOperations(ir).head)

  test("let scopes the newline-separated clauses that follow it in an ensures block"):
    val src = service(
      """|  operation Bump {
         |    ensures:
         |      let c = count in
         |        count' = c + 1
         |        count' > c
         |  }
         |""".stripMargin
    )
    SpecFixtures.buildFromSource("let-block", src).map: ir =>
      ensuresOf(ir) match
        case List(LetF("c", _, BinaryOpF(BAnd(), _, _, _), _)) => ()
        case other                                             => fail(s"expected a single let folding both clauses, got: $other")

  test("clauses before the let stay siblings; the let only absorbs what follows"):
    val src = service(
      """|  operation Bump {
         |    ensures:
         |      count' >= 0
         |      let c = count in
         |        count' = c + 1
         |        count' > c
         |  }
         |""".stripMargin
    )
    SpecFixtures.buildFromSource("let-mid", src).map: ir =>
      ensuresOf(ir) match
        case List(_, LetF("c", _, BinaryOpF(BAnd(), _, _, _), _)) => ()
        case other                                                => fail(s"expected [clause, let(c1 and c2)], got: $other")

  test("an `and`-connected let (single clause) is left unchanged"):
    val src = service(
      """|  operation Bump {
         |    ensures:
         |      let c = count in
         |        count' = c + 1 and count' > c
         |  }
         |""".stripMargin
    )
    SpecFixtures.buildFromSource("let-and", src).map: ir =>
      ensuresOf(ir) match
        case List(LetF("c", _, BinaryOpF(BAnd(), _, _, _), _)) => ()
        case other                                             => fail(s"expected a single unchanged let, got: $other")

  test("a let with no following clauses is unchanged (no spurious folding)"):
    val src = service(
      """|  operation Bump {
         |    ensures:
         |      let c = count in count' = c + 1
         |  }
         |""".stripMargin
    )
    SpecFixtures.buildFromSource("let-solo", src).map: ir =>
      ensuresOf(ir) match
        case List(LetF("c", _, BinaryOpF(BEq(), _, _, _), _)) => ()
        case other                                            => fail(s"expected a single let with a bare body, got: $other")
