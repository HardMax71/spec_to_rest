package specrest.testgen

import specrest.codegen.OperationContext
import specrest.codegen.ScalarOps
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

object StubOps:
  private given CanEqual[route_kind, route_kind] = CanEqual.derived

  def isStub(profiled: ProfiledService, op: ProfiledOperation): Boolean =
    // A scalar-state op gets a real emitted body (direct-emit path), so it
    // counts as "has a body" exactly like a kernel-bound method.
    val scalarHandled =
      ScalarOps.views(profiled).exists(_.operation.operationName == op.operationName)
    isFailLoudStub(op.dafnyMethod.isDefined || scalarHandled, effectiveKind(profiled, op))

  // A `list` route returns the array as the bare response body, so its single declared
  // output is the whole body — `response_data`, not `response_data["<name>"]`.
  def bareBodyOutput(op: ProfiledOperation, opDecl: operation_decl): Option[String] =
    val outs = operOutputs(opDecl).map(prmName)
    val isList = OperationContext.initialRouteKind(op) match
      case _: RkList => true
      case _         => false
    // A single Seq-valued output is the bare response array whatever the
    // route kind resolves to (filtered list ops classify as custom).
    val seqOut = operOutputs(opDecl) match
      case single :: Nil =>
        prmType(single) match
          case SeqTypeF(_, _) => true
          case _              => false
      case _ => false
    if (isList || seqOut) && outs.sizeIs == 1 then outs.headOption else None

  def authSkipReason(op: ProfiledOperation): String =
    s"operation is protected by requires_auth (${op.requiresAuth.mkString(", ")}); " +
      "the python runtime enforces credentials, and the auth-aware conformance " +
      "harness lands with #26"

  def skipReason(op: ProfiledOperation): String =
    s"operation '${op.operationName}' is a fail-loud stub: the convention engine cannot " +
      "derive a body and synthesis produced no kernel method, so the generated handler " +
      "raises NotImplementedError. There is no behavior to assert against."

  private def effectiveKind(profiled: ProfiledService, op: ProfiledOperation): route_kind =
    op.targetEntity.flatMap(en => profiled.entities.find(_.entityName == en)) match
      case Some(entity) => OperationContext.from(op, entity).routeKind
      case None         => OperationContext.initialRouteKind(op)
