package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*

private[codegen] object EmitShared:

  def routeKindName(rk: route_kind): String = rk match
    case _: RkCreate   => "create"
    case _: RkRead     => "read"
    case _: RkList     => "list"
    case _: RkDelete   => "delete"
    case _: RkRedirect => "redirect"
    case _: RkOther    => "other"

  // Routes with fewer path params are matched first (less specific before more
  // specific), so a static path is never shadowed by a `{param}` catch-all.
  def byPathSpecificity(aPath: String, bPath: String): Boolean =
    aPath.count(_ == '{') < bPath.count(_ == '{')
