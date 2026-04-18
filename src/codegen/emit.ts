import { buildAlembicMigration } from "#codegen/alembic/migration.js";
import { TemplateEngine } from "#codegen/engine.js";
import { buildOpenApiDocument } from "#codegen/openapi/build.js";
import { serializeOpenApi } from "#codegen/openapi/serialize.js";
import { classifyRouteKind, type RouteKind } from "#codegen/route-kind.js";
import { pythonFastapiPostgresTemplates } from "#codegen/templates.js";
import { buildRenderContext } from "#codegen/types.js";
import { toSnakeCase, pluralize } from "#convention/naming.js";
import type {
  EndpointSpec,
  ParamSpec,
  TableSpec,
} from "#convention/types.js";
import type { TypeAliasDecl, TypeExpr } from "#ir/types.js";
import type {
  ProfiledEntity,
  ProfiledField,
  ProfiledOperation,
  ProfiledService,
} from "#profile/types.js";

export interface EmittedFile {
  readonly path: string;
  readonly content: string;
}

export interface EmitOptions {
  readonly createdDate?: string;
  readonly revision?: string;
}

interface EnrichedPathParam {
  readonly name: string;
  readonly pythonType: string;
}

interface EnrichedOperation {
  readonly operationName: string;
  readonly handlerName: string;
  readonly kind: string;
  readonly method: string;
  readonly path: string;
  readonly successStatus: number;
  readonly pathParamsWithTypes: readonly EnrichedPathParam[];
  readonly hasRequestBody: boolean;
  readonly requestBodyType: string;
  readonly responseAnnotation: string;
  readonly serviceCallArgs: string;
  readonly routeKind: RouteKind;
  readonly pathParamSignature: string;
  readonly serviceSignatureExtraArgs: string;
  readonly serviceReturnAnnotation: string;
  readonly modelLookupColumn: string;
  readonly pathParamName: string;
  readonly customRequestSchema: CustomRequestSchema | null;
}

interface CustomRequestSchema {
  readonly schemaName: string;
  readonly fields: readonly ProfiledField[];
}

interface StdlibImport {
  readonly module: string;
  readonly names: readonly string[];
}

const STDLIB_TYPE_SOURCES: ReadonlyMap<string, StdlibImport> = new Map([
  ["datetime", { module: "datetime", names: ["datetime"] }],
  ["date", { module: "datetime", names: ["date"] }],
  ["Decimal", { module: "decimal", names: ["Decimal"] }],
  ["UUID", { module: "uuid", names: ["UUID"] }],
]);

const POSTGRES_DIALECT_TYPES: ReadonlySet<string> = new Set(["JSONB"]);

function pythonTypeForParam(typeExpr: TypeExpr, typeMap: ReadonlyMap<string, string>): string {
  if (typeExpr.kind === "NamedType") {
    const mapped = typeMap.get(typeExpr.name);
    if (mapped !== undefined) return mapped;
    return "str";
  }
  if (typeExpr.kind === "OptionType") {
    return `${pythonTypeForParam(typeExpr.innerType, typeMap)} | None`;
  }
  return "str";
}

function resolveAliasToPython(
  typeExpr: TypeExpr,
  base: ReadonlyMap<string, string>,
  aliasesByName: ReadonlyMap<string, TypeAliasDecl>,
  visited: Set<string>,
): string | null {
  if (typeExpr.kind !== "NamedType") return null;
  const direct = base.get(typeExpr.name);
  if (direct !== undefined) return direct;
  if (visited.has(typeExpr.name)) return null;
  visited.add(typeExpr.name);
  const alias = aliasesByName.get(typeExpr.name);
  if (alias === undefined) return null;
  return resolveAliasToPython(alias.typeExpr, base, aliasesByName, visited);
}

function buildTypeLookup(profiled: ProfiledService): ReadonlyMap<string, string> {
  const map = new Map<string, string>();
  for (const [specType, mapping] of profiled.profile.typeMap.entries()) {
    map.set(specType, mapping.python);
  }
  const aliasesByName = new Map(profiled.ir.typeAliases.map((a) => [a.name, a]));
  for (const alias of profiled.ir.typeAliases) {
    const resolved = resolveAliasToPython(alias.typeExpr, map, aliasesByName, new Set());
    if (resolved !== null) map.set(alias.name, resolved);
  }
  return map;
}

