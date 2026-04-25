package specrest.lint

import munit.CatsEffectSuite
import specrest.lint.testutil.SpecFixtures

class UnusedEntityTest extends CatsEffectSuite:

  test("L05 fires on declared-but-unreferenced entity"):
    SpecFixtures.loadLintIR("l05_unused_entity_bad").map: ir =>
      val diags = UnusedEntity.run(ir)
      assertEquals(diags.length, 1)
      val d = diags.head
      assertEquals(d.code, "L05")
      assertEquals(d.level, LintLevel.Warning)
      assert(d.message.contains("Orphan"), d.message)

  test("L05 silent on the all-lints-pass fixture"):
    SpecFixtures.loadLintIR("passing").map: ir =>
      assertEquals(UnusedEntity.run(ir), Nil)

  test("L05 silent on url_shortener regression fence"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      assertEquals(UnusedEntity.run(ir), Nil)
