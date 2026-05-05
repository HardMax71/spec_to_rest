package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

import specrest.convention.Naming
import specrest.profile.ProfiledService

object AdminRouter:

  def emit(profiled: ProfiledService): String =
    val ir       = profiled.ir
    val entities = ir.c.collect { case e: EntityDeclFull => e }

    val entityImports = entities
      .map(e => s"from app.models.${Naming.toSnakeCase(e.a)} import ${e.a}")
      .mkString("\n")

    val deleteStatements =
      if entities.isEmpty then "    pass"
      else
        entities
          .map(e => s"    await session.execute(delete(${e.a}))")
          .mkString("\n")

    val stateFieldsList = ir.f.toList.flatMap {
      case StateDeclFull(fs, _) => fs.collect { case sf: StateFieldDeclFull => sf }
    }
    val stateProjections =
      if stateFieldsList.isEmpty then "    return {}"
      else
        val needsRows = stateFieldsList.exists(f => projectionFor(f, ir).isDefined)
        val rowsLine =
          if entities.size == 1 && needsRows then
            val e = entities.head
            s"    rows = (await session.execute(select(${e.a}))).scalars().all()\n"
          else if entities.size > 1 && needsRows then
            entities
              .map: e =>
                val v = s"rows_${Naming.toSnakeCase(e.a)}"
                s"    $v = (await session.execute(select(${e.a}))).scalars().all()"
              .mkString("\n") + "\n"
          else ""

        val pairs = stateFieldsList.map(f => projectionLine(f, ir, entities))
        val body  = pairs.mkString(",\n")
        s"$rowsLine    return {\n$body,\n    }"

    val seedEntities = ir.h.collect { case TransitionDeclFull(_, en, _, _, _) => en }.toSet
    val seedTargets  = entities.filter(e => seedEntities.contains(e.a))
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

  private def seedHandler(entity: EntityDeclFull, ir: ServiceIRFull): String =
    val snake  = Naming.toSnakeCase(entity.a)
    val pkName = primaryKeyField(entity).getOrElse("id")
    val dtFields = entity.c.collect:
      case FieldDeclFull(n, t, _, _) if isDateTimeType(t, ir, Set.empty) => n
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
        |$coercion    obj = ${entity.a}(**payload)
        |    session.add(obj)
        |    await session.commit()
        |    await session.refresh(obj)
        |    return {"$pkName": obj.$pkName}
        |""".stripMargin

  private[testgen] def isDateTimeType(
      t: type_expr_full,
      ir: ServiceIRFull,
      seen: Set[String]
  ): Boolean =
    t match
      case NamedTypeF("DateTime", _) => true
      case OptionTypeF(inner, _)     => isDateTimeType(inner, ir, seen)
      case NamedTypeF(name, _) if !seen.contains(name) =>
        ir.e.collect { case a: TypeAliasDeclFull => a }
          .find(_.a == name)
          .exists(alias => isDateTimeType(alias.b, ir, seen + name))
      case _ => false

  private[testgen] def isNumericType(
      t: type_expr_full,
      ir: ServiceIRFull,
      seen: Set[String]
  ): Boolean =
    t match
      case NamedTypeF(n, _) if Set("Int", "Long", "Float", "Double").contains(n) => true
      case OptionTypeF(inner, _)                                                 => isNumericType(inner, ir, seen)
      case NamedTypeF(name, _) if !seen.contains(name) =>
        ir.e.collect { case a: TypeAliasDeclFull => a }
          .find(_.a == name)
          .exists(alias => isNumericType(alias.b, ir, seen + name))
      case _ => false

  private[testgen] def isOptionalType(
      t: type_expr_full,
      ir: ServiceIRFull,
      seen: Set[String]
  ): Boolean =
    t match
      case OptionTypeF(_, _) => true
      case NamedTypeF(name, _) if !seen.contains(name) =>
        ir.e.collect { case a: TypeAliasDeclFull => a }
          .find(_.a == name)
          .exists(alias => isOptionalType(alias.b, ir, seen + name))
      case _ => false

  final private case class Projection(
      entityName: String,
      keyFieldName: String,
      valueShape: ProjectionValue
  )

  private enum ProjectionValue derives CanEqual:
    case PrimitiveField(fieldName: String)
    case EntityRow

  private def projectionFor(f: StateFieldDeclFull, ir: ServiceIRFull): Option[Projection] =
    f.b match
      case RelationTypeF(k, _, v, _) =>
        inferRelationProjection(k, v, ir)
      case NamedTypeF(name, _) =>
        ir.c.collect { case e: EntityDeclFull if e.a == name => e }.headOption
          .flatMap(e =>
            primaryKeyField(e).map(pk => Projection(name, pk, ProjectionValue.EntityRow))
          )
      case _ => None

  private def inferRelationProjection(
      k: type_expr_full,
      v: type_expr_full,
      ir: ServiceIRFull
  ): Option[Projection] =
    val kName = typeName(k)
    val vName = typeName(v)
    val entityList = ir.c.collect { case e: EntityDeclFull => e }
    (kName, vName) match
      case (Some(kn), Some(vn)) if entityList.exists(_.a == vn) =>
        for
          entity   <- entityList.find(_.a == vn)
          keyField <- entity.c.collect { case f: FieldDeclFull => f }.find(f => typeName(f.b).contains(kn))
        yield Projection(vn, keyField.a, ProjectionValue.EntityRow)
      case (Some(kn), Some(vn)) =>
        entityList
          .find: e =>
            val ef = e.c.collect { case f: FieldDeclFull => f }
            ef.exists(f => typeName(f.b).contains(kn)) &&
              ef.exists(f => typeName(f.b).contains(vn))
          .flatMap: e =>
            val ef = e.c.collect { case f: FieldDeclFull => f }
            for
              keyField <- ef.find(f => typeName(f.b).contains(kn))
              valField <- ef.find(f =>
                            typeName(f.b).contains(vn) && f.a != keyField.a
                          )
            yield Projection(e.a, keyField.a, ProjectionValue.PrimitiveField(valField.a))
      case _ => None

  private def typeName(t: type_expr_full): Option[String] = t match
    case NamedTypeF(n, _) => Some(n)
    case _                => None

  private[testgen] def primaryKeyField(e: EntityDeclFull): Option[String] =
    val fs = e.c.collect { case f: FieldDeclFull => f }
    fs.find(_.a == "id").map(_.a).orElse(fs.headOption.map(_.a))

  private def projectionLine(
      f: StateFieldDeclFull,
      ir: ServiceIRFull,
      entities: List[EntityDeclFull]
  ): String =
    projectionFor(f, ir) match
      case Some(p) =>
        val rowsRef =
          if entities.size <= 1 then "rows"
          else s"rows_${Naming.toSnakeCase(p.entityName)}"
        val valueExpr = p.valueShape match
          case ProjectionValue.PrimitiveField(name) => s"row.$name"
          case ProjectionValue.EntityRow            => "_row_to_dict(row)"
        val key = pyStringLit(f.a)
        s"        $key: {row.${p.keyFieldName}: $valueExpr for row in $rowsRef}"
      case None =>
        val key = pyStringLit(f.a)
        s"        # M5.1: state field '${f.a}' not backed by entity table\n        $key: None"

  private def pyStringLit(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
