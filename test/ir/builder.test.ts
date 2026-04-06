import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR, validateServiceIR, serializeIR, deserializeIR } from "#ir/index.js";
import type { ServiceIR, Expr } from "#ir/index.js";

// ─── Helpers ────────────────────────────────────────────────

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function loadFixture(name: string): string {
  return readFileSync(join(fixtureDir, name), "utf-8");
}

function buildFromSource(src: string): ServiceIR {
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

function buildFixture(name: string): ServiceIR {
  return buildFromSource(loadFixture(name));
}

/** Parse a single expression by wrapping it in a minimal service. */
function parseExpr(exprSrc: string): Expr {
  const ir = buildFromSource(`service T { invariant: ${exprSrc} }`);
  return ir.invariants[0].expr;
}

// ─── End-to-end fixture tests ───────────────────────────────

describe("buildIR — fixture round-trips", () => {
  const fixtures = [
    "url_shortener.spec",
    "todo_list.spec",
    "auth_service.spec",
    "ecommerce.spec",
    "edge_cases.spec",
  ];

  for (const name of fixtures) {
    it(`builds valid IR from ${name}`, () => {
      const ir = buildFixture(name);
      // Should not throw — validates the full IR recursively
      expect(() => validateServiceIR(ir)).not.toThrow();
    });

    it(`round-trips ${name} through JSON serialization`, () => {
      const ir = buildFixture(name);
      const json = serializeIR(ir);
      const restored = deserializeIR(json);
      expect(restored).toEqual(ir);
    });
  }
});

// ─── url_shortener.spec structural tests ────────────────────

describe("buildIR — url_shortener structural", () => {
  const ir = buildFixture("url_shortener.spec");

  it("has correct service name", () => {
    expect(ir.name).toBe("UrlShortener");
  });

  it("has 3 type aliases", () => {
    expect(ir.typeAliases).toHaveLength(3);
    expect(ir.typeAliases.map(t => t.name)).toEqual(["ShortCode", "LongURL", "BaseURL"]);
  });

  it("ShortCode constraint is nested BinaryOp with Matches", () => {
    const sc = ir.typeAliases[0];
    expect(sc.constraint).not.toBeNull();
    expect(sc.constraint!.kind).toBe("BinaryOp");
    // Drill into rightmost leaf: should be Matches
    let node = sc.constraint!;
    while (node.kind === "BinaryOp" && node.op === "and") {
      node = node.right;
    }
    expect(node.kind).toBe("Matches");
    if (node.kind === "Matches") {
      expect(node.pattern).toBe("^[a-zA-Z0-9]+$");
    }
  });

  it("has 1 entity with 4 fields and 1 invariant", () => {
    expect(ir.entities).toHaveLength(1);
    const e = ir.entities[0];
    expect(e.name).toBe("UrlMapping");
    expect(e.fields).toHaveLength(4);
    expect(e.invariants).toHaveLength(1);
    expect(e.invariants[0].kind).toBe("Call");
  });

  it("has state with 3 fields including relations", () => {
    expect(ir.state).not.toBeNull();
    expect(ir.state!.fields).toHaveLength(3);
    const store = ir.state!.fields[0];
    expect(store.typeExpr.kind).toBe("RelationType");
    if (store.typeExpr.kind === "RelationType") {
      expect(store.typeExpr.multiplicity).toBe("lone");
    }
  });

  it("has 4 operations", () => {
    expect(ir.operations).toHaveLength(4);
    expect(ir.operations.map(o => o.name)).toEqual(["Shorten", "Resolve", "Delete", "ListAll"]);
  });

  it("Shorten operation has correct input/output/requires/ensures", () => {
    const shorten = ir.operations[0];
    expect(shorten.inputs).toHaveLength(1);
    expect(shorten.inputs[0].name).toBe("url");
    expect(shorten.outputs).toHaveLength(2);
    expect(shorten.requires).toHaveLength(1);
    expect(shorten.ensures.length).toBeGreaterThanOrEqual(4);
  });

  it("has 3 global invariants", () => {
    expect(ir.invariants).toHaveLength(3);
    expect(ir.invariants[0].name).toBe("allURLsValid");
  });

  it("conventions have qualifier for Location header", () => {
    expect(ir.conventions).not.toBeNull();
    const locationRule = ir.conventions!.rules.find(r => r.qualifier === "Location");
    expect(locationRule).toBeDefined();
    expect(locationRule!.target).toBe("Resolve");
    expect(locationRule!.property).toBe("http_header");
    expect(locationRule!.value.kind).toBe("FieldAccess");
  });
});

// ─── todo_list.spec structural tests ────────────────────────

describe("buildIR — todo_list structural", () => {
  const ir = buildFixture("todo_list.spec");

  it("has correct service name", () => {
    expect(ir.name).toBe("TodoList");
  });

  it("has 2 enums with correct values", () => {
    expect(ir.enums).toHaveLength(2);
    const status = ir.enums[0];
    expect(status.name).toBe("Status");
    expect(status.values).toEqual(["TODO", "IN_PROGRESS", "DONE", "ARCHIVED"]);
    const priority = ir.enums[1];
    expect(priority.name).toBe("Priority");
    expect(priority.values).toEqual(["LOW", "MEDIUM", "HIGH", "URGENT"]);
  });

  it("Todo entity has 3 invariants", () => {
    const todo = ir.entities[0];
    expect(todo.name).toBe("Todo");
    expect(todo.invariants).toHaveLength(3);
  });

  it("has 1 transition with 6 rules", () => {
    expect(ir.transitions).toHaveLength(1);
    const t = ir.transitions[0];
    expect(t.name).toBe("TodoLifecycle");
    expect(t.entityName).toBe("Todo");
    expect(t.fieldName).toBe("status");
    expect(t.rules).toHaveLength(6);
    // Last rule has a guard (when clause)
    const lastRule = t.rules[5];
    expect(lastRule.from).toBe("DONE");
    expect(lastRule.to).toBe("IN_PROGRESS");
    expect(lastRule.via).toBe("Reopen");
    expect(lastRule.guard).not.toBeNull();
  });

  it("has 9 operations", () => {
    expect(ir.operations).toHaveLength(9);
  });

  it("Complete operation ensures use With + SomeWrap", () => {
    const complete = ir.operations.find(o => o.name === "Complete")!;
    // todo = pre(todos)[id] with { status = DONE, completed_at = some(now()) }
    const firstEnsure = complete.ensures[0];
    expect(firstEnsure.kind).toBe("BinaryOp");
    if (firstEnsure.kind === "BinaryOp") {
      const rhs = firstEnsure.right;
      expect(rhs.kind).toBe("With");
    }
  });
});

// ─── edge_cases.spec structural tests ───────────────────────

describe("buildIR — edge_cases structural", () => {
  const ir = buildFixture("edge_cases.spec");

  it("has entity with extends", () => {
    const child = ir.entities.find(e => e.name === "Child")!;
    expect(child.extends_).toBe("Base");
  });

  it("has enum with trailing comma", () => {
    const color = ir.enums[0];
    expect(color.name).toBe("Color");
    expect(color.values).toEqual(["RED", "GREEN", "BLUE"]);
  });

  it("state has all 4 multiplicities", () => {
    expect(ir.state).not.toBeNull();
    const fields = ir.state!.fields;
    const rels = fields.filter(f => f.typeExpr.kind === "RelationType");
    const mults = rels.map(r =>
      r.typeExpr.kind === "RelationType" ? r.typeExpr.multiplicity : null,
    );
    expect(mults).toContain("one");
    expect(mults).toContain("lone");
    expect(mults).toContain("some");
    expect(mults).toContain("set");
  });

  it("state has Map, Seq, Option types", () => {
    const fields = ir.state!.fields;
    expect(fields.find(f => f.typeExpr.kind === "MapType")).toBeDefined();
    expect(fields.find(f => f.typeExpr.kind === "SeqType")).toBeDefined();
    expect(fields.find(f => f.typeExpr.kind === "OptionType")).toBeDefined();
  });

  it("has function declaration", () => {
    expect(ir.functions).toHaveLength(1);
    const f = ir.functions[0];
    expect(f.name).toBe("clamp");
    expect(f.params).toHaveLength(3);
    expect(f.body.kind).toBe("If");
  });

  it("has predicate declaration", () => {
    expect(ir.predicates).toHaveLength(1);
    const p = ir.predicates[0];
    expect(p.name).toBe("isPositive");
    expect(p.params).toHaveLength(1);
    expect(p.body.kind).toBe("BinaryOp");
  });

  it("has fact declaration", () => {
    expect(ir.facts).toHaveLength(1);
    expect(ir.facts[0].name).toBe("someFact");
  });

  it("has type alias with regex constraint", () => {
    const email = ir.typeAliases[0];
    expect(email.name).toBe("Email");
    expect(email.constraint).not.toBeNull();
    expect(email.constraint!.kind).toBe("Matches");
    if (email.constraint!.kind === "Matches") {
      expect(email.constraint!.pattern).toBe("^[^@]+@[^@]+\\.[^@]+$");
    }
  });

  it("transition has guard condition", () => {
    const t = ir.transitions[0];
    const guardedRule = t.rules.find(r => r.guard !== null)!;
    expect(guardedRule).toBeDefined();
    expect(guardedRule.from).toBe("GREEN");
    expect(guardedRule.via).toBe("NoOutput");
  });
});

// ─── auth_service.spec structural tests ─────────────────────

describe("buildIR — auth_service structural", () => {
  const ir = buildFixture("auth_service.spec");

  it("has correct service name and counts", () => {
    expect(ir.name).toBe("AuthService");
    expect(ir.typeAliases).toHaveLength(4);
    expect(ir.enums).toHaveLength(1);
    expect(ir.entities).toHaveLength(3);
    expect(ir.operations).toHaveLength(7);
    expect(ir.invariants).toHaveLength(6);
    expect(ir.facts).toHaveLength(1);
  });

  it("Login ensures uses Let expression", () => {
    const login = ir.operations.find(o => o.name === "Login")!;
    // The ensures clause starts with a let
    expect(login.ensures.some(e => e.kind === "Let")).toBe(true);
  });

  it("RefreshToken uses The expression", () => {
    const refresh = ir.operations.find(o => o.name === "RefreshToken")!;
    expect(refresh.ensures.some(e => e.kind === "Let")).toBe(true);
    // Deep check: the let's value should be a The
    const letExpr = refresh.ensures.find(e => e.kind === "Let")!;
    if (letExpr.kind === "Let") {
      expect(letExpr.value.kind).toBe("The");
    }
  });
});

// ─── ecommerce.spec structural tests ────────────────────────

describe("buildIR — ecommerce structural", () => {
  const ir = buildFixture("ecommerce.spec");

  it("has correct service name and counts", () => {
    expect(ir.name).toBe("OrderService");
    expect(ir.typeAliases).toHaveLength(5);
    expect(ir.enums).toHaveLength(2);
    expect(ir.entities).toHaveLength(5);
    expect(ir.operations).toHaveLength(11);
    expect(ir.invariants).toHaveLength(6);
    expect(ir.transitions).toHaveLength(1);
  });

  it("AddLineItem uses nested Let expressions", () => {
    const addLine = ir.operations.find(o => o.name === "AddLineItem")!;
    const letExpr = addLine.ensures.find(e => e.kind === "Let");
    expect(letExpr).toBeDefined();
    // Nested let: let product = ... in let item = ... in ...
    if (letExpr?.kind === "Let") {
      expect(letExpr.body.kind).toBe("Let");
    }
  });

  it("Order entity invariant uses Lambda", () => {
    const order = ir.entities.find(e => e.name === "Order")!;
    // invariant: subtotal = sum(items, i => i.line_total)
    const sumInvariant = order.invariants[0];
    expect(sumInvariant.kind).toBe("BinaryOp");
    if (sumInvariant.kind === "BinaryOp") {
      expect(sumInvariant.right.kind).toBe("Call");
      if (sumInvariant.right.kind === "Call") {
        expect(sumInvariant.right.args[1].kind).toBe("Lambda");
      }
    }
  });

  it("OrderLifecycle transition has conditional guards", () => {
    const t = ir.transitions[0];
    expect(t.name).toBe("OrderLifecycle");
    const guarded = t.rules.filter(r => r.guard !== null);
    expect(guarded.length).toBeGreaterThanOrEqual(3);
  });
});

// ─── Expression coverage ────────────────────────────────────

describe("buildIR — expression kinds", () => {
  it("parses binary arithmetic", () => {
    expect(parseExpr("1 + 2").kind).toBe("BinaryOp");
    expect(parseExpr("3 * 4").kind).toBe("BinaryOp");
    expect(parseExpr("5 - 1").kind).toBe("BinaryOp");
    expect(parseExpr("10 / 2").kind).toBe("BinaryOp");
  });

  it("parses comparisons", () => {
    for (const [src, op] of [
      ["x = y", "="],
      ["x != y", "!="],
      ["x < y", "<"],
      ["x > y", ">"],
      ["x <= y", "<="],
      ["x >= y", ">="],
    ] as const) {
      const e = parseExpr(src);
      expect(e.kind).toBe("BinaryOp");
      if (e.kind === "BinaryOp") expect(e.op).toBe(op);
    }
  });

  it("parses logical operators", () => {
    const and = parseExpr("a and b");
    expect(and.kind).toBe("BinaryOp");
    if (and.kind === "BinaryOp") expect(and.op).toBe("and");

    const or = parseExpr("a or b");
    if (or.kind === "BinaryOp") expect(or.op).toBe("or");

    const impl = parseExpr("a implies b");
    if (impl.kind === "BinaryOp") expect(impl.op).toBe("implies");

    const iff = parseExpr("a iff b");
    if (iff.kind === "BinaryOp") expect(iff.op).toBe("iff");
  });

  it("parses set operations", () => {
    expect(parseExpr("a union b").kind).toBe("BinaryOp");
    expect(parseExpr("a intersect b").kind).toBe("BinaryOp");
    expect(parseExpr("a minus b").kind).toBe("BinaryOp");
  });

  it("parses containment", () => {
    const inE = parseExpr("x in s");
    expect(inE.kind).toBe("BinaryOp");
    if (inE.kind === "BinaryOp") expect(inE.op).toBe("in");

    const notIn = parseExpr("x not in s");
    expect(notIn.kind).toBe("BinaryOp");
    if (notIn.kind === "BinaryOp") expect(notIn.op).toBe("not_in");

    const sub = parseExpr("a subset b");
    expect(sub.kind).toBe("BinaryOp");
    if (sub.kind === "BinaryOp") expect(sub.op).toBe("subset");
  });

  it("parses unary operators", () => {
    const not = parseExpr("not x");
    expect(not.kind).toBe("UnaryOp");
    if (not.kind === "UnaryOp") expect(not.op).toBe("not");

    const neg = parseExpr("-x");
    if (neg.kind === "UnaryOp") expect(neg.op).toBe("negate");

    const card = parseExpr("#s");
    if (card.kind === "UnaryOp") expect(card.op).toBe("cardinality");

    const pow = parseExpr("^s");
    if (pow.kind === "UnaryOp") expect(pow.op).toBe("power");
  });

  it("parses field access and index", () => {
    const fa = parseExpr("x.y");
    expect(fa.kind).toBe("FieldAccess");
    if (fa.kind === "FieldAccess") expect(fa.field).toBe("y");

    const idx = parseExpr("x[y]");
    expect(idx.kind).toBe("Index");
  });

  it("parses function call", () => {
    const call = parseExpr("f(x, y)");
    expect(call.kind).toBe("Call");
    if (call.kind === "Call") expect(call.args).toHaveLength(2);

    const noArgs = parseExpr("f()");
    if (noArgs.kind === "Call") expect(noArgs.args).toHaveLength(0);
  });

  it("parses prime (post-state)", () => {
    const p = parseExpr("store'");
    expect(p.kind).toBe("Prime");
  });

  it("parses pre()", () => {
    const p = parseExpr("pre(store)");
    expect(p.kind).toBe("Pre");
  });

  it("parses enum access", () => {
    const ea = parseExpr("Status.DONE");
    expect(ea.kind).toBe("EnumAccess");
    if (ea.kind === "EnumAccess") {
      expect(ea.member).toBe("DONE");
    }
  });

  it("parses literals", () => {
    const i = parseExpr("42");
    expect(i.kind).toBe("IntLit");
    if (i.kind === "IntLit") expect(i.value).toBe(42);

    const f = parseExpr("3.14");
    expect(f.kind).toBe("FloatLit");
    if (f.kind === "FloatLit") expect(f.value).toBeCloseTo(3.14);

    const s = parseExpr('"hello"');
    expect(s.kind).toBe("StringLit");
    if (s.kind === "StringLit") expect(s.value).toBe("hello");

    expect(parseExpr("true").kind).toBe("BoolLit");
    expect(parseExpr("false").kind).toBe("BoolLit");
    expect(parseExpr("none").kind).toBe("NoneLit");
  });

  it("parses identifiers", () => {
    const lower = parseExpr("foo");
    expect(lower.kind).toBe("Identifier");
    if (lower.kind === "Identifier") expect(lower.name).toBe("foo");

    const upper = parseExpr("Foo");
    expect(upper.kind).toBe("Identifier");
    if (upper.kind === "Identifier") expect(upper.name).toBe("Foo");
  });

  it("parses set literal", () => {
    const e = parseExpr("{1, 2, 3}");
    expect(e.kind).toBe("SetLiteral");
    if (e.kind === "SetLiteral") expect(e.elements).toHaveLength(3);
  });

  it("parses empty set literal", () => {
    // Use it inside an equality to avoid ambiguity with service body
    const ir = buildFromSource(`service T {
      operation O {
        output: s: Set[Int]
        requires: true
        ensures: s = {}
      }
    }`);
    const eq = ir.operations[0].ensures[0];
    expect(eq.kind).toBe("BinaryOp");
    if (eq.kind === "BinaryOp") {
      expect(eq.right.kind).toBe("SetLiteral");
      if (eq.right.kind === "SetLiteral") expect(eq.right.elements).toHaveLength(0);
    }
  });

  it("parses map literal", () => {
    const ir = buildFromSource(`service T {
      operation O {
        output: m: Map[String, Int]
        requires: true
        ensures: m = {"a" -> 1, "b" -> 2}
      }
    }`);
    const eq = ir.operations[0].ensures[0];
    if (eq.kind === "BinaryOp") {
      expect(eq.right.kind).toBe("MapLiteral");
      if (eq.right.kind === "MapLiteral") expect(eq.right.entries).toHaveLength(2);
    }
  });

  it("parses sequence literal", () => {
    const ir = buildFromSource(`service T {
      operation O {
        output: s: Seq[Int]
        requires: true
        ensures: s = [1, 2, 3]
      }
    }`);
    const eq = ir.operations[0].ensures[0];
    if (eq.kind === "BinaryOp") {
      expect(eq.right.kind).toBe("SeqLiteral");
      if (eq.right.kind === "SeqLiteral") expect(eq.right.elements).toHaveLength(3);
    }
  });

  it("parses empty sequence literal", () => {
    const ir = buildFromSource(`service T {
      operation O {
        output: s: Seq[Int]
        requires: true
        ensures: s = []
      }
    }`);
    const eq = ir.operations[0].ensures[0];
    if (eq.kind === "BinaryOp") {
      expect(eq.right.kind).toBe("SeqLiteral");
      if (eq.right.kind === "SeqLiteral") expect(eq.right.elements).toHaveLength(0);
    }
  });

  it("parses quantifier with multiple bindings", () => {
    const e = parseExpr("all x in s, y in t | x = y");
    expect(e.kind).toBe("Quantifier");
    if (e.kind === "Quantifier") {
      expect(e.quantifier).toBe("all");
      expect(e.bindings).toHaveLength(2);
      expect(e.bindings[0].bindingKind).toBe("in");
    }
  });

  it("parses some() wrap", () => {
    const e = parseExpr("some(42)");
    expect(e.kind).toBe("SomeWrap");
  });

  it("parses if-then-else", () => {
    const e = parseExpr("if x > 0 then x else 0");
    expect(e.kind).toBe("If");
    if (e.kind === "If") {
      expect(e.condition.kind).toBe("BinaryOp");
    }
  });

  it("parses let-in", () => {
    const e = parseExpr("let x = 1 in x + 2");
    expect(e.kind).toBe("Let");
    if (e.kind === "Let") {
      expect(e.variable).toBe("x");
    }
  });

  it("parses lambda", () => {
    const ir = buildFromSource(`service T {
      invariant: sum(s, x => x * 2)
    }`);
    const call = ir.invariants[0].expr;
    expect(call.kind).toBe("Call");
    if (call.kind === "Call") {
      expect(call.args[1].kind).toBe("Lambda");
      if (call.args[1].kind === "Lambda") {
        expect(call.args[1].param).toBe("x");
      }
    }
  });

  it("parses constructor", () => {
    const ir = buildFromSource(`service T {
      invariant: Foo { a = 1, b = 2 }
    }`);
    const ctor = ir.invariants[0].expr;
    expect(ctor.kind).toBe("Constructor");
    if (ctor.kind === "Constructor") {
      expect(ctor.typeName).toBe("Foo");
      expect(ctor.fields).toHaveLength(2);
    }
  });

  it("parses set comprehension", () => {
    const ir = buildFromSource(`service T {
      invariant: { x in s | x > 0 }
    }`);
    const sc = ir.invariants[0].expr;
    expect(sc.kind).toBe("SetComprehension");
    if (sc.kind === "SetComprehension") {
      expect(sc.variable).toBe("x");
    }
  });

  it("parses the expression", () => {
    const e = parseExpr("the x in s | x > 0");
    expect(e.kind).toBe("The");
    if (e.kind === "The") {
      expect(e.variable).toBe("x");
    }
  });
});

// ─── Span tracking ─────────────────────────────────────────

describe("buildIR — span tracking", () => {
  it("service span covers entire serviceDecl", () => {
    const ir = buildFromSource("service Foo {\n}\n");
    expect(ir.span).toBeDefined();
    expect(ir.span!.startLine).toBe(1);
  });

  it("entity span is populated", () => {
    const ir = buildFromSource(`service S {
  entity E {
    x: Int
  }
}`);
    expect(ir.entities[0].span).toBeDefined();
    expect(ir.entities[0].span!.startLine).toBe(2);
  });

  it("expression spans are populated", () => {
    const ir = buildFromSource(`service S {
  invariant: x > 0
}`);
    const expr = ir.invariants[0].expr;
    expect(expr.span).toBeDefined();
    // The expression "x > 0" starts on line 2
    expect(expr.span!.startLine).toBe(2);
  });
});

// ─── Import handling ────────────────────────────────────────

describe("buildIR — imports", () => {
  it("collects import strings", () => {
    const ir = buildFromSource(`import "base.spec"
import "utils.spec"
service S {}`);
    expect(ir.imports).toEqual(["base.spec", "utils.spec"]);
  });

  it("handles no imports", () => {
    const ir = buildFromSource("service S {}");
    expect(ir.imports).toEqual([]);
  });
});

// ─── String escape handling ─────────────────────────────────

describe("buildIR — string escapes", () => {
  it("handles escaped quotes in strings", () => {
    const e = parseExpr('"hello \\"world\\""');
    expect(e.kind).toBe("StringLit");
    if (e.kind === "StringLit") {
      expect(e.value).toBe('hello "world"');
    }
  });

  it("handles backslash escapes", () => {
    const e = parseExpr('"line1\\nline2"');
    expect(e.kind).toBe("StringLit");
    if (e.kind === "StringLit") {
      expect(e.value).toBe("line1\nline2");
    }
  });
});

// ─── Edge cases: keywords as identifiers ────────────────────

describe("buildIR — keyword-as-identifier", () => {
  it("allows keywords as field names", () => {
    const ir = buildFromSource(`service S {
  entity E {
    output: Int
    input: String
    state: Bool
  }
}`);
    expect(ir.entities[0].fields.map(f => f.name)).toEqual(["output", "input", "state"]);
  });

  it("allows keywords in state field names", () => {
    const ir = buildFromSource(`service S {
  state {
    field: Int
    via: String
  }
}`);
    expect(ir.state!.fields.map(f => f.name)).toEqual(["field", "via"]);
  });
});
