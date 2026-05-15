package specrest.codegen

import specrest.convention.DatabaseSchema
import specrest.convention.EndpointSpec
import specrest.profile.DependencySpec
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledOperation

final case class TypeMappingEntry(
    specType: String,
    domain: String,
    validation: String,
    ormColumn: String
)

final case class RenderProfile(
    name: String,
    displayName: String,
    language: String,
    framework: String,
    database: String,
    orm: String,
    migrationTool: String,
    validation: String,
    packageManager: String,
    httpServer: String,
    dbDriver: String,
    async: Boolean,
    pythonVersion: String,
    directories: List[String],
    typeMap: List[TypeMappingEntry],
    dependencies: List[DependencySpec],
    devDependencies: List[DependencySpec]
)

final case class ServiceNames(name: String, snakeName: String, kebabName: String)

final case class ComposeEnv(key: String, value: String)

final case class DialectView(
    id: String,
    isPostgres: Boolean,
    isSqlite: Boolean,
    isMysql: Boolean,
    hasDbService: Boolean,
    needsFkPragma: Boolean,
    envUrl: String,
    localUrl: String,
    ciUrl: String,
    dbImage: String,
    dbPort: String,
    dbVolumePath: String,
    dbHealthCmd: String,
    composeEnv: List[ComposeEnv]
)

object DialectView:
  def of(database: String, snake: String): DialectView = database match
    case "sqlite" =>
      DialectView(
        id = "sqlite",
        isPostgres = false,
        isSqlite = true,
        isMysql = false,
        hasDbService = false,
        needsFkPragma = true,
        envUrl = s"sqlite+aiosqlite:////data/$snake.db",
        localUrl = s"sqlite+aiosqlite:///./$snake.db",
        ciUrl = s"sqlite+aiosqlite:///./$snake.db",
        dbImage = "",
        dbPort = "",
        dbVolumePath = "",
        dbHealthCmd = "",
        composeEnv = Nil
      )
    case "mysql" =>
      DialectView(
        id = "mysql",
        isPostgres = false,
        isSqlite = false,
        isMysql = true,
        hasDbService = true,
        needsFkPragma = false,
        envUrl = s"mysql+aiomysql://$snake:$snake@db:3306/$snake",
        localUrl = s"mysql+aiomysql://$snake:$snake@localhost:3306/$snake",
        ciUrl = s"mysql+aiomysql://$snake:$snake@127.0.0.1:3306/$snake",
        dbImage = "mysql:8.4",
        dbPort = "3306",
        dbVolumePath = "/var/lib/mysql",
        dbHealthCmd = "mysqladmin ping -h 127.0.0.1 --silent",
        composeEnv = List(
          ComposeEnv("MYSQL_USER", snake),
          ComposeEnv("MYSQL_PASSWORD", snake),
          ComposeEnv("MYSQL_DATABASE", snake),
          ComposeEnv("MYSQL_ROOT_PASSWORD", s"${snake}_root")
        )
      )
    case _ =>
      DialectView(
        id = "postgres",
        isPostgres = true,
        isSqlite = false,
        isMysql = false,
        hasDbService = true,
        needsFkPragma = false,
        envUrl = s"postgresql+asyncpg://$snake:$snake@db:5432/$snake",
        localUrl = s"postgresql+asyncpg://$snake:$snake@localhost:5432/$snake",
        ciUrl = s"postgresql+asyncpg://$snake:$snake@localhost:5432/$snake",
        dbImage = "postgres:17-alpine",
        dbPort = "5432",
        dbVolumePath = "/var/lib/postgresql/data",
        dbHealthCmd = s"pg_isready -U $snake",
        composeEnv = List(
          ComposeEnv("POSTGRES_USER", snake),
          ComposeEnv("POSTGRES_PASSWORD", snake),
          ComposeEnv("POSTGRES_DB", snake)
        )
      )

final case class RenderContext(
    service: ServiceNames,
    profile: RenderProfile,
    entities: List[ProfiledEntity],
    operations: List[ProfiledOperation],
    endpoints: List[EndpointSpec],
    schema: DatabaseSchema,
    db: DialectView,
    dafnyKernel: Option[DafnyKernel] = None
)

final case class RenderResult(fileName: String, content: String)

final case class TemplateSource(name: String, content: String)

object RenderContext:
  import specrest.convention.Naming
  import specrest.profile.{DeploymentProfile, ProfiledService, TypeMapping}

  def buildRenderContext(
      profiled: ProfiledService,
      dafnyKernel: Option[DafnyKernel] = None
  ): RenderContext =
    RenderContext(
      service = ServiceNames(
        name = profiled.ir.a,
        snakeName = Naming.toSnakeCase(profiled.ir.a),
        kebabName = Naming.toKebabCase(profiled.ir.a)
      ),
      profile = convertProfile(profiled.profile),
      entities = profiled.entities,
      operations = profiled.operations,
      endpoints = profiled.endpoints,
      schema = profiled.schema,
      db = DialectView.of(profiled.profile.database, Naming.toSnakeCase(profiled.ir.a)),
      dafnyKernel = dafnyKernel
    )

  private def convertProfile(profile: DeploymentProfile): RenderProfile =
    RenderProfile(
      name = profile.name,
      displayName = profile.displayName,
      language = profile.language,
      framework = profile.framework,
      database = profile.database,
      orm = profile.orm,
      migrationTool = profile.migrationTool,
      validation = profile.validation,
      packageManager = profile.packageManager,
      httpServer = profile.httpServer,
      dbDriver = profile.dbDriver,
      async = profile.async,
      pythonVersion = profile.pythonVersion,
      directories = profile.directories,
      typeMap = convertTypeMap(profile.typeMap),
      dependencies = profile.dependencies,
      devDependencies = profile.devDependencies
    )

  private def convertTypeMap(tm: Map[String, TypeMapping]): List[TypeMappingEntry] =
    tm.toList.map: (specType, m) =>
      TypeMappingEntry(specType, m.domain, m.validation, m.ormColumn)
