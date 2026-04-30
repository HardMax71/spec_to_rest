package specrest.cli

import cats.effect.ExitCode
import specrest.ir.VerifyError
import specrest.verify.CheckOutcome
import specrest.verify.CheckResult
import specrest.verify.DiagnosticCategory

object ExitCodes:
  given CanEqual[ExitCode, ExitCode] = CanEqual.derived

  val Ok: ExitCode         = ExitCode(0)
  val Violations: ExitCode = ExitCode(1)
  val Translator: ExitCode = ExitCode(2)
  val Backend: ExitCode    = ExitCode(3)

  def forVerifyError(e: VerifyError): ExitCode = e match
    case _: VerifyError.Parse           => Violations
    case _: VerifyError.Build           => Violations
    case _: VerifyError.Translator      => Translator
    case _: VerifyError.AlloyTranslator => Translator
    case _: VerifyError.Backend         => Backend

  def forCheckResults(checks: List[CheckResult], ok: Boolean): ExitCode =
    val hasBackendError =
      checks.exists(_.diagnostic.exists(_.category == DiagnosticCategory.BackendError))
    val hasViolation =
      checks.exists(c => c.status == CheckOutcome.Unsat || c.status == CheckOutcome.Unknown)
    val hasTranslatorLimitation = checks.exists(c =>
      c.status == CheckOutcome.Skipped &&
        c.diagnostic.exists(_.category == DiagnosticCategory.TranslatorLimitation)
    )
    if hasBackendError then Backend
    else if hasViolation then Violations
    else if hasTranslatorLimitation then Translator
    else if ok then Ok
    else Violations
