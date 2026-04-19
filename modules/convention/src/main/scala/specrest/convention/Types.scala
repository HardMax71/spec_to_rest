package specrest.convention

import specrest.ir.{Span, TypeExpr}

enum HttpMethod:
  case GET, POST, PUT, PATCH, DELETE

object HttpMethod:
  val all: Set[HttpMethod] = HttpMethod.values.toSet

  def parse(s: String): Option[HttpMethod] = s match
    case "GET"    => Some(GET)
    case "POST"   => Some(POST)
    case "PUT"    => Some(PUT)
    case "PATCH"  => Some(PATCH)
    case "DELETE" => Some(DELETE)
    case _        => None

enum OperationKind:
  case Create, Read, Replace, PartialUpdate, Delete, CreateChild, FilteredRead, SideEffect,
    BatchMutation, Transition

final case class AnalysisSignals(
    mutatedRelations: List[String],
    preservedRelations: List[String],
    createsNewKey: Boolean,
    deletesKey: Boolean,
    targetEntityFieldCount: Option[Int],
    withFieldCount: Option[Int],
    filterParamCount: Int,
    isTransition: Boolean,
    hasCollectionInput: Boolean,
)

final case class OperationClassification(
    operationName: String,
    kind: OperationKind,
    method: HttpMethod,
    matchedRule: String,
    targetEntity: Option[String],
    signals: AnalysisSignals,
)

final case class ParamSpec(
    name: String,
    typeExpr: TypeExpr,
    required: Boolean,
)

final case class EndpointSpec(
    operationName: String,
    method: HttpMethod,
    path: String,
    pathParams: List[ParamSpec],
    queryParams: List[ParamSpec],
    bodyParams: List[ParamSpec],
    successStatus: Int,
)

final case class ColumnSpec(
    name: String,
    sqlType: String,
    nullable: Boolean,
    defaultValue: Option[String],
)

final case class ForeignKeySpec(
    column: String,
    refTable: String,
    refColumn: String,
    onDelete: String,
)

final case class IndexSpec(
    name: String,
    columns: List[String],
    unique: Boolean,
)

final case class TableSpec(
    name: String,
    entityName: String,
    columns: List[ColumnSpec],
    primaryKey: String,
    foreignKeys: List[ForeignKeySpec],
    checks: List[String],
    indexes: List[IndexSpec],
)

final case class DatabaseSchema(tables: List[TableSpec])

enum DiagnosticLevel:
  case Error, Warning

final case class ConventionDiagnostic(
    level: DiagnosticLevel,
    message: String,
    span: Option[Span],
    target: String,
    property: String,
)
