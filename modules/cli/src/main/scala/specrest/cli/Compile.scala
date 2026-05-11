package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import specrest.cli.ExitCodes.given
import specrest.codegen.DafnyKernel
import specrest.codegen.Emit
import specrest.codegen.EmitOptions
import specrest.codegen.OperationBinding
import specrest.convention.Classify
import specrest.convention.SynthesisStrategy
import specrest.convention.dafny.Generator as DafnyGenerator
import specrest.ir.VerifyError
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate
import specrest.synth.Cache
import specrest.synth.DafnyCli
import specrest.synth.DafnyTranslateCli
import specrest.synth.FileAssembly
import specrest.synth.TargetLanguage
import specrest.synth.TranslatedDafny
import specrest.testgen.FilePaths
import specrest.testgen.Strategies
import specrest.testgen.SupportedTargets
import specrest.testgen.TestEmit
import specrest.verify.VerificationConfig

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import scala.util.control.NonFatal

final case class CompileOptions(
    target: String,
    outDir: String,
    ignoreVerify: Boolean = false,
    withTests: Boolean = false,
    strictStrategies: Boolean = false,
    withSynthesis: Boolean = false,
    synthesisModel: String = "claude-sonnet-4-6",
    synthesisTemperature: Double = 1.0,
    synthesisCacheDir: Option[String] = None,
    dafnyBin: Option[String] = None,
    dafnyTranslateTimeoutSec: Int = 60,
    allowSkeletons: Boolean = false,
    dryRun: Boolean = false
)

