package specrest.testgen

import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse

class TestCtxTest extends CatsEffectSuite:

  private def loadIR(specSrc: String) =
    Parse.parseSpec(specSrc).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("fromOperation derives input/output/state names from real IR (safe_counter)"):
    val src = scala.io.Source
      .fromFile("fixtures/spec/safe_counter.spec")
      .getLines
      .mkString("\n")
    loadIR(src).map: ir =>
      val incOp = ir.operations.find(_.name == "Increment").getOrElse(fail("no Increment"))
      val ctx   = TestCtx.fromOperation(incOp, ir, CaptureMode.PreState)
      assert(ctx.stateFields.contains("count"), s"state fields=${ctx.stateFields}")
      assertEquals(ctx.inputs, Set.empty[String])
      assertEquals(ctx.outputs, Set.empty[String])
      assertEquals(ctx.capture, CaptureMode.PreState)

  test("ExprToPython translates every safe_counter clause without Skip"):
    val src = scala.io.Source
      .fromFile("fixtures/spec/safe_counter.spec")
      .getLines
      .mkString("\n")
    loadIR(src).map: ir =>
      ir.operations.foreach: op =>
        val reqCtx = TestCtx.fromOperation(op, ir, CaptureMode.PreState)
        val ensCtx = TestCtx.fromOperation(op, ir, CaptureMode.PostState)
        op.requires.foreach: e =>
          val r = ExprToPython.translate(e, reqCtx)
          assert(
            r.isInstanceOf[ExprPy.Py],
            s"requires of ${op
                .name} skipped: ${r match { case ExprPy.Skip(s, _) => s; case _ => "?" }}"
          )
        op.ensures.foreach: e =>
          val r = ExprToPython.translate(e, ensCtx)
          assert(
            r.isInstanceOf[ExprPy.Py],
            s"ensures of ${op
                .name} skipped: ${r match { case ExprPy.Skip(s, _) => s; case _ => "?" }}"
          )
