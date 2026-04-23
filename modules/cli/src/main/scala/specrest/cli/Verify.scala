package specrest.cli

import cats.effect.unsafe.implicits.global
import specrest.ir.ServiceIR
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.*
import specrest.verify.Diagnostic.formatDiagnostic
import specrest.verify.alloy.Render as AlloyRender
import specrest.verify.alloy.Translator as AlloyTranslator
import specrest.verify.certificates.DumpSink
import specrest.verify.z3.SmtLib
import specrest.verify.z3.Translator
import specrest.verify.z3.WasmBackend

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
    explain: Boolean = false,
    json: Boolean = false,
    jsonOut: Option[String] = None
)

object Verify:
  val ExitOk: Int         = 0
  val ExitViolations: Int = 1
  val ExitTranslator: Int = 2
  val ExitBackend: Int    = 3

  def run(specFile: String, opts: VerifyOptions, log: Logger): Int =
    val wantsJson = opts.json || opts.jsonOut.isDefined
    val wantsDump =
      opts.dumpSmt || opts.dumpSmtOut.isDefined || opts.dumpAlloy || opts.dumpAlloyOut.isDefined
    if wantsJson && wantsDump then
      log.error(
        "--json / --json-out cannot be combined with --dump-smt / --dump-alloy " +
          "(dump flags short-circuit before checks run; JSON output requires a full run)"
      )
      return ExitViolations
    Check.readSource(specFile, log) match
      case Left(_) => ExitViolations
      case Right(source) =>
        val tParse0 = System.nanoTime()
        val parsedE = Parse.parseSpec(source).unsafeRunSync()
        log.verbose(f"Parsed in ${(System.nanoTime() - tParse0) / 1_000_000.0}%.0fms")
        parsedE match
          case Left(VerifyError.Parse(errors)) =>
            errors.foreach: e =>
              log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            ExitViolations
          case Right(parsed) =>
            val tBuild0 = System.nanoTime()
            Builder.buildIR(parsed.tree).unsafeRunSync() match
              case Left(err) =>
                log.error(Check.renderBuildError(specFile, err))
                ExitViolations
              case Right(ir) =>
                log.verbose(f"Built IR in ${(System.nanoTime() - tBuild0) / 1_000_000.0}%.0fms")
                runWithIR(specFile, ir, opts, log)

  private def runWithIR(
      specFile: String,
      ir: ServiceIR,
      opts: VerifyOptions,
      log: Logger
  ): Int =
    if opts.dumpSmt || opts.dumpSmtOut.isDefined then
      val tTrans0 = System.nanoTime()
      Translator.translate(ir).unsafeRunSync() match
        case Left(err) =>
          log.error(s"$specFile: translator limitation: ${err.message}")
          ExitTranslator
        case Right(script) =>
          log.verbose(
            f"Translated IR to Z3 script: ${script.sorts.length} sorts, ${script.funcs.length} function decls, ${script.assertions.length} assertions (${(System.nanoTime() - tTrans0) / 1_000_000.0}%.0fms)"
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
    else if opts.dumpAlloy || opts.dumpAlloyOut.isDefined then
      AlloyTranslator.translateGlobal(ir, opts.alloyScope).unsafeRunSync() match
        case Left(err) =>
          log.error(s"$specFile: alloy translator: ${err.message}")
          ExitTranslator
        case Right(module) =>
          val source = AlloyRender.render(module)
          opts.dumpAlloyOut match
            case Some(path) =>
              Files.writeString(Paths.get(path), source)
              log.success(s"Wrote Alloy source to $path")
            case None =>
              print(source)
          ExitOk
    else
      log.verbose(s"Timeout: ${opts.timeoutMs}ms")
      log.verbose(s"Alloy scope: ${opts.alloyScope}")
      val sink: Option[DumpSink] = opts.dumpVc match
        case None => None
        case Some(p) =>
          DumpSink.open(Paths.get(p)) match
            case Left(err) =>
              log.error(s"$specFile: ${err.message}")
              return ExitBackend
            case Right(s) => Some(s)
      sink.foreach(s => log.verbose(s"Writing per-check VC artifacts to ${s.dir}"))
      val backend = WasmBackend()
      try
        val tRun0 = System.nanoTime()
        val report = Consistency.runConsistencyChecksSync(
          ir,
          backend,
          VerificationConfig(
            timeoutMs = opts.timeoutMs,
            alloyScope = opts.alloyScope,
            captureCore = opts.explain
          ),
          sink
        )
        val totalMs = (System.nanoTime() - tRun0) / 1_000_000.0
        sink.foreach: s =>
          s.writeIndex(specFile, totalMs, report.ok)
          log.success(s"Wrote ${s.entryCount} VC artifacts and verdicts.json to ${s.dir}")
        if opts.json || opts.jsonOut.isDefined then
          val rendered = JsonReport.render(JsonReport.toJson(specFile, report, totalMs))
          opts.jsonOut match
            case Some(path) =>
              val _ = Files.writeString(Paths.get(path), rendered)
              log.success(s"Wrote JSON report to $path")
            case None =>
              print(rendered)
          exitCodeFor(report.checks, report.ok)
        else reportConsistency(specFile, report.checks, report.ok, totalMs, log)
      finally backend.close()

  private def reportConsistency(
      specFile: String,
      checks: List[CheckResult],
      ok: Boolean,
      totalMs: Double,
      log: Logger
  ): Int =
    val passes   = checks.count(_.status == CheckOutcome.Sat)
    val skipped  = checks.count(_.status == CheckOutcome.Skipped)
    val failures = checks.length - passes - skipped
    val exitCode = exitCodeFor(checks, ok)

    if exitCode == ExitOk then
      log.success(
        f"$specFile: $passes/${checks.length} consistency checks passed (${totalMs}%.0fms)"
      )
      checks.foreach(c => log.verbose(formatCheckLine(c)))
      exitCode
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

  private def exitCodeFor(checks: List[CheckResult], ok: Boolean): Int =
    val hasBackendError =
      checks.exists(_.diagnostic.exists(_.category == DiagnosticCategory.BackendError))
    val hasViolation =
      checks.exists(c => c.status == CheckOutcome.Unsat || c.status == CheckOutcome.Unknown)
    val hasTranslatorLimitation = checks.exists(c =>
      c.status == CheckOutcome.Skipped &&
        c.diagnostic.exists(_.category == DiagnosticCategory.TranslatorLimitation)
    )
    if hasBackendError then ExitBackend
    else if hasViolation then ExitViolations
    else if hasTranslatorLimitation then ExitTranslator
    else if ok then ExitOk
    else ExitViolations

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
