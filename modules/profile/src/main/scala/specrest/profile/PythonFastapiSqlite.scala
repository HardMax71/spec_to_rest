package specrest.profile

object PythonFastapiSqlite:

  val profile: DeploymentProfile =
    PythonFastapi.profile.copy(
      name = "python-fastapi-sqlite",
      displayName = "Python + FastAPI + SQLite",
      database = "sqlite",
      dbDriver = "aiosqlite",
      dependencies = PythonFastapi.profile.dependencies.map {
        case DependencySpec("asyncpg", _) => DependencySpec("aiosqlite", ">=0.20")
        case other                        => other
      }
    )
