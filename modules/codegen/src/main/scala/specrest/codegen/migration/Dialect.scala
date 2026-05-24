package specrest.codegen.migration

import specrest.convention.ConventionDiagnostic
import specrest.convention.DiagnosticLevel
import specrest.ir.generated.SpecRestGenerated.*

final case class DialectCaps(
    supportsPartialIndex: Boolean,
    supportsCheckConstraint: Boolean,
    supportsCheckOnAutoIncrement: Boolean,
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
    composeEnv: List[ComposeEnv],
    dsnRecipe: Option[specrest.codegen.Dsn.Recipe]
) derives CanEqual

trait Dialect:
  def id: String
  def caps: DialectCaps
  def saType(t: CanonicalType): SaType
  def renderTrigger(t: trigger_spec): TriggerEmission
  def rawTrigger(t: trigger_spec): TriggerEmission

  /** SQL for a regex CHECK predicate. Postgres `~`, MySQL `REGEXP`; SQLite has no regex in a CHECK
    * (no `~`/REGEXP, no loadable extension at migrate time) so it returns None — the check is
    * dropped for SQLite (regex stays enforced via the other dialects / app layer).
    */
  def regexCheck(column: String, pattern: String): Option[String]
  def partialIndex(ix: index_spec): FeatureEmission[String]

  /** Prisma attribute that makes a primary key DB-generated, emitted by the ts-express schema only
    * for an auto-increment PK (`CanonicalType.isAutoIncrement`). Connector-agnostic for
    * postgres/mysql/sqlite/sqlserver, so the default fits every dialect added so far; a future
    * CockroachDB dialect overrides it (that connector needs `@default(sequence())` + `BigInt`).
    */
  def prismaAutoIncrement: String = "@default(autoincrement())"

  /** Service-name-parameterised deployment facts (connection URLs, docker-compose db service,
    * FK-pragma need). The single source of truth for the database axis — `DialectView.of`-style
    * enumeration must not be duplicated elsewhere; resolve via `Dialect.forDatabase` and call this.
    */
  def deployment(snake: String): DialectView

  /** Raw-SQL DDL facet, used by the golang-migrate / Prisma `migration.sql` path (`SqlRenderer`).
    * Postgres returns the schema's column type verbatim so the existing postgres goldens are
    * byte-identical; sqlite/mysql remap via `CanonicalType`. `serialColumnDef` renders the
    * auto-increment primary key column inline; `sqlType` carries the source width (`SERIAL` vs
    * `BIGSERIAL`) so 32-bit serials are not silently widened to 64-bit. `serialUsesSeparatePk` is
    * false where the dialect requires the pk declared on the column itself (SQLite rowid alias).
    */
  def sqlColumnType(sqlType: String): String
  def sqlServerDefault(expr: String): String

  /** Column DEFAULT for the Alembic (`server_default=`) path. Differs from `sqlServerDefault`
    * because the Alembic renderer maps `NOW()` to the dialect-portable `sa.func.now()` itself, so
    * this must NOT rewrite `NOW()` — it only normalizes dialect-incompatible literals (e.g. strips
    * the Postgres `::jsonb` cast, and renders MySQL JSON defaults as a parenthesised expression).
    */
  def alembicServerDefault(expr: String): String

  def serialColumnDef(name: String, sqlType: String): String
  def serialUsesSeparatePk: Boolean

  def schemaDiagnostics(schema: database_schema): List[ConventionDiagnostic] =
    schema_tables(schema).flatMap(table_indexes).flatMap(ix => partialIndex(ix).diagnostics)

