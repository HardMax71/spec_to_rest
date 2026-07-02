package specrest.profile

enum LanguageId(val slug: String) derives CanEqual:
  case Python extends LanguageId("python")
  case Go     extends LanguageId("go")
  case Ts     extends LanguageId("ts")

object LanguageId:
  def parse(s: String): Option[LanguageId] = values.find(_.slug == s)

enum DatabaseId(val slug: String) derives CanEqual:
  case Postgres extends DatabaseId("postgres")
  case Sqlite   extends DatabaseId("sqlite")
  case Mysql    extends DatabaseId("mysql")

object DatabaseId:
  def parse(s: String): Option[DatabaseId] = values.find(_.slug == s)

  def display(d: DatabaseId): String = d match
    case Postgres => "PostgreSQL"
    case Sqlite   => "SQLite"
    case Mysql    => "MySQL"

final case class TargetKey(language: LanguageId, framework: String, database: DatabaseId)
    derives CanEqual:
  def slug: String           = s"${language.slug}-$framework-${database.slug}"
  def segments: List[String] = List(language.slug, framework, database.slug)
  def frameworkPath: String  = TargetKey.frameworkPath(language, framework)
  def layoutPath: String     = segments.mkString("/")

object TargetKey:
  def frameworkPath(language: LanguageId, framework: String): String =
    s"${language.slug}/$framework"

  def parse(slug: String): Either[String, TargetKey] =
    val parts = slug.split("-").toList
    if parts.length < 3 then
      Left(s"malformed target '$slug' (expected language-framework-database)")
    else
      val fw = parts.slice(1, parts.length - 1).mkString("-")
      (LanguageId.parse(parts.head), DatabaseId.parse(parts.last)) match
        case (Some(l), Some(d)) => Right(TargetKey(l, fw, d))
        case _ =>
          Left(s"unrecognised language or database in target '$slug'")

trait Framework:
  def id: String
  def supportedLanguages: Set[LanguageId]
  def supportedDialects: Set[DatabaseId]
  def supportsTestgen(database: DatabaseId): Boolean = false
  def profile(language: LanguageId, database: DatabaseId): DeploymentProfile

object Fastapi extends Framework:
  val id                                  = "fastapi"
  val supportedLanguages: Set[LanguageId] = Set(LanguageId.Python)
  val supportedDialects: Set[DatabaseId] =
    Set(DatabaseId.Postgres, DatabaseId.Sqlite, DatabaseId.Mysql)

  override def supportsTestgen(database: DatabaseId): Boolean = true

  def profile(language: LanguageId, database: DatabaseId): DeploymentProfile =
    val (driver, driverDep) = database match
      case DatabaseId.Postgres => ("asyncpg", DependencySpec("asyncpg", ">=0.30"))
      case DatabaseId.Sqlite   => ("aiosqlite", DependencySpec("aiosqlite", ">=0.20"))
      case DatabaseId.Mysql    => ("aiomysql", DependencySpec("aiomysql", ">=0.2"))
    DeploymentProfile(
      name = TargetKey(language, id, database).slug,
      displayName = s"Python + FastAPI + ${DatabaseId.display(database)}",
      language = language.slug,
      framework = id,
      database = database.slug,
      orm = "sqlalchemy",
      migrationTool = "alembic",
      validation = "pydantic",
      packageManager = "uv",
      httpServer = "uvicorn",
      httpPort = 8000,
      dbDriver = driver,
      async = true,
      fileNaming = NamingStyle.SnakeCase,
      classNaming = NamingStyle.PascalCase,
      fieldNaming = NamingStyle.SnakeCase,
      typeMap = TypeMap.PythonPrimitives,
      dependencies = List(
        DependencySpec("fastapi", ">=0.115"),
        DependencySpec("uvicorn[standard]", ">=0.34"),
        DependencySpec("sqlalchemy", ">=2.0"),
        driverDep,
        DependencySpec("alembic", ">=1.14"),
        DependencySpec("pydantic-settings", ">=2.0"),
        DependencySpec("structlog", ">=24,<26"),
        DependencySpec("prometheus-client", ">=0.21")
      ),
      devDependencies = List(
        DependencySpec("pytest", ">=8.0"),
        DependencySpec("pytest-asyncio", ">=0.24"),
        DependencySpec("httpx", ">=0.28"),
        DependencySpec("ruff", ">=0.8"),
        DependencySpec("mypy", ">=1.13")
      ),
      pythonVersion = ">=3.10",
      directories = List(
        "app",
        "app/models",
        "app/schemas",
        "app/routers",
        "app/services",
        "alembic",
        "alembic/versions",
        "tests"
      )
    )

