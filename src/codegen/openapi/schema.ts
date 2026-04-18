import {
  extractFieldConstraints,
  type JsonSchemaConstraints,
} from "#codegen/openapi/constraints.js";
import type { SchemaObject } from "#codegen/openapi/types.js";
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
  const nullable = isOptionType(input.typeExpr);
  const effectiveType = nullable
    ? (input.typeExpr as { innerType: TypeExpr }).innerType
    : input.typeExpr;
  const constraints = extractFieldConstraints(
    effectiveType,
    input.constraint ?? null,
    input.aliasMap,
    input.enumMap,
  );
  const schema = typeExprToSchema(effectiveType, constraints, input);
  return { schema: nullable ? { ...schema, nullable: true } : schema, nullable };
}

function isOptionType(t: TypeExpr): boolean {
  return t.kind === "OptionType";
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
      return { type: "array", items: inner.schema };
    }

    case "MapType":
      return { type: "object" };

    case "OptionType":
      return typeExprToSchema(typeExpr.innerType, constraints, input);

    case "RelationType":
      return { type: "integer" };
  }
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
