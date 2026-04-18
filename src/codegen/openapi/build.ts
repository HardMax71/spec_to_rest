import { buildComponents, type BuildContext } from "#codegen/openapi/components.js";
import { buildPaths } from "#codegen/openapi/paths.js";
import type { OpenApiDocument, TagObject } from "#codegen/openapi/types.js";
import { toSnakeCase } from "#convention/naming.js";
import type { EntityDecl, EnumDecl, TypeAliasDecl } from "#ir/types.js";
import type { ProfiledService } from "#profile/types.js";

export function buildOpenApiDocument(profiled: ProfiledService): OpenApiDocument {
  const aliasMap: ReadonlyMap<string, TypeAliasDecl> = new Map(
    profiled.ir.typeAliases.map((a) => [a.name, a]),
  );
  const enumMap: ReadonlyMap<string, EnumDecl> = new Map(
    profiled.ir.enums.map((e) => [e.name, e]),
  );
  const entityDecls: ReadonlyMap<string, EntityDecl> = new Map(
    profiled.ir.entities.map((e) => [e.name, e]),
  );
  const entityNames: ReadonlySet<string> = new Set(profiled.entities.map((e) => e.entityName));

  const ctx: BuildContext = { aliasMap, enumMap, entityNames, entityDecls };

  return {
    openapi: "3.1.0",
    info: {
      title: profiled.ir.name,
      version: "0.1.0",
      description: `API for ${profiled.ir.name}. Generated from formal specification.`,
    },
    servers: [{ url: "http://localhost:8000", description: "Local development" }],
    paths: buildPaths(profiled, ctx),
    components: buildComponents(profiled, ctx),
    tags: buildTags(profiled),
  };
}

function buildTags(profiled: ProfiledService): readonly TagObject[] {
  const tags: TagObject[] = profiled.entities.map((e) => ({
    name: toSnakeCase(e.entityName),
    description: `${e.entityName} operations`,
  }));
  tags.push({ name: "infrastructure", description: "Health and metrics endpoints" });
  return tags;
}
