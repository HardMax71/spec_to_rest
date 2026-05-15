package specrest.codegen.migration

object AlembicSyntax:

  def mapSqlTypeToSa(sqlType: String): String =
    CanonicalType.parse(sqlType) match
      case Some(t) => renderPostgresSa(t)
      case None =>
        throw new RuntimeException(s"Unsupported SQL type in Alembic migration: $sqlType")

  private def renderPostgresSa(t: CanonicalType): String = t match
    case CanonicalType.Text                => "sa.Text()"
    case CanonicalType.Varchar(n)          => s"sa.String(length=$n)"
    case CanonicalType.Int4                => "sa.Integer()"
    case CanonicalType.Serial4             => "sa.Integer()"
    case CanonicalType.Int8                => "sa.BigInteger()"
    case CanonicalType.Serial8             => "sa.BigInteger()"
    case CanonicalType.Float8              => "sa.Float()"
    case CanonicalType.Bool                => "sa.Boolean()"
    case CanonicalType.Timestamptz         => "sa.DateTime(timezone=True)"
    case CanonicalType.DateOnly            => "sa.Date()"
    case CanonicalType.Uuid                => "sa.Uuid()"
    case CanonicalType.Numeric(p, Some(s)) => s"sa.Numeric($p, $s)"
    case CanonicalType.Numeric(p, None)    => s"sa.Numeric($p)"
    case CanonicalType.Bytes               => "sa.LargeBinary()"
    case CanonicalType.Json                => "postgresql.JSONB()"

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
    val safe = body
      .replace("\\", "\\\\")
      .replace("\"\"\"", "\"\"\\\"")
    "\"\"\"\n" + safe + "\n\"\"\""
