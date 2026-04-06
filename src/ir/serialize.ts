import type { ServiceIR } from "./types.js";

export function serializeIR(ir: ServiceIR): string {
  return JSON.stringify(ir, null, 2);
}

export function deserializeIR(json: string): ServiceIR {
  return JSON.parse(json) as ServiceIR;
}
