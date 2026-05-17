package specrest.testgen

import specrest.codegen.RouteKind
import specrest.ir.generated.SpecRestGenerated.OperationDeclFull
import specrest.ir.generated.SpecRestGenerated.ParamDeclFull
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

object StubOps:

  def isStub(profiled: ProfiledService, op: ProfiledOperation): Boolean =
    RouteKind.isFailLoudStub(op, entityNonIdColumns(profiled, op))

  // A `list` route returns the array as the bare response body, so its single declared
  // output is the whole body — `response_data`, not `response_data["<name>"]`.
  def bareBodyOutput(op: ProfiledOperation, opDecl: OperationDeclFull): Option[String] =
    val outs = opDecl.c.collect { case ParamDeclFull(n, _, _) => n }
    if RouteKind.classify(op) == RouteKind.List && outs.sizeIs == 1 then outs.headOption
    else None

  def skipReason(op: ProfiledOperation): String =
    s"operation '${op.operationName}' is a fail-loud stub: the convention engine cannot " +
      "derive a body and synthesis produced no kernel method, so the generated handler " +
      "raises NotImplementedError. There is no behavior to assert against."

  private def entityNonIdColumns(
      profiled: ProfiledService,
      op: ProfiledOperation
  ): Set[String] =
    op.targetEntity
      .flatMap(en => profiled.entities.find(_.entityName == en))
      .map(_.fields.filterNot(_.fieldName == "id").map(_.columnName).toSet)
      .getOrElse(Set.empty)