object Dialect:
  def forDatabase(database: String): Dialect = database match
    case "postgres" => Postgres
    case "sqlite"   => Sqlite
    case "mysql"    => Mysql
    case other =>
      throw new RuntimeException(
        s"No SQL dialect registered for database '$other' (known: postgres, sqlite, mysql)"
      )

  private def recompute(t: trigger_spec, rowRef: String): String =
    val agg      = TriggerSql.aggregateExpr(t)
    val tgtTable = trigger_target_table(t)
    val tgtCol   = trigger_target_column(t)
    val srcTable = trigger_source_table(t)
    val srcFk    = trigger_source_foreign_key(t)
    s"UPDATE $tgtTable SET $tgtCol = " +
      s"(SELECT $agg FROM $srcTable " +
      s"WHERE $srcFk = $rowRef.$srcFk) " +
      s"WHERE id = $rowRef.$srcFk;"

  private def createTrigger(name: String, event: String, src: String, body: String): String =
    s"CREATE TRIGGER $name AFTER $event ON $src FOR EACH ROW BEGIN $body END"

  /** SQLite and MySQL share the same shape: one single-statement BEGIN/END trigger per row event
    * (no stored function, no PL/pgSQL). UPDATE recomputes both the old and new parent in case the
    * foreign key moved between parents.
    */
  private def perEventTriggers(t: trigger_spec): List[(String, String)] =
    val nm  = trigger_name(t)
    val src = trigger_source_table(t)
    List(
      s"${nm}_ins" -> createTrigger(
        s"${nm}_ins",
        "INSERT",
        src,
        recompute(t, "NEW")
      ),
      s"${nm}_upd" -> createTrigger(
        s"${nm}_upd",
        "UPDATE",
        src,
        recompute(t, "OLD") + " " + recompute(t, "NEW")
      ),
      s"${nm}_del" -> createTrigger(
        s"${nm}_del",
        "DELETE",
        src,
        recompute(t, "OLD")
      )
    )

  def perEventTriggerEmission(t: trigger_spec): TriggerEmission =
    val triggers = perEventTriggers(t)
    TriggerEmission(
      upgrade = triggers.map((_, sql) => s"op.execute(${AlembicSyntax.tripleQuoted(sql)})"),
      downgrade = triggers.map((name, _) => s"""op.execute("DROP TRIGGER IF EXISTS $name;")""")
    )

  /** Raw-SQL (non-Alembic) per-event triggers for the go/ts migration path. */
  def perEventRawTrigger(t: trigger_spec): TriggerEmission =
    val triggers = perEventTriggers(t)
    TriggerEmission(
      upgrade = triggers.map((_, sql) => s"$sql;"),
      downgrade = triggers.reverse.map((name, _) => s"DROP TRIGGER IF EXISTS $name;")
    )

  private[migration] def normalizeNow(expr: String): String =
    if expr.trim.equalsIgnoreCase("NOW()") then "CURRENT_TIMESTAMP" else expr

  /** Strip a trailing PostgreSQL `::type` cast (`'[]'::jsonb` -> `'[]'`). Anchored at end so a `::`
    * inside a quoted literal is preserved; covers `jsonb`, `text`, `varchar(255)`, `int[]`.
    */
  private[migration] def stripPgCast(expr: String): String =
    expr.replaceAll("""::\w+(\(\d+(,\s*\d+)?\))?(\[\])?\s*$""", "")

  /** MySQL JSON/TEXT/BLOB columns reject literal DEFAULTs; the canonical collection/map defaults
    * (`'[]'::jsonb` / `'{}'::jsonb`) must be emitted as a parenthesised expression default.
    */
  private[migration] def mysqlCollectionDefault(expr: String): Option[String] =
    expr.trim match
      case "'[]'::jsonb" => Some("(JSON_ARRAY())")
      case "'{}'::jsonb" => Some("(JSON_OBJECT())")
      case _             => None

  private[migration] def isSerial4(sqlType: String): Boolean =
    CanonicalType.parse(sqlType) match
      case Some(CanonicalType.Serial4) => true
      case _                           => false

  private[migration] def sqliteType(sqlType: String): String =
    CanonicalType.parse(sqlType) match
      case Some(CanonicalType.Text)                => "TEXT"
      case Some(CanonicalType.Varchar(_))          => "TEXT"
      case Some(CanonicalType.Int4)                => "INTEGER"
      case Some(CanonicalType.Serial4)             => "INTEGER"
      case Some(CanonicalType.Int8)                => "INTEGER"
      case Some(CanonicalType.Serial8)             => "INTEGER"
      case Some(CanonicalType.Float8)              => "REAL"
      case Some(CanonicalType.Bool)                => "BOOLEAN"
      case Some(CanonicalType.Timestamptz)         => "DATETIME"
      case Some(CanonicalType.DateOnly)            => "DATE"
      case Some(CanonicalType.Uuid)                => "TEXT"
      case Some(CanonicalType.Numeric(p, Some(s))) => s"NUMERIC($p, $s)"
      case Some(CanonicalType.Numeric(p, None))    => s"NUMERIC($p)"
      case Some(CanonicalType.Bytes)               => "BLOB"
      case Some(CanonicalType.Json)                => "TEXT"
      case None                                    => sqlType

  private[migration] def mysqlType(sqlType: String): String =
    CanonicalType.parse(sqlType) match
      case Some(CanonicalType.Text)                => "VARCHAR(255)"
      case Some(CanonicalType.Varchar(n))          => s"VARCHAR($n)"
      case Some(CanonicalType.Int4)                => "INT"
      case Some(CanonicalType.Serial4)             => "INT"
      case Some(CanonicalType.Int8)                => "BIGINT"
      case Some(CanonicalType.Serial8)             => "BIGINT"
      case Some(CanonicalType.Float8)              => "DOUBLE"
      case Some(CanonicalType.Bool)                => "TINYINT(1)"
      case Some(CanonicalType.Timestamptz)         => "DATETIME"
      case Some(CanonicalType.DateOnly)            => "DATE"
      case Some(CanonicalType.Uuid)                => "CHAR(36)"
      case Some(CanonicalType.Numeric(p, Some(s))) => s"DECIMAL($p, $s)"
      case Some(CanonicalType.Numeric(p, None))    => s"DECIMAL($p)"
      case Some(CanonicalType.Bytes)               => "LONGBLOB"
      case Some(CanonicalType.Json)                => "JSON"
      case None                                    => sqlType

  def hasPostgresDialectTypes(ops: List[migration_op], dialect: Dialect = Postgres): Boolean =
    def needsDialectImport(sqlType: String): Boolean =
      CanonicalType.parse(sqlType).exists(dialect.saType(_).importModule.isDefined)
    ops.exists:
      case CreateTable(t)   => table_columns(t).exists(c => needsDialectImport(column_sql_type(c)))
      case DropTable(t)     => table_columns(t).exists(c => needsDialectImport(column_sql_type(c)))
      case AddColumn(_, c)  => needsDialectImport(column_sql_type(c))
      case DropColumn(_, c) => needsDialectImport(column_sql_type(c))
      case AlterColumnType(_, _, o, n) =>
        needsDialectImport(o) || needsDialectImport(n)
      case _ => false

