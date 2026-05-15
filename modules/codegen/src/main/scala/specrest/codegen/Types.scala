package specrest.codegen

import specrest.codegen.migration.Dialect
import specrest.codegen.migration.DialectView
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
      db = Dialect
        .forDatabase(profiled.profile.database)
        .deployment(Naming.toSnakeCase(profiled.ir.a)),
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
