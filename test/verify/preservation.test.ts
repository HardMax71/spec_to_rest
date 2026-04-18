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

describe.sequential("preservation — url_shortener (partial-frame synthesis)", () => {
  let backend: WasmBackend;

  beforeAll(() => {
    backend = new WasmBackend();
  });

  it("Delete preserves every invariant thanks to 'k not in X'' frame synthesis", async () => {
    const ir = irFromFile("../parser/fixtures/url_shortener.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    for (const inv of ["allURLsValid", "metadataConsistent", "clickCountNonNegative"]) {
      expect(findCheck(report.checks, `Delete.preserves.${inv}`)?.status).toBe("sat");
    }
  });

  it("Resolve preserves every invariant thanks to field-update frame synthesis", async () => {
    const ir = irFromFile("../parser/fixtures/url_shortener.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    for (const inv of ["allURLsValid", "metadataConsistent", "clickCountNonNegative"]) {
      expect(findCheck(report.checks, `Resolve.preserves.${inv}`)?.status).toBe("sat");
    }
  });

  it("Shorten preservation checks are skipped — string-typed '+' in ensures", async () => {
    const ir = irFromFile("../parser/fixtures/url_shortener.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    for (const inv of ["allURLsValid", "metadataConsistent", "clickCountNonNegative"]) {
      const check = findCheck(report.checks, `Shorten.preserves.${inv}`);
      expect(check?.status).toBe("skipped");
      expect(check?.detail).toContain("arithmetic operator '+'");
    }
  });

  it("ListAll preservation checks are skipped — standalone set comprehension", async () => {
    const ir = irFromFile("../parser/fixtures/url_shortener.spec");
    const report = await runConsistencyChecks(ir, backend, DEFAULT_VERIFICATION_CONFIG);
    for (const inv of ["allURLsValid", "metadataConsistent", "clickCountNonNegative"]) {
      const check = findCheck(report.checks, `ListAll.preserves.${inv}`);
      expect(check?.status).toBe("skipped");
      expect(check?.detail).toContain("set comprehensions");
    }
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
