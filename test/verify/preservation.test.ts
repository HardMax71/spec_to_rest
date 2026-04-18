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

describe.sequential("preservation — safe_counter (both ops preserve invariant)", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("every (operation, invariant) preservation check is sat", async () => {
    const ir = irFromFile("./fixtures/safe_counter.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    expect(findCheck(report.checks, "Increment.preserves.countNonNegative")?.status).toBe("sat");
    expect(findCheck(report.checks, "Decrement.preserves.countNonNegative")?.status).toBe("sat");
    expect(report.ok).toBe(true);
  });
});

describe.sequential("preservation — broken_decrement (ensures violates invariant)", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("detects an operation whose ensures drives state below the invariant floor", async () => {
    const ir = irFromFile("./fixtures/broken_decrement.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    const preservation = findCheck(report.checks, "Decrement.preserves.clicksNonNegative");
    expect(preservation?.status).toBe("unsat");
    expect(preservation?.detail).toContain("does not preserve");
    expect(report.ok).toBe(false);
  });
});
