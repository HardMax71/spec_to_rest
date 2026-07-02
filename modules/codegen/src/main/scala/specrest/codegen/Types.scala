package specrest.codegen

import specrest.codegen.migration.Dialect
import specrest.codegen.migration.DialectView
import specrest.convention.EndpointSpec
import specrest.ir.generated.SpecRestGenerated.database_schema
import specrest.ir.generated.SpecRestGenerated.svcName
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

// The pinned GitHub Action SHAs (and the zizmor version) the generated ci.yml
// workflows reference. One place to bump; before this existed the python and
// go/ts templates had drifted to different checkout pins (v6.0.3 vs v4.2.2).
final case class ActionPins(
    checkout: String,
    setupUv: String,
    setupPython: String,
    setupGo: String,
    setupNode: String,
    uploadArtifact: String,
    zizmor: String
)

object ActionPins:
  val Current: ActionPins = ActionPins(
    checkout = "df4cb1c069e1874edd31b4311f1884172cec0e10 # v6.0.3",
    setupUv = "fac544c07dec837d0ccb6301d7b5580bf5edae39 # v8.2.0",
    setupPython = "a309ff8b426b58ec0e2a45f0f869d46889d02405 # v6.2.0",
    setupGo = "d35c59abb061a4a6fb18e82ac0862c26744d6ab5 # v5.5.0",
    setupNode = "39370e3970a6d050c480ffad4ff0ed4d3fdee5af # v4.1.0",
    uploadArtifact = "ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2",
    zizmor = "1.25.2"
  )

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
    needsJwt: Boolean = false,
    pins: ActionPins = ActionPins.Current
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
      needsJwt = AuthSchemes.needsJwt(profiled.ir)
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
