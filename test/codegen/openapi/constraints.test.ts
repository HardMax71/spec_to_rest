import { describe, expect, it } from "vitest";
import { extractFieldConstraints } from "#codegen/openapi/constraints.js";
import type { Expr, TypeAliasDecl, TypeExpr } from "#ir/types.js";

const stringType: TypeExpr = { kind: "NamedType", name: "String" };
const intType: TypeExpr = { kind: "NamedType", name: "Int" };

function int(value: number): Expr {
  return { kind: "IntLit", value };
}

function valueRef(): Expr {
  return { kind: "Identifier", name: "value" };
}

function lenCall(): Expr {
  return {
    kind: "Call",
    callee: { kind: "Identifier", name: "len" },
    args: [valueRef()],
  };
}

function binOp(op: Expr extends { kind: "BinaryOp"; op: infer O } ? O : never, left: Expr, right: Expr): Expr {
  return { kind: "BinaryOp", op, left, right };
}

describe("extractFieldConstraints", () => {
  const emptyAlias = new Map<string, TypeAliasDecl>();
  const emptyEnum = new Map();

  it.each([
    [
      "len(value) >= 6",
      binOp(">=", lenCall(), int(6)),
      { minLength: 6 },
    ],
    [
      "len(value) <= 10",
      binOp("<=", lenCall(), int(10)),
      { maxLength: 10 },
    ],
    [
      "len(value) >= 6 and len(value) <= 10",
      binOp(
        "and",
        binOp(">=", lenCall(), int(6)),
        binOp("<=", lenCall(), int(10)),
      ),
      { minLength: 6, maxLength: 10 },
    ],
    [
      "len(value) = 64",
      binOp("=", lenCall(), int(64)),
      { minLength: 64, maxLength: 64 },
    ],
    [
      "value >= 0",
      binOp(">=", valueRef(), int(0)),
      { minimum: 0 },
    ],
    [
      "value > 0",
      binOp(">", valueRef(), int(0)),
      { exclusiveMinimum: 0 },
    ],
    [
      "value <= 100",
      binOp("<=", valueRef(), int(100)),
      { maximum: 100 },
    ],
  ])("extracts constraints from %s", (_label, expr, expected) => {
    const typeExpr = _label.startsWith("value") ? intType : stringType;
    const result = extractFieldConstraints(typeExpr, expr, emptyAlias, emptyEnum);
    expect(result).toEqual(expected);
  });

  it("extracts pattern from Matches expression", () => {
    const expr: Expr = {
      kind: "Matches",
      expr: valueRef(),
      pattern: "^[a-z]+$",
    };
    const result = extractFieldConstraints(stringType, expr, emptyAlias, emptyEnum);
    expect(result).toEqual({ pattern: "^[a-z]+$" });
  });

  it("resolves constraints through a type alias", () => {
    const shortCodeAlias: TypeAliasDecl = {
      kind: "TypeAlias",
      name: "ShortCode",
      typeExpr: stringType,
      constraint: binOp(
        "and",
        binOp(">=", lenCall(), int(6)),
        binOp("<=", lenCall(), int(10)),
      ),
    };
    const aliasMap = new Map([["ShortCode", shortCodeAlias]]);
    const result = extractFieldConstraints(
      { kind: "NamedType", name: "ShortCode" },
      null,
      aliasMap,
      emptyEnum,
    );
    expect(result).toEqual({ minLength: 6, maxLength: 10 });
  });

  it("Option[T] inherits constraints from the inner type", () => {
    const shortCodeAlias: TypeAliasDecl = {
      kind: "TypeAlias",
      name: "ShortCode",
      typeExpr: stringType,
      constraint: binOp(">=", lenCall(), int(6)),
    };
    const aliasMap = new Map([["ShortCode", shortCodeAlias]]);
    const option: TypeExpr = {
      kind: "OptionType",
      innerType: { kind: "NamedType", name: "ShortCode" },
    };
    const result = extractFieldConstraints(option, null, aliasMap, emptyEnum);
    expect(result).toEqual({ minLength: 6 });
  });

  it("ignores unknown predicates (e.g., isValidURI)", () => {
    const expr: Expr = {
      kind: "Call",
      callee: { kind: "Identifier", name: "isValidURI" },
      args: [valueRef()],
    };
    const result = extractFieldConstraints(stringType, expr, emptyAlias, emptyEnum);
    expect(result).toEqual({});
  });
});
