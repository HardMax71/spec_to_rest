import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { translate, translateOperationRequires, translateOperationEnabled } from "#verify/translator.js";
import type { Z3Expr, Z3FunctionDecl, Z3Script } from "#verify/script.js";
import { TranslatorError } from "#verify/types.js";
import type { ServiceIR } from "#ir/types.js";

function scriptFrom(src: string): Z3Script {
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return translate(buildIR(tree));
}

function irFrom(src: string): ServiceIR {
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

function service(body: string): string {
  return `service T { ${body} }`;
}

function findAssertion(script: Z3Script, predicate: (e: Z3Expr) => boolean): Z3Expr | undefined {
  return script.assertions.find(predicate);
}

function findFunc(script: Z3Script, name: string): Z3FunctionDecl | undefined {
  return script.funcs.find((f) => f.name === name);
}

describe("translator — literals and simple comparisons", () => {
  it.each([
    ["integer literal", "0 = 0", "IntLit"],
    ["boolean literal", "true and true", "BoolLit"],
  ])("translates %s via top-level invariant", (_label, src, expectedKind) => {
    const script = scriptFrom(service(`invariant: ${src}`));
    expect(JSON.stringify(script.assertions)).toContain(expectedKind);
  });

  it.each([
    ["=", "="],
    ["!=", "!="],
    ["<", "<"],
    ["<=", "<="],
    [">", ">"],
    [">=", ">="],
  ])("maps comparison operator %s to Cmp", (specOp, z3Op) => {
    const script = scriptFrom(service(`invariant: 1 ${specOp} 2`));
    const cmp = findAssertion(script, (e) => e.kind === "Cmp");
    expect(cmp).toBeDefined();
    expect(cmp && cmp.kind === "Cmp" && cmp.op).toBe(z3Op);
  });

  it.each([
    ["+", "+"],
    ["-", "-"],
    ["*", "*"],
    ["/", "/"],
  ])("maps arithmetic operator %s to Arith", (specOp, z3Op) => {
    const script = scriptFrom(service(`invariant: (1 ${specOp} 2) = 0`));
    const arith = JSON.stringify(script.assertions).match(/"Arith"/g);
    expect(arith).not.toBeNull();
    const root = script.assertions[0];
    expect(root.kind).toBe("Cmp");
    if (root.kind === "Cmp" && root.lhs.kind === "Arith") {
      expect(root.lhs.op).toBe(z3Op);
    }
  });
});

describe("translator — logical connectives", () => {
  it.each([
    ["and", "And"],
    ["or", "Or"],
    ["implies", "Implies"],
  ])("%s maps to %s kind", (specOp, kind) => {
    const script = scriptFrom(service(`invariant: true ${specOp} false`));
    expect(script.assertions[0].kind).toBe(kind);
  });

  it("not maps to Not", () => {
    const script = scriptFrom(service(`invariant: not true`));
    expect(script.assertions[0].kind).toBe("Not");
  });

  it("iff is expanded to bidirectional implication", () => {
    const script = scriptFrom(service(`invariant: true iff false`));
    expect(script.assertions[0].kind).toBe("And");
  });
});

describe("translator — entity invariants and field constraints", () => {
  it("entity invariant becomes forall over the entity sort", () => {
    const script = scriptFrom(
      service(`
        entity X {
          v: Int where value >= 0
        }
      `),
    );
    const q = findAssertion(script, (e) => e.kind === "Quantifier");
    expect(q?.kind).toBe("Quantifier");
    expect(q?.kind === "Quantifier" && q.q).toBe("ForAll");
    expect(q?.kind === "Quantifier" && q.bindings[0].sort.kind === "Uninterp").toBe(true);
  });

  it("self-bound fields in entity invariants resolve to field-function applications", () => {
    const script = scriptFrom(
      service(`
        entity UrlMapping {
          click_count: Int where value >= 0
          invariant: click_count >= 0
        }
      `),
    );
    const uclick = findFunc(script, "UrlMapping_click_count");
    expect(uclick).toBeDefined();
    expect(uclick?.resultSort.kind).toBe("Int");
    expect(uclick?.argSorts[0].kind === "Uninterp" && uclick.argSorts[0].name).toBe("UrlMapping");
  });
});

describe("translator — state relations", () => {
  it("emits dom and map functions for relational state fields", () => {
    const script = scriptFrom(
      service(`
        entity Mapping { value: Int }
        state { store: Int -> lone Mapping }
      `),
    );
    expect(findFunc(script, "store_dom")).toBeDefined();
    expect(findFunc(script, "store_map")).toBeDefined();
  });

  it("multiplicity 'one' emits totality axiom", () => {
    const script = scriptFrom(
      service(`
        entity Mapping { value: Int }
        state { store: Int -> one Mapping }
      `),
    );
    const totalityQ = script.assertions.find(
      (e) =>
        e.kind === "Quantifier" &&
        e.q === "ForAll" &&
        e.body.kind === "App" &&
        e.body.func === "store_dom",
    );
    expect(totalityQ).toBeDefined();
  });
});

describe("translator — quantifiers over state relations", () => {
  it("'all c in store | ...' becomes forall c. dom(c) implies body", () => {
    const script = scriptFrom(
      service(`
        entity V { value: Int }
        state { store: Int -> lone V }
        invariant: all c in store | true
      `),
    );
    const q = findAssertion(script, (e) => e.kind === "Quantifier" && e.q === "ForAll");
    expect(q?.kind === "Quantifier" && q.body.kind).toBe("Implies");
  });
});

describe("translator — calls, fields, indexing, matches", () => {
  it("Call isValidURI(x) declares an uninterpreted Bool predicate", () => {
    scriptFrom(
      service(`
        type U = String where isValidURI(value)
      `),
    );
  });

  it("Matches declares a fresh uninterpreted Bool predicate", () => {
    const script = scriptFrom(
      service(`
        type C = String where value matches /^[a-z]+$/
      `),
    );
    const matchesDecl = script.funcs.find((f) => f.name.startsWith("matches_"));
    expect(matchesDecl).toBeDefined();
    expect(matchesDecl?.resultSort.kind).toBe("Bool");
  });

  it("Index rel[k] renders as rel_map(k)", () => {
    const script = scriptFrom(
      service(`
        entity V { value: Int }
        state { store: Int -> lone V }
        invariant: all c in store | store[c] = store[c]
      `),
    );
    expect(JSON.stringify(script.assertions)).toContain(`"store_map"`);
  });

  it("FieldAccess on entity resolves to its declared field function", () => {
    const script = scriptFrom(
      service(`
        entity UrlMapping {
          click_count: Int
        }
        state { metadata: Int -> lone UrlMapping }
        invariant: all c in metadata | metadata[c].click_count >= 0
      `),
    );
    expect(JSON.stringify(script.assertions)).toContain(`"UrlMapping_click_count"`);
  });
});

describe("translator — enums", () => {
  it("enum members become nullary functions with pairwise distinctness", () => {
    const script = scriptFrom(
      service(`
        enum Status { Pending, Done }
      `),
    );
    expect(findFunc(script, "Status_Pending")).toBeDefined();
    expect(findFunc(script, "Status_Done")).toBeDefined();
    const distinctness = script.assertions.find(
      (e) =>
        (e.kind === "Not" && e.arg.kind === "Cmp") ||
        (e.kind === "And" &&
          e.args.some((a) => a.kind === "Not" && a.arg.kind === "Cmp")),
    );
    expect(distinctness).toBeDefined();
  });
});

describe("translator — primitive-underlying type aliases", () => {
  it("type Positive = Int where value > 0 translates without throwing", () => {
    expect(() =>
      scriptFrom(service(`type Positive = Int where value > 0`)),
    ).not.toThrow();
  });

  it("entity field of primitive-alias type inlines the alias refinement as an axiom", () => {
    const script = scriptFrom(
      service(`
        type Positive = Int where value > 0
        entity E { n: Positive }
      `),
    );
    const en = findFunc(script, "E_n");
    expect(en?.resultSort.kind).toBe("Int");
    const refinement = script.assertions.find(
      (a) =>
        a.kind === "Quantifier" &&
        a.q === "ForAll" &&
        a.body.kind === "Cmp" &&
        a.body.op === ">" &&
        a.body.lhs.kind === "App" &&
        a.body.lhs.func === "E_n",
    );
    expect(refinement).toBeDefined();
  });

  it("type Flag = Bool where value = true translates to Bool underlying sort", () => {
    const script = scriptFrom(
      service(`
        type Flag = Bool where value = true
        entity E { f: Flag }
      `),
    );
    const ef = findFunc(script, "E_f");
    expect(ef?.resultSort.kind).toBe("Bool");
  });

  it("type Wrapper = Int without a where-clause uses Int sort directly", () => {
    const script = scriptFrom(
      service(`
        type Wrapper = Int
        entity E { v: Wrapper }
      `),
    );
    const ev = findFunc(script, "E_v");
    expect(ev?.resultSort.kind).toBe("Int");
  });
});

describe("translator — string literal distinctness", () => {
  it("adds pairwise distinctness when a spec uses two or more string literals", () => {
    const script = scriptFrom(
      service(`
        invariant: "a" = "a"
        invariant: "b" = "b"
      `),
    );
    const hasDistinctness = JSON.stringify(script.assertions).includes(`"op":"!="`);
    expect(hasDistinctness).toBe(true);
  });

  it("adds no distinctness axiom when there is at most one string literal", () => {
    const script = scriptFrom(
      service(`
        invariant: "a" = "a"
      `),
    );
    const hasDistinctness = JSON.stringify(script.assertions).includes(`"op":"!="`);
    expect(hasDistinctness).toBe(false);
  });
});

describe("translator — out-of-scope kinds throw TranslatorError", () => {
  it("Prime throws", () => {
    expect(() =>
      scriptFrom(
        service(`
          state { x: Int }
          operation Op { ensures: x' = x }
          invariant: x' = x
        `),
      ),
    ).toThrow(TranslatorError);
  });

  it("With throws", () => {
    expect(() =>
      scriptFrom(
        service(`
          entity E { a: Int }
          invariant: (E with { a = 1 }) = (E with { a = 1 })
        `),
      ),
    ).toThrow(TranslatorError);
  });

  it("Pre throws", () => {
    expect(() =>
      scriptFrom(
        service(`
          state { x: Int }
          invariant: pre(x) = 0
        `),
      ),
    ).toThrow(TranslatorError);
  });
});

describe("translator — state refinement for primitive aliases", () => {
  it("state const of primitive-alias type asserts the alias constraint", () => {
    const script = scriptFrom(
      service(`
        type Positive = Int where value > 0
        state { x: Positive }
      `),
    );
    const refinement = script.assertions.find(
      (a) =>
        a.kind === "Cmp" &&
        a.op === ">" &&
        a.lhs.kind === "App" &&
        a.lhs.func === "state_x",
    );
    expect(refinement).toBeDefined();
  });

  it("state relation map of primitive-alias value type asserts the alias constraint under dom", () => {
    const script = scriptFrom(
      service(`
        type Positive = Int where value > 0
        state { r: Int -> lone Positive }
      `),
    );
    const refinement = script.assertions.find(
      (a) =>
        a.kind === "Quantifier" &&
        a.q === "ForAll" &&
        a.body.kind === "Implies" &&
        a.body.lhs.kind === "App" &&
        a.body.lhs.func === "r_dom" &&
        a.body.rhs.kind === "Cmp" &&
        a.body.rhs.op === ">",
    );
    expect(refinement).toBeDefined();
  });

  it("total state relation (multiplicity one) of primitive-alias type drops the dom guard", () => {
    const script = scriptFrom(
      service(`
        type Positive = Int where value > 0
        state { r: Int -> one Positive }
      `),
    );
    const refinement = script.assertions.find(
      (a) =>
        a.kind === "Quantifier" &&
        a.q === "ForAll" &&
        a.body.kind === "Cmp" &&
        a.body.op === ">" &&
        a.body.lhs.kind === "App" &&
        a.body.lhs.func === "r_map",
    );
    expect(refinement).toBeDefined();
  });
});

describe("translator — cardinality", () => {
  it("cardinality on a state relation declares an uninterpreted Int with a ≥ 0 axiom", () => {
    const script = scriptFrom(
      service(`
        entity V { value: Int }
        state { store: Int -> lone V }
        invariant: #store >= 0
      `),
    );
    const cardFunc = script.funcs.find((f) => f.name === "card_store");
    expect(cardFunc).toBeDefined();
    expect(cardFunc?.resultSort.kind).toBe("Int");
    const geZero = script.assertions.find(
      (a) =>
        a.kind === "Cmp" &&
        a.op === ">=" &&
        a.lhs.kind === "App" &&
        a.lhs.func === "card_store" &&
        a.rhs.kind === "IntLit" &&
        a.rhs.value === 0,
    );
    expect(geZero).toBeDefined();
  });

  it("cardinality on a non-state identifier throws", () => {
    expect(() =>
      scriptFrom(
        service(`
          entity V { value: Int }
          invariant: #V >= 0
        `),
      ),
    ).toThrow(TranslatorError);
  });
});

describe("translator — operation requires", () => {
  const src = `service T {
    entity Item { value: Int }
    state { store: Int -> lone Item }
    operation Put {
      input: key: Int
      requires: key > 0
    }
    operation Fetch {
      input: key: Int
      requires: key in store
    }
    invariant: true
  }`;

  it("translateOperationRequires declares typed input constants", () => {
    const ir = irFrom(src);
    const op = ir.operations.find((o) => o.name === "Put")!;
    const script = translateOperationRequires(ir, op);
    const input = script.funcs.find((f) => f.name === "input_Put_key");
    expect(input).toBeDefined();
    expect(input?.resultSort.kind).toBe("Int");
    expect(input?.argSorts.length).toBe(0);
  });

  it("translateOperationRequires omits top-level invariants", () => {
    const ir = irFrom(src);
    const op = ir.operations.find((o) => o.name === "Put")!;
    const script = translateOperationRequires(ir, op);
    const hasTopLevelTrue = script.assertions.some(
      (a) => a.kind === "BoolLit" && a.value === true,
    );
    expect(hasTopLevelTrue).toBe(false);
  });

  it("translateOperationEnabled includes top-level invariants plus requires", () => {
    const ir = irFrom(src);
    const op = ir.operations.find((o) => o.name === "Put")!;
    const script = translateOperationEnabled(ir, op);
    const hasTopLevelTrue = script.assertions.some(
      (a) => a.kind === "BoolLit" && a.value === true,
    );
    expect(hasTopLevelTrue).toBe(true);
    const requiresCmp = script.assertions.some(
      (a) =>
        a.kind === "Cmp" &&
        a.op === ">" &&
        a.lhs.kind === "App" &&
        a.lhs.func === "input_Put_key",
    );
    expect(requiresCmp).toBe(true);
  });

  it("operation requires can reference state-relation membership", () => {
    const ir = irFrom(src);
    const op = ir.operations.find((o) => o.name === "Fetch")!;
    const script = translateOperationRequires(ir, op);
    const membership = script.assertions.some(
      (a) =>
        a.kind === "App" &&
        a.func === "store_dom" &&
        a.args.length === 1 &&
        a.args[0].kind === "App" &&
        a.args[0].func === "input_Fetch_key",
    );
    expect(membership).toBe(true);
  });
});

describe("translator — determinism", () => {
  it("translate is pure: two runs over the same IR produce identical scripts", () => {
    const src = service(`
      entity X { v: Int where value >= 0 }
      state { xs: Int -> one X }
      invariant: all x in xs | xs[x].v >= 0
    `);
    const a = scriptFrom(src);
    const b = scriptFrom(src);
    expect(JSON.stringify(a)).toBe(JSON.stringify(b));
  });
});

describe("translator — url_shortener integration", () => {
  it("translates the fixture without throwing", () => {
    const src = readFileSync(
      join(import.meta.dirname, "../parser/fixtures/url_shortener.spec"),
      "utf-8",
    );
    const { tree, errors } = parseSpec(src);
    expect(errors).toEqual([]);
    const script = translate(buildIR(tree));
    expect(script.sorts.length).toBeGreaterThan(0);
    expect(script.funcs.length).toBeGreaterThan(0);
    expect(script.assertions.length).toBeGreaterThan(0);
    expect(findFunc(script, "UrlMapping_click_count")).toBeDefined();
    expect(findFunc(script, "store_map")).toBeDefined();
    expect(findFunc(script, "metadata_map")).toBeDefined();
  });
});
