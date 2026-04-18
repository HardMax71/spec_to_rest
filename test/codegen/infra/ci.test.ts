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

interface CiStep {
  readonly name?: string;
  readonly uses?: string;
  readonly run?: string;
  readonly if?: string;
  readonly with?: Record<string, string>;
}

interface CiJob {
  readonly "runs-on": string;
  readonly needs?: string | readonly string[];
  readonly services?: Record<string, { readonly image: string; readonly options?: string }>;
  readonly env?: Record<string, string>;
  readonly steps: readonly CiStep[];
}

interface CiWorkflow {
  readonly name: string;
  readonly on: Record<string, { readonly branches: readonly string[] }>;
  readonly jobs: Record<string, CiJob>;
}

const fixtures = [
  "url_shortener.spec",
  "todo_list.spec",
  "ecommerce.spec",
  "auth_service.spec",
  "edge_cases.spec",
];

function parse(fixture: string): CiWorkflow {
  const raw = emittedFile(fixture, ".github/workflows/ci.yml");
  return yaml.load(raw) as CiWorkflow;
}

describe(".github/workflows/ci.yml — structural invariants", () => {
  it.each(fixtures)("runs on push and pull_request to main (%s)", (fixture) => {
    const doc = parse(fixture);
    expect(doc.on.push?.branches).toEqual(["main"]);
    expect(doc.on.pull_request?.branches).toEqual(["main"]);
  });

  it.each(fixtures)("defines test and docker jobs with docker.needs=test (%s)", (fixture) => {
    const doc = parse(fixture);
    expect(Object.keys(doc.jobs).sort()).toEqual(["docker", "test"]);
    expect(doc.jobs.docker.needs).toBe("test");
  });

  it.each(fixtures)("test job spins up postgres service with healthcheck (%s)", (fixture) => {
    const doc = parse(fixture);
    const pg = doc.jobs.test.services?.postgres;
    expect(pg?.image).toBe("postgres:17-alpine");
    expect(pg?.options ?? "").toContain("pg_isready");
  });

  it.each(fixtures)("test job uses setup-uv and setup-python 3.13 (%s)", (fixture) => {
    const steps = parse(fixture).jobs.test.steps;
    const uses = steps.map((s) => s.uses).filter((u): u is string => typeof u === "string");
    expect(uses).toContain("astral-sh/setup-uv@v7");
    const setupPython = steps.find((s) => s.uses === "actions/setup-python@v5");
    expect(setupPython).toBeDefined();
    expect(setupPython?.with?.["python-version"]).toBe("3.13");
  });

  it.each(fixtures)("runs ruff, mypy, alembic, pytest, schemathesis (%s)", (fixture) => {
    const steps = parse(fixture).jobs.test.steps;
    const commands = steps
      .map((s) => s.run ?? "")
      .filter((r) => r.length > 0)
      .join("\n");
    expect(commands).toContain("uv run ruff check");
    expect(commands).toContain("uv run mypy");
    expect(commands).toContain("uv run alembic upgrade head");
    expect(commands).toContain("uv run pytest");
    expect(commands).toContain(
      "uv run schemathesis run openapi.yaml --base-url http://localhost:8000 --stateful=links",
    );
  });

  it.each(fixtures)("uses polling wait for /health instead of sleep (%s)", (fixture) => {
    const content = emittedFile(fixture, ".github/workflows/ci.yml");
    expect(content).toMatch(/until curl -fsS http:\/\/localhost:8000\/health/);
    expect(content).not.toMatch(/^\s*run:\s*sleep 3\s*$/m);
  });

  it.each(fixtures)("docker job tears down with compose down -v (%s)", (fixture) => {
    const steps = parse(fixture).jobs.docker.steps;
    const tearDown = steps.find((s) => (s.run ?? "").includes("docker compose down"));
    expect(tearDown).toBeDefined();
    expect(tearDown?.if).toBe("always()");
  });

  it.each(fixtures)("docker job seeds .env from .env.example before compose up (%s)", (fixture) => {
    const steps = parse(fixture).jobs.docker.steps;
    const seed = steps.findIndex((s) => (s.run ?? "").includes("cp .env.example .env"));
    const up = steps.findIndex((s) => (s.run ?? "").includes("docker compose up"));
    expect(seed).toBeGreaterThanOrEqual(0);
    expect(up).toBeGreaterThan(seed);
  });
});
