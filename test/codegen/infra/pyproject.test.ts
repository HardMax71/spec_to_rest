import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { emitProject } from "#codegen/emit.js";
import { buildIR } from "#ir/index.js";
import { parseSpec } from "#parser/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import { PYTHON_FASTAPI_POSTGRES } from "#profile/python-fastapi.js";
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

describe("pyproject.toml — structural invariants", () => {
  it.each(fixtures)("has required section headers and metadata (%s)", (fixture) => {
    const content = emittedFile(fixture, "pyproject.toml");
    expect(content).toContain("[build-system]");
    expect(content).toContain("[project]");
    expect(content).toContain("[project.optional-dependencies]");
    expect(content).toContain("[tool.ruff]");
    expect(content).toContain("[tool.mypy]");
    expect(content).toContain("[tool.pytest.ini_options]");
    expect(content).toContain("[tool.hatch.build.targets.wheel]");
    expect(content).toMatch(/requires-python = ">=3\.10"/);
    expect(content).toContain('packages = ["app"]');
  });

  it.each(fixtures)("lists every profile dependency (%s)", (fixture) => {
    const content = emittedFile(fixture, "pyproject.toml");
    for (const dep of PYTHON_FASTAPI_POSTGRES.dependencies) {
      expect(content).toContain(`"${dep.name}${dep.version}"`);
    }
  });

  it.each(fixtures)("lists every profile dev dependency plus schemathesis (%s)", (fixture) => {
    const content = emittedFile(fixture, "pyproject.toml");
    for (const dep of PYTHON_FASTAPI_POSTGRES.devDependencies) {
      expect(content).toContain(`"${dep.name}${dep.version}"`);
    }
    expect(content).toMatch(/"schemathesis>=/);
  });

  it("uses the service kebab-case name as the project name", () => {
    const content = emittedFile("url_shortener.spec", "pyproject.toml");
    expect(content).toMatch(/name = "url-shortener"/);
  });
});
