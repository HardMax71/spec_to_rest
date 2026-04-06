import { readFileSync } from "node:fs";
import { parseSpec } from "../parser/index.js";
import { buildIR } from "../ir/index.js";
import { formatIR, type Format } from "./format.js";
import type { Logger } from "./log.js";

export interface InspectOptions {
  format: Format;
}

export function runInspect(
  specFile: string,
  opts: InspectOptions,
  log: Logger,
): number {
  let source: string;
  try {
    source = readFileSync(specFile, "utf-8");
  } catch {
    log.error(`File not found: ${specFile}`);
    return 1;
  }

  const { tree, errors } = parseSpec(source);
  if (errors.length > 0) {
    for (const e of errors) {
      log.error(`${specFile}:${e.line}:${e.column}: ${e.message}`);
    }
    return 1;
  }

  const ir = buildIR(tree);
  const output = formatIR(ir, opts.format);
  console.log(output);
  return 0;
}
