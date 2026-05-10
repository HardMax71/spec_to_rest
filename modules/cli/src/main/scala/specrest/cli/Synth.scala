package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.kernel.Resource
import specrest.cli.ExitCodes.given
import specrest.convention.Classify
import specrest.convention.OperationClassification
import specrest.convention.SynthesisStrategy
import specrest.convention.dafny.DafnyMethodHeader
import specrest.convention.dafny.Generator as DafnyGenerator
import specrest.ir.VerifyError
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.synth.Cache
import specrest.synth.CegisBudget
import specrest.synth.CegisLoop
import specrest.synth.CegisOutcome
import specrest.synth.DafnyCli
import specrest.synth.FallbackBudget
import specrest.synth.FallbackOrchestrator
import specrest.synth.FallbackOutcome
import specrest.synth.LlmProvider
import specrest.synth.OpOutcome
import specrest.synth.PromptStrategy
import specrest.synth.Reporter
import specrest.synth.SynthRequest
import specrest.synth.SynthResult
import specrest.synth.SynthesisReport
import specrest.synth.Synthesizer
import specrest.synth.Tracker
import specrest.synth.providers.AnthropicProvider
import specrest.synth.providers.OpenAIProvider

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
    fallback: Boolean = false,
    escalateTo: List[String] = Nil
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
    escalateTo: List[String] = Nil
)