object Postgres extends Dialect:
  val id = "postgres"

  val caps: DialectCaps = DialectCaps(
    supportsPartialIndex = true,
    supportsCheckConstraint = true,
    supportsCheckOnAutoIncrement = true,
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

  def renderTrigger(t: trigger_spec): TriggerEmission =
    TriggerEmission(
      upgrade = List(
        s"op.execute(${AlembicSyntax.tripleQuoted(TriggerSql.functionBody(t))})",
        s"op.execute(${AlembicSyntax.tripleQuoted(TriggerSql.triggerStatement(t))})"
      ),
      downgrade = TriggerSql.dropStatements(t).map(stmt => s"""op.execute("$stmt")""")
    )

  def rawTrigger(t: trigger_spec): TriggerEmission =
    TriggerEmission(
      upgrade = List(TriggerSql.functionBody(t), TriggerSql.triggerStatement(t)),
      downgrade = TriggerSql.dropStatements(t)
    )

  def regexCheck(column: String, pattern: String): Option[String] =
    Some(s"$column ~ '$pattern'")

  def partialIndex(ix: index_spec): FeatureEmission[String] =
    index_filter_clause(ix) match
      case Some(f) =>
        FeatureEmission(s", postgresql_where=sa.text(${AlembicSyntax.pythonStringLiteral(f)})", Nil)
      case None => FeatureEmission("", Nil)

  def deployment(snake: String): DialectView =
    val recipe = specrest.codegen.Dsn.Recipe(
      spec = specrest.codegen.Dsn.Spec(
        shape = specrest.codegen.Dsn.Shape.Url("postgresql+asyncpg"),
        port = 5432
      ),
      secrets = specrest.codegen.Dsn.Secrets("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")
    )
    DialectView(
      id = "postgres",
      hasDbService = true,
      needsFkPragma = false,
      envUrl = specrest.codegen.Dsn.renderDev(recipe, host = "db", snake),
      localUrl = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
      ciUrl = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
      dbImage = "postgres:17-alpine",
      dbPort = "5432",
      dbVolumePath = "/var/lib/postgresql/data",
      dbHealthCmd = s"pg_isready -U $snake",
      engineArgs = "    pool_size=10,\n    max_overflow=20,\n    pool_pre_ping=True,",
      composeEnv = List(
        ComposeEnv(recipe.secrets.userKey, snake),
        ComposeEnv(recipe.secrets.passwordKey, snake),
        ComposeEnv(recipe.secrets.dbKey, snake)
      ),
      dsnRecipe = Some(recipe)
    )

  def sqlColumnType(sqlType: String): String     = sqlType
  def sqlServerDefault(expr: String): String     = expr
  def alembicServerDefault(expr: String): String = expr
  def serialColumnDef(name: String, sqlType: String): String =
    if Dialect.isSerial4(sqlType) then s"$name SERIAL NOT NULL" else s"$name BIGSERIAL NOT NULL"
  def serialUsesSeparatePk: Boolean = true

object Sqlite extends Dialect:
  val id = "sqlite"

  val caps: DialectCaps = DialectCaps(
    supportsPartialIndex = true,
    supportsCheckConstraint = true,
    supportsCheckOnAutoIncrement = true,
    fkEnforcedByDefault = false,
    requiresTextIndexPrefix = false,
    transactionalDdl = true
  )

  def saType(t: CanonicalType): SaType = t match
    case CanonicalType.Text       => SaType("sa.Text()", None)
    case CanonicalType.Varchar(n) => SaType(s"sa.String(length=$n)", None)
    case CanonicalType.Int4       => SaType("sa.Integer()", None)
    case CanonicalType.Serial4    => SaType("sa.Integer()", None)
    case CanonicalType.Int8       => SaType("sa.BigInteger()", None)
    // SQLite autoincrements only the INTEGER PRIMARY KEY rowid alias; a BIGINT PK is
    // not that alias, so a 64-bit serial must map to INTEGER (rowid is already 64-bit).
    case CanonicalType.Serial8             => SaType("sa.Integer()", None)
    case CanonicalType.Float8              => SaType("sa.Float()", None)
    case CanonicalType.Bool                => SaType("sa.Boolean()", None)
    case CanonicalType.Timestamptz         => SaType("sa.DateTime()", None)
    case CanonicalType.DateOnly            => SaType("sa.Date()", None)
    case CanonicalType.Uuid                => SaType("sa.Uuid()", None)
    case CanonicalType.Numeric(p, Some(s)) => SaType(s"sa.Numeric($p, $s)", None)
    case CanonicalType.Numeric(p, None)    => SaType(s"sa.Numeric($p)", None)
    case CanonicalType.Bytes               => SaType("sa.LargeBinary()", None)
    case CanonicalType.Json                => SaType("sa.JSON()", None)

  def renderTrigger(t: trigger_spec): TriggerEmission = Dialect.perEventTriggerEmission(t)
  def rawTrigger(t: trigger_spec): TriggerEmission    = Dialect.perEventRawTrigger(t)

  def regexCheck(column: String, pattern: String): Option[String] = None

  def partialIndex(ix: index_spec): FeatureEmission[String] =
    index_filter_clause(ix) match
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
    composeEnv = Nil,
    dsnRecipe = None
  )

  def sqlColumnType(sqlType: String): String = Dialect.sqliteType(sqlType)
  def sqlServerDefault(expr: String): String =
    Dialect.normalizeNow(Dialect.stripPgCast(expr))
  def alembicServerDefault(expr: String): String = Dialect.stripPgCast(expr)
  def serialColumnDef(name: String, @scala.annotation.unused sqlType: String): String =
    s"$name INTEGER PRIMARY KEY AUTOINCREMENT"
  def serialUsesSeparatePk: Boolean = false

