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

  import specrest.codegen.KernelTypes.Kind

  private val ScalarPyTypes = Set("str", "int", "bool", "datetime")

  export specrest.codegen.StatePlan.Plan

  def dafnyName(specName: String): String = specName.replace("_", "__")

  private def baseType(domainType: String): String =
    domainType.replaceAll("\\s*\\|\\s*None$", "").trim

  // Beyond bare scalars the bridge marshals enum-typed fields (string
  // columns, Dafny datatype values) and scalar-element collections (JSON
  // columns, Dafny sets/seqs); the spec type decides, not the column type.
  private def kindFor(profiled: ProfiledService, entityName: String, f: ProfiledField) =
    specrest.codegen.KernelTypes.fieldKind(profiled.ir, entityName, f.fieldName)

  private def kindSupported(
      profiled: ProfiledService,
      k: Option[Kind],
      nullable: Boolean
  ): Boolean = k match
    case Some(Kind.Scalar(b))                => ScalarPyTypes.contains(b)
    case Some(Kind.EnumK(_))                 => true
    case Some(Kind.SetOf(_) | Kind.SeqOf(_)) => !nullable
    case Some(Kind.EntitySetOf(en))          =>
      // One nesting level: the element entity's own fields must all marshal
      // without another entity collection inside.
      !nullable && specrest.codegen.EmitShared.nestedEntity(profiled, en).exists { ne =>
        ne.fields.forall { nf =>
          kindFor(profiled, ne.entityName, nf) match
            case Some(Kind.EntitySetOf(_)) => false
            case k =>
              kindSupported(profiled, k, nf.nullable) ||
              ScalarPyTypes.contains(baseType(nf.domainType))
        }
      }
    case Some(Kind.OptOf(inner)) =>
      // Nullable collections have no bridge conversions on any target yet.
      inner match
        case Kind.SetOf(_) | Kind.SeqOf(_) | Kind.EntitySetOf(_) => false
        case _                                                   => kindSupported(profiled, Some(inner), nullable = false)
    case _ => false

  def plan(profiled: ProfiledService): Either[String, Plan] =
    specrest.codegen.StatePlan.analyze(
      profiled,
      fieldSupported = f =>
        val owner = profiled.entities
          .find(_.fields.exists(_ eq f))
          .map(_.entityName)
        owner.exists(en => kindSupported(profiled, kindFor(profiled, en, f), f.nullable)) ||
        ScalarPyTypes.contains(baseType(f.domainType))
      ,
      keySupported = k => Set("str", "int").contains(baseType(k.domainType)) && !k.nullable,
      seqSupported = true
    )

  def hasState(plan: Plan): Boolean = plan.hasState

  private def toDafnyFieldExpr(kind: Option[Kind], f: ProfiledField, rowRef: String): String =
    val access = s"$rowRef.${f.columnName}"
    // A nullable field resolves to OptOf(inner); optionality rides on the
    // profiled nullable flag, so the arms match on the payload kind.
    kind.map(specrest.codegen.KernelTypes.unwrapOpt) match
      case Some(Kind.EnumK(n)) if !f.nullable =>
        s"enum_to_dafny(\"$n\", $access)"
      case Some(Kind.EnumK(n)) =>
        s"_some_or_none($access, lambda _v: enum_to_dafny(\"$n\", _v))"
      case Some(Kind.SetOf(el)) =>
        s"to_dafny_set(${elemToDafny(el, "_x")} for _x in $access)"
      case Some(Kind.SeqOf(el)) =>
        s"to_dafny_seq([${elemToDafny(el, "_x")} for _x in $access])"
      case Some(Kind.EntitySetOf(en)) =>
        // The JSON column stores element ids; the nested entity's own rows
        // were indexed by id in the hydrate prelude.
        val snake = Naming.toSnakeCase(en)
        s"to_dafny_set(_${snake}_to_dafny(_${snake}_by_id[int(_x)]) for _x in $access)"
      case _ =>
        val conv = baseType(f.domainType) match
          case "str"      => (v: String) => s"to_dafny_str($v)"
          case "datetime" => (v: String) => s"_epoch($v)"
          case "bool"     => (v: String) => s"bool($v)"
          case _          => (v: String) => s"int($v)"
        if f.nullable then s"_some_or_none($access, ${convName(f)})"
        else conv(access)

  private def elemToDafny(el: String, ref: String): String =
    if el == "str" then s"to_dafny_str($ref)" else s"int($ref)"

  private def elemFromDafny(el: String, ref: String): String =
    if el == "str" then s"from_dafny_str($ref)" else s"int($ref)"

  private def fromDafnyFieldExpr(kind: Option[Kind], f: ProfiledField, valueRef: String): String =
    val access = s"$valueRef.${specrest.codegen.EmitShared.pyDafnySelector(f.fieldName)}"
    kind.map(specrest.codegen.KernelTypes.unwrapOpt) match
      case Some(Kind.EnumK(n)) if !f.nullable =>
        s"enum_from_dafny(\"$n\", $access)"
      case Some(Kind.EnumK(n)) =>
        s"_value_or_none($access, lambda _v: enum_from_dafny(\"$n\", _v))"
      case Some(Kind.SetOf(el)) =>
        // Sets serialize sorted so the JSON column is deterministic.
        s"sorted(${elemFromDafny(el, "_x")} for _x in $access)"
      case Some(Kind.SeqOf(el)) =>
        s"[${elemFromDafny(el, "_x")} for _x in $access]"
      case Some(Kind.EntitySetOf(_)) =>
        // The column keeps the id-list shape; the element rows persist in
        // their own table pass.
        s"sorted(int(_x.${specrest.codegen.EmitShared.pyDafnySelector("id")}) for _x in $access)"
      case _ =>
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

    // Element entities of Set[Entity] fields, paired with the relations that
    // embed them: hydration joins their rows in by id, persistence upserts
    // them from the post-state sets.
    val nestedByName: Map[String, ProfiledEntity] =
      (for
        r <- planned.relations
        f <- r.entity.fields
        en <- kindFor(profiled, r.entity.entityName, f).toList.collect {
                case Kind.EntitySetOf(en) => en
              }
        ne <- specrest.codegen.EmitShared.nestedEntity(profiled, en)
      yield ne.entityName -> ne).toMap
    val nestedEntities = nestedByName.values.toList.sortBy(_.entityName)
    def nestedFieldsOf(e: ProfiledEntity): List[(ProfiledField, ProfiledEntity)] =
      e.fields.flatMap(f =>
        kindFor(profiled, e.entityName, f)
          .collect { case Kind.EntitySetOf(en) => en }
          .flatMap(nestedByName.get)
          .map(f -> _)
      )

    val entityImports =
      ((planned.relations.map(_.entity) ++ nestedEntities)
        .distinctBy(_.entityName)
        .map(e =>
          s"from app.models.${Naming.toSnakeCase(e.entityName)} import ${e.modelClassName}"
        ) :::
        (if planned.scalars.isEmpty then Nil
         else List("from app.models.service_state import ServiceState as _ServiceStateRow"))).sorted
    val scalarImport = Nil

    val needsDatetime = (planned.relations.map(_.entity) ++ nestedEntities)
      .exists(_.fields.exists(f => baseType(f.domainType) == "datetime"))

    val rowsVar = (e: ProfiledEntity) => s"${Naming.toSnakeCase(e.entityName)}_rows"

    val seqRelations = planned.relations.filter(_.isSeq)
    val hydrateLines = List.newBuilder[String]
    for e <- nestedEntities do
      val snake = Naming.toSnakeCase(e.entityName)
      hydrateLines += s"    ${snake}_rows = (await session.execute(select(${e.modelClassName}))).scalars().all()"
      hydrateLines += s"    _${snake}_by_id = {int(r.id): r for r in ${snake}_rows}"
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
            hydrateLines += s"            ${toDafnyFieldExpr(kindFor(profiled, r.entity.entityName, f), f, "r")},"
          hydrateLines += "        )"
          hydrateLines += s"        for r in ${rows}_ordered"
          hydrateLines += "    ])"
        case (Some(key), _) =>
          hydrateLines += s"    st.${dafnyName(r.stateField)} = to_dafny_map({"
          r.valueField match
            case Some(vf) =>
              hydrateLines += s"        ${keyToDafny(key, "r")}: ${toDafnyFieldExpr(kindFor(profiled, r.entity.entityName, vf), vf, "r")}"
            case None =>
              hydrateLines += s"        ${keyToDafny(key, "r")}: module_.${r.entity.entityName}_${r.entity.entityName}("
              for f <- r.entity.fields do
                hydrateLines += s"            ${toDafnyFieldExpr(kindFor(profiled, r.entity.entityName, f), f, "r")},"
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
        persistLines += s"                ${f.columnName}=${fromDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, s"${snake}_value")},"
      persistLines += "            ))"
      persistLines += "        else:"
      for f <- e.fields if f.fieldName != key.fieldName do
        persistLines += s"            ${snake}_row.${f.columnName} = ${fromDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, s"${snake}_value")}"
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
        persistLines += s"            ${f.columnName}=${fromDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, s"${snake}_value")},"
      persistLines += "        ))"
    for ne <- nestedEntities do
      val snake = Naming.toSnakeCase(ne.entityName)
      val idSel = specrest.codegen.EmitShared.pyDafnySelector("id")
      val owners =
        for
          r      <- planned.relations
          (f, e) <- nestedFieldsOf(r.entity) if e.entityName == ne.entityName
        yield (r, f)
      persistLines += s"    _${snake}_post: dict[int, Any] = {}"
      for (r, f) <- owners do
        val values =
          if r.isSeq then s"from_dafny_seq(st.${dafnyName(r.stateField)})"
          else s"from_dafny_map(st.${dafnyName(r.stateField)}).values()"
        persistLines += s"    for _owner in $values:"
        persistLines += s"        for _x in _owner.${specrest.codegen.EmitShared.pyDafnySelector(f.fieldName)}:"
        persistLines += s"            _${snake}_post[int(_x.$idSel)] = _x"
      persistLines += s"    _${snake}_rows = {"
      persistLines += "        int(r.id): r"
      persistLines += s"        for r in (await session.execute(select(${ne.modelClassName}))).scalars().all()"
      persistLines += "    }"
      persistLines += s"    for _${snake}_key, _${snake}_value in _${snake}_post.items():"
      persistLines += s"        _${snake}_row = _${snake}_rows.get(_${snake}_key)"
      persistLines += s"        if _${snake}_row is None:"
      persistLines += s"            session.add(${ne.modelClassName}("
      for f <- ne.fields do
        persistLines += s"                ${f.columnName}=${fromDafnyFieldExpr(kindFor(profiled, ne.entityName, f), f, s"_${snake}_value")},"
      persistLines += "            ))"
      persistLines += "        else:"
      for f <- ne.fields if f.fieldName != "id" do
        persistLines += s"            _${snake}_row.${f.columnName} = ${fromDafnyFieldExpr(kindFor(profiled, ne.entityName, f), f, s"_${snake}_value")}"
      persistLines += s"    for _${snake}_key, _${snake}_row in _${snake}_rows.items():"
      persistLines += s"        if _${snake}_key not in _${snake}_post:"
      persistLines += s"            await session.delete(_${snake}_row)"
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

    val nestedHelpers =
      nestedEntities.map { ne =>
        val snake = Naming.toSnakeCase(ne.entityName)
        val ctorArgs = ne.fields
          .map(f =>
            s"        ${toDafnyFieldExpr(kindFor(profiled, ne.entityName, f), f, "r")},"
          )
          .mkString("\n")
        s"""|
            |
            |
            |def _${snake}_to_dafny(r: Any) -> Any:
            |    return module_.${ne.entityName}_${ne.entityName}(
            |$ctorArgs
            |    )""".stripMargin
      }.mkString

    val datetimeImport =
      (if needsDatetime then "\nfrom datetime import datetime, timezone" else "") match
        case dt if needsDatetime || needsOptional || nestedEntities.nonEmpty =>
          s"$dt\nfrom typing import Any\n"
        case dt => s"$dt\n"
    val bridgedEntities = planned.relations.map(_.entity) ++ nestedEntities
    val usesEnum = bridgedEntities.exists(e =>
      e.fields.exists(f =>
        kindFor(profiled, e.entityName, f).exists {
          case Kind.EnumK(_) | Kind.OptOf(Kind.EnumK(_)) => true
          case _                                         => false
        }
      )
    )
    val usesSet = bridgedEntities.exists(e =>
      e.fields.exists(f =>
        kindFor(profiled, e.entityName, f).exists {
          case Kind.SetOf(_) | Kind.EntitySetOf(_) => true
          case _                                   => false
        }
      )
    )
    val usesSeqField = bridgedEntities.exists(e =>
      e.fields.exists(f =>
        kindFor(profiled, e.entityName, f).exists {
          case Kind.SeqOf(_) => true
          case _             => false
        }
      )
    )
    val adapterNames =
      (List("from_dafny_map", "from_dafny_str", "make_state", "to_dafny_map", "to_dafny_str") :::
        (if seqRelations.nonEmpty || usesSeqField then List("from_dafny_seq", "to_dafny_seq")
         else Nil) :::
        (if usesEnum then List("enum_from_dafny", "enum_to_dafny") else Nil) :::
        (if usesSet then List("to_dafny_set") else Nil)).sorted

    s"""from __future__ import annotations
       |${datetimeImport}
       |from sqlalchemy import select
       |from sqlalchemy.ext.asyncio import AsyncSession
       |
       |from app.dafny_kernel import module_
       |${(entityImports ++ scalarImport).mkString("\n")}
       |from app.services._dafny_adapter import (
       |${adapterNames.map(n => s"    $n,").mkString("\n")}
       |)$optionalHelpers$datetimeHelpers$nestedHelpers
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
