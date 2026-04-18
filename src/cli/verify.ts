import { readFileSync, writeFileSync } from "node:fs";
import { basename } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR, BuildError } from "#ir/index.js";
import { translate } from "#verify/translator.js";
import { renderSmtLib } from "#verify/smtlib.js";
import { WasmBackend } from "#verify/backend.js";
import { runConsistencyChecks, type CheckResult } from "#verify/consistency.js";
import { TranslatorError } from "#verify/types.js";
import type { Logger } from "#cli/log.js";

export interface VerifyOptions {
  timeout: number;
  dumpSmt: boolean;
  dumpSmtOut?: string;
}

export async function runVerify(
  specFile: string,
  opts: VerifyOptions,
  log: Logger,
): Promise<number> {
  let source: string;
  try {
    source = readFileSync(specFile, "utf-8");
  } catch (err: unknown) {
    log.error(readErrorMessage(specFile, err));
    return 1;
  }

  const tParse0 = performance.now();
  const { tree, errors } = parseSpec(source);
  log.verbose(`Parsed in ${(performance.now() - tParse0).toFixed(0)}ms`);
  if (errors.length > 0) {
    for (const e of errors) {
      log.error(`${specFile}:${e.line}:${e.column}: ${e.message}`);
    }
    return 1;
  }

  try {
    const tBuild0 = performance.now();
    const ir = buildIR(tree);
    log.verbose(`Built IR in ${(performance.now() - tBuild0).toFixed(0)}ms`);

    if (opts.dumpSmt || opts.dumpSmtOut) {
      const tTrans0 = performance.now();
      const script = translate(ir);
      log.verbose(
        `Translated IR to Z3 script: ${script.sorts.length} sorts, ` +
          `${script.funcs.length} function decls, ${script.assertions.length} assertions ` +
          `(${(performance.now() - tTrans0).toFixed(0)}ms)`,
      );
      const smt = renderSmtLib(script, opts.timeout > 0 ? opts.timeout : undefined);
      if (opts.dumpSmtOut) {
        writeFileSync(opts.dumpSmtOut, smt);
        log.success(`Wrote SMT-LIB to ${opts.dumpSmtOut}`);
      } else {
        process.stdout.write(smt);
      }
      return 0;
    }

    log.verbose(`Timeout: ${opts.timeout}ms`);
    const backend = new WasmBackend();
    const tRun0 = performance.now();
    const report = await runConsistencyChecks(ir, backend, { timeoutMs: opts.timeout });
    const totalMs = performance.now() - tRun0;
    const label = basename(specFile);
    return reportConsistency(label, report.checks, report.ok, totalMs, log);
  } catch (err: unknown) {
    if (err instanceof BuildError || err instanceof TranslatorError) {
      log.error(`${specFile}: ${err.message}`);
      return 1;
    }
    log.error(`${specFile}: ${err instanceof Error ? err.message : String(err)}`);
    return 1;
  }
}

function reportConsistency(
  label: string,
  checks: readonly CheckResult[],
  ok: boolean,
  totalMs: number,
  log: Logger,
): number {
  const passes = checks.filter((c) => c.status === "sat").length;
  const skipped = checks.filter((c) => c.status === "skipped").length;
  const failures = checks.length - passes - skipped;
  const totalFmt = totalMs.toFixed(0);

  if (ok) {
    const skipNote = skipped > 0 ? ` (${skipped} skipped)` : "";
    log.success(`${label}: ${passes}/${checks.length} consistency checks passed${skipNote} (${totalFmt}ms)`);
    for (const c of checks) log.verbose(formatCheck(c));
    return 0;
  }

  log.error(`${label}: ${failures} failure(s) in ${checks.length} consistency checks (${totalFmt}ms)`);
  for (const c of checks) {
    if (c.status === "sat" || c.status === "skipped") log.verbose(formatCheck(c));
    else log.error(formatCheck(c));
  }
  return 1;
}

function formatCheck(c: CheckResult): string {
  const icon = c.status === "sat" ? "✔" : c.status === "skipped" ? "∙" : "✘";
  const id = c.id.padEnd(28, " ");
  const status = c.status.padEnd(8, " ");
  const duration = `${c.durationMs.toFixed(0)}ms`.padStart(8, " ");
  const detail = c.detail ? ` — ${c.detail}` : "";
  return `  ${icon} ${id} ${status} ${duration}${detail}`;
}

function readErrorMessage(specFile: string, err: unknown): string {
  const code = err instanceof Error ? (err as NodeJS.ErrnoException).code : undefined;
  if (code === "ENOENT") return `File not found: ${specFile}`;
  if (code === "EISDIR") return `Is a directory: ${specFile}`;
  if (err instanceof Error) return `Cannot read ${specFile}: ${err.message}`;
  return `Cannot read ${specFile}`;
}
