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

    val modelImports = (entities
      .map(e => s"from app.models.${Naming.toSnakeCase(entName(e))} import ${entName(e)}") ++
      (if hasScalars then List("from app.models.service_state import ServiceState") else Nil)).sorted
    val firstPartyImports =
      ("from app.database import get_session" :: modelImports :::
        List("from app.security import require_admin")).mkString("\n")

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
        val neededEntityNames = AdminModel.entityBackedProjectionNames(ir)
        val needsRows         = neededEntityNames.nonEmpty
        val rowsLine =
          val seqEntities = irStateFields(ir)
            .flatMap(f => AdminModel.projectionFor(f, ir))
            .collect:
              case p if p.valueShape == AdminModel.ProjectionValue.SeqRows => p.entityName
            .toSet
          def rowsQuery(v: String, e: entity_decl): String =
            if seqEntities.contains(entName(e)) then
              s"""    $v = (
    |        await session.execute(select(${entName(e)}).order_by(${entName(e)}.id))
    |    ).scalars().all()""".stripMargin
            else s"    $v = (await session.execute(select(${entName(e)}))).scalars().all()"
          if entities.size == 1 && needsRows then
            val e = entities.head
            rowsQuery("rows", e) + "\n"
          else if entities.size > 1 && needsRows then
            entities
              .filter(e => neededEntityNames.contains(entName(e)))
              .map(e => rowsQuery(s"rows_${Naming.toSnakeCase(entName(e))}", e))
              .mkString("\n") + "\n"
          else ""
        val stateRowLine =
          if hasScalars then
            "    state_row = (await session.execute(select(ServiceState))).scalar_one()\n"
          else ""

        val pairs = stateFieldsList.map(f => projectionLine(f, ir, entities))
        val body  = pairs.mkString(",\n")
        s"$rowsLine$stateRowLine    return {\n$body,\n    }"

    val seedTargets = AdminModel.seedTargets(ir)
    val seedSection =
      if seedTargets.isEmpty then ""
      else seedTargets.map(e => seedHandler(e, ir)).mkString("\n", "\n", "")

    val sqlalchemyImports = (
      (if deleteStatements.contains("delete(") then List("delete") else Nil) :::
        (if stateProjections.contains("select(") then List("select") else Nil)
    ) match
      case Nil   => ""
      case names => s"\nfrom sqlalchemy import ${names.mkString(", ")}"

    s"""from datetime import date, datetime
       |from typing import Any
       |
       |from fastapi import APIRouter, ${if seedTargets.nonEmpty then "Body, " else ""}Depends
       |from fastapi.responses import Response$sqlalchemyImports${
        if hasScalars then "\nfrom sqlalchemy import update as sa_update" else ""
      }
       |from sqlalchemy.ext.asyncio import AsyncSession
       |
       |$firstPartyImports
       |
       |router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])
       |
       |
       |def _row_to_dict(row: Any) -> dict[str, Any]:
       |    out: dict[str, Any] = {}
       |    for col in row.__table__.columns:
       |        v = getattr(row, col.name)
       |        if isinstance(v, datetime):
       |            # Same canonical wire form as the API's responses; drivers
       |            # hand back naive datetimes for UTC-stored columns. Dates
       |            # and non-UTC offsets serialize unchanged.
       |            iso = v.isoformat()
       |            if iso.endswith("+00:00"):
       |                v = iso.replace("+00:00", "Z")
       |            elif v.tzinfo is None:
       |                v = f"{iso}Z"
       |            else:
       |                v = iso
       |        elif isinstance(v, date):
       |            v = v.isoformat()
       |        out[col.name] = v
       |    return out
       |
       |
       |def _parse_iso(value: Any) -> Any:
       |    if isinstance(value, str):
       |        return datetime.fromisoformat(value)
       |    return value
       |
       |
       |@router.post("/reset", status_code=204)
       |async def reset(session: AsyncSession = Depends(get_session)) -> Response:
       |$deleteStatements
       |    # Committing in dependency teardown can land after the response,
       |    # racing a client's next request against the deletes.
       |    await session.commit()
       |    return Response(status_code=204)
       |
       |
       |@router.get("/state")
       |async def get_state(session: AsyncSession = Depends(get_session)) -> dict[str, Any]:
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
    // Freshness counters (forall k in rel: k < counter) must move past a
    // seeded key or every guarded call on the seeded state 409s.
    val backingFields = svcState(ir)
      .map(stdFields)
      .getOrElse(Nil)
      .filter(f => AdminModel.projectionFor(f, ir).exists(_.entityName == entName(entity)))
    val counters = (backingFields
      .flatMap(f => specrest.convention.ScalarState.freshnessCounters(ir, stfName(f))) :::
      specrest.convention.ScalarState.nestedIdFreshnessCounters(ir, entName(entity))).distinct
    val counterBumps =
      if counters.isEmpty then ""
      else
        val body = counters
          .map { c =>
            val col = specrest.convention.ScalarState.columnName(c)
            s"    if state_row is not None and obj.$pkName >= state_row.$col:\n" +
              s"        state_row.$col = obj.$pkName + 1\n"
          }
          .mkString
        "    state_row = (await session.execute(select(ServiceState))).scalar_one_or_none()\n" +
          body + "    await session.commit()\n"
    s"""|@router.post("/seed/$snake", status_code=201)
        |async def seed_$snake(
        |    payload: dict[str, Any] = Body(...),
        |    session: AsyncSession = Depends(get_session),
        |) -> dict[str, Any]:
        |    payload = dict(payload)
        |$coercion    obj = ${entName(entity)}(**payload)
        |    session.add(obj)
        |    await session.commit()
        |    await session.refresh(obj)
        |$counterBumps    return {"$pkName": obj.$pkName}
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
            other match
              case AdminModel.ProjectionValue.SeqRows =>
                s"        $key: [_row_to_dict(row) for row in $rowsRef]"
              case AdminModel.ProjectionValue.PrimitiveField(name) =>
                s"        $key: {row.${p.keyFieldName}: row.$name for row in $rowsRef}"
              case _ =>
                s"        $key: {row.${p.keyFieldName}: _row_to_dict(row) for row in $rowsRef}"
      case None =>
        val key = pyStringLit(stfName(f))
        s"        # M5.1: state field '${stfName(f)}' not backed by entity table\n        $key: None"

  private def pyStringLit(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
