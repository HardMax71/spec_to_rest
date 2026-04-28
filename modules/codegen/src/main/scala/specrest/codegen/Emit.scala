package specrest.codegen

import specrest.codegen.alembic.AlembicMigration
import specrest.codegen.alembic.BuildMigrationOptions
import specrest.codegen.alembic.Migration
import specrest.codegen.openapi.OpenApi
import specrest.convention.EndpointSpec
import specrest.convention.Naming
import specrest.convention.TableSpec
import specrest.ir.TypeAliasDecl
import specrest.ir.TypeExpr
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

import scala.collection.mutable

final case class EmittedFile(path: String, content: String)

final case class EmitOptions(
    createdDate: Option[String] = None,
    revision: Option[String] = None
)

final private case class StdlibImport(module: String, names: List[String])

final private case class EnrichedPathParam(name: String, pythonType: String)

final private case class CustomRequestSchema(schemaName: String, fields: List[ProfiledField])

final private case class EnrichedOperation(
    operationName: String,
    handlerName: String,
    kind: String,
    method: String,
    path: String,
    successStatus: Int,
    pathParamsWithTypes: List[EnrichedPathParam],
    hasRequestBody: Boolean,
    requestBodyType: String,
    responseAnnotation: String,
    serviceCallArgs: String,
    routeKind: String,
    pathParamSignature: String,
    serviceSignatureExtraArgs: String,
    serviceReturnAnnotation: String,
    modelLookupColumn: String,
    pathParamName: String,
    customRequestSchema: Option[CustomRequestSchema]
)

final private case class EntityImports(
    sqlalchemyImports: List[String],
    postgresImports: List[String],
    stdlibImports: List[StdlibImport]
)

final private case class RouterTemplateImports(
    needsHttpException: Boolean,
    needsResponse: Boolean,
    needsRedirectResponse: Boolean,
    schemas: List[String],
    stdlibImports: List[StdlibImport]
)

final private case class ServiceTemplateImports(
    sqlalchemyCoreImports: List[String],
    schemas: List[String],
    needsModelImport: Boolean
)

final private case class ModelCtx(
    service: ServiceNames,
    profile: RenderProfile,
    entities: List[ProfiledEntity],
    operations: List[ProfiledOperation],
    endpoints: List[EndpointSpec],
    schema: specrest.convention.DatabaseSchema,
    entity: ProfiledEntity,
    table: Option[TableSpec],
    entityOperations: List[EnrichedOperation],
    nonIdFields: List[ProfiledField],
    sqlalchemyImports: List[String],
    postgresImports: List[String],
    stdlibImports: List[StdlibImport]
)

final private case class SchemaCtx(
    service: ServiceNames,
    profile: RenderProfile,
    entities: List[ProfiledEntity],
    operations: List[ProfiledOperation],
    endpoints: List[EndpointSpec],
    schema: specrest.convention.DatabaseSchema,
    entity: ProfiledEntity,
    table: Option[TableSpec],
    entityOperations: List[EnrichedOperation],
    nonIdFields: List[ProfiledField],
    readFields: List[ProfiledField],
    customRequestSchemas: List[CustomRequestSchema],
    stdlibImports: List[StdlibImport]
)

final private case class RouterCtx(
    service: ServiceNames,
    profile: RenderProfile,
    entities: List[ProfiledEntity],
    operations: List[ProfiledOperation],
    endpoints: List[EndpointSpec],
    schema: specrest.convention.DatabaseSchema,
    entity: ProfiledEntity,
    table: Option[TableSpec],
    entityOperations: List[EnrichedOperation],
    routerImports: RouterTemplateImports
)

final private case class ServiceCtx(
    service: ServiceNames,
    profile: RenderProfile,
    entities: List[ProfiledEntity],
    operations: List[ProfiledOperation],
    endpoints: List[EndpointSpec],
    schema: specrest.convention.DatabaseSchema,
    entity: ProfiledEntity,
    table: Option[TableSpec],
    entityOperations: List[EnrichedOperation],
    serviceImports: ServiceTemplateImports
)

final private case class AlembicCtx(
    service: ServiceNames,
    profile: RenderProfile,
    entities: List[ProfiledEntity],
    operations: List[ProfiledOperation],
    endpoints: List[EndpointSpec],
    schema: specrest.convention.DatabaseSchema,
    migration: AlembicMigration
)

