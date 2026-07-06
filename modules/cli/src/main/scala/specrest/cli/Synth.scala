package specrest.cli

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.traverse.*
import specrest.cli.ExitStatus.given
import specrest.convention.Classify
import specrest.dafny.DafnyMethodHeader
import specrest.dafny.Generator as DafnyGenerator
import specrest.ir.VerifyError
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.synth.Cache
import specrest.synth.CacheEntry
import specrest.synth.CegisBudget
import specrest.synth.CegisLoop
import specrest.synth.CegisOutcome
import specrest.synth.DafnyCli
import specrest.synth.FallbackBudget
import specrest.synth.FallbackOrchestrator
import specrest.synth.FallbackOutcome
import specrest.synth.FileAssembly
import specrest.synth.LlmProvider
import specrest.synth.ModelFamily
import specrest.synth.OpOutcome
import specrest.synth.PromptStrategy
import specrest.synth.Reporter
import specrest.synth.SynthPromptVersion
import specrest.synth.SynthRequest
import specrest.synth.SynthResult
import specrest.synth.SynthesisReport
import specrest.synth.Synthesizer
import specrest.synth.TokenUsage
import specrest.synth.Tracker
import specrest.synth.providers.AnthropicProvider
import specrest.synth.providers.OpenAIProvider
import specrest.synth.providers.RoutingProvider

import java.io.PrintStream
import java.nio.file.Paths

final case class SynthOptions(
    operation: String,
    model: String,
    temperature: Double,
    maxTokens: Int,
    noCache: Boolean,
    cacheDir: Option[String]
)

final case class SynthVerifyOptions(
    operation: String,
    model: String,
    temperature: Double,
    maxTokens: Int,
    noCache: Boolean,
    cacheDir: Option[String],
    dafnyBin: Option[String],
    dafnyTimeoutSec: Int,
    maxIter: Int,
    maxCostUsd: Double,
    maxOutputTokens: Long,
    fallback: Boolean = false,
    escalateTo: List[String] = Nil,
    withHints: Option[Boolean] = None
):
  def hintsEnabled: Boolean = withHints.getOrElse(fallback)

final case class SynthAcceptOptions(
    operation: String,
    bodyFile: String,
    model: String,
    temperature: Double,
    cacheDir: Option[String],
    dafnyBin: Option[String],
    dafnyTimeoutSec: Int
)

final case class SynthVerifyAllOptions(
    model: String,
    temperature: Double,
    maxTokens: Int,
    noCache: Boolean,
    cacheDir: Option[String],
    dafnyBin: Option[String],
    dafnyTimeoutSec: Int,
    maxIter: Int,
    maxCostUsd: Double,
    maxOutputTokens: Long,
    escalateTo: List[String] = Nil,
    withHints: Option[Boolean] = None
):
  def hintsEnabled: Boolean = withHints.getOrElse(true)

