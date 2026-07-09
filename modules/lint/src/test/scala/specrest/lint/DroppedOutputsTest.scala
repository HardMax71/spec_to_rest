package specrest.lint

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.SpanT
import specrest.ir.generated.SpecRestGenerated.less_int
import specrest.lint.testutil.SpecFixtures

class DroppedOutputsTest extends CatsEffectSuite:

  private def deleteSpec(output: String, conventions: String): String =
    s"""service DropDemo {
       |  entity Item {
       |    id: Int
       |    name: String
       |  }
       |
       |  state {
       |    items: Int -> Item
       |  }
       |
       |  operation DeleteItem {
       |    input: id: Int
       |    output: $output
       |
       |    requires:
       |      id in items
       |
       |    ensures:
       |      id not in items'
       |  }
       |$conventions}""".stripMargin

  private val overrideBlock =
    """  conventions {
      |    DeleteItem.http_status_success = 200
      |  }
      |""".stripMargin

  List(
    ("warns on entity output with defaulted 204", "item: Item", "", 1),
    ("silent when http_status_success overrides to 200", "item: Item", overrideBlock, 0),
    ("silent on single Bool flag output with 204", "deleted: Bool", "", 0)
  ).foreach: (label, output, conventions, expected) =>
    test(s"L07 $label"):
      SpecFixtures.buildFromSource("DropDemo", deleteSpec(output, conventions)).map: ir =>
        val diags = DroppedOutputs.run(ir)
        assertEquals(diags.length, expected, diags.map(_.message).toString)

  test("L07 names the op, the 204, the dropped outputs, and the override"):
    SpecFixtures.buildFromSource("DropDemo", deleteSpec("item: Item", "")).map: ir =>
      val diags = DroppedOutputs.run(ir)
      assertEquals(diags.length, 1)
      val d = diags.head
      assertEquals(d.code, "L07")
      assertEquals(d.level, LintLevel.Warning)
      assert(d.message.contains("DeleteItem"), d.message)
      assert(d.message.contains("204"), d.message)
      assert(d.message.contains("'item'"), d.message)
      assert(d.message.contains("DeleteItem.http_status_success"), d.message)
      assert(
        d.span.exists { case SpanT(line, _, _, _) => less_int(BigInt(0), line) },
        s"expected span, got ${d.span}"
      )

  test("L07 silent on the all-lints-pass fixture"):
    SpecFixtures.loadLintIR("passing").map: ir =>
      assertEquals(DroppedOutputs.run(ir), Nil)

  List("url_shortener", "todo_list", "auth_service", "ecommerce").foreach: name =>
    test(s"L07 silent on fixtures/spec/$name.spec"):
      SpecFixtures.loadIR(name).map: ir =>
        assertEquals(DroppedOutputs.run(ir), Nil)
