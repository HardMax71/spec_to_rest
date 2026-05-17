package specrest.codegen

import specrest.convention.HttpMethod
import specrest.convention.OperationKind
import specrest.profile.ProfiledOperation

enum RouteKind derives CanEqual:
  case Create, Read, List, Delete, Redirect, Other

object RouteKind:
  private val RedirectStatuses: Set[Int] = Set(301, 302, 303, 307, 308)

  def classify(op: ProfiledOperation): RouteKind =
    val shape = classifyShape(op)
    // The `list` template returns every row and takes no arguments, so any declared filter
    // input would be silently dropped — an unfiltered result returned with a 200. Emit the
    // fail-loud stub (Other) instead, exactly as a Create whose shape doesn't match the
    // entity is downgraded. A genuine list-all (no inputs) is unaffected; real filtering is
    // the synthesis pass's job, not a silently-wrong direct emit.
    if shape == List && hasFilterInputs(op) then Other else shape

  private def hasFilterInputs(op: ProfiledOperation): Boolean =
    op.endpoint.queryParams.nonEmpty || op.endpoint.bodyParams.nonEmpty

  private def classifyShape(op: ProfiledOperation): RouteKind =
    val method          = op.endpoint.method
    val status          = op.endpoint.successStatus
    val pathParamCount  = op.endpoint.pathParams.length
    val hasPathParam    = pathParamCount > 0
    val singlePathParam = pathParamCount == 1

    if RedirectStatuses.contains(status) then Redirect
    else if op.kind == OperationKind.Create then Create
    else if op.kind == OperationKind.Read && singlePathParam then Read
    else if op.kind == OperationKind.Read && !hasPathParam then List
    else if op.kind == OperationKind.FilteredRead && !hasPathParam then List
    else if op.kind == OperationKind.Delete && singlePathParam then Delete
    else if method == HttpMethod.GET && !hasPathParam then List
    else Other