function paramPythonType(param: ParamSpec, typeLookup: ReadonlyMap<string, string>): string {
  return pythonTypeForParam(param.typeExpr, typeLookup);
}

function enrichOperation(
  op: ProfiledOperation,
  entity: ProfiledEntity,
  typeLookup: ReadonlyMap<string, string>,
): EnrichedOperation {
  const endpoint: EndpointSpec = op.endpoint;
  const pathParamsWithTypes: EnrichedPathParam[] = endpoint.pathParams.map((p) => ({
    name: p.name,
    pythonType: paramPythonType(p, typeLookup),
  }));

  let routeKind = classifyRouteKind(op);
  const method = endpoint.method.toLowerCase();

  const pathParamCallArgs = pathParamsWithTypes.map((p) => p.name).join(", ");
  const hasRequestBody = routeKind === "create" || endpoint.bodyParams.length > 0;

  const entityNonIdColumnNames = new Set(
    entity.fields.filter((f) => f.columnName !== "id").map((f) => f.columnName),
  );
  const bodyParamNames = endpoint.bodyParams.map((p) => p.name);
  const matchesEntityCreateShape =
    routeKind === "create" &&
    bodyParamNames.length === entityNonIdColumnNames.size &&
    bodyParamNames.every((n) => entityNonIdColumnNames.has(n));

  let customRequestSchema: CustomRequestSchema | null = null;
  let requestBodyType = "";
  if (hasRequestBody) {
    if (routeKind === "create" && matchesEntityCreateShape) {
      requestBodyType = entity.createSchemaName;
    } else {
      requestBodyType = `${op.operationName}Request`;
      const requestBodyByName = new Map(op.requestBodyFields.map((f) => [f.fieldName, f]));
      const pathParamNames = new Set(endpoint.pathParams.map((p) => p.name));
      const fields = op.requestBodyFields.filter(
        (f) => !pathParamNames.has(f.fieldName) && requestBodyByName.has(f.fieldName),
      );
      customRequestSchema = { schemaName: requestBodyType, fields };
    }
  }

  if (routeKind === "create" && !matchesEntityCreateShape) {
    routeKind = "other";
  }

  let responseAnnotation: string;
  let serviceCallArgs: string;
  let pathParamSignature: string;
  let serviceSignatureExtraArgs: string;
  let serviceReturnAnnotation: string;

  switch (routeKind) {
    case "create":
      responseAnnotation = entity.readSchemaName;
      serviceCallArgs = hasRequestBody ? "body" : "";
      pathParamSignature = "";
      serviceSignatureExtraArgs = "";
      serviceReturnAnnotation = entity.readSchemaName;
      break;
    case "read":
      responseAnnotation = entity.readSchemaName;
      serviceCallArgs = pathParamCallArgs;
      pathParamSignature = pathParamsWithTypes
        .map((p) => `${p.name}: ${p.pythonType}`)
        .join(", ");
      serviceSignatureExtraArgs = pathParamSignature;
      serviceReturnAnnotation = `${entity.readSchemaName} | None`;
      break;
    case "list":
      responseAnnotation = `list[${entity.readSchemaName}]`;
      serviceCallArgs = "";
      pathParamSignature = "";
      serviceSignatureExtraArgs = "";
      serviceReturnAnnotation = `list[${entity.readSchemaName}]`;
      break;
    case "delete":
      responseAnnotation = "Response";
      serviceCallArgs = pathParamCallArgs;
      pathParamSignature = pathParamsWithTypes
        .map((p) => `${p.name}: ${p.pythonType}`)
        .join(", ");
      serviceSignatureExtraArgs = pathParamSignature;
      serviceReturnAnnotation = "bool";
      break;
    case "redirect":
      responseAnnotation = "RedirectResponse";
      serviceCallArgs = pathParamCallArgs;
      pathParamSignature = pathParamsWithTypes
        .map((p) => `${p.name}: ${p.pythonType}`)
        .join(", ");
      serviceSignatureExtraArgs = pathParamSignature;
      serviceReturnAnnotation = "str";
      break;
    default: {
      const args = [
        ...pathParamsWithTypes.map((p) => `${p.name}: ${p.pythonType}`),
        ...(hasRequestBody ? [`body: ${requestBodyType}`] : []),
      ];
      responseAnnotation = "None";
      serviceCallArgs = [
        ...pathParamsWithTypes.map((p) => p.name),
        ...(hasRequestBody ? ["body"] : []),
      ].join(", ");
      pathParamSignature = "";
      serviceSignatureExtraArgs = args.join(", ");
      serviceReturnAnnotation = "None";
      break;
    }
  }

  const pathParamName = pathParamsWithTypes.length > 0 ? pathParamsWithTypes[0].name : "id";
  const modelLookupColumn = resolveModelLookupColumn(entity, pathParamName);

  return {
    operationName: op.operationName,
    handlerName: op.handlerName,
    kind: op.kind,
    method,
    path: endpoint.path,
    successStatus: endpoint.successStatus,
    pathParamsWithTypes,
    hasRequestBody,
    requestBodyType,
    responseAnnotation,
    serviceCallArgs,
    routeKind,
    pathParamSignature,
    serviceSignatureExtraArgs,
    serviceReturnAnnotation,
    modelLookupColumn,
    pathParamName,
    customRequestSchema,
  };
}

