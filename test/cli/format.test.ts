import { describe, it, expect } from "vitest";
import { join } from "node:path";
import { readFileSync } from "node:fs";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { formatIR, formatSummary, formatEndpoints, formatProfile } from "#cli/format.js";
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

describe("formatProfile", () => {
  it("shows target profile info", () => {
    const output = formatProfile(irFrom("url_shortener.spec"), "python-fastapi-postgres");
    expect(output).toContain("python-fastapi-postgres");
    expect(output).toContain("Python + FastAPI + PostgreSQL");
  });

  it("shows stack details including uv", () => {
    const output = formatProfile(irFrom("url_shortener.spec"), "python-fastapi-postgres");
    expect(output).toContain("Package Manager: uv");
    expect(output).toContain("Framework:       fastapi");
    expect(output).toContain("ORM:             sqlalchemy (async)");
    expect(output).toContain("DB Driver:       asyncpg");
  });

  it("shows entity details", () => {
    const output = formatProfile(irFrom("url_shortener.spec"), "python-fastapi-postgres");
    expect(output).toContain("UrlMapping");
    expect(output).toContain("url_mapping.py");
    expect(output).toContain("UrlMappingCreate");
    expect(output).toContain("UrlMappingRead");
  });

  it("shows endpoint handlers", () => {
    const output = formatProfile(irFrom("url_shortener.spec"), "python-fastapi-postgres");
    expect(output).toContain("async def shorten(...)");
    expect(output).toContain("async def resolve(...)");
    expect(output).toContain("async def delete(...)");
  });

  it("shows dependencies", () => {
    const output = formatProfile(irFrom("url_shortener.spec"), "python-fastapi-postgres");
    expect(output).toContain("fastapi>=0.115");
    expect(output).toContain("sqlalchemy>=2.0");
    expect(output).toContain("pyproject.toml via uv");
  });

  it("accessible via formatIR with profile format", () => {
    const output = formatIR(irFrom("url_shortener.spec"), "profile", "python-fastapi-postgres");
    expect(output).toContain("python-fastapi-postgres");
    expect(output).toContain("uv");
  });
});
