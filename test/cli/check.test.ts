import { describe, it, expect, beforeEach } from "vitest";
import { join } from "node:path";
import { writeFileSync, unlinkSync } from "node:fs";
import { createConsola, LogLevels } from "consola";
import { runCheck } from "#cli/check.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");
const fixture = (name: string) => join(fixtureDir, name);

let messages: { type: string; args: unknown[] }[];
let log: Logger;

beforeEach(() => {
  messages = [];
  log = createConsola({ level: LogLevels.verbose });
  log.mockTypes((type) => (...args: unknown[]) => messages.push({ type, args }));
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

describe("runCheck", () => {
  it.each(validFixtures)("%s exits 0", (name) => {
    expect(runCheck(fixture(name), log)).toBe(0);
    expect(ofType("error")).toEqual([]);
    expect(ofType("success")).toHaveLength(1);
  });

  it.each(validFixtures)("%s reports operation count", (name) => {
    runCheck(fixture(name), log);
    expect(firstArg("success")).toMatch(/\d+ operations/);
  });

  it("shows timing in verbose output", () => {
    runCheck(fixture("url_shortener.spec"), log);
    expect(ofType("verbose").some((m) => String(m.args[0]).includes("Parsed in"))).toBe(true);
    expect(ofType("verbose").some((m) => String(m.args[0]).includes("Built IR in"))).toBe(true);
  });

  it("missing file exits 1", () => {
    expect(runCheck("nonexistent.spec", log)).toBe(1);
    expect(firstArg("error")).toContain("File not found");
  });

  it("invalid spec exits 1 with line info", () => {
    const badFile = join(import.meta.dirname, "__bad_spec__.spec");
    writeFileSync(badFile, "not a valid spec at all {{{", "utf-8");
    try {
      expect(runCheck(badFile, log)).toBe(1);
      expect(ofType("error").length).toBeGreaterThan(0);
    } finally {
      unlinkSync(badFile);
    }
  });
});
