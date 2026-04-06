/**
 * Runtime validators for deserialized IR nodes.
 * Each validator checks the discriminant and required fields,
 * recursing into nested Expr / TypeExpr / declaration arrays.
 */

import type {
  ServiceIR,
  Span,
  TypeExpr,
  Expr,
  FieldAssign,
  MapEntry,
  QuantifierBinding,
  EntityDecl,
  FieldDecl,
  EnumDecl,
  TypeAliasDecl,
  StateDecl,
  StateFieldDecl,
  OperationDecl,
  ParamDecl,
  TransitionDecl,
  TransitionRule,
  InvariantDecl,
  FactDecl,
  FunctionDecl,
  PredicateDecl,
  ConventionsDecl,
  ConventionRule,
} from "./types.js";

type V = Record<string, unknown>;

function isObj(v: unknown): v is V {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function isStr(v: unknown): v is string {
  return typeof v === "string";
}

function isNum(v: unknown): v is number {
  return typeof v === "number";
}

function isBool(v: unknown): v is boolean {
  return typeof v === "boolean";
}

function fail(path: string, msg: string): never {
  throw new Error(`Invalid IR at ${path}: ${msg}`);
}

function check(cond: boolean, path: string, msg: string): asserts cond {
  if (!cond) fail(path, msg);
}

function arr(v: unknown, path: string): unknown[] {
  check(Array.isArray(v), path, "expected array");
  return v as unknown[];
}

// ─── Span ────────────────────────────────────────────────────

export function validateSpan(v: unknown, path: string): asserts v is Span {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(isNum(o.startLine), `${path}.startLine`, "expected number");
  check(isNum(o.startCol), `${path}.startCol`, "expected number");
  check(isNum(o.endLine), `${path}.endLine`, "expected number");
  check(isNum(o.endCol), `${path}.endCol`, "expected number");
}

function optSpan(v: V, path: string): void {
  if ("span" in v && v.span !== undefined) validateSpan(v.span, `${path}.span`);
}

// ─── TypeExpr ────────────────────────────────────────────────

const MULTIPLICITIES = new Set(["one", "lone", "some", "set"]);

export function validateTypeExpr(v: unknown, path: string): asserts v is TypeExpr {
  check(isObj(v), path, "expected object");
  const o = v as V;
  optSpan(o, path);
  switch (o.kind) {
    case "NamedType":
      check(isStr(o.name), `${path}.name`, "expected string");
      break;
    case "SetType":
      validateTypeExpr(o.elementType, `${path}.elementType`);
      break;
    case "MapType":
      validateTypeExpr(o.keyType, `${path}.keyType`);
      validateTypeExpr(o.valueType, `${path}.valueType`);
      break;
    case "SeqType":
      validateTypeExpr(o.elementType, `${path}.elementType`);
      break;
    case "OptionType":
      validateTypeExpr(o.innerType, `${path}.innerType`);
      break;
    case "RelationType":
      validateTypeExpr(o.fromType, `${path}.fromType`);
      check(isStr(o.multiplicity) && MULTIPLICITIES.has(o.multiplicity as string), `${path}.multiplicity`, "expected one|lone|some|set");
      validateTypeExpr(o.toType, `${path}.toType`);
      break;
    default:
      fail(path, `unknown TypeExpr kind: ${String(o.kind)}`);
  }
}

// ─── Expr ────────────────────────────────────────────────────

const BINARY_OPS = new Set([
  "and", "or", "implies", "iff",
  "=", "!=", "<", ">", "<=", ">=",
  "in", "not_in", "subset",
  "union", "intersect", "minus",
  "+", "-", "*", "/",
]);
const UNARY_OPS = new Set(["not", "negate", "cardinality", "power"]);
const QUANTIFIER_KINDS = new Set(["all", "some", "no", "exists"]);

function validateFieldAssign(v: unknown, path: string): asserts v is FieldAssign {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(isStr(o.name), `${path}.name`, "expected string");
  validateExpr(o.value, `${path}.value`);
}

function validateMapEntry(v: unknown, path: string): asserts v is MapEntry {
  check(isObj(v), path, "expected object");
  const o = v as V;
  validateExpr(o.key, `${path}.key`);
  validateExpr(o.value, `${path}.value`);
}

function validateQuantifierBinding(v: unknown, path: string): asserts v is QuantifierBinding {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(isStr(o.variable), `${path}.variable`, "expected string");
  validateExpr(o.domain, `${path}.domain`);
  check(o.bindingKind === "in" || o.bindingKind === "colon", `${path}.bindingKind`, 'expected "in" or "colon"');
}

export function validateExpr(v: unknown, path: string): asserts v is Expr {
  check(isObj(v), path, "expected object");
  const o = v as V;
  optSpan(o, path);
  switch (o.kind) {
    case "BinaryOp":
      check(isStr(o.op) && BINARY_OPS.has(o.op as string), `${path}.op`, "invalid BinaryOp");
      validateExpr(o.left, `${path}.left`);
      validateExpr(o.right, `${path}.right`);
      break;
    case "UnaryOp":
      check(isStr(o.op) && UNARY_OPS.has(o.op as string), `${path}.op`, "invalid UnaryOp");
      validateExpr(o.operand, `${path}.operand`);
      break;
    case "Quantifier":
      check(isStr(o.quantifier) && QUANTIFIER_KINDS.has(o.quantifier as string), `${path}.quantifier`, "invalid QuantifierKind");
      for (const [i, b] of arr(o.bindings, `${path}.bindings`).entries())
        validateQuantifierBinding(b, `${path}.bindings[${i}]`);
      validateExpr(o.body, `${path}.body`);
      break;
    case "SomeWrap":
      validateExpr(o.expr, `${path}.expr`);
      break;
    case "The":
      check(isStr(o.variable), `${path}.variable`, "expected string");
      validateExpr(o.domain, `${path}.domain`);
      validateExpr(o.body, `${path}.body`);
      break;
    case "FieldAccess":
      validateExpr(o.base, `${path}.base`);
      check(isStr(o.field), `${path}.field`, "expected string");
      break;
    case "EnumAccess":
      validateExpr(o.base, `${path}.base`);
      check(isStr(o.member), `${path}.member`, "expected string");
      break;
    case "Index":
      validateExpr(o.base, `${path}.base`);
      validateExpr(o.index, `${path}.index`);
      break;
    case "Call":
      validateExpr(o.callee, `${path}.callee`);
      for (const [i, a] of arr(o.args, `${path}.args`).entries())
        validateExpr(a, `${path}.args[${i}]`);
      break;
    case "Prime":
    case "Pre":
      validateExpr(o.expr, `${path}.expr`);
      break;
    case "With":
      validateExpr(o.base, `${path}.base`);
      for (const [i, u] of arr(o.updates, `${path}.updates`).entries())
        validateFieldAssign(u, `${path}.updates[${i}]`);
      break;
    case "If":
      validateExpr(o.condition, `${path}.condition`);
      validateExpr(o.then, `${path}.then`);
      validateExpr(o.else_, `${path}.else_`);
      break;
    case "Let":
      check(isStr(o.variable), `${path}.variable`, "expected string");
      validateExpr(o.value, `${path}.value`);
      validateExpr(o.body, `${path}.body`);
      break;
    case "Lambda":
      check(isStr(o.param), `${path}.param`, "expected string");
      validateExpr(o.body, `${path}.body`);
      break;
    case "Constructor":
      check(isStr(o.typeName), `${path}.typeName`, "expected string");
      for (const [i, f] of arr(o.fields, `${path}.fields`).entries())
        validateFieldAssign(f, `${path}.fields[${i}]`);
      break;
    case "SetLiteral":
    case "SeqLiteral":
      for (const [i, e] of arr(o.elements, `${path}.elements`).entries())
        validateExpr(e, `${path}.elements[${i}]`);
      break;
    case "MapLiteral":
      for (const [i, e] of arr(o.entries, `${path}.entries`).entries())
        validateMapEntry(e, `${path}.entries[${i}]`);
      break;
    case "SetComprehension":
      check(isStr(o.variable), `${path}.variable`, "expected string");
      validateExpr(o.domain, `${path}.domain`);
      validateExpr(o.predicate, `${path}.predicate`);
      break;
    case "Matches":
      validateExpr(o.expr, `${path}.expr`);
      check(isStr(o.pattern), `${path}.pattern`, "expected string");
      break;
    case "IntLit":
    case "FloatLit":
      check(isNum(o.value), `${path}.value`, "expected number");
      break;
    case "StringLit":
      check(isStr(o.value), `${path}.value`, "expected string");
      break;
    case "BoolLit":
      check(isBool(o.value), `${path}.value`, "expected boolean");
      break;
    case "NoneLit":
      break;
    case "Identifier":
      check(isStr(o.name), `${path}.name`, "expected string");
      break;
    default:
      fail(path, `unknown Expr kind: ${String(o.kind)}`);
  }
}

// ─── Declarations ────────────────────────────────────────────

function optExpr(v: unknown, path: string): void {
  if (v !== null) validateExpr(v, path);
}

function validateParamDecl(v: unknown, path: string): asserts v is ParamDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Param", `${path}.kind`, 'expected "Param"');
  check(isStr(o.name), `${path}.name`, "expected string");
  validateTypeExpr(o.typeExpr, `${path}.typeExpr`);
  optSpan(o, path);
}

