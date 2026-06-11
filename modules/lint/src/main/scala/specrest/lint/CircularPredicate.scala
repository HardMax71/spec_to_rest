package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object CircularPredicate extends LintPass:
  val code = "L06"

  def run(ir: service_ir): List[LintDiagnostic] =
    val predNames = svcPredicates(ir).map(prdName)
    val funcNames = svcFunctions(ir).map(fncName)
    val nodes     = (predNames ++ funcNames).distinct
    if nodes.isEmpty then Nil
    else
      val spans = (
        svcPredicates(ir).map(p => prdName(p) -> prdSpan(p)) ++
          svcFunctions(ir).map(fn => fncName(fn) -> fncSpan(fn))
      ).toMap

      val edges =
        svcPredicates(ir).map { p =>
          prdName(p) -> collectCallNames(prdBody(p), nodes).distinct
        } ++
          svcFunctions(ir).map { fn =>
            fncName(fn) -> collectCallNames(fncBody(fn), nodes).distinct
          }

      // Lifted findCycles takes sorted node list and AList edges. Sort to
      // match the legacy deterministic iteration order.
      val cycles = findCycles(nodes.sorted, edges)

      cycles.map: cyc =>
        val head    = cyc.head
        val display = (cyc :+ head).mkString(" -> ")
        val related =
          cyc.tail.flatMap(n => spans.get(n).flatten.map(s => RelatedSpan(s, s"in cycle: $n")))
        LintDiagnostic(
          code,
          LintLevel.Error,
          s"circular predicate dependency: $display",
          spans.get(head).flatten,
          related
        )
