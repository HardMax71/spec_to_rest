import { describe, it, expect } from "vitest";
import { WasmBackend } from "#verify/backend.js";
import { Z3_BOOL, Z3_INT, uninterp, type Z3Script } from "#verify/script.js";
import { DEFAULT_VERIFICATION_CONFIG } from "#verify/types.js";

const backend = new WasmBackend();

async function check(script: Z3Script, timeoutMs = 30_000): Promise<"sat" | "unsat" | "unknown"> {
  const r = await backend.check(script, { timeoutMs });
  return r.status;
}

describe.sequential("WasmBackend — trivial check-sat scenarios", () => {
  it("returns sat on an empty script", async () => {
    expect(await check({ sorts: [], funcs: [], assertions: [] })).toBe("sat");
  });

  it("returns unsat on a false assertion", async () => {
    expect(
      await check({
        sorts: [],
        funcs: [],
        assertions: [{ kind: "BoolLit", value: false }],
      }),
    ).toBe("unsat");
  });

  it("returns sat on a trivially true forall", async () => {
    expect(
      await check({
        sorts: [uninterp("U")],
        funcs: [],
        assertions: [
          {
            kind: "Quantifier",
            q: "ForAll",
            bindings: [{ name: "u", sort: uninterp("U") }],
            body: {
              kind: "Cmp",
              op: "=",
              lhs: { kind: "Var", name: "u", sort: uninterp("U") },
              rhs: { kind: "Var", name: "u", sort: uninterp("U") },
            },
          },
        ],
      }),
    ).toBe("sat");
  });

  it("returns unsat when an uninterpreted func contradicts itself", async () => {
    expect(
      await check({
        sorts: [uninterp("U")],
        funcs: [
          {
            kind: "FuncDecl",
            name: "p",
            argSorts: [uninterp("U")],
            resultSort: Z3_BOOL,
          },
        ],
        assertions: [
          {
            kind: "Quantifier",
            q: "ForAll",
            bindings: [{ name: "u", sort: uninterp("U") }],
            body: {
              kind: "App",
              func: "p",
              args: [{ kind: "Var", name: "u", sort: uninterp("U") }],
            },
          },
          {
            kind: "Quantifier",
            q: "ForAll",
            bindings: [{ name: "u", sort: uninterp("U") }],
            body: {
              kind: "Not",
              arg: {
                kind: "App",
                func: "p",
                args: [{ kind: "Var", name: "u", sort: uninterp("U") }],
              },
            },
          },
          // make the universe non-empty
          {
            kind: "Quantifier",
            q: "Exists",
            bindings: [{ name: "u", sort: uninterp("U") }],
            body: { kind: "BoolLit", value: true },
          },
        ],
      }),
    ).toBe("unsat");
  });

  it("uses integer arithmetic correctly", async () => {
    expect(
      await check({
        sorts: [],
        funcs: [{ kind: "FuncDecl", name: "x", argSorts: [], resultSort: Z3_INT }],
        assertions: [
          {
            kind: "Cmp",
            op: "=",
            lhs: {
              kind: "Arith",
              op: "+",
              args: [
                { kind: "App", func: "x", args: [] },
                { kind: "IntLit", value: 1 },
              ],
            },
            rhs: { kind: "IntLit", value: 2 },
          },
        ],
      }),
    ).toBe("sat");
  });

  it("honors config defaults from DEFAULT_VERIFICATION_CONFIG", () => {
    expect(DEFAULT_VERIFICATION_CONFIG.timeoutMs).toBe(30_000);
  });
});
