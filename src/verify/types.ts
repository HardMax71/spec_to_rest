export type CheckStatus = "sat" | "unsat" | "unknown";

export interface VerificationConfig {
  readonly timeoutMs: number;
}

export const DEFAULT_VERIFICATION_CONFIG: VerificationConfig = {
  timeoutMs: 30_000,
};

export interface SmokeCheckResult {
  readonly status: CheckStatus;
  readonly durationMs: number;
}

export class TranslatorError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "TranslatorError";
  }
}