function resolveModelLookupColumn(entity: ProfiledEntity, pathParamName: string): string {
  if (entity.fields.some((f) => f.columnName === pathParamName)) return pathParamName;
  const entitySnake = toSnakeCase(entity.entityName);
  if (pathParamName === `${entitySnake}_id`) return "id";
  return "id";
}

const SENSITIVE_EXACT_NAMES: ReadonlySet<string> = new Set([
  "password",
  "password_hash",
  "secret",
  "token",
  "api_key",
]);

const SENSITIVE_SUFFIXES: readonly string[] = [
  "_hash",
  "_secret",
  "_password",
  "_api_key",
  "_token",
];

function isSensitiveFieldName(name: string): boolean {
  if (SENSITIVE_EXACT_NAMES.has(name)) return true;
  return SENSITIVE_SUFFIXES.some((s) => name.endsWith(s));
}

function byPathSpecificity(a: EnrichedOperation, b: EnrichedOperation): number {
  const aParams = (a.path.match(/\{/g) ?? []).length;
  const bParams = (b.path.match(/\{/g) ?? []).length;
  if (aParams !== bParams) return aParams - bParams;
  return 0;
}

function mergeStdlibImport(
  byModule: Map<string, Set<string>>,
  pythonType: string,
): void {
  const key = pythonType.replace(/\s*\|\s*None$/, "");
  const stdlib = STDLIB_TYPE_SOURCES.get(key);
  if (stdlib === undefined) return;
  const existing = byModule.get(stdlib.module) ?? new Set<string>();
  for (const n of stdlib.names) existing.add(n);
  byModule.set(stdlib.module, existing);
}

function finalizeStdlibImports(byModule: Map<string, Set<string>>): readonly StdlibImport[] {
  const result: StdlibImport[] = [];
  for (const [module, names] of byModule) {
    result.push({ module, names: [...names].sort() });
  }
  result.sort((a, b) => a.module.localeCompare(b.module));
  return result;
}

function collectEntityImports(entity: ProfiledEntity): {
  sqlalchemyImports: readonly string[];
  postgresImports: readonly string[];
  stdlibImports: readonly StdlibImport[];
} {
  const sqlSet = new Set<string>();
  const pgSet = new Set<string>();
  const stdlibByModule = new Map<string, Set<string>>();

  for (const field of entity.fields) {
    const colType = field.sqlalchemyColumnType;
    if (POSTGRES_DIALECT_TYPES.has(colType)) {
      pgSet.add(colType);
    } else {
      sqlSet.add(colType);
    }
    mergeStdlibImport(stdlibByModule, field.pythonType);
  }

  return {
    sqlalchemyImports: [...sqlSet].sort(),
    postgresImports: [...pgSet].sort(),
    stdlibImports: finalizeStdlibImports(stdlibByModule),
  };
}

function collectSchemaStdlibImports(
  entity: ProfiledEntity,
  customRequestSchemas: readonly CustomRequestSchema[],
): readonly StdlibImport[] {
  const stdlibByModule = new Map<string, Set<string>>();
  for (const field of entity.fields) {
    mergeStdlibImport(stdlibByModule, field.pythonType);
  }
  for (const schema of customRequestSchemas) {
    for (const field of schema.fields) {
      mergeStdlibImport(stdlibByModule, field.pythonType);
    }
  }
  return finalizeStdlibImports(stdlibByModule);
}

interface RouterTemplateImports {
  readonly needsHttpException: boolean;
  readonly needsResponse: boolean;
  readonly needsRedirectResponse: boolean;
  readonly schemas: readonly string[];
  readonly stdlibImports: readonly StdlibImport[];
}

function collectRouterImports(
  entity: ProfiledEntity,
  operations: readonly EnrichedOperation[],
): RouterTemplateImports {
  let needsHttpException = false;
  let needsResponse = false;
  let needsRedirectResponse = false;
  const schemaSet = new Set<string>();
  const stdlibByModule = new Map<string, Set<string>>();

  for (const op of operations) {
    if (op.routeKind === "read") needsHttpException = true;
    if (op.routeKind === "delete") {
      needsHttpException = true;
      needsResponse = true;
    }
    if (op.routeKind === "redirect") needsRedirectResponse = true;
    if (op.hasRequestBody && op.requestBodyType) schemaSet.add(op.requestBodyType);
    if (op.routeKind === "create" || op.routeKind === "read" || op.routeKind === "list") {
      schemaSet.add(entity.readSchemaName);
    }
    for (const p of op.pathParamsWithTypes) {
      mergeStdlibImport(stdlibByModule, p.pythonType);
    }
  }

  return {
    needsHttpException,
    needsResponse,
    needsRedirectResponse,
    schemas: [...schemaSet].sort(),
    stdlibImports: finalizeStdlibImports(stdlibByModule),
  };
}

interface ServiceTemplateImports {
  readonly sqlalchemyCoreImports: readonly string[];
  readonly schemas: readonly string[];
  readonly needsModelImport: boolean;
}

function collectServiceImports(
  entity: ProfiledEntity,
  operations: readonly EnrichedOperation[],
): ServiceTemplateImports {
  let needsSelect = false;
  let needsSaDelete = false;
  let needsModelImport = false;
  const schemaSet = new Set<string>();

  for (const op of operations) {
    if (op.routeKind === "read" || op.routeKind === "list") {
      needsSelect = true;
      needsModelImport = true;
    }
    if (op.routeKind === "delete") {
      needsSaDelete = true;
      needsModelImport = true;
    }
    if (op.routeKind === "create") needsModelImport = true;
    if (op.routeKind === "create" || op.routeKind === "read" || op.routeKind === "list") {
      schemaSet.add(entity.readSchemaName);
    }
    if (op.hasRequestBody && op.requestBodyType) schemaSet.add(op.requestBodyType);
  }

  const coreImports: string[] = [];
  if (needsSaDelete) coreImports.push("delete as sa_delete");
  if (needsSelect) coreImports.push("select");

  return {
    sqlalchemyCoreImports: coreImports,
    schemas: [...schemaSet].sort(),
    needsModelImport,
  };
}

function findTable(
  tables: readonly TableSpec[],
  entityName: string,
): TableSpec | null {
  return tables.find((t) => t.entityName === entityName) ?? null;
}

export function emitProject(
  profiled: ProfiledService,
  opts: EmitOptions = {},
): EmittedFile[] {
  const ctx = buildRenderContext(profiled);
  const engine = new TemplateEngine();
  const typeLookup = buildTypeLookup(profiled);
  const templates = pythonFastapiPostgresTemplates;
  const files: EmittedFile[] = [];

  files.push({
    path: "app/__init__.py",
    content: "",
  });

  files.push({
    path: "app/main.py",
    content: engine.render(templates.main, ctx),
  });
  files.push({
    path: "app/config.py",
    content: engine.render(templates.config, ctx),
  });
  files.push({
    path: "app/database.py",
    content: engine.render(templates.database, ctx),
  });

  files.push({
    path: "app/db/__init__.py",
    content: "",
  });
  files.push({
    path: "app/db/base.py",
    content: engine.render(templates.dbBase, ctx),
  });
  files.push({
    path: "app/models/__init__.py",
    content: engine.render(templates.modelInit, ctx),
  });
  files.push({
    path: "app/schemas/__init__.py",
    content: engine.render(templates.schemaInit, ctx),
  });
  files.push({
    path: "app/routers/__init__.py",
    content: engine.render(templates.routerInit, ctx),
  });
  files.push({
    path: "app/services/__init__.py",
    content: engine.render(templates.serviceInit, ctx),
  });

  for (const entity of ctx.entities) {
    const table = findTable(ctx.schema.tables, entity.entityName);
    const entityOperations = ctx.operations
      .filter((op) => op.targetEntity === entity.entityName)
      .map((op) => enrichOperation(op, entity, typeLookup))
      .sort(byPathSpecificity);

    const {
      sqlalchemyImports,
      postgresImports,
      stdlibImports,
    } = collectEntityImports(entity);
    const routerImports = collectRouterImports(entity, entityOperations);
    const serviceImports = collectServiceImports(entity, entityOperations);

    const entitySnake = toSnakeCase(entity.entityName);
    const routerSnake = toSnakeCase(pluralize(entity.entityName));

    const nonIdFields = entity.fields.filter((f) => f.columnName !== "id");
    const readFields = nonIdFields.filter((f) => !isSensitiveFieldName(f.columnName));
    const customRequestSchemas = entityOperations
      .map((op) => op.customRequestSchema)
      .filter((s): s is CustomRequestSchema => s !== null);
    const schemaStdlibWithRequests = collectSchemaStdlibImports(entity, customRequestSchemas);

    const modelCtx = {
      ...ctx,
      entity,
      table,
      entityOperations,
      nonIdFields,
      sqlalchemyImports,
      postgresImports,
      stdlibImports,
    };

    const schemaCtx = {
      ...ctx,
      entity,
      table,
      entityOperations,
      nonIdFields,
      readFields,
      customRequestSchemas,
      stdlibImports: schemaStdlibWithRequests,
    };

    const routerCtx = {
      ...ctx,
      entity,
      table,
      entityOperations,
      routerImports,
    };

    const serviceCtx = {
      ...ctx,
      entity,
      table,
      entityOperations,
      serviceImports,
    };

    files.push({
      path: `app/models/${entitySnake}.py`,
      content: engine.render(templates.modelEntity, modelCtx),
    });
    files.push({
      path: `app/schemas/${entitySnake}.py`,
      content: engine.render(templates.schemaEntity, schemaCtx),
    });
    files.push({
      path: `app/routers/${routerSnake}.py`,
      content: engine.render(templates.routerEntity, routerCtx),
    });
    files.push({
      path: `app/services/${entitySnake}.py`,
      content: engine.render(templates.serviceEntity, serviceCtx),
    });
  }

  files.push({
    path: "openapi.yaml",
    content: serializeOpenApi(buildOpenApiDocument(profiled)),
  });

  const migration = buildAlembicMigration(profiled.schema, {
    createdDate: opts.createdDate,
    revision: opts.revision,
  });
  const alembicCtx = { ...ctx, migration };
  files.push({
    path: "alembic.ini",
    content: engine.render(templates.alembicIni, ctx),
  });
  files.push({
    path: "alembic/env.py",
    content: engine.render(templates.alembicEnv, ctx),
  });
  files.push({
    path: `alembic/versions/${migration.revision}_initial_schema.py`,
    content: engine.render(templates.alembicMigration, alembicCtx),
  });

  files.push({ path: "pyproject.toml", content: engine.render(templates.pyproject, ctx) });
  files.push({ path: "Dockerfile", content: engine.render(templates.dockerfile, ctx) });
  files.push({ path: "docker-compose.yml", content: engine.render(templates.dockerCompose, ctx) });
  files.push({ path: ".env.example", content: engine.render(templates.envExample, ctx) });
  files.push({ path: "Makefile", content: engine.render(templates.makefile, ctx) });
  files.push({ path: ".gitignore", content: engine.render(templates.gitignore, ctx) });
  files.push({ path: ".dockerignore", content: engine.render(templates.dockerignore, ctx) });
  files.push({ path: "README.md", content: engine.render(templates.readme, ctx) });
  files.push({
    path: ".github/workflows/ci.yml",
    content: engine.render(templates.ciWorkflow, ctx),
  });
  files.push({
    path: "tests/test_health.py",
    content: engine.render(templates.testHealth, ctx),
  });

  return files;
}
