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
  readonly domFuncPost: string;
  readonly mapFuncPost: string;
  readonly isTotal: boolean;
}

interface StateConstInfo {
  readonly kind: "Const";
  readonly sort: Z3Sort;
  readonly funcName: string;
  readonly funcNamePost: string;
}

type StateMode = "pre" | "post";

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
  private readonly skolemIds = new Map<string, number>();
  stateMode: StateMode = "pre";

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

  cardinalityNameFor(targetName: string, mode: StateMode = "pre"): string {
    const key = mode === "post" ? `${targetName}__post` : targetName;
    const existing = this.cardinalityNames.get(key);
    if (existing !== undefined) return existing;
    const name = mode === "post" ? `card_${targetName}_post` : `card_${targetName}`;
    this.cardinalityNames.set(key, name);
    return name;
  }

  freshSkolem(prefix: string): string {
    const count = this.skolemIds.get(prefix) ?? 0;
    this.skolemIds.set(prefix, count + 1);
    return `${prefix}_${count}`;
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

export function translateOperationPreservation(
  ir: ServiceIR,
  op: OperationDecl,
  inv: InvariantDecl,
): Z3Script {
  const ctx = new TranslateCtx();
  declareBase(ctx, ir);
  if (ir.state) declareStatePostState(ctx, ir.state);
  const env = declareOperationInputs(ctx, op);
  declareOperationOutputs(ctx, op, env);
  for (const preInv of ir.invariants) {
    ctx.assertions.push(translateExpr(ctx, preInv.expr, env));
  }
  for (const req of op.requires) {
    ctx.assertions.push(translateExpr(ctx, req, env));
  }
  for (const ens of op.ensures) {
    ctx.assertions.push(translateEnsuresClause(ctx, ens, env));
  }
  synthesizeFrame(ctx, ir.state, op, env);
  synthesizeCardinalityAxioms(ctx, ir.state, op);
  const postInv = withStateMode(ctx, "post", () => translateExpr(ctx, inv.expr, env));
  ctx.assertions.push({ kind: "Not", arg: postInv });
  return finalizeScript(ctx);
}

function declareOperationOutputs(
  ctx: TranslateCtx,
  op: OperationDecl,
  env: Map<string, Z3Expr>,
): void {
  for (const out of op.outputs) {
    const sort = sortForType(ctx, out.typeExpr);
    const funcName = `output_${op.name}_${out.name}`;
    if (!ctx.funcs.has(funcName)) {
      ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: sort });
    }
    env.set(out.name, { kind: "App", func: funcName, args: [] });
    if (out.typeExpr.kind === "NamedType") {
      const alias = ctx.primitiveAliases.get(out.typeExpr.name);
      if (alias) {
        const refineEnv = new Map<string, Z3Expr>();
        refineEnv.set("value", { kind: "App", func: funcName, args: [] });
        ctx.assertions.push(translateExpr(ctx, alias.constraint, refineEnv));
      }
    }
  }
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
  for (const sf of state.fields) emitStateTotality(ctx, sf, "pre");
  for (const sf of state.fields) emitStateRefinement(ctx, sf, "pre");
}

function declareStatePostState(ctx: TranslateCtx, state: StateDecl): void {
  for (const sf of state.fields) declareStatePostFunc(ctx, sf);
  for (const sf of state.fields) emitStateTotality(ctx, sf, "post");
  for (const sf of state.fields) emitStateRefinement(ctx, sf, "post");
}

function declareStatePostFunc(ctx: TranslateCtx, sf: StateFieldDecl): void {
  const info = ctx.state.get(sf.name);
  if (!info) return;
  if (info.kind === "Relation") {
    ctx.declareFunc({
      kind: "FuncDecl",
      name: info.domFuncPost,
      argSorts: [info.keySort],
      resultSort: Z3_BOOL,
    });
    ctx.declareFunc({
      kind: "FuncDecl",
      name: info.mapFuncPost,
      argSorts: [info.keySort],
      resultSort: info.valueSort,
    });
  } else {
    ctx.declareFunc({
      kind: "FuncDecl",
      name: info.funcNamePost,
      argSorts: [],
      resultSort: info.sort,
    });
  }
}

function emitStateRefinement(ctx: TranslateCtx, sf: StateFieldDecl, mode: StateMode): void {
  const info = ctx.state.get(sf.name);
  if (!info) return;
  if (info.kind === "Const") {
    const aliasConstraint = refinementConstraintFor(ctx, sf.typeExpr);
    if (!aliasConstraint) return;
    const env = new Map<string, Z3Expr>();
    env.set("value", { kind: "App", func: constFuncFor(info, mode), args: [] });
    ctx.assertions.push(translateExpr(ctx, aliasConstraint, env));
    return;
  }
  if (sf.typeExpr.kind !== "RelationType" && sf.typeExpr.kind !== "MapType") return;
  const keyType = sf.typeExpr.kind === "RelationType" ? sf.typeExpr.fromType : sf.typeExpr.keyType;
  const valueType = sf.typeExpr.kind === "RelationType" ? sf.typeExpr.toType : sf.typeExpr.valueType;
  const keyConstraint = refinementConstraintFor(ctx, keyType);
  const valueConstraint = refinementConstraintFor(ctx, valueType);
  if (keyConstraint) emitRelationKeyRefinement(ctx, info, sf.name, keyConstraint, mode);
  if (valueConstraint) emitRelationValueRefinement(ctx, info, sf.name, valueConstraint, mode);
}

