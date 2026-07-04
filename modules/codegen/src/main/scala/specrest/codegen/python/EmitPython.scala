package specrest.codegen.python

import specrest.codegen.AuthSchemes
import specrest.codegen.Compose
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
import specrest.codegen.SensitiveFields
import specrest.codegen.TemplateEngine
import specrest.codegen.Templates
import specrest.codegen.alembic.BuildMigrationOptions
import specrest.codegen.alembic.Migration
import specrest.codegen.migration.AlembicRenderer
import specrest.codegen.migration.CanonicalType
import specrest.codegen.migration.Dialect
import specrest.codegen.migration.MigrationPlan
import specrest.codegen.migration.Revision
import specrest.codegen.migration.SchemaCodec
import specrest.codegen.migration.SchemaSnapshot
import specrest.codegen.openapi.OpenApi
import specrest.convention.EndpointSpec
import specrest.convention.StringRefinements
import specrest.ir.HttpMethods
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

import scala.collection.mutable

final case class AlembicDelta(
    revision: String,
    downRevision: String,
    createdDate: String,
    upgradeStatements: List[String],
    downgradeStatements: List[String],
    needsPostgresDialect: Boolean
)

final case class StateModelCtx(
    tableName: String,
    stateFields: List[ScalarStateFieldView]
)

final case class PyScalarOp(
    handlerName: String,
    method: String,
    path: String,
    successStatus: Int,
    guardConds: List[String],
    valuesArgs: String,
    guardPretty: String,
    authDependency: Option[String]
)

final case class StateOpsCtx(
    stateFields: List[ScalarStateFieldView],
    scalarOps: List[PyScalarOp],
    authDeps: List[String]
)

final private case class StdlibImport(module: String, names: List[String])

final private case class EnrichedPathParam(
    name: String,
    domainType: String,
    routerType: String
)

final private case class CustomRequestSchema(schemaName: String, fields: List[SchemaFieldView])

final private case class SchemaFieldView(
    columnName: String,
    validationType: String,
    domainType: String,
    nullable: Boolean,
    fieldSuffix: String,
    updateSuffix: String
)

final private case class ModelInitFieldView(columnName: String, bodyAccessor: String)

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
    customRequestSchema: Option[CustomRequestSchema],
    dafnyMethod: Option[String],
    kernelHandlerSignature: String,
    kernelCallArgs: List[String],
    kernelGuardName: String,
    kernelGuardStatus: Int,
    kernelGuardDetail: String,
    kernelHasState: Boolean,
    kernelHasInv: Boolean,
    kernelResultBind: String,
    kernelResultExpr: String,
    initFields: List[ModelInitFieldView],
    authDependency: Option[String]
)

final private case class EntityImports(
    sqlalchemyImports: List[String],
    postgresImports: List[String],
    stdlibImports: List[StdlibImport]
)

final private case class ServiceTemplateImports(
    sqlalchemyPlainImports: List[String],
    sqlalchemyAliasImports: List[String],
    schemas: List[String],
    needsModelImport: Boolean,
    needsDafnyKernel: Boolean,
    needsStateBridge: Boolean,
    needsAny: Boolean,
    adapterImports: List[String],
    hasAppImports: Boolean,
    needsTypingCast: Boolean
)

