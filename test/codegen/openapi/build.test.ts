import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { buildOpenApiDocument } from "#codegen/openapi/build.js";
import { buildIR } from "#ir/index.js";
import { parseSpec } from "#parser/index.js";
import { buildProfiledService } from "#profile/annotate.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function docFrom(fixtureName: string) {
  const src = readFileSync(join(fixtureDir, fixtureName), "utf-8");
  const { tree } = parseSpec(src);
  const profiled = buildProfiledService(buildIR(tree), "python-fastapi-postgres");
  return { doc: buildOpenApiDocument(profiled), profiled };
}

describe("buildOpenApiDocument — document shape", () => {
  it.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
    "auth_service.spec",
    "edge_cases.spec",
  ])("emits a well-formed document for %s", (fixture) => {
    const { doc } = docFrom(fixture);
    expect(doc.openapi).toBe("3.1.0");
    expect(doc.info.title).toBeTruthy();
    expect(doc.info.version).toBe("0.1.0");
    expect(doc.servers.length).toBeGreaterThan(0);
    expect(doc.components.schemas.ErrorResponse).toBeDefined();
    expect(doc.paths["/health"].get).toBeDefined();
  });
});

describe("buildOpenApiDocument — per-entity component schemas", () => {
  it("emits Create/Read/Update for every entity in ecommerce", () => {
    const { doc, profiled } = docFrom("ecommerce.spec");
    for (const entity of profiled.entities) {
      expect(doc.components.schemas[entity.createSchemaName]).toBeDefined();
      expect(doc.components.schemas[entity.readSchemaName]).toBeDefined();
      expect(doc.components.schemas[entity.updateSchemaName]).toBeDefined();
    }
  });

  it("Read schema requires id and includes id as integer", () => {
    const { doc } = docFrom("url_shortener.spec");
    const read = doc.components.schemas.UrlMappingRead;
    expect(read.type).toBe("object");
    expect(read.required).toContain("id");
    expect(read.properties?.id).toEqual({ type: "integer" });
  });

  it("Update schema marks all non-id fields as nullable", () => {
    const { doc } = docFrom("url_shortener.spec");
    const update = doc.components.schemas.UrlMappingUpdate;
    for (const [name, schema] of Object.entries(update.properties ?? {})) {
      expect(name).not.toBe("id");
      expect(schema.nullable).toBe(true);
    }
    expect(update.required).toBeUndefined();
  });
});

describe("buildOpenApiDocument — constraint propagation", () => {
  it("ShortCode alias constraints surface on UrlMappingRead.code", () => {
    const { doc } = docFrom("url_shortener.spec");
    const read = doc.components.schemas.UrlMappingRead;
    const code = read.properties?.code;
    expect(code?.minLength).toBe(6);
    expect(code?.maxLength).toBe(10);
    expect(code?.pattern).toBe("^[a-zA-Z0-9]+$");
  });

  it("DateTime field gets format date-time", () => {
    const { doc } = docFrom("url_shortener.spec");
    const read = doc.components.schemas.UrlMappingRead;
    expect(read.properties?.created_at).toEqual({
      type: "string",
      format: "date-time",
    });
  });
});

describe("buildOpenApiDocument — paths mirror endpoints", () => {
  it.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
    "auth_service.spec",
  ])("every endpoint has a corresponding path entry (%s)", (fixture) => {
    const { doc, profiled } = docFrom(fixture);
    for (const ep of profiled.endpoints) {
      const pathItem = doc.paths[ep.path];
      expect(pathItem, `path ${ep.path} missing`).toBeDefined();
      const method = ep.method.toLowerCase() as keyof typeof pathItem;
      expect(pathItem[method], `${ep.method} ${ep.path} missing`).toBeDefined();
    }
  });

  it("DELETE operation with 204 has empty content response", () => {
    const { doc } = docFrom("url_shortener.spec");
    const del = doc.paths["/{code}"].delete;
    expect(del?.responses["204"].content).toBeUndefined();
    expect(del?.responses["204"].description).toBe("No content");
  });

  it("302 redirect responses include a Location header", () => {
    const { doc } = docFrom("url_shortener.spec");
    const get = doc.paths["/{code}"].get;
    expect(get?.responses["302"].headers?.Location).toBeDefined();
  });

  it("POST /shorten request body refs UrlMappingCreate", () => {
    const { doc } = docFrom("url_shortener.spec");
    const post = doc.paths["/shorten"].post;
    const ref = post?.requestBody?.content["application/json"].schema.$ref;
    expect(ref).toBe("#/components/schemas/UrlMappingCreate");
  });

  it("path-parameterized operations include a 404 response", () => {
    const { doc } = docFrom("url_shortener.spec");
    expect(doc.paths["/{code}"].get?.responses["404"]).toBeDefined();
    expect(doc.paths["/{code}"].delete?.responses["404"]).toBeDefined();
  });
});
