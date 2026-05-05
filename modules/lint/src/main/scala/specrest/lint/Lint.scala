package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object Lint:
  private val passes: List[LintPass] = List(
    TypeMismatch,
    UndefinedRef,
    MissingEnsures,
    OperationOverlap,
    UnusedEntity,
    CircularPredicate
  )

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    passes.flatMap(_.run(ir))
