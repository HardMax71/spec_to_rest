package specrest.codegen.ts

import specrest.codegen.DafnyKernel
import specrest.codegen.EmitOptions
import specrest.codegen.EmittedFile
import specrest.codegen.RenderContext
import specrest.codegen.RouteKind
import specrest.codegen.TemplateEngine
import specrest.codegen.TsTemplates
import specrest.codegen.migration.MigrationOp
import specrest.codegen.migration.Revision
import specrest.codegen.migration.SchemaCodec
import specrest.codegen.migration.SchemaDiff
import specrest.codegen.migration.SchemaSnapshot
import specrest.codegen.migration.SqlRenderer
import specrest.codegen.openapi.OpenApi
import specrest.convention.DatabaseSchema
import specrest.convention.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final private case class TsCustomSchema(name: String, fields: List[TsFieldView])

final private case class TsFieldView(
    tsField: String,
    jsonName: String,
    columnName: String,
    domainType: String,
    prismaType: String,
    prismaAttrs: String,
    zodSchema: String,
    nullable: Boolean,
    isPrimaryKey: Boolean
)

final private case class TsPathParam(name: String, tsName: String, domainType: String)

final private case class TsOperation(
    operationName: String,
    handlerName: String,
    method: String,
    path: String,
    expressPath: String,
    successStatus: Int,
    routeKind: String,
    pathParams: List[TsPathParam],
    hasRequestBody: Boolean,
    requestBodyType: String,
    customRequestSchemaName: Option[String],
    customRequestFields: List[TsFieldView],
    serviceMethod: String,
    serviceCallArgs: String,
    serviceSignatureArgs: String,
    serviceReturnType: String,
    redirectField: Option[String],
    dafnyMethod: Option[String],
    prismaCall: String,
    prismaWhere: String,
    prismaCreateData: String,
    lookupField: String
)

final private case class TsEntityCtx(
    service: TsServiceNames,
    packageName: String,
    entity: ProfiledEntity,
    entityCamel: String,
    entityPascal: String,
    entitySnake: String,
    entityKebab: String,
    entityPlural: String,
    entityPluralCamel: String,
    entityPluralKebab: String,
    primaryKey: TsFieldView,
    fields: List[TsFieldView],
    nonIdFields: List[TsFieldView],
    operations: List[TsOperation],
    needsDecimal: Boolean,
    needsBuffer: Boolean,
    needsPrismaImport: Boolean,
    customSchemas: List[TsCustomSchema]
)

final private case class TsServiceNames(name: String, snakeName: String, kebabName: String)

final private case class TsProjectCtx(
    service: TsServiceNames,
    packageName: String,
    entities: List[TsEntityCtx],
    needsDecimal: Boolean,
    needsBuffer: Boolean,
    dafnyKernel: Option[DafnyKernel]
)

