import { createConsola, LogLevels, type ConsolaInstance } from "consola";

export type Logger = ConsolaInstance;

export interface LogOptions {
  verbose: boolean;
  quiet: boolean;
  color: boolean;
}

export function createLogger(opts: LogOptions): Logger {
  let level = LogLevels.info;
  if (opts.verbose && !opts.quiet) level = LogLevels.verbose;
  if (opts.quiet) level = LogLevels.error;

  return createConsola({
    level,
    fancy: opts.color,
  });
}
