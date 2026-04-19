package specrest.verify.z3

import specrest.ir.*
import specrest.verify.TranslatorError

import scala.collection.mutable

private val StringSortName = "String"

private enum StateMode:
  case Pre, Post

final private case class EntityInfo(
    sort: Z3Sort,
    fields: mutable.LinkedHashMap[String, (Z3Sort, String)]
)

final private case class EnumInfo(sort: Z3Sort, members: List[String])

final private case class PrimitiveAliasInfo(underlyingSort: Z3Sort, constraint: Expr)

final private case class TypeAliasInfo(sort: Z3Sort)

sealed private trait StateEntry

final private case class StateRelationInfo(
    keySort: Z3Sort,
    valueSort: Z3Sort,
    domFunc: String,
    mapFunc: String,
    domFuncPost: String,
    mapFuncPost: String,
    isTotal: Boolean
) extends StateEntry

final private case class StateConstInfo(
    sort: Z3Sort,
    funcName: String,
    funcNamePost: String
) extends StateEntry

final private class TranslateCtx:
  val sorts: mutable.LinkedHashMap[String, Z3Sort]              = mutable.LinkedHashMap.empty
  val funcs: mutable.LinkedHashMap[String, Z3FunctionDecl]      = mutable.LinkedHashMap.empty
  val assertions: mutable.ArrayBuffer[Z3Expr]                   = mutable.ArrayBuffer.empty
  val entities: mutable.LinkedHashMap[String, EntityInfo]       = mutable.LinkedHashMap.empty
  val enums: mutable.LinkedHashMap[String, EnumInfo]            = mutable.LinkedHashMap.empty
  val typeAliases: mutable.LinkedHashMap[String, TypeAliasInfo] = mutable.LinkedHashMap.empty
  val primitiveAliases: mutable.LinkedHashMap[String, PrimitiveAliasInfo] =
    mutable.LinkedHashMap.empty
  val state: mutable.LinkedHashMap[String, StateEntry]        = mutable.LinkedHashMap.empty
  val matchesIds: mutable.LinkedHashMap[String, Int]          = mutable.LinkedHashMap.empty
  val stringLitIds: mutable.LinkedHashMap[String, Int]        = mutable.LinkedHashMap.empty
  val cardinalityNames: mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty
  val skolemIds: mutable.LinkedHashMap[String, Int]           = mutable.LinkedHashMap.empty
  val inputs: mutable.ArrayBuffer[ArtifactBinding]            = mutable.ArrayBuffer.empty
  val outputs: mutable.ArrayBuffer[ArtifactBinding]           = mutable.ArrayBuffer.empty
  var hasPostState: Boolean                                   = false
  var stateMode: StateMode                                    = StateMode.Pre

  def declareSort(sort: Z3Sort): Unit = sort match
    case Z3Sort.Uninterp(_) =>
      val k = Z3Sort.key(sort)
      if !sorts.contains(k) then sorts(k) = sort
    case Z3Sort.SetOf(elem) => declareSort(elem)
    case _                  => ()

  def declareFunc(decl: Z3FunctionDecl): Unit =
    if !funcs.contains(decl.name) then
      funcs(decl.name) = decl
      decl.argSorts.foreach(declareSort)
      declareSort(decl.resultSort)

  def matchesNameFor(pattern: String): String =
    matchesIds.get(pattern) match
      case Some(id) => s"matches_$id"
      case None =>
        val id = matchesIds.size
        matchesIds(pattern) = id
        s"matches_$id"

  def stringLitNameFor(value: String): String =
    stringLitIds.get(value) match
      case Some(id) => s"str_$id"
      case None =>
        val id = stringLitIds.size
        stringLitIds(value) = id
        s"str_$id"

  def stringLitCount: Int = stringLitIds.size

  def cardinalityNameFor(targetName: String, mode: StateMode = StateMode.Pre): String =
    val key = if mode == StateMode.Post then s"${targetName}__post" else targetName
    cardinalityNames.get(key) match
      case Some(name) => name
      case None =>
        val name =
          if mode == StateMode.Post then s"card_${targetName}_post" else s"card_$targetName"
        cardinalityNames(key) = name
        name

  def freshSkolem(prefix: String): String =
    val count = skolemIds.getOrElse(prefix, 0)
    skolemIds(prefix) = count + 1
    s"${prefix}_$count"

