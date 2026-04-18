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

describe("services/entity.py template — via emitProject", () => {
  it("declares a service class that wraps AsyncSession", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const svc = files.find((f) => f.path === "app/services/url_mapping.py")!;
    expect(svc.content).toContain("class UrlMappingService:");
    expect(svc.content).toContain("def __init__(self, session: AsyncSession) -> None:");
    expect(svc.content).toContain("self._session = session");
  });

  it("emits a stub for creates whose body does not match the entity create schema", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const svc = files.find((f) => f.path === "app/services/url_mapping.py")!;
    expect(svc.content).toMatch(/async def shorten\(self, body: ShortenRequest\)/);
    expect(svc.content).toMatch(/raise NotImplementedError/);
  });

  it("emits real SQL for delete against the path-param column", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const svc = files.find((f) => f.path === "app/services/url_mapping.py")!;
    expect(svc.content).toContain("sa_delete(UrlMapping).where(UrlMapping.code == code)");
    expect(svc.content).toContain("return result.rowcount > 0");
  });

  it("emits a NotImplementedError stub for non-CRUD operations (transitions)", () => {
    const files = emitProject(profiledFrom("todo_list.spec"));
    const svc = files.find((f) => f.path === "app/services/todo.py")!;
    expect(svc.content).toMatch(/raise NotImplementedError\(/);
  });

  it("ecommerce produces one service file per entity, each with a class", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const files = emitProject(profiled);
    for (const entity of profiled.entities) {
      const snake = entity.modelFileName.replace(/\.py$/, "");
      const svc = files.find((f) => f.path === `app/services/${snake}.py`);
      expect(svc).toBeDefined();
      expect(svc!.content).toContain(`class ${entity.entityName}Service:`);
    }
  });
});

describe("services/__init__.py template", () => {
  it("re-exports every entity's *Service class", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const files = emitProject(profiled);
    const init = files.find((f) => f.path === "app/services/__init__.py")!;
    for (const entity of profiled.entities) {
      expect(init.content).toContain(`${entity.entityName}Service`);
    }
  });
});
