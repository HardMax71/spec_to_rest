package specrest.codegen.python

import specrest.codegen.AdminModel
import specrest.convention.ScalarState
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledService

object AdminRouter:

  def emit(profiled: ProfiledService): String =
    val ir         = profiled.ir
    val entities   = svcEntities(ir)
    val hasScalars = ScalarState.fields(ir).nonEmpty

    val entityImports = entities
      .map(e => s"from app.models.${Naming.toSnakeCase(entName(e))} import ${entName(e)}")
      .mkString("\n")
    val stateImport =
      if hasScalars then "\nfrom app.models.service_state import ServiceState" else ""

    val scalarResets =
      if hasScalars then
        val seeds = ScalarState
          .fieldsWithSeeds(ir)
          .map((sf, seed) => s"${ScalarState.columnName(stfName(sf))}=$seed")
          .mkString(", ")
        s"\n    await session.execute(sa_update(ServiceState).values($seeds))"
      else ""
    val deleteStatements =
      if entities.isEmpty && !hasScalars then "    pass"
      else
        entities
          .map(e => s"    await session.execute(delete(${entName(e)}))")
          .mkString("\n") + scalarResets

    val stateFieldsList = irStateFields(ir)
    val stateProjections =
      if stateFieldsList.isEmpty then "    return {}"
      else
        val projections = stateFieldsList.map(f => f -> AdminModel.projectionFor(f, ir))
        val needsRows = projections.exists:
          case (_, Some(p)) =>
            p.valueShape match
              case AdminModel.ProjectionValue.ScalarStateColumn(_) => false
              case _                                               => true
          case _ => false
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
        val stateRowLine =
          if hasScalars then
            "    state_row = (await session.execute(select(ServiceState))).scalar_one()\n"
          else ""

        val pairs = stateFieldsList.map(f => projectionLine(f, ir, entities))
        val body  = pairs.mkString(",\n")
        s"$rowsLine$stateRowLine    return {\n$body,\n    }"

    val seedEntities = svcTransitions(ir).map(trnEntity).toSet
    val seedTargets  = entities.filter(e => seedEntities.contains(entName(e)))
    val seedSection =
      if seedTargets.isEmpty then ""
      else seedTargets.map(e => seedHandler(e, ir)).mkString("\n", "\n", "")

    s"""from datetime import datetime, date
       |
       |from fastapi import APIRouter, Body, Depends
       |from fastapi.responses import Response
       |from sqlalchemy import delete, select${
        if hasScalars then ", update as sa_update" else ""
      }
       |from sqlalchemy.ext.asyncio import AsyncSession
       |
       |from app.database import get_session
       |from app.security import require_admin
       |${if entityImports.nonEmpty then entityImports else ""}$stateImport
       |
       |router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])
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
       |$deleteStatements
       |    return Response(status_code=204)
       |
       |
       |@router.get("/state")
       |async def get_state(session: AsyncSession = Depends(get_session)) -> dict:
       |$stateProjections$seedSection
       |""".stripMargin

  private def seedHandler(entity: entity_decl, ir: ServiceIRFull): String =
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
        |    payload = dict(payload)
        |$coercion    obj = ${entName(entity)}(**payload)
        |    session.add(obj)
        |    await session.commit()
        |    await session.refresh(obj)
        |    return {"$pkName": obj.$pkName}
        |""".stripMargin

  private def projectionLine(
      f: state_field_decl,
      ir: ServiceIRFull,
      entities: List[entity_decl]
  ): String =
    AdminModel.projectionFor(f, ir) match
      case Some(p) =>
        val key = pyStringLit(stfName(f))
        p.valueShape match
          case AdminModel.ProjectionValue.ScalarStateColumn(col) =>
            s"        $key: state_row.$col"
          case other =>
            val rowsRef =
              if entities.size <= 1 then "rows"
              else s"rows_${Naming.toSnakeCase(p.entityName)}"
            val valueExpr = other match
              case AdminModel.ProjectionValue.PrimitiveField(name) => s"row.$name"
              case _                                               => "_row_to_dict(row)"
            s"        $key: {row.${p.keyFieldName}: $valueExpr for row in $rowsRef}"
      case None =>
        val key = pyStringLit(stfName(f))
        s"        # M5.1: state field '${stfName(f)}' not backed by entity table\n        $key: None"

  private def pyStringLit(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