function emitRelationKeyRefinement(
  ctx: TranslateCtx,
  info: StateInfo,
  fieldName: string,
  keyConstraint: Expr,
  mode: StateMode,
): void {
  const suffix = mode === "post" ? "_post" : "";
  const varName = `k_${fieldName}_key${suffix}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: info.keySort };
  const env = new Map<string, Z3Expr>();
  env.set("value", keyVar);
  const pred = translateExpr(ctx, keyConstraint, env);
  ctx.assertions.push({
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: info.keySort }],
    body: {
      kind: "Implies",
      lhs: { kind: "App", func: domFuncFor(info, mode), args: [keyVar] },
      rhs: pred,
    },
  });
}

function emitRelationValueRefinement(
  ctx: TranslateCtx,
  info: StateInfo,
  fieldName: string,
  valueConstraint: Expr,
  mode: StateMode,
): void {
  const suffix = mode === "post" ? "_post" : "";
  const varName = `k_${fieldName}${suffix}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: info.keySort };
  const env = new Map<string, Z3Expr>();
  env.set("value", { kind: "App", func: mapFuncFor(info, mode), args: [keyVar] });
  const body = translateExpr(ctx, valueConstraint, env);
  const guarded: Z3Expr = info.isTotal
    ? body
    : {
        kind: "Implies",
        lhs: { kind: "App", func: domFuncFor(info, mode), args: [keyVar] },
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
      domFuncPost: `${sf.name}_dom_post`,
      mapFuncPost: `${sf.name}_map_post`,
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
      domFuncPost: `${sf.name}_dom_post`,
      mapFuncPost: `${sf.name}_map_post`,
      isTotal: false,
    });
  } else {
    const fieldSort = sortForType(ctx, sf.typeExpr);
    const funcName = `state_${sf.name}`;
    ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: fieldSort });
    ctx.state.set(sf.name, {
      kind: "Const",
      sort: fieldSort,
      funcName,
      funcNamePost: `${funcName}_post`,
    });
  }
}

function domFuncFor(info: StateInfo, mode: StateMode): string {
  return mode === "post" ? info.domFuncPost : info.domFunc;
}

function mapFuncFor(info: StateInfo, mode: StateMode): string {
  return mode === "post" ? info.mapFuncPost : info.mapFunc;
}

function constFuncFor(info: StateConstInfo, mode: StateMode): string {
  return mode === "post" ? info.funcNamePost : info.funcName;
}

function emitStateTotality(ctx: TranslateCtx, sf: StateFieldDecl, mode: StateMode): void {
  const info = ctx.state.get(sf.name);
  if (!info || info.kind !== "Relation" || !info.isTotal) return;
  const suffix = mode === "post" ? "_post" : "";
  const varName = `k_${sf.name}${suffix}`;
  const body: Z3Expr = {
    kind: "App",
    func: domFuncFor(info, mode),
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
    case "Prime":
      return withStateMode(ctx, "post", () => translateExpr(ctx, expr.expr, env));
    case "Pre":
      return withStateMode(ctx, "pre", () => translateExpr(ctx, expr.expr, env));
    case "With":
      return translateWith(ctx, expr, env);
    case "SetComprehension":
      return translateSetComprehension(ctx, expr);
    case "Let":
      return translateLet(ctx, expr, env);
    default:
      throw new TranslatorError(
        `expression kind '${expr.kind}' is not yet supported by the verifier`,
      );
  }
}

function withStateMode<T>(ctx: TranslateCtx, mode: StateMode, fn: () => T): T {
  const saved = ctx.stateMode;
  ctx.stateMode = mode;
  try {
    return fn();
  } finally {
    ctx.stateMode = saved;
  }
}

function translateBinaryOp(
  ctx: TranslateCtx,
  op: BinaryOp,
  leftExpr: Expr,
  rightExpr: Expr,
  env: TypeEnv,
): Z3Expr {
  if (op === "=" || op === "!=") {
    const domEq = tryLowerDomEquality(ctx, leftExpr, rightExpr, op === "!=");
    if (domEq) return domEq;
  }
  if (op === "in" || op === "not_in") {
    const left = translateExpr(ctx, leftExpr, env);
    const mem = membership(leftExpr, rightExpr, left, ctx, op, env);
    return op === "in" ? mem : { kind: "Not", arg: mem };
  }
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
    case "/": {
      const leftSort = inferSort(ctx, leftExpr, env, left);
      const rightSort = inferSort(ctx, rightExpr, env, right);
      if ((leftSort && leftSort.kind !== "Int") || (rightSort && rightSort.kind !== "Int")) {
        throw new TranslatorError(
          `arithmetic operator '${op}' is only supported on integers (deferred for string/set arithmetic)`,
        );
      }
      return { kind: "Arith", op: op as ArithOp, args: [left, right] };
    }
    case "subset":
    case "union":
    case "intersect":
    case "minus":
      throw new TranslatorError(
        `set operator '${op}' outside a primed-state equality needs a set-theoretic backend (tracked in #73)`,
      );
    default:
      throw new TranslatorError(`binary op '${op}' is not supported by the verifier`);
  }
}

function membership(
  leftExpr: Expr,
  rightExpr: Expr,
  leftZ: Z3Expr,
  ctx: TranslateCtx,
  op: "in" | "not_in",
  env: TypeEnv,
): Z3Expr {
  void leftExpr;
  const resolved = resolveStateRelationReference(ctx, rightExpr);
  if (resolved) {
    return { kind: "App", func: domFuncFor(resolved.info, resolved.mode), args: [leftZ] };
  }
  if (rightExpr.kind === "SetComprehension") {
    return membershipInComprehension(ctx, leftZ, rightExpr, env);
  }
  throw new TranslatorError(
    `membership operator '${op}' is only supported against a state relation or set comprehension (deferred for general sets — see issue #73)`,
  );
}

function membershipInComprehension(
  ctx: TranslateCtx,
  leftZ: Z3Expr,
  sc: Extract<Expr, { kind: "SetComprehension" }>,
  env: TypeEnv,
): Z3Expr {
  const resolved = resolveBindingDomain(ctx, {
    variable: sc.variable,
    domain: sc.domain,
    bindingKind: "in",
  });
  const subEnv = new Map(env);
  subEnv.set(sc.variable, leftZ);
  const predicate = translateExpr(ctx, sc.predicate, subEnv);
  const guard = resolved.guard ? resolved.guard(sc.variable) : null;
  if (!guard) return predicate;
  const guardSubst = substituteVar(guard, sc.variable, leftZ);
  return { kind: "And", args: [guardSubst, predicate] };
}

