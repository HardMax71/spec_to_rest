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
  indentString,
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

  it("handles empty string", () => {
    expect(camelCaseHelper("")).toBe("");
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

  it("handles empty string", () => {
    expect(pascalCaseHelper("")).toBe("");
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
  it("concatenates strings", () => {
    expect(concatHelper("hello", " ", "world")).toBe("hello world");
  });

  it("concatenates two strings", () => {
    expect(concatHelper("a", "b")).toBe("ab");
  });
});

describe("joinHelper", () => {
  it("joins array with separator", () => {
    expect(joinHelper(["a", "b", "c"], ", ")).toBe("a, b, c");
  });

  it("handles single-element array", () => {
    expect(joinHelper(["a"], ", ")).toBe("a");
  });

  it("defaults to comma separator", () => {
    expect(joinHelper(["a", "b"])).toBe("a,b");
  });
});

describe("indentString", () => {
  it("indents each line by N spaces", () => {
    expect(indentString("line1\nline2", 4)).toBe("    line1\n    line2");
  });

  it("does not indent empty lines", () => {
    expect(indentString("line1\n\nline2", 4)).toBe("    line1\n\n    line2");
  });

  it("preserves relative indentation", () => {
    expect(indentString("def foo():\n    pass", 4)).toBe(
      "    def foo():\n        pass",
    );
  });
});

describe("eqHelper", () => {
  const cases: [string | number | boolean, string | number | boolean, boolean][] = [
    ["a", "a", true],
    ["a", "b", false],
    [1, 1, true],
    [true, true, true],
    [true, false, false],
  ];

  it.each(cases)("eq(%s, %s) -> %s", (a, b, expected) => {
    expect(eqHelper(a, b)).toBe(expected);
  });
});

describe("neHelper", () => {
  const cases: [string | number, string | number, boolean][] = [
    ["a", "b", true],
    ["a", "a", false],
    [1, 2, true],
  ];

  it.each(cases)("ne(%s, %s) -> %s", (a, b, expected) => {
    expect(neHelper(a, b)).toBe(expected);
  });
});

describe("andHelper", () => {
  it("returns true when both truthy", () => {
    expect(andHelper(true, true)).toBe(true);
  });

  it("returns false when any falsy", () => {
    expect(andHelper(true, false)).toBe(false);
  });

  it("treats non-empty string as truthy", () => {
    expect(andHelper("a", "b")).toBe(true);
  });

  it("treats empty string as falsy", () => {
    expect(andHelper("a", "")).toBe(false);
  });
});

describe("orHelper", () => {
  it("returns true when any truthy", () => {
    expect(orHelper(false, true)).toBe(true);
  });

  it("returns false when all falsy", () => {
    expect(orHelper(false, false)).toBe(false);
  });
});

describe("notHelper", () => {
  it("inverts boolean", () => {
    expect(notHelper(true)).toBe(false);
    expect(notHelper(false)).toBe(true);
  });

  it("treats empty string as falsy", () => {
    expect(notHelper("")).toBe(true);
  });

  it("treats non-empty string as truthy", () => {
    expect(notHelper("x")).toBe(false);
  });
});
