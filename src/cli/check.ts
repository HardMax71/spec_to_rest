import { readFileSync } from "node:fs";
import { parseSpec } from "#parser/index.js";
import { buildIR, BuildError } from "#ir/index.js";
import { validateConventions } from "#convention/validate.js";
import type { Logger } from "#cli/log.js";

export function runCheck(specFile: string, log: Logger): number {
  let source: string;
  try {
    source = readFileSync(specFile, "utf-8");
  } catch (err: unknown) {
    log.error(readErrorMessage(specFile, err));
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

    const diagnostics = validateConventions(ir.conventions, ir);
    const errors = diagnostics.filter((d) => d.level === "error");
    const warnings = diagnostics.filter((d) => d.level === "warning");

    for (const w of warnings) {
      const loc = w.span ? `${specFile}:${w.span.startLine}:${w.span.startCol}: ` : "";
      log.warn(`${loc}warning: ${w.message}`);
    }
    for (const e of errors) {
      const loc = e.span ? `${specFile}:${e.span.startLine}:${e.span.startCol}: ` : "";
      log.error(`${loc}${e.message}`);
    }
    if (errors.length > 0) return 1;

    log.success(
      `${specFile}: valid (${ir.operations.length} operations, ` +
        `${ir.entities.length} entities, ${ir.invariants.length} invariants)`,
    );
    return 0;
  } catch (err) {
    if (err instanceof BuildError) {
      log.error(`${specFile}: ${err.message}`);
    } else {
      log.error(`${specFile}: ${err instanceof Error ? err.message : String(err)}`);
    }
    return 1;
  }
}

function readErrorMessage(specFile: string, err: unknown): string {
  const code = err instanceof Error ? (err as NodeJS.ErrnoException).code : undefined;
  if (code === "ENOENT") return `File not found: ${specFile}`;
  if (code === "EISDIR") return `Is a directory: ${specFile}`;
  if (err instanceof Error) return `Cannot read ${specFile}: ${err.message}`;
  return `Cannot read ${specFile}`;
}
