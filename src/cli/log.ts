import { createConsola, LogLevels, type ConsolaInstance } from "consola";

export type Logger = ConsolaInstance;

export interface LogOptions {
  verbose: boolean;
  quiet: boolean;
  color: boolean;
}

export function createLogger(opts: LogOptions): Logger {
  let level = LogLevels.info;
  if (opts.quiet) level = LogLevels.error;
  if (opts.verbose) level = LogLevels.verbose;

  return createConsola({
    level,
    fancy: opts.color,
  });
}
