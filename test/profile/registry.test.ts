import { describe, it, expect } from "vitest";
import { getProfile, listProfiles } from "#profile/registry.js";

describe("getProfile", () => {
  it("returns profile for 'python-fastapi'", () => {
    const profile = getProfile("python-fastapi");
    expect(profile.name).toBe("python-fastapi-postgres");
    expect(profile.language).toBe("python");
    expect(profile.framework).toBe("fastapi");
  });

  it("returns same profile for 'python-fastapi-postgres' alias", () => {
    const a = getProfile("python-fastapi");
    const b = getProfile("python-fastapi-postgres");
    expect(a).toBe(b);
  });

  it("throws for unknown profile", () => {
    expect(() => getProfile("unknown")).toThrow(/Unknown deployment profile/);
  });

  it("profile has non-empty typeMap", () => {
    const profile = getProfile("python-fastapi");
    expect(profile.typeMap.size).toBeGreaterThan(0);
    expect(profile.typeMap.has("String")).toBe(true);
    expect(profile.typeMap.has("Int")).toBe(true);
  });

  it("profile has dependencies", () => {
    const profile = getProfile("python-fastapi");
    expect(profile.dependencies.length).toBeGreaterThan(0);
    expect(profile.dependencies.some((d) => d.name === "fastapi")).toBe(true);
  });

  it("profile has devDependencies", () => {
    const profile = getProfile("python-fastapi");
    expect(profile.devDependencies.length).toBeGreaterThan(0);
    expect(profile.devDependencies.some((d) => d.name === "pytest")).toBe(true);
  });

  it("profile specifies uv as package manager", () => {
    expect(getProfile("python-fastapi").packageManager).toBe("uv");
  });

  it("profile is async", () => {
    expect(getProfile("python-fastapi").async).toBe(true);
  });

  it("profile has directories", () => {
    const dirs = getProfile("python-fastapi").directories;
    expect(dirs).toContain("app");
    expect(dirs).toContain("app/models");
    expect(dirs).toContain("tests");
  });
});

describe("listProfiles", () => {
  it("includes python-fastapi", () => {
    expect(listProfiles()).toContain("python-fastapi");
  });

  it("returns non-empty array", () => {
    expect(listProfiles().length).toBeGreaterThan(0);
  });
});
