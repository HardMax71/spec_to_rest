package specrest.codegen

object SensitiveFields:
  private val Exact: Set[String] = Set(
    "password",
    "password_hash",
    "secret",
    "token",
    "api_key"
  )

  private val Suffixes: List[String] = List(
    "_hash",
    "_secret",
    "_password",
    "_api_key",
    "_token"
  )

  def isSensitive(name: String): Boolean =
    Exact.contains(name) || Suffixes.exists(name.endsWith)