object Chi extends Framework:
  val id                                  = "chi"
  val supportedLanguages: Set[LanguageId] = Set(LanguageId.Go)
  val supportedDialects: Set[DatabaseId] =
    Set(DatabaseId.Postgres, DatabaseId.Sqlite, DatabaseId.Mysql)
  override def supportsTestgen(database: DatabaseId): Boolean = true

  private def driverDep(database: DatabaseId): DependencySpec = database match
    case DatabaseId.Postgres =>
      DependencySpec("github.com/uptrace/bun/driver/pgdriver", "v1.2.18")
    case DatabaseId.Sqlite =>
      DependencySpec("github.com/uptrace/bun/driver/sqliteshim", "v1.2.18")
    case DatabaseId.Mysql =>
      DependencySpec("github.com/go-sql-driver/mysql", "v1.10.0")

  def profile(language: LanguageId, database: DatabaseId): DeploymentProfile =
    DeploymentProfile(
      name = TargetKey(language, id, database).slug,
      displayName = s"Go + chi + ${DatabaseId.display(database)}",
      language = language.slug,
      framework = id,
      database = database.slug,
      orm = "bun",
      migrationTool = "golang-migrate",
      validation = "go-playground/validator",
      packageManager = "go",
      httpServer = "net/http",
      httpPort = 8080,
      dbDriver = "bun",
      async = false,
      fileNaming = NamingStyle.SnakeCase,
      classNaming = NamingStyle.PascalCase,
      fieldNaming = NamingStyle.PascalCase,
      typeMap = TypeMap.GoPrimitives,
      dependencies = List(
        DependencySpec("github.com/go-chi/chi/v5", "v5.1.0"),
        DependencySpec("github.com/uptrace/bun", "v1.2.18"),
        driverDep(database),
        DependencySpec("github.com/google/uuid", "v1.6.0"),
        DependencySpec("github.com/shopspring/decimal", "v1.4.0"),
        DependencySpec("github.com/caarlos0/env/v11", "v11.2.0"),
        DependencySpec("github.com/go-playground/validator/v10", "v10.22.1"),
        DependencySpec("github.com/prometheus/client_golang", "v1.20.5")
      ),
      devDependencies = List(
        DependencySpec("github.com/stretchr/testify", "v1.9.0")
      ),
      pythonVersion = "",
      directories = List(
        "cmd",
        "cmd/server",
        "internal",
        "internal/config",
        "internal/database",
        "internal/models",
        "internal/handlers",
        "internal/services",
        "internal/validators",
        "migrations",
        "tests"
      )
    )

object Express extends Framework:
  val id                                  = "express"
  val supportedLanguages: Set[LanguageId] = Set(LanguageId.Ts)
  val supportedDialects: Set[DatabaseId] =
    Set(DatabaseId.Postgres, DatabaseId.Sqlite, DatabaseId.Mysql)

  override def supportsTestgen(database: DatabaseId): Boolean = true

  def profile(language: LanguageId, database: DatabaseId): DeploymentProfile =
    DeploymentProfile(
      name = TargetKey(language, id, database).slug,
      displayName = s"TypeScript + Express + Prisma + ${DatabaseId.display(database)}",
      language = language.slug,
      framework = id,
      database = database.slug,
      orm = "prisma",
      migrationTool = "prisma-migrate",
      validation = "zod",
      packageManager = "npm",
      httpServer = "express",
      httpPort = 8080,
      dbDriver = "@prisma/client",
      async = true,
      fileNaming = NamingStyle.CamelCase,
      classNaming = NamingStyle.PascalCase,
      fieldNaming = NamingStyle.CamelCase,
      typeMap = TypeMap.TsPrimitives,
      dependencies = List(
        DependencySpec("express", "^4.21.2"),
        DependencySpec("@prisma/client", "^6.2.0"),
        DependencySpec("zod", "^3.23.8"),
        DependencySpec("dotenv", "^16.4.7"),
        DependencySpec("prom-client", "^15.1.3")
      ),
      devDependencies = List(
        DependencySpec("typescript", "^5.6.3"),
        DependencySpec("@types/node", "^22.10.1"),
        DependencySpec("@types/express", "^4.17.21"),
        DependencySpec("prisma", "^6.2.0"),
        DependencySpec("vitest", "^2.1.8"),
        DependencySpec("supertest", "^7.0.0"),
        DependencySpec("@types/supertest", "^6.0.2"),
        DependencySpec("tsx", "^4.19.2")
      ),
      pythonVersion = "",
      directories = List(
        "src",
        "src/middleware",
        "src/routes",
        "src/services",
        "src/schemas",
        "src/types",
        "prisma",
        "tests"
      )
    )
