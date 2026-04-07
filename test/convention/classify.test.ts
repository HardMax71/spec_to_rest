import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import type { ServiceIR, Expr } from "#ir/index.js";
import { classifyOperations } from "#convention/index.js";
import type { OperationClassification, HttpMethod, OperationKind } from "#convention/index.js";
import {
  walkExpr,
  collectPrimedIdentifiers,
  collectPreservedRelations,
  detectCreatePattern,
  detectDeletePattern,
  countFilterParams,
  hasCollectionInput,
  flattenEnsures,
} from "#convention/expr-analysis.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function buildFixture(name: string): ServiceIR {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

function lookup(
  results: readonly OperationClassification[],
  name: string,
): OperationClassification {
  const found = results.find((r) => r.operationName === name);
  if (!found) throw new Error(`No classification for ${name}`);
  return found;
}

// ─── Fixture integration tests ──────────────────────────────

describe("classifyOperations", () => {
  describe("url_shortener.spec", () => {
    const ir = buildFixture("url_shortener.spec");
    const results = classifyOperations(ir);

    it.each([
      ["Shorten", "POST", "create", "M1"],
      ["Delete", "DELETE", "delete", "M5"],
      ["ListAll", "GET", "read", "M2"],
    ] as const)("%s → %s (%s, %s)", (name, method, kind, rule) => {
      const c = lookup(results, name);
      expect(c.method).toBe(method);
      expect(c.kind).toBe(kind);
      expect(c.matchedRule).toBe(rule);
    });

    it("classifies Resolve as PATCH (mutates metadata click_count)", () => {
      const c = lookup(results, "Resolve");
      expect(c.method).toBe("PATCH");
      expect(c.kind).toBe("partial_update");
      expect(c.matchedRule).toBe("M4");
    });
  });

  describe("todo_list.spec", () => {
    const ir = buildFixture("todo_list.spec");
    const results = classifyOperations(ir);

    it.each([
      ["CreateTodo", "POST", "create", "M1"],
      ["GetTodo", "GET", "read", "M2"],
      ["ListTodos", "GET", "read", "M2"],
      ["DeleteTodo", "DELETE", "delete", "M5"],
    ] as const)("%s → %s (%s, %s)", (name, method, kind, rule) => {
      const c = lookup(results, name);
      expect(c.method).toBe(method);
      expect(c.kind).toBe(kind);
      expect(c.matchedRule).toBe(rule);
    });

    it.each(["StartWork", "Complete", "Reopen", "Archive"])(
      "%s → POST (transition, M10)",
      (name) => {
        const c = lookup(results, name);
        expect(c.method).toBe("POST");
        expect(c.kind).toBe("transition");
        expect(c.matchedRule).toBe("M10");
      },
    );

    it("classifies UpdateTodo as PATCH (partial_update, M4)", () => {
      const c = lookup(results, "UpdateTodo");
      expect(c.method).toBe("PATCH");
      expect(c.kind).toBe("partial_update");
      expect(c.matchedRule).toBe("M4");
    });
  });

  describe("ecommerce.spec", () => {
    const ir = buildFixture("ecommerce.spec");
    const results = classifyOperations(ir);

    it("classifies CreateDraftOrder as POST (create, M1)", () => {
      const c = lookup(results, "CreateDraftOrder");
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("create");
      expect(c.matchedRule).toBe("M1");
    });

    it.each([
      "PlaceOrder",
      "RecordPayment",
      "ShipOrder",
      "ConfirmDelivery",
      "CancelOrder",
      "ProcessReturn",
    ])("%s → POST (transition, M10)", (name) => {
      const c = lookup(results, name);
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("transition");
      expect(c.matchedRule).toBe("M10");
    });

    it("classifies GetOrder as GET (read, M2)", () => {
      const c = lookup(results, "GetOrder");
      expect(c.method).toBe("GET");
      expect(c.kind).toBe("read");
    });

    it("classifies ListOrders as GET (read, M2)", () => {
      const c = lookup(results, "ListOrders");
      expect(c.method).toBe("GET");
      expect(c.kind).toBe("read");
    });

    it("classifies AddLineItem as PATCH (updates existing order)", () => {
      const c = lookup(results, "AddLineItem");
      expect(c.method).toBe("PATCH");
      expect(c.kind).toBe("partial_update");
    });

    it("classifies RemoveLineItem as PATCH (updates existing order)", () => {
      const c = lookup(results, "RemoveLineItem");
      expect(c.method).toBe("PATCH");
      expect(c.kind).toBe("partial_update");
    });
  });

  describe("auth_service.spec", () => {
    const ir = buildFixture("auth_service.spec");
    const results = classifyOperations(ir);

    it("classifies Register as POST (create, M1)", () => {
      const c = lookup(results, "Register");
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("create");
      expect(c.matchedRule).toBe("M1");
    });

    it("classifies Login as POST (create, M1)", () => {
      const c = lookup(results, "Login");
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("create");
    });

    it("classifies LoginFailed as POST (create — appends to login_attempts)", () => {
      const c = lookup(results, "LoginFailed");
      expect(c.method).toBe("POST");
    });

    it("classifies RefreshToken as POST (create — creates new session)", () => {
      const c = lookup(results, "RefreshToken");
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("create");
    });

    it.each(["RequestPasswordReset", "ResetPassword", "Logout"])(
      "%s → PATCH (partial_update — field-access mutations)",
      (name) => {
        const c = lookup(results, name);
        expect(c.method).toBe("PATCH");
        expect(c.kind).toBe("partial_update");
      },
    );
  });

  describe("edge_cases.spec", () => {
    const ir = buildFixture("edge_cases.spec");
    const results = classifyOperations(ir);

    it("classifies NoInput as POST (transition via ColorCycle)", () => {
      const c = lookup(results, "NoInput");
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("transition");
      expect(c.matchedRule).toBe("M10");
    });

    it("classifies NoOutput as POST (transition via ColorCycle)", () => {
      const c = lookup(results, "NoOutput");
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("transition");
      expect(c.matchedRule).toBe("M10");
    });

    it("classifies PrimedVars as POST (create — adds to relation)", () => {
      const c = lookup(results, "PrimedVars");
      expect(c.method).toBe("POST");
      expect(c.kind).toBe("create");
    });

    it("classifies Comprehension as GET (read — no state mutation)", () => {
      const c = lookup(results, "Comprehension");
      expect(c.method).toBe("GET");
      expect(c.kind).toBe("read");
    });
  });
});

