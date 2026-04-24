package specrest.verify

enum CheckStatus:
  case Sat, Unsat, Unknown

object CheckStatus:
  def token(s: CheckStatus): String = s match
    case Sat     => "sat"
    case Unsat   => "unsat"
    case Unknown => "unknown"

final case class VerificationConfig(
    timeoutMs: Long,
    captureModel: Boolean = false,
    alloyScope: Int = 5,
    captureCore: Boolean = false,
    maxParallel: Int = VerificationConfig.defaultParallelism,
    suggestions: Boolean = true
)

object VerificationConfig:
  val defaultParallelism: Int = Runtime.getRuntime.availableProcessors

  val Default: VerificationConfig = VerificationConfig(timeoutMs = 30_000L)
