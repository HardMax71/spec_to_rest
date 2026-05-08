package specrest.synth

import cats.effect.IO
import specrest.convention.OperationClassification
import specrest.convention.dafny.DafnyMethodHeader

final case class SynthRequest(
    classification: OperationClassification,
    header: DafnyMethodHeader,
    skeleton: String,
    model: String,
    temperature: Double = 0.0,
    maxTokens: Int = 2048
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
    val rec =
      CallRecord(req.classification.operationName, entry.model, entry.usage, cost, cached = true)
    tracker.record(rec).as(
      Right(SynthResult(entry.body, entry.candidate, entry.usage, cost, cached = true, entry.model))
    )

  private def runUncached(req: SynthRequest, key: CacheKey): IO[Either[SynthError, SynthResult]] =
    val prompt = PromptBuilder.initial(req.classification, req.header, req.skeleton)
    val llmReq = LlmRequest(prompt.system, prompt.user, req.model, req.maxTokens, req.temperature)
    provider.complete(llmReq).flatMap:
      case Left(err) =>
        IO.pure(Left(ProviderFailure(err)))
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
        body  <- ResponseParser.extractMethodBody(block, req.classification.operationName)
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
      CallRecord(req.classification.operationName, resp.model, resp.usage, cost, cached = false)
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
    val store = cache match
      case Some(c) => c.store(key, entry).attempt.map(_.left.toOption.map(e =>
          CacheFailure(s"cache write failed: ${e.getMessage}")
        ))
      case None => IO.pure(None)
    val rec =
      CallRecord(req.classification.operationName, resp.model, resp.usage, cost, cached = false)
    for
      _      <- tracker.record(rec)
      cacheE <- store
    yield cacheE match
      case Some(failure) => Left(failure)
      case None =>
        Right(SynthResult(body, block, resp.usage, cost, cached = false, resp.model))
