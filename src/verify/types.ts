import type { Model, FuncDecl, Sort } from "z3-solver";

export type CheckStatus = "sat" | "unsat" | "unknown";

export interface VerificationConfig {
  readonly timeoutMs: number;
  readonly captureModel?: boolean;
}

export const DEFAULT_VERIFICATION_CONFIG: VerificationConfig = {
  timeoutMs: 30_000,
};

export interface SmokeCheckResult {
  readonly status: CheckStatus;
  readonly durationMs: number;
  readonly model: Model<"verify"> | null;
  readonly sortMap: ReadonlyMap<string, Sort<"verify">>;
  readonly funcMap: ReadonlyMap<string, FuncDecl<"verify">>;
}

export class TranslatorError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "TranslatorError";
  }
}
