import chalk, { Chalk, type ChalkInstance } from "chalk";

export interface LogOptions {
  verbose: boolean;
  quiet: boolean;
  color: boolean;
}

export interface Logger {
  info(msg: string): void;
  success(msg: string): void;
  error(msg: string): void;
  verbose(msg: string): void;
  chalk: ChalkInstance;
}

export function createLogger(opts: LogOptions): Logger {
  const c = opts.color ? chalk : new Chalk({ level: 0 });

  return {
    chalk: c,
    info(msg: string) {
      if (!opts.quiet) console.log(msg);
    },
    success(msg: string) {
      if (!opts.quiet) console.log(c.green(msg));
    },
    error(msg: string) {
      console.error(c.red(msg));
    },
    verbose(msg: string) {
      if (opts.verbose && !opts.quiet) console.log(c.dim(msg));
    },
  };
}
