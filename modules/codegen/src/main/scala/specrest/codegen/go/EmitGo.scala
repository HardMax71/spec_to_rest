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
    responseType: String,
    serviceMethod: String,
    serviceCallArgs: String,
    serviceSignatureArgs: String,
    serviceReturnType: String,
    redirectField: Option[String],
    dafnyMethod: Option[String],
    routeThroughKernel: Boolean,
    kernelServiceMethod: String,
    kernelHandlerArgs: String,
    lookupColumn: String,
    createAssigns: List[String],
    authMiddleware: Option[String]
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
    modelStructLines: List[String],
    createStructLines: List[String],
    handlerImports: String
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
    composeEnv: List[GoComposeEnv],
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
    service: GoServiceNames,
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
    val service = GoServiceNames(
      name = ctx.service.name,
      snakeName = ctx.service.snakeName,
      kebabName = ctx.service.kebabName
    )

    val typeLookup = EmitShared.aliasResolvedDomainLookup(profiled)

    val entities = profiled.entities.map: entity =>
      val entityOps = profiled.operations
        .filter(_.targetEntity.contains(entity.entityName))
        .map(op => enrichOperation(op, entity, typeLookup))
        .sortWith((a, b) => EmitShared.byPathSpecificity(a.path, b.path))
      buildEntityCtx(module, entity, entityOps)

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
      val recipe = specrest.codegen.Dsn.Recipe(
        spec = specrest.codegen.Dsn.Spec(
          shape = specrest.codegen.Dsn.Shape.Url("postgres"),
          port = 5432,
          suffix = "?sslmode=disable"
        ),
        secrets = specrest.codegen.Dsn.Secrets("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")
      )
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
        composeEnv = dv.composeEnv.map(e => GoComposeEnv(e.key, e.value)),
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
        composeEnv = dv.composeEnv.map(e => GoComposeEnv(e.key, e.value)),
        dsnRecipe = None
      )
    case "mysql" =>
      val dv = specrest.codegen.migration.Mysql.deployment(snake)
      val recipe = specrest.codegen.Dsn.Recipe(
        spec = specrest.codegen.Dsn.Spec(
          shape = specrest.codegen.Dsn.Shape.MysqlGo,
          port = 3306,
          suffix = "?parseTime=true"
        ),
        secrets = specrest.codegen.Dsn.Secrets("MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_DATABASE")
      )
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
        composeEnv = dv.composeEnv.map(e => GoComposeEnv(e.key, e.value)),
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
      "pagination"        -> Pagination.view
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
    val usesErrors    = usesNotFound || operations.exists(effectiveKind(_) == "other")
    val usesSqlNoRows = operations.exists(effectiveKind(_) == "read")
    val usesKernel    = operations.exists(_.routeThroughKernel)
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
          Option.when(usesNotFound)("\"errors\""),
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

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String]
  ): GoOperation =
    val endpoint = op.endpoint
    val pathParams = endpoint.pathParams.map: p =>
      val goType = goTypeForParam(p.typeExpr, typeLookup)
      GoPathParam(p.name, toCamelCase(p.name), goType, isInt = goType == "int64")

    val nonIdFields   = entity.fields.filterNot(_.fieldName == "id").map(toGoField)
    val createAssigns = nonIdFields.map(f => s"${f.goField}: body.${f.goField}")
    val lookupCol = pathParams.headOption match
      case Some(p) if entity.fields.exists(_.columnName == p.name) => p.name
      case _                                                       => "id"

    val method  = HttpMethods.upper(endpoint.method)
    val chiPath = endpoint.path

    val ctx = OperationContext.from(op, entity)

    val customRequestSchemaName = ctx.customRequestSchemaName
    val hasRequestBody          = ctx.hasRequestBody
    val routeKind               = ctx.routeKind

    val (requestBodyType, customRequestFields) =
      if !hasRequestBody then ("", List.empty[GoFieldView])
      else
        customRequestSchemaName match
          case None =>
            (entity.createSchemaName, List.empty[GoFieldView])
          case Some(name) =>
            val pathParamNames = endpoint.pathParams.map(_.name).toSet
            val fields = op.requestBodyFields
              .filterNot(f => pathParamNames.contains(f.fieldName))
              .map(toGoField)
            (name, fields)

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

    val kernelMethod =
      goKernelServiceMethod(
        op,
        entity,
        serviceMethodName,
        hasRequestBody,
        requestBodyType,
        typeLookup
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
      responseType = responseType,
      serviceMethod = serviceMethodName,
      serviceCallArgs = serviceCallArgs,
      serviceSignatureArgs = serviceSig,
      serviceReturnType = serviceReturnType,
      redirectField = redirectField,
      dafnyMethod = op.dafnyMethod,
      routeThroughKernel = kernelMethod.isDefined,
      kernelServiceMethod = kernelMethod.getOrElse(""),
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
    val setSql = v.updates
      .map(u => s"${u.columnName} = ${ScalarOps.renderRhs(u.rhs, u.columnName)}")
      .mkString(", ")
    val whereSql =
      ("id = 1" :: v.guards.map(g => s"${g.columnName} ${ScalarOps.sqlCmp(g.cmp)} ${g.lit}"))
        .mkString(" AND ")
    GoScalarOpView(
      handlerName = Naming.toPascalCase(v.operation.operationName, Naming.PascalStrategy.Go),
      methodPascal = methodPascal,
      chiPath = ep.path,
      successStatus = ep.successStatus,
      setSql = setSql,
      whereSql = whereSql,
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
    typeExpr match
      case NamedTypeF(n, _)      => typeLookup.getOrElse(n, "string")
      case OptionTypeF(inner, _) => s"*${goTypeForParam(inner, typeLookup)}"
      case _                     => "string"

  // Idiomatic-Go <-> Dafny-runtime converters keyed by the Go domain type. The empty pair marks a
  // type whose Go and Dafny representations coincide (bool), so no conversion call is emitted.
  private val goKernelScalarConv: Map[String, (String, String)] = Map(
    "string" -> ("dafnykernel.StringToDafny", "dafnykernel.StringFromDafny"),
    "int64"  -> ("dafnykernel.IntToDafny", "dafnykernel.IntFromDafny"),
    "bool"   -> ("", "")
  )

  // Render a service method that routes the operation through its verified Dafny kernel method,
  // marshalling scalar inputs and outputs across the Go/Dafny boundary. Returns None (the caller
  // falls back to the route-kind stub) unless the op is kernel-bound and every input/output is a
  // supported scalar; query-param inputs and collection/datatype results are deferred follow-ups.
  private def goKernelServiceMethod(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      serviceMethod: String,
      hasRequestBody: Boolean,
      requestBodyType: String,
      typeLookup: Map[String, String]
  ): Option[String] =
    val endpoint = op.endpoint
    op.dafnyMethod.filter(_ => endpoint.queryParams.isEmpty).flatMap: dafnyName =>
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
        val callArgs = ("state" :: convertedArgs.flatten).mkString(", ")
        val call     = s"dafnykernel.Companion_Default___.$dafnyName($callArgs)"
        val sigParts =
          endpoint.pathParams.map(p =>
            s"${toCamelCase(p.name)} ${goTypeForParam(p.typeExpr, typeLookup)}"
          ) ++ Option.when(hasRequestBody)(s"body models.$requestBodyType")
        val sig = if sigParts.isEmpty then "" else sigParts.mkString(", ", ", ", "")
        def fromDafny(f: ProfiledField, v: String): String =
          val conv = goKernelScalarConv(f.domainType)._2
          if conv.isEmpty then v else s"$conv($v)"
        val (returnType, resultLines) =
          outputs match
            case single :: Nil =>
              val v = "out" + toPascalCase(single.fieldName)
              (
                s"(${single.domainType}, error)",
                List(s"\t$v := $call", s"\treturn ${fromDafny(single, v)}, nil")
              )
            case many =>
              val vars      = many.map(f => "out" + toPascalCase(f.fieldName))
              val keyTokens = many.map(f => s"\"${f.columnName}\":")
              val keyWidth  = keyTokens.map(_.length).max
              val entries = many.zip(vars).zip(keyTokens).map: (fv, key) =>
                s"\t\t${key.padTo(keyWidth, ' ')} ${fromDafny(fv._1, fv._2)},"
              (
                "(map[string]any, error)",
                (s"\t${vars.mkString(", ")} := $call" :: "\treturn map[string]any{" :: entries) :::
                  List("\t}, nil")
              )
        val header =
          s"func (s *${entity.modelClassName}Service) $serviceMethod(ctx context.Context$sig) $returnType {"
        (header :: "\tstate := dafnykernel.MakeState()" :: resultLines ::: List("}")).mkString("\n")

  private def toCamelCase(name: String): String =
    Naming.toCamelCase(name, Naming.CamelStrategy.Plain)
