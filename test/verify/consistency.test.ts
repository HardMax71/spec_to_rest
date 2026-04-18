import { describe, it, expect, beforeAll } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { WasmBackend } from "#verify/backend.js";
import { runConsistencyChecks, type CheckResult } from "#verify/consistency.js";
import { DEFAULT_VERIFICATION_CONFIG } from "#verify/types.js";
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

describe.sequential("consistency — url_shortener happy path", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("all consistency checks pass on the url_shortener fixture", async () => {
    const ir = irFromFile("../parser/fixtures/url_shortener.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    expect(report.ok).toBe(true);
    const global = findCheck(report.checks, "global");
    expect(global?.status).toBe("sat");
    for (const op of ["Shorten", "Resolve", "Delete", "ListAll"]) {
      expect(findCheck(report.checks, `${op}.requires`)?.status).toBe("sat");
      expect(findCheck(report.checks, `${op}.enabled`)?.status).toBe("sat");
    }
  });
});

describe.sequential("consistency — dead and unreachable operations", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("detects a dead operation (requires itself is contradictory)", async () => {
    const ir = irFromFile("./fixtures/dead_op.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    expect(report.ok).toBe(false);
    expect(findCheck(report.checks, "global")?.status).toBe("sat");
    expect(findCheck(report.checks, "DeadOp.requires")?.status).toBe("unsat");
    expect(findCheck(report.checks, "DeadOp.enabled")?.status).toBe("unsat");
  });

  it("detects an unreachable operation (requires contradicts the invariants)", async () => {
    const ir = irFromFile("./fixtures/unreachable_op.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    expect(report.ok).toBe(false);
    expect(findCheck(report.checks, "global")?.status).toBe("sat");
    expect(findCheck(report.checks, "UnreachableOp.requires")?.status).toBe("sat");
    expect(findCheck(report.checks, "UnreachableOp.enabled")?.status).toBe("unsat");
  });
});

describe.sequential("consistency — contradictory invariants still caught", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("unsat_invariants.spec fails the global check", async () => {
    const ir = irFromFile("./fixtures/unsat_invariants.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    expect(report.ok).toBe(false);
    expect(findCheck(report.checks, "global")?.status).toBe("unsat");
  });
});
