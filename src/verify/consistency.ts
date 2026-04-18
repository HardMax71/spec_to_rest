import type { ServiceIR, OperationDecl, InvariantDecl, Span } from "#ir/types.js";
import type { WasmBackend } from "#verify/backend.js";
import {
  translate,
  translateOperationRequires,
  translateOperationEnabled,
  translateOperationPreservation,
} from "#verify/translator.js";
import { TranslatorError } from "#verify/types.js";
import type { CheckStatus, VerificationConfig, SmokeCheckResult } from "#verify/types.js";
import type { Z3Script } from "#verify/script.js";
import { decodeCounterExample, type CounterExample } from "#verify/counterexample.js";
import type {
  VerificationDiagnostic,
  DiagnosticCategory,
  RelatedSpan,
} from "#verify/diagnostic.js";
import { suggestionFor } from "#verify/diagnostic.js";

export type CheckKind = "global" | "requires" | "enabled" | "preservation";
export type CheckOutcome = CheckStatus | "skipped";

export interface CheckResult {
  readonly id: string;
  readonly kind: CheckKind;
  readonly operationName: string | null;
  readonly invariantName: string | null;
  readonly status: CheckOutcome;
  readonly durationMs: number;
  readonly detail: string | null;
  readonly sourceSpans: readonly Span[];
  readonly diagnostic: VerificationDiagnostic | null;
}

export interface ConsistencyReport {
  readonly checks: readonly CheckResult[];
  readonly ok: boolean;
}

export async function runConsistencyChecks(
  ir: ServiceIR,
  backend: WasmBackend,
  config: VerificationConfig,
): Promise<ConsistencyReport> {
  const checks: CheckResult[] = [];
  checks.push(await runGlobal(ir, backend, config));
  const ops = [...ir.operations].sort((a, b) => a.name.localeCompare(b.name));
  const invariants = enumerateInvariants(ir);
  for (const op of ops) {
    checks.push(await runOperationCheck(ir, op, "requires", backend, config));
    checks.push(await runOperationCheck(ir, op, "enabled", backend, config));
    for (const inv of invariants) {
      checks.push(await runPreservationCheck(ir, op, inv, backend, config));
    }
  }
  const ok = checks.every((c) => c.status === "sat" || c.status === "skipped");
  return { checks, ok };
}

interface NamedInvariant {
  readonly name: string;
  readonly decl: InvariantDecl;
}

function enumerateInvariants(ir: ServiceIR): NamedInvariant[] {
  const result: NamedInvariant[] = [];
  for (let i = 0; i < ir.invariants.length; i += 1) {
    const inv = ir.invariants[i];
    result.push({ name: inv.name ?? `inv_${i}`, decl: inv });
  }
  return result;
}

async function runGlobal(
  ir: ServiceIR,
  backend: WasmBackend,
  config: VerificationConfig,
): Promise<CheckResult> {
  const sourceSpans = invariantSpans(ir);
  try {
    const script = translate(ir);
    const result = await backend.check(script, config);
    return finalizeCheck({
      id: "global",
      kind: "global",
      operationName: null,
      invariantName: null,
      rawStatus: result.status,
      outcome: result.status,
      durationMs: result.durationMs,
      sourceSpans,
      ir,
      invariantDecl: null,
      op: null,
      script,
      result,
    });
  } catch (err: unknown) {
    return skippedCheck("global", "global", null, null, sourceSpans, err);
  }
}

async function runOperationCheck(
  ir: ServiceIR,
  op: OperationDecl,
  kind: "requires" | "enabled",
  backend: WasmBackend,
  config: VerificationConfig,
): Promise<CheckResult> {
  const id = `${op.name}.${kind}`;
  const sourceSpans = operationCheckSpans(op, kind, ir);
  try {
    const script =
      kind === "requires"
        ? translateOperationRequires(ir, op)
        : translateOperationEnabled(ir, op);
    const result = await backend.check(script, config);
    return finalizeCheck({
      id,
      kind,
      operationName: op.name,
      invariantName: null,
      rawStatus: result.status,
      outcome: result.status,
      durationMs: result.durationMs,
      sourceSpans,
      ir,
      invariantDecl: null,
      op,
      script,
      result,
    });
  } catch (err: unknown) {
    return skippedCheck(id, kind, op.name, null, sourceSpans, err);
  }
}

