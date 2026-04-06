import type { ServiceIR } from "#ir/types.js";
import { validateServiceIR } from "#ir/validate.js";

export function serializeIR(ir: ServiceIR): string {
  return JSON.stringify(ir, null, 2);
}

export function deserializeIR(json: string): ServiceIR {
  const parsed: unknown = JSON.parse(json);
  validateServiceIR(parsed);
  return parsed;
}
