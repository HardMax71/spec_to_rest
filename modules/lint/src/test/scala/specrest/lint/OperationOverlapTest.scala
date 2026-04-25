package specrest.lint

import munit.CatsEffectSuite
import specrest.lint.testutil.SpecFixtures

class OperationOverlapTest extends CatsEffectSuite:

  test("L04 fires on two ops with identical signature and equivalent requires"):
    SpecFixtures.loadLintIR("l04_overlap_bad").map: ir =>
      val diags = OperationOverlap.run(ir)
      assertEquals(diags.length, 1)
      val d = diags.head
      assertEquals(d.code, "L04")
      assertEquals(d.level, LintLevel.Warning)
      assert(d.message.contains("Increment"), d.message)
      assert(d.message.contains("Add"), d.message)
      assert(d.relatedSpans.nonEmpty, "expected a related span pointing to the first op")

  test("L04 silent on the all-lints-pass fixture"):
    SpecFixtures.loadLintIR("passing").map: ir =>
      assertEquals(OperationOverlap.run(ir), Nil)

  test("L04 silent on safe_counter (Increment vs Decrement differ in requires)"):
    SpecFixtures.loadIR("safe_counter").map: ir =>
      assertEquals(OperationOverlap.run(ir), Nil)

  test("L04 silent on todo_list (StartWork/Complete/Reopen differ on status guard)"):
    SpecFixtures.loadIR("todo_list").map: ir =>
      assertEquals(OperationOverlap.run(ir), Nil)

  test("L04 silent on url_shortener regression fence"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      assertEquals(OperationOverlap.run(ir), Nil)
