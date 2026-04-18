import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { emitProject } from "#codegen/emit.js";
import { buildIR } from "#ir/index.js";
import { parseSpec } from "#parser/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import type { ProfiledService } from "#profile/types.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function profiledFrom(name: string): ProfiledService {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildProfiledService(buildIR(tree), "python-fastapi-postgres");
}

function emittedFile(fixture: string, path: string): string {
  const files = emitProject(profiledFrom(fixture));
  const file = files.find((f) => f.path === path);
  expect(file, `${path} not emitted for ${fixture}`).toBeDefined();
  return file!.content;
}

function python3Available(): boolean {
  const probe = spawnSync("python3", ["--version"], { encoding: "utf-8" });
  return probe.status === 0;
}

describe("alembic/env.py — structural invariants", () => {
  it("imports the app models so Base.metadata is populated before alembic runs", () => {
    const content = emittedFile("url_shortener.spec", "alembic/env.py");
    expect(content).toContain("import app.models");
    expect(content).toContain("from app.models.base import Base");
    expect(content).toContain("target_metadata = Base.metadata");
  });

  it("uses the async alembic pattern (asyncio.run + async_engine_from_config)", () => {
    const content = emittedFile("url_shortener.spec", "alembic/env.py");
    expect(content).toContain("import asyncio");
    expect(content).toContain("from sqlalchemy.ext.asyncio import async_engine_from_config");
    expect(content).toContain("asyncio.run(run_migrations_online())");
    expect(content).toContain("await connection.run_sync(do_run_migrations)");
  });

  it("does not swap the async driver (runs alembic on asyncpg directly)", () => {
    const content = emittedFile("url_shortener.spec", "alembic/env.py");
    expect(content).not.toContain("+psycopg");
    expect(content).not.toContain("from sqlalchemy import engine_from_config");
    expect(content).toContain(
      'config.set_main_option("sqlalchemy.url", settings.database_url)',
    );
  });

  it("defines both offline and online migration entry points", () => {
    const content = emittedFile("url_shortener.spec", "alembic/env.py");
    expect(content).toContain("def run_migrations_offline() -> None:");
    expect(content).toContain("async def run_migrations_online() -> None:");
    expect(content).toContain("if context.is_offline_mode():");
  });

  const maybeIt = python3Available() ? it : it.skip;
  maybeIt.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
  ])("parses as valid Python via ast.parse (%s)", (fixture) => {
    const content = emittedFile(fixture, "alembic/env.py");
    const proc = spawnSync(
      "python3",
      ["-c", "import ast, sys; ast.parse(sys.stdin.read())"],
      { input: content, encoding: "utf-8" },
    );
    if (proc.status !== 0) {
      throw new Error(`env.py for ${fixture} failed ast.parse:\n${proc.stderr}`);
    }
  });
});

describe("alembic.ini — structural invariants", () => {
  it("points script_location at alembic and leaves sqlalchemy.url blank (env.py sets it)", () => {
    const content = emittedFile("url_shortener.spec", "alembic.ini");
    expect(content).toMatch(/script_location\s*=\s*alembic/);
    expect(content).toMatch(/sqlalchemy\.url\s*=\s*$/m);
  });

  it("defines the standard alembic logger config blocks", () => {
    const content = emittedFile("url_shortener.spec", "alembic.ini");
    expect(content).toContain("[loggers]");
    expect(content).toContain("[logger_alembic]");
    expect(content).toContain("[logger_sqlalchemy]");
    expect(content).toContain("[handler_console]");
  });
});
