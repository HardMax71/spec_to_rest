import { describe, it, expect, beforeAll } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { WasmBackend } from "#verify/backend.js";
import { runConsistencyChecks, type CheckResult } from "#verify/consistency.js";
import { DEFAULT_VERIFICATION_CONFIG } from "#verify/types.js";
import { formatDiagnostic } from "#verify/diagnostic.js";
import type { ServiceIR } from "#ir/types.js";

function irFromFile(relPath: string): ServiceIR {
  const src = readFileSync(join(import.meta.dirname, relPath), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

function findCheck(checks: readonly CheckResult[], id: string): CheckResult | undefined {
  return checks.find((c) => c.id === id);
}

describe.sequential("diagnostic — preservation violation on broken_url_shortener", () => {
  let backend: WasmBackend;
  let checks: readonly CheckResult[];

  beforeAll(async () => {
    backend = new WasmBackend();
    const ir = irFromFile("./fixtures/broken_url_shortener.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    checks = report.checks;
  });

  it("attaches an invariant_violation_by_operation diagnostic with a counterexample", () => {
    const check = findCheck(checks, "Tamper.preserves.clickCountNonNegative");
    expect(check?.status).toBe("unsat");
    expect(check?.diagnostic).not.toBeNull();
    expect(check?.diagnostic?.category).toBe("invariant_violation_by_operation");
    expect(check?.diagnostic?.counterexample).not.toBeNull();
  });

  it("counterexample decodes entities, state relations, and inputs", () => {
    const diag = findCheck(checks, "Tamper.preserves.clickCountNonNegative")?.diagnostic;
    const ce = diag?.counterexample;
    expect(ce).toBeDefined();
    expect(ce!.inputs.find((i) => i.name === "code")).toBeDefined();
    expect(ce!.entities.some((e) => e.sortName === "UrlMapping")).toBe(true);
    const post = ce!.stateRelations.find((r) => r.stateName === "metadata" && r.side === "post");
    expect(post?.entries.length).toBeGreaterThan(0);
  });

  it("formatDiagnostic produces a multi-line report with inputs / pre-state / post-state", () => {
    const diag = findCheck(checks, "Tamper.preserves.clickCountNonNegative")?.diagnostic;
    expect(diag).not.toBeNull();
    const output = formatDiagnostic(diag!, "broken_url_shortener.spec");
    expect(output).toContain("error: operation 'Tamper' violates invariant 'clickCountNonNegative'");
    expect(output).toContain("Counterexample:");
    expect(output).toContain("pre-state:");
    expect(output).toContain("post-state:");
    expect(output).toContain("hint:");
  });

  it("records the source span on the diagnostic", () => {
    const check = findCheck(checks, "Tamper.preserves.clickCountNonNegative");
    const diag = check!.diagnostic!;
    expect(diag.primarySpan).not.toBeNull();
    expect(diag.primarySpan!.startLine).toBeGreaterThan(0);
    expect(check!.sourceSpans.length).toBeGreaterThan(0);
  });
});

describe.sequential("diagnostic — category assignment across check kinds", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("contradictory invariants → contradictory_invariants category", async () => {
    const ir = irFromFile("./fixtures/unsat_invariants.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    const global = findCheck(report.checks, "global");
    expect(global?.status).toBe("unsat");
    expect(global?.diagnostic?.category).toBe("contradictory_invariants");
  });

  it("dead operation → unsatisfiable_precondition category", async () => {
    const ir = irFromFile("./fixtures/dead_op.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    const check = findCheck(report.checks, "DeadOp.requires");
    expect(check?.status).toBe("unsat");
    expect(check?.diagnostic?.category).toBe("unsatisfiable_precondition");
  });

  it("unreachable operation → unreachable_operation category", async () => {
    const ir = irFromFile("./fixtures/unreachable_op.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    const check = findCheck(report.checks, "UnreachableOp.enabled");
    expect(check?.status).toBe("unsat");
    expect(check?.diagnostic?.category).toBe("unreachable_operation");
  });

  it("passing check → no diagnostic attached", async () => {
    const ir = irFromFile("./fixtures/safe_counter.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    const ok = findCheck(report.checks, "Increment.preserves.countNonNegative");
    expect(ok?.status).toBe("sat");
    expect(ok?.diagnostic).toBeNull();
  });
});

describe.sequential("diagnostic — formatter for non-preservation categories", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("contradictory invariants include at least one related span note", async () => {
    const ir = irFromFile("./fixtures/unsat_invariants.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    const global = findCheck(report.checks, "global")!;
    const output = formatDiagnostic(global.diagnostic!, "unsat_invariants.spec");
    expect(output).toContain("error:");
    expect(output).toContain("invariants are jointly unsatisfiable");
  });
});
