package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledOperation

final case class OperationContext(
    initialRouteKind: route_kind,
    routeKind: route_kind,
    entityNonIdColumnNames: Set[String],
    matchesEntityCreateShape: Boolean,
    hasRequestBody: Boolean,
    customRequestSchemaName: Option[String]
) derives CanEqual

object OperationContext:
  private given CanEqual[route_kind, route_kind] = CanEqual.derived

  // The "initial" routekind for callers that don't have a target entity in scope
  // (e.g. testgen's bareBodyOutput where the result depends only on classification,
  // not on entity-shape matching). `from` calls this internally; sharing it keeps
  // testgen and codegen from drifting on the field-extraction details.
  def initialRouteKind(op: ProfiledOperation): route_kind =
    classify(
      op.endpoint.method,
      int_of_integer(BigInt(op.endpoint.successStatus)),
      Nata(BigInt(op.endpoint.pathParams.length)),
      op.kind,
      op.endpoint.queryParams.nonEmpty || op.endpoint.bodyParams.nonEmpty
    )

  def from(op: ProfiledOperation, entity: ProfiledEntity): OperationContext =
    val entityNonIdColumnNames =
      entity.fields.filterNot(_.fieldName == "id").map(_.columnName).toSet
    val bodyParamNames   = op.endpoint.bodyParams.map(_.name)
    val initialRouteKind = OperationContext.initialRouteKind(op)
    val matchesEntityCreateShape =
      matchesCreateShape(initialRouteKind, bodyParamNames, entityNonIdColumnNames.toList)
    val routeKind = effectiveRouteKind(initialRouteKind, matchesEntityCreateShape)
    val isCreate = initialRouteKind match
      case _: RkCreate => true
      case _           => false
    val hasRequestBody = isCreate || op.endpoint.bodyParams.nonEmpty
    val customRequestSchemaName =
      if !hasRequestBody then None
      else if isCreate && matchesEntityCreateShape then None
      else Some(s"${op.operationName}Request")
    OperationContext(
      initialRouteKind = initialRouteKind,
      routeKind = routeKind,
      entityNonIdColumnNames = entityNonIdColumnNames,
      matchesEntityCreateShape = matchesEntityCreateShape,
      hasRequestBody = hasRequestBody,
      customRequestSchemaName = customRequestSchemaName
    )
