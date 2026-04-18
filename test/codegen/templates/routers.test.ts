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

describe("routers/entity.py template — via emitProject", () => {
  it("creates APIRouter + imports schemas + imports service", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const router = files.find((f) => f.path === "app/routers/url_mappings.py")!;
    expect(router.content).toContain("from fastapi import APIRouter");
    expect(router.content).toContain("from app.schemas.url_mapping import");
    expect(router.content).toContain("from app.services.url_mapping import UrlMappingService");
    expect(router.content).toMatch(/router = APIRouter\(tags=\["url_mapping"\]\)/);
  });

  it.each([
    ["Shorten", "post", "/shorten", 201],
    ["Delete", "delete", "/{code}", 204],
    ["ListAll", "get", "/urls", 200],
  ])(
    "emits @router.%s for operation %s with path %s and status %s",
    (handler, method, path, status) => {
      const files = emitProject(profiledFrom("url_shortener.spec"));
      const router = files.find((f) => f.path === "app/routers/url_mappings.py")!;
      expect(router.content).toContain(`@router.${method}("${path}", status_code=${status})`);
      const expectedHandler = handler
        .replace(/([A-Z])/g, "_$1")
        .replace(/^_/, "")
        .toLowerCase();
      expect(router.content).toContain(`async def ${expectedHandler}(`);
    },
  );

  it("uses Depends(get_session) for every handler", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const router = files.find((f) => f.path === "app/routers/url_mappings.py")!;
    const handlerCount = (router.content.match(/async def /g) ?? []).length;
    const dependsCount = (router.content.match(/Depends\(get_session\)/g) ?? []).length;
    expect(handlerCount).toBeGreaterThan(0);
    expect(dependsCount).toBe(handlerCount);
  });

  it("typed-parameterizes path params using resolved Python types", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const router = files.find((f) => f.path === "app/routers/url_mappings.py")!;
    expect(router.content).toMatch(/code: str,/);
  });

  it("ecommerce generates one router file per entity", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const files = emitProject(profiled);
    for (const entity of profiled.entities) {
      const f = files.find((x) => x.path === `app/routers/${entity.routerFileName}`);
      expect(f).toBeDefined();
      expect(f!.content).toContain("router = APIRouter(");
    }
  });
});

describe("routers/__init__.py template", () => {
  it("imports each entity's router module", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const files = emitProject(profiled);
    const init = files.find((f) => f.path === "app/routers/__init__.py")!;
    for (const entity of profiled.entities) {
      const mod = entity.routerFileName.replace(/\.py$/, "");
      expect(init.content).toContain(`from app.routers import ${mod}`);
    }
  });
});
