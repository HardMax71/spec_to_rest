package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledService

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

  // Profile base types keyed by name, with type aliases resolved to their base
  // domain (cycle-guarded). Shared by the Go and TS emitters; the Python emitter
  // resolves aliases straight to Python types and additionally unwraps Option.
  def aliasResolvedDomainLookup(profiled: ProfiledService): Map[String, String] =
    val base = profiled.profile.typeMap.map((k, v) => k -> v.domain)
    val aliasExprs =
      svcTypeAliases(profiled.ir).map(a => talName(a) -> talType(a)).toMap
    def resolve(te: type_expr, seen: Set[String]): Option[String] = te match
      case NamedTypeF(n, _) =>
        base
          .get(n)
          .orElse(
            if seen(n) then None
            else aliasExprs.get(n).flatMap(resolve(_, seen + n))
          )
      case _ => None
    base ++ aliasExprs.flatMap((n, t) => resolve(t, Set.empty).map(n -> _))

  def redirectTargetColumn(entity: ProfiledEntity): Option[String] =
    List("url", "location", "redirect_url").find(c => entity.fields.exists(_.columnName == c))
