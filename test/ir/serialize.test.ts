import { describe, it, expect } from "vitest";
import type { ServiceIR, Expr, TypeExpr } from "../../src/ir/index.js";
import { serializeIR, deserializeIR } from "../../src/ir/index.js";

const id = (name: string): Expr => ({ kind: "Identifier", name });
const intLit = (value: number): Expr => ({ kind: "IntLit", value });
const strLit = (value: string): Expr => ({ kind: "StringLit", value });
const namedType = (name: string): TypeExpr => ({ kind: "NamedType", name });

function buildUrlShortenerIR(): ServiceIR {
  return {
    kind: "Service",
    name: "UrlShortener",
    imports: [],
    entities: [
      {
        kind: "Entity",
        name: "UrlMapping",
        extends_: null,
        fields: [
          { kind: "Field", name: "code", typeExpr: namedType("ShortCode"), constraint: null },
          { kind: "Field", name: "url", typeExpr: namedType("LongURL"), constraint: null },
          { kind: "Field", name: "created_at", typeExpr: namedType("DateTime"), constraint: null },
          {
            kind: "Field",
            name: "click_count",
            typeExpr: namedType("Int"),
            constraint: {
              kind: "BinaryOp",
              op: ">=",
              left: id("value"),
              right: intLit(0),
            },
            span: { startLine: 10, startCol: 4, endLine: 10, endCol: 40 },
          },
        ],
        invariants: [
          { kind: "Call", callee: id("isValidURI"), args: [id("url")] },
        ],
        span: { startLine: 5, startCol: 2, endLine: 15, endCol: 3 },
      },
    ],
    enums: [],
    typeAliases: [
      {
        kind: "TypeAlias",
        name: "ShortCode",
        typeExpr: namedType("String"),
        constraint: {
          kind: "BinaryOp",
          op: "and",
          left: {
            kind: "BinaryOp",
            op: ">=",
            left: { kind: "Call", callee: id("len"), args: [id("value")] },
            right: intLit(6),
          },
          right: {
            kind: "BinaryOp",
            op: "and",
            left: {
              kind: "BinaryOp",
              op: "<=",
              left: { kind: "Call", callee: id("len"), args: [id("value")] },
              right: intLit(10),
            },
            right: {
              kind: "Matches",
              expr: id("value"),
              pattern: "^[a-zA-Z0-9]+$",
            },
          },
        },
      },
      {
        kind: "TypeAlias",
        name: "LongURL",
        typeExpr: namedType("String"),
        constraint: {
          kind: "BinaryOp",
          op: "and",
          left: {
            kind: "BinaryOp",
            op: ">",
            left: { kind: "Call", callee: id("len"), args: [id("value")] },
            right: intLit(0),
          },
          right: { kind: "Call", callee: id("isValidURI"), args: [id("value")] },
        },
      },
    ],
    state: {
      kind: "State",
      fields: [
        {
          kind: "StateField",
          name: "store",
          typeExpr: {
            kind: "RelationType",
            fromType: namedType("ShortCode"),
            multiplicity: "lone",
            toType: namedType("LongURL"),
          },
        },
        {
          kind: "StateField",
          name: "metadata",
          typeExpr: {
            kind: "RelationType",
            fromType: namedType("ShortCode"),
            multiplicity: "lone",
            toType: namedType("UrlMapping"),
          },
        },
        {
          kind: "StateField",
          name: "base_url",
          typeExpr: namedType("BaseURL"),
        },
      ],
    },
    operations: [
      {
        kind: "Operation",
        name: "Shorten",
        inputs: [{ kind: "Param", name: "url", typeExpr: namedType("LongURL") }],
        outputs: [
          { kind: "Param", name: "code", typeExpr: namedType("ShortCode") },
          { kind: "Param", name: "short_url", typeExpr: namedType("String") },
        ],
        requires: [{ kind: "Call", callee: id("isValidURI"), args: [id("url")] }],
        ensures: [
          {
            kind: "BinaryOp",
            op: "not_in",
            left: id("code"),
            right: { kind: "Pre", expr: id("store") },
          },
          {
            kind: "BinaryOp",
            op: "=",
            left: { kind: "Prime", expr: id("store") },
            right: {
              kind: "BinaryOp",
              op: "+",
              left: { kind: "Pre", expr: id("store") },
              right: {
                kind: "MapLiteral",
                entries: [{ key: id("code"), value: id("url") }],
              },
            },
          },
        ],
      },
    ],
    transitions: [],
    invariants: [
      {
        kind: "Invariant",
        name: "allURLsValid",
        expr: {
          kind: "Quantifier",
          quantifier: "all",
          bindings: [{ variable: "c", domain: id("store"), bindingKind: "in" }],
          body: {
            kind: "Call",
            callee: id("isValidURI"),
            args: [{ kind: "Index", base: id("store"), index: id("c") }],
          },
        },
      },
    ],
    facts: [],
    functions: [],
    predicates: [],
    conventions: {
      kind: "Conventions",
      rules: [
        {
          kind: "ConventionRule",
          target: "Shorten",
          property: "http_method",
          qualifier: null,
          value: strLit("POST"),
        },
        {
          kind: "ConventionRule",
          target: "Shorten",
          property: "http_status_success",
          qualifier: null,
          value: intLit(201),
        },
        {
          kind: "ConventionRule",
          target: "Resolve",
          property: "http_header",
          qualifier: "Location",
          value: { kind: "FieldAccess", base: id("output"), field: "url" },
        },
      ],
    },
  };
}

