import { describe, it, expect } from "vitest";
import {
  pluralize,
  splitCamelCase,
  toKebabCase,
  toSnakeCase,
  toPathSegment,
  toTableName,
  toColumnName,
} from "#convention/naming.js";

describe("pluralize", () => {
  const cases: Array<[string, string]> = [
    ["order", "orders"],
    ["user", "users"],
    ["product", "products"],
    ["todo", "todos"],
    ["session", "sessions"],
    ["address", "addresses"],
    ["status", "statuses"],
    ["match", "matches"],
    ["push", "pushes"],
    ["box", "boxes"],
    ["quiz", "quizes"],
    ["category", "categories"],
    ["entry", "entries"],
    ["inventory", "inventory"],
    ["metadata", "metadata"],
    ["data", "data"],
    ["person", "people"],
    ["child", "children"],
    ["analysis", "analyses"],
    ["index", "indices"],
    ["leaf", "leaves"],
    ["life", "lives"],
  ];

  it.each(cases)("%s → %s", (input, expected) => {
    expect(pluralize(input)).toBe(expected);
  });

  it("preserves leading uppercase", () => {
    expect(pluralize("Person")).toBe("People");
    expect(pluralize("Category")).toBe("Categories");
    expect(pluralize("Order")).toBe("Orders");
  });
});

describe("splitCamelCase", () => {
  const cases: Array<[string, string[]]> = [
    ["Order", ["Order"]],
    ["OrderItem", ["Order", "Item"]],
    ["ShortCode", ["Short", "Code"]],
    ["UrlMapping", ["Url", "Mapping"]],
    ["LineItem", ["Line", "Item"]],
    ["URLShortener", ["URL", "Shortener"]],
    ["getHTTPResponse", ["get", "HTTP", "Response"]],
  ];

  it.each(cases)("%s → %j", (input, expected) => {
    expect(splitCamelCase(input)).toEqual(expected);
  });
});

describe("toKebabCase", () => {
  const cases: Array<[string, string]> = [
    ["Order", "order"],
    ["OrderItem", "order-item"],
    ["ShortCode", "short-code"],
    ["LoginFailed", "login-failed"],
    ["CreateDraftOrder", "create-draft-order"],
  ];

  it.each(cases)("%s → %s", (input, expected) => {
    expect(toKebabCase(input)).toBe(expected);
  });
});

describe("toSnakeCase", () => {
  const cases: Array<[string, string]> = [
    ["Order", "order"],
    ["OrderItem", "order_item"],
    ["ShortCode", "short_code"],
    ["UrlMapping", "url_mapping"],
    ["InventoryEntry", "inventory_entry"],
  ];

  it.each(cases)("%s → %s", (input, expected) => {
    expect(toSnakeCase(input)).toBe(expected);
  });
});

describe("toPathSegment", () => {
  const cases: Array<[string, string]> = [
    ["Order", "orders"],
    ["Todo", "todos"],
    ["Product", "products"],
    ["LineItem", "line-items"],
    ["ShortCode", "short-codes"],
    ["UrlMapping", "url-mappings"],
    ["InventoryEntry", "inventory-entries"],
    ["User", "users"],
    ["Session", "sessions"],
    ["Payment", "payments"],
  ];

  it.each(cases)("%s → %s", (input, expected) => {
    expect(toPathSegment(input)).toBe(expected);
  });
});

describe("toTableName", () => {
  const cases: Array<[string, string]> = [
    ["Order", "orders"],
    ["Todo", "todos"],
    ["Product", "products"],
    ["LineItem", "line_items"],
    ["ShortCode", "short_codes"],
    ["UrlMapping", "url_mappings"],
    ["InventoryEntry", "inventory_entries"],
    ["User", "users"],
    ["Payment", "payments"],
  ];

  it.each(cases)("%s → %s", (input, expected) => {
    expect(toTableName(input)).toBe(expected);
  });
});

describe("toColumnName", () => {
  it("passes through snake_case names", () => {
    expect(toColumnName("order_id")).toBe("order_id");
    expect(toColumnName("created_at")).toBe("created_at");
    expect(toColumnName("status")).toBe("status");
  });

  it("converts CamelCase to snake_case", () => {
    expect(toColumnName("OrderId")).toBe("order_id");
    expect(toColumnName("ShortCode")).toBe("short_code");
  });
});
