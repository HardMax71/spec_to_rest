import type { TypeExpr } from "#ir/types.js";
import type { DeploymentProfile, MappedType } from "#profile/types.js";

export interface TypeContext {
  readonly entityNames: ReadonlySet<string>;
  readonly enumNames: ReadonlySet<string>;
  readonly aliasMap: ReadonlyMap<string, TypeExpr>;
}

export function mapType(
  typeExpr: TypeExpr,
  profile: DeploymentProfile,
  ctx: TypeContext,
): MappedType {
  switch (typeExpr.kind) {
    case "NamedType":
      return mapNamedType(typeExpr.name, profile, ctx);

    case "OptionType": {
      const inner = mapType(typeExpr.innerType, profile, ctx);
      return {
        python: `${inner.python} | None`,
        pydantic: `${inner.pydantic} | None`,
        sqlalchemy: `Mapped[${inner.python} | None]`,
      };
    }

    case "SetType": {
      const elem = mapType(typeExpr.elementType, profile, ctx);
      return {
        python: `set[${elem.python}]`,
        pydantic: `set[${elem.pydantic}]`,
        sqlalchemy: "JSONB",
      };
    }

    case "SeqType": {
      const elem = mapType(typeExpr.elementType, profile, ctx);
      return {
        python: `list[${elem.python}]`,
        pydantic: `list[${elem.pydantic}]`,
        sqlalchemy: "JSONB",
      };
    }

    case "MapType": {
      const key = mapType(typeExpr.keyType, profile, ctx);
      const val = mapType(typeExpr.valueType, profile, ctx);
      return {
        python: `dict[${key.python}, ${val.python}]`,
        pydantic: `dict[${key.pydantic}, ${val.pydantic}]`,
        sqlalchemy: "JSONB",
      };
    }

    case "RelationType":
      return { python: "int", pydantic: "int", sqlalchemy: "Mapped[int]" };
  }
}

function mapNamedType(
  name: string,
  profile: DeploymentProfile,
  ctx: TypeContext,
): MappedType {
  const mapping = profile.typeMap.get(name);
  if (mapping) {
    return {
      python: mapping.python,
      pydantic: mapping.pydantic,
      sqlalchemy: `Mapped[${mapping.python}]`,
    };
  }

  if (ctx.entityNames.has(name)) {
    return { python: "int", pydantic: "int", sqlalchemy: "Mapped[int]" };
  }

  if (ctx.enumNames.has(name)) {
    return { python: "str", pydantic: "str", sqlalchemy: "Mapped[str]" };
  }

  const alias = ctx.aliasMap.get(name);
  if (alias) {
    return mapType(alias, profile, ctx);
  }

  return { python: name, pydantic: name, sqlalchemy: `Mapped[${name}]` };
}
