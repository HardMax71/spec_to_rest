import type { Expr, ParamDecl, FieldAssign } from "#ir/types.js";

export function walkExpr(expr: Expr, visit: (node: Expr) => "skip" | void): void {
  if (visit(expr) === "skip") return;

  switch (expr.kind) {
    case "BinaryOp":
      walkExpr(expr.left, visit);
      walkExpr(expr.right, visit);
      break;
    case "UnaryOp":
      walkExpr(expr.operand, visit);
      break;
    case "Quantifier":
      for (const b of expr.bindings) walkExpr(b.domain, visit);
      walkExpr(expr.body, visit);
      break;
    case "SomeWrap":
      walkExpr(expr.expr, visit);
      break;
    case "The":
      walkExpr(expr.domain, visit);
      walkExpr(expr.body, visit);
      break;
    case "FieldAccess":
      walkExpr(expr.base, visit);
      break;
    case "EnumAccess":
      walkExpr(expr.base, visit);
      break;
    case "Index":
      walkExpr(expr.base, visit);
      walkExpr(expr.index, visit);
      break;
    case "Call":
      walkExpr(expr.callee, visit);
      for (const a of expr.args) walkExpr(a, visit);
      break;
    case "Prime":
      walkExpr(expr.expr, visit);
      break;
    case "Pre":
      walkExpr(expr.expr, visit);
      break;
    case "With":
      walkExpr(expr.base, visit);
      for (const u of expr.updates) walkExpr(u.value, visit);
      break;
    case "If":
      walkExpr(expr.condition, visit);
      walkExpr(expr.then, visit);
      walkExpr(expr.else_, visit);
      break;
    case "Let":
      walkExpr(expr.value, visit);
      walkExpr(expr.body, visit);
      break;
    case "Lambda":
      walkExpr(expr.body, visit);
      break;
    case "Constructor":
      for (const f of expr.fields) walkExpr(f.value, visit);
      break;
    case "SetLiteral":
      for (const e of expr.elements) walkExpr(e, visit);
      break;
    case "MapLiteral":
      for (const e of expr.entries) {
        walkExpr(e.key, visit);
        walkExpr(e.value, visit);
      }
      break;
    case "SetComprehension":
      walkExpr(expr.domain, visit);
      walkExpr(expr.predicate, visit);
      break;
    case "SeqLiteral":
      for (const e of expr.elements) walkExpr(e, visit);
      break;
    case "Matches":
      walkExpr(expr.expr, visit);
      break;
    case "Identifier":
    case "IntLit":
    case "FloatLit":
    case "StringLit":
    case "BoolLit":
    case "NoneLit":
      break;
  }
}

function rootIdentifier(expr: Expr): string | null {
  switch (expr.kind) {
    case "Identifier":
      return expr.name;
    case "Index":
      return rootIdentifier(expr.base);
    case "FieldAccess":
      return rootIdentifier(expr.base);
    default:
      return null;
  }
}

export function collectPrimedIdentifiers(ensures: readonly Expr[]): Set<string> {
  const result = new Set<string>();
  for (const clause of ensures) {
    walkExpr(clause, (node) => {
      if (node.kind === "Prime") {
        const name = rootIdentifier(node.expr);
        if (name) result.add(name);
      }
    });
  }
  return result;
}

export function collectPreservedRelations(
  ensures: readonly Expr[],
  stateFieldNames: ReadonlySet<string>,
): Set<string> {
  const result = new Set<string>();
  const flat = flattenEnsures(ensures);
  for (const clause of flat) {
    if (clause.kind !== "BinaryOp" || clause.op !== "=") continue;
    if (
      clause.left.kind === "Prime" &&
      clause.left.expr.kind === "Identifier" &&
      clause.right.kind === "Identifier" &&
      clause.left.expr.name === clause.right.name &&
      stateFieldNames.has(clause.left.expr.name)
    ) {
      result.add(clause.left.expr.name);
    }
  }
  return result;
}

