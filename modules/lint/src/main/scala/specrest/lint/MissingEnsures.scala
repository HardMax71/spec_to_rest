package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object MissingEnsures extends LintPass:
  val code = "L03"

  def run(ir: service_ir_full): List[LintDiagnostic] =
    ir.g.flatMap: op =>
      if op.c.nonEmpty && op.e.isEmpty then
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operation '${op.name}' declares outputs but has no 'ensures' block — outputs will be unconstrained",
            op.span
          )
        )
      else Nil
