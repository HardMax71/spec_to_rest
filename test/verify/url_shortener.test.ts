import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { translate } from "#verify/translator.js";
import { WasmBackend } from "#verify/backend.js";

const backend = new WasmBackend();
const parserFixtures = join(import.meta.dirname, "../parser/fixtures");
const verifyFixtures = join(import.meta.dirname, "fixtures");

async function verifyFixture(absPath: string): Promise<"sat" | "unsat" | "unknown"> {
  const src = readFileSync(absPath, "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  const script = translate(buildIR(tree));
  const result = await backend.check(script, { timeoutMs: 30_000 });
  return result.status;
}

describe.sequential("verify — end-to-end smoke check", () => {
  it("url_shortener.spec is satisfiable (invariants can hold)", async () => {
    const status = await verifyFixture(join(parserFixtures, "url_shortener.spec"));
    expect(status).toBe("sat");
  });

  it("a contradictory invariant set is reported unsat", async () => {
    const status = await verifyFixture(join(verifyFixtures, "unsat_invariants.spec"));
    expect(status).toBe("unsat");
  });
});
