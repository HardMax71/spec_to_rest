import { init, type Arith, type Bool, type Context, type Expr, type FuncDecl, type Model, type Sort } from "z3-solver";
import type { Z3_ast } from "z3-solver";
import type { Z3Script, Z3Expr, Z3Sort, Z3FunctionDecl } from "#verify/script.js";
import { sortKey } from "#verify/script.js";
import type { SmokeCheckResult, VerificationConfig } from "#verify/types.js";

type Ctx = Context<"verify">;
type SortT = Sort<"verify">;
type FuncT = FuncDecl<"verify">;
type ExprT = Expr<"verify", Sort<"verify">, Z3_ast>;
type BoolT = Bool<"verify">;
type ArithT = Arith<"verify">;

interface RenderCtx {
  readonly ctx: Ctx;
  readonly sortMap: Map<string, SortT>;
  readonly funcMap: Map<string, FuncT>;
  readonly varStack: Map<string, ExprT>[];
}

export class WasmBackend {
  private ctxPromise: Promise<Ctx> | null = null;

  private async getContext(): Promise<Ctx> {
    if (!this.ctxPromise) {
      this.ctxPromise = init().then((high) => high.Context("verify"));
    }
    return this.ctxPromise;
  }

  async check(script: Z3Script, cfg: VerificationConfig): Promise<SmokeCheckResult> {
    const ctx = await this.getContext();
    const sortMap = declareSorts(ctx, script.sorts);
    const funcMap = declareFuncs(ctx, script.funcs, sortMap);
    const solver = new ctx.Solver();
    if (cfg.timeoutMs > 0) solver.set("timeout", cfg.timeoutMs);
    const rctx: RenderCtx = { ctx, sortMap, funcMap, varStack: [] };
    for (const a of script.assertions) {
      solver.add(renderBool(rctx, a));
    }
    const t0 = performance.now();
    const status = await solver.check();
    const durationMs = performance.now() - t0;
    const model = status === "sat" && cfg.captureModel ? solver.model() : null;
    return { status, durationMs, model, sortMap, funcMap };
  }
}

export type Z3Model = Model<"verify">;
export type Z3SortMap = ReadonlyMap<string, SortT>;
export type Z3FuncMap = ReadonlyMap<string, FuncT>;

function declareSorts(ctx: Ctx, sorts: readonly Z3Sort[]): Map<string, SortT> {
  const map = new Map<string, SortT>();
  for (const s of sorts) {
    if (s.kind === "Uninterp") map.set(sortKey(s), ctx.Sort.declare(s.name));
  }
  return map;
}

function resolveSort(ctx: Ctx, sortMap: Map<string, SortT>, s: Z3Sort): SortT {
  if (s.kind === "Int") return ctx.Int.sort();
  if (s.kind === "Bool") return ctx.Bool.sort();
  const found = sortMap.get(sortKey(s));
  if (found) return found;
  const fresh = ctx.Sort.declare(s.name);
  sortMap.set(sortKey(s), fresh);
  return fresh;
}

function declareFuncs(
  ctx: Ctx,
  funcs: readonly Z3FunctionDecl[],
  sortMap: Map<string, SortT>,
): Map<string, FuncT> {
  const map = new Map<string, FuncT>();
  for (const f of funcs) {
    const argSorts = f.argSorts.map((s) => resolveSort(ctx, sortMap, s));
    const resultSort = resolveSort(ctx, sortMap, f.resultSort);
    const decl = ctx.Function.declare(f.name, ...argSorts, resultSort);
    map.set(f.name, decl);
  }
  return map;
}

function lookupVar(rctx: RenderCtx, name: string): ExprT | undefined {
  for (let i = rctx.varStack.length - 1; i >= 0; i -= 1) {
    const hit = rctx.varStack[i].get(name);
    if (hit) return hit;
  }
  return undefined;
}

