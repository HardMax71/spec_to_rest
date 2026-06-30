package specrest.synth

enum ModelFamily derives CanEqual:
  case Anthropic
  case OpenAI

object ModelFamily:
  def of(model: String): ModelFamily =
    if model.toLowerCase.startsWith("gpt") then ModelFamily.OpenAI
    else ModelFamily.Anthropic
