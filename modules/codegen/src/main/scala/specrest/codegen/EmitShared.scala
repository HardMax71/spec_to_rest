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

  // Fewer path params = more specific (a static path beats a `{param}` catch-all),
  // so they sort first and a static path is never shadowed by a catch-all route.
  def byPathSpecificity(aPath: String, bPath: String): Boolean =
    aPath.count(_ == '{') < bPath.count(_ == '{')
