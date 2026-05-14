package specrest.verify

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

enum DiagnosticCategory derives CanEqual:
  case ContradictoryInvariants, UnsatisfiablePrecondition, UnreachableOperation,
    InvariantViolationByOperation, SolverTimeout, TranslatorLimitation, BackendError,
    SoundnessLimitation

object DiagnosticCategory:
  def token(c: DiagnosticCategory): String = c match
    case ContradictoryInvariants       => "contradictory_invariants"
    case UnsatisfiablePrecondition     => "unsatisfiable_precondition"
    case UnreachableOperation          => "unreachable_operation"
    case InvariantViolationByOperation => "invariant_violation_by_operation"
    case SolverTimeout                 => "solver_timeout"
    case TranslatorLimitation          => "translator_limitation"
    case BackendError                  => "backend_error"
    case SoundnessLimitation           => "soundness_limitation"

enum DiagnosticLevel derives CanEqual:
  case Error, Warning

object DiagnosticLevel:
  def token(l: DiagnosticLevel): String = l match
    case Error   => "error"
    case Warning => "warning"

final case class RelatedSpan(span: span_t, note: String)

final case class VerificationDiagnostic(
    level: DiagnosticLevel,
    category: DiagnosticCategory,
    message: String,
    primarySpan: Option[span_t],
    relatedSpans: List[RelatedSpan],
    counterexample: Option[DecodedCounterExample],
    suggestion: Option[String],
    coreSpans: List[RelatedSpan] = Nil,
    narrative: Option[String] = None
)

object Diagnostic:

  private val MaxSuggestionLength = 200

  final case class SuggestionContext(
      ir: ServiceIRFull,
      op: Option[OperationDeclFull],
      invariantDecl: Option[InvariantDeclFull],
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
      case DiagnosticCategory.SoundnessLimitation  => suggestionFor(category)
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
    case DiagnosticCategory.SoundnessLimitation =>
      Some(
        "The check involves a construct that is outside the formally verified subset captured by the Isabelle 'lower' projection. Narrow the spec to lowerable shapes — or, if the construct is essential to your spec, file a follow-up to extend the verified subset (see proofs/isabelle/STATUS.md for the current ledger)."
      )

  private def contradictoryInvariantsSuggestion(ctx: SuggestionContext): Option[String] =
    val names = invariantDisplayNames(ctx.ir)
    names match
      case Nil => suggestionFor(DiagnosticCategory.ContradictoryInvariants)
      case one :: Nil =>
        Some(
          s"Invariant '$one' is unsatisfiable on its own — its range constraints cannot be met by any state. Narrow the predicate or drop it."
        )
      case many =>
        val list = formatNameList(many, max = 3)
        Some(
          s"The invariant set is jointly unsatisfiable; for example, review $list for a pair whose range constraints cannot overlap (e.g., 'x >= 10' alongside 'x <= 5'); narrow or drop one."
        )

  private def unsatisfiablePreconditionSuggestion(ctx: SuggestionContext): Option[String] =
    ctx.operationName match
      case None => suggestionFor(DiagnosticCategory.UnsatisfiablePrecondition)
      case Some(op) =>
        Some(
          s"'$op.d' is unsatisfiable on its own — its conjuncts contradict each other. Inspect the listed spans for a pair like 'y > 10' ∧ 'y < 5', or relax the input-type refinement."
        )

  private def unreachableOperationSuggestion(ctx: SuggestionContext): Option[String] =
    ctx.operationName match
      case None => suggestionFor(DiagnosticCategory.UnreachableOperation)
      case Some(op) =>
        val invs = invariantDisplayNames(ctx.ir)
        val invClause =
          if invs.isEmpty then "the invariants"
          else s"the invariants (e.g., ${formatNameList(invs, max = 3)})"
        Some(
          s"'$op' is unreachable: 'requires' is satisfiable alone but conflicts with $invClause on every valid pre-state. Relax an invariant or tighten the input type."
        )

  private def invariantViolationSuggestion(ctx: SuggestionContext): Option[String] =
    (ctx.operationName, ctx.invariantName, ctx.invariantDecl) match
      case (Some(op), Some(inv), Some(decl)) =>
        val fields = collectFieldNames(decl.b)
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
    val features = ctx.invariantDecl.map(_.b).toList.flatMap(featureSummary).distinct
    val featureClause =
      if features.isEmpty then ""
      else s" (uses ${features.mkString(", ")})"
    val invClause = ctx.invariantName match
      case Some(name) => s"; simplify invariant '$name'$featureClause"
      case None       => ""
    Some(
      s"Solver timed out on '${ctx.checkId}' after ${ctx.timeoutMs}ms. Increase --timeout$invClause, or split a heavy quantifier into smaller predicates."
    )

  private def invariantDisplayNames(ir: ServiceIRFull): List[String] =
    ir.i.zipWithIndex.map: (inv, i) =>
      inv match { case InvariantDeclFull(n, _, _) => n.getOrElse(s"inv_$i") }

  private def formatNameList(names: List[String], max: Int): String =
    val quoted = names.map(n => s"'$n'")
    if quoted.size <= max then quoted.mkString(", ")
    else quoted.take(max).mkString(", ") + ", …"

  private def cap(s: String): String =
    if s.length <= MaxSuggestionLength then s
    else s.take(MaxSuggestionLength - 1) + "…"

  private def collectFieldNames(e: expr_full): List[String] =
    val out = List.newBuilder[String]
    def walk(x: expr_full): Unit =
      x match
        case FieldAccessF(_, field, _) => out += field
        case _                         => ()
      SpecRestGenerated.subexprs(x).foreach(walk)
    walk(e)
    out.result().distinct

  private def featureSummary(e: expr_full): List[String] =
    val out = List.newBuilder[String]
    def walk(x: expr_full, depthQuant: Int): Unit = x match
      case QuantifierF(_, bs, body, _) =>
        if depthQuant >= 1 then out += "nested quantifiers"
        bs.foreach { case QuantifierBindingFull(_, dom, _, _) => walk(dom, depthQuant) }
        walk(body, depthQuant + 1)
      case SetComprehensionF(_, d, p, _) =>
        out += "set comprehension"
        walk(d, depthQuant); walk(p, depthQuant)
      case UnaryOpF(UPower(), op, _) =>
        out += "powerset"
        walk(op, depthQuant)
      case _ =>
        SpecRestGenerated.subexprs(x).foreach(walk(_, depthQuant))
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
    diag.narrative.foreach: n =>
      lines += ""
      n.split("\n", -1).foreach(line => lines += s"  $line")
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

  private def formatLocation(specFile: String, span: span_t): String =
    span match { case SpanT(int_of_integer(a), int_of_integer(b), _, _) => s"$specFile:$a:$b" }
