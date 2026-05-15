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

final case class ComposeEnv(key: String, value: String) derives CanEqual

final case class DialectView(
    id: String,
    hasDbService: Boolean,
    needsFkPragma: Boolean,
    envUrl: String,
    localUrl: String,
    ciUrl: String,
    dbImage: String,
    dbPort: String,
    dbVolumePath: String,
    dbHealthCmd: String,
    engineArgs: String,
    composeEnv: List[ComposeEnv]
) derives CanEqual

trait Dialect:
  def id: String
  def caps: DialectCaps
  def saType(t: CanonicalType): SaType
  def renderTrigger(t: TriggerSpec): TriggerEmission
  def partialIndex(ix: IndexSpec): FeatureEmission[String]

  /** Service-name-parameterised deployment facts (connection URLs, docker-compose db service,
    * FK-pragma need). The single source of truth for the database axis — `DialectView.of`-style
    * enumeration must not be duplicated elsewhere; resolve via `Dialect.forDatabase` and call this.
    */
  def deployment(snake: String): DialectView

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

  def deployment(snake: String): DialectView = DialectView(
    id = "postgres",
    hasDbService = true,
    needsFkPragma = false,
    envUrl = s"postgresql+asyncpg://$snake:$snake@db:5432/$snake",
    localUrl = s"postgresql+asyncpg://$snake:$snake@localhost:5432/$snake",
    ciUrl = s"postgresql+asyncpg://$snake:$snake@localhost:5432/$snake",
    dbImage = "postgres:17-alpine",
    dbPort = "5432",
    dbVolumePath = "/var/lib/postgresql/data",
    dbHealthCmd = s"pg_isready -U $snake",
    engineArgs = "    pool_size=10,\n    max_overflow=20,\n    pool_pre_ping=True,",
    composeEnv = List(
      ComposeEnv("POSTGRES_USER", snake),
      ComposeEnv("POSTGRES_PASSWORD", snake),
      ComposeEnv("POSTGRES_DB", snake)
    )
  )

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

  def deployment(snake: String): DialectView = DialectView(
    id = "sqlite",
    hasDbService = false,
    needsFkPragma = true,
    envUrl = s"sqlite+aiosqlite:////data/$snake.db",
    localUrl = s"sqlite+aiosqlite:///./$snake.db",
    ciUrl = s"sqlite+aiosqlite:///./$snake.db",
    dbImage = "",
    dbPort = "",
    dbVolumePath = "",
    dbHealthCmd = "",
    engineArgs = """    connect_args={"check_same_thread": False},""",
    composeEnv = Nil
  )

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

  def deployment(snake: String): DialectView = DialectView(
    id = "mysql",
    hasDbService = true,
    needsFkPragma = false,
    envUrl = s"mysql+aiomysql://$snake:$snake@db:3306/$snake",
    localUrl = s"mysql+aiomysql://$snake:$snake@localhost:3306/$snake",
    ciUrl = s"mysql+aiomysql://$snake:$snake@127.0.0.1:3306/$snake",
    dbImage = "mysql:8.4",
    dbPort = "3306",
    dbVolumePath = "/var/lib/mysql",
    dbHealthCmd = s"mysqladmin ping -h 127.0.0.1 -u $snake -p$snake --silent",
    engineArgs = "    pool_size=10,\n    max_overflow=20,\n    pool_pre_ping=True,",
    composeEnv = List(
      ComposeEnv("MYSQL_USER", snake),
      ComposeEnv("MYSQL_PASSWORD", snake),
      ComposeEnv("MYSQL_DATABASE", snake),
      ComposeEnv("MYSQL_ROOT_PASSWORD", s"${snake}_root")
    )
  )