function renderExpr(rctx: RenderCtx, e: Z3Expr): ExprT {
  switch (e.kind) {
    case "Var": {
      const bound = lookupVar(rctx, e.name);
      if (!bound) throw new Error(`unbound Z3 variable '${e.name}'`);
      return bound;
    }
    case "App": {
      const decl = rctx.funcMap.get(e.func);
      if (!decl) throw new Error(`undeclared Z3 function '${e.func}'`);
      const args = e.args.map((a) => renderExpr(rctx, a));
      return decl.call(...args);
    }
    case "IntLit":
      return rctx.ctx.Int.val(e.value);
    case "BoolLit":
      return rctx.ctx.Bool.val(e.value);
    case "And":
      return rctx.ctx.And(...e.args.map((a) => renderBool(rctx, a)));
    case "Or":
      return rctx.ctx.Or(...e.args.map((a) => renderBool(rctx, a)));
    case "Not":
      return rctx.ctx.Not(renderBool(rctx, e.arg));
    case "Implies":
      return rctx.ctx.Implies(renderBool(rctx, e.lhs), renderBool(rctx, e.rhs));
    case "Cmp":
      return renderCmp(rctx, e);
    case "Arith":
      return renderArith(rctx, e);
    case "Quantifier":
      return renderQuantifier(rctx, e);
    default:
      return assertUnreachable(e);
  }
}

function assertUnreachable(x: never): never {
  throw new Error(`unreachable Z3Expr kind: ${JSON.stringify(x)}`);
}

function renderBool(rctx: RenderCtx, e: Z3Expr): BoolT {
  const out = renderExpr(rctx, e);
  return out as BoolT;
}

function renderArithExpr(rctx: RenderCtx, e: Z3Expr): ArithT {
  const out = renderExpr(rctx, e);
  return out as ArithT;
}

type CmpNode = Extract<Z3Expr, { kind: "Cmp" }>;
function renderCmp(rctx: RenderCtx, e: CmpNode): BoolT {
  if (e.op === "=" || e.op === "!=") {
    const l = renderExpr(rctx, e.lhs);
    const r = renderExpr(rctx, e.rhs);
    return e.op === "=" ? l.eq(r) : l.neq(r);
  }
  const l = renderArithExpr(rctx, e.lhs);
  const r = renderArithExpr(rctx, e.rhs);
  switch (e.op) {
    case "<":
      return l.lt(r);
    case "<=":
      return l.le(r);
    case ">":
      return l.gt(r);
    case ">=":
      return l.ge(r);
  }
}

type ArithNode = Extract<Z3Expr, { kind: "Arith" }>;
function renderArith(rctx: RenderCtx, e: ArithNode): ArithT {
  const args = e.args.map((a) => renderArithExpr(rctx, a));
  if (args.length === 0) throw new Error("Arith with no args");
  let acc: ArithT = args[0];
  for (let i = 1; i < args.length; i += 1) {
    const rhs = args[i];
    switch (e.op) {
      case "+":
        acc = acc.add(rhs);
        break;
      case "-":
        acc = acc.sub(rhs);
        break;
      case "*":
        acc = acc.mul(rhs);
        break;
      case "/":
        acc = acc.div(rhs);
        break;
    }
  }
  return acc;
}

type QuantifierNode = Extract<Z3Expr, { kind: "Quantifier" }>;
function renderQuantifier(rctx: RenderCtx, e: QuantifierNode): BoolT {
  if (e.bindings.length === 0) {
    throw new Error(`Quantifier must have at least one binding (got 0 for ${e.q})`);
  }
  const frame = new Map<string, ExprT>();
  const first = rctx.ctx.Const(e.bindings[0].name, resolveSort(rctx.ctx, rctx.sortMap, e.bindings[0].sort));
  frame.set(e.bindings[0].name, first);
  const rest: ExprT[] = [];
  for (let i = 1; i < e.bindings.length; i += 1) {
    const b = e.bindings[i];
    const sort = resolveSort(rctx.ctx, rctx.sortMap, b.sort);
    const c = rctx.ctx.Const(b.name, sort);
    frame.set(b.name, c);
    rest.push(c);
  }
  const consts: [ExprT, ...ExprT[]] = [first, ...rest];
  rctx.varStack.push(frame);
  try {
    const body = renderBool(rctx, e.body);
    if (e.q === "ForAll") return rctx.ctx.ForAll(consts, body);
    return rctx.ctx.Exists(consts, body);
  } finally {
    rctx.varStack.pop();
  }
}
