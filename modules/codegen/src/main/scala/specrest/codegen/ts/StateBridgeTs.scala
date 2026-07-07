package specrest.codegen.ts

import specrest.codegen.HydrationScope
import specrest.codegen.StatePlan
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.enumNameFull
import specrest.ir.generated.SpecRestGenerated.enumValuesFull
import specrest.ir.generated.SpecRestGenerated.svcEnums
import specrest.ir.generated.SpecRestGenerated.svcOperations
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

// Generates src/services/stateBridge.ts: hydrate a Dafny ServiceState from the
// database and persist its mutations back, both against Prisma's transaction
// client so kernel service functions run them inside one $transaction. The
// Dafny JS backend doubles underscores in identifiers (dtor_created__at,
// base__url) and exposes datatype fields as dtor_ properties.
//
// Hydration runs a static schedule: the union of every operation's
// wave-ordered load plan, one guarded block per (relation, source shape), so
// a request's scope (relation -> list of source descriptors) activates
// exactly its own subset. hydrateState returns alongside the state a record
// of what was actually loaded (whole relation or a pk set), and persistState
// confines its stale-row delete scans to that record.
object StateBridgeTs:

  import specrest.codegen.KernelTypes.Kind

  // One guarded load block per (relation, shape): all Input sources of a
  // relation batch into a single keys descriptor, so they collapse here.
  private enum StepShape derives CanEqual:
    case FullLoad
    case InputKeys
    case FieldOf(src: String, field: String)
    case Dependent(src: String, coll: String, elem: String)
    case ValueCol(src: String, column: String)

  private def stepShape(source: Option[HydrationScope.KeySource]): StepShape = source match
    case None                                                     => StepShape.FullLoad
    case Some(HydrationScope.KeySource.Input(_))                  => StepShape.InputKeys
    case Some(HydrationScope.KeySource.FieldOfRows(src, f))       => StepShape.FieldOf(src, f)
    case Some(HydrationScope.KeySource.DependentField(src, c, e)) => StepShape.Dependent(src, c, e)
    case Some(HydrationScope.KeySource.ValueColumn(src, col))     => StepShape.ValueCol(src, col)

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
    val seqNames     = seqRelations.map(_.stateField).toSet
    val relByField   = planned.relations.map(r => r.stateField -> r).toMap

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
    val hydrateDefault =
      planned.relations.map(_.stateField).distinct.sorted.map(f => s"$f: [{ kind: 'full' }]")
    val persistDefault =
      planned.relations.map(_.stateField).distinct.sorted.map(f => s"$f: { kind: 'full' }")
    def relSel(r: StatePlan.RelationPlan): String     = s"${camel(r.stateField)}Sel"
    def relAcc(r: StatePlan.RelationPlan): String     = s"${camel(r.stateField)}Acc"
    def relRowsVar(r: StatePlan.RelationPlan): String = s"${camel(r.stateField)}Rows"
    def keysCast(key: ProfiledField): String =
      if key.domainType == "string" then "string[]" else "number[]"
    val numCmp = "(a, b) => (a < b ? -1 : a > b ? 1 : 0)"
    def sortFor(target: ProfiledField): String =
      if baseTs(target.domainType) == "string" then ".sort()" else s".sort($numCmp)"
    // Derived key values normalize to number for numeric targets: a BigInt
    // pk projects as bigint, which Prisma's strict IntFilter (a plain Int
    // column like payments.order_id) rejects at runtime.
    def projFor(target: ProfiledField, ref: String): String =
      if baseTs(target.domainType) == "string" then ref else s"Number($ref)"

    // The static load schedule: the union of every operation's wave-ordered
    // plan, plus a full load per relation so the scope-less default (and any
    // degraded plan) can hydrate everything. Each (relation, shape) step runs
    // once, at the latest wave any operation gives it. Moving a derived step
    // later only grows its source's accumulated rows, and the one inversion
    // the maximum can create (the two halves of a certified cycle swapping)
    // re-keys rows the bilateral certificate already pins to hydrated ones.
    val unionSteps =
      (svcOperations(profiled.ir)
        .flatMap(op => HydrationScope.loadPlan(HydrationScope.analyze(op, profiled.ir)))
        .map(step => (step.relation, stepShape(step.source), step.wave))
        ::: planned.relations.map(r => (r.stateField, StepShape.FullLoad, 1)))
        .filter((rel, _, _) => relByField.contains(rel) && !seqNames(rel))
        .groupBy((rel, shape, _) => (rel, shape))
        .toList
        .map { case ((rel, shape), steps) => (rel, shape, steps.map(_._3).max) }
        .sortBy((rel, shape, wave) => (wave, rel, shape.toString))

    def accInsert(r: StatePlan.RelationPlan, key: ProfiledField, pad: String): List[String] =
      List(
        s"${pad}for (const r of ${relRowsVar(r)}) {",
        s"$pad  ${relAcc(r)}.set(r.${camel(key.fieldName)}, r);",
        s"$pad}"
      )
    def stepLines(r: StatePlan.RelationPlan, key: ProfiledField, shape: StepShape): List[String] =
      val rel      = r.stateField
      val client   = camel(r.entity.entityName)
      val sel      = relSel(r)
      val rows     = relRowsVar(r)
      val keyCamel = camel(key.fieldName)
      def srcAccOf(src: String): String =
        relByField.get(src).map(relAcc).getOrElse(s"${camel(src)}Acc")
      shape match
        case StepShape.FullLoad =>
          List(
            s"  if ($sel.some((d) => d.kind === 'full')) {",
            s"    const $rows = await tx.$client.findMany();"
          ) ::: accInsert(r, key, "    ") ::: List("  }")
        case StepShape.InputKeys =>
          val keysVar = s"${camel(rel)}InputKeys"
          List(
            s"  const $keysVar = $sel.flatMap((d) => (d.kind === 'keys' ? [...d.keys] : []));",
            s"  if ($keysVar.length > 0) {",
            s"    const $rows = await tx.$client.findMany({",
            s"      where: { $keyCamel: { in: $keysVar as ${keysCast(key)} } },",
            "    });"
          ) ::: accInsert(r, key, "    ") ::: List("  }")
        case StepShape.FieldOf(src, field) =>
          val srcField = relByField.get(src).toList
            .flatMap(_.entity.fields)
            .find(_.fieldName == field)
          val srcCamel = srcField.map(f => camel(f.fieldName)).getOrElse(camel(field))
          val keysVar  = s"${camel(rel)}From${Naming.toPascalCase(src)}Keys"
          val proj     = projFor(key, s"r.$srcCamel")
          val gather =
            if srcField.exists(_.nullable) then
              s"      ...new Set([...${srcAccOf(src)}.values()].flatMap((r) => (r.$srcCamel === null ? [] : [$proj]))),"
            else s"      ...new Set([...${srcAccOf(src)}.values()].map((r) => $proj)),"
          List(
            s"  if ($sel.some((d) => d.kind === 'fieldOf' && d.src === '$src' && d.field === '$field')) {",
            s"    const $keysVar = [",
            gather,
            s"    ]${sortFor(key)};",
            s"    if ($keysVar.length > 0) {",
            s"      const $rows = await tx.$client.findMany({",
            s"        where: { $keyCamel: { in: $keysVar } },",
            "      });"
          ) ::: accInsert(r, key, "      ") ::: List("    }", "  }")
        case StepShape.ValueCol(src, column) =>
          val colField = r.entity.fields.find(_.fieldName == column)
          val colCamel = colField.map(f => camel(f.fieldName)).getOrElse(camel(column))
          val srcKeys = colField match
            case Some(cf) if baseTs(cf.domainType) != "string" =>
              s"[...${srcAccOf(src)}.keys()].map((k) => Number(k)).sort($numCmp)"
            case _ => s"[...${srcAccOf(src)}.keys()].sort()"
          List(
            "  if (",
            s"    $sel.some((d) => d.kind === 'valueCol' && d.src === '$src' && d.column === '$column') &&",
            s"    ${srcAccOf(src)}.size > 0",
            "  ) {",
            s"    const $rows = await tx.$client.findMany({",
            s"      where: { $colCamel: { in: $srcKeys } },",
            "    });"
          ) ::: accInsert(r, key, "    ") ::: List("  }")
        case StepShape.Dependent(src, coll, elem) =>
          val srcEntity = relByField.get(src).map(_.entity)
          val collField = srcEntity.toList.flatMap(_.fields).find(_.fieldName == coll)
          val collCamel = collField.map(f => camel(f.fieldName)).getOrElse(camel(coll))
          val nested = collField
            .flatMap(f => srcEntity.flatMap(e => kindFor(profiled.ir, e.entityName, f)))
            .collect { case Kind.EntitySetOf(en) => en }
            .flatMap(nestedByName.get)
          nested.toList.flatMap { ne =>
            val neCamel   = camel(ne.entityName)
            val elemField = ne.fields.find(_.fieldName == elem)
            val elemCamel = elemField.map(f => camel(f.fieldName)).getOrElse(camel(elem))
            val keysVar   = s"${camel(rel)}DepKeys"
            List(
              "  if (",
              s"    $sel.some(",
              s"      (d) => d.kind === 'dependent' && d.src === '$src' && d.collection === '$coll' && d.elem === '$elem',",
              "    )",
              "  ) {",
              s"    const $keysVar = [",
              "      ...new Set(",
              s"        [...${srcAccOf(src)}.values()].flatMap((r) =>",
              s"          (r.$collCamel as number[]).flatMap((x) => {",
              s"            const li = ${neCamel}ById.get(Number(x));",
              s"            return li ? [${projFor(key, s"li.$elemCamel")}] : [];",
              "          }),",
              "        ),",
              "      ),",
              s"    ]${sortFor(key)};",
              s"    if ($keysVar.length > 0) {",
              s"      const $rows = await tx.$client.findMany({",
              s"        where: { $keyCamel: { in: $keysVar } },",
              "      });"
            ) ::: accInsert(r, key, "      ") ::: List("    }", "  }")
          }
    def constructMap(r: StatePlan.RelationPlan, rKey: ProfiledField): List[String] =
      val e       = r.entity
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
        s"  if (${relSel(r)}.length > 0) {",
        s"    let $builder = emptyDafnyMap() as DafnyMap;",
        s"    for (const r of ${relAcc(r)}.values()) {"
      ) ::: update ::: List(
        "    }",
        s"    st['${dafnyName(r.stateField)}'] = $builder;",
        s"    if (${relSel(r)}.some((d) => d.kind === 'full')) {",
        s"      hydrated['${r.stateField}'] = { kind: 'full' };",
        "    } else {",
        s"      hydrated['${r.stateField}'] = { kind: 'keys', keys: [...${relAcc(r)}.keys()] };",
        "    }",
        "  }"
      )
    def constructSeq(r: StatePlan.RelationPlan): List[String] =
      val e = r.entity
      List(
        s"  if (sc.${r.stateField}) {",
        s"    st['${dafnyName(r.stateField)}'] = dafnySeqOf(${relRowsVar(r)}.map((r) =>",
        s"      dafnyModule['${e.entityName}'].create_${e.entityName}("
      )
        ::: e.fields.map(f => s"        ${toDafnyExpr(profiled.ir, e.entityName, f, "r")},")
        ::: List(
          "      ),",
          "    ));",
          s"    hydrated['${r.stateField}'] = { kind: 'full' };",
          "  }"
        )

    val keyedRelations = planned.relations
      .filterNot(_.isSeq)
      .flatMap(r => r.keyField.map(r -> _))
      .sortBy(_._1.stateField)
    val ownerRels =
      planned.relations.filter(r => relNestedRefs(r).nonEmpty).map(_.stateField).toSet
    val lastStepIdx: Map[String, Int] =
      unionSteps.zipWithIndex.groupMapReduce(_._1._1)(_._2)(math.max)
    val lastOwnerIdx =
      unionSteps.zipWithIndex.collect { case ((rel, _, _), i) if ownerRels(rel) => i }.maxOption

    val hydrate = new StringBuilder
    def add(sb: StringBuilder, lines: List[String]): Unit =
      lines.foreach(l => sb ++= l + "\n")
    hydrate ++= "  const hydrated: Record<string, HydratedSel> = {};\n"
    if planned.relations.nonEmpty then
      add(hydrate, scopeAssign("  const sc: HydrationScope = scope ?? ", hydrateDefault, 0))
    for (r, key) <- keyedRelations do
      val model = r.entity.modelClassName
      hydrate ++= s"  const ${relSel(r)} = sc.${r.stateField} ?? [];\n"
      hydrate ++= s"  const ${relAcc(r)} = new Map<$model['${camel(key.fieldName)}'], $model>();\n"
    for ne <- nestedEntities do
      hydrate ++= s"  const ${camel(ne.entityName)}Ids = new Set<number>();\n"
    // Seq-valued state loads whole or not at all (a seq present in scope is
    // always [{ kind: 'full' }]); rows ordered by the serial pk are the seq.
    // Loads sit before the schedule so a seq owner's id union can key the
    // nested selects; the Dafny values construct with the keyed relations.
    for r <- seqRelations do
      val client = camel(r.entity.entityName)
      add(
        hydrate,
        List(
          s"  const ${relRowsVar(r)} = sc.${r.stateField}",
          s"    ? await tx.$client.findMany({ orderBy: { id: 'asc' } })",
          "    : [];"
        )
      )
      for (f, ne) <- relNestedRefs(r) do
        add(
          hydrate,
          List(
            s"  for (const r of ${relRowsVar(r)}) {",
            s"    for (const x of r.${camel(f.fieldName)} as number[]) {",
            s"      ${camel(ne.entityName)}Ids.add(Number(x));",
            "    }",
            "  }"
          )
        )
    for ((rel, shape, _), idx) <- unionSteps.zipWithIndex do
      val r = relByField(rel)
      r.keyField.foreach(key => add(hydrate, stepLines(r, key, shape)))
      // The id union across a nested owner's accumulated rows keys the
      // nested selects, so it collects after the owner's last load step.
      if ownerRels(rel) && lastStepIdx.get(rel).contains(idx) then
        for (f, ne) <- relNestedRefs(r) do
          add(
            hydrate,
            List(
              s"  for (const r of ${relAcc(r)}.values()) {",
              s"    for (const x of r.${camel(f.fieldName)} as number[]) {",
              s"      ${camel(ne.entityName)}Ids.add(Number(x));",
              "    }",
              "  }"
            )
          )
      if lastOwnerIdx.contains(idx) then
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
              ::: List(
                "    );",
                s"  const ${client}ById = new Map(${client}Rows.map((r) => [Number(r.id), r]));"
              )
          )
    // Each relation's Dafny value constructs once, after its last scheduled
    // step, from whatever the steps accumulated; the hydrated record keeps
    // the actually-loaded pks so persist can confine its delete scans.
    for (r, key) <- keyedRelations do add(hydrate, constructMap(r, key))
    for r        <- seqRelations do add(hydrate, constructSeq(r))
    if planned.scalars.nonEmpty then
      hydrate ++= "  const scalarRow = await tx.serviceState.findUnique({ where: { id: 1 } });\n"
      hydrate ++= "  if (scalarRow) {\n"
      for sc <- planned.scalars do
        hydrate ++= s"    st['${dafnyName(sc.stateField)}'] = intToDafny(scalarRow.${camel(sc.columnName)});\n"
      hydrate ++= "  }\n"

    val persist = new StringBuilder
    if planned.relations.nonEmpty then
      add(persist, scopeAssign("  const hy: HydratedRecord = hydrated ?? ", persistDefault, 0))
    for ne <- nestedEntities do
      persist ++= s"  const ${camel(ne.entityName)}PreIds = new Set<number>();\n"
    for r <- seqRelations do
      val e      = r.entity
      val client = camel(e.entityName)
      persist ++= s"  if (hy.${r.stateField}) {\n"
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
    def sortedHydratedKeys(sel: String, key: ProfiledField): String =
      if key.domainType == "string" then s"([...$sel.keys] as string[]).sort()"
      else s"([...$sel.keys] as number[]).sort($numCmp)"
    for (r, rKey) <- planned.entityRowRelations.flatMap(r => r.keyField.map(r -> _)) do
      val e      = r.entity
      val client = camel(e.entityName)
      val sel    = relSel(r)
      persist ++= s"  const $sel = hy.${r.stateField};\n"
      persist ++= s"  if ($sel) {\n"
      // A full hydration scans the whole table for stale rows; a keyed one
      // confines the scan to the pks hydrate actually loaded, so rows the
      // request never saw cannot be judged stale. An empty key set loads no
      // rows: the post map upserts in full and nothing deletes.
      persist ++= s"    const ${client}Rows =\n"
      persist ++= s"      $sel.kind === 'full' || $sel.keys.length > 0\n"
      persist ++= s"        ? await tx.$client.findMany({\n"
      persist ++= "            where:\n"
      persist ++= s"              $sel.kind === 'keys'\n"
      persist ++= s"                ? { ${camel(rKey.fieldName)}: { in: ${sortedHydratedKeys(sel, rKey)} } }\n"
      persist ++= "                : undefined,\n"
      persist ++= "          })\n"
      persist ++= "        : [];\n"
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
    val prismaTypes =
      ("Prisma" :: keyedRelations.map(_._1.entity.modelClassName)).distinct.sorted.mkString(", ")
    s"""import type { $prismaTypes } from '@prisma/client';
       |
       |import {
       |${adapterNames.map(n => s"  $n,").mkString("\n")}
       |} from '../dafnyKernel/adapter.js';
       |
       |type DafnyMap = { update(k: unknown, v: unknown): DafnyMap } & Iterable<[unknown, unknown]>;
       |
       |// Which rows of each state relation an operation touches: a missing entry
       |// skips the relation in both directions, and each descriptor in a
       |// relation's list arms one block of the bridge's static load schedule.
       |// 'keys' batches the operation's input key values (kept when empty: a
       |// persist-only scope); the derived kinds key the relation off the rows
       |// already accumulated for their source relation in an earlier wave.
       |export type HydrationSel =
       |  | { kind: 'full' }
       |  | { kind: 'keys'; keys: ReadonlyArray<unknown> }
       |  | { kind: 'fieldOf'; src: string; field: string }
       |  | { kind: 'dependent'; src: string; collection: string; elem: string }
       |  | { kind: 'valueCol'; src: string; column: string };
       |
       |export type HydrationScope = Readonly<Record<string, ReadonlyArray<HydrationSel> | undefined>>;
       |
       |// What hydrateState actually loaded per relation: the whole table or the
       |// exact key set. persistState confines its stale-row delete scans to this
       |// record, so rows a request never loaded cannot be judged stale.
       |export type HydratedSel = { kind: 'full' } | { kind: 'keys'; keys: ReadonlyArray<unknown> };
       |
       |export type HydratedRecord = Readonly<Record<string, HydratedSel | undefined>>;
       |
       |export const hydrateState = async (
       |  tx: Prisma.TransactionClient,
       |  scope?: HydrationScope,
       |): Promise<[unknown, HydratedRecord]> => {
       |  const st = makeState() as Record<string, unknown>;
       |${hydrate.toString}  return [st, hydrated];
       |};
       |
       |export const persistState = async (
       |  tx: Prisma.TransactionClient,
       |  state: unknown,
       |  hydrated?: HydratedRecord,
       |): Promise<void> => {
       |  const st = state as Record<string, unknown>;
       |${persist.toString}};
       |""".stripMargin
