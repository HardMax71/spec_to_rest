import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import type { ServiceIR } from "#ir/index.js";
import { deriveSchema } from "#convention/schema.js";
import type { DatabaseSchema, TableSpec } from "#convention/types.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function buildFixture(name: string): ServiceIR {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

function deriveFixtureSchema(name: string): DatabaseSchema {
  return deriveSchema(buildFixture(name));
}

function findTable(schema: DatabaseSchema, name: string): TableSpec {
  const table = schema.tables.find((t) => t.name === name);
  if (!table) throw new Error(`No table named ${name}. Tables: ${schema.tables.map((t) => t.name).join(", ")}`);
  return table;
}

function findColumn(table: TableSpec, name: string) {
  const col = table.columns.find((c) => c.name === name);
  if (!col) throw new Error(`No column named ${name} in ${table.name}. Columns: ${table.columns.map((c) => c.name).join(", ")}`);
  return col;
}

// ─── URL Shortener ───────────────────────────────────────────

describe("url_shortener.spec schema", () => {
  it("creates url_mappings table", () => {
    const schema = deriveFixtureSchema("url_shortener.spec");
    const table = findTable(schema, "url_mappings");
    expect(table.entityName).toBe("UrlMapping");
    expect(table.primaryKey).toBe("id");
  });

  it("has correct columns for UrlMapping", () => {
    const schema = deriveFixtureSchema("url_shortener.spec");
    const table = findTable(schema, "url_mappings");

    const id = findColumn(table, "id");
    expect(id.sqlType).toBe("BIGSERIAL");

    const code = findColumn(table, "code");
    expect(code.sqlType).toBe("TEXT");
    expect(code.nullable).toBe(false);

    const url = findColumn(table, "url");
    expect(url.sqlType).toBe("TEXT");

    const clickCount = findColumn(table, "click_count");
    expect(clickCount.sqlType).toBe("INTEGER");

    const createdAt = findColumn(table, "created_at");
    expect(createdAt.sqlType).toBe("TIMESTAMPTZ");
  });

  it("generates CHECK constraints from field where clauses", () => {
    const schema = deriveFixtureSchema("url_shortener.spec");
    const table = findTable(schema, "url_mappings");
    expect(table.checks.some((c) => c.includes("click_count") && c.includes(">= 0"))).toBe(true);
  });

  it("includes auto timestamps", () => {
    const schema = deriveFixtureSchema("url_shortener.spec");
    const table = findTable(schema, "url_mappings");
    expect(table.columns.some((c) => c.name === "created_at")).toBe(true);
    expect(table.columns.some((c) => c.name === "updated_at")).toBe(true);
  });
});

// ─── Todo List ───────────────────────────────────────────────

describe("todo_list.spec schema", () => {
  it("creates todos table", () => {
    const schema = deriveFixtureSchema("todo_list.spec");
    const table = findTable(schema, "todos");
    expect(table.entityName).toBe("Todo");
  });

  it("maps enum fields to TEXT with CHECK", () => {
    const schema = deriveFixtureSchema("todo_list.spec");
    const table = findTable(schema, "todos");
    const status = findColumn(table, "status");
    expect(status.sqlType).toBe("TEXT");
    expect(table.checks.some((c) => c.includes("status") && c.includes("IN"))).toBe(true);
  });

  it("maps Option fields as nullable", () => {
    const schema = deriveFixtureSchema("todo_list.spec");
    const table = findTable(schema, "todos");
    const description = findColumn(table, "description");
    expect(description.nullable).toBe(true);

    const completedAt = findColumn(table, "completed_at");
    expect(completedAt.nullable).toBe(true);
  });

  it("maps Set fields to JSONB", () => {
    const schema = deriveFixtureSchema("todo_list.spec");
    const table = findTable(schema, "todos");
    const tags = findColumn(table, "tags");
    expect(tags.sqlType).toBe("JSONB");
  });
});

// ─── E-commerce ──────────────────────────────────────────────

describe("ecommerce.spec schema", () => {
  it("creates all entity tables", () => {
    const schema = deriveFixtureSchema("ecommerce.spec");
    const tableNames = schema.tables.map((t) => t.name);
    expect(tableNames).toContain("products");
    expect(tableNames).toContain("line_items");
    expect(tableNames).toContain("orders");
    expect(tableNames).toContain("payments");
    expect(tableNames).toContain("inventory_entries");
  });

  it("maps Money type alias to INTEGER", () => {
    const schema = deriveFixtureSchema("ecommerce.spec");
    const orders = findTable(schema, "orders");
    const total = findColumn(orders, "total");
    expect(total.sqlType).toBe("INTEGER");
  });

  it("maps enum fields correctly", () => {
    const schema = deriveFixtureSchema("ecommerce.spec");
    const orders = findTable(schema, "orders");
    const status = findColumn(orders, "status");
    expect(status.sqlType).toBe("TEXT");
    expect(orders.checks.some((c) => c.includes("status") && c.includes("IN"))).toBe(true);
  });

  it("maps Option[DateTime] as nullable TIMESTAMPTZ", () => {
    const schema = deriveFixtureSchema("ecommerce.spec");
    const orders = findTable(schema, "orders");
    const shippedAt = findColumn(orders, "shipped_at");
    expect(shippedAt.nullable).toBe(true);
    expect(shippedAt.sqlType).toBe("TIMESTAMPTZ");
  });

  it("generates CHECK constraints from entity invariants", () => {
    const schema = deriveFixtureSchema("ecommerce.spec");
    const products = findTable(schema, "products");
    expect(products.checks.some((c) => c.includes("price") && c.includes("> 0"))).toBe(true);
  });

  it("generates field where-clause constraints", () => {
    const schema = deriveFixtureSchema("ecommerce.spec");
    const lineItems = findTable(schema, "line_items");
    expect(lineItems.checks.some((c) => c.includes("id") && c.includes("> 0"))).toBe(true);
  });
});

// ─── Auth Service ────────────────────────────────────────────

describe("auth_service.spec schema", () => {
  it("creates user, session, and login_attempt tables", () => {
    const schema = deriveFixtureSchema("auth_service.spec");
    const tableNames = schema.tables.map((t) => t.name);
    expect(tableNames).toContain("users");
    expect(tableNames).toContain("sessions");
    expect(tableNames).toContain("login_attempts");
  });

  it("maps User fields correctly", () => {
    const schema = deriveFixtureSchema("auth_service.spec");
    const users = findTable(schema, "users");

    const email = findColumn(users, "email");
    expect(email.sqlType).toBe("TEXT");
    expect(email.nullable).toBe(false);

    const lastLogin = findColumn(users, "last_login");
    expect(lastLogin.nullable).toBe(true);
    expect(lastLogin.sqlType).toBe("TIMESTAMPTZ");

    const isActive = findColumn(users, "is_active");
    expect(isActive.sqlType).toBe("BOOLEAN");
  });
});

// ─── Edge Cases ──────────────────────────────────────────────

describe("edge_cases.spec schema", () => {
  it("creates tables for entities", () => {
    const schema = deriveFixtureSchema("edge_cases.spec");
    const tableNames = schema.tables.map((t) => t.name);
    expect(tableNames).toContain("bases");
    expect(tableNames).toContain("children");
  });

  it("Base entity has correct columns", () => {
    const schema = deriveFixtureSchema("edge_cases.spec");
    const bases = findTable(schema, "bases");
    expect(bases.columns.some((c) => c.name === "id")).toBe(true);
    expect(bases.columns.some((c) => c.name === "created_at")).toBe(true);
  });
});
