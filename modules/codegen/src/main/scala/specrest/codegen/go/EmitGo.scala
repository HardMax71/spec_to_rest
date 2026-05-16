package specrest.codegen.go

import specrest.codegen.DafnyKernel
import specrest.codegen.EmitOptions
import specrest.codegen.EmittedFile
import specrest.codegen.GoTemplates
import specrest.codegen.RenderContext
import specrest.codegen.RouteKind
import specrest.codegen.TemplateEngine
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

final private case class GoCustomSchema(name: String, fields: List[GoFieldView])

final private case class GoFieldView(
    goField: String,
    jsonTag: String,
    dbTag: String,
    bunTag: String,
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
    lookupColumn: String,
    createAssigns: List[String]
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
    usesNotFound: Boolean,
    usesErrors: Boolean,
    customSchemas: List[GoCustomSchema]
)

final private case class GoServiceNames(name: String, snakeName: String, kebabName: String)

final private case class GoComposeEnv(key: String, value: String)

final private case class GoDbView(
    id: String,
    databaseImports: String,
    openStmt: String,
    bunNew: String,
    appDsn: String,
    appDsnCompose: String,
    migrateUrl: String,
    txBegin: String,
    txCommit: String,
    hasDbService: Boolean,
    dbImage: String,
    dbPort: String,
    dbVolumePath: String,
    dbHealthCmd: String,
    composeEnv: List[GoComposeEnv]
)

