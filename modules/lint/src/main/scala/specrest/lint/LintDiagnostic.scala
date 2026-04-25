package specrest.lint

import specrest.ir.Span

enum LintLevel:
  case Error, Warning

final case class RelatedSpan(span: Span, note: String)

final case class LintDiagnostic(
    code: String,
    level: LintLevel,
    message: String,
    span: Option[Span],
    relatedSpans: List[RelatedSpan] = Nil
)

trait LintPass:
  def code: String
  def run(ir: specrest.ir.ServiceIR): List[LintDiagnostic]
