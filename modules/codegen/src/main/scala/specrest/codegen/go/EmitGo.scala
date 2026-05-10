package specrest.codegen.go

import specrest.codegen.DafnyKernel
import specrest.codegen.EmitOptions
import specrest.codegen.EmittedFile
import specrest.codegen.GoTemplates
import specrest.codegen.RenderContext
import specrest.codegen.RouteKind
import specrest.codegen.TemplateEngine
import specrest.codegen.openapi.OpenApi
import specrest.convention.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final private case class GoCustomSchema(name: String, fields: List[GoFieldView])

final private case class GoFieldView(
    goField: String,
    jsonTag: String,
    dbTag: String,
    domainType: String,
    sqlType: String,
    nullable: Boolean,
    isSensitive: Boolean,
    isPrimaryKey: Boolean
)

final private case class GoPathParam(name: String, goName: String, domainType: String)

final private case class GoOperation(
    operationName: String,
    handlerName: String,
    method: String,
    path: String,
    chiPath: String,
    successStatus: Int,
    routeKind: String,
    pathParams: List[GoPathParam],
    hasRequestBody: Boolean,
    requestBodyType: String,
    customRequestSchemaName: Option[String],
    customRequestFields: List[GoFieldView],
    responseType: String,
    serviceMethod: String,
    serviceCallArgs: String,
    serviceSignatureArgs: String,
    serviceReturnType: String,
    redirectField: Option[String],
    dafnyMethod: Option[String],
    sql: String,
    sqlArgs: String,
    scanFields: String
)

final private case class GoEntityCtx(
    service: GoServiceNames,
    module: String,
    entity: ProfiledEntity,
    entitySnake: String,
    entityCamel: String,
    entityPlural: String,
    entityPluralSnake: String,
    primaryKey: GoFieldView,
    fields: List[GoFieldView],
    nonIdFields: List[GoFieldView],
    operations: List[GoOperation],
    needsTime: Boolean,
    needsUuid: Boolean,
    needsDecimal: Boolean,
    needsValidator: Boolean,
    customSchemas: List[GoCustomSchema]
)

final private case class GoServiceNames(name: String, snakeName: String, kebabName: String)

final private case class GoProjectCtx(
    service: GoServiceNames,
    module: String,
    entities: List[GoEntityCtx],
    needsTime: Boolean,
    needsUuid: Boolean,
    needsDecimal: Boolean,
    dafnyKernel: Option[DafnyKernel]
)

