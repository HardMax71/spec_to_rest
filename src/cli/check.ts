import { readFileSync } from "node:fs";
import { parseSpec } from "../parser/index.js";
import { buildIR, BuildError } from "../ir/index.js";
import type { Logger } from "./log.js";

export function runCheck(specFile: string, log: Logger): number {
  let source: string;
  try {
    source = readFileSync(specFile, "utf-8");
  } catch {
    log.error(`File not found: ${specFile}`);
    return 1;
  }

  const t0 = performance.now();
  const { tree, errors } = parseSpec(source);
  const parseMs = performance.now() - t0;
  log.verbose(`Parsed in ${parseMs.toFixed(0)}ms`);

  if (errors.length > 0) {
    for (const e of errors) {
      log.error(`${specFile}:${e.line}:${e.column}: ${e.message}`);
    }
    return 1;
  }

  try {
    const t1 = performance.now();
    const ir = buildIR(tree);
    const buildMs = performance.now() - t1;
    log.verbose(`Built IR in ${buildMs.toFixed(0)}ms`);

    log.success(
      `${specFile}: valid (${ir.operations.length} operations, ` +
        `${ir.entities.length} entities, ${ir.invariants.length} invariants)`,
    );
    return 0;
  } catch (err) {
    if (err instanceof BuildError) {
      log.error(`${specFile}:${err.line}:${err.column}: ${err.message}`);
    } else {
      log.error(`${specFile}: ${err instanceof Error ? err.message : String(err)}`);
    }
    return 1;
  }
}
