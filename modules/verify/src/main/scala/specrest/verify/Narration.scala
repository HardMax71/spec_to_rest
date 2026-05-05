package specrest.verify

import specrest.ir.generated.SpecRestGenerated.*

import specrest.ir.PrettyPrint

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
      lines += "Why this violates the invariant:"
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
          lines += s"       line ${rs.span.a}: ${rs.note}"
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
      lines += "Why this operation is unreachable:"
      val req = combineConjuncts(op.d)
      lines += s"  1. Operation '$opName' has 'requires':"
      lines += s"       ${PrettyPrint.expr(req)}"
      lines += "  2. No pre-state satisfies both 'requires' and the invariants."
      if ctx.coreSpans.nonEmpty then
        lines += "     The unsat core points at:"
        ctx.coreSpans.take(4).foreach: rs =>
          lines += s"       line ${rs.span.a}: ${rs.note}"
      else
        lines += "     (Run with --explain to see the contributing clauses.)"
      lines.result().mkString("\n")

  private def combineConjuncts(es: List[expr_full]): expr_full = es match
    case Nil      => BoolLitF(true)
    case h :: Nil => h
    case h :: t   => t.foldLeft(h)((acc, e) => BinaryOpF(BAnd(), acc, e))

  private def contributingField(e: expr_full, ir: ServiceIRFull): Option[String] =
    val fields      = scala.collection.mutable.LinkedHashSet.empty[String]
    val identifiers = scala.collection.mutable.LinkedHashSet.empty[String]
    def walk(x: expr_full): Unit = x match
      case FieldAccessF(base, field, _) => fields += field; walk(base)
      case IdentifierF(n, _)            => identifiers += n
      case BinaryOpF(_, l, r, _)        => walk(l); walk(r)
      case UnaryOpF(_, op, _)           => walk(op)
      case QuantifierF(_, bs, body, _)  => bs.foreach(b => walk(b.domain)); walk(body)
      case SomeWrapF(x, _)              => walk(x)
      case TheF(_, d, b, _)             => walk(d); walk(b)
      case EnumAccessF(b, _, _)         => walk(b)
      case IndexF(b, i, _)              => walk(b); walk(i)
      case CallF(c, args, _)            => walk(c); args.foreach(walk)
      case PrimeF(x, _)                 => walk(x)
      case PreF(x, _)                   => walk(x)
      case WithF(b, ups, _)             => walk(b); ups.foreach(u => walk(u.value))
      case IfF(c, t, e, _)              => walk(c); walk(t); walk(e)
      case LetF(_, v, b, _)             => walk(v); walk(b)
      case LambdaF(_, b, _)             => walk(b)
      case ConstructorF(_, fs, _)       => fs.foreach(f => walk(f.value))
      case SetLiteralF(es, _)           => es.foreach(walk)
      case MapLiteralF(es, _) =>
        es.foreach { e =>
          walk(e.key); walk(e.value)
        }
      case SetComprehensionF(_, d, p, _) => walk(d); walk(p)
      case SeqLiteralF(es, _)            => es.foreach(walk)
      case MatchesF(x, _, _)             => walk(x)
      case _                             => ()
    walk(e)
    fields.headOption.orElse:
      val stateFieldNames = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
      identifiers.iterator.find(stateFieldNames.contains)

  private def ensuresRhsForField(op: OperationDeclFull, field: String): Option[expr_full] =
    val candidates = op.e.flatMap(extractRhs(_, field))
    candidates match
      case Nil      => None
      case r :: Nil => Some(r)
      case _        => None

  private def extractRhs(e: expr_full, field: String): List[expr_full] = e match
    case BinaryOpF(BEq(), lhs, rhs, _) if assignsField(lhs, field) => List(rhs)
    case BinaryOpF(BAnd(), l, r, _)                                => extractRhs(l, field) ++ extractRhs(r, field)
    case _                                                         => Nil

  private def assignsField(lhs: expr_full, field: String): Boolean = lhs match
    case FieldAccessF(_, f, _) => f == field
    case IdentifierF(n, _)     => n == field
    case PrimeF(inner, _)      => assignsField(inner, field)
    case IndexF(base, _, _)    => assignsField(base, field)
    case _                     => false

  private def describePreInputs(
      ce: DecodedCounterExample,
      op: OperationDeclFull,
      field: String
  ): Option[String] =
    val parts         = List.newBuilder[String]
    val inputDisplays = scala.collection.mutable.LinkedHashSet.empty[String]
    op.b.foreach: p =>
      ce.b.find(_.name == p.name).foreach: inp =>
        parts += s"${p.name} = ${inp.value.display}"
        inputDisplays += inp.value.display
    val preEntries = ce.stateRelations.filter(_.side == "pre")
    preEntries.foreach: rel =>
      preferredEntry(rel, inputDisplays.toSet).foreach: entry =>
        val a = entry.value.entityLabel.getOrElse(entry.value.display)
        ce.c.find(_.label == target).foreach: ent =>
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
      op.b.flatMap(p => ce.b.find(_.name == p.name).map(_.value.display)).toSet
    val fromRelations = ce.stateRelations.filter(_.side == "post").iterator.flatMap: rel =>
      preferredEntry(rel, inputDisplays).iterator.flatMap: entry =>
        val a = entry.value.entityLabel.getOrElse(entry.value.display)
        ce.c.iterator.filter(_.label == target).flatMap: ent =>
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
    rel.a
      .find(e => inputDisplays.contains(e.key.display))
      .orElse(rel.a.sortBy(_.key.display).headOption)

  private def rangePairConflict(invs: List[InvariantDeclFull]): Option[String] =
    val ranges = invs.flatMap(d => rangeOf(d.expr).map(r => (d, r)))
    ranges.combinations(2).collectFirst:
      case List((aDecl, (aIdent, aOp, aBound)), (bDecl, (bIdent, bOp, bBound)))
          if aIdent == bIdent && conflicts(aOp, aBound, bOp, bBound) =>
        val aName = aDecl.name.getOrElse("invariant")
        val bName = bDecl.name.getOrElse("invariant")
        s"For example, '$aName' and '$bName' bound '$aIdent' to disjoint ranges."

  private def rangeOf(e: expr_full): Option[(String, bin_op_full, Long)] = e match
    case BinaryOpF(op @ (BGe() | BGt() | BLe() | BLt()), l, r, _) =>
      (l, r) match
        case (IdentifierF(n, _), IntLitF(v, _)) => Some((n, op, v))
        case (IntLitF(v, _), IdentifierF(n, _)) => Some((n, mirror(op), v))
        case _                                  => None
    case _ => None

  private def mirror(op: bin_op_full): bin_op_full = op match
    case BGe() => BLe()
    case BLe() => BGe()
    case BGt() => BLt()
    case BLt() => BGt()
    case other => other

  private def conflicts(aOp: bin_op_full, aB: Long, bOp: bin_op_full, bB: Long): Boolean =
    val aLow    = aOp == BGe() || aOp == BGt()
    val bLow    = bOp == BGe() || bOp == BGt()
    val aStrict = aOp == BGt() || aOp == BLt()
    val bStrict = bOp == BGt() || bOp == BLt()
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