@SuppressWarnings(Array("org.wartremover.warts.Var"))
object EmitGo:

  def emit(profiled: ProfiledService, opts: EmitOptions): List[EmittedFile] =
    val engine    = new TemplateEngine
    val templates = GoTemplates.goChiPostgres
    val ctx       = RenderContext.buildRenderContext(profiled, opts.dafnyKernel)
    val module    = goModuleName(ctx.service.kebabName)
    val service = GoServiceNames(
      name = ctx.service.name,
      snakeName = ctx.service.snakeName,
      kebabName = ctx.service.kebabName
    )

    val typeLookup = profiled.profile.typeMap.map: (k, v) =>
      k -> v.domain

    val entities = profiled.entities.map: entity =>
      val entityOps = profiled.operations
        .filter(_.targetEntity.contains(entity.entityName))
        .map(op => enrichOperation(op, entity, typeLookup))
        .sortWith(byPathSpecificity)
      buildEntityCtx(service, module, entity, entityOps)

    val needsTime    = entities.exists(_.needsTime)
    val needsUuid    = entities.exists(_.needsUuid)
    val needsDecimal = entities.exists(_.needsDecimal)

    val projectCtx = GoProjectCtx(
      service = service,
      module = module,
      entities = entities,
      needsTime = needsTime,
      needsUuid = needsUuid,
      needsDecimal = needsDecimal,
      dafnyKernel = opts.dafnyKernel
    )

    val files = List.newBuilder[EmittedFile]

    files += EmittedFile("go.mod", engine.renderAny(templates.goMod, mergeProfile(ctx, projectCtx)))
    files += EmittedFile(
      "cmd/server/main.go",
      engine.renderAny(templates.main, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "internal/config/config.go",
      engine.renderAny(templates.config, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "internal/database/database.go",
      engine.renderAny(templates.database, mergeProfile(ctx, projectCtx))
    )

    files += EmittedFile(
      "internal/handlers/common.go",
      engine.renderAny(templates.handlerCommon, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "internal/services/common.go",
      engine.renderAny(templates.serviceCommon, mergeProfile(ctx, projectCtx))
    )

    entities.foreach: entityCtx =>
      val perEntity = mergeProfile(ctx, projectCtx, Some(entityCtx))
      files += EmittedFile(
        s"internal/models/${entityCtx.entitySnake}.go",
        engine.renderAny(templates.modelEntity, perEntity)
      )
      files += EmittedFile(
        s"internal/handlers/${entityCtx.entityPluralSnake}.go",
        engine.renderAny(templates.handlerEntity, perEntity)
      )
      files += EmittedFile(
        s"internal/services/${entityCtx.entitySnake}.go",
        engine.renderAny(templates.serviceEntity, perEntity)
      )

    files += EmittedFile(
      "migrations/001_initial_schema.up.sql",
      engine.renderAny(templates.migrationUp, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "migrations/001_initial_schema.down.sql",
      engine.renderAny(templates.migrationDown, mergeProfile(ctx, projectCtx))
    )

    files += EmittedFile(
      "Dockerfile",
      engine.renderAny(templates.dockerfile, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "docker-compose.yml",
      engine.renderAny(templates.dockerCompose, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      ".env.example",
      engine.renderAny(templates.envExample, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "Makefile",
      engine.renderAny(templates.makefile, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      ".gitignore",
      engine.renderAny(templates.gitignore, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      ".dockerignore",
      engine.renderAny(templates.dockerignore, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "README.md",
      engine.renderAny(templates.readme, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      ".github/workflows/ci.yml",
      engine.renderAny(templates.ciWorkflow, mergeProfile(ctx, projectCtx))
    )
    files += EmittedFile(
      "tests/health_test.go",
      engine.renderAny(templates.testHealth, mergeProfile(ctx, projectCtx))
    )

    files += EmittedFile(
      "openapi.yaml",
      OpenApi.serialize(OpenApi.buildOpenApiDocument(profiled))
    )

    opts.dafnyKernel.foreach: kernel =>
      val pkg = kernel.packagePath.stripSuffix("/")
      kernel.files.toList.sortBy(_._1).foreach: (rel, content) =>
        files += EmittedFile(s"$pkg/$rel", content)

    files.result()

  private def goModuleName(serviceKebab: String): String =
    s"github.com/generated/$serviceKebab"

  private def mergeProfile(
      ctx: RenderContext,
      proj: GoProjectCtx,
      currentEntity: Option[GoEntityCtx] = None
  ): Map[String, Any] =
    val base = Map[String, Any](
      "service"      -> proj.service,
      "module"       -> proj.module,
      "profile"      -> ctx.profile,
      "entities"     -> proj.entities,
      "needsTime"    -> proj.needsTime,
      "needsUuid"    -> proj.needsUuid,
      "needsDecimal" -> proj.needsDecimal,
      "hasDafny"     -> proj.dafnyKernel.isDefined
    )
    currentEntity match
      case Some(e) =>
        base + ("entity" -> e) + ("entityCtx" -> e)
      case None => base

  private def buildEntityCtx(
      service: GoServiceNames,
      module: String,
      entity: ProfiledEntity,
      operations: List[GoOperation]
  ): GoEntityCtx =
    val _                 = (service, module)
    val entitySnake       = Naming.toSnakeCase(entity.entityName)
    val entityCamel       = toCamelCase(entity.entityName)
    val entityPlural      = Naming.pluralize(entity.entityName)
    val entityPluralSnake = Naming.toSnakeCase(entityPlural)

    val primaryKey = GoFieldView(
      goField = "ID",
      jsonTag = "id",
      dbTag = "id",
      domainType = "int64",
      sqlType = "BIGSERIAL PRIMARY KEY",
      nullable = false,
      isSensitive = false,
      isPrimaryKey = true
    )

    val nonIdFields = entity.fields.filterNot(_.fieldName == "id").map(toGoField)
    val allFields   = primaryKey +: nonIdFields

    val needsTime = nonIdFields.exists: f =>
      f.domainType.contains("time.Time")
    val needsUuid = nonIdFields.exists: f =>
      f.domainType.contains("uuid.UUID")
    val needsDecimal = nonIdFields.exists: f =>
      f.domainType.contains("decimal.Decimal")

    val customSchemas = operations.flatMap: op =>
      op.customRequestSchemaName.map(name => GoCustomSchema(name, op.customRequestFields))

    GoEntityCtx(
      service =
        GoServiceNames(entity.entityName, entitySnake, Naming.toKebabCase(entity.entityName)),
      module = module,
      entity = entity,
      entitySnake = entitySnake,
      entityCamel = entityCamel,
      entityPlural = entityPlural,
      entityPluralSnake = entityPluralSnake,
      primaryKey = primaryKey,
      fields = allFields,
      nonIdFields = nonIdFields,
      operations = operations,
      needsTime = needsTime,
      needsUuid = needsUuid,
      needsDecimal = needsDecimal,
      needsValidator = nonIdFields.nonEmpty,
      customSchemas = customSchemas
    )

  private def toGoField(f: ProfiledField): GoFieldView =
    GoFieldView(
      goField = toPascalCase(f.fieldName),
      jsonTag = f.columnName,
      dbTag = f.columnName,
      domainType = f.domainType,
      sqlType = sqlTypeFor(f.ormColumnType, f.nullable),
      nullable = f.nullable,
      isSensitive = false,
      isPrimaryKey = false
    )

  private def sqlTypeFor(base: String, nullable: Boolean): String =
    if nullable then base else s"$base NOT NULL"

  private val GoInitialisms: Set[String] =
    Set("id", "url", "uuid", "api", "http", "json", "html", "sql", "ip", "tcp", "udp")

  private def toPascalCase(name: String): String =
    val parts = name.split('_').toList.flatMap(p => Naming.splitCamelCase(p)).filter(_.nonEmpty)
    parts.map: w =>
      if GoInitialisms.contains(w.toLowerCase) then w.toUpperCase
      else w.head.toUpper +: w.tail.toLowerCase
    .mkString

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String]
  ): GoOperation =
    val endpoint = op.endpoint
    val pathParams = endpoint.pathParams.map: p =>
      GoPathParam(p.name, toCamelCase(p.name), goTypeForParam(p.typeExpr, typeLookup))

    val nonIdFields  = entity.fields.filterNot(_.fieldName == "id").map(toGoField)
    val tableName    = entity.tableName
    val nonIdCols    = nonIdFields.map(_.dbTag)
    val nonIdColsCsv = nonIdCols.mkString(", ")
    val allColsCsv   = ("id" +: nonIdCols).mkString(", ")
    val placeholders = nonIdCols.indices.map(i => s"$$${i + 1}").mkString(", ")
    val scanFields =
      ("&out.ID" +: nonIdFields.map(f => s"&out.${f.goField}")).mkString(", ")
    val itemScanFields =
      ("&item.ID" +: nonIdFields.map(f => s"&item.${f.goField}")).mkString(", ")
    val insertArgs = nonIdFields.map(f => s"body.${f.goField}").mkString(", ")
    val lookupCol = pathParams.headOption match
      case Some(p) if entity.fields.exists(_.columnName == p.name) => p.name
      case _                                                       => "id"
    val pathArgsCsv = pathParams.map(_.goName).mkString(", ")

    val initialRouteKind = RouteKind.classify(op)
    val method           = endpoint.method.toString.toUpperCase
    val chiPath          = endpoint.path.replaceAll("\\{([^}]+)\\}", "{$1}")

    val entityNonIdColumnNames =
      entity.fields.filterNot(_.fieldName == "id").map(_.columnName).toSet
    val bodyParamNames = endpoint.bodyParams.map(_.name)
    val matchesEntityCreateShape =
      initialRouteKind == RouteKind.Create &&
        bodyParamNames.size == entityNonIdColumnNames.size &&
        bodyParamNames.forall(entityNonIdColumnNames.contains)

    val hasRequestBody = initialRouteKind == RouteKind.Create || endpoint.bodyParams.nonEmpty

    var requestBodyType         = ""
    var customRequestSchemaName = Option.empty[String]
    var customRequestFields     = List.empty[GoFieldView]

    if hasRequestBody then
      if initialRouteKind == RouteKind.Create && matchesEntityCreateShape then
        requestBodyType = entity.createSchemaName
      else
        requestBodyType = s"${op.operationName}Request"
        customRequestSchemaName = Some(requestBodyType)
        val pathParamNames = endpoint.pathParams.map(_.name).toSet
        customRequestFields = op.requestBodyFields
          .filterNot(f => pathParamNames.contains(f.fieldName))
          .map(toGoField)

    val routeKind =
      if initialRouteKind == RouteKind.Create && !matchesEntityCreateShape then RouteKind.Other
      else initialRouteKind

    val pathParamCallArgs  = pathParams.map(_.goName).mkString(", ")
    val pathParamSignature = pathParams.map(p => s"${p.goName} ${p.domainType}").mkString(", ")
    val readSchemaName     = entity.readSchemaName
    val (
      responseType,
      redirectField,
      serviceReturnType,
      serviceMethodName,
      serviceCallArgs,
      serviceSig,
      sql,
      sqlArgs
    ) =
      routeKind match
        case RouteKind.Create =>
          val sig = if hasRequestBody then s"body $requestBodyType" else ""
          val ret = readSchemaName
          val s =
            s"INSERT INTO $tableName ($nonIdColsCsv) VALUES ($placeholders) RETURNING $allColsCsv"
          (ret, Option.empty[String], ret, op.operationName, "body", sig, s, insertArgs)
        case RouteKind.Read =>
          val s = s"SELECT $allColsCsv FROM $tableName WHERE $lookupCol = $$1"
          (
            s"*$readSchemaName",
            Option.empty[String],
            s"*$readSchemaName",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature,
            s,
            pathArgsCsv
          )
        case RouteKind.List =>
          val s = s"SELECT $allColsCsv FROM $tableName ORDER BY id"
          (
            s"[]$readSchemaName",
            Option.empty[String],
            s"[]$readSchemaName",
            op.operationName,
            "",
            "",
            s,
            ""
          )
        case RouteKind.Delete =>
          val s = s"DELETE FROM $tableName WHERE $lookupCol = $$1"
          (
            "",
            Option.empty[String],
            "bool",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature,
            s,
            pathArgsCsv
          )
        case RouteKind.Redirect =>
          val tgt = redirectTarget(op, entity).getOrElse("URL")
          val s   = s"SELECT $allColsCsv FROM $tableName WHERE $lookupCol = $$1"
          (
            "string",
            Some(tgt),
            s"*$readSchemaName",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature,
            s,
            pathArgsCsv
          )
        case RouteKind.Other =>
          val args = pathParams.map(p => s"${p.goName} ${p.domainType}") ++
            (if hasRequestBody then List(s"body models.$requestBodyType") else Nil)
          val call = (pathParams.map(_.goName) ++ (if hasRequestBody then List("body") else Nil))
            .mkString(", ")
          ("", Option.empty[String], "error", op.operationName, call, args.mkString(", "), "", "")

    val itemScan = routeKind match
      case RouteKind.List => itemScanFields
      case _              => scanFields

    GoOperation(
      operationName = op.operationName,
      handlerName = op.operationName,
      method = method,
      path = endpoint.path,
      chiPath = chiPath,
      successStatus = endpoint.successStatus,
      routeKind = routeKindName(routeKind),
      pathParams = pathParams,
      hasRequestBody = hasRequestBody,
      requestBodyType = requestBodyType,
      customRequestSchemaName = customRequestSchemaName,
      customRequestFields = customRequestFields,
      responseType = responseType,
      serviceMethod = serviceMethodName,
      serviceCallArgs = serviceCallArgs,
      serviceSignatureArgs = serviceSig,
      serviceReturnType = serviceReturnType,
      redirectField = redirectField,
      dafnyMethod = op.dafnyMethod,
      sql = sql,
      sqlArgs = sqlArgs,
      scanFields = itemScan
    )

  private def redirectTarget(
      @scala.annotation.unused op: ProfiledOperation,
      entity: ProfiledEntity
  ): Option[String] =
    val candidates = List("url", "location", "redirect_url")
    candidates.find: c =>
      entity.fields.exists(_.columnName == c)
    .map(toPascalCase)

  private def goTypeForParam(typeExpr: type_expr_full, typeLookup: Map[String, String]): String =
    typeExpr match
      case NamedTypeF(n, _)      => typeLookup.getOrElse(n, "string")
      case OptionTypeF(inner, _) => s"*${goTypeForParam(inner, typeLookup)}"
      case _                     => "string"

  private def toCamelCase(name: String): String =
    val parts = name.split('_').toList.flatMap(p => Naming.splitCamelCase(p)).filter(_.nonEmpty)
    parts.zipWithIndex.map: (w, i) =>
      if i == 0 then w.toLowerCase
      else w.head.toUpper +: w.tail.toLowerCase
    .mkString

  private def routeKindName(rk: RouteKind): String = rk match
    case RouteKind.Create   => "create"
    case RouteKind.Read     => "read"
    case RouteKind.List     => "list"
    case RouteKind.Delete   => "delete"
    case RouteKind.Redirect => "redirect"
    case RouteKind.Other    => "other"

  private def byPathSpecificity(a: GoOperation, b: GoOperation): Boolean =
    val aCount = a.path.count(_ == '{')
    val bCount = b.path.count(_ == '{')
    aCount < bCount