async function runPreservationCheck(
  ir: ServiceIR,
  op: OperationDecl,
  inv: NamedInvariant,
  backend: WasmBackend,
  config: VerificationConfig,
): Promise<CheckResult> {
  const id = `${op.name}.preserves.${inv.name}`;
  const sourceSpans = preservationSpans(op, inv.decl);
  try {
    const script = translateOperationPreservation(ir, op, inv.decl);
    const result = await backend.check(script, {
      ...config,
      captureModel: true,
    });
    const inverted = invertStatus(result.status);
    return finalizeCheck({
      id,
      kind: "preservation",
      operationName: op.name,
      invariantName: inv.name,
      rawStatus: result.status,
      outcome: inverted,
      durationMs: result.durationMs,
      sourceSpans,
      ir,
      invariantDecl: inv.decl,
      op,
      script,
      result,
    });
  } catch (err: unknown) {
    return skippedCheck(id, "preservation", op.name, inv.name, sourceSpans, err);
  }
}

interface FinalizeArgs {
  readonly id: string;
  readonly kind: CheckKind;
  readonly operationName: string | null;
  readonly invariantName: string | null;
  readonly rawStatus: CheckStatus;
  readonly outcome: CheckOutcome;
  readonly durationMs: number;
  readonly sourceSpans: readonly Span[];
  readonly ir: ServiceIR;
  readonly invariantDecl: InvariantDecl | null;
  readonly op: OperationDecl | null;
  readonly script: Z3Script;
  readonly result: SmokeCheckResult;
}

function finalizeCheck(args: FinalizeArgs): CheckResult {
  const detail = detailFor(args.kind, args.operationName, args.invariantName, args.rawStatus);
  const diagnostic = buildDiagnostic(args);
  return {
    id: args.id,
    kind: args.kind,
    operationName: args.operationName,
    invariantName: args.invariantName,
    status: args.outcome,
    durationMs: args.durationMs,
    detail,
    sourceSpans: args.sourceSpans,
    diagnostic,
  };
}

function buildDiagnostic(args: FinalizeArgs): VerificationDiagnostic | null {
  if (args.outcome === "sat" || args.outcome === "skipped") return null;
  const category = categoryFor(args.kind, args.rawStatus);
  if (!category) return null;
  const counterexample =
    args.kind === "preservation" && args.rawStatus === "sat" && args.result.model
      ? decodeCounterExample(
          args.result.model,
          args.result.sortMap,
          args.result.funcMap,
          args.script.artifact,
        )
      : null;
  return {
    level: "error",
    category,
    message: messageFor(category, args.operationName, args.invariantName),
    primarySpan: primarySpanFor(args),
    relatedSpans: relatedSpansFor(args),
    counterexample,
    suggestion: suggestionFor(category),
  };
}

function categoryFor(kind: CheckKind, status: CheckStatus): DiagnosticCategory | null {
  if (status === "unknown") return "solver_timeout";
  if (kind === "global" && status === "unsat") return "contradictory_invariants";
  if (kind === "requires" && status === "unsat") return "unsatisfiable_precondition";
  if (kind === "enabled" && status === "unsat") return "unreachable_operation";
  if (kind === "preservation" && status === "sat") return "invariant_violation_by_operation";
  return null;
}

function messageFor(
  category: DiagnosticCategory,
  op: string | null,
  inv: string | null,
): string {
  switch (category) {
    case "contradictory_invariants":
      return "invariants are jointly unsatisfiable — no valid state exists";
    case "unsatisfiable_precondition":
      return `'requires' of operation '${op ?? "?"}' is unsatisfiable under the spec's base constraints`;
    case "unreachable_operation":
      return `operation '${op ?? "?"}' is unreachable — no valid pre-state satisfies both the invariants and its 'requires'`;
    case "invariant_violation_by_operation":
      return `operation '${op ?? "?"}' violates invariant '${inv ?? "?"}'`;
    case "solver_timeout":
      return `solver could not decide the check within the timeout`;
    case "translator_limitation":
      return `verifier does not yet support a construct used by this check`;
    case "backend_error":
      return `solver backend error`;
    default:
      return "verification failed";
  }
}

