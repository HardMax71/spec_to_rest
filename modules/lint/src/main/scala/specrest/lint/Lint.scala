package specrest.lint

import specrest.ir.ServiceIR

object Lint:
  private val passes: List[LintPass] = List(
    TypeMismatch,
    UndefinedRef,
    MissingEnsures,
    UnusedEntity,
    CircularPredicate
  )

  def run(ir: ServiceIR): List[LintDiagnostic] =
    passes.flatMap(_.run(ir))
