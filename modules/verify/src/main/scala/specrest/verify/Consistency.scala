package specrest.verify

import specrest.ir.*

enum CheckKind:
  case Global, Requires, Enabled, Preservation

enum CheckOutcome:
  case Sat, Unsat, Unknown, Skipped

object CheckOutcome:
  def fromStatus(s: CheckStatus): CheckOutcome = s match
    case CheckStatus.Sat     => Sat
    case CheckStatus.Unsat   => Unsat
    case CheckStatus.Unknown => Unknown

final case class CheckResult(
    id: String,
    kind: CheckKind,
    operationName: Option[String],
    invariantName: Option[String],
    status: CheckOutcome,
    durationMs: Double,
    detail: Option[String],
    sourceSpans: List[Span],
    diagnostic: Option[VerificationDiagnostic],
)

final case class ConsistencyReport(checks: List[CheckResult], ok: Boolean)

object Consistency:

  private final case class NamedInvariant(name: String, decl: InvariantDecl)

  def runConsistencyChecks(
      ir: ServiceIR,
      backend: WasmBackend,
      config: VerificationConfig,
  ): ConsistencyReport =
    val checks = List.newBuilder[CheckResult]
    checks += runGlobal(ir, backend, config)
    val ops        = ir.operations.sortBy(_.name.toLowerCase)
    val invariants = enumerateInvariants(ir)
    for op <- ops do
      checks += runOperationCheck(ir, op, CheckKind.Requires, backend, config)
      checks += runOperationCheck(ir, op, CheckKind.Enabled, backend, config)
      for inv <- invariants do
        checks += runPreservationCheck(ir, op, inv, backend, config)
    val results = checks.result()
    val ok = results.forall(c =>
      c.status == CheckOutcome.Sat || c.status == CheckOutcome.Skipped,
    )
    ConsistencyReport(results, ok)

  private def enumerateInvariants(ir: ServiceIR): List[NamedInvariant] =
    ir.invariants.zipWithIndex.map: (inv, i) =>
      NamedInvariant(inv.name.getOrElse(s"inv_$i"), inv)

  private def runGlobal(
      ir: ServiceIR,
      backend: WasmBackend,
      config: VerificationConfig,
  ): CheckResult =
    val sourceSpans = ir.invariants.flatMap(_.span)
    try
      val script = Translator.translate(ir)
      val result = backend.check(script, config)
      val outcome = CheckOutcome.fromStatus(result.status)
      finalizeCheck(FinalizeArgs(
        id            = "global",
        kind          = CheckKind.Global,
        operationName = None,
        invariantName = None,
        rawStatus     = result.status,
        outcome       = outcome,
        durationMs    = result.durationMs,
        sourceSpans   = sourceSpans,
        ir            = ir,
        invariantDecl = None,
        op            = None,
      ))
    catch case e: Throwable =>
      skippedCheck("global", CheckKind.Global, None, None, sourceSpans, e)

  private def runOperationCheck(
      ir: ServiceIR,
      op: OperationDecl,
      kind: CheckKind,
      backend: WasmBackend,
      config: VerificationConfig,
  ): CheckResult =
    val kindStr = kind match
      case CheckKind.Requires => "requires"
      case CheckKind.Enabled  => "enabled"
      case _                   => "?"
    val id          = s"${op.name}.$kindStr"
    val sourceSpans = operationCheckSpans(op, kind, ir)
    try
      val script = kind match
        case CheckKind.Requires => Translator.translateOperationRequires(ir, op)
        case CheckKind.Enabled  => Translator.translateOperationEnabled(ir, op)
        case _ =>
          throw new TranslatorError(s"runOperationCheck: unexpected kind $kind")
      val result  = backend.check(script, config)
      val outcome = CheckOutcome.fromStatus(result.status)
      finalizeCheck(FinalizeArgs(
        id            = id,
        kind          = kind,
        operationName = Some(op.name),
        invariantName = None,
        rawStatus     = result.status,
        outcome       = outcome,
        durationMs    = result.durationMs,
        sourceSpans   = sourceSpans,
        ir            = ir,
        invariantDecl = None,
        op            = Some(op),
      ))
    catch case e: Throwable =>
      skippedCheck(id, kind, Some(op.name), None, sourceSpans, e)

  private def runPreservationCheck(
      ir: ServiceIR,
      op: OperationDecl,
      inv: NamedInvariant,
      backend: WasmBackend,
      config: VerificationConfig,
  ): CheckResult =
    val id          = s"${op.name}.preserves.${inv.name}"
    val sourceSpans = preservationSpans(op, inv.decl)
    try
      val script   = Translator.translateOperationPreservation(ir, op, inv.decl)
      val result   = backend.check(script, config.copy(captureModel = true))
      val inverted = invertStatus(result.status)
      finalizeCheck(FinalizeArgs(
        id            = id,
        kind          = CheckKind.Preservation,
        operationName = Some(op.name),
        invariantName = Some(inv.name),
        rawStatus     = result.status,
        outcome       = inverted,
        durationMs    = result.durationMs,
        sourceSpans   = sourceSpans,
        ir            = ir,
        invariantDecl = Some(inv.decl),
        op            = Some(op),
      ))
    catch case e: Throwable =>
      skippedCheck(id, CheckKind.Preservation, Some(op.name), Some(inv.name), sourceSpans, e)

  private final case class FinalizeArgs(
      id: String,
      kind: CheckKind,
      operationName: Option[String],
      invariantName: Option[String],
      rawStatus: CheckStatus,
      outcome: CheckOutcome,
      durationMs: Double,
      sourceSpans: List[Span],
      ir: ServiceIR,
      invariantDecl: Option[InvariantDecl],
      op: Option[OperationDecl],
  )

  private def finalizeCheck(args: FinalizeArgs): CheckResult =
    val detail     = detailFor(args.kind, args.operationName, args.invariantName, args.rawStatus)
    val diagnostic = buildDiagnostic(args)
    CheckResult(
      id            = args.id,
      kind          = args.kind,
      operationName = args.operationName,
      invariantName = args.invariantName,
      status        = args.outcome,
      durationMs    = args.durationMs,
      detail        = detail,
      sourceSpans   = args.sourceSpans,
      diagnostic    = diagnostic,
    )

  private def buildDiagnostic(args: FinalizeArgs): Option[VerificationDiagnostic] =
    if args.outcome == CheckOutcome.Sat || args.outcome == CheckOutcome.Skipped then None
    else
      categoryFor(args.kind, args.rawStatus).map: category =>
        VerificationDiagnostic(
          level          = DiagnosticLevel.Error,
          category       = category,
          message        = messageFor(category, args.operationName, args.invariantName),
          primarySpan    = primarySpanFor(args),
          relatedSpans   = relatedSpansFor(args),
          counterexample = None,
          suggestion     = Diagnostic.suggestionFor(category),
        )

  private def categoryFor(kind: CheckKind, status: CheckStatus): Option[DiagnosticCategory] =
    if status == CheckStatus.Unknown then Some(DiagnosticCategory.SolverTimeout)
    else
      (kind, status) match
        case (CheckKind.Global, CheckStatus.Unsat)       => Some(DiagnosticCategory.ContradictoryInvariants)
        case (CheckKind.Requires, CheckStatus.Unsat)     => Some(DiagnosticCategory.UnsatisfiablePrecondition)
        case (CheckKind.Enabled, CheckStatus.Unsat)      => Some(DiagnosticCategory.UnreachableOperation)
        case (CheckKind.Preservation, CheckStatus.Sat)   => Some(DiagnosticCategory.InvariantViolationByOperation)
        case _                                             => None

  private def messageFor(
      category: DiagnosticCategory,
      op: Option[String],
      inv: Option[String],
  ): String = category match
    case DiagnosticCategory.ContradictoryInvariants =>
      "invariants are jointly unsatisfiable — no valid state exists"
    case DiagnosticCategory.UnsatisfiablePrecondition =>
      s"'requires' of operation '${op.getOrElse("?")}' is unsatisfiable under the spec's base constraints"
    case DiagnosticCategory.UnreachableOperation =>
      s"operation '${op.getOrElse("?")}' is unreachable — no valid pre-state satisfies both the invariants and its 'requires'"
    case DiagnosticCategory.InvariantViolationByOperation =>
      s"operation '${op.getOrElse("?")}' violates invariant '${inv.getOrElse("?")}'"
    case DiagnosticCategory.SolverTimeout =>
      "solver could not decide the check within the timeout"
    case DiagnosticCategory.TranslatorLimitation =>
      "verifier does not yet support a construct used by this check"
    case DiagnosticCategory.BackendError =>
      "solver backend error"

  private def primarySpanFor(args: FinalizeArgs): Option[Span] =
    if args.kind == CheckKind.Preservation && args.invariantDecl.isDefined then
      args.op.flatMap(_.span).orElse(args.invariantDecl.flatMap(_.span))
    else if args.kind == CheckKind.Global then
      args.ir.invariants.headOption.flatMap(_.span)
    else args.op.flatMap(_.span)

  private def relatedSpansFor(args: FinalizeArgs): List[RelatedSpan] =
    val out = List.newBuilder[RelatedSpan]
    if args.kind == CheckKind.Preservation then
      args.invariantDecl.flatMap(_.span).foreach: s =>
        out += RelatedSpan(s, s"invariant '${args.invariantName.getOrElse("?")}' declared here")
    if args.kind == CheckKind.Global then
      args.ir.invariants.zipWithIndex.drop(1).foreach: (inv, i) =>
        inv.span.foreach: s =>
          out += RelatedSpan(s, s"invariant '${inv.name.getOrElse(s"inv_$i")}'")
    out.result()

  private def operationCheckSpans(
      op: OperationDecl,
      kind: CheckKind,
      ir: ServiceIR,
  ): List[Span] =
    val out = List.newBuilder[Span]
    op.span.foreach(out += _)
    for r <- op.requires do r.spanOpt.foreach(out += _)
    if kind == CheckKind.Enabled then
      for inv <- ir.invariants do inv.span.foreach(out += _)
    out.result()

  private def preservationSpans(op: OperationDecl, inv: InvariantDecl): List[Span] =
    val out = List.newBuilder[Span]
    op.span.foreach(out += _)
    inv.span.foreach(out += _)
    for e <- op.ensures do e.spanOpt.foreach(out += _)
    out.result()

  private def invertStatus(status: CheckStatus): CheckOutcome = status match
    case CheckStatus.Unsat   => CheckOutcome.Sat
    case CheckStatus.Sat     => CheckOutcome.Unsat
    case CheckStatus.Unknown => CheckOutcome.Unknown

  private def skippedCheck(
      id: String,
      kind: CheckKind,
      operationName: Option[String],
      invariantName: Option[String],
      sourceSpans: List[Span],
      err: Throwable,
  ): CheckResult =
    val message      = Option(err.getMessage).getOrElse(err.toString)
    val isTranslator = err.isInstanceOf[TranslatorError]
    val category =
      if isTranslator then DiagnosticCategory.TranslatorLimitation
      else DiagnosticCategory.BackendError
    val status: CheckOutcome =
      if isTranslator then CheckOutcome.Skipped else CheckOutcome.Unknown
    val detail =
      if isTranslator then Some(s"translator limitation: $message")
      else Some(s"backend error: $message")
    val diagnostic = VerificationDiagnostic(
      level          = if isTranslator then DiagnosticLevel.Warning else DiagnosticLevel.Error,
      category       = category,
      message =
        if isTranslator then s"translator limitation on check '$id': $message"
        else s"solver backend error on check '$id': $message",
      primarySpan    = sourceSpans.headOption,
      relatedSpans   = Nil,
      counterexample = None,
      suggestion     = Diagnostic.suggestionFor(category),
    )
    CheckResult(
      id            = id,
      kind          = kind,
      operationName = operationName,
      invariantName = invariantName,
      status        = status,
      durationMs    = 0.0,
      detail        = detail,
      sourceSpans   = sourceSpans,
      diagnostic    = Some(diagnostic),
    )

  private def detailFor(
      kind: CheckKind,
      op: Option[String],
      inv: Option[String],
      status: CheckStatus,
  ): Option[String] = kind match
    case CheckKind.Preservation =>
      status match
        case CheckStatus.Unsat => None
        case CheckStatus.Sat =>
          Some(s"operation '${op.getOrElse("?")}' does not preserve invariant '${inv.getOrElse("?")}' — counterexample found")
        case CheckStatus.Unknown =>
          Some(s"solver could not decide preservation of invariant '${inv.getOrElse("?")}' by operation '${op.getOrElse("?")}'")
    case CheckKind.Global =>
      status match
        case CheckStatus.Sat     => None
        case CheckStatus.Unsat   => Some("invariants are jointly contradictory — no valid state exists")
        case CheckStatus.Unknown => Some("solver could not decide invariant satisfiability within the timeout")
    case CheckKind.Requires =>
      status match
        case CheckStatus.Sat => None
        case CheckStatus.Unsat =>
          Some(s"'requires' of operation '${op.getOrElse("?")}' is unsatisfiable under the spec's base constraints — the operation can never fire")
        case CheckStatus.Unknown =>
          Some(s"solver could not decide 'requires' satisfiability for operation '${op.getOrElse("?")}'")
    case CheckKind.Enabled =>
      status match
        case CheckStatus.Sat => None
        case CheckStatus.Unsat =>
          Some(s"operation '${op.getOrElse("?")}' is dead — no valid pre-state satisfies both the invariants and its 'requires'")
        case CheckStatus.Unknown =>
          Some(s"solver could not decide enablement for operation '${op.getOrElse("?")}'")