export function detectCreatePattern(
  ensures: readonly Expr[],
  stateFieldNames: ReadonlySet<string>,
): { field: string } | null {
  const flat = flattenEnsures(ensures);
  for (const clause of flat) {
    if (clause.kind !== "BinaryOp" || clause.op !== "=") continue;
    const lhs = clause.left;
    const rhs = clause.right;

    // Pattern: R' = pre(R) + {...}  (possibly nested: pre(R) + A + B)
    if (
      lhs.kind === "Prime" &&
      lhs.expr.kind === "Identifier" &&
      stateFieldNames.has(lhs.expr.name) &&
      rhs.kind === "BinaryOp" &&
      rhs.op === "+" &&
      containsPreInPlusChain(rhs, lhs.expr.name)
    ) {
      return { field: lhs.expr.name };
    }
  }
  return null;
}

function containsPreInPlusChain(expr: Expr, fieldName: string): boolean {
  if (expr.kind === "Pre" && expr.expr.kind === "Identifier" && expr.expr.name === fieldName) {
    return true;
  }
  if (expr.kind === "BinaryOp" && expr.op === "+") {
    return containsPreInPlusChain(expr.left, fieldName);
  }
  return false;
}

export function detectDeletePattern(
  ensures: readonly Expr[],
  stateFieldNames: ReadonlySet<string>,
): { field: string } | null {
  const flat = flattenEnsures(ensures);
  for (const clause of flat) {
    // Pattern: key not in R'
    if (
      clause.kind === "BinaryOp" &&
      clause.op === "not_in" &&
      clause.right.kind === "Prime" &&
      clause.right.expr.kind === "Identifier" &&
      stateFieldNames.has(clause.right.expr.name)
    ) {
      return { field: clause.right.expr.name };
    }
  }
  return null;
}

export function collectWithFields(ensures: readonly Expr[]): {
  fieldNames: readonly string[];
  baseIdentifier: string | null;
} | null {
  for (const clause of ensures) {
    let found: { fieldNames: readonly string[]; baseIdentifier: string | null } | null = null;
    walkExpr(clause, (node) => {
      if (found) return "skip";
      if (node.kind === "With") {
        const fieldNames = node.updates.map((u: FieldAssign) => u.name);
        const baseIdent = resolveWithBase(node.base);
        found = { fieldNames, baseIdentifier: baseIdent };
        return "skip";
      }
    });
    if (found) return found;
  }
  return null;
}

function resolveWithBase(expr: Expr): string | null {
  switch (expr.kind) {
    case "Identifier":
      return null;
    case "Index":
      if (expr.base.kind === "Pre" && expr.base.expr.kind === "Identifier") {
        return expr.base.expr.name;
      }
      if (expr.base.kind === "Identifier") {
        return expr.base.name;
      }
      return rootIdentifier(expr.base);
    default:
      return rootIdentifier(expr);
  }
}

export function countFilterParams(inputs: readonly ParamDecl[]): number {
  let count = 0;
  for (const p of inputs) {
    if (p.typeExpr.kind === "OptionType") count++;
  }
  return count;
}

export function hasCollectionInput(inputs: readonly ParamDecl[]): boolean {
  for (const p of inputs) {
    const k = p.typeExpr.kind;
    if (k === "SetType" || k === "SeqType" || k === "MapType") return true;
  }
  return false;
}

export function flattenEnsures(ensures: readonly Expr[]): readonly Expr[] {
  const result: Expr[] = [];
  for (const clause of ensures) {
    flattenExpr(clause, result);
  }
  return result;
}

function flattenExpr(expr: Expr, out: Expr[]): void {
  if (expr.kind === "BinaryOp" && expr.op === "and") {
    flattenExpr(expr.left, out);
    flattenExpr(expr.right, out);
  } else if (expr.kind === "Let") {
    flattenExpr(expr.body, out);
  } else {
    out.push(expr);
  }
}

export function detectKeyExistsInRequires(
  requires: readonly Expr[],
  stateFieldNames: ReadonlySet<string>,
): Set<string> {
  const result = new Set<string>();
  const flat = flattenEnsures(requires);
  for (const clause of flat) {
    // Pattern: key in R
    if (
      clause.kind === "BinaryOp" &&
      clause.op === "in" &&
      clause.right.kind === "Identifier" &&
      stateFieldNames.has(clause.right.name)
    ) {
      result.add(clause.right.name);
    }
  }
  return result;
}
