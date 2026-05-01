package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.Resource
import specrest.cli.ExitCodes.given
import specrest.ir.ServiceIR
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.*
import specrest.verify.Diagnostic.formatDiagnostic
import specrest.verify.alloy.Render as AlloyRender
import specrest.verify.alloy.Translator as AlloyTranslator
import specrest.verify.cert.Emit as CertEmit
import specrest.verify.certificates.DumpSink
import specrest.verify.z3.SmtLib
import specrest.verify.z3.Translator

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

final case class VerifyOptions(
    timeoutMs: Long,
    dumpSmt: Boolean,
    dumpSmtOut: Option[String],
    dumpAlloy: Boolean = false,
    dumpAlloyOut: Option[String] = None,
    alloyScope: Int = 5,
    dumpVc: Option[String] = None,
    emitCert: Option[String] = None,
    explain: Boolean = false,
    json: Boolean = false,
    jsonOut: Option[String] = None,
    parallel: Option[Int] = None,
    suggestions: Boolean = true,
    narration: Boolean = true
)

object Verify:

  def run(
      specFile: String,
      opts: VerifyOptions,
      log: Logger,
      stdout: PrintStream = System.out
  ): IO[ExitCode] =
    val wantsJson = opts.json || opts.jsonOut.isDefined
    val wantsDump =
      opts.dumpSmt || opts.dumpSmtOut.isDefined || opts.dumpAlloy || opts.dumpAlloyOut.isDefined
    if wantsJson && wantsDump then
      IO.delay {
        log.error(
          "--json / --json-out cannot be combined with --dump-smt / --dump-alloy " +
            "(dump flags short-circuit before checks run; JSON output requires a full run)"
        )
      }.as(ExitCodes.Violations)
    else
      Check.readSource(specFile, log).flatMap:
        case Left(code) => IO.pure(code)
        case Right(source) =>
          val tParse0 = System.nanoTime()
          Parse.parseSpec(source).flatMap { parsedE =>
            IO.delay(log.verbose(f"Parsed in ${(System.nanoTime() - tParse0) / 1_000_000.0}%.0fms"))
              .flatMap: _ =>
                parsedE match
                  case Left(VerifyError.Parse(errors)) =>
                    IO.delay {
                      errors.foreach: e =>
                        log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
                    }.as(ExitCodes.Violations)
                  case Right(parsed) =>
                    val tBuild0 = System.nanoTime()
                    Builder.buildIR(parsed.tree).flatMap:
                      case Left(err) =>
                        IO.delay(log.error(Check.renderBuildError(specFile, err)))
                          .as(ExitCodes.Violations)
                      case Right(ir) =>
                        IO.delay(
                          log.verbose(
                            f"Built IR in ${(System.nanoTime() - tBuild0) / 1_000_000.0}%.0fms"
                          )
                        ) >> emitCertBundleIfRequested(ir, opts, log) >>
                          runWithIR(specFile, ir, opts, log, stdout)
          }

  private def runWithIR(
      specFile: String,
      ir: ServiceIR,
      opts: VerifyOptions,
      log: Logger,
      stdout: PrintStream
  ): IO[ExitCode] =
    if opts.dumpSmt || opts.dumpSmtOut.isDefined then
      dumpSmtFlow(specFile, ir, opts, log, stdout)
    else if opts.dumpAlloy || opts.dumpAlloyOut.isDefined then
      dumpAlloyFlow(specFile, ir, opts, log, stdout)
    else
      verifyFlow(specFile, ir, opts, log, stdout)

  /** When `--emit-cert <dir>` is set, write a self-contained Lake-project bundle containing one
    * translation-validation theorem per invariant + per operation `requires` clause (M_L.3, issue
    * #129).
    *
    * The cert bundle's `lakefile.toml` declares a path-based `[[require]]` against the in-repo
    * `proofs/lean/` workspace, resolved as `<cwd>/proofs/lean` in absolute form. Run `cd <dir> &&
    * lake build` to discharge the certs.
    */
  private def emitCertBundleIfRequested(
      ir: ServiceIR,
      opts: VerifyOptions,
      log: Logger
  ): IO[Unit] = opts.emitCert match
    case None => IO.unit
    case Some(dir) =>
      IO.blocking {
        val outDir          = Paths.get(dir).toAbsolutePath
        val proofsLeanLocal = Paths.get("proofs/lean").toAbsolutePath
        if !Files.exists(proofsLeanLocal.resolve("lakefile.toml")) then
          throw new java.io.FileNotFoundException(
            s"proofs/lean workspace not found at $proofsLeanLocal " +
              "(expected the in-repo Lake workspace; run from the spec_to_rest repo root)"
          )
        Files.createDirectories(outDir)
        val proofsPath = proofsLeanLocal.toString
        val bundle     = CertEmit.emit(ir, proofsPath)
        Files.writeString(outDir.resolve("lakefile.toml"), bundle.renderLakefile)
        // Copy the project's lean-toolchain pin so the cert bundle uses the
        // same Lean version SpecRest was tested against.
        val toolchainText =
          val src = Paths.get("proofs/lean/lean-toolchain")
          if Files.exists(src) then Files.readString(src)
          else "leanprover/lean4:v4.29.1\n"
        Files.writeString(outDir.resolve("lean-toolchain"), toolchainText)
        Files.writeString(
          outDir.resolve(s"${bundle.moduleName}.lean"),
          bundle.renderModule
        )
        log.success(
          s"Wrote translation-validation cert (${bundle.summary.totalChecks} obligations: " +
            s"${bundle.summary.certifiedChecks} cert_decide, " +
            s"${bundle.summary.stubbedChecks} trivial) to $outDir"
        )
      }

  private def dumpSmtFlow(
      specFile: String,
      ir: ServiceIR,
      opts: VerifyOptions,
      log: Logger,
      stdout: PrintStream
  ): IO[ExitCode] =
    val tTrans0 = System.nanoTime()
    Translator.translate(ir).flatMap:
      case Left(err) =>
        IO.delay(log.error(s"$specFile: translator limitation: ${err.message}"))
          .as(ExitCodes.Translator)
      case Right(script) =>
        val transMs = (System.nanoTime() - tTrans0) / 1_000_000.0
        IO.delay(
          log.verbose(
            f"Translated IR to Z3 script: ${script.sorts.length} sorts, ${script.funcs.length} function decls, ${script.assertions.length} assertions (${transMs}%.0fms)"
          )
        ) >> {
          val timeout = if opts.timeoutMs > 0 then Some(opts.timeoutMs) else None
          val smt     = SmtLib.renderSmtLib(script, timeout)
          opts.dumpSmtOut match
            case Some(path) =>
              IO.blocking(Files.writeString(Paths.get(path), smt))
                .productR(IO.delay(log.success(s"Wrote SMT-LIB to $path")))
                .as(ExitCodes.Ok)
            case None =>
              IO.blocking { stdout.print(smt); stdout.flush() }.as(ExitCodes.Ok)
        }

  private def dumpAlloyFlow(
      specFile: String,
      ir: ServiceIR,
      opts: VerifyOptions,
      log: Logger,
      stdout: PrintStream
  ): IO[ExitCode] =
    AlloyTranslator.translateGlobal(ir, opts.alloyScope).flatMap:
      case Left(err) =>
        IO.delay(log.error(s"$specFile: alloy translator: ${err.message}"))
          .as(ExitCodes.Translator)
      case Right(module) =>
        val source = AlloyRender.render(module)
        opts.dumpAlloyOut match
          case Some(path) =>
            IO.blocking(Files.writeString(Paths.get(path), source))
              .productR(IO.delay(log.success(s"Wrote Alloy source to $path")))
              .as(ExitCodes.Ok)
          case None =>
            IO.blocking { stdout.print(source); stdout.flush() }.as(ExitCodes.Ok)

  private def openDumpSink(
      specFile: String,
      opts: VerifyOptions,
      log: Logger
  ): Resource[IO, Either[ExitCode, Option[DumpSink]]] =
    opts.dumpVc match
      case None =>
        Resource.pure(Right(None))
      case Some(p) =>
        DumpSink.openResource(Paths.get(p)).evalMap:
          case Left(err) =>
            IO.delay(log.error(s"$specFile: ${err.message}"))
              .as(Left(ExitCodes.Backend))
          case Right(sink) =>
            IO.delay(log.verbose(s"Writing per-check VC artifacts to ${sink.dir}"))
              .as(Right(Some(sink)))

  private def verifyFlow(
      specFile: String,
      ir: ServiceIR,
      opts: VerifyOptions,
      log: Logger,
      stdout: PrintStream
  ): IO[ExitCode] =
    IO.delay(log.verbose(s"Timeout: ${opts.timeoutMs}ms")) >>
      IO.delay(log.verbose(s"Alloy scope: ${opts.alloyScope}")) >>
      openDumpSink(specFile, opts, log).use:
        case Left(code) => IO.pure(code)
        case Right(sink) =>
          val maxParallel = opts.parallel.getOrElse(VerificationConfig.defaultParallelism)
          IO.delay(log.verbose(s"Max parallel: $maxParallel")) >> {
            val tRun0 = System.nanoTime()
            Consistency.runConsistencyChecks(
              ir,
              VerificationConfig(
                timeoutMs = opts.timeoutMs,
                alloyScope = opts.alloyScope,
                captureCore = opts.explain,
                maxParallel = maxParallel,
                suggestions = opts.suggestions,
                narration = opts.narration
              ),
              sink
            ).flatMap { report =>
              val totalMs = (System.nanoTime() - tRun0) / 1_000_000.0
              val writeIndex = sink match
                case Some(s) =>
                  IO.blocking(s.writeIndex(specFile, totalMs, report.ok)) >>
                    IO.delay(
                      log.success(
                        s"Wrote ${s.entryCount} VC artifacts and verdicts.json to ${s.dir}"
                      )
                    )
                case None => IO.unit
              writeIndex >> {
                if opts.json || opts.jsonOut.isDefined then
                  emitJson(specFile, report, totalMs, opts, log, stdout)
                else reportConsistency(specFile, report.checks, report.ok, totalMs, log)
              }
            }
          }

  private def emitJson(
      specFile: String,
      report: ConsistencyReport,
      totalMs: Double,
      opts: VerifyOptions,
      log: Logger,
      stdout: PrintStream
  ): IO[ExitCode] =
    val rendered = JsonReport.render(JsonReport.toJson(specFile, report, totalMs))
    val write = opts.jsonOut match
      case Some(path) =>
        IO.blocking(Files.writeString(Paths.get(path), rendered)).void >>
          IO.delay(log.success(s"Wrote JSON report to $path"))
      case None =>
        IO.blocking { stdout.print(rendered); stdout.flush() }
    write.as(ExitCodes.forCheckResults(report.checks, report.ok))

  def runGate(
      specFile: String,
      ir: ServiceIR,
      config: VerificationConfig,
      log: Logger
  ): IO[ExitCode] =
    val tRun0 = System.nanoTime()
    Consistency.runConsistencyChecks(ir, config, None).flatMap: report =>
      val totalMs = (System.nanoTime() - tRun0) / 1_000_000.0
      reportConsistency(specFile, report.checks, report.ok, totalMs, log)

  private[cli] def reportConsistency(
      specFile: String,
      checks: List[CheckResult],
      ok: Boolean,
      totalMs: Double,
      log: Logger
  ): IO[ExitCode] =
    IO.delay {
      val passes   = checks.count(_.status == CheckOutcome.Sat)
      val skipped  = checks.count(_.status == CheckOutcome.Skipped)
      val failures = checks.length - passes - skipped
      val exitCode = ExitCodes.forCheckResults(checks, ok)

      if exitCode == ExitCodes.Ok then
        log.success(
          f"$specFile: $passes/${checks.length} consistency checks passed (${totalMs}%.0fms)"
        )
        checks.foreach(c => log.verbose(formatCheckLine(c)))
      else
        if failures == 0 && skipped > 0 then
          log.warn(
            f"$specFile: $passes/${checks.length} checks passed; $skipped skipped (translator coverage gap) (${totalMs}%.0fms)"
          )
        else
          log.error(
            f"$specFile: $failures failure(s), $skipped skipped in ${checks.length} consistency checks (${totalMs}%.0fms)"
          )

        checks.foreach: c =>
          if c.status == CheckOutcome.Sat then log.verbose(formatCheckLine(c))
          else
            c.diagnostic match
              case Some(diag) =>
                if c.status == CheckOutcome.Skipped then
                  log.warn("")
                  log.warn(formatDiagnostic(diag, specFile))
                else
                  log.error("")
                  log.error(formatDiagnostic(diag, specFile))
              case None =>
                log.error(formatCheckLine(c))
      exitCode
    }

  private def formatCheckLine(c: CheckResult): String =
    val icon = c.status match
      case CheckOutcome.Sat     => "✔"
      case CheckOutcome.Skipped => "·"
      case _                    => "✘"
    val statusStr = c.status match
      case CheckOutcome.Sat     => "sat"
      case CheckOutcome.Unsat   => "unsat"
      case CheckOutcome.Unknown => "unknown"
      case CheckOutcome.Skipped => "skipped"
    val tag      = s"[${VerifierTool.token(c.tool)}]".padTo(7, ' ')
    val id       = c.id.padTo(28, ' ')
    val status   = statusStr.padTo(8, ' ')
    val duration = f"${c.durationMs}%.0fms".reverse.padTo(8, ' ').reverse
    val detail   = c.detail.map(d => s" — $d").getOrElse("")
    s"  $icon $tag $id $status $duration$detail"
