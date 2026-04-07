import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import type { ServiceIR } from "#ir/index.js";
import { classifyOperations } from "#convention/classify.js";
import { deriveEndpoints, getConvention } from "#convention/path.js";
import type { EndpointSpec, HttpMethod } from "#convention/types.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function buildFixture(name: string): ServiceIR {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildIR(tree);
}

function deriveFixture(name: string): readonly EndpointSpec[] {
  const ir = buildFixture(name);
  const classifications = classifyOperations(ir);
  return deriveEndpoints(classifications, ir);
}

function lookup(endpoints: readonly EndpointSpec[], name: string): EndpointSpec {
  const found = endpoints.find((e) => e.operationName === name);
  if (!found) throw new Error(`No endpoint for ${name}`);
  return found;
}

// ─── URL Shortener ───────────────────────────────────────────

describe("url_shortener.spec endpoints", () => {
  const expectations: Array<[string, HttpMethod, string, number]> = [
    ["Shorten", "POST", "/shorten", 201],
    ["Resolve", "GET", "/{code}", 302],
    ["Delete", "DELETE", "/{code}", 204],
    ["ListAll", "GET", "/urls", 200],
  ];

  it.each(expectations)(
    "%s → %s %s (%d)",
    (opName, method, path, status) => {
      const endpoints = deriveFixture("url_shortener.spec");
      const ep = lookup(endpoints, opName);
      expect(ep.method).toBe(method);
      expect(ep.path).toBe(path);
      expect(ep.successStatus).toBe(status);
    },
  );

  it("Shorten has no path params and url in body", () => {
    const endpoints = deriveFixture("url_shortener.spec");
    const ep = lookup(endpoints, "Shorten");
    expect(ep.pathParams).toHaveLength(0);
    expect(ep.bodyParams).toHaveLength(1);
    expect(ep.bodyParams[0].name).toBe("url");
  });

  it("Resolve has code as path param", () => {
    const endpoints = deriveFixture("url_shortener.spec");
    const ep = lookup(endpoints, "Resolve");
    expect(ep.pathParams).toHaveLength(1);
    expect(ep.pathParams[0].name).toBe("code");
  });

  it("ListAll has no params", () => {
    const endpoints = deriveFixture("url_shortener.spec");
    const ep = lookup(endpoints, "ListAll");
    expect(ep.pathParams).toHaveLength(0);
    expect(ep.queryParams).toHaveLength(0);
    expect(ep.bodyParams).toHaveLength(0);
  });
});

// ─── Todo List ───────────────────────────────────────────────

describe("todo_list.spec endpoints", () => {
  const expectations: Array<[string, HttpMethod, string, number]> = [
    ["CreateTodo", "POST", "/todos", 201],
    ["GetTodo", "GET", "/todos/{id}", 200],
    ["ListTodos", "GET", "/todos", 200],
    ["StartWork", "POST", "/todos/{id}/start", 200],
    ["Complete", "POST", "/todos/{id}/complete", 200],
    ["Reopen", "POST", "/todos/{id}/reopen", 200],
    ["Archive", "POST", "/todos/{id}/archive", 200],
  ];

  it.each(expectations)(
    "%s → %s %s (%d)",
    (opName, method, path, status) => {
      const endpoints = deriveFixture("todo_list.spec");
      const ep = lookup(endpoints, opName);
      expect(ep.method).toBe(method);
      expect(ep.path).toBe(path);
      expect(ep.successStatus).toBe(status);
    },
  );

  it("ListTodos has optional query params", () => {
    const endpoints = deriveFixture("todo_list.spec");
    const ep = lookup(endpoints, "ListTodos");
    expect(ep.queryParams.length).toBeGreaterThan(0);
    expect(ep.queryParams.every((p) => !p.required)).toBe(true);
  });

  it("CreateTodo has body params", () => {
    const endpoints = deriveFixture("todo_list.spec");
    const ep = lookup(endpoints, "CreateTodo");
    expect(ep.bodyParams.length).toBeGreaterThan(0);
    const titleParam = ep.bodyParams.find((p) => p.name === "title");
    expect(titleParam).toBeDefined();
    expect(titleParam!.required).toBe(true);
  });
});

// ─── E-commerce ──────────────────────────────────────────────

