import type {
  TranslatorArtifact,
  ArtifactEntity,
  ArtifactStateEntry,
  ArtifactBinding,
  Z3Sort,
} from "#verify/script.js";
import { sortKey } from "#verify/script.js";
import type { Z3Model, Z3SortMap, Z3FuncMap } from "#verify/backend.js";

export interface DecodedValue {
  readonly display: string;
  readonly entityLabel: string | null;
}

export interface DecodedEntity {
  readonly sortName: string;
  readonly label: string;
  readonly rawElement: string;
  readonly fields: readonly { readonly name: string; readonly value: DecodedValue }[];
}

export interface DecodedRelationEntry {
  readonly key: DecodedValue;
  readonly value: DecodedValue;
}

export interface DecodedRelation {
  readonly stateName: string;
  readonly side: "pre" | "post";
  readonly entries: readonly DecodedRelationEntry[];
}

export interface DecodedConstant {
  readonly stateName: string;
  readonly side: "pre" | "post";
  readonly value: DecodedValue;
}

export interface DecodedInput {
  readonly name: string;
  readonly value: DecodedValue;
}

export interface CounterExample {
  readonly entities: readonly DecodedEntity[];
  readonly stateRelations: readonly DecodedRelation[];
  readonly stateConstants: readonly DecodedConstant[];
  readonly inputs: readonly DecodedInput[];
}

interface DecodeCtx {
  readonly model: Z3Model;
  readonly sortMap: Z3SortMap;
  readonly funcMap: Z3FuncMap;
  readonly artifact: TranslatorArtifact;
  readonly rawToLabel: Map<string, string>;
}

export function decodeCounterExample(
  model: Z3Model,
  sortMap: Z3SortMap,
  funcMap: Z3FuncMap,
  artifact: TranslatorArtifact,
): CounterExample {
  const ctx: DecodeCtx = { model, sortMap, funcMap, artifact, rawToLabel: new Map() };

  const entities = decodeEntities(ctx);
  const enumsDecoded = decodeEnums(ctx);
  for (const e of enumsDecoded) ctx.rawToLabel.set(e.rawElement, e.label);

  const stateRelations = decodeStateRelations(ctx);
  const stateConstants = decodeStateConstants(ctx);
  const inputs = decodeInputs(ctx);

  return { entities, stateRelations, stateConstants, inputs };
}

function decodeEntities(ctx: DecodeCtx): DecodedEntity[] {
  const result: DecodedEntity[] = [];
  for (const entity of ctx.artifact.entities) {
    const sort = ctx.sortMap.get(sortKey(entity.sort));
    if (!sort) continue;
    const universe = safeSortUniverse(ctx, sort);
    for (let i = 0; i < universe.length; i += 1) {
      const elem = universe[i];
      const raw = String(elem);
      const label = `${entity.name}#${i}`;
      ctx.rawToLabel.set(raw, label);
      const fields = decodeEntityFields(ctx, entity, elem);
      result.push({ sortName: entity.name, label, rawElement: raw, fields });
    }
  }
  return result;
}

function decodeEntityFields(
  ctx: DecodeCtx,
  entity: ArtifactEntity,
  elem: unknown,
): readonly { name: string; value: DecodedValue }[] {
  const out: { name: string; value: DecodedValue }[] = [];
  for (const field of entity.fields) {
    const decl = ctx.funcMap.get(field.funcName);
    if (!decl) continue;
    const applied = callFunc(decl, [elem]);
    const evaluated = evalExpr(ctx.model, applied);
    out.push({ name: field.name, value: decodeValueRaw(ctx, evaluated) });
  }
  return out;
}

interface DecodedEnumElement {
  readonly sortName: string;
  readonly label: string;
  readonly rawElement: string;
}

function decodeEnums(ctx: DecodeCtx): DecodedEnumElement[] {
  const result: DecodedEnumElement[] = [];
  for (const e of ctx.artifact.enums) {
    for (const member of e.members) {
      const decl = ctx.funcMap.get(member.funcName);
      if (!decl) continue;
      const applied = callFunc(decl, []);
      const evaluated = evalExpr(ctx.model, applied);
      result.push({
        sortName: e.name,
        label: `${e.name}.${member.name}`,
        rawElement: String(evaluated),
      });
    }
  }
  return result;
}

