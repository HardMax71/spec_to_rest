package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
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
      val incOp = svcOperations(ir)
        .find(o => operName(o) == "Increment")
        .getOrElse(fail("no Increment"))
      val ctx = TestCtx.fromOperation(incOp, ir, CaptureMode.PreState)
      assert(ctx.stateFields.contains("count"), s"state fields=${ctx.stateFields}")
      assertEquals(ctx.inputs, Set.empty[String])
      assertEquals(ctx.outputs, Set.empty[String])
      assertEquals(ctx.capture, CaptureMode.PreState)

  test("ExprToPython translates safe_counter clauses via the backed scalar `count` (#407)"):
    val src = scala.io.Source
      .fromFile("fixtures/spec/safe_counter.spec")
      .getLines
      .mkString("\n")
    loadIR(src).map: ir =>
      val results = svcOperations(ir).flatMap: op =>
        val reqCtx = TestCtx.fromOperation(op, ir, CaptureMode.PreState)
        val ensCtx = TestCtx.fromOperation(op, ir, CaptureMode.PostState)
        operRequires(op).map(e =>
          s"${operName(op)}.requires" -> ExprToPython.translate(e, reqCtx)
        ) ++
          operEnsures(op).map(e => s"${operName(op)}.ensures" -> ExprToPython.translate(e, ensCtx))
      // `count` is an Int scalar backed by the service_state table since #407,
      // so every clause translates - nothing honest-skips.
      val skips = results.collect { case (n, Translated.Skip(r, _)) => n -> r }
      assert(skips.isEmpty, s"backed scalar state must not skip; got $skips")
      assert(
        results.exists {
          case (n, Translated.Emit(code)) =>
            n == "Decrement.ensures" && code.contains("post_state[\"count\"]")
          case _ => false
        },
        s"Decrement.ensures must translate against post_state; got $results"
      )