function validateFieldDecl(v: unknown, path: string): asserts v is FieldDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Field", `${path}.kind`, 'expected "Field"');
  check(isStr(o.name), `${path}.name`, "expected string");
  validateTypeExpr(o.typeExpr, `${path}.typeExpr`);
  optExpr(o.constraint, `${path}.constraint`);
  optSpan(o, path);
}

function validateEntityDecl(v: unknown, path: string): asserts v is EntityDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Entity", `${path}.kind`, 'expected "Entity"');
  check(isStr(o.name), `${path}.name`, "expected string");
  check(o.extends_ === null || isStr(o.extends_), `${path}.extends_`, "expected string or null");
  for (const [i, f] of arr(o.fields, `${path}.fields`).entries())
    validateFieldDecl(f, `${path}.fields[${i}]`);
  for (const [i, e] of arr(o.invariants, `${path}.invariants`).entries())
    validateExpr(e, `${path}.invariants[${i}]`);
  optSpan(o, path);
}

function validateEnumDecl(v: unknown, path: string): asserts v is EnumDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Enum", `${path}.kind`, 'expected "Enum"');
  check(isStr(o.name), `${path}.name`, "expected string");
  for (const val of arr(o.values, `${path}.values`))
    check(isStr(val), `${path}.values[]`, "expected string");
  optSpan(o, path);
}

