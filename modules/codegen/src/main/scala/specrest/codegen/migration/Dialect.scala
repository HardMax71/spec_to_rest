package specrest.codegen.migration

import specrest.convention.ConventionDiagnostic
import specrest.convention.IndexSpec
import specrest.convention.TriggerSpec

final case class DialectCaps(
    supportsPartialIndex: Boolean,
    supportsCheckConstraint: Boolean,
    fkEnforcedByDefault: Boolean,
    requiresTextIndexPrefix: Boolean,
    transactionalDdl: Boolean
) derives CanEqual

final case class SaType(expr: String, importModule: Option[String]) derives CanEqual

final case class TriggerEmission(upgrade: List[String], downgrade: List[String]) derives CanEqual

final case class FeatureEmission[A](value: A, diagnostics: List[ConventionDiagnostic])

trait Dialect:
  def id: String
  def caps: DialectCaps
  def saType(t: CanonicalType): SaType
  def renderTrigger(t: TriggerSpec): TriggerEmission
  def partialIndex(ix: IndexSpec): FeatureEmission[String]

object Dialect:
  def forDatabase(database: String): Dialect = database match
    case "postgres" => Postgres
    case other =>
      throw new RuntimeException(
        s"No SQL dialect registered for database '$other' (known: postgres)"
      )

object Postgres extends Dialect:
  val id = "postgres"

  val caps: DialectCaps = DialectCaps(
    supportsPartialIndex = true,
    supportsCheckConstraint = true,
    fkEnforcedByDefault = true,
    requiresTextIndexPrefix = false,
    transactionalDdl = true
  )

  def saType(t: CanonicalType): SaType = t match
    case CanonicalType.Text                => SaType("sa.Text()", None)
    case CanonicalType.Varchar(n)          => SaType(s"sa.String(length=$n)", None)
    case CanonicalType.Int4                => SaType("sa.Integer()", None)
    case CanonicalType.Serial4             => SaType("sa.Integer()", None)
    case CanonicalType.Int8                => SaType("sa.BigInteger()", None)
    case CanonicalType.Serial8             => SaType("sa.BigInteger()", None)
    case CanonicalType.Float8              => SaType("sa.Float()", None)
    case CanonicalType.Bool                => SaType("sa.Boolean()", None)
    case CanonicalType.Timestamptz         => SaType("sa.DateTime(timezone=True)", None)
    case CanonicalType.DateOnly            => SaType("sa.Date()", None)
    case CanonicalType.Uuid                => SaType("sa.Uuid()", None)
    case CanonicalType.Numeric(p, Some(s)) => SaType(s"sa.Numeric($p, $s)", None)
    case CanonicalType.Numeric(p, None)    => SaType(s"sa.Numeric($p)", None)
    case CanonicalType.Bytes               => SaType("sa.LargeBinary()", None)
    case CanonicalType.Json =>
      SaType("postgresql.JSONB()", Some("sqlalchemy.dialects.postgresql"))

  def renderTrigger(t: TriggerSpec): TriggerEmission =
    TriggerEmission(
      upgrade = List(
        s"op.execute(${AlembicSyntax.tripleQuoted(TriggerSql.functionBody(t))})",
        s"op.execute(${AlembicSyntax.tripleQuoted(TriggerSql.triggerStatement(t))})"
      ),
      downgrade = TriggerSql.dropStatements(t).map(stmt => s"""op.execute("$stmt")""")
    )

  def partialIndex(ix: IndexSpec): FeatureEmission[String] =
    ix.filterClause match
      case Some(f) =>
        FeatureEmission(s", postgresql_where=sa.text(${AlembicSyntax.pythonStringLiteral(f)})", Nil)
      case None => FeatureEmission("", Nil)
