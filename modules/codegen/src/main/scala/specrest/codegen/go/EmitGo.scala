package specrest.codegen.go

import specrest.codegen.Compose
import specrest.codegen.DafnyKernel
import specrest.codegen.EmitOptions
import specrest.codegen.EmittedFile
import specrest.codegen.EnvExample
import specrest.codegen.ExtensionStub
import specrest.codegen.GoTemplates
import specrest.codegen.OperationContext
import specrest.codegen.RenderContext
import specrest.codegen.TemplateEngine
import specrest.codegen.migration.Revision
import specrest.codegen.migration.SchemaCodec
import specrest.codegen.migration.SchemaDiff
import specrest.codegen.migration.SchemaSnapshot
import specrest.codegen.migration.SqlRenderer
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
    needsStrconv: Boolean,
    needsValidator: Boolean,
    hasOperations: Boolean,
    usesRequestBody: Boolean,
    usesPathParams: Boolean,
    usesNotFound: Boolean,
    usesErrors: Boolean,
    usesSqlNoRows: Boolean,
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
    composeEnv: List[GoComposeEnv],
    dsnRecipe: Option[specrest.codegen.Dsn.Recipe]
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

    val baseTypeLookup = profiled.profile.typeMap.map: (k, v) =>
      k -> v.domain
    val aliasExprs =
      profiled.ir.e.collect { case TypeAliasDeclFull(n, t, _, _) => n -> t }.toMap
    def resolveAliasType(te: type_expr_full, seen: Set[String] = Set.empty): Option[String] =
      te match
        case NamedTypeF(n, _) =>
          baseTypeLookup
            .get(n)
            .orElse(
              if seen(n) then None
              else aliasExprs.get(n).flatMap(resolveAliasType(_, seen + n))
            )
        case _ => None
    val typeLookup =
      baseTypeLookup ++ aliasExprs.flatMap((n, t) => resolveAliasType(t).map(n -> _))

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
    files += EmittedFile(".env.example", EnvExample.render(composeIn))

    files += EmittedFile(
      "internal/extensions/extensions.go",
      ExtensionStub.go,
      preserve = true
    )

    // Go links statically: main.go always calls testadmin.Register, so the
    // package must always exist. This is the `!conformance` no-op; testgen
    // (default; opt out with --no-tests) emits the spec-derived
    // `conformance`-tagged Register.
    files += EmittedFile(
      "internal/testadmin/testadmin.go",
      """|//go:build !conformance
         |
         |package testadmin
         |
         |import (
         |	"github.com/go-chi/chi/v5"
         |	"github.com/uptrace/bun"
         |)
         |
         |// Register is a no-op in normal builds; the spec-derived conformance
         |// implementation (build tag `conformance`, emitted by default — opt out
         |// with --no-tests) replaces it via the build constraint.
         |func Register(_ chi.Router, _ *bun.DB) {}
         |""".stripMargin
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
      val tableOps   = SchemaDiff.topoSort(schema_tables(schema)).map(CreateTable.apply)
      val triggerOps = schema_triggers(schema).map(AddTrigger.apply)
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

    // The handler's `errors` import covers `errors.Is(ErrNotFound)` in the read AND redirect
    // routes (the redirect route still maps a not-found error to 404). The service's
    // `database/sql` import is needed only by the `read` branch's `sql.ErrNoRows` — a redirect
    // op is now a fail-loud stub that does no row lookup, so it must not pull in `database/sql`.
    val usesNotFound = operations.exists: o =>
      o.routeKind == "read" || o.routeKind == "redirect"
    val usesErrors      = usesNotFound || operations.exists(_.routeKind == "other")
    val usesSqlNoRows   = operations.exists(_.routeKind == "read")
    val usesRequestBody = operations.exists(_.hasRequestBody)
    val usesPathParams  = operations.exists(_.pathParams.nonEmpty)

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
      needsStrconv = operations.exists(_.pathParams.exists(_.isInt)),
      needsValidator = nonIdFields.nonEmpty,
      hasOperations = operations.nonEmpty,
      usesRequestBody = usesRequestBody,
      usesPathParams = usesPathParams,
      usesNotFound = usesNotFound,
      usesErrors = usesErrors,
      usesSqlNoRows = usesSqlNoRows,
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
      val goType = goTypeForParam(p.typeExpr, typeLookup)
      GoPathParam(p.name, toCamelCase(p.name), goType, isInt = goType == "int64")

    val nonIdFields   = entity.fields.filterNot(_.fieldName == "id").map(toGoField)
    val createAssigns = nonIdFields.map(f => s"${f.goField}: body.${f.goField}")
    val lookupCol = pathParams.headOption match
      case Some(p) if entity.fields.exists(_.columnName == p.name) => p.name
      case _                                                       => "id"

    val method  = endpoint.method.toString.toUpperCase
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
          val tgt = redirectTarget(op, entity).getOrElse("URL")
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

  private def routeKindName(rk: route_kind): String = rk match
    case _: RkCreate   => "create"
    case _: RkRead     => "read"
    case _: RkList     => "list"
    case _: RkDelete   => "delete"
    case _: RkRedirect => "redirect"
    case _: RkOther    => "other"

  private def byPathSpecificity(a: GoOperation, b: GoOperation): Boolean =
    val aCount = a.path.count(_ == '{')
    val bCount = b.path.count(_ == '{')
    aCount < bCount
