package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

final case class ParamSpec(
    name: String,
    typeExpr: type_expr,
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
