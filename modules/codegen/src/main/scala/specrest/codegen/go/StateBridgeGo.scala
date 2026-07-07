package specrest.codegen.go

import specrest.codegen.HydrationScope
import specrest.codegen.StatePlan
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.enumNameFull
import specrest.ir.generated.SpecRestGenerated.enumValuesFull
import specrest.ir.generated.SpecRestGenerated.operInputs
import specrest.ir.generated.SpecRestGenerated.prmType
import specrest.ir.generated.SpecRestGenerated.svcEnums
import specrest.ir.generated.SpecRestGenerated.svcOperations
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

// Generates internal/services/state_bridge.go: hydrate a Dafny ServiceState
// from the database and persist its mutations back, both against bun.IDB so
// the kernel service methods can run them inside one RunInTx transaction.
// Dafny's Go backend doubles underscores in identifiers (Dtor_created__at)
// and returns tuple elements behind pointers; both quirks live here only.
//
// Hydration runs a static schedule: the union of every operation's
// wave-ordered load plan, one guarded block per (relation, source shape), so
// a request's scope map (relation -> slice of source descriptors) activates
// exactly its own subset. hydrateState returns alongside the state a record
// of what was actually loaded (whole relation or the loaded key values), and
// persistState confines its stale-row delete scans to that record.
object StateBridgeGo:

  import specrest.codegen.KernelTypes.Kind

  private val ScalarGoTypes = Set("string", "int64", "bool", "time.Time")

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

  private[codegen] def kindFor(profiled: ProfiledService, entityName: String, f: ProfiledField) =
    specrest.codegen.KernelTypes.fieldKind(profiled.ir, entityName, f.fieldName)

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
          kindFor(profiled, ne.entityName, nf) match
            case Some(Kind.EntitySetOf(_)) => false
            case k =>
              kindSupported(profiled, k, nf.nullable) ||
              ScalarGoTypes.contains(nf.domainType.stripPrefix("*"))
        }
      }
    case Some(Kind.OptOf(inner)) =>
      // Nullable collections have no bridge conversions on any target yet.
      inner match
        case Kind.SetOf(_) | Kind.SeqOf(_) | Kind.EntitySetOf(_) => false
        case _                                                   => kindSupported(profiled, Some(inner), nullable = false)
    case _ => false

  def plan(profiled: ProfiledService): Either[String, StatePlan.Plan] =
    StatePlan.analyze(
      profiled,
      // Nullable go fields carry the pointer spelling in domainType; enum
      // and scalar-collection fields resolve through the spec's kinds.
      fieldSupported = f =>
        ScalarGoTypes.contains(f.domainType.stripPrefix("*")) || {
          val owner = profiled.entities
            .find(_.fields.exists(_ eq f))
            .map(_.entityName)
          owner.exists(en => kindSupported(profiled, kindFor(profiled, en, f), f.nullable))
        },
      keySupported = k => Set("string", "int64").contains(k.domainType) && !k.nullable,
      seqSupported = true
    )

  private def pascal(name: String): String =
    Naming.toPascalCase(name, Naming.PascalStrategy.Go)

  private def dafnyName(specName: String): String = specName.replace("_", "__")

  // Dafny's Go backend exports a state field by capitalizing its first rune
  // and keeping the doubled underscores: base_url becomes Base__url.
  private def stateFieldName(specName: String): String =
    val d = dafnyName(specName)
    d.head.toUpper.toString + d.tail

  private def rowsVar(e: ProfiledEntity): String =
    Naming.toCamelCase(e.entityName, Naming.CamelStrategy.Plain) + "Rows"

  // Renders `<lhs><mapType>{...}`, padding each key token the way gofmt's
  // tabwriter aligns adjacent composite-literal entries.
  private[go] def scopeAssign(
      lhs: String,
      indent: String,
      mapType: String,
      entries: List[(String, String)]
  ): List[String] =
    if entries.isEmpty then List(s"$indent$lhs$mapType{}")
    else
      val width = entries.map(_._1.length + 3).max
      (s"$indent$lhs$mapType{"
        :: entries.map((k, v) => s"$indent\t${s"\"$k\":".padTo(width + 1, ' ')}$v,"))
        ::: List(s"$indent}")

  private[codegen] def enumHelperName(enumName: String, toDafny: Boolean): String =
    val base = Naming.toCamelCase(enumName, Naming.CamelStrategy.Plain)
    if toDafny then s"${base}ToDafny" else s"${base}FromDafny"

  private def elemToDafny(el: String, ref: String): String =
    if el == "str" then s"dafnykernel.StringToDafny($ref)" else s"dafnykernel.IntToDafny($ref)"

  // Collection fields hoist to pre-lines (gofmt rejects inline closures);
  // toDafnyParts returns those lines plus the expression to splice.
  private def toDafnyParts(
      kind: Option[Kind],
      f: ProfiledField,
      rowRef: String,
      indent: String
  ): (List[String], String) =
    val access = s"$rowRef.${pascal(f.fieldName)}"
    // A nullable field resolves to OptOf(inner); optionality rides on the
    // profiled nullable flag, so the arms match on the payload kind.
    kind.map(specrest.codegen.KernelTypes.unwrapOpt) match
      case Some(Kind.EnumK(n)) if !f.nullable =>
        (Nil, s"${enumHelperName(n, toDafny = true)}($access)")
      case Some(Kind.EnumK(n)) =>
        val local = Naming.toCamelCase(f.fieldName, Naming.CamelStrategy.Plain) + "Opt"
        (
          List(
            s"$indent$local := dafnykernel.Companion_Option_.Create_None_()",
            s"${indent}if $access != nil {",
            s"$indent\t$local = dafnykernel.Companion_Option_.Create_Some_(${enumHelperName(n, toDafny = true)}(*$access))",
            s"$indent}"
          ),
          local
        )
      case Some(Kind.SetOf(el)) if !f.nullable =>
        collToDafnyParts(el, access, f, seq = false, indent)
      case Some(Kind.SeqOf(el)) if !f.nullable =>
        collToDafnyParts(el, access, f, seq = true, indent)
      case Some(Kind.EntitySetOf(en)) if !f.nullable =>
        // The JSON column stores element ids; rows were indexed by id in the
        // hydrate prelude, and the helper builds the Dafny element value.
        val local  = Naming.toCamelCase(f.fieldName, Naming.CamelStrategy.Plain) + "Conv"
        val byID   = Naming.toCamelCase(en, Naming.CamelStrategy.Plain) + "ByID"
        val helper = nestedToDafnyName(en)
        (
          List(
            s"$indent${local}Elems := make([]interface{}, 0, len($access))",
            s"${indent}for _, x := range $access {",
            // A stale id-list entry must fail hydration loudly, not enter
            // verified state as a zero-valued element.
            s"$indent\t${local}Row, ${local}Ok := $byID[x]",
            s"$indent\tif !${local}Ok {",
            s"""$indent\t\treturn nil, nil, fmt.Errorf("stale ${en} id %d in ${f.columnName}", x)""",
            s"$indent\t}",
            s"$indent\t${local}Elems = append(${local}Elems, $helper(${local}Row))",
            s"$indent}",
            s"$indent$local := _dafny.SetOf(${local}Elems...)"
          ),
          local
        )
      case Some(Kind.SetOf(_) | Kind.SeqOf(_) | Kind.EntitySetOf(_)) =>
        // The plan gate rejects nullable collections; nothing marshals them.
        (Nil, access)
      case _ => (Nil, scalarToDafny(f, access))

  private def collToDafnyParts(
      el: String,
      access: String,
      f: ProfiledField,
      seq: Boolean,
      indent: String
  ): (List[String], String) =
    val local = Naming.toCamelCase(f.fieldName, Naming.CamelStrategy.Plain) + "Conv"
    val ctor  = if seq then "SeqOf" else "SetOf"
    (
      List(
        s"$indent${local}Elems := make([]interface{}, 0, len($access))",
        s"${indent}for _, x := range $access {",
        s"$indent\t${local}Elems = append(${local}Elems, ${elemToDafny(el, "x")})",
        s"$indent}",
        s"$indent$local := _dafny.$ctor(${local}Elems...)"
      ),
      local
    )

  private def scalarToDafny(f: ProfiledField, access: String): String =
    if f.nullable then
      f.domainType.stripPrefix("*") match
        case "string"    => s"dafnykernel.OptStringToDafny($access)"
        case "int64"     => s"dafnykernel.OptIntToDafny($access)"
        case "time.Time" => s"dafnykernel.OptTimeToDafny($access)"
        case "bool"      => s"dafnykernel.OptBoolToDafny($access)"
        case _           => access
    else
      f.domainType match
        case "string"    => s"dafnykernel.StringToDafny($access)"
        case "int64"     => s"dafnykernel.IntToDafny($access)"
        case "time.Time" => s"dafnykernel.IntToDafny($access.Unix())"
        case _           => access
  private def elemFromDafny(el: String, ref: String): String =
    if el == "str" then s"dafnykernel.StringFromDafny($ref.(_dafny.Sequence))"
    else s"dafnykernel.IntFromDafny($ref.(_dafny.Int))"

  private[codegen] def fromDafnyParts(
      kind: Option[Kind],
      f: ProfiledField,
      valueRef: String,
      indent: String
  ): (List[String], String) =
    val access = s"$valueRef.Dtor_${dafnyName(f.fieldName)}()"
    kind.map(specrest.codegen.KernelTypes.unwrapOpt) match
      case Some(Kind.EnumK(n)) if !f.nullable =>
        (Nil, s"${enumHelperName(n, toDafny = false)}($access)")
      case Some(Kind.EnumK(n)) =>
        val local = Naming.toCamelCase(f.fieldName, Naming.CamelStrategy.Plain) + "OptOut"
        (
          List(
            s"${indent}var $local *string",
            s"${indent}if ($access).Is_Some() {",
            s"$indent\t${local}V := ${enumHelperName(n, toDafny = false)}(($access).Dtor_value().(dafnykernel.$n))",
            s"$indent\t$local = &${local}V",
            s"$indent}"
          ),
          local
        )
      case Some(Kind.SetOf(el)) if !f.nullable =>
        collFromDafnyParts(el, access, f, seq = false, indent)
      case Some(Kind.SeqOf(el)) if !f.nullable =>
        collFromDafnyParts(el, access, f, seq = true, indent)
      case Some(Kind.EntitySetOf(en)) if !f.nullable =>
        // The column keeps the id-list shape; the element rows persist in
        // their own table pass.
        val local = Naming.toCamelCase(f.fieldName, Naming.CamelStrategy.Plain) + "Out"
        (
          List(
            s"$indent$local := make([]int64, 0)",
            s"${indent}it$local := _dafny.Iterate($access)",
            s"${indent}for v, ok := it$local(); ok; v, ok = it$local() {",
            s"$indent\t$local = append($local, dafnykernel.IntFromDafny(v.(dafnykernel.$en).Dtor_id()))",
            s"$indent}",
            s"${indent}sort.Slice($local, func(i, j int) bool {",
            s"$indent\treturn $local[i] < $local[j]",
            s"$indent})"
          ),
          local
        )
      case Some(Kind.SetOf(_) | Kind.SeqOf(_) | Kind.EntitySetOf(_)) =>
        (Nil, access)
      case _ => (Nil, scalarFromDafny(f, access))

  private def collFromDafnyParts(
      el: String,
      access: String,
      f: ProfiledField,
      seq: Boolean,
      indent: String
  ): (List[String], String) =
    val goElem = if el == "str" then "string" else "int64"
    val local  = Naming.toCamelCase(f.fieldName, Naming.CamelStrategy.Plain) + "Out"
    // Sets serialize sorted so the JSON column stays deterministic.
    val sortLines =
      if seq then Nil
      else if el == "str" then List(s"${indent}sort.Strings($local)")
      else
        List(
          s"${indent}sort.Slice($local, func(i, j int) bool {",
          s"$indent\treturn $local[i] < $local[j]",
          s"$indent})"
        )
    (
      List(
        s"$indent$local := make([]$goElem, 0)",
        s"${indent}it$local := _dafny.Iterate($access)",
        s"${indent}for v, ok := it$local(); ok; v, ok = it$local() {",
        s"$indent\t$local = append($local, ${elemFromDafny(el, "v")})",
        s"$indent}"
      ) ::: sortLines,
      local
    )

  private def scalarFromDafny(f: ProfiledField, access: String): String =
    if f.nullable then
      f.domainType.stripPrefix("*") match
        case "string"    => s"dafnykernel.OptStringFromDafny($access)"
        case "int64"     => s"dafnykernel.OptIntFromDafny($access)"
        case "time.Time" => s"dafnykernel.OptTimeFromDafny($access)"
        case "bool"      => s"dafnykernel.OptBoolFromDafny($access)"
        case _           => access
    else
      f.domainType match
        case "string"    => s"dafnykernel.StringFromDafny($access)"
        case "int64"     => s"dafnykernel.IntFromDafny($access)"
        case "time.Time" => s"time.Unix(dafnykernel.IntFromDafny($access), 0).UTC()"
        case _           => access

  private def nestedToDafnyName(entityName: String): String =
    Naming.toCamelCase(entityName, Naming.CamelStrategy.Plain) + "ToDafny"

  private def keyToDafny(key: ProfiledField, rowRef: String): String =
    key.domainType match
      case "string" => s"dafnykernel.StringToDafny($rowRef.${pascal(key.fieldName)})"
      case _        => s"dafnykernel.IntToDafny($rowRef.${pascal(key.fieldName)})"

  def emit(profiled: ProfiledService, module: String): String =
    val planned = plan(profiled) match
      case Right(p) => p
      case Left(_)  => StatePlan.Plan(Nil, Nil)

    val entities = planned.relations.map(_.entity).distinctBy(_.entityName)
    // Element entities of Set[Entity] fields: hydration joins their rows in
    // by id, persistence upserts them from the post-state sets.
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
    val bridgedEntities = entities ++ nestedEntities
    val needsTime =
      bridgedEntities.exists(_.fields.exists(_.domainType.stripPrefix("*") == "time.Time"))
    val timeImport = if needsTime then "\n\t\"time\"" else ""
    val fieldKinds =
      bridgedEntities.flatMap(e => e.fields.flatMap(f => kindFor(profiled, e.entityName, f)))
    val inputKinds = svcOperations(profiled.ir)
      .flatMap(operInputs)
      .flatMap(pd => specrest.codegen.KernelTypes.resolve(profiled.ir, prmType(pd)))
    def unwrap(k: Kind): Kind = k match
      case Kind.OptOf(inner) => inner
      case other             => other
    val allKinds  = (fieldKinds ++ inputKinds).map(unwrap)
    val usedEnums = allKinds.collect { case Kind.EnumK(n) => n }.distinct
    val usesColl = allKinds.exists {
      case Kind.SetOf(_) | Kind.SeqOf(_) | Kind.EntitySetOf(_) => true
      case _                                                   => false
    }
    val sortImport = if usesColl then "\n\t\"sort\"" else ""
    val scalarImports =
      if planned.scalars.isEmpty then ""
      else "\n\t\"database/sql\"\n\t\"errors\""
    // One trio per enum: hydrate trusts stored values, the checked variant
    // rejects invalid API inputs, and the reverse reads the ctor back out.
    val enumHelpers = usedEnums
      .flatMap { n =>
        svcEnums(profiled.ir).find(e => enumNameFull(e) == n).map { e =>
          val values = enumValuesFull(e)
          val goName = enumHelperName(n, toDafny = true)
          val fromN  = enumHelperName(n, toDafny = false)
          val toCases = values
            .map(v =>
              s"\tcase \"$v\":\n\t\treturn dafnykernel.Companion_${n}_.Create_${v.replace("_", "__")}_(), nil"
            )
            .mkString("\n")
          val fromCases = values
            .map(v => s"\tcase v.Is_${v.replace("_", "__")}():\n\t\treturn \"$v\"")
            .mkString("\n")
          s"""|func ${goName}Checked(v string) (dafnykernel.$n, error) {
              |\tswitch v {
              |$toCases
              |\t}
              |\treturn dafnykernel.$n{}, ErrInvalidInput
              |}
              |
              |func $goName(v string) dafnykernel.$n {
              |\tout, _ := ${goName}Checked(v)
              |\treturn out
              |}
              |
              |func $fromN(v dafnykernel.$n) string {
              |\tswitch {
              |$fromCases
              |\t}
              |\treturn \"\"
              |}
              |""".stripMargin
        }
      }
      .mkString("\n")

    val nestedHelpers = nestedEntities.map { ne =>
      val parts = ne.fields
        .map(f => toDafnyParts(kindFor(profiled, ne.entityName, f), f, "r", "\t"))
      val pre  = parts.flatMap(_._1).map(l => s"$l\n").mkString
      val args = parts.map(p => s"\t\t${p._2},").mkString("\n")
      s"""|func ${nestedToDafnyName(ne.entityName)}(r models.${ne.modelClassName}) dafnykernel.${ne
           .entityName} {
          |$pre\treturn dafnykernel.Companion_${ne.entityName}_.Create_${ne.entityName}_(
          |$args
          |\t)
          |}
          |""".stripMargin
    }.mkString("\n")

    val seqRelations = planned.relations.filter(_.isSeq)
    val seqNames     = seqRelations.map(_.stateField).toSet
    val relByField   = planned.relations.map(r => r.stateField -> r).toMap
    val hydrateDefault =
      planned.relations.map(_.stateField).distinct.sorted.map(f => f -> "{{kind: \"full\"}}")
    val persistDefault =
      planned.relations.map(_.stateField).distinct.sorted.map(f => f -> "{full: true}")
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
    def camel(name: String): String                = Naming.toCamelCase(name, Naming.CamelStrategy.Plain)
    def relRows(r: StatePlan.RelationPlan): String = camel(r.stateField) + "Rows"
    def selVar(rel: String): String                = camel(rel) + "Sel"
    def accVar(rel: String): String                = camel(rel) + "Acc"
    def goKeyType(key: ProfiledField): String =
      if key.domainType == "string" then "string" else "int64"
    def idsVar(ne: ProfiledEntity, pre: Boolean): String =
      val base = camel(ne.entityName)
      if pre then s"${base}PreIDs" else s"${base}IDs"

    // The static load schedule: the union of every operation's wave-ordered
    // plan, plus a full load per relation so the scope=nil default (and any
    // degraded plan) can hydrate everything. Each (relation, shape) step runs
    // once, at the latest wave any operation gives it. Moving a derived step
    // later only grows its source's accumulated rows, and the one inversion
    // the maximum can create (the two halves of a certified cycle swapping)
    // re-keys rows the bilateral certificate already pins to hydrated ones.
    val unionSteps =
      (svcOperations(profiled.ir)
        .flatMap(op => HydrationScope.loadPlan(HydrationScope.analyze(op, profiled.ir)))
        .map(st => (st.relation, stepShape(st.source), st.wave))
        ::: planned.relations.map(r => (r.stateField, StepShape.FullLoad, 1)))
        .filter((rel, _, _) => relByField.contains(rel) && !seqNames(rel))
        .groupBy((rel, shape, _) => (rel, shape))
        .toList
        .map { case ((rel, shape), sts) => (rel, shape, sts.map(_._3).max) }
        .sortBy((rel, shape, wave) => (wave, rel, shape.toString))

    val keyedRelations =
      planned.relations.filterNot(_.isSeq).flatMap(r => r.keyField.map(r -> _))
    val ownerRels =
      planned.relations.filter(r => nestedRefs(r).nonEmpty).map(_.stateField).toSet
    val lastStepIdx: Map[String, Int] =
      unionSteps.zipWithIndex.groupMapReduce(_._1._1)(_._2)(math.max)
    val lastOwnerIdx =
      unionSteps.zipWithIndex.collect { case ((rel, _, _), i) if ownerRels(rel) => i }.maxOption
    val hasInputSteps = unionSteps.exists((_, shape, _) => shape == StepShape.InputKeys)

    def loadInto(r: StatePlan.RelationPlan, key: ProfiledField, where: String, pad: String) =
      List(
        s"${pad}rows := make([]models.${r.entity.modelClassName}, 0)",
        s"${pad}if err := db.NewSelect().Model(&rows)$where.Scan(ctx); err != nil {",
        s"$pad\treturn nil, nil, err",
        s"$pad}",
        s"${pad}for _, r := range rows {",
        s"$pad\t${accVar(r.stateField)}[r.${pascal(key.fieldName)}] = r",
        s"$pad}"
      )
    def whereIn(column: String, keys: String): String =
      s".Where(\"? IN (?)\", bun.Ident(\"$column\"), bun.In($keys))"
    // The projected values feeding a derived load: nullable source fields
    // ride as pointers, so they filter and deref into the key set.
    def keySetLines(access: String, nullable: Boolean, pad: String) =
      if nullable then
        List(
          s"${pad}if $access != nil {",
          s"$pad\tkeySet[*$access] = true",
          s"$pad}"
        )
      else List(s"${pad}keySet[$access] = true")
    def keyedLoad(r: StatePlan.RelationPlan, key: ProfiledField, pad: String): List[String] =
      s"${pad}keys := sortedKeys(keySet)" ::
        s"${pad}if len(keys) > 0 {" ::
        loadInto(r, key, whereIn(key.columnName, "keys"), s"$pad\t") :::
        List(s"$pad}")
    def stepLines(r: StatePlan.RelationPlan, key: ProfiledField, shape: StepShape): List[String] =
      val rel = r.stateField
      val sel = selVar(rel)
      shape match
        case StepShape.FullLoad =>
          s"\tif scopeHas($sel, hydrationSrc{kind: \"full\"}) {" ::
            loadInto(r, key, "", "\t\t") ::: List("\t}")
        case StepShape.InputKeys =>
          val keysVar = camel(rel) + "InputKeys"
          s"\t$keysVar := scopeInputKeys($sel)" ::
            s"\tif len($keysVar) > 0 {" ::
            loadInto(r, key, whereIn(key.columnName, keysVar), "\t\t") ::: List("\t}")
        case StepShape.FieldOf(src, field) =>
          val srcField = relByField.get(src).toList
            .flatMap(_.entity.fields)
            .find(_.fieldName == field)
          val goType = srcField.map(_.domainType.stripPrefix("*")).getOrElse("string")
          s"\tif scopeHas($sel, hydrationSrc{kind: \"fieldOf\", src: \"$src\", a: \"$field\"}) {" ::
            s"\t\tkeySet := make(map[$goType]bool)" ::
            s"\t\tfor _, r := range ${accVar(src)} {" ::
            keySetLines(s"r.${pascal(field)}", srcField.exists(_.nullable), "\t\t\t") :::
            "\t\t}" ::
            keyedLoad(r, key, "\t\t") ::: List("\t}")
        case StepShape.ValueCol(src, column) =>
          val col =
            r.entity.fields.find(_.fieldName == column).map(_.columnName).getOrElse(column)
          s"\tif scopeHas($sel, hydrationSrc{kind: \"valueCol\", src: \"$src\", a: \"$column\"}) && len(${accVar(src)}) > 0 {" ::
            s"\t\tkeys := sortedKeys(${accVar(src)})" ::
            loadInto(r, key, whereIn(col, "keys"), "\t\t") ::: List("\t}")
        case StepShape.Dependent(src, coll, elem) =>
          val srcEntity = relByField.get(src).map(_.entity)
          val collField = srcEntity.toList.flatMap(_.fields).find(_.fieldName == coll)
          val nested = collField
            .flatMap(f => srcEntity.flatMap(e => kindFor(profiled, e.entityName, f)))
            .collect { case Kind.EntitySetOf(en) => en }
            .flatMap(nestedByName.get)
          nested.toList.flatMap { ne =>
            val byID      = camel(ne.entityName) + "ByID"
            val elemField = ne.fields.find(_.fieldName == elem)
            val goType    = elemField.map(_.domainType.stripPrefix("*")).getOrElse("string")
            s"\tif scopeHas($sel, hydrationSrc{kind: \"dependent\", src: \"$src\", a: \"$coll\", b: \"$elem\"}) {" ::
              s"\t\tkeySet := make(map[$goType]bool)" ::
              s"\t\tfor _, r := range ${accVar(src)} {" ::
              s"\t\t\tfor _, x := range r.${pascal(coll)} {" ::
              s"\t\t\t\tif elem, ok := $byID[x]; ok {" ::
              keySetLines(s"elem.${pascal(elem)}", elemField.exists(_.nullable), "\t\t\t\t\t") :::
              "\t\t\t\t}" ::
              "\t\t\t}" ::
              "\t\t}" ::
              keyedLoad(r, key, "\t\t") ::: List("\t}")
          }
    def entityValue(r: StatePlan.RelationPlan, sink: StringBuilder): String =
      r.valueField match
        case Some(vf) =>
          val (pre, expr) =
            toDafnyParts(kindFor(profiled, r.entity.entityName, vf), vf, "r", "\t\t\t")
          pre.foreach(l => sink ++= s"$l\n")
          expr
        case None =>
          val parts = r.entity.fields
            .map(f => toDafnyParts(kindFor(profiled, r.entity.entityName, f), f, "r", "\t\t\t"))
          parts.flatMap(_._1).foreach(l => sink ++= s"$l\n")
          val args = parts.map(p => s"\t\t\t\t${p._2},").mkString("\n")
          s"dafnykernel.Companion_${r.entity.entityName}_.Create_${r.entity.entityName}_(\n$args\n\t\t\t)"

    val hydrate = new StringBuilder
    hydrate ++= "\thydrated := make(map[string]hydrationSel)\n"
    if planned.relations.nonEmpty then
      hydrate ++= "\tif scope == nil {\n"
      scopeAssign("scope = ", "\t\t", "map[string][]hydrationSrc", hydrateDefault)
        .foreach(l => hydrate ++= s"$l\n")
      hydrate ++= "\t}\n"
    for (r, key) <- keyedRelations.sortBy(_._1.stateField) do
      hydrate ++= s"\t${selVar(r.stateField)} := scope[\"${r.stateField}\"]\n"
      hydrate ++= s"\t${accVar(r.stateField)} := make(map[${goKeyType(key)}]models.${r.entity.modelClassName})\n"
    for ne <- nestedEntities do
      hydrate ++= s"\t${idsVar(ne, pre = false)} := make([]int64, 0)\n"
    // Seq-valued state loads whole or not at all (a seq present in scope is
    // always {{kind: "full"}}); rows ordered by the serial pk are the seq.
    // Loads sit before the schedule so a seq owner's id union can key the
    // nested selects; the Dafny values construct with the keyed relations
    // below.
    for r <- seqRelations do
      val rows = relRows(r)
      hydrate ++= s"\t$rows := make([]models.${r.entity.modelClassName}, 0)\n"
      hydrate ++= s"\tif _, ok := scope[\"${r.stateField}\"]; ok {\n"
      hydrate ++= s"\t\tif err := db.NewSelect().Model(&$rows).Order(\"id ASC\").Scan(ctx); err != nil {\n"
      hydrate ++= "\t\t\treturn nil, nil, err\n\t\t}\n"
      for (f, ne) <- nestedRefs(r) do
        hydrate ++= s"\t\tfor _, r := range $rows {\n"
        hydrate ++= s"\t\t\t${idsVar(ne, pre = false)} = append(${idsVar(ne, pre = false)}, r.${pascal(f.fieldName)}...)\n"
        hydrate ++= "\t\t}\n"
      hydrate ++= "\t}\n"
    def nestedLoadLines(): Unit =
      for ne <- nestedEntities do
        val byID = camel(ne.entityName) + "ByID"
        val rows = rowsVar(ne)
        val ids  = idsVar(ne, pre = false)
        hydrate ++= s"\t$rows := make([]models.${ne.modelClassName}, 0)\n"
        hydrate ++= s"\tif len($ids) > 0 {\n"
        hydrate ++= s"\t\tif err := db.NewSelect().Model(&$rows)${whereIn("id", ids)}.Scan(ctx); err != nil {\n"
        hydrate ++= "\t\t\treturn nil, nil, err\n\t\t}\n"
        hydrate ++= "\t}\n"
        hydrate ++= s"\t$byID := make(map[int64]models.${ne.modelClassName}, len($rows))\n"
        hydrate ++= s"\tfor _, r := range $rows {\n\t\t$byID[r.ID] = r\n\t}\n"
    // Owner relations that only ever load as seqs still need the nested rows
    // before their construction; without a keyed owner step the schedule has
    // no anchor, so the load sits right after the seq loads.
    if lastOwnerIdx.isEmpty then nestedLoadLines()
    for ((rel, shape, _), idx) <- unionSteps.zipWithIndex do
      val r = relByField(rel)
      r.keyField.foreach(key => stepLines(r, key, shape).foreach(l => hydrate ++= s"$l\n"))
      // The id union across a nested owner's accumulated rows keys the
      // nested selects, so it collects after the owner's last load step.
      if ownerRels(rel) && lastStepIdx.get(rel).contains(idx) then
        for (f, ne) <- nestedRefs(r) do
          hydrate ++= s"\tfor _, r := range ${accVar(rel)} {\n"
          hydrate ++= s"\t\t${idsVar(ne, pre = false)} = append(${idsVar(ne, pre = false)}, r.${pascal(f.fieldName)}...)\n"
          hydrate ++= "\t}\n"
      if lastOwnerIdx.contains(idx) then nestedLoadLines()
    // Each relation's Dafny value constructs once, after its last scheduled
    // step, from whatever the steps accumulated; the hydrated record keeps
    // the actually-loaded keys so persist can confine its delete scans.
    for (r, rKey) <- keyedRelations.sortBy(_._1.stateField) do
      val rel     = r.stateField
      val builder = camel(rel) + "Builder"
      hydrate ++= s"\tif len(${selVar(rel)}) > 0 {\n"
      hydrate ++= s"\t\t$builder := _dafny.NewMapBuilder()\n"
      hydrate ++= s"\t\tfor _, k := range sortedKeys(${accVar(rel)}) {\n"
      hydrate ++= s"\t\t\tr := ${accVar(rel)}[k]\n"
      val value = entityValue(r, hydrate)
      hydrate ++= s"\t\t\t$builder.Add(${keyToDafny(rKey, "r")}, $value)\n"
      hydrate ++= "\t\t}\n"
      hydrate ++= s"\t\tst.${stateFieldName(rel)} = $builder.ToMap()\n"
      hydrate ++= s"\t\tif scopeHas(${selVar(rel)}, hydrationSrc{kind: \"full\"}) {\n"
      hydrate ++= s"\t\t\thydrated[\"$rel\"] = hydrationSel{full: true}\n"
      hydrate ++= "\t\t} else {\n"
      hydrate ++= s"\t\t\thydrated[\"$rel\"] = hydrationSel{keys: anyKeys(${accVar(rel)})}\n"
      hydrate ++= "\t\t}\n"
      hydrate ++= "\t}\n"
    for r <- seqRelations do
      val elems = camel(r.stateField) + "Elems"
      hydrate ++= s"\tif _, ok := scope[\"${r.stateField}\"]; ok {\n"
      hydrate ++= s"\t\t$elems := make([]interface{}, 0, len(${relRows(r)}))\n"
      hydrate ++= s"\t\tfor _, r := range ${relRows(r)} {\n"
      val parts = r.entity.fields
        .map(f => toDafnyParts(kindFor(profiled, r.entity.entityName, f), f, "r", "\t\t\t"))
      parts.flatMap(_._1).foreach(l => hydrate ++= s"$l\n")
      val args = parts.map(p => s"\t\t\t\t${p._2},").mkString("\n")
      hydrate ++= s"\t\t\t$elems = append($elems, dafnykernel.Companion_${r.entity.entityName}_.Create_${r.entity.entityName}_(\n$args\n\t\t\t))\n"
      hydrate ++= "\t\t}\n"
      hydrate ++= s"\t\tst.${stateFieldName(r.stateField)} = _dafny.SeqOf($elems...)\n"
      hydrate ++= s"\t\thydrated[\"${r.stateField}\"] = hydrationSel{full: true}\n"
      hydrate ++= "\t}\n"

    def collectIds(rows: String, refs: List[(ProfiledField, ProfiledEntity)], pre: Boolean) =
      refs.flatMap { (f, ne) =>
        List(
          s"\t\tfor _, r := range $rows {",
          s"\t\t\t${idsVar(ne, pre)} = append(${idsVar(ne, pre)}, r.${pascal(f.fieldName)}...)",
          "\t\t}"
        )
      }
    def persistLoadLines(r: StatePlan.RelationPlan, rows: String): List[String] =
      r.keyField.toList.flatMap { key =>
        List(
          "\t\tif sel.full {",
          s"\t\t\tif err := db.NewSelect().Model(&$rows).Scan(ctx); err != nil {",
          "\t\t\t\treturn err",
          "\t\t\t}",
          "\t\t} else if len(sel.keys) > 0 {",
          s"\t\t\tif err := db.NewSelect().Model(&$rows)${whereIn(key.columnName, "sel.keys")}.Scan(ctx); err != nil {",
          "\t\t\t\treturn err",
          "\t\t\t}",
          "\t\t}"
        )
      }

    val persist = new StringBuilder
    if planned.relations.nonEmpty then
      persist ++= "\tif hydrated == nil {\n"
      scopeAssign("hydrated = ", "\t\t", "map[string]hydrationSel", persistDefault)
        .foreach(l => persist ++= s"$l\n")
      persist ++= "\t}\n"
    for ne <- nestedEntities do
      persist ++= s"\t${idsVar(ne, pre = true)} := make([]int64, 0)\n"
    for (r, rKey) <- planned.entityRowRelations.flatMap(r => r.keyField.map(r -> _)) do
      val e        = r.entity
      val rows     = rowsVar(e)
      val keyGo    = pascal(rKey.fieldName)
      val existing = Naming.toCamelCase(e.entityName, Naming.CamelStrategy.Plain) + "Existing"
      val keyType  = rKey.domainType
      // A full hydration scans the whole table for stale rows; a keyed one
      // confines the scan to the keys hydrate actually loaded, so rows the
      // request never saw cannot be judged stale. An empty key set loads no
      // rows: the post map upserts in full and nothing deletes.
      persist ++= s"\tif sel, ok := hydrated[\"${r.stateField}\"]; ok {\n"
      persist ++= s"\t\t$rows := make([]models.${e.modelClassName}, 0)\n"
      persistLoadLines(r, rows).foreach(l => persist ++= s"$l\n")
      persist ++= s"\t\t$existing := make(map[$keyType]models.${e.modelClassName}, len($rows))\n"
      persist ++= s"\t\tfor _, r := range $rows {\n\t\t\t$existing[r.$keyGo] = r\n\t\t}\n"
      // Nested ids collect before the upsert loop rewrites these rows: the
      // nested delete scan may only judge rows the hydrated owners referenced
      // in their pre state.
      collectIds(rows, nestedFieldsOf(e), pre = true).foreach(l => persist ++= s"$l\n")
      val seen = Naming.toCamelCase(e.entityName, Naming.CamelStrategy.Plain) + "Seen"
      val iter = Naming.toCamelCase(e.entityName, Naming.CamelStrategy.Plain) + "It"
      persist ++= s"\t\t$seen := make(map[$keyType]bool)\n"
      persist ++= s"\t\t$iter := st.${stateFieldName(r.stateField)}.Items().Iterator()\n"
      persist ++= s"\t\tfor tu, ok := $iter(); ok; tu, ok = $iter() {\n"
      persist ++= "\t\t\tpair := tu.(_dafny.Tuple)\n"
      val keyExpr = keyType match
        case "string" => "dafnykernel.StringFromDafny((*pair.IndexInt(0)).(_dafny.Sequence))"
        case _        => "dafnykernel.IntFromDafny((*pair.IndexInt(0)).(_dafny.Int))"
      persist ++= s"\t\t\tkey := $keyExpr\n"
      persist ++= s"\t\t\tvalue := (*pair.IndexInt(1)).(dafnykernel.${e.entityName})\n"
      persist ++= s"\t\t\t$seen[key] = true\n"
      persist ++= s"\t\t\trow, exists := $existing[key]\n"
      // The key column stays immutable on updates (the row was fetched by it,
      // and rewriting it from the Dafny value could desync row identity from
      // the map key); inserts set it from the value like every other field.
      for f <- e.fields if f.fieldName != rKey.fieldName do
        val (preF, exprF) = fromDafnyParts(kindFor(profiled, e.entityName, f), f, "value", "\t\t\t")
        preF.foreach(l => persist ++= s"$l\n")
        persist ++= s"\t\t\trow.${pascal(f.fieldName)} = $exprF\n"
      persist ++= "\t\t\tif exists {\n"
      persist ++= "\t\t\t\tif _, err := db.NewUpdate().Model(&row).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\t\treturn err\n\t\t\t\t}\n"
      persist ++= "\t\t\t} else {\n"
      val (preK, exprK) =
        fromDafnyParts(kindFor(profiled, e.entityName, rKey), rKey, "value", "\t\t\t\t")
      preK.foreach(l => persist ++= s"$l\n")
      persist ++= s"\t\t\t\trow.${pascal(rKey.fieldName)} = $exprK\n"
      persist ++= "\t\t\t\tif _, err := db.NewInsert().Model(&row).Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\t\treturn err\n\t\t\t\t}\n"
      persist ++= "\t\t\t}\n"
      persist ++= "\t\t}\n"
      persist ++= s"\t\tfor key := range $existing {\n"
      persist ++= s"\t\t\tif !$seen[key] {\n"
      persist ++= s"\t\t\t\trow := $existing[key]\n"
      persist ++= "\t\t\t\tif _, err := db.NewDelete().Model(&row).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\t\treturn err\n\t\t\t\t}\n"
      persist ++= "\t\t\t}\n\t\t}\n"
      persist ++= "\t}\n"
    for r <- seqRelations do
      val e     = r.entity
      val snake = Naming.toCamelCase(e.entityName, Naming.CamelStrategy.Plain)
      persist ++= s"\tif _, ok := hydrated[\"${r.stateField}\"]; ok {\n"
      val seqNested = nestedFieldsOf(e)
      if seqNested.nonEmpty then
        val olds = snake + "Olds"
        persist ++= s"\t\t$olds := make([]models.${e.modelClassName}, 0)\n"
        persist ++= s"\t\tif err := db.NewSelect().Model(&$olds).Scan(ctx); err != nil {\n"
        persist ++= "\t\t\treturn err\n\t\t}\n"
        collectIds(olds, seqNested, pre = true).foreach(l => persist ++= s"$l\n")
      // Reinsert in seq order: the serial pk reassigns, which nothing
      // observes (the seq projection orders by it and exposes no id).
      persist ++= s"\t\tif _, err := db.NewDelete().Model((*models.${e.modelClassName})(nil)).Where(\"1 = 1\").Exec(ctx); err != nil {\n"
      persist ++= "\t\t\treturn err\n\t\t}\n"
      persist ++= s"\t\tit${snake} := _dafny.Iterate(st.${stateFieldName(r.stateField)})\n"
      persist ++= s"\t\tfor elem, ok := it${snake}(); ok; elem, ok = it${snake}() {\n"
      persist ++= s"\t\t\tvalue := elem.(dafnykernel.${e.entityName})\n"
      persist ++= s"\t\t\trow := models.${e.modelClassName}{}\n"
      for f <- e.fields do
        val (preF, exprF) = fromDafnyParts(kindFor(profiled, e.entityName, f), f, "value", "\t\t\t")
        preF.foreach(l => persist ++= s"$l\n")
        persist ++= s"\t\t\trow.${pascal(f.fieldName)} = $exprF\n"
      persist ++= "\t\t\tif _, err := db.NewInsert().Model(&row).Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\treturn err\n\t\t\t}\n"
      persist ++= "\t\t}\n"
      persist ++= "\t}\n"
    for ne <- nestedEntities do
      val camel = Naming.toCamelCase(ne.entityName, Naming.CamelStrategy.Plain)
      // Only whole-entity relations iterate as owner objects; a relation
      // projecting a single value field holds scalars, not rows. Skipped
      // owners hydrated nothing, so their post maps are empty and this pass
      // degenerates to a no-op when no owner is in scope.
      val owners =
        for
          r      <- planned.relations if r.valueField.isEmpty
          (f, e) <- nestedFieldsOf(r.entity) if e.entityName == ne.entityName
        yield (r, f)
      persist ++= s"\t${camel}Post := make(map[int64]dafnykernel.${ne.entityName})\n"
      for (r, f) <- owners do
        val ownerIt = s"it${pascal(r.stateField)}${pascal(f.fieldName)}"
        if r.isSeq then
          persist ++= s"\t$ownerIt := _dafny.Iterate(st.${stateFieldName(r.stateField)})\n"
          persist ++= s"\tfor elem, ok := $ownerIt(); ok; elem, ok = $ownerIt() {\n"
          persist ++= s"\t\towner := elem.(dafnykernel.${r.entity.entityName})\n"
        else
          persist ++= s"\t$ownerIt := st.${stateFieldName(r.stateField)}.Items().Iterator()\n"
          persist ++= s"\tfor tu, ok := $ownerIt(); ok; tu, ok = $ownerIt() {\n"
          persist ++= "\t\tpair := tu.(_dafny.Tuple)\n"
          persist ++= s"\t\towner := (*pair.IndexInt(1)).(dafnykernel.${r.entity.entityName})\n"
        persist ++= s"\t\tit${pascal(f.fieldName)}Elems := _dafny.Iterate(owner.Dtor_${dafnyName(f.fieldName)}())\n"
        persist ++= s"\t\tfor x, okX := it${pascal(f.fieldName)}Elems(); okX; x, okX = it${pascal(f.fieldName)}Elems() {\n"
        persist ++= s"\t\t\tli := x.(dafnykernel.${ne.entityName})\n"
        persist ++= s"\t\t\t${camel}Post[dafnykernel.IntFromDafny(li.Dtor_id())] = li\n"
        persist ++= "\t\t}\n"
        persist ++= "\t}\n"
      val rows     = rowsVar(ne)
      val existing = camel + "Existing"
      val preIds   = idsVar(ne, pre = true)
      persist ++= s"\t$rows := make([]models.${ne.modelClassName}, 0)\n"
      persist ++= s"\tif len($preIds) > 0 {\n"
      persist ++= s"\t\tif err := db.NewSelect().Model(&$rows).Where(\"? IN (?)\", bun.Ident(\"id\"), bun.In($preIds)).Scan(ctx); err != nil {\n"
      persist ++= "\t\t\treturn err\n\t\t}\n"
      persist ++= "\t}\n"
      persist ++= s"\t$existing := make(map[int64]models.${ne.modelClassName}, len($rows))\n"
      persist ++= s"\tfor _, r := range $rows {\n\t\t$existing[r.ID] = r\n\t}\n"
      persist ++= s"\tfor key, value := range ${camel}Post {\n"
      persist ++= s"\t\trow, exists := $existing[key]\n"
      for f <- ne.fields if f.fieldName != "id" do
        val (preF, exprF) = fromDafnyParts(kindFor(profiled, ne.entityName, f), f, "value", "\t\t")
        preF.foreach(l => persist ++= s"$l\n")
        persist ++= s"\t\trow.${pascal(f.fieldName)} = $exprF\n"
      persist ++= "\t\tif exists {\n"
      persist ++= "\t\t\tif _, err := db.NewUpdate().Model(&row).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\treturn err\n\t\t\t}\n"
      persist ++= "\t\t} else {\n"
      persist ++= "\t\t\trow.ID = key\n"
      persist ++= "\t\t\tif _, err := db.NewInsert().Model(&row).Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\treturn err\n\t\t\t}\n"
      persist ++= "\t\t}\n"
      persist ++= "\t}\n"
      persist ++= s"\tfor key := range $existing {\n"
      persist ++= s"\t\tif _, ok := ${camel}Post[key]; !ok {\n"
      persist ++= s"\t\t\trow := $existing[key]\n"
      persist ++= "\t\t\tif _, err := db.NewDelete().Model(&row).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\treturn err\n\t\t\t}\n"
      persist ++= "\t\t}\n\t}\n"
    if planned.scalars.nonEmpty then
      persist ++= "\tscalarRow := new(models.ServiceState)\n"
      persist ++= "\terr := db.NewSelect().Model(scalarRow).Where(\"id = 1\").Limit(1).Scan(ctx)\n"
      persist ++= "\tif err != nil {\n\t\treturn err\n\t}\n"
      for sc <- planned.scalars do
        persist ++= s"\tscalarRow.${pascal(sc.columnName)} = dafnykernel.IntFromDafny(st.${stateFieldName(sc.stateField)})\n"
      persist ++= "\tif _, err := db.NewUpdate().Model(scalarRow).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\treturn err\n\t}\n"

    val scalarHydrate = new StringBuilder
    if planned.scalars.nonEmpty then
      scalarHydrate ++= "\tscalarRow := new(models.ServiceState)\n"
      scalarHydrate ++= "\tif err := db.NewSelect().Model(scalarRow).Where(\"id = 1\").Limit(1).Scan(ctx); err != nil {\n"
      scalarHydrate ++= "\t\tif !errors.Is(err, sql.ErrNoRows) {\n"
      scalarHydrate ++= "\t\t\treturn nil, nil, err\n\t\t}\n"
      scalarHydrate ++= "\t} else {\n"
      for sc <- planned.scalars do
        scalarHydrate ++= s"\t\tst.${stateFieldName(sc.stateField)} = dafnykernel.IntToDafny(scalarRow.${pascal(sc.columnName)})\n"
      scalarHydrate ++= "\t}\n"

    val fmtImport = if nestedEntities.nonEmpty then "\n\t\"fmt\"" else ""
    val orderedImports =
      if keyedRelations.nonEmpty then "\t\"cmp\"\n\t\"context\"" else "\t\"context\""
    val slicesImport = if keyedRelations.nonEmpty then "\n\t\"slices\"" else ""
    val scheduleHelpers =
      val has =
        if keyedRelations.isEmpty then ""
        else
          s"""|
              |
              |func scopeHas(sel []hydrationSrc, want hydrationSrc) bool {
              |\tfor _, d := range sel {
              |\t\tif d.kind == want.kind && d.src == want.src && d.a == want.a && d.b == want.b {
              |\t\t\treturn true
              |\t\t}
              |\t}
              |\treturn false
              |}
              |
              |func sortedKeys[K cmp.Ordered, V any](m map[K]V) []K {
              |\tout := make([]K, 0, len(m))
              |\tfor k := range m {
              |\t\tout = append(out, k)
              |\t}
              |\tslices.Sort(out)
              |\treturn out
              |}
              |
              |func anyKeys[K cmp.Ordered, V any](m map[K]V) []any {
              |\tks := sortedKeys(m)
              |\tout := make([]any, 0, len(ks))
              |\tfor _, k := range ks {
              |\t\tout = append(out, k)
              |\t}
              |\treturn out
              |}""".stripMargin
      val inputs =
        if !hasInputSteps then ""
        else
          s"""|
              |
              |func scopeInputKeys(sel []hydrationSrc) []any {
              |\tkeys := make([]any, 0)
              |\tfor _, d := range sel {
              |\t\tif d.kind == "keys" {
              |\t\t\tkeys = append(keys, d.keys...)
              |\t\t}
              |\t}
              |\treturn keys
              |}""".stripMargin
      s"$has$inputs"
    val helperBlock =
      val h = s"$enumHelpers$nestedHelpers"
      if h.isEmpty then h else s"$h\n"
    s"""package services
       |
       |import (
       |$orderedImports$scalarImports$fmtImport$slicesImport$sortImport$timeImport
       |
       |\t"github.com/uptrace/bun"
       |
       |\tdafnykernel "$module/internal/dafnykernel"
       |\t_dafny "$module/internal/dafnykernel/dafny"
       |\t"$module/internal/models"
       |)
       |
       |// Which rows of each state relation an operation loads: a nil map
       |// hydrates every relation whole, an absent entry skips the relation in
       |// both directions, and each descriptor guards one block of the static
       |// load schedule (kind "full" scans the table, "keys" filters on the key
       |// column by input values, "fieldOf"/"dependent"/"valueCol" key the load
       |// off rows accumulated for src in an earlier wave).
       |type hydrationSrc struct {
       |\tkind string
       |\tsrc  string
       |\ta    string
       |\tb    string
       |\tkeys []any
       |}
       |
       |// What hydration actually loaded per relation: the whole table or a set
       |// of key values. persistState confines its stale-row delete scans to
       |// exactly this record; an empty keys set upserts the whole post map and
       |// deletes nothing.
       |type hydrationSel struct {
       |\tfull bool
       |\tkeys []any
       |}$scheduleHelpers
       |
       |${helperBlock}func hydrateState(ctx context.Context, db bun.IDB, scope map[string][]hydrationSrc) (*dafnykernel.ServiceState, map[string]hydrationSel, error) {
       |\tst := dafnykernel.MakeState()
       |${hydrate.toString}${scalarHydrate.toString}\treturn st, hydrated, nil
       |}
       |
       |func persistState(ctx context.Context, db bun.IDB, st *dafnykernel.ServiceState, hydrated map[string]hydrationSel) error {
       |${persist.toString}\treturn nil
       |}
       |""".stripMargin
