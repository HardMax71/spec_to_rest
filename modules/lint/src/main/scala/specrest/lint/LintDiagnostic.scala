package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

enum LintLevel derives CanEqual:
  case Error, Warning

final case class RelatedSpan(span: SpanT, note: String)

final case class LintDiagnostic(
    code: String,
    level: LintLevel,
    message: String,
    span: Option[SpanT],
    relatedSpans: List[RelatedSpan] = Nil
)

trait LintPass:
  def code: String
  def run(ir: specrest.ir.ServiceIRFull): List[LintDiagnostic]
