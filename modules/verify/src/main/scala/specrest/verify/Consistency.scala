package specrest.verify

import cats.effect.IO
import cats.effect.syntax.all.*
import cats.syntax.all.*
import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*
import specrest.verify.alloy.AlloyBackend
import specrest.verify.alloy.AlloyModule
import specrest.verify.alloy.Render as AlloyRender
import specrest.verify.alloy.Translator as AlloyTranslator
import specrest.verify.certificates.DumpSink
import specrest.verify.z3.SmokeCheckResult
import specrest.verify.z3.SmtLib
import specrest.verify.z3.Translator
import specrest.verify.z3.TranslatorArtifact
import specrest.verify.z3.WasmBackend
import specrest.verify.z3.Z3CounterExample
import specrest.verify.z3.Z3Script

enum CheckKind derives CanEqual:
  case Global, Requires, Enabled, Preservation, Temporal

object CheckKind:
  def token(k: CheckKind): String = k match
    case Global       => "global"
    case Requires     => "requires"
    case Enabled      => "enabled"
    case Preservation => "preservation"
    case Temporal     => "temporal"

enum CheckOutcome derives CanEqual:
  case Sat, Unsat, Unknown, Skipped

object CheckOutcome:
  def fromStatus(s: CheckStatus): CheckOutcome = s match
    case CheckStatus.Sat     => Sat
    case CheckStatus.Unsat   => Unsat
    case CheckStatus.Unknown => Unknown

  def token(o: CheckOutcome): String = o match
    case Sat     => "sat"
    case Unsat   => "unsat"
    case Unknown => "unknown"
    case Skipped => "skipped"

final case class CheckResult(
    id: String,
    kind: CheckKind,
    tool: VerifierTool,
    operationName: Option[String],
    invariantName: Option[String],
    status: CheckOutcome,
    durationMs: Double,
    detail: Option[String],
    sourceSpans: List[span_t],
    diagnostic: Option[VerificationDiagnostic],
    trust: TrustLevel = TrustLevel.BestEffort
)

final case class ConsistencyReport(checks: List[CheckResult], ok: Boolean)