object Compile:

  def run(specFile: String, opts: CompileOptions, log: Logger): IO[ExitCode] =
    if opts.withTests && !SupportedTargets.All.contains(opts.target) then
      IO.delay(
        log.error(
          s"--with-tests currently supports only ${SupportedTargets.All.mkString(", ")} " +
            s"(got --target = ${opts.target})"
        )
      ).as(ExitCodes.Violations)
    else
      val warnIfStrictWithoutTests =
        if opts.strictStrategies && !opts.withTests then
          IO.delay(
            log.warn(
              "--strict-strategies has no effect without --with-tests; ignoring"
            )
          )
        else IO.unit
      warnIfStrictWithoutTests *> runImpl(specFile, opts, log)

  private def runImpl(specFile: String, opts: CompileOptions, log: Logger): IO[ExitCode] =
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
              case Left(err) =>
                IO.delay(log.error(Check.renderBuildError(specFile, err))).as(ExitCodes.Violations)
              case Right(ir) =>
                val gate =
                  if opts.ignoreVerify then
                    IO.delay(log.warn("proceeding without verification (--ignore-verify)"))
                      .as(ExitCodes.Ok)
                  else Verify.runGate(specFile, ir, VerificationConfig.Default, log)
                gate.flatMap:
                  case ok if ok == ExitCodes.Ok =>
                    val strictGate =
                      if opts.withTests && opts.strictStrategies then
                        val unhandled = Strategies.forIR(ir).filter(_.skipped.nonEmpty)
                        if unhandled.isEmpty then IO.pure(ExitCodes.Ok)
                        else
                          IO.delay {
                            log.error(
                              "--strict-strategies: type aliases / enums with incomplete strategy synthesis (no convention override registered):"
                            )
                            unhandled.foreach: s =>
                              log.error(s"  ${s.typeName}: ${s.skipped.mkString("; ")}")
                          }.as(ExitCodes.Violations)
                      else IO.pure(ExitCodes.Ok)

                    strictGate.flatMap:
                      case strictOk if strictOk == ExitCodes.Ok =>
                        emitProject(specFile, ir, opts, log)
                      case strictCode => IO.pure(strictCode)
                  case gateCode => IO.pure(gateCode)

  private def emitProject(
      specFile: String,
      ir: ServiceIRFull,
      opts: CompileOptions,
      log: Logger
  ): IO[ExitCode] =
    val profiledBase = Annotate.buildProfiledService(ir, opts.target)
    val kernelStep: IO[Either[ExitCode, Option[KernelBundle]]] =
      if !opts.withSynthesis then IO.pure(Right(None))
      else buildKernel(specFile, ir, opts, log)

    kernelStep.flatMap:
      case Left(code) => IO.pure(code)
      case Right(maybeKernel) =>
        val profiled =
          maybeKernel match
            case Some(b) => Annotate.attachDafnyMethods(profiledBase, b.bindings)
            case None    => profiledBase
        IO.blocking {
          val emitOpts  = EmitOptions(dafnyKernel = maybeKernel.map(_.kernel))
          val baseFiles = Emit.emitProject(profiled, emitOpts)
          val testFiles = if opts.withTests then TestEmit.emit(profiled) else Nil
          val files     = baseFiles ++ testFiles
          val outRoot   = Paths.get(opts.outDir)
          if opts.dryRun then
            val plans = if Files.isDirectory(outRoot) then Plan.classify(files, outRoot)
            else files.map(f => FilePlan(FileAction.Create, f.path))
            val rendered = Plan.render(plans, log.palette)
            val t        = Plan.tally(plans)
            System.out.println(rendered)
            log.success(
              s"dry-run: ${t.total} files planned for ${opts.outDir} " +
                s"(create=${t.create} update=${t.update} unchanged=${t.unchanged} preserve=${t.preserved})"
            )
            ExitCodes.Ok
          else
            Files.createDirectories(outRoot)
            files.foreach: f =>
              val target = outRoot.resolve(f.path)
              Option(target.getParent).foreach(Files.createDirectories(_))
              val isUserStrategies = f.path == FilePaths.StrategiesUserFile
              if isUserStrategies && Files.exists(target) then ()
              else
                Files.writeString(
                  target,
                  f.content,
                  StandardOpenOption.CREATE,
                  StandardOpenOption.TRUNCATE_EXISTING
                )
            log.success(s"wrote ${files.length} files to ${opts.outDir}")
            ExitCodes.Ok
        }.handleErrorWith:
          case NonFatal(e) =>
            IO.delay(
              log.error(s"$specFile: ${Option(e.getMessage).getOrElse(e.toString)}")
            ).as(ExitCodes.Violations)
          case e => IO.raiseError(e)

  final private case class KernelBundle(
      kernel: DafnyKernel,
      bindings: Map[String, String]
  )

  private def buildKernel(
      specFile: String,
      ir: ServiceIRFull,
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitCode, Option[KernelBundle]]] =
    val classifications = Classify.classifyOperations(ir)
    val synthOps        = classifications.filter(_.strategy == SynthesisStrategy.LlmSynthesis)
    if synthOps.isEmpty then
      IO.delay(
        log.warn(
          s"$specFile: --with-synthesis requested but no LLM_SYNTHESIS operations exist; emitting without kernel"
        )
      ).as(Right(None))
    else
      DafnyGenerator.generate(ir) match
        case Left(dErr) =>
          IO.delay(log.error(s"$specFile: Dafny generation failed: ${dErr.message}"))
            .as(Left(ExitCodes.Translator))
        case Right(dafny) =>
          val cacheRoot =
            opts.synthesisCacheDir.map(Paths.get(_)).getOrElse(Cache.defaultRoot(Paths.get("")))
          val verifiedRoot = cacheRoot.resolve("verified")
          loadVerifiedBodies(specFile, synthOps, dafny.methods, verifiedRoot, opts, log).flatMap:
            case Left(code) => IO.pure(Left(code))
            case Right(bodies) =>
              FileAssembly.spliceAll(dafny.text, bodies) match
                case Left(failure) =>
                  IO.delay(log.error(s"$specFile: splice failed: ${failure.message}"))
                    .as(Left(ExitCodes.Translator))
                case Right(fullDfy) =>
                  resolveDafnyAndTranslate(specFile, fullDfy, opts, log).map: r =>
                    r.map: translated =>
                      val bindings = opts.target match
                        case "go-chi-postgres" =>
                          synthOps
                            .map(c =>
                              c.operationName -> s"dafnykernel.${c.operationName}"
                            )
                            .toMap
                        case "ts-express-postgres" =>
                          synthOps
                            .map(c =>
                              c.operationName -> s"dafnyKernel.${c.operationName}"
                            )
                            .toMap
                        case _ =>
                          synthOps
                            .map(c => c.operationName -> dafnyCallable(c.operationName))
                            .toMap
                      val (packagePath, files) = opts.target match
                        case "go-chi-postgres" =>
                          (DafnyKernel.GoDefaultPackagePath, translated.files)
                        case "ts-express-postgres" =>
                          (DafnyKernel.JsDefaultPackagePath, translated.files)
                        case _ =>
                          (
                            DafnyKernel.PythonDefaultPackagePath,
                            DafnyKernel.rewritePythonImports(translated.files)
                          )
                      val kernel = DafnyKernel(
                        packagePath = packagePath,
                        files = files,
                        bindings = bindings.toList
                          .sortBy(_._1)
                          .map((n, p) => OperationBinding(n, p))
                      )
                      Some(KernelBundle(kernel, bindings))

  private def loadVerifiedBodies(
      specFile: String,
      synthOps: List[specrest.convention.OperationClassification],
      methods: List[specrest.convention.dafny.DafnyMethodHeader],
      verifiedRoot: Path,
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitCode, Map[String, String]]] =
    val cacheRoot =
      opts.synthesisCacheDir.map(Paths.get(_)).getOrElse(Cache.defaultRoot(Paths.get("")))
    val skeletonsRoot      = Cache.skeletonsRoot(cacheRoot)
    val skeletonsAvailable = opts.allowSkeletons && Files.isDirectory(skeletonsRoot)
    if !Files.isDirectory(verifiedRoot) && !skeletonsAvailable then
      val msg =
        if opts.allowSkeletons then
          s"$specFile: --with-synthesis --allow-skeletons set but neither $verifiedRoot " +
            s"nor $skeletonsRoot exists; run `synth verify --fallback` (or `synth verify-all`) for " +
            "each LLM_SYNTHESIS op to populate at least one cache namespace"
        else
          s"$specFile: no verified-body cache at $verifiedRoot; run `synth verify` for each " +
            "LLM_SYNTHESIS op first (or pass --allow-skeletons to consume `synth verify --fallback` skeletons)"
      IO.delay(log.error(msg)).as(Left(ExitCodes.Violations))
    else
      val verifiedCacheIO: IO[Option[Cache]] =
        if Files.isDirectory(verifiedRoot) then Cache.make(verifiedRoot).map(Some(_))
        else IO.pure(None)
      val skeletonCacheIO: IO[Option[Cache]] =
        if skeletonsAvailable then Cache.make(skeletonsRoot).map(Some(_))
        else IO.pure(None)
      for
        verifiedCache <- verifiedCacheIO
        skeletonCache <- skeletonCacheIO
        result <- foldVerifiedAndSkeleton(
                    specFile,
                    synthOps,
                    methods,
                    verifiedCache,
                    skeletonCache,
                    opts,
                    log
                  )
      yield result

  private def foldVerifiedAndSkeleton(
      specFile: String,
      synthOps: List[specrest.convention.OperationClassification],
      methods: List[specrest.convention.dafny.DafnyMethodHeader],
      verifiedCache: Option[Cache],
      skeletonCache: Option[Cache],
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitCode, Map[String, String]]] =
    synthOps.foldLeft[IO[Either[ExitCode, Map[String, String]]]](IO.pure(Right(Map.empty))):
      (accIO, c) =>
        accIO.flatMap:
          case Left(code) => IO.pure(Left(code))
          case Right(acc) =>
            methods.find(_.name == c.operationName) match
              case None =>
                IO.delay(
                  log.error(
                    s"$specFile: Dafny header missing for synthesised op '${c.operationName}'"
                  )
                ).as(Left(ExitCodes.Translator))
              case Some(header) =>
                val key = Cache.keyFor(header, opts.synthesisModel, opts.synthesisTemperature)
                lookupOpBody(
                  specFile,
                  c.operationName,
                  key,
                  verifiedCache,
                  skeletonCache,
                  opts,
                  log
                ).map:
                  case Right(body) => Right(acc + (c.operationName -> body))
                  case Left(code)  => Left(code)

  private def lookupOpBody(
      specFile: String,
      opName: String,
      key: specrest.synth.CacheKey,
      verifiedCache: Option[Cache],
      skeletonCache: Option[Cache],
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitCode, String]] =
    val verifiedLookup = verifiedCache match
      case Some(c) => c.lookup(key)
      case None    => IO.pure(None)
    verifiedLookup.flatMap:
      case Some(entry) if entry.outcome == specrest.synth.CacheOutcome.Verified =>
        IO.pure(Right(entry.body))
      case _ =>
        if !opts.allowSkeletons then
          IO.delay(missingVerifiedBodyError(specFile, opName, opts, log))
            .as(Left(ExitCodes.Violations))
        else
          val skeletonLookup = skeletonCache match
            case Some(c) => c.lookup(key)
            case None    => IO.pure(None)
          skeletonLookup.flatMap:
            case Some(entry) =>
              IO.delay(
                log.warn(
                  s"$specFile: '$opName' falling back to unverified skeleton body " +
                    "(--allow-skeletons set); the generated handler will halt at runtime " +
                    "with a Dafny HaltException when invoked"
                )
              ).as(Right(entry.body))
            case None =>
              IO.delay(missingVerifiedBodyError(specFile, opName, opts, log))
                .as(Left(ExitCodes.Violations))

  private def missingVerifiedBodyError(
      specFile: String,
      opName: String,
      opts: CompileOptions,
      log: Logger
  ): Unit =
    log.error(
      s"$specFile: no verified body cached for '$opName' " +
        s"(model=${opts.synthesisModel}, temp=${opts.synthesisTemperature}). " +
        s"Run: cli/run synth verify $specFile --operation $opName " +
        s"--model ${opts.synthesisModel} --temperature ${opts.synthesisTemperature}"
    )

  private def resolveDafnyAndTranslate(
      specFile: String,
      fullDfy: String,
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitCode, TranslatedDafny]] =
    DafnyCli.resolveBinary(opts.dafnyBin).flatMap:
      case Left(msg) =>
        IO.delay(log.error(s"$specFile: $msg")).as(Left(ExitCodes.Backend))
      case Right(binary) =>
        DafnyTranslateCli.make(binary).use: translator =>
          val lang = TargetLanguage.forCompileTarget(opts.target)
          translator.translate(fullDfy, lang, opts.dafnyTranslateTimeoutSec).map:
            case Left(err) =>
              log.error(s"$specFile: dafny translate failed: $err")
              Left(ExitCodes.Backend)
            case Right(out) => Right(out)

  private def dafnyCallable(opName: String): String = opName
