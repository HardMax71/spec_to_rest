package specrest.profile

import specrest.ir.TypeExpr

final case class TypeContext(
    entityNames: Set[String],
    enumNames: Set[String],
    aliasMap: Map[String, TypeExpr],
)

object TypeMap:

  def mapType(typeExpr: TypeExpr, profile: DeploymentProfile, ctx: TypeContext): MappedType =
    typeExpr match
      case TypeExpr.NamedType(name, _) => mapNamedType(name, profile, ctx)
      case TypeExpr.OptionType(inner, _) =>
        val m = mapType(inner, profile, ctx)
        MappedType(s"${m.python} | None", s"${m.pydantic} | None", s"Mapped[${m.python} | None]")
      case TypeExpr.SetType(inner, _) =>
        val m = mapType(inner, profile, ctx)
        MappedType(s"list[${m.python}]", s"list[${m.pydantic}]", s"Mapped[list[${m.python}]]")
      case TypeExpr.SeqType(inner, _) =>
        val m = mapType(inner, profile, ctx)
        MappedType(s"list[${m.python}]", s"list[${m.pydantic}]", s"Mapped[list[${m.python}]]")
      case TypeExpr.MapType(k, v, _) =>
        val km = mapType(k, profile, ctx)
        val vm = mapType(v, profile, ctx)
        MappedType(
          s"dict[${km.python}, ${vm.python}]",
          s"dict[${km.pydantic}, ${vm.pydantic}]",
          s"Mapped[dict[${km.python}, ${vm.python}]]",
        )
      case TypeExpr.RelationType(_, _, _, _) =>
        MappedType("int", "int", "Mapped[int]")

  private def mapNamedType(name: String, profile: DeploymentProfile, ctx: TypeContext): MappedType =
    profile.typeMap.get(name) match
      case Some(m) => MappedType(m.python, m.pydantic, s"Mapped[${m.python}]")
      case None if ctx.entityNames.contains(name) =>
        MappedType("int", "int", "Mapped[int]")
      case None if ctx.enumNames.contains(name) =>
        MappedType("str", "str", "Mapped[str]")
      case None =>
        ctx.aliasMap.get(name) match
          case Some(alias) => mapType(alias, profile, ctx)
          case None        => MappedType(name, name, s"Mapped[$name]")

  def resolveTypeExpr(typeExpr: TypeExpr, aliasMap: Map[String, TypeExpr]): TypeExpr =
    typeExpr match
      case TypeExpr.NamedType(n, _) =>
        aliasMap.get(n) match
          case Some(alias) => resolveTypeExpr(alias, aliasMap)
          case None        => typeExpr
      case _ => typeExpr
