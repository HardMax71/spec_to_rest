package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.Return"))
object CircularPredicate extends LintPass:
  val code = "L06"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val predNames = ir.m.map { case PredicateDeclFull(n, _, _, _) => n }.toSet
    val funcNames = ir.l.map { case FunctionDeclFull(n, _, _, _, _) => n }.toSet
    val nodes     = predNames ++ funcNames
    if nodes.isEmpty then return Nil

    val spans = mutable.Map.empty[String, Option[span_t]]
    val edges = mutable.Map.empty[String, Set[String]]
    for case PredicateDeclFull(name, _, body, span) <- ir.m do
      spans(name) = span
      edges(name) = callees(body, nodes)
    for case FunctionDeclFull(name, _, _, body, span) <- ir.l do
      spans(name) = span
      edges(name) = callees(body, nodes)

    val cycles = findCycles(nodes.toList.sorted, edges.toMap)
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

  private def callees(body: expr_full, names: Set[String]): Set[String] =
    val acc = mutable.Set.empty[String]
    ExprWalk.foreach(body):
      case CallF(IdentifierF(n, _), _, _) if names.contains(n) => acc += n
      case _                                                   => ()
    acc.toSet

  private def findCycles(nodes: List[String], edges: Map[String, Set[String]]): List[List[String]] =
    val onStack    = mutable.Set.empty[String]
    val visited    = mutable.Set.empty[String]
    val stack      = mutable.ListBuffer.empty[String]
    val cycles     = mutable.ListBuffer.empty[List[String]]
    val seenCycles = mutable.Set.empty[Set[String]]

    def dfs(n: String): Unit =
      if onStack.contains(n) then
        val idx = stack.indexOf(n)
        if idx >= 0 then
          val cyc = stack.slice(idx, stack.length).toList
          val key = cyc.toSet
          if !seenCycles.contains(key) then
            seenCycles += key
            cycles += cyc
      else if !visited.contains(n) then
        visited += n
        onStack += n
        stack += n
        edges.getOrElse(n, Set.empty).toList.sorted.foreach(dfs)
        onStack -= n
        val _ = stack.remove(stack.length - 1)
      else ()

    nodes.foreach: n =>
      stack.clear()
      onStack.clear()
      dfs(n)

    cycles.toList
