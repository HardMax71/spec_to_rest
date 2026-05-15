package specrest.profile

object PythonFastapiMysql:

  val profile: DeploymentProfile =
    PythonFastapi.profile.copy(
      name = "python-fastapi-mysql",
      displayName = "Python + FastAPI + MySQL",
      database = "mysql",
      dbDriver = "aiomysql",
      dependencies = PythonFastapi.profile.dependencies.map {
        case DependencySpec("asyncpg", _) => DependencySpec("aiomysql", ">=0.2")
        case other                        => other
      }
    )
