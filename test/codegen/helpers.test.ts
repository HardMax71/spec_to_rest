import { describe, it, expect } from "vitest";
import {
  snakeCaseHelper,
  camelCaseHelper,
  pascalCaseHelper,
  kebabCaseHelper,
  pluralizeHelper,
  upperHelper,
  lowerHelper,
  concatHelper,
  joinHelper,
  indentHelper,
  eqHelper,
  neHelper,
  andHelper,
  orHelper,
  notHelper,
} from "#codegen/helpers.js";

describe("snakeCaseHelper", () => {
  const cases = [
    ["OrderItem", "order_item"],
    ["UrlMapping", "url_mapping"],
    ["User", "user"],
    ["CreateDraftOrder", "create_draft_order"],
  ] as const;

  it.each(cases)("%s -> %s", (input, expected) => {
    expect(snakeCaseHelper(input)).toBe(expected);
  });
});

describe("camelCaseHelper", () => {
  const cases = [
    ["OrderItem", "orderItem"],
    ["UrlMapping", "urlMapping"],
    ["User", "user"],
    ["CreateDraftOrder", "createDraftOrder"],
  ] as const;

  it.each(cases)("%s -> %s", (input, expected) => {
    expect(camelCaseHelper(input)).toBe(expected);
  });
});

describe("pascalCaseHelper", () => {
  const cases = [
    ["OrderItem", "OrderItem"],
    ["urlMapping", "UrlMapping"],
    ["user", "User"],
  ] as const;

  it.each(cases)("%s -> %s", (input, expected) => {
    expect(pascalCaseHelper(input)).toBe(expected);
  });
});

describe("kebabCaseHelper", () => {
  const cases = [
    ["OrderItem", "order-item"],
    ["UrlMapping", "url-mapping"],
  ] as const;

  it.each(cases)("%s -> %s", (input, expected) => {
    expect(kebabCaseHelper(input)).toBe(expected);
  });
});

describe("pluralizeHelper", () => {
  const cases = [
    ["order", "orders"],
    ["person", "people"],
    ["status", "statuses"],
  ] as const;

  it.each(cases)("%s -> %s", (input, expected) => {
    expect(pluralizeHelper(input)).toBe(expected);
  });
});

describe("upperHelper", () => {
  it("converts to uppercase", () => {
    expect(upperHelper("hello")).toBe("HELLO");
  });
});

describe("lowerHelper", () => {
  it("converts to lowercase", () => {
    expect(lowerHelper("HELLO")).toBe("hello");
  });
});

describe("concatHelper", () => {
  it("concatenates strings (ignoring Handlebars options arg)", () => {
    expect(concatHelper("hello", " ", "world", {})).toBe("hello world");
  });

  it("concatenates two strings", () => {
    expect(concatHelper("a", "b", {})).toBe("ab");
  });
});

describe("joinHelper", () => {
  it("joins array with separator", () => {
    expect(joinHelper(["a", "b", "c"], ", ")).toBe("a, b, c");
  });

  it("handles single-element array", () => {
    expect(joinHelper(["a"], ", ")).toBe("a");
  });
});

describe("indentHelper", () => {
  it("indents each line by N spaces (inline mode)", () => {
    const result = indentHelper.call({}, "line1\nline2", 4);
    expect(result).toBe("    line1\n    line2");
  });

  it("does not indent empty lines", () => {
    const result = indentHelper.call({}, "line1\n\nline2", 4);
    expect(result).toBe("    line1\n\n    line2");
  });

  it("preserves relative indentation", () => {
    const result = indentHelper.call({}, "def foo():\n    pass", 4);
    expect(result).toBe("    def foo():\n        pass");
  });

  it("works as block helper", () => {
    const options = { fn: () => "line1\nline2\n" } as unknown as Handlebars.HelperOptions;
    const result = indentHelper.call({}, 4, options);
    expect(result).toContain("    line1");
    expect(result).toContain("    line2");
  });
});

describe("logic helpers", () => {
  const eqCases = [
    ["a", "a", true],
    ["a", "b", false],
    [1, 1, true],
    [1, "1", false],
  ] as const;

  it.each(eqCases)("eq(%s, %s) -> %s", (a, b, expected) => {
    expect(eqHelper(a, b)).toBe(expected);
  });

  const neCases = [
    ["a", "b", true],
    ["a", "a", false],
  ] as const;

  it.each(neCases)("ne(%s, %s) -> %s", (a, b, expected) => {
    expect(neHelper(a, b)).toBe(expected);
  });

  it("and returns true when all truthy", () => {
    expect(andHelper(true, true, {})).toBe(true);
  });

  it("and returns false when any falsy", () => {
    expect(andHelper(true, false, {})).toBe(false);
  });

  it("or returns true when any truthy", () => {
    expect(orHelper(false, true, {})).toBe(true);
  });

  it("or returns false when all falsy", () => {
    expect(orHelper(false, false, {})).toBe(false);
  });

  it("not inverts truthiness", () => {
    expect(notHelper(true)).toBe(false);
    expect(notHelper(false)).toBe(true);
    expect(notHelper("")).toBe(true);
    expect(notHelper("x")).toBe(false);
  });
});
