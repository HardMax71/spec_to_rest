import { fieldToSchema } from "#codegen/openapi/schema.js";
import type { ComponentsObject, SchemaObject } from "#codegen/openapi/types.js";
import type {
  EntityDecl,
  EnumDecl,
  FieldDecl,
  TypeAliasDecl,
  TypeExpr,
} from "#ir/types.js";
import type { ProfiledEntity, ProfiledService } from "#profile/types.js";

export interface BuildContext {
  readonly aliasMap: ReadonlyMap<string, TypeAliasDecl>;
  readonly enumMap: ReadonlyMap<string, EnumDecl>;
  readonly entityNames: ReadonlySet<string>;
  readonly entityDecls: ReadonlyMap<string, EntityDecl>;
}

export function buildComponents(
  profiled: ProfiledService,
  ctx: BuildContext,
): ComponentsObject {
  const schemas: Record<string, SchemaObject> = {
    ErrorResponse: errorResponseSchema(),
  };

  for (const entity of profiled.entities) {
    const entityDecl = ctx.entityDecls.get(entity.entityName);
    if (entityDecl === undefined) continue;

    const decoratedFields = decorateFields(entityDecl.fields, ctx);
    schemas[entity.createSchemaName] = createSchemaObject(decoratedFields, entity);
    schemas[entity.readSchemaName] = readSchemaObject(decoratedFields, entity);
    schemas[entity.updateSchemaName] = updateSchemaObject(decoratedFields, entity);
  }

  return { schemas };
}

interface DecoratedField {
  readonly name: string;
  readonly schema: SchemaObject;
  readonly nullable: boolean;
}

function decorateFields(
  fields: readonly FieldDecl[],
  ctx: BuildContext,
): readonly DecoratedField[] {
  return fields.map((field) => {
    const { schema, nullable } = fieldToSchema({
      typeExpr: field.typeExpr,
      constraint: field.constraint,
      aliasMap: ctx.aliasMap,
      enumMap: ctx.enumMap,
      entityNames: ctx.entityNames,
    });
    const columnName = toColumnName(field.name, field.typeExpr, ctx.entityNames);
    return { name: columnName, schema, nullable };
  });
}

function toColumnName(
  fieldName: string,
  typeExpr: TypeExpr,
  entityNames: ReadonlySet<string>,
): string {
  const effectiveType =
    typeExpr.kind === "OptionType" ? typeExpr.innerType : typeExpr;
  if (effectiveType.kind === "NamedType" && entityNames.has(effectiveType.name)) {
    return `${fieldName}_id`;
  }
  return fieldName;
}

function nonIdFields(fields: readonly DecoratedField[]): readonly DecoratedField[] {
  return fields.filter((f) => f.name !== "id");
}

function createSchemaObject(
  fields: readonly DecoratedField[],
  entity: ProfiledEntity,
): SchemaObject {
  const fs = nonIdFields(fields);
  return {
    type: "object",
    description: `Create payload for ${entity.entityName}`,
    required: fs.filter((f) => !f.nullable).map((f) => f.name),
    properties: Object.fromEntries(fs.map((f) => [f.name, f.schema])),
  };
}

function readSchemaObject(
  fields: readonly DecoratedField[],
  entity: ProfiledEntity,
): SchemaObject {
  const fs = nonIdFields(fields);
  const props: Record<string, SchemaObject> = { id: { type: "integer" } };
  for (const f of fs) props[f.name] = f.schema;
  return {
    type: "object",
    description: `Read view for ${entity.entityName}`,
    required: ["id", ...fs.filter((f) => !f.nullable).map((f) => f.name)],
    properties: props,
  };
}

function updateSchemaObject(
  fields: readonly DecoratedField[],
  entity: ProfiledEntity,
): SchemaObject {
  const fs = nonIdFields(fields);
  return {
    type: "object",
    description: `Update payload for ${entity.entityName}`,
    properties: Object.fromEntries(
      fs.map((f) => [f.name, { ...f.schema, nullable: true }]),
    ),
  };
}

function errorResponseSchema(): SchemaObject {
  return {
    type: "object",
    description: "Standard error response body",
    required: ["detail"],
    properties: {
      detail: { type: "string", description: "Human-readable error description" },
    },
  };
}
