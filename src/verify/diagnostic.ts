import type { Span } from "#ir/types.js";
import type { CounterExample } from "#verify/counterexample.js";
import { formatCounterExample } from "#verify/counterexample.js";

export type DiagnosticCategory =
  | "contradictory_invariants"
  | "unsatisfiable_precondition"
  | "unreachable_operation"
  | "invariant_violation_by_operation"
  | "solver_timeout"
  | "translator_limitation"
  | "backend_error";

export interface RelatedSpan {
  readonly span: Span;
  readonly note: string;
}

export interface VerificationDiagnostic {
  readonly level: "error" | "warning";
  readonly category: DiagnosticCategory;
  readonly message: string;
  readonly primarySpan: Span | null;
  readonly relatedSpans: readonly RelatedSpan[];
  readonly counterexample: CounterExample | null;
  readonly suggestion: string | null;
}

export function suggestionFor(category: DiagnosticCategory): string | null {
  switch (category) {
    case "contradictory_invariants":
      return "Review the invariant set for a pair whose range constraints cannot overlap (e.g., 'x >= 10' alongside 'x <= 5').";
    case "unsatisfiable_precondition":
      return "Check whether 'requires' mentions an input predicate that contradicts a base-state refinement or an invariant the translator inlines.";
    case "unreachable_operation":
      return "The operation's 'requires' is satisfiable in isolation but contradicts an invariant. Relax one or the other, or constrain the input type to exclude the conflicting range.";
    case "invariant_violation_by_operation":
      return "Tighten the 'ensures' clause so the invariant's constrained fields appear on the right-hand side of a '=' or a range predicate.";
    case "solver_timeout":
      return "Try increasing --timeout, simplifying the invariant, or splitting a heavy quantifier into smaller predicates.";
    case "translator_limitation":
      return "This spec uses a construct the verifier cannot yet translate. File an issue or narrow the invariant so the unsupported construct does not appear on the verification path.";
    case "backend_error":
      return "The solver crashed on this check. Re-run with --verbose; if reproducible, file an issue including the --dump-smt output.";
    default:
      return null;
  }
}

export function formatDiagnostic(
  diag: VerificationDiagnostic,
  specFile: string,
): string {
  const lines: string[] = [];
  const header = formatPrimary(diag, specFile);
  lines.push(header);
  for (const rel of diag.relatedSpans) {
    lines.push(`  related: ${formatLocation(specFile, rel.span)} (${rel.note})`);
  }
  if (diag.counterexample) {
    lines.push("");
    lines.push("  Counterexample:");
    lines.push(formatCounterExample(diag.counterexample));
  }
  if (diag.suggestion) {
    lines.push("");
    lines.push(`  hint: ${diag.suggestion}`);
  }
  return lines.join("\n");
}

function formatPrimary(diag: VerificationDiagnostic, specFile: string): string {
  const levelWord = diag.level === "error" ? "error" : "warning";
  const loc = diag.primarySpan ? formatLocation(specFile, diag.primarySpan) : specFile;
  return `${loc}: ${levelWord}: ${diag.message}`;
}

function formatLocation(specFile: string, span: Span): string {
  return `${specFile}:${span.startLine}:${span.startCol}`;
}
