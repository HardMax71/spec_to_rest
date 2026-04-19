package specrest.codegen

import specrest.convention.HttpMethod
import specrest.convention.OperationKind
import specrest.profile.ProfiledOperation

enum RouteKind:
  case Create, Read, List, Delete, Redirect, Other

object RouteKind:
  private val RedirectStatuses: Set[Int] = Set(301, 302, 303, 307, 308)

  def classify(op: ProfiledOperation): RouteKind =
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
