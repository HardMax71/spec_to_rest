package specrest.verify

import cats.effect.IO
import cats.effect.syntax.all.*
import cats.syntax.all.*
import scala.concurrent.duration.*
import specrest.ir.*
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

enum CheckKind:
  case Global, Requires, Enabled, Preservation, Temporal

object CheckKind:
  def token(k: CheckKind): String = k match
    case Global       => "global"
    case Requires     => "requires"
    case Enabled      => "enabled"
    case Preservation => "preservation"
    case Temporal     => "temporal"

enum CheckOutcome:
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
    sourceSpans: List[Span],
    diagnostic: Option[VerificationDiagnostic]
)

final case class ConsistencyReport(checks: List[CheckResult], ok: Boolean)

object Consistency:

  final private case class NamedInvariant(name: String, decl: InvariantDecl)

  private enum CheckPlan:
    case Global(ir: ServiceIR)
    case Op(ir: ServiceIR, op: OperationDecl, kind: CheckKind)
    case Preservation(ir: ServiceIR, op: OperationDecl, inv: NamedInvariant)
    case Temporal(ir: ServiceIR, decl: TemporalDecl)

  def runConsistencyChecks(
      ir: ServiceIR,
      backend: WasmBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): IO[ConsistencyReport] =
    AlloyBackend.make.use: alloyBackend =>
      IO.delay(runConsistencyChecksWithAlloy(ir, backend, alloyBackend, config, dump))

  def runConsistencyChecks(
      ir: ServiceIR,
      config: VerificationConfig,
      dump: Option[DumpSink] = None
  ): IO[ConsistencyReport] =
    val plans = planChecks(ir)
    val results: IO[List[CheckResult]] =
      if config.maxParallel <= 1 then
        backendsResource.use: (wasm, alloy) =>
          plans.traverse(p => runOne(p, wasm, alloy, config, dump))
      else
        plans.parTraverseN(config.maxParallel): plan =>
          backendsResource.use: (wasm, alloy) =>
            runOne(plan, wasm, alloy, config, dump)
    results.map(reportFromResults)

  private def runOne(
      plan: CheckPlan,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): IO[CheckResult] =
    val io = IO.interruptible(executePlan(plan, backend, alloyBackend, config, dump))
    if config.timeoutMs <= 0 then io
    else io.timeoutTo(config.timeoutMs.millis, IO.pure(timeoutFallback(plan, config)))

  private def backendsResource: cats.effect.Resource[IO, (WasmBackend, AlloyBackend)] =
    for
      wasm  <- WasmBackend.make
      alloy <- AlloyBackend.make
    yield (wasm, alloy)

  def runConsistencyChecksSync(
      ir: ServiceIR,
      backend: WasmBackend,
      config: VerificationConfig,
      dump: Option[DumpSink] = None
  ): ConsistencyReport =
    var allocated: Option[AlloyBackend] = None
    lazy val alloyBackend =
      val backend = new AlloyBackend
      allocated = Some(backend)
      backend
    try runConsistencyChecksWithAlloy(ir, backend, alloyBackend, config, dump)
    finally allocated.foreach(_.close())

  private def runConsistencyChecksWithAlloy(
      ir: ServiceIR,
      backend: WasmBackend,
      alloyBackend: => AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): ConsistencyReport =
    val plans   = planChecks(ir)
    val results = plans.map(p => executePlan(p, backend, alloyBackend, config, dump))
    reportFromResults(results)

  private def planChecks(ir: ServiceIR): List[CheckPlan] =
    val builder = List.newBuilder[CheckPlan]
    builder += CheckPlan.Global(ir)
    val ops        = ir.operations.sortBy(_.name.toLowerCase)
    val invariants = enumerateInvariants(ir)
    for op <- ops do
      builder += CheckPlan.Op(ir, op, CheckKind.Requires)
      builder += CheckPlan.Op(ir, op, CheckKind.Enabled)
      for inv <- invariants do
        builder += CheckPlan.Preservation(ir, op, inv)
    for t <- ir.temporals do
      builder += CheckPlan.Temporal(ir, t)
    builder.result()

  private def executePlan(
      plan: CheckPlan,
      backend: WasmBackend,
      alloyBackend: => AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): CheckResult = plan match
    case CheckPlan.Global(ir) =>
      runGlobal(ir, backend, alloyBackend, config, dump)
    case CheckPlan.Op(ir, op, kind) =>
      runOperationCheck(ir, op, kind, backend, alloyBackend, config, dump)
    case CheckPlan.Preservation(ir, op, inv) =>
      runPreservationCheck(ir, op, inv, backend, alloyBackend, config, dump)
    case CheckPlan.Temporal(ir, decl) =>
      runTemporalAlloy(ir, decl, alloyBackend, config, dump)

  private def reportFromResults(results: List[CheckResult]): ConsistencyReport =
    val ok = results.forall(c =>
      c.status == CheckOutcome.Sat || c.status == CheckOutcome.Skipped
    )
    ConsistencyReport(results, ok)

  private def timeoutFallback(plan: CheckPlan, config: VerificationConfig): CheckResult =
    val (id, kind, tool, operationName, invariantName, sourceSpans) = plan match
      case CheckPlan.Global(ir) =>
        (
          "global",
          CheckKind.Global,
          Classifier.classifyGlobal(ir),
          None,
          None,
          ir.invariants.flatMap(_.span)
        )
      case CheckPlan.Op(ir, op, k) =>
        val kindStr = k match
          case CheckKind.Requires => "requires"
          case CheckKind.Enabled  => "enabled"
          case _                  => "?"
        val t = k match
          case CheckKind.Requires => Classifier.classifyRequires(op)
          case CheckKind.Enabled  => Classifier.classifyEnabled(op, ir)
          case _                  => VerifierTool.Z3
        (s"${op.name}.$kindStr", k, t, Some(op.name), None, operationCheckSpans(op, k, ir))
      case CheckPlan.Preservation(_, op, inv) =>
        (
          s"${op.name}.preserves.${inv.name}",
          CheckKind.Preservation,
          Classifier.classifyPreservation(op, inv.decl),
          Some(op.name),
          Some(inv.name),
          preservationSpans(op, inv.decl)
        )
      case CheckPlan.Temporal(_, decl) =>
        (
          s"temporal.${decl.name}",
          CheckKind.Temporal,
          VerifierTool.Alloy,
          None,
          Some(decl.name),
          decl.span.toList
        )
    val diagnostic = VerificationDiagnostic(
      level = DiagnosticLevel.Error,
      category = DiagnosticCategory.SolverTimeout,
      message = s"outer timeout on check '$id': fired at ${config.timeoutMs}ms",
      primarySpan = sourceSpans.headOption,
      relatedSpans = Nil,
      counterexample = None,
      suggestion = Diagnostic.suggestionFor(DiagnosticCategory.SolverTimeout)
    )
    CheckResult(
      id = id,
      kind = kind,
      tool = tool,
      operationName = operationName,
      invariantName = invariantName,
      status = CheckOutcome.Unknown,
      durationMs = config.timeoutMs.toDouble,
      detail = Some(s"outer timeout: ${config.timeoutMs}ms"),
      sourceSpans = sourceSpans,
      diagnostic = Some(diagnostic)
    )

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
      val noteForKind = kind match
        case CheckKind.Global       => "contributing invariant"
        case CheckKind.Requires     => "contributing requires clause"
        case CheckKind.Enabled      => "contributing assertion"
        case CheckKind.Preservation => "contributing assertion"
        case CheckKind.Temporal     => "contributing assertion"
      // De-duplicate by spec span; multiple Pos entries can map to the same fact.
      val seen = scala.collection.mutable.LinkedHashSet.empty[Span]
      for
        pos  <- result.corePositions.toList
        span <- nearestSpanForLine(rendered.factSpans, pos.y)
        if seen.add(span)
      yield RelatedSpan(span, noteForKind)

  private def nearestSpanForLine(
      lineMap: Map[Int, Span],
      line: Int
  ): Option[Span] =
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
      val noteForKind = kind match
        case CheckKind.Global       => "contributing invariant"
        case CheckKind.Requires     => "contributing requires clause"
        case CheckKind.Enabled      => "contributing assertion"
        case CheckKind.Preservation => "contributing assertion"
        case CheckKind.Temporal     => "contributing assertion"
      result.unsatCoreTrackers.flatMap: name =>
        name.stripPrefix("_t_").toIntOption.flatMap: idx =>
          script.assertions.lift(idx).flatMap(_.spanOpt).map: span =>
            RelatedSpan(span, noteForKind)

  private def enumerateInvariants(ir: ServiceIR): List[NamedInvariant] =
    ir.invariants.zipWithIndex.map: (inv, i) =>
      NamedInvariant(inv.name.getOrElse(s"inv_$i"), inv)

  private def runGlobal(
      ir: ServiceIR,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): CheckResult =
    val sourceSpans = ir.invariants.flatMap(_.span)
    val tool        = Classifier.classifyGlobal(ir)
    if tool == VerifierTool.Alloy then
      return runGlobalAlloy(ir, alloyBackend, config, sourceSpans, dump)
    Translator.translateSync(ir) match
      case Left(err) =>
        skippedCheck(
          "global",
          CheckKind.Global,
          tool,
          None,
          None,
          sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message
        )
      case Right(script) =>
        backend.checkSync(script, config) match
          case Left(err) =>
            skippedCheck(
              "global",
              CheckKind.Global,
              tool,
              None,
              None,
              sourceSpans,
              DiagnosticCategory.BackendError,
              err.message
            )
          case Right(result) =>
            val outcome = CheckOutcome.fromStatus(result.status)
            dumpZ3(
              dump,
              "global",
              script,
              config.timeoutMs,
              outcome,
              result.status,
              result.durationMs
            )
            finalizeCheck(FinalizeArgs(
              id = "global",
              kind = CheckKind.Global,
              tool = tool,
              operationName = None,
              invariantName = None,
              rawStatus = result.status,
              outcome = outcome,
              durationMs = result.durationMs,
              sourceSpans = sourceSpans,
              ir = ir,
              invariantDecl = None,
              op = None,
              coreSpans = z3CoreSpans(script, result, CheckKind.Global)
            ))

  private def runGlobalAlloy(
      ir: ServiceIR,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      sourceSpans: List[Span],
      dump: Option[DumpSink]
  ): CheckResult =
    AlloyTranslator.translateGlobalSync(ir, config.alloyScope) match
      case Left(err) =>
        skippedCheck(
          "global",
          CheckKind.Global,
          VerifierTool.Alloy,
          None,
          None,
          sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message
        )
      case Right(module) =>
        val rendered = AlloyRender.renderWithLineMap(module)
        alloyBackend.checkSync(
          rendered.source,
          commandIdx = 0,
          timeoutMs = config.timeoutMs,
          captureCore = config.captureCore
        ) match
          case Left(err) =>
            skippedCheck(
              "global",
              CheckKind.Global,
              VerifierTool.Alloy,
              None,
              None,
              sourceSpans,
              DiagnosticCategory.BackendError,
              err.message
            )
          case Right(result) =>
            val outcome = CheckOutcome.fromStatus(result.status)
            dumpAlloy(dump, "global", rendered.source, outcome, result.status, result.durationMs)
            finalizeCheck(FinalizeArgs(
              id = "global",
              kind = CheckKind.Global,
              tool = VerifierTool.Alloy,
              operationName = None,
              invariantName = None,
              rawStatus = result.status,
              outcome = outcome,
              durationMs = result.durationMs,
              sourceSpans = sourceSpans,
              ir = ir,
              invariantDecl = None,
              op = None,
              coreSpans = alloyCoreSpans(rendered, result, CheckKind.Global)
            ))

  private def runOperationCheck(
      ir: ServiceIR,
      op: OperationDecl,
      kind: CheckKind,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): CheckResult =
    val kindStr = kind match
      case CheckKind.Requires => "requires"
      case CheckKind.Enabled  => "enabled"
      case _                  => "?"
    val id          = s"${op.name}.$kindStr"
    val sourceSpans = operationCheckSpans(op, kind, ir)
    val tool = kind match
      case CheckKind.Requires => Classifier.classifyRequires(op)
      case CheckKind.Enabled  => Classifier.classifyEnabled(op, ir)
      case _                  => VerifierTool.Z3
    if tool == VerifierTool.Alloy then
      return runOperationAlloy(ir, op, kind, alloyBackend, config, id, sourceSpans, dump)
    val scriptE: Either[VerifyError.Translator, Z3Script] = kind match
      case CheckKind.Requires => Translator.translateOperationRequiresSync(ir, op)
      case CheckKind.Enabled  => Translator.translateOperationEnabledSync(ir, op)
      case _ =>
        Left(VerifyError.Translator(s"runOperationCheck: unexpected kind $kind"))
    scriptE match
      case Left(err) =>
        skippedCheck(
          id,
          kind,
          tool,
          Some(op.name),
          None,
          sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message
        )
      case Right(script) =>
        backend.checkSync(script, config) match
          case Left(err) =>
            skippedCheck(
              id,
              kind,
              tool,
              Some(op.name),
              None,
              sourceSpans,
              DiagnosticCategory.BackendError,
              err.message
            )
          case Right(result) =>
            val outcome = CheckOutcome.fromStatus(result.status)
            dumpZ3(dump, id, script, config.timeoutMs, outcome, result.status, result.durationMs)
            finalizeCheck(FinalizeArgs(
              id = id,
              kind = kind,
              tool = tool,
              operationName = Some(op.name),
              invariantName = None,
              rawStatus = result.status,
              outcome = outcome,
              durationMs = result.durationMs,
              sourceSpans = sourceSpans,
              ir = ir,
              invariantDecl = None,
              op = Some(op),
              coreSpans = z3CoreSpans(script, result, kind)
            ))

  private def runOperationAlloy(
      ir: ServiceIR,
      op: OperationDecl,
      kind: CheckKind,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      id: String,
      sourceSpans: List[Span],
      dump: Option[DumpSink]
  ): CheckResult =
    val moduleE: Either[VerifyError.AlloyTranslator, AlloyModule] = kind match
      case CheckKind.Requires =>
        AlloyTranslator.translateOperationRequiresSync(ir, op, config.alloyScope)
      case CheckKind.Enabled =>
        AlloyTranslator.translateOperationEnabledSync(ir, op, config.alloyScope)
      case _ =>
        Left(VerifyError.AlloyTranslator(s"runOperationAlloy: unexpected kind $kind"))
    moduleE match
      case Left(err) =>
        skippedCheck(
          id,
          kind,
          VerifierTool.Alloy,
          Some(op.name),
          None,
          sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message
        )
      case Right(module) =>
        val rendered = AlloyRender.renderWithLineMap(module)
        alloyBackend.checkSync(
          rendered.source,
          commandIdx = 0,
          timeoutMs = config.timeoutMs,
          captureCore = config.captureCore
        ) match
          case Left(err) =>
            skippedCheck(
              id,
              kind,
              VerifierTool.Alloy,
              Some(op.name),
              None,
              sourceSpans,
              DiagnosticCategory.BackendError,
              err.message
            )
          case Right(result) =>
            val outcome = CheckOutcome.fromStatus(result.status)
            dumpAlloy(dump, id, rendered.source, outcome, result.status, result.durationMs)
            finalizeCheck(FinalizeArgs(
              id = id,
              kind = kind,
              tool = VerifierTool.Alloy,
              operationName = Some(op.name),
              invariantName = None,
              rawStatus = result.status,
              outcome = outcome,
              durationMs = result.durationMs,
              sourceSpans = sourceSpans,
              ir = ir,
              invariantDecl = None,
              op = Some(op),
              coreSpans = alloyCoreSpans(rendered, result, kind)
            ))

  private def runPreservationCheck(
      ir: ServiceIR,
      op: OperationDecl,
      inv: NamedInvariant,
      backend: WasmBackend,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): CheckResult =
    val id          = s"${op.name}.preserves.${inv.name}"
    val sourceSpans = preservationSpans(op, inv.decl)
    val tool        = Classifier.classifyPreservation(op, inv.decl)
    if tool == VerifierTool.Alloy then
      return runPreservationAlloy(ir, op, inv, alloyBackend, config, id, sourceSpans, dump)
    Translator.translateOperationPreservationSync(ir, op, inv.decl) match
      case Left(err) =>
        skippedCheck(
          id,
          CheckKind.Preservation,
          tool,
          Some(op.name),
          Some(inv.name),
          sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message
        )
      case Right(script) =>
        backend.checkSync(script, config.copy(captureModel = true)) match
          case Left(err) =>
            skippedCheck(
              id,
              CheckKind.Preservation,
              tool,
              Some(op.name),
              Some(inv.name),
              sourceSpans,
              DiagnosticCategory.BackendError,
              err.message
            )
          case Right(result) =>
            val inverted = invertStatus(result.status)
            dumpZ3(dump, id, script, config.timeoutMs, inverted, result.status, result.durationMs)
            finalizeCheck(FinalizeArgs(
              id = id,
              kind = CheckKind.Preservation,
              tool = tool,
              operationName = Some(op.name),
              invariantName = Some(inv.name),
              rawStatus = result.status,
              outcome = inverted,
              durationMs = result.durationMs,
              sourceSpans = sourceSpans,
              ir = ir,
              invariantDecl = Some(inv.decl),
              op = Some(op),
              smokeResult = Some(result),
              artifact = Some(script.artifact),
              coreSpans = z3CoreSpans(script, result, CheckKind.Preservation)
            ))

  private def runTemporalAlloy(
      ir: ServiceIR,
      decl: TemporalDecl,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      dump: Option[DumpSink]
  ): CheckResult =
    val id          = s"temporal.${decl.name}"
    val sourceSpans = decl.span.toList
    AlloyTranslator.translateTemporalSync(ir, decl, config.alloyScope) match
      case Left(err) =>
        skippedCheck(
          id,
          CheckKind.Temporal,
          VerifierTool.Alloy,
          None,
          Some(decl.name),
          sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message
        )
      case Right(translation) =>
        val rendered = AlloyRender.renderWithLineMap(translation.module)
        alloyBackend.checkSync(
          rendered.source,
          commandIdx = 0,
          timeoutMs = config.timeoutMs,
          captureCore = config.captureCore
        ) match
          case Left(err) =>
            skippedCheck(
              id,
              CheckKind.Temporal,
              VerifierTool.Alloy,
              None,
              Some(decl.name),
              sourceSpans,
              DiagnosticCategory.BackendError,
              err.message
            )
          case Right(result) =>
            val outcome = translation.kind match
              case AlloyTranslator.TemporalKind.Always => invertStatus(result.status)
              case AlloyTranslator.TemporalKind.Eventually =>
                CheckOutcome.fromStatus(result.status)
            dumpAlloy(dump, id, rendered.source, outcome, result.status, result.durationMs)
            finalizeCheck(FinalizeArgs(
              id = id,
              kind = CheckKind.Temporal,
              tool = VerifierTool.Alloy,
              operationName = None,
              invariantName = Some(decl.name),
              rawStatus = result.status,
              outcome = outcome,
              durationMs = result.durationMs,
              sourceSpans = sourceSpans,
              ir = ir,
              invariantDecl = None,
              op = None,
              coreSpans = alloyCoreSpans(rendered, result, CheckKind.Temporal)
            ))

  private def runPreservationAlloy(
      ir: ServiceIR,
      op: OperationDecl,
      inv: NamedInvariant,
      alloyBackend: AlloyBackend,
      config: VerificationConfig,
      id: String,
      sourceSpans: List[Span],
      dump: Option[DumpSink]
  ): CheckResult =
    AlloyTranslator.translateOperationPreservationSync(ir, op, inv.decl, config.alloyScope) match
      case Left(err) =>
        skippedCheck(
          id,
          CheckKind.Preservation,
          VerifierTool.Alloy,
          Some(op.name),
          Some(inv.name),
          sourceSpans,
          DiagnosticCategory.TranslatorLimitation,
          err.message
        )
      case Right(module) =>
        val rendered = AlloyRender.renderWithLineMap(module)
        alloyBackend.checkSync(
          rendered.source,
          commandIdx = 0,
          timeoutMs = config.timeoutMs,
          captureCore = config.captureCore
        ) match
          case Left(err) =>
            skippedCheck(
              id,
              CheckKind.Preservation,
              VerifierTool.Alloy,
              Some(op.name),
              Some(inv.name),
              sourceSpans,
              DiagnosticCategory.BackendError,
              err.message
            )
          case Right(result) =>
            val inverted = invertStatus(result.status)
            dumpAlloy(dump, id, rendered.source, inverted, result.status, result.durationMs)
            finalizeCheck(FinalizeArgs(
              id = id,
              kind = CheckKind.Preservation,
              tool = VerifierTool.Alloy,
              operationName = Some(op.name),
              invariantName = Some(inv.name),
              rawStatus = result.status,
              outcome = inverted,
              durationMs = result.durationMs,
              sourceSpans = sourceSpans,
              ir = ir,
              invariantDecl = Some(inv.decl),
              op = Some(op),
              coreSpans = alloyCoreSpans(rendered, result, CheckKind.Preservation)
            ))

  final private case class FinalizeArgs(
      id: String,
      kind: CheckKind,
      tool: VerifierTool,
      operationName: Option[String],
      invariantName: Option[String],
      rawStatus: CheckStatus,
      outcome: CheckOutcome,
      durationMs: Double,
      sourceSpans: List[Span],
      ir: ServiceIR,
      invariantDecl: Option[InvariantDecl],
      op: Option[OperationDecl],
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
      diagnostic = diagnostic
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
            yield Z3CounterExample.decode(model, smoke.sortMap, smoke.funcMap, artifact)
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
      ir: ServiceIR
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
      tool: VerifierTool,
      operationName: Option[String],
      invariantName: Option[String],
      sourceSpans: List[Span],
      category: DiagnosticCategory,
      message: String
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
      diagnostic = Some(diagnostic)
    )

  private def detailFor(
      kind: CheckKind,
      op: Option[String],
      inv: Option[String],
      outcome: CheckOutcome
  ): Option[String] = kind match
    case CheckKind.Preservation =>
      outcome match
        case CheckOutcome.Sat => None
        case CheckOutcome.Unsat =>
          Some(
            s"operation '${op.getOrElse("?")}' does not preserve invariant '${inv.getOrElse("?")}' — counterexample found"
          )
        case CheckOutcome.Unknown =>
          Some(
            s"solver could not decide preservation of invariant '${inv.getOrElse("?")}' by operation '${op.getOrElse("?")}'"
          )
        case CheckOutcome.Skipped => None
    case CheckKind.Global =>
      outcome match
        case CheckOutcome.Sat => None
        case CheckOutcome.Unsat =>
          Some("invariants are jointly contradictory — no valid state exists")
        case CheckOutcome.Unknown =>
          Some("solver could not decide invariant satisfiability within the timeout")
        case CheckOutcome.Skipped => None
    case CheckKind.Requires =>
      outcome match
        case CheckOutcome.Sat => None
        case CheckOutcome.Unsat =>
          Some(
            s"'requires' of operation '${op.getOrElse("?")}' is unsatisfiable under the spec's base constraints — the operation can never fire"
          )
        case CheckOutcome.Unknown =>
          Some(
            s"solver could not decide 'requires' satisfiability for operation '${op.getOrElse("?")}'"
          )
        case CheckOutcome.Skipped => None
    case CheckKind.Enabled =>
      outcome match
        case CheckOutcome.Sat => None
        case CheckOutcome.Unsat =>
          Some(
            s"operation '${op.getOrElse("?")}' is dead — no valid pre-state satisfies both the invariants and its 'requires'"
          )
        case CheckOutcome.Unknown =>
          Some(s"solver could not decide enablement for operation '${op.getOrElse("?")}'")
        case CheckOutcome.Skipped => None
    case CheckKind.Temporal =>
      outcome match
        case CheckOutcome.Sat => None
        case CheckOutcome.Unsat =>
          Some(
            s"temporal property '${inv.getOrElse("?")}' does not hold under the invariants at the current Alloy scope"
          )
        case CheckOutcome.Unknown =>
          Some(
            s"solver could not decide temporal property '${inv.getOrElse("?")}' within the timeout"
          )
        case CheckOutcome.Skipped => None
