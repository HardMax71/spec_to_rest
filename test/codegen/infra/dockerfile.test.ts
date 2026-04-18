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

const fixtures = [
  "url_shortener.spec",
  "todo_list.spec",
  "ecommerce.spec",
  "auth_service.spec",
  "edge_cases.spec",
];

describe("Dockerfile — structural invariants", () => {
  it.each(fixtures)("uses python:3.13-slim-bookworm in a two-stage build (%s)", (fixture) => {
    const content = emittedFile(fixture, "Dockerfile");
    const fromLines = content.split("\n").filter((l) => l.startsWith("FROM "));
    expect(fromLines).toHaveLength(2);
    for (const line of fromLines) expect(line).toContain("python:3.13-slim-bookworm");
    expect(content).toContain("FROM python:3.13-slim-bookworm AS builder");
    expect(content).toContain("FROM python:3.13-slim-bookworm AS runtime");
  });

  it.each(fixtures)("installs via uv with cache mount and pinned version (%s)", (fixture) => {
    const content = emittedFile(fixture, "Dockerfile");
    expect(content).toMatch(/ghcr\.io\/astral-sh\/uv:\d+\.\d+\.\d+/);
    expect(content).not.toContain("ghcr.io/astral-sh/uv:latest");
    expect(content).toContain("uv sync --no-dev --no-install-project");
    expect(content).toContain("--mount=type=cache,target=/root/.cache/uv");
  });

  it.each(fixtures)("runs as non-root with HEALTHCHECK and exposes 8000 (%s)", (fixture) => {
    const content = emittedFile(fixture, "Dockerfile");
    expect(content).toContain("useradd --create-home --uid 1000 appuser");
    expect(content).toContain("USER appuser");
    expect(content).toContain("EXPOSE 8000");
    expect(content).toContain("HEALTHCHECK");
    expect(content).toContain("curl -fsS http://localhost:8000/health");
    expect(content).toContain('CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]');
  });
});