object Synth:

  def run(
      specFile: String,
      opts: SynthOptions,
      log: Logger,
      out: PrintStream = System.out,
      err: PrintStream = System.err
  ): IO[ExitCode] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitCodes.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(buildErr) =>
                IO.delay(log.error(Check.renderBuildError(specFile, buildErr)))
                  .as(ExitCodes.Violations)
              case Right(ir) =>
                runWithIR(specFile, ir, opts, log, out, err)

  private def runWithIR(
      specFile: String,
      ir: ServiceIRFull,
      opts: SynthOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitCode] =
    val classifications = Classify.classifyOperations(ir)
    classifications.find(_.operationName == opts.operation) match
      case None =>
        IO.delay(log.error(s"$specFile: operation '${opts.operation}' not found"))
          .as(ExitCodes.Violations)
      case Some(c) if c.strategy == SynthesisStrategy.DirectEmit =>
        IO.delay(
          log.error(
            s"$specFile: operation '${opts.operation}' is classified DIRECT_EMIT; " +
              "no LLM synthesis required"
          )
        ).as(ExitCodes.Violations)
      case Some(c) =>
        DafnyGenerator.generate(ir) match
          case Left(dErr) =>
            IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
              .as(ExitCodes.Translator)
          case Right(dafny) =>
            dafny.methods.find(_.name == c.operationName) match
              case None =>
                IO.delay(log.error(s"$specFile: no Dafny header for '${c.operationName}'"))
                  .as(ExitCodes.Translator)
              case Some(header) =>
                executeSynth(specFile, c, header, dafny.text, opts, log, out, err)

  private def executeSynth(
      specFile: String,
      c: OperationClassification,
      header: DafnyMethodHeader,
      skeleton: String,
      opts: SynthOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitCode] =
    val req = SynthRequest(c, header, skeleton, opts.model, opts.temperature, opts.maxTokens)
    providerResource(opts.model).use: provider =>
      cacheResource(opts).flatMap: cache =>
        Tracker.empty.flatMap: tracker =>
          val synth = new Synthesizer(provider, cache, tracker)
          synth.synthesize(req).flatMap:
            case Left(synthErr) =>
              IO.delay(log.error(s"$specFile: synth ${c.operationName}: ${synthErr.message}"))
                .as(ExitCodes.forSynthError(synthErr))
            case Right(result) => emitResult(result, c.operationName, out, err).as(ExitCodes.Ok)

  private def cacheResource(opts: SynthOptions): IO[Option[Cache]] =
    if opts.noCache then IO.pure(None)
    else
      val root = opts.cacheDir match
        case Some(p) => Paths.get(p)
        case None    => Cache.defaultRoot(Paths.get(""))
      Cache.make(root).map(Some(_))

  private def providerResource(model: String): Resource[IO, LlmProvider] =
    if model.toLowerCase.startsWith("gpt") then
      OpenAIProvider.fromEnv.map(p => p: LlmProvider)
    else AnthropicProvider.fromEnv.map(p => p: LlmProvider)

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

  def runVerify(
      specFile: String,
      opts: SynthVerifyOptions,
      log: Logger,
      out: PrintStream = System.out,
      err: PrintStream = System.err
  ): IO[ExitCode] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitCodes.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(buildErr) =>
                IO.delay(log.error(Check.renderBuildError(specFile, buildErr)))
                  .as(ExitCodes.Violations)
              case Right(ir) => runVerifyWithIR(specFile, ir, opts, log, out, err)

  private def runVerifyWithIR(
      specFile: String,
      ir: ServiceIRFull,
      opts: SynthVerifyOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitCode] =
    val classifications = Classify.classifyOperations(ir)
    classifications.find(_.operationName == opts.operation) match
      case None =>
        IO.delay(log.error(s"$specFile: operation '${opts.operation}' not found"))
          .as(ExitCodes.Violations)
      case Some(c) if c.strategy == SynthesisStrategy.DirectEmit =>
        IO.delay(
          log.error(
            s"$specFile: operation '${opts.operation}' is classified DIRECT_EMIT; " +
              "no LLM synthesis required"
          )
        ).as(ExitCodes.Violations)
      case Some(c) =>
        DafnyGenerator.generate(ir) match
          case Left(dErr) =>
            IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
              .as(ExitCodes.Translator)
          case Right(dafny) =>
            dafny.methods.find(_.name == c.operationName) match
              case None =>
                IO.delay(log.error(s"$specFile: no Dafny header for '${c.operationName}'"))
                  .as(ExitCodes.Translator)
              case Some(header) =>
                executeCegis(specFile, c, header, dafny.text, opts, log, out, err)

  private def executeCegis(
      specFile: String,
      c: OperationClassification,
      header: DafnyMethodHeader,
      skeleton: String,
      opts: SynthVerifyOptions,
      log: Logger,
      out: PrintStream,
      err: PrintStream
  ): IO[ExitCode] =
    DafnyCli.resolveBinary(opts.dafnyBin).flatMap:
      case Left(msg) =>
        IO.delay(log.error(s"$specFile: $msg")).as(ExitCodes.Backend)
      case Right(binary) =>
        val req = SynthRequest(c, header, skeleton, opts.model, opts.temperature, opts.maxTokens)
        val budget = CegisBudget.Default.copy(
          maxIterations = opts.maxIter,
          maxCostUsd = opts.maxCostUsd
        )
        val resources =
          for
            provider  <- providerResource(opts.model)
            cache     <- Resource.eval(verifiedCacheResource(opts))
            skelCache <- Resource.eval(skeletonCacheResource(opts))
            verifier  <- DafnyCli.make(binary)
          yield (provider, cache, skelCache, verifier)
        resources.use: (provider, cache, skelCache, verifier) =>
          Tracker.empty.flatMap: tracker =>
            if opts.fallback then
              val fb = FallbackBudget.Default.copy(
                modelLadder =
                  if opts.escalateTo.isEmpty then List(opts.model)
                  else opts.model :: opts.escalateTo,
                sharedCostCapUsd = opts.maxCostUsd
              )
              val orch = new FallbackOrchestrator(
                provider,
                verifier,
                cache,
                skelCache,
                tracker,
                budget,
                fb,
                opts.dafnyTimeoutSec
              )
              orch.run(req).flatMap: outcome =>
                emitFallbackOutcome(outcome, c.operationName, out, err) *>
                  tracker.summary.flatMap: s =>
                    emitSummary(s, err).as(ExitCodes.forFallbackOutcome(outcome))
            else
              val loop =
                new CegisLoop(provider, verifier, cache, tracker, budget, opts.dafnyTimeoutSec)
              loop.run(req).flatMap: outcome =>
                emitOutcome(outcome, c.operationName, out, err) *> tracker.summary.flatMap: s =>
                  emitSummary(s, err).as(ExitCodes.forCegisOutcome(outcome))

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
  ): IO[ExitCode] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitCodes.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(buildErr) =>
                IO.delay(log.error(Check.renderBuildError(specFile, buildErr)))
                  .as(ExitCodes.Violations)
              case Right(ir) => runVerifyAllWithIR(specFile, ir, opts, log, err)

  private def runVerifyAllWithIR(
      specFile: String,
      ir: ServiceIRFull,
      opts: SynthVerifyAllOptions,
      log: Logger,
      err: PrintStream
  ): IO[ExitCode] =
    val classifications = Classify
      .classifyOperations(ir)
      .filter(_.strategy == SynthesisStrategy.LlmSynthesis)
    if classifications.isEmpty then
      IO.delay(
        log.warn(s"$specFile: no LLM_SYNTHESIS operations to verify; nothing to do")
      ).as(ExitCodes.Ok)
    else
      DafnyCli.resolveBinary(opts.dafnyBin).flatMap:
        case Left(msg) => IO.delay(log.error(s"$specFile: $msg")).as(ExitCodes.Backend)
        case Right(binary) =>
          DafnyGenerator.generate(ir) match
            case Left(dErr) =>
              IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
                .as(ExitCodes.Translator)
            case Right(dafny) =>
              executeVerifyAll(specFile, classifications, dafny, opts, binary, log, err)

  private def executeVerifyAll(
      specFile: String,
      classifications: List[OperationClassification],
      dafny: specrest.convention.dafny.DafnyOutput,
      opts: SynthVerifyAllOptions,
      binary: String,
      log: Logger,
      err: PrintStream
  ): IO[ExitCode] =
    val budget = CegisBudget.Default.copy(
      maxIterations = opts.maxIter,
      maxCostUsd = opts.maxCostUsd
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
      maxCostUsd = opts.maxCostUsd
    )
    val resources =
      for
        provider  <- providerResource(opts.model)
        cache     <- Resource.eval(verifiedCacheResource(verifyOpts))
        skelCache <- Resource.eval(skeletonCacheResource(verifyOpts))
        verifier  <- DafnyCli.make(binary)
      yield (provider, cache, skelCache, verifier)
    resources.use: (provider, cache, skelCache, verifier) =>
      Tracker.empty.flatMap: tracker =>
        val fb = FallbackBudget.Default.copy(
          modelLadder =
            if opts.escalateTo.isEmpty then List(opts.model)
            else opts.model :: opts.escalateTo,
          sharedCostCapUsd = opts.maxCostUsd
        )
        val orch = new FallbackOrchestrator(
          provider,
          verifier,
          cache,
          skelCache,
          tracker,
          budget,
          fb,
          opts.dafnyTimeoutSec
        )
        runOrchestrationLoop(specFile, classifications, dafny, orch, opts, err, log)

  private def runOrchestrationLoop(
      specFile: String,
      classifications: List[OperationClassification],
      dafny: specrest.convention.dafny.DafnyOutput,
      orch: FallbackOrchestrator,
      opts: SynthVerifyAllOptions,
      err: PrintStream,
      log: Logger
  ): IO[ExitCode] =
    classifications
      .foldLeft[IO[Either[ExitCode, List[OpOutcome]]]](IO.pure(Right(Nil))): (accIO, c) =>
        accIO.flatMap:
          case Left(code) => IO.pure(Left(code))
          case Right(acc) =>
            dafny.methods.find(_.name == c.operationName) match
              case None =>
                IO.delay(log.error(s"$specFile: no Dafny header for '${c.operationName}'"))
                  .as(Left(ExitCodes.Translator))
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
                  Right(acc :+ OpOutcome.fromFallback(c.operationName, outcome))
      .flatMap:
        case Left(code) => IO.pure(code)
        case Right(ops) =>
          val report   = SynthesisReport(ops)
          val rendered = Reporter.render(report, useColor = false)
          IO.blocking {
            err.println(rendered)
          }.as(exitCodeForReport(report))

  private def exitCodeForReport(report: SynthesisReport): ExitCode =
    if report.totals.skeleton == 0 then ExitCodes.Ok else ExitCodes.Violations