object Translator:

  def translate(ir: ServiceIR): Z3Script =
    val ctx = new TranslateCtx
    declareBase(ctx, ir)
    for inv <- ir.invariants do emitTopLevelInvariant(ctx, inv)
    finalizeScript(ctx)

  def translateOperationRequires(ir: ServiceIR, op: OperationDecl): Z3Script =
    val ctx = new TranslateCtx
    declareBase(ctx, ir)
    val env = declareOperationInputs(ctx, op)
    for req <- op.requires do ctx.assertions += translateExpr(ctx, req, env)
    finalizeScript(ctx)

  def translateOperationEnabled(ir: ServiceIR, op: OperationDecl): Z3Script =
    val ctx = new TranslateCtx
    declareBase(ctx, ir)
    for inv <- ir.invariants do emitTopLevelInvariant(ctx, inv)
    val env = declareOperationInputs(ctx, op)
    for req <- op.requires do ctx.assertions += translateExpr(ctx, req, env)
    finalizeScript(ctx)

  def translateOperationPreservation(
      ir: ServiceIR,
      op: OperationDecl,
      inv: InvariantDecl
  ): Z3Script =
    val ctx = new TranslateCtx
    ctx.hasPostState = true
    declareBase(ctx, ir)
    ir.state.foreach(s => declareStatePostState(ctx, s))
    val env = declareOperationInputs(ctx, op)
    declareOperationOutputs(ctx, op, env)
    for preInv <- ir.invariants do ctx.assertions += translateExpr(ctx, preInv.expr, env)
    for req    <- op.requires do ctx.assertions += translateExpr(ctx, req, env)
    for ens    <- op.ensures do ctx.assertions += translateEnsuresClause(ctx, ens, env)
    synthesizeFrame(ctx, ir.state, op, env)
    synthesizeCardinalityAxioms(ctx, ir.state, op)
    val postInv = withStateMode(ctx, StateMode.Post, () => translateExpr(ctx, inv.expr, env))
    ctx.assertions += Z3Expr.Not(postInv).withSpan(inv.span)
    finalizeScript(ctx)

  private def declareBase(ctx: TranslateCtx, ir: ServiceIR): Unit =
    for e <- ir.enums do declareEnum(ctx, e)
    for t <- ir.typeAliases do declareTypeAlias(ctx, t)
    for e <- ir.entities do declareEntity(ctx, e)
    ir.state.foreach(s => declareState(ctx, s))
    for t <- ir.typeAliases do emitTypeAliasConstraint(ctx, t)
    for e <- ir.entities do emitEntityAssertions(ctx, e)

  private def finalizeScript(ctx: TranslateCtx): Z3Script =
    emitStringLiteralDistinctness(ctx)
    Z3Script(
      sorts = ctx.sorts.values.toList.sortBy(Z3Sort.key),
      funcs = ctx.funcs.values.toList.sortBy(_.name.toLowerCase),
      assertions = ctx.assertions.toList,
      artifact = buildArtifact(ctx)
    )

  private def buildArtifact(ctx: TranslateCtx): TranslatorArtifact =
    val entities = ctx.entities.toList.map: (name, info) =>
      val fields = info.fields.toList.map: (fn, f) =>
        ArtifactEntityField(fn, f._1, f._2)
      ArtifactEntity(name, info.sort, fields)
    val enums = ctx.enums.toList.map: (name, info) =>
      val members = info.members.map(m => ArtifactEnumMember(m, s"${name}_$m"))
      ArtifactEnum(name, info.sort, members)
    val state = ctx.state.toList.map:
      case (name, r: StateRelationInfo) =>
        ArtifactStateEntry.Relation(
          name,
          r.keySort,
          r.valueSort,
          r.domFunc,
          r.mapFunc,
          r.domFuncPost,
          r.mapFuncPost
        )
      case (name, c: StateConstInfo) =>
        ArtifactStateEntry.Const(name, c.sort, c.funcName, c.funcNamePost)
    TranslatorArtifact(
      entities = entities,
      enums = enums,
      state = state,
      inputs = ctx.inputs.toList,
      outputs = ctx.outputs.toList,
      hasPostState = ctx.hasPostState
    )

  private def declareOperationInputs(
      ctx: TranslateCtx,
      op: OperationDecl
  ): mutable.Map[String, Z3Expr] =
    val env = mutable.Map.empty[String, Z3Expr]
    for input <- op.inputs do
      val sort     = sortForType(ctx, input.typeExpr)
      val funcName = s"input_${op.name}_${input.name}"
      if !ctx.funcs.contains(funcName) then
        ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      env(input.name) = Z3Expr.App(funcName, Nil)
      ctx.inputs += ArtifactBinding(input.name, funcName, sort)
      maybeAssertInputRefinement(ctx, input, funcName)
    env

  private def maybeAssertInputRefinement(
      ctx: TranslateCtx,
      input: ParamDecl,
      funcName: String
  ): Unit =
    input.typeExpr match
      case TypeExpr.NamedType(n, _) =>
        ctx.primitiveAliases.get(n).foreach: alias =>
          val env = mutable.Map.empty[String, Z3Expr]
          env("value") = Z3Expr.App(funcName, Nil)
          ctx.assertions += translateExpr(ctx, alias.constraint, env)
      case _ => ()

  private def declareOperationOutputs(
      ctx: TranslateCtx,
      op: OperationDecl,
      env: mutable.Map[String, Z3Expr]
  ): Unit =
    for out <- op.outputs do
      val sort     = sortForType(ctx, out.typeExpr)
      val funcName = s"output_${op.name}_${out.name}"
      if !ctx.funcs.contains(funcName) then
        ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      env(out.name) = Z3Expr.App(funcName, Nil)
      ctx.outputs += ArtifactBinding(out.name, funcName, sort)
      out.typeExpr match
        case TypeExpr.NamedType(n, _) =>
          ctx.primitiveAliases.get(n).foreach: alias =>
            val refineEnv = mutable.Map.empty[String, Z3Expr]
            refineEnv("value") = Z3Expr.App(funcName, Nil)
            ctx.assertions += translateExpr(ctx, alias.constraint, refineEnv)
        case _ => ()

  private def declareEnum(ctx: TranslateCtx, e: EnumDecl): Unit =
    val sort = Z3Sort.Uninterp(e.name)
    ctx.declareSort(sort)
    ctx.enums(e.name) = EnumInfo(sort, e.values)
    val memberConsts = e.values.map: v =>
      val funcName = s"${e.name}_$v"
      ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      Z3Expr.App(funcName, Nil)
    if memberConsts.length >= 2 then
      val pairs = mutable.ArrayBuffer.empty[Z3Expr]
      for i <- memberConsts.indices; j <- (i + 1) until memberConsts.length do
        pairs += Z3Expr.Not(Z3Expr.Cmp(CmpOp.Eq, memberConsts(i), memberConsts(j)))
      val out = if pairs.length == 1 then pairs.head else Z3Expr.And(pairs.toList)
      ctx.assertions += out

  private def declareTypeAlias(ctx: TranslateCtx, t: TypeAliasDecl): Unit =
    val primitiveSort = primitiveUnderlyingSort(t)
    (primitiveSort, t.constraint) match
      case (Some(ps), Some(c)) =>
        ctx.primitiveAliases(t.name) = PrimitiveAliasInfo(ps, c)
      case _ =>
        val sort = primitiveSort.getOrElse(Z3Sort.Uninterp(t.name))
        ctx.declareSort(sort)
        ctx.typeAliases(t.name) = TypeAliasInfo(sort)

  private def primitiveUnderlyingSort(t: TypeAliasDecl): Option[Z3Sort] =
    t.typeExpr match
      case TypeExpr.NamedType("Int", _)  => Some(Z3Sort.Int)
      case TypeExpr.NamedType("Bool", _) => Some(Z3Sort.Bool)
      case _                             => None

  private def emitTypeAliasConstraint(ctx: TranslateCtx, t: TypeAliasDecl): Unit =
    t.constraint match
      case None => ()
      case Some(constraint) =>
        if ctx.primitiveAliases.contains(t.name) then ()
        else
          val sort    = ctx.typeAliases(t.name).sort
          val varName = s"self_${t.name}"
          val selfRef = Z3Expr.Var(varName, sort)
          val env     = mutable.Map.empty[String, Z3Expr]
          env("value") = selfRef
          val body = translateExpr(ctx, constraint, env)
          ctx.assertions += Z3Expr.Quantifier(
            QKind.ForAll,
            List(Z3Binding(varName, sort)),
            body
          )

  private def declareEntity(ctx: TranslateCtx, e: EntityDecl): Unit =
    val sort = Z3Sort.Uninterp(e.name)
    ctx.declareSort(sort)
    val fields = mutable.LinkedHashMap.empty[String, (Z3Sort, String)]
    for f <- e.fields do
      val fieldSort = sortForType(ctx, f.typeExpr)
      val funcName  = s"${e.name}_${f.name}"
      ctx.declareFunc(Z3FunctionDecl(funcName, List(sort), fieldSort))
      fields(f.name) = (fieldSort, funcName)
    ctx.entities(e.name) = EntityInfo(sort, fields)

  private def emitEntityAssertions(ctx: TranslateCtx, e: EntityDecl): Unit =
    val info    = ctx.entities(e.name)
    val varName = s"self_${e.name}"
    val selfRef = Z3Expr.Var(varName, info.sort)

    for f <- e.fields do
      val (fieldSort, fieldFunc) = info.fields(f.name)
      val aliasConstraint        = refinementConstraintFor(ctx, f.typeExpr)
      val fieldRead: Z3Expr      = Z3Expr.App(fieldFunc, List(selfRef))
      val bodies                 = mutable.ArrayBuffer.empty[Z3Expr]
      aliasConstraint.foreach: c =>
        val env = mutable.Map.empty[String, Z3Expr]
        env("value") = fieldRead
        bodies += translateExpr(ctx, c, env)
      f.constraint.foreach: c =>
        val env = mutable.Map.empty[String, Z3Expr]
        env("value") = fieldRead
        bodies += translateExpr(ctx, c, env)
      if bodies.nonEmpty then
        val body = if bodies.length == 1 then bodies.head else Z3Expr.And(bodies.toList)
        ctx.assertions += Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, info.sort)),
          body
        )
      val _ = fieldSort

    for inv <- e.invariants do
      val env = mutable.Map.empty[String, Z3Expr]
      env("self") = selfRef
      for (fname, (_, funcName)) <- info.fields do
        env(fname) = Z3Expr.App(funcName, List(selfRef))
      val body = translateExpr(ctx, inv, env)
      ctx.assertions += Z3Expr.Quantifier(
        QKind.ForAll,
        List(Z3Binding(varName, info.sort)),
        body
      )

  private def refinementConstraintFor(ctx: TranslateCtx, te: TypeExpr): Option[Expr] =
    te match
      case TypeExpr.NamedType(n, _) => ctx.primitiveAliases.get(n).map(_.constraint)
      case _                        => None

  private def declareState(ctx: TranslateCtx, state: StateDecl): Unit =
    for sf <- state.fields do declareStateField(ctx, sf)
    for sf <- state.fields do emitStateTotality(ctx, sf, StateMode.Pre)
    for sf <- state.fields do emitStateRefinement(ctx, sf, StateMode.Pre)

  private def declareStatePostState(ctx: TranslateCtx, state: StateDecl): Unit =
    for sf <- state.fields do declareStatePostFunc(ctx, sf)
    for sf <- state.fields do emitStateTotality(ctx, sf, StateMode.Post)
    for sf <- state.fields do emitStateRefinement(ctx, sf, StateMode.Post)

  private def declareStatePostFunc(ctx: TranslateCtx, sf: StateFieldDecl): Unit =
    ctx.state.get(sf.name) match
      case Some(r: StateRelationInfo) =>
        ctx.declareFunc(Z3FunctionDecl(r.domFuncPost, List(r.keySort), Z3Sort.Bool))
        ctx.declareFunc(Z3FunctionDecl(r.mapFuncPost, List(r.keySort), r.valueSort))
      case Some(c: StateConstInfo) =>
        ctx.declareFunc(Z3FunctionDecl(c.funcNamePost, Nil, c.sort))
      case None => ()

  private def emitStateRefinement(
      ctx: TranslateCtx,
      sf: StateFieldDecl,
      mode: StateMode
  ): Unit =
    ctx.state.get(sf.name) match
      case Some(c: StateConstInfo) =>
        refinementConstraintFor(ctx, sf.typeExpr).foreach: aliasConstraint =>
          val env = mutable.Map.empty[String, Z3Expr]
          env("value") = Z3Expr.App(constFuncFor(c, mode), Nil)
          ctx.assertions += translateExpr(ctx, aliasConstraint, env)
      case Some(r: StateRelationInfo) =>
        val (keyType, valueType) = sf.typeExpr match
          case TypeExpr.RelationType(f, _, t, _) => (f, t)
          case TypeExpr.MapType(k, v, _)         => (k, v)
          case _                                 => return
        val keyConstraint   = refinementConstraintFor(ctx, keyType)
        val valueConstraint = refinementConstraintFor(ctx, valueType)
        keyConstraint.foreach(c => emitRelationKeyRefinement(ctx, r, sf.name, c, mode))
        valueConstraint.foreach(c => emitRelationValueRefinement(ctx, r, sf.name, c, mode))
      case None => ()

  private def emitRelationKeyRefinement(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      fieldName: String,
      keyConstraint: Expr,
      mode: StateMode
  ): Unit =
    val suffix  = if mode == StateMode.Post then "_post" else ""
    val varName = s"k_${fieldName}_key$suffix"
    val keyVar  = Z3Expr.Var(varName, info.keySort)
    val env     = mutable.Map.empty[String, Z3Expr]
    env("value") = keyVar
    val pred = translateExpr(ctx, keyConstraint, env)
    ctx.assertions += Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, info.keySort)),
      Z3Expr.Implies(
        Z3Expr.App(domFuncFor(info, mode), List(keyVar)),
        pred
      )
    )

  private def emitRelationValueRefinement(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      fieldName: String,
      valueConstraint: Expr,
      mode: StateMode
  ): Unit =
    val suffix  = if mode == StateMode.Post then "_post" else ""
    val varName = s"k_$fieldName$suffix"
    val keyVar  = Z3Expr.Var(varName, info.keySort)
    val env     = mutable.Map.empty[String, Z3Expr]
    env("value") = Z3Expr.App(mapFuncFor(info, mode), List(keyVar))
    val body = translateExpr(ctx, valueConstraint, env)
    val guarded =
      if info.isTotal then body
      else
        Z3Expr.Implies(
          Z3Expr.App(domFuncFor(info, mode), List(keyVar)),
          body
        )
    ctx.assertions += Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, info.keySort)),
      guarded
    )

  private def declareStateField(ctx: TranslateCtx, sf: StateFieldDecl): Unit =
    sf.typeExpr match
      case TypeExpr.RelationType(from, mult, to, _) =>
        val keySort   = sortForType(ctx, from)
        val valueSort = sortForType(ctx, to)
        val domFunc   = s"${sf.name}_dom"
        val mapFunc   = s"${sf.name}_map"
        ctx.declareFunc(Z3FunctionDecl(domFunc, List(keySort), Z3Sort.Bool))
        ctx.declareFunc(Z3FunctionDecl(mapFunc, List(keySort), valueSort))
        ctx.state(sf.name) = StateRelationInfo(
          keySort,
          valueSort,
          domFunc,
          mapFunc,
          s"${sf.name}_dom_post",
          s"${sf.name}_map_post",
          isTotal = mult == Multiplicity.One
        )
      case TypeExpr.MapType(k, v, _) =>
        val keySort   = sortForType(ctx, k)
        val valueSort = sortForType(ctx, v)
        val domFunc   = s"${sf.name}_dom"
        val mapFunc   = s"${sf.name}_map"
        ctx.declareFunc(Z3FunctionDecl(domFunc, List(keySort), Z3Sort.Bool))
        ctx.declareFunc(Z3FunctionDecl(mapFunc, List(keySort), valueSort))
        ctx.state(sf.name) = StateRelationInfo(
          keySort,
          valueSort,
          domFunc,
          mapFunc,
          s"${sf.name}_dom_post",
          s"${sf.name}_map_post",
          isTotal = false
        )
      case _ =>
        val fieldSort = sortForType(ctx, sf.typeExpr)
        val funcName  = s"state_${sf.name}"
        ctx.declareFunc(Z3FunctionDecl(funcName, Nil, fieldSort))
        ctx.state(sf.name) = StateConstInfo(fieldSort, funcName, s"${funcName}_post")

  private def domFuncFor(info: StateRelationInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.domFuncPost else info.domFunc

  private def mapFuncFor(info: StateRelationInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.mapFuncPost else info.mapFunc

  private def constFuncFor(info: StateConstInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.funcNamePost else info.funcName

  private def emitStateTotality(ctx: TranslateCtx, sf: StateFieldDecl, mode: StateMode): Unit =
    ctx.state.get(sf.name) match
      case Some(r: StateRelationInfo) if r.isTotal =>
        val suffix  = if mode == StateMode.Post then "_post" else ""
        val varName = s"k_${sf.name}$suffix"
        val body    = Z3Expr.App(domFuncFor(r, mode), List(Z3Expr.Var(varName, r.keySort)))
        ctx.assertions += Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, r.keySort)),
          body
        )
      case _ => ()

  private def emitTopLevelInvariant(ctx: TranslateCtx, inv: InvariantDecl): Unit =
    val env = mutable.Map.empty[String, Z3Expr]
    ctx.assertions += translateExpr(ctx, inv.expr, env)

  private def emitStringLiteralDistinctness(ctx: TranslateCtx): Unit =
    val n = ctx.stringLitCount
    if n < 2 then return
    val consts = (0 until n).toList.map(i => Z3Expr.App(s"str_$i", Nil))
    val pairs  = mutable.ArrayBuffer.empty[Z3Expr]
    for i <- consts.indices; j <- (i + 1) until consts.length do
      pairs += Z3Expr.Cmp(CmpOp.Neq, consts(i), consts(j))
    val out = if pairs.length == 1 then pairs.head else Z3Expr.And(pairs.toList)
    ctx.assertions += out

  private def sortForType(ctx: TranslateCtx, te: TypeExpr): Z3Sort = te match
    case TypeExpr.NamedType(n, _)      => sortForNamedType(ctx, n)
    case TypeExpr.OptionType(inner, _) => sortForType(ctx, inner)
    case TypeExpr.SetType(e, _)        => Z3Sort.SetOf(sortForType(ctx, e))
    case TypeExpr.SeqType(e, _)        => Z3Sort.Uninterp(s"Seq_${sortNameOf(sortForType(ctx, e))}")
    case TypeExpr.MapType(k, v, _) =>
      Z3Sort.Uninterp(s"Map_${sortNameOf(sortForType(ctx, k))}_${sortNameOf(sortForType(ctx, v))}")
    case TypeExpr.RelationType(f, _, t, _) =>
      Z3Sort.Uninterp(s"Rel_${sortNameOf(sortForType(ctx, f))}_${sortNameOf(sortForType(ctx, t))}")

  private def sortNameOf(s: Z3Sort): String = s match
    case Z3Sort.Int         => "Int"
    case Z3Sort.Bool        => "Bool"
    case Z3Sort.Uninterp(n) => n
    case Z3Sort.SetOf(e)    => s"Set_${sortNameOf(e)}"

  private def sortForNamedType(ctx: TranslateCtx, name: String): Z3Sort = name match
    case "Int"    => Z3Sort.Int
    case "Bool"   => Z3Sort.Bool
    case "String" => Z3Sort.Uninterp(StringSortName)
    case _ =>
      ctx.entities.get(name).map(_.sort).orElse {
        ctx.enums.get(name).map(_.sort)
      }.orElse {
        ctx.primitiveAliases.get(name).map(_.underlyingSort)
      }.orElse {
        ctx.typeAliases.get(name).map(_.sort)
      }.getOrElse(Z3Sort.Uninterp(name))

  def translateExpr(ctx: TranslateCtx, expr: Expr, env: mutable.Map[String, Z3Expr]): Z3Expr =
    val out = translateExprRaw(ctx, expr, env)
    out.withSpan(expr.spanOpt)

  private def translateExprRaw(
      ctx: TranslateCtx,
      expr: Expr,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr match
    case Expr.IntLit(v, _)               => Z3Expr.IntLit(v)
    case Expr.BoolLit(v, _)              => Z3Expr.BoolLit(v)
    case Expr.StringLit(v, _)            => stringLiteralConst(ctx, v)
    case Expr.Identifier(name, _)        => resolveIdentifier(ctx, name, env)
    case b @ Expr.BinaryOp(_, _, _, _)   => translateBinaryOp(ctx, b.op, b.left, b.right, env)
    case u @ Expr.UnaryOp(_, _, _)       => translateUnaryOp(ctx, u, env)
    case q @ Expr.Quantifier(_, _, _, _) => translateQuantifier(ctx, q, env)
    case f @ Expr.FieldAccess(_, _, _)   => translateFieldAccess(ctx, f, env)
    case i @ Expr.Index(_, _, _)         => translateIndex(ctx, i, env)
    case c @ Expr.Call(_, _, _)          => translateCall(ctx, c, env)
    case m @ Expr.Matches(_, _, _)       => translateMatches(ctx, m, env)
    case e @ Expr.EnumAccess(_, _, _)    => translateEnumAccess(ctx, e)
    case Expr.Prime(inner, _) =>
      withStateMode(ctx, StateMode.Post, () => translateExpr(ctx, inner, env))
    case Expr.Pre(inner, _) =>
      withStateMode(ctx, StateMode.Pre, () => translateExpr(ctx, inner, env))
    case w @ Expr.With(_, _, _)                 => translateWith(ctx, w, env)
    case sc @ Expr.SetComprehension(_, _, _, _) => translateSetComprehension(sc)
    case sl @ Expr.SetLiteral(_, _)             => translateSetLiteral(ctx, sl, env)
    case l @ Expr.Let(_, _, _, _)               => translateLet(ctx, l, env)
    case other =>
      throw new TranslatorError(
        s"expression kind '${other.getClass.getSimpleName}' is not yet supported by the verifier"
      )

  private def withStateMode[T](ctx: TranslateCtx, mode: StateMode, fn: () => T): T =
    val saved = ctx.stateMode
    ctx.stateMode = mode
    try fn()
    finally ctx.stateMode = saved

  private def translateBinaryOp(
      ctx: TranslateCtx,
      op: BinOp,
      leftExpr: Expr,
      rightExpr: Expr,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    if op == BinOp.Eq || op == BinOp.Neq then
      val scEq = tryLowerSetComprehensionEquality(ctx, leftExpr, rightExpr, env)
      if scEq.isDefined then
        return if op == BinOp.Eq then scEq.get else Z3Expr.Not(scEq.get)
      val domEq = tryLowerDomEquality(ctx, leftExpr, rightExpr, negate = op == BinOp.Neq)
      if domEq.isDefined then return domEq.get
    if op == BinOp.In || op == BinOp.NotIn then
      val left = translateExpr(ctx, leftExpr, env)
      val mem  = membership(leftExpr, rightExpr, left, ctx, op, env)
      return if op == BinOp.In then mem else Z3Expr.Not(mem)
    val left  = translateExpr(ctx, leftExpr, env)
    val right = translateExpr(ctx, rightExpr, env)
    op match
      case BinOp.And     => Z3Expr.And(List(left, right))
      case BinOp.Or      => Z3Expr.Or(List(left, right))
      case BinOp.Implies => Z3Expr.Implies(left, right)
      case BinOp.Iff =>
        Z3Expr.And(List(Z3Expr.Implies(left, right), Z3Expr.Implies(right, left)))
      case BinOp.Eq | BinOp.Neq | BinOp.Lt | BinOp.Le | BinOp.Gt | BinOp.Ge =>
        val cmp = op match
          case BinOp.Eq  => CmpOp.Eq
          case BinOp.Neq => CmpOp.Neq
          case BinOp.Lt  => CmpOp.Lt
          case BinOp.Le  => CmpOp.Le
          case BinOp.Gt  => CmpOp.Gt
          case BinOp.Ge  => CmpOp.Ge
          case _         => CmpOp.Eq
        Z3Expr.Cmp(cmp, left, right)
      case BinOp.Add | BinOp.Sub | BinOp.Mul | BinOp.Div =>
        val leftSort  = inferSort(ctx, leftExpr, env, Some(left))
        val rightSort = inferSort(ctx, rightExpr, env, Some(right))
        val leftBad   = leftSort.exists(_ != Z3Sort.Int)
        val rightBad  = rightSort.exists(_ != Z3Sort.Int)
        if leftBad || rightBad then
          throw new TranslatorError(
            s"arithmetic operator '${binOpToTs(op)}' is only supported on integers (deferred for string/set arithmetic)"
          )
        val aop = op match
          case BinOp.Add => ArithOp.Add
          case BinOp.Sub => ArithOp.Sub
          case BinOp.Mul => ArithOp.Mul
          case BinOp.Div => ArithOp.Div
          case _         => ArithOp.Add
        Z3Expr.Arith(aop, List(left, right))
      case BinOp.Subset | BinOp.Union | BinOp.Intersect | BinOp.Diff =>
        ensureSetBinOpSorts(ctx, leftExpr, rightExpr, left, right, op, env)
        val sop = op match
          case BinOp.Subset    => SetOpKind.Subset
          case BinOp.Union     => SetOpKind.Union
          case BinOp.Intersect => SetOpKind.Intersect
          case BinOp.Diff      => SetOpKind.Diff
          case _               => SetOpKind.Union
        Z3Expr.SetBinOp(sop, left, right)
      case _ =>
        throw new TranslatorError(s"binary op '${binOpToTs(op)}' is not supported by the verifier")

  private def ensureSetBinOpSorts(
      ctx: TranslateCtx,
      leftExpr: Expr,
      rightExpr: Expr,
      left: Z3Expr,
      right: Z3Expr,
      op: BinOp,
      env: mutable.Map[String, Z3Expr]
  ): Unit =
    val leftSort  = inferSort(ctx, leftExpr, env, Some(left))
    val rightSort = inferSort(ctx, rightExpr, env, Some(right))
    val leftBad = leftSort.exists {
      case Z3Sort.SetOf(_) => false
      case _               => true
    }
    val rightBad = rightSort.exists {
      case Z3Sort.SetOf(_) => false
      case _               => true
    }
    if leftBad || rightBad then
      throw new TranslatorError(
        s"set operator '${binOpToTs(op)}' requires both operands to be sets"
      )
    (leftSort, rightSort) match
      case (Some(Z3Sort.SetOf(le)), Some(Z3Sort.SetOf(re))) if !Z3Sort.eq(le, re) =>
        throw new TranslatorError(
          s"set operator '${binOpToTs(op)}' requires both operands to have the same element sort; got $le and $re"
        )
      case _ => ()

  private def binOpToTs(op: BinOp): String = op match
    case BinOp.And       => "and"
    case BinOp.Or        => "or"
    case BinOp.Implies   => "implies"
    case BinOp.Iff       => "iff"
    case BinOp.Eq        => "="
    case BinOp.Neq       => "!="
    case BinOp.Lt        => "<"
    case BinOp.Gt        => ">"
    case BinOp.Le        => "<="
    case BinOp.Ge        => ">="
    case BinOp.In        => "in"
    case BinOp.NotIn     => "not_in"
    case BinOp.Subset    => "subset"
    case BinOp.Union     => "union"
    case BinOp.Intersect => "intersect"
    case BinOp.Diff      => "minus"
    case BinOp.Add       => "+"
    case BinOp.Sub       => "-"
    case BinOp.Mul       => "*"
    case BinOp.Div       => "/"

  private def membership(
      leftExpr: Expr,
      rightExpr: Expr,
      leftZ: Z3Expr,
      ctx: TranslateCtx,
      op: BinOp,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val _ = leftExpr
    resolveStateRelationReference(ctx, rightExpr) match
      case Some((info, mode)) =>
        Z3Expr.App(domFuncFor(info, mode), List(leftZ))
      case None =>
        rightExpr match
          case sc @ Expr.SetComprehension(_, _, _, _) =>
            membershipInComprehension(ctx, leftZ, sc, env)
          case Expr.SetLiteral(elements, _) =>
            membershipInSetLiteral(ctx, leftZ, elements, env)
          case _ =>
            val rightZ = translateExpr(ctx, rightExpr, env)
            inferSortOfZ3Expr(ctx, rightZ) match
              case Some(Z3Sort.SetOf(elemSort)) =>
                val leftSort = inferSortOfZ3Expr(ctx, leftZ)
                if leftSort.exists(s => !Z3Sort.eq(s, elemSort)) then
                  throw new TranslatorError(
                    s"membership operator '${binOpToTs(op)}' requires the left-hand side sort to match the set's element sort; got ${leftSort.get} against a set of $elemSort"
                  )
                Z3Expr.SetMember(leftZ, rightZ)
              case _ =>
                throw new TranslatorError(
                  s"membership operator '${binOpToTs(op)}' is only supported against a state relation, set literal, set comprehension, or set-valued expression"
                )

  private def membershipInSetLiteral(
      ctx: TranslateCtx,
      leftZ: Z3Expr,
      elements: List[Expr],
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    if elements.isEmpty then Z3Expr.BoolLit(false)
    else
      val translated = elements.map(e => translateExpr(ctx, e, env))
      val leftSort   = inferSortOfZ3Expr(ctx, leftZ)
      leftSort.foreach: ls =>
        val mismatchIdx = translated.indexWhere: t =>
          inferSortOfZ3Expr(ctx, t).exists(s => !Z3Sort.eq(s, ls))
        if mismatchIdx >= 0 then
          val got = inferSortOfZ3Expr(ctx, translated(mismatchIdx)).get
          throw new TranslatorError(
            s"set literal elements must match the membership LHS sort; expected $ls but found $got"
          )
      val eqs = translated.map(rhs => Z3Expr.Cmp(CmpOp.Eq, leftZ, rhs))
      if eqs.length == 1 then eqs.head else Z3Expr.Or(eqs)

  private def membershipInComprehension(
      ctx: TranslateCtx,
      leftZ: Z3Expr,
      sc: Expr.SetComprehension,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val resolved = resolveBindingDomain(
      ctx,
      QuantifierBinding(sc.variable, sc.domain, BindingKind.In)
    )
    val subEnv = env.clone()
    subEnv(sc.variable) = leftZ
    val predicate = translateExpr(ctx, sc.predicate, subEnv)
    resolved.guard match
      case None => predicate
      case Some(gFn) =>
        val guard      = gFn(sc.variable)
        val guardSubst = substituteVar(guard, sc.variable, leftZ)
        Z3Expr.And(List(guardSubst, predicate))

  private def substituteVar(expr: Z3Expr, varName: String, replacement: Z3Expr): Z3Expr = expr match
    case Z3Expr.Var(n, _, _) if n == varName => replacement
    case v: Z3Expr.Var                       => v
    case Z3Expr.App(f, args, sp) =>
      Z3Expr.App(f, args.map(a => substituteVar(a, varName, replacement)), sp)
    case Z3Expr.And(args, sp) =>
      Z3Expr.And(args.map(a => substituteVar(a, varName, replacement)), sp)
    case Z3Expr.Or(args, sp) =>
      Z3Expr.Or(args.map(a => substituteVar(a, varName, replacement)), sp)
    case Z3Expr.Not(arg, sp) =>
      Z3Expr.Not(substituteVar(arg, varName, replacement), sp)
    case Z3Expr.Implies(l, r, sp) =>
      Z3Expr.Implies(
        substituteVar(l, varName, replacement),
        substituteVar(r, varName, replacement),
        sp
      )
    case Z3Expr.Cmp(op, l, r, sp) =>
      Z3Expr.Cmp(
        op,
        substituteVar(l, varName, replacement),
        substituteVar(r, varName, replacement),
        sp
      )
    case Z3Expr.Arith(op, args, sp) =>
      Z3Expr.Arith(op, args.map(a => substituteVar(a, varName, replacement)), sp)
    case q @ Z3Expr.Quantifier(kind, bindings, body, sp) =>
      if bindings.exists(_.name == varName) then q
      else Z3Expr.Quantifier(kind, bindings, substituteVar(body, varName, replacement), sp)
    case Z3Expr.SetLit(elemSort, members, sp) =>
      Z3Expr.SetLit(elemSort, members.map(m => substituteVar(m, varName, replacement)), sp)
    case Z3Expr.SetMember(elem, set, sp) =>
      Z3Expr.SetMember(
        substituteVar(elem, varName, replacement),
        substituteVar(set, varName, replacement),
        sp
      )
    case Z3Expr.SetBinOp(op, l, r, sp) =>
      Z3Expr.SetBinOp(
        op,
        substituteVar(l, varName, replacement),
        substituteVar(r, varName, replacement),
        sp
      )
    case other => other

  private def resolveStateRelationReference(
      ctx: TranslateCtx,
      expr: Expr
  ): Option[(StateRelationInfo, StateMode)] = expr match
    case Expr.Identifier(name, _) =>
      ctx.state.get(name) match
        case Some(r: StateRelationInfo) => Some((r, ctx.stateMode))
        case _                          => None
    case Expr.Prime(inner, _) =>
      resolveStateRelationReference(ctx, inner).map((info, _) => (info, StateMode.Post))
    case Expr.Pre(inner, _) =>
      resolveStateRelationReference(ctx, inner).map((info, _) => (info, StateMode.Pre))
    case _ => None

  private def translateUnaryOp(
      ctx: TranslateCtx,
      expr: Expr.UnaryOp,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr.op match
    case UnOp.Not => Z3Expr.Not(translateExpr(ctx, expr.operand, env))
    case UnOp.Negate =>
      Z3Expr.Arith(ArithOp.Sub, List(Z3Expr.IntLit(0), translateExpr(ctx, expr.operand, env)))
    case UnOp.Cardinality => translateCardinality(ctx, expr.operand)
    case UnOp.Power =>
      throw new TranslatorError(
        "powerset operator is not decidable in first-order SMT; narrow the invariant to avoid powerset"
      )

  private def translateCardinality(ctx: TranslateCtx, operand: Expr): Z3Expr = operand match
    case Expr.Prime(Expr.Identifier(n, _), _) => cardinalityRefFor(ctx, n, StateMode.Post)
    case Expr.Pre(Expr.Identifier(n, _), _)   => cardinalityRefFor(ctx, n, StateMode.Pre)
    case Expr.Identifier(n, _)                => cardinalityRefFor(ctx, n, ctx.stateMode)
    case _ =>
      throw new TranslatorError(
        "cardinality '#expr' is only supported on state-relation identifiers"
      )

  private def cardinalityRefFor(
      ctx: TranslateCtx,
      targetName: String,
      mode: StateMode
  ): Z3Expr =
    ctx.state.get(targetName) match
      case Some(_: StateRelationInfo) =>
        val funcName = ctx.cardinalityNameFor(targetName, mode)
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, Z3Sort.Int))
          ctx.assertions += Z3Expr.Cmp(
            CmpOp.Ge,
            Z3Expr.App(funcName, Nil),
            Z3Expr.IntLit(0)
          )
        Z3Expr.App(funcName, Nil)
      case _ =>
        throw new TranslatorError(
          s"cardinality '#$targetName' requires a state relation; '$targetName' is not declared as one"
        )

  private def translateQuantifier(
      ctx: TranslateCtx,
      q: Expr.Quantifier,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val newEnv       = env.clone()
    val bindings     = mutable.ArrayBuffer.empty[Z3Binding]
    val domainGuards = mutable.ArrayBuffer.empty[Z3Expr]
    for b <- q.bindings do
      val resolved = resolveBindingDomain(ctx, b)
      bindings += Z3Binding(b.variable, resolved.sort)
      newEnv(b.variable) = Z3Expr.Var(b.variable, resolved.sort)
      resolved.guard.foreach(gFn => domainGuards += gFn(b.variable))
    val body        = translateExpr(ctx, q.body, newEnv)
    val guardedBody = applyGuards(q.quantifier, domainGuards.toList, body)
    mapQuantifier(q.quantifier) match
      case Right(zq) => Z3Expr.Quantifier(zq, bindings.toList, guardedBody)
      case Left(_) =>
        Z3Expr.Not(
          Z3Expr.Quantifier(QKind.Exists, bindings.toList, guardedBody)
        )

  private def mapQuantifier(q: QuantKind): Either[Unit, QKind] = q match
    case QuantKind.All                     => Right(QKind.ForAll)
    case QuantKind.Some | QuantKind.Exists => Right(QKind.Exists)
    case QuantKind.No                      => Left(())

  private def applyGuards(q: QuantKind, guards: List[Z3Expr], body: Z3Expr): Z3Expr =
    guards match
      case Nil => body
      case one :: Nil =>
        if q == QuantKind.All then Z3Expr.Implies(one, body)
        else Z3Expr.And(List(one, body))
      case xs =>
        val g = Z3Expr.And(xs)
        if q == QuantKind.All then Z3Expr.Implies(g, body)
        else Z3Expr.And(List(g, body))

  final private case class BindingResolution(sort: Z3Sort, guard: Option[String => Z3Expr])

  private def resolveBindingDomain(
      ctx: TranslateCtx,
      b: QuantifierBinding
  ): BindingResolution = b.domain match
    case Expr.Identifier(name, _) =>
      ctx.entities.get(name).map(e => BindingResolution(e.sort, None)).orElse:
        ctx.typeAliases.get(name).map(a => BindingResolution(a.sort, None))
      .orElse:
        ctx.primitiveAliases.get(name).map: pa =>
          val gFn: String => Z3Expr = vn =>
            val env = mutable.Map.empty[String, Z3Expr]
            env("value") = Z3Expr.Var(vn, pa.underlyingSort)
            translateExpr(ctx, pa.constraint, env)
          BindingResolution(pa.underlyingSort, Some(gFn))
      .orElse:
        ctx.enums.get(name).map(e => BindingResolution(e.sort, None))
      .orElse:
        ctx.state.get(name).collect:
          case r: StateRelationInfo =>
            val mode = ctx.stateMode
            val gFn: String => Z3Expr = vn =>
              Z3Expr.App(domFuncFor(r, mode), List(Z3Expr.Var(vn, r.keySort)))
            BindingResolution(r.keySort, Some(gFn))
      .getOrElse(BindingResolution(Z3Sort.Uninterp("Unknown"), None))
    case _ => BindingResolution(Z3Sort.Uninterp("Unknown"), None)

  private def translateFieldAccess(
      ctx: TranslateCtx,
      expr: Expr.FieldAccess,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val base     = translateExpr(ctx, expr.base, env)
    val baseSort = inferSort(ctx, expr.base, env, Some(base))
    baseSort match
      case Some(Z3Sort.Uninterp(name)) =>
        ctx.entities.get(name) match
          case Some(entity) =>
            entity.fields.get(expr.field) match
              case Some((_, funcName)) => Z3Expr.App(funcName, List(base))
              case None =>
                throw new TranslatorError(s"entity '$name' has no field '${expr.field}'")
          case None =>
            throw new TranslatorError(
              s"field access '.${expr.field}' requires an entity-typed base; inferred sort is not an entity"
            )
      case _ =>
        throw new TranslatorError(
          s"field access '.${expr.field}' requires an entity-typed base; inferred sort is not an entity"
        )

  private def translateIndex(
      ctx: TranslateCtx,
      expr: Expr.Index,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = resolveStateRelationReference(ctx, expr.base) match
    case Some((info, mode)) =>
      val key = translateExpr(ctx, expr.index, env)
      Z3Expr.App(mapFuncFor(info, mode), List(key))
    case None =>
      throw new TranslatorError(
        "indexing is only supported on state-relation references (including primed/pre-state forms); general map/sequence indexing is not supported"
      )

  private def translateCall(
      ctx: TranslateCtx,
      expr: Expr.Call,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    expr.callee match
      case Expr.Identifier(name, _) =>
        val args = expr.args.map(a => translateExpr(ctx, a, env))
        val argSorts =
          expr.args.map(a => inferSort(ctx, a, env, None).getOrElse(Z3Sort.Uninterp("Any")))
        val resultSort = callReturnSort(name)
        val funcName   = s"${name}_${argSortsMangled(argSorts)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, argSorts, resultSort))
        Z3Expr.App(funcName, args)
      case _ =>
        throw new TranslatorError(
          "higher-order call (non-identifier callee) is not supported by the verifier"
        )

  private def callReturnSort(name: String): Z3Sort = name match
    case "len"        => Z3Sort.Int
    case "isValidURI" => Z3Sort.Bool
    case _            => Z3Sort.Uninterp("Any")

  private def argSortsMangled(sorts: List[Z3Sort]): String =
    if sorts.isEmpty then "0" else sorts.map(sortNameOf).mkString("_")

  private def translateMatches(
      ctx: TranslateCtx,
      expr: Expr.Matches,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val arg = translateExpr(ctx, expr.expr, env)
    val argSort =
      inferSort(ctx, expr.expr, env, Some(arg)).getOrElse(Z3Sort.Uninterp(StringSortName))
    val baseName = ctx.matchesNameFor(expr.pattern)
    val funcName = s"${baseName}_${sortNameOf(argSort)}"
    if !ctx.funcs.contains(funcName) then
      ctx.declareFunc(Z3FunctionDecl(funcName, List(argSort), Z3Sort.Bool))
    Z3Expr.App(funcName, List(arg))

  private def translateLet(
      ctx: TranslateCtx,
      expr: Expr.Let,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val value  = translateExpr(ctx, expr.value, env)
    val newEnv = env.clone()
    newEnv(expr.variable) = value
    translateExpr(ctx, expr.body, newEnv)

  private def translateWith(
      ctx: TranslateCtx,
      expr: Expr.With,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val baseSort = inferSort(ctx, expr.base, env, None)
    baseSort match
      case Some(Z3Sort.Uninterp(name)) =>
        ctx.entities.get(name) match
          case None =>
            throw new TranslatorError(
              s"'with' expression requires an entity sort; '$name' is not an entity"
            )
          case Some(entity) =>
            val baseZ      = translateExpr(ctx, expr.base, env)
            val skolemName = ctx.freshSkolem(s"with_$name")
            ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, Z3Sort.Uninterp(name)))
            val skolemRef    = Z3Expr.App(skolemName, Nil)
            val updatedNames = expr.updates.map(_.name).toSet
            for (fname, (_, funcName)) <- entity.fields do
              if !updatedNames.contains(fname) then
                ctx.assertions += Z3Expr.Cmp(
                  CmpOp.Eq,
                  Z3Expr.App(funcName, List(skolemRef)),
                  Z3Expr.App(funcName, List(baseZ))
                )
            for update <- expr.updates do
              entity.fields.get(update.name) match
                case None =>
                  throw new TranslatorError(s"entity '$name' has no field '${update.name}'")
                case Some((_, funcName)) =>
                  val value = translateExpr(ctx, update.value, env)
                  ctx.assertions += Z3Expr.Cmp(
                    CmpOp.Eq,
                    Z3Expr.App(funcName, List(skolemRef)),
                    value
                  )
            skolemRef
      case _ =>
        throw new TranslatorError("'with' expression requires a known entity sort")

  private def translateSetComprehension(sc: Expr.SetComprehension): Z3Expr =
    val _ = sc
    throw new TranslatorError(
      "standalone set comprehensions as expressions are not supported because the binder's element sort typically does not match the receiver's declared type; use an inline membership form: `y in {x in S | P}`"
    )

  private def translateSetLiteral(
      ctx: TranslateCtx,
      sl: Expr.SetLiteral,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    if sl.elements.isEmpty then
      throw new TranslatorError(
        "empty set literal '{}' requires context to infer its element sort; use a non-empty set"
      )
    val translated  = sl.elements.map(e => translateExpr(ctx, e, env))
    val memberSorts = translated.map(t => inferSortOfZ3Expr(ctx, t))
    val unknownIdx  = memberSorts.indexWhere(_.isEmpty)
    if unknownIdx >= 0 then
      throw new TranslatorError(
        s"set literal element has unknown sort: ${sl.elements(unknownIdx)}"
      )
    val knownSorts  = memberSorts.flatten
    val elemSort    = knownSorts.head
    val mismatchIdx = knownSorts.indexWhere(s => !Z3Sort.eq(s, elemSort))
    if mismatchIdx >= 0 then
      throw new TranslatorError(
        s"set literal elements must all have the same sort; expected $elemSort but found ${knownSorts(mismatchIdx)}"
      )
    Z3Expr.SetLit(elemSort, translated)

  private def translateEnumAccess(ctx: TranslateCtx, expr: Expr.EnumAccess): Z3Expr =
    expr.base match
      case Expr.Identifier(enumName, _) =>
        val memberName = expr.member
        val funcName   = s"${enumName}_$memberName"
        if !ctx.funcs.contains(funcName) then
          val resultSort = ctx.enums.get(enumName).map(_.sort).getOrElse(Z3Sort.Uninterp(enumName))
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, resultSort))
        Z3Expr.App(funcName, Nil)
      case _ =>
        throw new TranslatorError("enum access base must be an identifier")

  private def stringLiteralConst(ctx: TranslateCtx, value: String): Z3Expr =
    val name = ctx.stringLitNameFor(value)
    if !ctx.funcs.contains(name) then
      ctx.declareFunc(Z3FunctionDecl(name, Nil, Z3Sort.Uninterp(StringSortName)))
    Z3Expr.App(name, Nil)

  private def resolveIdentifier(
      ctx: TranslateCtx,
      name: String,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    env.get(name) match
      case Some(bound) => bound
      case None =>
        ctx.state.get(name) match
          case Some(c: StateConstInfo) =>
            Z3Expr.App(constFuncFor(c, ctx.stateMode), Nil)
          case Some(r: StateRelationInfo) =>
            val suffix  = if ctx.stateMode == StateMode.Post then "_post" else ""
            val refName = s"state_${name}_ref$suffix"
            if !ctx.funcs.contains(refName) then
              val s = Z3Sort.Uninterp(s"Rel_${sortNameOf(r.keySort)}_${sortNameOf(r.valueSort)}")
              ctx.declareFunc(Z3FunctionDecl(refName, Nil, s))
            Z3Expr.App(refName, Nil)
          case None =>
            ctx.entities.get(name) match
              case Some(entity) =>
                val funcName = s"entity_${name}_ref"
                if !ctx.funcs.contains(funcName) then
                  ctx.declareFunc(Z3FunctionDecl(funcName, Nil, entity.sort))
                Z3Expr.App(funcName, Nil)
              case None =>
                val funcName = s"id_$name"
                if !ctx.funcs.contains(funcName) then
                  ctx.declareFunc(Z3FunctionDecl(funcName, Nil, Z3Sort.Uninterp("Any")))
                Z3Expr.App(funcName, Nil)

  private def inferSort(
      ctx: TranslateCtx,
      expr: Expr,
      env: mutable.Map[String, Z3Expr],
      translated: Option[Z3Expr]
  ): Option[Z3Sort] = expr match
    case Expr.Identifier(name, _) =>
      env.get(name).flatMap(inferSortOfZ3Expr(ctx, _)).orElse:
        ctx.state.get(name).collect {
          case c: StateConstInfo => c.sort
          case r: StateRelationInfo =>
            Z3Sort.Uninterp(s"Rel_${sortNameOf(r.keySort)}_${sortNameOf(r.valueSort)}")
        }.orElse {
          ctx.entities.get(name).map(_.sort)
        }.orElse {
          ctx.typeAliases.get(name).map(_.sort)
        }.orElse {
          ctx.enums.get(name).map(_.sort)
        }
    case Expr.Index(base, _, _) =>
      resolveStateRelationReference(ctx, base).map((info, _) => info.valueSort)
    case Expr.Prime(inner, _) => inferSort(ctx, inner, env, translated)
    case Expr.Pre(inner, _)   => inferSort(ctx, inner, env, translated)
    case Expr.FieldAccess(base, field, _) =>
      inferSort(ctx, base, env, None) match
        case Some(Z3Sort.Uninterp(name)) =>
          ctx.entities.get(name).flatMap(_.fields.get(field).map(_._1))
        case _ => None
    case Expr.IntLit(_, _)                         => Some(Z3Sort.Int)
    case Expr.BoolLit(_, _)                        => Some(Z3Sort.Bool)
    case Expr.StringLit(_, _)                      => Some(Z3Sort.Uninterp(StringSortName))
    case Expr.Call(Expr.Identifier(name, _), _, _) => Some(callReturnSort(name))
    case Expr.SetLiteral(elements, _) =>
      elements.iterator.flatMap(e => inferSort(ctx, e, env, None)).nextOption().map(Z3Sort.SetOf(_))
    case _ =>
      translated match
        case Some(Z3Expr.Var(_, sort, _)) => Some(sort)
        case _                            => None

  private def inferSortOfZ3Expr(ctx: TranslateCtx, e: Z3Expr): Option[Z3Sort] = e match
    case Z3Expr.Var(_, sort, _)    => Some(sort)
    case Z3Expr.IntLit(_, _)       => Some(Z3Sort.Int)
    case Z3Expr.BoolLit(_, _)      => Some(Z3Sort.Bool)
    case Z3Expr.App(func, _, _)    => ctx.funcs.get(func).map(_.resultSort)
    case Z3Expr.EmptySet(s, _)     => Some(Z3Sort.SetOf(s))
    case Z3Expr.SetLit(s, _, _)    => Some(Z3Sort.SetOf(s))
    case Z3Expr.SetMember(_, _, _) => Some(Z3Sort.Bool)
    case Z3Expr.SetBinOp(op, l, _, _) =>
      op match
        case SetOpKind.Subset                                       => Some(Z3Sort.Bool)
        case SetOpKind.Union | SetOpKind.Intersect | SetOpKind.Diff => inferSortOfZ3Expr(ctx, l)
    case _ => None

  private def tryLowerDomEquality(
      ctx: TranslateCtx,
      leftExpr: Expr,
      rightExpr: Expr,
      negate: Boolean
  ): Option[Z3Expr] =
    for
      leftDom  <- asDomOfStateRelation(ctx, leftExpr)
      rightDom <- asDomOfStateRelation(ctx, rightExpr)
      if Z3Sort.eq(leftDom._1.keySort, rightDom._1.keySort)
    yield
      val varName = s"k_domeq_${leftDom._1.domFunc}_${rightDom._1.domFunc}"
      val keyVar  = Z3Expr.Var(varName, leftDom._1.keySort)
      val lhsMem  = Z3Expr.App(domFuncFor(leftDom._1, leftDom._2), List(keyVar))
      val rhsMem  = Z3Expr.App(domFuncFor(rightDom._1, rightDom._2), List(keyVar))
      val body = Z3Expr.And(List(
        Z3Expr.Implies(lhsMem, rhsMem),
        Z3Expr.Implies(rhsMem, lhsMem)
      ))
      val forall = Z3Expr.Quantifier(
        QKind.ForAll,
        List(Z3Binding(varName, leftDom._1.keySort)),
        body
      )
      if negate then Z3Expr.Not(forall) else forall

  private def asDomOfStateRelation(
      ctx: TranslateCtx,
      expr: Expr
  ): Option[(StateRelationInfo, StateMode)] = expr match
    case Expr.Call(Expr.Identifier("dom", _), arg :: Nil, _) =>
      resolveStateRelationReference(ctx, arg)
    case _ => None

  private def tryLowerSetComprehensionEquality(
      ctx: TranslateCtx,
      leftExpr: Expr,
      rightExpr: Expr,
      env: mutable.Map[String, Z3Expr]
  ): Option[Z3Expr] =
    val pair: Option[(Expr, Expr.SetComprehension)] = (leftExpr, rightExpr) match
      case (sc: Expr.SetComprehension, other) => Some((other, sc))
      case (other, sc: Expr.SetComprehension) => Some((other, sc))
      case _                                  => None
    pair.map: (setExpr, sc) =>
      translateSetComprehensionEquality(ctx, setExpr, sc, env)

  private def translateSetComprehensionEquality(
      ctx: TranslateCtx,
      setExpr: Expr,
      sc: Expr.SetComprehension,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val setZ = translateExpr(ctx, setExpr, env)
    val elemSort = inferSortOfZ3Expr(ctx, setZ) match
      case Some(Z3Sort.SetOf(e)) => e
      case Some(other) =>
        throw new TranslatorError(
          s"set-comprehension equality requires a set-sorted receiver; got ${Z3Sort.key(other)}"
        )
      case None =>
        throw new TranslatorError(
          "set-comprehension equality requires a receiver with an inferrable set sort"
        )
    val resolved = resolveBindingDomain(
      ctx,
      QuantifierBinding(sc.variable, sc.domain, BindingKind.In)
    )
    if !Z3Sort.eq(resolved.sort, elemSort) then
      throw new TranslatorError(
        s"set-comprehension binder sort ${Z3Sort.key(resolved.sort)} does not match receiver element sort ${Z3Sort.key(elemSort)}"
      )
    // Use a fresh binder so we don't capture an outer identifier that shadows
    // `sc.variable` in setZ (which was translated against the outer env).
    val freshName = ctx.freshSkolem(s"sc_${sc.variable}")
    val varZ      = Z3Expr.Var(freshName, elemSort)
    val subEnv    = env.clone()
    subEnv(sc.variable) = varZ
    val predicate = translateExpr(ctx, sc.predicate, subEnv)
    val domAndPred = resolved.guard match
      case None      => predicate
      case Some(gFn) => Z3Expr.And(List(gFn(freshName), predicate))
    val memberInSet = Z3Expr.SetMember(varZ, setZ)
    val iff = Z3Expr.And(List(
      Z3Expr.Implies(memberInSet, domAndPred),
      Z3Expr.Implies(domAndPred, memberInSet)
    ))
    Z3Expr.Quantifier(QKind.ForAll, List(Z3Binding(freshName, elemSort)), iff)

  private def translateEnsuresClause(
      ctx: TranslateCtx,
      expr: Expr,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr match
    case Expr.BinaryOp(BinOp.Eq, l, r, _) =>
      tryLowerRelationEquality(ctx, l, r, env).getOrElse(translateExpr(ctx, expr, env))
    case _ => translateExpr(ctx, expr, env)

  private def tryLowerRelationEquality(
      ctx: TranslateCtx,
      leftExpr: Expr,
      rightExpr: Expr,
      env: mutable.Map[String, Z3Expr]
  ): Option[Z3Expr] =
    val leftRel  = resolveStateRelationReference(ctx, leftExpr)
    val rightRel = resolveStateRelationReference(ctx, rightExpr)
    (leftRel, rightRel) match
      case (Some(l), Some(r)) =>
        relationEqualityAxiom(l._1, l._2, r._1, r._2)
      case _ =>
        val primedRel = leftRel.orElse(rightRel)
        val otherExpr = if leftRel.isDefined then rightExpr else leftExpr
        primedRel.flatMap: pr =>
          lowerRelationRhs(ctx, otherExpr, pr._1, env).map(_(pr._1, pr._2))

  private type RelationRhsLowering = (StateRelationInfo, StateMode) => Z3Expr

  private def lowerRelationRhs(
      ctx: TranslateCtx,
      expr: Expr,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] =
    tryLowerSingleInsertRhs(ctx, expr, targetInfo, env)
      .orElse(tryLowerSingleMinusRhs(ctx, expr, targetInfo, env))

  private def tryLowerSingleInsertRhs(
      ctx: TranslateCtx,
      expr: Expr,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] = expr match
    case Expr.BinaryOp(op, left, right, _) if op == BinOp.Add || op == BinOp.Union =>
      resolveStateRelationReference(ctx, left).flatMap: base =>
        extractMapEntries(right).filter(_.nonEmpty).map: entries =>
          (lhsInfo: StateRelationInfo, lhsMode: StateMode) =>
            relationInsertionAxiom(
              ctx,
              lhsInfo,
              lhsMode,
              base._1,
              base._2,
              entries,
              env,
              targetInfo
            )
    case _ => None

  private def tryLowerSingleMinusRhs(
      ctx: TranslateCtx,
      expr: Expr,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] = expr match
    case Expr.BinaryOp(op, left, right, _) if op == BinOp.Sub || op == BinOp.Diff =>
      resolveStateRelationReference(ctx, left).flatMap: base =>
        extractKeySet(right).filter(_.nonEmpty).map: keys =>
          (lhsInfo: StateRelationInfo, lhsMode: StateMode) =>
            relationDeletionAxiom(ctx, lhsInfo, lhsMode, base._1, base._2, keys, env, targetInfo)
    case _ => None

  final private case class KeyValueEntry(key: Expr, value: Expr)

  private def extractMapEntries(expr: Expr): Option[List[KeyValueEntry]] = expr match
    case Expr.MapLiteral(entries, _) => Some(entries.map(e => KeyValueEntry(e.key, e.value)))
    case _                           => None

  private def extractKeySet(expr: Expr): Option[List[Expr]] = expr match
    case Expr.SetLiteral(elements, _) => Some(elements)
    case Expr.MapLiteral(entries, _)  => Some(entries.map(_.key))
    case _                            => None

  private def relationEqualityAxiom(
      a: StateRelationInfo,
      aMode: StateMode,
      b: StateRelationInfo,
      bMode: StateMode
  ): Option[Z3Expr] =
    if !Z3Sort.eq(a.keySort, b.keySort) || !Z3Sort.eq(a.valueSort, b.valueSort) then None
    else
      val varName = s"k_releq_${a.domFunc}_${b.domFunc}"
      val keyVar  = Z3Expr.Var(varName, a.keySort)
      val aDom    = Z3Expr.App(domFuncFor(a, aMode), List(keyVar))
      val bDom    = Z3Expr.App(domFuncFor(b, bMode), List(keyVar))
      val aMap    = Z3Expr.App(mapFuncFor(a, aMode), List(keyVar))
      val bMap    = Z3Expr.App(mapFuncFor(b, bMode), List(keyVar))
      val body = Z3Expr.And(List(
        Z3Expr.And(List(
          Z3Expr.Implies(aDom, bDom),
          Z3Expr.Implies(bDom, aDom)
        )),
        Z3Expr.Implies(aDom, Z3Expr.Cmp(CmpOp.Eq, aMap, bMap))
      ))
      Some(Z3Expr.Quantifier(QKind.ForAll, List(Z3Binding(varName, a.keySort)), body))

  private def relationInsertionAxiom(
      ctx: TranslateCtx,
      lhs: StateRelationInfo,
      lhsMode: StateMode,
      base: StateRelationInfo,
      baseMode: StateMode,
      entries: List[KeyValueEntry],
      env: mutable.Map[String, Z3Expr],
      targetInfo: StateRelationInfo
  ): Z3Expr =
    val _       = targetInfo
    val varName = s"k_insert_${lhs.domFunc}"
    val keyVar  = Z3Expr.Var(varName, lhs.keySort)
    val translated = entries.map: e =>
      (translateExpr(ctx, e.key, env), translateExpr(ctx, e.value, env))
    val keyEqs   = translated.map((k, _) => Z3Expr.Cmp(CmpOp.Eq, keyVar, k))
    val anyKeyEq = if keyEqs.length == 1 then keyEqs.head else Z3Expr.Or(keyEqs)
    val lhsDom   = Z3Expr.App(domFuncFor(lhs, lhsMode), List(keyVar))
    val baseDom  = Z3Expr.App(domFuncFor(base, baseMode), List(keyVar))
    val domBody = Z3Expr.And(List(
      Z3Expr.Implies(lhsDom, Z3Expr.Or(List(baseDom, anyKeyEq))),
      Z3Expr.Implies(Z3Expr.Or(List(baseDom, anyKeyEq)), lhsDom)
    ))
    val lhsMap  = Z3Expr.App(mapFuncFor(lhs, lhsMode), List(keyVar))
    val baseMap = Z3Expr.App(mapFuncFor(base, baseMode), List(keyVar))
    val perEntry = translated.map: (k, v) =>
      Z3Expr.Implies(
        Z3Expr.Cmp(CmpOp.Eq, keyVar, k),
        Z3Expr.Cmp(CmpOp.Eq, lhsMap, v)
      )
    val fallthrough = Z3Expr.Implies(
      Z3Expr.And(List(Z3Expr.Not(anyKeyEq), baseDom)),
      Z3Expr.Cmp(CmpOp.Eq, lhsMap, baseMap)
    )
    val mapBody = Z3Expr.And(perEntry :+ fallthrough)
    Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, lhs.keySort)),
      Z3Expr.And(List(domBody, mapBody))
    )

  private def relationDeletionAxiom(
      ctx: TranslateCtx,
      lhs: StateRelationInfo,
      lhsMode: StateMode,
      base: StateRelationInfo,
      baseMode: StateMode,
      keyExprs: List[Expr],
      env: mutable.Map[String, Z3Expr],
      targetInfo: StateRelationInfo
  ): Z3Expr =
    val _         = targetInfo
    val varName   = s"k_delete_${lhs.domFunc}"
    val keyVar    = Z3Expr.Var(varName, lhs.keySort)
    val keys      = keyExprs.map(k => translateExpr(ctx, k, env))
    val keyEqs    = keys.map(k => Z3Expr.Cmp(CmpOp.Eq, keyVar, k))
    val anyKeyEq  = if keyEqs.length == 1 then keyEqs.head else Z3Expr.Or(keyEqs)
    val notAnyKey = Z3Expr.Not(anyKeyEq)
    val lhsDom    = Z3Expr.App(domFuncFor(lhs, lhsMode), List(keyVar))
    val baseDom   = Z3Expr.App(domFuncFor(base, baseMode), List(keyVar))
    val domBody = Z3Expr.And(List(
      Z3Expr.Implies(lhsDom, Z3Expr.And(List(baseDom, notAnyKey))),
      Z3Expr.Implies(Z3Expr.And(List(baseDom, notAnyKey)), lhsDom)
    ))
    val lhsMap  = Z3Expr.App(mapFuncFor(lhs, lhsMode), List(keyVar))
    val baseMap = Z3Expr.App(mapFuncFor(base, baseMode), List(keyVar))
    val mapBody = Z3Expr.Implies(
      Z3Expr.And(List(baseDom, notAnyKey)),
      Z3Expr.Cmp(CmpOp.Eq, lhsMap, baseMap)
    )
    Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, lhs.keySort)),
      Z3Expr.And(List(domBody, mapBody))
    )

  private def synthesizeFrame(
      ctx: TranslateCtx,
      state: Option[StateDecl],
      op: OperationDecl,
      env: mutable.Map[String, Z3Expr]
  ): Unit = state match
    case None => ()
    case Some(s) =>
      for sf <- s.fields do
        ctx.state.get(sf.name) match
          case None => ()
          case Some(info) =>
            val analysis = analyzeStateMention(op.ensures, sf.name)
            if !analysis.fullyReplaced then
              info match
                case c: StateConstInfo =>
                  if !analysis.touched then
                    ctx.assertions += Z3Expr.Cmp(
                      CmpOp.Eq,
                      Z3Expr.App(c.funcNamePost, Nil),
                      Z3Expr.App(c.funcName, Nil)
                    )
                case r: StateRelationInfo =>
                  if !analysis.touched then
                    relationEqualityAxiom(r, StateMode.Post, r, StateMode.Pre)
                      .foreach(ctx.assertions += _)
                    syncCardinalityFrameIfDeclared(ctx, sf.name)
                  else
                    emitPartialRelationFrame(ctx, r, sf.name, analysis, env, op)

  private def syncCardinalityFrameIfDeclared(ctx: TranslateCtx, stateName: String): Unit =
    val preCard  = ctx.cardinalityNameFor(stateName, StateMode.Pre)
    val postCard = ctx.cardinalityNameFor(stateName, StateMode.Post)
    if !ctx.funcs.contains(preCard) && !ctx.funcs.contains(postCard) then return
    ensureCardinalityDecl(ctx, preCard)
    ensureCardinalityDecl(ctx, postCard)
    ctx.assertions += Z3Expr.Cmp(
      CmpOp.Eq,
      Z3Expr.App(postCard, Nil),
      Z3Expr.App(preCard, Nil)
    )

  final private case class StateMentionAnalysis(
      touched: Boolean,
      fullyReplaced: Boolean,
      removedKeys: List[Expr],
      fieldUpdatedKeys: List[(Expr, Set[String])],
      hasUnclassifiedMention: Boolean
  )

  private def analyzeStateMention(
      ensures: List[Expr],
      stateName: String
  ): StateMentionAnalysis =
    var touched                = false
    var fullyReplaced          = false
    var hasUnclassifiedMention = false
    val removedKeys            = mutable.ArrayBuffer.empty[Expr]
    val fieldUpdatedKeys       = mutable.LinkedHashMap.empty[String, (Expr, mutable.Set[String])]

    for ens <- ensures do
      if exprMentionsPostState(ens, stateName) then
        touched = true
        if matchesFullReplacement(ens, stateName) then fullyReplaced = true
        else
          matchNotInPrimed(ens, stateName) match
            case Some(k) => removedKeys += k
            case None =>
              matchFieldUpdatePrimed(ens, stateName) match
                case Some(fu) =>
                  val keyJson = keyIdentity(fu.key)
                  fieldUpdatedKeys.get(keyJson) match
                    case Some((_, fields)) => fields += fu.field
                    case None =>
                      fieldUpdatedKeys(keyJson) = (fu.key, mutable.Set(fu.field))
                case None =>
                  if !matchesCardinalityConstraint(ens, stateName) then
                    hasUnclassifiedMention = true

    StateMentionAnalysis(
      touched = touched,
      fullyReplaced = fullyReplaced,
      removedKeys = removedKeys.toList,
      fieldUpdatedKeys = fieldUpdatedKeys.values.toList.map((k, fs) => (k, fs.toSet)),
      hasUnclassifiedMention = hasUnclassifiedMention
    )

  private def keyIdentity(expr: Expr): String = expr.toString

  private def matchesFullReplacement(expr: Expr, stateName: String): Boolean = expr match
    case Expr.BinaryOp(BinOp.Eq, l, r, _) =>
      referencesPrimedRelation(l, stateName) || referencesPrimedRelation(r, stateName)
    case _ => false

  private def matchNotInPrimed(expr: Expr, stateName: String): Option[Expr] = expr match
    case Expr.BinaryOp(BinOp.NotIn, l, r, _) if referencesPrimedRelation(r, stateName) =>
      Some(l)
    case _ => None

  final private case class FieldUpdateMatch(key: Expr, field: String)

  private def matchFieldUpdatePrimed(expr: Expr, stateName: String): Option[FieldUpdateMatch] =
    expr match
      case Expr.BinaryOp(BinOp.Eq, l, r, _) =>
        matchFieldUpdateSide(l, stateName).orElse(matchFieldUpdateSide(r, stateName))
      case _ => None

  private def matchFieldUpdateSide(side: Expr, stateName: String): Option[FieldUpdateMatch] =
    side match
      case Expr.FieldAccess(Expr.Index(base, index, _), field, _)
          if referencesPrimedRelation(base, stateName) =>
        Some(FieldUpdateMatch(index, field))
      case _ => None

  private def matchesCardinalityConstraint(expr: Expr, stateName: String): Boolean = expr match
    case Expr.BinaryOp(_, l, r, _) =>
      sideIsPrimeCardinality(l, stateName) || sideIsPrimeCardinality(r, stateName)
    case _ => false

  private def sideIsPrimeCardinality(side: Expr, stateName: String): Boolean = side match
    case Expr.UnaryOp(UnOp.Cardinality, Expr.Prime(Expr.Identifier(n, _), _), _) =>
      n == stateName
    case _ => false

  private def emitPartialRelationFrame(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      stateName: String,
      analysis: StateMentionAnalysis,
      env: mutable.Map[String, Z3Expr],
      op: OperationDecl
  ): Unit =
    if analysis.hasUnclassifiedMention then return
    val varName         = s"k_pf_$stateName"
    val keyVar          = Z3Expr.Var(varName, info.keySort)
    val domPre          = Z3Expr.App(info.domFunc, List(keyVar))
    val domPost         = Z3Expr.App(info.domFuncPost, List(keyVar))
    val mapPre          = Z3Expr.App(info.mapFunc, List(keyVar))
    val mapPost         = Z3Expr.App(info.mapFuncPost, List(keyVar))
    val removedKeyExprs = analysis.removedKeys.map(k => translateExpr(ctx, k, env))
    val fieldUpdateKeyExprs = analysis.fieldUpdatedKeys.map: (k, fs) =>
      (translateExpr(ctx, k, env), fs)
    val isRemoved      = anyEqual(keyVar, removedKeyExprs)
    val isFieldUpdated = anyEqual(keyVar, fieldUpdateKeyExprs.map(_._1))
    val domClause      = buildDomClause(domPre, domPost, isRemoved, isFieldUpdated)
    ctx.assertions += Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, info.keySort)),
      domClause
    )
    val mapClause = buildMapClause(domPre, mapPre, mapPost, isRemoved, isFieldUpdated)
    ctx.assertions += Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, info.keySort)),
      mapClause
    )
    emitUnmentionedFieldFrames(ctx, info, fieldUpdateKeyExprs)
    if analysis.removedKeys.nonEmpty && analysis.fieldUpdatedKeys.isEmpty then
      val guards = op.requires ++ op.ensures
      val allIn = analysis.removedKeys.forall(k =>
        hasMembershipSideCond(guards, k, stateName, BinOp.In)
      )
      if allIn then
        val preCardName  = ctx.cardinalityNameFor(stateName, StateMode.Pre)
        val postCardName = ctx.cardinalityNameFor(stateName, StateMode.Post)
        if ctx.funcs.contains(preCardName) || ctx.funcs.contains(postCardName) then
          ensureCardinalityDecl(ctx, preCardName)
          ensureCardinalityDecl(ctx, postCardName)
          ctx.assertions += Z3Expr.Cmp(
            CmpOp.Eq,
            Z3Expr.App(postCardName, Nil),
            Z3Expr.Arith(
              ArithOp.Sub,
              List(Z3Expr.App(preCardName, Nil), Z3Expr.IntLit(analysis.removedKeys.length.toLong))
            )
          )
    else if analysis.removedKeys.isEmpty && analysis.fieldUpdatedKeys.nonEmpty then
      syncCardinalityFrameIfDeclared(ctx, stateName)

  private def anyEqual(keyVar: Z3Expr, candidates: List[Z3Expr]): Option[Z3Expr] =
    candidates match
      case Nil      => None
      case c :: Nil => Some(Z3Expr.Cmp(CmpOp.Eq, keyVar, c))
      case xs       => Some(Z3Expr.Or(xs.map(c => Z3Expr.Cmp(CmpOp.Eq, keyVar, c))))

  private def buildDomClause(
      domPre: Z3Expr,
      domPost: Z3Expr,
      isRemoved: Option[Z3Expr],
      isFieldUpdated: Option[Z3Expr]
  ): Z3Expr =
    val pieces = mutable.ArrayBuffer.empty[Z3Expr]
    isFieldUpdated.foreach(fu => pieces += Z3Expr.Implies(fu, domPost))
    isRemoved match
      case Some(rm) =>
        val notRemoved = Z3Expr.Not(rm)
        pieces += Z3Expr.Implies(Z3Expr.And(List(notRemoved, domPre)), domPost)
        pieces += Z3Expr.Implies(
          Z3Expr.And(List(notRemoved, Z3Expr.Not(domPre))),
          Z3Expr.Not(domPost)
        )
        pieces += Z3Expr.Implies(rm, Z3Expr.Not(domPost))
      case None =>
        pieces += Z3Expr.Implies(domPre, domPost)
        pieces += Z3Expr.Implies(domPost, domPre)
    if pieces.length == 1 then pieces.head else Z3Expr.And(pieces.toList)

  private def buildMapClause(
      domPre: Z3Expr,
      mapPre: Z3Expr,
      mapPost: Z3Expr,
      isRemoved: Option[Z3Expr],
      isFieldUpdated: Option[Z3Expr]
  ): Z3Expr =
    val parts = mutable.ArrayBuffer[Z3Expr](domPre)
    isRemoved.foreach(rm => parts += Z3Expr.Not(rm))
    isFieldUpdated.foreach(fu => parts += Z3Expr.Not(fu))
    val guard = if parts.length == 1 then parts.head else Z3Expr.And(parts.toList)
    Z3Expr.Implies(guard, Z3Expr.Cmp(CmpOp.Eq, mapPost, mapPre))

  private def emitUnmentionedFieldFrames(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      updates: List[(Z3Expr, Set[String])]
  ): Unit =
    info.valueSort match
      case Z3Sort.Uninterp(name) =>
        ctx.entities.get(name).foreach: entity =>
          for (key, fields) <- updates do
            val mapPreAtKey  = Z3Expr.App(info.mapFunc, List(key))
            val mapPostAtKey = Z3Expr.App(info.mapFuncPost, List(key))
            for (fname, (_, funcName)) <- entity.fields do
              if !fields.contains(fname) then
                ctx.assertions += Z3Expr.Cmp(
                  CmpOp.Eq,
                  Z3Expr.App(funcName, List(mapPostAtKey)),
                  Z3Expr.App(funcName, List(mapPreAtKey))
                )
      case _ => ()

  private def ensureCardinalityDecl(ctx: TranslateCtx, funcName: String): Unit =
    if ctx.funcs.contains(funcName) then ()
    else
      ctx.declareFunc(Z3FunctionDecl(funcName, Nil, Z3Sort.Int))
      ctx.assertions += Z3Expr.Cmp(
        CmpOp.Ge,
        Z3Expr.App(funcName, Nil),
        Z3Expr.IntLit(0)
      )

  private def synthesizeCardinalityAxioms(
      ctx: TranslateCtx,
      state: Option[StateDecl],
      op: OperationDecl
  ): Unit = state match
    case None => ()
    case Some(s) =>
      for sf <- s.fields do
        ctx.state.get(sf.name) match
          case Some(_: StateRelationInfo) =>
            detectCardinalityDelta(op, sf.name).foreach: delta =>
              val preCard  = ctx.cardinalityNameFor(sf.name, StateMode.Pre)
              val postCard = ctx.cardinalityNameFor(sf.name, StateMode.Post)
              ensureCardinalityDecl(ctx, preCard)
              ensureCardinalityDecl(ctx, postCard)
              val preRef  = Z3Expr.App(preCard, Nil)
              val postRef = Z3Expr.App(postCard, Nil)
              val rhs =
                if delta == 0 then preRef
                else
                  Z3Expr.Arith(
                    if delta > 0 then ArithOp.Add else ArithOp.Sub,
                    List(preRef, Z3Expr.IntLit(math.abs(delta).toLong))
                  )
              ctx.assertions += Z3Expr.Cmp(CmpOp.Eq, postRef, rhs)
          case _ => ()

  private def detectCardinalityDelta(op: OperationDecl, relName: String): Option[Int] =
    val guards = op.requires ++ op.ensures
    op.ensures.iterator.flatMap: ens =>
      matchPrimedRelationEquality(ens, relName).toList.flatMap: primeEq =>
        if isIdentityRhs(primeEq.rhs, relName) then List(0)
        else
          insertDeltaWithSideCond(guards, primeEq.rhs, relName).map(identity).toList ++
            deleteDeltaWithSideCond(guards, primeEq.rhs, relName).map(-_).toList
    .nextOption()

  private def insertDeltaWithSideCond(
      guards: List[Expr],
      rhs: Expr,
      relName: String
  ): Option[Int] = rhs match
    case Expr.BinaryOp(op, left, right, _) if op == BinOp.Add || op == BinOp.Union =>
      if !referencesPreRelation(left, relName) then None
      else
        extractInsertKeys(right).flatMap: keys =>
          if keys.forall(k => hasMembershipSideCond(guards, k, relName, BinOp.NotIn)) then
            Some(keys.length)
          else None
    case _ => None

  private def deleteDeltaWithSideCond(
      guards: List[Expr],
      rhs: Expr,
      relName: String
  ): Option[Int] = rhs match
    case Expr.BinaryOp(op, left, right, _) if op == BinOp.Sub || op == BinOp.Diff =>
      if !referencesPreRelation(left, relName) then None
      else
        extractKeySet(right).flatMap: keys =>
          if keys.forall(k => hasMembershipSideCond(guards, k, relName, BinOp.In)) then
            Some(keys.length)
          else None
    case _ => None

  private def extractInsertKeys(expr: Expr): Option[List[Expr]] =
    extractMapEntries(expr).map(_.map(_.key)).orElse(extractKeySet(expr))

  private def hasMembershipSideCond(
      guards: List[Expr],
      key: Expr,
      relName: String,
      op: BinOp
  ): Boolean =
    guards.exists(g =>
      flattenAnds(g).exists(sub => matchesMembershipSideCond(sub, key, relName, op))
    )

  private def flattenAnds(expr: Expr): List[Expr] = expr match
    case Expr.BinaryOp(BinOp.And, l, r, _) => flattenAnds(l) ++ flattenAnds(r)
    case _                                 => List(expr)

  private def matchesMembershipSideCond(
      expr: Expr,
      key: Expr,
      relName: String,
      op: BinOp
  ): Boolean = expr match
    case Expr.BinaryOp(exprOp, l, r, _) if exprOp == op =>
      exprStructurallyEqual(l, key) && referencesPreRelation(r, relName)
    case _ => false

  private def exprStructurallyEqual(a: Expr, b: Expr): Boolean = a.toString == b.toString

  final private case class PrimedRelEq(rhs: Expr)

  private def matchPrimedRelationEquality(expr: Expr, relName: String): Option[PrimedRelEq] =
    expr match
      case Expr.BinaryOp(BinOp.Eq, l, r, _) =>
        if referencesPrimedRelation(l, relName) then Some(PrimedRelEq(r))
        else if referencesPrimedRelation(r, relName) then Some(PrimedRelEq(l))
        else None
      case _ => None

  private def referencesPrimedRelation(expr: Expr, relName: String): Boolean = expr match
    case Expr.Prime(Expr.Identifier(n, _), _) => n == relName
    case _                                    => false

  private def referencesPreRelation(expr: Expr, relName: String): Boolean = expr match
    case Expr.Pre(Expr.Identifier(n, _), _) => n == relName
    case Expr.Identifier(n, _)              => n == relName
    case _                                  => false

  private def isIdentityRhs(expr: Expr, relName: String): Boolean =
    referencesPreRelation(expr, relName)

  private def exprMentionsPostState(expr: Expr, stateName: String): Boolean =
    walkMentionsPost(expr, stateName, insidePrime = false)

  private def walkMentionsPost(expr: Expr, stateName: String, insidePrime: Boolean): Boolean =
    expr match
      case Expr.Prime(inner, _)  => walkMentionsPost(inner, stateName, insidePrime = true)
      case Expr.Pre(inner, _)    => walkMentionsPost(inner, stateName, insidePrime = false)
      case Expr.Identifier(n, _) => insidePrime && n == stateName
      case Expr.BinaryOp(_, l, r, _) =>
        walkMentionsPost(l, stateName, insidePrime) || walkMentionsPost(r, stateName, insidePrime)
      case Expr.UnaryOp(_, operand, _)  => walkMentionsPost(operand, stateName, insidePrime)
      case Expr.FieldAccess(base, _, _) => walkMentionsPost(base, stateName, insidePrime)
      case Expr.Index(base, idx, _) =>
        walkMentionsPost(base, stateName, insidePrime) ||
        walkMentionsPost(idx, stateName, insidePrime)
      case Expr.Call(_, args, _) =>
        args.exists(a => walkMentionsPost(a, stateName, insidePrime))
      case Expr.Quantifier(_, bindings, body, _) =>
        walkMentionsPost(body, stateName, insidePrime) ||
        bindings.exists(b => walkMentionsPost(b.domain, stateName, insidePrime))
      case Expr.With(base, updates, _) =>
        walkMentionsPost(base, stateName, insidePrime) ||
        updates.exists(u => walkMentionsPost(u.value, stateName, insidePrime))
      case Expr.If(c, t, e, _) =>
        walkMentionsPost(c, stateName, insidePrime) ||
        walkMentionsPost(t, stateName, insidePrime) ||
        walkMentionsPost(e, stateName, insidePrime)
      case Expr.Let(_, v, b, _) =>
        walkMentionsPost(v, stateName, insidePrime) ||
        walkMentionsPost(b, stateName, insidePrime)
      case Expr.SetComprehension(_, d, p, _) =>
        walkMentionsPost(d, stateName, insidePrime) ||
        walkMentionsPost(p, stateName, insidePrime)
      case Expr.Matches(inner, _, _) => walkMentionsPost(inner, stateName, insidePrime)
      case Expr.SomeWrap(inner, _)   => walkMentionsPost(inner, stateName, insidePrime)
      case Expr.MapLiteral(entries, _) =>
        entries.exists(e =>
          walkMentionsPost(e.key, stateName, insidePrime) ||
            walkMentionsPost(e.value, stateName, insidePrime)
        )
      case Expr.SetLiteral(elements, _) =>
        elements.exists(e => walkMentionsPost(e, stateName, insidePrime))
      case Expr.SeqLiteral(elements, _) =>
        elements.exists(e => walkMentionsPost(e, stateName, insidePrime))
      case Expr.Constructor(_, fields, _) =>
        fields.exists(f => walkMentionsPost(f.value, stateName, insidePrime))
      case _ => false
