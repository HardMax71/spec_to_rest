import type { ServiceIR, OperationDecl, ParamDecl } from "#ir/types.js";
import type {
  ProfiledService,
  ProfiledEntity,
  ProfiledOperation,
  ProfiledField,
  DeploymentProfile,
} from "#profile/types.js";
import { getProfile } from "#profile/registry.js";
import { mapType, type TypeContext } from "#profile/type-map.js";
import { classifyOperations } from "#convention/classify.js";
import { deriveEndpoints } from "#convention/path.js";
import { deriveSchema } from "#convention/schema.js";
import { toSnakeCase, toTableName, toColumnName, pluralize } from "#convention/naming.js";
import { getConvention } from "#convention/path.js";

export function buildProfiledService(ir: ServiceIR, profileName: string): ProfiledService {
  const profile = getProfile(profileName);
  const classifications = classifyOperations(ir);
  const endpoints = deriveEndpoints(classifications, ir);
  const schema = deriveSchema(ir);

  const ctx: TypeContext = {
    entityNames: new Set(ir.entities.map((e) => e.name)),
    enumNames: new Set(ir.enums.map((e) => e.name)),
    aliasMap: new Map(ir.typeAliases.map((a) => [a.name, a.typeExpr])),
  };

  const classificationMap = new Map(classifications.map((c) => [c.operationName, c]));
  const endpointMap = new Map(endpoints.map((e) => [e.operationName, e]));
  const tableMap = new Map(schema.tables.map((t) => [t.entityName, t]));

  const entities = ir.entities.map((entity) => {
    const table = tableMap.get(entity.name);
    const tableNameOverride = getConvention(ir.conventions, entity.name, "db_table");
    const tableName = tableNameOverride || toTableName(entity.name);

    return profileEntity(entity.name, tableName, entity.fields, profile, ctx, table !== undefined);
  });

  const operations = ir.operations.map((op) => {
    const classification = classificationMap.get(op.name);
    const endpoint = endpointMap.get(op.name);
    if (!classification || !endpoint) {
      return {
        operationName: op.name,
        handlerName: toSnakeCase(op.name),
        endpoint: endpoint!,
        kind: classification?.kind ?? ("side_effect" as const),
        targetEntity: classification?.targetEntity ?? null,
        requestBodyFields: profileParams(op.inputs, profile, ctx),
        responseFields: profileParams(op.outputs, profile, ctx),
      };
    }

    return profileOperation(op, classification.kind, classification.targetEntity, endpoint, profile, ctx);
  });

  return { ir, profile, endpoints, schema, entities, operations };
}

function profileEntity(
  entityName: string,
  tableName: string,
  fields: readonly { readonly name: string; readonly typeExpr: import("#ir/types.js").TypeExpr }[],
  profile: DeploymentProfile,
  ctx: TypeContext,
  _hasTable: boolean,
): ProfiledEntity {
  const snakeName = toSnakeCase(entityName);
  const pluralSnake = toSnakeCase(pluralize(entityName));

  const profiledFields = fields.map((f) => profileField(f.name, f.typeExpr, profile, ctx));

  return {
    entityName,
    tableName,
    modelClassName: entityName,
    createSchemaName: `${entityName}Create`,
    readSchemaName: `${entityName}Read`,
    updateSchemaName: `${entityName}Update`,
    modelFileName: `${snakeName}.py`,
    schemaFileName: `${snakeName}.py`,
    routerFileName: `${pluralSnake}.py`,
    fields: profiledFields,
  };
}

function profileField(
  fieldName: string,
  typeExpr: import("#ir/types.js").TypeExpr,
  profile: DeploymentProfile,
  ctx: TypeContext,
): ProfiledField {
  const mapped = mapType(typeExpr, profile, ctx);
  const colName = toColumnName(fieldName);
  const nullable = typeExpr.kind === "OptionType";
  const sqlalchemyColumnType = resolveColumnType(typeExpr, profile, ctx);

  return {
    fieldName,
    columnName: colName,
    pythonType: mapped.python,
    pydanticType: mapped.pydantic,
    sqlalchemyType: mapped.sqlalchemy,
    sqlalchemyColumnType,
    nullable,
    hasDefault: false,
  };
}

function resolveColumnType(
  typeExpr: import("#ir/types.js").TypeExpr,
  profile: DeploymentProfile,
  ctx: TypeContext,
): string {
  switch (typeExpr.kind) {
    case "NamedType": {
      const mapping = profile.typeMap.get(typeExpr.name);
      if (mapping) return mapping.sqlalchemy;
      if (ctx.entityNames.has(typeExpr.name)) return "Integer";
      if (ctx.enumNames.has(typeExpr.name)) return "String";
      const alias = ctx.aliasMap.get(typeExpr.name);
      if (alias) return resolveColumnType(alias, profile, ctx);
      return "String";
    }
    case "OptionType":
      return resolveColumnType(typeExpr.innerType, profile, ctx);
    case "SetType":
    case "SeqType":
    case "MapType":
      return "JSONB";
    case "RelationType":
      return "Integer";
  }
}

function profileOperation(
  op: OperationDecl,
  kind: import("#convention/types.js").OperationKind,
  targetEntity: string | null,
  endpoint: import("#convention/types.js").EndpointSpec,
  profile: DeploymentProfile,
  ctx: TypeContext,
): ProfiledOperation {
  return {
    operationName: op.name,
    handlerName: toSnakeCase(op.name),
    endpoint,
    kind,
    targetEntity,
    requestBodyFields: profileParams(op.inputs, profile, ctx),
    responseFields: profileParams(op.outputs, profile, ctx),
  };
}

function profileParams(
  params: readonly ParamDecl[],
  profile: DeploymentProfile,
  ctx: TypeContext,
): readonly ProfiledField[] {
  return params.map((p) => profileField(p.name, p.typeExpr, profile, ctx));
}
