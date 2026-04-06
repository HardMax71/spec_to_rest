import { readFileSync } from "node:fs";
import { parseSpec } from "#parser/index.js";
import { buildIR, BuildError } from "#ir/index.js";
import { formatIR, type Format } from "#cli/format.js";
import type { Logger } from "#cli/log.js";

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
  } catch (err: unknown) {
    log.error(readErrorMessage(specFile, err));
    return 1;
  }

  const { tree, errors } = parseSpec(source);
  if (errors.length > 0) {
    for (const e of errors) {
      log.error(`${specFile}:${e.line}:${e.column}: ${e.message}`);
    }
    return 1;
  }

  try {
    const ir = buildIR(tree);
    const output = formatIR(ir, opts.format);
    console.log(output);
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
