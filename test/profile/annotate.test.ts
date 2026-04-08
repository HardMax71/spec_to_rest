import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import type { ProfiledService } from "#profile/types.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function profileFixture(name: string): ProfiledService {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  const ir = buildIR(tree);
  return buildProfiledService(ir, "python-fastapi");
}

const fixtures = [
  { name: "url_shortener.spec", entities: 1, ops: 4 },
  { name: "todo_list.spec", entities: 1, ops: 9 },
  { name: "ecommerce.spec", entities: 5, ops: 11 },
  { name: "auth_service.spec", entities: 3, ops: 7 },
] as const;

describe("buildProfiledService", () => {
  it.each(fixtures)("$name has correct entity count ($entities)", ({ name, entities }) => {
    const profiled = profileFixture(name);
    expect(profiled.entities.length).toBe(entities);
  });

  it.each(fixtures)("$name has correct operation count ($ops)", ({ name, ops }) => {
    const profiled = profileFixture(name);
    expect(profiled.operations.length).toBe(ops);
  });

  it.each(fixtures)("$name has matching endpoint count", ({ name, ops }) => {
    const profiled = profileFixture(name);
    expect(profiled.endpoints.length).toBe(ops);
  });

  it("uses python-fastapi-postgres profile", () => {
    const profiled = profileFixture("url_shortener.spec");
    expect(profiled.profile.name).toBe("python-fastapi-postgres");
    expect(profiled.profile.packageManager).toBe("uv");
  });
});

describe("ProfiledEntity naming", () => {
  it("url_shortener UrlMapping entity has correct names", () => {
    const profiled = profileFixture("url_shortener.spec");
    const entity = profiled.entities.find((e) => e.entityName === "UrlMapping")!;
    expect(entity).toBeDefined();
    expect(entity.modelClassName).toBe("UrlMapping");
    expect(entity.createSchemaName).toBe("UrlMappingCreate");
    expect(entity.readSchemaName).toBe("UrlMappingRead");
    expect(entity.updateSchemaName).toBe("UrlMappingUpdate");
    expect(entity.modelFileName).toBe("url_mapping.py");
    expect(entity.schemaFileName).toBe("url_mapping.py");
    expect(entity.routerFileName).toBe("url_mappings.py");
  });

  it("ecommerce entities have snake_case file names", () => {
    const profiled = profileFixture("ecommerce.spec");
    for (const entity of profiled.entities) {
      expect(entity.modelFileName).toMatch(/^[a-z_]+\.py$/);
      expect(entity.schemaFileName).toMatch(/^[a-z_]+\.py$/);
      expect(entity.routerFileName).toMatch(/^[a-z_]+\.py$/);
    }
  });

  it("entity tableName matches schema derivation", () => {
    const profiled = profileFixture("url_shortener.spec");
    const entity = profiled.entities.find((e) => e.entityName === "UrlMapping")!;
    expect(entity.tableName).toBe("url_mappings");
  });
});

describe("ProfiledEntity fields", () => {
  it("url_shortener UrlMapping fields have python types", () => {
    const profiled = profileFixture("url_shortener.spec");
    const entity = profiled.entities.find((e) => e.entityName === "UrlMapping")!;
    for (const field of entity.fields) {
      expect(field.pythonType).toBeTruthy();
      expect(field.pydanticType).toBeTruthy();
      expect(field.sqlalchemyType).toBeTruthy();
      expect(field.sqlalchemyColumnType).toBeTruthy();
    }
  });

  it("todo_list Option fields are nullable", () => {
    const profiled = profileFixture("todo_list.spec");
    const todo = profiled.entities.find((e) => e.entityName === "Todo")!;
    const description = todo.fields.find((f) => f.fieldName === "description")!;
    expect(description.nullable).toBe(true);
    expect(description.pythonType).toContain("| None");
  });

  it("todo_list enum fields map to str", () => {
    const profiled = profileFixture("todo_list.spec");
    const todo = profiled.entities.find((e) => e.entityName === "Todo")!;
    const status = todo.fields.find((f) => f.fieldName === "status")!;
    expect(status.pythonType).toBe("str");
    expect(status.sqlalchemyColumnType).toBe("String");
  });

  it("todo_list Set fields map to set[str]", () => {
    const profiled = profileFixture("todo_list.spec");
    const todo = profiled.entities.find((e) => e.entityName === "Todo")!;
    const tags = todo.fields.find((f) => f.fieldName === "tags")!;
    expect(tags.pythonType).toBe("set[str]");
    expect(tags.sqlalchemyType).toBe("JSONB");
  });
});

describe("ProfiledOperation", () => {
  it("handler names are snake_case", () => {
    const profiled = profileFixture("url_shortener.spec");
    for (const op of profiled.operations) {
      expect(op.handlerName).toMatch(/^[a-z][a-z0-9_]*$/);
    }
  });

  it("url_shortener operations have correct handler names", () => {
    const profiled = profileFixture("url_shortener.spec");
    const names = profiled.operations.map((o) => o.handlerName);
    expect(names).toContain("shorten");
    expect(names).toContain("resolve");
    expect(names).toContain("delete");
    expect(names).toContain("list_all");
  });

  it("operations have endpoint data", () => {
    const profiled = profileFixture("url_shortener.spec");
    for (const op of profiled.operations) {
      expect(op.endpoint).toBeDefined();
      expect(op.endpoint.method).toBeTruthy();
      expect(op.endpoint.path).toBeTruthy();
    }
  });

  it("operations have profiled request/response fields", () => {
    const profiled = profileFixture("url_shortener.spec");
    const shorten = profiled.operations.find((o) => o.operationName === "Shorten")!;
    expect(shorten.requestBodyFields.length).toBeGreaterThan(0);
    expect(shorten.responseFields.length).toBeGreaterThan(0);
    expect(shorten.requestBodyFields[0].pythonType).toBeTruthy();
  });

  it("ecommerce operations have target entities", () => {
    const profiled = profileFixture("ecommerce.spec");
    const withEntities = profiled.operations.filter((o) => o.targetEntity !== null);
    expect(withEntities.length).toBeGreaterThan(0);
  });
});
