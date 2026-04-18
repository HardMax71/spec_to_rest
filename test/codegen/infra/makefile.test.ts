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

const requiredTargets = [
  "help",
  "install",
  "run",
  "test",
  "lint",
  "typecheck",
  "migrate",
  "docker-up",
  "docker-down",
  "clean",
];

describe("Makefile — structural invariants", () => {
  it.each(requiredTargets)("declares the '%s' target", (target) => {
    const content = emittedFile("url_shortener.spec", "Makefile");
    expect(content).toMatch(new RegExp(`^${target}:`, "m"));
  });

  it("lists every target in .PHONY", () => {
    const content = emittedFile("url_shortener.spec", "Makefile");
    const phonyLine = content.split("\n").find((l) => l.startsWith(".PHONY:"));
    expect(phonyLine).toBeDefined();
    for (const target of requiredTargets) {
      expect(phonyLine).toContain(target);
    }
  });

  it("uses uv for test/lint/migrate commands", () => {
    const content = emittedFile("url_shortener.spec", "Makefile");
    expect(content).toContain("uv run pytest");
    expect(content).toContain("uv run ruff check");
    expect(content).toContain("uv run alembic upgrade head");
  });
});
