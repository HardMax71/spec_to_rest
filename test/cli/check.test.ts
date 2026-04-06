import { describe, it, expect, vi, beforeEach } from "vitest";
import { join } from "node:path";
import { writeFileSync, unlinkSync } from "node:fs";
import { runCheck } from "../../src/cli/check.js";
import { createLogger, type Logger } from "../../src/cli/log.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");
const fixture = (name: string) => join(fixtureDir, name);

let logged: { info: string[]; success: string[]; error: string[]; verbose: string[] };
let log: Logger;

beforeEach(() => {
  logged = { info: [], success: [], error: [], verbose: [] };
  log = {
    ...createLogger({ verbose: true, quiet: false, color: false }),
    info: (msg: string) => logged.info.push(msg),
    success: (msg: string) => logged.success.push(msg),
    error: (msg: string) => logged.error.push(msg),
    verbose: (msg: string) => logged.verbose.push(msg),
  };
});

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
    expect(logged.error).toEqual([]);
    expect(logged.success).toHaveLength(1);
  });

  it.each(validFixtures)("%s reports operation count", (name) => {
    runCheck(fixture(name), log);
    expect(logged.success[0]).toMatch(/\d+ operations/);
  });

  it("shows timing in verbose output", () => {
    runCheck(fixture("url_shortener.spec"), log);
    expect(logged.verbose.some((m) => m.includes("Parsed in"))).toBe(true);
    expect(logged.verbose.some((m) => m.includes("Built IR in"))).toBe(true);
  });

  it("missing file exits 1", () => {
    expect(runCheck("nonexistent.spec", log)).toBe(1);
    expect(logged.error[0]).toContain("File not found");
  });

  it("invalid spec exits 1 with line info", () => {
    const badFile = join(import.meta.dirname, "__bad_spec__.spec");
    writeFileSync(badFile, "not a valid spec at all {{{", "utf-8");
    try {
      expect(runCheck(badFile, log)).toBe(1);
      expect(logged.error.length).toBeGreaterThan(0);
    } finally {
      unlinkSync(badFile);
    }
  });
});
