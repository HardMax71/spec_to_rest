import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import { TemplateEngine } from "#codegen/engine.js";
import { buildRenderContext } from "#codegen/types.js";
import { pythonFastapiPostgresTemplates } from "#codegen/templates.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function ctxFrom(name: string) {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildRenderContext(buildProfiledService(buildIR(tree), "python-fastapi-postgres"));
}

describe("main.py template", () => {
  const engine = new TemplateEngine();

  it.each(["url_shortener.spec", "todo_list.spec", "ecommerce.spec"])(
    "renders without throwing for %s",
    (fixture) => {
      expect(() => engine.render(pythonFastapiPostgresTemplates.main, ctxFrom(fixture))).not.toThrow();
    },
  );

  it.each([
    ["url_shortener.spec", "UrlShortener"],
    ["todo_list.spec", "TodoList"],
    ["ecommerce.spec", "OrderService"],
  ])("uses the service name in FastAPI title — %s", (fixture, serviceName) => {
    const out = engine.render(pythonFastapiPostgresTemplates.main, ctxFrom(fixture));
    expect(out).toContain(`title="${serviceName}"`);
  });

  it("imports one router per entity for multi-entity services", () => {
    const ctx = ctxFrom("ecommerce.spec");
    const out = engine.render(pythonFastapiPostgresTemplates.main, ctx);
    for (const entity of ctx.entities) {
      const snake = entity.routerFileName.replace(/\.py$/, "");
      expect(out).toContain(`from app.routers import ${snake}`);
      expect(out).toContain(`app.include_router(${snake}.router)`);
    }
  });

  it("always emits a /health endpoint and CORS middleware", () => {
    const out = engine.render(pythonFastapiPostgresTemplates.main, ctxFrom("url_shortener.spec"));
    expect(out).toContain('@app.get("/health"');
    expect(out).toContain("CORSMiddleware");
    expect(out).toContain("lifespan=lifespan");
  });
});
