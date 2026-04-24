package specrest.verify

import specrest.ir.Expr
import specrest.ir.InvariantDecl
import specrest.ir.OperationDecl
import specrest.ir.ServiceIR
import specrest.ir.Span
import specrest.ir.UnOp

enum DiagnosticCategory:
  case ContradictoryInvariants, UnsatisfiablePrecondition, UnreachableOperation,
    InvariantViolationByOperation, SolverTimeout, TranslatorLimitation, BackendError

object DiagnosticCategory:
  def token(c: DiagnosticCategory): String = c match
    case ContradictoryInvariants       => "contradictory_invariants"
    case UnsatisfiablePrecondition     => "unsatisfiable_precondition"
    case UnreachableOperation          => "unreachable_operation"
    case InvariantViolationByOperation => "invariant_violation_by_operation"
    case SolverTimeout                 => "solver_timeout"
    case TranslatorLimitation          => "translator_limitation"
    case BackendError                  => "backend_error"

enum DiagnosticLevel:
  case Error, Warning

object DiagnosticLevel:
  def token(l: DiagnosticLevel): String = l match
    case Error   => "error"
    case Warning => "warning"

final case class RelatedSpan(span: Span, note: String)

final case class VerificationDiagnostic(
    level: DiagnosticLevel,
    category: DiagnosticCategory,
    message: String,
    primarySpan: Option[Span],
    relatedSpans: List[RelatedSpan],
    counterexample: Option[DecodedCounterExample],
    suggestion: Option[String],
    coreSpans: List[RelatedSpan] = Nil
)

