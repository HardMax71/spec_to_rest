package specrest.profile

object GoChiPostgres:

  private val PrimitiveTypeMap: Map[String, TypeMapping] = Map(
    "String"   -> TypeMapping("string", "string", "TEXT"),
    "Int"      -> TypeMapping("int64", "int64", "BIGINT"),
    "Float"    -> TypeMapping("float64", "float64", "DOUBLE PRECISION"),
    "Bool"     -> TypeMapping("bool", "bool", "BOOLEAN"),
    "Boolean"  -> TypeMapping("bool", "bool", "BOOLEAN"),
    "DateTime" -> TypeMapping("time.Time", "time.Time", "TIMESTAMPTZ"),
    "Date"     -> TypeMapping("time.Time", "time.Time", "DATE"),
    "UUID"     -> TypeMapping("uuid.UUID", "uuid.UUID", "UUID"),
    "Decimal"  -> TypeMapping("decimal.Decimal", "decimal.Decimal", "NUMERIC"),
    "Bytes"    -> TypeMapping("[]byte", "[]byte", "BYTEA"),
    "Money"    -> TypeMapping("int64", "int64", "BIGINT")
  )

  val profile: DeploymentProfile = DeploymentProfile(
    name = "go-chi-postgres",
    displayName = "Go + chi + PostgreSQL",
    language = "go",
    framework = "chi",
    database = "postgres",
    orm = "pgx",
    migrationTool = "golang-migrate",
    validation = "go-playground/validator",
    packageManager = "go",
    httpServer = "net/http",
    dbDriver = "pgx",
    async = false,
    fileNaming = NamingStyle.SnakeCase,
    classNaming = NamingStyle.PascalCase,
    fieldNaming = NamingStyle.PascalCase,
    typeMap = PrimitiveTypeMap,
    dependencies = List(
      DependencySpec("github.com/go-chi/chi/v5", "v5.1.0"),
      DependencySpec("github.com/jackc/pgx/v5", "v5.7.1"),
      DependencySpec("github.com/google/uuid", "v1.6.0"),
      DependencySpec("github.com/shopspring/decimal", "v1.4.0"),
      DependencySpec("github.com/caarlos0/env/v11", "v11.2.0"),
      DependencySpec("github.com/go-playground/validator/v10", "v10.22.1")
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
