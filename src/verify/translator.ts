import type {
  ServiceIR,
  EntityDecl,
  EnumDecl,
  TypeAliasDecl,
  StateDecl,
  StateFieldDecl,
  InvariantDecl,
  OperationDecl,
  TypeExpr,
  Expr,
  BinaryOp,
  QuantifierBinding,
} from "#ir/types.js";
import {
  type Z3Sort,
  type Z3Expr,
  type Z3FunctionDecl,
  type Z3Script,
  type Z3Binding,
  type CmpOp,
  type ArithOp,
  Z3_INT,
  Z3_BOOL,
  uninterp,
  sortKey,
} from "#verify/script.js";
import { TranslatorError } from "#verify/types.js";

const STRING_SORT_NAME = "String";

interface EntityInfo {
  readonly sort: Z3Sort;
  readonly fields: ReadonlyMap<string, { readonly sort: Z3Sort; readonly funcName: string }>;
}

interface StateInfo {
  readonly kind: "Relation";
  readonly keySort: Z3Sort;
  readonly valueSort: Z3Sort;
  readonly domFunc: string;
  readonly mapFunc: string;
  readonly isTotal: boolean;
}

interface StateConstInfo {
  readonly kind: "Const";
  readonly sort: Z3Sort;
  readonly funcName: string;
}

type StateEntry = StateInfo | StateConstInfo;

type TypeEnv = ReadonlyMap<string, Z3Expr>;

class TranslateCtx {
  readonly sorts = new Map<string, Z3Sort>();
  readonly funcs = new Map<string, Z3FunctionDecl>();
  readonly assertions: Z3Expr[] = [];
  readonly entities = new Map<string, EntityInfo>();
  readonly enums = new Map<string, { readonly sort: Z3Sort; readonly members: readonly string[] }>();
  readonly typeAliases = new Map<string, { readonly sort: Z3Sort }>();
  readonly primitiveAliases = new Map<
    string,
    { readonly underlyingSort: Z3Sort; readonly constraint: Expr }
  >();
  readonly state = new Map<string, StateEntry>();
  private readonly matchesIds = new Map<string, number>();
  private readonly stringLitIds = new Map<string, number>();
  private readonly cardinalityNames = new Map<string, string>();

  declareSort(sort: Z3Sort): void {
    if (sort.kind !== "Uninterp") return;
    const key = sortKey(sort);
    if (!this.sorts.has(key)) this.sorts.set(key, sort);
  }

  declareFunc(decl: Z3FunctionDecl): void {
    if (!this.funcs.has(decl.name)) {
      this.funcs.set(decl.name, decl);
      for (const s of decl.argSorts) this.declareSort(s);
      this.declareSort(decl.resultSort);
    }
  }

  matchesNameFor(pattern: string): string {
    const existing = this.matchesIds.get(pattern);
    if (existing !== undefined) return `matches_${existing}`;
    const id = this.matchesIds.size;
    this.matchesIds.set(pattern, id);
    return `matches_${id}`;
  }

  stringLitNameFor(value: string): string {
    const existing = this.stringLitIds.get(value);
    if (existing !== undefined) return `str_${existing}`;
    const id = this.stringLitIds.size;
    this.stringLitIds.set(value, id);
    return `str_${id}`;
  }

  stringLitCount(): number {
    return this.stringLitIds.size;
  }

  cardinalityNameFor(targetName: string): string {
    const existing = this.cardinalityNames.get(targetName);
    if (existing !== undefined) return existing;
    const name = `card_${targetName}`;
    this.cardinalityNames.set(targetName, name);
    return name;
  }
}

export function translate(ir: ServiceIR): Z3Script {
  const ctx = new TranslateCtx();
  declareBase(ctx, ir);
  for (const inv of ir.invariants) emitTopLevelInvariant(ctx, inv);
  return finalizeScript(ctx);
}

export function translateOperationRequires(ir: ServiceIR, op: OperationDecl): Z3Script {
  const ctx = new TranslateCtx();
  declareBase(ctx, ir);
  const env = declareOperationInputs(ctx, op);
  for (const req of op.requires) {
    ctx.assertions.push(translateExpr(ctx, req, env));
  }
  return finalizeScript(ctx);
}

