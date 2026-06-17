package specrest.verify.z3

import cats.effect.IO
import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*

import scala.util.boundary

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Return",
    "org.wartremover.warts.OptionPartial"
  )
)
object Translator extends Declarations with ExpressionEncoder with RelationFrames
    with SmtTermBridge:

  def translate(ir: service_ir): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        declareBase(ctx, ir)
        for inv <- svcInvariants(ir) do emitTopLevelInvariant(ctx, inv)
        Right(finalizeScript(ctx))
    }

  def translateOperationRequires(
      ir: service_ir,
      op: operation_decl
  ): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        declareBase(ctx, ir)
        val env = declareOperationInputs(ctx, op)
        for req <- operRequires(op) do ctx.assertions += translateCheckedExpr(ctx, req, env)
        Right(finalizeScript(ctx))
    }

  def translateOperationEnabled(
      ir: service_ir,
      op: operation_decl
  ): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        declareBase(ctx, ir)
        for inv <- svcInvariants(ir) do emitTopLevelInvariant(ctx, inv)
        val env = declareOperationInputs(ctx, op)
        for req <- operRequires(op) do ctx.assertions += translateCheckedExpr(ctx, req, env)
        Right(finalizeScript(ctx))
    }

  def translateOperationPreservation(
      ir: service_ir,
      op: operation_decl,
      inv: invariant_decl
  ): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        ctx.hasPostState = true
        declareBase(ctx, ir)
        svcState(ir).foreach(s => declareStatePostState(ctx, s))
        val env = declareOperationInputs(ctx, op)
        declareOperationOutputs(ctx, op, env)
        for preInv <- svcInvariants(ir) do
          ctx.assertions += translateCheckedExpr(ctx, invBody(preInv), env)
        for req <- operRequires(op) do ctx.assertions += translateCheckedExpr(ctx, req, env)
        for ens <- operEnsures(op) do ctx.assertions += translateEnsuresClause(ctx, ens, env)
        synthesizeFrame(ctx, svcState(ir), op, env)
        synthesizeCardinalityAxioms(ctx, svcState(ir), op)
        val postInv =
          withStateMode(ctx, StateMode.Post, () => translateCheckedExpr(ctx, invBody(inv), env))
        ctx.assertions += Z3Expr.Not(postInv).withSpan(invSpan(inv))
        Right(finalizeScript(ctx))
    }