function primarySpanFor(args: FinalizeArgs): Span | null {
  if (args.kind === "preservation" && args.invariantDecl) {
    return args.op?.span ?? args.invariantDecl.span ?? null;
  }
  if (args.kind === "global") {
    return args.ir.invariants[0]?.span ?? null;
  }
  return args.op?.span ?? null;
}

function relatedSpansFor(args: FinalizeArgs): RelatedSpan[] {
  const out: RelatedSpan[] = [];
  if (args.kind === "preservation" && args.invariantDecl?.span) {
    out.push({
      span: args.invariantDecl.span,
      note: `invariant '${args.invariantName ?? "?"}' declared here`,
    });
  }
  if (args.kind === "global") {
    for (let i = 1; i < args.ir.invariants.length; i += 1) {
      const inv = args.ir.invariants[i];
      if (inv.span) {
        out.push({ span: inv.span, note: `invariant '${inv.name ?? `inv_${i}`}'` });
      }
    }
  }
  return out;
}

function invariantSpans(ir: ServiceIR): Span[] {
  const out: Span[] = [];
  for (const inv of ir.invariants) {
    if (inv.span) out.push(inv.span);
  }
  return out;
}

function operationCheckSpans(op: OperationDecl, kind: "requires" | "enabled", ir: ServiceIR): Span[] {
  const out: Span[] = [];
  if (op.span) out.push(op.span);
  for (const r of op.requires) if (r.span) out.push(r.span);
  if (kind === "enabled") {
    for (const inv of ir.invariants) if (inv.span) out.push(inv.span);
  }
  return out;
}

function preservationSpans(op: OperationDecl, inv: InvariantDecl): Span[] {
  const out: Span[] = [];
  if (op.span) out.push(op.span);
  if (inv.span) out.push(inv.span);
  for (const e of op.ensures) if (e.span) out.push(e.span);
  return out;
}

function invertStatus(status: CheckStatus): CheckOutcome {
  if (status === "unsat") return "sat";
  if (status === "sat") return "unsat";
  return "unknown";
}

function skippedCheck(
  id: string,
  kind: CheckKind,
  operationName: string | null,
  invariantName: string | null,
  sourceSpans: readonly Span[],
  err: unknown,
): CheckResult {
  const message = err instanceof Error ? err.message : String(err);
  const isTranslator = err instanceof TranslatorError;
  const category: DiagnosticCategory = isTranslator ? "translator_limitation" : "backend_error";
  const status: CheckOutcome = isTranslator ? "skipped" : "unknown";
  const detail = isTranslator
    ? `translator limitation: ${message}`
    : `backend error: ${message}`;
  const diagnostic: VerificationDiagnostic = {
    level: isTranslator ? "warning" : "error",
    category,
    message: isTranslator
      ? `translator limitation on check '${id}': ${message}`
      : `solver backend error on check '${id}': ${message}`,
    primarySpan: sourceSpans[0] ?? null,
    relatedSpans: [],
    counterexample: null,
    suggestion: suggestionFor(category),
  };
  return {
    id,
    kind,
    operationName,
    invariantName,
    status,
    durationMs: 0,
    detail,
    sourceSpans,
    diagnostic,
  };
}

function detailFor(
  kind: CheckKind,
  op: string | null,
  inv: string | null,
  status: CheckStatus,
): string | null {
  if (kind === "preservation") {
    if (status === "unsat") return null;
    if (status === "sat") {
      return `operation '${op}' does not preserve invariant '${inv}' — counterexample found`;
    }
    return `solver could not decide preservation of invariant '${inv}' by operation '${op}'`;
  }
  if (status === "sat") return null;
  if (kind === "global") {
    if (status === "unsat") return "invariants are jointly contradictory — no valid state exists";
    return "solver could not decide invariant satisfiability within the timeout";
  }
  if (kind === "requires") {
    if (status === "unsat") {
      return `'requires' of operation '${op}' is unsatisfiable under the spec's base constraints — the operation can never fire`;
    }
    return `solver could not decide 'requires' satisfiability for operation '${op}'`;
  }
  if (status === "unsat") {
    return `operation '${op}' is dead — no valid pre-state satisfies both the invariants and its 'requires'`;
  }
  return `solver could not decide enablement for operation '${op}'`;
}