object Consistency:

  final private case class NamedInvariant(name: String, decl: invariant_decl)

  private enum CheckPlan:
    case Global(ir: service_ir)
    case Op(ir: service_ir, op: operation_decl, kind: CheckKind)
    case Preservation(ir: service_ir, op: operation_decl, inv: NamedInvariant)
    case Temporal(ir: service_ir, decl: temporal_decl)

  // Beta-reduce user function/predicate calls via the verified, capture-guarded
  // `inline_calls` desugar before classification. Done at the IR level here, not
  // in `Translator`, because the trust classifier runs `lower` on these raw exprs
  // to pick Sound-vs-best-effort: it must see the inlined body too, else a
  // now-verifiable call would still route best-effort. Capped fixpoint; a residual
  // `CallF` (recursive / binder-containing / capture-risky) stays best-effort.
  private def inlineExpr(
      fs: List[function_decl],
      ps: List[predicate_decl],
      expr: expr
  ): expr =
    def hasUserCall(e: expr): Boolean =
      allSubexprs(e).exists {
        case CallF(IdentifierF(nm, _), _, _) =>
          fs.exists(f => fncName(f) == nm) || ps.exists(p => prdName(p) == nm)
        case _ => false
      }
    @annotation.tailrec
    def loop(e: expr, fuel: Int): expr =
      if fuel <= 0 || !hasUserCall(e) then e
      else loop(inline_calls(fs, ps, e), fuel - 1)
    loop(expr, 16)

  private def inlineService(ir: service_ir): service_ir =
    val fs = svcFunctions(ir)
    val ps = svcPredicates(ir)
    if fs.isEmpty && ps.isEmpty then ir
    else
      def inl(e: expr): expr = inlineExpr(fs, ps, e)
      ir match
        // positional fields: ServiceIRFull g=operations i=invariants;
        // OperationDeclFull d=requires e=ensures; InvariantDeclFull b=body
        case s: ServiceIRFull =>
          s.copy(
            g = s.g.map { case op: OperationDeclFull =>
              op.copy(d = op.d.map(inl), e = op.e.map(inl))
            },
            i = s.i.map { case inv: InvariantDeclFull => inv.copy(b = inl(inv.b)) }
          )

  def runConsistencyChecks(
      ir0: service_ir,
      config: VerificationConfig,
      dump: Option[DumpSink] = None
  ): IO[ConsistencyReport] =
    val ir    = inlineService(ir0)
    val plans = planChecks(ir)
    val enums = Trust.enumNames(ir)
    val results: IO[List[CheckResult]] =
      if config.maxParallel <= 1 then
        backendsResource.use: (wasm, alloy) =>
          plans.traverse(p => executePlan(p, wasm, alloy, config, dump, enums))
      else
        plans.parTraverseN(config.maxParallel): plan =>
          backendsResource.use: (wasm, alloy) =>
            executePlan(plan, wasm, alloy, config, dump, enums)
    results.map(rs => reportFromResults(rs.map(c => enrichSuggestion(c, ir, config))))

  private def enrichSuggestion(
      check: CheckResult,
      ir: service_ir,
      config: VerificationConfig
  ): CheckResult =
    check.diagnostic match
      case None => check
      case Some(diag) =>
        val op = check.operationName.flatMap(n =>
          svcOperations(ir).find(o => operName(o) == n)
        )
        val isInvariantBound = check.kind == CheckKind.Preservation
        val invDecl =
          if !isInvariantBound then None
          else
            check.invariantName.flatMap: n =>
              svcInvariants(ir)
                .find(i => invName(i).contains(n))
                .orElse(n.stripPrefix("inv_").toIntOption.flatMap(idx =>
                  svcInvariants(ir).lift(idx)
                ))
        val invDisplayName = if isInvariantBound then check.invariantName else None
        val newSuggestion: Option[String] =
          if !config.suggestions then None
          else
            diag.category match
              case DiagnosticCategory.TranslatorLimitation | DiagnosticCategory.BackendError =>
                diag.suggestion
              case _ =>
                Diagnostic.suggestionFor(
                  diag.category,
                  Diagnostic.SuggestionContext(
                    ir = ir,
                    op = op,
                    invariantDecl = invDecl,
                    operationName = check.operationName,
                    invariantName = invDisplayName,
                    counterexample = diag.counterexample,
                    checkId = check.id,
                    timeoutMs = config.timeoutMs
                  )
                )
        val newNarrative: Option[String] =
          if !config.narration then None
          else
            Narration.narrate(
              diag.category,
              Narration.Context(
                ir = ir,
                op = op,
                invariantDecl = invDecl,
                operationName = check.operationName,
                invariantName = invDisplayName,
                counterexample = diag.counterexample,
                coreSpans = diag.coreSpans
              )
            )
        check.copy(diagnostic =
          Some(diag.copy(suggestion = newSuggestion, narrative = newNarrative))
        )

  private def backendsResource: cats.effect.Resource[IO, (WasmBackend, AlloyBackend)] =
    for
      wasm  <- WasmBackend.make
      alloy <- AlloyBackend.make
    yield (wasm, alloy)

  private def planChecks(ir: service_ir): List[CheckPlan] =
    val builder = List.newBuilder[CheckPlan]
    builder += CheckPlan.Global(ir)
    val ops        = svcOperations(ir).sortBy(o => operName(o).toLowerCase)
    val invariants = enumerateInvariants(ir)
    for op <- ops do
      builder += CheckPlan.Op(ir, op, CheckKind.Requires)
      builder += CheckPlan.Op(ir, op, CheckKind.Enabled)
      for inv <- invariants do
        builder += CheckPlan.Preservation(ir, op, inv)
    for t <- svcTemporals(ir) do
      builder += CheckPlan.Temporal(ir, t)
    builder.result()

  private def executePlan(
      plan: CheckPlan,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink],
      enums: List[String]
  ): IO[CheckResult] =
    val trust = planTrust(plan, enums)
    plan match
      case CheckPlan.Global(ir) =>
        runGlobal(ir, backend, alloyBackend, config, dump, trust)
      case CheckPlan.Op(ir, op, kind) =>
        runOperationCheck(ir, op, kind, backend, alloyBackend, config, dump, trust)
      case CheckPlan.Preservation(ir, op, inv) =>
        runPreservationCheck(ir, op, inv, backend, alloyBackend, config, dump, trust)
      case CheckPlan.Temporal(ir, decl) =>
        runTemporalAlloy(ir, decl, alloyBackend, config, dump, trust)

  private def planTrust(plan: CheckPlan, enums: List[String]): TrustLevel = plan match
    case CheckPlan.Global(ir) =>
      TrustLevel.fromLifted(trustGlobal(enums, ir))
    case CheckPlan.Op(_, op, CheckKind.Requires) =>
      TrustLevel.fromLifted(trustRequires(enums, op))
    case CheckPlan.Op(ir, op, CheckKind.Enabled) =>
      TrustLevel.fromLifted(trustEnabled(enums, op, ir))
    case CheckPlan.Op(_, _, _) => TrustLevel.BestEffort
    case CheckPlan.Preservation(_, op, inv) =>
      TrustLevel.fromLifted(trustPreservation(enums, op, inv.decl))
    case CheckPlan.Temporal(_, _) => TrustLevel.BestEffort

  private def reportFromResults(results: List[CheckResult]): ConsistencyReport =
    val ok = results.forall(c =>
      c.status == CheckOutcome.Sat || c.status == CheckOutcome.Skipped
    )
    ConsistencyReport(results, ok)

  private def dumpZ3(
      dump: Option[DumpSink],
      id: String,
      script: Z3Script,
      timeoutMs: Long,
      outcome: CheckOutcome,
      rawStatus: CheckStatus,
      durationMs: Double
  ): Unit =
    dump.foreach: sink =>
      val timeout = if timeoutMs > 0 then Some(timeoutMs) else None
      sink.writeZ3(id, SmtLib.renderSmtLib(script, timeout), outcome, rawStatus, durationMs)

  private def dumpAlloy(
      dump: Option[DumpSink],
      id: String,
      source: String,
      outcome: CheckOutcome,
      rawStatus: CheckStatus,
      durationMs: Double
  ): Unit =
    dump.foreach(_.writeAlloy(id, source, outcome, rawStatus, durationMs))

  private def alloyCoreSpans(
      rendered: specrest.verify.alloy.RenderedAlloy,
      result: specrest.verify.alloy.AlloyCheckResult,
      kind: CheckKind
  ): List[RelatedSpan] =
    if result.corePositions.isEmpty then Nil
    else
      val noteForKind = contributionNote(kind)
      // De-duplicate by spec span; multiple Pos entries can map to the same fact.
      val seen = scala.collection.mutable.LinkedHashSet.empty[span_t]
      for
        pos  <- result.corePositions.toList
        span <- nearestSpanForLine(rendered.factSpans, pos.y)
        if seen.add(span)
      yield RelatedSpan(span, noteForKind)

  private def nearestSpanForLine(
      lineMap: Map[Int, span_t],
      line: Int
  ): Option[span_t] =
    if lineMap.contains(line) then lineMap.get(line)
    else
      // Pos.y can sit inside the body region; walk upward to the nearest fact entry.
      lineMap.keys.filter(_ <= line).maxOption.flatMap(lineMap.get)

  private def z3CoreSpans(
      script: Z3Script,
      result: SmokeCheckResult,
      kind: CheckKind
  ): List[RelatedSpan] =
    if result.unsatCoreTrackers.isEmpty then Nil
    else
      val noteForKind = contributionNote(kind)
      result.unsatCoreTrackers.flatMap: name =>
        name.stripPrefix("_t_").toIntOption.flatMap: idx =>
          script.assertions.lift(idx).flatMap(_.spanOpt).map: span =>
            RelatedSpan(span, noteForKind)

  private def enumerateInvariants(ir: service_ir): List[NamedInvariant] =
    svcInvariants(ir).zipWithIndex.map: (inv, i) =>
      NamedInvariant(invName(inv).getOrElse(s"inv_$i"), inv)

  final private case class CheckMeta(
      id: String,
      kind: CheckKind,
      operationName: Option[String],
      invariantName: Option[String],
      sourceSpans: List[span_t],
      op: Option[operation_decl],
      invariantDecl: Option[invariant_decl]
  )

  private def runGlobal(
      ir: service_ir,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink],
      trust: TrustLevel
  ): IO[CheckResult] =
    val sourceSpans = svcInvariants(ir).flatMap(invSpan)
    val tool        = Classifier.classifyGlobal(ir)
    val meta        = CheckMeta("global", CheckKind.Global, None, None, sourceSpans, None, None)
    if tool == VerifierTool.Alloy then
      runAlloyCheck(
        ir,
        meta,
        AlloyTranslator.translateGlobal(ir, config.alloyScope),
        alloyBackend,
        config,
        dump,
        trust,
        CheckOutcome.fromStatus
      )
    else if trust == TrustLevel.BestEffort then
      IO.pure(soundnessSkipped("global", CheckKind.Global, tool, None, None, sourceSpans))
    else
      runZ3Check(
        ir,
        meta,
        tool,
        Translator.translate(ir),
        backend,
        config,
        dump,
        trust,
        CheckOutcome.fromStatus,
        captureModel = false
      )

  private def runOperationCheck(
      ir: service_ir,
      op: operation_decl,
      kind: CheckKind,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink],
      trust: TrustLevel
  ): IO[CheckResult] =
    val kindStr = kind match
      case CheckKind.Requires => "requires"
      case CheckKind.Enabled  => "enabled"
      case _                  => "?"
    val id          = s"${operName(op)}.$kindStr"
    val sourceSpans = operationCheckSpans(op, kind, ir)
    val tool = kind match
      case CheckKind.Requires => Classifier.classifyRequires(op)
      case CheckKind.Enabled  => Classifier.classifyEnabled(op, ir)
      case _                  => VerifierTool.Z3
    val meta = CheckMeta(id, kind, Some(operName(op)), None, sourceSpans, Some(op), None)
    if tool == VerifierTool.Alloy then
      val moduleIO: IO[Either[VerifyError.AlloyTranslator, AlloyModule]] = kind match
        case CheckKind.Requires =>
          AlloyTranslator.translateOperationRequires(ir, op, config.alloyScope)
        case CheckKind.Enabled =>
          AlloyTranslator.translateOperationEnabled(ir, op, config.alloyScope)
        case _ =>
          IO.pure(Left(VerifyError.AlloyTranslator(s"runOperationCheck: unexpected kind $kind")))
      runAlloyCheck(ir, meta, moduleIO, alloyBackend, config, dump, trust, CheckOutcome.fromStatus)
    else if trust == TrustLevel.BestEffort then
      IO.pure(soundnessSkipped(id, kind, tool, Some(operName(op)), None, sourceSpans))
    else
      val scriptIO: IO[Either[VerifyError.Translator, Z3Script]] = kind match
        case CheckKind.Requires => Translator.translateOperationRequires(ir, op)
        case CheckKind.Enabled  => Translator.translateOperationEnabled(ir, op)
        case _ =>
          IO.pure(Left(VerifyError.Translator(s"runOperationCheck: unexpected kind $kind")))
      runZ3Check(
        ir,
        meta,
        tool,
        scriptIO,
        backend,
        config,
        dump,
        trust,
        CheckOutcome.fromStatus,
        captureModel = false
      )

  private def runPreservationCheck(
      ir: service_ir,
      op: operation_decl,
      inv: NamedInvariant,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink],
      trust: TrustLevel
  ): IO[CheckResult] =
    val id          = s"${operName(op)}.preserves.${inv.name}"
    val sourceSpans = preservationSpans(op, inv.decl)
    val tool        = Classifier.classifyPreservation(op, inv.decl)
    val meta = CheckMeta(
      id,
      CheckKind.Preservation,
      Some(operName(op)),
      Some(inv.name),
      sourceSpans,
      Some(op),
      Some(inv.decl)
    )
    if tool == VerifierTool.Alloy then
      runAlloyCheck(
        ir,
        meta,
        AlloyTranslator.translateOperationPreservation(ir, op, inv.decl, config.alloyScope),
        alloyBackend,
        config,
        dump,
        trust,
        invertStatus
      )
    else if trust == TrustLevel.BestEffort then
      IO.pure(
        soundnessSkipped(
          id,
          CheckKind.Preservation,
          tool,
          Some(operName(op)),
          Some(inv.name),
          sourceSpans
        )
      )
    else
      runZ3Check(
        ir,
        meta,
        tool,
        Translator.translateOperationPreservation(ir, op, inv.decl),
        backend,
        config,
        dump,
        trust,
        invertStatus,
        captureModel = true
      )

  private def runTemporalAlloy(
      ir: service_ir,
      decl: temporal_decl,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink],
      trust: TrustLevel
  ): IO[CheckResult] =
    val id = s"temporal.${tmpName(decl)}"
    val meta = CheckMeta(
      id,
      CheckKind.Temporal,
      None,
      Some(tmpName(decl)),
      tmpSpan(decl).toList,
      None,
      None
    )
    // `always(P)` is checked as `I and not P` (unsat = holds), `eventually(P)` as
    // `I and P` (sat = holds), so the outcome mapping depends on the translation kind.
    AlloyTranslator.translateTemporal(ir, decl, config.alloyScope).flatMap:
      case Left(err) =>
        IO.pure(skippedCheck(
          id,
          CheckKind.Temporal,
          VerifierTool.Alloy,
          None,
          Some(tmpName(decl)),
          meta.sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message,
          trust
        ))
      case Right(translation) =>
        val outcomeOf = translation.kind match
          case AlloyTranslator.TemporalKind.Always     => invertStatus
          case AlloyTranslator.TemporalKind.Eventually => CheckOutcome.fromStatus
        runAlloyCheck(
          ir,
          meta,
          IO.pure(Right(translation.module)),
          alloyBackend,
          config,
          dump,
          trust,
          outcomeOf
        )

  private def runZ3Check(
      ir: service_ir,
      meta: CheckMeta,
      tool: VerifierTool,
      scriptIO: IO[Either[VerifyError.Translator, Z3Script]],
      backend: WasmBackend,
      config: VerificationConfig,
      dump: Option[DumpSink],
      trust: TrustLevel,
      outcomeOf: CheckStatus => CheckOutcome,
      captureModel: Boolean
  ): IO[CheckResult] =
    scriptIO.flatMap:
      case Left(err) =>
        IO.pure(skippedCheck(
          meta.id,
          meta.kind,
          tool,
          meta.operationName,
          meta.invariantName,
          meta.sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message,
          trust
        ))
      case Right(script) =>
        val checkConfig = if captureModel then config.copy(captureModel = true) else config
        backend.check(script, checkConfig).flatMap:
          case Left(err) =>
            IO.pure(skippedCheck(
              meta.id,
              meta.kind,
              tool,
              meta.operationName,
              meta.invariantName,
              meta.sourceSpans,
              DiagnosticCategory.BackendError,
              err.message,
              trust
            ))
          case Right(result) =>
            val outcome = outcomeOf(result.status)
            IO.blocking(
              dumpZ3(
                dump,
                meta.id,
                script,
                config.timeoutMs,
                outcome,
                result.status,
                result.durationMs
              )
            ).as(finalizeCheck(FinalizeArgs(
              id = meta.id,
              kind = meta.kind,
              tool = tool,
              operationName = meta.operationName,
              invariantName = meta.invariantName,
              rawStatus = result.status,
              outcome = outcome,
              durationMs = result.durationMs,
              sourceSpans = meta.sourceSpans,
              ir = ir,
              invariantDecl = meta.invariantDecl,
              op = meta.op,
              trust = trust,
              smokeResult = if captureModel then Some(result) else None,
              artifact = if captureModel then Some(script.artifact) else None,
              coreSpans = z3CoreSpans(script, result, meta.kind)
            )))

  private def runAlloyCheck(
      ir: service_ir,
      meta: CheckMeta,
      moduleIO: IO[Either[VerifyError.AlloyTranslator, AlloyModule]],
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink],
      trust: TrustLevel,
      outcomeOf: CheckStatus => CheckOutcome
  ): IO[CheckResult] =
    moduleIO.flatMap:
      case Left(err) =>
        IO.pure(skippedCheck(
          meta.id,
          meta.kind,
          VerifierTool.Alloy,
          meta.operationName,
          meta.invariantName,
          meta.sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message,
          trust
        ))
      case Right(module) =>
        val rendered = AlloyRender.renderWithLineMap(module)
        alloyBackend.check(
          rendered.source,
          commandIdx = 0,
          timeoutMs = config.timeoutMs,
          captureCore = config.captureCore
        ).flatMap:
          case Left(err) =>
            IO.pure(skippedCheck(
              meta.id,
              meta.kind,
              VerifierTool.Alloy,
              meta.operationName,
              meta.invariantName,
              meta.sourceSpans,
              DiagnosticCategory.BackendError,
              err.message,
              trust
            ))
          case Right(result) =>
            val outcome = outcomeOf(result.status)
            IO.blocking(
              dumpAlloy(dump, meta.id, rendered.source, outcome, result.status, result.durationMs)
            ).as(finalizeCheck(FinalizeArgs(
              id = meta.id,
              kind = meta.kind,
              tool = VerifierTool.Alloy,
              operationName = meta.operationName,
              invariantName = meta.invariantName,
              rawStatus = result.status,
              outcome = outcome,
              durationMs = result.durationMs,
              sourceSpans = meta.sourceSpans,
              ir = ir,
              invariantDecl = meta.invariantDecl,
              op = meta.op,
              trust = trust,
              coreSpans = alloyCoreSpans(rendered, result, meta.kind)
            )))

  final private case class FinalizeArgs(
      id: String,
      kind: CheckKind,
      tool: VerifierTool,
      operationName: Option[String],
      invariantName: Option[String],
      rawStatus: CheckStatus,
      outcome: CheckOutcome,
      durationMs: Double,
      sourceSpans: List[span_t],
      ir: service_ir,
      invariantDecl: Option[invariant_decl],
      op: Option[operation_decl],
      trust: TrustLevel,
      smokeResult: Option[SmokeCheckResult] = None,
      artifact: Option[TranslatorArtifact] = None,
      coreSpans: List[RelatedSpan] = Nil
  )

  private def finalizeCheck(args: FinalizeArgs): CheckResult =
    val detail     = detailFor(args.kind, args.operationName, args.invariantName, args.outcome)
    val diagnostic = buildDiagnostic(args)
    CheckResult(
      id = args.id,
      kind = args.kind,
      tool = args.tool,
      operationName = args.operationName,
      invariantName = args.invariantName,
      status = args.outcome,
      durationMs = args.durationMs,
      detail = detail,
      sourceSpans = args.sourceSpans,
      diagnostic = diagnostic,
      trust = args.trust
    )

  private def buildDiagnostic(args: FinalizeArgs): Option[VerificationDiagnostic] =
    if args.outcome == CheckOutcome.Sat || args.outcome == CheckOutcome.Skipped then None
    else
      categoryFor(args.kind, args.rawStatus).map: category =>
        val counterexample =
          if category == DiagnosticCategory.InvariantViolationByOperation then
            for
              smoke    <- args.smokeResult
              model    <- smoke.model
              artifact <- args.artifact
            yield Z3CounterExample.decode(model, smoke.sortMap, smoke.funcMap, artifact, args.ir)
          else None
        VerificationDiagnostic(
          level = DiagnosticLevel.Error,
          category = category,
          message = messageFor(category, args.operationName, args.invariantName),
          primarySpan = primarySpanFor(args),
          relatedSpans = relatedSpansFor(args),
          counterexample = counterexample,
          suggestion = Diagnostic.suggestionFor(category),
          coreSpans = args.coreSpans
        )

  private def categoryFor(kind: CheckKind, status: CheckStatus): Option[DiagnosticCategory] =
    if status == CheckStatus.Unknown then Some(DiagnosticCategory.SolverTimeout)
    else
      (kind, status) match
        case (CheckKind.Global, CheckStatus.Unsat) =>
          Some(DiagnosticCategory.ContradictoryInvariants)
        case (CheckKind.Requires, CheckStatus.Unsat) =>
          Some(DiagnosticCategory.UnsatisfiablePrecondition)
        case (CheckKind.Enabled, CheckStatus.Unsat) => Some(DiagnosticCategory.UnreachableOperation)
        case (CheckKind.Preservation, CheckStatus.Sat) =>
          Some(DiagnosticCategory.InvariantViolationByOperation)
        case _ => None

  private def messageFor(
      category: DiagnosticCategory,
      op: Option[String],
      inv: Option[String]
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
    case DiagnosticCategory.SoundnessLimitation =>
      "check skipped: outside the verified subset"

  private def primarySpanFor(args: FinalizeArgs): Option[span_t] =
    if args.kind == CheckKind.Preservation && args.invariantDecl.isDefined then
      args.op.flatMap(operSpan).orElse(args.invariantDecl.flatMap(invSpan))
    else if args.kind == CheckKind.Global then
      svcInvariants(args.ir).headOption.flatMap(invSpan)
    else args.op.flatMap(operSpan)

  private def relatedSpansFor(args: FinalizeArgs): List[RelatedSpan] =
    val out = List.newBuilder[RelatedSpan]
    if args.kind == CheckKind.Preservation then
      args.invariantDecl.flatMap(invSpan).foreach: s =>
        out += RelatedSpan(s, s"invariant '${args.invariantName.getOrElse("?")}' declared here")
    if args.kind == CheckKind.Global then
      svcInvariants(args.ir).zipWithIndex.drop(1).foreach: (inv, i) =>
        invSpan(inv).foreach: s =>
          out += RelatedSpan(s, s"invariant '${invName(inv).getOrElse(s"inv_$i")}'")
    out.result()

  private def operationCheckSpans(
      op: operation_decl,
      kind: CheckKind,
      ir: service_ir
  ): List[span_t] =
    val out = List.newBuilder[span_t]
    operSpan(op).foreach(out += _)
    for r <- operRequires(op) do spanOf(r).foreach(out += _)
    if kind == CheckKind.Enabled then
      for inv <- svcInvariants(ir) do invSpan(inv).foreach(out += _)
    out.result()

  private def preservationSpans(op: operation_decl, inv: invariant_decl): List[span_t] =
    val out = List.newBuilder[span_t]
    operSpan(op).foreach(out += _)
    invSpan(inv).foreach(out += _)
    for e <- operEnsures(op) do spanOf(e).foreach(out += _)
    out.result()

  private def invertStatus(status: CheckStatus): CheckOutcome = status match
    case CheckStatus.Unsat   => CheckOutcome.Sat
    case CheckStatus.Sat     => CheckOutcome.Unsat
    case CheckStatus.Unknown => CheckOutcome.Unknown

  private def soundnessSkipped(
      id: String,
      kind: CheckKind,
      tool: VerifierTool,
      operationName: Option[String],
      invariantName: Option[String],
      sourceSpans: List[span_t]
  ): CheckResult =
    val message = "outside lower's coverage"
    val diagnostic = VerificationDiagnostic(
      level = DiagnosticLevel.Warning,
      category = DiagnosticCategory.SoundnessLimitation,
      message = s"check '$id' skipped: $message",
      primarySpan = sourceSpans.headOption,
      relatedSpans = Nil,
      counterexample = None,
      suggestion = Diagnostic.suggestionFor(DiagnosticCategory.SoundnessLimitation)
    )
    CheckResult(
      id = id,
      kind = kind,
      tool = tool,
      operationName = operationName,
      invariantName = invariantName,
      status = CheckOutcome.Skipped,
      durationMs = 0.0,
      detail = Some(s"soundness-limitation: $message"),
      sourceSpans = sourceSpans,
      diagnostic = Some(diagnostic),
      trust = TrustLevel.BestEffort
    )

  private def skippedCheck(
      id: String,
      kind: CheckKind,
      tool: VerifierTool,
      operationName: Option[String],
      invariantName: Option[String],
      sourceSpans: List[span_t],
      category: DiagnosticCategory,
      message: String,
      trust: TrustLevel
  ): CheckResult =
    val isTranslator = category == DiagnosticCategory.TranslatorLimitation
    val status: CheckOutcome =
      if isTranslator then CheckOutcome.Skipped else CheckOutcome.Unknown
    val detail =
      if isTranslator then Some(s"translator limitation: $message")
      else Some(s"backend error: $message")
    val diagnostic = VerificationDiagnostic(
      level = if isTranslator then DiagnosticLevel.Warning else DiagnosticLevel.Error,
      category = category,
      message =
        if isTranslator then s"translator limitation on check '$id': $message"
        else s"solver backend error on check '$id': $message",
      primarySpan = sourceSpans.headOption,
      relatedSpans = Nil,
      counterexample = None,
      suggestion = Diagnostic.suggestionFor(category)
    )
    CheckResult(
      id = id,
      kind = kind,
      tool = tool,
      operationName = operationName,
      invariantName = invariantName,
      status = status,
      durationMs = 0.0,
      detail = detail,
      sourceSpans = sourceSpans,
      diagnostic = Some(diagnostic),
      trust = trust
    )

  private def contributionNote(kind: CheckKind): String = kind match
    case CheckKind.Global       => "contributing invariant"
    case CheckKind.Requires     => "contributing requires clause"
    case CheckKind.Enabled      => "contributing assertion"
    case CheckKind.Preservation => "contributing assertion"
    case CheckKind.Temporal     => "contributing assertion"

  private def detailFor(
      kind: CheckKind,
      op: Option[String],
      inv: Option[String],
      outcome: CheckOutcome
  ): Option[String] = (kind, outcome) match
    case (_, CheckOutcome.Sat | CheckOutcome.Skipped) => None
    case (CheckKind.Preservation, CheckOutcome.Unsat) =>
      Some(
        s"operation '${op.getOrElse("?")}' does not preserve invariant '${inv.getOrElse("?")}' — counterexample found"
      )
    case (CheckKind.Preservation, CheckOutcome.Unknown) =>
      Some(
        s"solver could not decide preservation of invariant '${inv.getOrElse("?")}' by operation '${op.getOrElse("?")}'"
      )
    case (CheckKind.Global, CheckOutcome.Unsat) =>
      Some("invariants are jointly contradictory — no valid state exists")
    case (CheckKind.Global, CheckOutcome.Unknown) =>
      Some("solver could not decide invariant satisfiability within the timeout")
    case (CheckKind.Requires, CheckOutcome.Unsat) =>
      Some(
        s"'requires' of operation '${op.getOrElse("?")}' is unsatisfiable under the spec's base constraints — the operation can never fire"
      )
    case (CheckKind.Requires, CheckOutcome.Unknown) =>
      Some(
        s"solver could not decide 'requires' satisfiability for operation '${op.getOrElse("?")}'"
      )
    case (CheckKind.Enabled, CheckOutcome.Unsat) =>
      Some(
        s"operation '${op.getOrElse("?")}' is dead — no valid pre-state satisfies both the invariants and its 'requires'"
      )
    case (CheckKind.Enabled, CheckOutcome.Unknown) =>
      Some(s"solver could not decide enablement for operation '${op.getOrElse("?")}'")
    case (CheckKind.Temporal, CheckOutcome.Unsat) =>
      Some(
        s"temporal property '${inv.getOrElse("?")}' does not hold under the invariants at the current Alloy scope"
      )
    case (CheckKind.Temporal, CheckOutcome.Unknown) =>
      Some(
        s"solver could not decide temporal property '${inv.getOrElse("?")}' within the timeout"
      )
