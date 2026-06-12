package specrest.codegen

import specrest.codegen.migration.Dialect
import specrest.codegen.migration.DialectView
import specrest.convention.EndpointSpec
import specrest.ir.generated.SpecRestGenerated.database_schema
import specrest.ir.generated.SpecRestGenerated.ssdKind
import specrest.ir.generated.SpecRestGenerated.svcName
import specrest.ir.generated.SpecRestGenerated.svcSecurity
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
    schema: database_schema,
    db: DialectView,
    dafnyKernel: Option[DafnyKernel] = None,
    scalarStateFields: List[ScalarStateFieldView] = Nil,
    hasScalarOps: Boolean = false,
    routerImport: String = "admin",
    authSettingLines: List[String] = Nil,
    needsJwt: Boolean = false
)

final case class RenderResult(fileName: String, content: String)

final case class TemplateSource(name: String, content: String)

object RenderContext:
  import specrest.ir.Naming
  import specrest.profile.{DeploymentProfile, ProfiledService, TypeMapping}

  def buildRenderContext(
      profiled: ProfiledService,
      dafnyKernel: Option[DafnyKernel] = None
  ): RenderContext =
    RenderContext(
      service = ServiceNames(
        name = svcName(profiled.ir),
        snakeName = Naming.toSnakeCase(svcName(profiled.ir)),
        kebabName = Naming.toKebabCase(svcName(profiled.ir))
      ),
      profile = convertProfile(profiled.profile),
      entities = profiled.entities,
      operations = profiled.operations,
      endpoints = profiled.endpoints,
      schema = profiled.schema,
      db = Dialect
        .forDatabase(profiled.profile.database)
        .deployment(Naming.toSnakeCase(svcName(profiled.ir))),
      dafnyKernel = dafnyKernel,
      scalarStateFields = ScalarOps.stateFields(profiled),
      hasScalarOps = ScalarOps.views(profiled).nonEmpty,
      routerImport = {
        val modules =
          "admin" ::
            profiled.entities.map(e => Naming.toSnakeCase(Naming.pluralize(e.entityName))) :::
            (if ScalarOps.views(profiled).nonEmpty then List("state_ops") else Nil)
        modules.sorted.mkString(", ")
      },
      authSettingLines = specrest.codegen.python.SecurityPython.settingLines(profiled.ir),
      needsJwt = svcSecurity(profiled.ir)
        .exists(s => specrest.codegen.python.SecurityPython.isJwt(ssdKind(s)))
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
