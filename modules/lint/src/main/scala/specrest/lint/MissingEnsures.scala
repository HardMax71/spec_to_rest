package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object MissingEnsures extends LintPass:
  val code = "L03"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    ir.g.flatMap { case op @ OperationDeclFull(name, _, outputs, _, ensures, span) =>
      if outputs.nonEmpty && ensures.isEmpty then
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operation '$name' declares outputs but has no 'ensures' block — outputs will be unconstrained",
            span
          )
        )
      else Nil
    }
