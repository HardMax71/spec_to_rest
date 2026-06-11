package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object MissingEnsures extends LintPass:
  val code = "L03"

  def run(ir: service_ir): List[LintDiagnostic] =
    svcOperations(ir).flatMap { op =>
      if operationMissingEnsures(op) then
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operation '${operName(op)}' declares outputs but has no 'ensures' block — outputs will be unconstrained",
            operSpan(op)
          )
        )
      else Nil
    }
