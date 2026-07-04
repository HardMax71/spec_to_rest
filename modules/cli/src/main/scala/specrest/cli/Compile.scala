package specrest.cli

import cats.effect.IO
import specrest.cli.ExitStatus.given
import specrest.codegen.DafnyKernel
import specrest.codegen.Emit
import specrest.codegen.EmitOptions
import specrest.codegen.OperationBinding
import specrest.codegen.migration.Dialect
import specrest.codegen.migration.Revision
import specrest.codegen.migration.SchemaDiff
import specrest.convention.Classify
import specrest.convention.Validate
import specrest.dafny.Generator as DafnyGenerator
import specrest.ir.VerifyError
import specrest.ir.generated.SpecRestGenerated.LlmSynthesis
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.classificationOperationName
import specrest.ir.generated.SpecRestGenerated.classificationStrategy
import specrest.ir.generated.SpecRestGenerated.migration_op
import specrest.ir.generated.SpecRestGenerated.operation_classification
import specrest.ir.generated.SpecRestGenerated.synthesis_strategy
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate
import specrest.profile.LanguageId
import specrest.profile.ProfiledService
import specrest.profile.TargetKey
import specrest.synth.Cache
import specrest.synth.DafnyCli
import specrest.synth.DafnyTranslateCli
import specrest.synth.FileAssembly
import specrest.synth.TargetLanguage
import specrest.synth.TranslatedDafny
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
    withTests: Boolean = true,
    strictStrategies: Boolean = false,
    withSynthesis: Boolean = false,
    synthesisModel: String = "claude-sonnet-4-6",
    synthesisTemperature: Double = 1.0,
    synthesisCacheDir: Option[String] = None,
    dafnyBin: Option[String] = None,
    dafnyTranslateTimeoutSec: Int = 60,
    allowSkeletons: Boolean = false,
    synthesisPartial: Boolean = false,
    dryRun: Boolean = false
)

