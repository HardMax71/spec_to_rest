import {
  extractFieldConstraints,
  type JsonSchemaConstraints,
} from "#codegen/openapi/constraints.js";
import type { OpenApiSchemaType, SchemaObject } from "#codegen/openapi/types.js";
import type { EnumDecl, Expr, TypeAliasDecl, TypeExpr } from "#ir/types.js";

interface FieldToSchemaInput {
  readonly typeExpr: TypeExpr;
  readonly constraint?: Expr | null;
  readonly aliasMap: ReadonlyMap<string, TypeAliasDecl>;
  readonly enumMap: ReadonlyMap<string, EnumDecl>;
  readonly entityNames: ReadonlySet<string>;
}

export interface FieldSchema {
  readonly schema: SchemaObject;
  readonly nullable: boolean;
}

export function fieldToSchema(input: FieldToSchemaInput): FieldSchema {
  const nullable = input.typeExpr.kind === "OptionType";
  const effectiveType = nullable
    ? (input.typeExpr as { readonly innerType: TypeExpr }).innerType
    : input.typeExpr;
  const constraints = extractFieldConstraints(
    effectiveType,
    input.constraint ?? null,
    input.aliasMap,
    input.enumMap,
  );
  return { schema: typeExprToSchema(effectiveType, constraints, input), nullable };
}

export function makeNullable(schema: SchemaObject): SchemaObject {
  if (schema.$ref !== undefined) {
    return { anyOf: [schema, { type: "null" }] };
  }
  const { type } = schema;
  if (type === undefined) {
    return { anyOf: [schema, { type: "null" }] };
  }
  const current: readonly OpenApiSchemaType[] = Array.isArray(type) ? type : [type];
  if (current.includes("null")) return schema;
  return { ...schema, type: [...current, "null"] };
}

function typeExprToSchema(
  typeExpr: TypeExpr,
  constraints: JsonSchemaConstraints,
  input: FieldToSchemaInput,
): SchemaObject {
  switch (typeExpr.kind) {
    case "NamedType":
      return namedTypeSchema(typeExpr.name, constraints, input);

    case "SetType":
    case "SeqType": {
      const inner = fieldToSchema({
        typeExpr: typeExpr.elementType,
        aliasMap: input.aliasMap,
        enumMap: input.enumMap,
        entityNames: input.entityNames,
      });
      return { type: "array", items: inner.schema, ...arrayBoundsFrom(constraints) };
    }

    case "MapType": {
      const value = fieldToSchema({
        typeExpr: typeExpr.valueType,
        aliasMap: input.aliasMap,
        enumMap: input.enumMap,
        entityNames: input.entityNames,
      });
      return { type: "object", additionalProperties: value.schema };
    }

    case "OptionType":
      return typeExprToSchema(typeExpr.innerType, constraints, input);

    case "RelationType":
      return { type: "integer" };
  }
}

function arrayBoundsFrom(constraints: JsonSchemaConstraints): Pick<SchemaObject, "minItems" | "maxItems"> {
  const out: { minItems?: number; maxItems?: number } = {};
  if (constraints.minLength !== undefined) out.minItems = constraints.minLength;
  if (constraints.maxLength !== undefined) out.maxItems = constraints.maxLength;
  return out;
}

function namedTypeSchema(
  name: string,
  constraints: JsonSchemaConstraints,
  input: FieldToSchemaInput,
): SchemaObject {
  const primitive = PRIMITIVE_SCHEMAS.get(name);
  if (primitive !== undefined) {
    return { ...primitive, ...constraints };
  }

  const enumDecl = input.enumMap.get(name);
  if (enumDecl !== undefined) {
    return { type: "string", enum: [...enumDecl.values] };
  }

  if (input.entityNames.has(name)) {
    return { $ref: `#/components/schemas/${name}Read` };
  }

  const alias = input.aliasMap.get(name);
  if (alias !== undefined) {
    return typeExprToSchema(alias.typeExpr, constraints, input);
  }

  return { type: "string", ...constraints };
}

const PRIMITIVE_SCHEMAS: ReadonlyMap<string, SchemaObject> = new Map([
  ["String", { type: "string" }],
  ["Int", { type: "integer" }],
  ["Float", { type: "number" }],
  ["Bool", { type: "boolean" }],
  ["Boolean", { type: "boolean" }],
  ["DateTime", { type: "string", format: "date-time" }],
  ["Date", { type: "string", format: "date" }],
  ["UUID", { type: "string", format: "uuid" }],
  ["Decimal", { type: "string", format: "decimal" }],
  ["Bytes", { type: "string", format: "byte" }],
  ["Money", { type: "integer" }],
]);
