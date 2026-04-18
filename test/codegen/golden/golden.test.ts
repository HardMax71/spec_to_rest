import { existsSync, readFileSync } from "node:fs";
import { readdir } from "node:fs/promises";
import { join, relative, sep } from "node:path";
import { describe, expect, it } from "vitest";
import { emitProject, type EmittedFile } from "#codegen/emit.js";
import { buildIR } from "#ir/index.js";
import { parseSpec } from "#parser/index.js";
import { buildProfiledService } from "#profile/annotate.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");
const goldenRoot = join(import.meta.dirname, "expected");

const FROZEN_DATE = "2026-04-18";
const FROZEN_REVISION = "001";

const fixtures = [
  "url_shortener",
  "todo_list",
  "ecommerce",
  "auth_service",
  "edge_cases",
] as const;

function emitFor(fixture: string): readonly EmittedFile[] {
  const src = readFileSync(join(fixtureDir, `${fixture}.spec`), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return emitProject(
    buildProfiledService(buildIR(tree), "python-fastapi-postgres"),
    { createdDate: FROZEN_DATE, revision: FROZEN_REVISION },
  );
}

async function walkGolden(fixture: string): Promise<string[]> {
  const root = join(goldenRoot, fixture);
  if (!existsSync(root)) return [];
  const out: string[] = [];
  async function rec(dir: string): Promise<void> {
    for (const ent of await readdir(dir, { withFileTypes: true })) {
      const p = join(dir, ent.name);
      if (ent.isDirectory()) await rec(p);
      else out.push(relative(root, p).split(sep).join("/"));
    }
  }
  await rec(root);
  return out.sort();
}

const emitted: Record<string, readonly EmittedFile[]> = Object.fromEntries(
  fixtures.map((f) => [f, emitFor(f)]),
);

describe.each(fixtures)("golden files — %s", (fixture) => {
  const files = emitted[fixture];

  it.each(files.map((f) => [f.path]))("matches golden — %s", async (relPath) => {
    const file = files.find((f) => f.path === relPath);
    expect(file, `${relPath} not emitted`).toBeDefined();
    await expect(file!.content).toMatchFileSnapshot(join(goldenRoot, fixture, relPath));
  });

  it("emitted path set equals golden-tree path set", async () => {
    const emittedPaths = files.map((f) => f.path).sort();
    const goldenPaths = await walkGolden(fixture);
    expect(emittedPaths).toEqual(goldenPaths);
  });
});
