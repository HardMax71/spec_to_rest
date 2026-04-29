package specrest.testgen

import specrest.convention.Naming
import specrest.ir.EntityDecl
import specrest.ir.ServiceIR
import specrest.ir.StateFieldDecl
import specrest.ir.TypeExpr
import specrest.profile.ProfiledService

object AdminRouter:

  def emit(profiled: ProfiledService): String =
    val ir       = profiled.ir
    val entities = ir.entities

    val entityImports = entities
      .map(e => s"from app.models.${Naming.toSnakeCase(e.name)} import ${e.name}")
      .mkString("\n")

    val deleteStatements =
      if entities.isEmpty then "    pass"
      else
        entities
          .map(e => s"    await session.execute(delete(${e.name}))")
          .mkString("\n")

    val stateFieldsList = ir.state.toList.flatMap(_.fields)
    val stateProjections =
      if stateFieldsList.isEmpty then "    return {}"
      else
        val needsRows = stateFieldsList.exists(f => projectionFor(f, ir).isDefined)
        val rowsLine =
          if entities.size == 1 && needsRows then
            val e = entities.head
            s"    rows = (await session.execute(select(${e.name}))).scalars().all()\n"
          else if entities.size > 1 && needsRows then
            entities
              .map: e =>
                val v = s"rows_${Naming.toSnakeCase(e.name)}"
                s"    $v = (await session.execute(select(${e.name}))).scalars().all()"
              .mkString("\n") + "\n"
          else ""

        val pairs = stateFieldsList.map(f => projectionLine(f, ir, entities))
        val body  = pairs.mkString(",\n")
        s"$rowsLine    return {\n$body,\n    }"

    val seedEntities = ir.transitions.map(_.entityName).toSet
    val seedTargets  = entities.filter(e => seedEntities.contains(e.name))
    val seedSection =
      if seedTargets.isEmpty then ""
      else seedTargets.map(e => seedHandler(e, ir)).mkString("\n", "\n", "")

    s"""import os
       |from datetime import datetime, date
       |
       |from fastapi import APIRouter, Body, Depends, HTTPException
       |from fastapi.responses import Response
       |from sqlalchemy import delete, select
       |from sqlalchemy.ext.asyncio import AsyncSession
       |
       |from app.database import get_session
       |${if entityImports.nonEmpty then entityImports else ""}
       |
       |router = APIRouter(prefix="/__test_admin__", tags=["test-admin"])
       |
       |
       |def _check_enabled() -> None:
       |    if os.environ.get("ENABLE_TEST_ADMIN") != "1":
       |        raise HTTPException(status_code=403, detail="test admin disabled")
       |
       |
       |def _row_to_dict(row) -> dict:
       |    out: dict = {}
       |    for col in row.__table__.columns:
       |        v = getattr(row, col.name)
       |        if isinstance(v, (datetime, date)):
       |            v = v.isoformat()
       |        out[col.name] = v
       |    return out
       |
       |
       |def _parse_iso(value):
       |    if isinstance(value, str):
       |        return datetime.fromisoformat(value)
       |    return value
       |
       |
       |@router.post("/reset", status_code=204)
       |async def reset(session: AsyncSession = Depends(get_session)) -> Response:
       |    _check_enabled()
       |$deleteStatements
       |    return Response(status_code=204)
       |
       |
       |@router.get("/state")
       |async def get_state(session: AsyncSession = Depends(get_session)) -> dict:
       |    _check_enabled()
       |$stateProjections$seedSection
       |""".stripMargin

  private def seedHandler(entity: EntityDecl, ir: ServiceIR): String =
    val snake  = Naming.toSnakeCase(entity.name)
    val pkName = primaryKeyField(entity).getOrElse("id")
    val dtFields = entity.fields.collect:
      case f if isDateTimeType(f.typeExpr, ir, Set.empty) => f.name
    val coercion =
      if dtFields.isEmpty then ""
      else
        val lines = dtFields.map: n =>
          val k = pyStringLit(n)
          s"    if $k in payload and payload[$k] is not None:\n        payload[$k] = _parse_iso(payload[$k])"
        lines.mkString("", "\n", "\n")
    s"""|@router.post("/seed/$snake", status_code=201)
        |async def seed_$snake(
        |    payload: dict = Body(...),
        |    session: AsyncSession = Depends(get_session),
        |) -> dict:
        |    _check_enabled()
        |    payload = dict(payload)
        |$coercion    obj = ${entity.name}(**payload)
        |    session.add(obj)
        |    await session.commit()
        |    await session.refresh(obj)
        |    return {"$pkName": obj.$pkName}
        |""".stripMargin

  private def isDateTimeType(t: TypeExpr, ir: ServiceIR, seen: Set[String]): Boolean =
    t match
      case TypeExpr.NamedType("DateTime", _) => true
      case TypeExpr.OptionType(inner, _)     => isDateTimeType(inner, ir, seen)
      case TypeExpr.NamedType(name, _) if !seen.contains(name) =>
        ir.typeAliases
          .find(_.name == name)
          .exists(alias => isDateTimeType(alias.typeExpr, ir, seen + name))
      case _ => false

  final private case class Projection(
      entityName: String,
      keyFieldName: String,
      valueShape: ProjectionValue
  )

  private enum ProjectionValue:
    case PrimitiveField(fieldName: String)
    case EntityRow

  private def projectionFor(f: StateFieldDecl, ir: ServiceIR): Option[Projection] =
    f.typeExpr match
      case TypeExpr.RelationType(k, _, v, _) =>
        inferRelationProjection(k, v, ir)
      case TypeExpr.NamedType(name, _) if ir.entities.exists(_.name == name) =>
        ir.entities
          .find(_.name == name)
          .flatMap(e =>
            primaryKeyField(e).map(pk => Projection(name, pk, ProjectionValue.EntityRow))
          )
      case _ => None

  private def inferRelationProjection(
      k: TypeExpr,
      v: TypeExpr,
      ir: ServiceIR
  ): Option[Projection] =
    val kName = typeName(k)
    val vName = typeName(v)
    (kName, vName) match
      case (Some(kn), Some(vn)) if ir.entities.exists(_.name == vn) =>
        for
          entity   <- ir.entities.find(_.name == vn)
          keyField <- entity.fields.find(f => typeName(f.typeExpr).contains(kn))
        yield Projection(vn, keyField.name, ProjectionValue.EntityRow)
      case (Some(kn), Some(vn)) =>
        // Both primitives or aliases — find an entity with both fields
        ir.entities
          .find: e =>
            e.fields.exists(f => typeName(f.typeExpr).contains(kn)) &&
              e.fields.exists(f => typeName(f.typeExpr).contains(vn))
          .flatMap: e =>
            for
              keyField <- e.fields.find(f => typeName(f.typeExpr).contains(kn))
              valField <- e.fields.find(f =>
                            typeName(f.typeExpr).contains(vn) && f.name != keyField.name
                          )
            yield Projection(e.name, keyField.name, ProjectionValue.PrimitiveField(valField.name))
      case _ => None

  private def typeName(t: TypeExpr): Option[String] = t match
    case TypeExpr.NamedType(n, _) => Some(n)
    case _                        => None

  private[testgen] def primaryKeyField(e: EntityDecl): Option[String] =
    e.fields.find(_.name == "id").map(_.name).orElse(e.fields.headOption.map(_.name))

  private def projectionLine(
      f: StateFieldDecl,
      ir: ServiceIR,
      entities: List[EntityDecl]
  ): String =
    projectionFor(f, ir) match
      case Some(p) =>
        val rowsRef =
          if entities.size <= 1 then "rows"
          else s"rows_${Naming.toSnakeCase(p.entityName)}"
        val valueExpr = p.valueShape match
          case ProjectionValue.PrimitiveField(name) => s"row.$name"
          case ProjectionValue.EntityRow            => "_row_to_dict(row)"
        val key = pyStringLit(f.name)
        s"        $key: {row.${p.keyFieldName}: $valueExpr for row in $rowsRef}"
      case None =>
        val key = pyStringLit(f.name)
        s"        # M5.1: state field '${f.name}' not backed by entity table\n        $key: None"

  private def pyStringLit(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
