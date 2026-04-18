import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { buildOpenApiDocument } from "#codegen/openapi/build.js";
import { buildIR } from "#ir/index.js";
import { parseSpec } from "#parser/index.js";
import { buildProfiledService } from "#profile/annotate.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function profiledFrom(fixture: string) {
  const src = readFileSync(join(fixtureDir, fixture), "utf-8");
  const { tree } = parseSpec(src);
  return buildProfiledService(buildIR(tree), "python-fastapi-postgres");
}

describe("openapi conformance — paths ↔ endpoints bijection", () => {
  it.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
    "auth_service.spec",
    "edge_cases.spec",
  ])("every entity-backed endpoint + /health maps to one path item in %s", (fixture) => {
    const profiled = profiledFrom(fixture);
    const doc = buildOpenApiDocument(profiled);
    const entityNames = new Set(profiled.entities.map((e) => e.entityName));

    const fromOps = new Set(
      profiled.operations
        .filter((op) => op.targetEntity !== null && entityNames.has(op.targetEntity))
        .map((op) => `${op.endpoint.method} ${op.endpoint.path}`),
    );
    fromOps.add("GET /health");

    const fromDoc = new Set<string>();
    for (const [path, item] of Object.entries(doc.paths)) {
      for (const method of ["get", "post", "put", "patch", "delete"] as const) {
        if (item[method] !== undefined) {
          fromDoc.add(`${method.toUpperCase()} ${path}`);
        }
      }
    }

    expect([...fromDoc].sort()).toEqual([...fromOps].sort());
  });

  it("operationId matches handlerName for every entity-backed operation", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const doc = buildOpenApiDocument(profiled);
    const entityNames = new Set(profiled.entities.map((e) => e.entityName));
    for (const op of profiled.operations) {
      if (op.targetEntity === null || !entityNames.has(op.targetEntity)) continue;
      const method = op.endpoint.method.toLowerCase() as "get" | "post" | "put" | "patch" | "delete";
      const operation = doc.paths[op.endpoint.path][method];
      expect(operation?.operationId).toBe(op.handlerName);
    }
  });
});
