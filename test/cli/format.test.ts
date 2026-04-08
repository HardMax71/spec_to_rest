import { describe, it, expect } from "vitest";
import { join } from "node:path";
import { readFileSync } from "node:fs";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { formatIR, formatSummary, formatEndpoints } from "#cli/format.js";
import type { ServiceIR } from "#ir/index.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function irFrom(name: string): ServiceIR {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

const fixtures = [
  { name: "url_shortener.spec", service: "UrlShortener", ops: 4, entities: 1 },
  { name: "todo_list.spec", service: "TodoList", ops: 9, entities: 1 },
  { name: "auth_service.spec", service: "AuthService", ops: 7, entities: 3 },
  { name: "ecommerce.spec", service: "OrderService", ops: 11, entities: 5 },
  { name: "edge_cases.spec", service: "EdgeCases", ops: 11, entities: 2 },
] as const;

describe("formatSummary", () => {
  it.each(fixtures)("$name starts with service name $service", ({ name, service }) => {
    const summary = formatSummary(irFrom(name));
    expect(summary).toMatch(new RegExp(`^Service: ${service}`));
  });

  it.each(fixtures)("$name shows correct operation count", ({ name, ops }) => {
    const summary = formatSummary(irFrom(name));
    expect(summary).toContain(`Operations:  ${ops}`);
  });

  it.each(fixtures)("$name shows correct entity count", ({ name, entities }) => {
    const summary = formatSummary(irFrom(name));
    expect(summary).toContain(`Entities:    ${entities}`);
  });

  it("includes named invariants", () => {
    const summary = formatSummary(irFrom("url_shortener.spec"));
    expect(summary).toContain("allURLsValid");
    expect(summary).toContain("metadataConsistent");
  });

  it("includes operation signatures", () => {
    const summary = formatSummary(irFrom("url_shortener.spec"));
    expect(summary).toContain("Shorten (1→2)");
    expect(summary).toContain("Delete (1→0)");
  });
});

describe("formatIR", () => {
  it.each(["json", "ir"] as const)("format '%s' produces valid JSON", (fmt) => {
    const ir = irFrom("url_shortener.spec");
    const output = formatIR(ir, fmt);
    expect(() => JSON.parse(output)).not.toThrow();
    expect(JSON.parse(output).kind).toBe("Service");
  });

  it("format 'summary' produces human text", () => {
    const ir = irFrom("url_shortener.spec");
    const output = formatIR(ir, "summary");
    expect(output).toContain("Service: UrlShortener");
    expect(() => JSON.parse(output)).toThrow();
  });

  it("format 'endpoints' produces endpoint table", () => {
    const ir = irFrom("url_shortener.spec");
    const output = formatIR(ir, "endpoints");
    expect(output).toContain("Service: UrlShortener");
    expect(output).toContain("Endpoints:");
    expect(output).toContain("Shorten");
    expect(output).toContain("POST");
    expect(output).toContain("/shorten");
  });
});

describe("formatSummary conventions detail", () => {
  it("lists convention rules for url_shortener", () => {
    const summary = formatSummary(irFrom("url_shortener.spec"));
    expect(summary).toContain('Shorten.http_method = "POST"');
    expect(summary).toContain('Resolve.http_path = "/{code}"');
  });

  it("shows Conventions section for specs with conventions", () => {
    const summary = formatSummary(irFrom("edge_cases.spec"));
    expect(summary).toContain("Conventions:");
  });
});

describe("formatEndpoints", () => {
  it("shows override annotations for url_shortener", () => {
    const output = formatEndpoints(irFrom("url_shortener.spec"));
    expect(output).toContain("[method: override, path: override, status: override]");
  });

  it("shows auto annotations for todo_list operations without overrides", () => {
    const output = formatEndpoints(irFrom("todo_list.spec"));
    expect(output).toContain("method: override");
    expect(output).toContain("path: override");
    expect(output).toContain("status: auto");
  });

  it("includes all operations", () => {
    const output = formatEndpoints(irFrom("ecommerce.spec"));
    expect(output).toContain("CreateDraftOrder");
    expect(output).toContain("AddLineItem");
    expect(output).toContain("ListOrders");
  });
});