export function translateOperationEnabled(ir: ServiceIR, op: OperationDecl): Z3Script {
  const ctx = new TranslateCtx();
  declareBase(ctx, ir);
  for (const inv of ir.invariants) emitTopLevelInvariant(ctx, inv);
  const env = declareOperationInputs(ctx, op);
  for (const req of op.requires) {
    ctx.assertions.push(translateExpr(ctx, req, env));
  }
  return finalizeScript(ctx);
}

function declareBase(ctx: TranslateCtx, ir: ServiceIR): void {
  for (const e of ir.enums) declareEnum(ctx, e);
  for (const t of ir.typeAliases) declareTypeAlias(ctx, t);
  for (const e of ir.entities) declareEntity(ctx, e);
  if (ir.state) declareState(ctx, ir.state);

  for (const t of ir.typeAliases) emitTypeAliasConstraint(ctx, t);
  for (const e of ir.entities) emitEntityAssertions(ctx, e);
}

function finalizeScript(ctx: TranslateCtx): Z3Script {
  emitStringLiteralDistinctness(ctx);
  return {
    sorts: [...ctx.sorts.values()].sort((a, b) => sortKey(a).localeCompare(sortKey(b))),
    funcs: [...ctx.funcs.values()].sort((a, b) => a.name.localeCompare(b.name)),
    assertions: ctx.assertions,
  };
}

function declareOperationInputs(ctx: TranslateCtx, op: OperationDecl): Map<string, Z3Expr> {
  const env = new Map<string, Z3Expr>();
  for (const input of op.inputs) {
    const sort = sortForType(ctx, input.typeExpr);
    const funcName = `input_${op.name}_${input.name}`;
    if (!ctx.funcs.has(funcName)) {
      ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: sort });
    }
    env.set(input.name, { kind: "App", func: funcName, args: [] });
    maybeAssertInputRefinement(ctx, op, input, funcName, sort);
  }
  return env;
}

function maybeAssertInputRefinement(
  ctx: TranslateCtx,
  op: OperationDecl,
  input: { readonly name: string; readonly typeExpr: TypeExpr },
  funcName: string,
  sort: Z3Sort,
): void {
  void op;
  void sort;
  if (input.typeExpr.kind !== "NamedType") return;
  const alias = ctx.primitiveAliases.get(input.typeExpr.name);
  if (!alias) return;
  const env = new Map<string, Z3Expr>();
  env.set("value", { kind: "App", func: funcName, args: [] });
  ctx.assertions.push(translateExpr(ctx, alias.constraint, env));
}

function declareEnum(ctx: TranslateCtx, e: EnumDecl): void {
  const sort = uninterp(e.name);
  ctx.declareSort(sort);
  ctx.enums.set(e.name, { sort, members: e.values });
  const memberConsts: Z3Expr[] = [];
  for (const v of e.values) {
    const funcName = `${e.name}_${v}`;
    ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: sort });
    memberConsts.push({ kind: "App", func: funcName, args: [] });
  }
  if (memberConsts.length >= 2) {
    const pairs: Z3Expr[] = [];
    for (let i = 0; i < memberConsts.length; i += 1) {
      for (let j = i + 1; j < memberConsts.length; j += 1) {
        pairs.push({
          kind: "Not",
          arg: { kind: "Cmp", op: "=", lhs: memberConsts[i], rhs: memberConsts[j] },
        });
      }
    }
    ctx.assertions.push(pairs.length === 1 ? pairs[0] : { kind: "And", args: pairs });
  }
}

function declareTypeAlias(ctx: TranslateCtx, t: TypeAliasDecl): void {
  const primitiveSort = primitiveUnderlyingSort(t);
  if (primitiveSort && t.constraint) {
    ctx.primitiveAliases.set(t.name, {
      underlyingSort: primitiveSort,
      constraint: t.constraint,
    });
    return;
  }
  const sort = primitiveSort ?? uninterp(t.name);
  ctx.declareSort(sort);
  ctx.typeAliases.set(t.name, { sort });
}

function primitiveUnderlyingSort(t: TypeAliasDecl): Z3Sort | null {
  if (t.typeExpr.kind !== "NamedType") return null;
  if (t.typeExpr.name === "Int") return Z3_INT;
  if (t.typeExpr.name === "Bool") return Z3_BOOL;
  return null;
}

