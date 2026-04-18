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

describe(".gitignore — structural invariants", () => {
  it("ignores Python + venv + tool caches but keeps .env.example tracked", () => {
    const content = emittedFile("url_shortener.spec", ".gitignore");
    for (const entry of [
      "__pycache__/",
      ".venv/",
      ".ruff_cache/",
      ".mypy_cache/",
      ".pytest_cache/",
      ".env",
    ]) {
      expect(content).toContain(entry);
    }
    expect(content).toContain("!.env.example");
  });
});

describe(".dockerignore — structural invariants", () => {
  it("excludes build-irrelevant paths while still shipping app/", () => {
    const content = emittedFile("url_shortener.spec", ".dockerignore");
    for (const entry of [".git/", "__pycache__/", "tests/", ".venv/", ".env"]) {
      expect(content).toContain(entry);
    }
    expect(content).not.toMatch(/^app\/?$/m);
    expect(content).not.toMatch(/^pyproject\.toml$/m);
  });
});
