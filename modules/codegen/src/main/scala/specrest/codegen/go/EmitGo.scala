package specrest.codegen.go

import specrest.codegen.AuthSchemes
import specrest.codegen.Compose
import specrest.codegen.DafnyKernel
import specrest.codegen.EmitOptions
import specrest.codegen.EmitShared
import specrest.codegen.EmittedFile
import specrest.codegen.EnvExample
import specrest.codegen.ExtensionStub
import specrest.codegen.GoTemplates
import specrest.codegen.OperationContext
import specrest.codegen.Pagination
import specrest.codegen.RenderContext
import specrest.codegen.ScalarOpView
import specrest.codegen.ScalarOps
import specrest.codegen.ScalarStateFieldView
import specrest.codegen.TemplateEngine
import specrest.codegen.migration.MigrationPlan
import specrest.codegen.migration.SchemaCodec
import specrest.codegen.migration.SchemaDiff
import specrest.codegen.migration.SchemaSnapshot
import specrest.codegen.migration.SqlRenderer
import specrest.codegen.openapi.OpenApi
import specrest.convention.StringRefinements
import specrest.ir.HttpMethods
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final private case class GoCustomSchema(
    name: String,
    fields: List[GoFieldView],
    structLines: List[String]
)

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

final private case class GoPathParam(
    name: String,
    goName: String,
    domainType: String,
    isInt: Boolean
)

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
    customRules: List[GoValidation.FieldRule],
    bodyValidates: Boolean,
    responseType: String,
    serviceMethod: String,
    serviceCallArgs: String,
    serviceSignatureArgs: String,
    serviceReturnType: String,
    redirectField: Option[String],
    dafnyMethod: Option[String],
    routeThroughKernel: Boolean,
    kernelServiceMethod: String,
    kernelGuardStatus: Int,
    kernelGuardDetail: String,
    kernelRedirect: Boolean,
    kernelHandlerArgs: String,
    lookupColumn: String,
    createAssigns: List[String],
    authMiddleware: Option[String]
)

final private case class GoEntityCtx(
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
    needsStrconv: Boolean,
    needsValidator: Boolean,
    hasOperations: Boolean,
    usesRequestBody: Boolean,
    usesPathParams: Boolean,
    usesNotFound: Boolean,
    usesErrors: Boolean,
    usesSqlNoRows: Boolean,
    usesKernel: Boolean,
    usesModels: Boolean,
    customSchemas: List[GoCustomSchema],
    validationBlock: String,
    modelImports: String,
    modelStructLines: List[String],
    createStructLines: List[String],
    handlerImports: String
)

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
    composeEnv: List[specrest.codegen.migration.ComposeEnv],
    dsnRecipe: Option[specrest.codegen.Dsn.Recipe]
)

final private case class GoScalarOpView(
    handlerName: String,
    methodPascal: String,
    chiPath: String,
    successStatus: Int,
    setSql: String,
    whereSql: String,
    guardPretty: String,
    authMiddleware: Option[String]
)

final private case class GoProjectCtx(
    service: specrest.codegen.ServiceNames,
    module: String,
    entities: List[GoEntityCtx],
    needsTime: Boolean,
    needsUuid: Boolean,
    needsDecimal: Boolean,
    db: GoDbView,
    dafnyKernel: Option[DafnyKernel],
    scalarOps: List[GoScalarOpView],
    authConfigLines: List[String]
)

