package specrest.synth

import cats.effect.IO
import specrest.ir.generated.SpecRestGenerated.classificationOperationName

final case class IterationRecord(
    iteration: Int,
    body: String,
    fullCandidate: String,
    errors: List[VerifierError],
    usage: TokenUsage,
    costUsd: Double
) derives CanEqual

final case class CegisHistory(records: List[IterationRecord]) derives CanEqual:
  def add(r: IterationRecord): CegisHistory = CegisHistory(records :+ r)
  def lastBody: Option[String]              = records.lastOption.map(_.body)

object CegisHistory:
  val empty: CegisHistory = CegisHistory(Nil)

sealed trait CegisOutcome derives CanEqual

object CegisOutcome:
  final case class Verified(
      body: String,
      fullCandidate: String,
      iterations: Int,
      history: CegisHistory
  ) extends CegisOutcome

  final case class Aborted(
      reason: AbortReason,
      lastBody: Option[String],
      history: CegisHistory
  ) extends CegisOutcome

final class CegisLoop(
    provider: LlmProvider,
    verifier: DafnyVerifier,
    cache: Option[Cache],
    tracker: Tracker,
    budget: CegisBudget,
    dafnyTimeoutSec: Int = 60,
    withHints: Boolean = false
):

  def run(req: SynthRequest): IO[CegisOutcome] =
    val key = Cache.keyFor(req.header, req.model, req.temperature)
    cacheLookup(key).flatMap:
      case Some(entry) => onCacheHit(req, entry)
      case None        => iterate(req, key, 1, CegisHistory.empty, None)

  private def cacheLookup(key: CacheKey): IO[Option[CacheEntry]] =
    cache match
      case Some(c) => c.lookup(key)
      case None    => IO.pure(None)

  private def onCacheHit(req: SynthRequest, entry: CacheEntry): IO[CegisOutcome] =
    val cost    = Pricing.costOrZero(entry.usage, entry.model)
    val fullDfy =
      FileAssembly
        .splice(req.skeleton, classificationOperationName(req.classification), entry.body)
        .toOption
        .getOrElse(entry.candidate)
    val rec  = IterationRecord(0, entry.body, fullDfy, Nil, entry.usage, cost)
    val call = CallRecord(
      classificationOperationName(req.classification),
      entry.model,
      entry.usage,
      cost,
      cached = true
    )
    tracker
      .record(call)
      .as(
        CegisOutcome.Verified(
          body = entry.body,
          fullCandidate = fullDfy,
          iterations = 0,
          history = CegisHistory(List(rec))
        )
      )

  private def iterate(
      req: SynthRequest,
      key: CacheKey,
      i: Int,
      history: CegisHistory,
      prevError: Option[VerifierError]
  ): IO[CegisOutcome] =
    budgetCheck(history, i) match
      case Some(reason) => IO.pure(CegisOutcome.Aborted(reason, history.lastBody, history))
      case None         =>
        val prompt = i match
          case 1 =>
            PromptBuilder.initial(req.classification, req.header, req.skeleton, req.strategy)
          case _ =>
            (history.lastBody, prevError) match
              case (Some(prev), Some(err)) =>
                PromptBuilder.repair(
                  req.classification,
                  req.header,
                  req.skeleton,
                  prev,
                  err,
                  withHints
                )
              case _ =>
                PromptBuilder.initial(req.classification, req.header, req.skeleton, req.strategy)
        val llmReq = LlmRequest(
          system = prompt.system,
          userMessage = prompt.user,
          model = req.model,
          maxTokens = req.maxTokens,
          temperature = req.temperature
        )
        provider.complete(llmReq).flatMap:
          case Left(err)   => abort(AbortReason.ProviderFailed(err, i), history)
          case Right(resp) => afterLlm(req, key, i, history, resp)

  private def afterLlm(
      req: SynthRequest,
      key: CacheKey,
      i: Int,
      history: CegisHistory,
      resp: LlmResponse
  ): IO[CegisOutcome] =
    val cost     = Pricing.costOrZero(resp.usage, resp.model)
    val opName   = classificationOperationName(req.classification)
    val callRec  = CallRecord(opName, resp.model, resp.usage, cost, cached = false)
    val recorded = tracker.record(callRec)
    val parsed   =
      for
        block <- ResponseParser.extractCodeBlock(resp.text)
        body  <- ResponseParser.extractMethodBody(block, opName)
      yield (block, body)
    parsed match
      case Left(perr) =>
        recorded *> abort(AbortReason.ResponseUnparseable(perr, i), history)
      case Right((block, body)) =>
        DiffChecker.check(req.header, block) match
          case Left(diff) =>
            recorded *> abort(AbortReason.DiffViolation(diff, i), history)
          case Right(()) =>
            recorded *> verifyAndContinue(req, key, i, history, resp, block, body, cost)

  private def verifyAndContinue(
      req: SynthRequest,
      key: CacheKey,
      i: Int,
      history: CegisHistory,
      resp: LlmResponse,
      block: String,
      body: String,
      cost: Double
  ): IO[CegisOutcome] =
    FileAssembly.splice(
      req.skeleton,
      classificationOperationName(req.classification),
      body,
      ResponseParser.helperSection(block, classificationOperationName(req.classification))
    ) match
      case Left(failure) =>
        abort(AbortReason.SpliceFailed(failure.message, i), history)
      case Right(fullDfy) =>
        verifier.verify(fullDfy, dafnyTimeoutSec).flatMap:
          case Left(backendErr) =>
            abort(AbortReason.VerifierBackendFailure(backendErr, i), history)
          case Right(run) =>
            val errors      = run.errorsFor(classificationOperationName(req.classification))
            val record      = IterationRecord(i, body, fullDfy, errors, resp.usage, cost)
            val nextHistory = history.add(record)
            if run.verifiedFor(classificationOperationName(req.classification)) then
              cacheStore(key, block, body, resp).as(
                CegisOutcome.Verified(body, fullDfy, i, nextHistory): CegisOutcome
              )
            else
              repeatedErrorCheck(nextHistory, errors) match
                case Some(reason) =>
                  IO.pure(CegisOutcome.Aborted(reason, Some(body), nextHistory): CegisOutcome)
                case None =>
                  iterate(req, key, i + 1, nextHistory, errors.headOption)

  private def cacheStore(
      key: CacheKey,
      block: String,
      body: String,
      resp: LlmResponse
  ): IO[Unit] =
    cache match
      case None    => IO.unit
      case Some(c) =>
        val entry = CacheEntry(block, body, resp.usage, resp.model, SynthPromptVersion)
        c.store(key, entry).attempt.flatMap:
          case Right(_) => IO.unit
          case Left(e)  =>
            IO.consoleForIO.errorln(s"warning: cache write failed: ${e.getMessage}")

  private def abort(reason: AbortReason, history: CegisHistory): IO[CegisOutcome] =
    IO.pure(CegisOutcome.Aborted(reason, history.lastBody, history))

  private def budgetCheck(history: CegisHistory, nextIteration: Int): Option[AbortReason] =
    if nextIteration > budget.maxIterations then
      Some(AbortReason.BudgetExhausted(BudgetKind.MaxIterations))
    else
      val totalIn  = history.records.map(_.usage.inputTokens.toLong).sum
      val totalOut = history.records.map(_.usage.outputTokens.toLong).sum
      val totalUsd = history.records.map(_.costUsd).sum
      if totalIn >= budget.maxInputTokens then
        Some(AbortReason.BudgetExhausted(BudgetKind.InputTokens))
      else if totalOut >= budget.maxOutputTokens then
        Some(AbortReason.BudgetExhausted(BudgetKind.OutputTokens))
      else if totalUsd >= budget.maxCostUsd then
        Some(AbortReason.BudgetExhausted(BudgetKind.Cost))
      else None

  private def repeatedErrorCheck(
      history: CegisHistory,
      currentErrors: List[VerifierError]
  ): Option[AbortReason] =
    currentErrors.headOption.flatMap: head =>
      val recent        = history.records.takeRight(budget.repeatedErrorThreshold)
      val sameSignature =
        recent.flatMap(_.errors.headOption).count(e => sameError(e, head))
      if sameSignature >= budget.repeatedErrorThreshold then
        Some(AbortReason.StuckOnSameError(head, sameSignature))
      else None

  private def sameError(a: VerifierError, b: VerifierError): Boolean =
    a.category == b.category && a.line == b.line
