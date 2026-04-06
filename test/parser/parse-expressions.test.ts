import { describe, it, expect } from "vitest";
import { parseSpec } from "#parser/index.js";

/** Helper: wraps an expression in a minimal spec context as an invariant. */
function parseExpr(exprText: string) {
  const input = `service T { invariant: ${exprText} }`;
  const { errors } = parseSpec(input);
  if (errors.length > 0) {
    throw new Error(
      `Parse errors for expression "${exprText}":\n${errors.map((e) => `  ${e.line}:${e.column} ${e.message}`).join("\n")}`,
    );
  }
}

/** Helper: wraps expressions in an ensures block. */
function parseEnsures(...exprs: string[]) {
  const input = `service T {
    state { x: Int }
    operation Op {
      input: v: Int
      requires: true
      ensures:
        ${exprs.join("\n        ")}
    }
  }`;
  const { errors } = parseSpec(input);
  if (errors.length > 0) {
    throw new Error(
      `Parse errors:\n${errors.map((e) => `  ${e.line}:${e.column} ${e.message}`).join("\n")}`,
    );
  }
}

describe("Primed variables (post-state)", () => {
  it("parses simple primed variable", () => {
    parseEnsures("x' = 42");
  });

  it("parses primed with indexing", () => {
    parseExpr("items'[id] = val");
  });

  it("parses primed with field access", () => {
    parseExpr("users'[id].name = name");
  });

  it("parses pre() with postfix chain", () => {
    parseExpr("pre(items)[id].name = val");
  });
});

describe("Lambda expressions", () => {
  it("parses simple lambda", () => {
    parseExpr("sum(items, i => i.total)");
  });

  it("parses lambda with arithmetic", () => {
    parseExpr("sum(items, i => i.quantity * i.price)");
  });
});

describe("Quantifier expressions", () => {
  it("parses all...in...| pattern", () => {
    parseExpr("all x in items | x > 0");
  });

  it("parses some...in...| pattern", () => {
    parseExpr("some x in items | x.active = true");
  });

  it("parses no...in...| pattern", () => {
    parseExpr("no x in items | x < 0");
  });

  it("parses exists...in...| pattern", () => {
    parseExpr("exists x in items | x = target");
  });

  it("parses multi-binding quantifier", () => {
    parseExpr("all x in items, y in items | x != y implies f(x) != f(y)");
  });

  it("parses nested quantifiers", () => {
    parseExpr("all x in items | all y in items | x != y implies items[x] != items[y]");
  });
});

describe("some() as Option wrapper vs quantifier", () => {
  it("parses some(expr) as Option wrapper", () => {
    parseExpr("result = some(val + 1)");
  });

  it("parses some x in S | P as quantifier", () => {
    parseExpr("some s in sessions | sessions[s].active = true");
  });
});

describe("the expression", () => {
  it("parses the...in...|", () => {
    parseExpr("the x in items | items[x] = target");
  });
});

describe("Constructor expressions", () => {
  it("parses simple constructor", () => {
    parseExpr("result = Item { name = val, count = 0 }");
  });

  it("parses constructor with function calls in fields", () => {
    parseExpr("LoginAttempt { email = email, timestamp = now(), success = true }");
  });
});

describe("With expressions (record update)", () => {
  it("parses simple with expression", () => {
    parseExpr("item with { status = DONE }");
  });

  it("parses with expression with multiple fields", () => {
    parseExpr("pre(items)[id] with { status = DONE, completed_at = some(now()) }");
  });
});

describe("Set and map literals", () => {
  it("parses empty set", () => {
    parseExpr("items = {}");
  });

  it("parses set literal", () => {
    parseExpr("x in {TODO, DONE}");
  });

  it("parses map literal", () => {
    parseExpr('m = {"a" -> 1, "b" -> 2}');
  });

  it("parses set comprehension", () => {
    parseExpr("result = { x in items | x > 0 }");
  });

  it("parses single-element set", () => {
    parseExpr("s = {item}");
  });
});

describe("Sequence literals", () => {
  it("parses empty sequence", () => {
    parseExpr("items = []");
  });

  it("parses sequence literal", () => {
    parseExpr("items = [1, 2, 3]");
  });
});

describe("If-then-else", () => {
  it("parses simple if-then-else", () => {
    parseExpr("if x > 0 then x else 0");
  });

  it("parses nested if-then-else", () => {
    parseExpr("if x < lo then lo else if x > hi then hi else x");
  });
});

describe("Let expressions", () => {
  it("parses simple let", () => {
    parseExpr("let total = #items in total >= 0");
  });

  it("parses nested let", () => {
    parseExpr("let a = x + 1 in let b = a * 2 in b > 0");
  });
});

