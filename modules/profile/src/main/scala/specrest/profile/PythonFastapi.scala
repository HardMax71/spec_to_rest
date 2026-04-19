package specrest.profile

object PythonFastapi:

  private val PrimitiveTypeMap: Map[String, TypeMapping] = Map(
    "String"   -> TypeMapping("str", "str", "String"),
    "Int"      -> TypeMapping("int", "int", "Integer"),
    "Float"    -> TypeMapping("float", "float", "Float"),
    "Bool"     -> TypeMapping("bool", "bool", "Boolean"),
    "Boolean"  -> TypeMapping("bool", "bool", "Boolean"),
    "DateTime" -> TypeMapping("datetime", "datetime", "DateTime"),
    "Date"     -> TypeMapping("date", "date", "Date"),
    "UUID"     -> TypeMapping("UUID", "UUID", "Uuid"),
    "Decimal"  -> TypeMapping("Decimal", "Decimal", "Numeric"),
    "Bytes"    -> TypeMapping("bytes", "bytes", "LargeBinary"),
    "Money"    -> TypeMapping("int", "int", "Integer")
  )

  val profile: DeploymentProfile = DeploymentProfile(
    name = "python-fastapi-postgres",
    displayName = "Python + FastAPI + PostgreSQL",
    language = "python",
    framework = "fastapi",
    database = "postgres",
    orm = "sqlalchemy",
    migrationTool = "alembic",
    validation = "pydantic",
    packageManager = "uv",
    httpServer = "uvicorn",
    dbDriver = "asyncpg",
    async = true,
    fileNaming = NamingStyle.SnakeCase,
    classNaming = NamingStyle.PascalCase,
    fieldNaming = NamingStyle.SnakeCase,
    typeMap = PrimitiveTypeMap,
    dependencies = List(
      DependencySpec("fastapi", ">=0.115"),
      DependencySpec("uvicorn[standard]", ">=0.34"),
      DependencySpec("sqlalchemy", ">=2.0"),
      DependencySpec("asyncpg", ">=0.30"),
      DependencySpec("alembic", ">=1.14"),
      DependencySpec("pydantic-settings", ">=2.0")
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
    ),
    modelDir = "app/models",
    schemaDir = "app/schemas",
    routerDir = "app/routers"
  )