function decodeStateRelations(ctx: DecodeCtx): DecodedRelation[] {
  const result: DecodedRelation[] = [];
  for (const entry of ctx.artifact.state) {
    if (entry.kind !== "Relation") continue;
    const keySort = ctx.sortMap.get(sortKey(entry.keySort));
    const universeKeys = keySort ? safeSortUniverse(ctx, keySort) : [];
    const inputKeys = inputsOfSort(ctx, entry.keySort);
    const candidates = universeKeys.length > 0 ? universeKeys : inputKeys;
    result.push(buildRelationSide(ctx, entry, candidates, "pre"));
    if (ctx.artifact.hasPostState) {
      result.push(buildRelationSide(ctx, entry, candidates, "post"));
    }
  }
  return result;
}

function inputsOfSort(ctx: DecodeCtx, wantSort: Z3Sort): readonly unknown[] {
  const out: unknown[] = [];
  for (const b of ctx.artifact.inputs) {
    if (sortKey(b.sort) !== sortKey(wantSort)) continue;
    const decl = ctx.funcMap.get(b.funcName);
    if (!decl) continue;
    out.push(evalExpr(ctx.model, callFunc(decl, [])));
  }
  return out;
}

function buildRelationSide(
  ctx: DecodeCtx,
  relation: Extract<ArtifactStateEntry, { kind: "Relation" }>,
  keyUniverse: readonly unknown[],
  side: "pre" | "post",
): DecodedRelation {
  const domFuncName = side === "pre" ? relation.domFunc : relation.domFuncPost;
  const mapFuncName = side === "pre" ? relation.mapFunc : relation.mapFuncPost;
  const domDecl = ctx.funcMap.get(domFuncName);
  const mapDecl = ctx.funcMap.get(mapFuncName);
  const entries: DecodedRelationEntry[] = [];
  if (domDecl && mapDecl) {
    for (const k of keyUniverse) {
      const inDom = evalExpr(ctx.model, callFunc(domDecl, [k]));
      if (String(inDom) !== "true") continue;
      const mappedTo = evalExpr(ctx.model, callFunc(mapDecl, [k]));
      entries.push({
        key: decodeValueRaw(ctx, k),
        value: decodeValueRaw(ctx, mappedTo),
      });
    }
  }
  return { stateName: relation.name, side, entries };
}

function decodeStateConstants(ctx: DecodeCtx): DecodedConstant[] {
  const result: DecodedConstant[] = [];
  for (const entry of ctx.artifact.state) {
    if (entry.kind !== "Const") continue;
    result.push(buildConstantSide(ctx, entry, "pre"));
    if (ctx.artifact.hasPostState) result.push(buildConstantSide(ctx, entry, "post"));
  }
  return result;
}

function buildConstantSide(
  ctx: DecodeCtx,
  entry: Extract<ArtifactStateEntry, { kind: "Const" }>,
  side: "pre" | "post",
): DecodedConstant {
  const funcName = side === "pre" ? entry.funcName : entry.funcNamePost;
  const decl = ctx.funcMap.get(funcName);
  const value = decl
    ? decodeValueRaw(ctx, evalExpr(ctx.model, callFunc(decl, [])))
    : { display: "<unknown>", entityLabel: null };
  return { stateName: entry.name, side, value };
}

function decodeInputs(ctx: DecodeCtx): DecodedInput[] {
  return decodeBindings(ctx, ctx.artifact.inputs);
}

function decodeBindings(ctx: DecodeCtx, bindings: readonly ArtifactBinding[]): DecodedInput[] {
  const result: DecodedInput[] = [];
  for (const b of bindings) {
    const decl = ctx.funcMap.get(b.funcName);
    if (!decl) continue;
    const applied = callFunc(decl, []);
    const evaluated = evalExpr(ctx.model, applied);
    result.push({ name: b.name, value: decodeValueRaw(ctx, evaluated) });
  }
  return result;
}

