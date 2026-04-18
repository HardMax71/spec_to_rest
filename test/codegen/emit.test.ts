import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import { emitProject } from "#codegen/emit.js";
import type { ProfiledService } from "#profile/types.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function profiledFrom(name: string): ProfiledService {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  const ir = buildIR(tree);
  return buildProfiledService(ir, "python-fastapi-postgres");
}

function python3Available(): boolean {
  const probe = spawnSync("python3", ["--version"], { encoding: "utf-8" });
  return probe.status === 0;
}

describe("emitProject — file paths", () => {
  it("produces the expected file tree for url_shortener", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const paths = files.map((f) => f.path).sort();
    expect(paths).toEqual([
      ".dockerignore",
      ".env.example",
      ".github/workflows/ci.yml",
      ".gitignore",
      "Dockerfile",
      "Makefile",
      "README.md",
      "alembic.ini",
      "alembic/env.py",
      "alembic/versions/001_initial_schema.py",
      "app/__init__.py",
      "app/config.py",
      "app/database.py",
      "app/db/__init__.py",
      "app/db/base.py",
      "app/main.py",
      "app/models/__init__.py",
      "app/models/url_mapping.py",
      "app/routers/__init__.py",
      "app/routers/url_mappings.py",
      "app/schemas/__init__.py",
      "app/schemas/url_mapping.py",
      "app/services/__init__.py",
      "app/services/url_mapping.py",
      "docker-compose.yml",
      "openapi.yaml",
      "pyproject.toml",
      "tests/test_health.py",
    ]);
  });

  it("produces one model/schema/router/service file per entity for ecommerce", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const files = emitProject(profiled);
    const paths = new Set(files.map((f) => f.path));
    for (const entity of profiled.entities) {
      const snake = entity.modelFileName.replace(/\.py$/, "");
      const routerSnake = entity.routerFileName.replace(/\.py$/, "");
      expect(paths).toContain(`app/models/${snake}.py`);
      expect(paths).toContain(`app/schemas/${snake}.py`);
      expect(paths).toContain(`app/routers/${routerSnake}.py`);
      expect(paths).toContain(`app/services/${snake}.py`);
    }
  });

  it.each(["url_shortener.spec", "todo_list.spec", "ecommerce.spec"])(
    "returns non-empty content for every file — %s",
    (fixture) => {
      const files = emitProject(profiledFrom(fixture));
      expect(files.length).toBeGreaterThan(0);
      for (const f of files) {
        if (f.path === "app/__init__.py" || f.path === "app/db/__init__.py") continue;
        expect(f.content.length).toBeGreaterThan(0);
      }
    },
  );
});

describe("emitProject — syntactic validity", () => {
  const py3 = python3Available();
  const maybeIt = py3 ? it : it.skip;

  maybeIt.each(["url_shortener.spec", "todo_list.spec", "ecommerce.spec"])(
    "every generated .py file is valid Python — %s",
    (fixture) => {
      const files = emitProject(profiledFrom(fixture));
      for (const f of files) {
        if (!f.path.endsWith(".py")) continue;
        const proc = spawnSync(
          "python3",
          ["-c", "import ast, sys; ast.parse(sys.stdin.read())"],
          { input: f.content, encoding: "utf-8" },
        );
        if (proc.status !== 0) {
          throw new Error(`SyntaxError in ${f.path}:\n${proc.stderr}\n--- content ---\n${f.content}`);
        }
      }
    },
  );
});

describe("emitProject — structural markers", () => {
  it("main.py imports each entity router and registers it", () => {
    const files = emitProject(profiledFrom("ecommerce.spec"));
    const main = files.find((f) => f.path === "app/main.py")!;
    expect(main.content).toMatch(/from app\.routers import products/);
    expect(main.content).toMatch(/app\.include_router\(products\.router\)/);
    expect(main.content).toContain("@app.get(\"/health\"");
  });

  it("model file emits SQLAlchemy 2.0 Mapped columns", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const model = files.find((f) => f.path === "app/models/url_mapping.py")!;
    expect(model.content).toContain("class UrlMapping(Base):");
    expect(model.content).toContain('__tablename__ = "url_mappings"');
    expect(model.content).toContain("id: Mapped[int] = mapped_column(primary_key=True)");
    expect(model.content).toContain("from sqlalchemy.orm import Mapped, mapped_column");
    expect(model.content).toMatch(/code: Mapped\[str\] = mapped_column\(String\)/);
  });

  it("schema file emits Create/Read/Update classes", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const schema = files.find((f) => f.path === "app/schemas/url_mapping.py")!;
    expect(schema.content).toContain("class UrlMappingCreate(BaseModel):");
    expect(schema.content).toContain("class UrlMappingRead(BaseModel):");
    expect(schema.content).toContain("class UrlMappingUpdate(BaseModel):");
    expect(schema.content).toContain("ConfigDict(from_attributes=True)");
  });

  it("router file emits @router.{method} decorators with correct status codes", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const router = files.find((f) => f.path === "app/routers/url_mappings.py")!;
    expect(router.content).toContain('@router.post("/shorten", status_code=201)');
    expect(router.content).toContain('@router.delete("/{code}", status_code=204)');
    expect(router.content).toContain("async def shorten(");
  });

  it("service file emits CRUD method bodies (delete) and stubs for non-matching creates", () => {
    const files = emitProject(profiledFrom("url_shortener.spec"));
    const svc = files.find((f) => f.path === "app/services/url_mapping.py")!;
    expect(svc.content).toContain("class UrlMappingService:");
    expect(svc.content).toContain("sa_delete(UrlMapping).where(UrlMapping.code == code)");
    expect(svc.content).toMatch(
      /async def shorten\(self, body: ShortenRequest\) -> None:\n\s+raise NotImplementedError\(/,
    );
  });

  it("emits NotImplementedError stubs for transition operations (todo_list)", () => {
    const files = emitProject(profiledFrom("todo_list.spec"));
    const svc = files.find((f) => f.path === "app/services/todo.py")!;
    expect(svc.content).toMatch(/raise NotImplementedError/);
  });
});
