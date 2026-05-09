package specrest.synth

enum TargetLanguage(val cliFlag: String, val outputSuffix: String) derives CanEqual:
  case Python extends TargetLanguage("py", "-py")

object TargetLanguage:
  def fromName(name: String): Option[TargetLanguage] =
    name.toLowerCase match
      case "py" | "python" => Some(Python)
      case _               => None
