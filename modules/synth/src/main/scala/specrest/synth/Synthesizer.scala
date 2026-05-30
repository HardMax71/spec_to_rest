package specrest.synth

import cats.effect.IO
import specrest.dafny.DafnyMethodHeader
import specrest.ir.generated.SpecRestGenerated.classificationOperationName
import specrest.ir.generated.SpecRestGenerated.operation_classification

final case class SynthRequest(
    classification: operation_classification,
    header: DafnyMethodHeader,
    skeleton: String,
    model: String,
    temperature: Double = 0.0,
    maxTokens: Int = 2048,
    strategy: PromptStrategy = PromptStrategy.ZeroShot
)

final case class SynthResult(
    body: String,
    fullCandidate: String,
    usage: TokenUsage,
    costUsd: Double,
    cached: Boolean,
    model: String
)

final class Synthesizer(
    provider: LlmProvider,
    cache: Option[Cache],
    tracker: Tracker
):

  def synthesize(req: SynthRequest): IO[Either[SynthError, SynthResult]] =
    val key = Cache.keyFor(req.header, req.model, req.temperature)
    cacheLookup(key).flatMap:
      case Some(entry) => onCacheHit(req, entry)
      case None        => runUncached(req, key)

  private def cacheLookup(key: CacheKey): IO[Option[CacheEntry]] =
    cache match
      case Some(c) => c.lookup(key)
      case None    => IO.pure(None)

  private def onCacheHit(
      req: SynthRequest,
      entry: CacheEntry
  ): IO[Either[SynthError, SynthResult]] =
    val cost = Pricing.costOrZero(entry.usage, entry.model)
    val rec  =
      CallRecord(
        classificationOperationName(req.classification),
        entry.model,
        entry.usage,
        cost,
        cached = true
      )
    tracker.record(rec).as(
      Right(SynthResult(entry.body, entry.candidate, entry.usage, cost, cached = true, entry.model))
    )

  private def runUncached(req: SynthRequest, key: CacheKey): IO[Either[SynthError, SynthResult]] =
    val prompt = PromptBuilder.initial(req.classification, req.header, req.skeleton, req.strategy)
    val llmReq = LlmRequest(prompt.system, prompt.user, req.model, req.maxTokens, req.temperature)
    provider.complete(llmReq).flatMap:
      case Left(err) =>
        val failed = CallRecord(
          classificationOperationName(req.classification),
          req.model,
          TokenUsage(0, 0),
          costUsd = 0.0,
          cached = false
        )
        tracker.record(failed).as(Left(ProviderFailure(err)))
      case Right(resp) =>
        afterLlmCall(req, key, resp)

  private def afterLlmCall(
      req: SynthRequest,
      key: CacheKey,
      resp: LlmResponse
  ): IO[Either[SynthError, SynthResult]] =
    val parsed =
      for
        block <- ResponseParser.extractCodeBlock(resp.text)
        body  <-
          ResponseParser.extractMethodBody(block, classificationOperationName(req.classification))
      yield (block, body)
    parsed match
      case Left(perr) =>
        recordFailure(req, resp).as(Left(ResponseParseFailure(perr)))
      case Right((block, body)) =>
        DiffChecker.check(req.header, block) match
          case Left(diff) =>
            recordFailure(req, resp).as(Left(DiffCheckFailure(diff)))
          case Right(()) =>
            persistAndRecord(req, key, resp, block, body)

  private def recordFailure(req: SynthRequest, resp: LlmResponse): IO[Unit] =
    val cost = Pricing.costOrZero(resp.usage, resp.model)
    tracker.record(
      CallRecord(
        classificationOperationName(req.classification),
        resp.model,
        resp.usage,
        cost,
        cached = false
      )
    )

  private def persistAndRecord(
      req: SynthRequest,
      key: CacheKey,
      resp: LlmResponse,
      block: String,
      body: String
  ): IO[Either[SynthError, SynthResult]] =
    val cost  = Pricing.costOrZero(resp.usage, resp.model)
    val entry = CacheEntry(block, body, resp.usage, resp.model, SynthPromptVersion)
    val rec   =
      CallRecord(
        classificationOperationName(req.classification),
        resp.model,
        resp.usage,
        cost,
        cached = false
      )
    val storeBestEffort: IO[Unit] = cache match
      case Some(c) =>
        c.store(key, entry).attempt.flatMap:
          case Right(_) => IO.unit
          case Left(e)  =>
            IO.consoleForIO.errorln(
              s"warning: cache write failed for ${classificationOperationName(req.classification)}: ${e.getMessage}"
            )
      case None => IO.unit
    for
      _ <- tracker.record(rec)
      _ <- storeBestEffort
    yield Right(SynthResult(body, block, resp.usage, cost, cached = false, resp.model))