object EmitTs:

  def emit(profiled: ProfiledService, opts: EmitOptions): List[EmittedFile] =
    val engine      = new TemplateEngine
    val templates   = TsTemplates.tsExpressPostgres
    val ctx         = RenderContext.buildRenderContext(profiled, opts.dafnyKernel)
    val packageName = npmPackageName(ctx.service.kebabName)
    val service = TsServiceNames(
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
      buildEntityCtx(packageName, entity, entityOps)

    val needsDecimal = entities.exists(_.needsDecimal)
    val needsBuffer  = entities.exists(_.needsBuffer)

    val projectCtx = TsProjectCtx(
      service = service,
      packageName = packageName,
      entities = entities,
      needsDecimal = needsDecimal,
      needsBuffer = needsBuffer,
      dafnyKernel = opts.dafnyKernel
    )

    val files        = List.newBuilder[EmittedFile]
    val projectScope = mergeProfile(ctx, projectCtx)

    val projectHead: List[(String, String)] = List(
      "package.json"               -> templates.packageJson,
      "tsconfig.json"              -> templates.tsconfig,
      "prisma/schema.prisma"       -> templates.prismaSchema,
      "src/index.ts"               -> templates.index,
      "src/app.ts"                 -> templates.app,
      "src/config.ts"              -> templates.config,
      "src/prisma.ts"              -> templates.prisma,
      "src/middleware/error.ts"    -> templates.errorMiddleware,
      "src/middleware/validate.ts" -> templates.validateMiddleware,
      "src/routes/index.ts"        -> templates.routesIndex
    )

    val projectTail: List[(String, String)] = List(
      "Dockerfile"               -> templates.dockerfile,
      "docker-compose.yml"       -> templates.dockerCompose,
      ".env.example"             -> templates.envExample,
      "Makefile"                 -> templates.makefile,
      ".gitignore"               -> templates.gitignore,
      ".dockerignore"            -> templates.dockerignore,
      "README.md"                -> templates.readme,
      ".github/workflows/ci.yml" -> templates.ciWorkflow,
      "tests/health.test.ts"     -> templates.testHealth
    )

    projectHead.foreach: (path, tpl) =>
      files += EmittedFile(path, engine.renderAny(tpl, projectScope))

    entities.foreach: entityCtx =>
      val perEntity = mergeProfile(ctx, projectCtx, Some(entityCtx))
      files += EmittedFile(
        s"src/types/${entityCtx.entityCamel}.ts",
        engine.renderAny(templates.typeEntity, perEntity)
      )
      files += EmittedFile(
        s"src/schemas/${entityCtx.entityCamel}.ts",
        engine.renderAny(templates.schemaEntity, perEntity)
      )
      files += EmittedFile(
        s"src/services/${entityCtx.entityCamel}.ts",
        engine.renderAny(templates.serviceEntity, perEntity)
      )
      files += EmittedFile(
        s"src/routes/${entityCtx.entityPluralCamel}.ts",
        engine.renderAny(templates.routeEntity, perEntity)
      )

    projectTail.foreach: (path, tpl) =>
      files += EmittedFile(path, engine.renderAny(tpl, projectScope))

    files += EmittedFile(
      "openapi.yaml",
      OpenApi.serialize(OpenApi.buildOpenApiDocument(profiled))
    )

    emitPrismaMigrations(profiled, opts, templates, engine, files)

    opts.dafnyKernel.foreach: kernel =>
      val pkg = kernel.packagePath.stripSuffix("/")
      kernel.files.toList.sortBy(_._1).foreach: (rel, content) =>
        files += EmittedFile(s"$pkg/$rel", content)

    files.result()

  private def emitPrismaMigrations(
      profiled: ProfiledService,
      opts: EmitOptions,
      templates: specrest.codegen.TsExpressPostgresTemplates,
      engine: TemplateEngine,
      files: scala.collection.mutable.Builder[EmittedFile, List[EmittedFile]]
  ): Unit =
    val schema = profiled.schema
    files += EmittedFile(
      ".spec-snapshot.json",
      SchemaCodec.encode(SchemaSnapshot.of(schema))
    )
    files += EmittedFile(
      "prisma/migrations/migration_lock.toml",
      engine.renderAny(templates.migrationLock, Map.empty[String, Any])
    )

    opts.previousSnapshot match
      case None =>
        val ops = schema.tables.map(MigrationOp.CreateTable.apply)
        val upScope = Map[String, Any](
          "migration" -> PrismaMigrationView(SqlRenderer.upgrade(ops))
        )
        val downScope = Map[String, Any](
          "migration" -> PrismaMigrationView(SqlRenderer.downgrade(ops))
        )
        files += EmittedFile(
          "prisma/migrations/001_initial_schema/migration.sql",
          engine.renderAny(templates.migrationSql, upScope)
        )
        files += EmittedFile(
          "prisma/migrations/001_initial_schema/down.sql",
          engine.renderAny(templates.migrationSql, downScope)
        )
      case Some(prev) =>
        val ops = SchemaDiff.compute(prev, schema)
        if ops.nonEmpty then
          val nextRev = Revision.next(opts.existingRevisions)
          val upScope = Map[String, Any](
            "migration" -> PrismaMigrationView(SqlRenderer.upgrade(ops))
          )
          val downScope = Map[String, Any](
            "migration" -> PrismaMigrationView(SqlRenderer.downgrade(ops))
          )
          files += EmittedFile(
            s"prisma/migrations/${nextRev}_schema_update/migration.sql",
            engine.renderAny(templates.migrationSql, upScope)
          )
          files += EmittedFile(
            s"prisma/migrations/${nextRev}_schema_update/down.sql",
            engine.renderAny(templates.migrationSql, downScope)
          )

  final private case class PrismaMigrationView(upgradeStatements: List[String])

  private def npmPackageName(serviceKebab: String): String =
    s"@generated/$serviceKebab"

  private def mergeProfile(
      ctx: RenderContext,
      proj: TsProjectCtx,
      currentEntity: Option[TsEntityCtx] = None
  ): Map[String, Any] =
    val base = Map[String, Any](
      "service"      -> proj.service,
      "packageName"  -> proj.packageName,
      "profile"      -> ctx.profile,
      "entities"     -> proj.entities,
      "needsDecimal" -> proj.needsDecimal,
      "needsBuffer"  -> proj.needsBuffer,
      "hasDafny"     -> proj.dafnyKernel.isDefined
    )
    currentEntity match
      case Some(e) =>
        base + ("entity" -> e) + ("entityCtx" -> e)
      case None => base

  private def buildEntityCtx(
      packageName: String,
      entity: ProfiledEntity,
      operations: List[TsOperation]
  ): TsEntityCtx =
    val entityCamel       = toCamelCase(entity.entityName)
    val entityPascal      = toPascalCase(entity.entityName)
    val entitySnake       = Naming.toSnakeCase(entity.entityName)
    val entityKebab       = Naming.toKebabCase(entity.entityName)
    val entityPlural      = Naming.pluralize(entity.entityName)
    val entityPluralCamel = toCamelCase(entityPlural)
    val entityPluralKebab = Naming.toKebabCase(entityPlural)

    val (primaryKey, nonIdFields) =
      entity.fields.find(_.fieldName == "id") match
        case Some(idField) =>
          val pk = toTsField(idField).copy(
            tsField = "id",
            jsonName = "id",
            columnName = "id",
            prismaAttrs = "@id @default(autoincrement())",
            isPrimaryKey = true
          )
          (pk, entity.fields.filterNot(_.fieldName == "id").map(toTsField))
        case None =>
          val pk = TsFieldView(
            tsField = "id",
            jsonName = "id",
            columnName = "id",
            domainType = "number",
            prismaType = "Int",
            prismaAttrs = "@id @default(autoincrement())",
            zodSchema = "z.number()",
            nullable = false,
            isPrimaryKey = true
          )
          (pk, entity.fields.map(toTsField))
    val allFields = primaryKey +: nonIdFields

    val needsDecimal = nonIdFields.exists(f => f.domainType.contains("Prisma.Decimal"))
    val needsBuffer  = nonIdFields.exists(f => f.domainType.contains("Buffer"))

    val customSchemas = operations.flatMap: op =>
      op.customRequestSchemaName.map(name => TsCustomSchema(name, op.customRequestFields))

    TsEntityCtx(
      service =
        TsServiceNames(entity.entityName, entitySnake, Naming.toKebabCase(entity.entityName)),
      packageName = packageName,
      entity = entity,
      entityCamel = entityCamel,
      entityPascal = entityPascal,
      entitySnake = entitySnake,
      entityKebab = entityKebab,
      entityPlural = entityPlural,
      entityPluralCamel = entityPluralCamel,
      entityPluralKebab = entityPluralKebab,
      primaryKey = primaryKey,
      fields = allFields,
      nonIdFields = nonIdFields,
      operations = operations,
      needsDecimal = needsDecimal,
      needsBuffer = needsBuffer,
      needsPrismaImport = needsDecimal,
      customSchemas = customSchemas
    )

  private def toTsField(f: ProfiledField): TsFieldView =
    val tsName = toCamelCase(f.fieldName)
    val attrs  = prismaAttrs(f, tsName)
    TsFieldView(
      tsField = tsName,
      jsonName = tsName,
      columnName = f.columnName,
      domainType = f.domainType,
      prismaType = prismaTypeFor(f.ormColumnType),
      prismaAttrs = attrs,
      zodSchema = zodSchemaFor(f),
      nullable = f.nullable,
      isPrimaryKey = false
    )

  private def prismaTypeFor(sqlColumnType: String): String =
    sqlColumnType.toUpperCase match
      case "TEXT"             => "String"
      case "VARCHAR"          => "String"
      case "INTEGER"          => "Int"
      case "BIGINT"           => "BigInt"
      case "SMALLINT"         => "Int"
      case "BOOLEAN"          => "Boolean"
      case "DOUBLE PRECISION" => "Float"
      case "REAL"             => "Float"
      case "DECIMAL"          => "Decimal"
      case "NUMERIC"          => "Decimal"
      case "TIMESTAMPTZ"      => "DateTime"
      case "TIMESTAMP"        => "DateTime"
      case "DATE"             => "DateTime"
      case "UUID"             => "String"
      case "BYTEA"            => "Bytes"
      case "JSONB"            => "Json"
      case "JSON"             => "Json"
      case _                  => "String"

  private def prismaAttrs(f: ProfiledField, tsName: String): String =
    val mapAttr =
      if f.columnName != tsName then s"""@map("${f.columnName}")"""
      else ""
    val nativeAttr = nativePrismaAttr(f.ormColumnType)
    val nullable   = if f.nullable then "?" else ""
    val parts      = List(mapAttr, nativeAttr).filter(_.nonEmpty)
    val attrs      = parts.mkString(" ")
    if attrs.isEmpty then nullable else (nullable + " " + attrs).trim

  private def nativePrismaAttr(sqlColumnType: String): String =
    sqlColumnType.toUpperCase match
      case "TEXT"             => "@db.Text"
      case "VARCHAR"          => "@db.VarChar"
      case "INTEGER"          => "@db.Integer"
      case "BIGINT"           => "@db.BigInt"
      case "SMALLINT"         => "@db.SmallInt"
      case "BOOLEAN"          => "@db.Boolean"
      case "DOUBLE PRECISION" => "@db.DoublePrecision"
      case "REAL"             => "@db.Real"
      case "DECIMAL"          => "@db.Decimal"
      case "NUMERIC"          => "@db.Decimal"
      case "TIMESTAMPTZ"      => "@db.Timestamptz()"
      case "TIMESTAMP"        => "@db.Timestamp()"
      case "DATE"             => "@db.Date"
      case "UUID"             => "@db.Uuid"
      case "BYTEA"            => "@db.ByteA"
      case "JSONB"            => "@db.JsonB"
      case "JSON"             => "@db.Json"
      case _                  => ""

  private def zodSchemaFor(f: ProfiledField): String =
    val base = baseZod(f.domainType)
    if f.nullable then s"$base.nullable()" else base

  private def baseZod(domainType: String): String =
    val stripped = domainType.replaceAll("\\s*\\|\\s*null$", "").trim
    stripped match
      case "string"         => "z.string()"
      case "number"         => "z.number()"
      case "boolean"        => "z.boolean()"
      case "Date"           => "z.coerce.date()"
      case "Buffer"         => "z.instanceof(Buffer)"
      case "Prisma.Decimal" => "z.union([z.string(), z.number()])"
      case other if other.endsWith("[]") =>
        s"z.array(${baseZod(other.dropRight(2))})"
      case _ => "z.string()"

  private val TsReservedNames: Set[String] =
    Set("class", "function", "default", "delete", "new", "return", "var", "let", "const")

  private def toCamelCase(name: String): String =
    val parts = name.split('_').toList.flatMap(p => Naming.splitCamelCase(p)).filter(_.nonEmpty)
    val joined = parts.zipWithIndex.map: (w, i) =>
      if i == 0 then w.toLowerCase
      else w.head.toUpper +: w.tail.toLowerCase
    .mkString
    if TsReservedNames.contains(joined) then s"${joined}_" else joined

  private def toPascalCase(name: String): String =
    val parts = name.split('_').toList.flatMap(p => Naming.splitCamelCase(p)).filter(_.nonEmpty)
    parts.map(w => w.head.toUpper +: w.tail.toLowerCase).mkString

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String]
  ): TsOperation =
    val endpoint = op.endpoint
    val pathParams = endpoint.pathParams.map: p =>
      TsPathParam(p.name, toCamelCase(p.name), tsTypeForParam(p.typeExpr, typeLookup))

    val nonIdFields      = entity.fields.filterNot(_.fieldName == "id").map(toTsField)
    val initialRouteKind = RouteKind.classify(op)
    val method           = endpoint.method.toString.toLowerCase
    val expressPath      = toExpressPath(endpoint.path)

    val entityNonIdColumnNames =
      entity.fields.filterNot(_.fieldName == "id").map(_.columnName).toSet
    val bodyParamNames = endpoint.bodyParams.map(_.name)
    val matchesEntityCreateShape =
      initialRouteKind == RouteKind.Create &&
        bodyParamNames.size == entityNonIdColumnNames.size &&
        bodyParamNames.forall(entityNonIdColumnNames.contains)

    val hasRequestBody = initialRouteKind == RouteKind.Create || endpoint.bodyParams.nonEmpty

    val (requestBodyType, customRequestSchemaName, customRequestFields) =
      if !hasRequestBody then
        ("", Option.empty[String], List.empty[TsFieldView])
      else if initialRouteKind == RouteKind.Create && matchesEntityCreateShape then
        (entity.createSchemaName, Option.empty[String], List.empty[TsFieldView])
      else
        val name           = s"${op.operationName}Request"
        val pathParamNames = endpoint.pathParams.map(_.name).toSet
        val fields = op.requestBodyFields
          .filterNot(f => pathParamNames.contains(f.fieldName))
          .map(toTsField)
        (name, Some(name), fields)

    val routeKind =
      if initialRouteKind == RouteKind.Create && !matchesEntityCreateShape then RouteKind.Other
      else initialRouteKind

    val readSchemaName = entity.readSchemaName
    val pathArgsCsv    = pathParams.map(_.tsName).mkString(", ")

    val lookupField = pathParams.headOption match
      case Some(p) if entity.fields.exists(_.columnName == p.name) =>
        toCamelCase(p.name)
      case _ => "id"

    val createDataObj = nonIdFields
      .map(f => s"${f.tsField}: body.${f.tsField}")
      .mkString(", ")

    val whereExpr = pathParams.headOption match
      case Some(p) => s"$lookupField: ${p.tsName}"
      case None    => "id: 0"

    val lookupIsPk = lookupField == "id"
    val readCall   = if lookupIsPk then "findUnique" else "findFirst"
    val deleteCall = if lookupIsPk then "delete" else "deleteMany"

    val (
      serviceMethodName,
      serviceCallArgs,
      serviceSig,
      serviceReturnType,
      redirectField,
      prismaCall
    ) =
      routeKind match
        case RouteKind.Create =>
          val sig = if hasRequestBody then s"body: $requestBodyType" else ""
          (
            op.operationName,
            "body",
            sig,
            s"Promise<$readSchemaName>",
            Option.empty[String],
            "create"
          )
        case RouteKind.Read =>
          val sig = pathParams.map(p => s"${p.tsName}: ${p.domainType}").mkString(", ")
          (
            op.operationName,
            pathArgsCsv,
            sig,
            s"Promise<$readSchemaName | null>",
            Option.empty[String],
            readCall
          )
        case RouteKind.List =>
          (
            op.operationName,
            "",
            "",
            s"Promise<$readSchemaName[]>",
            Option.empty[String],
            "findMany"
          )
        case RouteKind.Delete =>
          val sig = pathParams.map(p => s"${p.tsName}: ${p.domainType}").mkString(", ")
          (op.operationName, pathArgsCsv, sig, "Promise<boolean>", Option.empty[String], deleteCall)
        case RouteKind.Redirect =>
          val tgt = redirectTarget(op, entity).getOrElse("url")
          val sig = pathParams.map(p => s"${p.tsName}: ${p.domainType}").mkString(", ")
          (
            op.operationName,
            pathArgsCsv,
            sig,
            s"Promise<$readSchemaName | null>",
            Some(tgt),
            readCall
          )
        case RouteKind.Other =>
          val args = pathParams.map(p => s"${p.tsName}: ${p.domainType}") ++
            (if hasRequestBody then List(s"body: $requestBodyType") else Nil)
          val call = (pathParams.map(_.tsName) ++ (if hasRequestBody then List("body") else Nil))
            .mkString(", ")
          (op.operationName, call, args.mkString(", "), "Promise<void>", Option.empty[String], "")

    TsOperation(
      operationName = op.operationName,
      handlerName = toCamelCase(op.operationName),
      method = method,
      path = endpoint.path,
      expressPath = expressPath,
      successStatus = endpoint.successStatus,
      routeKind = routeKindName(routeKind),
      pathParams = pathParams,
      hasRequestBody = hasRequestBody,
      requestBodyType = requestBodyType,
      customRequestSchemaName = customRequestSchemaName,
      customRequestFields = customRequestFields,
      serviceMethod = serviceMethodName,
      serviceCallArgs = serviceCallArgs,
      serviceSignatureArgs = serviceSig,
      serviceReturnType = serviceReturnType,
      redirectField = redirectField,
      dafnyMethod = op.dafnyMethod,
      prismaCall = prismaCall,
      prismaWhere = whereExpr,
      prismaCreateData = createDataObj,
      lookupField = lookupField
    )

  private def toExpressPath(chiPath: String): String =
    """\{([^}]+)\}""".r.replaceAllIn(chiPath, m => ":" + toCamelCase(m.group(1)))

  private def redirectTarget(
      @scala.annotation.unused op: ProfiledOperation,
      entity: ProfiledEntity
  ): Option[String] =
    val candidates = List("url", "location", "redirect_url")
    candidates
      .find(c => entity.fields.exists(_.columnName == c))
      .map(toCamelCase)

  private def tsTypeForParam(typeExpr: type_expr_full, typeLookup: Map[String, String]): String =
    typeExpr match
      case NamedTypeF(n, _)      => typeLookup.getOrElse(n, "string")
      case OptionTypeF(inner, _) => s"${tsTypeForParam(inner, typeLookup)} | null"
      case _                     => "string"

  private def routeKindName(rk: RouteKind): String = rk match
    case RouteKind.Create   => "create"
    case RouteKind.Read     => "read"
    case RouteKind.List     => "list"
    case RouteKind.Delete   => "delete"
    case RouteKind.Redirect => "redirect"
    case RouteKind.Other    => "other"

  private def byPathSpecificity(a: TsOperation, b: TsOperation): Boolean =
    val aCount = a.path.count(_ == '{')
    val bCount = b.path.count(_ == '{')
    aCount < bCount
