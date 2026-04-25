package specrest.lint

import munit.CatsEffectSuite
import specrest.lint.testutil.SpecFixtures

class UndefinedRefTest extends CatsEffectSuite:

  test("L02 catches misspelled state-field references"):
    SpecFixtures.loadLintIR("l02_undefined_ref_bad").map: ir =>
      val diags = UndefinedRef.run(ir)
      val names = diags.map(_.message)
      assert(diags.forall(_.code == "L02"), diags)
      assert(diags.forall(_.level == LintLevel.Error), diags)
      assert(names.exists(_.contains("ammount")), names)
      assert(names.exists(_.contains("cnt")), names)

  test("L02 silent on the all-lints-pass fixture"):
    SpecFixtures.loadLintIR("passing").map: ir =>
      assertEquals(UndefinedRef.run(ir), Nil)

  test("L02 silent on url_shortener regression fence"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      assertEquals(UndefinedRef.run(ir), Nil)

  test("L02 flags undefined identifier in callee position"):
    val src =
      """service CalleeTypo {
        |  state { count: Int }
        |  operation Touch {
        |    requires:
        |      missingFn(count)
        |    ensures:
        |      count' = count
        |  }
        |}""".stripMargin
    SpecFixtures.buildFromSource("CalleeTypo", src).map: ir =>
      val diags = UndefinedRef.run(ir)
      assert(diags.exists(_.message.contains("missingFn")), diags.map(_.message))

  test("L02 walks transition `when` guards"):
    val src =
      """service TransitionGuard {
        |  enum Status {
        |    OPEN,
        |    CLOSED
        |  }
        |  entity Thing {
        |    id: Int
        |    status: Status
        |  }
        |  state { x: Int }
        |  transition Lifecycle {
        |    entity: Thing
        |    field: status
        |    OPEN -> CLOSED via Close when missingGuard(x)
        |  }
        |  operation Close {
        |    requires: true
        |    ensures: x' = x
        |  }
        |  invariant nonNeg: x >= 0
        |}""".stripMargin
    SpecFixtures.buildFromSource("TransitionGuard", src).map: ir =>
      val diags = UndefinedRef.run(ir)
      assert(diags.exists(_.message.contains("missingGuard")), diags.map(_.message))