function substituteVar(expr: Z3Expr, varName: string, replacement: Z3Expr): Z3Expr {
  switch (expr.kind) {
    case "Var":
      return expr.name === varName ? replacement : expr;
    case "App":
      return { kind: "App", func: expr.func, args: expr.args.map((a) => substituteVar(a, varName, replacement)) };
    case "And":
    case "Or":
      return { kind: expr.kind, args: expr.args.map((a) => substituteVar(a, varName, replacement)) };
    case "Not":
      return { kind: "Not", arg: substituteVar(expr.arg, varName, replacement) };
    case "Implies":
      return {
        kind: "Implies",
        lhs: substituteVar(expr.lhs, varName, replacement),
        rhs: substituteVar(expr.rhs, varName, replacement),
      };
    case "Cmp":
      return {
        kind: "Cmp",
        op: expr.op,
        lhs: substituteVar(expr.lhs, varName, replacement),
        rhs: substituteVar(expr.rhs, varName, replacement),
      };
    case "Arith":
      return { kind: "Arith", op: expr.op, args: expr.args.map((a) => substituteVar(a, varName, replacement)) };
    case "Quantifier": {
      if (expr.bindings.some((b) => b.name === varName)) return expr;
      return {
        kind: "Quantifier",
        q: expr.q,
        bindings: expr.bindings,
        body: substituteVar(expr.body, varName, replacement),
      };
    }
    default:
      return expr;
  }
}

function resolveStateRelationReference(
  ctx: TranslateCtx,
  expr: Expr,
): { info: StateInfo; mode: StateMode } | null {
  if (expr.kind === "Identifier") {
    const info = ctx.state.get(expr.name);
    if (info && info.kind === "Relation") return { info, mode: ctx.stateMode };
    return null;
  }
  if (expr.kind === "Prime") {
    const inner = resolveStateRelationReference(ctx, expr.expr);
    return inner ? { info: inner.info, mode: "post" } : null;
  }
  if (expr.kind === "Pre") {
    const inner = resolveStateRelationReference(ctx, expr.expr);
    return inner ? { info: inner.info, mode: "pre" } : null;
  }
  return null;
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
        `powerset operator needs a set-theoretic backend (tracked in #73)`,
      );
    default:
      throw new TranslatorError(`unary op '${expr.op}' is not supported by the verifier`);
  }
}

function translateCardinality(ctx: TranslateCtx, operand: Expr): Z3Expr {
  if (operand.kind === "Prime" && operand.expr.kind === "Identifier") {
    return cardinalityRefFor(ctx, operand.expr.name, "post");
  }
  if (operand.kind === "Pre" && operand.expr.kind === "Identifier") {
    return cardinalityRefFor(ctx, operand.expr.name, "pre");
  }
  if (operand.kind !== "Identifier") {
    throw new TranslatorError(
      "cardinality '#expr' is only supported on state-relation identifiers (deferred for general set expressions — see issue #73)",
    );
  }
  return cardinalityRefFor(ctx, operand.name, ctx.stateMode);
}

