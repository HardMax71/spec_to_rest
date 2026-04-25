package specrest.verify

import specrest.ir.BinOp
import specrest.ir.Expr
import specrest.ir.InvariantDecl
import specrest.ir.OperationDecl
import specrest.ir.PrettyPrint
import specrest.ir.ServiceIR

object Narration:

  private val MaxLines: Int     = 12
  private val MaxLength: Int    = 1200
  private val Truncated: String = "(narration truncated; see counterexample above for full state)"

  final case class Context(
      ir: ServiceIR,
      op: Option[OperationDecl],
      invariantDecl: Option[InvariantDecl],
      operationName: Option[String],
      invariantName: Option[String],
      counterexample: Option[DecodedCounterExample],
      coreSpans: List[RelatedSpan]
  )

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
      field   <- contributingField(invDecl.expr, ctx.ir)
    yield
      val rhs    = ensuresRhsForField(op, field)
      val opName = ctx.operationName.getOrElse(op.name)
      val lines  = List.newBuilder[String]
      lines += s"Why this violates the invariant:"
      lines += s"  1. Invariant '$invName' requires:"
      lines += s"       ${PrettyPrint.expr(invDecl.expr)}"
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
    val invs = ctx.ir.invariants
    if invs.isEmpty then None
    else
      val lines    = List.newBuilder[String]
      val invNames = invs.zipWithIndex.map((inv, i) => inv.name.getOrElse(s"inv_$i"))
      lines += "Why these invariants conflict:"
      lines += "  1. The verifier could not satisfy all invariants jointly."
      if ctx.coreSpans.nonEmpty then
        lines += "  2. The unsat core points at:"
        ctx.coreSpans.take(4).foreach: rs =>
          lines += s"       line ${rs.span.startLine}: ${rs.note}"
        rangePairConflict(invs).foreach: msg =>
          lines += s"  3. $msg"
      else
        val list = invNames.take(4).mkString(", ")
        lines += s"  2. Invariants involved: $list."
        lines += "     (Run with --explain to see the contributing pair.)"
      Some(lines.result().mkString("\n"))

  private def narrateUnreachable(ctx: Context): Option[String] =
    ctx.op.map: op =>
      val opName = ctx.operationName.getOrElse(op.name)
      val lines  = List.newBuilder[String]
      lines += s"Why this operation is unreachable:"
      val req = combineConjuncts(op.requires)
      lines += s"  1. Operation '$opName' has 'requires':"
      lines += s"       ${PrettyPrint.expr(req)}"
      lines += "  2. No pre-state satisfies both 'requires' and the invariants."
      if ctx.coreSpans.nonEmpty then
        lines += "     The unsat core points at:"
        ctx.coreSpans.take(4).foreach: rs =>
          lines += s"       line ${rs.span.startLine}: ${rs.note}"
      else
        lines += "     (Run with --explain to see the contributing clauses.)"
      lines.result().mkString("\n")

  private def combineConjuncts(es: List[Expr]): Expr = es match
    case Nil      => Expr.BoolLit(true)
    case h :: Nil => h
    case h :: t   => t.foldLeft(h)((acc, e) => Expr.BinaryOp(BinOp.And, acc, e))

  private def contributingField(e: Expr, ir: ServiceIR): Option[String] =
    val fields      = scala.collection.mutable.LinkedHashSet.empty[String]
    val identifiers = scala.collection.mutable.LinkedHashSet.empty[String]
    def walk(x: Expr): Unit = x match
      case Expr.FieldAccess(base, field, _) => fields += field; walk(base)
      case Expr.Identifier(n, _)            => identifiers += n
      case Expr.BinaryOp(_, l, r, _)        => walk(l); walk(r)
      case Expr.UnaryOp(_, op, _)           => walk(op)
      case Expr.Quantifier(_, bs, body, _)  => bs.foreach(b => walk(b.domain)); walk(body)
      case Expr.SomeWrap(x, _)              => walk(x)
      case Expr.The(_, d, b, _)             => walk(d); walk(b)
      case Expr.EnumAccess(b, _, _)         => walk(b)
      case Expr.Index(b, i, _)              => walk(b); walk(i)
      case Expr.Call(c, args, _)            => walk(c); args.foreach(walk)
      case Expr.Prime(x, _)                 => walk(x)
      case Expr.Pre(x, _)                   => walk(x)
      case Expr.With(b, ups, _)             => walk(b); ups.foreach(u => walk(u.value))
      case Expr.If(c, t, e, _)              => walk(c); walk(t); walk(e)
      case Expr.Let(_, v, b, _)             => walk(v); walk(b)
      case Expr.Lambda(_, b, _)             => walk(b)
      case Expr.Constructor(_, fs, _)       => fs.foreach(f => walk(f.value))
      case Expr.SetLiteral(es, _)           => es.foreach(walk)
      case Expr.MapLiteral(es, _) =>
        es.foreach { e =>
          walk(e.key); walk(e.value)
        }
      case Expr.SetComprehension(_, d, p, _) => walk(d); walk(p)
      case Expr.SeqLiteral(es, _)            => es.foreach(walk)
      case Expr.Matches(x, _, _)             => walk(x)
      case _                                 => ()
    walk(e)
    fields.headOption.orElse:
      val stateFieldNames = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
      identifiers.iterator.find(stateFieldNames.contains)

  private def ensuresRhsForField(op: OperationDecl, field: String): Option[Expr] =
    val candidates = op.ensures.flatMap(extractRhs(_, field))
    candidates match
      case Nil      => None
      case r :: Nil => Some(r)
      case _        => None

  private def extractRhs(e: Expr, field: String): List[Expr] = e match
    case Expr.BinaryOp(BinOp.Eq, lhs, rhs, _) if assignsField(lhs, field) => List(rhs)
    case Expr.BinaryOp(BinOp.And, l, r, _)                                => extractRhs(l, field) ++ extractRhs(r, field)
    case _                                                                => Nil

  private def assignsField(lhs: Expr, field: String): Boolean = lhs match
    case Expr.FieldAccess(_, f, _) => f == field
    case Expr.Identifier(n, _)     => n == field
    case Expr.Prime(inner, _)      => assignsField(inner, field)
    case Expr.Index(base, _, _)    => assignsField(base, field)
    case _                         => false

  private def describePreInputs(
      ce: DecodedCounterExample,
      op: OperationDecl,
      field: String
  ): Option[String] =
    val parts         = List.newBuilder[String]
    val inputDisplays = scala.collection.mutable.LinkedHashSet.empty[String]
    op.inputs.foreach: p =>
      ce.inputs.find(_.name == p.name).foreach: inp =>
        parts += s"${p.name} = ${inp.value.display}"
        inputDisplays += inp.value.display
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
      op: OperationDecl,
      field: String
  ): Option[String] =
    val inputDisplays =
      op.inputs.flatMap(p => ce.inputs.find(_.name == p.name).map(_.value.display)).toSet
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

  private def rangePairConflict(invs: List[InvariantDecl]): Option[String] =
    val ranges = invs.flatMap(d => rangeOf(d.expr).map(r => (d, r)))
    ranges.combinations(2).collectFirst:
      case List((aDecl, (aIdent, aOp, aBound)), (bDecl, (bIdent, bOp, bBound)))
          if aIdent == bIdent && conflicts(aOp, aBound, bOp, bBound) =>
        val aName = aDecl.name.getOrElse("invariant")
        val bName = bDecl.name.getOrElse("invariant")
        s"For example, '$aName' and '$bName' bound '$aIdent' to disjoint ranges."

  private def rangeOf(e: Expr): Option[(String, BinOp, Long)] = e match
    case Expr.BinaryOp(op @ (BinOp.Ge | BinOp.Gt | BinOp.Le | BinOp.Lt), l, r, _) =>
      (l, r) match
        case (Expr.Identifier(n, _), Expr.IntLit(v, _)) => Some((n, op, v))
        case (Expr.IntLit(v, _), Expr.Identifier(n, _)) => Some((n, mirror(op), v))
        case _                                          => None
    case _ => None

  private def mirror(op: BinOp): BinOp = op match
    case BinOp.Ge => BinOp.Le
    case BinOp.Le => BinOp.Ge
    case BinOp.Gt => BinOp.Lt
    case BinOp.Lt => BinOp.Gt
    case other    => other

  private def conflicts(aOp: BinOp, aB: Long, bOp: BinOp, bB: Long): Boolean =
    val aLow    = aOp == BinOp.Ge || aOp == BinOp.Gt
    val bLow    = bOp == BinOp.Ge || bOp == BinOp.Gt
    val aStrict = aOp == BinOp.Gt || aOp == BinOp.Lt
    val bStrict = bOp == BinOp.Gt || bOp == BinOp.Lt
    val strict  = aStrict || bStrict
    if aLow && !bLow then if strict then aB >= bB else aB > bB
    else if !aLow && bLow then if strict then bB >= aB else bB > aB
    else false

  private def cap(s: String): String =
    val lines = s.split("\n", -1).toList
    val capped =
      if lines.size <= MaxLines then s
      else lines.take(MaxLines).mkString("\n") + s"\n  $Truncated"
    if capped.length <= MaxLength then capped
    else capped.take(MaxLength - Truncated.length - 1) + "\n" + Truncated
