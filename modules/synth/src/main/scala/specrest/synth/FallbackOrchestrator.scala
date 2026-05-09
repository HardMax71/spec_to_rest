package specrest.synth

import cats.effect.IO
import cats.effect.kernel.Ref

final case class FallbackBudget(
    promptStrategies: List[PromptStrategy] = PromptStrategy.FallbackOrder,
    modelLadder: List[String] = Nil,
    sharedCostCapUsd: Double = 1.00,
    sharedInputTokenCap: Long = 100_000L,
    sharedOutputTokenCap: Long = 50_000L
) derives CanEqual

object FallbackBudget:
  val Default: FallbackBudget = FallbackBudget()

final case class AttemptRecord(
    strategy: PromptStrategy,
    model: String,
    cegisOutcome: CegisOutcome,
    costUsd: Double,
    inputTokens: Long,
    outputTokens: Long
) derives CanEqual

sealed trait FallbackOutcome derives CanEqual:
  def attempts: List[AttemptRecord]

object FallbackOutcome:

  final case class Verified(
      body: String,
      fullCandidate: String,
      finalStrategy: PromptStrategy,
      finalModel: String,
      cegisIterations: Int,
      attempts: List[AttemptRecord]
  ) extends FallbackOutcome

  final case class SkeletonOnly(
      body: String,
      fullCandidate: String,
      reason: String,
      attempts: List[AttemptRecord]
  ) extends FallbackOutcome

