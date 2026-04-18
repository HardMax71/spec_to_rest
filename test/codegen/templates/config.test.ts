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
  const { tree } = parseSpec(src);
  return buildRenderContext(buildProfiledService(buildIR(tree), "python-fastapi-postgres"));
}

describe("config.py template", () => {
  const engine = new TemplateEngine();

  it.each(["url_shortener.spec", "todo_list.spec", "ecommerce.spec"])(
    "renders a Settings class for %s",
    (fixture) => {
      const out = engine.render(pythonFastapiPostgresTemplates.config, ctxFrom(fixture));
      expect(out).toContain("class Settings(BaseSettings):");
      expect(out).toContain("settings = Settings()");
    },
  );

  it.each([
    ["url_shortener.spec", "url_shortener"],
    ["todo_list.spec", "todo_list"],
    ["ecommerce.spec", "order_service"],
  ])(
    "default database_url uses service snake-cased name as db/user — %s",
    (fixture, snake) => {
      const out = engine.render(pythonFastapiPostgresTemplates.config, ctxFrom(fixture));
      expect(out).toContain(`postgresql+asyncpg://${snake}:${snake}@localhost:5432/${snake}`);
    },
  );

  it("declares required settings fields with defaults", () => {
    const out = engine.render(pythonFastapiPostgresTemplates.config, ctxFrom("url_shortener.spec"));
    expect(out).toMatch(/database_url: str = /);
    expect(out).toMatch(/base_url: str = /);
    expect(out).toMatch(/log_level: str = /);
  });
});
