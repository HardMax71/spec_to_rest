package specrest.codegen.ts

import specrest.codegen.AuthSchemes
import specrest.codegen.Compose
import specrest.codegen.DafnyKernel
import specrest.codegen.EmitOptions
import specrest.codegen.EmitShared
import specrest.codegen.EmittedFile
import specrest.codegen.EnvExample
import specrest.codegen.ExtensionStub
import specrest.codegen.HydrationScope
import specrest.codegen.KernelTypes
import specrest.codegen.OperationContext
import specrest.codegen.Pagination
import specrest.codegen.RenderContext
import specrest.codegen.ScalarOpView
import specrest.codegen.ScalarOps
import specrest.codegen.ScalarStateFieldView
import specrest.codegen.SensitiveFields
import specrest.codegen.TemplateEngine
import specrest.codegen.TsTemplates
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

import java.util.Locale

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
    routeThroughKernel: Boolean,
    kernelServiceFn: String,
    kernelNoResult: Boolean,
    kernelCandidateConsts: List[(String, String)],
    kernelRouteCallArgs: String,
    authMiddleware: Option[String],
    prismaCall: String,
    prismaWhere: String,
    prismaCreateData: String,
    lookupField: String
)

final private case class TsEntityCtx(
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
    authDeps: List[String],
    needsDecimal: Boolean,
    needsBuffer: Boolean,
    needsPrismaImport: Boolean,
    needsResultCast: Boolean,
    hasListRoute: Boolean,
    usesKernel: Boolean,
    kernelImports: String,
    customSchemas: List[TsCustomSchema]
)

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
    composeEnv: List[specrest.codegen.migration.ComposeEnv],
    dsnRecipe: Option[specrest.codegen.Dsn.Recipe]
)

final private case class TsScalarOpView(
    handlerName: String,
    method: String,
    expressPath: String,
    successStatus: Int,
    updateSql: String,
    guardPretty: String,
    authMiddleware: Option[String]
)

final private case class TsProjectCtx(
    service: specrest.codegen.ServiceNames,
    packageName: String,
    entities: List[TsEntityCtx],
    needsDecimal: Boolean,
    needsBuffer: Boolean,
    db: TsDbView,
    dafnyKernel: Option[DafnyKernel],
    scalarOps: List[TsScalarOpView],
    authSchemaLines: List[String],
    authConfigLines: List[String]
)

