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

final private case class TsPathParam(
    name: String,
    tsName: String,
    domainType: String,
    stmt: String
)

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
    needsResultCast: Boolean,
    customSchemas: List[TsCustomSchema]
)

final private case class TsServiceNames(name: String, snakeName: String, kebabName: String)

final private case class TsComposeEnv(key: String, value: String)

final private case class TsDbView(
    provider: String,
    appDsn: String,
    appDsnCompose: String,
    nativeAttrs: Boolean,
    hasDbService: Boolean,
    dbImage: String,
    dbPort: String,
    dbHealthCmd: String,
    dbVolumePath: String,
    composeEnv: List[TsComposeEnv]
)

final private case class TsProjectCtx(
    service: TsServiceNames,
    packageName: String,
    entities: List[TsEntityCtx],
    needsDecimal: Boolean,
    needsBuffer: Boolean,
    db: TsDbView,
    dafnyKernel: Option[DafnyKernel]
)

object EmitTs:

  private val NumericPrismaTypes: Set[String] = Set("Int", "BigInt", "Float", "Decimal")

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

    val triggerMaintainedByTable: Map[String, Set[String]] =
      profiled.schema.triggers
        .groupBy(_.targetTable)
        .view
        .mapValues(_.map(_.targetColumn).toSet)
        .toMap

    val db = tsDbView(profiled.profile.database, service.snakeName)

    val entities = profiled.entities.map: entity =>
      val entityOps = profiled.operations
        .filter(_.targetEntity.contains(entity.entityName))
        .map(op => enrichOperation(op, entity, typeLookup, db.nativeAttrs))
        .sortWith(byPathSpecificity)
      val maintained = triggerMaintainedByTable.getOrElse(entity.tableName, Set.empty)
      buildEntityCtx(packageName, entity, entityOps, maintained, db.nativeAttrs)

    val needsDecimal = entities.exists(_.needsDecimal)
    val needsBuffer  = entities.exists(_.needsBuffer)

    val projectCtx = TsProjectCtx(
      service = service,
      packageName = packageName,
      entities = entities,
      needsDecimal = needsDecimal,
      needsBuffer = needsBuffer,
      db = db,
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

    emitPrismaMigrations(profiled, opts, templates, engine, projectCtx.db, files)

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
      db: TsDbView,
      files: scala.collection.mutable.Builder[EmittedFile, List[EmittedFile]]
  ): Unit =
    val schema  = profiled.schema
    val dialect = specrest.codegen.migration.Dialect.forDatabase(profiled.profile.database)
    files += EmittedFile(
      ".spec-snapshot.json",
      SchemaCodec.encode(SchemaSnapshot.of(schema))
    )
    files += EmittedFile(
      "prisma/migrations/migration_lock.toml",
      engine.renderAny(templates.migrationLock, Map[String, Any]("db" -> db))
    )

    val emitInitial: () => Unit = () =>
      val tableOps   = SchemaDiff.topoSort(schema.tables).map(MigrationOp.CreateTable.apply)
      val triggerOps = schema.triggers.map(MigrationOp.AddTrigger.apply)
      val ops        = tableOps ++ triggerOps
      val upScope = Map[String, Any](
        "migration" -> PrismaMigrationView(SqlRenderer.upgrade(ops, dialect))
      )
      val downScope = Map[String, Any](
        "migration" -> PrismaMigrationView(SqlRenderer.downgrade(ops, dialect))
      )
      files += EmittedFile(
        "prisma/migrations/001_initial_schema/migration.sql",
        engine.renderAny(templates.migrationSql, upScope)
      )
      files += EmittedFile(
        "prisma/migrations/001_initial_schema/down.sql",
        engine.renderAny(templates.migrationSql, downScope)
      )

    opts.previousSnapshot match
      case None                                      => emitInitial()
      case Some(_) if opts.existingRevisions.isEmpty => emitInitial()
      case Some(prev) =>
        val ops = SchemaDiff.compute(prev, schema)
        if ops.nonEmpty then
          val nextRev = Revision.next(opts.existingRevisions)
          val upScope = Map[String, Any](
            "migration" -> PrismaMigrationView(SqlRenderer.upgrade(ops, dialect))
          )
          val downScope = Map[String, Any](
            "migration" -> PrismaMigrationView(SqlRenderer.downgrade(ops, dialect))
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

  private def tsDbView(database: String, snake: String): TsDbView = database match
    case "postgres" =>
      val dv = specrest.codegen.migration.Postgres.deployment(snake)
      TsDbView(
        provider = "postgresql",
        appDsn = s"postgresql://$snake:$snake@localhost:5432/$snake?schema=public",
        appDsnCompose = s"postgresql://$snake:$snake@db:5432/$snake?schema=public",
        nativeAttrs = true,
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbHealthCmd = dv.dbHealthCmd,
        dbVolumePath = dv.dbVolumePath,
        composeEnv = dv.composeEnv.map(e => TsComposeEnv(e.key, e.value))
      )
    case "sqlite" =>
      val dv = specrest.codegen.migration.Sqlite.deployment(snake)
      TsDbView(
        provider = "sqlite",
        appDsn = s"file:./$snake.db",
        appDsnCompose = s"file:./$snake.db",
        nativeAttrs = false,
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbHealthCmd = dv.dbHealthCmd,
        dbVolumePath = dv.dbVolumePath,
        composeEnv = dv.composeEnv.map(e => TsComposeEnv(e.key, e.value))
      )
    case "mysql" =>
      val dv = specrest.codegen.migration.Mysql.deployment(snake)
      TsDbView(
        provider = "mysql",
        appDsn = s"mysql://$snake:$snake@localhost:3306/$snake",
        appDsnCompose = s"mysql://$snake:$snake@db:3306/$snake",
        nativeAttrs = false,
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbHealthCmd = dv.dbHealthCmd,
        dbVolumePath = dv.dbVolumePath,
        composeEnv = dv.composeEnv.map(e => TsComposeEnv(e.key, e.value))
      )
    case other =>
      throw new RuntimeException(
        s"No TS database view for '$other' (known: postgres, sqlite, mysql)"
      )

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
      "db"           -> proj.db,
      "hasDafny"     -> proj.dafnyKernel.isDefined
    )
    currentEntity match
      case Some(e) =>
        base + ("entity" -> e) + ("entityCtx" -> e)
      case None => base

  private def buildEntityCtx(
      packageName: String,
      entity: ProfiledEntity,
      operations: List[TsOperation],
      triggerMaintainedColumns: Set[String],
      nativeAttrs: Boolean
  ): TsEntityCtx =
    val entityCamel       = toCamelCase(entity.entityName)
    val entityPascal      = toPascalCase(entity.entityName)
    val entitySnake       = Naming.toSnakeCase(entity.entityName)
    val entityKebab       = Naming.toKebabCase(entity.entityName)
    val entityPlural      = Naming.pluralize(entity.entityName)
    val entityPluralCamel = toCamelCase(entityPlural)
    val entityPluralKebab = Naming.toKebabCase(entityPlural)

    def mkField(f: ProfiledField): TsFieldView =
      val base = toTsField(f, nativeAttrs)
      if triggerMaintainedColumns.contains(f.columnName)
        && NumericPrismaTypes.contains(base.prismaType)
      then
        val attrs =
          if base.prismaAttrs.isEmpty then "@default(0)"
          else s"${base.prismaAttrs} @default(0)"
        base.copy(prismaAttrs = attrs)
      else base

    val (primaryKey, nonIdFields) =
      entity.fields.find(_.fieldName == "id") match
        case Some(idField) =>
          val pk = toTsField(idField, nativeAttrs).copy(
            tsField = "id",
            jsonName = "id",
            columnName = "id",
            prismaAttrs = "@id @default(autoincrement())",
            isPrimaryKey = true
          )
          (pk, entity.fields.filterNot(_.fieldName == "id").map(mkField))
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
          (pk, entity.fields.map(mkField))
    val allFields = primaryKey +: nonIdFields

    val needsDecimal = nonIdFields.exists(f => f.domainType.contains("Prisma.Decimal"))
    val needsBuffer  = nonIdFields.exists(f => f.domainType.contains("Buffer"))
    // Prisma types a Json column as JsonValue, which does not match the zod-derived
    // read DTO (e.g. string[]); the service must cast the row at the boundary.
    val needsResultCast = allFields.exists(_.prismaType == "Json")

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
      needsResultCast = needsResultCast,
      customSchemas = customSchemas
    )

  private def toTsField(f: ProfiledField, nativeAttrs: Boolean): TsFieldView =
    val tsName = toCamelCase(f.fieldName)
    val attrs  = prismaAttrs(f, tsName, nativeAttrs)
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

  final private case class PrismaMapping(typeName: String, dbAttr: String)

  private val PrismaSqlTypes: Map[String, PrismaMapping] = Map(
    "TEXT"             -> PrismaMapping("String", "@db.Text"),
    "VARCHAR"          -> PrismaMapping("String", "@db.VarChar"),
    "INTEGER"          -> PrismaMapping("Int", "@db.Integer"),
    "BIGINT"           -> PrismaMapping("BigInt", "@db.BigInt"),
    "SMALLINT"         -> PrismaMapping("Int", "@db.SmallInt"),
    "BOOLEAN"          -> PrismaMapping("Boolean", "@db.Boolean"),
    "DOUBLE PRECISION" -> PrismaMapping("Float", "@db.DoublePrecision"),
    "REAL"             -> PrismaMapping("Float", "@db.Real"),
    "DECIMAL"          -> PrismaMapping("Decimal", "@db.Decimal"),
    "NUMERIC"          -> PrismaMapping("Decimal", "@db.Decimal"),
    "TIMESTAMPTZ"      -> PrismaMapping("DateTime", "@db.Timestamptz()"),
    "TIMESTAMP"        -> PrismaMapping("DateTime", "@db.Timestamp()"),
    "DATE"             -> PrismaMapping("DateTime", "@db.Date"),
    "UUID"             -> PrismaMapping("String", "@db.Uuid"),
    "BYTEA"            -> PrismaMapping("Bytes", "@db.ByteA"),
    "JSONB"            -> PrismaMapping("Json", "@db.JsonB"),
    "JSON"             -> PrismaMapping("Json", "@db.Json")
  )

  private def prismaTypeFor(sqlColumnType: String): String =
    PrismaSqlTypes.get(sqlColumnType.toUpperCase).map(_.typeName).getOrElse("String")

  private def prismaAttrs(f: ProfiledField, tsName: String, nativeAttrs: Boolean): String =
    val mapAttr =
      if f.columnName != tsName then s"""@map("${f.columnName}")"""
      else ""
    val nativeAttr = if nativeAttrs then nativePrismaAttr(f.ormColumnType) else ""
    List(mapAttr, nativeAttr).filter(_.nonEmpty).mkString(" ")

  private def nativePrismaAttr(sqlColumnType: String): String =
    PrismaSqlTypes.get(sqlColumnType.toUpperCase).map(_.dbAttr).getOrElse("")

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

  private def toCamelCase(name: String): String =
    Naming.toCamelCase(name, Naming.CamelStrategy.Ts)

  private def toPascalCase(name: String): String =
    Naming.toPascalCase(name, Naming.PascalStrategy.Plain)

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String],
      nativeAttrs: Boolean
  ): TsOperation =
    val endpoint = op.endpoint
    val pathParams = endpoint.pathParams.map: p =>
      val tsType = tsTypeForParam(p.typeExpr, typeLookup)
      val nm     = toCamelCase(p.name)
      val stmt =
        if tsType == "number" then
          s"""      const $nm = Number(req.params['${p.name}']);
             |      if (!Number.isInteger($nm)) {
             |        throw NotFound();
             |      }""".stripMargin
        else s"      const $nm = req.params['${p.name}'] ?? '';"
      TsPathParam(p.name, nm, tsType, stmt)

    val nonIdFields      = entity.fields.filterNot(_.fieldName == "id").map(toTsField(_, nativeAttrs))
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
          .map(toTsField(_, nativeAttrs))
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
