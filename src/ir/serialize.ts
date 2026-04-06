import type { ServiceIR } from "./types.js";

export function serializeIR(ir: ServiceIR): string {
  return JSON.stringify(ir, null, 2);
}

export function deserializeIR(json: string): ServiceIR {
  const parsed: unknown = JSON.parse(json);
  if (!isServiceIR(parsed)) {
    throw new Error("Invalid ServiceIR: expected object with kind 'Service'");
  }
  return parsed;
}

function isServiceIR(value: unknown): value is ServiceIR {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    v.kind === "Service" &&
    typeof v.name === "string" &&
    Array.isArray(v.imports) &&
    Array.isArray(v.entities) &&
    Array.isArray(v.enums) &&
    Array.isArray(v.typeAliases) &&
    (v.state === null || typeof v.state === "object") &&
    Array.isArray(v.operations) &&
    Array.isArray(v.transitions) &&
    Array.isArray(v.invariants) &&
    Array.isArray(v.facts) &&
    Array.isArray(v.functions) &&
    Array.isArray(v.predicates) &&
    (v.conventions === null || typeof v.conventions === "object")
  );
}