function validateTypeAliasDecl(v: unknown, path: string): asserts v is TypeAliasDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "TypeAlias", `${path}.kind`, 'expected "TypeAlias"');
  check(isStr(o.name), `${path}.name`, "expected string");
  validateTypeExpr(o.typeExpr, `${path}.typeExpr`);
  optExpr(o.constraint, `${path}.constraint`);
  optSpan(o, path);
}

function validateStateFieldDecl(v: unknown, path: string): asserts v is StateFieldDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "StateField", `${path}.kind`, 'expected "StateField"');
  check(isStr(o.name), `${path}.name`, "expected string");
  validateTypeExpr(o.typeExpr, `${path}.typeExpr`);
  optSpan(o, path);
}

function validateStateDecl(v: unknown, path: string): asserts v is StateDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "State", `${path}.kind`, 'expected "State"');
  for (const [i, f] of arr(o.fields, `${path}.fields`).entries())
    validateStateFieldDecl(f, `${path}.fields[${i}]`);
  optSpan(o, path);
}

function validateOperationDecl(v: unknown, path: string): asserts v is OperationDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Operation", `${path}.kind`, 'expected "Operation"');
  check(isStr(o.name), `${path}.name`, "expected string");
  for (const [i, p] of arr(o.inputs, `${path}.inputs`).entries())
    validateParamDecl(p, `${path}.inputs[${i}]`);
  for (const [i, p] of arr(o.outputs, `${path}.outputs`).entries())
    validateParamDecl(p, `${path}.outputs[${i}]`);
  for (const [i, e] of arr(o.requires, `${path}.requires`).entries())
    validateExpr(e, `${path}.requires[${i}]`);
  for (const [i, e] of arr(o.ensures, `${path}.ensures`).entries())
    validateExpr(e, `${path}.ensures[${i}]`);
  optSpan(o, path);
}

function validateTransitionRule(v: unknown, path: string): asserts v is TransitionRule {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "TransitionRule", `${path}.kind`, 'expected "TransitionRule"');
  check(isStr(o.from), `${path}.from`, "expected string");
  check(isStr(o.to), `${path}.to`, "expected string");
  check(isStr(o.via), `${path}.via`, "expected string");
  optExpr(o.guard, `${path}.guard`);
  optSpan(o, path);
}

function validateTransitionDecl(v: unknown, path: string): asserts v is TransitionDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Transition", `${path}.kind`, 'expected "Transition"');
  check(isStr(o.name), `${path}.name`, "expected string");
  check(isStr(o.entityName), `${path}.entityName`, "expected string");
  check(isStr(o.fieldName), `${path}.fieldName`, "expected string");
  for (const [i, r] of arr(o.rules, `${path}.rules`).entries())
    validateTransitionRule(r, `${path}.rules[${i}]`);
  optSpan(o, path);
}

