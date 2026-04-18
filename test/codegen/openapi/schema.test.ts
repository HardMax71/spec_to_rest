import { describe, expect, it } from "vitest";
import { fieldToSchema, makeNullable } from "#codegen/openapi/schema.js";
import type { EnumDecl, TypeAliasDecl, TypeExpr } from "#ir/types.js";

const emptyAlias: ReadonlyMap<string, TypeAliasDecl> = new Map();
const emptyEnum: ReadonlyMap<string, EnumDecl> = new Map();
const emptyEntities: ReadonlySet<string> = new Set();

function named(name: string): TypeExpr {
  return { kind: "NamedType", name };
}

describe("fieldToSchema — nullability propagation into collections", () => {
  it("Set[Option[String]] marks the items schema as nullable via 3.1 type union", () => {
    const typeExpr: TypeExpr = {
      kind: "SetType",
      elementType: { kind: "OptionType", innerType: named("String") },
    };
    const { schema, nullable } = fieldToSchema({
      typeExpr,
      aliasMap: emptyAlias,
      enumMap: emptyEnum,
      entityNames: emptyEntities,
    });
    expect(nullable).toBe(false);
    expect(schema.type).toBe("array");
    expect(schema.items?.type).toEqual(["string", "null"]);
  });

  it("Seq[Option[Int]] preserves null in items", () => {
    const typeExpr: TypeExpr = {
      kind: "SeqType",
      elementType: { kind: "OptionType", innerType: named("Int") },
    };
    const { schema } = fieldToSchema({
      typeExpr,
      aliasMap: emptyAlias,
      enumMap: emptyEnum,
      entityNames: emptyEntities,
    });
    expect(schema.items?.type).toEqual(["integer", "null"]);
  });

  it("Map[String, Option[Int]] marks additionalProperties as nullable", () => {
    const typeExpr: TypeExpr = {
      kind: "MapType",
      keyType: named("String"),
      valueType: { kind: "OptionType", innerType: named("Int") },
    };
    const { schema } = fieldToSchema({
      typeExpr,
      aliasMap: emptyAlias,
      enumMap: emptyEnum,
      entityNames: emptyEntities,
    });
    expect(schema.type).toBe("object");
    const addl = schema.additionalProperties;
    expect(typeof addl === "object" && addl !== null).toBe(true);
    if (typeof addl === "object" && addl !== null) {
      expect(addl.type).toEqual(["integer", "null"]);
    }
  });
});

describe("makeNullable", () => {
  it("widens a scalar type to a union with null", () => {
    expect(makeNullable({ type: "string" }).type).toEqual(["string", "null"]);
  });

  it("keeps extra keywords (pattern, format, enum) alongside the widened type", () => {
    const out = makeNullable({ type: "string", pattern: "^[a-z]+$" });
    expect(out.type).toEqual(["string", "null"]);
    expect(out.pattern).toBe("^[a-z]+$");
  });

  it("wraps a $ref in anyOf instead of mutating it (3.1 forbids $ref siblings)", () => {
    const out = makeNullable({ $ref: "#/components/schemas/Foo" });
    expect(out.$ref).toBeUndefined();
    expect(out.anyOf?.length).toBe(2);
    expect(out.anyOf?.[0].$ref).toBe("#/components/schemas/Foo");
    expect(out.anyOf?.[1].type).toBe("null");
  });

  it("is idempotent — already-null-unioned schemas are left alone", () => {
    const input = { type: ["string", "null"] as const };
    const out = makeNullable(input);
    expect(out.type).toEqual(["string", "null"]);
  });
});