final private case class GoProjectCtx(
    service: GoServiceNames,
    module: String,
    entities: List[GoEntityCtx],
    needsTime: Boolean,
    needsUuid: Boolean,
    needsDecimal: Boolean,
    db: GoDbView,
    dafnyKernel: Option[DafnyKernel]
)

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
      buildEntityCtx(module, entity, entityOps)

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
      db = goDbView(profiled.profile.database, service.snakeName),
      dafnyKernel = opts.dafnyKernel
    )

    val files        = List.newBuilder[EmittedFile]
    val projectScope = mergeProfile(ctx, projectCtx)

    val projectFiles: List[(String, String)] = List(
      "go.mod"                        -> templates.goMod,
      "cmd/server/main.go"            -> templates.main,
      "internal/config/config.go"     -> templates.config,
      "internal/database/database.go" -> templates.database,
      "internal/handlers/common.go"   -> templates.handlerCommon,
      "internal/services/common.go"   -> templates.serviceCommon,
      "Dockerfile"                    -> templates.dockerfile,
      "docker-compose.yml"            -> templates.dockerCompose,
      ".env.example"                  -> templates.envExample,
      "Makefile"                      -> templates.makefile,
      ".gitignore"                    -> templates.gitignore,
      ".dockerignore"                 -> templates.dockerignore,
      "README.md"                     -> templates.readme,
      ".github/workflows/ci.yml"      -> templates.ciWorkflow,
      "tests/health_test.go"          -> templates.testHealth
    )

    projectFiles.foreach: (path, tpl) =>
      files += EmittedFile(path, engine.renderAny(tpl, projectScope))

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

    emitMigrationFiles(profiled, opts, templates, engine, projectCtx.db, files)

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

  private def importBlock(lines: List[String]): String =
    "\n" + lines.map(l => s"\t$l").mkString("\n")

  private val sqlOpenWithErr: String =
    "\n\tif err != nil {\n\t\treturn nil, err\n\t}"

  private def goDbView(database: String, snake: String): GoDbView = database match
    case "postgres" =>
      GoDbView(
        id = "postgres",
        databaseImports = importBlock(
          List(
            "\"github.com/uptrace/bun\"",
            "\"github.com/uptrace/bun/dialect/pgdialect\"",
            "\"github.com/uptrace/bun/driver/pgdriver\""
          )
        ),
        openStmt = "sqldb := sql.OpenDB(pgdriver.NewConnector(pgdriver.WithDSN(dsn)))",
        bunNew = "pgdialect.New()",
        appDsn = s"postgres://$snake:$snake@localhost:5432/$snake?sslmode=disable",
        appDsnCompose = s"postgres://$snake:$snake@db:5432/$snake?sslmode=disable",
        migrateUrl = s"postgres://$snake:$snake@localhost:5432/$snake?sslmode=disable",
        txBegin = "BEGIN;",
        txCommit = "COMMIT;",
        hasDbService = true,
        dbImage = "postgres:16-alpine",
        dbPort = "5432",
        dbVolumePath = "/var/lib/postgresql/data",
        dbHealthCmd = s"pg_isready -U $snake",
        composeEnv = List(
          GoComposeEnv("POSTGRES_USER", snake),
          GoComposeEnv("POSTGRES_PASSWORD", snake),
          GoComposeEnv("POSTGRES_DB", snake)
        )
      )
    case "sqlite" =>
      GoDbView(
        id = "sqlite",
        databaseImports = importBlock(
          List(
            "\"github.com/uptrace/bun\"",
            "\"github.com/uptrace/bun/dialect/sqlitedialect\"",
            "\"github.com/uptrace/bun/driver/sqliteshim\""
          )
        ),
        openStmt = s"sqldb, err := sql.Open(sqliteshim.ShimName, dsn)$sqlOpenWithErr",
        bunNew = "sqlitedialect.New()",
        appDsn = s"file:$snake.db?cache=shared&_pragma=foreign_keys(1)",
        appDsnCompose = s"file:$snake.db?cache=shared&_pragma=foreign_keys(1)",
        migrateUrl = s"sqlite://$snake.db",
        txBegin = "",
        txCommit = "",
        hasDbService = false,
        dbImage = "",
        dbPort = "",
        dbVolumePath = "",
        dbHealthCmd = "",
        composeEnv = Nil
      )
    case "mysql" =>
      GoDbView(
        id = "mysql",
        databaseImports = importBlock(
          List(
            "_ \"github.com/go-sql-driver/mysql\"",
            "\"github.com/uptrace/bun\"",
            "\"github.com/uptrace/bun/dialect/mysqldialect\""
          )
        ),
        openStmt = s"""sqldb, err := sql.Open("mysql", dsn)$sqlOpenWithErr""",
        bunNew = "mysqldialect.New()",
        appDsn = s"$snake:$snake@tcp(localhost:3306)/$snake?parseTime=true",
        appDsnCompose = s"$snake:$snake@tcp(db:3306)/$snake?parseTime=true",
        migrateUrl = s"mysql://$snake:$snake@tcp(localhost:3306)/$snake",
        txBegin = "",
        txCommit = "",
        hasDbService = true,
        dbImage = "mysql:8.4",
        dbPort = "3306",
        dbVolumePath = "/var/lib/mysql",
        dbHealthCmd = s"mysqladmin ping -h 127.0.0.1 -u $snake -p$snake --silent",
        composeEnv = List(
          GoComposeEnv("MYSQL_USER", snake),
          GoComposeEnv("MYSQL_PASSWORD", snake),
          GoComposeEnv("MYSQL_DATABASE", snake),
          GoComposeEnv("MYSQL_ROOT_PASSWORD", s"${snake}_root")
        )
      )
    case other =>
      throw new RuntimeException(
        s"No Go database view for '$other' (known: postgres, sqlite, mysql)"
      )

  private def emitMigrationFiles(
      profiled: ProfiledService,
      opts: EmitOptions,
      templates: specrest.codegen.GoChiPostgresTemplates,
      engine: TemplateEngine,
      db: GoDbView,
      files: scala.collection.mutable.Builder[EmittedFile, List[EmittedFile]]
  ): Unit =
    val schema  = profiled.schema
    val dialect = specrest.codegen.migration.Dialect.forDatabase(profiled.profile.database)
    files += EmittedFile(
      ".spec-snapshot.json",
      SchemaCodec.encode(SchemaSnapshot.of(schema))
    )

    val emitInitial: () => Unit = () =>
      val tableOps   = SchemaDiff.topoSort(schema.tables).map(MigrationOp.CreateTable.apply)
      val triggerOps = schema.triggers.map(MigrationOp.AddTrigger.apply)
      val ops        = tableOps ++ triggerOps
      val view = SqlMigrationView(
        upgradeStatements = SqlRenderer.upgrade(ops, dialect),
        downgradeStatements = SqlRenderer.downgrade(ops, dialect)
      )
      val scope = Map[String, Any](
        "migration" -> view,
        "txBegin"   -> db.txBegin,
        "txCommit"  -> db.txCommit
      )
      files += EmittedFile(
        "migrations/001_initial_schema.up.sql",
        engine.renderAny(templates.migrationUp, scope)
      )
      files += EmittedFile(
        "migrations/001_initial_schema.down.sql",
        engine.renderAny(templates.migrationDown, scope)
      )

    opts.previousSnapshot match
      case None                                      => emitInitial()
      case Some(_) if opts.existingRevisions.isEmpty => emitInitial()
      case Some(prev) =>
        val ops = SchemaDiff.compute(prev, schema)
        if ops.nonEmpty then
          val nextRev = Revision.next(opts.existingRevisions)
          val view = SqlMigrationView(
            upgradeStatements = SqlRenderer.upgrade(ops, dialect),
            downgradeStatements = SqlRenderer.downgrade(ops, dialect)
          )
          val scope = Map[String, Any](
            "migration" -> view,
            "txBegin"   -> db.txBegin,
            "txCommit"  -> db.txCommit
          )
          files += EmittedFile(
            s"migrations/${nextRev}_schema_update.up.sql",
            engine.renderAny(templates.migrationUp, scope)
          )
          files += EmittedFile(
            s"migrations/${nextRev}_schema_update.down.sql",
            engine.renderAny(templates.migrationDown, scope)
          )

  final private case class SqlMigrationView(
      upgradeStatements: List[String],
      downgradeStatements: List[String]
  )

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
      "db"           -> proj.db,
      "hasDafny"     -> proj.dafnyKernel.isDefined
    )
    currentEntity match
      case Some(e) =>
        base + ("entity" -> e) + ("entityCtx" -> e)
      case None => base

  private def buildEntityCtx(
      module: String,
      entity: ProfiledEntity,
      operations: List[GoOperation]
  ): GoEntityCtx =
    val entitySnake       = Naming.toSnakeCase(entity.entityName)
    val entityCamel       = toCamelCase(entity.entityName)
    val entityPlural      = Naming.pluralize(entity.entityName)
    val entityPluralSnake = Naming.toSnakeCase(entityPlural)

    val (primaryKey, nonIdFields) =
      entity.fields.find(_.fieldName == "id") match
        case Some(idField) =>
          val pk = toGoField(idField).copy(
            goField = "ID",
            bunTag = s"${idField.columnName},pk,autoincrement",
            sqlType = s"${idField.ormColumnType} PRIMARY KEY",
            isPrimaryKey = true
          )
          (pk, entity.fields.filterNot(_.fieldName == "id").map(toGoField))
        case None =>
          val pk = GoFieldView(
            goField = "ID",
            jsonTag = "id",
            dbTag = "id",
            bunTag = "id,pk,autoincrement",
            domainType = "int64",
            sqlType = "BIGSERIAL PRIMARY KEY",
            nullable = false,
            isSensitive = false,
            isPrimaryKey = true
          )
          (pk, entity.fields.map(toGoField))
    val allFields = primaryKey +: nonIdFields

    val needsTime = nonIdFields.exists: f =>
      f.domainType.contains("time.Time")
    val needsUuid = nonIdFields.exists: f =>
      f.domainType.contains("uuid.UUID")
    val needsDecimal = nonIdFields.exists: f =>
      f.domainType.contains("decimal.Decimal")

    val usesNotFound = operations.exists: o =>
      o.routeKind == "read" || o.routeKind == "redirect"
    val usesErrors = usesNotFound || operations.exists(_.routeKind == "other")

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
      usesNotFound = usesNotFound,
      usesErrors = usesErrors,
      customSchemas = customSchemas
    )

  private def toGoField(f: ProfiledField): GoFieldView =
    GoFieldView(
      goField = toPascalCase(f.fieldName),
      jsonTag = f.columnName,
      dbTag = f.columnName,
      bunTag = if f.nullable then f.columnName else s"${f.columnName},notnull",
      domainType = f.domainType,
      sqlType = sqlTypeFor(f.ormColumnType, f.nullable),
      nullable = f.nullable,
      isSensitive = false,
      isPrimaryKey = false
    )

  private def sqlTypeFor(base: String, nullable: Boolean): String =
    if nullable then base else s"$base NOT NULL"

  private def toPascalCase(name: String): String =
    Naming.toPascalCase(name, Naming.PascalStrategy.Go)

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String]
  ): GoOperation =
    val endpoint = op.endpoint
    val pathParams = endpoint.pathParams.map: p =>
      GoPathParam(p.name, toCamelCase(p.name), goTypeForParam(p.typeExpr, typeLookup))

    val nonIdFields   = entity.fields.filterNot(_.fieldName == "id").map(toGoField)
    val createAssigns = nonIdFields.map(f => s"${f.goField}: body.${f.goField}")
    val lookupCol = pathParams.headOption match
      case Some(p) if entity.fields.exists(_.columnName == p.name) => p.name
      case _                                                       => "id"

    val initialRouteKind = RouteKind.classify(op)
    val method           = endpoint.method.toString.toUpperCase
    val chiPath          = endpoint.path

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
        ("", Option.empty[String], List.empty[GoFieldView])
      else if initialRouteKind == RouteKind.Create && matchesEntityCreateShape then
        (entity.createSchemaName, Option.empty[String], List.empty[GoFieldView])
      else
        val name           = s"${op.operationName}Request"
        val pathParamNames = endpoint.pathParams.map(_.name).toSet
        val fields = op.requestBodyFields
          .filterNot(f => pathParamNames.contains(f.fieldName))
          .map(toGoField)
        (name, Some(name), fields)

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
      serviceSig
    ) =
      routeKind match
        case RouteKind.Create =>
          val sig = if hasRequestBody then s"body $requestBodyType" else ""
          val ret = readSchemaName
          (ret, Option.empty[String], ret, op.operationName, "body", sig)
        case RouteKind.Read =>
          (
            s"*$readSchemaName",
            Option.empty[String],
            s"*$readSchemaName",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature
          )
        case RouteKind.List =>
          (
            s"[]$readSchemaName",
            Option.empty[String],
            s"[]$readSchemaName",
            op.operationName,
            "",
            ""
          )
        case RouteKind.Delete =>
          (
            "",
            Option.empty[String],
            "bool",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature
          )
        case RouteKind.Redirect =>
          val tgt = redirectTarget(op, entity).getOrElse("URL")
          (
            "string",
            Some(tgt),
            s"*$readSchemaName",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature
          )
        case RouteKind.Other =>
          val args = pathParams.map(p => s"${p.goName} ${p.domainType}") ++
            (if hasRequestBody then List(s"body models.$requestBodyType") else Nil)
          val call = (pathParams.map(_.goName) ++ (if hasRequestBody then List("body") else Nil))
            .mkString(", ")
          ("", Option.empty[String], "error", op.operationName, call, args.mkString(", "))

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
      lookupColumn = lookupCol,
      createAssigns = createAssigns
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
    Naming.toCamelCase(name, Naming.CamelStrategy.Plain)

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