object Compile:

  private def isLlmSynthesis(s: synthesis_strategy): Boolean = s match
    case _: LlmSynthesis => true
    case _               => false

  def run(specFile: String, opts: CompileOptions, log: Logger): IO[ExitStatus] =
    val downgrade    = opts.withTests && !SupportedTargets.supports(opts.target)
    val resolvedOpts = if downgrade then opts.copy(withTests = false) else opts
    val downgradeNotice =
      if downgrade then
        IO.delay(
          log.warn(
            s"target ${opts.target} does not support native test generation; skipping " +
              "(pass --no-tests to silence this warning)"
          )
        )
      else IO.unit
    val warnIfStrictWithoutTests =
      if resolvedOpts.strictStrategies && !resolvedOpts.withTests then
        IO.delay(
          log.warn(
            "--strict-strategies has no effect without test generation; ignoring"
          )
        )
      else IO.unit
    downgradeNotice *> warnIfStrictWithoutTests *> runImpl(specFile, resolvedOpts, log)

  private def runImpl(specFile: String, opts: CompileOptions, log: Logger): IO[ExitStatus] =
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
              case Left(err) =>
                IO.delay(log.error(Check.renderBuildError(specFile, err))).as(ExitStatus.Violations)
              case Right(ir) =>
                val routeDiags = Validate.validateRoutes(ir) ++ Validate.validateSecurity(ir)
                val gate =
                  if routeDiags.nonEmpty then
                    IO.delay(routeDiags.foreach(d => log.error(Check.renderConv(specFile, d))))
                      .as(ExitStatus.Violations)
                  else if opts.ignoreVerify then
                    IO.delay(log.warn("proceeding without verification (--ignore-verify)"))
                      .as(ExitStatus.Ok)
                  else Verify.runGate(specFile, ir, VerificationConfig.Default, log)
                gate.flatMap:
                  case ok if ok == ExitStatus.Ok =>
                    val strictGate =
                      if opts.withTests && opts.strictStrategies then
                        val unhandled = Strategies.forIR(ir).filter(_.skipped.nonEmpty)
                        if unhandled.isEmpty then IO.pure(ExitStatus.Ok)
                        else
                          IO.delay {
                            log.error(
                              "--strict-strategies: type aliases / enums with incomplete strategy synthesis (no convention override registered):"
                            )
                            unhandled.foreach: s =>
                              log.error(s"  ${s.typeName}: ${s.skipped.mkString("; ")}")
                          }.as(ExitStatus.Violations)
                      else IO.pure(ExitStatus.Ok)

                    strictGate.flatMap:
                      case strictOk if strictOk == ExitStatus.Ok =>
                        emitProject(specFile, ir, opts, log)
                      case strictCode => IO.pure(strictCode)
                  case gateCode =>
                    IO.delay(
                      log.error(
                        s"$specFile: code generation blocked by the verification gate (exit ${gateCode.code}); " +
                          "re-run with --ignore-verify to generate without verifying"
                      )
                    ).as(gateCode)

  private def emitProject(
      specFile: String,
      ir: ServiceIRFull,
      opts: CompileOptions,
      log: Logger
  ): IO[ExitStatus] =
    val profiledBase = Annotate.buildProfiledService(ir, opts.target)
    val kernelStep: IO[Either[ExitStatus, Option[KernelBundle]]] =
      if !opts.withSynthesis then IO.pure(Right(None))
      else buildKernel(specFile, ir, opts, log)

    kernelStep.flatMap:
      case Left(code) => IO.pure(code)
      case Right(maybeKernel) =>
        val profiled =
          maybeKernel match
            case Some(b) => Annotate.attachDafnyMethods(profiledBase, b.bindings, b.candidates)
            case None    => profiledBase
        IO.blocking {
          val outRoot          = Paths.get(opts.outDir)
          val previousSnapshot = SnapshotIO.readSnapshot(outRoot, log)
          val existingRevs     = Revision.discover(outRoot, opts.target)
          val emitOpts = EmitOptions(
            dafnyKernel = maybeKernel.map(_.kernel),
            previousSnapshot = previousSnapshot,
            existingRevisions = existingRevs
          )
          val baseFiles = Emit.emitProject(profiled, emitOpts)
          warnOnDialectDegradations(profiled, log)
          previousSnapshot.foreach: prev =>
            val ops = SchemaDiff.compute(prev, profiled.schema)
            warnOnDestructiveOps(ops, log)
          val testFiles = if opts.withTests then TestEmit.emit(profiled) else Nil
          val files     = baseFiles ++ testFiles
          if opts.dryRun then
            val plans = if Files.isDirectory(outRoot) then Plan.classify(files, outRoot)
            else files.map(f => FilePlan(FileAction.Create, f.path))
            val t = Plan.tally(plans)
            log.data(Plan.render(plans, log.palette))
            log.success(
              s"dry-run: ${t.total} files planned for ${opts.outDir} " +
                s"(create=${t.create} update=${t.update} unchanged=${t.unchanged} preserve=${t.preserved})"
            )
            ExitStatus.Ok
          else
            Files.createDirectories(outRoot)
            files.foreach: f =>
              val target = outRoot.resolve(f.path)
              Option(target.getParent).foreach(Files.createDirectories(_))
              if f.preserve && Files.exists(target) then ()
              else
                Files.writeString(
                  target,
                  f.content,
                  StandardOpenOption.CREATE,
                  StandardOpenOption.TRUNCATE_EXISTING
                )
            log.success(s"wrote ${files.length} files to ${opts.outDir}")
            ExitStatus.Ok
        }.handleErrorWith:
          case NonFatal(e) =>
            IO.delay(
              log.error(s"$specFile: ${Option(e.getMessage).getOrElse(e.toString)}")
            ).as(ExitStatus.Violations)
          case e => IO.raiseError(e)

  final private case class KernelBundle(
      kernel: DafnyKernel,
      bindings: Map[String, String],
      candidates: Map[String, List[specrest.profile.CandidateInput]]
  )

  private def buildKernel(
      specFile: String,
      ir: ServiceIRFull,
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitStatus, Option[KernelBundle]]] =
    val classifications = Classify.classifyOperations(ir)
    val synthOps        = classifications.filter(c => isLlmSynthesis(classificationStrategy(c)))
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
            .as(Left(ExitStatus.Translator))
        case Right(dafny) =>
          val cacheRoot =
            opts.synthesisCacheDir.map(Paths.get(_)).getOrElse(Cache.defaultRoot(Paths.get("")))
          val verifiedRoot = cacheRoot.resolve("verified")
          loadVerifiedBodies(specFile, synthOps, dafny.methods, verifiedRoot, opts, log).flatMap:
            case Left(code)    => IO.pure(Left(code))
            case Right(bodies) =>
              // --synthesis-partial: only ops that actually got a verified body are bound to
              // the kernel; the rest were spliced as skeletons / left unbound, so they must
              // NOT be routed to the kernel — codegen emits the fail-loud stub and testgen
              // (Finding 1) skips them. Binding off `synthOps` instead would call kernel
              // methods backed by unverified placeholder bodies.
              val boundOps =
                synthOps.filter(c => bodies.contains(classificationOperationName(c)))
              FileAssembly.spliceAll(dafny.text, bodies) match
                case Left(failure) =>
                  IO.delay(log.error(s"$specFile: splice failed: ${failure.message}"))
                    .as(Left(ExitStatus.Translator))
                case Right(fullDfy) =>
                  resolveDafnyAndTranslate(specFile, fullDfy, opts, log).map: r =>
                    r.map: translated =>
                      val bindings = boundOps
                        .map: c =>
                          val n = classificationOperationName(c)
                          n -> n
                        .toMap
                      val (packagePath, files) = kernelTargetLanguage(opts.target) match
                        case TargetLanguage.Go =>
                          (DafnyKernel.GoDefaultPackagePath, translated.files)
                        case TargetLanguage.JavaScript =>
                          (DafnyKernel.JsDefaultPackagePath, translated.files)
                        case TargetLanguage.Python =>
                          val rewritten = DafnyKernel.rewritePythonImports(translated.files)
                          val withShim =
                            if rewritten.values.exists(_.contains("from ._externs import")) then
                              rewritten + ("_externs.py" -> DafnyKernel.PythonExternShim)
                            else rewritten
                          (DafnyKernel.PythonDefaultPackagePath, withShim)
                      val kernel = DafnyKernel(
                        packagePath = packagePath,
                        files = files,
                        bindings = bindings.toList
                          .sortBy(_._1)
                          .map((n, p) => OperationBinding(n, p))
                      )
                      val candidates = dafny.methods
                        .filter(m => bindings.contains(m.name))
                        .map: m =>
                          m.name -> m.candidates.map(c =>
                            specrest.profile.CandidateInput(
                              c.param,
                              c.output,
                              c.field,
                              c.sampleLength,
                              c.sampleCharset
                            )
                          )
                        .toMap
                      Some(KernelBundle(kernel, bindings, candidates))

  private def loadVerifiedBodies(
      specFile: String,
      synthOps: List[operation_classification],
      methods: List[specrest.dafny.DafnyMethodHeader],
      verifiedRoot: Path,
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitStatus, Map[String, FileAssembly.MethodPart]]] =
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
      IO.delay(log.error(msg)).as(Left(ExitStatus.Violations))
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
      synthOps: List[operation_classification],
      methods: List[specrest.dafny.DafnyMethodHeader],
      verifiedCache: Option[Cache],
      skeletonCache: Option[Cache],
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitStatus, Map[String, FileAssembly.MethodPart]]] =
    synthOps.foldLeft[IO[Either[ExitStatus, Map[String, FileAssembly.MethodPart]]]](
      IO.pure(Right(Map.empty))
    ): (accIO, c) =>
      accIO.flatMap:
        case Left(code) => IO.pure(Left(code))
        case Right(acc) =>
          val opName = classificationOperationName(c)
          methods.find(_.name == opName) match
            case None =>
              IO.delay(
                log.error(
                  s"$specFile: Dafny header missing for synthesised op '$opName'"
                )
              ).as(Left(ExitStatus.Translator))
            case Some(header) =>
              val key = Cache.keyFor(header, opts.synthesisModel, opts.synthesisTemperature)
              lookupOpBody(
                specFile,
                opName,
                key,
                verifiedCache,
                skeletonCache,
                opts,
                log
              ).map:
                case Right(Some(part)) => Right(acc + (opName -> part))
                case Right(None)       => Right(acc)
                case Left(code)        => Left(code)

  private def lookupOpBody(
      specFile: String,
      opName: String,
      key: specrest.synth.CacheKey,
      verifiedCache: Option[Cache],
      skeletonCache: Option[Cache],
      opts: CompileOptions,
      log: Logger
  ): IO[Either[ExitStatus, Option[FileAssembly.MethodPart]]] =
    val verifiedLookup = verifiedCache match
      case Some(c) => c.lookup(key)
      case None    => IO.pure(None)
    def partialOrError: IO[Either[ExitStatus, Option[FileAssembly.MethodPart]]] =
      if opts.synthesisPartial then
        IO.delay(
          log.warn(
            s"$specFile: '$opName' has no verified body (--synthesis-partial); emitting a " +
              "fail-loud stub — testgen records it in _testgen_skips.json and asserts no behavior"
          )
        ).as(Right(None))
      else
        IO.delay(missingVerifiedBodyError(specFile, opName, opts, log))
          .as(Left(ExitStatus.Violations))
    verifiedLookup.flatMap:
      case Some(entry) if entry.outcome == specrest.synth.CacheOutcome.Verified =>
        IO.pure(Right(Some(methodPartOf(entry, opName))))
      case _ =>
        if !opts.allowSkeletons then partialOrError
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
              ).as(Right(Some(methodPartOf(entry, opName))))
            case None => partialOrError

  // The verifier checked body and helper declarations spliced together, so the
  // kernel assembly must splice the same pair; the helpers live only in the
  // cached candidate block. Skeleton entries are the exception: their
  // candidate is the whole spliced file (not an LLM block), so everything
  // before the method is module content, and their halt-stub bodies need no
  // helpers anyway.
  private[cli] def methodPartOf(
      entry: specrest.synth.CacheEntry,
      opName: String
  ): FileAssembly.MethodPart =
    val helpers =
      if entry.outcome == specrest.synth.CacheOutcome.Skeleton then ""
      else specrest.synth.ResponseParser.helperSection(entry.candidate, opName)
    FileAssembly.MethodPart(entry.body, helpers)

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
  ): IO[Either[ExitStatus, TranslatedDafny]] =
    DafnyCli.resolveBinary(opts.dafnyBin).flatMap:
      case Left(msg) =>
        IO.delay(log.error(s"$specFile: $msg")).as(Left(ExitStatus.Backend))
      case Right(binary) =>
        DafnyTranslateCli.make(binary).use: translator =>
          val lang = kernelTargetLanguage(opts.target)
          translator.translate(fullDfy, lang, opts.dafnyTranslateTimeoutSec).map:
            case Left(err) =>
              log.error(s"$specFile: dafny translate failed: $err")
              Left(ExitStatus.Backend)
            case Right(out) => Right(out)

  // The Dafny kernel's target language follows the compile target's language
  // through the one slug grammar (TargetKey); the CLI constructs the slug from
  // validated flags, so the Python default is the unreachable-parse fallback.
  private def kernelTargetLanguage(target: String): TargetLanguage =
    TargetKey.parse(target).toOption match
      case Some(key) =>
        key.language match
          case LanguageId.Python => TargetLanguage.Python
          case LanguageId.Go     => TargetLanguage.Go
          case LanguageId.Ts     => TargetLanguage.JavaScript
      case None => TargetLanguage.Python

  private def warnOnDialectDegradations(profiled: ProfiledService, log: Logger): Unit =
    val dialect     = Dialect.forDatabase(profiled.profile.database)
    val diagnostics = dialect.schemaDiagnostics(profiled.schema)
    diagnostics.foreach: d =>
      log.warn(s"[${dialect.id}] ${d.message}")

  private def warnOnDestructiveOps(ops: List[migration_op], log: Logger): Unit =
    val destructive = SchemaDiff.destructive(ops)
    if destructive.nonEmpty then
      log.warn(
        s"migration contains ${destructive.size} destructive change(s); " +
          "review the generated migration before applying to populated data"
      )
      destructive.foreach: op =>
        log.warn(s"  - ${SchemaDiff.describeDestructive(op)}")