describe("Pre expressions", () => {
  it("parses pre(ident)", () => {
    parseExpr("pre(store) = store'");
  });

  it("parses pre with postfix", () => {
    parseExpr("pre(items)[id].name = val");
  });
});

describe("Operator precedence", () => {
  it("parses arithmetic precedence (add vs mul)", () => {
    parseExpr("a + b * c = a + (b * c)");
  });

  it("parses comparison with arithmetic", () => {
    parseExpr("x + 1 >= y * 2");
  });

  it("parses division", () => {
    parseExpr("total / count >= 0");
  });

  it("parses division without regex conflict", () => {
    // a / b / c must parse as (a / b) / c, not as a REGEX_LIT
    parseExpr("a / b / c > 0");
  });

  it("parses logical precedence (and vs or)", () => {
    parseExpr("a or b and c");
  });

  it("parses implies", () => {
    parseExpr("x > 0 implies y > 0");
  });

  it("parses iff", () => {
    parseExpr("x > 0 iff y > 0");
  });

  it("parses not in", () => {
    parseExpr("key not in store");
  });

  it("parses subset", () => {
    parseExpr("a subset b");
  });

  it("parses matches with regex", () => {
    parseExpr("value matches /^[a-z]+$/");
  });

  it("* binds tighter than + (tree structure)", () => {
    // Parse `a + b * c` and verify the tree: the top-level node should be
    // addExpr, with mulExpr nested inside its right child.
    const input = `service T { invariant: a + b * c }`;
    const { tree, errors } = parseSpec(input);
    expect(errors).toEqual([]);
    const invariant = tree.serviceDecl().serviceMember(0)!.invariantDecl()!;
    const topExpr = invariant.expr();
    // Top-level should be addExpr (+ has lower precedence, so it's the root)
    expect(topExpr.constructor.name).toBe("AddExprContext");
  });

  it("and binds tighter than or (tree structure)", () => {
    const input = `service T { invariant: a or b and c }`;
    const { tree, errors } = parseSpec(input);
    expect(errors).toEqual([]);
    const invariant = tree.serviceDecl().serviceMember(0)!.invariantDecl()!;
    const topExpr = invariant.expr();
    // Top-level should be orExpr (or has lower precedence, so it's the root)
    expect(topExpr.constructor.name).toBe("OrExprContext");
  });

  it(". binds tighter than = (tree structure)", () => {
    const input = `service T { invariant: x.name = y }`;
    const { tree, errors } = parseSpec(input);
    expect(errors).toEqual([]);
    const invariant = tree.serviceDecl().serviceMember(0)!.invariantDecl()!;
    const topExpr = invariant.expr();
    // Top-level should be eqExpr (= has lower precedence than .)
    expect(topExpr.constructor.name).toBe("EqExprContext");
  });
});

describe("Set operations", () => {
  it("parses union", () => {
    parseExpr("a union b");
  });

  it("parses intersect", () => {
    parseExpr("a intersect b");
  });

  it("parses minus", () => {
    parseExpr("a minus b");
  });
});

describe("Cardinality", () => {
  it("parses # operator", () => {
    parseExpr("#items > 0");
  });

  it("parses # with pre()", () => {
    parseExpr("#pre(items) + 1 = #items'");
  });
});

describe("Function calls", () => {
  it("parses function call", () => {
    parseExpr("len(name) >= 1");
  });

  it("parses nested function calls", () => {
    parseExpr("hash(trim(password))");
  });

  it("parses function call with multiple args", () => {
    parseExpr("sum(items, i => i.total)");
  });
});

describe("Field access and indexing chains", () => {
  it("parses chained field access", () => {
    parseExpr("order.items.count");
  });

  it("parses indexing", () => {
    parseExpr("items[id]");
  });

  it("parses chained index and field", () => {
    parseExpr("orders[oid].items");
  });

  it("parses complex chain", () => {
    parseExpr("pre(inventory)[sku].reserved");
  });
});

describe("Multiple expressions in ensures block", () => {
  it("parses multiple independent expressions", () => {
    parseEnsures(
      "x' = v",
      "#x' > 0",
    );
  });

  it("parses complex multi-expression ensures", () => {
    parseEnsures(
      "x' = pre(x) + v",
      "all y in x' | y > 0",
    );
  });
});

describe("Enum member access", () => {
  it("parses qualified enum access", () => {
    parseExpr("status = OrderStatus.PLACED");
  });
});

describe("String concatenation", () => {
  it("parses string + string", () => {
    parseExpr('result = base_url + "/" + code');
  });
});
