package specrest.profile

import specrest.ir.generated.SpecRestGenerated.*

final case class TypeContext(
    entityNames: Set[String],
    enumNames: Set[String],
    aliasMap: Map[String, type_expr_full]
)

object TypeMap:

  def mapType(typeExpr: type_expr_full, profile: DeploymentProfile, ctx: TypeContext): MappedType =
    typeExpr match
      case NamedTypeF(name, _) => mapNamedType(name, profile, ctx)
      case OptionTypeF(inner, _) =>
        val m = mapType(inner, profile, ctx)
        wrapOption(m, profile)
      case SetTypeF(inner, _) =>
        val m = mapType(inner, profile, ctx)
        wrapList(m, profile)
      case SeqTypeF(inner, _) =>
        val m = mapType(inner, profile, ctx)
        wrapList(m, profile)
      case MapTypeF(k, v, _) =>
        val km = mapType(k, profile, ctx)
        val vm = mapType(v, profile, ctx)
        wrapMap(km, vm, profile)
      case RelationTypeF(_, _, _, _) =>
        relationFor(profile)

  private def mapNamedType(name: String, profile: DeploymentProfile, ctx: TypeContext): MappedType =
    profile.typeMap.get(name) match
      case Some(m) => MappedType(m.domain, m.validation, ormFieldFor(profile, m.domain))
      case None if ctx.entityNames.contains(name) =>
        relationFor(profile)
      case None if ctx.enumNames.contains(name) =>
        enumFor(profile)
      case None =>
        ctx.aliasMap.get(name) match
          case Some(alias) => mapType(alias, profile, ctx)
          case None        => MappedType(name, name, ormFieldFor(profile, name))

  private def ormFieldFor(profile: DeploymentProfile, domain: String): String =
    profile.language match
      case "go" => domain
      case "ts" => domain
      case _    => s"Mapped[$domain]"

  private def wrapOption(inner: MappedType, profile: DeploymentProfile): MappedType =
    profile.language match
      case "go" =>
        MappedType(s"*${inner.domain}", inner.validation, s"*${inner.domain}")
      case "ts" =>
        MappedType(
          s"${inner.domain} | null",
          s"${inner.validation} | null",
          s"${inner.domain} | null"
        )
      case _ =>
        MappedType(
          s"${inner.domain} | None",
          s"${inner.validation} | None",
          s"Mapped[${inner.domain} | None]"
        )

  private def wrapList(inner: MappedType, profile: DeploymentProfile): MappedType =
    profile.language match
      case "go" =>
        MappedType(s"[]${inner.domain}", s"[]${inner.validation}", s"[]${inner.domain}")
      case "ts" =>
        MappedType(s"${inner.domain}[]", s"${inner.validation}[]", s"${inner.domain}[]")
      case _ =>
        MappedType(
          s"list[${inner.domain}]",
          s"list[${inner.validation}]",
          s"Mapped[list[${inner.domain}]]"
        )

  private def wrapMap(km: MappedType, vm: MappedType, profile: DeploymentProfile): MappedType =
    profile.language match
      case "go" =>
        MappedType(
          s"map[${km.domain}]${vm.domain}",
          s"map[${km.validation}]${vm.validation}",
          s"map[${km.domain}]${vm.domain}"
        )
      case "ts" =>
        MappedType(
          s"Record<${km.domain}, ${vm.domain}>",
          s"Record<${km.validation}, ${vm.validation}>",
          s"Record<${km.domain}, ${vm.domain}>"
        )
      case _ =>
        MappedType(
          s"dict[${km.domain}, ${vm.domain}]",
          s"dict[${km.validation}, ${vm.validation}]",
          s"Mapped[dict[${km.domain}, ${vm.domain}]]"
        )

  private def relationFor(profile: DeploymentProfile): MappedType =
    profile.language match
      case "go" => MappedType("int64", "int64", "int64")
      case "ts" => MappedType("number", "number", "number")
      case _    => MappedType("int", "int", "Mapped[int]")

  private def enumFor(profile: DeploymentProfile): MappedType =
    profile.language match
      case "go" => MappedType("string", "string", "string")
      case "ts" => MappedType("string", "string", "string")
      case _    => MappedType("str", "str", "Mapped[str]")

  def resolveTypeExpr(
      typeExpr: type_expr_full,
      aliasMap: Map[String, type_expr_full]
  ): type_expr_full =
    typeExpr match
      case NamedTypeF(n, _) =>
        aliasMap.get(n) match
          case Some(alias) => resolveTypeExpr(alias, aliasMap)
          case None        => typeExpr
      case _ => typeExpr