function emitTypeAliasConstraint(ctx: TranslateCtx, t: TypeAliasDecl): void {
  if (!t.constraint) return;
  if (ctx.primitiveAliases.has(t.name)) return;
  const sort = ctx.typeAliases.get(t.name)!.sort;
  const varName = `self_${t.name}`;
  const selfRef: Z3Expr = { kind: "Var", name: varName, sort };
  const env = new Map<string, Z3Expr>();
  env.set("value", selfRef);
  const body = translateExpr(ctx, t.constraint, env);
  ctx.assertions.push({
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort }],
    body,
  });
}

function declareEntity(ctx: TranslateCtx, e: EntityDecl): void {
  const sort = uninterp(e.name);
  ctx.declareSort(sort);
  const fields = new Map<string, { sort: Z3Sort; funcName: string }>();
  for (const f of e.fields) {
    const fieldSort = sortForType(ctx, f.typeExpr);
    const funcName = `${e.name}_${f.name}`;
    ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [sort], resultSort: fieldSort });
    fields.set(f.name, { sort: fieldSort, funcName });
  }
  ctx.entities.set(e.name, { sort, fields });
}

function emitEntityAssertions(ctx: TranslateCtx, e: EntityDecl): void {
  const info = ctx.entities.get(e.name)!;
  const varName = `self_${e.name}`;
  const selfRef: Z3Expr = { kind: "Var", name: varName, sort: info.sort };

  for (const f of e.fields) {
    const fieldInfo = info.fields.get(f.name)!;
    const aliasConstraint = refinementConstraintFor(ctx, f.typeExpr);
    const fieldRead: Z3Expr = { kind: "App", func: fieldInfo.funcName, args: [selfRef] };
    const bodies: Z3Expr[] = [];
    if (aliasConstraint) {
      const env = new Map<string, Z3Expr>();
      env.set("value", fieldRead);
      bodies.push(translateExpr(ctx, aliasConstraint, env));
    }
    if (f.constraint) {
      const env = new Map<string, Z3Expr>();
      env.set("value", fieldRead);
      bodies.push(translateExpr(ctx, f.constraint, env));
    }
    if (bodies.length > 0) {
      const body: Z3Expr = bodies.length === 1 ? bodies[0] : { kind: "And", args: bodies };
      ctx.assertions.push({
        kind: "Quantifier",
        q: "ForAll",
        bindings: [{ name: varName, sort: info.sort }],
        body,
      });
    }
  }

  for (const inv of e.invariants) {
    const env = new Map<string, Z3Expr>();
    env.set("self", selfRef);
    for (const [fname, finfo] of info.fields) {
      env.set(fname, { kind: "App", func: finfo.funcName, args: [selfRef] });
    }
    const body = translateExpr(ctx, inv, env);
    ctx.assertions.push({
      kind: "Quantifier",
      q: "ForAll",
      bindings: [{ name: varName, sort: info.sort }],
      body,
    });
  }
}

function refinementConstraintFor(ctx: TranslateCtx, te: TypeExpr): Expr | null {
  if (te.kind !== "NamedType") return null;
  const prim = ctx.primitiveAliases.get(te.name);
  return prim ? prim.constraint : null;
}

function declareState(ctx: TranslateCtx, state: StateDecl): void {
  for (const sf of state.fields) declareStateField(ctx, sf);
  for (const sf of state.fields) emitStateTotality(ctx, sf);
  for (const sf of state.fields) emitStateRefinement(ctx, sf);
}