// ─── Expression analysis unit tests ─────────────────────────

describe("expr-analysis", () => {
  function parseExpr(src: string): Expr {
    const { tree, errors } = parseSpec(`service T { invariant: ${src} }`);
    expect(errors).toEqual([]);
    return buildIR(tree).invariants[0].expr;
  }

  function parseEnsures(src: string): readonly Expr[] {
    const wrapped = `service T {
      state { r: Int -> lone String }
      operation Op {
        input: x: Int
        requires: true
        ensures: ${src}
      }
    }`;
    const { tree, errors } = parseSpec(wrapped);
    expect(errors).toEqual([]);
    return buildIR(tree).operations[0].ensures;
  }

  describe("walkExpr", () => {
    it("visits all nodes", () => {
      const expr = parseExpr("a and b");
      const kinds: string[] = [];
      walkExpr(expr, (node) => {
        kinds.push(node.kind);
      });
      expect(kinds).toContain("BinaryOp");
      expect(kinds).toContain("Identifier");
    });

    it("respects skip", () => {
      const expr = parseExpr("a and b");
      const kinds: string[] = [];
      walkExpr(expr, (node) => {
        kinds.push(node.kind);
        return "skip";
      });
      expect(kinds).toEqual(["BinaryOp"]);
    });
  });

  describe("collectPrimedIdentifiers", () => {
    it("finds primed identifier in ensures", () => {
      const ensures = parseEnsures("r' = pre(r) + {x -> \"hello\"}");
      const primed = collectPrimedIdentifiers(ensures);
      expect(primed.has("r")).toBe(true);
    });
  });

  describe("collectPreservedRelations", () => {
    it("detects R' = R pattern", () => {
      const ensures = parseEnsures("r' = r");
      const preserved = collectPreservedRelations(ensures, new Set(["r"]));
      expect(preserved.has("r")).toBe(true);
    });

    it("does not match R' = pre(R) + {...}", () => {
      const ensures = parseEnsures("r' = pre(r) + {x -> \"hello\"}");
      const preserved = collectPreservedRelations(ensures, new Set(["r"]));
      expect(preserved.has("r")).toBe(false);
    });
  });

  describe("detectCreatePattern", () => {
    it("detects R' = pre(R) + {...}", () => {
      const ensures = parseEnsures("r' = pre(r) + {x -> \"hello\"}");
      const result = detectCreatePattern(ensures, new Set(["r"]));
      expect(result).toEqual({ field: "r" });
    });

    it("returns null for R' = R", () => {
      const ensures = parseEnsures("r' = r");
      const result = detectCreatePattern(ensures, new Set(["r"]));
      expect(result).toBeNull();
    });
  });

  describe("detectDeletePattern", () => {
    it("detects key not in R'", () => {
      const ensures = parseEnsures("x not in r'");
      const result = detectDeletePattern(ensures, new Set(["r"]));
      expect(result).toEqual({ field: "r" });
    });
  });

  describe("countFilterParams", () => {
    it("counts Option-typed params", () => {
      const src = `service T {
        state { r: Int -> lone String }
        operation Op {
          input: a: Option[Int], b: String, c: Option[String]
          requires: true
          ensures: r' = r
        }
      }`;
      const { tree, errors } = parseSpec(src);
      expect(errors).toEqual([]);
      const ir = buildIR(tree);
      expect(countFilterParams(ir.operations[0].inputs)).toBe(2);
    });
  });

  describe("hasCollectionInput", () => {
    it("detects Set input", () => {
      const src = `service T {
        state { r: Int -> lone String }
        operation Op {
          input: items: Set[Int]
          requires: true
          ensures: r' = r
        }
      }`;
      const { tree, errors } = parseSpec(src);
      expect(errors).toEqual([]);
      const ir = buildIR(tree);
      expect(hasCollectionInput(ir.operations[0].inputs)).toBe(true);
    });

    it("returns false for scalar inputs", () => {
      const src = `service T {
        state { r: Int -> lone String }
        operation Op {
          input: x: Int
          requires: true
          ensures: r' = r
        }
      }`;
      const { tree, errors } = parseSpec(src);
      expect(errors).toEqual([]);
      const ir = buildIR(tree);
      expect(hasCollectionInput(ir.operations[0].inputs)).toBe(false);
    });
  });

  describe("flattenEnsures", () => {
    it("splits and-chains", () => {
      const expr = parseExpr("a and b and c");
      const flat = flattenEnsures([expr]);
      expect(flat.length).toBe(3);
    });
  });
});