object Synth:

  def run(
      specFile: String,
      opts: SynthOptions,
      log: Logger,
      out: PrintStream = System.out,
      err: PrintStream = System.err
  ): IO[ExitStatus] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitStatus.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(buildErr) =>
                IO.delay(log.error(Check.renderBuildError(specFile, buildErr)))
                  .as(ExitStatus.Violations)
              case Right(ir) =>
                runWithIR(specFile, ir, opts, log, out, err)

  private def isDirectEmit(s: synthesis_strategy): Boolean = s match
    case _: DirectEmit => true
    case _             => false

  private def isLlmSynthesis(s: synthesis_strategy): Boolean = s match
    case _: LlmSynthesis => true
    case _               => false

  private def runWithIR(
      specFile: String,
      ir: ServiceIRFull,
      opts: SynthOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitStatus] =
    val classifications = Classify.classifyOperations(ir)
    classifications.find(c => classificationOperationName(c) == opts.operation) match
      case None =>
        IO.delay(log.error(s"$specFile: operation '${opts.operation}' not found"))
          .as(ExitStatus.Violations)
      case Some(c) if isDirectEmit(classificationStrategy(c)) =>
        IO.delay(
          log.error(
            s"$specFile: operation '${opts.operation}' is classified DIRECT_EMIT; " +
              "no LLM synthesis required"
          )
        ).as(ExitStatus.Violations)
      case Some(c) =>
        DafnyGenerator.generate(ir) match
          case Left(dErr) =>
            IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
              .as(ExitStatus.Translator)
          case Right(dafny) =>
            val opName = classificationOperationName(c)
            dafny.methods.find(_.name == opName) match
              case None =>
                IO.delay(log.error(s"$specFile: no Dafny header for '$opName'"))
                  .as(ExitStatus.Translator)
              case Some(header) =>
                executeSynth(specFile, c, header, dafny.text, opts, log, out, err)

  private def executeSynth(
      specFile: String,
      c: operation_classification,
      header: DafnyMethodHeader,
      skeleton: String,
      opts: SynthOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitStatus] =
    val req    = SynthRequest(c, header, skeleton, opts.model, opts.temperature, opts.maxTokens)
    val opName = classificationOperationName(c)
    routerResource(List(opts.model)).use: provider =>
      cacheResource(opts).flatMap: cache =>
        Tracker.empty.flatMap: tracker =>
          val synth = new Synthesizer(provider, cache, tracker)
          synth.synthesize(req).flatMap:
            case Left(synthErr) =>
              IO.delay(log.error(s"$specFile: synth $opName: ${synthErr.message}"))
                .as(ExitStatus.forSynthError(synthErr))
            case Right(result) => emitResult(result, opName, out, err).as(ExitStatus.Ok)

  private def cacheResource(opts: SynthOptions): IO[Option[Cache]] =
    if opts.noCache then IO.pure(None)
    else
      val root = opts.cacheDir match
        case Some(p) => Paths.get(p)
        case None    => Cache.defaultRoot(Paths.get(""))
      Cache.make(root).map(Some(_))

  private def modelLadder(model: String, escalateTo: List[String]): List[String] =
    if escalateTo.isEmpty then List(model) else model :: escalateTo

  private def routerResource(models: List[String]): Resource[IO, LlmProvider] =
    models
      .flatMap(model => ModelFamily.of(model).toList)
      .distinct
      .traverse(family => providerForFamily(family).map(family -> _))
      .map(pairs => new RoutingProvider(pairs.toMap))

  private def providerForFamily(family: ModelFamily): Resource[IO, LlmProvider] =
    family match
      case ModelFamily.OpenAI    => OpenAIProvider.fromEnv.map(p => p: LlmProvider)
      case ModelFamily.Anthropic => AnthropicProvider.fromEnv.map(p => p: LlmProvider)

  private def emitResult(
      r: SynthResult,
      opName: String,
      out: PrintStream,
      err: PrintStream
  ): IO[Unit] =
    IO.blocking {
      out.println(r.body)
      val cacheTag = if r.cached then "(cached)" else ""
      err.println(
        f"[synth] op=$opName model=${r.model} in=${r.usage.inputTokens}tok " +
          f"out=${r.usage.outputTokens}tok cost=$$${r.costUsd}%.4f $cacheTag".trim
      )
    }

  def runAccept(
      specFile: String,
      opts: SynthAcceptOptions,
      log: Logger,
      out: PrintStream = System.out
  ): IO[ExitStatus] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitStatus.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(buildErr) =>
                IO.delay(log.error(Check.renderBuildError(specFile, buildErr)))
                  .as(ExitStatus.Violations)
              case Right(ir) => runAcceptWithIR(specFile, ir, opts, log, out)

  private def runAcceptWithIR(
      specFile: String,
      ir: ServiceIRFull,
      opts: SynthAcceptOptions,
      log: Logger,
      out: PrintStream
  ): IO[ExitStatus] =
    val classifications = Classify.classifyOperations(ir)
    classifications.find(c => classificationOperationName(c) == opts.operation) match
      case None =>
        IO.delay(log.error(s"$specFile: operation '${opts.operation}' not found"))
          .as(ExitStatus.Violations)
      case Some(c) if isDirectEmit(classificationStrategy(c)) =>
        IO.delay(
          log.error(
            s"$specFile: operation '${opts.operation}' is classified DIRECT_EMIT; " +
              "no synthesized body is used"
          )
        ).as(ExitStatus.Violations)
      case Some(c) =>
        DafnyGenerator.generate(ir) match
          case Left(dErr) =>
            IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
              .as(ExitStatus.Translator)
          case Right(dafny) =>
            val opName = classificationOperationName(c)
            dafny.methods.find(_.name == opName) match
              case None =>
                IO.delay(log.error(s"$specFile: no Dafny header for '$opName'"))
                  .as(ExitStatus.Translator)
              case Some(header) =>
                acceptBody(specFile, opName, header, dafny.text, opts, log, out)

  // The same gate the CEGIS loop applies to an LLM candidate, applied to a
  // provided body: splice into the skeleton, verify with dafny once, and only
  // a verified result reaches the cache. Provenance rides in the model label.
  private def acceptBody(
      specFile: String,
      opName: String,
      header: specrest.dafny.DafnyMethodHeader,
      skeleton: String,
      opts: SynthAcceptOptions,
      log: Logger,
      out: PrintStream
  ): IO[ExitStatus] =
    DafnyCli.resolveBinary(opts.dafnyBin).flatMap:
      case Left(msg) =>
        IO.delay(log.error(s"$specFile: $msg")).as(ExitStatus.Backend)
      case Right(binary) =>
        IO.blocking(java.nio.file.Files.readString(java.nio.file.Paths.get(opts.bodyFile)))
          .flatMap: bodyText =>
            FileAssembly.splice(skeleton, opName, bodyText.stripLineEnd) match
              case Left(failure) =>
                IO.delay(log.error(s"$specFile: splice failed: ${failure.message}"))
                  .as(ExitStatus.Violations)
              case Right(fullDfy) =>
                DafnyCli.make(binary).use: verifier =>
                  verifier.verify(fullDfy, opts.dafnyTimeoutSec).flatMap:
                    case Left(backendErr) =>
                      IO.delay(log.error(s"$specFile: dafny failed: $backendErr"))
                        .as(ExitStatus.Backend)
                    case Right(run) if run.verifiedFor(opName) =>
                      val baseRoot = opts.cacheDir match
                        case Some(pth) => Paths.get(pth)
                        case None      => Cache.defaultRoot(Paths.get(""))
                      Cache.make(Cache.verifiedRoot(baseRoot)).flatMap: cache =>
                        val key = Cache.keyFor(header, opts.model, opts.temperature)
                        val entry = CacheEntry(
                          bodyText,
                          bodyText.stripLineEnd,
                          TokenUsage(0, 0),
                          opts.model,
                          SynthPromptVersion
                        )
                        cache.store(key, entry) *>
                          IO.delay(
                            out.println(s"[synth-accept] op=$opName VERIFIED and cached")
                          ).as(ExitStatus.Ok)
                    case Right(run) =>
                      IO.delay {
                        run
                          .errorsFor(opName)
                          .foreach(e =>
                            log.error(
                              s"$opName: ${e.category}${e.line.fold("")(l => s" line $l")}: ${e.message}"
                            )
                          )
                      }.as(ExitStatus.Violations)

  def runVerify(
      specFile: String,
      opts: SynthVerifyOptions,
      log: Logger,
      out: PrintStream = System.out,
      err: PrintStream = System.err
  ): IO[ExitStatus] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitStatus.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(buildErr) =>
                IO.delay(log.error(Check.renderBuildError(specFile, buildErr)))
                  .as(ExitStatus.Violations)
              case Right(ir) => runVerifyWithIR(specFile, ir, opts, log, out, err)

  private def runVerifyWithIR(
      specFile: String,
      ir: ServiceIRFull,
      opts: SynthVerifyOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitStatus] =
    val classifications = Classify.classifyOperations(ir)
    classifications.find(c => classificationOperationName(c) == opts.operation) match
      case None =>
        IO.delay(log.error(s"$specFile: operation '${opts.operation}' not found"))
          .as(ExitStatus.Violations)
      case Some(c) if isDirectEmit(classificationStrategy(c)) =>
        IO.delay(
          log.error(
            s"$specFile: operation '${opts.operation}' is classified DIRECT_EMIT; " +
              "no LLM synthesis required"
          )
        ).as(ExitStatus.Violations)
      case Some(c) =>
        DafnyGenerator.generate(ir) match
          case Left(dErr) =>
            IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
              .as(ExitStatus.Translator)
          case Right(dafny) =>
            val opName = classificationOperationName(c)
            dafny.methods.find(_.name == opName) match
              case None =>
                IO.delay(log.error(s"$specFile: no Dafny header for '$opName'"))
                  .as(ExitStatus.Translator)
              case Some(header) =>
                executeCegis(specFile, c, header, dafny.text, opts, log, out, err)

  private def executeCegis(
      specFile: String,
      c: operation_classification,
      header: DafnyMethodHeader,
      skeleton: String,
      opts: SynthVerifyOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitStatus] =
    DafnyCli.resolveBinary(opts.dafnyBin).flatMap:
      case Left(msg) =>
        IO.delay(log.error(s"$specFile: $msg")).as(ExitStatus.Backend)
      case Right(binary) =>
        val req = SynthRequest(c, header, skeleton, opts.model, opts.temperature, opts.maxTokens)
        val budget = CegisBudget.Default.copy(
          maxIterations = opts.maxIter,
          maxCostUsd = opts.maxCostUsd,
          maxOutputTokens = opts.maxOutputTokens
        )
        val ladder    = modelLadder(opts.model, opts.escalateTo)
        val runModels = if opts.fallback then ladder else List(opts.model)
        val resources =
          for
            provider  <- routerResource(runModels)
            cache     <- Resource.eval(verifiedCacheResource(opts))
            skelCache <- Resource.eval(skeletonCacheResource(opts))
            verifier  <- DafnyCli.make(binary)
          yield (provider, cache, skelCache, verifier)
        resources.use: (provider, cache, skelCache, verifier) =>
          Tracker.empty.flatMap: tracker =>
            if opts.fallback then
              // The shared token pools must scale with the plan, or their
              // defaults strangle the ladder after one attempt and escalation
              // never fires; cost stays the binding global guard.
              val planSize = ladder.length * PromptStrategy.FallbackOrder.length
              val fb = FallbackBudget.Default.copy(
                modelLadder = ladder,
                sharedCostCapUsd = opts.maxCostUsd,
                sharedInputTokenCap = CegisBudget.Default.maxInputTokens * planSize,
                sharedOutputTokenCap = opts.maxOutputTokens * planSize
              )
              val orch = new FallbackOrchestrator(
                provider,
                verifier,
                cache,
                skelCache,
                tracker,
                budget,
                fb,
                opts.dafnyTimeoutSec,
                opts.hintsEnabled
              )
              orch.run(req).flatMap: outcome =>
                emitFallbackOutcome(outcome, classificationOperationName(c), out, err) *>
                  tracker.summary.flatMap: s =>
                    emitSummary(s, err).as(ExitStatus.forFallbackOutcome(outcome))
            else
              val loop =
                new CegisLoop(
                  provider,
                  verifier,
                  cache,
                  tracker,
                  budget,
                  opts.dafnyTimeoutSec,
                  opts.hintsEnabled
                )
              loop.run(req).flatMap: outcome =>
                emitOutcome(outcome, classificationOperationName(c), out, err) *> tracker.summary
                  .flatMap: s =>
                    emitSummary(s, err).as(ExitStatus.forCegisOutcome(outcome))

  private def verifiedCacheResource(opts: SynthVerifyOptions): IO[Option[Cache]] =
    if opts.noCache then IO.pure(None)
    else
      val baseRoot = opts.cacheDir match
        case Some(p) => Paths.get(p)
        case None    => Cache.defaultRoot(Paths.get(""))
      Cache.make(Cache.verifiedRoot(baseRoot)).map(Some(_))

  private def skeletonCacheResource(opts: SynthVerifyOptions): IO[Option[Cache]] =
    if opts.noCache then IO.pure(None)
    else
      val baseRoot = opts.cacheDir match
        case Some(p) => Paths.get(p)
        case None    => Cache.defaultRoot(Paths.get(""))
      Cache.make(Cache.skeletonsRoot(baseRoot)).map(Some(_))

  private def emitOutcome(
      outcome: CegisOutcome,
      opName: String,
      out: PrintStream,
      err: PrintStream
  ): IO[Unit] =
    IO.blocking {
      outcome match
        case v: CegisOutcome.Verified =>
          out.println(v.body)
          err.println(
            s"[synth-verify] op=$opName VERIFIED iter=${v.iterations} " +
              s"records=${v.history.records.length}"
          )
        case CegisOutcome.Aborted(reason, lastBody, history) =>
          err.println(
            s"[synth-verify] op=$opName ABORTED iter=${history.records.length}: ${reason.message}"
          )
          lastBody.foreach: body =>
            err.println("[synth-verify] last candidate body:")
            err.println(body)
    }

  private def emitSummary(
      s: specrest.synth.CostSummary,
      err: PrintStream
  ): IO[Unit] =
    IO.blocking {
      err.println(
        f"[synth-verify] tokens in=${s.inputTokens}tok out=${s.outputTokens}tok " +
          f"cost=$$${s.totalUsd}%.4f calls=${s.operations} cachedHits=${s.cachedHits}"
      )
    }

  private def emitFallbackOutcome(
      outcome: FallbackOutcome,
      opName: String,
      out: PrintStream,
      err: PrintStream
  ): IO[Unit] =
    IO.blocking {
      outcome match
        case v: FallbackOutcome.Verified =>
          out.println(v.body)
          val verdict = if v.attempts.length == 1 then "VERIFIED" else "VERIFIED-ESCALATED"
          err.println(
            s"[synth-verify] op=$opName $verdict attempts=${v.attempts.length} " +
              s"strategy=${PromptStrategy.displayName(v.finalStrategy)} model=${v.finalModel} " +
              s"iter=${v.cegisIterations}"
          )
        case s: FallbackOutcome.SkeletonOnly =>
          out.println(s.body)
          err.println(
            s"[synth-verify] op=$opName SKELETON attempts=${s.attempts.length} reason=${s.reason}"
          )
    }

  def runVerifyAll(
      specFile: String,
      opts: SynthVerifyAllOptions,
      log: Logger,
      err: PrintStream = System.err
  ): IO[ExitStatus] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitStatus.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(buildErr) =>
                IO.delay(log.error(Check.renderBuildError(specFile, buildErr)))
                  .as(ExitStatus.Violations)
              case Right(ir) => runVerifyAllWithIR(specFile, ir, opts, log, err)

  private def runVerifyAllWithIR(
      specFile: String,
      ir: ServiceIRFull,
      opts: SynthVerifyAllOptions,
      log: Logger,
      err: PrintStream
  ): IO[ExitStatus] =
    val classifications = Classify
      .classifyOperations(ir)
      .filter(c => isLlmSynthesis(classificationStrategy(c)))
    if classifications.isEmpty then
      IO.delay(
        log.warn(s"$specFile: no LLM_SYNTHESIS operations to verify; nothing to do")
      ).as(ExitStatus.Ok)
    else
      DafnyCli.resolveBinary(opts.dafnyBin).flatMap:
        case Left(msg) => IO.delay(log.error(s"$specFile: $msg")).as(ExitStatus.Backend)
        case Right(binary) =>
          DafnyGenerator.generate(ir) match
            case Left(dErr) =>
              IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
                .as(ExitStatus.Translator)
            case Right(dafny) =>
              executeVerifyAll(specFile, classifications, dafny, opts, binary, log, err)

  private def executeVerifyAll(
      specFile: String,
      classifications: List[operation_classification],
      dafny: specrest.dafny.DafnyOutput,
      opts: SynthVerifyAllOptions,
      binary: String,
      log: Logger,
      err: PrintStream
  ): IO[ExitStatus] =
    val budget = CegisBudget.Default.copy(
      maxIterations = opts.maxIter,
      maxCostUsd = opts.maxCostUsd,
      maxOutputTokens = opts.maxOutputTokens
    )
    val verifyOpts = SynthVerifyOptions(
      operation = "",
      model = opts.model,
      temperature = opts.temperature,
      maxTokens = opts.maxTokens,
      noCache = opts.noCache,
      cacheDir = opts.cacheDir,
      dafnyBin = opts.dafnyBin,
      dafnyTimeoutSec = opts.dafnyTimeoutSec,
      maxIter = opts.maxIter,
      maxCostUsd = opts.maxCostUsd,
      maxOutputTokens = opts.maxOutputTokens
    )
    val ladder = modelLadder(opts.model, opts.escalateTo)
    val resources =
      for
        provider  <- routerResource(ladder)
        cache     <- Resource.eval(verifiedCacheResource(verifyOpts))
        skelCache <- Resource.eval(skeletonCacheResource(verifyOpts))
        verifier  <- DafnyCli.make(binary)
      yield (provider, cache, skelCache, verifier)
    resources.use: (provider, cache, skelCache, verifier) =>
      Tracker.empty.flatMap: tracker =>
        val fbPlanSize = ladder.length * PromptStrategy.FallbackOrder.length
        val fb = FallbackBudget.Default.copy(
          modelLadder = ladder,
          sharedCostCapUsd = opts.maxCostUsd,
          sharedInputTokenCap = CegisBudget.Default.maxInputTokens * fbPlanSize,
          sharedOutputTokenCap = opts.maxOutputTokens * fbPlanSize
        )
        val orch = new FallbackOrchestrator(
          provider,
          verifier,
          cache,
          skelCache,
          tracker,
          budget,
          fb,
          opts.dafnyTimeoutSec,
          opts.hintsEnabled
        )
        runOrchestrationLoop(specFile, classifications, dafny, orch, opts, err, log)

  private def runOrchestrationLoop(
      specFile: String,
      classifications: List[operation_classification],
      dafny: specrest.dafny.DafnyOutput,
      orch: FallbackOrchestrator,
      opts: SynthVerifyAllOptions,
      err: PrintStream,
      log: Logger
  ): IO[ExitStatus] =
    classifications
      .foldLeft[IO[Either[ExitStatus, List[OpOutcome]]]](IO.pure(Right(Nil))): (accIO, c) =>
        accIO.flatMap:
          case Left(code) => IO.pure(Left(code))
          case Right(acc) =>
            val opName = classificationOperationName(c)
            dafny.methods.find(_.name == opName) match
              case None =>
                IO.delay(log.error(s"$specFile: no Dafny header for '$opName'"))
                  .as(Left(ExitStatus.Translator))
              case Some(header) =>
                val req = SynthRequest(
                  c,
                  header,
                  dafny.text,
                  model = opts.model,
                  temperature = opts.temperature,
                  maxTokens = opts.maxTokens
                )
                orch.run(req).map: outcome =>
                  Right(acc :+ OpOutcome.fromFallback(opName, outcome))
      .flatMap:
        case Left(code) => IO.pure(code)
        case Right(ops) =>
          val report   = SynthesisReport(ops)
          val rendered = Reporter.render(report, useColor = false)
          IO.blocking {
            err.println(rendered)
          }.as(exitCodeForReport(report))

  private def exitCodeForReport(report: SynthesisReport): ExitStatus =
    if report.totals.skeleton == 0 then ExitStatus.Ok else ExitStatus.Violations
