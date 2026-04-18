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

describe("README.md — structural invariants", () => {
  it("titles with the service name and mentions quick-start commands", () => {
    const content = emittedFile("url_shortener.spec", "README.md");
    expect(content).toMatch(/^# UrlShortener/m);
    expect(content).toContain("cp .env.example .env");
    expect(content).toContain("make docker-up");
    expect(content).toContain("curl http://localhost:8000/health");
    expect(content).toContain("openapi.yaml");
  });

  it("mentions uv for non-Docker development", () => {
    const content = emittedFile("todo_list.spec", "README.md");
    expect(content).toContain("uv sync --all-extras");
    expect(content).toContain("uv run uvicorn app.main:app --reload");
  });
});
