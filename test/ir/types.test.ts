import { describe, it, expect } from "vitest";
import type {
  ServiceIR,
  EntityDecl,
  EnumDecl,
  TypeAliasDecl,
  StateDecl,
  OperationDecl,
  TransitionDecl,
  InvariantDecl,
  FactDecl,
  FunctionDecl,
  PredicateDecl,
  ConventionsDecl,
  TypeExpr,
  Expr,
  Span,
} from "#ir/index.js";

const span: Span = { startLine: 1, startCol: 0, endLine: 1, endCol: 10 };

// ─── Helper expressions ──────────────────────────────────────

const idExpr = (name: string): Expr => ({ kind: "Identifier", name });
const intLit = (value: number): Expr => ({ kind: "IntLit", value });
const strLit = (value: string): Expr => ({ kind: "StringLit", value });
const boolLit = (value: boolean): Expr => ({ kind: "BoolLit", value });
const namedType = (name: string): TypeExpr => ({ kind: "NamedType", name });

// ─── Declaration tests ───────────────────────────────────────

describe("EntityDecl", () => {
  it("constructs with fields, extends, invariants, and where constraints", () => {
    const entity: EntityDecl = {
      kind: "Entity",
      name: "Todo",
      extends_: "Base",
      fields: [
        {
          kind: "Field",
          name: "id",
          typeExpr: namedType("Int"),
          constraint: {
            kind: "BinaryOp",
            op: ">",
            left: idExpr("value"),
            right: intLit(0),
          },
          span,
        },
        {
          kind: "Field",
          name: "title",
          typeExpr: namedType("String"),
          constraint: null,
        },
        {
          kind: "Field",
          name: "tags",
          typeExpr: { kind: "SetType", elementType: namedType("String") },
          constraint: null,
        },
      ],
      invariants: [
        {
          kind: "BinaryOp",
          op: "implies",
          left: {
            kind: "BinaryOp",
            op: "=",
            left: idExpr("status"),
            right: idExpr("DONE"),
          },
          right: {
            kind: "BinaryOp",
            op: "!=",
            left: idExpr("completed_at"),
            right: { kind: "NoneLit" },
          },
        },
      ],
      span,
    };
    expect(entity.kind).toBe("Entity");
    expect(entity.extends_).toBe("Base");
    expect(entity.fields).toHaveLength(3);
    expect(entity.fields[2].typeExpr.kind).toBe("SetType");
    expect(entity.invariants).toHaveLength(1);
  });
});

describe("EnumDecl", () => {
  it("constructs with values", () => {
    const e: EnumDecl = {
      kind: "Enum",
      name: "Status",
      values: ["TODO", "IN_PROGRESS", "DONE", "ARCHIVED"],
      span,
    };
    expect(e.values).toHaveLength(4);
    expect(e.values[1]).toBe("IN_PROGRESS");
  });
});

describe("TypeAliasDecl", () => {
  it("constructs with where constraint", () => {
    const alias: TypeAliasDecl = {
      kind: "TypeAlias",
      name: "Money",
      typeExpr: namedType("Int"),
      constraint: {
        kind: "BinaryOp",
        op: ">=",
        left: idExpr("value"),
        right: intLit(0),
      },
    };
    expect(alias.constraint).not.toBeNull();
    expect(alias.constraint!.kind).toBe("BinaryOp");
  });

  it("constructs with regex constraint", () => {
    const alias: TypeAliasDecl = {
      kind: "TypeAlias",
      name: "Email",
      typeExpr: namedType("String"),
      constraint: {
        kind: "Matches",
        expr: idExpr("value"),
        pattern: "^[^@]+@[^@]+\\.[^@]+$",
      },
    };
    expect(alias.constraint!.kind).toBe("Matches");
  });
});

describe("StateDecl", () => {
  it("constructs with relation types using all multiplicities", () => {
    const state: StateDecl = {
      kind: "State",
      fields: [
        {
          kind: "StateField",
          name: "store",
          typeExpr: {
            kind: "RelationType",
            fromType: namedType("ShortCode"),
            multiplicity: "lone",
            toType: namedType("LongURL"),
          },
        },
        {
          kind: "StateField",
          name: "count",
          typeExpr: namedType("Int"),
        },
        {
          kind: "StateField",
          name: "history",
          typeExpr: { kind: "SeqType", elementType: namedType("String") },
        },
        {
          kind: "StateField",
          name: "tags",
          typeExpr: {
            kind: "MapType",
            keyType: namedType("String"),
            valueType: namedType("Int"),
          },
        },
      ],
    };
    expect(state.fields).toHaveLength(4);
    const rel = state.fields[0].typeExpr;
    expect(rel.kind).toBe("RelationType");
    if (rel.kind === "RelationType") {
      expect(rel.multiplicity).toBe("lone");
    }
  });
});

