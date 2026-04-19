package specrest.cli

import java.nio.file.{Files, Paths}
import specrest.parser.{BuildError, Builder, Parse}
import specrest.verify.*
import specrest.verify.Diagnostic.formatDiagnostic

final case class VerifyOptions(
    timeoutMs: Long,
    dumpSmt: Boolean,
    dumpSmtOut: Option[String],
)

object Verify:
  val ExitOk: Int         = 0
  val ExitViolations: Int = 1
  val ExitTranslator: Int = 2
  val ExitBackend: Int    = 3

  def run(specFile: String, opts: VerifyOptions, log: Logger): Int =
    Check.readSource(specFile, log) match
      case Left(_) => ExitViolations
      case Right(source) =>
        val tParse0 = System.nanoTime()
        val parsed  = Parse.parseSpec(source)
        log.verbose(f"Parsed in ${(System.nanoTime() - tParse0) / 1_000_000.0}%.0fms")
        if parsed.errors.nonEmpty then
          parsed.errors.foreach: e =>
            log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
          ExitViolations
        else
          try runWithIR(specFile, parsed.tree, opts, log)
          catch
            case e: BuildError =>
              log.error(s"$specFile: ${e.getMessage}")
              ExitViolations
            case e: TranslatorError =>
              log.error(s"$specFile: translator limitation: ${e.getMessage}")
              ExitTranslator
            case e: RuntimeException =>
              log.error(s"$specFile: ${e.getMessage}")
              ExitBackend

  private def runWithIR(
      specFile: String,
      tree: specrest.parser.generated.SpecParser.SpecFileContext,
      opts: VerifyOptions,
      log: Logger,
  ): Int =
    val tBuild0 = System.nanoTime()
    val ir      = Builder.buildIR(tree)
    log.verbose(f"Built IR in ${(System.nanoTime() - tBuild0) / 1_000_000.0}%.0fms")

    if opts.dumpSmt || opts.dumpSmtOut.isDefined then
      val tTrans0 = System.nanoTime()
      val script  = Translator.translate(ir)
      log.verbose(
        f"Translated IR to Z3 script: ${script.sorts.length} sorts, ${script.funcs.length} function decls, ${script.assertions.length} assertions (${(System.nanoTime() - tTrans0) / 1_000_000.0}%.0fms)",
      )
      val timeout = if opts.timeoutMs > 0 then Some(opts.timeoutMs) else None
      val smt     = SmtLib.renderSmtLib(script, timeout)
      opts.dumpSmtOut match
        case Some(path) =>
          Files.writeString(Paths.get(path), smt)
          log.success(s"Wrote SMT-LIB to $path")
        case None =>
          print(smt)
      ExitOk
    else
      log.verbose(s"Timeout: ${opts.timeoutMs}ms")
      val backend = WasmBackend()
      try
        val tRun0 = System.nanoTime()
        val report = Consistency.runConsistencyChecks(
          ir,
          backend,
          VerificationConfig(timeoutMs = opts.timeoutMs),
        )
        val totalMs = (System.nanoTime() - tRun0) / 1_000_000.0
        reportConsistency(specFile, report.checks, report.ok, totalMs, log)
      finally backend.close()

  private def reportConsistency(
      specFile: String,
      checks: List[CheckResult],
      ok: Boolean,
      totalMs: Double,
      log: Logger,
  ): Int =
    val passes   = checks.count(_.status == CheckOutcome.Sat)
    val skipped  = checks.count(_.status == CheckOutcome.Skipped)
    val failures = checks.length - passes - skipped
    val exitCode = exitCodeFor(checks, ok)

    if exitCode == ExitOk then
      log.success(
        f"$specFile: $passes/${checks.length} consistency checks passed (${totalMs}%.0fms)",
      )
      checks.foreach(c => log.verbose(formatCheckLine(c)))
      exitCode
    else
      if failures == 0 && skipped > 0 then
        log.warn(
          f"$specFile: $passes/${checks.length} checks passed; $skipped skipped (translator coverage gap) (${totalMs}%.0fms)",
        )
      else
        log.error(
          f"$specFile: $failures failure(s), $skipped skipped in ${checks.length} consistency checks (${totalMs}%.0fms)",
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

  private def exitCodeFor(checks: List[CheckResult], ok: Boolean): Int =
    val hasBackendError = checks.exists(_.diagnostic.exists(_.category == DiagnosticCategory.BackendError))
    if hasBackendError then return ExitBackend
    val hasViolation = checks.exists(c => c.status == CheckOutcome.Unsat || c.status == CheckOutcome.Unknown)
    if hasViolation then return ExitViolations
    val hasTranslatorLimitation = checks.exists(c =>
      c.status == CheckOutcome.Skipped &&
        c.diagnostic.exists(_.category == DiagnosticCategory.TranslatorLimitation),
    )
    if hasTranslatorLimitation then ExitTranslator
    else if ok then ExitOk
    else ExitViolations

  private def formatCheckLine(c: CheckResult): String =
    val icon = c.status match
      case CheckOutcome.Sat     => "✔"
      case CheckOutcome.Skipped => "·"
      case _                     => "✘"
    val statusStr = c.status match
      case CheckOutcome.Sat     => "sat"
      case CheckOutcome.Unsat   => "unsat"
      case CheckOutcome.Unknown => "unknown"
      case CheckOutcome.Skipped => "skipped"
    val id       = c.id.padTo(28, ' ')
    val status   = statusStr.padTo(8, ' ')
    val duration = f"${c.durationMs}%.0fms".reverse.padTo(8, ' ').reverse
    val detail   = c.detail.map(d => s" — $d").getOrElse("")
    s"  $icon $id $status $duration$detail"
