package specrest.codegen.python

import specrest.ir.Naming
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

  export specrest.codegen.StatePlan.Plan

  def dafnyName(specName: String): String = specName.replace("_", "__")

  private def baseType(domainType: String): String =
    domainType.replaceAll("\\s*\\|\\s*None$", "").trim

  def plan(profiled: ProfiledService): Either[String, Plan] =
    specrest.codegen.StatePlan.analyze(
      profiled,
      fieldSupported = f => ScalarPyTypes.contains(baseType(f.domainType)),
      keySupported = k => Set("str", "int").contains(baseType(k.domainType)) && !k.nullable
    )

  def hasState(plan: Plan): Boolean = plan.hasState

  private def toDafnyFieldExpr(f: ProfiledField, rowRef: String): String =
    val access = s"$rowRef.${f.columnName}"
    val conv = baseType(f.domainType) match
      case "str"      => (v: String) => s"to_dafny_str($v)"
      case "datetime" => (v: String) => s"_epoch($v)"
      case "bool"     => (v: String) => s"bool($v)"
      case _          => (v: String) => s"int($v)"
    if f.nullable then s"_some_or_none($access, ${convName(f)})"
    else conv(access)

  private def fromDafnyFieldExpr(f: ProfiledField, valueRef: String): String =
    val access = s"$valueRef.${specrest.codegen.EmitShared.pyDafnySelector(f.fieldName)}"
    def conv(v: String): String = baseType(f.domainType) match
      case "str"      => s"from_dafny_str($v)"
      case "datetime" => s"_from_epoch($v)"
      case "bool"     => s"bool($v)"
      case _          => s"int($v)"
    if f.nullable then s"_value_or_none($access, ${convBackName(f)})"
    else conv(access)

  private def convName(f: ProfiledField): String = baseType(f.domainType) match
    case "str"      => "to_dafny_str"
    case "datetime" => "_epoch"
    case "bool"     => "bool"
    case _          => "int"

  private def convBackName(f: ProfiledField): String = baseType(f.domainType) match
    case "str"      => "from_dafny_str"
    case "datetime" => "_from_epoch"
    case "bool"     => "bool"
    case _          => "int"

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
      case Left(_)  => specrest.codegen.StatePlan.Plan(Nil, Nil)

    val entityImports =
      (planned.relations
        .map(_.entity)
        .distinctBy(_.entityName)
        .map(e =>
          s"from app.models.${Naming.toSnakeCase(e.entityName)} import ${e.modelClassName}"
        ) :::
        (if planned.scalars.isEmpty then Nil
         else List("from app.models.service_state import ServiceState as _ServiceStateRow"))).sorted
    val scalarImport = Nil

    val needsDatetime = planned.relations
      .exists(_.entity.fields.exists(f => baseType(f.domainType) == "datetime"))

    val rowsVar = (e: ProfiledEntity) => s"${Naming.toSnakeCase(e.entityName)}_rows"

    val seqRelations = planned.relations.filter(_.isSeq)
    val hydrateLines = List.newBuilder[String]
    for e <- planned.relations.filterNot(_.isSeq).map(_.entity).distinctBy(_.entityName) do
      hydrateLines += s"    ${rowsVar(e)} = (await session.execute(select(${e.modelClassName}))).scalars().all()"
    for r <- planned.relations do
      val rows = rowsVar(r.entity)
      (r.keyField, r.isSeq) match
        case (_, true) =>
          // Seq-valued state: rows ordered by the serial pk are the seq.
          hydrateLines += s"    ${rows}_ordered = ("
          hydrateLines += s"        await session.execute(select(${r.entity.modelClassName}).order_by(${r.entity.modelClassName}.id))"
          hydrateLines += "    ).scalars().all()"
          hydrateLines += s"    st.${dafnyName(r.stateField)} = to_dafny_seq(["
          hydrateLines += s"        module_.${r.entity.entityName}_${r.entity.entityName}("
          for f <- r.entity.fields do
            hydrateLines += s"            ${toDafnyFieldExpr(f, "r")},"
          hydrateLines += "        )"
          hydrateLines += s"        for r in ${rows}_ordered"
          hydrateLines += "    ])"
        case (Some(key), _) =>
          hydrateLines += s"    st.${dafnyName(r.stateField)} = to_dafny_map({"
          r.valueField match
            case Some(vf) =>
              hydrateLines += s"        ${keyToDafny(key, "r")}: ${toDafnyFieldExpr(vf, "r")}"
            case None =>
              hydrateLines += s"        ${keyToDafny(key, "r")}: module_.${r.entity.entityName}_${r.entity.entityName}("
              for f <- r.entity.fields do
                hydrateLines += s"            ${toDafnyFieldExpr(f, "r")},"
              hydrateLines += "        )"
          hydrateLines += s"        for r in $rows"
          hydrateLines += "    })"
        case (None, false) => ()
    if planned.scalars.nonEmpty then
      hydrateLines += "    scalar_row = ("
      hydrateLines += "        await session.execute(select(_ServiceStateRow))"
      hydrateLines += "    ).scalar_one_or_none()"
      hydrateLines += "    if scalar_row is not None:"
      for sc <- planned.scalars do
        hydrateLines += s"        st.${dafnyName(sc.stateField)} = int(scalar_row.${sc.columnName})"

    val persistLines = List.newBuilder[String]
    for (r, key) <- planned.entityRowRelations.flatMap(r => r.keyField.map(r -> _)) do
      val e     = r.entity
      val snake = Naming.toSnakeCase(e.entityName)
      persistLines += s"    ${snake}_post = {"
      persistLines += s"        ${keyFromDafny(key, "k")}: v"
      persistLines += s"        for k, v in from_dafny_map(st.${dafnyName(r.stateField)}).items()"
      persistLines += "    }"
      persistLines += s"    ${snake}_rows = {"
      persistLines += s"        r.${key.columnName}: r"
      persistLines += s"        for r in (await session.execute(select(${e.modelClassName}))).scalars().all()"
      persistLines += "    }"
      persistLines += s"    for ${snake}_key, ${snake}_value in ${snake}_post.items():"
      persistLines += s"        ${snake}_row = ${snake}_rows.get(${snake}_key)"
      persistLines += s"        if ${snake}_row is None:"
      persistLines += s"            session.add(${e.modelClassName}("
      for f <- e.fields do
        persistLines += s"                ${f.columnName}=${fromDafnyFieldExpr(f, s"${snake}_value")},"
      persistLines += "            ))"
      persistLines += "        else:"
      for f <- e.fields if f.fieldName != key.fieldName do
        persistLines += s"            ${snake}_row.${f.columnName} = ${fromDafnyFieldExpr(f, s"${snake}_value")}"
      persistLines += s"    for ${snake}_key, ${snake}_row in ${snake}_rows.items():"
      persistLines += s"        if ${snake}_key not in ${snake}_post:"
      persistLines += s"            await session.delete(${snake}_row)"
    for r <- seqRelations do
      val e = r.entity
      // Reinsert in seq order: the serial pk reassigns, which nothing
      // observes (the seq projection orders by it and exposes no id).
      val snake = Naming.toSnakeCase(e.entityName)
      persistLines += s"    for ${snake}_old in (await session.execute(select(${e.modelClassName}))).scalars().all():"
      persistLines += s"        await session.delete(${snake}_old)"
      persistLines += "    await session.flush()"
      persistLines += s"    for ${snake}_value in from_dafny_seq(st.${dafnyName(r.stateField)}):"
      persistLines += s"        session.add(${e.modelClassName}("
      for f <- e.fields do
        persistLines += s"            ${f.columnName}=${fromDafnyFieldExpr(f, s"${snake}_value")},"
      persistLines += "        ))"
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

    val needsOptional = planned.relations.exists(_.entity.fields.exists(_.nullable))
    val optionalHelpers =
      if !needsOptional then ""
      else
        """|
           |
           |
           |def _some_or_none(value: Any, conv: Any) -> Any:
           |    return module_.Option_Some(conv(value)) if value is not None else module_.Option_None()
           |
           |
           |def _value_or_none(value: Any, conv: Any) -> Any:
           |    return conv(value.value) if isinstance(value, module_.Option_Some) else None""".stripMargin
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
           |    # Columns are naive TIMESTAMPs; asyncpg and aiomysql reject aware
           |    # values, so persist writes naive UTC.
           |    return datetime.fromtimestamp(int(value), tz=timezone.utc).replace(tzinfo=None)""".stripMargin

    val datetimeImport =
      (if needsDatetime then "\nfrom datetime import datetime, timezone" else "") match
        case dt if needsDatetime || needsOptional => s"$dt\nfrom typing import Any\n"
        case dt                                   => s"$dt\n"
    val adapterNames =
      (List("from_dafny_map", "from_dafny_str", "make_state", "to_dafny_map", "to_dafny_str") :::
        (if seqRelations.nonEmpty then List("from_dafny_seq", "to_dafny_seq") else Nil)).sorted

    s"""from __future__ import annotations
       |${datetimeImport}
       |from sqlalchemy import select
       |from sqlalchemy.ext.asyncio import AsyncSession
       |
       |from app.dafny_kernel import module_
       |${(entityImports ++ scalarImport).mkString("\n")}
       |from app.services._dafny_adapter import (
       |${adapterNames.map(n => s"    $n,").mkString("\n")}
       |)$optionalHelpers$datetimeHelpers
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
