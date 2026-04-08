import { describe, it, expect } from "vitest";
import { getProfile, listProfiles } from "#profile/registry.js";

describe("getProfile", () => {
  it("returns profile for 'python-fastapi-postgres'", () => {
    const profile = getProfile("python-fastapi-postgres");
    expect(profile.name).toBe("python-fastapi-postgres");
    expect(profile.language).toBe("python");
    expect(profile.framework).toBe("fastapi");
  });

  it("throws for unknown profile", () => {
    expect(() => getProfile("unknown")).toThrow(/Unknown deployment profile/);
  });

  it("profile has non-empty typeMap", () => {
    const profile = getProfile("python-fastapi-postgres");
    expect(profile.typeMap.size).toBeGreaterThan(0);
    expect(profile.typeMap.has("String")).toBe(true);
    expect(profile.typeMap.has("Int")).toBe(true);
  });

  it("profile has dependencies", () => {
    const profile = getProfile("python-fastapi-postgres");
    expect(profile.dependencies.length).toBeGreaterThan(0);
    expect(profile.dependencies.some((d) => d.name === "fastapi")).toBe(true);
  });

  it("profile has devDependencies", () => {
    const profile = getProfile("python-fastapi-postgres");
    expect(profile.devDependencies.length).toBeGreaterThan(0);
    expect(profile.devDependencies.some((d) => d.name === "pytest")).toBe(true);
  });

  it("profile specifies uv as package manager", () => {
    expect(getProfile("python-fastapi-postgres").packageManager).toBe("uv");
  });

  it("profile is async", () => {
    expect(getProfile("python-fastapi-postgres").async).toBe(true);
  });

  it("profile has directories", () => {
    const dirs = getProfile("python-fastapi-postgres").directories;
    expect(dirs).toContain("app");
    expect(dirs).toContain("app/models");
    expect(dirs).toContain("tests");
  });

  it("profile has model/schema/router directory paths", () => {
    const profile = getProfile("python-fastapi-postgres");
    expect(profile.modelDir).toBe("app/models");
    expect(profile.schemaDir).toBe("app/schemas");
    expect(profile.routerDir).toBe("app/routers");
  });
});

describe("listProfiles", () => {
  it("includes python-fastapi-postgres", () => {
    expect(listProfiles()).toContain("python-fastapi-postgres");
  });

  it("returns non-empty array", () => {
    expect(listProfiles().length).toBeGreaterThan(0);
  });
});
