package specrest.codegen.ts

import specrest.codegen.AuthSchemes
import specrest.codegen.Compose
import specrest.codegen.DafnyKernel
import specrest.codegen.EmitOptions
import specrest.codegen.EmitShared
import specrest.codegen.EmittedFile
import specrest.codegen.EnvExample
import specrest.codegen.ExtensionStub
import specrest.codegen.OperationContext
import specrest.codegen.Pagination
import specrest.codegen.RenderContext
import specrest.codegen.ScalarOpView
import specrest.codegen.ScalarOps
import specrest.codegen.ScalarStateFieldView
import specrest.codegen.TemplateEngine
import specrest.codegen.TsTemplates
import specrest.codegen.migration.Revision
import specrest.codegen.migration.SchemaCodec
import specrest.codegen.migration.SchemaDiff
import specrest.codegen.migration.SchemaSnapshot
import specrest.codegen.migration.SqlRenderer
import specrest.codegen.openapi.OpenApi
import specrest.ir.Naming
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
    routeThroughKernel: Boolean,
    kernelServiceFn: String,
    kernelRouteCallArgs: String,
    authMiddleware: Option[String],
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
    authDeps: List[String],
    needsDecimal: Boolean,
    needsBuffer: Boolean,
    needsPrismaImport: Boolean,
    needsResultCast: Boolean,
    hasListRoute: Boolean,
    usesKernel: Boolean,
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
    composeEnv: List[TsComposeEnv],
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
    service: TsServiceNames,
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
    val service = TsServiceNames(
      name = ctx.service.name,
      snakeName = ctx.service.snakeName,
      kebabName = ctx.service.kebabName
    )

    val baseTypeLookup = profiled.profile.typeMap.map: (k, v) =>
      k -> v.domain
    val aliasExprs =
      svcTypeAliases(profiled.ir).map(a => talName(a) -> talType(a)).toMap
    def resolveAliasType(te: type_expr, seen: Set[String] = Set.empty): Option[String] =
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
        .map(op => enrichOperation(op, entity, typeLookup, db.nativeAttrs))
        .sortWith((a, b) => EmitShared.byPathSpecificity(a.path, b.path))
      val maintained = triggerMaintainedByTable.getOrElse(entity.tableName, Set.empty)
      buildEntityCtx(
        packageName,
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

    opts.previousSnapshot match
      case None                                      => emitInitial()
      case Some(_) if opts.existingRevisions.isEmpty => emitInitial()
      case Some(prev) =>
        val ops = SchemaDiff.compute(prev, schema)
        if ops.nonEmpty then
          val nextRev = Revision.next(opts.existingRevisions)
          // A state table first appearing in a delta still needs its singleton
          // row; without it every scalar op's guarded UPDATE matches 0 rows.
          val deltaSeeds = ops.collect:
            case CreateTable(t) if ScalarOps.isStateTable(t) =>
              ScalarOps.seedSqlFor(t) + ";"
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
    val ep = v.operation.endpoint
    val method = ep.method match
      case _: GET    => "get"
      case _: POST   => "post"
      case _: PUT    => "put"
      case _: PATCH  => "patch"
      case _: DELETE => "delete"
    val setSql = v.updates
      .map(u => s"${u.columnName} = ${ScalarOps.renderRhs(u.rhs, u.columnName)}")
      .mkString(", ")
    val whereSql =
      ("id = 1" :: v.guards.map(g => s"${g.columnName} ${ScalarOps.sqlCmp(g.cmp)} ${g.lit}"))
        .mkString(" AND ")
    TsScalarOpView(
      handlerName = Naming.toCamelCase(v.operation.operationName, Naming.CamelStrategy.Ts),
      method = method,
      expressPath = ep.path,
      successStatus = ep.successStatus,
      updateSql = s"UPDATE ${ScalarOps.TableName} SET $setSql WHERE $whereSql",
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
      val recipe = specrest.codegen.Dsn.Recipe(
        spec = specrest.codegen.Dsn.Spec(
          shape = specrest.codegen.Dsn.Shape.Url("postgresql"),
          port = 5432,
          suffix = "?schema=public"
        ),
        secrets = specrest.codegen.Dsn.Secrets("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")
      )
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
        composeEnv = dv.composeEnv.map(e => TsComposeEnv(e.key, e.value)),
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
        composeEnv = dv.composeEnv.map(e => TsComposeEnv(e.key, e.value)),
        dsnRecipe = None
      )
    case "mysql" =>
      val dv = specrest.codegen.migration.Mysql.deployment(snake)
      val recipe = specrest.codegen.Dsn.Recipe(
        spec = specrest.codegen.Dsn.Spec(
          shape = specrest.codegen.Dsn.Shape.Url("mysql"),
          port = 3306
        ),
        secrets = specrest.codegen.Dsn.Secrets("MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_DATABASE")
      )
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
        composeEnv = dv.composeEnv.map(e => TsComposeEnv(e.key, e.value)),
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
      "pagination"        -> Pagination.view
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
      authDeps = operations.flatMap(_.authMiddleware).distinct.sorted,
      needsDecimal = needsDecimal,
      needsBuffer = needsBuffer,
      needsPrismaImport = needsDecimal,
      needsResultCast = needsResultCast,
      hasListRoute = operations.exists(_.routeKind == "list"),
      usesKernel = operations.exists(_.routeThroughKernel),
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
      val stmt =
        if isBigIntPk then
          s"""      let $nm: bigint;
             |      try {
             |        $nm = BigInt(req.params['${p.name}'] ?? '');
             |      } catch {
             |        throw NotFound();
             |      }""".stripMargin
        else if tsType == "number" then
          s"""      const $nm = Number(req.params['${p.name}']);
             |      if (!Number.isFinite($nm)) {
             |        throw NotFound();
             |      }""".stripMargin
        else s"      const $nm = req.params['${p.name}'] ?? '';"
      TsPathParam(p.name, nm, tsType, stmt)

    val nonIdFields = entity.fields.filterNot(_.fieldName == "id").map(toTsField(_, nativeAttrs))
    val method = endpoint.method match
      case _: GET    => "get"
      case _: POST   => "post"
      case _: PUT    => "put"
      case _: PATCH  => "patch"
      case _: DELETE => "delete"
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
            val pathParamNames = endpoint.pathParams.map(_.name).toSet
            val fields = op.requestBodyFields
              .filterNot(f => pathParamNames.contains(f.fieldName))
              .map(toTsField(_, nativeAttrs))
            (name, fields)

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
        typeLookup
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

  private def redirectTarget(
      @scala.annotation.unused op: ProfiledOperation,
      entity: ProfiledEntity
  ): Option[String] =
    val candidates = List("url", "location", "redirect_url")
    candidates
      .find(c => entity.fields.exists(_.columnName == c))
      .map(toCamelCase)

  private def tsTypeForParam(typeExpr: type_expr, typeLookup: Map[String, String]): String =
    typeExpr match
      case NamedTypeF(n, _)      => typeLookup.getOrElse(n, "string")
      case OptionTypeF(inner, _) => s"${tsTypeForParam(inner, typeLookup)} | null"
      case _                     => "string"

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
  private def tsKernelServiceFn(
      op: ProfiledOperation,
      handlerName: String,
      routeKind: String,
      hasRequestBody: Boolean,
      requestBodyType: String,
      typeLookup: Map[String, String]
  ): Option[(String, String)] =
    val endpoint = op.endpoint
    op.dafnyMethod.filter(_ => endpoint.queryParams.isEmpty).flatMap: dafnyName =>
      val inputs =
        endpoint.pathParams.map(p =>
          toCamelCase(p.name) -> tsTypeForParam(p.typeExpr, typeLookup)
        ) ++
          endpoint.bodyParams.map(p =>
            s"body.${toCamelCase(p.name)}" -> tsTypeForParam(p.typeExpr, typeLookup)
          )
      val convertedArgs = inputs.map: (access, tsType) =>
        tsKernelScalarConv.get(tsType).map: (toDafny, _) =>
          if toDafny.isEmpty then access else s"$toDafny($access)"
      val outputs = op.responseFields
      val outputsOk =
        outputs.nonEmpty && outputs.forall(f => tsKernelScalarConv.contains(f.domainType))
      // A redirect op's route issues `res.redirect(status, result)`, so the kernel result must be a
      // single string (the redirect target); otherwise leave it to the redirect route-kind branch.
      val redirectOk =
        routeKind != "redirect" || (outputs.sizeIs == 1 && outputs.head.domainType == "string")
      Option.when(convertedArgs.forall(_.isDefined) && outputsOk && redirectOk):
        val callArgs = ("state" :: convertedArgs.flatten).mkString(", ")
        val call     = s"companion.$dafnyName($callArgs)"
        val sig =
          (endpoint.pathParams.map(p =>
            s"${toCamelCase(p.name)}: ${tsTypeForParam(p.typeExpr, typeLookup)}"
          ) ++ Option.when(hasRequestBody)(s"body: $requestBodyType")).mkString(", ")
        def fromDafny(f: ProfiledField, v: String): String =
          val conv = tsKernelScalarConv(f.domainType)._2
          if conv.isEmpty then v else s"$conv($v)"
        val (returnType, bodyLines) =
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
                many.map(f => s"${toCamelCase(f.fieldName)}: ${f.domainType}").mkString("; ")
              val unknowns = List.fill(vars.size)("unknown").mkString(", ")
              val retFields =
                many.zip(vars).map((f, v) => s"${toCamelCase(f.fieldName)}: ${fromDafny(f, v)}")
              (
                s"{ $typeBody }",
                List(
                  s"  const [${vars.mkString(", ")}] = $call as [$unknowns];",
                  s"  return { ${retFields.mkString(", ")} };"
                )
              )
        val header = s"export const $handlerName = async ($sig): Promise<$returnType> => {"
        val fn =
          (header :: "  const state = makeState();" :: bodyLines ::: List("};")).mkString("\n")
        val routeArgs =
          (endpoint.pathParams.map(p => toCamelCase(p.name)) ++ Option.when(hasRequestBody)("body"))
            .mkString(", ")
        (fn, routeArgs)
