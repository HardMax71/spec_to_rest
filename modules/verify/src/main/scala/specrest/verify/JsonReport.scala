package specrest.verify

import io.circe.Json
import io.circe.Printer
import specrest.ir.Span

object JsonReport:

  val SchemaVersion: Int = 1

  private val printer: Printer = Printer.spaces2.copy(dropNullValues = false)

  def toJson(specFile: String, report: ConsistencyReport, totalMs: Double): Json =
    Json.obj(
      "schemaVersion" -> Json.fromInt(SchemaVersion),
      "specFile"      -> Json.fromString(specFile),
      "ok"            -> Json.fromBoolean(report.ok),
      "totalMs"       -> Json.fromDoubleOrNull(totalMs),
      "checks"        -> Json.arr(report.checks.map(checkJson)*)
    )

  def render(json: Json): String = printer.print(json) + "\n"

  private def checkJson(c: CheckResult): Json =
    Json.obj(
      "id"            -> Json.fromString(c.id),
      "kind"          -> Json.fromString(checkKindToken(c.kind)),
      "tool"          -> Json.fromString(VerifierTool.token(c.tool)),
      "operationName" -> optString(c.operationName),
      "invariantName" -> optString(c.invariantName),
      "status"        -> Json.fromString(outcomeToken(c.status)),
      "durationMs"    -> Json.fromDoubleOrNull(c.durationMs),
      "detail"        -> optString(c.detail),
      "sourceSpans"   -> Json.arr(c.sourceSpans.map(spanJson)*),
      "diagnostic"    -> c.diagnostic.fold(Json.Null)(diagnosticJson)
    )

  private def diagnosticJson(d: VerificationDiagnostic): Json =
    Json.obj(
      "level"          -> Json.fromString(levelToken(d.level)),
      "category"       -> Json.fromString(categoryToken(d.category)),
      "message"        -> Json.fromString(d.message),
      "primarySpan"    -> d.primarySpan.fold(Json.Null)(spanJson),
      "relatedSpans"   -> Json.arr(d.relatedSpans.map(relatedSpanJson)*),
      "counterexample" -> d.counterexample.fold(Json.Null)(counterExampleJson),
      "suggestion"     -> optString(d.suggestion),
      "coreSpans"      -> Json.arr(d.coreSpans.map(relatedSpanJson)*)
    )

  private def spanJson(s: Span): Json =
    Json.obj(
      "startLine" -> Json.fromInt(s.startLine),
      "startCol"  -> Json.fromInt(s.startCol),
      "endLine"   -> Json.fromInt(s.endLine),
      "endCol"    -> Json.fromInt(s.endCol)
    )

  private def relatedSpanJson(r: RelatedSpan): Json =
    Json.obj(
      "span" -> spanJson(r.span),
      "note" -> Json.fromString(r.note)
    )

  private def counterExampleJson(ce: DecodedCounterExample): Json =
    Json.obj(
      "entities"       -> Json.arr(ce.entities.map(entityJson)*),
      "stateRelations" -> Json.arr(ce.stateRelations.map(relationJson)*),
      "stateConstants" -> Json.arr(ce.stateConstants.map(constantJson)*),
      "inputs"         -> Json.arr(ce.inputs.map(inputJson)*)
    )

  private def entityJson(e: DecodedEntity): Json =
    Json.obj(
      "sortName"   -> Json.fromString(e.sortName),
      "label"      -> Json.fromString(e.label),
      "rawElement" -> Json.fromString(e.rawElement),
      "fields"     -> Json.arr(e.fields.map(fieldJson)*)
    )

  private def fieldJson(f: DecodedEntityField): Json =
    Json.obj(
      "name"  -> Json.fromString(f.name),
      "value" -> valueJson(f.value)
    )

  private def valueJson(v: DecodedValue): Json =
    Json.obj(
      "display"     -> Json.fromString(v.display),
      "entityLabel" -> optString(v.entityLabel)
    )

  private def relationJson(r: DecodedRelation): Json =
    Json.obj(
      "stateName" -> Json.fromString(r.stateName),
      "side"      -> Json.fromString(r.side),
      "entries"   -> Json.arr(r.entries.map(relationEntryJson)*)
    )

  private def relationEntryJson(e: DecodedRelationEntry): Json =
    Json.obj(
      "key"   -> valueJson(e.key),
      "value" -> valueJson(e.value)
    )

  private def constantJson(c: DecodedConstant): Json =
    Json.obj(
      "stateName" -> Json.fromString(c.stateName),
      "side"      -> Json.fromString(c.side),
      "value"     -> valueJson(c.value)
    )

  private def inputJson(i: DecodedInput): Json =
    Json.obj(
      "name"  -> Json.fromString(i.name),
      "value" -> valueJson(i.value)
    )

  private def optString(o: Option[String]): Json =
    o.fold(Json.Null)(Json.fromString)

  private def checkKindToken(k: CheckKind): String = k match
    case CheckKind.Global       => "global"
    case CheckKind.Requires     => "requires"
    case CheckKind.Enabled      => "enabled"
    case CheckKind.Preservation => "preservation"
    case CheckKind.Temporal     => "temporal"

  private def outcomeToken(o: CheckOutcome): String = o match
    case CheckOutcome.Sat     => "sat"
    case CheckOutcome.Unsat   => "unsat"
    case CheckOutcome.Unknown => "unknown"
    case CheckOutcome.Skipped => "skipped"

  private def levelToken(l: DiagnosticLevel): String = l match
    case DiagnosticLevel.Error   => "error"
    case DiagnosticLevel.Warning => "warning"

  private def categoryToken(c: DiagnosticCategory): String = c match
    case DiagnosticCategory.ContradictoryInvariants       => "contradictory_invariants"
    case DiagnosticCategory.UnsatisfiablePrecondition     => "unsatisfiable_precondition"
    case DiagnosticCategory.UnreachableOperation          => "unreachable_operation"
    case DiagnosticCategory.InvariantViolationByOperation => "invariant_violation_by_operation"
    case DiagnosticCategory.SolverTimeout                 => "solver_timeout"
    case DiagnosticCategory.TranslatorLimitation          => "translator_limitation"
    case DiagnosticCategory.BackendError                  => "backend_error"
