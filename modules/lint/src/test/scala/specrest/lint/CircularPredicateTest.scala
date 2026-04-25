package specrest.lint

import munit.CatsEffectSuite
import specrest.lint.testutil.SpecFixtures

class CircularPredicateTest extends CatsEffectSuite:

  test("L06 catches mutually-recursive predicates"):
    SpecFixtures.loadLintIR("l06_circular_predicate_bad").map: ir =>
      val diags = CircularPredicate.run(ir)
      assert(diags.nonEmpty, "expected at least one cycle diagnostic")
      val d = diags.head
      assertEquals(d.code, "L06")
      assertEquals(d.level, LintLevel.Error)
      assert(d.message.contains("isA"), d.message)
      assert(d.message.contains("isB"), d.message)

  test("L06 silent on the all-lints-pass fixture"):
    SpecFixtures.loadLintIR("passing").map: ir =>
      assertEquals(CircularPredicate.run(ir), Nil)

  test("L06 silent on url_shortener regression fence"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      assertEquals(CircularPredicate.run(ir), Nil)
