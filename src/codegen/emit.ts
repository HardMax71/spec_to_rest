import { TemplateEngine } from "#codegen/engine.js";
import { pythonFastapiPostgresTemplates } from "#codegen/templates.js";
import { buildRenderContext } from "#codegen/types.js";
import { toSnakeCase, pluralize } from "#convention/naming.js";
import type {
  EndpointSpec,
  ParamSpec,
  TableSpec,
} from "#convention/types.js";
import type { TypeExpr } from "#ir/types.js";
import type {
  ProfiledEntity,
  ProfiledOperation,
  ProfiledService,
} from "#profile/types.js";

export interface EmittedFile {
  readonly path: string;
  readonly content: string;
}

type RouteKind =
  | "create"
  | "read"
  | "list"
  | "delete"
  | "other";

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
  readonly lookupColumn: string;
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

function classifyRouteKind(op: ProfiledOperation): RouteKind {
  const method = op.endpoint.method;
  const pathParamCount = op.endpoint.pathParams.length;
  const hasPathParam = pathParamCount > 0;
  const singlePathParam = pathParamCount === 1;
  if (op.kind === "create") return "create";
  if (op.kind === "read" && singlePathParam) return "read";
  if (op.kind === "read" && !hasPathParam) return "list";
  if (op.kind === "filtered_read" && !hasPathParam) return "list";
  if (op.kind === "delete" && singlePathParam) return "delete";
  if (method === "GET" && !hasPathParam) return "list";
  return "other";
}

function buildTypeLookup(profiled: ProfiledService): ReadonlyMap<string, string> {
  const map = new Map<string, string>();
  for (const [specType, mapping] of profiled.profile.typeMap.entries()) {
    map.set(specType, mapping.python);
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

  const routeKind = classifyRouteKind(op);
  const method = endpoint.method.toLowerCase();

  const pathParamCallArgs = pathParamsWithTypes.map((p) => p.name).join(", ");
  const hasRequestBody = routeKind === "create" || endpoint.bodyParams.length > 0;
  const usesUpdateSchema = op.kind === "partial_update" || op.kind === "replace";
  const requestBodyType = !hasRequestBody
    ? ""
    : usesUpdateSchema
      ? entity.updateSchemaName
      : entity.createSchemaName;

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

  const lookupColumn = pathParamsWithTypes.length > 0 ? pathParamsWithTypes[0].name : "id";

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
    lookupColumn,
  };
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

function collectSchemaStdlibImports(entity: ProfiledEntity): readonly StdlibImport[] {
  const stdlibByModule = new Map<string, Set<string>>();
  for (const field of entity.fields) {
    mergeStdlibImport(stdlibByModule, field.pythonType);
  }
  return finalizeStdlibImports(stdlibByModule);
}

interface RouterTemplateImports {
  readonly needsHttpException: boolean;
  readonly needsResponse: boolean;
  readonly schemas: readonly string[];
  readonly stdlibImports: readonly StdlibImport[];
}

function collectRouterImports(
  entity: ProfiledEntity,
  operations: readonly EnrichedOperation[],
): RouterTemplateImports {
  let needsHttpException = false;
  let needsResponse = false;
  const schemaSet = new Set<string>();
  const stdlibByModule = new Map<string, Set<string>>();

  for (const op of operations) {
    if (op.routeKind === "read") needsHttpException = true;
    if (op.routeKind === "delete") {
      needsHttpException = true;
      needsResponse = true;
    }
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
    schemas: [...schemaSet].sort(),
    stdlibImports: finalizeStdlibImports(stdlibByModule),
  };
}

interface ServiceTemplateImports {
  readonly sqlalchemyCoreImports: readonly string[];
  readonly schemas: readonly string[];
}

function collectServiceImports(
  entity: ProfiledEntity,
  operations: readonly EnrichedOperation[],
): ServiceTemplateImports {
  let needsSelect = false;
  let needsSaDelete = false;
  const schemaSet = new Set<string>();

  for (const op of operations) {
    if (op.routeKind === "read" || op.routeKind === "list") needsSelect = true;
    if (op.routeKind === "delete") needsSaDelete = true;
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
  };
}

function findTable(
  tables: readonly TableSpec[],
  entityName: string,
): TableSpec | null {
  return tables.find((t) => t.entityName === entityName) ?? null;
}

export function emitProject(profiled: ProfiledService): EmittedFile[] {
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
    path: "app/models/base.py",
    content: engine.render(templates.modelBase, ctx),
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
      .map((op) => enrichOperation(op, entity, typeLookup));

    const {
      sqlalchemyImports,
      postgresImports,
      stdlibImports,
    } = collectEntityImports(entity);
    const schemaStdlib = collectSchemaStdlibImports(entity);
    const routerImports = collectRouterImports(entity, entityOperations);
    const serviceImports = collectServiceImports(entity, entityOperations);

    const entitySnake = toSnakeCase(entity.entityName);
    const routerSnake = toSnakeCase(pluralize(entity.entityName));

    const modelCtx = {
      ...ctx,
      entity,
      table,
      entityOperations,
      sqlalchemyImports,
      postgresImports,
      stdlibImports,
    };

    const schemaCtx = {
      ...ctx,
      entity,
      table,
      entityOperations,
      stdlibImports: schemaStdlib,
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

  return files;
}
