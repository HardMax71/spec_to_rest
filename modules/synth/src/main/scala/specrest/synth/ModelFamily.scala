package specrest.synth

enum ModelFamily derives CanEqual:
  case Anthropic
  case OpenAI

object ModelFamily:
  def of(model: String): Option[ModelFamily] =
    val m = model.toLowerCase
    if m.startsWith("gpt") then Some(ModelFamily.OpenAI)
    else if m.startsWith("claude") then Some(ModelFamily.Anthropic)
    else None
