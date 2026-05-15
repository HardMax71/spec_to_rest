package specrest.codegen.migration

object AlembicSyntax:

  def mapSqlTypeToSa(sqlType: String, dialect: Dialect = Postgres): String =
    CanonicalType.parse(sqlType) match
      case Some(t) => dialect.saType(t).expr
      case None =>
        throw new RuntimeException(s"Unsupported SQL type in Alembic migration: $sqlType")

  def mapServerDefault(value: Option[String]): Option[String] = value match
    case None          => None
    case Some("NOW()") => Some("sa.func.now()")
    case Some(v)       => Some(s"sa.text(${pythonStringLiteral(v)})")

  def pythonStringLiteral(s: String): String =
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    if !escaped.contains("'") then s"'$escaped'"
    else if !escaped.contains("\"") then s"\"$escaped\""
    else s"\"${escaped.replace("\"", "\\\"")}\""

  def tripleQuoted(body: String): String =
    val safe = body
      .replace("\\", "\\\\")
      .replace("\"\"\"", "\"\"\\\"")
    "\"\"\"\n" + safe + "\n\"\"\""
