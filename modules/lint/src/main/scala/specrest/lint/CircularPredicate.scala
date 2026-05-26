package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object CircularPredicate extends LintPass:
  val code = "L06"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val predNames = ir.m.collect { case PredicateDeclFull(n, _, _, _) => n }
    val funcNames = ir.l.collect { case FunctionDeclFull(n, _, _, _, _) => n }
    val nodes     = (predNames ++ funcNames).distinct
    if nodes.isEmpty then Nil
    else
      val spans = (
        ir.m.collect { case PredicateDeclFull(n, _, _, sp) => n -> sp } ++
          ir.l.collect { case FunctionDeclFull(n, _, _, _, sp) => n -> sp }
      ).toMap

      val edges =
        ir.m.collect { case PredicateDeclFull(n, _, body, _) =>
          n -> collectCallNames(body, nodes).distinct
        } ++
          ir.l.collect { case FunctionDeclFull(n, _, _, body, _) =>
            n -> collectCallNames(body, nodes).distinct
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
