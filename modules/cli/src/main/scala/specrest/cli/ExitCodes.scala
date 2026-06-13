package specrest.cli

import cats.effect.ExitCode
import specrest.ir.VerifyError
import specrest.synth.AbortReason
import specrest.synth.CacheFailure
import specrest.synth.CegisOutcome
import specrest.synth.DiffCheckFailure
import specrest.synth.FallbackOutcome
import specrest.synth.ProviderFailure
import specrest.synth.ResponseParseFailure
import specrest.synth.SynthError
import specrest.verify.CheckOutcome
import specrest.verify.CheckResult
import specrest.verify.DiagnosticCategory

enum ExitStatus(val code: Int, val label: String, val meaning: String) derives CanEqual:
  case Ok extends ExitStatus(0, "ok", "all checks passed")
  case Violations
      extends ExitStatus(
        1,
        "violations",
        "a check was violated (unsat or unknown), or the spec failed to parse or build"
      )
  case Translator
      extends ExitStatus(
        2,
        "translator-limit",
        "a check uses a construct the SMT or Alloy translator cannot encode, so verification is incomplete; the skipped checks were not violated"
      )
  case Backend
      extends ExitStatus(
        3,
        "backend-error",
        "the solver backend failed (timeout, crash, or unavailable)"
      )
  case Trust
      extends ExitStatus(
        4,
        "trust-limit",
        "a check is outside the formally verified subset and was skipped to preserve soundness, not a failure"
      )

  def exit: ExitCode = ExitCode(code)

object ExitStatus:
  // Test-run failures reuse the backend-error code; a named alias, not a distinct status.
  val Tests: ExitStatus = Backend

  val legend: String =
    values.toList.map(s => s"${s.code} ${s.label}").mkString("exit codes: ", " | ", "")

  def forSynthError(e: SynthError): ExitStatus = e match
    case _: ProviderFailure      => Backend
    case _: ResponseParseFailure => Translator
    case _: DiffCheckFailure     => Translator
    case _: CacheFailure         => Backend

  def forCegisOutcome(o: CegisOutcome): ExitStatus = o match
    case _: CegisOutcome.Verified => Ok
    case CegisOutcome.Aborted(reason, _, _) =>
      reason match
        case _: AbortReason.BudgetExhausted        => Violations
        case _: AbortReason.StuckOnSameError       => Violations
        case _: AbortReason.ResponseUnparseable    => Translator
        case _: AbortReason.DiffViolation          => Translator
        case _: AbortReason.SpliceFailed           => Translator
        case _: AbortReason.ProviderFailed         => Backend
        case _: AbortReason.VerifierBackendFailure => Backend

  def forFallbackOutcome(o: FallbackOutcome): ExitStatus = o match
    case _: FallbackOutcome.Verified     => Ok
    case _: FallbackOutcome.SkeletonOnly => Violations

  def forVerifyError(e: VerifyError): ExitStatus = e match
    case _: VerifyError.Parse           => Violations
    case _: VerifyError.Build           => Violations
    case _: VerifyError.Translator      => Translator
    case _: VerifyError.AlloyTranslator => Translator
    case _: VerifyError.Backend         => Backend

  def forCheckResults(checks: List[CheckResult], ok: Boolean): ExitStatus =
    val hasBackendError =
      checks.exists(_.diagnostic.exists(_.category == DiagnosticCategory.BackendError))
    val hasViolation =
      checks.exists(c => c.status == CheckOutcome.Unsat || c.status == CheckOutcome.Unknown)
    val hasTranslatorLimitation = checks.exists(c =>
      c.status == CheckOutcome.Skipped &&
        c.diagnostic.exists(_.category == DiagnosticCategory.TranslatorLimitation)
    )
    val hasSoundnessLimitation = checks.exists(c =>
      c.status == CheckOutcome.Skipped &&
        c.diagnostic.exists(_.category == DiagnosticCategory.SoundnessLimitation)
    )
    if hasBackendError then Backend
    else if hasViolation then Violations
    else if hasTranslatorLimitation then Translator
    else if hasSoundnessLimitation then Trust
    else if ok then Ok
    else Violations
