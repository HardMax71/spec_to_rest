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

describe("tests/test_health.py — structural invariants", () => {
  it.each(fixtures)("hits /health via ASGITransport and asserts 200 (%s)", (fixture) => {
    const content = emittedFile(fixture, "tests/test_health.py");
    expect(content).toContain("from httpx import ASGITransport, AsyncClient");
    expect(content).toContain("from app.main import app");
    expect(content).toContain("@pytest.mark.asyncio");
    expect(content).toContain('await client.get("/health")');
    expect(content).toContain("response.status_code == 200");
    expect(content).toContain('{"status": "ok"}');
  });
});
