import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { parseSpec } from "#parser/index.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const fixturesDir = join(__dirname, "fixtures");

function readFixture(name: string): string {
  return readFileSync(join(fixturesDir, name), "utf-8");
}

describe("Acceptance: all 4 worked examples parse without error", () => {
  const fixtures: [string, string][] = [
    ["url_shortener.spec", "UrlShortener"],
    ["todo_list.spec", "TodoList"],
    ["auth_service.spec", "AuthService"],
    ["ecommerce.spec", "OrderService"],
  ];

  for (const [file, expectedService] of fixtures) {
    it(`parses ${file} with zero errors`, () => {
      const input = readFixture(file);
      const { tree, errors } = parseSpec(input);

      expect(errors).toEqual([]);
      expect(tree).toBeTruthy();

      // Verify the service name is captured correctly
      const serviceDecl = tree.serviceDecl();
      expect(serviceDecl.UPPER_IDENT().getText()).toBe(expectedService);
    });
  }

  it("parses edge_cases.spec with zero errors", () => {
    const input = readFixture("edge_cases.spec");
    const { errors } = parseSpec(input);
    expect(errors).toEqual([]);
  });
});
