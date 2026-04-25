package specrest.lint

import munit.CatsEffectSuite
import specrest.lint.testutil.SpecFixtures

class TypeMismatchTest extends CatsEffectSuite:

  test("L01 catches obvious literal mixups (and 5, + true, >= \"zero\")"):
    SpecFixtures.loadLintIR("l01_type_mismatch_bad").map: ir =>
      val diags = TypeMismatch.run(ir)
      assert(diags.nonEmpty, "expected at least one type-mismatch diagnostic")
      assert(diags.forall(_.code == "L01"), diags)
      assert(diags.forall(_.level == LintLevel.Error), diags)
      val msgs = diags.map(_.message)
      assert(msgs.exists(_.contains("logical 'and'")), msgs)
      assert(msgs.exists(_.contains("arithmetic '+'")), msgs)
      assert(msgs.exists(_.contains("comparison '>'")), msgs)

  test("L01 silent on the all-lints-pass fixture"):
    SpecFixtures.loadLintIR("passing").map: ir =>
      assertEquals(TypeMismatch.run(ir), Nil)

  test("L01 silent on url_shortener regression fence"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      assertEquals(TypeMismatch.run(ir), Nil)