object EmitTs:

  private val NumericPrismaTypes: Set[String] = Set("Int", "BigInt", "Float", "Decimal")

  def emit(profiled: ProfiledService, opts: EmitOptions): List[EmittedFile] =
    val engine      = new TemplateEngine
    val templates   = TsTemplates.tsExpressPostgres
    val ctx         = RenderContext.buildRenderContext(profiled, opts.dafnyKernel)
    val packageName = npmPackageName(ctx.service.kebabName)
    val service     = ctx.service

    val typeLookup = EmitShared.aliasResolvedDomainLookup(profiled)
    val bridgePlan = StateBridgeTs.plan(profiled)
    val kernelCtx = TsKernelCtx(
      stateReady = irStateFields(profiled.ir).isEmpty || bridgePlan.isRight,
      hasState = bridgePlan.toOption.exists(_.hasState),
      hasInv = irStateFields(profiled.ir).nonEmpty,
      ir = profiled.ir
    )

    val triggerMaintainedByTable: Map[String, Set[String]] =
      schemaTriggers(profiled.schema)
        .groupBy(triggerTargetTable)
        .view
        .mapValues(_.map(triggerTargetColumn).toSet)
        .toMap

    val db = tsDbView(profiled.profile.database, service.snakeName)
    val prismaAutoIncrement =
      specrest.codegen.migration.Dialect.forDatabase(profiled.profile.database).prismaAutoIncrement

    val entities = profiled.entities.map: entity =>
      val entityOps = profiled.operations
        .filter(_.targetEntity.contains(entity.entityName))
        .map(op =>
          enrichOperation(op, entity, profiled.entities, typeLookup, db.nativeAttrs, kernelCtx)
        )
        .sortWith((a, b) => EmitShared.byPathSpecificity(a.path, b.path))
      val maintained = triggerMaintainedByTable.getOrElse(entity.tableName, Set.empty)
      buildEntityCtx(
        packageName,
        profiled,
        entity,
        entityOps,
        maintained,
        db.nativeAttrs,
        prismaAutoIncrement
      )

    val needsDecimal = entities.exists(_.needsDecimal)
    val needsBuffer  = entities.exists(_.needsBuffer)

    val scalarFields = ScalarOps.stateFields(profiled)
    val scalarOps    = ScalarOps.views(profiled).map(tsScalarOp)

    val projectCtx = TsProjectCtx(
      service = service,
      packageName = packageName,
      entities = entities,
      needsDecimal = needsDecimal,
      needsBuffer = needsBuffer,
      db = db,
      dafnyKernel = opts.dafnyKernel,
      scalarOps = scalarOps,
      authSchemaLines = SecurityTs.schemaLines(profiled.ir),
      authConfigLines = SecurityTs.configLines(profiled.ir)
    )

    val files        = List.newBuilder[EmittedFile]
    val projectScope = mergeProfile(ctx, projectCtx)

    val projectHead: List[(String, String)] = List(
      "package.json"               -> templates.packageJson,
      "tsconfig.json"              -> templates.tsconfig,
      "prisma/schema.prisma"       -> templates.prismaSchema,
      "src/index.ts"               -> templates.index,
      "src/tracing.ts"             -> templates.tracing,
      "src/app.ts"                 -> templates.app,
      "src/config.ts"              -> templates.config,
      "src/prisma.ts"              -> templates.prisma,
      "src/pagination.ts"          -> templates.pagination,
      "src/middleware/error.ts"    -> templates.errorMiddleware,
      "src/middleware/validate.ts" -> templates.validateMiddleware,
      "src/middleware/auth.ts"     -> templates.authMiddleware,
      "src/routes/index.ts"        -> templates.routesIndex
    )

    val projectTail: List[(String, String)] = List(
      "Dockerfile"               -> templates.dockerfile,
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

    files += EmittedFile("src/routes/admin.ts", AdminRouterTs.emit(profiled))
    if svcSecurity(profiled.ir).nonEmpty then
      files += EmittedFile("src/middleware/schemes.ts", SecurityTs.emit(profiled))

    if scalarOps.nonEmpty then
      files += EmittedFile("src/services/stateOps.ts", tsStateService(scalarFields, scalarOps))
      files += EmittedFile("src/routes/stateOps.ts", tsStateRoutes(scalarOps))

    projectTail.foreach: (path, tpl) =>
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
      "src/extensions/index.ts",
      ExtensionStub.ts,
      preserve = true
    )

    files += EmittedFile(
      "openapi.yaml",
      OpenApi.serialize(OpenApi.buildOpenApiDocument(profiled))
    )

    emitPrismaMigrations(profiled, opts, templates, engine, projectCtx.db, files)

    if entities.exists(_.usesKernel) && kernelCtx.hasState then
      files += EmittedFile("src/services/stateBridge.ts", StateBridgeTs.emit(profiled))
    opts.dafnyKernel.foreach: kernel =>
      val pkg = kernel.packagePath.stripSuffix("/")
      DafnyKernel.rewriteJsKernel(kernel.files).toList.sortBy(_._1).foreach: (rel, content) =>
        files += EmittedFile(s"$pkg/$rel", content)
      files += EmittedFile(s"$pkg/adapter.ts", templates.dafnyAdapter)
      // tsc does not copy the non-TS `.cjs` kernel into `dist/`, so `npm start` (which runs the
      // compiled output) would not find it; the gated `build` step runs this copy.
      files += EmittedFile("scripts/copyKernel.mjs", templates.dafnyCopyScript)

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
      val tableOps   = SchemaDiff.topoSort(schemaTables(schema)).map(CreateTable.apply)
      val triggerOps = schemaTriggers(schema).map(AddTrigger.apply)
      val ops        = tableOps ++ triggerOps
      val seeds = SchemaDiff
        .topoSort(schemaTables(schema))
        .filter(ScalarOps.isStateTable)
        .map(t => ScalarOps.seedSqlFor(t) + ";")
      val upScope = Map[String, Any](
        "migration" -> PrismaMigrationView(SqlRenderer.upgrade(ops, dialect) ++ seeds)
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

    MigrationPlan.of(opts.previousSnapshot, opts.existingRevisions, schema) match
      case MigrationPlan.Initial  => emitInitial()
      case MigrationPlan.UpToDate => ()
      case MigrationPlan.Delta(ops, nextRev) =>
        val deltaSeeds = ScalarOps.deltaStateSeeds(ops).map(_ + ";")
        val upScope = Map[String, Any](
          "migration" -> PrismaMigrationView(SqlRenderer.upgrade(ops, dialect) ++ deltaSeeds)
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

  private def composeInputs(db: TsDbView): Compose.Inputs =
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

  private def tsScalarOp(v: ScalarOpView): TsScalarOpView =
    val ep     = v.operation.endpoint
    val method = HttpMethods.lower(ep.method)
    TsScalarOpView(
      handlerName = Naming.toCamelCase(v.operation.operationName, Naming.CamelStrategy.Ts),
      method = method,
      expressPath = ep.path,
      successStatus = ep.successStatus,
      updateSql =
        s"UPDATE ${ScalarOps.TableName} SET ${ScalarOps.updateSetSql(v)} WHERE ${ScalarOps.guardWhereSql(v)}",
      guardPretty = v.guardPretty,
      authMiddleware = Option.when(v.operation.requiresAuth.nonEmpty)(
        SecurityTs.middlewareName(v.operation.requiresAuth)
      )
    )

  private def tsStateService(
      fields: List[ScalarStateFieldView],
      ops: List[TsScalarOpView]
  ): String =
    val snapshotPairs = fields
      .map(f => s"    ${f.specName}: Number(row.${f.camelName}),")
      .mkString("\n")
    val opFns = ops
      .map: op =>
        s"""|export const ${op.handlerName} = async (): Promise<StateSnapshot | null> => {
            |  // Atomic guarded update: the requires clause rides in the WHERE so a
            |  // stale read can never break the verified invariant under concurrency.
            |  const updated = await prisma.$$executeRawUnsafe(
            |    '${op.updateSql}',
            |  );
            |  if (updated === 0) {
            |    return null;
            |  }
            |  return snapshot();
            |};
            |""".stripMargin
      .mkString("\n")
    s"""|import { prisma } from '../prisma.js';
        |
        |export type StateSnapshot = {
        |${fields.map(f => s"  ${f.specName}: number;").mkString("\n")}
        |};
        |
        |const snapshot = async (): Promise<StateSnapshot> => {
        |  const row = await prisma.serviceState.findUniqueOrThrow({ where: { id: 1 } });
        |  return {
        |$snapshotPairs
        |  };
        |};
        |
        |$opFns""".stripMargin

  private def tsStateRoutes(ops: List[TsScalarOpView]): String =
    val authImports = ops.flatMap(_.authMiddleware).distinct.sorted
    val routes = ops
      .map: op =>
        val guard = op.authMiddleware.fold("")(m => s"\n    $m,")
        s"""|  app.${op.method}(
            |    '${op.expressPath}',$guard
            |    wrap(async (_req, res) => {
            |      const result = await stateOps.${op.handlerName}();
            |      if (result === null) {
            |        res.status(409).json({ detail: 'precondition failed: ${op.guardPretty}' });
            |        return;
            |      }
            |      res.status(${op.successStatus}).json(result);
            |    }),
            |  );
            |""".stripMargin
      .mkString("\n")
    s"""|import type { Express, NextFunction, Request, Response } from 'express';
        |
        |${
         if authImports.nonEmpty then
           s"import { ${authImports.mkString(", ")} } from '../middleware/schemes.js';\n"
         else ""
       }import * as stateOps from '../services/stateOps.js';
        |
        |const wrap =
        |  (handler: (req: Request, res: Response) => Promise<void>) =>
        |  (req: Request, res: Response, next: NextFunction): void => {
        |    handler(req, res).catch(next);
        |  };
        |
        |export const registerStateOpsRoutes = (app: Express): void => {
        |$routes};
        |""".stripMargin

  private def tsDbView(database: String, snake: String): TsDbView = database match
    case "postgres" =>
      val dv = specrest.codegen.migration.Postgres.deployment(snake)
      val recipe = specrest.codegen.Dsn
        .postgresRecipe(specrest.codegen.Dsn.Shape.Url("postgresql"), "?schema=public")
      TsDbView(
        provider = "postgresql",
        appDsn = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
        appDsnCompose = specrest.codegen.Dsn.renderDev(recipe, host = "db", snake),
        nativeAttrs = true,
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbHealthCmd = dv.dbHealthCmd,
        dbVolumePath = dv.dbVolumePath,
        composeEnv = dv.composeEnv,
        dsnRecipe = Some(recipe)
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
        composeEnv = dv.composeEnv,
        dsnRecipe = None
      )
    case "mysql" =>
      val dv = specrest.codegen.migration.Mysql.deployment(snake)
      val recipe =
        specrest.codegen.Dsn.mysqlRecipe(specrest.codegen.Dsn.Shape.Url("mysql"))
      TsDbView(
        provider = "mysql",
        appDsn = specrest.codegen.Dsn.renderDev(recipe, host = "localhost", snake),
        appDsnCompose = specrest.codegen.Dsn.renderDev(recipe, host = "db", snake),
        nativeAttrs = false,
        hasDbService = dv.hasDbService,
        dbImage = dv.dbImage,
        dbPort = dv.dbPort,
        dbHealthCmd = dv.dbHealthCmd,
        dbVolumePath = dv.dbVolumePath,
        composeEnv = dv.composeEnv,
        dsnRecipe = Some(recipe)
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
      "service"           -> proj.service,
      "packageName"       -> proj.packageName,
      "profile"           -> ctx.profile,
      "entities"          -> proj.entities,
      "needsDecimal"      -> proj.needsDecimal,
      "needsBuffer"       -> proj.needsBuffer,
      "db"                -> proj.db,
      "hasDafny"          -> proj.dafnyKernel.isDefined,
      "scalarOps"         -> proj.scalarOps,
      "scalarStateFields" -> ctx.scalarStateFields,
      "hasScalarOps"      -> ctx.hasScalarOps,
      "needsJwt"          -> ctx.needsJwt,
      "authSchemaLines"   -> proj.authSchemaLines,
      "authConfigLines"   -> proj.authConfigLines,
      "pagination"        -> Pagination.view,
      "pins"              -> ctx.pins
    )
    currentEntity match
      case Some(e) =>
        base + ("entity" -> e) + ("entityCtx" -> e)
      case None => base

  private def buildEntityCtx(
      packageName: String,
      profiled: ProfiledService,
      entity: ProfiledEntity,
      operations: List[TsOperation],
      triggerMaintainedColumns: Set[String],
      nativeAttrs: Boolean,
      prismaAutoIncrement: String
  ): TsEntityCtx =
    val entityCamel       = toCamelCase(entity.entityName)
    val entityPascal      = toPascalCase(entity.entityName)
    val entitySnake       = Naming.toSnakeCase(entity.entityName)
    val entityKebab       = Naming.toKebabCase(entity.entityName)
    val entityPlural      = Naming.pluralize(entity.entityName)
    val entityPluralCamel = toCamelCase(entityPlural)
    val entityPluralKebab = Naming.toKebabCase(entityPlural)

    def mkField(f: ProfiledField): TsFieldView =
      val enumVals = KernelTypes
        .fieldKind(profiled.ir, entity.entityName, f.fieldName)
        .collect {
          case KernelTypes.Kind.EnumK(n)                         => n
          case KernelTypes.Kind.OptOf(KernelTypes.Kind.EnumK(n)) => n
        }
        .flatMap(n => svcEnums(profiled.ir).find(e => enumNameFull(e) == n))
        .map(enumValuesFull)
      val base = toTsField(
        f,
        nativeAttrs,
        EmitShared.entityFieldRefinement(profiled, entity, f),
        enumVals
      )
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
          // An explicitly-declared `id: Int` PK is application-supplied (spec `next_id`), so it
          // must NOT carry the Prisma auto-increment default — only a synthesized serial PK does.
          val auto =
            specrest.codegen.migration.CanonicalType.isAutoIncrementType(idField.ormColumnType)
          val pk = toTsField(idField, nativeAttrs).copy(
            tsField = "id",
            jsonName = "id",
            columnName = "id",
            prismaAttrs = if auto then s"@id $prismaAutoIncrement" else "@id",
            isPrimaryKey = true
          )
          (pk, entity.fields.filterNot(_.fieldName == "id").map(mkField))
        case None =>
          // Synthesized PK is always BIGSERIAL (64-bit) — the Prisma type must match the
          // migration's column (Prisma `BigInt`), not the legacy hardcoded `Int`, or the
          // generated client is mistyped against its own migration. The DTO/zod stay `number`
          // (the spec's `Int` wire contract); a bigint id is bridged by the result cast and
          // the app-level BigInt JSON serializer.
          val nativeAttr = if nativeAttrs then nativePrismaAttr("BIGSERIAL") else ""
          val pk = TsFieldView(
            tsField = "id",
            jsonName = "id",
            columnName = "id",
            domainType = "number",
            prismaType = prismaTypeFor("BIGSERIAL"),
            prismaAttrs =
              List(s"@id $prismaAutoIncrement", nativeAttr).filter(_.nonEmpty).mkString(" "),
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
    // A BigInt PK (synthesized BIGSERIAL) makes the Prisma row type diverge from the
    // number-typed DTO; the cast keeps tsc happy and the app-level BigInt JSON serializer
    // emits it as a number on the wire (the spec's `Int` contract).
    val needsResultCast = allFields.exists(f => f.prismaType == "Json" || f.prismaType == "BigInt")

    val customSchemas = operations.flatMap: op =>
      op.customRequestSchemaName.map(name => TsCustomSchema(name, op.customRequestFields))

    TsEntityCtx(
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
      authDeps = operations.flatMap(_.authMiddleware).distinct.sorted,
      needsDecimal = needsDecimal,
      needsBuffer = needsBuffer,
      needsPrismaImport = needsDecimal,
      needsResultCast = needsResultCast,
      hasListRoute = operations.exists(_.routeKind == "list"),
      usesKernel = operations.exists(_.routeThroughKernel),
      kernelImports = {
        val fns = operations.filter(_.routeThroughKernel).map(_.kernelServiceFn).mkString("\n")
        val adapterNames = List(
          "companion",
          "dafnyCollToArray",
          "dafnySeqOf",
          "dafnySetOf",
          "enumFromDafny",
          "enumToDafny",
          "intFromDafny",
          "intFromQueryString",
          "intToDafny",
          "makeState",
          "sampleCandidate",
          "someOrNone",
          "stringFromDafny",
          "stringToDafny",
          "valueOrNull"
        ).filter(n => fns.contains(n + "(") || fns.contains(n + "."))
        val lines = List.newBuilder[String]
        if fns.contains("HttpError(") then
          lines += "import { HttpError } from '../middleware/error.js';"
        if adapterNames.nonEmpty then
          lines += adapterNames
            .map(n => s"  $n,")
            .mkString("import {\n", "\n", "\n} from '../dafnyKernel/adapter.js';")
        if fns.contains("hydrateState(") then
          val scopeType =
            if fns.contains(": HydrationScope") then "type HydrationScope, " else ""
          lines += s"import { ${scopeType}hydrateState, persistState } from './stateBridge.js';"
        val charsets = operations
          .filter(_.routeThroughKernel)
          .flatMap(_.kernelCandidateConsts)
          .distinctBy(_._1)
        charsets.foreach: (name, charset) =>
          lines += s"const $name = ${tsSingleQuoted(charset)};"
        lines.result().mkString("\n")
      },
      customSchemas = customSchemas
    )

  private def toTsField(
      f: ProfiledField,
      nativeAttrs: Boolean,
      reduced: StringRefinements.Reduced = StringRefinements.Reduced(None, None, Nil),
      enumValues: Option[List[String]] = None
  ): TsFieldView =
    val tsName = f.fieldName
    val attrs  = prismaAttrs(f, tsName, nativeAttrs)
    // Enum-typed inputs validate as z.enum members at the boundary: the
    // kernel constructs the datatype by member name and must never see an
    // invalid one.
    val zod = enumValues match
      case Some(vs) =>
        val base = vs.map(v => s"'$v'").mkString("z.enum([", ", ", "])")
        if f.nullable then s"$base.nullable()" else base
      case None => zodSchemaFor(f, reduced)
    TsFieldView(
      tsField = tsName,
      jsonName = tsName,
      columnName = f.columnName,
      domainType = f.domainType,
      prismaType = prismaTypeFor(f.ormColumnType),
      prismaAttrs = attrs,
      zodSchema = zod,
      nullable = f.nullable,
      isPrimaryKey = false
    )

  final private case class PrismaMapping(typeName: String, dbAttr: String)

  private val PrismaSqlTypes: Map[String, PrismaMapping] = Map(
    "TEXT"             -> PrismaMapping("String", "@db.Text"),
    "VARCHAR"          -> PrismaMapping("String", "@db.VarChar"),
    "INTEGER"          -> PrismaMapping("Int", "@db.Integer"),
    "SERIAL"           -> PrismaMapping("Int", "@db.Integer"),
    "BIGINT"           -> PrismaMapping("BigInt", "@db.BigInt"),
    "BIGSERIAL"        -> PrismaMapping("BigInt", "@db.BigInt"),
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
    PrismaSqlTypes.get(sqlColumnType.toUpperCase(Locale.ROOT)).map(_.typeName).getOrElse("String")

  private def prismaAttrs(f: ProfiledField, tsName: String, nativeAttrs: Boolean): String =
    val mapAttr =
      if f.columnName != tsName then s"""@map("${f.columnName}")"""
      else ""
    val nativeAttr = if nativeAttrs then nativePrismaAttr(f.ormColumnType) else ""
    List(mapAttr, nativeAttr).filter(_.nonEmpty).mkString(" ")

  private def nativePrismaAttr(sqlColumnType: String): String =
    PrismaSqlTypes.get(sqlColumnType.toUpperCase(Locale.ROOT)).map(_.dbAttr).getOrElse("")

  // Request schemas carry the spec's string refinements as zod constraints,
  // the same reduction the python and go boundaries enforce; the JS regex
  // literal escapes its slashes.
  private def zodSchemaFor(f: ProfiledField, reduced: StringRefinements.Reduced): String =
    val base0 = baseZod(f.domainType)
    val base =
      if f.domainType == "string" && !reduced.isEmpty then
        val minS = reduced.minLen.map(n => s".min($n)").getOrElse("")
        val maxS = reduced.maxLen.map(n => s".max($n)").getOrElse("")
        val reS = StringRefinements
          .combinedPattern(reduced.patterns)
          .map(p => s".regex(/${p.replace("/", "\\/")}/)")
          .getOrElse("")
        s"$base0$minS$maxS$reS"
      else base0
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

  final private case class TsKernelCtx(
      stateReady: Boolean,
      hasState: Boolean,
      hasInv: Boolean,
      ir: ServiceIRFull
  )

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      allEntities: List[ProfiledEntity],
      typeLookup: Map[String, String],
      nativeAttrs: Boolean,
      kernelCtx: TsKernelCtx
  ): TsOperation =
    val endpoint = op.endpoint
    // The synthesized PK is BIGSERIAL -> Prisma `BigInt`, so a path param that resolves to the
    // PK (`{id}`) must be coerced to `bigint` for the Prisma `where`. A param matching a
    // non-id entity column (e.g. `{code}`) is a normal lookup and keeps its spec type.
    val pkSqlType =
      entity.fields.find(_.fieldName == "id").map(_.ormColumnType).getOrElse("BIGSERIAL")
    val pkIsBigInt = prismaTypeFor(pkSqlType) == "BigInt"
    val bigIntPkParam: Option[String] =
      if !pkIsBigInt then None
      else
        endpoint.pathParams.headOption.collect:
          case p if !entity.fields.exists(_.columnName == p.name) => p.name
    val pathParams = endpoint.pathParams.map: p =>
      val isBigIntPk = bigIntPkParam.contains(p.name)
      val tsType     = if isBigIntPk then "bigint" else tsTypeForParam(p.typeExpr, typeLookup)
      val nm         = toCamelCase(p.name)
      // toExpressPath renames `{order_id}` to `:orderId`, so req.params keys
      // by the camel name; reading the spec name yields undefined (and
      // BigInt('') is 0n, silently mis-keying every multi-word param route).
      val stmt =
        if isBigIntPk then
          s"""      let $nm: bigint;
             |      try {
             |        $nm = BigInt(req.params['$nm'] ?? '');
             |      } catch {
             |        throw NotFound();
             |      }""".stripMargin
        else if tsType == "number" then
          s"""      const $nm = Number(req.params['$nm']);
             |      if (!Number.isFinite($nm)) {
             |        throw NotFound();
             |      }""".stripMargin
        else s"      const $nm = req.params['$nm'] ?? '';"
      TsPathParam(p.name, nm, tsType, stmt)

    val nonIdFields = entity.fields.filterNot(_.fieldName == "id").map(toTsField(_, nativeAttrs))
    val method      = HttpMethods.lower(endpoint.method)
    val expressPath = toExpressPath(endpoint.path)

    val ctx = OperationContext.from(op, entity)

    val customRequestSchemaName = ctx.customRequestSchemaName
    val hasRequestBody          = ctx.hasRequestBody
    val routeKind               = ctx.routeKind

    val (requestBodyType, customRequestFields) =
      if !hasRequestBody then ("", List.empty[TsFieldView])
      else
        customRequestSchemaName match
          case None =>
            (entity.createSchemaName, List.empty[TsFieldView])
          case Some(name) =>
            val byName = endpoint.bodyParams.map(p => p.name -> p.typeExpr).toMap
            val views = OperationContext.customRequestBodyFields(op).map { f =>
              val reduced = byName.get(f.fieldName) match
                case Some(t) => StringRefinements.reduceField(t, None, kernelCtx.ir)
                case None    => StringRefinements.Reduced(None, None, Nil)
              toTsField(f, nativeAttrs, reduced)
            }
            (name, views)

    val readSchemaName = entity.readSchemaName
    val pathArgsCsv    = pathParams.map(_.tsName).mkString(", ")

    val lookupField =
      EmitShared.lookupColumn(entity, endpoint.pathParams.headOption.map(_.name))

    val createDataObj = nonIdFields
      .map(f => s"${f.tsField}: body.${f.tsField}")
      .mkString(", ")

    val whereExpr = pathParams.headOption match
      case Some(p) => s"$lookupField: ${p.tsName}"
      case None    => if pkIsBigInt then "id: 0n" else "id: 0"

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
        case _: RkCreate =>
          val sig = if hasRequestBody then s"body: $requestBodyType" else ""
          (
            op.operationName,
            "body",
            sig,
            s"Promise<$readSchemaName>",
            Option.empty[String],
            "create"
          )
        case _: RkRead =>
          val sig = pathParams.map(p => s"${p.tsName}: ${p.domainType}").mkString(", ")
          (
            op.operationName,
            pathArgsCsv,
            sig,
            s"Promise<$readSchemaName | null>",
            Option.empty[String],
            readCall
          )
        case _: RkList =>
          (
            op.operationName,
            "",
            "",
            s"Promise<$readSchemaName[]>",
            Option.empty[String],
            "findMany"
          )
        case _: RkDelete =>
          val sig = pathParams.map(p => s"${p.tsName}: ${p.domainType}").mkString(", ")
          (op.operationName, pathArgsCsv, sig, "Promise<boolean>", Option.empty[String], deleteCall)
        case _: RkRedirect =>
          val tgt = EmitShared.redirectTargetColumn(entity).getOrElse("url")
          val sig = pathParams.map(p => s"${p.tsName}: ${p.domainType}").mkString(", ")
          (
            op.operationName,
            pathArgsCsv,
            sig,
            s"Promise<$readSchemaName | null>",
            Some(tgt),
            readCall
          )
        case _: RkOther =>
          val args = pathParams.map(p => s"${p.tsName}: ${p.domainType}") ++
            (if hasRequestBody then List(s"body: $requestBodyType") else Nil)
          val call = (pathParams.map(_.tsName) ++ (if hasRequestBody then List("body") else Nil))
            .mkString(", ")
          (op.operationName, call, args.mkString(", "), "Promise<void>", Option.empty[String], "")

    val handlerName = toCamelCase(op.operationName)
    val kernelFn =
      tsKernelServiceFn(
        op,
        handlerName,
        EmitShared.routeKindName(routeKind),
        hasRequestBody,
        requestBodyType,
        allEntities,
        kernelCtx,
        pathParams
      )

    TsOperation(
      operationName = op.operationName,
      handlerName = handlerName,
      method = method,
      path = endpoint.path,
      expressPath = expressPath,
      successStatus = endpoint.successStatus,
      routeKind = EmitShared.routeKindName(routeKind),
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
      routeThroughKernel = kernelFn.isDefined,
      kernelServiceFn = kernelFn.map(_._1).getOrElse(""),
      kernelNoResult = kernelFn.exists(_._1.contains("): Promise<void>")),
      kernelCandidateConsts = kernelFn
        .map(_ =>
          op.dafnyCandidates.map(c => tsCandidateCharsetConst(c.param) -> c.sampleCharset)
        )
        .getOrElse(Nil),
      kernelRouteCallArgs = kernelFn.map(_._2).getOrElse(""),
      authMiddleware = Option.when(op.requiresAuth.nonEmpty)(
        SecurityTs.middlewareName(op.requiresAuth)
      ),
      prismaCall = prismaCall,
      prismaWhere = whereExpr,
      prismaCreateData = createDataObj,
      lookupField = lookupField
    )

  private def toExpressPath(chiPath: String): String =
    """\{([^}]+)\}""".r.replaceAllIn(chiPath, m => ":" + toCamelCase(m.group(1)))

  private def tsTypeForParam(typeExpr: type_expr, typeLookup: Map[String, String]): String =
    EmitShared.paramType(typeExpr, typeLookup, "string", t => s"$t | null")

  // Idiomatic-TS <-> Dafny-runtime converters keyed by the TS domain type; the empty pair marks a
  // type whose TS and Dafny representations coincide (boolean), so no conversion call is emitted.
  private val tsKernelScalarConv: Map[String, (String, String)] = Map(
    "string"  -> ("stringToDafny", "stringFromDafny"),
    "number"  -> ("intToDafny", "intFromDafny"),
    "boolean" -> ("", "")
  )

  // Render the exported service function that routes the operation through its verified Dafny kernel
  // method, marshalling scalar inputs/outputs across the TS/Dafny boundary. Returns None (caller
  // falls back to the route-kind stub) unless the op is kernel-bound and every input/output is a
  // supported scalar; query-param inputs and collection/datatype results are deferred follow-ups.
  // The second tuple element is the route-handler call-arg list (idiomatic, pre-marshalling).
  private def tsSingleQuoted(s: String): String =
    "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

  private def tsCandidateCharsetConst(param: String): String =
    toCamelCase(param) + "Charset"

  // Kernel entity projections must key exactly like the read DTOs, including
  // the TS reserved-identifier escaping.
  private def tsElemToDafny(el: String, ref: String): String =
    if el == "str" then s"stringToDafny($ref as string)" else s"intToDafny($ref as number)"

  private def tsKernelServiceFn(
      op: ProfiledOperation,
      handlerName: String,
      routeKind: String,
      hasRequestBody: Boolean,
      requestBodyType: String,
      allEntities: List[ProfiledEntity],
      kernelCtx: TsKernelCtx,
      routePathParams: List[TsPathParam]
  ): Option[(String, String)] =
    val endpoint = op.endpoint
    val eligible = kernelCtx.stateReady
    op.dafnyMethod.filter(_ => eligible).flatMap: dafnyName =>
      // Inputs convert by their spec kinds: enums through the adapter's
      // checked helper (throws InvalidInputError, mapped to 422), scalar
      // collections through the set/seq builders, options by null-guarding.
      // The someOrNone callback types its argument unknown, so every leaf
      // conversion carries its own cast.
      val bigintParams =
        routePathParams.filter(_.domainType == "bigint").map(_.tsName).toSet
      def inputToDafny(access: String, kind: KernelTypes.Kind): Option[String] =
        kind match
          case KernelTypes.Kind.Scalar("str") => Some(s"stringToDafny($access as string)")
          // A synthesized-pk path param arrives as bigint (the route coerces
          // with BigInt for the Prisma where); intToDafny takes both.
          case KernelTypes.Kind.Scalar("int") if bigintParams.contains(access) =>
            Some(s"intToDafny($access)")
          case KernelTypes.Kind.Scalar("int")  => Some(s"intToDafny($access as number)")
          case KernelTypes.Kind.Scalar("bool") => Some(access)
          case KernelTypes.Kind.Scalar(_)      => None
          case KernelTypes.Kind.EnumK(n)       => Some(s"enumToDafny('$n', $access as string)")
          case KernelTypes.Kind.SetOf(el) =>
            Some(s"dafnySetOf(($access as unknown[]).map((x) => ${tsElemToDafny(el, "x")}))")
          case KernelTypes.Kind.SeqOf(el) =>
            Some(s"dafnySeqOf(($access as unknown[]).map((x) => ${tsElemToDafny(el, "x")}))")
          case KernelTypes.Kind.EntitySetOf(_) => None
          case KernelTypes.Kind.OptOf(inner) =>
            inputToDafny("_v", inner).map(conv => s"someOrNone($access, (_v) => $conv)")
      val specOp = svcOperations(kernelCtx.ir).find(o => operName(o) == op.operationName)
      val specInputTypes = specOp
        .map(o => operInputs(o).map(pd => prmName(pd) -> prmType(pd)).toMap)
        .getOrElse(Map.empty)
      val queryNames = endpoint.queryParams.map(_.name).toSet
      // Query values arrive as strings, so only string-shaped kinds convert.
      def queryKindOk(k: KernelTypes.Kind): Boolean = k match
        // Optional int query params parse via intFromQueryString (throws
        // InvalidInputError, mapped to 422); required ints stay off.
        case KernelTypes.Kind.OptOf(KernelTypes.Kind.Scalar("int")) => true
        case other =>
          KernelTypes.unwrapOpt(other) match
            case KernelTypes.Kind.Scalar("str") | KernelTypes.Kind.EnumK(_) => true
            case _                                                          => false
      val inputs =
        endpoint.pathParams.map(p => p.name -> toCamelCase(p.name)) ++
          endpoint.queryParams.map(p => p.name -> toCamelCase(p.name)) ++
          endpoint.bodyParams.map(p => p.name -> s"body.${p.name}")
      val convertedArgs = inputs.map: (specName, access) =>
        specInputTypes
          .get(specName)
          .flatMap(t => KernelTypes.resolve(kernelCtx.ir, t))
          .filter(k => !queryNames.contains(specName) || queryKindOk(k))
          .flatMap(k =>
            (queryNames.contains(specName), k) match
              case (true, KernelTypes.Kind.OptOf(KernelTypes.Kind.Scalar("int"))) =>
                Some(s"someOrNone($access, (_v) => intToDafny(intFromQueryString(_v)))")
              case _ => inputToDafny(access, k)
          )
      // Which rows each state relation must hydrate for this operation, as
      // the scope literal the bridge receives: per relation a list of source
      // descriptors matched against the bridge's static load schedule. Keyed
      // scopes name the request values through the same access paths the
      // kernel args use; a relation the contracts never touch stays out of
      // the literal and is skipped in both directions.
      val accessByName = inputs.toMap
      val scopeLines: List[String] = specOp match
        case None => List("const scope = undefined;")
        case Some(decl) =>
          val opScopes = HydrationScope.analyze(decl, kernelCtx.ir)
          val opPlan   = HydrationScope.loadPlan(opScopes)
          val entries = opScopes.byField.toList
            .sortBy(_._1)
            .flatMap { (rel, sc) =>
              sc match
                case HydrationScope.Scope.Skip    => None
                case HydrationScope.Scope.Full    => Some(s"$rel: [{ kind: 'full' }]")
                case HydrationScope.Scope.Keys(_) =>
                  // The op's load plan is the authority: a derived source
                  // whose relation never gains rows degrades there to a full
                  // load, and the literal must agree with the schedule the
                  // bridge runs. A persist-only scope keeps an empty keys
                  // descriptor so the relation stays visible to persistence.
                  val steps = opPlan.filter(_.relation == rel)
                  if steps.exists(_.source.isEmpty) then Some(s"$rel: [{ kind: 'full' }]")
                  else
                    val sources = steps.flatMap(_.source)
                    val inputs = sources.collect { case HydrationScope.KeySource.Input(n) =>
                      accessByName.get(n)
                    }
                    if inputs.contains(None) then Some(s"$rel: [{ kind: 'full' }]")
                    else
                      val derived = sources.collect {
                        case HydrationScope.KeySource.FieldOfRows(src, f) =>
                          s"{ kind: 'fieldOf', src: '$src', field: '$f' }"
                        case HydrationScope.KeySource.DependentField(src, coll, ef) =>
                          s"{ kind: 'dependent', src: '$src', collection: '$coll', elem: '$ef' }"
                        case HydrationScope.KeySource.ValueColumn(src, col) =>
                          s"{ kind: 'valueCol', src: '$src', column: '$col' }"
                      }
                      val keyed =
                        if inputs.nonEmpty || derived.isEmpty then
                          List(s"{ kind: 'keys', keys: [${inputs.flatten.mkString(", ")}] }")
                        else Nil
                      Some(s"$rel: [${(keyed ::: derived).mkString(", ")}]")
            }
          StateBridgeTs.scopeAssign("const scope: HydrationScope = ", entries, margin = 4)
      val outputs     = op.responseFields
      val specOutputs = specOp.map(operOutputs).getOrElse(Nil)
      // Mirrors the python and go marshalling: no outputs is a plain effect
      // call, a single entity output projects the read shape flat, scalars
      // unpack positionally.
      val entityOutput = specOutputs match
        case single :: Nil =>
          prmType(single) match
            case NamedTypeF(n, _) => allEntities.find(_.entityName == n)
            case _                => None
        case _ => None
      val seqEntityOutput = specOutputs match
        case single :: Nil =>
          prmType(single) match
            case SeqTypeF(NamedTypeF(n, _), _) => allEntities.find(_.entityName == n)
            case _                             => None
        case _ => None
      val entityOutputFields = entityOutput
        .orElse(seqEntityOutput)
        .map(_.fields.filterNot(f => SensitiveFields.isSensitive(f.columnName)))
        .getOrElse(Nil)
      val tsEntityTypes = Set("string", "number", "boolean", "Date")
      val outFieldKinds: Map[String, KernelTypes.Kind] = entityOutput
        .orElse(seqEntityOutput)
        .map(e =>
          e.fields.flatMap { f =>
            KernelTypes
              .fieldKind(kernelCtx.ir, e.entityName, f.fieldName)
              .map {
                case KernelTypes.Kind.OptOf(inner) => f.fieldName -> inner
                case other                         => f.fieldName -> other
              }
          }.toMap
        )
        .getOrElse(Map.empty)
      def outFieldOk(f: ProfiledField): Boolean =
        outFieldKinds.get(f.fieldName) match
          case Some(KernelTypes.Kind.EnumK(_))                             => true
          case Some(KernelTypes.Kind.SetOf(_) | KernelTypes.Kind.SeqOf(_)) => !f.nullable
          case Some(KernelTypes.Kind.EntitySetOf(_))                       => !f.nullable
          case _                                                           => tsEntityTypes.contains(f.domainType.replaceAll("\\s*\\|\\s*null$", ""))
      val outputsOk =
        if specOutputs.isEmpty then true
        else if entityOutput.isDefined || seqEntityOutput.isDefined then
          entityOutputFields.nonEmpty && entityOutputFields.forall(outFieldOk)
        else outputs.nonEmpty && outputs.forall(f => tsKernelScalarConv.contains(f.domainType))
      // A redirect op's route issues `res.redirect(status, result)`, so the kernel result must be a
      // single string (the redirect target); otherwise leave it to the redirect route-kind branch.
      val redirectOk =
        routeKind != "redirect" || (outputs.sizeIs == 1 && outputs.head.domainType == "string")
      Option.when(convertedArgs.forall(_.isDefined) && outputsOk && redirectOk):
        val candidates = op.dafnyCandidates.map: c =>
          (toCamelCase(c.param), c.sampleLength, tsCandidateCharsetConst(c.param))
        val candArgs = candidates.map((v, _, _) => s"stringToDafny($v)")
        val callArgs = ("state" :: convertedArgs.flatten ::: candArgs).mkString(", ")
        val call     = s"companion.$dafnyName($callArgs)"
        val sig =
          (routePathParams.map(p => s"${p.tsName}: ${p.domainType}") ++
            endpoint.queryParams.map(p => s"${toCamelCase(p.name)}: string | null") ++
            Option.when(hasRequestBody)(s"body: $requestBodyType")).mkString(", ")
        def fromDafny(f: ProfiledField, v: String): String =
          val conv = tsKernelScalarConv(f.domainType)._2
          if conv.isEmpty then v else s"$conv($v)"
        // The state wrapper splices persistState before the LAST body line,
        // so every shape ends on exactly one return statement.
        val noResult  = specOutputs.isEmpty
        val outEntity = entityOutput.orElse(seqEntityOutput)
        val (returnType, bodyLines) =
          if noResult then ("void", List(s"  $call;", "  return;"))
          else if entityOutput.isDefined then
            // The response nests the entity under the spec's output name.
            val outName = specOutputs.headOption.map(prmName).getOrElse("result")
            val pairs = entityOutputFields.map: f =>
              s"      ${f.fieldName}: ${StateBridgeTs
                  .fromDafnyExpr(kernelCtx.ir, outEntity.map(_.entityName).getOrElse(""), f, "out")},"
            (
              "Record<string, unknown>",
              (s"  const out = $call as Record<string, unknown>;" ::
                "  const result = {" ::
                s"    ${outName}: {" :: pairs) :::
                List("    },", "  };", "  return result;")
            )
          else if seqEntityOutput.isDefined then
            val entityName = seqEntityOutput.map(_.entityName).getOrElse("")
            val pairs = entityOutputFields.map: f =>
              s"      ${f.fieldName}: ${StateBridgeTs.fromDafnyExpr(kernelCtx.ir, entityName, f, "elem")},"
            (
              "Array<Record<string, unknown>>",
              (s"  const out = $call;" ::
                "  const result = dafnyCollToArray(out).map((v) => {" ::
                "    const elem = v as Record<string, unknown>;" ::
                "    return {" :: pairs) :::
                List("    };", "  });", "  return result;")
            )
          else
            outputs match
              case single :: Nil =>
                val v = "out" + toPascalCase(single.fieldName)
                (
                  single.domainType,
                  List(s"  const $v = $call;", s"  return ${fromDafny(single, v)};")
                )
              case many =>
                val vars = many.map(f => "out" + toPascalCase(f.fieldName))
                val typeBody =
                  many.map(f => s"${f.fieldName}: ${f.domainType}").mkString("; ")
                val unknowns = List.fill(vars.size)("unknown").mkString(", ")
                val retFields =
                  many.zip(vars).map((f, v) => s"${f.fieldName}: ${fromDafny(f, v)}")
                (
                  s"{ $typeBody }",
                  List(
                    s"  const [${vars.mkString(", ")}] = $call as [$unknowns];",
                    s"  return { ${retFields.mkString(", ")} };"
                  )
                )
        val guardStatus = routeKind match
          case "read" | "redirect" | "delete" => 404
          case _                              => 409
        val guardDetail = if guardStatus == 404 then "not found" else "precondition failed"
        val invCheck =
          if kernelCtx.hasInv then
            List(
              "  if (!(companion.ServiceStateInv(state) as boolean)) {",
              "    throw new HttpError(500, 'service state invariant violated');",
              "  }"
            )
          else Nil
        val guardCheck =
          if candidates.isEmpty then
            List(
              s"  if (!(companion.Requires$dafnyName($callArgs) as boolean)) {",
              s"    throw new HttpError($guardStatus, '$guardDetail');",
              "  }"
            )
          else
            // Sampled candidates: exhaustion means a real precondition
            // failure, not sampling bad luck.
            candidates.map((v, _, _) => s"  let $v = '';") :::
              List("  let okCand = false;", "  for (let attempt = 0; attempt < 8; attempt++) {") :::
              candidates.map((v, len, const) => s"    $v = sampleCandidate($len, $const);") :::
              List(
                s"    if (companion.Requires$dafnyName($callArgs) as boolean) {",
                "      okCand = true;",
                "      break;",
                "    }",
                "  }",
                "  if (!okCand) {",
                s"    throw new HttpError($guardStatus, '$guardDetail');",
                "  }"
              )
        val header = s"export const $handlerName = async ($sig): Promise<$returnType> => {"
        val fn =
          if kernelCtx.hasState then
            val steps =
              scopeLines :::
                "const [state, hydrated] = await hydrateState(tx, scope);" ::
                (invCheck ::: guardCheck).map(_.stripPrefix("  ")) :::
                bodyLines.dropRight(1).map(_.stripPrefix("  ")) :::
                "await persistState(tx, state, hydrated);" ::
                bodyLines.last.stripPrefix("  ") :: Nil
            (header ::
              "  return prisma.$transaction(async (tx) => {" ::
              steps.map("    " + _) :::
              List("  });", "};")).mkString("\n")
          else
            (header :: "  const state = makeState();" ::
              invCheck ::: guardCheck ::: bodyLines ::: List("};")).mkString("\n")
        val routeArgs =
          (endpoint.pathParams.map(p => toCamelCase(p.name)) ++
            endpoint.queryParams.map(p =>
              // Absent optional filters arrive as null, never empty strings.
              s"typeof req.query.${p.name} === 'string' && req.query.${p.name} !== '' ? req.query.${p.name} : null"
            ) ++
            Option.when(hasRequestBody)("body"))
            .mkString(", ")
        (fn, routeArgs)
