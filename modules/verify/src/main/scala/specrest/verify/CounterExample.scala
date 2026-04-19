package specrest.verify

import com.microsoft.z3.{Expr as Z3AstExpr, FuncDecl, Model, Sort}
import scala.collection.mutable

final case class DecodedValue(display: String, entityLabel: Option[String])

final case class DecodedEntityField(name: String, value: DecodedValue)

final case class DecodedEntity(
    sortName: String,
    label: String,
    rawElement: String,
    fields: List[DecodedEntityField]
)

final case class DecodedRelationEntry(key: DecodedValue, value: DecodedValue)

final case class DecodedRelation(
    stateName: String,
    side: String,
    entries: List[DecodedRelationEntry]
)

final case class DecodedConstant(
    stateName: String,
    side: String,
    value: DecodedValue
)

final case class DecodedInput(name: String, value: DecodedValue)

final case class DecodedCounterExample(
    entities: List[DecodedEntity],
    stateRelations: List[DecodedRelation],
    stateConstants: List[DecodedConstant],
    inputs: List[DecodedInput]
)

object CounterExample:

  def decode(
      model: Model,
      sortMap: Map[String, Sort],
      funcMap: Map[String, FuncDecl[?]],
      artifact: TranslatorArtifact
  ): DecodedCounterExample =
    val rawToLabel = mutable.LinkedHashMap.empty[String, String]

    // enums first — the entity decoder uses rawToLabel for field values
    for e <- artifact.enums do
      for member <- e.members do
        funcMap.get(member.funcName).foreach: decl =>
          val evaluated = evalExpr(model, applyDecl(decl, Nil))
          rawToLabel(evaluated.toString) = s"${e.name}.${member.name}"

    val entities = artifact.entities.flatMap: entity =>
      val sortOpt = sortMap.get(Z3Sort.key(entity.sort))
      sortOpt match
        case None => Nil
        case Some(sort) =>
          safeSortUniverse(model, sort).zipWithIndex.map: (elem, idx) =>
            val raw   = elem.toString
            val label = s"${entity.name}#$idx"
            rawToLabel(raw) = label
            val fields = entity.fields.flatMap: field =>
              funcMap.get(field.funcName).map: decl =>
                val applied   = applyDecl(decl, List(elem))
                val evaluated = evalExpr(model, applied)
                DecodedEntityField(field.name, decodeValue(evaluated, rawToLabel))
            DecodedEntity(
              sortName = entity.name,
              label = label,
              rawElement = raw,
              fields = fields
            )

    val stateRelations = artifact.state.flatMap:
      case r: ArtifactStateEntry.Relation =>
        val universeKeys = sortMap.get(Z3Sort.key(r.keySort)) match
          case Some(s) => safeSortUniverse(model, s)
          case None    => Nil
        val candidates =
          if universeKeys.nonEmpty then universeKeys
          else inputsOfSort(model, funcMap, artifact.inputs, r.keySort)
        val pre = buildRelationSide(model, funcMap, r, candidates, "pre", rawToLabel)
        val post = if artifact.hasPostState then
          List(buildRelationSide(model, funcMap, r, candidates, "post", rawToLabel))
        else Nil
        pre :: post
      case _: ArtifactStateEntry.Const => Nil

    val stateConstants = artifact.state.flatMap:
      case c: ArtifactStateEntry.Const =>
        val pre = buildConstantSide(model, funcMap, c, "pre", rawToLabel)
        val post = if artifact.hasPostState then
          List(buildConstantSide(model, funcMap, c, "post", rawToLabel))
        else Nil
        pre :: post
      case _: ArtifactStateEntry.Relation => Nil

    val inputs = artifact.inputs.flatMap: b =>
      funcMap.get(b.funcName).map: decl =>
        val evaluated = evalExpr(model, applyDecl(decl, Nil))
        DecodedInput(b.name, decodeValue(evaluated, rawToLabel))

    DecodedCounterExample(
      entities = entities,
      stateRelations = stateRelations,
      stateConstants = stateConstants,
      inputs = inputs
    )

  def format(ce: DecodedCounterExample): String =
    val lines = List.newBuilder[String]
    if ce.inputs.nonEmpty then
      lines += "  inputs:"
      for inp <- ce.inputs do lines += s"    ${inp.name} = ${inp.value.display}"
    if ce.entities.nonEmpty then
      lines += "  entities:"
      for e <- ce.entities do
        val fieldStr = e.fields.map(f => s"${f.name} = ${f.value.display}").mkString(", ")
        lines += s"    ${e.label} { $fieldStr }"
    val pre  = ce.stateRelations.filter(_.side == "pre")
    val preC = ce.stateConstants.filter(_.side == "pre")
    if pre.nonEmpty || preC.nonEmpty then
      lines += "  pre-state:"
      for r <- pre do lines += formatRelation(r)
      for c <- preC do lines += s"    ${c.stateName} = ${c.value.display}"
    val post  = ce.stateRelations.filter(_.side == "post")
    val postC = ce.stateConstants.filter(_.side == "post")
    if post.nonEmpty || postC.nonEmpty then
      lines += "  post-state:"
      for r <- post do lines += formatRelation(r)
      for c <- postC do lines += s"    ${c.stateName}' = ${c.value.display}"
    lines.result().mkString("\n")

  private def formatRelation(r: DecodedRelation): String =
    val label = if r.side == "pre" then r.stateName else s"${r.stateName}'"
    if r.entries.isEmpty then s"    $label = {}"
    else
      val entryStrs = r.entries.map(e => s"${e.key.display} → ${e.value.display}").mkString(", ")
      s"    $label = { $entryStrs }"

  private def inputsOfSort(
      model: Model,
      funcMap: Map[String, FuncDecl[?]],
      inputs: List[ArtifactBinding],
      wantSort: Z3Sort
  ): List[Z3AstExpr[?]] =
    inputs.flatMap: b =>
      if Z3Sort.key(b.sort) != Z3Sort.key(wantSort) then None
      else funcMap.get(b.funcName).map(decl => evalExpr(model, applyDecl(decl, Nil)))

  private def buildRelationSide(
      model: Model,
      funcMap: Map[String, FuncDecl[?]],
      relation: ArtifactStateEntry.Relation,
      keyUniverse: List[Z3AstExpr[?]],
      side: String,
      rawToLabel: mutable.LinkedHashMap[String, String]
  ): DecodedRelation =
    val domFuncName = if side == "pre" then relation.domFunc else relation.domFuncPost
    val mapFuncName = if side == "pre" then relation.mapFunc else relation.mapFuncPost
    val entries = (funcMap.get(domFuncName), funcMap.get(mapFuncName)) match
      case (Some(domDecl), Some(mapDecl)) =>
        keyUniverse.flatMap: k =>
          val inDom = evalExpr(model, applyDecl(domDecl, List(k)))
          if inDom.toString != "true" then None
          else
            val mappedTo = evalExpr(model, applyDecl(mapDecl, List(k)))
            Some(
              DecodedRelationEntry(
                key = decodeValue(k, rawToLabel),
                value = decodeValue(mappedTo, rawToLabel)
              )
            )
      case _ => Nil
    DecodedRelation(relation.name, side, entries)

  private def buildConstantSide(
      model: Model,
      funcMap: Map[String, FuncDecl[?]],
      entry: ArtifactStateEntry.Const,
      side: String,
      rawToLabel: mutable.LinkedHashMap[String, String]
  ): DecodedConstant =
    val funcName = if side == "pre" then entry.funcName else entry.funcNamePost
    val value = funcMap.get(funcName) match
      case Some(decl) => decodeValue(evalExpr(model, applyDecl(decl, Nil)), rawToLabel)
      case None       => DecodedValue("<unknown>", None)
    DecodedConstant(entry.name, side, value)

  private def decodeValue(
      expr: Z3AstExpr[?],
      rawToLabel: mutable.LinkedHashMap[String, String]
  ): DecodedValue =
    val text = normalizeZ3Text(expr.toString)
    rawToLabel.get(text) match
      case Some(label) => DecodedValue(label, Some(label))
      case None =>
        if text == "true" || text == "false" then DecodedValue(text, None)
        else if text.matches("-?\\d+") then DecodedValue(text, None)
        else
          matchStringLiteral(text) match
            case Some(s) => DecodedValue(s"\"$s\"", None)
            case None    => DecodedValue(prettyUninterp(text), None)

  private val NegNum       = """^\(-\s+(\d+)\)$""".r
  private val StrLit       = """^str_(\d+)$""".r
  private val UninterpName = """^([A-Za-z_][\w]*)!val!(\d+)$""".r

  private def normalizeZ3Text(raw: String): String =
    NegNum.findFirstMatchIn(raw.trim) match
      case Some(m) => s"-${m.group(1)}"
      case None    => raw

  private def matchStringLiteral(text: String): Option[String] =
    StrLit.findFirstMatchIn(text).map(m => s"<string#${m.group(1)}>")

  private def prettyUninterp(text: String): String =
    UninterpName.findFirstMatchIn(text) match
      case Some(m) => s"${m.group(1)}#${m.group(2)}"
      case None    => text

  private def safeSortUniverse(model: Model, sort: Sort): List[Z3AstExpr[?]] =
    try
      val universe = model.getSortUniverse(sort)
      universe.toList
    catch case _: Throwable => Nil

  private def applyDecl(decl: FuncDecl[?], args: List[Z3AstExpr[?]]): Z3AstExpr[?] =
    decl
      .asInstanceOf[FuncDecl[Sort]]
      .apply(args.toArray.asInstanceOf[Array[Z3AstExpr[Sort]]]*)

  private def evalExpr(model: Model, expr: Z3AstExpr[?]): Z3AstExpr[?] =
    model.eval(expr.asInstanceOf[Z3AstExpr[Sort]], true)
