import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import type { ServiceIR, ConventionsDecl, ConventionRule, Expr } from "#ir/types.js";
import { validateConventions } from "#convention/validate.js";
import type { ConventionDiagnostic } from "#convention/types.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function buildFixture(name: string): ServiceIR {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

function errorsOnly(diags: readonly ConventionDiagnostic[]): readonly ConventionDiagnostic[] {
  return diags.filter((d) => d.level === "error");
}

function warningsOnly(diags: readonly ConventionDiagnostic[]): readonly ConventionDiagnostic[] {
  return diags.filter((d) => d.level === "warning");
}

function makeRule(target: string, property: string, value: Expr, qualifier: string | null = null): ConventionRule {
  return { kind: "ConventionRule", target, property, qualifier, value };
}

function makeConventions(rules: ConventionRule[]): ConventionsDecl {
  return { kind: "Conventions", rules };
}

function str(value: string): Expr {
  return { kind: "StringLit", value } as Expr;
}

function int(value: number): Expr {
  return { kind: "IntLit", value } as Expr;
}

function bool(value: boolean): Expr {
  return { kind: "BoolLit", value } as Expr;
}

// ─── Null conventions ───────────────────────────────────────

describe("validateConventions with null", () => {
  it("returns empty array for null conventions", () => {
    const ir = buildFixture("url_shortener.spec");
    expect(validateConventions(null, ir)).toEqual([]);
  });
});

// ─── Valid fixtures produce zero errors ─────────────────────

describe("valid fixtures", () => {
  const validFixtures = [
    "url_shortener.spec",
    "todo_list.spec",
    "auth_service.spec",
    "ecommerce.spec",
    "edge_cases.spec",
  ];

  it.each(validFixtures)("%s has no convention errors", (name) => {
    const ir = buildFixture(name);
    const diags = errorsOnly(validateConventions(ir.conventions, ir));
    expect(diags).toEqual([]);
  });
});

// ─── Target validation ──────────────────────────────────────

describe("target validation", () => {
  it("errors on unknown target", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Nonexistent", "http_method", str("GET")),
    ]);
    const diags = validateConventions(conventions, ir);
    expect(errorsOnly(diags)).toHaveLength(1);
    expect(diags[0].message).toContain("no operation or entity named 'Nonexistent'");
  });

  it("accepts valid operation target", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Shorten", "http_method", str("POST")),
    ]);
    expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
  });

  it("accepts valid entity target", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("UrlMapping", "db_table", str("mappings")),
    ]);
    expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
  });
});

// ─── Property validation ────────────────────────────────────

describe("property validation", () => {
  it("errors on unknown property", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Shorten", "http_frobnicate", str("yes")),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain("unknown convention property 'http_frobnicate'");
  });

  it("errors when entity property on operation target", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Shorten", "db_table", str("custom")),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain("not valid for operation");
    expect(diags[0].message).toContain("applies to entities");
  });

  it("errors when operation property on entity target", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("UrlMapping", "http_method", str("GET")),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain("not valid for entity");
    expect(diags[0].message).toContain("applies to operations");
  });
});

// ─── Value validation ───────────────────────────────────────

describe("value validation", () => {
  const cases: Array<[string, string, Expr, string]> = [
    ["http_method non-string", "http_method", int(42), "expected a string"],
    ["http_method invalid verb", "http_method", str("GETPOST"), 'got "GETPOST"'],
    ["http_status_success non-integer", "http_status_success", str("200"), "expected an integer"],
    ["http_status_success out of range (low)", "http_status_success", int(50), "between 100 and 599"],
    ["http_status_success out of range (high)", "http_status_success", int(999), "between 100 and 599"],
    ["http_path non-string", "http_path", int(42), "expected a string"],
    ["http_path no leading slash", "http_path", str("widgets"), "must start with '/'"],
  ];

  it.each(cases)("%s", (_label, property, value, expectedMsg) => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Shorten", property, value),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain(expectedMsg);
  });

  it("valid http_method values pass", () => {
    const ir = buildFixture("url_shortener.spec");
    for (const method of ["GET", "POST", "PUT", "PATCH", "DELETE"]) {
      const conventions = makeConventions([
        makeRule("Shorten", "http_method", str(method)),
      ]);
      expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
    }
  });

  it("valid http_status_success values pass", () => {
    const ir = buildFixture("url_shortener.spec");
    for (const status of [100, 200, 201, 204, 302, 404, 500, 599]) {
      const conventions = makeConventions([
        makeRule("Shorten", "http_status_success", int(status)),
      ]);
      expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
    }
  });

  it("valid http_path values pass", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Shorten", "http_path", str("/widgets")),
      makeRule("Resolve", "http_path", str("/{code}")),
    ]);
    expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
  });
});

