package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

enum HttpMethod derives CanEqual:
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

enum OperationKind derives CanEqual:
  case Create, Read, Replace, PartialUpdate, Delete, CreateChild, FilteredRead, SideEffect,
    BatchMutation, Transition

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
    kind: OperationKind,
    method: HttpMethod,
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
    method: HttpMethod,
    path: String,
    pathParams: List[ParamSpec],
    queryParams: List[ParamSpec],
    bodyParams: List[ParamSpec],
    successStatus: Int
)

final case class ColumnSpec(
    name: String,
    sqlType: String,
    nullable: Boolean,
    defaultValue: Option[String]
) derives CanEqual

final case class ForeignKeySpec(
    column: String,
    refTable: String,
    refColumn: String,
    onDelete: String
) derives CanEqual

final case class IndexSpec(
    name: String,
    columns: List[String],
    unique: Boolean,
    filterClause: Option[String] = None
) derives CanEqual

final case class TableSpec(
    name: String,
    entityName: String,
    columns: List[ColumnSpec],
    primaryKey: String,
    foreignKeys: List[ForeignKeySpec],
    checks: List[String],
    indexes: List[IndexSpec]
) derives CanEqual

enum TriggerAggregate derives CanEqual:
  case Sum, Count, Min, Max

final case class TriggerSpec(
    name: String,
    functionName: String,
    targetTable: String,
    targetColumn: String,
    sourceTable: String,
    sourceForeignKey: String,
    aggregate: TriggerAggregate,
    sourceColumn: Option[String]
) derives CanEqual

final case class DatabaseSchema(
    tables: List[TableSpec],
    triggers: List[TriggerSpec] = Nil
) derives CanEqual

enum DiagnosticLevel derives CanEqual:
  case Error, Warning

final case class ConventionDiagnostic(
    level: DiagnosticLevel,
    message: String,
    span: Option[span_t],
    target: String,
    property: String
)
