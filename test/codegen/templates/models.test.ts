import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import { emitProject } from "#codegen/emit.js";
import { TemplateEngine } from "#codegen/engine.js";
import { buildRenderContext } from "#codegen/types.js";
import { pythonFastapiPostgresTemplates } from "#codegen/templates.js";
import type { ProfiledService } from "#profile/types.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function profiledFrom(name: string): ProfiledService {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree } = parseSpec(src);
  return buildProfiledService(buildIR(tree), "python-fastapi-postgres");
}

describe("db/base.py template", () => {
  it("declares a single Base(DeclarativeBase) class", () => {
    const engine = new TemplateEngine();
    const ctx = buildRenderContext(profiledFrom("url_shortener.spec"));
    const out = engine.render(pythonFastapiPostgresTemplates.dbBase, ctx);
    expect(out).toContain("class Base(DeclarativeBase):");
    expect(out).toContain("from sqlalchemy.orm import DeclarativeBase");
  });
});

describe("models/__init__.py template", () => {
  it.each(["url_shortener.spec", "todo_list.spec", "ecommerce.spec"])(
    "re-exports every entity model class for %s",
    (fixture) => {
      const engine = new TemplateEngine();
      const ctx = buildRenderContext(profiledFrom(fixture));
      const out = engine.render(pythonFastapiPostgresTemplates.modelInit, ctx);
      for (const entity of ctx.entities) {
        expect(out).toContain(`import ${entity.modelClassName}`);
      }
      expect(out).toContain('"Base"');
    },
  );
});

describe("models/entity.py template — via emitProject", () => {
  it.each([
    ["url_shortener.spec", "UrlMapping", "url_mappings"],
    ["todo_list.spec", "Todo", "todos"],
  ])(
    "emits class + __tablename__ for %s",
    (fixture, entityName, tableName) => {
      const files = emitProject(profiledFrom(fixture));
      const snake = entityName.replace(/([A-Z])/g, "_$1").replace(/^_/, "").toLowerCase();
      const model = files.find((f) => f.path === `app/models/${snake}.py`)!;
      expect(model.content).toContain(`class ${entityName}(Base):`);
      expect(model.content).toContain(`__tablename__ = "${tableName}"`);
    },
  );

  it("emits Mapped[T] = mapped_column(ColType) for every entity field", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const files = emitProject(profiled);
    const entity = profiled.entities[0];
    const model = files.find((f) => f.path === `app/models/${entity.modelFileName}`)!;
    for (const field of entity.fields) {
      expect(model.content).toContain(`${field.columnName}: ${field.sqlalchemyType}`);
      expect(model.content).toContain(field.sqlalchemyColumnType);
    }
  });

  it("always declares `id: Mapped[int] = mapped_column(primary_key=True)`", () => {
    const files = emitProject(profiledFrom("ecommerce.spec"));
    const modelFiles = files.filter((f) => f.path.startsWith("app/models/") && !f.path.endsWith("__init__.py") && !f.path.endsWith("base.py"));
    expect(modelFiles.length).toBeGreaterThan(0);
    for (const f of modelFiles) {
      expect(f.content).toContain("id: Mapped[int] = mapped_column(primary_key=True)");
    }
  });

  it("collects stdlib imports only for types that need them", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const files = emitProject(profiled);
    const model = files.find((f) => f.path === "app/models/url_mapping.py")!;
    expect(model.content).toContain("from datetime import datetime");
  });
});
