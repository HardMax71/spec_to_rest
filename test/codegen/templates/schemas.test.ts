import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import { emitProject } from "#codegen/emit.js";
import type { ProfiledService } from "#profile/types.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function profiledFrom(name: string): ProfiledService {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree } = parseSpec(src);
  return buildProfiledService(buildIR(tree), "python-fastapi-postgres");
}

describe("schemas/entity.py template — via emitProject", () => {
  it.each([
    ["url_shortener.spec", "UrlMapping"],
    ["todo_list.spec", "Todo"],
  ])(
    "emits Create/Read/Update classes for %s.%s",
    (fixture, entityName) => {
      const files = emitProject(profiledFrom(fixture));
      const snake = entityName.replace(/([A-Z])/g, "_$1").replace(/^_/, "").toLowerCase();
      const schema = files.find((f) => f.path === `app/schemas/${snake}.py`)!;
      expect(schema.content).toContain(`class ${entityName}Create(BaseModel):`);
      expect(schema.content).toContain(`class ${entityName}Read(BaseModel):`);
      expect(schema.content).toContain(`class ${entityName}Update(BaseModel):`);
      expect(schema.content).toContain("ConfigDict(from_attributes=True)");
    },
  );

  it("Read schema includes `id: int` and all entity fields", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const files = emitProject(profiled);
    const entity = profiled.entities[0];
    const schema = files.find((f) => f.path === "app/schemas/url_mapping.py")!;
    expect(schema.content).toMatch(/class UrlMappingRead\(BaseModel\):[\s\S]*?id: int/);
    for (const field of entity.fields) {
      expect(schema.content).toContain(`${field.columnName}: ${field.pydanticType}`);
    }
  });

  it("Update schema types every field as `T | None = None`", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const files = emitProject(profiled);
    const entity = profiled.entities[0];
    const schema = files.find((f) => f.path === "app/schemas/url_mapping.py")!;
    const updateBlock = schema.content.split("class UrlMappingUpdate(BaseModel):")[1];
    for (const field of entity.fields) {
      expect(updateBlock).toContain(`${field.columnName}: ${field.pydanticType} | None = None`);
    }
  });
});

describe("schemas/__init__.py template", () => {
  it("re-exports Create/Read/Update for every entity", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const files = emitProject(profiled);
    const init = files.find((f) => f.path === "app/schemas/__init__.py")!;
    for (const entity of profiled.entities) {
      expect(init.content).toContain(entity.createSchemaName);
      expect(init.content).toContain(entity.readSchemaName);
      expect(init.content).toContain(entity.updateSchemaName);
    }
  });
});
