import { describe, it, expect } from "vitest";
import { parseSpec } from "#parser/index.js";

describe("Error reporting", () => {
  it("reports error for missing closing brace", () => {
    const { errors } = parseSpec(`service T {`);
    expect(errors.length).toBeGreaterThan(0);
  });

  it("reports error for missing colon in field", () => {
    const { errors } = parseSpec(`
      service T {
        entity E { name String }
      }
    `);
    expect(errors.length).toBeGreaterThan(0);
  });

  it("reports error for invalid token", () => {
    const { errors } = parseSpec(`
      service T {
        entity E { name: String; }
      }
    `);
    expect(errors.length).toBeGreaterThan(0);
  });

  it("reports error line and column", () => {
    const { errors } = parseSpec(`service T {
  entity E {
    name String
  }
}`);
    expect(errors.length).toBeGreaterThan(0);
    expect(errors[0].line).toBeGreaterThan(0);
    expect(typeof errors[0].column).toBe("number");
    expect(errors[0].message.length).toBeGreaterThan(0);
  });

  it("allows empty service", () => {
    const { errors } = parseSpec(`service Empty {}`);
    expect(errors).toEqual([]);
  });

  it("reports error for completely invalid input", () => {
    const { errors } = parseSpec(`not valid spec at all`);
    expect(errors.length).toBeGreaterThan(0);
  });
});
