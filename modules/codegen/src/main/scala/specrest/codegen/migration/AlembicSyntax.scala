package specrest.codegen.migration

object AlembicSyntax:

  private val DirectSaTypes: Map[String, String] = Map(
    "TEXT"             -> "sa.Text()",
    "BIGSERIAL"        -> "sa.BigInteger()",
    "BIGINT"           -> "sa.BigInteger()",
    "INTEGER"          -> "sa.Integer()",
    "SERIAL"           -> "sa.Integer()",
    "BOOLEAN"          -> "sa.Boolean()",
    "DOUBLE PRECISION" -> "sa.Float()",
    "DATE"             -> "sa.Date()",
    "TIMESTAMPTZ"      -> "sa.DateTime(timezone=True)",
    "UUID"             -> "sa.Uuid()",
    "BYTEA"            -> "sa.LargeBinary()",
    "JSONB"            -> "postgresql.JSONB()"
  )

  private val NumericWithScalePattern = """^NUMERIC\((\d+)\s*,\s*(\d+)\)$""".r
  private val NumericNoScalePattern   = """^NUMERIC\((\d+)\)$""".r
  private val VarcharPattern          = """^VARCHAR\((\d+)\)$""".r

  def mapSqlTypeToSa(sqlType: String): String =
    DirectSaTypes.get(sqlType) match
      case Some(direct) => direct
      case None =>
        sqlType match
          case NumericWithScalePattern(p, s) => s"sa.Numeric($p, $s)"
          case NumericNoScalePattern(p)      => s"sa.Numeric($p)"
          case VarcharPattern(len)           => s"sa.String(length=$len)"
          case _ =>
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

  def usesPostgresDialect(sqlType: String): Boolean =
    mapSqlTypeToSa(sqlType).startsWith("postgresql.")

  def tripleQuoted(body: String): String =
    val safe = body.replace("\"\"\"", "\"\"\\\"\"")
    "\"\"\"\n" + safe + "\n\"\"\""
