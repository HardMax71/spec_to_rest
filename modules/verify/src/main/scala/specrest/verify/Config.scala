package specrest.verify

enum CheckStatus:
  case Sat, Unsat, Unknown

object CheckStatus:
  def tokenLower(s: CheckStatus): String = s match
    case Sat     => "sat"
    case Unsat   => "unsat"
    case Unknown => "unknown"

final case class VerificationConfig(
    timeoutMs: Long,
    captureModel: Boolean = false,
    alloyScope: Int = 5
)

object VerificationConfig:
  val Default: VerificationConfig = VerificationConfig(timeoutMs = 30_000L)

final class TranslatorError(message: String) extends RuntimeException(message)
