package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledService

private[codegen] object EmitShared:

  // The Dafny python backend doubles inner underscores and appends a trailing
  // underscore to python keywords and builtins (`id` compiles to `id_`).
  private val PyReserved = Set(
    "id",
    "type",
    "str",
    "int",
    "float",
    "bool",
    "list",
    "dict",
    "set",
    "hash",
    "input",
    "object",
    "property",
    "min",
    "max",
    "sum",
    "len",
    "filter",
    "map",
    "range",
    "bytes",
    "print",
    "vars",
    "dir",
    "next",
    "iter",
    "super",
    "format",
    "hex",
    "oct",
    "abs",
    "round",
    "pow",
    "repr",
    "zip",
    "all",
    "any",
    "class",
    "def",
    "from",
    "import",
    "return",
    "pass",
    "if",
    "else",
    "elif",
    "for",
    "while",
    "in",
    "is",
    "not",
    "and",
    "or",
    "None",
    "True",
    "False",
    "lambda",
    "global",
    "nonlocal",
    "del",
    "with",
    "as",
    "try",
    "except",
    "finally",
    "raise",
    "assert",
    "yield",
    "async",
    "await",
    "break",
    "continue"
  )

  def pyDafnySelector(fieldName: String): String =
    val doubled = fieldName.replace("_", "__")
    if PyReserved.contains(doubled) then doubled + "_" else doubled

  def doubleQuoted(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  // A field's boundary-enforceable string refinement: the entity declaration's
  // alias type plus its inline where clause, reduced by StringRefinements.
  def entityFieldRefinement(
      profiled: specrest.profile.ProfiledService,
      entity: specrest.profile.ProfiledEntity,
      f: specrest.profile.ProfiledField
  ): specrest.convention.StringRefinements.Reduced =
    import specrest.ir.generated.SpecRestGenerated.*
    svcEntities(profiled.ir)
      .find(d => entName(d) == entity.entityName)
      .flatMap(d => entFields(d).find(fd => fldName(fd) == f.fieldName))
      .map(fd =>
        specrest.convention.StringRefinements.reduceField(fldType(fd), fldDefault(fd), profiled.ir)
      )
      .getOrElse(specrest.convention.StringRefinements.Reduced(None, None, Nil))

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
  // domain (cycle-guarded). Go and TS pass no optionWrap, so Option-typed
  // aliases stay unresolved (their historical behavior); Python wraps them as
  // `T | None`.
  def aliasResolvedDomainLookup(
      profiled: ProfiledService,
      optionWrap: Option[String => String] = None
  ): Map[String, String] =
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
      case OptionTypeF(inner, _) =>
        optionWrap.flatMap(w => resolve(inner, seen).map(w))
      case _ => None
    base ++ aliasExprs.flatMap((n, t) => resolve(t, Set.empty).map(n -> _))

  def paramType(
      te: type_expr,
      lookup: Map[String, String],
      default: String,
      optionWrap: String => String
  ): String = te match
    case NamedTypeF(n, _)      => lookup.getOrElse(n, default)
    case OptionTypeF(inner, _) => optionWrap(paramType(inner, lookup, default, optionWrap))
    case _                     => default

  def redirectTargetColumn(entity: ProfiledEntity): Option[String] =
    List("url", "location", "redirect_url").find(c => entity.fields.exists(_.columnName == c))

  // The row-lookup column for single-row routes: the first path param when it
  // names an entity column, otherwise the primary key.
  def lookupColumn(entity: ProfiledEntity, firstPathParam: Option[String]): String =
    firstPathParam match
      case Some(p) if entity.fields.exists(_.columnName == p) => p
      case _                                                  => "id"