function cardinalityRefFor(ctx: TranslateCtx, targetName: string, mode: StateMode): Z3Expr {
  const state = ctx.state.get(targetName);
  if (!state || state.kind !== "Relation") {
    throw new TranslatorError(
      `cardinality '#${targetName}' requires a state relation; '${targetName}' is not declared as one`,
    );
  }
  const funcName = ctx.cardinalityNameFor(targetName, mode);
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
      const mode = ctx.stateMode;
      return {
        sort: state.keySort,
        guard: (vn) => ({
          kind: "App",
          func: domFuncFor(state, mode),
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
    `field access '.${expr.field}' requires an entity-typed base; inferred sort is not an entity`,
  );
}

function translateIndex(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "Index" }>,
  env: TypeEnv,
): Z3Expr {
  const resolved = resolveStateRelationReference(ctx, expr.base);
  if (resolved) {
    const key = translateExpr(ctx, expr.index, env);
    return { kind: "App", func: mapFuncFor(resolved.info, resolved.mode), args: [key] };
  }
  throw new TranslatorError(
    "indexing is only supported on state-relation references (including primed/pre-state forms); general map/sequence indexing needs a set-theoretic backend (tracked in #73)",
  );
}

function translateCall(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "Call" }>,
  env: TypeEnv,
): Z3Expr {
  if (expr.callee.kind !== "Identifier") {
    throw new TranslatorError("higher-order call (non-identifier callee) is not supported by the verifier");
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

function translateLet(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "Let" }>,
  env: TypeEnv,
): Z3Expr {
  const value = translateExpr(ctx, expr.value, env);
  const newEnv = new Map(env);
  newEnv.set(expr.variable, value);
  return translateExpr(ctx, expr.body, newEnv);
}

function translateWith(
  ctx: TranslateCtx,
  expr: Extract<Expr, { kind: "With" }>,
  env: TypeEnv,
): Z3Expr {
  const baseSort = inferSort(ctx, expr.base, env, null);
  if (!baseSort || baseSort.kind !== "Uninterp") {
    throw new TranslatorError("'with' expression requires a known entity sort");
  }
  const entity = ctx.entities.get(baseSort.name);
  if (!entity) {
    throw new TranslatorError(
      `'with' expression requires an entity sort; '${baseSort.name}' is not an entity`,
    );
  }
  const baseZ = translateExpr(ctx, expr.base, env);
  const skolemName = ctx.freshSkolem(`with_${baseSort.name}`);
  ctx.declareFunc({
    kind: "FuncDecl",
    name: skolemName,
    argSorts: [],
    resultSort: baseSort,
  });
  const skolemRef: Z3Expr = { kind: "App", func: skolemName, args: [] };
  const updatedNames = new Set(expr.updates.map((u) => u.name));
  for (const [fname, finfo] of entity.fields) {
    if (updatedNames.has(fname)) continue;
    ctx.assertions.push({
      kind: "Cmp",
      op: "=",
      lhs: { kind: "App", func: finfo.funcName, args: [skolemRef] },
      rhs: { kind: "App", func: finfo.funcName, args: [baseZ] },
    });
  }
  for (const update of expr.updates) {
    const finfo = entity.fields.get(update.name);
    if (!finfo) {
      throw new TranslatorError(
        `entity '${baseSort.name}' has no field '${update.name}'`,
      );
    }
    const value = translateExpr(ctx, update.value, env);
    ctx.assertions.push({
      kind: "Cmp",
      op: "=",
      lhs: { kind: "App", func: finfo.funcName, args: [skolemRef] },
      rhs: value,
    });
  }
  return skolemRef;
}

function translateSetComprehension(
  _ctx: TranslateCtx,
  _expr: Extract<Expr, { kind: "SetComprehension" }>,
): Z3Expr {
  throw new TranslatorError(
    "standalone set comprehensions require a set-theoretic backend not yet wired up (see issue #73); supported inline in membership-context: `y in {x in S | P}`",
  );
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
      return { kind: "App", func: constFuncFor(state, ctx.stateMode), args: [] };
    }
    const suffix = ctx.stateMode === "post" ? "_post" : "";
    const refName = `state_${name}_ref${suffix}`;
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
  if (expr.kind === "Index") {
    const resolved = resolveStateRelationReference(ctx, expr.base);
    if (resolved) return resolved.info.valueSort;
  }
  if (expr.kind === "Prime" || expr.kind === "Pre") {
    return inferSort(ctx, expr.expr, env, translated);
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

function tryLowerDomEquality(
  ctx: TranslateCtx,
  leftExpr: Expr,
  rightExpr: Expr,
  negate: boolean,
): Z3Expr | null {
  const leftDom = asDomOfStateRelation(ctx, leftExpr);
  const rightDom = asDomOfStateRelation(ctx, rightExpr);
  if (!leftDom || !rightDom) return null;
  const varName = `k_domeq_${leftDom.info.domFunc}_${rightDom.info.domFunc}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: leftDom.info.keySort };
  const lhsMem: Z3Expr = { kind: "App", func: domFuncFor(leftDom.info, leftDom.mode), args: [keyVar] };
  const rhsMem: Z3Expr = {
    kind: "App",
    func: domFuncFor(rightDom.info, rightDom.mode),
    args: [keyVar],
  };
  const body: Z3Expr = {
    kind: "And",
    args: [
      { kind: "Implies", lhs: lhsMem, rhs: rhsMem },
      { kind: "Implies", lhs: rhsMem, rhs: lhsMem },
    ],
  };
  const forall: Z3Expr = {
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: leftDom.info.keySort }],
    body,
  };
  return negate ? { kind: "Not", arg: forall } : forall;
}

function asDomOfStateRelation(
  ctx: TranslateCtx,
  expr: Expr,
): { info: StateInfo; mode: StateMode } | null {
  if (expr.kind !== "Call" || expr.callee.kind !== "Identifier") return null;
  if (expr.callee.name !== "dom") return null;
  if (expr.args.length !== 1) return null;
  return resolveStateRelationReference(ctx, expr.args[0]);
}

function translateEnsuresClause(ctx: TranslateCtx, expr: Expr, env: TypeEnv): Z3Expr {
  if (expr.kind === "BinaryOp" && expr.op === "=") {
    const lowered = tryLowerRelationEquality(ctx, expr.left, expr.right, env);
    if (lowered) return lowered;
  }
  return translateExpr(ctx, expr, env);
}

function tryLowerRelationEquality(
  ctx: TranslateCtx,
  leftExpr: Expr,
  rightExpr: Expr,
  env: TypeEnv,
): Z3Expr | null {
  const leftRel = resolveStateRelationReference(ctx, leftExpr);
  if (!leftRel) return null;
  const rightRel = resolveStateRelationReference(ctx, rightExpr);
  if (rightRel) {
    return relationEqualityAxiom(leftRel.info, leftRel.mode, rightRel.info, rightRel.mode);
  }
  const lowered = lowerRelationRhs(ctx, rightExpr, leftRel.info, env);
  if (lowered) return lowered(leftRel.info, leftRel.mode);
  return null;
}

type RelationRhsLowering = (targetInfo: StateInfo, targetMode: StateMode) => Z3Expr;

function lowerRelationRhs(
  ctx: TranslateCtx,
  expr: Expr,
  targetInfo: StateInfo,
  env: TypeEnv,
): RelationRhsLowering | null {
  const insertLowering = tryLowerSingleInsertRhs(ctx, expr, targetInfo, env);
  if (insertLowering) return insertLowering;
  const minusLowering = tryLowerSingleMinusRhs(ctx, expr, targetInfo, env);
  if (minusLowering) return minusLowering;
  return null;
}

function tryLowerSingleInsertRhs(
  ctx: TranslateCtx,
  expr: Expr,
  targetInfo: StateInfo,
  env: TypeEnv,
): RelationRhsLowering | null {
  if (expr.kind !== "BinaryOp" || (expr.op !== "+" && expr.op !== "union")) return null;
  const base = resolveStateRelationReference(ctx, expr.left);
  if (!base) return null;
  const entries = extractMapEntries(expr.right);
  if (!entries || entries.length === 0) return null;
  return (lhsInfo, lhsMode) =>
    relationInsertionAxiom(
      ctx,
      lhsInfo,
      lhsMode,
      base.info,
      base.mode,
      entries,
      env,
      targetInfo,
    );
}

function tryLowerSingleMinusRhs(
  ctx: TranslateCtx,
  expr: Expr,
  targetInfo: StateInfo,
  env: TypeEnv,
): RelationRhsLowering | null {
  if (expr.kind !== "BinaryOp" || (expr.op !== "-" && expr.op !== "minus")) return null;
  const base = resolveStateRelationReference(ctx, expr.left);
  if (!base) return null;
  const keys = extractKeySet(expr.right);
  if (!keys || keys.length === 0) return null;
  return (lhsInfo, lhsMode) =>
    relationDeletionAxiom(ctx, lhsInfo, lhsMode, base.info, base.mode, keys, env, targetInfo);
}

interface KeyValueEntry {
  readonly key: Expr;
  readonly value: Expr;
}

function extractMapEntries(expr: Expr): readonly KeyValueEntry[] | null {
  if (expr.kind !== "MapLiteral") return null;
  return expr.entries.map((e) => ({ key: e.key, value: e.value }));
}

function extractKeySet(expr: Expr): readonly Expr[] | null {
  if (expr.kind === "SetLiteral") return expr.elements;
  if (expr.kind === "MapLiteral") return expr.entries.map((e) => e.key);
  return null;
}

function relationEqualityAxiom(
  a: StateInfo,
  aMode: StateMode,
  b: StateInfo,
  bMode: StateMode,
): Z3Expr {
  const varName = `k_releq_${a.domFunc}_${b.domFunc}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: a.keySort };
  const aDom: Z3Expr = { kind: "App", func: domFuncFor(a, aMode), args: [keyVar] };
  const bDom: Z3Expr = { kind: "App", func: domFuncFor(b, bMode), args: [keyVar] };
  const aMap: Z3Expr = { kind: "App", func: mapFuncFor(a, aMode), args: [keyVar] };
  const bMap: Z3Expr = { kind: "App", func: mapFuncFor(b, bMode), args: [keyVar] };
  const body: Z3Expr = {
    kind: "And",
    args: [
      {
        kind: "And",
        args: [
          { kind: "Implies", lhs: aDom, rhs: bDom },
          { kind: "Implies", lhs: bDom, rhs: aDom },
        ],
      },
      { kind: "Implies", lhs: aDom, rhs: { kind: "Cmp", op: "=", lhs: aMap, rhs: bMap } },
    ],
  };
  return {
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: a.keySort }],
    body,
  };
}