object EmitGo:

  def emit(profiled: ProfiledService, opts: EmitOptions): List[EmittedFile] =
    val engine    = new TemplateEngine
    val templates = GoTemplates.goChiPostgres
    val ctx       = RenderContext.buildRenderContext(profiled, opts.dafnyKernel)
    val module    = goModuleName(ctx.service.kebabName)
    val service   = ctx.service

    val typeLookup = EmitShared.aliasResolvedDomainLookup(profiled)
    val bridgePlan = StateBridgeGo.plan(profiled)
    val kernelCtx = GoKernelCtx(
      stateReady = irStateFields(profiled.ir).isEmpty || bridgePlan.isRight,
      hasState = bridgePlan.toOption.exists(_.hasState),
      hasInv = irStateFields(profiled.ir).nonEmpty,
      ir = profiled.ir
    )

    val entities = profiled.entities.map: entity =>
      val entityOps = profiled.operations
        .filter(_.targetEntity.contains(entity.entityName))
        .map(op => enrichOperation(op, entity, typeLookup, kernelCtx))
        .sortWith((a, b) => EmitShared.byPathSpecificity(a.path, b.path))
      buildEntityCtx(module, profiled, entity, entityOps)

    val needsTime    = entities.exists(_.needsTime)
    val needsUuid    = entities.exists(_.needsUuid)
    val needsDecimal = entities.exists(_.needsDecimal)

    val scalarFields = ScalarOps.stateFields(profiled)
    val scalarOps    = ScalarOps.views(profiled).map(goScalarOp)

    val projectCtx = GoProjectCtx(
      service = service,
      module = module,
      entities = entities,
      needsTime = needsTime,
      needsUuid = needsUuid,
      needsDecimal = needsDecimal,
      db = goDbView(profiled.profile.database, service.snakeName),
      dafnyKernel = opts.dafnyKernel,
      scalarOps = scalarOps,
      authConfigLines = padCells(SecurityGo.configLines(profiled.ir))
    )

    val files        = List.newBuilder[EmittedFile]
    val projectScope = mergeProfile(ctx, projectCtx)

    val kernelRouted = entities.exists(_.operations.exists(_.routeThroughKernel))
    if kernelRouted && kernelCtx.hasState then
      files += EmittedFile(
        "internal/services/state_bridge.go",
        StateBridgeGo.emit(profiled, module)
      )

    val projectFiles: List[(String, String)] = List(
      "go.mod"                        -> templates.goMod,
      "cmd/server/main.go"            -> templates.main,
      "internal/config/config.go"     -> templates.config,
      "internal/auth/auth.go"         -> templates.auth,
      "internal/database/database.go" -> templates.database,
      "internal/models/common.go"     -> templates.modelCommon,
      "internal/handlers/common.go"   -> templates.handlerCommon,
      "internal/services/common.go"   -> templates.serviceCommon,
      "Dockerfile"                    -> templates.dockerfile,
      "Makefile"                      -> templates.makefile,
      ".gitignore"                    -> templates.gitignore,
      ".dockerignore"                 -> templates.dockerignore,
      "README.md"                     -> templates.readme,
      ".github/workflows/ci.yml"      -> templates.ciWorkflow,
      "tests/health_test.go"          -> templates.testHealth
    )

    projectFiles.foreach: (path, tpl) =>
      files += EmittedFile(path, engine.renderAny(tpl, projectScope))

    val composeIn = composeInputs(projectCtx.db)
    files += EmittedFile("docker-compose.yml", Compose.base(composeIn).yaml)
    files += EmittedFile(
      "docker-compose.override.yml.example",
      Compose.overrideExample(composeIn).yaml
    )
    files += EmittedFile(
      "docker-compose.staging.yml",
      Compose.staging(composeIn).yaml,
      preserve = true
    )
    files += EmittedFile("docker-compose.prod.yml", Compose.prod(composeIn).yaml, preserve = true)
    val authEnv = AuthSchemes.envEntries(profiled.ir).map((k, v) => EnvExample.Entry(k, v))
    files += EmittedFile(".env.example", EnvExample.render(composeIn, authEnv))

    files += EmittedFile(
      "internal/extensions/extensions.go",
      ExtensionStub.go,
      preserve = true
    )

    files += EmittedFile("internal/admin/admin.go", AdminRouterGo.emit(profiled))
    if svcSecurity(profiled.ir).nonEmpty then
      files += EmittedFile("internal/auth/schemes.go", SecurityGo.emit(profiled, module))

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

    if scalarFields.nonEmpty then
      files += EmittedFile("internal/models/service_state.go", goStateModel(scalarFields))
    if scalarOps.nonEmpty then
      files += EmittedFile(
        "internal/services/state_ops.go",
        goStateService(module, scalarFields, scalarOps)
      )
      files += EmittedFile("internal/handlers/state_ops.go", goStateHandler(module, scalarOps))

    emitMigrationFiles(profiled, opts, templates, engine, projectCtx.db, files)

    files += EmittedFile(
      "openapi.yaml",
      OpenApi.serialize(OpenApi.buildOpenApiDocument(profiled))
    )

    opts.dafnyKernel.foreach: kernel =>
      val pkg = kernel.packagePath.stripSuffix("/")
      DafnyKernel.rewriteGoImports(kernel.files, module, pkg).toList.sortBy(_._1).foreach:
        (rel, content) => files += EmittedFile(s"$pkg/$rel", content)
      files += EmittedFile(
        s"$pkg/adapter.go",
        engine.renderAny(templates.dafnyAdapter, Map("module" -> module))
      )

    files.result()

  private def composeInputs(db: GoDbView): Compose.Inputs =
    Compose.Inputs(
      family = Compose.Family.GoTs,
      appPort = 8080,
      dbVolumeName = "dbdata",
      hasDbService = db.hasDbService,
      dbImage = db.dbImage,
      dbPort = db.dbPort,
      dbVolumePath = db.dbVolumePath,
      dbHealthCmd = db.dbHealthCmd,
      secretEnv = db.composeEnv.map(e => e.key -> e.value),
      dsnComposeNetwork = db.appDsnCompose,
      dsnRecipe = db.dsnRecipe,
      envExampleHeaderLine = None
    )

  private def goModuleName(serviceKebab: String): String =
    s"github.com/generated/$serviceKebab"

  private def importBlock(lines: List[String]): String =
    "\n" + lines.map(l => s"\t$l").mkString("\n")

  private val sqlOpenWithErr: String =
    "\n\tif err != nil {\n\t\treturn nil, err\n\t}"

  private def goDbView(database: String, snake: String): GoDbView = database match
    case "postgres" =>
      val dv = specrest.codegen.migration.Postgres.deployment(snake)
      val recipe = specrest.codegen.Dsn
        .postgresRecipe(specrest.codegen.Dsn.Shape.Url("postgres"), "?sslmode=disable")
      GoDbView(
        id = dv.id,
        databaseImports = importBlock(
          List(
            "\"github.com/uptrace/bun\"",
            "\"github.com/uptrace/bun/dialect/pgdialect\"",
            "\"github.com/uptrace/bun/driver/pgdriver\""
          )
        ),
        openStmt = "sqldb := sql.OpenDB(pgdriver.NewConnector(pgdriver.WithDSN(dsn)))",
        bunNew = "pgdialect.New()",
        appDsn = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
        appDsnCompose = specrest.codegen.Dsn.renderDev(recipe, host = "db", snake),
        migrateUrl = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
        txBegin = "BEGIN;",
        txCommit = "COMMIT;",
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbVolumePath = dv.dbVolumePath,
        dbHealthCmd = dv.dbHealthCmd,
        composeEnv = dv.composeEnv,
        dsnRecipe = Some(recipe)
      )
    case "sqlite" =>
      val dv = specrest.codegen.migration.Sqlite.deployment(snake)
      GoDbView(
        id = dv.id,
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
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbVolumePath = dv.dbVolumePath,
        dbHealthCmd = dv.dbHealthCmd,
        composeEnv = dv.composeEnv,
        dsnRecipe = None
      )
    case "mysql" =>
      val dv = specrest.codegen.migration.Mysql.deployment(snake)
      val recipe = specrest.codegen.Dsn
        .mysqlRecipe(specrest.codegen.Dsn.Shape.MysqlGo, "?parseTime=true")
      GoDbView(
        id = dv.id,
        databaseImports = importBlock(
          List(
            "_ \"github.com/go-sql-driver/mysql\"",
            "\"github.com/uptrace/bun\"",
            "\"github.com/uptrace/bun/dialect/mysqldialect\""
          )
        ),
        openStmt = s"""sqldb, err := sql.Open("mysql", dsn)$sqlOpenWithErr""",
        bunNew = "mysqldialect.New()",
        appDsn = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
        appDsnCompose = specrest.codegen.Dsn.renderDev(recipe, host = "db", snake),
        migrateUrl = s"mysql://$snake:$snake@tcp(localhost:3306)/$snake",
        txBegin = "",
        txCommit = "",
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbVolumePath = dv.dbVolumePath,
        dbHealthCmd = dv.dbHealthCmd,
        composeEnv = dv.composeEnv,
        dsnRecipe = Some(recipe)
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
      val tableOps   = SchemaDiff.topoSort(schemaTables(schema)).map(CreateTable.apply)
      val triggerOps = schemaTriggers(schema).map(AddTrigger.apply)
      val ops        = tableOps ++ triggerOps
      val seeds = SchemaDiff
        .topoSort(schemaTables(schema))
        .filter(ScalarOps.isStateTable)
        .map(t => ScalarOps.seedSqlFor(t) + ";")
      val view = SqlMigrationView(
        upgradeStatements = SqlRenderer.upgrade(ops, dialect) ++ seeds,
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

    MigrationPlan.of(opts.previousSnapshot, opts.existingRevisions, schema) match
      case MigrationPlan.Initial  => emitInitial()
      case MigrationPlan.UpToDate => ()
      case MigrationPlan.Delta(ops, nextRev) =>
        val deltaSeeds = ScalarOps.deltaStateSeeds(ops).map(_ + ";")
        val view = SqlMigrationView(
          upgradeStatements = SqlRenderer.upgrade(ops, dialect) ++ deltaSeeds,
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
      "service"           -> proj.service,
      "module"            -> proj.module,
      "profile"           -> ctx.profile,
      "entities"          -> proj.entities,
      "needsTime"         -> proj.needsTime,
      "needsUuid"         -> proj.needsUuid,
      "needsDecimal"      -> proj.needsDecimal,
      "db"                -> proj.db,
      "hasDafny"          -> proj.dafnyKernel.isDefined,
      "scalarOps"         -> proj.scalarOps,
      "scalarStateFields" -> ctx.scalarStateFields,
      "hasScalarOps"      -> ctx.hasScalarOps,
      "needsJwt"          -> ctx.needsJwt,
      "authConfigLines"   -> proj.authConfigLines,
      "pagination"        -> Pagination.view,
      "pins"              -> ctx.pins
    )
    currentEntity match
      case Some(e) =>
        base + ("entity" -> e) + ("entityCtx" -> e)
      case None => base

  private def buildEntityCtx(
      module: String,
      profiled: ProfiledService,
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

    // The handler's `errors` import covers `errors.Is(ErrNotFound)` in the read AND redirect
    // routes (the redirect route still maps a not-found error to 404). The service's
    // `database/sql` import is needed only by the `read` branch's `sql.ErrNoRows` — a redirect
    // op is now a fail-loud stub that does no row lookup, so it must not pull in `database/sql`.
    // A kernel-routed op emits the kernel branch instead of its route-kind body, so its original
    // route kind must not pull in the route-kind imports (errors / database/sql) it no longer uses.
    def effectiveKind(o: GoOperation): String =
      if o.routeThroughKernel then "kernel" else o.routeKind
    val usesNotFound = operations.exists: o =>
      effectiveKind(o) == "read" || effectiveKind(o) == "redirect"
    // Kernel handlers errors.Is the precondition sentinel, so the handler
    // file needs the errors import even when no route-kind body does; the
    // services file must not (its sentinels live in common.go).
    val handlerUsesErrors = usesNotFound || operations.exists(_.routeThroughKernel)
    val usesErrors        = usesNotFound || operations.exists(effectiveKind(_) == "other")
    val usesSqlNoRows     = operations.exists(effectiveKind(_) == "read")
    val usesKernel        = operations.exists(_.routeThroughKernel)
    // The `models` import is referenced by every CRUD route-kind body (entity model query/return)
    // and by any op carrying a request body. A kernel-routed scalar op without a body, or an
    // `other` stub without a body, never names a models type — so gating on hasOperations alone
    // leaves the import unused (a hard Go compile error) for such entities.
    val usesModels = operations.exists: o =>
      o.hasRequestBody || (!o.routeThroughKernel && o.routeKind != "other")
    val usesRequestBody = operations.exists(_.hasRequestBody)
    val usesPathParams  = operations.exists(_.pathParams.nonEmpty)

    val customSchemas = operations.flatMap: op =>
      op.customRequestSchemaName.map(name =>
        GoCustomSchema(name, op.customRequestFields, schemaStructLines(op.customRequestFields))
      )

    // Validators emit only for request types some handler actually decodes;
    // an unused create schema would otherwise carry dead validation code.
    val usedBodyTypes = operations.filter(_.hasRequestBody).map(_.requestBodyType).toSet
    val createRules = entity.fields
      .filterNot(_.fieldName == "id")
      .map(f => GoValidation.FieldRule(f, EmitShared.entityFieldRefinement(profiled, entity, f)))
    val structRules =
      (Option
        .when(usedBodyTypes.contains(entity.createSchemaName))(
          entity.createSchemaName -> createRules
        )
        .toList :::
        operations
          .filter(o => o.customRequestSchemaName.isDefined && o.customRules.nonEmpty)
          .map(o => o.requestBodyType -> o.customRules)).distinctBy(_._1)
    val fileValidations = GoValidation.forStructs(entity.entityName, structRules)
    val validatedNames  = fileValidations.structs.map(_.structName).toSet
    val opsWithValidation = operations.map: o =>
      o.copy(bodyValidates = o.hasRequestBody && validatedNames.contains(o.requestBodyType))

    GoEntityCtx(
      module = module,
      entity = entity,
      entitySnake = entitySnake,
      entityCamel = entityCamel,
      entityPlural = entityPlural,
      entityPluralSnake = entityPluralSnake,
      primaryKey = primaryKey,
      fields = allFields,
      nonIdFields = nonIdFields,
      operations = opsWithValidation,
      needsTime = needsTime,
      needsUuid = needsUuid,
      needsDecimal = needsDecimal,
      needsStrconv = operations.exists(_.pathParams.exists(_.isInt)),
      needsValidator = nonIdFields.nonEmpty,
      hasOperations = operations.nonEmpty,
      usesRequestBody = usesRequestBody,
      usesPathParams = usesPathParams,
      usesNotFound = usesNotFound,
      usesErrors = usesErrors,
      usesSqlNoRows = usesSqlNoRows,
      usesKernel = usesKernel,
      usesModels = usesModels,
      customSchemas = customSchemas,
      validationBlock =
        if fileValidations.isEmpty then ""
        else
          "\n" + (fileValidations.patternVars.mkString("\n") ::
            fileValidations.structs.map(_.funcText)).filter(_.nonEmpty).mkString("\n\n")
      ,
      modelImports = {
        val stdlib = List(
          Option.when(!fileValidations.isEmpty)("\"fmt\""),
          Option.when(fileValidations.needsRegexp)("\"regexp\""),
          Option.when(needsTime)("\"time\""),
          Option.when(fileValidations.needsUtf8)("\"unicode/utf8\"")
        ).flatten
        val third =
          Option.when(needsUuid)("\"github.com/google/uuid\"").toList :::
            Option.when(needsDecimal)("\"github.com/shopspring/decimal\"").toList :::
            List("\"github.com/uptrace/bun\"")
        val groups = List(stdlib, third).filter(_.nonEmpty)
        groups.map(_.map("\t" + _).mkString("\n")).mkString("import (\n", "\n\n", "\n)")
      },
      modelStructLines = padCells(
        List(
          "bun.BaseModel",
          s"`bun:\"table:${entity.tableName},alias:${entity.tableName}\"`"
        ) :: allFields.map(f =>
          List(f.goField, f.domainType, s"`bun:\"${f.bunTag}\" json:\"${f.jsonTag}\"`")
        )
      ),
      createStructLines = schemaStructLines(nonIdFields),
      handlerImports = {
        val stdlib = List(
          Option.when(usesRequestBody)("\"encoding/json\""),
          Option.when(handlerUsesErrors)("\"errors\""),
          Option.when(operations.nonEmpty)("\"net/http\""),
          Option.when(operations.exists(_.pathParams.exists(_.isInt)))("\"strconv\"")
        ).flatten
        val third =
          Option.when(usesRequestBody)(s"\"$module/internal/models\"").toList :::
            List(s"\"$module/internal/services\"") :::
            Option.when(usesPathParams)("\"github.com/go-chi/chi/v5\"").toList
        val groups = List(stdlib, third).filter(_.nonEmpty)
        groups.map(_.map("\t" + _).mkString("\n")).mkString("import (\n", "\n\n", "\n)")
      }
    )

  private def schemaStructLines(fields: List[GoFieldView]): List[String] =
    padCells(
      fields.map: f =>
        val validate = if f.nullable then "" else " validate:\"required\""
        List(f.goField, f.domainType, s"`json:\"${f.jsonTag}\"$validate`")
    )

  // gofmt column alignment for struct field runs: every cell except a row's
  // last is padded to the column max + 1 (text/tabwriter semantics).
  private def padCells(rows: List[List[String]]): List[String] =
    val numCols = rows.map(_.length).maxOption.getOrElse(0)
    val widths = (0 until numCols).map: i =>
      rows.filter(_.length > i + 1).map(_(i).length).maxOption.getOrElse(0)
    rows.map: row =>
      row.zipWithIndex.map: (cell, i) =>
        if i == row.length - 1 then cell else cell.padTo(widths(i) + 1, ' ')
      .mkString

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

  final private case class GoKernelCtx(
      stateReady: Boolean,
      hasState: Boolean,
      hasInv: Boolean,
      ir: ServiceIRFull
  )

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String],
      kernelCtx: GoKernelCtx
  ): GoOperation =
    val endpoint = op.endpoint
    val pathParams = endpoint.pathParams.map: p =>
      val goType = goTypeForParam(p.typeExpr, typeLookup)
      GoPathParam(p.name, toCamelCase(p.name), goType, isInt = goType == "int64")

    val nonIdFields   = entity.fields.filterNot(_.fieldName == "id").map(toGoField)
    val createAssigns = nonIdFields.map(f => s"${f.goField}: body.${f.goField}")
    val lookupCol     = EmitShared.lookupColumn(entity, endpoint.pathParams.headOption.map(_.name))

    val method  = HttpMethods.upper(endpoint.method)
    val chiPath = endpoint.path

    val ctx = OperationContext.from(op, entity)

    val customRequestSchemaName = ctx.customRequestSchemaName
    val hasRequestBody          = ctx.hasRequestBody
    val routeKind               = ctx.routeKind

    val (requestBodyType, customRequestFields, customRules) =
      if !hasRequestBody then ("", List.empty[GoFieldView], List.empty[GoValidation.FieldRule])
      else
        customRequestSchemaName match
          case None =>
            (entity.createSchemaName, List.empty[GoFieldView], List.empty[GoValidation.FieldRule])
          case Some(name) =>
            val fields = OperationContext.customRequestBodyFields(op)
            val byName = endpoint.bodyParams.map(p => p.name -> p.typeExpr).toMap
            val rules = fields.map { f =>
              val reduced = byName.get(f.fieldName) match
                case Some(t) => StringRefinements.reduceField(t, None, kernelCtx.ir)
                case None    => StringRefinements.Reduced(None, None, Nil)
              GoValidation.FieldRule(f, reduced)
            }
            (name, fields.map(toGoField), rules)

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
        case _: RkCreate =>
          val sig = if hasRequestBody then s"body $requestBodyType" else ""
          val ret = readSchemaName
          (ret, Option.empty[String], ret, op.operationName, "body", sig)
        case _: RkRead =>
          (
            s"*$readSchemaName",
            Option.empty[String],
            s"*$readSchemaName",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature
          )
        case _: RkList =>
          (
            s"[]$readSchemaName",
            Option.empty[String],
            s"[]$readSchemaName",
            op.operationName,
            "",
            ""
          )
        case _: RkDelete =>
          (
            "",
            Option.empty[String],
            "bool",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature
          )
        case _: RkRedirect =>
          val tgt = EmitShared.redirectTargetColumn(entity).map(toPascalCase).getOrElse("URL")
          (
            "string",
            Some(tgt),
            s"*$readSchemaName",
            op.operationName,
            pathParamCallArgs,
            pathParamSignature
          )
        case _: RkOther =>
          val args = pathParams.map(p => s"${p.goName} ${p.domainType}") ++
            (if hasRequestBody then List(s"body models.$requestBodyType") else Nil)
          val call = (pathParams.map(_.goName) ++ (if hasRequestBody then List("body") else Nil))
            .mkString(", ")
          ("", Option.empty[String], "error", op.operationName, call, args.mkString(", "))

    val isRedirectKind = routeKind match
      case _: RkRedirect => true
      case _             => false
    val kernelGuardStatus = routeKind match
      case _: RkRead | _: RkRedirect | _: RkDelete => 404
      case _                                       => 409
    val kernelMethod =
      goKernelServiceMethod(
        op,
        entity,
        serviceMethodName,
        hasRequestBody,
        requestBodyType,
        typeLookup,
        kernelCtx
      )
    val kernelHandlerArgs =
      (endpoint.pathParams.map(p => toCamelCase(p.name)) ++
        (if hasRequestBody then List("body") else Nil)).mkString(", ")

    GoOperation(
      operationName = op.operationName,
      handlerName = op.operationName,
      method = method,
      path = endpoint.path,
      chiPath = chiPath,
      successStatus = endpoint.successStatus,
      routeKind = EmitShared.routeKindName(routeKind),
      pathParams = pathParams,
      hasRequestBody = hasRequestBody,
      requestBodyType = requestBodyType,
      customRequestSchemaName = customRequestSchemaName,
      customRequestFields = customRequestFields,
      customRules = customRules,
      bodyValidates = false,
      responseType = responseType,
      serviceMethod = serviceMethodName,
      serviceCallArgs = serviceCallArgs,
      serviceSignatureArgs = serviceSig,
      serviceReturnType = serviceReturnType,
      redirectField = redirectField,
      dafnyMethod = op.dafnyMethod,
      routeThroughKernel = kernelMethod.isDefined,
      kernelServiceMethod = kernelMethod.getOrElse(""),
      kernelGuardStatus = kernelGuardStatus,
      kernelGuardDetail = if kernelGuardStatus == 404 then "not found" else "precondition failed",
      kernelRedirect = isRedirectKind,
      kernelHandlerArgs = kernelHandlerArgs,
      lookupColumn = lookupCol,
      createAssigns = createAssigns,
      authMiddleware = Option.when(op.requiresAuth.nonEmpty)(
        s"auth.${SecurityGo.middlewareName(op.requiresAuth)}(cfg)"
      )
    )

  private def goScalarOp(v: ScalarOpView): GoScalarOpView =
    val ep           = v.operation.endpoint
    val methodPascal = HttpMethods.pascal(ep.method)
    GoScalarOpView(
      handlerName = Naming.toPascalCase(v.operation.operationName, Naming.PascalStrategy.Go),
      methodPascal = methodPascal,
      chiPath = ep.path,
      successStatus = ep.successStatus,
      setSql = ScalarOps.updateSetSql(v),
      whereSql = ScalarOps.guardWhereSql(v),
      guardPretty = v.guardPretty,
      authMiddleware = Option.when(v.operation.requiresAuth.nonEmpty)(
        s"auth.${SecurityGo.middlewareName(v.operation.requiresAuth)}(cfg)"
      )
    )

  private def goStateModel(fields: List[ScalarStateFieldView]): String =
    val cols = fields
      .map: f =>
        val goField = Naming.toPascalCase(f.columnName, Naming.PascalStrategy.Go)
        s"""\t$goField int64 `bun:"${f.columnName}" json:"${f.specName}"`"""
      .mkString("\n")
    s"""|package models
        |
        |import "github.com/uptrace/bun"
        |
        |type ServiceState struct {
        |\tbun.BaseModel `bun:"table:${ScalarOps.TableName}"`
        |
        |\tID int64 `bun:"id,pk" json:"id"`
        |$cols
        |}
        |""".stripMargin

  private def goStateService(
      module: String,
      fields: List[ScalarStateFieldView],
      ops: List[GoScalarOpView]
  ): String =
    val snapshotPairs = fields
      .map: f =>
        val goField = Naming.toPascalCase(f.columnName, Naming.PascalStrategy.Go)
        s"""\t\t"${f.specName}": st.$goField,"""
      .mkString("\n")
    val methods = ops
      .map: op =>
        s"""|func (s *StateOpsService) ${op.handlerName}(ctx context.Context) (map[string]int64, bool, error) {
            |\tres, err := s.db.NewUpdate().Model((*models.ServiceState)(nil)).
            |\t\tSet("${op.setSql}").
            |\t\tWhere("${op.whereSql}").
            |\t\tExec(ctx)
            |\tif err != nil {
            |\t\treturn nil, false, err
            |\t}
            |\tn, err := res.RowsAffected()
            |\tif err != nil {
            |\t\treturn nil, false, err
            |\t}
            |\tif n == 0 {
            |\t\treturn nil, false, nil
            |\t}
            |\tsnap, err := s.snapshot(ctx)
            |\tif err != nil {
            |\t\treturn nil, false, err
            |\t}
            |\treturn snap, true, nil
            |}
            |""".stripMargin
      .mkString("\n")
    s"""|package services
        |
        |import (
        |\t"context"
        |
        |\t"github.com/uptrace/bun"
        |
        |\t"$module/internal/config"
        |\t"$module/internal/models"
        |)
        |
        |type StateOpsService struct {
        |\tdb  *bun.DB
        |\tcfg *config.Config
        |}
        |
        |func NewStateOpsService(db *bun.DB, cfg *config.Config) *StateOpsService {
        |\treturn &StateOpsService{db: db, cfg: cfg}
        |}
        |
        |func (s *StateOpsService) snapshot(ctx context.Context) (map[string]int64, error) {
        |\tst := new(models.ServiceState)
        |\tif err := s.db.NewSelect().Model(st).Where("id = 1").Limit(1).Scan(ctx); err != nil {
        |\t\treturn nil, err
        |\t}
        |\treturn map[string]int64{
        |$snapshotPairs
        |\t}, nil
        |}
        |
        |$methods""".stripMargin

  private def goStateHandler(module: String, ops: List[GoScalarOpView]): String =
    val methods = ops
      .map: op =>
        s"""|func (h *StateOpsHandler) ${op.handlerName}(w http.ResponseWriter, r *http.Request) {
            |\tresult, ok, err := h.svc.${op.handlerName}(r.Context())
            |\tif err != nil {
            |\t\twriteError(w, http.StatusInternalServerError, err.Error())
            |\t\treturn
            |\t}
            |\tif !ok {
            |\t\twriteError(w, http.StatusConflict, "precondition failed: ${op.guardPretty}")
            |\t\treturn
            |\t}
            |\twriteJSON(w, ${op.successStatus}, result)
            |}
            |""".stripMargin
      .mkString("\n")
    s"""|package handlers
        |
        |import (
        |\t"net/http"
        |
        |\t"$module/internal/services"
        |)
        |
        |type StateOpsHandler struct {
        |\tsvc *services.StateOpsService
        |}
        |
        |func NewStateOpsHandler(svc *services.StateOpsService) *StateOpsHandler {
        |\treturn &StateOpsHandler{svc: svc}
        |}
        |
        |$methods""".stripMargin

  private def goTypeForParam(typeExpr: type_expr, typeLookup: Map[String, String]): String =
    EmitShared.paramType(typeExpr, typeLookup, "string", t => s"*$t")

  // Idiomatic-Go <-> Dafny-runtime converters keyed by the Go domain type. The empty pair marks a
  // type whose Go and Dafny representations coincide (bool), so no conversion call is emitted.
  private val goKernelScalarConv: Map[String, (String, String)] = Map(
    "string" -> ("dafnykernel.StringToDafny", "dafnykernel.StringFromDafny"),
    "int64"  -> ("dafnykernel.IntToDafny", "dafnykernel.IntFromDafny"),
    "bool"   -> ("", "")
  )

  // Render a service method that routes the operation through its verified Dafny kernel method.
  // With bridgeable state the whole call runs in one transaction: hydrate the ServiceState from
  // the database, check the compiled contract twins (invariant failure and precondition failure
  // map to sentinels the handler translates), call the verified method, persist the mutation.
  // Returns None (the caller falls back to the route-kind stub) unless the op is kernel-bound,
  // every input/output is a supported scalar, and the spec's state round-trips through the
  // bridge; query-param inputs and collection/datatype results remain deferred follow-ups.
  private def goKernelServiceMethod(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      serviceMethod: String,
      hasRequestBody: Boolean,
      requestBodyType: String,
      typeLookup: Map[String, String],
      kernelCtx: GoKernelCtx
  ): Option[String] =
    val endpoint = op.endpoint
    op.dafnyMethod
      .filter(_ => endpoint.queryParams.isEmpty && kernelCtx.stateReady)
      .flatMap: dafnyName =>
        val inputSources =
          endpoint.pathParams.map(p =>
            toCamelCase(p.name) -> goTypeForParam(p.typeExpr, typeLookup)
          ) ++
            endpoint.bodyParams.map(p =>
              s"body.${toPascalCase(p.name)}" -> goTypeForParam(p.typeExpr, typeLookup)
            )
        val convertedArgs = inputSources.map: (access, goType) =>
          goKernelScalarConv.get(goType).map: (toDafny, _) =>
            if toDafny.isEmpty then access else s"$toDafny($access)"
        val outputs = op.responseFields
        val outputsOk =
          outputs.nonEmpty && outputs.forall(f => goKernelScalarConv.contains(f.domainType))
        Option.when(convertedArgs.forall(_.isDefined) && outputsOk):
          val args     = convertedArgs.flatten
          val callArgs = ("state" :: args).mkString(", ")
          val call     = s"dafnykernel.Companion_Default___.$dafnyName($callArgs)"
          val guard    = s"dafnykernel.Companion_Default___.Requires$dafnyName($callArgs)"
          val sigParts =
            endpoint.pathParams.map(p =>
              s"${toCamelCase(p.name)} ${goTypeForParam(p.typeExpr, typeLookup)}"
            ) ++ Option.when(hasRequestBody)(s"body models.$requestBodyType")
          val sig = if sigParts.isEmpty then "" else sigParts.mkString(", ", ", ", "")
          def fromDafny(f: ProfiledField, v: String): String =
            val conv = goKernelScalarConv(f.domainType)._2
            if conv.isEmpty then v else s"$conv($v)"
          def zeroOf(goType: String): String = goType match
            case "string" => "\"\""
            case "int64"  => "0"
            case "bool"   => "false"
            case _        => "nil"
          val (returnType, zero, resultAssign) =
            outputs match
              case single :: Nil =>
                val v = "out" + toPascalCase(single.fieldName)
                (
                  s"(${single.domainType}, error)",
                  zeroOf(single.domainType),
                  List(
                    s"\t\t$v := $call",
                    s"\t\tresult = ${fromDafny(single, v)}"
                  )
                )
              case many =>
                val vars      = many.map(f => "out" + toPascalCase(f.fieldName))
                val keyTokens = many.map(f => s"\"${f.columnName}\":")
                val keyWidth  = keyTokens.map(_.length).max
                val entries = many.zip(vars).zip(keyTokens).map: (fv, key) =>
                  s"\t\t\t${key.padTo(keyWidth, ' ')} ${fromDafny(fv._1, fv._2)},"
                (
                  "(map[string]any, error)",
                  "nil",
                  (s"\t\t${vars.mkString(", ")} := $call" :: "\t\tresult = map[string]any{" :: entries) :::
                    List("\t\t}")
                )
          val resultDecl = returnType match
            case "(map[string]any, error)" => "\tvar result map[string]any"
            case other                     => s"\tvar result ${outputs.head.domainType}"
          val invCheck =
            if kernelCtx.hasInv then
              List(
                "\t\tif !dafnykernel.Companion_Default___.ServiceStateInv(state) {",
                "\t\t\treturn ErrKernelStateInvariant",
                "\t\t}"
              )
            else Nil
          val guardCheck = List(
            s"\t\tif !$guard {",
            "\t\t\treturn ErrKernelPrecondition",
            "\t\t}"
          )
          val header =
            s"func (s *${entity.modelClassName}Service) $serviceMethod(ctx context.Context$sig) $returnType {"
          val body =
            if kernelCtx.hasState then
              (header :: resultDecl ::
                "\tif err := s.db.RunInTx(ctx, nil, func(ctx context.Context, tx bun.Tx) error {" ::
                "\t\tstate, err := hydrateState(ctx, tx)" ::
                "\t\tif err != nil {" :: "\t\t\treturn err" :: "\t\t}" ::
                (invCheck ::: guardCheck ::: resultAssign :::
                  List(
                    "\t\tif err := persistState(ctx, tx, state); err != nil {",
                    "\t\t\treturn err",
                    "\t\t}",
                    "\t\treturn nil",
                    "\t}); err != nil {",
                    s"\t\treturn $zero, err",
                    "\t}",
                    "\treturn result, nil",
                    "}"
                  ))): List[String]
            else
              (header :: resultDecl ::
                "\tstate := dafnykernel.MakeState()" ::
                "\terr := func() error {" ::
                (invCheck ::: guardCheck ::: resultAssign :::
                  List(
                    "\t\treturn nil",
                    "\t}()",
                    "\tif err != nil {",
                    s"\t\treturn $zero, err",
                    "\t}",
                    "\treturn result, nil",
                    "}"
                  ))): List[String]
          body.mkString("\n")

  private def toCamelCase(name: String): String =
    Naming.toCamelCase(name, Naming.CamelStrategy.Plain)