function decodeValueRaw(ctx: DecodeCtx, expr: unknown): DecodedValue {
  const text = normalizeZ3Text(String(expr));
  const label = ctx.rawToLabel.get(text);
  if (label) return { display: label, entityLabel: label };
  if (text === "true" || text === "false") return { display: text, entityLabel: null };
  if (/^-?\d+$/.test(text)) return { display: text, entityLabel: null };
  const stringLit = matchStringLiteral(text);
  if (stringLit !== null) return { display: JSON.stringify(stringLit), entityLabel: null };
  return { display: prettyUninterp(text), entityLabel: null };
}

function normalizeZ3Text(raw: string): string {
  const negMatch = /^\(-\s+(\d+)\)$/.exec(raw.trim());
  if (negMatch) return `-${negMatch[1]}`;
  return raw;
}

function matchStringLiteral(text: string): string | null {
  const m = /^str_(\d+)$/.exec(text);
  return m ? `<string#${m[1]}>` : null;
}

function prettyUninterp(text: string): string {
  const m = /^([A-Za-z_][\w]*)!val!(\d+)$/.exec(text);
  return m ? `${m[1]}#${m[2]}` : text;
}

function safeSortUniverse(ctx: DecodeCtx, sort: unknown): readonly unknown[] {
  if (!isSortLike(sort)) return [];
  try {
    const universe = ctx.model.sortUniverse(sort);
    const out: unknown[] = [];
    for (let i = 0; i < universe.length(); i += 1) out.push(universe.get(i));
    return out;
  } catch {
    return [];
  }
}

function isSortLike(x: unknown): x is Parameters<Z3Model["sortUniverse"]>[0] {
  return typeof x === "object" && x !== null;
}

interface FuncLike {
  call(...args: unknown[]): unknown;
}

function isFuncLike(x: unknown): x is FuncLike {
  return typeof x === "object" && x !== null && typeof (x as FuncLike).call === "function";
}

function callFunc(decl: unknown, args: readonly unknown[]): unknown {
  if (!isFuncLike(decl)) throw new Error("func decl is not callable");
  return decl.call(...args);
}

function evalExpr(model: Z3Model, expr: unknown): unknown {
  return model.eval(expr as Parameters<Z3Model["eval"]>[0], true);
}

export function formatCounterExample(ce: CounterExample): string {
  const lines: string[] = [];
  if (ce.inputs.length > 0) {
    lines.push("  inputs:");
    for (const inp of ce.inputs) lines.push(`    ${inp.name} = ${inp.value.display}`);
  }
  if (ce.entities.length > 0) {
    lines.push("  entities:");
    for (const e of ce.entities) {
      const fieldStr = e.fields.map((f) => `${f.name} = ${f.value.display}`).join(", ");
      lines.push(`    ${e.label} { ${fieldStr} }`);
    }
  }
  const pre = ce.stateRelations.filter((r) => r.side === "pre");
  const preC = ce.stateConstants.filter((c) => c.side === "pre");
  if (pre.length > 0 || preC.length > 0) {
    lines.push("  pre-state:");
    for (const r of pre) lines.push(formatRelation(r));
    for (const c of preC) lines.push(`    ${c.stateName} = ${c.value.display}`);
  }
  const post = ce.stateRelations.filter((r) => r.side === "post");
  const postC = ce.stateConstants.filter((c) => c.side === "post");
  if (post.length > 0 || postC.length > 0) {
    lines.push("  post-state:");
    for (const r of post) lines.push(formatRelation(r));
    for (const c of postC) lines.push(`    ${c.stateName}' = ${c.value.display}`);
  }
  return lines.join("\n");
}

function formatRelation(r: DecodedRelation): string {
  const label = r.side === "pre" ? r.stateName : `${r.stateName}'`;
  if (r.entries.length === 0) return `    ${label} = {}`;
  const entryStrs = r.entries.map((e) => `${e.key.display} → ${e.value.display}`).join(", ");
  return `    ${label} = { ${entryStrs} }`;
}

