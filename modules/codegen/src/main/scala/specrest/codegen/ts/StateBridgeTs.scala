package specrest.codegen.ts

import specrest.codegen.StatePlan
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.enumNameFull
import specrest.ir.generated.SpecRestGenerated.enumValuesFull
import specrest.ir.generated.SpecRestGenerated.svcEnums
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

// Generates src/services/stateBridge.ts: hydrate a Dafny ServiceState from the
// database and persist its mutations back, both against Prisma's transaction
// client so kernel service functions run them inside one $transaction. The
// Dafny JS backend doubles underscores in identifiers (dtor_created__at,
// base__url) and exposes datatype fields as dtor_ properties.
object StateBridgeTs:

  import specrest.codegen.KernelTypes.Kind

  private val ScalarTsTypes = Set("string", "number", "boolean", "Date")

  private[codegen] def kindFor(ir: ServiceIRFull, entityName: String, f: ProfiledField) =
    specrest.codegen.KernelTypes.fieldKind(ir, entityName, f.fieldName)

  private def kindSupported(
      profiled: ProfiledService,
      k: Option[Kind],
      nullable: Boolean
  ): Boolean = k match
    case Some(Kind.Scalar(_))                => true
    case Some(Kind.EnumK(_))                 => true
    case Some(Kind.SetOf(_) | Kind.SeqOf(_)) => !nullable
    case Some(Kind.EntitySetOf(en))          =>
      // One nesting level: the element entity's own fields must all marshal
      // without another entity collection inside.
      !nullable && specrest.codegen.EmitShared.nestedEntity(profiled, en).exists { ne =>
        ne.fields.forall { nf =>
          kindFor(profiled.ir, ne.entityName, nf) match
            case Some(Kind.EntitySetOf(_)) => false
            case k =>
              kindSupported(profiled, k, nf.nullable) ||
              ScalarTsTypes.contains(baseTs(nf.domainType))
        }
      }
    case Some(Kind.OptOf(inner)) =>
      inner match
        case Kind.SetOf(_) | Kind.SeqOf(_) | Kind.EntitySetOf(_) => false
        case _                                                   => kindSupported(profiled, Some(inner), nullable = false)
    case _ => false

  def plan(profiled: ProfiledService): Either[String, StatePlan.Plan] =
    StatePlan.analyze(
      profiled,
      // Nullable ts fields carry the union spelling in domainType; enum and
      // scalar-collection fields resolve through the spec's kinds.
      fieldSupported = f =>
        ScalarTsTypes.contains(baseTs(f.domainType)) || {
          val owner = profiled.entities
            .find(_.fields.exists(_ eq f))
            .map(_.entityName)
          owner.exists(en => kindSupported(profiled, kindFor(profiled.ir, en, f), f.nullable))
        },
      keySupported = k => Set("string", "number").contains(k.domainType) && !k.nullable,
      seqSupported = true
    )

  // Renders `<prefix>{ e1, e2 };` on one line when it fits 100 columns after
  // `margin` spaces of ambient indent, one entry per line otherwise.
  def scopeAssign(prefix: String, entries: List[String], margin: Int): List[String] =
    if entries.isEmpty then List(s"$prefix{};")
    else
      val single = s"$prefix{ ${entries.mkString(", ")} };"
      if margin + single.length <= 100 then List(single)
      else
        val pad = prefix.takeWhile(_ == ' ')
        (s"$prefix{" :: entries.map(e => s"$pad  $e,")) ::: List(s"$pad};")

  private[codegen] def enumValuesLiteral(ir: ServiceIRFull, enumName: String): String =
    svcEnums(ir)
      .find(e => enumNameFull(e) == enumName)
      .map(e => enumValuesFull(e).map(v => s"'$v'").mkString("[", ", ", "]"))
      .getOrElse("[]")

  private def baseTs(domainType: String): String =
    domainType.replaceAll("\\s*\\|\\s*null$", "").trim

  private def camel(name: String): String =
    Naming.toCamelCase(name, Naming.CamelStrategy.Plain)

  private def dafnyName(specName: String): String = specName.replace("_", "__")

  private def elemToDafny(el: String, ref: String): String =
    if el == "str" then s"stringToDafny($ref as string)" else s"intToDafny($ref as number)"

  private def elemFromDafny(el: String, ref: String): String =
    if el == "str" then s"stringFromDafny($ref)" else s"intFromDafny($ref)"

  private[codegen] def toDafnyExpr(
      ir: ServiceIRFull,
      entityName: String,
      f: ProfiledField,
      rowRef: String
  ): String =
    val access = s"$rowRef.${camel(f.fieldName)}"
    kindFor(ir, entityName, f).map(specrest.codegen.KernelTypes.unwrapOpt) match
      case Some(Kind.EnumK(n)) if !f.nullable =>
        s"enumToDafny('$n', $access as string)"
      case Some(Kind.EnumK(n)) =>
        s"someOrNone($access, (_v) => enumToDafny('$n', _v as string))"
      case Some(Kind.SetOf(el)) if !f.nullable =>
        s"dafnySetOf(($access as unknown[]).map((x) => ${elemToDafny(el, "x")}))"
      case Some(Kind.SeqOf(el)) if !f.nullable =>
        s"dafnySeqOf(($access as unknown[]).map((x) => ${elemToDafny(el, "x")}))"
      case Some(Kind.SetOf(el)) =>
        s"someOrNone($access, (_v) => dafnySetOf((_v as unknown[]).map((x) => ${elemToDafny(el, "x")})))"
      case Some(Kind.SeqOf(el)) =>
        s"someOrNone($access, (_v) => dafnySeqOf((_v as unknown[]).map((x) => ${elemToDafny(el, "x")})))"
      case Some(Kind.EntitySetOf(en)) if !f.nullable =>
        // The JSON column stores element ids; the element rows were fetched
        // in the hydrate prelude and the helper builds the Dafny value.
        val rows = s"${camel(en)}Rows"
        s"dafnySetOf($rows.filter((li) => ($access as number[]).includes(Number(li.id))).map((li) => ${camel(en)}ToDafny(li)))"
      case Some(Kind.EntitySetOf(_)) => access
      case _                         => scalarToDafny(f, access)

  private def scalarToDafny(f: ProfiledField, access: String): String =
    if f.nullable then
      baseTs(f.domainType) match
        case "string" => s"someOrNone($access, (v) => stringToDafny(v as string))"
        case "number" => s"someOrNone($access, (v) => intToDafny(v as number))"
        case "Date" =>
          s"someOrNone($access, (v) => intToDafny(Math.floor((v as Date).getTime() / 1000)))"
        case _ => s"someOrNone($access, (v) => v)"
    else
      f.domainType match
        case "string" => s"stringToDafny($access)"
        case "number" => s"intToDafny($access)"
        case "Date"   => s"intToDafny(Math.floor($access.getTime() / 1000))"
        case _        => access

  private[codegen] def fromDafnyExpr(
      ir: ServiceIRFull,
      entityName: String,
      f: ProfiledField,
      valueRef: String
  ): String =
    val access = s"$valueRef['dtor_${dafnyName(f.fieldName)}']"
    kindFor(ir, entityName, f).map(specrest.codegen.KernelTypes.unwrapOpt) match
      case Some(Kind.EnumK(n)) if !f.nullable =>
        s"enumFromDafny('$n', ${enumValuesLiteral(ir, n)}, $access)"
      case Some(Kind.EnumK(n)) =>
        s"(valueOrNull($access, (_v) => enumFromDafny('$n', ${enumValuesLiteral(ir, n)}, _v)) as string | null)"
      case Some(Kind.SetOf(el)) if !f.nullable =>
        // Sets serialize sorted so the JSON column stays deterministic.
        s"dafnyCollToArray($access).map((v) => ${elemFromDafny(el, "v")}).sort()"
      case Some(Kind.SeqOf(el)) if !f.nullable =>
        s"dafnyCollToArray($access).map((v) => ${elemFromDafny(el, "v")})"
      case Some(Kind.SetOf(el)) =>
        s"(valueOrNull($access, (_v) => dafnyCollToArray(_v).map((v) => ${elemFromDafny(el, "v")}).sort()) as string[] | null)"
      case Some(Kind.SeqOf(el)) =>
        s"(valueOrNull($access, (_v) => dafnyCollToArray(_v).map((v) => ${elemFromDafny(el, "v")})) as unknown[] | null)"
      case Some(Kind.EntitySetOf(_)) if !f.nullable =>
        // The column keeps the id-list shape; the element rows persist in
        // their own table pass.
        s"dafnyCollToArray($access).map((v) => intFromDafny((v as Record<string, unknown>)['dtor_id'])).sort((a, b) => a - b)"
      case Some(Kind.EntitySetOf(_)) => access
      case _                         => scalarFromDafny(f, access)

  private def scalarFromDafny(f: ProfiledField, access: String): String =
    if f.nullable then
      baseTs(f.domainType) match
        case "string" => s"(valueOrNull($access, (v) => stringFromDafny(v)) as string | null)"
        case "number" => s"(valueOrNull($access, (v) => intFromDafny(v)) as number | null)"
        case "Date" =>
          s"(valueOrNull($access, (v) => new Date(intFromDafny(v) * 1000)) as Date | null)"
        case _ => s"(valueOrNull($access, (v) => v) as boolean | null)"
    else
      f.domainType match
        case "string" => s"stringFromDafny($access)"
        case "number" => s"intFromDafny($access)"
        case "Date"   => s"new Date(intFromDafny($access) * 1000)"
        case _        => s"($access as boolean)"

  private def keyToDafny(key: ProfiledField, rowRef: String): String =
    key.domainType match
      case "string" => s"stringToDafny($rowRef.${camel(key.fieldName)})"
      case _        => s"intToDafny($rowRef.${camel(key.fieldName)})"

  def emit(profiled: ProfiledService): String =
    val planned = plan(profiled) match
      case Right(p) => p
      case Left(_)  => StatePlan.Plan(Nil, Nil)

    val seqRelations = planned.relations.filter(_.isSeq)

    // Element entities of Set[Entity] fields: hydration fetches their rows
    // and builds Dafny values through a local helper, persistence upserts
    // them from the post-state sets.
    val nestedByName =
      (for
        r <- planned.relations
        f <- r.entity.fields
        en <- kindFor(profiled.ir, r.entity.entityName, f).toList.collect {
                case Kind.EntitySetOf(en) => en
              }
        ne <- specrest.codegen.EmitShared.nestedEntity(profiled, en)
      yield ne.entityName -> ne).toMap
    val nestedEntities = nestedByName.values.toList.sortBy(_.entityName)
    def nestedFieldsOf(e: specrest.profile.ProfiledEntity) =
      e.fields.flatMap(f =>
        kindFor(profiled.ir, e.entityName, f)
          .collect { case Kind.EntitySetOf(en) => en }
          .flatMap(nestedByName.get)
          .map(f -> _)
      )

    // The fields a relation's hydrate block actually converts: a value
    // projection reads only its value column, everything else the whole row.
    def hydratedFields(r: StatePlan.RelationPlan): List[ProfiledField] =
      r.valueField.map(List(_)).getOrElse(r.entity.fields)
    def relNestedRefs(
        r: StatePlan.RelationPlan
    ): List[(ProfiledField, specrest.profile.ProfiledEntity)] =
      hydratedFields(r).flatMap(f =>
        kindFor(profiled.ir, r.entity.entityName, f)
          .collect { case Kind.EntitySetOf(en) => en }
          .flatMap(nestedByName.get)
          .map(f -> _)
      )
    val fullDefault =
      planned.relations.map(_.stateField).distinct.sorted.map(f => s"$f: { kind: 'full' }")
    def relSel(r: StatePlan.RelationPlan): String     = s"${camel(r.stateField)}Sel"
    def relRowsVar(r: StatePlan.RelationPlan): String = s"${camel(r.stateField)}Rows"
    def keysCast(key: ProfiledField): String =
      if key.domainType == "string" then "string[]" else "number[]"
    def keyedWhere(sel: String, key: ProfiledField): String =
      s"$sel.kind === 'keys' ? { ${camel(key.fieldName)}: { in: $sel.keys as ${keysCast(key)} } } : undefined"
    def loadLines(r: StatePlan.RelationPlan): List[String] =
      val client = camel(r.entity.entityName)
      val sel    = relSel(r)
      // A seq present in scope is always full; keyed loads never apply.
      if r.isSeq then
        List(
          s"  const $sel = sc.${r.stateField};",
          s"  const ${relRowsVar(r)} = $sel",
          s"    ? await tx.$client.findMany({ orderBy: { id: 'asc' } })",
          "    : [];"
        )
      else
        r.keyField.toList.flatMap { key =>
          List(
            s"  const $sel = sc.${r.stateField};",
            s"  const ${relRowsVar(r)} = $sel",
            s"    ? await tx.$client.findMany({",
            s"        where: ${keyedWhere(sel, key)},",
            "      })",
            "    : [];"
          )
        }
    def idCollectLines(r: StatePlan.RelationPlan, idsSuffix: String): List[String] =
      relNestedRefs(r).flatMap { (f, ne) =>
        List(
          s"  for (const r of ${relRowsVar(r)}) {",
          s"    for (const x of r.${camel(f.fieldName)} as number[]) {",
          s"      ${camel(ne.entityName)}$idsSuffix.add(Number(x));",
          "    }",
          "  }"
        )
      }
    def constructLines(r: StatePlan.RelationPlan): List[String] =
      val e = r.entity
      if r.isSeq then
        // Rows ordered by the serial pk are the seq, element order preserved.
        List(
          s"  if (${relSel(r)}) {",
          s"    st['${dafnyName(r.stateField)}'] = dafnySeqOf(${relRowsVar(r)}.map((r) =>",
          s"      dafnyModule['${e.entityName}'].create_${e.entityName}("
        )
          ::: e.fields.map(f => s"        ${toDafnyExpr(profiled.ir, e.entityName, f, "r")},")
          ::: List("      ),", "    ));", "  }")
      else
        r.keyField.toList.flatMap { rKey =>
          val builder = s"${camel(r.stateField)}Map"
          val update = r.valueField match
            case Some(vf) =>
              List(
                s"      $builder = $builder.update(${keyToDafny(rKey, "r")}, ${toDafnyExpr(profiled.ir, e.entityName, vf, "r")});"
              )
            case None =>
              s"      $builder = $builder.update(${keyToDafny(rKey, "r")}, dafnyModule['${e.entityName}'].create_${e.entityName}(" ::
                e.fields.map(f => s"        ${toDafnyExpr(profiled.ir, e.entityName, f, "r")},") :::
                List("      ));")
          List(
            s"  if (${relSel(r)}) {",
            s"    let $builder = emptyDafnyMap() as DafnyMap;",
            s"    for (const r of ${relRowsVar(r)}) {"
          ) ::: update ::: List(
            "    }",
            s"    st['${dafnyName(r.stateField)}'] = $builder;",
            "  }"
          )
        }

    val hydrate = new StringBuilder
    def add(sb: StringBuilder, lines: List[String]): Unit =
      lines.foreach(l => sb ++= l + "\n")
    if planned.relations.nonEmpty then
      add(hydrate, scopeAssign("  const sc: HydrationScope = scope ?? ", fullDefault, 0))
    for ne <- nestedEntities do
      hydrate ++= s"  const ${camel(ne.entityName)}Ids = new Set<number>();\n"
    // Relations embedding nested entities load first so the id union across
    // their hydrated rows can key the nested selects; their Dafny values
    // construct after <nested>ToDafny exists.
    val nestedOwners = planned.relations.filter(r => relNestedRefs(r).nonEmpty)
    for r <- nestedOwners do
      add(hydrate, loadLines(r))
      add(hydrate, idCollectLines(r, "Ids"))
    for ne <- nestedEntities do
      val client = camel(ne.entityName)
      add(
        hydrate,
        List(
          s"  const ${client}Rows =",
          s"    ${client}Ids.size > 0",
          s"      ? await tx.$client.findMany({ where: { id: { in: [...${client}Ids] } } })",
          "      : [];",
          s"  const ${client}ToDafny = (r: (typeof ${client}Rows)[number]): unknown =>",
          s"    dafnyModule['${ne.entityName}'].create_${ne.entityName}("
        )
          ::: ne.fields.map(f => s"      ${toDafnyExpr(profiled.ir, ne.entityName, f, "r")},")
          ::: List("    );")
      )
    for r <- planned.relations do
      if relNestedRefs(r).isEmpty then add(hydrate, loadLines(r))
      add(hydrate, constructLines(r))
    if planned.scalars.nonEmpty then
      hydrate ++= "  const scalarRow = await tx.serviceState.findUnique({ where: { id: 1 } });\n"
      hydrate ++= "  if (scalarRow) {\n"
      for sc <- planned.scalars do
        hydrate ++= s"    st['${dafnyName(sc.stateField)}'] = intToDafny(scalarRow.${camel(sc.columnName)});\n"
      hydrate ++= "  }\n"

    val persist = new StringBuilder
    if planned.relations.nonEmpty then
      add(persist, scopeAssign("  const sc: HydrationScope = scope ?? ", fullDefault, 0))
    for ne <- nestedEntities do
      persist ++= s"  const ${camel(ne.entityName)}PreIds = new Set<number>();\n"
    for r <- seqRelations do
      val e      = r.entity
      val client = camel(e.entityName)
      persist ++= s"  if (sc.${r.stateField}) {\n"
      val seqNested = nestedFieldsOf(e)
      if seqNested.nonEmpty then
        persist ++= s"    const ${client}Olds = await tx.$client.findMany();\n"
        for (f, ne) <- seqNested do
          persist ++= s"    for (const r of ${client}Olds) {\n"
          persist ++= s"      for (const x of r.${camel(f.fieldName)} as number[]) {\n"
          persist ++= s"        ${camel(ne.entityName)}PreIds.add(Number(x));\n"
          persist ++= "      }\n"
          persist ++= "    }\n"
      // Reinsert in seq order: the serial pk reassigns, which nothing
      // observes (the seq projection orders by it and exposes no id).
      persist ++= s"    await tx.$client.deleteMany();\n"
      persist ++= s"    for (const v of st['${dafnyName(r.stateField)}'] as Iterable<unknown>) {\n"
      persist ++= "      const value = v as Record<string, unknown>;\n"
      persist ++= s"      await tx.$client.create({\n        data: {\n"
      for f <- e.fields do
        persist ++= s"          ${camel(f.fieldName)}: ${fromDafnyExpr(profiled.ir, e.entityName, f, "value")},\n"
      persist ++= "        },\n      });\n"
      persist ++= "    }\n"
      persist ++= "  }\n"
    for (r, rKey) <- planned.entityRowRelations.flatMap(r => r.keyField.map(r -> _)) do
      val e      = r.entity
      val client = camel(e.entityName)
      val sel    = relSel(r)
      persist ++= s"  const $sel = sc.${r.stateField};\n"
      persist ++= s"  if ($sel) {\n"
      // A keys scope confines the existing-rows scan to the hydrated keys, so
      // rows the request never loaded cannot be judged stale; an empty key
      // list loads no rows, upserts the whole post map, and deletes nothing.
      persist ++= s"    const ${client}Rows = await tx.$client.findMany({\n"
      persist ++= s"      where: ${keyedWhere(sel, rKey)},\n"
      persist ++= "    });\n"
      // String-typed lookup keys are exact for every magnitude: prisma pks
      // arrive as bigint and dafny keys convert through number, so comparing
      // their canonical string forms avoids both bigint mismatch and float
      // precision loss on large ids.
      persist ++= s"    const ${client}Existing = new Map(${client}Rows.map((r) => [String(r.${camel(rKey.fieldName)}), r]));\n"
      // Nested ids collect before the upsert loop mutates these rows: the
      // nested delete scan may only judge rows the hydrated owners referenced
      // in their pre state.
      for (f, ne) <- nestedFieldsOf(e) do
        persist ++= s"    for (const r of ${client}Rows) {\n"
        persist ++= s"      for (const x of r.${camel(f.fieldName)} as number[]) {\n"
        persist ++= s"        ${camel(ne.entityName)}PreIds.add(Number(x));\n"
        persist ++= "      }\n"
        persist ++= "    }\n"
      persist ++= s"    const ${client}Seen = new Set<string>();\n"
      persist ++= s"    for (const [k, v] of st['${dafnyName(r.stateField)}'] as Iterable<[unknown, unknown]>) {\n"
      val dafnyKeyString =
        if rKey.domainType == "string" then "stringFromDafny(k)" else "intKeyFromDafny(k)"
      persist ++= s"      const key = $dafnyKeyString;\n"
      persist ++= "      const value = v as Record<string, unknown>;\n"
      persist ++= s"      ${client}Seen.add(key);\n"
      val nonKey = e.fields.filter(_.fieldName != rKey.fieldName)
      persist ++= "      const data = {\n"
      for f <- nonKey do
        persist ++= s"        ${camel(f.fieldName)}: ${fromDafnyExpr(profiled.ir, e.entityName, f, "value")},\n"
      persist ++= "      };\n"
      persist ++= s"      const row = ${client}Existing.get(key);\n"
      persist ++= "      if (row) {\n"
      persist ++= s"        await tx.$client.update({ where: { id: row.id }, data });\n"
      persist ++= "      } else {\n"
      persist ++= s"        await tx.$client.create({\n"
      persist ++= s"          data: { ${camel(rKey.fieldName)}: ${fromDafnyExpr(profiled.ir, e.entityName, rKey, "value")}, ...data },\n"
      persist ++= "        });\n"
      persist ++= "      }\n"
      persist ++= "    }\n"
      persist ++= s"    for (const [key, row] of ${client}Existing) {\n"
      persist ++= s"      if (!${client}Seen.has(key)) {\n"
      persist ++= s"        await tx.$client.delete({ where: { id: row.id } });\n"
      persist ++= "      }\n"
      persist ++= "    }\n"
      persist ++= "  }\n"
    for ne <- nestedEntities do
      val client = camel(ne.entityName)
      // Only whole-entity relations iterate as owner objects; a relation
      // projecting a single value field holds scalars, not rows. Skipped
      // owners hydrated nothing, so their post maps are empty and this pass
      // degenerates to a no-op when no owner is in scope.
      val owners =
        for
          r      <- planned.relations if r.valueField.isEmpty
          (f, e) <- nestedFieldsOf(r.entity) if e.entityName == ne.entityName
        yield (r, f)
      persist ++= s"  const ${client}Post = new Map<number, Record<string, unknown>>();\n"
      for (r, f) <- owners do
        if r.isSeq then
          persist ++= s"  for (const v of st['${dafnyName(r.stateField)}'] as Iterable<unknown>) {\n"
        else
          persist ++= s"  for (const [, v] of st['${dafnyName(r.stateField)}'] as Iterable<[unknown, unknown]>) {\n"
        persist ++= "    const owner = v as Record<string, unknown>;\n"
        persist ++= s"    for (const x of dafnyCollToArray(owner['dtor_${dafnyName(f.fieldName)}'])) {\n"
        persist ++= "      const li = x as Record<string, unknown>;\n"
        persist ++= s"      ${client}Post.set(intFromDafny(li['dtor_id']), li);\n"
        persist ++= "    }\n"
        persist ++= "  }\n"
      persist ++= s"  const ${client}PostRows =\n"
      persist ++= s"    ${client}PreIds.size > 0\n"
      persist ++= s"      ? await tx.$client.findMany({ where: { id: { in: [...${client}PreIds] } } })\n"
      persist ++= "      : [];\n"
      persist ++= s"  const ${client}Existing = new Map(${client}PostRows.map((r) => [Number(r.id), r]));\n"
      persist ++= s"  for (const [key, value] of ${client}Post) {\n"
      persist ++= "    const data = {\n"
      for f <- ne.fields if f.fieldName != "id" do
        persist ++= s"      ${camel(f.fieldName)}: ${fromDafnyExpr(profiled.ir, ne.entityName, f, "value")},\n"
      persist ++= "    };\n"
      persist ++= s"    const row = ${client}Existing.get(key);\n"
      persist ++= "    if (row) {\n"
      persist ++= s"      await tx.$client.update({ where: { id: row.id }, data });\n"
      persist ++= "    } else {\n"
      persist ++= s"      await tx.$client.create({ data: { id: key, ...data } });\n"
      persist ++= "    }\n"
      persist ++= "  }\n"
      persist ++= s"  for (const [key, row] of ${client}Existing) {\n"
      persist ++= s"    if (!${client}Post.has(key)) {\n"
      persist ++= s"      await tx.$client.delete({ where: { id: row.id } });\n"
      persist ++= "    }\n"
      persist ++= "  }\n"
    if planned.scalars.nonEmpty then
      persist ++= "  const scalarData = {\n"
      for sc <- planned.scalars do
        persist ++= s"    ${camel(sc.columnName)}: intFromDafny(st['${dafnyName(sc.stateField)}']),\n"
      persist ++= "  };\n"
      persist ++= "  await tx.serviceState.upsert({\n"
      persist ++= "    where: { id: 1 },\n"
      persist ++= "    update: scalarData,\n"
      persist ++= "    create: { id: 1, ...scalarData },\n"
      persist ++= "  });\n"

    val adapterNames = (List(
      "dafnyModule",
      "emptyDafnyMap",
      "intFromDafny",
      "intToDafny",
      "makeState",
      "stringFromDafny",
      "stringToDafny"
    ) ::: (if seqRelations.nonEmpty then List("dafnySeqOf") else Nil)
      ::: (if planned.entityRowRelations.exists(_.keyField.exists(_.domainType != "string")) then
             List("intKeyFromDafny")
           else Nil)
      ::: (if planned.relations.exists(_.entity.fields.exists(_.nullable)) then
             List("someOrNone", "valueOrNull")
           else Nil)
      ::: {
        // Kind-driven conversions pull their helpers by content scan: the
        // hydrate/persist text is already assembled at this point.
        val text = hydrate.toString + persist.toString
        List("enumToDafny", "enumFromDafny", "dafnySetOf", "dafnyCollToArray")
          .filter(n => text.contains(n + "("))
      }).sorted.distinct
    s"""import type { Prisma } from '@prisma/client';
       |
       |import {
       |${adapterNames.map(n => s"  $n,").mkString("\n")}
       |} from '../dafnyKernel/adapter.js';
       |
       |type DafnyMap = { update(k: unknown, v: unknown): DafnyMap } & Iterable<[unknown, unknown]>;
       |
       |// Which rows of each state relation an operation touches: a missing entry
       |// skips the relation in both directions, 'full' loads the whole table, and
       |// 'keys' confines both the load and the stale-row delete scan to the listed
       |// key values (an empty list is a persist-only scope).
       |export type HydrationSel = { kind: 'full' } | { kind: 'keys'; keys: ReadonlyArray<unknown> };
       |
       |export type HydrationScope = Readonly<Record<string, HydrationSel | undefined>>;
       |
       |export const hydrateState = async (
       |  tx: Prisma.TransactionClient,
       |  scope?: HydrationScope,
       |): Promise<unknown> => {
       |  const st = makeState() as Record<string, unknown>;
       |${hydrate.toString}  return st;
       |};
       |
       |export const persistState = async (
       |  tx: Prisma.TransactionClient,
       |  state: unknown,
       |  scope?: HydrationScope,
       |): Promise<void> => {
       |  const st = state as Record<string, unknown>;
       |${persist.toString}};
       |""".stripMargin