final class FallbackOrchestrator(
    provider: LlmProvider,
    verifier: DafnyVerifier,
    cache: Option[Cache],
    skeletonCache: Option[Cache],
    tracker: Tracker,
    perAttemptBudget: CegisBudget,
    fallbackBudget: FallbackBudget,
    dafnyTimeoutSec: Int = 60
):

  def run(req: SynthRequest): IO[FallbackOutcome] =
    val ladder =
      if fallbackBudget.modelLadder.isEmpty then List(req.model) else fallbackBudget.modelLadder
    val plan =
      for
        model <- ladder
        strat <- fallbackBudget.promptStrategies
      yield (model, strat)
    Ref.of[IO, List[AttemptRecord]](Nil).flatMap: history =>
      Ref.of[IO, BudgetSpent](BudgetSpent.empty).flatMap: spent =>
        runPlan(req, plan, history, spent)

  private def runPlan(
      req: SynthRequest,
      plan: List[(String, PromptStrategy)],
      history: Ref[IO, List[AttemptRecord]],
      spent: Ref[IO, BudgetSpent]
  ): IO[FallbackOutcome] = plan match
    case Nil =>
      finalize(req, history, "exhausted all (model, strategy) combinations")
    case (model, strat) :: rest =>
      spent.get.flatMap: s =>
        budgetExceeded(s) match
          case Some(reason) => finalize(req, history, reason)
          case None => runOne(req, model, strat, history, spent) *>
              history.get.flatMap: hs =>
                hs.headOption match
                  case Some(AttemptRecord(_, _, _: CegisOutcome.Verified, _, _, _)) =>
                    verifiedOutcome(hs.reverse)
                  case _ => runPlan(req, rest, history, spent)

  private def runOne(
      req: SynthRequest,
      model: String,
      strat: PromptStrategy,
      history: Ref[IO, List[AttemptRecord]],
      spent: Ref[IO, BudgetSpent]
  ): IO[Unit] =
    val attemptReq = req.copy(model = model, strategy = strat)
    val loop       = new CegisLoop(provider, verifier, cache, tracker, perAttemptBudget, dafnyTimeoutSec)
    loop.run(attemptReq).flatMap: outcome =>
      val (cost, ins, outs) = outcomeCost(outcome)
      val rec               = AttemptRecord(strat, model, outcome, cost, ins, outs)
      history.update(rec :: _) *> spent.update(_.add(cost, ins, outs))

  private def verifiedOutcome(attempts: List[AttemptRecord]): IO[FallbackOutcome] =
    val verifiedAttempt = attempts.collectFirst:
      case rec @ AttemptRecord(_, _, v: CegisOutcome.Verified, _, _, _) => (rec, v)
    verifiedAttempt match
      case Some((rec, verified)) =>
        IO.pure(
          FallbackOutcome.Verified(
            body = verified.body,
            fullCandidate = verified.fullCandidate,
            finalStrategy = rec.strategy,
            finalModel = rec.model,
            cegisIterations = verified.iterations,
            attempts = attempts
          )
        )
      case None =>
        IO.raiseError(
          new IllegalStateException(
            s"verifiedOutcome called without a Verified record (attempts=${attempts.length})"
          )
        )

  private def finalize(
      req: SynthRequest,
      history: Ref[IO, List[AttemptRecord]],
      reason: String
  ): IO[FallbackOutcome] =
    history.get.map(_.reverse).flatMap(finalizeFromList(req, _, reason))

  private def finalizeFromList(
      req: SynthRequest,
      attempts: List[AttemptRecord],
      reason: String
  ): IO[FallbackOutcome] =
    val lastAbort = attempts.reverse.collectFirst:
      case AttemptRecord(_, _, CegisOutcome.Aborted(r, _, _), _, _, _) => r.message
    val abortReason = lastAbort.getOrElse(reason)
    val (finalStrategy, finalModel) = attempts.lastOption match
      case Some(rec) => (rec.strategy, rec.model)
      case None      => (req.strategy, req.model)
    val body = SkeletonGenerator.fallbackBody(
      header = req.header,
      attempts = attempts.length,
      finalStrategy = PromptStrategy.displayName(finalStrategy),
      finalModel = finalModel,
      reason = abortReason
    )
    val fullDfy =
      FileAssembly.splice(req.skeleton, req.header.name, body) match
        case Right(text) => text
        case Left(_)     => req.skeleton
    val outcome = FallbackOutcome.SkeletonOnly(body, fullDfy, abortReason, attempts)
    persistSkeleton(req, body, fullDfy).as(outcome)

  private def persistSkeleton(
      req: SynthRequest,
      body: String,
      fullCandidate: String
  ): IO[Unit] =
    skeletonCache match
      case None => IO.unit
      case Some(c) =>
        val key = Cache.keyFor(req.header, req.model, req.temperature)
        val entry = CacheEntry(
          candidate = fullCandidate,
          body = body,
          usage = TokenUsage(0, 0),
          model = req.model,
          promptVersion = SynthPromptVersion,
          outcome = CacheOutcome.Skeleton
        )
        c.store(key, entry).attempt.flatMap:
          case Right(_) => IO.unit
          case Left(e) =>
            IO.consoleForIO.errorln(s"warning: skeleton cache write failed: ${e.getMessage}")

  private def budgetExceeded(spent: BudgetSpent): Option[String] =
    if spent.costUsd >= fallbackBudget.sharedCostCapUsd then
      Some(f"shared cost cap reached: $$${spent.costUsd}%.4f")
    else if spent.inputTokens >= fallbackBudget.sharedInputTokenCap then
      Some(s"shared input-token cap reached: ${spent.inputTokens}")
    else if spent.outputTokens >= fallbackBudget.sharedOutputTokenCap then
      Some(s"shared output-token cap reached: ${spent.outputTokens}")
    else None

  private def outcomeCost(outcome: CegisOutcome): (Double, Long, Long) =
    val recs = outcome match
      case CegisOutcome.Verified(_, _, _, hist) => hist.records
      case CegisOutcome.Aborted(_, _, hist)     => hist.records
    val cost = recs.map(_.costUsd).sum
    val ins  = recs.map(_.usage.inputTokens.toLong).sum
    val outs = recs.map(_.usage.outputTokens.toLong).sum
    (cost, ins, outs)

  final private case class BudgetSpent(
      costUsd: Double,
      inputTokens: Long,
      outputTokens: Long
  ) derives CanEqual:
    def add(c: Double, i: Long, o: Long): BudgetSpent =
      BudgetSpent(costUsd + c, inputTokens + i, outputTokens + o)

  private object BudgetSpent:
    val empty: BudgetSpent = BudgetSpent(0.0, 0L, 0L)
