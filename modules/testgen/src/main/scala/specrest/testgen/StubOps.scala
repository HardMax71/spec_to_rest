package specrest.testgen

import specrest.codegen.OperationContext
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

object StubOps:
  private given CanEqual[route_kind, route_kind] = CanEqual.derived

  def isStub(profiled: ProfiledService, op: ProfiledOperation): Boolean =
    isFailLoudStub(op.dafnyMethod.isDefined, effectiveKind(profiled, op))

  // A `list` route returns the array as the bare response body, so its single declared
  // output is the whole body — `response_data`, not `response_data["<name>"]`.
  def bareBodyOutput(op: ProfiledOperation, opDecl: operation_decl_full): Option[String] =
    val outs   = operOutputs(opDecl).map(prmName)
    val isList = OperationContext.initialRouteKind(op) match
      case _: RkList => true
      case _         => false
    if isList && outs.sizeIs == 1 then outs.headOption else None

  def skipReason(op: ProfiledOperation): String =
    s"operation '${op.operationName}' is a fail-loud stub: the convention engine cannot " +
      "derive a body and synthesis produced no kernel method, so the generated handler " +
      "raises NotImplementedError. There is no behavior to assert against."

  private def effectiveKind(profiled: ProfiledService, op: ProfiledOperation): route_kind =
    op.targetEntity.flatMap(en => profiled.entities.find(_.entityName == en)) match
      case Some(entity) => OperationContext.from(op, entity).routeKind
      case None         => OperationContext.initialRouteKind(op)
