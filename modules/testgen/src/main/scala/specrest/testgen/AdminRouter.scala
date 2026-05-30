package specrest.testgen

import specrest.convention.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledService

object AdminRouter:

  def emit(profiled: ProfiledService): String =
    val ir       = profiled.ir
    val entities = svcEntities(ir)

    val entityImports = entities
      .map(e => s"from app.models.${Naming.toSnakeCase(entName(e))} import ${entName(e)}")
      .mkString("\n")

    val deleteStatements =
      if entities.isEmpty then "    pass"
      else
        entities
          .map(e => s"    await session.execute(delete(${entName(e)}))")
          .mkString("\n")

    val stateFieldsList = svcState(ir).toList.flatMap(stdFields)
    val stateProjections =
      if stateFieldsList.isEmpty then "    return {}"
      else
        val needsRows = stateFieldsList.exists(f => AdminModel.projectionFor(f, ir).isDefined)
        val rowsLine =
          if entities.size == 1 && needsRows then
            val e = entities.head
            s"    rows = (await session.execute(select(${entName(e)}))).scalars().all()\n"
          else if entities.size > 1 && needsRows then
            entities
              .map: e =>
                val v = s"rows_${Naming.toSnakeCase(entName(e))}"
                s"    $v = (await session.execute(select(${entName(e)}))).scalars().all()"
              .mkString("\n") + "\n"
          else ""

        val pairs = stateFieldsList.map(f => projectionLine(f, ir, entities))
        val body  = pairs.mkString(",\n")
        s"$rowsLine    return {\n$body,\n    }"

    val seedEntities = svcTransitions(ir).map(trnEntity).toSet
    val seedTargets  = entities.filter(e => seedEntities.contains(entName(e)))
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

  private def seedHandler(entity: entity_decl_full, ir: ServiceIRFull): String =
    val snake  = Naming.toSnakeCase(entName(entity))
    val pkName = AdminModel.primaryKeyField(entity).getOrElse("id")
    val dtFields = entFields(entity)
      .filter(fld => isDateTimeType(svcTypeAliases(ir), fldType(fld)))
      .map(fldName)
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
        |$coercion    obj = ${entName(entity)}(**payload)
        |    session.add(obj)
        |    await session.commit()
        |    await session.refresh(obj)
        |    return {"$pkName": obj.$pkName}
        |""".stripMargin

  private def projectionLine(
      f: state_field_decl_full,
      ir: ServiceIRFull,
      entities: List[entity_decl_full]
  ): String =
    AdminModel.projectionFor(f, ir) match
      case Some(p) =>
        val rowsRef =
          if entities.size <= 1 then "rows"
          else s"rows_${Naming.toSnakeCase(p.entityName)}"
        val valueExpr = p.valueShape match
          case AdminModel.ProjectionValue.PrimitiveField(name) => s"row.$name"
          case AdminModel.ProjectionValue.EntityRow            => "_row_to_dict(row)"
        val key = pyStringLit(stfName(f))
        s"        $key: {row.${p.keyFieldName}: $valueExpr for row in $rowsRef}"
      case None =>
        val key = pyStringLit(stfName(f))
        s"        # M5.1: state field '${stfName(f)}' not backed by entity table\n        $key: None"

  private def pyStringLit(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