function relationInsertionAxiom(
  ctx: TranslateCtx,
  lhs: StateInfo,
  lhsMode: StateMode,
  base: StateInfo,
  baseMode: StateMode,
  entries: readonly KeyValueEntry[],
  env: TypeEnv,
  targetInfo: StateInfo,
): Z3Expr {
  void targetInfo;
  const varName = `k_insert_${lhs.domFunc}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: lhs.keySort };
  const translatedEntries = entries.map((e) => ({
    key: translateExpr(ctx, e.key, env),
    value: translateExpr(ctx, e.value, env),
  }));
  const keyEqs: Z3Expr[] = translatedEntries.map((e) => ({
    kind: "Cmp",
    op: "=",
    lhs: keyVar,
    rhs: e.key,
  }));
  const anyKeyEq: Z3Expr = keyEqs.length === 1 ? keyEqs[0] : { kind: "Or", args: keyEqs };
  const lhsDom: Z3Expr = { kind: "App", func: domFuncFor(lhs, lhsMode), args: [keyVar] };
  const baseDom: Z3Expr = { kind: "App", func: domFuncFor(base, baseMode), args: [keyVar] };
  const domBody: Z3Expr = {
    kind: "And",
    args: [
      { kind: "Implies", lhs: lhsDom, rhs: { kind: "Or", args: [baseDom, anyKeyEq] } },
      { kind: "Implies", lhs: { kind: "Or", args: [baseDom, anyKeyEq] }, rhs: lhsDom },
    ],
  };
  const lhsMap: Z3Expr = { kind: "App", func: mapFuncFor(lhs, lhsMode), args: [keyVar] };
  const baseMap: Z3Expr = { kind: "App", func: mapFuncFor(base, baseMode), args: [keyVar] };
  const perEntryMapAxioms: Z3Expr[] = translatedEntries.map((e) => ({
    kind: "Implies",
    lhs: { kind: "Cmp", op: "=", lhs: keyVar, rhs: e.key },
    rhs: { kind: "Cmp", op: "=", lhs: lhsMap, rhs: e.value },
  }));
  const fallthrough: Z3Expr = {
    kind: "Implies",
    lhs: {
      kind: "And",
      args: [{ kind: "Not", arg: anyKeyEq }, baseDom],
    },
    rhs: { kind: "Cmp", op: "=", lhs: lhsMap, rhs: baseMap },
  };
  const mapBody: Z3Expr = { kind: "And", args: [...perEntryMapAxioms, fallthrough] };
  return {
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: lhs.keySort }],
    body: { kind: "And", args: [domBody, mapBody] },
  };
}

function relationDeletionAxiom(
  ctx: TranslateCtx,
  lhs: StateInfo,
  lhsMode: StateMode,
  base: StateInfo,
  baseMode: StateMode,
  keyExprs: readonly Expr[],
  env: TypeEnv,
  targetInfo: StateInfo,
): Z3Expr {
  void targetInfo;
  const varName = `k_delete_${lhs.domFunc}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: lhs.keySort };
  const keys = keyExprs.map((k) => translateExpr(ctx, k, env));
  const keyEqs: Z3Expr[] = keys.map((k) => ({
    kind: "Cmp",
    op: "=",
    lhs: keyVar,
    rhs: k,
  }));
  const anyKeyEq: Z3Expr = keyEqs.length === 1 ? keyEqs[0] : { kind: "Or", args: keyEqs };
  const notAnyKey: Z3Expr = { kind: "Not", arg: anyKeyEq };
  const lhsDom: Z3Expr = { kind: "App", func: domFuncFor(lhs, lhsMode), args: [keyVar] };
  const baseDom: Z3Expr = { kind: "App", func: domFuncFor(base, baseMode), args: [keyVar] };
  const domBody: Z3Expr = {
    kind: "And",
    args: [
      { kind: "Implies", lhs: lhsDom, rhs: { kind: "And", args: [baseDom, notAnyKey] } },
      { kind: "Implies", lhs: { kind: "And", args: [baseDom, notAnyKey] }, rhs: lhsDom },
    ],
  };
  const lhsMap: Z3Expr = { kind: "App", func: mapFuncFor(lhs, lhsMode), args: [keyVar] };
  const baseMap: Z3Expr = { kind: "App", func: mapFuncFor(base, baseMode), args: [keyVar] };
  const mapBody: Z3Expr = {
    kind: "Implies",
    lhs: { kind: "And", args: [baseDom, notAnyKey] },
    rhs: { kind: "Cmp", op: "=", lhs: lhsMap, rhs: baseMap },
  };
  return {
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: lhs.keySort }],
    body: { kind: "And", args: [domBody, mapBody] },
  };
}