describe("ecommerce.spec endpoints", () => {
  const expectations: Array<[string, HttpMethod, string, number]> = [
    ["CreateDraftOrder", "POST", "/orders", 201],
    ["AddLineItem", "POST", "/orders/{order_id}/items", 201],
    ["RemoveLineItem", "DELETE", "/orders/{order_id}/items/{item_id}", 204],
    ["PlaceOrder", "POST", "/orders/{order_id}/place", 200],
    ["RecordPayment", "POST", "/orders/{order_id}/payments", 201],
    ["ShipOrder", "POST", "/orders/{order_id}/ship", 200],
    ["ConfirmDelivery", "POST", "/orders/{order_id}/deliver", 200],
    ["CancelOrder", "POST", "/orders/{order_id}/cancel", 200],
    ["ProcessReturn", "POST", "/orders/{order_id}/return", 200],
    ["GetOrder", "GET", "/orders/{order_id}", 200],
    ["ListOrders", "GET", "/orders", 200],
  ];

  it.each(expectations)(
    "%s → %s %s (%d)",
    (opName, method, path, status) => {
      const endpoints = deriveFixture("ecommerce.spec");
      const ep = lookup(endpoints, opName);
      expect(ep.method).toBe(method);
      expect(ep.path).toBe(path);
      expect(ep.successStatus).toBe(status);
    },
  );

  it("AddLineItem has order_id in path and sku/quantity in body", () => {
    const endpoints = deriveFixture("ecommerce.spec");
    const ep = lookup(endpoints, "AddLineItem");
    expect(ep.pathParams).toHaveLength(1);
    expect(ep.pathParams[0].name).toBe("order_id");
    expect(ep.bodyParams.some((p) => p.name === "sku")).toBe(true);
    expect(ep.bodyParams.some((p) => p.name === "quantity")).toBe(true);
  });

  it("RemoveLineItem has both order_id and item_id in path", () => {
    const endpoints = deriveFixture("ecommerce.spec");
    const ep = lookup(endpoints, "RemoveLineItem");
    expect(ep.pathParams).toHaveLength(2);
    const paramNames = ep.pathParams.map((p) => p.name);
    expect(paramNames).toContain("order_id");
    expect(paramNames).toContain("item_id");
  });

  it("ListOrders has optional query params", () => {
    const endpoints = deriveFixture("ecommerce.spec");
    const ep = lookup(endpoints, "ListOrders");
    expect(ep.queryParams.length).toBeGreaterThan(0);
    expect(ep.queryParams.every((p) => !p.required)).toBe(true);
  });
});

// ─── Auth Service ────────────────────────────────────────────

describe("auth_service.spec endpoints", () => {
  const expectations: Array<[string, HttpMethod, string, number]> = [
    ["Register", "POST", "/auth/register", 201],
    ["Login", "POST", "/auth/login", 200],
    ["RefreshToken", "POST", "/auth/refresh", 201],
    ["RequestPasswordReset", "POST", "/auth/password-reset", 200],
    ["ResetPassword", "POST", "/auth/password-reset/confirm", 200],
    ["Logout", "POST", "/auth/logout", 204],
  ];

  it.each(expectations)(
    "%s → %s %s (%d)",
    (opName, method, path, status) => {
      const endpoints = deriveFixture("auth_service.spec");
      const ep = lookup(endpoints, opName);
      expect(ep.method).toBe(method);
      expect(ep.path).toBe(path);
      expect(ep.successStatus).toBe(status);
    },
  );

  it("Register has body params for email, password, display_name", () => {
    const endpoints = deriveFixture("auth_service.spec");
    const ep = lookup(endpoints, "Register");
    expect(ep.bodyParams.length).toBe(3);
    const names = ep.bodyParams.map((p) => p.name);
    expect(names).toContain("email");
    expect(names).toContain("password");
    expect(names).toContain("display_name");
  });
});

// ─── Convention override helper ──────────────────────────────

describe("getConvention", () => {
  it("returns null for missing conventions", () => {
    expect(getConvention(null, "Foo", "http_path")).toBeNull();
  });

  it("extracts string convention from fixture", () => {
    const ir = buildFixture("url_shortener.spec");
    expect(getConvention(ir.conventions, "Shorten", "http_path")).toBe("/shorten");
    expect(getConvention(ir.conventions, "Resolve", "http_path")).toBe("/{code}");
  });

  it("extracts numeric convention from fixture", () => {
    const ir = buildFixture("url_shortener.spec");
    expect(getConvention(ir.conventions, "Shorten", "http_status_success")).toBe("201");
    expect(getConvention(ir.conventions, "Resolve", "http_status_success")).toBe("302");
  });

  it("returns null for non-existent convention", () => {
    const ir = buildFixture("url_shortener.spec");
    expect(getConvention(ir.conventions, "Shorten", "nonexistent")).toBeNull();
  });
});
