package specrest.synth

enum TargetLanguage(val cliFlag: String, val outputSuffix: String) derives CanEqual:
  case Python extends TargetLanguage("py", "-py")
  case Go     extends TargetLanguage("go", "-go")

object TargetLanguage:
  def fromName(name: String): Option[TargetLanguage] =
    name.toLowerCase match
      case "py" | "python" => Some(Python)
      case "go" | "golang" => Some(Go)
      case _               => None

  def forCompileTarget(target: String): TargetLanguage =
    target match
      case "go-chi-postgres" => Go
      case _                 => Python