function emitStateRefinement(ctx: TranslateCtx, sf: StateFieldDecl): void {
  const info = ctx.state.get(sf.name);
  if (!info) return;
  if (info.kind === "Const") {
    const aliasConstraint = refinementConstraintFor(ctx, sf.typeExpr);
    if (!aliasConstraint) return;
    const env = new Map<string, Z3Expr>();
    env.set("value", { kind: "App", func: info.funcName, args: [] });
    ctx.assertions.push(translateExpr(ctx, aliasConstraint, env));
    return;
  }
  if (sf.typeExpr.kind !== "RelationType" && sf.typeExpr.kind !== "MapType") return;
  const valueType = sf.typeExpr.kind === "RelationType" ? sf.typeExpr.toType : sf.typeExpr.valueType;
  const aliasConstraint = refinementConstraintFor(ctx, valueType);
  if (!aliasConstraint) return;
  const varName = `k_${sf.name}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: info.keySort };
  const env = new Map<string, Z3Expr>();
  env.set("value", { kind: "App", func: info.mapFunc, args: [keyVar] });
  const body = translateExpr(ctx, aliasConstraint, env);
  const guarded: Z3Expr = info.isTotal
    ? body
    : {
        kind: "Implies",
        lhs: { kind: "App", func: info.domFunc, args: [keyVar] },
        rhs: body,
      };
  ctx.assertions.push({
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: info.keySort }],
    body: guarded,
  });
}

function declareStateField(ctx: TranslateCtx, sf: StateFieldDecl): void {
  if (sf.typeExpr.kind === "RelationType") {
    const keySort = sortForType(ctx, sf.typeExpr.fromType);
    const valueSort = sortForType(ctx, sf.typeExpr.toType);
    const domFunc = `${sf.name}_dom`;
    const mapFunc = `${sf.name}_map`;
    ctx.declareFunc({ kind: "FuncDecl", name: domFunc, argSorts: [keySort], resultSort: Z3_BOOL });
    ctx.declareFunc({ kind: "FuncDecl", name: mapFunc, argSorts: [keySort], resultSort: valueSort });
    ctx.state.set(sf.name, {
      kind: "Relation",
      keySort,
      valueSort,
      domFunc,
      mapFunc,
      isTotal: sf.typeExpr.multiplicity === "one",
    });
  } else if (sf.typeExpr.kind === "MapType") {
    const keySort = sortForType(ctx, sf.typeExpr.keyType);
    const valueSort = sortForType(ctx, sf.typeExpr.valueType);
    const domFunc = `${sf.name}_dom`;
    const mapFunc = `${sf.name}_map`;
    ctx.declareFunc({ kind: "FuncDecl", name: domFunc, argSorts: [keySort], resultSort: Z3_BOOL });
    ctx.declareFunc({ kind: "FuncDecl", name: mapFunc, argSorts: [keySort], resultSort: valueSort });
    ctx.state.set(sf.name, {
      kind: "Relation",
      keySort,
      valueSort,
      domFunc,
      mapFunc,
      isTotal: false,
    });
  } else {
    const fieldSort = sortForType(ctx, sf.typeExpr);
    const funcName = `state_${sf.name}`;
    ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: fieldSort });
    ctx.state.set(sf.name, { kind: "Const", sort: fieldSort, funcName });
  }
}

function emitStateTotality(ctx: TranslateCtx, sf: StateFieldDecl): void {
  const info = ctx.state.get(sf.name);
  if (!info || info.kind !== "Relation" || !info.isTotal) return;
  const varName = `k_${sf.name}`;
  const body: Z3Expr = {
    kind: "App",
    func: info.domFunc,
    args: [{ kind: "Var", name: varName, sort: info.keySort }],
  };
  ctx.assertions.push({
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: info.keySort }],
    body,
  });
}

function emitTopLevelInvariant(ctx: TranslateCtx, inv: InvariantDecl): void {
  const env = new Map<string, Z3Expr>();
  ctx.assertions.push(translateExpr(ctx, inv.expr, env));
}

function emitStringLiteralDistinctness(ctx: TranslateCtx): void {
  const n = ctx.stringLitCount();
  if (n < 2) return;
  const consts: Z3Expr[] = [];
  for (let i = 0; i < n; i += 1) {
    consts.push({ kind: "App", func: `str_${i}`, args: [] });
  }
  const pairs: Z3Expr[] = [];
  for (let i = 0; i < consts.length; i += 1) {
    for (let j = i + 1; j < consts.length; j += 1) {
      pairs.push({ kind: "Cmp", op: "!=", lhs: consts[i], rhs: consts[j] });
    }
  }
  ctx.assertions.push(pairs.length === 1 ? pairs[0] : { kind: "And", args: pairs });
}

function sortForType(ctx: TranslateCtx, te: TypeExpr): Z3Sort {
  if (te.kind === "NamedType") return sortForNamedType(ctx, te.name);
  if (te.kind === "OptionType") return sortForType(ctx, te.innerType);
  if (te.kind === "SetType") return uninterp(`Set_${sortNameOf(sortForType(ctx, te.elementType))}`);
  if (te.kind === "SeqType") return uninterp(`Seq_${sortNameOf(sortForType(ctx, te.elementType))}`);
  if (te.kind === "MapType") {
    const k = sortNameOf(sortForType(ctx, te.keyType));
    const v = sortNameOf(sortForType(ctx, te.valueType));
    return uninterp(`Map_${k}_${v}`);
  }
  if (te.kind === "RelationType") {
    const k = sortNameOf(sortForType(ctx, te.fromType));
    const v = sortNameOf(sortForType(ctx, te.toType));
    return uninterp(`Rel_${k}_${v}`);
  }
  return uninterp("Unknown");
}

function sortNameOf(s: Z3Sort): string {
  if (s.kind === "Int") return "Int";
  if (s.kind === "Bool") return "Bool";
  return s.name;
}

function sortForNamedType(ctx: TranslateCtx, name: string): Z3Sort {
  if (name === "Int") return Z3_INT;
  if (name === "Bool") return Z3_BOOL;
  if (name === "String") return uninterp(STRING_SORT_NAME);
  const entity = ctx.entities.get(name);
  if (entity) return entity.sort;
  const enumSort = ctx.enums.get(name);
  if (enumSort) return enumSort.sort;
  const primAlias = ctx.primitiveAliases.get(name);
  if (primAlias) return primAlias.underlyingSort;
  const alias = ctx.typeAliases.get(name);
  if (alias) return alias.sort;
  return uninterp(name);
}

export function translateExpr(ctx: TranslateCtx, expr: Expr, env: TypeEnv): Z3Expr {
  switch (expr.kind) {
    case "IntLit":
      return { kind: "IntLit", value: expr.value };
    case "BoolLit":
      return { kind: "BoolLit", value: expr.value };
    case "StringLit":
      return stringLiteralConst(ctx, expr.value);
    case "Identifier":
      return resolveIdentifier(ctx, expr.name, env);
    case "BinaryOp":
      return translateBinaryOp(ctx, expr.op, expr.left, expr.right, env);
    case "UnaryOp":
      return translateUnaryOp(ctx, expr, env);
    case "Quantifier":
      return translateQuantifier(ctx, expr, env);
    case "FieldAccess":
      return translateFieldAccess(ctx, expr, env);
    case "Index":
      return translateIndex(ctx, expr, env);
    case "Call":
      return translateCall(ctx, expr, env);
    case "Matches":
      return translateMatches(ctx, expr, env);
    case "EnumAccess":
      return translateEnumAccess(ctx, expr);
    default:
      throw new TranslatorError(`expression kind '${expr.kind}' is out of M4.1 scope`);
  }
}

function translateBinaryOp(
  ctx: TranslateCtx,
  op: BinaryOp,
  leftExpr: Expr,
  rightExpr: Expr,
  env: TypeEnv,
): Z3Expr {
  const left = translateExpr(ctx, leftExpr, env);
  const right = translateExpr(ctx, rightExpr, env);
  switch (op) {
    case "and":
      return { kind: "And", args: [left, right] };
    case "or":
      return { kind: "Or", args: [left, right] };
    case "implies":
      return { kind: "Implies", lhs: left, rhs: right };
    case "iff":
      return {
        kind: "And",
        args: [
          { kind: "Implies", lhs: left, rhs: right },
          { kind: "Implies", lhs: right, rhs: left },
        ],
      };
    case "=":
    case "!=":
    case "<":
    case "<=":
    case ">":
    case ">=":
      return { kind: "Cmp", op: op as CmpOp, lhs: left, rhs: right };
    case "+":
    case "-":
    case "*":
    case "/":
      return { kind: "Arith", op: op as ArithOp, args: [left, right] };
    case "in":
      return membership(leftExpr, rightExpr, left, ctx, op);
    case "not_in":
      return { kind: "Not", arg: membership(leftExpr, rightExpr, left, ctx, op) };
    case "subset":
    case "union":
    case "intersect":
    case "minus":
      throw new TranslatorError(
        `set operator '${op}' is out of M4.1 scope (deferred to M4.2+)`,
      );
    default:
      throw new TranslatorError(`binary op '${op}' is out of M4.1 scope`);
  }
}

function membership(
  leftExpr: Expr,
  rightExpr: Expr,
  leftZ: Z3Expr,
  ctx: TranslateCtx,
  op: "in" | "not_in",
): Z3Expr {
  if (rightExpr.kind === "Identifier") {
    const state = ctx.state.get(rightExpr.name);
    if (state && state.kind === "Relation") {
      return { kind: "App", func: state.domFunc, args: [leftZ] };
    }
  }
  void leftExpr;
  throw new TranslatorError(
    `membership operator '${op}' is only supported against a state relation in M4.1 (deferred to M4.2+ for general sets)`,
  );
}

function translateUnaryOp(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "UnaryOp" }>,
  env: TypeEnv,
): Z3Expr {
  switch (expr.op) {
    case "not":
      return { kind: "Not", arg: translateExpr(ctx, expr.operand, env) };
    case "negate":
      return {
        kind: "Arith",
        op: "-",
        args: [{ kind: "IntLit", value: 0 }, translateExpr(ctx, expr.operand, env)],
      };
    case "cardinality":
      return translateCardinality(ctx, expr.operand);
    case "power":
      throw new TranslatorError(
        `powerset operator is out of M4.1 scope (deferred to M4.2+)`,
      );
    default:
      throw new TranslatorError(`unary op '${expr.op}' is out of M4.1 scope`);
  }
}

function translateCardinality(ctx: TranslateCtx, operand: Expr): Z3Expr {
  if (operand.kind !== "Identifier") {
    throw new TranslatorError(
      "cardinality '#expr' is only supported on state-relation identifiers in M4.2 (deferred for general set expressions — see issue #73)",
    );
  }
  const state = ctx.state.get(operand.name);
  if (!state || state.kind !== "Relation") {
    throw new TranslatorError(
      `cardinality '#${operand.name}' requires a state relation; '${operand.name}' is not declared as one`,
    );
  }
  const funcName = ctx.cardinalityNameFor(operand.name);
  if (!ctx.funcs.has(funcName)) {
    ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: Z3_INT });
    ctx.assertions.push({
      kind: "Cmp",
      op: ">=",
      lhs: { kind: "App", func: funcName, args: [] },
      rhs: { kind: "IntLit", value: 0 },
    });
  }
  return { kind: "App", func: funcName, args: [] };
}

function translateQuantifier(
  ctx: TranslateCtx,
  q: Extract<Expr, { kind: "Quantifier" }>,
  env: TypeEnv,
): Z3Expr {
  const newEnv = new Map(env);
  const bindings: Z3Binding[] = [];
  const domainGuards: Z3Expr[] = [];
  for (const b of q.bindings) {
    const resolved = resolveBindingDomain(ctx, b);
    bindings.push({ name: b.variable, sort: resolved.sort });
    newEnv.set(b.variable, { kind: "Var", name: b.variable, sort: resolved.sort });
    if (resolved.guard) domainGuards.push(resolved.guard(b.variable));
  }
  const body = translateExpr(ctx, q.body, newEnv);
  const guardedBody = applyGuards(q.quantifier, domainGuards, body);
  const zq = mapQuantifier(q.quantifier);
  if (zq === "none") {
    const exists: Z3Expr = { kind: "Quantifier", q: "Exists", bindings, body: guardedBody };
    return { kind: "Not", arg: exists };
  }
  return { kind: "Quantifier", q: zq, bindings, body: guardedBody };
}

function mapQuantifier(q: string): "ForAll" | "Exists" | "none" {
  if (q === "all") return "ForAll";
  if (q === "some" || q === "exists") return "Exists";
  if (q === "no") return "none";
  throw new TranslatorError(`unknown quantifier kind '${q}'`);
}

function applyGuards(q: string, guards: readonly Z3Expr[], body: Z3Expr): Z3Expr {
  if (guards.length === 0) return body;
  const guardExpr: Z3Expr =
    guards.length === 1 ? guards[0] : { kind: "And", args: guards };
  if (q === "all") return { kind: "Implies", lhs: guardExpr, rhs: body };
  return { kind: "And", args: [guardExpr, body] };
}

interface BindingResolution {
  readonly sort: Z3Sort;
  readonly guard: ((varName: string) => Z3Expr) | null;
}

function resolveBindingDomain(ctx: TranslateCtx, b: QuantifierBinding): BindingResolution {
  if (b.domain.kind === "Identifier") {
    const name = b.domain.name;
    const entity = ctx.entities.get(name);
    if (entity) return { sort: entity.sort, guard: null };
    const alias = ctx.typeAliases.get(name);
    if (alias) return { sort: alias.sort, guard: null };
    const primAlias = ctx.primitiveAliases.get(name);
    if (primAlias) {
      return {
        sort: primAlias.underlyingSort,
        guard: (vn) => {
          const env = new Map<string, Z3Expr>();
          env.set("value", { kind: "Var", name: vn, sort: primAlias.underlyingSort });
          return translateExpr(ctx, primAlias.constraint, env);
        },
      };
    }
    const enumDecl = ctx.enums.get(name);
    if (enumDecl) return { sort: enumDecl.sort, guard: null };
    const state = ctx.state.get(name);
    if (state && state.kind === "Relation") {
      return {
        sort: state.keySort,
        guard: (vn) => ({
          kind: "App",
          func: state.domFunc,
          args: [{ kind: "Var", name: vn, sort: state.keySort }],
        }),
      };
    }
  }
  return { sort: uninterp("Unknown"), guard: null };
}

function translateFieldAccess(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "FieldAccess" }>,
  env: TypeEnv,
): Z3Expr {
  const base = translateExpr(ctx, expr.base, env);
  const baseSort = inferSort(ctx, expr.base, env, base);
  if (baseSort && baseSort.kind === "Uninterp") {
    const entity = ctx.entities.get(baseSort.name);
    if (entity) {
      const field = entity.fields.get(expr.field);
      if (field) {
        return { kind: "App", func: field.funcName, args: [base] };
      }
      throw new TranslatorError(
        `entity '${baseSort.name}' has no field '${expr.field}'`,
      );
    }
  }
  throw new TranslatorError(
    `field access '.${expr.field}' on non-entity sort is out of M4.1 scope`,
  );
}

function translateIndex(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "Index" }>,
  env: TypeEnv,
): Z3Expr {
  if (expr.base.kind === "Identifier") {
    const state = ctx.state.get(expr.base.name);
    if (state && state.kind === "Relation") {
      const key = translateExpr(ctx, expr.index, env);
      return { kind: "App", func: state.mapFunc, args: [key] };
    }
  }
  throw new TranslatorError(
    "indexing is only supported on state-relation identifiers in M4.1 (deferred to M4.2+ for general maps/sequences)",
  );
}

function translateCall(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "Call" }>,
  env: TypeEnv,
): Z3Expr {
  if (expr.callee.kind !== "Identifier") {
    throw new TranslatorError("higher-order call (non-identifier callee) is out of M4.1 scope");
  }
  const name = expr.callee.name;
  const args = expr.args.map((a) => translateExpr(ctx, a, env));
  const argSorts = expr.args.map((a) => inferSort(ctx, a, env, null) ?? uninterp("Any"));
  const resultSort = callReturnSort(name);
  const funcName = `${name}_${argSortsMangled(argSorts)}`;
  if (!ctx.funcs.has(funcName)) {
    ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts, resultSort });
  }
  return { kind: "App", func: funcName, args };
}

function callReturnSort(name: string): Z3Sort {
  if (name === "len") return Z3_INT;
  if (name === "isValidURI") return Z3_BOOL;
  return uninterp("Any");
}

function argSortsMangled(sorts: readonly Z3Sort[]): string {
  if (sorts.length === 0) return "0";
  return sorts.map(sortNameOf).join("_");
}

function translateMatches(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "Matches" }>,
  env: TypeEnv,
): Z3Expr {
  const arg = translateExpr(ctx, expr.expr, env);
  const argSort = inferSort(ctx, expr.expr, env, arg) ?? uninterp(STRING_SORT_NAME);
  const baseName = ctx.matchesNameFor(expr.pattern);
  const funcName = `${baseName}_${sortNameOf(argSort)}`;
  if (!ctx.funcs.has(funcName)) {
    ctx.declareFunc({
      kind: "FuncDecl",
      name: funcName,
      argSorts: [argSort],
      resultSort: Z3_BOOL,
    });
  }
  return { kind: "App", func: funcName, args: [arg] };
}

function translateEnumAccess(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "EnumAccess" }>,
): Z3Expr {
  if (expr.base.kind !== "Identifier") {
    throw new TranslatorError("enum access base must be an identifier");
  }
  const enumName = expr.base.name;
  const memberName = expr.member;
  const funcName = `${enumName}_${memberName}`;
  if (!ctx.funcs.has(funcName)) {
    const enumSort = ctx.enums.get(enumName);
    const resultSort = enumSort ? enumSort.sort : uninterp(enumName);
    ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort });
  }
  return { kind: "App", func: funcName, args: [] };
}

function stringLiteralConst(ctx: TranslateCtx, value: string): Z3Expr {
  const name = ctx.stringLitNameFor(value);
  if (!ctx.funcs.has(name)) {
    ctx.declareFunc({
      kind: "FuncDecl",
      name,
      argSorts: [],
      resultSort: uninterp(STRING_SORT_NAME),
    });
  }
  return { kind: "App", func: name, args: [] };
}

function resolveIdentifier(ctx: TranslateCtx, name: string, env: TypeEnv): Z3Expr {
  const bound = env.get(name);
  if (bound) return bound;
  const state = ctx.state.get(name);
  if (state) {
    if (state.kind === "Const") {
      return { kind: "App", func: state.funcName, args: [] };
    }
    const refName = `state_${name}_ref`;
    if (!ctx.funcs.has(refName)) {
      ctx.declareFunc({
        kind: "FuncDecl",
        name: refName,
        argSorts: [],
        resultSort: uninterp(`Rel_${sortNameOf(state.keySort)}_${sortNameOf(state.valueSort)}`),
      });
    }
    return { kind: "App", func: refName, args: [] };
  }
  const entity = ctx.entities.get(name);
  if (entity) {
    const funcName = `entity_${name}_ref`;
    if (!ctx.funcs.has(funcName)) {
      ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: entity.sort });
    }
    return { kind: "App", func: funcName, args: [] };
  }
  const funcName = `id_${name}`;
  if (!ctx.funcs.has(funcName)) {
    ctx.declareFunc({
      kind: "FuncDecl",
      name: funcName,
      argSorts: [],
      resultSort: uninterp("Any"),
    });
  }
  return { kind: "App", func: funcName, args: [] };
}

function inferSort(
  ctx: TranslateCtx,
  expr: Expr,
  env: TypeEnv,
  translated: Z3Expr | null,
): Z3Sort | null {
  if (expr.kind === "Identifier") {
    const bound = env.get(expr.name);
    if (bound) {
      const inferred = inferSortOfZ3Expr(ctx, bound);
      if (inferred) return inferred;
    }
    const state = ctx.state.get(expr.name);
    if (state && state.kind === "Const") return state.sort;
    if (state && state.kind === "Relation")
      return uninterp(`Rel_${sortNameOf(state.keySort)}_${sortNameOf(state.valueSort)}`);
    const entity = ctx.entities.get(expr.name);
    if (entity) return entity.sort;
    const alias = ctx.typeAliases.get(expr.name);
    if (alias) return alias.sort;
    const enumDecl = ctx.enums.get(expr.name);
    if (enumDecl) return enumDecl.sort;
    return null;
  }
  if (expr.kind === "Index" && expr.base.kind === "Identifier") {
    const state = ctx.state.get(expr.base.name);
    if (state && state.kind === "Relation") return state.valueSort;
  }
  if (expr.kind === "FieldAccess") {
    const baseSort = inferSort(ctx, expr.base, env, null);
    if (baseSort && baseSort.kind === "Uninterp") {
      const entity = ctx.entities.get(baseSort.name);
      if (entity) {
        const field = entity.fields.get(expr.field);
        if (field) return field.sort;
      }
    }
  }
  if (expr.kind === "IntLit") return Z3_INT;
  if (expr.kind === "BoolLit") return Z3_BOOL;
  if (expr.kind === "StringLit") return uninterp(STRING_SORT_NAME);
  if (expr.kind === "Call" && expr.callee.kind === "Identifier") {
    return callReturnSort(expr.callee.name);
  }
  if (translated && translated.kind === "Var") return translated.sort;
  return null;
}

function inferSortOfZ3Expr(ctx: TranslateCtx, e: Z3Expr): Z3Sort | null {
  if (e.kind === "Var") return e.sort;
  if (e.kind === "IntLit") return Z3_INT;
  if (e.kind === "BoolLit") return Z3_BOOL;
  if (e.kind === "App") {
    const decl = ctx.funcs.get(e.func);
    if (decl) return decl.resultSort;
  }
  return null;
}

