import { fieldToSchema, makeNullable } from "#codegen/openapi/schema.js";
import { isSensitiveFieldName } from "#codegen/sensitive-fields.js";
import type { ComponentsObject, SchemaObject } from "#codegen/openapi/types.js";
import type {
  EntityDecl,
  EnumDecl,
  TypeAliasDecl,
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

    const decoratedFields = decorateFields(entity, entityDecl, ctx);
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
  entity: ProfiledEntity,
  entityDecl: EntityDecl,
  ctx: BuildContext,
): readonly DecoratedField[] {
  return entity.fields.map((profiled, index) => {
    const decl = entityDecl.fields[index];
    const { schema, nullable } = fieldToSchema({
      typeExpr: decl.typeExpr,
      constraint: decl.constraint,
      aliasMap: ctx.aliasMap,
      enumMap: ctx.enumMap,
      entityNames: ctx.entityNames,
    });
    return { name: profiled.columnName, schema, nullable };
  });
}

function nonIdFields(fields: readonly DecoratedField[]): readonly DecoratedField[] {
  return fields.filter((f) => f.name !== "id");
}

function fieldProperty(f: DecoratedField): SchemaObject {
  return f.nullable ? makeNullable(f.schema) : f.schema;
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
    properties: Object.fromEntries(fs.map((f) => [f.name, fieldProperty(f)])),
  };
}

function readSchemaObject(
  fields: readonly DecoratedField[],
  entity: ProfiledEntity,
): SchemaObject {
  const fs = nonIdFields(fields).filter((f) => !isSensitiveFieldName(f.name));
  const props: Record<string, SchemaObject> = { id: { type: "integer" } };
  for (const f of fs) props[f.name] = fieldProperty(f);
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
      fs.map((f) => [f.name, makeNullable(f.schema)]),
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
