import { describe, it, expect } from "vitest";
import { mapType, resolveTypeExpr, type TypeContext } from "#profile/type-map.js";
import { getProfile } from "#profile/registry.js";
import type { TypeExpr } from "#ir/types.js";

const profile = getProfile("python-fastapi-postgres");
const ctx: TypeContext = {
  entityNames: new Set(["Order", "User"]),
  enumNames: new Set(["Status", "Priority"]),
  aliasMap: new Map(),
};

function named(name: string): TypeExpr {
  return { kind: "NamedType", name };
}

function option(inner: TypeExpr): TypeExpr {
  return { kind: "OptionType", innerType: inner };
}

function set(elem: TypeExpr): TypeExpr {
  return { kind: "SetType", elementType: elem };
}

function seq(elem: TypeExpr): TypeExpr {
  return { kind: "SeqType", elementType: elem };
}

function map(key: TypeExpr, value: TypeExpr): TypeExpr {
  return { kind: "MapType", keyType: key, valueType: value };
}

describe("primitive type mapping", () => {
  const primitives = [
    { spec: "String",   python: "str",       pydantic: "str",       sqlalchemy: "Mapped[str]" },
    { spec: "Int",      python: "int",       pydantic: "int",       sqlalchemy: "Mapped[int]" },
    { spec: "Float",    python: "float",     pydantic: "float",     sqlalchemy: "Mapped[float]" },
    { spec: "Bool",     python: "bool",      pydantic: "bool",      sqlalchemy: "Mapped[bool]" },
    { spec: "Boolean",  python: "bool",      pydantic: "bool",      sqlalchemy: "Mapped[bool]" },
    { spec: "DateTime", python: "datetime",  pydantic: "datetime",  sqlalchemy: "Mapped[datetime]" },
    { spec: "Date",     python: "date",      pydantic: "date",      sqlalchemy: "Mapped[date]" },
    { spec: "UUID",     python: "uuid.UUID", pydantic: "uuid.UUID", sqlalchemy: "Mapped[uuid.UUID]" },
    { spec: "Decimal",  python: "Decimal",   pydantic: "Decimal",   sqlalchemy: "Mapped[Decimal]" },
    { spec: "Bytes",    python: "bytes",     pydantic: "bytes",     sqlalchemy: "Mapped[bytes]" },
    { spec: "Money",    python: "int",       pydantic: "int",       sqlalchemy: "Mapped[int]" },
  ] as const;

  it.each(primitives)("$spec -> python=$python, sqlalchemy=$sqlalchemy", ({ spec, python, pydantic, sqlalchemy }) => {
    const result = mapType(named(spec), profile, ctx);
    expect(result.python).toBe(python);
    expect(result.pydantic).toBe(pydantic);
    expect(result.sqlalchemy).toBe(sqlalchemy);
  });
});

describe("composite type mapping", () => {
  it("Option[String] -> str | None", () => {
    const result = mapType(option(named("String")), profile, ctx);
    expect(result.python).toBe("str | None");
    expect(result.pydantic).toBe("str | None");
    expect(result.sqlalchemy).toBe("Mapped[str | None]");
  });

  it("Set[String] -> list[str] / Mapped[list[str]]", () => {
    const result = mapType(set(named("String")), profile, ctx);
    expect(result.python).toBe("list[str]");
    expect(result.pydantic).toBe("list[str]");
    expect(result.sqlalchemy).toBe("Mapped[list[str]]");
  });

  it("Seq[Int] -> list[int] / Mapped[list[int]]", () => {
    const result = mapType(seq(named("Int")), profile, ctx);
    expect(result.python).toBe("list[int]");
    expect(result.pydantic).toBe("list[int]");
    expect(result.sqlalchemy).toBe("Mapped[list[int]]");
  });

  it("Map[String, Int] -> dict[str, int] / Mapped[dict[str, int]]", () => {
    const result = mapType(map(named("String"), named("Int")), profile, ctx);
    expect(result.python).toBe("dict[str, int]");
    expect(result.pydantic).toBe("dict[str, int]");
    expect(result.sqlalchemy).toBe("Mapped[dict[str, int]]");
  });

  it("Option[Set[String]] -> list[str] | None", () => {
    const result = mapType(option(set(named("String"))), profile, ctx);
    expect(result.python).toBe("list[str] | None");
  });
});

describe("type alias resolution", () => {
  const ctxWithAlias: TypeContext = {
    entityNames: new Set(),
    enumNames: new Set(),
    aliasMap: new Map([
      ["ShortCode", named("String")],
      ["Money", named("Int")],
      ["MaybeStr", option(named("String"))],
    ]),
  };

  it("resolves type alias to underlying type", () => {
    const result = mapType(named("ShortCode"), profile, ctxWithAlias);
    expect(result.python).toBe("str");
    expect(result.sqlalchemy).toBe("Mapped[str]");
  });

  it("resolves Money alias to int", () => {
    const result = mapType(named("Money"), profile, ctxWithAlias);
    expect(result.python).toBe("int");
  });

  it("resolveTypeExpr follows alias chain", () => {
    const resolved = resolveTypeExpr(named("ShortCode"), ctxWithAlias.aliasMap);
    expect(resolved.kind).toBe("NamedType");
    if (resolved.kind === "NamedType") expect(resolved.name).toBe("String");
  });

  it("resolveTypeExpr resolves alias to OptionType", () => {
    const resolved = resolveTypeExpr(named("MaybeStr"), ctxWithAlias.aliasMap);
    expect(resolved.kind).toBe("OptionType");
  });
});

describe("reference type mapping", () => {
  it("entity reference -> int (FK)", () => {
    const result = mapType(named("Order"), profile, ctx);
    expect(result.python).toBe("int");
    expect(result.pydantic).toBe("int");
    expect(result.sqlalchemy).toBe("Mapped[int]");
  });

  it("enum reference -> str", () => {
    const result = mapType(named("Status"), profile, ctx);
    expect(result.python).toBe("str");
    expect(result.pydantic).toBe("str");
    expect(result.sqlalchemy).toBe("Mapped[str]");
  });

  it("unknown named type -> raw name", () => {
    const result = mapType(named("CustomType"), profile, ctx);
    expect(result.python).toBe("CustomType");
    expect(result.sqlalchemy).toBe("Mapped[CustomType]");
  });
});

describe("relation type mapping", () => {
  it("RelationType -> int", () => {
    const rel: TypeExpr = {
      kind: "RelationType",
      fromType: named("Int"),
      multiplicity: "one",
      toType: named("Order"),
    };
    const result = mapType(rel, profile, ctx);
    expect(result.python).toBe("int");
    expect(result.sqlalchemy).toBe("Mapped[int]");
  });
});
