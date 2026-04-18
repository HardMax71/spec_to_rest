import { readFileSync } from "node:fs";
import { join } from "node:path";
import yaml from "js-yaml";
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

interface ComposeDoc {
  readonly version?: unknown;
  readonly services: Record<string, ComposeService>;
  readonly volumes?: Record<string, unknown>;
}

interface ComposeService {
  readonly image?: string;
  readonly build?: string;
  readonly command?: unknown;
  readonly environment?: Record<string, string>;
  readonly env_file?: string;
  readonly ports?: readonly string[];
  readonly healthcheck?: { readonly test: readonly string[] };
  readonly depends_on?: Record<string, { readonly condition: string }>;
  readonly volumes?: readonly string[];
}

const fixtures = [
  "url_shortener.spec",
  "todo_list.spec",
  "ecommerce.spec",
  "auth_service.spec",
  "edge_cases.spec",
];

function parse(fixture: string): ComposeDoc {
  const raw = emittedFile(fixture, "docker-compose.yml");
  return yaml.load(raw) as ComposeDoc;
}

describe("docker-compose.yml — structural invariants", () => {
  it.each(fixtures)("omits the deprecated version key (%s)", (fixture) => {
    const doc = parse(fixture);
    expect(doc.version).toBeUndefined();
  });

  it.each(fixtures)("defines db, migrations, app services (%s)", (fixture) => {
    const doc = parse(fixture);
    expect(Object.keys(doc.services).sort()).toEqual(["app", "db", "migrations"]);
  });

  it.each(fixtures)("db uses postgres:17-alpine with pg_isready healthcheck (%s)", (fixture) => {
    const db = parse(fixture).services.db;
    expect(db.image).toBe("postgres:17-alpine");
    expect(db.healthcheck?.test).toContain("CMD-SHELL");
    expect(db.healthcheck?.test.join(" ")).toContain("pg_isready");
    expect(db.volumes).toContain("db_data:/var/lib/postgresql/data");
  });

  it.each(fixtures)("migrations runs alembic upgrade head and depends on db (%s)", (fixture) => {
    const mig = parse(fixture).services.migrations;
    expect(mig.command).toEqual(["alembic", "upgrade", "head"]);
    expect(mig.depends_on?.db?.condition).toBe("service_healthy");
  });

  it.each(fixtures)("app depends on migrations service_completed_successfully (%s)", (fixture) => {
    const app = parse(fixture).services.app;
    expect(app.depends_on?.db?.condition).toBe("service_healthy");
    expect(app.depends_on?.migrations?.condition).toBe("service_completed_successfully");
    expect(app.ports).toContain("8000:8000");
    expect(app.env_file).toBe(".env");
  });

  it.each(fixtures)("declares the db_data named volume (%s)", (fixture) => {
    const doc = parse(fixture);
    expect(doc.volumes).toBeDefined();
    expect(Object.keys(doc.volumes ?? {})).toContain("db_data");
  });
});
