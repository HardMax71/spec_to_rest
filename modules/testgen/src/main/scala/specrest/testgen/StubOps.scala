package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

object StubOps:
  private given CanEqual[route_kind, route_kind] = CanEqual.derived

  def isStub(profiled: ProfiledService, op: ProfiledOperation): Boolean =
    isFailLoudStub(op.dafnyMethod.isDefined, effectiveKind(profiled, op))

  // A `list` route returns the array as the bare response body, so its single declared
  // output is the whole body — `response_data`, not `response_data["<name>"]`.
  def bareBodyOutput(op: ProfiledOperation, opDecl: OperationDeclFull): Option[String] =
    val outs = opDecl.c.collect { case ParamDeclFull(n, _, _) => n }
    val isList = initialKind(op) match
      case _: RkList => true
      case _         => false
    if isList && outs.sizeIs == 1 then outs.headOption else None

  def skipReason(op: ProfiledOperation): String =
    s"operation '${op.operationName}' is a fail-loud stub: the convention engine cannot " +
      "derive a body and synthesis produced no kernel method, so the generated handler " +
      "raises NotImplementedError. There is no behavior to assert against."

  private def initialKind(op: ProfiledOperation): route_kind =
    classify(
      op.endpoint.method.toString,
      int_of_integer(BigInt(op.endpoint.successStatus)),
      Nata(BigInt(op.endpoint.pathParams.length)),
      op.kind.toString,
      op.endpoint.queryParams.nonEmpty || op.endpoint.bodyParams.nonEmpty
    )

  private def effectiveKind(profiled: ProfiledService, op: ProfiledOperation): route_kind =
    val initial = initialKind(op)
    val entityNonIdColumns = op.targetEntity
      .flatMap(en => profiled.entities.find(_.entityName == en))
      .map(_.fields.filterNot(_.fieldName == "id").map(_.columnName))
      .getOrElse(Nil)
    val bodyParamNames = op.endpoint.bodyParams.map(_.name)
    val matches        = matchesCreateShape(initial, bodyParamNames, entityNonIdColumns)
    effectiveRouteKind(initial, matches)