describe("JSON round-trip", () => {
  it("round-trips a full URL shortener IR without loss", () => {
    const original = buildUrlShortenerIR();
    const json = serializeIR(original);
    const restored = deserializeIR(json);
    expect(restored).toEqual(original);
  });

  it("preserves spans on nodes that have them", () => {
    const original = buildUrlShortenerIR();
    const restored = deserializeIR(serializeIR(original));
    expect(restored.entities[0].span).toEqual({
      startLine: 5,
      startCol: 2,
      endLine: 15,
      endCol: 3,
    });
    expect(restored.entities[0].fields[3].span).toEqual({
      startLine: 10,
      startCol: 4,
      endLine: 10,
      endCol: 40,
    });
  });

  it("handles nodes without spans (undefined → absent in JSON)", () => {
    const original = buildUrlShortenerIR();
    const json = serializeIR(original);
    const parsed = JSON.parse(json);
    // Nodes without span should not have a span key in the JSON
    expect("span" in parsed.operations[0]).toBe(false);
    expect("span" in parsed.entities[0].fields[0]).toBe(false);
    // Restored object should not have span either
    const restored = deserializeIR(json);
    expect(restored.operations[0].span).toBeUndefined();
  });

  it("rejects invalid JSON that is not a ServiceIR", () => {
    expect(() => deserializeIR('{"kind":"Entity"}')).toThrow("Invalid ServiceIR");
    expect(() => deserializeIR('"hello"')).toThrow("Invalid ServiceIR");
    expect(() => deserializeIR("42")).toThrow("Invalid ServiceIR");
    expect(() => deserializeIR("null")).toThrow("Invalid ServiceIR");
  });

  it("preserves null values", () => {
    const original = buildUrlShortenerIR();
    const restored = deserializeIR(serializeIR(original));
    expect(restored.entities[0].extends_).toBeNull();
    expect(restored.entities[0].fields[0].constraint).toBeNull();
  });

  it("preserves empty arrays", () => {
    const original = buildUrlShortenerIR();
    const restored = deserializeIR(serializeIR(original));
    expect(restored.imports).toEqual([]);
    expect(restored.enums).toEqual([]);
    expect(restored.transitions).toEqual([]);
    expect(restored.facts).toEqual([]);
  });

  it("produces valid JSON string", () => {
    const original = buildUrlShortenerIR();
    const json = serializeIR(original);
    expect(() => JSON.parse(json)).not.toThrow();
  });

  it("round-trips nested expressions", () => {
    const original = buildUrlShortenerIR();
    const restored = deserializeIR(serializeIR(original));
    // Check the deeply nested ShortCode constraint
    const shortCode = restored.typeAliases[0];
    expect(shortCode.constraint!.kind).toBe("BinaryOp");
    if (shortCode.constraint!.kind === "BinaryOp") {
      expect(shortCode.constraint!.right.kind).toBe("BinaryOp");
    }
  });
});