@SuppressWarnings(Array("org.wartremover.warts.Var"))
object EmitPython:

  private val StdlibTypeSources: Map[String, StdlibImport] = Map(
    "datetime" -> StdlibImport("datetime", List("datetime")),
    "date"     -> StdlibImport("datetime", List("date")),
    "Decimal"  -> StdlibImport("decimal", List("Decimal")),
    "UUID"     -> StdlibImport("uuid", List("UUID"))
  )

  private val PostgresDialectTypes: Set[String] = Set("JSONB")

  def emit(profiled: ProfiledService, opts: EmitOptions = EmitOptions()): List[EmittedFile] =
    val ctx        = RenderContext.buildRenderContext(profiled, opts.dafnyKernel)
    val engine     = new TemplateEngine
    val typeLookup = EmitShared.aliasResolvedDomainLookup(profiled, Some(t => s"$t | None"))
    val bridgePlan = StateBridge.plan(profiled)
    val kernelCtx = KernelCtx(
      stateReady = irStateFields(profiled.ir).isEmpty || bridgePlan.isRight,
      hasState = bridgePlan.toOption.exists(StateBridge.hasState),
      hasInv = irStateFields(profiled.ir).nonEmpty,
      ir = profiled.ir
    )
    val templates = Templates.pythonFastapiPostgres
    val dialect   = Dialect.forDatabase(profiled.profile.database)
    val files     = List.newBuilder[EmittedFile]

    files += EmittedFile("app/__init__.py", "")
    files += EmittedFile("app/main.py", engine.renderAny(templates.main, ctx))
    files += EmittedFile("app/config.py", engine.renderAny(templates.config, ctx))
    files += EmittedFile("app/security.py", SecurityPython.emit(profiled))
    files += EmittedFile("app/database.py", engine.renderAny(templates.database, ctx))
    files += EmittedFile("app/redaction.py", engine.renderAny(templates.redaction, ctx))
    files += EmittedFile(
      "app/pagination.py",
      engine.renderAny(templates.pagination, Map("pagination" -> Pagination.view))
    )
    files += EmittedFile("app/db/__init__.py", "")
    files += EmittedFile("app/db/base.py", engine.renderAny(templates.dbBase, ctx))
    files += EmittedFile("app/models/__init__.py", PyInit.models(profiled))
    files += EmittedFile("app/schemas/__init__.py", PyInit.schemas(profiled))
    files += EmittedFile("app/routers/__init__.py", PyInit.routers(profiled))
    files += EmittedFile("app/services/__init__.py", PyInit.services(profiled))
    files += EmittedFile(
      "app/extensions/__init__.py",
      ExtensionStub.python,
      preserve = true
    )
    files += EmittedFile("app/routers/admin.py", AdminRouter.emit(profiled))

    val scalarFields = ScalarOps.stateFields(profiled)
    val scalarOps    = ScalarOps.views(profiled)
    if scalarFields.nonEmpty then
      files += EmittedFile(
        "app/models/service_state.py",
        engine.renderAny(
          templates.modelServiceState,
          StateModelCtx(ScalarOps.TableName, scalarFields)
        )
      )
    if scalarOps.nonEmpty then
      val pyOps = scalarOps.map(pyScalarOp)
      val stateCtx =
        StateOpsCtx(scalarFields, pyOps, pyOps.flatMap(_.authDependency).distinct.sorted)
      files += EmittedFile(
        "app/services/state_ops.py",
        engine.renderAny(templates.serviceStateOps, stateCtx)
      )
      files += EmittedFile(
        "app/routers/state_ops.py",
        engine.renderAny(templates.routerStateOps, stateCtx)
      )

    for entity <- ctx.entities do
      val table = schemaTables(ctx.schema).find(t => tableEntityName(t) == entity.entityName)
      val entityOps = ctx.operations
        .filter(_.targetEntity.contains(entity.entityName))
        .map(op => enrichOperation(op, entity, typeLookup, kernelCtx))
        .sortWith((a, b) => EmitShared.byPathSpecificity(a.path, b.path))

      val imports        = collectEntityImports(entity, dialect)
      val routerImports  = collectRouterImports(entity, entityOps)
      val serviceImports = collectServiceImports(entity, entityOps)

      val entitySnake = Naming.toSnakeCase(entity.entityName)
      val routerSnake = Naming.toSnakeCase(Naming.pluralize(entity.entityName))

      val nonIdFields = entity.fields.filterNot(_.fieldName == "id")
      val modelFields =
        nonIdFields.map(f => f.copy(ormColumnType = modelColumnType(f.ormColumnType, dialect)))
      val readFieldsRaw =
        nonIdFields.filterNot(f => SensitiveFields.isSensitive(f.columnName))
      val nonIdFieldViews =
        nonIdFields.map(f =>
          schemaInputField(f, EmitShared.entityFieldRefinement(profiled, entity, f))
        )
      val readFieldViews       = readFieldsRaw.map(schemaReadField)
      val customRequestSchemas = entityOps.flatMap(_.customRequestSchema)
      val schemaStdlib         = collectSchemaStdlibImports(entity, customRequestSchemas)
      val needsSecretStr =
        nonIdFields.exists(f => SensitiveFields.isSensitive(f.columnName)) ||
          customRequestSchemas.exists(_.fields.exists(_.validationType == "SecretStr"))

      val base = entityScope(ctx, entity, table, entityOps)
      val modelCtx = base +
        ("nonIdFields"       -> modelFields) +
        ("sqlalchemyImports" -> imports.sqlalchemyImports) +
        ("postgresImports"   -> imports.postgresImports) +
        ("stdlibImports"     -> imports.stdlibImports)
      val schemaCtx = base +
        ("nonIdFields"          -> nonIdFieldViews) +
        ("readFields"           -> readFieldViews) +
        ("customRequestSchemas" -> customRequestSchemas) +
        ("needsSecretStr"       -> needsSecretStr) +
        ("needsField" -> (nonIdFieldViews ++ customRequestSchemas.flatMap(_.fields))
          .exists(v => v.fieldSuffix.contains("Field(") || v.updateSuffix.contains("Field("))) +
        ("stdlibImports" -> schemaStdlib)
      val routerCtx  = base + ("routerImports"  -> routerImports)
      val serviceCtx = base + ("serviceImports" -> serviceImports)

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

    files += EmittedFile("alembic.ini", engine.renderAny(templates.alembicIni, ctx))
    files += EmittedFile("alembic/env.py", engine.renderAny(templates.alembicEnv, ctx))
    files += EmittedFile(
      ".spec-snapshot.json",
      SchemaCodec.encode(SchemaSnapshot.of(profiled.schema))
    )
    val emitInitial: () => Unit = () =>
      val migration = Migration.buildAlembicMigration(
        profiled.schema,
        BuildMigrationOptions(revision = opts.revision, createdDate = opts.createdDate),
        dialect
      )
      val alembicCtx = projectScope(ctx) + ("migration" -> migration)
      files += EmittedFile(
        s"alembic/versions/${migration.revision}_initial_schema.py",
        engine.renderAny(templates.alembicMigration, alembicCtx)
      )

    MigrationPlan.of(opts.previousSnapshot, opts.existingRevisions, profiled.schema) match
      case MigrationPlan.Initial  => emitInitial()
      case MigrationPlan.UpToDate => ()
      case MigrationPlan.Delta(ops, nextRev) =>
        val downRev = Revision.head(opts.existingRevisions).getOrElse("001")
        val deltaSeeds =
          ScalarOps.deltaStateSeeds(ops).map(seed => s"""op.execute("$seed")""")
        val delta = AlembicDelta(
          revision = nextRev,
          downRevision = downRev,
          createdDate = opts.createdDate.getOrElse(java.time.LocalDate.now.toString),
          upgradeStatements = AlembicRenderer.upgrade(ops, dialect) ++ deltaSeeds,
          downgradeStatements = AlembicRenderer.downgrade(ops, dialect),
          needsPostgresDialect = Dialect.hasPostgresDialectTypes(ops, dialect)
        )
        val deltaCtx = Map[String, Any](
          "service"   -> ctx.service,
          "profile"   -> ctx.profile,
          "migration" -> delta
        )
        files += EmittedFile(
          s"alembic/versions/${nextRev}_schema_update.py",
          engine.renderAny(templates.alembicDelta, deltaCtx)
        )

    files += EmittedFile("pyproject.toml", engine.renderAny(templates.pyproject, ctx))
    files += EmittedFile("Dockerfile", engine.renderAny(templates.dockerfile, ctx))
    val composeIn = composeInputs(ctx)
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
    val authEnv = AuthSchemes.envEntries(profiled.ir).map: (k, v) =>
      EnvExample.Entry(
        k,
        v,
        Some("Credential for the spec's security scheme; unset rejects requests (401)")
      )
    files += EmittedFile(".env.example", EnvExample.render(composeIn, authEnv))
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

    ctx.dafnyKernel.foreach: kernel =>
      val pkg = kernel.packagePath.stripSuffix("/")
      kernel.files.toList.sortBy(_._1).foreach: (rel, content) =>
        files += EmittedFile(s"$pkg/$rel", content)
      if !kernel.files.contains("__init__.py") then
        files += EmittedFile(s"$pkg/__init__.py", "from . import module_  # noqa: F401\n")
      files += EmittedFile(
        "app/services/_dafny_adapter.py",
        engine.renderAny(templates.dafnyAdapter, ctx)
      )
      files += EmittedFile(
        "app/services/_synth.py",
        engine.renderAny(templates.synthService, ctx)
      )
      if kernelCtx.hasState then
        files += EmittedFile("app/services/_state_bridge.py", StateBridge.emit(profiled))

    files.result()

  private def composeInputs(ctx: RenderContext): Compose.Inputs =
    Compose.Inputs(
      family = Compose.Family.Python,
      appPort = 8000,
      dbVolumeName = "db_data",
      hasDbService = ctx.db.hasDbService,
      dbImage = ctx.db.dbImage,
      dbPort = ctx.db.dbPort,
      dbVolumePath = ctx.db.dbVolumePath,
      dbHealthCmd = ctx.db.dbHealthCmd,
      secretEnv = ctx.db.composeEnv.map(e => e.key -> e.value),
      dsnComposeNetwork = ctx.db.envUrl,
      dsnRecipe = ctx.db.dsnRecipe,
      envExampleHeaderLine =
        Some(s"Database connection string (async SQLAlchemy + ${ctx.profile.dbDriver})")
    )

  // One shared base scope per entity render (the Go and TS emitters use the
  // same merged-map pattern); each template's extras ride on top.
  private def projectScope(ctx: RenderContext): Map[String, Any] =
    Map[String, Any](
      "service"    -> ctx.service,
      "profile"    -> ctx.profile,
      "entities"   -> ctx.entities,
      "operations" -> ctx.operations,
      "endpoints"  -> ctx.endpoints,
      "schema"     -> ctx.schema
    )

  private def entityScope(
      ctx: RenderContext,
      entity: ProfiledEntity,
      table: Option[table_spec],
      entityOps: List[EnrichedOperation]
  ): Map[String, Any] =
    projectScope(ctx) +
      ("entity"           -> entity) +
      ("table"            -> table) +
      ("entityOperations" -> entityOps)

  // Refinements the reduction can express become pydantic constraints, so the
  // boundary rejects what the spec's types reject; the compiled Dafny guards
  // cannot check regex predicates (they are proof-level stubs) and the
  // database CHECKs cannot either, so this is the only enforcement point.
  private def schemaInputField(
      f: ProfiledField,
      reduced: StringRefinements.Reduced
  ): SchemaFieldView =
    val sensitive = SensitiveFields.isSensitive(f.columnName)
    val ptype     = if sensitive then "SecretStr" else f.validationType
    val args =
      if sensitive || f.domainType != "str" then Nil
      else
        reduced.minLen.map(n => s"min_length=$n").toList ++
          reduced.maxLen.map(n => s"max_length=$n").toList ++
          StringRefinements.combinedPattern(reduced.patterns).map(p => s"pattern=r\"$p\"").toList
    val fieldSuffix =
      if args.isEmpty then if f.nullable then " = None" else ""
      else if f.nullable then s" = Field(default=None, ${args.mkString(", ")})"
      else s" = Field(${args.mkString(", ")})"
    val updateSuffix =
      if args.isEmpty then " = None"
      else s" = Field(default=None, ${args.mkString(", ")})"
    SchemaFieldView(f.columnName, ptype, f.domainType, f.nullable, fieldSuffix, updateSuffix)

  private def schemaReadField(f: ProfiledField): SchemaFieldView =
    SchemaFieldView(f.columnName, f.validationType, f.domainType, f.nullable, "", "")

  private def modelInitField(f: ProfiledField): ModelInitFieldView =
    val accessor =
      if SensitiveFields.isSensitive(f.columnName) then
        if f.nullable then
          s"body.${f.columnName}.get_secret_value() if body.${f.columnName} is not None else None"
        else s"body.${f.columnName}.get_secret_value()"
      else s"body.${f.columnName}"
    ModelInitFieldView(f.columnName, accessor)

  private def pythonTypeForParam(
      typeExpr: type_expr,
      typeLookup: Map[String, String]
  ): String =
    EmitShared.paramType(typeExpr, typeLookup, "str", t => s"$t | None")

  final private case class KernelCtx(
      stateReady: Boolean,
      hasState: Boolean,
      hasInv: Boolean,
      ir: ServiceIRFull
  )

  // Python types the kernel boundary can convert (matching the Go/TS gates);
  // an op with anything else falls back to its route-kind body, which for an
  // LLM_SYNTHESIS op is the fail-loud stub.
  private val KernelScalarTypes = Set("str", "int", "bool")

  private def kernelFromDafny(domainType: String, ref: String): String = domainType match
    case "str"  => s"from_dafny_str($ref)"
    case "bool" => s"bool($ref)"
    case _      => s"int($ref)"

  private def enrichOperation(
      op: ProfiledOperation,
      entity: ProfiledEntity,
      typeLookup: Map[String, String],
      kernelCtx: KernelCtx
  ): EnrichedOperation =
    val endpoint = op.endpoint
    val pathParamsWithTypes = endpoint.pathParams.map { p =>
      val t = pythonTypeForParam(p.typeExpr, typeLookup)
      // FastAPI's int is unbounded, so an out-of-range id must fail at the
      // router boundary (422) instead of reaching the driver as a 500; the
      // bounds come from the column the param binds against (asyncpg rejects
      // out-of-int32 values for INTEGER columns outright). Services keep the
      // plain type.
      val routerType =
        if t == "int" then
          val col = EmitShared.lookupColumn(entity, Some(p.name))
          val colType = entity.fields
            .find(_.columnName == col)
            .map(_.ormColumnType.toUpperCase(java.util.Locale.ROOT))
            .getOrElse("BIGINT")
          // The python profile carries SQLAlchemy type names (Integer,
          // SmallInteger, BigInteger); spec-level typeMap overrides may carry
          // raw SQL strings. Both vocabularies match explicitly so the bounds
          // never depend on a casing coincidence.
          val (lo, hi) = colType match
            case "SMALLINT" | "SMALLINTEGER"  => ("-32768", "32767")
            case "INTEGER" | "INT" | "SERIAL" => ("-2147483648", "2147483647")
            case _                            => ("-9223372036854775808", "9223372036854775807")
          s"Annotated[int, Path(ge=$lo, le=$hi)]"
        else t
      EnrichedPathParam(p.name, t, routerType)
    }

    val method = HttpMethods.lower(endpoint.method)

    val ctx = OperationContext.from(op, entity)

    val pathParamCallArgs = pathParamsWithTypes.map(_.name).mkString(", ")
    val hasRequestBody    = ctx.hasRequestBody
    val routeKind         = ctx.routeKind

    val (requestBodyType, customRequestSchema) =
      if !hasRequestBody then ("", Option.empty[CustomRequestSchema])
      else
        ctx.customRequestSchemaName match
          case None =>
            (entity.createSchemaName, Option.empty[CustomRequestSchema])
          case Some(name) =>
            val fields = OperationContext.customRequestBodyFields(op)
            val byName = endpoint.bodyParams.map(p => p.name -> p.typeExpr).toMap
            val views = fields.map { f =>
              val reduced = byName.get(f.fieldName) match
                case Some(t) => StringRefinements.reduceField(t, None, kernelCtx.ir)
                case None    => StringRefinements.Reduced(None, None, Nil)
              schemaInputField(f, reduced)
            }
            (name, Some(CustomRequestSchema(name, views)))

    val (
      responseAnnotation,
      serviceCallArgs,
      pathParamSignature,
      serviceExtraArgs,
      serviceReturnAnno
    ) =
      routeKind match
        case _: RkCreate =>
          (
            entity.readSchemaName,
            if hasRequestBody then "body" else "",
            "",
            "",
            entity.readSchemaName
          )
        case _: RkRead =>
          val sig = pathParamsWithTypes.map(p => s"${p.name}: ${p.domainType}").mkString(", ")
          (
            entity.readSchemaName,
            pathParamCallArgs,
            sig,
            sig,
            s"${entity.readSchemaName} | None"
          )
        case _: RkList =>
          (
            s"list[${entity.readSchemaName}]",
            "",
            "",
            "",
            s"list[${entity.readSchemaName}]"
          )
        case _: RkDelete =>
          val sig = pathParamsWithTypes.map(p => s"${p.name}: ${p.domainType}").mkString(", ")
          ("Response", pathParamCallArgs, sig, sig, "bool")
        case _: RkRedirect =>
          val sig = pathParamsWithTypes.map(p => s"${p.name}: ${p.domainType}").mkString(", ")
          ("RedirectResponse", pathParamCallArgs, sig, sig, "str")
        case _: RkOther =>
          val args = pathParamsWithTypes.map(p => s"${p.name}: ${p.domainType}") ++
            (if hasRequestBody then List(s"body: $requestBodyType") else Nil)
          val call = (pathParamsWithTypes.map(_.name) ++
            (if hasRequestBody then List("body") else Nil)).mkString(", ")
          ("None", call, "", args.mkString(", "), "None")

    val pathParamName =
      if pathParamsWithTypes.nonEmpty then pathParamsWithTypes.head.name else "id"
    val modelLookupColumn =
      EmitShared.lookupColumn(entity, endpoint.pathParams.headOption.map(_.name))

    // The kernel boundary converts scalars only; state must round-trip through
    // the bridge; every output must convert back. Anything else keeps today's
    // fail-loud stub instead of silently running on wrong values.
    // Query-parameter ops are a stated v1 non-goal (#510): the router emits no
    // query variables, so routing them through the kernel would NameError.
    val kernelArgSources: List[(String, String)] =
      endpoint.pathParams.map(p => p.name -> pythonTypeForParam(p.typeExpr, typeLookup)) ++
        endpoint.bodyParams.map(p =>
          s"body.${p.name}" -> pythonTypeForParam(p.typeExpr, typeLookup)
        )
    val kernelOuts = op.responseFields
    val kernelEligible =
      op.dafnyMethod.isDefined &&
        endpoint.queryParams.isEmpty &&
        kernelCtx.stateReady &&
        kernelArgSources.forall((_, t) => KernelScalarTypes.contains(t)) &&
        kernelOuts.nonEmpty &&
        kernelOuts.forall(f => !f.nullable && KernelScalarTypes.contains(f.domainType))
    val dafnyMethodFinal = if kernelEligible then op.dafnyMethod else None

    val kernelCallArgs = kernelArgSources.map: (arg, t) =>
      if t == "str" then s"to_dafny_str($arg)" else arg
    val isRedirectKind = routeKind match
      case _: RkRedirect => true
      case _             => false
    val (kernelResultBind, kernelResultExpr) =
      if isRedirectKind then
        val out = kernelOuts.headOption
        ("result", out.map(f => kernelFromDafny(f.domainType, "result")).getOrElse("result"))
      else
        kernelOuts match
          case Nil => ("result", "result")
          case outs =>
            val binds = outs.map(f => s"out_${f.fieldName}")
            val pairs = outs
              .zip(binds)
              .map((f, b) => s"\"${f.fieldName}\": ${kernelFromDafny(f.domainType, b)}")
            (binds.mkString(", "), s"{${pairs.mkString(", ")}}")
    val kernelGuardStatus = routeKind match
      case _: RkRead | _: RkRedirect | _: RkDelete => 404
      case _                                       => 409
    val kernelGuardDetail =
      if kernelGuardStatus == 404 then "not found" else "precondition failed"

    val (kernelSig, _) = kernelSignatureAndArgs(endpoint, requestBodyType, typeLookup)

    // When the operation is routed through the Dafny kernel, the service handler's signature
    // is `kernelHandlerSignature` (path + query + body, in that order). The router must pass
    // the same arg list — `serviceCallArgs` is route-kind specific and may omit some of them
    // (e.g. RkCreate's serviceCallArgs is just "body", losing path/query params).
    val effectiveServiceCallArgs =
      if dafnyMethodFinal.isDefined then kernelRouterCallArgs(endpoint)
      else serviceCallArgs

    val (responseAnnotationFinal, serviceReturnAnnoFinal) =
      if dafnyMethodFinal.isDefined && !isRedirectKind then
        ("dict[str, Any]", "dict[str, Any]")
      else (responseAnnotation, serviceReturnAnno)

    val kindName = op.kind match
      case _: Create        => "Create"
      case _: Read          => "Read"
      case _: Replace       => "Replace"
      case _: PartialUpdate => "PartialUpdate"
      case _: Deletea       => "Delete"
      case _: CreateChild   => "CreateChild"
      case _: FilteredRead  => "FilteredRead"
      case _: SideEffect    => "SideEffect"
      case _: BatchMutation => "BatchMutation"
      case _: Transition    => "Transition"

    val createInitFields = routeKind match
      case _: RkCreate => entity.fields.filterNot(_.fieldName == "id").map(modelInitField)
      case _           => List.empty[ModelInitFieldView]

    EnrichedOperation(
      operationName = op.operationName,
      handlerName = op.handlerName,
      kind = kindName,
      method = method,
      path = endpoint.path,
      successStatus = endpoint.successStatus,
      pathParamsWithTypes = pathParamsWithTypes,
      hasRequestBody = hasRequestBody,
      requestBodyType = requestBodyType,
      responseAnnotation = responseAnnotationFinal,
      serviceCallArgs = effectiveServiceCallArgs,
      routeKind = EmitShared.routeKindName(routeKind),
      pathParamSignature = pathParamSignature,
      serviceSignatureExtraArgs = serviceExtraArgs,
      serviceReturnAnnotation = serviceReturnAnnoFinal,
      modelLookupColumn = modelLookupColumn,
      pathParamName = pathParamName,
      customRequestSchema = customRequestSchema,
      dafnyMethod = dafnyMethodFinal,
      kernelHandlerSignature = kernelSig,
      kernelCallArgs = kernelCallArgs,
      kernelGuardName = s"Requires${op.operationName}",
      kernelGuardStatus = kernelGuardStatus,
      kernelGuardDetail = kernelGuardDetail,
      kernelHasState = kernelCtx.hasState,
      kernelHasInv = kernelCtx.hasInv,
      kernelResultBind = kernelResultBind,
      kernelResultExpr = kernelResultExpr,
      initFields = createInitFields,
      authDependency =
        Option.when(op.requiresAuth.nonEmpty)(SecurityPython.dependencyName(op.requiresAuth))
    )

  private def kernelSignatureAndArgs(
      endpoint: EndpointSpec,
      requestBodyType: String,
      typeLookup: Map[String, String]
  ): (String, List[String]) =
    val pathSig =
      endpoint.pathParams.map(p => s"${p.name}: ${pythonTypeForParam(p.typeExpr, typeLookup)}")
    val querySig =
      endpoint.queryParams.map(p => s"${p.name}: ${pythonTypeForParam(p.typeExpr, typeLookup)}")
    val bodySig =
      if endpoint.bodyParams.nonEmpty && requestBodyType.nonEmpty then
        List(s"body: $requestBodyType")
      else Nil
    val callArgs = endpoint.pathParams.map(_.name) ++
      endpoint.queryParams.map(_.name) ++
      endpoint.bodyParams.map(p => s"body.${p.name}")
    ((pathSig ++ querySig ++ bodySig).mkString(", "), callArgs)

  private def kernelRouterCallArgs(endpoint: EndpointSpec): String =
    val parts = endpoint.pathParams.map(_.name) ++
      endpoint.queryParams.map(_.name) ++
      (if endpoint.bodyParams.nonEmpty then List("body") else Nil)
    parts.mkString(", ")

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

  private def modelColumnType(ormColumnType: String, dialect: Dialect): String =
    if ormColumnType == "JSONB" && dialect.saType(CanonicalType.Json).importModule.isEmpty then
      "JSON"
    else ormColumnType

  private def pyScalarOp(v: ScalarOpView): PyScalarOp =
    val ep     = v.operation.endpoint
    val method = HttpMethods.lower(ep.method)
    val guardConds = v.guards.map: g =>
      s"ServiceState.${g.columnName} ${ScalarOps.pyCmp(g.cmp)} ${g.lit}"
    val valuesArgs = v.updates
      .map(u => s"${u.columnName}=${ScalarOps.renderRhs(u.rhs, s"ServiceState.${u.columnName}")}")
      .mkString(", ")
    PyScalarOp(
      handlerName = v.operation.handlerName,
      method = method,
      path = ep.path,
      successStatus = ep.successStatus,
      guardConds = guardConds,
      valuesArgs = valuesArgs,
      guardPretty = v.guardPretty,
      authDependency = Option.when(v.operation.requiresAuth.nonEmpty)(
        SecurityPython.dependencyName(v.operation.requiresAuth)
      )
    )

  private def collectEntityImports(entity: ProfiledEntity, dialect: Dialect): EntityImports =
    val sqlSet         = mutable.Set.empty[String]
    val pgSet          = mutable.Set.empty[String]
    val stdlibByModule = mutable.Map.empty[String, mutable.Set[String]]
    // the id column renders as `mapped_column(primary_key=True)` with no type,
    // so its column type must not contribute an import
    for field <- entity.fields.filterNot(_.fieldName == "id") do
      val colType = modelColumnType(field.ormColumnType, dialect)
      if PostgresDialectTypes.contains(colType) then pgSet += colType
      else sqlSet += colType
      mergeStdlibImport(stdlibByModule, field.domainType)
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
    for field  <- entity.fields do mergeStdlibImport(stdlibByModule, field.domainType)
    for schema <- customRequestSchemas; view <- schema.fields do
      mergeStdlibImport(stdlibByModule, view.domainType)
    finalizeStdlibImports(stdlibByModule)

  // The router's whole import header assembles here as one rendered block
  // (isort groups: stdlib, third-party, first-party), so the template carries
  // no per-import conditionals.
  private def collectRouterImports(
      entity: ProfiledEntity,
      operations: List[EnrichedOperation]
  ): String =
    val kinds          = operations.map(_.routeKind).toSet
    val schemaSet      = mutable.Set.empty[String]
    val stdlibByModule = mutable.Map.empty[String, mutable.Set[String]]

    for op <- operations do
      if op.hasRequestBody && op.requestBodyType.nonEmpty then schemaSet += op.requestBodyType
      if op.routeKind == "create" || op.routeKind == "read" || op.routeKind == "list" then
        schemaSet += entity.readSchemaName
      op.pathParamsWithTypes.foreach(p => mergeStdlibImport(stdlibByModule, p.domainType))

    val needsAnnotated =
      operations.exists(_.pathParamsWithTypes.exists(p => p.routerType != p.domainType))
    val needsAny = operations.exists(_.responseAnnotation.contains("Any"))
    val typingNames =
      (Option.when(needsAnnotated)("Annotated") ++ Option.when(needsAny)("Any")).toList
    val stdlib =
      (Option
        .when(typingNames.nonEmpty)(
          "typing" -> s"from typing import ${typingNames.mkString(", ")}"
        )
        .toList :::
        finalizeStdlibImports(stdlibByModule).map(i =>
          i.module -> s"from ${i.module} import ${i.names.mkString(", ")}"
        )).sortBy(_._1).map(_._2)

    val fastapiNames = List(
      Some("APIRouter"),
      Option.when(operations.nonEmpty)("Depends"),
      Option.when(kinds.contains("read") || kinds.contains("delete"))("HTTPException"),
      Option.when(needsAnnotated)("Path"),
      Option.when(kinds.contains("delete"))("Response")
    ).flatten
    val thirdParty =
      s"from fastapi import ${fastapiNames.mkString(", ")}" ::
        Option
          .when(kinds.contains("redirect"))("from fastapi.responses import RedirectResponse")
          .toList :::
        Option
          .when(operations.nonEmpty)("from sqlalchemy.ext.asyncio import AsyncSession")
          .toList

    val authDeps = operations.flatMap(_.authDependency).distinct.sorted
    val snake    = Naming.toSnakeCase(entity.entityName)
    val firstParty =
      Option.when(operations.nonEmpty)("from app.database import get_session").toList :::
        Option.when(kinds.contains("list"))("from app.pagination import Pagination").toList :::
        (if schemaSet.nonEmpty then
           List(
             schemaSet.toList.sorted
               .map(n => s"    $n,")
               .mkString(s"from app.schemas.$snake import (\n", "\n", "\n)")
           )
         else Nil) :::
        Option
          .when(authDeps.nonEmpty)(s"from app.security import ${authDeps.mkString(", ")}")
          .toList :::
        Option
          .when(operations.nonEmpty)(
            s"from app.services.$snake import ${entity.entityName}Service"
          )
          .toList

    List(stdlib, thirdParty, firstParty)
      .filter(_.nonEmpty)
      .map(_.mkString("\n"))
      .mkString("\n\n")

  private def collectServiceImports(
      entity: ProfiledEntity,
      operations: List[EnrichedOperation]
  ): ServiceTemplateImports =
    var needsSelect      = false
    var needsSaDelete    = false
    var needsModelImport = false
    val schemaSet        = mutable.Set.empty[String]

    for op <- operations do
      val routedThroughKernel = op.dafnyMethod.isDefined
      if !routedThroughKernel && (op.routeKind == "read" || op.routeKind == "list") then
        needsSelect = true; needsModelImport = true
      if !routedThroughKernel && op.routeKind == "delete" then
        needsSaDelete = true; needsModelImport = true
      if !routedThroughKernel && op.routeKind == "create" then needsModelImport = true
      if op.routeKind == "create" || op.routeKind == "read" || op.routeKind == "list" then
        schemaSet += entity.readSchemaName
      if op.hasRequestBody && op.requestBodyType.nonEmpty then schemaSet += op.requestBodyType

    val plainImports = List.newBuilder[String]
    val aliasImports = List.newBuilder[String]
    if needsSaDelete then plainImports += "CursorResult"
    if needsSaDelete then aliasImports += "delete as sa_delete"
    if needsSelect then plainImports += "select"

    val kernelOps        = operations.filter(_.dafnyMethod.isDefined)
    val needsDafnyKernel = kernelOps.nonEmpty
    val kernelText = kernelOps
      .flatMap(o => o.kernelResultExpr :: o.kernelCallArgs)
      .mkString(" ")
    val adapterImports = List(
      Option.when(kernelText.contains("from_dafny_str("))("from_dafny_str"),
      Option.when(kernelOps.exists(!_.kernelHasState))("make_state"),
      Option.when(kernelText.contains("to_dafny_str("))("to_dafny_str")
    ).flatten
    ServiceTemplateImports(
      sqlalchemyPlainImports = plainImports.result().sorted,
      sqlalchemyAliasImports = aliasImports.result().sorted,
      schemas = schemaSet.toList.sorted,
      needsModelImport = needsModelImport,
      needsDafnyKernel = needsDafnyKernel,
      needsStateBridge = kernelOps.exists(_.kernelHasState),
      needsAny = kernelOps.exists(_.serviceReturnAnnotation == "dict[str, Any]"),
      adapterImports = adapterImports,
      hasAppImports = needsDafnyKernel || needsModelImport || schemaSet.nonEmpty,
      needsTypingCast = needsSaDelete
    )
