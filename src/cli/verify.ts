import { readFileSync, writeFileSync } from "node:fs";
import { parseSpec } from "#parser/index.js";
import { buildIR, BuildError } from "#ir/index.js";
import { translate } from "#verify/translator.js";
import { renderSmtLib } from "#verify/smtlib.js";
import { WasmBackend } from "#verify/backend.js";
import { runConsistencyChecks, type CheckResult } from "#verify/consistency.js";
import { TranslatorError } from "#verify/types.js";
import { formatDiagnostic } from "#verify/diagnostic.js";
import type { Logger } from "#cli/log.js";

export interface VerifyOptions {
  timeout: number;
  dumpSmt: boolean;
  dumpSmtOut?: string;
}

export const EXIT_OK = 0;
export const EXIT_VIOLATIONS = 1;
export const EXIT_TRANSLATOR = 2;
export const EXIT_BACKEND = 3;

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
    return EXIT_VIOLATIONS;
  }

  const tParse0 = performance.now();
  const { tree, errors } = parseSpec(source);
  log.verbose(`Parsed in ${(performance.now() - tParse0).toFixed(0)}ms`);
  if (errors.length > 0) {
    for (const e of errors) {
      log.error(`${specFile}:${e.line}:${e.column}: ${e.message}`);
    }
    return EXIT_VIOLATIONS;
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
      return EXIT_OK;
    }

    log.verbose(`Timeout: ${opts.timeout}ms`);
    const backend = new WasmBackend();
    const tRun0 = performance.now();
    const report = await runConsistencyChecks(ir, backend, { timeoutMs: opts.timeout });
    const totalMs = performance.now() - tRun0;
    return reportConsistency(specFile, report.checks, report.ok, totalMs, log);
  } catch (err: unknown) {
    if (err instanceof BuildError) {
      log.error(`${specFile}: ${err.message}`);
      return EXIT_VIOLATIONS;
    }
    if (err instanceof TranslatorError) {
      log.error(`${specFile}: translator limitation: ${err.message}`);
      return EXIT_TRANSLATOR;
    }
    log.error(`${specFile}: ${err instanceof Error ? err.message : String(err)}`);
    return EXIT_BACKEND;
  }
}

function reportConsistency(
  specFile: string,
  checks: readonly CheckResult[],
  ok: boolean,
  totalMs: number,
  log: Logger,
): number {
  const passes = checks.filter((c) => c.status === "sat").length;
  const skipped = checks.filter((c) => c.status === "skipped").length;
  const failures = checks.length - passes - skipped;
  const totalFmt = totalMs.toFixed(0);
  const exitCode = exitCodeFor(checks, ok);

  if (exitCode === EXIT_OK) {
    log.success(
      `${specFile}: ${passes}/${checks.length} consistency checks passed (${totalFmt}ms)`,
    );
    for (const c of checks) log.verbose(formatCheckLine(c));
    return exitCode;
  }

  if (failures === 0 && skipped > 0) {
    log.warn(
      `${specFile}: ${passes}/${checks.length} checks passed; ${skipped} skipped (translator coverage gap) (${totalFmt}ms)`,
    );
  } else {
    log.error(
      `${specFile}: ${failures} failure(s), ${skipped} skipped in ${checks.length} consistency checks (${totalFmt}ms)`,
    );
  }

  for (const c of checks) {
    if (c.status === "sat") {
      log.verbose(formatCheckLine(c));
      continue;
    }
    if (c.diagnostic) {
      if (c.status === "skipped") {
        log.warn("");
        log.warn(formatDiagnostic(c.diagnostic, specFile));
      } else {
        log.error("");
        log.error(formatDiagnostic(c.diagnostic, specFile));
      }
    } else {
      log.error(formatCheckLine(c));
    }
  }
  return exitCode;
}

function exitCodeFor(checks: readonly CheckResult[], ok: boolean): number {
  const hasBackendError = checks.some((c) => c.diagnostic?.category === "backend_error");
  if (hasBackendError) return EXIT_BACKEND;
  const hasViolation = checks.some((c) => c.status === "unsat" || c.status === "unknown");
  if (hasViolation) return EXIT_VIOLATIONS;
  const hasTranslatorLimitation = checks.some(
    (c) => c.status === "skipped" && c.diagnostic?.category === "translator_limitation",
  );
  if (hasTranslatorLimitation) return EXIT_TRANSLATOR;
  return ok ? EXIT_OK : EXIT_VIOLATIONS;
}

function formatCheckLine(c: CheckResult): string {
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
