import type { EnumDecl, Expr, TypeAliasDecl, TypeExpr } from "#ir/types.js";

export interface JsonSchemaConstraints {
  readonly minLength?: number;
  readonly maxLength?: number;
  readonly minimum?: number;
  readonly maximum?: number;
  readonly exclusiveMinimum?: number;
  readonly exclusiveMaximum?: number;
  readonly pattern?: string;
  readonly enum?: readonly string[];
}

interface MutableConstraints {
  minLength?: number;
  maxLength?: number;
  minimum?: number;
  maximum?: number;
  exclusiveMinimum?: number;
  exclusiveMaximum?: number;
  pattern?: string;
  enum?: readonly string[];
}

export function extractFieldConstraints(
  typeExpr: TypeExpr,
  constraint: Expr | null,
  aliasMap: ReadonlyMap<string, TypeAliasDecl>,
  enumMap: ReadonlyMap<string, EnumDecl>,
): JsonSchemaConstraints {
  const out: MutableConstraints = {};
  collectFromType(typeExpr, aliasMap, enumMap, out);
  if (constraint !== null) {
    visitConstraint(constraint, out);
  }
  return out;
}

function collectFromType(
  typeExpr: TypeExpr,
  aliasMap: ReadonlyMap<string, TypeAliasDecl>,
  enumMap: ReadonlyMap<string, EnumDecl>,
  out: MutableConstraints,
): void {
  if (typeExpr.kind === "OptionType") {
    collectFromType(typeExpr.innerType, aliasMap, enumMap, out);
    return;
  }
  if (typeExpr.kind !== "NamedType") return;

  const enumDecl = enumMap.get(typeExpr.name);
  if (enumDecl !== undefined) {
    out.enum = [...enumDecl.values];
    return;
  }

  const alias = aliasMap.get(typeExpr.name);
  if (alias === undefined) return;

  collectFromType(alias.typeExpr, aliasMap, enumMap, out);
  if (alias.constraint !== null) {
    visitConstraint(alias.constraint, out);
  }
}

function visitConstraint(expr: Expr, out: MutableConstraints): void {
  if (expr.kind === "BinaryOp" && expr.op === "and") {
    visitConstraint(expr.left, out);
    visitConstraint(expr.right, out);
    return;
  }

  if (expr.kind === "Matches" && isValueRef(expr.expr)) {
    out.pattern = expr.pattern;
    return;
  }

  if (expr.kind === "BinaryOp") {
    applyComparison(expr, out);
  }
}

function applyComparison(
  expr: Expr & { kind: "BinaryOp" },
  out: MutableConstraints,
): void {
  const right = literalNumber(expr.right);
  if (right === null) return;

  if (isLenCall(expr.left)) {
    applyLengthBound(expr.op, right, out);
    return;
  }

  if (isValueRef(expr.left)) {
    applyNumericBound(expr.op, right, out);
  }
}

function applyLengthBound(op: string, n: number, out: MutableConstraints): void {
  switch (op) {
    case ">=":
      out.minLength = n;
      return;
    case "<=":
      out.maxLength = n;
      return;
    case ">":
      out.minLength = n + 1;
      return;
    case "<":
      out.maxLength = n - 1;
      return;
    case "=":
      out.minLength = n;
      out.maxLength = n;
      return;
  }
}

function applyNumericBound(op: string, n: number, out: MutableConstraints): void {
  switch (op) {
    case ">=":
      out.minimum = n;
      return;
    case "<=":
      out.maximum = n;
      return;
    case ">":
      out.exclusiveMinimum = n;
      return;
    case "<":
      out.exclusiveMaximum = n;
      return;
    case "=":
      out.minimum = n;
      out.maximum = n;
      return;
  }
}

function isLenCall(expr: Expr): boolean {
  return (
    expr.kind === "Call" &&
    expr.callee.kind === "Identifier" &&
    expr.callee.name === "len"
  );
}

function isValueRef(expr: Expr): boolean {
  return expr.kind === "Identifier" && expr.name === "value";
}

function literalNumber(expr: Expr): number | null {
  if (expr.kind === "IntLit") return expr.value;
  if (expr.kind === "FloatLit") return expr.value;
  return null;
}
