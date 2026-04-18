import { describe, it, expect } from "vitest";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { translate } from "#verify/translator.js";
import type { Z3Expr } from "#verify/script.js";

function countNodesWithSpan(e: Z3Expr, counters: { total: number; withSpan: number }): void {
  counters.total += 1;
  if (e.span) counters.withSpan += 1;
  switch (e.kind) {
    case "App":
      for (const a of e.args) countNodesWithSpan(a, counters);
      break;
    case "And":
    case "Or":
    case "Arith":
      for (const a of e.args) countNodesWithSpan(a, counters);
      break;
    case "Not":
      countNodesWithSpan(e.arg, counters);
      break;
    case "Implies":
    case "Cmp":
      countNodesWithSpan(e.lhs, counters);
      countNodesWithSpan(e.rhs, counters);
      break;
    case "Quantifier":
      countNodesWithSpan(e.body, counters);
      break;
    default:
      break;
  }
}

describe("Z3Expr span propagation", () => {
  it("translateExpr stamps IR spans onto the root of each user-authored expression", () => {
    const src = `
      service S {
        state { counter: Int }
        invariant nonneg: counter >= 0
      }
    `;
    const { tree, errors } = parseSpec(src);
    expect(errors).toEqual([]);
    const ir = buildIR(tree);
    const script = translate(ir);
    const invariantAssertion = script.assertions[script.assertions.length - 1];
    expect(invariantAssertion.span).toBeDefined();
    expect(invariantAssertion.span!.startLine).toBeGreaterThan(0);
  });

  it("covers multiple expression kinds with spans", () => {
    const src = `
      service S {
        state {
          x: Int
          y: Int
        }
        invariant a: x >= 0 and y <= 100
        invariant b: x + y > 0 or not (x = y)
      }
    `;
    const { tree, errors } = parseSpec(src);
    expect(errors).toEqual([]);
    const ir = buildIR(tree);
    const script = translate(ir);
    const counters = { total: 0, withSpan: 0 };
    for (const a of script.assertions) countNodesWithSpan(a, counters);
    expect(counters.total).toBeGreaterThan(0);
    expect(counters.withSpan).toBeGreaterThan(0);
  });

  it("synthetic string-distinctness axioms have no span (not user-authored)", () => {
    const src = `
      service S {
        invariant z: "a" != "b"
        invariant w: "c" != "d"
      }
    `;
    const { tree, errors } = parseSpec(src);
    expect(errors).toEqual([]);
    const ir = buildIR(tree);
    const script = translate(ir);
    const syntheticLast = script.assertions[script.assertions.length - 1];
    expect(syntheticLast.kind === "And" || syntheticLast.kind === "Cmp").toBe(true);
    expect(syntheticLast.span).toBeUndefined();
  });
});
