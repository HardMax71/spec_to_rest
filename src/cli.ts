#!/usr/bin/env node
import { createRequire } from "node:module";
import { Command, Option } from "commander";
import { createLogger } from "#cli/log.js";
import { runInspect } from "#cli/inspect.js";
import { runCheck } from "#cli/check.js";
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

program.parse();
