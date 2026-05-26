package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object MissingEnsures extends LintPass:
  val code = "L03"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    ir.g.flatMap { case op: OperationDeclFull =>
      if operationMissingEnsures(op) then
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operation '${op.a}' declares outputs but has no 'ensures' block — outputs will be unconstrained",
            op.f
          )
        )
      else Nil
    }
