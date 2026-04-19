package specrest.verify

import specrest.ir.Span

enum DiagnosticCategory:
  case ContradictoryInvariants, UnsatisfiablePrecondition, UnreachableOperation,
    InvariantViolationByOperation, SolverTimeout, TranslatorLimitation, BackendError

enum DiagnosticLevel:
  case Error, Warning

final case class RelatedSpan(span: Span, note: String)

final case class VerificationDiagnostic(
    level: DiagnosticLevel,
    category: DiagnosticCategory,
    message: String,
    primarySpan: Option[Span],
    relatedSpans: List[RelatedSpan],
    counterexample: Option[DecodedCounterExample],
    suggestion: Option[String]
)

object Diagnostic:

  def suggestionFor(category: DiagnosticCategory): Option[String] = category match
    case DiagnosticCategory.ContradictoryInvariants =>
      Some(
        "Review the invariant set for a pair whose range constraints cannot overlap (e.g., 'x >= 10' alongside 'x <= 5')."
      )
    case DiagnosticCategory.UnsatisfiablePrecondition =>
      Some(
        "Check whether 'requires' mentions an input predicate that contradicts a base-state refinement or an invariant the translator inlines."
      )
    case DiagnosticCategory.UnreachableOperation =>
      Some(
        "The operation's 'requires' is satisfiable in isolation but contradicts an invariant. Relax one or the other, or constrain the input type to exclude the conflicting range."
      )
    case DiagnosticCategory.InvariantViolationByOperation =>
      Some(
        "Tighten the 'ensures' clause so the invariant's constrained fields appear on the right-hand side of a '=' or a range predicate."
      )
    case DiagnosticCategory.SolverTimeout =>
      Some(
        "Try increasing --timeout, simplifying the invariant, or splitting a heavy quantifier into smaller predicates."
      )
    case DiagnosticCategory.TranslatorLimitation =>
      Some(
        "This spec uses a construct the verifier cannot yet translate. File an issue or narrow the invariant so the unsupported construct does not appear on the verification path."
      )
    case DiagnosticCategory.BackendError =>
      Some(
        "The solver crashed on this check. Re-run with --verbose; if reproducible, file an issue including the --dump-smt output."
      )

  def formatDiagnostic(diag: VerificationDiagnostic, specFile: String): String =
    val lines = List.newBuilder[String]
    lines += formatPrimary(diag, specFile)
    for rel <- diag.relatedSpans do
      lines += s"  related: ${formatLocation(specFile, rel.span)} (${rel.note})"
    diag.counterexample.foreach: ce =>
      lines += ""
      lines += "  Counterexample:"
      lines += CounterExample.format(ce)
    diag.suggestion.foreach: s =>
      lines += ""
      lines += s"  hint: $s"
    lines.result().mkString("\n")

  private def formatPrimary(diag: VerificationDiagnostic, specFile: String): String =
    val levelWord = diag.level match
      case DiagnosticLevel.Error   => "error"
      case DiagnosticLevel.Warning => "warning"
    val loc = diag.primarySpan match
      case Some(s) => formatLocation(specFile, s)
      case None    => specFile
    s"$loc: $levelWord: ${diag.message}"

  private def formatLocation(specFile: String, span: Span): String =
    s"$specFile:${span.startLine}:${span.startCol}"