function synthesizeFrame(
  ctx: TranslateCtx,
  state: StateDecl | null,
  op: OperationDecl,
  env: TypeEnv,
): void {
  if (!state) return;
  for (const sf of state.fields) {
    const info = ctx.state.get(sf.name);
    if (!info) continue;
    const analysis = analyzeStateMention(op.ensures, sf.name);
    if (analysis.fullyReplaced) continue;
    if (info.kind === "Const") {
      if (!analysis.touched) {
        ctx.assertions.push({
          kind: "Cmp",
          op: "=",
          lhs: { kind: "App", func: info.funcNamePost, args: [] },
          rhs: { kind: "App", func: info.funcName, args: [] },
        });
      }
      continue;
    }
    if (!analysis.touched) {
      ctx.assertions.push(relationEqualityAxiom(info, "post", info, "pre"));
      syncCardinalityFrameIfDeclared(ctx, sf.name);
      continue;
    }
    emitPartialRelationFrame(ctx, info, sf.name, analysis, env);
  }
}

function syncCardinalityFrameIfDeclared(ctx: TranslateCtx, stateName: string): void {
  const preCard = ctx.cardinalityNameFor(stateName, "pre");
  const postCard = ctx.cardinalityNameFor(stateName, "post");
  if (!ctx.funcs.has(preCard) && !ctx.funcs.has(postCard)) return;
  ensureCardinalityDecl(ctx, preCard);
  ensureCardinalityDecl(ctx, postCard);
  ctx.assertions.push({
    kind: "Cmp",
    op: "=",
    lhs: { kind: "App", func: postCard, args: [] },
    rhs: { kind: "App", func: preCard, args: [] },
  });
}

interface StateMentionAnalysis {
  readonly touched: boolean;
  readonly fullyReplaced: boolean;
  readonly removedKeys: readonly Expr[];
  readonly fieldUpdatedKeys: readonly { readonly key: Expr; readonly fields: ReadonlySet<string> }[];
  readonly hasUnclassifiedMention: boolean;
}

function analyzeStateMention(
  ensures: readonly Expr[],
  stateName: string,
): StateMentionAnalysis {
  let touched = false;
  let fullyReplaced = false;
  let hasUnclassifiedMention = false;
  const removedKeys: Expr[] = [];
  const fieldUpdatedKeys = new Map<string, { key: Expr; fields: Set<string> }>();

  for (const ens of ensures) {
    const mentionsPost = exprMentionsPostState(ens, stateName);
    if (!mentionsPost) continue;
    touched = true;
    if (matchesFullReplacement(ens, stateName)) {
      fullyReplaced = true;
      continue;
    }
    const notIn = matchNotInPrimed(ens, stateName);
    if (notIn) {
      removedKeys.push(notIn);
      continue;
    }
    const fieldUpdate = matchFieldUpdatePrimed(ens, stateName);
    if (fieldUpdate) {
      const keyJson = JSON.stringify(stripSpan(fieldUpdate.key));
      const bucket = fieldUpdatedKeys.get(keyJson);
      if (bucket) bucket.fields.add(fieldUpdate.field);
      else fieldUpdatedKeys.set(keyJson, { key: fieldUpdate.key, fields: new Set([fieldUpdate.field]) });
      continue;
    }
    if (matchesCardinalityConstraint(ens, stateName)) continue;
    hasUnclassifiedMention = true;
  }

  return {
    touched,
    fullyReplaced,
    removedKeys,
    fieldUpdatedKeys: [...fieldUpdatedKeys.values()],
    hasUnclassifiedMention,
  };
}

function matchesFullReplacement(expr: Expr, stateName: string): boolean {
  if (expr.kind !== "BinaryOp" || expr.op !== "=") return false;
  return referencesPrimedRelation(expr.left, stateName) || referencesPrimedRelation(expr.right, stateName);
}

function matchNotInPrimed(expr: Expr, stateName: string): Expr | null {
  if (expr.kind !== "BinaryOp" || expr.op !== "not_in") return null;
  if (!referencesPrimedRelation(expr.right, stateName)) return null;
  return expr.left;
}

interface FieldUpdateMatch {
  readonly key: Expr;
  readonly field: string;
}

function matchFieldUpdatePrimed(expr: Expr, stateName: string): FieldUpdateMatch | null {
  if (expr.kind !== "BinaryOp" || expr.op !== "=") return null;
  return matchFieldUpdateSide(expr.left, stateName) ?? matchFieldUpdateSide(expr.right, stateName);
}

function matchFieldUpdateSide(side: Expr, stateName: string): FieldUpdateMatch | null {
  if (side.kind !== "FieldAccess") return null;
  if (side.base.kind !== "Index") return null;
  if (!referencesPrimedRelation(side.base.base, stateName)) return null;
  return { key: side.base.index, field: side.field };
}

function matchesCardinalityConstraint(expr: Expr, stateName: string): boolean {
  if (expr.kind !== "BinaryOp") return false;
  return sideIsPrimeCardinality(expr.left, stateName) || sideIsPrimeCardinality(expr.right, stateName);
}

function sideIsPrimeCardinality(side: Expr, stateName: string): boolean {
  if (side.kind !== "UnaryOp" || side.op !== "cardinality") return false;
  const operand = side.operand;
  if (operand.kind !== "Prime") return false;
  return operand.expr.kind === "Identifier" && operand.expr.name === stateName;
}

function stripSpan(expr: Expr): Expr {
  return expr;
}