object Mysql extends Dialect:
  val id = "mysql"

  val caps: DialectCaps = DialectCaps(
    supportsPartialIndex = false,
    supportsCheckConstraint = true,
    supportsCheckOnAutoIncrement = false,
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

  def renderTrigger(t: trigger_spec): TriggerEmission = Dialect.perEventTriggerEmission(t)
  def rawTrigger(t: trigger_spec): TriggerEmission    = Dialect.perEventRawTrigger(t)

  def regexCheck(column: String, pattern: String): Option[String] =
    Some(s"$column REGEXP '$pattern'")

  def partialIndex(ix: index_spec): FeatureEmission[String] =
    index_filter_clause(ix) match
      case Some(f) =>
        FeatureEmission(
          "",
          List(
            ConventionDiagnostic(
              DiagnosticLevel.Warning,
              s"partial index '${index_name(ix)}' (WHERE $f) emitted as a plain index: " +
                "MySQL does not support partial indexes",
              None,
              index_name(ix),
              "partial_index"
            )
          )
        )
      case None => FeatureEmission("", Nil)

  def deployment(snake: String): DialectView =
    val recipe = specrest.codegen.Dsn.Recipe(
      spec = specrest.codegen.Dsn.Spec(
        shape = specrest.codegen.Dsn.Shape.Url("mysql+aiomysql"),
        port = 3306
      ),
      secrets = specrest.codegen.Dsn.Secrets("MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_DATABASE")
    )
    DialectView(
      id = "mysql",
      hasDbService = true,
      needsFkPragma = false,
      envUrl = specrest.codegen.Dsn.renderDev(recipe, host = "db", snake),
      localUrl = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
      ciUrl = specrest.codegen.Dsn.renderDev(recipe, host = "127.0.0.1", snake),
      dbImage = "mysql:8.4",
      dbPort = "3306",
      dbVolumePath = "/var/lib/mysql",
      dbHealthCmd = s"mysqladmin ping -h 127.0.0.1 -u $snake -p$snake --silent",
      engineArgs = "    pool_size=10,\n    max_overflow=20,\n    pool_pre_ping=True,",
      composeEnv = List(
        ComposeEnv(recipe.secrets.userKey, snake),
        ComposeEnv(recipe.secrets.passwordKey, snake),
        ComposeEnv(recipe.secrets.dbKey, snake),
        ComposeEnv("MYSQL_ROOT_PASSWORD", s"${snake}_root")
      ),
      dsnRecipe = Some(recipe)
    )

  def sqlColumnType(sqlType: String): String = Dialect.mysqlType(sqlType)
  def sqlServerDefault(expr: String): String =
    Dialect
      .mysqlCollectionDefault(expr)
      .getOrElse(Dialect.normalizeNow(Dialect.stripPgCast(expr)))
  def alembicServerDefault(expr: String): String =
    Dialect.mysqlCollectionDefault(expr).getOrElse(Dialect.stripPgCast(expr))
  def serialColumnDef(name: String, sqlType: String): String =
    if Dialect.isSerial4(sqlType) then s"$name INT NOT NULL AUTO_INCREMENT"
    else s"$name BIGINT NOT NULL AUTO_INCREMENT"
  def serialUsesSeparatePk: Boolean = true
