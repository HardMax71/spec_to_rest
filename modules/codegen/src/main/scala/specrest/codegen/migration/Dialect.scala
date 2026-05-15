package specrest.codegen.migration

import specrest.convention.ConventionDiagnostic
import specrest.convention.DatabaseSchema
import specrest.convention.DiagnosticLevel
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

  def schemaDiagnostics(schema: DatabaseSchema): List[ConventionDiagnostic] =
    schema.tables.flatMap(_.indexes).flatMap(ix => partialIndex(ix).diagnostics)

object Dialect:
  def forDatabase(database: String): Dialect = database match
    case "postgres" => Postgres
    case "sqlite"   => Sqlite
    case "mysql"    => Mysql
    case other =>
      throw new RuntimeException(
        s"No SQL dialect registered for database '$other' (known: postgres, sqlite, mysql)"
      )

  private def recompute(t: TriggerSpec, rowRef: String): String =
    val agg = TriggerSql.aggregateExpr(t)
    s"UPDATE ${t.targetTable} SET ${t.targetColumn} = " +
      s"(SELECT $agg FROM ${t.sourceTable} " +
      s"WHERE ${t.sourceForeignKey} = $rowRef.${t.sourceForeignKey}) " +
      s"WHERE id = $rowRef.${t.sourceForeignKey};"

  private def createTrigger(name: String, event: String, src: String, body: String): String =
    s"CREATE TRIGGER $name AFTER $event ON $src FOR EACH ROW BEGIN $body END"

  /** SQLite and MySQL share the same shape: one single-statement BEGIN/END trigger per row event
    * (no stored function, no PL/pgSQL). UPDATE recomputes both the old and new parent in case the
    * foreign key moved between parents.
    */
  def perEventTriggerEmission(t: TriggerSpec): TriggerEmission =
    val triggers = List(
      s"${t.name}_ins" -> createTrigger(
        s"${t.name}_ins",
        "INSERT",
        t.sourceTable,
        recompute(t, "NEW")
      ),
      s"${t.name}_upd" -> createTrigger(
        s"${t.name}_upd",
        "UPDATE",
        t.sourceTable,
        recompute(t, "OLD") + " " + recompute(t, "NEW")
      ),
      s"${t.name}_del" -> createTrigger(
        s"${t.name}_del",
        "DELETE",
        t.sourceTable,
        recompute(t, "OLD")
      )
    )
    TriggerEmission(
      upgrade = triggers.map((_, sql) => s"op.execute(${AlembicSyntax.tripleQuoted(sql)})"),
      downgrade = triggers.map((name, _) => s"""op.execute("DROP TRIGGER IF EXISTS $name;")""")
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

object Sqlite extends Dialect:
  val id = "sqlite"

  val caps: DialectCaps = DialectCaps(
    supportsPartialIndex = true,
    supportsCheckConstraint = true,
    fkEnforcedByDefault = false,
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
    case CanonicalType.Timestamptz         => SaType("sa.DateTime()", None)
    case CanonicalType.DateOnly            => SaType("sa.Date()", None)
    case CanonicalType.Uuid                => SaType("sa.Uuid()", None)
    case CanonicalType.Numeric(p, Some(s)) => SaType(s"sa.Numeric($p, $s)", None)
    case CanonicalType.Numeric(p, None)    => SaType(s"sa.Numeric($p)", None)
    case CanonicalType.Bytes               => SaType("sa.LargeBinary()", None)
    case CanonicalType.Json                => SaType("sa.JSON()", None)

  def renderTrigger(t: TriggerSpec): TriggerEmission = Dialect.perEventTriggerEmission(t)

  def partialIndex(ix: IndexSpec): FeatureEmission[String] =
    ix.filterClause match
      case Some(f) =>
        FeatureEmission(s", sqlite_where=sa.text(${AlembicSyntax.pythonStringLiteral(f)})", Nil)
      case None => FeatureEmission("", Nil)

object Mysql extends Dialect:
  val id = "mysql"

  val caps: DialectCaps = DialectCaps(
    supportsPartialIndex = false,
    supportsCheckConstraint = true,
    fkEnforcedByDefault = true,
    requiresTextIndexPrefix = true,
    transactionalDdl = false
  )

  def saType(t: CanonicalType): SaType = t match
    case CanonicalType.Text                => SaType("sa.String(length=255)", None)
    case CanonicalType.Varchar(n)          => SaType(s"sa.String(length=$n)", None)
    case CanonicalType.Int4                => SaType("sa.Integer()", None)
    case CanonicalType.Serial4             => SaType("sa.Integer()", None)
    case CanonicalType.Int8                => SaType("sa.BigInteger()", None)
    case CanonicalType.Serial8             => SaType("sa.BigInteger()", None)
    case CanonicalType.Float8              => SaType("sa.Float()", None)
    case CanonicalType.Bool                => SaType("sa.Boolean()", None)
    case CanonicalType.Timestamptz         => SaType("sa.DateTime()", None)
    case CanonicalType.DateOnly            => SaType("sa.Date()", None)
    case CanonicalType.Uuid                => SaType("sa.Uuid()", None)
    case CanonicalType.Numeric(p, Some(s)) => SaType(s"sa.Numeric($p, $s)", None)
    case CanonicalType.Numeric(p, None)    => SaType(s"sa.Numeric($p)", None)
    case CanonicalType.Bytes               => SaType("sa.LargeBinary()", None)
    case CanonicalType.Json                => SaType("sa.JSON()", None)

  def renderTrigger(t: TriggerSpec): TriggerEmission = Dialect.perEventTriggerEmission(t)

  def partialIndex(ix: IndexSpec): FeatureEmission[String] =
    ix.filterClause match
      case Some(f) =>
        FeatureEmission(
          "",
          List(
            ConventionDiagnostic(
              DiagnosticLevel.Warning,
              s"partial index '${ix.name}' (WHERE $f) emitted as a plain index: " +
                "MySQL does not support partial indexes",
              None,
              ix.name,
              "partial_index"
            )
          )
        )
      case None => FeatureEmission("", Nil)