function emitPartialRelationFrame(
  ctx: TranslateCtx,
  info: StateInfo,
  stateName: string,
  analysis: StateMentionAnalysis,
  env: TypeEnv,
): void {
  if (analysis.hasUnclassifiedMention) return;
  const varName = `k_pf_${stateName}`;
  const keyVar: Z3Expr = { kind: "Var", name: varName, sort: info.keySort };
  const domPre: Z3Expr = { kind: "App", func: info.domFunc, args: [keyVar] };
  const domPost: Z3Expr = { kind: "App", func: info.domFuncPost, args: [keyVar] };
  const mapPre: Z3Expr = { kind: "App", func: info.mapFunc, args: [keyVar] };
  const mapPost: Z3Expr = { kind: "App", func: info.mapFuncPost, args: [keyVar] };
  const removedKeyExprs = analysis.removedKeys.map((k) => translateExpr(ctx, k, env));
  const fieldUpdateKeyExprs = analysis.fieldUpdatedKeys.map((fu) => ({
    key: translateExpr(ctx, fu.key, env),
    fields: fu.fields,
  }));
  const isRemoved = anyEqual(keyVar, removedKeyExprs);
  const isFieldUpdated = anyEqual(
    keyVar,
    fieldUpdateKeyExprs.map((fu) => fu.key),
  );
  const domClause = buildDomClause(domPre, domPost, isRemoved, isFieldUpdated);
  ctx.assertions.push({
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: info.keySort }],
    body: domClause,
  });
  const mapClause = buildMapClause(domPre, mapPre, mapPost, isRemoved, isFieldUpdated);
  ctx.assertions.push({
    kind: "Quantifier",
    q: "ForAll",
    bindings: [{ name: varName, sort: info.keySort }],
    body: mapClause,
  });
  emitUnmentionedFieldFrames(ctx, info, stateName, fieldUpdateKeyExprs);
  if (analysis.removedKeys.length > 0 && analysis.fieldUpdatedKeys.length === 0) {
    const preCardName = ctx.cardinalityNameFor(stateName, "pre");
    const postCardName = ctx.cardinalityNameFor(stateName, "post");
    if (ctx.funcs.has(preCardName) || ctx.funcs.has(postCardName)) {
      ensureCardinalityDecl(ctx, preCardName);
      ensureCardinalityDecl(ctx, postCardName);
      ctx.assertions.push({
        kind: "Cmp",
        op: "=",
        lhs: { kind: "App", func: postCardName, args: [] },
        rhs: {
          kind: "Arith",
          op: "-",
          args: [
            { kind: "App", func: preCardName, args: [] },
            { kind: "IntLit", value: analysis.removedKeys.length },
          ],
        },
      });
    }
  } else if (analysis.removedKeys.length === 0 && analysis.fieldUpdatedKeys.length > 0) {
    syncCardinalityFrameIfDeclared(ctx, stateName);
  }
}

function anyEqual(keyVar: Z3Expr, candidates: readonly Z3Expr[]): Z3Expr | null {
  if (candidates.length === 0) return null;
  const eqs: Z3Expr[] = candidates.map((c) => ({
    kind: "Cmp",
    op: "=",
    lhs: keyVar,
    rhs: c,
  }));
  return eqs.length === 1 ? eqs[0] : { kind: "Or", args: eqs };
}

function buildDomClause(
  domPre: Z3Expr,
  domPost: Z3Expr,
  isRemoved: Z3Expr | null,
  isFieldUpdated: Z3Expr | null,
): Z3Expr {
  const pieces: Z3Expr[] = [];
  if (isFieldUpdated) pieces.push({ kind: "Implies", lhs: isFieldUpdated, rhs: domPost });
  if (isRemoved) {
    const notRemoved: Z3Expr = { kind: "Not", arg: isRemoved };
    pieces.push({
      kind: "Implies",
      lhs: { kind: "And", args: [notRemoved, domPre] },
      rhs: domPost,
    });
    pieces.push({
      kind: "Implies",
      lhs: { kind: "And", args: [notRemoved, { kind: "Not", arg: domPre }] },
      rhs: { kind: "Not", arg: domPost },
    });
    pieces.push({ kind: "Implies", lhs: isRemoved, rhs: { kind: "Not", arg: domPost } });
  } else {
    pieces.push({ kind: "Implies", lhs: domPre, rhs: domPost });
    pieces.push({ kind: "Implies", lhs: domPost, rhs: domPre });
  }
  return pieces.length === 1 ? pieces[0] : { kind: "And", args: pieces };
}

function buildMapClause(
  domPre: Z3Expr,
  mapPre: Z3Expr,
  mapPost: Z3Expr,
  isRemoved: Z3Expr | null,
  isFieldUpdated: Z3Expr | null,
): Z3Expr {
  const guardParts: Z3Expr[] = [domPre];
  if (isRemoved) guardParts.push({ kind: "Not", arg: isRemoved });
  if (isFieldUpdated) guardParts.push({ kind: "Not", arg: isFieldUpdated });
  const guard: Z3Expr = guardParts.length === 1 ? guardParts[0] : { kind: "And", args: guardParts };
  return {
    kind: "Implies",
    lhs: guard,
    rhs: { kind: "Cmp", op: "=", lhs: mapPost, rhs: mapPre },
  };
}

function emitUnmentionedFieldFrames(
  ctx: TranslateCtx,
  info: StateInfo,
  stateName: string,
  updates: readonly { readonly key: Z3Expr; readonly fields: ReadonlySet<string> }[],
): void {
  if (info.valueSort.kind !== "Uninterp") return;
  const entity = ctx.entities.get(info.valueSort.name);
  if (!entity) return;
  for (const update of updates) {
    const mapPreAtKey: Z3Expr = { kind: "App", func: info.mapFunc, args: [update.key] };
    const mapPostAtKey: Z3Expr = { kind: "App", func: info.mapFuncPost, args: [update.key] };
    for (const [fname, finfo] of entity.fields) {
      if (update.fields.has(fname)) continue;
      ctx.assertions.push({
        kind: "Cmp",
        op: "=",
        lhs: { kind: "App", func: finfo.funcName, args: [mapPostAtKey] },
        rhs: { kind: "App", func: finfo.funcName, args: [mapPreAtKey] },
      });
    }
  }
  void stateName;
}

function ensureCardinalityDecl(ctx: TranslateCtx, funcName: string): void {
  if (ctx.funcs.has(funcName)) return;
  ctx.declareFunc({ kind: "FuncDecl", name: funcName, argSorts: [], resultSort: Z3_INT });
  ctx.assertions.push({
    kind: "Cmp",
    op: ">=",
    lhs: { kind: "App", func: funcName, args: [] },
    rhs: { kind: "IntLit", value: 0 },
  });
}

