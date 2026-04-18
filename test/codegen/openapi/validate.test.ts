import { readFileSync } from "node:fs";
import { join } from "node:path";
import { Validator } from "@seriousme/openapi-schema-validator";
import { describe, expect, it } from "vitest";
import { emitProject } from "#codegen/emit.js";
import { buildIR } from "#ir/index.js";
import { parseSpec } from "#parser/index.js";
import { buildProfiledService } from "#profile/annotate.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function openApiYamlFor(fixture: string): string {
  const src = readFileSync(join(fixtureDir, fixture), "utf-8");
  const { tree } = parseSpec(src);
  const profiled = buildProfiledService(buildIR(tree), "python-fastapi-postgres");
  const files = emitProject(profiled);
  const file = files.find((f) => f.path === "openapi.yaml");
  if (file === undefined) throw new Error("openapi.yaml not in emitted files");
  return file.content;
}

describe("emitted openapi.yaml validates against OpenAPI 3.1", () => {
  it.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
    "auth_service.spec",
    "edge_cases.spec",
  ])("validates for %s", async (fixture) => {
    const yamlContent = openApiYamlFor(fixture);
    const validator = new Validator();
    const result = await validator.validate(yamlContent);
    if (!result.valid) {
      // eslint-disable-next-line no-console
      console.error(`Validation errors for ${fixture}:`, result.errors);
    }
    expect(result.valid).toBe(true);
    expect(validator.version).toBe("3.1");
  });
});
