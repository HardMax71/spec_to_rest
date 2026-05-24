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

  def from(op: ProfiledOperation, entity: ProfiledEntity): OperationContext =
    val entityNonIdColumnNames =
      entity.fields.filterNot(_.fieldName == "id").map(_.columnName).toSet
    val bodyParamNames = op.endpoint.bodyParams.map(_.name)
    val hasFilterInputs =
      op.endpoint.queryParams.nonEmpty || op.endpoint.bodyParams.nonEmpty
    val initialRouteKind = classify(
      op.endpoint.method.toString,
      int_of_integer(BigInt(op.endpoint.successStatus)),
      Nata(BigInt(op.endpoint.pathParams.length)),
      op.kind.toString,
      hasFilterInputs
    )
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
