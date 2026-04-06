import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { join } from "node:path";
import { writeFileSync, unlinkSync } from "node:fs";
import { createConsola, LogLevels } from "consola";
import { runInspect } from "#cli/inspect.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");
const fixture = (name: string) => join(fixtureDir, name);

let messages: { type: string; args: unknown[] }[];
let log: Logger;
let stdout: string[];

beforeEach(() => {
  messages = [];
  stdout = [];
  log = createConsola({ level: LogLevels.info });
  log.mockTypes((type) => (...args: unknown[]) => messages.push({ type, args }));
  vi.spyOn(console, "log").mockImplementation((...args: unknown[]) => {
    stdout.push(args.map(String).join(" "));
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

const ofType = (type: string) => messages.filter((m) => m.type === type);
const firstArg = (type: string, i = 0) => String(ofType(type)[i]?.args[0] ?? "");

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
    expect(ofType("error")).toEqual([]);
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
    expect(firstArg("error")).toContain("File not found");
  });

  it("invalid spec exits 1 with parse errors", () => {
    const badFile = join(import.meta.dirname, "__bad_inspect__.spec");
    writeFileSync(badFile, "garbage {{", "utf-8");
    try {
      expect(runInspect(badFile, { format: "summary" }, log)).toBe(1);
      expect(ofType("error").length).toBeGreaterThan(0);
    } finally {
      unlinkSync(badFile);
    }
  });
});
