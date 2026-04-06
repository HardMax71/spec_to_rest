import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { join } from "node:path";
import { writeFileSync, unlinkSync } from "node:fs";
import { runInspect } from "../../src/cli/inspect.js";
import { createLogger, type Logger } from "../../src/cli/log.js";
import type { Format } from "../../src/cli/format.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");
const fixture = (name: string) => join(fixtureDir, name);

let logged: { error: string[] };
let stdout: string[];
let log: Logger;

beforeEach(() => {
  logged = { error: [] };
  stdout = [];
  vi.spyOn(console, "log").mockImplementation((...args: unknown[]) => {
    stdout.push(args.map(String).join(" "));
  });
  log = {
    ...createLogger({ verbose: false, quiet: false, color: false }),
    error: (msg: string) => logged.error.push(msg),
  };
});

afterEach(() => {
  vi.restoreAllMocks();
});

const validFixtures = [
  "url_shortener.spec",
  "todo_list.spec",
  "auth_service.spec",
  "ecommerce.spec",
  "edge_cases.spec",
];

describe("runInspect", () => {
  it.each(validFixtures)("%s exits 0 with summary format", (name) => {
    expect(runInspect(fixture(name), { format: "summary" }, log)).toBe(0);
    expect(logged.error).toEqual([]);
    expect(stdout.length).toBeGreaterThan(0);
    expect(stdout[0]).toMatch(/^Service: /);
  });

  it.each(["json", "ir"] as const)("format '%s' produces valid JSON", (fmt) => {
    expect(runInspect(fixture("url_shortener.spec"), { format: fmt }, log)).toBe(0);
    const output = stdout.join("\n");
    expect(() => JSON.parse(output)).not.toThrow();
  });

  it("summary format shows key fields", () => {
    runInspect(fixture("url_shortener.spec"), { format: "summary" }, log);
    const output = stdout.join("\n");
    expect(output).toContain("Operations:");
    expect(output).toContain("Entities:");
    expect(output).toContain("Invariants:");
  });

  it("missing file exits 1", () => {
    expect(runInspect("nonexistent.spec", { format: "summary" }, log)).toBe(1);
    expect(logged.error[0]).toContain("File not found");
  });

  it("invalid spec exits 1 with parse errors", () => {
    const badFile = join(import.meta.dirname, "__bad_inspect__.spec");
    writeFileSync(badFile, "garbage {{", "utf-8");
    try {
      expect(runInspect(badFile, { format: "summary" }, log)).toBe(1);
      expect(logged.error.length).toBeGreaterThan(0);
    } finally {
      unlinkSync(badFile);
    }
  });
});
