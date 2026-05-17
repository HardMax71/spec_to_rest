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
      val incOp = ir.g
        .collect { case o: OperationDeclFull => o }
        .find(_.a == "Increment")
        .getOrElse(fail("no Increment"))
      val ctx = TestCtx.fromOperation(incOp, ir, CaptureMode.PreState)
      assert(ctx.stateFields.contains("count"), s"state fields=${ctx.stateFields}")
      assertEquals(ctx.inputs, Set.empty[String])
      assertEquals(ctx.outputs, Set.empty[String])
      assertEquals(ctx.capture, CaptureMode.PreState)

  test("ExprToPython honest-skips safe_counter clauses that touch unbacked scalar `count`"):
    val src = scala.io.Source
      .fromFile("fixtures/spec/safe_counter.spec")
      .getLines
      .mkString("\n")
    loadIR(src).map: ir =>
      val results = ir.g.collect { case o: OperationDeclFull => o }.flatMap: op =>
        val reqCtx = TestCtx.fromOperation(op, ir, CaptureMode.PreState)
        val ensCtx = TestCtx.fromOperation(op, ir, CaptureMode.PostState)
        op.d.map(e => s"${op.a}.requires" -> ExprToPython.translate(e, reqCtx)) ++
          op.e.map(e => s"${op.a}.ensures" -> ExprToPython.translate(e, ensCtx))
      val skips = results.collect { case (n, ExprPy.Skip(r, _)) => n -> r }
      // `count` is the only state and it is unbacked; every clause that references it
      // must honest-skip rather than emit a `None`-comparison that crashes at runtime.
      assert(skips.nonEmpty, s"expected unbacked-state skips; got $results")
      assert(
        skips.forall(_._2.contains("not backed by an entity table")),
        s"safe_counter clauses should only skip for the unbacked-state reason; got $skips"
      )
      // `Increment.requires` is the literal `true` — no state reference, stays translatable.
      assert(
        results.exists { case (n, r) => n == "Increment.requires" && r.isInstanceOf[ExprPy.Py] },
        s"Increment.requires (`true`) must still translate; got $results"
      )