// ─── Signals tests ──────────────────────────────────────────

describe("classification signals", () => {
  it("reports mutated relations for Shorten", () => {
    const ir = buildFixture("url_shortener.spec");
    const results = classifyOperations(ir);
    const shorten = lookup(results, "Shorten");
    expect(shorten.signals.mutatedRelations.length).toBeGreaterThan(0);
    expect(shorten.signals.createsNewKey).toBe(true);
  });

  it("reports preserved store and mutated metadata for Resolve", () => {
    const ir = buildFixture("url_shortener.spec");
    const results = classifyOperations(ir);
    const resolve = lookup(results, "Resolve");
    expect(resolve.signals.preservedRelations).toContain("store");
    expect(resolve.signals.mutatedRelations).toContain("metadata");
  });

  it("reports deletesKey for Delete", () => {
    const ir = buildFixture("url_shortener.spec");
    const results = classifyOperations(ir);
    const del = lookup(results, "Delete");
    expect(del.signals.deletesKey).toBe(true);
  });

  it("reports isTransition for PlaceOrder", () => {
    const ir = buildFixture("ecommerce.spec");
    const results = classifyOperations(ir);
    const place = lookup(results, "PlaceOrder");
    expect(place.signals.isTransition).toBe(true);
  });

  it("reports target entity for CreateDraftOrder", () => {
    const ir = buildFixture("ecommerce.spec");
    const results = classifyOperations(ir);
    const create = lookup(results, "CreateDraftOrder");
    expect(create.targetEntity).toBe("Order");
  });

  it("reports no createsNewKey when key exists in requires (UpdateTodo)", () => {
    const ir = buildFixture("todo_list.spec");
    const results = classifyOperations(ir);
    const update = lookup(results, "UpdateTodo");
    expect(update.signals.createsNewKey).toBe(false);
  });
});

