package specrest.synth

enum TargetLanguage(val cliFlag: String, val outputSuffix: String) derives CanEqual:
  case Python     extends TargetLanguage("py", "-py")
  case Go         extends TargetLanguage("go", "-go")
  case JavaScript extends TargetLanguage("js", "-js")

object TargetLanguage:
  def fromName(name: String): Option[TargetLanguage] =
    name.toLowerCase match
      case "py" | "python"            => Some(Python)
      case "go" | "golang"            => Some(Go)
      case "js" | "javascript" | "ts" => Some(JavaScript)
      case _                          => None
