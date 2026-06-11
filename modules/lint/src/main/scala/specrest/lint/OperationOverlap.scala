package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object OperationOverlap extends LintPass:
  val code = "L04"

  def run(ir: service_ir): List[LintDiagnostic] =
    val out    = List.newBuilder[LintDiagnostic]
    val ops0   = svcOperations(ir)
    val groups = ops0.groupBy(opSignature).filter(_._2.length >= 2)
    for (_, ops) <- groups do
      val sorted = ops.sortBy(operName)
      for i <- sorted.indices; j <- (i + 1) until sorted.length do
        val a    = sorted(i)
        val b    = sorted(j)
        val nrA  = normalizedRequires(operRequires(a))
        val nrB  = normalizedRequires(operRequires(b))
        val same = nrA.length == nrB.length && nrA.zip(nrB).forall((x, y) => x == y)
        if same then
          val rel =
            operSpan(a).toList.map(s => RelatedSpan(s, s"first definition of '${operName(a)}'"))
          val phrase =
            if nrA.isEmpty then "neither has preconditions" else "they share the same preconditions"
          out += LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operations '${operName(a)}' and '${operName(b)}' have the same input/output signature and $phrase — dispatch is ambiguous on shared inputs",
            operSpan(b),
            rel
          )
    out.result()

  private def opSignature(
      op: operation_decl
  ): (List[(String, String)], List[(String, String)]) =
    (
      operInputs(op).map(paramShape),
      operOutputs(op).map(paramShape)
    )

  private def paramShape(p: param_decl): (String, String) =
    (prmName(p), typeStripSpans(prmType(p)).toString)

  private def normalizedRequires(rs: List[expr]): List[String] =
    flattenAndAll(rs)
      .filterNot(isTrueLit)
      .map(e => stripSpans(e).toString)
      .sorted