function validateInvariantDecl(v: unknown, path: string): asserts v is InvariantDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Invariant", `${path}.kind`, 'expected "Invariant"');
  check(o.name === null || isStr(o.name), `${path}.name`, "expected string or null");
  validateExpr(o.expr, `${path}.expr`);
  optSpan(o, path);
}

function validateFactDecl(v: unknown, path: string): asserts v is FactDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Fact", `${path}.kind`, 'expected "Fact"');
  check(o.name === null || isStr(o.name), `${path}.name`, "expected string or null");
  validateExpr(o.expr, `${path}.expr`);
  optSpan(o, path);
}

function validateFunctionDecl(v: unknown, path: string): asserts v is FunctionDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Function", `${path}.kind`, 'expected "Function"');
  check(isStr(o.name), `${path}.name`, "expected string");
  for (const [i, p] of arr(o.params, `${path}.params`).entries())
    validateParamDecl(p, `${path}.params[${i}]`);
  validateTypeExpr(o.returnType, `${path}.returnType`);
  validateExpr(o.body, `${path}.body`);
  optSpan(o, path);
}

function validatePredicateDecl(v: unknown, path: string): asserts v is PredicateDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Predicate", `${path}.kind`, 'expected "Predicate"');
  check(isStr(o.name), `${path}.name`, "expected string");
  for (const [i, p] of arr(o.params, `${path}.params`).entries())
    validateParamDecl(p, `${path}.params[${i}]`);
  validateExpr(o.body, `${path}.body`);
  optSpan(o, path);
}

function validateConventionRule(v: unknown, path: string): asserts v is ConventionRule {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "ConventionRule", `${path}.kind`, 'expected "ConventionRule"');
  check(isStr(o.target), `${path}.target`, "expected string");
  check(isStr(o.property), `${path}.property`, "expected string");
  check(o.qualifier === null || isStr(o.qualifier), `${path}.qualifier`, "expected string or null");
  validateExpr(o.value, `${path}.value`);
  optSpan(o, path);
}

function validateConventionsDecl(v: unknown, path: string): asserts v is ConventionsDecl {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Conventions", `${path}.kind`, 'expected "Conventions"');
  for (const [i, r] of arr(o.rules, `${path}.rules`).entries())
    validateConventionRule(r, `${path}.rules[${i}]`);
  optSpan(o, path);
}

// ─── ServiceIR (top-level) ───────────────────────────────────

export function validateServiceIR(v: unknown, path = "ServiceIR"): asserts v is ServiceIR {
  check(isObj(v), path, "expected object");
  const o = v as V;
  check(o.kind === "Service", `${path}.kind`, 'expected "Service"');
  check(isStr(o.name), `${path}.name`, "expected string");
  for (const val of arr(o.imports, `${path}.imports`))
    check(isStr(val), `${path}.imports[]`, "expected string");
  for (const [i, e] of arr(o.entities, `${path}.entities`).entries())
    validateEntityDecl(e, `${path}.entities[${i}]`);
  for (const [i, e] of arr(o.enums, `${path}.enums`).entries())
    validateEnumDecl(e, `${path}.enums[${i}]`);
  for (const [i, t] of arr(o.typeAliases, `${path}.typeAliases`).entries())
    validateTypeAliasDecl(t, `${path}.typeAliases[${i}]`);
  if (o.state !== null) validateStateDecl(o.state, `${path}.state`);
  for (const [i, op] of arr(o.operations, `${path}.operations`).entries())
    validateOperationDecl(op, `${path}.operations[${i}]`);
  for (const [i, t] of arr(o.transitions, `${path}.transitions`).entries())
    validateTransitionDecl(t, `${path}.transitions[${i}]`);
  for (const [i, inv] of arr(o.invariants, `${path}.invariants`).entries())
    validateInvariantDecl(inv, `${path}.invariants[${i}]`);
  for (const [i, f] of arr(o.facts, `${path}.facts`).entries())
    validateFactDecl(f, `${path}.facts[${i}]`);
  for (const [i, fn] of arr(o.functions, `${path}.functions`).entries())
    validateFunctionDecl(fn, `${path}.functions[${i}]`);
  for (const [i, p] of arr(o.predicates, `${path}.predicates`).entries())
    validatePredicateDecl(p, `${path}.predicates[${i}]`);
  if (o.conventions !== null) validateConventionsDecl(o.conventions, `${path}.conventions`);
  optSpan(o, path);
}
