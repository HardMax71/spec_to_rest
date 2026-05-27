package specrest.verify

import specrest.ir.PrettyPrint
import specrest.ir.generated.*

object Narration:

  private val MaxLines: Int     = 12
  private val MaxLength: Int    = 1200
  private val Truncated: String = "(narration truncated; see counterexample above for full state)"

  final case class Context(
      ir: ServiceIRFull,
      op: Option[OperationDeclFull],
      invariantDecl: Option[InvariantDeclFull],
      operationName: Option[String],
      invariantName: Option[String],
      counterexample: Option[DecodedCounterExample],
      coreSpans: List[RelatedSpan]
  )

  private def spanLine(s: span_t): Long = s match
    case SpanT(int_of_integer(a), _, _, _) => a.toLong

  def narrate(category: DiagnosticCategory, ctx: Context): Option[String] =
    val raw = category match
      case DiagnosticCategory.InvariantViolationByOperation => narrateInvariantViolation(ctx)
      case DiagnosticCategory.ContradictoryInvariants       => narrateContradictoryInvariants(ctx)
      case DiagnosticCategory.UnreachableOperation          => narrateUnreachable(ctx)
      case _                                                => None
    raw.map(cap)

  private def narrateInvariantViolation(ctx: Context): Option[String] =
    for
      op      <- ctx.op
      invDecl <- ctx.invariantDecl
      invName <- ctx.invariantName.orElse(Some("invariant"))
      ce      <- ctx.counterexample
      field   <- contributingField(invDecl.b, ctx.ir)
    yield
      val rhs    = ensuresRhsForField(op.e, field)
      val opName = ctx.operationName.getOrElse(op.a)
      val lines  = List.newBuilder[String]
      lines += "Why this violates the invariant:"
      lines += s"  1. Invariant '$invName' requires:"
      lines += s"       ${PrettyPrint.expr(invDecl.b)}"
      rhs match
        case Some(r) =>
          lines += s"  2. Operation '$opName' computes '$field' from:"
          lines += s"       ${PrettyPrint.expr(r)}"
        case None =>
          lines += s"  2. Operation '$opName' writes '$field'."
      val preLine  = describePreInputs(ce, op, field)
      val postLine = describePost(ce, op, field)
      preLine.foreach { line =>
        lines += s"  3. The solver picked $line${postLine.fold("")(p => s", producing post-state $p")}."
      }
      if preLine.isEmpty then
        postLine.foreach { p =>
          lines += s"  3. The solver produced post-state $p."
        }
      lines += s"  4. The post-state value violates the bound on '$field' from invariant '$invName'."
      lines.result().mkString("\n")

  private def narrateContradictoryInvariants(ctx: Context): Option[String] =
    val invs = ctx.ir.i.collect { case i: InvariantDeclFull => i }
    if invs.isEmpty then None
    else
      val lines = List.newBuilder[String]
      val invNames = invs.zipWithIndex.map { case (InvariantDeclFull(n, _, _), i) =>
        n.getOrElse(s"inv_$i")
      }
      lines += "Why these invariants conflict:"
      lines += "  1. The verifier could not satisfy all invariants jointly."
      if ctx.coreSpans.nonEmpty then
        lines += "  2. The unsat core points at:"
        ctx.coreSpans.take(4).foreach: rs =>
          lines += s"       line ${spanLine(rs.span)}: ${rs.note}"
        rangePairConflict(invs).foreach: msg =>
          lines += s"  3. $msg"
      else
        val list = invNames.take(4).mkString(", ")
        lines += s"  2. Invariants involved: $list."
        lines += "     (Run with --explain to see the contributing pair.)"
      Some(lines.result().mkString("\n"))

  private def narrateUnreachable(ctx: Context): Option[String] =
    ctx.op.map: op =>
      val opName = ctx.operationName.getOrElse(op.a)
      val lines  = List.newBuilder[String]
      lines += "Why this operation is unreachable:"
      val req = combineAnd(op.d)
      lines += s"  1. Operation '$opName' has 'requires':"
      lines += s"       ${PrettyPrint.expr(req)}"
      lines += "  2. No pre-state satisfies both 'requires' and the invariants."
      if ctx.coreSpans.nonEmpty then
        lines += "     The unsat core points at:"
        ctx.coreSpans.take(4).foreach: rs =>
          lines += s"       line ${spanLine(rs.span)}: ${rs.note}"
      else
        lines += "     (Run with --explain to see the contributing clauses.)"
      lines.result().mkString("\n")

  private def contributingField(e: expr_full, ir: ServiceIRFull): Option[String] =
    collectFieldAccessNames(e).headOption.orElse:
      val stateFieldNames = ir.f.toList.flatMap {
        case StateDeclFull(fs, _) => fs.collect { case StateFieldDeclFull(n, _, _) => n }
      }.toSet
      collectIdentifierNames(e).find(stateFieldNames.contains)

  private def describePreInputs(
      ce: DecodedCounterExample,
      op: OperationDeclFull,
      field: String
  ): Option[String] =
    val parts         = List.newBuilder[String]
    val inputDisplays = scala.collection.mutable.LinkedHashSet.empty[String]
    op.b.foreach { case ParamDeclFull(pn, _, _) =>
      ce.inputs.find(_.name == pn).foreach: inp =>
        parts += s"$pn = ${inp.value.display}"
        inputDisplays += inp.value.display
    }
    val preEntries = ce.stateRelations.filter(_.side == "pre")
    preEntries.foreach: rel =>
      preferredEntry(rel, inputDisplays.toSet).foreach: entry =>
        val target = entry.value.entityLabel.getOrElse(entry.value.display)
        ce.entities.find(_.label == target).foreach: ent =>
          ent.fields.find(_.name == field).foreach: fieldVal =>
            parts += s"pre(${rel.stateName})[${entry.key.display}].$field = ${fieldVal.value.display}"
    ce.stateConstants
      .filter(c => c.side == "pre" && c.stateName == field)
      .foreach: c =>
        parts += s"pre($field) = ${c.value.display}"
    val collected = parts.result()
    if collected.isEmpty then None else Some(collected.mkString(", "))

  private def describePost(
      ce: DecodedCounterExample,
      op: OperationDeclFull,
      field: String
  ): Option[String] =
    val inputDisplays =
      op.b.collect { case ParamDeclFull(n, _, _) => n }
        .flatMap(n => ce.inputs.find(_.name == n).map(_.value.display))
        .toSet
    val fromRelations = ce.stateRelations.filter(_.side == "post").iterator.flatMap: rel =>
      preferredEntry(rel, inputDisplays).iterator.flatMap: entry =>
        val target = entry.value.entityLabel.getOrElse(entry.value.display)
        ce.entities.iterator.filter(_.label == target).flatMap: ent =>
          ent.fields.iterator.filter(_.name == field).map: fieldVal =>
            s"${rel.stateName}'[${entry.key.display}].$field = ${fieldVal.value.display}"
    val fromConstants = ce.stateConstants.iterator
      .filter(c => c.side == "post" && c.stateName == field)
      .map(c => s"$field' = ${c.value.display}")
    fromRelations.nextOption().orElse(fromConstants.nextOption())

  private def preferredEntry(
      rel: DecodedRelation,
      inputDisplays: Set[String]
  ): Option[DecodedRelationEntry] =
    rel.entries
      .find(e => inputDisplays.contains(e.key.display))
      .orElse(rel.entries.sortBy(_.key.display).headOption)

  private def rangePairConflict(invs: List[InvariantDeclFull]): Option[String] =
    val ranges = invs.flatMap { case d @ InvariantDeclFull(_, e, _) => rangeOf(e).map(r => (d, r)) }
    ranges.combinations(2).collectFirst:
      case List((aDecl, (aIdent, (aOp, aBound))), (bDecl, (bIdent, (bOp, bBound))))
          if aIdent == bIdent && conflicts(aOp, aBound, bOp, bBound) =>
        val aName = aDecl.a.getOrElse("invariant")
        val bName = bDecl.a.getOrElse("invariant")
        s"For example, '$aName' and '$bName' bound '$aIdent' to disjoint ranges."

  private def cap(s: String): String =
    val lines = s.split("\n", -1).toList
    val capped =
      if lines.size <= MaxLines then s
      else lines.take(MaxLines).mkString("\n") + s"\n  $Truncated"
    if capped.length <= MaxLength then capped
    else capped.take(MaxLength - Truncated.length - 1) + "\n" + Truncated
