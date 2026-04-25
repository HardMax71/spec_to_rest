package specrest.lint

import specrest.ir.Expr
import specrest.ir.ServiceIR
import specrest.ir.Span

import scala.collection.mutable

object CircularPredicate extends LintPass:
  val code = "L06"

  def run(ir: ServiceIR): List[LintDiagnostic] =
    val predNames = ir.predicates.map(_.name).toSet
    val funcNames = ir.functions.map(_.name).toSet
    val nodes     = predNames ++ funcNames
    if nodes.isEmpty then return Nil

    val spans = mutable.Map.empty[String, Option[Span]]
    val edges = mutable.Map.empty[String, Set[String]]
    for p <- ir.predicates do
      spans(p.name) = p.span
      edges(p.name) = callees(p.body, nodes)
    for f <- ir.functions do
      spans(f.name) = f.span
      edges(f.name) = callees(f.body, nodes)

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

  private def callees(body: Expr, names: Set[String]): Set[String] =
    val acc = mutable.Set.empty[String]
    ExprWalk.foreach(body):
      case Expr.Call(Expr.Identifier(n, _), _, _) if names.contains(n) => acc += n
      case _                                                           => ()
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
