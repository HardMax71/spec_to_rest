#!/usr/bin/env node
import { createRequire } from "node:module";
import { Command, InvalidArgumentError, Option } from "commander";
import { createLogger } from "#cli/log.js";
import { runInspect } from "#cli/inspect.js";
import { runCheck } from "#cli/check.js";
import { runVerify } from "#cli/verify.js";
import type { Format } from "#cli/format.js";

const require = createRequire(import.meta.url);
const { version } = require("../package.json") as { version: string };

const program = new Command();

program
  .name("spec-to-rest")
  .description("Compile formal behavioral specs into verified REST services")
  .version(version)
  .option("-v, --verbose", "show detailed progress", false)
  .option("-q, --quiet", "suppress non-error output", false)
  .option("--color", "enable colored output", true)
  .option("--no-color", "disable colored output");

program
  .command("inspect")
  .description("Print the IR for a spec file")
  .argument("<spec-file>", "path to .spec file")
  .addOption(
    new Option("-f, --format <fmt>", "output format")
      .choices(["summary", "json", "ir", "endpoints", "profile"])
      .default("summary"),
  )
  .addOption(
    new Option("-t, --target <profile>", "deployment target profile")
      .choices(["python-fastapi-postgres"]),
  )
  .action((specFile: string, opts: { format: Format; target?: string }) => {
    const globals = program.opts<{ verbose: boolean; quiet: boolean; color: boolean }>();
    const log = createLogger(globals);
    const format = opts.target && opts.format === "summary" ? "profile" as Format : opts.format;
    process.exitCode = runInspect(specFile, { format, target: opts.target }, log);
  });

program
  .command("check")
  .description("Parse and validate a spec file")
  .argument("<spec-file>", "path to .spec file")
  .action((specFile: string) => {
    const globals = program.opts<{ verbose: boolean; quiet: boolean; color: boolean }>();
    const log = createLogger(globals);
    process.exitCode = runCheck(specFile, log);
  });

program
  .command("verify")
  .description("Run the Z3-backed verification engine on a spec file (M4.1: invariant satisfiability smoke check)")
  .argument("<spec-file>", "path to .spec file")
  .option(
    "--timeout <ms>",
    "per-check timeout in milliseconds (0 = no timeout)",
    parseTimeoutMs,
    30_000,
  )
  .option("--dump-smt", "emit SMT-LIB to stdout and exit (no solver run)", false)
  .option(
    "--dump-smt-out <file>",
    "write SMT-LIB to the given file and exit (no solver run)",
  )
  .action(
    async (
      specFile: string,
      opts: { timeout: number; dumpSmt: boolean; dumpSmtOut?: string },
    ) => {
      const globals = program.opts<{ verbose: boolean; quiet: boolean; color: boolean }>();
      const log = createLogger(globals);
      process.exitCode = await runVerify(specFile, opts, log);
    },
  );

function parseTimeoutMs(raw: string): number {
  if (!/^\d+$/.test(raw)) {
    throw new InvalidArgumentError(
      `--timeout must be a non-negative integer (got '${raw}')`,
    );
  }
  return parseInt(raw, 10);
}

program.parseAsync().catch((err: unknown) => {
  const message = err instanceof Error ? err.message : String(err);
  process.stderr.write(`${message}\n`);
  process.exit(1);
});