describe("OperationDecl", () => {
  it("constructs with inputs, outputs, requires, ensures", () => {
    const op: OperationDecl = {
      kind: "Operation",
      name: "Shorten",
      inputs: [{ kind: "Param", name: "url", typeExpr: namedType("LongURL") }],
      outputs: [
        { kind: "Param", name: "code", typeExpr: namedType("ShortCode") },
        { kind: "Param", name: "short_url", typeExpr: namedType("String") },
      ],
      requires: [
        {
          kind: "Call",
          callee: idExpr("isValidURI"),
          args: [idExpr("url")],
        },
      ],
      ensures: [
        {
          kind: "BinaryOp",
          op: "not_in",
          left: idExpr("code"),
          right: { kind: "Pre", expr: idExpr("store") },
        },
      ],
      span,
    };
    expect(op.inputs).toHaveLength(1);
    expect(op.outputs).toHaveLength(2);
    expect(op.requires).toHaveLength(1);
    expect(op.ensures).toHaveLength(1);
  });
});

describe("TransitionDecl", () => {
  it("constructs with rules and guards", () => {
    const trans: TransitionDecl = {
      kind: "Transition",
      name: "OrderLifecycle",
      entityName: "Order",
      fieldName: "status",
      rules: [
        { kind: "TransitionRule", from: "DRAFT", to: "PLACED", via: "PlaceOrder", guard: null },
        {
          kind: "TransitionRule",
          from: "DELIVERED",
          to: "RETURNED",
          via: "ProcessReturn",
          guard: {
            kind: "Call",
            callee: idExpr("withinReturnWindow"),
            args: [idExpr("order_id")],
          },
        },
      ],
    };
    expect(trans.rules).toHaveLength(2);
    expect(trans.rules[0].guard).toBeNull();
    expect(trans.rules[1].guard).not.toBeNull();
  });
});

describe("InvariantDecl / FactDecl", () => {
  it("constructs named invariant", () => {
    const inv: InvariantDecl = {
      kind: "Invariant",
      name: "positive",
      expr: {
        kind: "BinaryOp",
        op: ">=",
        left: idExpr("count"),
        right: intLit(0),
      },
    };
    expect(inv.name).toBe("positive");
  });

  it("constructs anonymous invariant", () => {
    const inv: InvariantDecl = {
      kind: "Invariant",
      name: null,
      expr: boolLit(true),
    };
    expect(inv.name).toBeNull();
  });

  it("constructs fact", () => {
    const fact: FactDecl = {
      kind: "Fact",
      name: "bounded",
      expr: {
        kind: "BinaryOp",
        op: "<=",
        left: { kind: "UnaryOp", op: "cardinality", operand: idExpr("items") },
        right: intLit(1000),
      },
    };
    expect(fact.kind).toBe("Fact");
  });
});

describe("FunctionDecl / PredicateDecl", () => {
  it("constructs function", () => {
    const fn: FunctionDecl = {
      kind: "Function",
      name: "add",
      params: [
        { kind: "Param", name: "a", typeExpr: namedType("Int") },
        { kind: "Param", name: "b", typeExpr: namedType("Int") },
      ],
      returnType: namedType("Int"),
      body: { kind: "BinaryOp", op: "+", left: idExpr("a"), right: idExpr("b") },
    };
    expect(fn.params).toHaveLength(2);
    expect(fn.returnType.kind).toBe("NamedType");
  });

  it("constructs predicate", () => {
    const pred: PredicateDecl = {
      kind: "Predicate",
      name: "isPositive",
      params: [{ kind: "Param", name: "n", typeExpr: namedType("Int") }],
      body: { kind: "BinaryOp", op: ">", left: idExpr("n"), right: intLit(0) },
    };
    expect(pred.kind).toBe("Predicate");
  });
});

describe("ConventionsDecl", () => {
  it("constructs with simple and qualified rules", () => {
    const conv: ConventionsDecl = {
      kind: "Conventions",
      rules: [
        {
          kind: "ConventionRule",
          target: "Shorten",
          property: "http_method",
          qualifier: null,
          value: strLit("POST"),
        },
        {
          kind: "ConventionRule",
          target: "Resolve",
          property: "http_header",
          qualifier: "Location",
          value: {
            kind: "FieldAccess",
            base: idExpr("output"),
            field: "url",
          },
        },
      ],
    };
    expect(conv.rules).toHaveLength(2);
    expect(conv.rules[0].qualifier).toBeNull();
    expect(conv.rules[1].qualifier).toBe("Location");
  });
});

