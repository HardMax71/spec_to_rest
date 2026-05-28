package specrest.lint

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.SpanT
import specrest.ir.generated.SpecRestGenerated.less_int
import specrest.lint.testutil.SpecFixtures

class MissingEnsuresTest extends CatsEffectSuite:

  test("L03 fires on operation with outputs but empty ensures"):
    SpecFixtures.loadLintIR("l03_missing_ensures_bad").map: ir =>
      val diags = MissingEnsures.run(ir)
      assertEquals(diags.length, 1)
      val d = diags.head
      assertEquals(d.code, "L03")
      assertEquals(d.level, LintLevel.Warning)
      assert(d.message.contains("Read"), d.message)
      assert(
        d.span.exists { case SpanT(line, _, _, _) => less_int(BigInt(0), line) },
        s"expected span, got ${d.span}"
      )

  test("L03 silent on the all-lints-pass fixture"):
    SpecFixtures.loadLintIR("passing").map: ir =>
      assertEquals(MissingEnsures.run(ir), Nil)

  test("L03 silent on url_shortener regression fence"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      assertEquals(MissingEnsures.run(ir), Nil)
