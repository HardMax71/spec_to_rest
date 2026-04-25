package specrest.lint

import specrest.ir.ServiceIR

object MissingEnsures extends LintPass:
  val code = "L03"

  def run(ir: ServiceIR): List[LintDiagnostic] =
    ir.operations.flatMap: op =>
      if op.outputs.nonEmpty && op.ensures.isEmpty then
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operation '${op.name}' declares outputs but has no 'ensures' block — outputs will be unconstrained",
            op.span
          )
        )
      else Nil
