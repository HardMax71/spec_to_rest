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

object ExitCodes:
  given CanEqual[ExitCode, ExitCode] = CanEqual.derived

  val Ok: ExitCode         = ExitCode(0)
  val Violations: ExitCode = ExitCode(1)
  val Translator: ExitCode = ExitCode(2)
  val Backend: ExitCode    = ExitCode(3)
  val Trust: ExitCode      = ExitCode(4)

  def forSynthError(e: SynthError): ExitCode = e match
    case _: ProviderFailure      => Backend
    case _: ResponseParseFailure => Translator
    case _: DiffCheckFailure     => Translator
    case _: CacheFailure         => Backend

  def forCegisOutcome(o: CegisOutcome): ExitCode = o match
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

  def forFallbackOutcome(o: FallbackOutcome): ExitCode = o match
    case _: FallbackOutcome.Verified     => Ok
    case _: FallbackOutcome.SkeletonOnly => Violations

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
