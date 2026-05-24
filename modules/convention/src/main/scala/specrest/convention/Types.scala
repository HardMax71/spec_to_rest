package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

enum SynthesisStrategy derives CanEqual:
  case DirectEmit, LlmSynthesis

object SynthesisStrategy:
  def label(s: SynthesisStrategy): String = s match
    case DirectEmit   => "DIRECT_EMIT"
    case LlmSynthesis => "LLM_SYNTHESIS"

final case class AnalysisSignals(
    mutatedRelations: List[String],
    preservedRelations: List[String],
    createsNewKey: Boolean,
    deletesKey: Boolean,
    targetEntityFieldCount: Option[Int],
    withFieldCount: Option[Int],
    filterParamCount: Int,
    isTransition: Boolean,
    hasCollectionInput: Boolean
)

final case class OperationClassification(
    operationName: String,
    kind: operation_kind,
    method: http_method,
    matchedRule: String,
    targetEntity: Option[String],
    strategy: SynthesisStrategy,
    signals: AnalysisSignals
)

final case class ParamSpec(
    name: String,
    typeExpr: type_expr_full,
    required: Boolean
)

final case class EndpointSpec(
    operationName: String,
    method: http_method,
    path: String,
    pathParams: List[ParamSpec],
    queryParams: List[ParamSpec],
    bodyParams: List[ParamSpec],
    successStatus: Int
)

enum DiagnosticLevel derives CanEqual:
  case Error, Warning

final case class ConventionDiagnostic(
    level: DiagnosticLevel,
    message: String,
    span: Option[span_t],
    target: String,
    property: String
)
