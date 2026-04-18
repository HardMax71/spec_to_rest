import type {
  ServiceIR,
  EntityDecl,
  EnumDecl,
  TypeAliasDecl,
  StateDecl,
  StateFieldDecl,
  InvariantDecl,
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
  readonly state = new Map<string, StateEntry>();
  private matchesCounter = 0;

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

  freshMatchesName(): string {
    const n = this.matchesCounter;
    this.matchesCounter += 1;
    return `matches_${n}`;
  }
}

export function translate(ir: ServiceIR): Z3Script {
  const ctx = new TranslateCtx();

  for (const e of ir.enums) declareEnum(ctx, e);
  for (const t of ir.typeAliases) declareTypeAlias(ctx, t);
  for (const e of ir.entities) declareEntity(ctx, e);
  if (ir.state) declareState(ctx, ir.state);

  for (const t of ir.typeAliases) emitTypeAliasConstraint(ctx, t);
  for (const e of ir.entities) emitEntityAssertions(ctx, e);
  for (const inv of ir.invariants) emitTopLevelInvariant(ctx, inv);

  return {
    sorts: [...ctx.sorts.values()].sort((a, b) => sortKey(a).localeCompare(sortKey(b))),
    funcs: [...ctx.funcs.values()].sort((a, b) => a.name.localeCompare(b.name)),
    assertions: ctx.assertions,
  };
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
  const sort = uninterp(t.name);
  ctx.declareSort(sort);
  ctx.typeAliases.set(t.name, { sort });
}

function emitTypeAliasConstraint(ctx: TranslateCtx, t: TypeAliasDecl): void {
  if (!t.constraint) return;
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
    if (!f.constraint) continue;
    const fieldInfo = info.fields.get(f.name)!;
    const env = new Map<string, Z3Expr>();
    env.set("value", { kind: "App", func: fieldInfo.funcName, args: [selfRef] });
    const body = translateExpr(ctx, f.constraint, env);
    ctx.assertions.push({
      kind: "Quantifier",
      q: "ForAll",
      bindings: [{ name: varName, sort: info.sort }],
      body,
    });
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

function declareState(ctx: TranslateCtx, state: StateDecl): void {
  for (const sf of state.fields) declareStateField(ctx, sf);
  for (const sf of state.fields) emitStateTotality(ctx, sf);
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
  const entity = ctx.entities.get(name);
  if (entity) return entity.sort;
  const enumSort = ctx.enums.get(name);
  if (enumSort) return enumSort.sort;
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
    case "in": {
      const member = asMembership(leftExpr, rightExpr, left, ctx);
      return member ?? appUninterpretedPredicate(ctx, "in", [left, right]);
    }
    case "not_in": {
      const member = asMembership(leftExpr, rightExpr, left, ctx);
      const body = member ?? appUninterpretedPredicate(ctx, "in", [left, right]);
      return { kind: "Not", arg: body };
    }
    case "subset":
    case "union":
    case "intersect":
    case "minus":
      return appUninterpretedPredicate(ctx, op, [left, right]);
    default:
      throw new TranslatorError(`binary op '${op}' is out of M4.1 scope`);
  }
}

function asMembership(
  leftExpr: Expr,
  rightExpr: Expr,
  leftZ: Z3Expr,
  ctx: TranslateCtx,
): Z3Expr | null {
  if (rightExpr.kind !== "Identifier") return null;
  const state = ctx.state.get(rightExpr.name);
  if (!state || state.kind !== "Relation") return null;
  void leftExpr;
  return { kind: "App", func: state.domFunc, args: [leftZ] };
}

function appUninterpretedPredicate(ctx: TranslateCtx, tag: string, args: readonly Z3Expr[]): Z3Expr {
  const name = `${tag}_${args.length}`;
  if (!ctx.funcs.has(name)) {
    const argSorts: Z3Sort[] = args.map(() => uninterp("Any"));
    ctx.declareFunc({ kind: "FuncDecl", name, argSorts, resultSort: Z3_BOOL });
  }
  return { kind: "App", func: name, args };
}

function translateUnaryOp(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "UnaryOp" }>,
  env: TypeEnv,
): Z3Expr {
  const inner = translateExpr(ctx, expr.operand, env);
  switch (expr.op) {
    case "not":
      return { kind: "Not", arg: inner };
    case "negate":
      return { kind: "Arith", op: "-", args: [{ kind: "IntLit", value: 0 }, inner] };
    case "cardinality": {
      const name = `card_${cardTag(expr.operand)}`;
      if (!ctx.funcs.has(name)) {
        ctx.declareFunc({
          kind: "FuncDecl",
          name,
          argSorts: [uninterp("Any")],
          resultSort: Z3_INT,
        });
      }
      return { kind: "App", func: name, args: [inner] };
    }
    case "power": {
      const name = "power_set";
      if (!ctx.funcs.has(name)) {
        ctx.declareFunc({
          kind: "FuncDecl",
          name,
          argSorts: [uninterp("Any")],
          resultSort: uninterp("Any"),
        });
      }
      return { kind: "App", func: name, args: [inner] };
    }
    default:
      throw new TranslatorError(`unary op '${expr.op}' is out of M4.1 scope`);
  }
}

function cardTag(e: Expr): string {
  if (e.kind === "Identifier") return e.name;
  if (e.kind === "Pre" && e.expr.kind === "Identifier") return `pre_${e.expr.name}`;
  return "expr";
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
  return "ForAll";
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
    }
  }
  const funcName = `field_${expr.field}`;
  if (!ctx.funcs.has(funcName)) {
    ctx.declareFunc({
      kind: "FuncDecl",
      name: funcName,
      argSorts: [uninterp("Any")],
      resultSort: uninterp("Any"),
    });
  }
  return { kind: "App", func: funcName, args: [base] };
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
  const base = translateExpr(ctx, expr.base, env);
  const index = translateExpr(ctx, expr.index, env);
  const funcName = "index_2";
  if (!ctx.funcs.has(funcName)) {
    ctx.declareFunc({
      kind: "FuncDecl",
      name: funcName,
      argSorts: [uninterp("Any"), uninterp("Any")],
      resultSort: uninterp("Any"),
    });
  }
  return { kind: "App", func: funcName, args: [base, index] };
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
  const argSort = inferSort(ctx, expr.expr, env, arg) ?? uninterp("Any");
  const funcName = ctx.freshMatchesName();
  ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [argSort], resultSort: Z3_BOOL });
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
  const key = `str_${stableHash(value)}`;
  if (!ctx.funcs.has(key)) {
    ctx.declareFunc({
      kind: "FuncDecl",
      name: key,
      argSorts: [],
      resultSort: uninterp("Str"),
    });
  }
  return { kind: "App", func: key, args: [] };
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

function stableHash(s: string): string {
  let h = 5381;
  for (let i = 0; i < s.length; i += 1) h = ((h << 5) + h + s.charCodeAt(i)) >>> 0;
  return h.toString(16);
}
