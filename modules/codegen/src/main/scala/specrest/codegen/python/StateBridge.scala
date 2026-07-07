package specrest.codegen.python

import specrest.codegen.StatePlan
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

  // Renders `<prefix>{e1, e2, ...}` on one line when it fits the generated
  // project's 100-column ruff limit, one entry per line otherwise.
  def scopeAssign(prefix: String, entries: List[String]): String =
    val single = s"$prefix{${entries.mkString(", ")}}"
    if single.length <= 100 then single
    else
      val pad = prefix.takeWhile(_ == ' ')
      ((s"$prefix{" :: entries.map(e => s"$pad    $e,")) ::: List(s"$pad}")).mkString("\n")

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

    val seqRelations = planned.relations.filter(_.isSeq)
    val fullDefault =
      planned.relations.map(_.stateField).distinct.sorted.map(f => s"\"$f\": (\"full\",)")

    // The fields a relation's hydrate block actually converts: a value
    // projection reads only its value column, everything else the whole row.
    def hydratedFields(r: StatePlan.RelationPlan): List[ProfiledField] =
      r.valueField.map(List(_)).getOrElse(r.entity.fields)
    def nestedRefs(r: StatePlan.RelationPlan): List[(ProfiledField, ProfiledEntity)] =
      hydratedFields(r).flatMap(f =>
        kindFor(profiled, r.entity.entityName, f)
          .collect { case Kind.EntitySetOf(en) => en }
          .flatMap(nestedByName.get)
          .map(f -> _)
      )
    def relRows(r: StatePlan.RelationPlan): String = s"${r.stateField}_rows"
    def guardLines(r: StatePlan.RelationPlan): List[String] =
      if r.isSeq then List(s"    if scope.get(\"${r.stateField}\") is not None:")
      else List(s"    sel = scope.get(\"${r.stateField}\")", "    if sel is not None:")
    def loadLines(r: StatePlan.RelationPlan): List[String] =
      val model = r.entity.modelClassName
      val q     = s"${r.stateField}_q"
      val rows  = relRows(r)
      if r.isSeq then
        // Seq-valued state: rows ordered by the serial pk are the seq. A seq
        // present in scope is always ("full",); keyed loads never apply.
        List(
          s"        $q = select($model).order_by($model.id)",
          s"        $rows = (await session.execute($q)).scalars().all()"
        )
      else
        r.keyField.toList.flatMap { key =>
          List(
            s"        $q = select($model)",
            "        if sel[0] == \"keys\":",
            s"            $q = $q.where($model.${key.columnName}.in_(list(sel[1])))",
            s"        $rows = (await session.execute($q)).scalars().all()"
          )
        }
    def constructLines(r: StatePlan.RelationPlan): List[String] =
      val e    = r.entity
      val p    = "        "
      val rows = relRows(r)
      (r.keyField, r.isSeq) match
        case (_, true) =>
          List(
            s"${p}st.${dafnyName(r.stateField)} = to_dafny_seq([",
            s"$p    module_.${e.entityName}_${e.entityName}("
          )
            ::: e.fields.map(f =>
              s"$p        ${toDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, "r")},"
            )
            ::: List(s"$p    )", s"$p    for r in $rows", s"$p])")
        case (Some(key), _) =>
          r.valueField match
            case Some(vf) =>
              List(
                s"${p}st.${dafnyName(r.stateField)} = to_dafny_map({",
                s"$p    ${keyToDafny(key, "r")}: ${toDafnyFieldExpr(kindFor(profiled, e.entityName, vf), vf, "r")}",
                s"$p    for r in $rows",
                s"$p})"
              )
            case None =>
              List(
                s"${p}st.${dafnyName(r.stateField)} = to_dafny_map({",
                s"$p    ${keyToDafny(key, "r")}: module_.${e.entityName}_${e.entityName}("
              )
                ::: e.fields.map(f =>
                  s"$p        ${toDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, "r")},"
                )
                ::: List(s"$p    )", s"$p    for r in $rows", s"$p})")
        case (None, false) => Nil

    val hydrateLines = List.newBuilder[String]
    hydrateLines += "    if scope is None:"
    hydrateLines ++= scopeAssign("        scope = ", fullDefault).linesIterator
    for ne <- nestedEntities do
      hydrateLines += s"    _${Naming.toSnakeCase(ne.entityName)}_ids: set[int] = set()"
    // Relations embedding nested entities load first so the id union across
    // their hydrated rows can key the nested selects; their Dafny values
    // construct after _<nested>_by_id exists.
    val nestedOwners = planned.relations.filter(r => nestedRefs(r).nonEmpty)
    for r <- nestedOwners do
      hydrateLines ++= guardLines(r)
      hydrateLines ++= loadLines(r)
      for (f, ne) <- nestedRefs(r) do
        val ids = s"_${Naming.toSnakeCase(ne.entityName)}_ids"
        hydrateLines += s"        $ids.update(int(_x) for _r in ${relRows(r)} for _x in _r.${f.columnName})"
    for ne <- nestedEntities do
      val snake = Naming.toSnakeCase(ne.entityName)
      val model = ne.modelClassName
      hydrateLines += s"    ${snake}_rows: list[$model] = []"
      hydrateLines += s"    if _${snake}_ids:"
      hydrateLines += s"        _${snake}_q = select($model).where($model.id.in_(list(_${snake}_ids)))"
      hydrateLines += s"        ${snake}_rows = list((await session.execute(_${snake}_q)).scalars().all())"
      hydrateLines += s"    _${snake}_by_id = {int(r.id): r for r in ${snake}_rows}"
    for r <- planned.relations do
      if nestedRefs(r).nonEmpty then
        hydrateLines += s"    if scope.get(\"${r.stateField}\") is not None:"
        hydrateLines ++= constructLines(r)
      else
        hydrateLines ++= guardLines(r)
        hydrateLines ++= loadLines(r)
        hydrateLines ++= constructLines(r)
    if planned.scalars.nonEmpty then
      hydrateLines += "    scalar_row = ("
      hydrateLines += "        await session.execute(select(_ServiceStateRow))"
      hydrateLines += "    ).scalar_one_or_none()"
      hydrateLines += "    if scalar_row is not None:"
      for sc <- planned.scalars do
        hydrateLines += s"        st.${dafnyName(sc.stateField)} = int(scalar_row.${sc.columnName})"

    val persistLines = List.newBuilder[String]
    persistLines += "    if scope is None:"
    persistLines ++= scopeAssign("        scope = ", fullDefault).linesIterator
    for ne <- nestedEntities do
      persistLines += s"    _${Naming.toSnakeCase(ne.entityName)}_pre_ids: set[int] = set()"
    for (r, key) <- planned.entityRowRelations.flatMap(r => r.keyField.map(r -> _)) do
      val e     = r.entity
      val snake = Naming.toSnakeCase(e.entityName)
      val model = e.modelClassName
      persistLines += s"    sel = scope.get(\"${r.stateField}\")"
      persistLines += "    if sel is not None:"
      persistLines += s"        ${snake}_post = {"
      persistLines += s"            ${keyFromDafny(key, "k")}: v"
      persistLines += s"            for k, v in from_dafny_map(st.${dafnyName(r.stateField)}).items()"
      persistLines += "        }"
      // A keys scope confines the existing-rows scan to the hydrated keys, so
      // rows the request never loaded cannot be judged stale; an empty key
      // list loads no rows, upserts the whole post map, and deletes nothing.
      persistLines += s"        ${snake}_q = select($model)"
      persistLines += "        if sel[0] == \"keys\":"
      persistLines += s"            ${snake}_q = ${snake}_q.where($model.${key.columnName}.in_(list(sel[1])))"
      persistLines += s"        ${snake}_rows = {"
      persistLines += s"            r.${key.columnName}: r"
      persistLines += s"            for r in (await session.execute(${snake}_q)).scalars().all()"
      persistLines += "        }"
      // Nested ids collect before the upsert loop mutates these rows: the
      // nested delete scan may only judge rows the hydrated owners referenced
      // in their pre state.
      for (f, ne) <- nestedFieldsOf(e) do
        val ids = s"_${Naming.toSnakeCase(ne.entityName)}_pre_ids"
        persistLines += s"        $ids.update(int(_x) for _r in ${snake}_rows.values() for _x in _r.${f.columnName})"
      persistLines += s"        for ${snake}_key, ${snake}_value in ${snake}_post.items():"
      persistLines += s"            ${snake}_row = ${snake}_rows.get(${snake}_key)"
      persistLines += s"            if ${snake}_row is None:"
      persistLines += s"                session.add($model("
      for f <- e.fields do
        persistLines += s"                    ${f.columnName}=${fromDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, s"${snake}_value")},"
      persistLines += "                ))"
      persistLines += "            else:"
      for f <- e.fields if f.fieldName != key.fieldName do
        persistLines += s"                ${snake}_row.${f.columnName} = ${fromDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, s"${snake}_value")}"
      persistLines += s"        for ${snake}_key, ${snake}_row in ${snake}_rows.items():"
      persistLines += s"            if ${snake}_key not in ${snake}_post:"
      persistLines += s"                await session.delete(${snake}_row)"
    for r <- seqRelations do
      val e = r.entity
      // Reinsert in seq order: the serial pk reassigns, which nothing
      // observes (the seq projection orders by it and exposes no id).
      val snake = Naming.toSnakeCase(e.entityName)
      val model = e.modelClassName
      persistLines += s"    if scope.get(\"${r.stateField}\") is not None:"
      val seqNested = nestedFieldsOf(e)
      if seqNested.isEmpty then
        persistLines += s"        for ${snake}_old in (await session.execute(select($model))).scalars().all():"
        persistLines += s"            await session.delete(${snake}_old)"
      else
        persistLines += s"        ${snake}_olds = (await session.execute(select($model))).scalars().all()"
        for (f, ne) <- seqNested do
          val ids = s"_${Naming.toSnakeCase(ne.entityName)}_pre_ids"
          persistLines += s"        $ids.update(int(_x) for _r in ${snake}_olds for _x in _r.${f.columnName})"
        persistLines += s"        for ${snake}_old in ${snake}_olds:"
        persistLines += s"            await session.delete(${snake}_old)"
      persistLines += "        await session.flush()"
      persistLines += s"        for ${snake}_value in from_dafny_seq(st.${dafnyName(r.stateField)}):"
      persistLines += s"            session.add($model("
      for f <- e.fields do
        persistLines += s"                ${f.columnName}=${fromDafnyFieldExpr(kindFor(profiled, e.entityName, f), f, s"${snake}_value")},"
      persistLines += "            ))"
    for ne <- nestedEntities do
      val snake = Naming.toSnakeCase(ne.entityName)
      val model = ne.modelClassName
      val idSel = specrest.codegen.EmitShared.pyDafnySelector("id")
      // Only whole-entity relations iterate as owner objects; a relation
      // projecting a single value field holds scalars, not rows. Skipped
      // owners hydrated nothing, so their post maps are empty and this pass
      // degenerates to a no-op when no owner is in scope.
      val owners =
        for
          r      <- planned.relations if r.valueField.isEmpty
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
      persistLines += s"    _${snake}_rows: dict[int, $model] = {}"
      persistLines += s"    if _${snake}_pre_ids:"
      persistLines += s"        _${snake}_q = select($model).where($model.id.in_(list(_${snake}_pre_ids)))"
      persistLines += s"        _${snake}_rows = {"
      persistLines += "            int(r.id): r"
      persistLines += s"            for r in (await session.execute(_${snake}_q)).scalars().all()"
      persistLines += "        }"
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

    val needsOptional =
      (planned.relations.map(_.entity) ++ nestedEntities).exists(_.fields.exists(_.nullable))
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
      (if needsDatetime then "\nfrom datetime import datetime, timezone" else "") +
        "\nfrom typing import Any\n"
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
       |async def hydrate_state(
       |    session: AsyncSession, scope: dict[str, Any] | None = None
       |) -> module_.ServiceState:
       |    st = make_state()
       |${hydrateLines.result().mkString("\n")}
       |    return st
       |
       |
       |async def persist_state(
       |    session: AsyncSession, st: module_.ServiceState, scope: dict[str, Any] | None = None
       |) -> None:
       |${persistLines.result().mkString("\n")}
       |""".stripMargin
