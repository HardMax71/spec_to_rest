package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object Lint:
  private val passes: List[LintPass] = List(
    TypeMismatch,
    UndefinedRef,
    MissingEnsures,
    OperationOverlap,
    UnusedEntity,
    CircularPredicate,
    DroppedOutputs
  )

  def run(ir: service_ir): List[LintDiagnostic] =
    passes.flatMap(_.run(ir))