// ─── Rule coverage tests (M3, M7, M8, M9) ──────────────────

describe("rule coverage — inline specs", () => {
  function buildInline(src: string): ServiceIR {
    const { tree, errors } = parseSpec(src);
    expect(errors).toEqual([]);
    return buildIR(tree);
  }

  it("M3 (PUT) — full entity replacement via with covering all fields", () => {
    const ir = buildInline(`service T {
      entity Item {
        a: Int
        b: String
      }
      state { items: Int -> lone Item }
      operation ReplaceItem {
        input: id: Int, a: Int, b: String
        output: item: Item
        requires: id in items
        ensures:
          item = pre(items)[id] with { a = a, b = b }
          items' = pre(items) + {id -> item}
      }
    }`);
    const [c] = classifyOperations(ir);
    expect(c.operationName).toBe("ReplaceItem");
    expect(c.method).toBe("PUT");
    expect(c.kind).toBe("replace");
    expect(c.matchedRule).toBe("M3");
    expect(c.signals.targetEntityFieldCount).toBe(2);
  });

  it("M7 (filtered_read) — read with >3 Option filter params", () => {
    const ir = buildInline(`service T {
      entity Item { a: Int }
      state { items: Int -> lone Item }
      operation Search {
        input: f1: Option[Int], f2: Option[String], f3: Option[Int], f4: Option[String]
        output: results: Set[Item]
        requires: true
        ensures:
          results = { x in items | true }
          items' = items
      }
    }`);
    const [c] = classifyOperations(ir);
    expect(c.operationName).toBe("Search");
    expect(c.method).toBe("GET");
    expect(c.kind).toBe("filtered_read");
    expect(c.matchedRule).toBe("M7");
    expect(c.signals.filterParamCount).toBe(4);
  });

  it("M9 (batch_mutation) — collection input with state mutation", () => {
    const ir = buildInline(`service T {
      entity Item { a: Int }
      state { items: Int -> lone Item }
      operation BatchCreate {
        input: batch: Set[Item]
        requires: true
        ensures:
          items' = pre(items)
      }
    }`);
    const [c] = classifyOperations(ir);
    expect(c.operationName).toBe("BatchCreate");
    expect(c.method).toBe("POST");
    expect(c.kind).toBe("batch_mutation");
    expect(c.matchedRule).toBe("M9");
    expect(c.signals.hasCollectionInput).toBe(true);
  });

  it("M4 (PATCH) — scalar state mutation without with expression", () => {
    const ir = buildInline(`service T {
      state { counter: Int }
      operation Bump {
        requires: true
        ensures: counter' = counter + 1
      }
    }`);
    const [c] = classifyOperations(ir);
    expect(c.operationName).toBe("Bump");
    expect(c.method).toBe("PATCH");
    expect(c.kind).toBe("partial_update");
    expect(c.matchedRule).toBe("M4");
  });
});