// ─── TypeExpr tests ──────────────────────────────────────────

describe("TypeExpr variants", () => {
  it("NamedType", () => {
    const t: TypeExpr = namedType("Int");
    expect(t.kind).toBe("NamedType");
  });

  it("SetType", () => {
    const t: TypeExpr = { kind: "SetType", elementType: namedType("String") };
    expect(t.kind).toBe("SetType");
  });

  it("MapType", () => {
    const t: TypeExpr = {
      kind: "MapType",
      keyType: namedType("String"),
      valueType: namedType("Int"),
    };
    expect(t.kind).toBe("MapType");
  });

  it("SeqType", () => {
    const t: TypeExpr = { kind: "SeqType", elementType: namedType("String") };
    expect(t.kind).toBe("SeqType");
  });

  it("OptionType", () => {
    const t: TypeExpr = { kind: "OptionType", innerType: namedType("DateTime") };
    expect(t.kind).toBe("OptionType");
  });

  it("RelationType", () => {
    const t: TypeExpr = {
      kind: "RelationType",
      fromType: namedType("Int"),
      multiplicity: "lone",
      toType: namedType("Todo"),
    };
    expect(t.kind).toBe("RelationType");
  });
});

// ─── Expr variant tests ──────────────────────────────────────

describe("Expr variants", () => {
  it("BinaryOp", () => {
    const e: Expr = { kind: "BinaryOp", op: "+", left: intLit(1), right: intLit(2) };
    expect(e.kind).toBe("BinaryOp");
  });

  it("UnaryOp", () => {
    const e: Expr = { kind: "UnaryOp", op: "not", operand: boolLit(true) };
    expect(e.kind).toBe("UnaryOp");
  });

  it("Quantifier with multiple bindings", () => {
    const e: Expr = {
      kind: "Quantifier",
      quantifier: "all",
      bindings: [
        { variable: "x", domain: idExpr("items"), bindingKind: "in" },
        { variable: "y", domain: idExpr("items"), bindingKind: "in" },
      ],
      body: {
        kind: "BinaryOp",
        op: "implies",
        left: { kind: "BinaryOp", op: "!=", left: idExpr("x"), right: idExpr("y") },
        right: boolLit(true),
      },
    };
    expect(e.kind).toBe("Quantifier");
    if (e.kind === "Quantifier") expect(e.bindings).toHaveLength(2);
  });

  it("SomeWrap", () => {
    const e: Expr = { kind: "SomeWrap", expr: intLit(42) };
    expect(e.kind).toBe("SomeWrap");
  });

  it("The", () => {
    const e: Expr = {
      kind: "The",
      variable: "x",
      domain: idExpr("items"),
      body: { kind: "BinaryOp", op: "=", left: idExpr("x"), right: intLit(1) },
    };
    expect(e.kind).toBe("The");
  });

  it("FieldAccess", () => {
    const e: Expr = { kind: "FieldAccess", base: idExpr("order"), field: "status" };
    expect(e.kind).toBe("FieldAccess");
  });

  it("EnumAccess", () => {
    const e: Expr = { kind: "EnumAccess", base: idExpr("OrderStatus"), member: "PLACED" };
    expect(e.kind).toBe("EnumAccess");
  });

  it("Index", () => {
    const e: Expr = { kind: "Index", base: idExpr("items"), index: idExpr("id") };
    expect(e.kind).toBe("Index");
  });

  it("Call", () => {
    const e: Expr = { kind: "Call", callee: idExpr("len"), args: [idExpr("name")] };
    expect(e.kind).toBe("Call");
  });

  it("Prime", () => {
    const e: Expr = { kind: "Prime", expr: idExpr("store") };
    expect(e.kind).toBe("Prime");
  });

  it("Pre", () => {
    const e: Expr = { kind: "Pre", expr: idExpr("store") };
    expect(e.kind).toBe("Pre");
  });

  it("With", () => {
    const e: Expr = {
      kind: "With",
      base: idExpr("item"),
      updates: [{ name: "status", value: idExpr("DONE") }],
    };
    expect(e.kind).toBe("With");
  });

  it("If", () => {
    const e: Expr = {
      kind: "If",
      condition: { kind: "BinaryOp", op: ">", left: idExpr("x"), right: intLit(0) },
      then: idExpr("x"),
      else_: intLit(0),
    };
    expect(e.kind).toBe("If");
  });

  it("Let", () => {
    const e: Expr = {
      kind: "Let",
      variable: "total",
      value: { kind: "UnaryOp", op: "cardinality", operand: idExpr("items") },
      body: { kind: "BinaryOp", op: ">=", left: idExpr("total"), right: intLit(0) },
    };
    expect(e.kind).toBe("Let");
  });

  it("Lambda", () => {
    const e: Expr = {
      kind: "Lambda",
      param: "i",
      body: { kind: "FieldAccess", base: idExpr("i"), field: "total" },
    };
    expect(e.kind).toBe("Lambda");
  });

  it("Constructor", () => {
    const e: Expr = {
      kind: "Constructor",
      typeName: "LoginAttempt",
      fields: [
        { name: "email", value: idExpr("email") },
        { name: "success", value: boolLit(true) },
      ],
    };
    expect(e.kind).toBe("Constructor");
  });

  it("SetLiteral", () => {
    const e: Expr = { kind: "SetLiteral", elements: [idExpr("TODO"), idExpr("DONE")] };
    expect(e.kind).toBe("SetLiteral");
  });

  it("MapLiteral", () => {
    const e: Expr = {
      kind: "MapLiteral",
      entries: [{ key: strLit("a"), value: intLit(1) }],
    };
    expect(e.kind).toBe("MapLiteral");
  });

  it("SetComprehension", () => {
    const e: Expr = {
      kind: "SetComprehension",
      variable: "x",
      domain: idExpr("items"),
      predicate: { kind: "BinaryOp", op: ">", left: idExpr("x"), right: intLit(0) },
    };
    expect(e.kind).toBe("SetComprehension");
  });

  it("SeqLiteral", () => {
    const e: Expr = { kind: "SeqLiteral", elements: [intLit(1), intLit(2), intLit(3)] };
    expect(e.kind).toBe("SeqLiteral");
  });

  it("Matches", () => {
    const e: Expr = {
      kind: "Matches",
      expr: idExpr("value"),
      pattern: "^[a-z]+$",
    };
    expect(e.kind).toBe("Matches");
  });

  it("Literals", () => {
    expect((intLit(42) as { kind: string }).kind).toBe("IntLit");
    expect(({ kind: "FloatLit", value: 3.14 } satisfies Expr).kind).toBe("FloatLit");
    expect(strLit("hello").kind).toBe("StringLit");
    expect(boolLit(true).kind).toBe("BoolLit");
    expect(({ kind: "NoneLit" } satisfies Expr).kind).toBe("NoneLit");
  });

  it("Identifier", () => {
    expect(idExpr("store").kind).toBe("Identifier");
  });
});

