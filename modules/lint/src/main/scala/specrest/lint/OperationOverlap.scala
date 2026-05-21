package specrest.lint

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

object OperationOverlap extends LintPass:
  val code = "L04"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val out    = List.newBuilder[LintDiagnostic]
    val ops0   = ir.g.collect { case o: OperationDeclFull => o }
    val groups = ops0.groupBy(opSignature).filter(_._2.length >= 2)
    for (_, ops) <- groups do
      val sorted = ops.sortBy(_.a)
      for i <- sorted.indices; j <- (i + 1) until sorted.length do
        val a    = sorted(i)
        val b    = sorted(j)
        val nrA  = normalizedRequires(a.d)
        val nrB  = normalizedRequires(b.d)
        val same = nrA.length == nrB.length && nrA.zip(nrB).forall((x, y) => x == y)
        if same then
          val rel = a.f.toList.map(s => RelatedSpan(s, s"first definition of '${a.a}'"))
          val phrase =
            if nrA.isEmpty then "neither has preconditions" else "they share the same preconditions"
          out += LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operations '${a.a}' and '${b.a}' have the same input/output signature and $phrase — dispatch is ambiguous on shared inputs",
            b.f,
            rel
          )
    out.result()

  private def opSignature(
      op: OperationDeclFull
  ): (List[(String, String)], List[(String, String)]) =
    (
      op.b.collect { case p: ParamDeclFull => paramShape(p) },
      op.c.collect { case p: ParamDeclFull => paramShape(p) }
    )

  private def paramShape(p: ParamDeclFull): (String, String) =
    (p.a, SpecRestGenerated.typeStripSpans(p.b).toString)

  private def normalizedRequires(rs: List[expr_full]): List[String] =
    flattenAndAll(rs)
      .filterNot(isLiteralTrue)
      .map(e => SpecRestGenerated.stripSpans(e).toString)
      .sorted

  private def isLiteralTrue(e: expr_full): Boolean = e match
    case BoolLitF(true, _) => true
    case _                 => false
