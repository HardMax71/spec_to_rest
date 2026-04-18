import type { ServiceIR, OperationDecl, InvariantDecl } from "#ir/types.js";
import type { WasmBackend } from "#verify/backend.js";
import {
  translate,
  translateOperationRequires,
  translateOperationEnabled,
  translateOperationPreservation,
} from "#verify/translator.js";
import { TranslatorError } from "#verify/types.js";
import type { CheckStatus, VerificationConfig } from "#verify/types.js";

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
  try {
    const script = translate(ir);
    const result = await backend.check(script, config);
    return {
      id: "global",
      kind: "global",
      operationName: null,
      invariantName: null,
      status: result.status,
      durationMs: result.durationMs,
      detail: detailFor("global", null, null, result.status),
    };
  } catch (err: unknown) {
    return skippedCheck("global", "global", null, null, err);
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
  try {
    const script =
      kind === "requires"
        ? translateOperationRequires(ir, op)
        : translateOperationEnabled(ir, op);
    const result = await backend.check(script, config);
    return {
      id,
      kind,
      operationName: op.name,
      invariantName: null,
      status: result.status,
      durationMs: result.durationMs,
      detail: detailFor(kind, op.name, null, result.status),
    };
  } catch (err: unknown) {
    return skippedCheck(id, kind, op.name, null, err);
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
  try {
    const script = translateOperationPreservation(ir, op, inv.decl);
    const result = await backend.check(script, config);
    const inverted = invertStatus(result.status);
    return {
      id,
      kind: "preservation",
      operationName: op.name,
      invariantName: inv.name,
      status: inverted,
      durationMs: result.durationMs,
      detail: detailFor("preservation", op.name, inv.name, result.status),
    };
  } catch (err: unknown) {
    return skippedCheck(id, "preservation", op.name, inv.name, err);
  }
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
  err: unknown,
): CheckResult {
  const message = err instanceof Error ? err.message : String(err);
  if (err instanceof TranslatorError) {
    return {
      id,
      kind,
      operationName,
      invariantName,
      status: "skipped",
      durationMs: 0,
      detail: `translator limitation: ${message}`,
    };
  }
  return {
    id,
    kind,
    operationName,
    invariantName,
    status: "unknown",
    durationMs: 0,
    detail: `backend error: ${message}`,
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
      return `operation '${op}' does not preserve invariant '${inv}' — counterexample found (formatted diagnostics in M4.4)`;
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