// ─── Full ServiceIR construction ─────────────────────────────

describe("ServiceIR", () => {
  it("constructs a complete service with all member types", () => {
    const service: ServiceIR = {
      kind: "Service",
      name: "TestService",
      imports: ["other.spec"],
      entities: [
        {
          kind: "Entity",
          name: "Item",
          extends_: null,
          fields: [{ kind: "Field", name: "id", typeExpr: namedType("Int"), constraint: null }],
          invariants: [],
        },
      ],
      enums: [{ kind: "Enum", name: "Status", values: ["ACTIVE", "INACTIVE"] }],
      typeAliases: [
        { kind: "TypeAlias", name: "Id", typeExpr: namedType("Int"), constraint: null },
      ],
      state: {
        kind: "State",
        fields: [{ kind: "StateField", name: "items", typeExpr: namedType("Int") }],
      },
      operations: [
        {
          kind: "Operation",
          name: "Get",
          inputs: [],
          outputs: [{ kind: "Param", name: "n", typeExpr: namedType("Int") }],
          requires: [boolLit(true)],
          ensures: [{ kind: "BinaryOp", op: "=", left: idExpr("n"), right: idExpr("items") }],
        },
      ],
      transitions: [],
      invariants: [{ kind: "Invariant", name: null, expr: boolLit(true) }],
      facts: [],
      functions: [],
      predicates: [],
      conventions: null,
      span,
    };
    expect(service.kind).toBe("Service");
    expect(service.entities).toHaveLength(1);
    expect(service.enums).toHaveLength(1);
    expect(service.operations).toHaveLength(1);
    expect(service.conventions).toBeNull();
  });
});
