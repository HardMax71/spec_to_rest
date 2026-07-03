package specrest.codegen.python

import specrest.codegen.AdminModel
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

// Generates app/services/_state_bridge.py: hydrate a Dafny ServiceState from
// the database and persist its mutations back, inside the request's session
// transaction. The schema knowledge is the same inference /admin/state uses
// (AdminModel.projectionFor); the Dafny side of every name doubles its
// underscores (the Dafny Python backend's identifier escaping).
object StateBridge:

  private val ScalarPyTypes = Set("str", "int", "bool", "datetime")

  final private case class RelationPlan(
      stateField: String,
      entity: ProfiledEntity,
      keyField: ProfiledField,
      valueField: Option[ProfiledField]
  ):
    def isEntityRow: Boolean = valueField.isEmpty

  final private case class ScalarPlan(stateField: String, columnName: String)

  final case class Plan private[StateBridge] (
      private[StateBridge] val relations: List[RelationPlan],
      private[StateBridge] val scalars: List[ScalarPlan]
  ):
    private[StateBridge] def entityRowRelations: List[RelationPlan] =
      relations.filter(_.isEntityRow).distinctBy(_.entity.entityName)

  def dafnyName(specName: String): String = specName.replace("_", "__")

  private def baseType(domainType: String): String =
    domainType.replaceAll("\\s*\\|\\s*None$", "").trim

  private def fieldSupported(f: ProfiledField): Boolean =
    ScalarPyTypes.contains(baseType(f.domainType))

  // None when the spec's state cannot round-trip through the bridge; the
  // reason keeps the eligibility decision explainable in logs and tests.
  def plan(profiled: ProfiledService): Either[String, Plan] =
    val ir        = profiled.ir
    val byName    = profiled.entities.map(e => e.entityName -> e).toMap
    val relations = List.newBuilder[RelationPlan]
    val scalars   = List.newBuilder[ScalarPlan]
    val problems  = List.newBuilder[String]

    for sf <- irStateFields(ir) do
      val name = stfName(sf)
      AdminModel.projectionFor(sf, ir) match
        case None => () // unbacked: hydrates to the Dafny zero value, like /admin/state's null
        case Some(p) =>
          p.valueShape match
            case AdminModel.ProjectionValue.ScalarStateColumn(col) =>
              scalars += ScalarPlan(name, col)
            case shape =>
              byName.get(p.entityName) match
                case None =>
                  problems += s"state field '$name' projects onto unknown entity '${p.entityName}'"
                case Some(entity) =>
                  entity.fields.find(_.fieldName == p.keyFieldName) match
                    case None =>
                      problems += s"state field '$name': key field '${p.keyFieldName}' not on '${p.entityName}'"
                    case Some(key) =>
                      val unsupported = entity.fields.filterNot(fieldSupported)
                      if unsupported.nonEmpty then
                        problems += s"state field '$name': entity '${p.entityName}' has field types the bridge cannot marshal (${unsupported.map(_.domainType).distinct.mkString(", ")})"
                      else if !Set("str", "int").contains(baseType(key.domainType)) || key.nullable
                      then
                        problems += s"state field '$name': key '${p.keyFieldName}' must be a non-optional str or int"
                      else
                        val valueField = shape match
                          case AdminModel.ProjectionValue.PrimitiveField(vf) =>
                            entity.fields.find(_.fieldName == vf)
                          case _ => None
                        shape match
                          case AdminModel.ProjectionValue.PrimitiveField(vf)
                              if valueField.isEmpty =>
                            problems += s"state field '$name': value field '$vf' not on '${p.entityName}'"
                          case _ =>
                            relations += RelationPlan(name, entity, key, valueField)

    val built       = Plan(relations.result(), scalars.result())
    val persistable = built.entityRowRelations.map(_.entity.entityName).toSet
    for r <- built.relations if !r.isEntityRow do
      if !persistable.contains(r.entity.entityName) then
        problems += s"state field '${r.stateField}' projects a single column of '${r.entity.entityName}', which no entity-valued state field covers; mutations could not be written back"

    problems.result() match
      case first :: _ => Left(first)
      case Nil        => Right(built)

  def hasState(plan: Plan): Boolean = plan.relations.nonEmpty || plan.scalars.nonEmpty

  private def toDafnyFieldExpr(f: ProfiledField, rowRef: String): String =
    val access = s"$rowRef.${f.columnName}"
    val conv = baseType(f.domainType) match
      case "str"      => (v: String) => s"to_dafny_str($v)"
      case "datetime" => (v: String) => s"_epoch($v)"
      case "bool"     => (v: String) => s"bool($v)"
      case _          => (v: String) => s"int($v)"
    if f.nullable then
      s"(module_.Option_Some(${conv(access)}) if $access is not None else module_.Option_None())"
    else conv(access)

  private def fromDafnyFieldExpr(f: ProfiledField, valueRef: String): String =
    val access = s"$valueRef.${dafnyName(f.fieldName)}"
    def conv(v: String): String = baseType(f.domainType) match
      case "str"      => s"from_dafny_str($v)"
      case "datetime" => s"_from_epoch($v)"
      case "bool"     => s"bool($v)"
      case _          => s"int($v)"
    if f.nullable then
      s"(${conv(s"$access.value")} if isinstance($access, module_.Option_Some) else None)"
    else conv(access)

  private def keyToDafny(key: ProfiledField, rowRef: String): String =
    baseType(key.domainType) match
      case "str" => s"to_dafny_str($rowRef.${key.columnName})"
      case _     => s"int($rowRef.${key.columnName})"

  private def keyFromDafny(key: ProfiledField, keyRef: String): String =
    baseType(key.domainType) match
      case "str" => s"from_dafny_str($keyRef)"
      case _     => s"int($keyRef)"

  def emit(profiled: ProfiledService): String =
    val planned = plan(profiled) match
      case Right(p) => p
      case Left(_)  => Plan(Nil, Nil)

    val entityImports = planned.relations
      .map(_.entity)
      .distinctBy(_.entityName)
      .sortBy(_.entityName)
      .map(e =>
        s"from app.models.${Naming.toSnakeCase(e.entityName)} import ${e.modelClassName}"
      )
    val scalarImport =
      if planned.scalars.isEmpty then Nil
      else List("from app.models.service_state import ServiceState as _ServiceStateRow")

    val needsDatetime = planned.relations
      .exists(_.entity.fields.exists(f => baseType(f.domainType) == "datetime"))

    val rowsVar = (e: ProfiledEntity) => s"${Naming.toSnakeCase(e.entityName)}_rows"

    val hydrateLines = List.newBuilder[String]
    for e <- planned.relations.map(_.entity).distinctBy(_.entityName) do
      hydrateLines += s"    ${rowsVar(e)} = (await session.execute(select(${e.modelClassName}))).scalars().all()"
    for r <- planned.relations do
      val rows = rowsVar(r.entity)
      hydrateLines += s"    st.${dafnyName(r.stateField)} = to_dafny_map({"
      r.valueField match
        case Some(vf) =>
          hydrateLines += s"        ${keyToDafny(r.keyField, "r")}: ${toDafnyFieldExpr(vf, "r")}"
        case None =>
          hydrateLines += s"        ${keyToDafny(r.keyField, "r")}: module_.${r.entity.entityName}_${r.entity.entityName}("
          for f <- r.entity.fields do
            hydrateLines += s"            ${toDafnyFieldExpr(f, "r")},"
          hydrateLines += "        )"
      hydrateLines += s"        for r in $rows"
      hydrateLines += "    })"
    if planned.scalars.nonEmpty then
      hydrateLines += "    scalar_row = ("
      hydrateLines += "        await session.execute(select(_ServiceStateRow))"
      hydrateLines += "    ).scalar_one_or_none()"
      hydrateLines += "    if scalar_row is not None:"
      for sc <- planned.scalars do
        hydrateLines += s"        st.${dafnyName(sc.stateField)} = int(scalar_row.${sc.columnName})"

    val persistLines = List.newBuilder[String]
    for r <- planned.entityRowRelations do
      val e     = r.entity
      val snake = Naming.toSnakeCase(e.entityName)
      persistLines += s"    ${snake}_post = {"
      persistLines += s"        ${keyFromDafny(r.keyField, "k")}: v"
      persistLines += s"        for k, v in from_dafny_map(st.${dafnyName(r.stateField)}).items()"
      persistLines += "    }"
      persistLines += s"    ${snake}_rows = {"
      persistLines += s"        r.${r.keyField.columnName}: r"
      persistLines += s"        for r in (await session.execute(select(${e.modelClassName}))).scalars().all()"
      persistLines += "    }"
      persistLines += s"    for key, value in ${snake}_post.items():"
      persistLines += s"        row = ${snake}_rows.get(key)"
      persistLines += "        if row is None:"
      persistLines += s"            session.add(${e.modelClassName}("
      for f <- e.fields do
        persistLines += s"                ${f.columnName}=${fromDafnyFieldExpr(f, "value")},"
      persistLines += "            ))"
      persistLines += "        else:"
      for f <- e.fields if f.fieldName != r.keyField.fieldName do
        persistLines += s"            row.${f.columnName} = ${fromDafnyFieldExpr(f, "value")}"
      persistLines += s"    for key, row in ${snake}_rows.items():"
      persistLines += s"        if key not in ${snake}_post:"
      persistLines += "            await session.delete(row)"
    if planned.scalars.nonEmpty then
      persistLines += "    scalar_row = ("
      persistLines += "        await session.execute(select(_ServiceStateRow))"
      persistLines += "    ).scalar_one_or_none()"
      persistLines += "    if scalar_row is None:"
      persistLines += "        scalar_row = _ServiceStateRow()"
      persistLines += "        session.add(scalar_row)"
      for sc <- planned.scalars do
        persistLines += s"    scalar_row.${sc.columnName} = int(st.${dafnyName(sc.stateField)})"
    persistLines += "    await session.flush()"

    val datetimeHelpers =
      if !needsDatetime then ""
      else
        """|
           |
           |
           |def _epoch(value: datetime) -> int:
           |    if value.tzinfo is None:
           |        return int(value.replace(tzinfo=timezone.utc).timestamp())
           |    return int(value.timestamp())
           |
           |
           |def _from_epoch(value: Any) -> datetime:
           |    return datetime.fromtimestamp(int(value), tz=timezone.utc)""".stripMargin

    val datetimeImport =
      if needsDatetime then "\nfrom datetime import datetime, timezone\nfrom typing import Any\n"
      else "\n"
    val adapterNames =
      List("from_dafny_map", "from_dafny_str", "make_state", "to_dafny_map", "to_dafny_str")

    s"""from __future__ import annotations
       |${datetimeImport}
       |from sqlalchemy import select
       |from sqlalchemy.ext.asyncio import AsyncSession
       |
       |from app.dafny_kernel import module_
       |${(entityImports ++ scalarImport).mkString("\n")}
       |from app.services._dafny_adapter import (
       |${adapterNames.map(n => s"    $n,").mkString("\n")}
       |)$datetimeHelpers
       |
       |
       |async def hydrate_state(session: AsyncSession) -> module_.ServiceState:
       |    st = make_state()
       |${hydrateLines.result().mkString("\n")}
       |    return st
       |
       |
       |async def persist_state(session: AsyncSession, st: module_.ServiceState) -> None:
       |${persistLines.result().mkString("\n")}
       |""".stripMargin
