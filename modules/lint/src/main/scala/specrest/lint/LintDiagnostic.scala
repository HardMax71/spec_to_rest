package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

enum LintLevel derives CanEqual:
  case Error, Warning

final case class RelatedSpan(span: span_t, note: String)

final case class LintDiagnostic(
    code: String,
    level: LintLevel,
    message: String,
    span: Option[span_t],
    relatedSpans: List[RelatedSpan] = Nil
)

trait LintPass:
  def code: String
  def run(ir: ServiceIRFull): List[LintDiagnostic]
