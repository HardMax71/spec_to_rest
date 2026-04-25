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