object Diagnostic:

  private val MaxSuggestionLength = 200

  final case class SuggestionContext(
      ir: ServiceIR,
      op: Option[OperationDecl],
      invariantDecl: Option[InvariantDecl],
      operationName: Option[String],
      invariantName: Option[String],
      counterexample: Option[DecodedCounterExample],
      checkId: String,
      timeoutMs: Long
  )

  def suggestionFor(category: DiagnosticCategory, ctx: SuggestionContext): Option[String] =
    val raw = category match
      case DiagnosticCategory.ContradictoryInvariants   => contradictoryInvariantsSuggestion(ctx)
      case DiagnosticCategory.UnsatisfiablePrecondition => unsatisfiablePreconditionSuggestion(ctx)
      case DiagnosticCategory.UnreachableOperation      => unreachableOperationSuggestion(ctx)
      case DiagnosticCategory.InvariantViolationByOperation =>
        invariantViolationSuggestion(ctx)
      case DiagnosticCategory.SolverTimeout        => solverTimeoutSuggestion(ctx)
      case DiagnosticCategory.TranslatorLimitation => suggestionFor(category)
      case DiagnosticCategory.BackendError         => suggestionFor(category)
    raw.map(cap)

  def suggestionFor(category: DiagnosticCategory): Option[String] = category match
    case DiagnosticCategory.ContradictoryInvariants =>
      Some(
        "Review the invariant set for a pair whose range constraints cannot overlap (e.g., 'x >= 10' alongside 'x <= 5')."
      )
    case DiagnosticCategory.UnsatisfiablePrecondition =>
      Some(
        "Check whether 'requires' mentions an input predicate that contradicts a base-state refinement or an invariant the translator inlines."
      )
    case DiagnosticCategory.UnreachableOperation =>
      Some(
        "The operation's 'requires' is satisfiable in isolation but contradicts an invariant. Relax one or the other, or constrain the input type to exclude the conflicting range."
      )
    case DiagnosticCategory.InvariantViolationByOperation =>
      Some(
        "Tighten the 'ensures' clause so the invariant's constrained fields appear on the right-hand side of a '=' or a range predicate."
      )
    case DiagnosticCategory.SolverTimeout =>
      Some(
        "Try increasing --timeout, simplifying the invariant, or splitting a heavy quantifier into smaller predicates."
      )
    case DiagnosticCategory.TranslatorLimitation =>
      Some(
        "This spec uses a construct the verifier cannot yet translate. File an issue or narrow the invariant so the unsupported construct does not appear on the verification path."
      )
    case DiagnosticCategory.BackendError =>
      Some(
        "The solver crashed on this check. Re-run with --verbose; if reproducible, file an issue including the --dump-smt output."
      )

  private def contradictoryInvariantsSuggestion(ctx: SuggestionContext): Option[String] =
    val names = invariantDisplayNames(ctx.ir)
    if names.isEmpty then suggestionFor(DiagnosticCategory.ContradictoryInvariants)
    else
      val list = formatNameList(names, max = 3)
      Some(
        s"Invariants $list are jointly unsatisfiable. Look for a pair whose range constraints cannot overlap (e.g., 'x >= 10' alongside 'x <= 5'); narrow one or drop it."
      )

  private def unsatisfiablePreconditionSuggestion(ctx: SuggestionContext): Option[String] =
    ctx.operationName match
      case None => suggestionFor(DiagnosticCategory.UnsatisfiablePrecondition)
      case Some(op) =>
        Some(
          s"'$op.requires' is unsatisfiable on its own — its conjuncts contradict each other. Inspect the listed spans for a pair like 'y > 10' ∧ 'y < 5', or relax the input-type refinement."
        )

  private def unreachableOperationSuggestion(ctx: SuggestionContext): Option[String] =
    ctx.operationName match
      case None => suggestionFor(DiagnosticCategory.UnreachableOperation)
      case Some(op) =>
        val invs = invariantDisplayNames(ctx.ir)
        val invList =
          if invs.isEmpty then "the invariants"
          else s"invariants ${formatNameList(invs, max = 3)}"
        Some(
          s"'$op' is unreachable: its 'requires' is satisfiable alone, but $invList block every valid pre-state. Relax one invariant, or tighten '$op''s input type to exclude the conflicting range."
        )

  private def invariantViolationSuggestion(ctx: SuggestionContext): Option[String] =
    (ctx.operationName, ctx.invariantName, ctx.invariantDecl) match
      case (Some(op), Some(inv), Some(decl)) =>
        val fields = collectFieldNames(decl.expr)
        if fields.isEmpty then
          Some(
            s"'$op' violates '$inv'. Tighten 'ensures' so the fields '$inv' constrains are pinned by '=' or a range predicate; see counterexample."
          )
        else
          val fieldList = formatNameList(fields, max = 2)
          Some(
            s"'$op' violates '$inv' on field(s) $fieldList — see counterexample. Tighten 'ensures' with a range predicate or a constructor that initialises ${fields.head} correctly."
          )
      case _ => suggestionFor(DiagnosticCategory.InvariantViolationByOperation)

  private def solverTimeoutSuggestion(ctx: SuggestionContext): Option[String] =
    val features = ctx.invariantDecl.map(_.expr).toList.flatMap(featureSummary).distinct
    val featureClause =
      if features.isEmpty then ""
      else s" (uses ${features.mkString(", ")})"
    val invClause = ctx.invariantName match
      case Some(name) => s"; simplify invariant '$name'$featureClause"
      case None       => ""
    Some(
      s"Solver timed out on '${ctx.checkId}' after ${ctx.timeoutMs}ms. Increase --timeout$invClause, or split a heavy quantifier into smaller predicates."
    )

  private def invariantDisplayNames(ir: ServiceIR): List[String] =
    ir.invariants.zipWithIndex.map: (inv, i) =>
      inv.name.getOrElse(s"inv_$i")

  private def formatNameList(names: List[String], max: Int): String =
    val quoted = names.map(n => s"'$n'")
    if quoted.size <= max then quoted.mkString(", ")
    else quoted.take(max).mkString(", ") + ", …"

  private def cap(s: String): String =
    if s.length <= MaxSuggestionLength then s
    else s.take(MaxSuggestionLength - 1) + "…"

  private def collectFieldNames(e: Expr): List[String] =
    val out = List.newBuilder[String]
    def walk(x: Expr): Unit = x match
      case Expr.FieldAccess(base, field, _) =>
        out += field
        walk(base)
      case Expr.BinaryOp(_, l, r, _)       => walk(l); walk(r)
      case Expr.UnaryOp(_, op, _)          => walk(op)
      case Expr.Quantifier(_, bs, body, _) => bs.foreach(b => walk(b.domain)); walk(body)
      case Expr.SomeWrap(x, _)             => walk(x)
      case Expr.The(_, d, b, _)            => walk(d); walk(b)
      case Expr.EnumAccess(b, _, _)        => walk(b)
      case Expr.Index(b, i, _)             => walk(b); walk(i)
      case Expr.Call(c, args, _)           => walk(c); args.foreach(walk)
      case Expr.Prime(x, _)                => walk(x)
      case Expr.Pre(x, _)                  => walk(x)
      case Expr.With(b, ups, _)            => walk(b); ups.foreach(u => walk(u.value))
      case Expr.If(c, t, e, _)             => walk(c); walk(t); walk(e)
      case Expr.Let(_, v, b, _)            => walk(v); walk(b)
      case Expr.Lambda(_, b, _)            => walk(b)
      case Expr.Constructor(_, fs, _)      => fs.foreach(f => walk(f.value))
      case Expr.SetLiteral(es, _)          => es.foreach(walk)
      case Expr.MapLiteral(es, _) => es.foreach { e =>
          walk(e.key); walk(e.value)
        }
      case Expr.SetComprehension(_, d, p, _) => walk(d); walk(p)
      case Expr.SeqLiteral(es, _)            => es.foreach(walk)
      case Expr.Matches(x, _, _)             => walk(x)
      case _                                 => ()
    walk(e)
    out.result().distinct

  private def featureSummary(e: Expr): List[String] =
    val out = List.newBuilder[String]
    def walk(x: Expr, depthQuant: Int): Unit = x match
      case Expr.Quantifier(_, bs, body, _) =>
        if depthQuant >= 1 then out += "quantifier alternation"
        bs.foreach(b => walk(b.domain, depthQuant))
        walk(body, depthQuant + 1)
      case Expr.SetComprehension(_, d, p, _) =>
        out += "set comprehension"
        walk(d, depthQuant); walk(p, depthQuant)
      case Expr.UnaryOp(UnOp.Power, op, _) =>
        out += "powerset"
        walk(op, depthQuant)
      case Expr.BinaryOp(_, l, r, _) => walk(l, depthQuant); walk(r, depthQuant)
      case Expr.UnaryOp(_, op, _)    => walk(op, depthQuant)
      case Expr.SomeWrap(x, _)       => walk(x, depthQuant)
      case Expr.The(_, d, b, _)      => walk(d, depthQuant); walk(b, depthQuant)
      case Expr.FieldAccess(b, _, _) => walk(b, depthQuant)
      case Expr.EnumAccess(b, _, _)  => walk(b, depthQuant)
      case Expr.Index(b, i, _)       => walk(b, depthQuant); walk(i, depthQuant)
      case Expr.Call(c, args, _)     => walk(c, depthQuant); args.foreach(walk(_, depthQuant))
      case Expr.Prime(x, _)          => walk(x, depthQuant)
      case Expr.Pre(x, _)            => walk(x, depthQuant)
      case Expr.With(b, ups, _) =>
        walk(b, depthQuant); ups.foreach(u => walk(u.value, depthQuant))
      case Expr.If(c, t, e, _)        => walk(c, depthQuant); walk(t, depthQuant); walk(e, depthQuant)
      case Expr.Let(_, v, b, _)       => walk(v, depthQuant); walk(b, depthQuant)
      case Expr.Lambda(_, b, _)       => walk(b, depthQuant)
      case Expr.Constructor(_, fs, _) => fs.foreach(f => walk(f.value, depthQuant))
      case Expr.SetLiteral(es, _)     => es.foreach(walk(_, depthQuant))
      case Expr.MapLiteral(es, _) =>
        es.foreach { e =>
          walk(e.key, depthQuant); walk(e.value, depthQuant)
        }
      case Expr.SeqLiteral(es, _) => es.foreach(walk(_, depthQuant))
      case Expr.Matches(x, _, _)  => walk(x, depthQuant)
      case _                      => ()
    walk(e, 0)
    out.result().distinct

  def formatDiagnostic(diag: VerificationDiagnostic, specFile: String): String =
    val lines = List.newBuilder[String]
    lines += formatPrimary(diag, specFile)
    for rel <- diag.relatedSpans do
      lines += s"  related: ${formatLocation(specFile, rel.span)} (${rel.note})"
    if diag.coreSpans.nonEmpty then
      lines += ""
      lines += "  unsat core (contributing assertions):"
      for rel <- diag.coreSpans do
        lines += s"    ${formatLocation(specFile, rel.span)}  ${rel.note}"
    diag.counterexample.foreach: ce =>
      lines += ""
      lines += "  Counterexample:"
      lines += CounterExample.format(ce)
    diag.suggestion.foreach: s =>
      lines += ""
      lines += s"  hint: $s"
    lines.result().mkString("\n")

  private def formatPrimary(diag: VerificationDiagnostic, specFile: String): String =
    val levelWord = diag.level match
      case DiagnosticLevel.Error   => "error"
      case DiagnosticLevel.Warning => "warning"
    val loc = diag.primarySpan match
      case Some(s) => formatLocation(specFile, s)
      case None    => specFile
    s"$loc: $levelWord: ${diag.message}"

  private def formatLocation(specFile: String, span: Span): String =
    s"$specFile:${span.startLine}:${span.startCol}"
