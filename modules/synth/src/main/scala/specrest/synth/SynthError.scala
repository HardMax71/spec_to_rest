package specrest.synth

sealed trait SynthError derives CanEqual:
  def message: String

final case class ProviderFailure(error: ProviderError) extends SynthError:
  def message: String = error.message

final case class ResponseParseFailure(error: ParseError) extends SynthError:
  def message: String = error.message

final case class DiffCheckFailure(error: DiffViolation) extends SynthError:
  def message: String = error.message

final case class CacheFailure(message: String) extends SynthError
