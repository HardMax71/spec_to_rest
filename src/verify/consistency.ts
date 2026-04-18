import type { ServiceIR, OperationDecl } from "#ir/types.js";
import type { WasmBackend } from "#verify/backend.js";
import { translate, translateOperationRequires, translateOperationEnabled } from "#verify/translator.js";
import { TranslatorError } from "#verify/types.js";
import type { CheckStatus, VerificationConfig } from "#verify/types.js";

export type CheckKind = "global" | "requires" | "enabled";
export type CheckOutcome = CheckStatus | "skipped";

export interface CheckResult {
  readonly id: string;
  readonly kind: CheckKind;
  readonly operationName: string | null;
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
  for (const op of ops) {
    checks.push(await runOperationCheck(ir, op, "requires", backend, config));
    checks.push(await runOperationCheck(ir, op, "enabled", backend, config));
  }
  const ok = checks.every((c) => c.status === "sat" || c.status === "skipped");
  return { checks, ok };
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
      status: result.status,
      durationMs: result.durationMs,
      detail: detailFor("global", null, result.status),
    };
  } catch (err: unknown) {
    return skippedCheck("global", "global", null, err);
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
      status: result.status,
      durationMs: result.durationMs,
      detail: detailFor(kind, op.name, result.status),
    };
  } catch (err: unknown) {
    return skippedCheck(id, kind, op.name, err);
  }
}

function skippedCheck(
  id: string,
  kind: CheckKind,
  operationName: string | null,
  err: unknown,
): CheckResult {
  const message = err instanceof TranslatorError
    ? err.message
    : err instanceof Error
      ? err.message
      : String(err);
  return {
    id,
    kind,
    operationName,
    status: "skipped",
    durationMs: 0,
    detail: `translator limitation: ${message}`,
  };
}

function detailFor(kind: CheckKind, op: string | null, status: CheckStatus): string | null {
  if (status === "sat") return null;
  if (kind === "global") {
    if (status === "unsat") return "invariants are jointly contradictory — no valid state exists";
    return "solver could not decide invariant satisfiability within the timeout";
  }
  if (kind === "requires") {
    if (status === "unsat") {
      return `'requires' clause of operation '${op}' is contradictory — the operation can never fire`;
    }
    return `solver could not decide 'requires' satisfiability for operation '${op}'`;
  }
  if (status === "unsat") {
    return `operation '${op}' is dead — no valid pre-state satisfies both the invariants and its 'requires'`;
  }
  return `solver could not decide enablement for operation '${op}'`;
}
