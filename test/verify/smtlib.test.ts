import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { translate } from "#verify/translator.js";
import { renderExpr, renderSmtLib } from "#verify/smtlib.js";
import { Z3_BOOL, Z3_INT, uninterp, type Z3Expr, type Z3Script } from "#verify/script.js";

describe("smtlib — expression rendering", () => {
  it.each<[string, Z3Expr, string]>([
    ["int literal", { kind: "IntLit", value: 42 }, "42"],
    [
      "negative int literal uses unary minus",
      { kind: "IntLit", value: -5 },
      "(- 5)",
    ],
    ["bool literal", { kind: "BoolLit", value: true }, "true"],
    ["nullary App", { kind: "App", func: "f", args: [] }, "f"],
    [
      "binary App",
      { kind: "App", func: "f", args: [{ kind: "IntLit", value: 1 }, { kind: "IntLit", value: 2 }] },
      "(f 1 2)",
    ],
    [
      "empty And is true",
      { kind: "And", args: [] },
      "true",
    ],
    [
      "singleton And is the arg",
      { kind: "And", args: [{ kind: "BoolLit", value: true }] },
      "true",
    ],
    [
      "And of two is a proper and form",
      {
        kind: "And",
        args: [
          { kind: "BoolLit", value: true },
          { kind: "BoolLit", value: false },
        ],
      },
      "(and true false)",
    ],
    [
      "Or empty is false",
      { kind: "Or", args: [] },
      "false",
    ],
    [
      "Not wraps arg",
      { kind: "Not", arg: { kind: "BoolLit", value: true } },
      "(not true)",
    ],
    [
      "Implies uses =>",
      {
        kind: "Implies",
        lhs: { kind: "BoolLit", value: true },
        rhs: { kind: "BoolLit", value: false },
      },
      "(=> true false)",
    ],
    [
      "Cmp = uses =",
      {
        kind: "Cmp",
        op: "=",
        lhs: { kind: "IntLit", value: 0 },
        rhs: { kind: "IntLit", value: 1 },
      },
      "(= 0 1)",
    ],
    [
      "Cmp != renders as distinct",
      {
        kind: "Cmp",
        op: "!=",
        lhs: { kind: "IntLit", value: 0 },
        rhs: { kind: "IntLit", value: 1 },
      },
      "(distinct 0 1)",
    ],
    [
      "Arith + renders infix-prefix",
      {
        kind: "Arith",
        op: "+",
        args: [{ kind: "IntLit", value: 1 }, { kind: "IntLit", value: 2 }],
      },
      "(+ 1 2)",
    ],
    [
      "Quantifier ForAll with one binder",
      {
        kind: "Quantifier",
        q: "ForAll",
        bindings: [{ name: "x", sort: uninterp("X") }],
        body: { kind: "BoolLit", value: true },
      },
      "(forall ((x X)) true)",
    ],
  ])("%s renders as %s", (_label, expr, expected) => {
    expect(renderExpr(expr)).toBe(expected);
  });
});

describe("smtlib — script rendering shape", () => {
  it("emits the standard prelude", () => {
    const out = renderSmtLib({ sorts: [], funcs: [], assertions: [] });
    expect(out).toContain("(set-logic ALL)");
    expect(out).toContain("(set-option :produce-models true)");
    expect(out.trim().endsWith("(check-sat)")).toBe(true);
  });

  it("emits timeout option when given", () => {
    const out = renderSmtLib({ sorts: [], funcs: [], assertions: [] }, 2_500);
    expect(out).toContain("(set-option :timeout 2500)");
  });

  it("omits timeout option when unset or zero", () => {
    expect(renderSmtLib({ sorts: [], funcs: [], assertions: [] }, 0)).not.toContain(":timeout");
    expect(renderSmtLib({ sorts: [], funcs: [], assertions: [] })).not.toContain(":timeout");
  });

  it("declares sorts and funcs", () => {
    const out = renderSmtLib({
      sorts: [uninterp("User")],
      funcs: [
        {
          kind: "FuncDecl",
          name: "age",
          argSorts: [uninterp("User")],
          resultSort: Z3_INT,
        },
      ],
      assertions: [
        {
          kind: "Quantifier",
          q: "ForAll",
          bindings: [{ name: "u", sort: uninterp("User") }],
          body: {
            kind: "Cmp",
            op: ">=",
            lhs: { kind: "App", func: "age", args: [{ kind: "Var", name: "u", sort: uninterp("User") }] },
            rhs: { kind: "IntLit", value: 0 },
          },
        },
      ],
    });
    expect(out).toContain("(declare-sort User 0)");
    expect(out).toContain("(declare-fun age (User) Int)");
    expect(out).toContain("(assert (forall ((u User)) (>= (age u) 0)))");
  });
});

function loadScript(fixture: string): Z3Script {
  const src = readFileSync(
    join(import.meta.dirname, "../parser/fixtures", fixture),
    "utf-8",
  );
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return translate(buildIR(tree));
}

describe("smtlib — deterministic output for url_shortener", () => {
  it("two renders are byte-identical", () => {
    const script = loadScript("url_shortener.spec");
    const a = renderSmtLib(script);
    const b = renderSmtLib(script);
    expect(a).toBe(b);
  });

  it("contains the hand-picked highlights", () => {
    const out = renderSmtLib(loadScript("url_shortener.spec"));
    expect(out).toContain("(declare-sort UrlMapping 0)");
    expect(out).toContain("(declare-fun UrlMapping_click_count (UrlMapping) Int)");
    expect(out).toContain("(declare-fun store_dom");
    expect(out).toContain("(declare-fun store_map");
    expect(out).toContain("(check-sat)");
  });
});

function writeIfAbsent(path: string, content: string): void {
  const { existsSync, writeFileSync } = require("node:fs") as typeof import("node:fs");
  if (!existsSync(path)) writeFileSync(path, content, "utf-8");
}

describe("smtlib — url_shortener snapshot", () => {
  it("matches the checked-in SMT-LIB fixture byte-for-byte", () => {
    const out = renderSmtLib(loadScript("url_shortener.spec"));
    const snapshotPath = join(import.meta.dirname, "fixtures", "url_shortener.smt2");
    writeIfAbsent(snapshotPath, out);
    const expected = readFileSync(snapshotPath, "utf-8");
    expect(out).toBe(expected);
    // silence unused imports
    expect(Z3_BOOL.kind).toBe("Bool");
  });
});