function synthesizeCardinalityAxioms(
  ctx: TranslateCtx,
  state: StateDecl | null,
  op: OperationDecl,
): void {
  if (!state) return;
  for (const sf of state.fields) {
    const info = ctx.state.get(sf.name);
    if (!info || info.kind !== "Relation") continue;
    const delta = detectCardinalityDelta(op.ensures, sf.name);
    if (delta === null) continue;
    const preCard = ctx.cardinalityNameFor(sf.name, "pre");
    const postCard = ctx.cardinalityNameFor(sf.name, "post");
    ensureCardinalityDecl(ctx, preCard);
    ensureCardinalityDecl(ctx, postCard);
    const preRef: Z3Expr = { kind: "App", func: preCard, args: [] };
    const postRef: Z3Expr = { kind: "App", func: postCard, args: [] };
    const rhs: Z3Expr =
      delta === 0
        ? preRef
        : {
            kind: "Arith",
            op: delta > 0 ? "+" : "-",
            args: [preRef, { kind: "IntLit", value: Math.abs(delta) }],
          };
    ctx.assertions.push({ kind: "Cmp", op: "=", lhs: postRef, rhs });
  }
}

function detectCardinalityDelta(ensures: readonly Expr[], relName: string): number | null {
  for (const ens of ensures) {
    const primeEq = matchPrimedRelationEquality(ens, relName);
    if (!primeEq) continue;
    if (isIdentityRhs(primeEq.rhs, relName)) return 0;
    const insert = insertDelta(primeEq.rhs, relName);
    if (insert !== null) return insert;
    const del = deleteDelta(primeEq.rhs, relName);
    if (del !== null) return -del;
  }
  return null;
}

function insertDelta(expr: Expr, relName: string): number | null {
  if (expr.kind !== "BinaryOp") return null;
  if (expr.op !== "+" && expr.op !== "union") return null;
  if (!referencesPreRelation(expr.left, relName)) return null;
  const entries = extractMapEntries(expr.right);
  if (entries) return entries.length;
  const keys = extractKeySet(expr.right);
  if (keys) return keys.length;
  return null;
}

function deleteDelta(expr: Expr, relName: string): number | null {
  if (expr.kind !== "BinaryOp") return null;
  if (expr.op !== "-" && expr.op !== "minus") return null;
  if (!referencesPreRelation(expr.left, relName)) return null;
  const keys = extractKeySet(expr.right);
  if (keys) return keys.length;
  return null;
}

interface PrimedRelEq {
  readonly rhs: Expr;
}

function matchPrimedRelationEquality(expr: Expr, relName: string): PrimedRelEq | null {
  if (expr.kind !== "BinaryOp" || expr.op !== "=") return null;
  if (referencesPrimedRelation(expr.left, relName)) return { rhs: expr.right };
  if (referencesPrimedRelation(expr.right, relName)) return { rhs: expr.left };
  return null;
}

function referencesPrimedRelation(expr: Expr, relName: string): boolean {
  return expr.kind === "Prime" && expr.expr.kind === "Identifier" && expr.expr.name === relName;
}

function referencesPreRelation(expr: Expr, relName: string): boolean {
  if (expr.kind === "Pre" && expr.expr.kind === "Identifier" && expr.expr.name === relName) {
    return true;
  }
  return expr.kind === "Identifier" && expr.name === relName;
}

function isIdentityRhs(expr: Expr, relName: string): boolean {
  return referencesPreRelation(expr, relName);
}


function ensuresMentionsPostState(
  ensures: readonly Expr[],
  stateName: string,
): boolean {
  return ensures.some((e) => exprMentionsPostState(e, stateName));
}

function exprMentionsPostState(expr: Expr, stateName: string): boolean {
  return walkMentionsPost(expr, stateName, false);
}

function walkMentionsPost(expr: Expr, stateName: string, insidePrime: boolean): boolean {
  switch (expr.kind) {
    case "Prime":
      return walkMentionsPost(expr.expr, stateName, true);
    case "Pre":
      return walkMentionsPost(expr.expr, stateName, false);
    case "Identifier":
      return insidePrime && expr.name === stateName;
    case "BinaryOp":
      return (
        walkMentionsPost(expr.left, stateName, insidePrime) ||
        walkMentionsPost(expr.right, stateName, insidePrime)
      );
    case "UnaryOp":
      return walkMentionsPost(expr.operand, stateName, insidePrime);
    case "FieldAccess":
      return walkMentionsPost(expr.base, stateName, insidePrime);
    case "Index":
      return (
        walkMentionsPost(expr.base, stateName, insidePrime) ||
        walkMentionsPost(expr.index, stateName, insidePrime)
      );
    case "Call":
      return expr.args.some((a) => walkMentionsPost(a, stateName, insidePrime));
    case "Quantifier":
      return (
        walkMentionsPost(expr.body, stateName, insidePrime) ||
        expr.bindings.some((b) => walkMentionsPost(b.domain, stateName, insidePrime))
      );
    case "With":
      return (
        walkMentionsPost(expr.base, stateName, insidePrime) ||
        expr.updates.some((u) => walkMentionsPost(u.value, stateName, insidePrime))
      );
    case "If":
      return (
        walkMentionsPost(expr.condition, stateName, insidePrime) ||
        walkMentionsPost(expr.then, stateName, insidePrime) ||
        walkMentionsPost(expr.else_, stateName, insidePrime)
      );
    case "Let":
      return (
        walkMentionsPost(expr.value, stateName, insidePrime) ||
        walkMentionsPost(expr.body, stateName, insidePrime)
      );
    case "SetComprehension":
      return (
        walkMentionsPost(expr.domain, stateName, insidePrime) ||
        walkMentionsPost(expr.predicate, stateName, insidePrime)
      );
    case "Matches":
      return walkMentionsPost(expr.expr, stateName, insidePrime);
    case "SomeWrap":
      return walkMentionsPost(expr.expr, stateName, insidePrime);
    case "MapLiteral":
      return expr.entries.some(
        (e) => walkMentionsPost(e.key, stateName, insidePrime) || walkMentionsPost(e.value, stateName, insidePrime),
      );
    case "SetLiteral":
    case "SeqLiteral":
      return expr.elements.some((e) => walkMentionsPost(e, stateName, insidePrime));
    case "Constructor":
      return expr.fields.some((f) => walkMentionsPost(f.value, stateName, insidePrime));
    default:
      return false;
  }
}

