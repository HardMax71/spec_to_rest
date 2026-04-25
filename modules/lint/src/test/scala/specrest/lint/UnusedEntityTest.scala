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

  test("L05 counts `extends` parents as references"):
    val src =
      """service WithInheritance {
        |  entity Base {
        |    id: Int
        |    label: String
        |  }
        |  entity Child extends Base {
        |    name: String
        |  }
        |  state { items: Int -> lone Child }
        |  operation Add {
        |    input: id: Int, name: String
        |    requires: id > 0
        |    ensures: items'[id].id = id
        |  }
        |  invariant nonEmpty: true
        |}""".stripMargin
    SpecFixtures.buildFromSource("WithInheritance", src).map: ir =>
      val diags = UnusedEntity.run(ir)
      assertEquals(
        diags,
        Nil,
        s"Base should be referenced via Child extends; got: ${diags.map(_.message)}"
      )
