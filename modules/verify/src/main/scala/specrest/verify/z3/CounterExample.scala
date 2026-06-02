package specrest.verify.z3

import com.microsoft.z3.Expr as Z3AstExpr
import com.microsoft.z3.FuncDecl
import com.microsoft.z3.Model
import com.microsoft.z3.RatNum
import com.microsoft.z3.Sort
import specrest.ir.generated.SpecRestGenerated.*
import specrest.verify.DecodedConstant
import specrest.verify.DecodedCounterExample
import specrest.verify.DecodedEntity
import specrest.verify.DecodedEntityField
import specrest.verify.DecodedInput
import specrest.verify.DecodedRelation
import specrest.verify.DecodedRelationEntry
import specrest.verify.DecodedValue
import specrest.verify.IrValueDecoder

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
object Z3CounterExample:

  def decode(
      model: Model,
      sortMap: Map[String, Sort],
      funcMap: Map[String, FuncDecl[?]],
      artifact: TranslatorArtifact,
      ir: service_ir_full
  ): DecodedCounterExample =
    val rawToLabel  = mutable.LinkedHashMap.empty[String, String]
    val typingFails = mutable.ListBuffer.empty[String]
    val ctx         = tyctxFromService(ir)

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
                validateType(
                  evaluated,
                  field.sort,
                  ctx,
                  rawToLabel.toMap,
                  s"entity ${entity.name}.${field.name}",
                  typingFails
                )
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
        val pre =
          buildRelationSide(model, funcMap, r, candidates, "pre", rawToLabel, ctx, typingFails)
        val post = if artifact.hasPostState then
          List(buildRelationSide(
            model,
            funcMap,
            r,
            candidates,
            "post",
            rawToLabel,
            ctx,
            typingFails
          ))
        else Nil
        pre :: post
      case _: ArtifactStateEntry.Const => Nil

    val stateConstants = artifact.state.flatMap:
      case c: ArtifactStateEntry.Const =>
        val pre = buildConstantSide(model, funcMap, c, "pre", rawToLabel, ctx, typingFails)
        val post = if artifact.hasPostState then
          List(buildConstantSide(model, funcMap, c, "post", rawToLabel, ctx, typingFails))
        else Nil
        pre :: post
      case _: ArtifactStateEntry.Relation => Nil

    val inputs = artifact.inputs.flatMap: b =>
      funcMap.get(b.funcName).map: decl =>
        val evaluated = evalExpr(model, applyDecl(decl, Nil))
        validateType(
          evaluated,
          b.sort,
          ctx,
          rawToLabel.toMap,
          s"input ${b.name}",
          typingFails
        )
        DecodedInput(b.name, decodeValue(evaluated, rawToLabel))

    DecodedCounterExample(
      entities = entities,
      stateRelations = stateRelations,
      stateConstants = stateConstants,
      inputs = inputs,
      typingFailures = typingFails.toList
    )

  private def sortToTy(sort: Z3Sort, ctx: tyctx_ext[Unit]): Option[ty] = sort match
    case Z3Sort.Bool => Some(TBool())
    case Z3Sort.Int  => Some(TInt())
    case Z3Sort.Real => Some(TReal())
    case Z3Sort.Uninterp(name) =>
      if tc_enums(ctx).contains(name) then Some(TEnum(name))
      else if tc_entities(ctx)
          .exists(e => entName(e) == name)
      then
        Some(TEntity(name))
      else None
    case Z3Sort.SetOf(elem) => sortToTy(elem, ctx).map(TSet.apply)
    case Z3Sort.OptionOf(_) => None
    case Z3Sort.SeqOf(_)    => None
    case Z3Sort.Str         => None

  private def validateType(
      expr: Z3AstExpr[?],
      sort: Z3Sort,
      ctx: tyctx_ext[Unit],
      rawToLabel: Map[String, String],
      site: String,
      sink: mutable.ListBuffer[String]
  ): Unit =
    sortToTy(sort, ctx) match
      case None =>
        sink += s"$site: no ty for Z3 sort ${Z3Sort.key(sort)} (raw '${expr.toString.trim}')"
      case Some(expectedTy) =>
        IrValueDecoder.decodeZ3(expr, sort, rawToLabel) match
          case None =>
            sink += s"$site: could not decode '${expr.toString.trim}' at sort ${Z3Sort.key(sort)}"
          case Some(value) =>
            if !check_value_has_ty(ctx, value, expectedTy) then
              sink += s"$site: decoded value did not match expected type ${expectedTy}"

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
      rawToLabel: mutable.LinkedHashMap[String, String],
      ctx: tyctx_ext[Unit],
      sink: mutable.ListBuffer[String]
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
            validateType(
              k,
              relation.keySort,
              ctx,
              rawToLabel.toMap,
              s"relation ${relation.name}.$side key",
              sink
            )
            validateType(
              mappedTo,
              relation.valueSort,
              ctx,
              rawToLabel.toMap,
              s"relation ${relation.name}.$side value",
              sink
            )
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
      rawToLabel: mutable.LinkedHashMap[String, String],
      ctx: tyctx_ext[Unit],
      sink: mutable.ListBuffer[String]
  ): DecodedConstant =
    val funcName = if side == "pre" then entry.funcName else entry.funcNamePost
    val value = funcMap.get(funcName) match
      case Some(decl) =>
        val evaluated = evalExpr(model, applyDecl(decl, Nil))
        validateType(
          evaluated,
          entry.sort,
          ctx,
          rawToLabel.toMap,
          s"constant ${entry.name}.$side",
          sink
        )
        decodeValue(evaluated, rawToLabel)
      case None => DecodedValue("<unknown>", None)
    DecodedConstant(entry.name, side, value)

  // Z3 returns Real model values as RatNum; render them readably (n or n/d).
  private def formatReal(expr: Z3AstExpr[?]): Option[String] = expr match
    case r: RatNum =>
      val num = r.getBigIntNumerator
      val den = r.getBigIntDenominator
      Some(if den.compareTo(java.math.BigInteger.ONE) == 0 then num.toString else s"$num/$den")
    case _ => None

  private def decodeValue(
      expr: Z3AstExpr[?],
      rawToLabel: mutable.LinkedHashMap[String, String]
  ): DecodedValue =
    formatReal(expr) match
      case Some(r) => DecodedValue(r, None)
      case None =>
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
    scala.util.Try(model.getSortUniverse(sort).toList).toOption.getOrElse(Nil)

  private def applyDecl(decl: FuncDecl[?], args: List[Z3AstExpr[?]]): Z3AstExpr[?] =
    decl
      .asInstanceOf[FuncDecl[Sort]]
      .apply(args.toArray.asInstanceOf[Array[Z3AstExpr[Sort]]]*)

  private def evalExpr(model: Model, expr: Z3AstExpr[?]): Z3AstExpr[?] =
    model.eval(expr.asInstanceOf[Z3AstExpr[Sort]], true)