object Emit:

  private val StdlibTypeSources: Map[String, StdlibImport] = Map(
    "datetime" -> StdlibImport("datetime", List("datetime")),
    "date"     -> StdlibImport("datetime", List("date")),
    "Decimal"  -> StdlibImport("decimal", List("Decimal")),
    "UUID"     -> StdlibImport("uuid", List("UUID"))
  )

  private val PostgresDialectTypes: Set[String] = Set("JSONB")

  def emitProject(profiled: ProfiledService, opts: EmitOptions = EmitOptions()): List[EmittedFile] =
    val ctx        = RenderContext.buildRenderContext(profiled)
    val engine     = new TemplateEngine
    val typeLookup = buildTypeLookup(profiled)
    val templates  = Templates.pythonFastapiPostgres
    val files      = List.newBuilder[EmittedFile]

    files += EmittedFile("app/__init__.py", "")
    files += EmittedFile("app/main.py", engine.renderAny(templates.main, ctx))
    files += EmittedFile("app/config.py", engine.renderAny(templates.config, ctx))
    files += EmittedFile("app/database.py", engine.renderAny(templates.database, ctx))
    files += EmittedFile("app/redaction.py", engine.renderAny(templates.redaction, ctx))
    files += EmittedFile("app/db/__init__.py", "")
    files += EmittedFile("app/db/base.py", engine.renderAny(templates.dbBase, ctx))
    files += EmittedFile("app/models/__init__.py", engine.renderAny(templates.modelInit, ctx))
    files += EmittedFile("app/schemas/__init__.py", engine.renderAny(templates.schemaInit, ctx))
    files += EmittedFile("app/routers/__init__.py", engine.renderAny(templates.routerInit, ctx))
    files += EmittedFile("app/services/__init__.py", engine.renderAny(templates.serviceInit, ctx))

    for entity <- ctx.entities do
      val table = ctx.schema.tables.find(_.entityName == entity.entityName)
      val entityOps = ctx.operations
        .filter(_.targetEntity.contains(entity.entityName))
        .map(op => enrichOperation(op, entity, typeLookup))
        .sortWith(byPathSpecificity)

      val imports        = collectEntityImports(entity)
      val routerImports  = collectRouterImports(entity, entityOps)
      val serviceImports = collectServiceImports(entity, entityOps)

      val entitySnake = Naming.toSnakeCase(entity.entityName)
      val routerSnake = Naming.toSnakeCase(Naming.pluralize(entity.entityName))

      val nonIdFields          = entity.fields.filterNot(_.columnName == "id")
      val readFields           = nonIdFields.filterNot(f => SensitiveFields.isSensitive(f.columnName))
      val customRequestSchemas = entityOps.flatMap(_.customRequestSchema)
      val schemaStdlib         = collectSchemaStdlibImports(entity, customRequestSchemas)

      val modelCtx = ModelCtx(
        service = ctx.service,
        profile = ctx.profile,
        entities = ctx.entities,
        operations = ctx.operations,
        endpoints = ctx.endpoints,
        schema = ctx.schema,
        entity = entity,
        table = table,
        entityOperations = entityOps,
        nonIdFields = nonIdFields,
        sqlalchemyImports = imports.sqlalchemyImports,
        postgresImports = imports.postgresImports,
        stdlibImports = imports.stdlibImports
      )

      val schemaCtx = SchemaCtx(
        service = ctx.service,
        profile = ctx.profile,
        entities = ctx.entities,
        operations = ctx.operations,
        endpoints = ctx.endpoints,
        schema = ctx.schema,
        entity = entity,
        table = table,
        entityOperations = entityOps,
        nonIdFields = nonIdFields,
        readFields = readFields,
        customRequestSchemas = customRequestSchemas,
        stdlibImports = schemaStdlib
      )

      val routerCtx = RouterCtx(
        service = ctx.service,
        profile = ctx.profile,
        entities = ctx.entities,
        operations = ctx.operations,
        endpoints = ctx.endpoints,
        schema = ctx.schema,
        entity = entity,
        table = table,
        entityOperations = entityOps,
        routerImports = routerImports
      )

      val serviceCtx = ServiceCtx(
        service = ctx.service,
        profile = ctx.profile,
        entities = ctx.entities,
        operations = ctx.operations,
        endpoints = ctx.endpoints,
        schema = ctx.schema,
        entity = entity,
        table = table,
        entityOperations = entityOps,
        serviceImports = serviceImports
      )

      files += EmittedFile(
        s"app/models/$entitySnake.py",
        engine.renderAny(templates.modelEntity, modelCtx)
      )
      files += EmittedFile(
        s"app/schemas/$entitySnake.py",
        engine.renderAny(templates.schemaEntity, schemaCtx)
      )
      files += EmittedFile(
        s"app/routers/$routerSnake.py",
        engine.renderAny(templates.routerEntity, routerCtx)
      )
      files += EmittedFile(
        s"app/services/$entitySnake.py",
        engine.renderAny(templates.serviceEntity, serviceCtx)
      )

    files += EmittedFile("openapi.yaml", OpenApi.serialize(OpenApi.buildOpenApiDocument(profiled)))

    val migration = Migration.buildAlembicMigration(
      profiled.schema,
      BuildMigrationOptions(revision = opts.revision, createdDate = opts.createdDate)
    )
    val alembicCtx = AlembicCtx(
      service = ctx.service,
      profile = ctx.profile,
      entities = ctx.entities,
      operations = ctx.operations,
      endpoints = ctx.endpoints,
      schema = ctx.schema,
      migration = migration
    )
    files += EmittedFile("alembic.ini", engine.renderAny(templates.alembicIni, ctx))
    files += EmittedFile("alembic/env.py", engine.renderAny(templates.alembicEnv, ctx))
    files += EmittedFile(
      s"alembic/versions/${migration.revision}_initial_schema.py",
      engine.renderAny(templates.alembicMigration, alembicCtx)
    )

    files += EmittedFile("pyproject.toml", engine.renderAny(templates.pyproject, ctx))
    files += EmittedFile("Dockerfile", engine.renderAny(templates.dockerfile, ctx))
    files += EmittedFile("docker-compose.yml", engine.renderAny(templates.dockerCompose, ctx))
    files += EmittedFile(".env.example", engine.renderAny(templates.envExample, ctx))
    files += EmittedFile("Makefile", engine.renderAny(templates.makefile, ctx))
    files += EmittedFile(".gitignore", engine.renderAny(templates.gitignore, ctx))
    files += EmittedFile(".dockerignore", engine.renderAny(templates.dockerignore, ctx))
    files += EmittedFile("README.md", engine.renderAny(templates.readme, ctx))
    files += EmittedFile(
      ".github/workflows/ci.yml",
      engine.renderAny(templates.ciWorkflow, ctx)
    )
    files += EmittedFile("tests/test_health.py", engine.renderAny(templates.testHealth, ctx))
    files += EmittedFile(
      "tests/test_log_redaction.py",
      engine.renderAny(templates.testLogRedaction, ctx)
    )

    files.result()

  private def buildTypeLookup(profiled: ProfiledService): Map[String, String] =
    val base = mutable.Map.empty[String, String]
    for (specType, mapping) <- profiled.profile.typeMap do base(specType) = mapping.python
    val aliasesByName = profiled.ir.typeAliases.map(a => a.name -> a).toMap
    for alias <- profiled.ir.typeAliases do
      val resolved = resolveAliasToPython(alias.typeExpr, base.toMap, aliasesByName, Set.empty)
      resolved.foreach(r => base(alias.name) = r)
    base.toMap

  private def resolveAliasToPython(
      typeExpr: TypeExpr,
      base: Map[String, String],
      aliasesByName: Map[String, TypeAliasDecl],
      visited: Set[String]
  ): Option[String] = typeExpr match
    case TypeExpr.NamedType(name, _) =>
      base.get(name).orElse:
        if visited.contains(name) then None
        else
          aliasesByName.get(name).flatMap: alias =>
            resolveAliasToPython(alias.typeExpr, base, aliasesByName, visited + name)
    case TypeExpr.OptionType(inner, _) =>
      resolveAliasToPython(inner, base, aliasesByName, visited).map(i => s"$i | None")
    case _ => None

  private def pythonTypeForParam(typeExpr: TypeExpr, typeLookup: Map[String, String]): String =
    typeExpr match
      case TypeExpr.NamedType(n, _) => typeLookup.getOrElse(n, "str")
      case TypeExpr.OptionType(inner, _) =>
        s"${pythonTypeForParam(inner, typeLookup)} | None"
      case _ => "str"

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String]
  ): EnrichedOperation =
    val endpoint = op.endpoint
    val pathParamsWithTypes = endpoint.pathParams.map: p =>
      EnrichedPathParam(p.name, pythonTypeForParam(p.typeExpr, typeLookup))

    val initialRouteKind = RouteKind.classify(op)
    val method           = endpoint.method.toString.toLowerCase

    val pathParamCallArgs = pathParamsWithTypes.map(_.name).mkString(", ")
    val hasRequestBody =
      initialRouteKind == RouteKind.Create || endpoint.bodyParams.nonEmpty

    val entityNonIdColumnNames =
      entity.fields.filterNot(_.columnName == "id").map(_.columnName).toSet
    val bodyParamNames = endpoint.bodyParams.map(_.name)
    val matchesEntityCreateShape =
      initialRouteKind == RouteKind.Create &&
        bodyParamNames.size == entityNonIdColumnNames.size &&
        bodyParamNames.forall(entityNonIdColumnNames.contains)

    var customRequestSchema: Option[CustomRequestSchema] = None
    var requestBodyType                                  = ""
    if hasRequestBody then
      if initialRouteKind == RouteKind.Create && matchesEntityCreateShape then
        requestBodyType = entity.createSchemaName
      else
        requestBodyType = s"${op.operationName}Request"
        val requestBodyByName = op.requestBodyFields.map(f => f.fieldName -> f).toMap
        val pathParamNames    = endpoint.pathParams.map(_.name).toSet
        val fields = op.requestBodyFields.filter: f =>
          !pathParamNames.contains(f.fieldName) && requestBodyByName.contains(f.fieldName)
        customRequestSchema = Some(CustomRequestSchema(requestBodyType, fields))

    val routeKind =
      if initialRouteKind == RouteKind.Create && !matchesEntityCreateShape then
        RouteKind.Other
      else initialRouteKind

    val (
      responseAnnotation,
      serviceCallArgs,
      pathParamSignature,
      serviceExtraArgs,
      serviceReturnAnno
    ) =
      routeKind match
        case RouteKind.Create =>
          (
            entity.readSchemaName,
            if hasRequestBody then "body" else "",
            "",
            "",
            entity.readSchemaName
          )
        case RouteKind.Read =>
          val sig = pathParamsWithTypes.map(p => s"${p.name}: ${p.pythonType}").mkString(", ")
          (
            entity.readSchemaName,
            pathParamCallArgs,
            sig,
            sig,
            s"${entity.readSchemaName} | None"
          )
        case RouteKind.List =>
          (
            s"list[${entity.readSchemaName}]",
            "",
            "",
            "",
            s"list[${entity.readSchemaName}]"
          )
        case RouteKind.Delete =>
          val sig = pathParamsWithTypes.map(p => s"${p.name}: ${p.pythonType}").mkString(", ")
          ("Response", pathParamCallArgs, sig, sig, "bool")
        case RouteKind.Redirect =>
          val sig = pathParamsWithTypes.map(p => s"${p.name}: ${p.pythonType}").mkString(", ")
          ("RedirectResponse", pathParamCallArgs, sig, sig, "str")
        case RouteKind.Other =>
          val args = pathParamsWithTypes.map(p => s"${p.name}: ${p.pythonType}") ++
            (if hasRequestBody then List(s"body: $requestBodyType") else Nil)
          val call = (pathParamsWithTypes.map(_.name) ++
            (if hasRequestBody then List("body") else Nil)).mkString(", ")
          ("None", call, "", args.mkString(", "), "None")

    val pathParamName =
      if pathParamsWithTypes.nonEmpty then pathParamsWithTypes.head.name else "id"
    val modelLookupColumn = resolveModelLookupColumn(entity, pathParamName)

    EnrichedOperation(
      operationName = op.operationName,
      handlerName = op.handlerName,
      kind = op.kind.toString,
      method = method,
      path = endpoint.path,
      successStatus = endpoint.successStatus,
      pathParamsWithTypes = pathParamsWithTypes,
      hasRequestBody = hasRequestBody,
      requestBodyType = requestBodyType,
      responseAnnotation = responseAnnotation,
      serviceCallArgs = serviceCallArgs,
      routeKind = routeKindTsName(routeKind),
      pathParamSignature = pathParamSignature,
      serviceSignatureExtraArgs = serviceExtraArgs,
      serviceReturnAnnotation = serviceReturnAnno,
      modelLookupColumn = modelLookupColumn,
      pathParamName = pathParamName,
      customRequestSchema = customRequestSchema
    )

  private def routeKindTsName(rk: RouteKind): String = rk match
    case RouteKind.Create   => "create"
    case RouteKind.Read     => "read"
    case RouteKind.List     => "list"
    case RouteKind.Delete   => "delete"
    case RouteKind.Redirect => "redirect"
    case RouteKind.Other    => "other"

  private def resolveModelLookupColumn(entity: ProfiledEntity, pathParamName: String): String =
    if entity.fields.exists(_.columnName == pathParamName) then pathParamName
    else
      val entitySnake = Naming.toSnakeCase(entity.entityName)
      if pathParamName == s"${entitySnake}_id" then "id" else "id"

  private def byPathSpecificity(a: EnrichedOperation, b: EnrichedOperation): Boolean =
    val aCount = a.path.count(_ == '{')
    val bCount = b.path.count(_ == '{')
    aCount < bCount

  private def mergeStdlibImport(
      byModule: mutable.Map[String, mutable.Set[String]],
      pythonType: String
  ): Unit =
    val key = pythonType.replaceAll("\\s*\\|\\s*None$", "")
    StdlibTypeSources.get(key).foreach: stdlib =>
      val existing = byModule.getOrElseUpdate(stdlib.module, mutable.Set.empty)
      stdlib.names.foreach(existing += _)

  private def finalizeStdlibImports(
      byModule: mutable.Map[String, mutable.Set[String]]
  ): List[StdlibImport] =
    byModule.toList
      .sortBy(_._1)
      .map((m, names) => StdlibImport(m, names.toList.sorted))

  private def collectEntityImports(entity: ProfiledEntity): EntityImports =
    val sqlSet         = mutable.Set.empty[String]
    val pgSet          = mutable.Set.empty[String]
    val stdlibByModule = mutable.Map.empty[String, mutable.Set[String]]
    for field <- entity.fields do
      val colType = field.sqlalchemyColumnType
      if PostgresDialectTypes.contains(colType) then pgSet += colType
      else sqlSet += colType
      mergeStdlibImport(stdlibByModule, field.pythonType)
    EntityImports(
      sqlalchemyImports = sqlSet.toList.sorted,
      postgresImports = pgSet.toList.sorted,
      stdlibImports = finalizeStdlibImports(stdlibByModule)
    )

  private def collectSchemaStdlibImports(
      entity: ProfiledEntity,
      customRequestSchemas: List[CustomRequestSchema]
  ): List[StdlibImport] =
    val stdlibByModule = mutable.Map.empty[String, mutable.Set[String]]
    for field  <- entity.fields do mergeStdlibImport(stdlibByModule, field.pythonType)
    for schema <- customRequestSchemas; field <- schema.fields do
      mergeStdlibImport(stdlibByModule, field.pythonType)
    finalizeStdlibImports(stdlibByModule)

  private def collectRouterImports(
      entity: ProfiledEntity,
      operations: List[EnrichedOperation]
  ): RouterTemplateImports =
    var needsHttpException    = false
    var needsResponse         = false
    var needsRedirectResponse = false
    val schemaSet             = mutable.Set.empty[String]
    val stdlibByModule        = mutable.Map.empty[String, mutable.Set[String]]

    for op <- operations do
      op.routeKind match
        case "read"     => needsHttpException = true
        case "delete"   => needsHttpException = true; needsResponse = true
        case "redirect" => needsRedirectResponse = true
        case _          => ()
      if op.hasRequestBody && op.requestBodyType.nonEmpty then schemaSet += op.requestBodyType
      if op.routeKind == "create" || op.routeKind == "read" || op.routeKind == "list" then
        schemaSet += entity.readSchemaName
      op.pathParamsWithTypes.foreach(p => mergeStdlibImport(stdlibByModule, p.pythonType))

    RouterTemplateImports(
      needsHttpException = needsHttpException,
      needsResponse = needsResponse,
      needsRedirectResponse = needsRedirectResponse,
      schemas = schemaSet.toList.sorted,
      stdlibImports = finalizeStdlibImports(stdlibByModule)
    )

  private def collectServiceImports(
      entity: ProfiledEntity,
      operations: List[EnrichedOperation]
  ): ServiceTemplateImports =
    var needsSelect      = false
    var needsSaDelete    = false
    var needsModelImport = false
    val schemaSet        = mutable.Set.empty[String]

    for op <- operations do
      if op.routeKind == "read" || op.routeKind == "list" then
        needsSelect = true; needsModelImport = true
      if op.routeKind == "delete" then needsSaDelete = true; needsModelImport = true
      if op.routeKind == "create" then needsModelImport = true
      if op.routeKind == "create" || op.routeKind == "read" || op.routeKind == "list" then
        schemaSet += entity.readSchemaName
      if op.hasRequestBody && op.requestBodyType.nonEmpty then schemaSet += op.requestBodyType

    val coreImports = List.newBuilder[String]
    if needsSaDelete then coreImports += "delete as sa_delete"
    if needsSelect then coreImports += "select"

    ServiceTemplateImports(
      sqlalchemyCoreImports = coreImports.result(),
      schemas = schemaSet.toList.sorted,
      needsModelImport = needsModelImport
    )