// ─── Entity property value validation ───────────────────────

describe("entity property value validation", () => {
  const cases: Array<[string, string, Expr, string]> = [
    ["db_table non-string", "db_table", int(42), "expected a string"],
    ["db_table empty", "db_table", str(""), "cannot be empty"],
    ["db_timestamps non-boolean", "db_timestamps", str("yes"), "expected true or false"],
    ["plural non-string", "plural", int(42), "expected a string"],
    ["plural empty", "plural", str(""), "cannot be empty"],
  ];

  it.each(cases)("%s", (_label, property, value, expectedMsg) => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("UrlMapping", property, value),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain(expectedMsg);
  });

  it("valid entity overrides pass", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("UrlMapping", "db_table", str("mappings")),
      makeRule("UrlMapping", "db_timestamps", bool(false)),
      makeRule("UrlMapping", "plural", str("mappings")),
    ]);
    expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
  });
});

// ─── Duplicate detection ────────────────────────────────────

describe("duplicate detection", () => {
  it("errors on duplicate override", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Shorten", "http_method", str("POST")),
      makeRule("Shorten", "http_method", str("PUT")),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain("duplicate override");
  });

  it("same property on different targets is not a duplicate", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Shorten", "http_method", str("POST")),
      makeRule("Resolve", "http_method", str("GET")),
    ]);
    expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
  });

  it("http_header with different qualifiers is not a duplicate", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Resolve", "http_header", str("test"), "Location"),
      makeRule("Resolve", "http_header", str("test"), "Cache-Control"),
    ]);
    expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
  });
});

// ─── http_header validation ─────────────────────────────────

describe("http_header validation", () => {
  it("errors when qualifier is missing", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Resolve", "http_header", str("test"), null),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain("requires a header name qualifier");
  });

  it("string value with qualifier passes", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Resolve", "http_header", str("https://example.com"), "Location"),
    ]);
    expect(errorsOnly(validateConventions(conventions, ir))).toEqual([]);
  });
});

// ─── Multiple errors accumulated ────────────────────────────

describe("error accumulation", () => {
  it("reports multiple independent errors", () => {
    const ir = buildFixture("url_shortener.spec");
    const conventions = makeConventions([
      makeRule("Nonexistent", "http_method", str("GET")),
      makeRule("Shorten", "http_frobnicate", str("yes")),
      makeRule("Shorten", "http_method", str("INVALID")),
    ]);
    const diags = errorsOnly(validateConventions(conventions, ir));
    expect(diags).toHaveLength(3);
  });
});

// ─── Fixture-based validation ───────────────────────────────

describe("convention_errors.spec fixture", () => {
  it("parses but has validation errors", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    const errors = errorsOnly(diags);
    expect(errors.length).toBeGreaterThan(0);
  });

  it("detects duplicate override", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    expect(diags.some((d) => d.message.includes("duplicate override"))).toBe(true);
  });

  it("detects unknown target", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    expect(diags.some((d) => d.message.includes("no operation or entity named 'Nonexistent'"))).toBe(true);
  });

  it("detects unknown property", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    expect(diags.some((d) => d.message.includes("unknown convention property 'http_frobnicate'"))).toBe(true);
  });

  it("detects invalid http_method value", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    expect(diags.some((d) => d.message.includes('"GETPOST"'))).toBe(true);
  });

  it("detects out-of-range status code", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    expect(diags.some((d) => d.message.includes("999"))).toBe(true);
  });

  it("detects entity property on operation target", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    expect(diags.some((d) => d.message.includes("not valid for operation") && d.target === "Create")).toBe(true);
  });

  it("detects operation property on entity target", () => {
    const ir = buildFixture("convention_errors.spec");
    const diags = validateConventions(ir.conventions, ir);
    expect(diags.some((d) => d.message.includes("not valid for entity") && d.target === "Widget")).toBe(true);
  });
});
