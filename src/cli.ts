#!/usr/bin/env node
import { Command } from "commander";
import { createLogger } from "./cli/log.js";
import { runInspect } from "./cli/inspect.js";
import { runCheck } from "./cli/check.js";
import type { Format } from "./cli/format.js";

const program = new Command();

program
  .name("spec-to-rest")
  .description("Compile formal behavioral specs into verified REST services")
  .version("0.1.0")
  .option("-v, --verbose", "show detailed progress", false)
  .option("-q, --quiet", "suppress non-error output", false)
  .option("--color", "force colored output", true)
  .option("--no-color", "disable colored output");

program
  .command("inspect")
  .description("Print the IR for a spec file")
  .argument("<spec-file>", "path to .spec file")
  .option("-f, --format <fmt>", "output format: summary, json, ir", "summary")
  .action((specFile: string, opts: { format: string }) => {
    const globals = program.opts<{ verbose: boolean; quiet: boolean; color: boolean }>();
    const log = createLogger(globals);
    const format = opts.format as Format;
    process.exitCode = runInspect(specFile, { format }, log);
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
