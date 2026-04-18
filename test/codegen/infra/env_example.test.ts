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

describe(".env.example — structural invariants", () => {
  it.each(fixtures)("declares DATABASE_URL, BASE_URL, LOG_LEVEL (%s)", (fixture) => {
    const content = emittedFile(fixture, ".env.example");
    expect(content).toMatch(/^DATABASE_URL=postgresql\+asyncpg:\/\//m);
    expect(content).toMatch(/^BASE_URL=http:\/\/localhost:8000$/m);
    expect(content).toMatch(/^LOG_LEVEL=info$/m);
  });

  it("points DATABASE_URL at the compose db host", () => {
    const content = emittedFile("url_shortener.spec", ".env.example");
    expect(content).toContain(
      "DATABASE_URL=postgresql+asyncpg://url_shortener:url_shortener@db:5432/url_shortener",
    );
  });
});
