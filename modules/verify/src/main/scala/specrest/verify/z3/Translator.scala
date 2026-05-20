package specrest.verify.z3

import cats.effect.IO
import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable
import scala.util.boundary

private given CanEqual[smt_term, smt_term]   = CanEqual.derived
private given CanEqual[expr_full, expr_full] = CanEqual.derived

private type TranslateBoundary =
  boundary.Label[Either[VerifyError.Translator, Z3Script]]

private def fail(ctx: TranslateCtx, msg: String): Nothing =
  boundary.break(Left(VerifyError.Translator(msg)))(using ctx.bnd)

extension (e: expr_full)
  private def spanOpt: Option[span_t] = SpecRestGenerated.span_of(e)

private val StringSortName = "String"

private enum StateMode derives CanEqual:
  case Pre, Post

final private case class EntityInfo(
    sort: Z3Sort,
    fields: mutable.LinkedHashMap[String, (Z3Sort, String)]
)

final private case class EnumInfo(sort: Z3Sort, members: List[String])

final private case class PrimitiveAliasInfo(underlyingSort: Z3Sort, constraint: expr_full)

final private case class TypeAliasInfo(sort: Z3Sort)

sealed private trait StateEntry derives CanEqual

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

@SuppressWarnings(Array("org.wartremover.warts.Var"))
final private class TranslateCtx(val bnd: TranslateBoundary):
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
  val predicateNames: mutable.Set[String]                     = mutable.Set.empty
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

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Return",
    "org.wartremover.warts.OptionPartial"
  )
)
object Translator:

  def translate(ir: ServiceIRFull): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        declareBase(ctx, ir)
        for case inv: InvariantDeclFull <- ir.i do emitTopLevelInvariant(ctx, inv)
        Right(finalizeScript(ctx))
    }

  def translateOperationRequires(
      ir: ServiceIRFull,
      op: OperationDeclFull
  ): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        declareBase(ctx, ir)
        val env = declareOperationInputs(ctx, op)
        for req <- op.d do ctx.assertions += translateExpr(ctx, req, env)
        Right(finalizeScript(ctx))
    }

  def translateOperationEnabled(
      ir: ServiceIRFull,
      op: OperationDeclFull
  ): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        declareBase(ctx, ir)
        for case inv: InvariantDeclFull <- ir.i do emitTopLevelInvariant(ctx, inv)
        val env = declareOperationInputs(ctx, op)
        for req <- op.d do ctx.assertions += translateExpr(ctx, req, env)
        Right(finalizeScript(ctx))
    }

  def translateOperationPreservation(
      ir: ServiceIRFull,
      op: OperationDeclFull,
      inv: InvariantDeclFull
  ): IO[Either[VerifyError.Translator, Z3Script]] =
    IO.delay {
      boundary:
        val ctx = new TranslateCtx(summon[TranslateBoundary])
        ctx.hasPostState = true
        declareBase(ctx, ir)
        ir.f.foreach { case s: StateDeclFull => declareStatePostState(ctx, s) }
        val env = declareOperationInputs(ctx, op)
        declareOperationOutputs(ctx, op, env)
        for case preInv: InvariantDeclFull <- ir.i do
          ctx.assertions += translateExpr(ctx, preInv.b, env)
        for req <- op.d do ctx.assertions += translateExpr(ctx, req, env)
        for ens <- op.e do ctx.assertions += translateEnsuresClause(ctx, ens, env)
        synthesizeFrame(ctx, ir.f.collect { case s: StateDeclFull => s }, op, env)
        synthesizeCardinalityAxioms(ctx, ir.f.collect { case s: StateDeclFull => s }, op)
        val postInv = withStateMode(ctx, StateMode.Post, () => translateExpr(ctx, inv.b, env))
        ctx.assertions += Z3Expr.Not(postInv).withSpan(inv.c)
        Right(finalizeScript(ctx))
    }

  private def declareBase(ctx: TranslateCtx, ir: ServiceIRFull): Unit =
    ir.m.foreach { case p: PredicateDeclFull => ctx.predicateNames += p.a }
    for case e: EnumDeclFull <- ir.d do declareEnum(ctx, e)
    for case t: TypeAliasDeclFull <- ir.e do declareTypeAlias(ctx, t)
    for case e: EntityDeclFull <- ir.c do declareEntity(ctx, e)
    ir.f.foreach { case s: StateDeclFull => declareState(ctx, s) }
    for case t: TypeAliasDeclFull <- ir.e do emitTypeAliasConstraint(ctx, t)
    for case e: EntityDeclFull <- ir.c do emitEntityAssertions(ctx, e)

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
      op: OperationDeclFull
  ): mutable.Map[String, Z3Expr] =
    val env = mutable.Map.empty[String, Z3Expr]
    for case input @ ParamDeclFull(inputName, inputType, _) <- op.b do
      val sort     = sortForType(ctx, inputType)
      val funcName = s"input_${op.a}_$inputName"
      if !ctx.funcs.contains(funcName) then
        ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      env(inputName) = Z3Expr.App(funcName, Nil)
      ctx.inputs += ArtifactBinding(inputName, funcName, sort)
      maybeAssertInputRefinement(ctx, input, funcName)
    env

  private def maybeAssertInputRefinement(
      ctx: TranslateCtx,
      input: ParamDeclFull,
      funcName: String
  ): Unit =
    input.b match
      case NamedTypeF(n, _) =>
        ctx.primitiveAliases.get(n).foreach: alias =>
          val env = mutable.Map.empty[String, Z3Expr]
          env("value") = Z3Expr.App(funcName, Nil)
          ctx.assertions += translateExpr(ctx, alias.constraint, env)
      case _ => ()

  private def declareOperationOutputs(
      ctx: TranslateCtx,
      op: OperationDeclFull,
      env: mutable.Map[String, Z3Expr]
  ): Unit =
    for case ParamDeclFull(outName, outType, _) <- op.c do
      val sort     = sortForType(ctx, outType)
      val funcName = s"output_${op.a}_$outName"
      if !ctx.funcs.contains(funcName) then
        ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      env(outName) = Z3Expr.App(funcName, Nil)
      ctx.outputs += ArtifactBinding(outName, funcName, sort)
      outType match
        case NamedTypeF(n, _) =>
          ctx.primitiveAliases.get(n).foreach: alias =>
            val refineEnv = mutable.Map.empty[String, Z3Expr]
            refineEnv("value") = Z3Expr.App(funcName, Nil)
            ctx.assertions += translateExpr(ctx, alias.constraint, refineEnv)
        case _ => ()

  private def declareEnum(ctx: TranslateCtx, e: EnumDeclFull): Unit =
    val sort = Z3Sort.Uninterp(e.a)
    ctx.declareSort(sort)
    ctx.enums(e.a) = EnumInfo(sort, e.b)
    val memberConsts = e.b.map: v =>
      val funcName = s"${e.a}_$v"
      ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      Z3Expr.App(funcName, Nil)
    if memberConsts.length >= 2 then
      val pairs = mutable.ArrayBuffer.empty[Z3Expr]
      for i <- memberConsts.indices; j <- (i + 1) until memberConsts.length do
        pairs += Z3Expr.Not(Z3Expr.Cmp(CmpOp.Eq, memberConsts(i), memberConsts(j)))
      val out = if pairs.length == 1 then pairs.head else Z3Expr.And(pairs.toList)
      ctx.assertions += out

  private def declareTypeAlias(ctx: TranslateCtx, t: TypeAliasDeclFull): Unit =
    val primitiveSort = primitiveUnderlyingSort(t)
    (primitiveSort, t.c) match
      case (Some(ps), Some(c)) =>
        ctx.primitiveAliases(t.a) = PrimitiveAliasInfo(ps, c)
      case _ =>
        val sort = primitiveSort.getOrElse(Z3Sort.Uninterp(t.a))
        ctx.declareSort(sort)
        ctx.typeAliases(t.a) = TypeAliasInfo(sort)

  private def primitiveUnderlyingSort(t: TypeAliasDeclFull): Option[Z3Sort] =
    t.b match
      case NamedTypeF("Int", _)  => Some(Z3Sort.Int)
      case NamedTypeF("Bool", _) => Some(Z3Sort.Bool)
      case _                     => None

  private def emitTypeAliasConstraint(ctx: TranslateCtx, t: TypeAliasDeclFull): Unit =
    t.c match
      case None => ()
      case Some(constraint) =>
        if ctx.primitiveAliases.contains(t.a) then ()
        else
          val sort    = ctx.typeAliases(t.a).sort
          val varName = s"self_${t.a}"
          val selfRef = Z3Expr.Var(varName, sort)
          val env     = mutable.Map.empty[String, Z3Expr]
          env("value") = selfRef
          val body = translateExpr(ctx, constraint, env)
          ctx.assertions += Z3Expr.Quantifier(
            QKind.ForAll,
            List(Z3Binding(varName, sort)),
            body
          )

  private def declareEntity(ctx: TranslateCtx, e: EntityDeclFull): Unit =
    val sort = Z3Sort.Uninterp(e.a)
    ctx.declareSort(sort)
    val fields = mutable.LinkedHashMap.empty[String, (Z3Sort, String)]
    for case FieldDeclFull(fName, fType, _, _) <- e.c do
      val fieldSort = sortForType(ctx, fType)
      val funcName  = s"${e.a}_$fName"
      ctx.declareFunc(Z3FunctionDecl(funcName, List(sort), fieldSort))
      fields(fName) = (fieldSort, funcName)
    ctx.entities(e.a) = EntityInfo(sort, fields)

  private def emitEntityAssertions(ctx: TranslateCtx, e: EntityDeclFull): Unit =
    val info    = ctx.entities(e.a)
    val varName = s"self_${e.a}"
    val selfRef = Z3Expr.Var(varName, info.sort)

    for case FieldDeclFull(fName, fType, fConstraint, _) <- e.c do
      val (fieldSort, fieldFunc) = info.fields(fName)
      val aliasConstraint        = refinementConstraintFor(ctx, fType)
      val fieldRead: Z3Expr      = Z3Expr.App(fieldFunc, List(selfRef))
      val bodies                 = mutable.ArrayBuffer.empty[Z3Expr]
      aliasConstraint.foreach: c =>
        val env = mutable.Map.empty[String, Z3Expr]
        env("value") = fieldRead
        bodies += translateExpr(ctx, c, env)
      fConstraint.foreach: c =>
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

    for inv <- e.d do
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

  private def refinementConstraintFor(ctx: TranslateCtx, te: type_expr_full): Option[expr_full] =
    te match
      case NamedTypeF(n, _) => ctx.primitiveAliases.get(n).map(_.constraint)
      case _                => None

  private def declareState(ctx: TranslateCtx, state: StateDeclFull): Unit =
    for case sf: StateFieldDeclFull <- state.a do declareStateField(ctx, sf)
    for case sf: StateFieldDeclFull <- state.a do emitStateTotality(ctx, sf, StateMode.Pre)
    for case sf: StateFieldDeclFull <- state.a do emitStateRefinement(ctx, sf, StateMode.Pre)

  private def declareStatePostState(ctx: TranslateCtx, state: StateDeclFull): Unit =
    for case sf: StateFieldDeclFull <- state.a do declareStatePostFunc(ctx, sf)
    for case sf: StateFieldDeclFull <- state.a do emitStateTotality(ctx, sf, StateMode.Post)
    for case sf: StateFieldDeclFull <- state.a do emitStateRefinement(ctx, sf, StateMode.Post)

  private def declareStatePostFunc(ctx: TranslateCtx, sf: StateFieldDeclFull): Unit =
    ctx.state.get(sf.a) match
      case Some(r: StateRelationInfo) =>
        ctx.declareFunc(Z3FunctionDecl(r.domFuncPost, List(r.keySort), Z3Sort.Bool))
        ctx.declareFunc(Z3FunctionDecl(r.mapFuncPost, List(r.keySort), r.valueSort))
      case Some(c: StateConstInfo) =>
        ctx.declareFunc(Z3FunctionDecl(c.funcNamePost, Nil, c.sort))
      case None => ()

  private def emitStateRefinement(
      ctx: TranslateCtx,
      sf: StateFieldDeclFull,
      mode: StateMode
  ): Unit =
    ctx.state.get(sf.a) match
      case Some(c: StateConstInfo) =>
        refinementConstraintFor(ctx, sf.b).foreach: aliasConstraint =>
          val env = mutable.Map.empty[String, Z3Expr]
          env("value") = Z3Expr.App(constFuncFor(c, mode), Nil)
          ctx.assertions += translateExpr(ctx, aliasConstraint, env)
      case Some(r: StateRelationInfo) =>
        val (keyType, valueType) = sf.b match
          case RelationTypeF(f, _, t, _) => (f, t)
          case MapTypeF(k, v, _)         => (k, v)
          case _                         => return
        val keyConstraint   = refinementConstraintFor(ctx, keyType)
        val valueConstraint = refinementConstraintFor(ctx, valueType)
        keyConstraint.foreach(c => emitRelationKeyRefinement(ctx, r, sf.a, c, mode))
        valueConstraint.foreach(c => emitRelationValueRefinement(ctx, r, sf.a, c, mode))
      case None => ()

  private def emitRelationKeyRefinement(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      fieldName: String,
      keyConstraint: expr_full,
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
      valueConstraint: expr_full,
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

  private def declareStateField(ctx: TranslateCtx, sf: StateFieldDeclFull): Unit =
    sf.b match
      case RelationTypeF(from, mult, to, _) =>
        val keySort   = sortForType(ctx, from)
        val valueSort = sortForType(ctx, to)
        val domFunc   = s"${sf.a}_dom"
        val mapFunc   = s"${sf.a}_map"
        ctx.declareFunc(Z3FunctionDecl(domFunc, List(keySort), Z3Sort.Bool))
        ctx.declareFunc(Z3FunctionDecl(mapFunc, List(keySort), valueSort))
        ctx.state(sf.a) = StateRelationInfo(
          keySort,
          valueSort,
          domFunc,
          mapFunc,
          s"${sf.a}_dom_post",
          s"${sf.a}_map_post",
          isTotal = mult match { case _: MultOne => true; case _ => false }
        )
      case MapTypeF(k, v, _) =>
        val keySort   = sortForType(ctx, k)
        val valueSort = sortForType(ctx, v)
        val domFunc   = s"${sf.a}_dom"
        val mapFunc   = s"${sf.a}_map"
        ctx.declareFunc(Z3FunctionDecl(domFunc, List(keySort), Z3Sort.Bool))
        ctx.declareFunc(Z3FunctionDecl(mapFunc, List(keySort), valueSort))
        ctx.state(sf.a) = StateRelationInfo(
          keySort,
          valueSort,
          domFunc,
          mapFunc,
          s"${sf.a}_dom_post",
          s"${sf.a}_map_post",
          isTotal = false
        )
      case _ =>
        val fieldSort = sortForType(ctx, sf.b)
        val funcName  = s"state_${sf.a}"
        ctx.declareFunc(Z3FunctionDecl(funcName, Nil, fieldSort))
        ctx.state(sf.a) = StateConstInfo(fieldSort, funcName, s"${funcName}_post")

  private def domFuncFor(info: StateRelationInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.domFuncPost else info.domFunc

  private def mapFuncFor(info: StateRelationInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.mapFuncPost else info.mapFunc

  private def constFuncFor(info: StateConstInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.funcNamePost else info.funcName

  private def emitStateTotality(
      ctx: TranslateCtx,
      sf: StateFieldDeclFull,
      mode: StateMode
  ): Unit =
    ctx.state.get(sf.a) match
      case Some(r: StateRelationInfo) if r.isTotal =>
        val suffix  = if mode == StateMode.Post then "_post" else ""
        val varName = s"k_${sf.a}$suffix"
        val body    = Z3Expr.App(domFuncFor(r, mode), List(Z3Expr.Var(varName, r.keySort)))
        ctx.assertions += Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, r.keySort)),
          body
        )
      case _ => ()

  private def emitTopLevelInvariant(ctx: TranslateCtx, inv: InvariantDeclFull): Unit =
    val env = mutable.Map.empty[String, Z3Expr]
    ctx.assertions += translateExpr(ctx, inv.b, env)

  private def emitStringLiteralDistinctness(ctx: TranslateCtx): Unit =
    val n = ctx.stringLitCount
    if n < 2 then return
    val consts = (0 until n).toList.map(i => Z3Expr.App(s"str_$i", Nil))
    val pairs  = mutable.ArrayBuffer.empty[Z3Expr]
    for i <- consts.indices; j <- (i + 1) until consts.length do
      pairs += Z3Expr.Cmp(CmpOp.Neq, consts(i), consts(j))
    val out = if pairs.length == 1 then pairs.head else Z3Expr.And(pairs.toList)
    ctx.assertions += out

  private def sortForType(ctx: TranslateCtx, te: type_expr_full): Z3Sort = te match
    case NamedTypeF(n, _)      => sortForNamedType(ctx, n)
    case OptionTypeF(inner, _) => sortForType(ctx, inner)
    case SetTypeF(e, _)        => Z3Sort.SetOf(sortForType(ctx, e))
    case SeqTypeF(e, _)        => Z3Sort.Uninterp(s"Seq_${sortNameOf(sortForType(ctx, e))}")
    case MapTypeF(k, v, _) =>
      Z3Sort.Uninterp(s"Map_${sortNameOf(sortForType(ctx, k))}_${sortNameOf(sortForType(ctx, v))}")
    case RelationTypeF(f, _, t, _) =>
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

  def translateExpr(ctx: TranslateCtx, expr: expr_full, env: mutable.Map[String, Z3Expr]): Z3Expr =
    val enums = ctx.enums.keys.toList
    val out = lower(enums, expr) match
      case Some(eSubset) => encodeFromSmtTerm(ctx, translateVerified(eSubset), env)
      case None          => translateExprRaw(ctx, expr, env)
    out.withSpan(expr.spanOpt)

  private def translateVerified(e: expr): smt_term = SpecRestGenerated.translate(e)

  private def translateExprRaw(
      ctx: TranslateCtx,
      expr: expr_full,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr match
    case IntLitF(int_of_integer(v), _) => Z3Expr.IntLit(v.toLong)
    case BoolLitF(v, _)                => Z3Expr.BoolLit(v)
    case StringLitF(v, _)              => stringLiteralConst(ctx, v)
    case IdentifierF(name, _)          => resolveIdentifier(ctx, name, env)
    case BinaryOpF(op, l, r, _)        => translateBinaryOp(ctx, op, l, r, env)
    case u @ UnaryOpF(_, _, _)         => translateUnaryOp(ctx, u, env)
    case q @ QuantifierF(_, _, _, _)   => translateQuantifier(ctx, q, env)
    case f @ FieldAccessF(_, _, _)     => translateFieldAccess(ctx, f, env)
    case i @ IndexF(_, _, _)           => translateIndex(ctx, i, env)
    case c @ CallF(_, _, _)            => translateCall(ctx, c, env)
    case m @ MatchesF(_, _, _)         => translateMatches(ctx, m, env)
    case e @ EnumAccessF(_, _, _)      => translateEnumAccess(ctx, e)
    case PrimeF(inner, _) =>
      withStateMode(ctx, StateMode.Post, () => translateExpr(ctx, inner, env))
    case PreF(inner, _) =>
      withStateMode(ctx, StateMode.Pre, () => translateExpr(ctx, inner, env))
    case w @ WithF(_, _, _)                 => translateWith(ctx, w, env)
    case sc @ SetComprehensionF(_, _, _, _) => translateSetComprehension(ctx, sc)
    case sl @ SetLiteralF(_, _)             => translateSetLiteral(ctx, sl, env)
    case l @ LetF(_, _, _, _)               => translateLet(ctx, l, env)
    case other =>
      fail(
        ctx,
        s"expression kind '${other.getClass.getSimpleName}' is not yet supported by the verifier"
      )

  private def withStateMode[T](ctx: TranslateCtx, mode: StateMode, fn: () => T): T =
    val saved = ctx.stateMode
    ctx.stateMode = mode
    try fn()
    finally ctx.stateMode = saved

  private def peelRelationRef(t: smt_term, default: StateMode): Option[(String, StateMode)] =
    SpecRestGenerated.peel_smt_relation_ref(t).map: rel =>
      val mode = t match
        case TPre(_)   => StateMode.Pre
        case TPrime(_) => StateMode.Post
        case _         => default
      (rel, mode)

  private def translateBinaryOp(
      ctx: TranslateCtx,
      op: bin_op_full,
      leftExpr: expr_full,
      rightExpr: expr_full,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val isEq    = op match { case _: BEq => true; case _ => false }
    val isNeq   = op match { case _: BNeq => true; case _ => false }
    val isIn    = op match { case _: BIn => true; case _ => false }
    val isNotIn = op match { case _: BNotIn => true; case _ => false }
    if isEq || isNeq then
      val scEq = tryLowerSetComprehensionEquality(ctx, leftExpr, rightExpr, env)
      if scEq.isDefined then
        return if isEq then scEq.get else Z3Expr.Not(scEq.get)
      val domEq = tryLowerDomEquality(ctx, leftExpr, rightExpr, negate = isNeq)
      if domEq.isDefined then return domEq.get
    if isIn || isNotIn then
      val left = translateExpr(ctx, leftExpr, env)
      val mem  = membership(leftExpr, rightExpr, left, ctx, op, env)
      return if isIn then mem else Z3Expr.Not(mem)
    val left  = translateExpr(ctx, leftExpr, env)
    val right = translateExpr(ctx, rightExpr, env)
    op match
      case BAnd()     => Z3Expr.And(List(left, right))
      case BOr()      => Z3Expr.Or(List(left, right))
      case BImplies() => Z3Expr.Implies(left, right)
      case BIff() =>
        Z3Expr.And(List(Z3Expr.Implies(left, right), Z3Expr.Implies(right, left)))
      case BEq() | BNeq() | BLt() | BLe() | BGt() | BGe() =>
        val cmp = op match
          case BEq()  => CmpOp.Eq
          case BNeq() => CmpOp.Neq
          case BLt()  => CmpOp.Lt
          case BLe()  => CmpOp.Le
          case BGt()  => CmpOp.Gt
          case BGe()  => CmpOp.Ge
          case _      => CmpOp.Eq
        Z3Expr.Cmp(cmp, left, right)
      case BAdd() | BSub() | BMul() | BDiv() =>
        val leftSort  = inferSort(ctx, leftExpr, env, Some(left))
        val rightSort = inferSort(ctx, rightExpr, env, Some(right))
        val leftBad   = leftSort.exists(_ != Z3Sort.Int)
        val rightBad  = rightSort.exists(_ != Z3Sort.Int)
        if leftBad || rightBad then
          fail(
            ctx,
            s"arithmetic operator '${binOpToTs(op)}' is only supported on integers (deferred for string/set arithmetic)"
          )
        val aop = op match
          case BAdd() => ArithOp.Add
          case BSub() => ArithOp.Sub
          case BMul() => ArithOp.Mul
          case BDiv() => ArithOp.Div
          case _      => ArithOp.Add
        Z3Expr.Arith(aop, List(left, right))
      case BSubset() | BUnion() | BIntersect() | BDiff() =>
        ensureSetBinOpSorts(ctx, leftExpr, rightExpr, left, right, op, env)
        val sop = op match
          case BSubset()    => SetOpKind.Subset
          case BUnion()     => SetOpKind.Union
          case BIntersect() => SetOpKind.Intersect
          case BDiff()      => SetOpKind.Diff
          case _            => SetOpKind.Union
        Z3Expr.SetBinOp(sop, left, right)
      case _ =>
        fail(ctx, s"binary op '${binOpToTs(op)}' is not supported by the verifier")

  private def ensureSetBinOpSorts(
      ctx: TranslateCtx,
      leftExpr: expr_full,
      rightExpr: expr_full,
      left: Z3Expr,
      right: Z3Expr,
      op: bin_op_full,
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
      fail(ctx, s"set operator '${binOpToTs(op)}' requires both operands to be sets")
    (leftSort, rightSort) match
      case (Some(Z3Sort.SetOf(le)), Some(Z3Sort.SetOf(re))) if !Z3Sort.eq(le, re) =>
        fail(
          ctx,
          s"set operator '${binOpToTs(op)}' requires both operands to have the same element sort; got $le and $re"
        )
      case _ => ()

  private def binOpToTs(op: bin_op_full): String = SpecRestGenerated.bin_op_to_ts(op)

  private def membership(
      leftExpr: expr_full,
      rightExpr: expr_full,
      leftZ: Z3Expr,
      ctx: TranslateCtx,
      op: bin_op_full,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val _ = leftExpr
    resolveStateRelationReference(ctx, rightExpr) match
      case Some((info, mode)) =>
        Z3Expr.App(domFuncFor(info, mode), List(leftZ))
      case None =>
        rightExpr match
          case sc @ SetComprehensionF(_, _, _, _) =>
            membershipInComprehension(ctx, leftZ, sc, env)
          case SetLiteralF(elements, _) =>
            membershipInSetLiteral(ctx, leftZ, elements, env)
          case _ =>
            val rightZ = translateExpr(ctx, rightExpr, env)
            inferSortOfZ3Expr(ctx, rightZ) match
              case Some(Z3Sort.SetOf(elemSort)) =>
                val leftSort = inferSortOfZ3Expr(ctx, leftZ)
                if leftSort.exists(s => !Z3Sort.eq(s, elemSort)) then
                  fail(
                    ctx,
                    s"membership operator '${binOpToTs(op)}' requires the left-hand side sort to match the set's element sort; got ${leftSort.get} against a set of $elemSort"
                  )
                Z3Expr.SetMember(leftZ, rightZ)
              case _ =>
                fail(
                  ctx,
                  s"membership operator '${binOpToTs(op)}' is only supported against a state relation, set literal, set comprehension, or set-valued expression"
                )

  private def membershipInSetLiteral(
      ctx: TranslateCtx,
      leftZ: Z3Expr,
      elements: List[expr_full],
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
          fail(
            ctx,
            s"set literal elements must match the membership LHS sort; expected $ls but found $got"
          )
      val eqs = translated.map(rhs => Z3Expr.Cmp(CmpOp.Eq, leftZ, rhs))
      if eqs.length == 1 then eqs.head else Z3Expr.Or(eqs)

  private def membershipInComprehension(
      ctx: TranslateCtx,
      leftZ: Z3Expr,
      sc: SetComprehensionF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val resolved = resolveBindingDomain(
      ctx,
      QuantifierBindingFull(sc.a, sc.b, BkIn(), None)
    )
    val subEnv = env.clone()
    subEnv(sc.a) = leftZ
    val predicate = translateExpr(ctx, sc.c, subEnv)
    resolved.guard match
      case None => predicate
      case Some(gFn) =>
        val guard      = gFn(sc.a)
        val guardSubst = substituteVar(guard, sc.a, leftZ)
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
      expr: expr_full
  ): Option[(StateRelationInfo, StateMode)] = expr match
    case IdentifierF(name, _) =>
      ctx.state.get(name) match
        case Some(r: StateRelationInfo) => Some((r, ctx.stateMode))
        case _                          => None
    case PrimeF(inner, _) =>
      resolveStateRelationReference(ctx, inner).map((info, _) => (info, StateMode.Post))
    case PreF(inner, _) =>
      resolveStateRelationReference(ctx, inner).map((info, _) => (info, StateMode.Pre))
    case _ => None

  private def translateUnaryOp(
      ctx: TranslateCtx,
      expr: UnaryOpF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr.a match
    case UNot() => Z3Expr.Not(translateExpr(ctx, expr.b, env))
    case UNegate() =>
      Z3Expr.Arith(ArithOp.Sub, List(Z3Expr.IntLit(0), translateExpr(ctx, expr.b, env)))
    case UCardinality() => translateCardinality(ctx, expr.b)
    case UPower() =>
      fail(
        ctx,
        "powerset operator is not decidable in first-order SMT; narrow the invariant to avoid powerset"
      )

  private def translateCardinality(ctx: TranslateCtx, operand: expr_full): Z3Expr =
    peel_relation_ref_full(operand) match
      case Some(name) =>
        val mode = operand match
          case PrimeF(_, _) => StateMode.Post
          case PreF(_, _)   => StateMode.Pre
          case _            => ctx.stateMode
        cardinalityRefFor(ctx, name, mode)
      case None =>
        fail(ctx, "cardinality '#expr' is only supported on state-relation identifiers")

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
        fail(
          ctx,
          s"cardinality '#$targetName' requires a state relation; '$targetName' is not declared as one"
        )

  private def translateQuantifier(
      ctx: TranslateCtx,
      q: QuantifierF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val newEnv       = env.clone()
    val bindings     = mutable.ArrayBuffer.empty[Z3Binding]
    val domainGuards = mutable.ArrayBuffer.empty[Z3Expr]
    for case bnd: QuantifierBindingFull <- q.b do
      val resolved = resolveBindingDomain(ctx, bnd)
      bindings += Z3Binding(bnd.a, resolved.sort)
      newEnv(bnd.a) = Z3Expr.Var(bnd.a, resolved.sort)
      resolved.guard.foreach(gFn => domainGuards += gFn(bnd.a))
    val body        = translateExpr(ctx, q.c, newEnv)
    val guardedBody = applyGuards(q.a, domainGuards.toList, body)
    mapQuantifier(q.a) match
      case Right(zq) => Z3Expr.Quantifier(zq, bindings.toList, guardedBody)
      case Left(_) =>
        Z3Expr.Not(
          Z3Expr.Quantifier(QKind.Exists, bindings.toList, guardedBody)
        )

  private def mapQuantifier(q: quant_kind_full): Either[Unit, QKind] = q match
    case QAll()              => Right(QKind.ForAll)
    case QSome() | QExists() => Right(QKind.Exists)
    case QNo()               => Left(())

  private def applyGuards(q: quant_kind_full, guards: List[Z3Expr], body: Z3Expr): Z3Expr =
    val isAll = q match { case _: QAll => true; case _ => false }
    guards match
      case Nil => body
      case one :: Nil =>
        if isAll then Z3Expr.Implies(one, body)
        else Z3Expr.And(List(one, body))
      case xs =>
        val g = Z3Expr.And(xs)
        if isAll then Z3Expr.Implies(g, body)
        else Z3Expr.And(List(g, body))

  final private case class BindingResolution(sort: Z3Sort, guard: Option[String => Z3Expr])

  private def resolveBindingDomain(
      ctx: TranslateCtx,
      b: QuantifierBindingFull
  ): BindingResolution = b.b match
    case IdentifierF(name, _) =>
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
      expr: FieldAccessF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val base     = translateExpr(ctx, expr.a, env)
    val baseSort = inferSort(ctx, expr.a, env, Some(base))
    baseSort match
      case Some(Z3Sort.Uninterp(name)) =>
        ctx.entities.get(name) match
          case Some(entity) =>
            entity.fields.get(expr.b) match
              case Some((_, funcName)) => Z3Expr.App(funcName, List(base))
              case None =>
                fail(ctx, s"entity '$name' has no field '${expr.b}'")
          case None =>
            fail(
              ctx,
              s"field access '.${expr.b}' requires an entity-typed base; inferred sort is not an entity"
            )
      case _ =>
        fail(
          ctx,
          s"field access '.${expr.b}' requires an entity-typed base; inferred sort is not an entity"
        )

  private def translateIndex(
      ctx: TranslateCtx,
      expr: IndexF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = resolveStateRelationReference(ctx, expr.a) match
    case Some((info, mode)) =>
      val key = translateExpr(ctx, expr.b, env)
      Z3Expr.App(mapFuncFor(info, mode), List(key))
    case None =>
      fail(
        ctx,
        "indexing is only supported on state-relation references (including primed/pre-state forms); general map/sequence indexing is not supported"
      )

  private def translateCall(
      ctx: TranslateCtx,
      expr: CallF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    expr.a match
      case IdentifierF(name, _) =>
        val args = expr.b.map(a => translateExpr(ctx, a, env))
        val argSorts =
          expr.b.map(a => inferSort(ctx, a, env, None).getOrElse(Z3Sort.Uninterp("Any")))
        val resultSort = callReturnSort(name, ctx)
        val funcName   = s"${name}_${argSortsMangled(argSorts)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, argSorts, resultSort))
        Z3Expr.App(funcName, args)
      case _ =>
        fail(ctx, "higher-order call (non-identifier callee) is not supported by the verifier")

  private def callReturnSort(name: String, ctx: TranslateCtx): Z3Sort = name match
    case "len"                               => Z3Sort.Int
    case n if ctx.predicateNames.contains(n) => Z3Sort.Bool
    case _                                   => Z3Sort.Uninterp("Any")

  private def argSortsMangled(sorts: List[Z3Sort]): String =
    if sorts.isEmpty then "0" else sorts.map(sortNameOf).mkString("_")

  private def translateMatches(
      ctx: TranslateCtx,
      expr: MatchesF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val arg = translateExpr(ctx, expr.a, env)
    val argSort =
      inferSort(ctx, expr.a, env, Some(arg)).getOrElse(Z3Sort.Uninterp(StringSortName))
    val baseName = ctx.matchesNameFor(expr.b)
    val funcName = s"${baseName}_${sortNameOf(argSort)}"
    if !ctx.funcs.contains(funcName) then
      ctx.declareFunc(Z3FunctionDecl(funcName, List(argSort), Z3Sort.Bool))
    Z3Expr.App(funcName, List(arg))

  private def translateLet(
      ctx: TranslateCtx,
      expr: LetF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val value  = translateExpr(ctx, expr.b, env)
    val newEnv = env.clone()
    newEnv(expr.a) = value
    translateExpr(ctx, expr.c, newEnv)

  private def translateWith(
      ctx: TranslateCtx,
      expr: WithF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val baseSort = inferSort(ctx, expr.a, env, None)
    baseSort match
      case Some(Z3Sort.Uninterp(name)) =>
        ctx.entities.get(name) match
          case None =>
            fail(ctx, s"'with' expression requires an entity sort; '$name' is not an entity")
          case Some(entity) =>
            val baseZ      = translateExpr(ctx, expr.a, env)
            val skolemName = ctx.freshSkolem(s"with_$name")
            ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, Z3Sort.Uninterp(name)))
            val skolemRef    = Z3Expr.App(skolemName, Nil)
            val updatedNames = expr.b.collect { case FieldAssignFull(n, _, _) => n }.toSet
            for (fname, (_, funcName)) <- entity.fields do
              if !updatedNames.contains(fname) then
                ctx.assertions += Z3Expr.Cmp(
                  CmpOp.Eq,
                  Z3Expr.App(funcName, List(skolemRef)),
                  Z3Expr.App(funcName, List(baseZ))
                )
            for case FieldAssignFull(uName, uValue, _) <- expr.b do
              entity.fields.get(uName) match
                case None =>
                  fail(ctx, s"entity '$name' has no field '$uName'")
                case Some((_, funcName)) =>
                  val value = translateExpr(ctx, uValue, env)
                  ctx.assertions += Z3Expr.Cmp(
                    CmpOp.Eq,
                    Z3Expr.App(funcName, List(skolemRef)),
                    value
                  )
            skolemRef
      case _ =>
        fail(ctx, "'with' expression requires a known entity sort")

  private def translateSetComprehension(ctx: TranslateCtx, sc: SetComprehensionF): Z3Expr =
    val _ = sc
    fail(
      ctx,
      "standalone set comprehensions as expressions are not supported because the binder's element sort typically does not match the receiver's declared type; use an inline membership form: `y in {x in S | P}`"
    )

  private def translateSetLiteral(
      ctx: TranslateCtx,
      sl: SetLiteralF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    if sl.a.isEmpty then
      fail(
        ctx,
        "empty set literal '{}' requires context to infer its element sort; use a non-empty set"
      )
    val translated  = sl.a.map(e => translateExpr(ctx, e, env))
    val memberSorts = translated.map(t => inferSortOfZ3Expr(ctx, t))
    val unknownIdx  = memberSorts.indexWhere(_.isEmpty)
    if unknownIdx >= 0 then
      fail(ctx, s"set literal element has unknown sort: ${sl.a(unknownIdx)}")
    val knownSorts  = memberSorts.flatten
    val elemSort    = knownSorts.head
    val mismatchIdx = knownSorts.indexWhere(s => !Z3Sort.eq(s, elemSort))
    if mismatchIdx >= 0 then
      fail(
        ctx,
        s"set literal elements must all have the same sort; expected $elemSort but found ${knownSorts(mismatchIdx)}"
      )
    Z3Expr.SetLit(elemSort, translated)

  private def translateEnumAccess(ctx: TranslateCtx, expr: EnumAccessF): Z3Expr =
    expr.a match
      case IdentifierF(enumName, _) =>
        val memberName = expr.b
        val funcName   = s"${enumName}_$memberName"
        if !ctx.funcs.contains(funcName) then
          val resultSort = ctx.enums.get(enumName).map(_.sort).getOrElse(Z3Sort.Uninterp(enumName))
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, resultSort))
        Z3Expr.App(funcName, Nil)
      case _ =>
        fail(ctx, "enum access base must be an identifier")

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
      expr: expr_full,
      env: mutable.Map[String, Z3Expr],
      translated: Option[Z3Expr]
  ): Option[Z3Sort] = expr match
    case IdentifierF(name, _) =>
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
    case IndexF(base, _, _) =>
      resolveStateRelationReference(ctx, base).map((info, _) => info.valueSort)
    case PrimeF(inner, _) => inferSort(ctx, inner, env, translated)
    case PreF(inner, _)   => inferSort(ctx, inner, env, translated)
    case FieldAccessF(base, field, _) =>
      inferSort(ctx, base, env, None) match
        case Some(Z3Sort.Uninterp(name)) =>
          ctx.entities.get(name).flatMap(_.fields.get(field).map(_._1))
        case _ => None
    case IntLitF(_, _)                     => Some(Z3Sort.Int)
    case BoolLitF(_, _)                    => Some(Z3Sort.Bool)
    case StringLitF(_, _)                  => Some(Z3Sort.Uninterp(StringSortName))
    case CallF(IdentifierF(name, _), _, _) => Some(callReturnSort(name, ctx))
    case SetLiteralF(elements, _) =>
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
      leftExpr: expr_full,
      rightExpr: expr_full,
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
      expr: expr_full
  ): Option[(StateRelationInfo, StateMode)] = expr match
    case CallF(IdentifierF("dom", _), arg :: Nil, _) =>
      resolveStateRelationReference(ctx, arg)
    case _ => None

  private def tryLowerSetComprehensionEquality(
      ctx: TranslateCtx,
      leftExpr: expr_full,
      rightExpr: expr_full,
      env: mutable.Map[String, Z3Expr]
  ): Option[Z3Expr] =
    val pair: Option[(expr_full, SetComprehensionF)] = (leftExpr, rightExpr) match
      case (sc: SetComprehensionF, other) => Some((other, sc))
      case (other, sc: SetComprehensionF) => Some((other, sc))
      case _                              => None
    pair.map: (setExpr, sc) =>
      translateSetComprehensionEquality(ctx, setExpr, sc, env)

  private def translateSetComprehensionEquality(
      ctx: TranslateCtx,
      setExpr: expr_full,
      sc: SetComprehensionF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val setZ = translateExpr(ctx, setExpr, env)
    val elemSort = inferSortOfZ3Expr(ctx, setZ) match
      case Some(Z3Sort.SetOf(e)) => e
      case Some(other) =>
        fail(
          ctx,
          s"set-comprehension equality requires a set-sorted receiver; got ${Z3Sort.key(other)}"
        )
      case None =>
        fail(ctx, "set-comprehension equality requires a receiver with an inferrable set sort")
    val resolved = resolveBindingDomain(
      ctx,
      QuantifierBindingFull(sc.a, sc.b, BkIn(), None)
    )
    if !Z3Sort.eq(resolved.sort, elemSort) then
      fail(
        ctx,
        s"set-comprehension binder sort ${Z3Sort.key(resolved.sort)} does not match receiver element sort ${Z3Sort.key(elemSort)}"
      )
    // Use a fresh binder so we don't capture an outer identifier that shadows
    // `sc.a` in setZ (which was translated against the outer env).
    val freshName = ctx.freshSkolem(s"sc_${sc.a}")
    val varZ      = Z3Expr.Var(freshName, elemSort)
    val subEnv    = env.clone()
    subEnv(sc.a) = varZ
    val predicate = translateExpr(ctx, sc.c, subEnv)
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
      expr: expr_full,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr match
    case BinaryOpF(BEq(), l, r, _) =>
      tryLowerRelationEquality(ctx, l, r, env).getOrElse(translateExpr(ctx, expr, env))
    case _ => translateExpr(ctx, expr, env)

  private def tryLowerRelationEquality(
      ctx: TranslateCtx,
      leftExpr: expr_full,
      rightExpr: expr_full,
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
      expr: expr_full,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] =
    tryLowerSingleInsertRhs(ctx, expr, targetInfo, env)
      .orElse(tryLowerSingleMinusRhs(ctx, expr, targetInfo, env))

  private def tryLowerSingleInsertRhs(
      ctx: TranslateCtx,
      expr: expr_full,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] = expr match
    case BinaryOpF(_: (BAdd | BUnion), left, right, _) =>
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
      expr: expr_full,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] = expr match
    case BinaryOpF(_: (BSub | BDiff), left, right, _) =>
      resolveStateRelationReference(ctx, left).flatMap: base =>
        extractKeySet(right).filter(_.nonEmpty).map: keys =>
          (lhsInfo: StateRelationInfo, lhsMode: StateMode) =>
            relationDeletionAxiom(ctx, lhsInfo, lhsMode, base._1, base._2, keys, env, targetInfo)
    case _ => None

  final private case class KeyValueEntry(key: expr_full, value: expr_full)

  private def extractMapEntries(expr: expr_full): Option[List[KeyValueEntry]] = expr match
    case MapLiteralF(entries, _) =>
      Some(entries.collect { case MapEntryFull(k, v, _) => KeyValueEntry(k, v) })
    case _ => None

  private def extractKeySet(expr: expr_full): Option[List[expr_full]] = expr match
    case SetLiteralF(elements, _) => Some(elements)
    case MapLiteralF(entries, _) =>
      Some(entries.collect { case MapEntryFull(k, _, _) => k })
    case _ => None

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
      keyExprs: List[expr_full],
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
      state: Option[StateDeclFull],
      op: OperationDeclFull,
      env: mutable.Map[String, Z3Expr]
  ): Unit = state match
    case None => ()
    case Some(s) =>
      for case StateFieldDeclFull(sfName, _, _) <- s.a do
        ctx.state.get(sfName) match
          case None => ()
          case Some(info) =>
            val analysis = analyzeStateMention(op.e, sfName)
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
                    syncCardinalityFrameIfDeclared(ctx, sfName)
                  else
                    emitPartialRelationFrame(ctx, r, sfName, analysis, env, op)

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
      removedKeys: List[expr_full],
      fieldUpdatedKeys: List[(expr_full, Set[String])],
      hasUnclassifiedMention: Boolean
  )

  private def analyzeStateMention(
      ensures: List[expr_full],
      stateName: String
  ): StateMentionAnalysis =
    var touched                = false
    var fullyReplaced          = false
    var hasUnclassifiedMention = false
    val removedKeys            = mutable.ArrayBuffer.empty[expr_full]
    val fieldUpdatedKeys       = mutable.LinkedHashMap.empty[String, (expr_full, mutable.Set[String])]

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

  private def keyIdentity(expr: expr_full): String = expr.toString

  private def matchesFullReplacement(expr: expr_full, stateName: String): Boolean = expr match
    case BinaryOpF(BEq(), l, r, _) =>
      referencesPrimedRelation(l, stateName) || referencesPrimedRelation(r, stateName)
    case _ => false

  private def matchNotInPrimed(expr: expr_full, stateName: String): Option[expr_full] = expr match
    case BinaryOpF(BNotIn(), l, r, _) if referencesPrimedRelation(r, stateName) =>
      Some(l)
    case _ => None

  final private case class FieldUpdateMatch(key: expr_full, field: String)

  private def matchFieldUpdatePrimed(expr: expr_full, stateName: String): Option[FieldUpdateMatch] =
    expr match
      case BinaryOpF(BEq(), l, r, _) =>
        matchFieldUpdateSide(l, stateName).orElse(matchFieldUpdateSide(r, stateName))
      case _ => None

  private def matchFieldUpdateSide(side: expr_full, stateName: String): Option[FieldUpdateMatch] =
    side match
      case FieldAccessF(IndexF(base, index, _), field, _)
          if referencesPrimedRelation(base, stateName) =>
        Some(FieldUpdateMatch(index, field))
      case _ => None

  private def matchesCardinalityConstraint(expr: expr_full, stateName: String): Boolean = expr match
    case BinaryOpF(_, l, r, _) =>
      sideIsPrimeCardinality(l, stateName) || sideIsPrimeCardinality(r, stateName)
    case _ => false

  private def sideIsPrimeCardinality(side: expr_full, stateName: String): Boolean = side match
    case UnaryOpF(UCardinality(), PrimeF(IdentifierF(n, _), _), _) =>
      n == stateName
    case _ => false

  private def emitPartialRelationFrame(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      stateName: String,
      analysis: StateMentionAnalysis,
      env: mutable.Map[String, Z3Expr],
      op: OperationDeclFull
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
      val guards = op.d ++ op.e
      val allIn = analysis.removedKeys.forall(k =>
        hasMembershipSideCond(guards, k, stateName, BIn())
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
      state: Option[StateDeclFull],
      op: OperationDeclFull
  ): Unit = state match
    case None => ()
    case Some(s) =>
      for case StateFieldDeclFull(sfName, _, _) <- s.a do
        ctx.state.get(sfName) match
          case Some(_: StateRelationInfo) =>
            detectCardinalityDelta(op, sfName).foreach: delta =>
              val preCard  = ctx.cardinalityNameFor(sfName, StateMode.Pre)
              val postCard = ctx.cardinalityNameFor(sfName, StateMode.Post)
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

  private def detectCardinalityDelta(op: OperationDeclFull, relName: String): Option[Int] =
    val guards = op.d ++ op.e
    op.e.iterator.flatMap: ens =>
      matchPrimedRelationEquality(ens, relName).toList.flatMap: primeEq =>
        if isIdentityRhs(primeEq.rhs, relName) then List(0)
        else
          insertDeltaWithSideCond(guards, primeEq.rhs, relName).map(identity).toList ++
            deleteDeltaWithSideCond(guards, primeEq.rhs, relName).map(-_).toList
    .nextOption()

  private def insertDeltaWithSideCond(
      guards: List[expr_full],
      rhs: expr_full,
      relName: String
  ): Option[Int] = rhs match
    case BinaryOpF(_: (BAdd | BUnion), left, right, _) =>
      if !referencesPreRelation(left, relName) then None
      else
        extractInsertKeys(right).flatMap: keys =>
          if keys.forall(k => hasMembershipSideCond(guards, k, relName, BNotIn())) then
            Some(keys.length)
          else None
    case _ => None

  private def deleteDeltaWithSideCond(
      guards: List[expr_full],
      rhs: expr_full,
      relName: String
  ): Option[Int] = rhs match
    case BinaryOpF(_: (BSub | BDiff), left, right, _) =>
      if !referencesPreRelation(left, relName) then None
      else
        extractKeySet(right).flatMap: keys =>
          if keys.forall(k => hasMembershipSideCond(guards, k, relName, BIn())) then
            Some(keys.length)
          else None
    case _ => None

  private def extractInsertKeys(expr: expr_full): Option[List[expr_full]] =
    extractMapEntries(expr).map(_.map(_.key)).orElse(extractKeySet(expr))

  private def hasMembershipSideCond(
      guards: List[expr_full],
      key: expr_full,
      relName: String,
      op: bin_op_full
  ): Boolean =
    guards.exists(g =>
      flattenAnds(g).exists(sub => matchesMembershipSideCond(sub, key, relName, op))
    )

  private def flattenAnds(expr: expr_full): List[expr_full] = SpecRestGenerated.flatten_and(expr)

  private def matchesMembershipSideCond(
      expr: expr_full,
      key: expr_full,
      relName: String,
      op: bin_op_full
  ): Boolean = expr match
    case BinaryOpF(exprOp, l, r, _) if exprOp.getClass.getName == op.getClass.getName =>
      exprStructurallyEqual(l, key) && referencesPreRelation(r, relName)
    case _ => false

  private def exprStructurallyEqual(a: expr_full, b: expr_full): Boolean = a == b

  final private case class PrimedRelEq(rhs: expr_full)

  private def matchPrimedRelationEquality(expr: expr_full, relName: String): Option[PrimedRelEq] =
    expr match
      case BinaryOpF(BEq(), l, r, _) =>
        if referencesPrimedRelation(l, relName) then Some(PrimedRelEq(r))
        else if referencesPrimedRelation(r, relName) then Some(PrimedRelEq(l))
        else None
      case _ => None

  private def referencesPrimedRelation(expr: expr_full, relName: String): Boolean =
    references_primed_relation_full(expr, relName)

  private def referencesPreRelation(expr: expr_full, relName: String): Boolean =
    references_pre_relation_full(expr, relName)

  private def isIdentityRhs(expr: expr_full, relName: String): Boolean =
    referencesPreRelation(expr, relName)

  private def exprMentionsPostState(expr: expr_full, stateName: String): Boolean =
    walkMentionsPost(expr, stateName, insidePrime = false)

  private def walkMentionsPost(expr: expr_full, stateName: String, insidePrime: Boolean): Boolean =
    expr match
      case PrimeF(inner, _)  => walkMentionsPost(inner, stateName, insidePrime = true)
      case PreF(inner, _)    => walkMentionsPost(inner, stateName, insidePrime = false)
      case IdentifierF(n, _) => insidePrime && n == stateName
      case _ =>
        SpecRestGenerated.subexprs(expr).exists(walkMentionsPost(_, stateName, insidePrime))

  private def encodeFromSmtTerm(
      ctx: TranslateCtx,
      term: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    term match
      case BLit(b)                 => Z3Expr.BoolLit(b)
      case ILit(int_of_integer(n)) => Z3Expr.IntLit(n.toLong)
      case TVar(name)              => resolveIdentifier(ctx, name, env)
      case EnumElemConst(en, mem) =>
        val funcName = s"${en}_$mem"
        if !ctx.funcs.contains(funcName) then
          val resultSort = ctx.enums.get(en).map(_.sort).getOrElse(Z3Sort.Uninterp(en))
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, resultSort))
        Z3Expr.App(funcName, Nil)

      case TNot(TEq(l, r)) =>
        Z3Expr.Cmp(CmpOp.Neq, encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
      case TOr(TLt(a1, b1), TEq(a2, b2)) if a1 == a2 && b1 == b2 =>
        Z3Expr.Cmp(CmpOp.Le, encodeFromSmtTerm(ctx, a1, env), encodeFromSmtTerm(ctx, b1, env))
      case TOr(TLt(b1, a1), TEq(a2, b2)) if a1 == a2 && b1 == b2 =>
        Z3Expr.Cmp(CmpOp.Ge, encodeFromSmtTerm(ctx, a2, env), encodeFromSmtTerm(ctx, b2, env))

      case TNot(t) => Z3Expr.Not(encodeFromSmtTerm(ctx, t, env))
      case TAnd(l, r) =>
        Z3Expr.And(List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env)))
      case TOr(l, r) =>
        Z3Expr.Or(List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env)))
      case TImplies(l, r) =>
        Z3Expr.Implies(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
      case TEq(l, r) =>
        Z3Expr.Cmp(CmpOp.Eq, encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
      case TLt(l, r) =>
        Z3Expr.Cmp(CmpOp.Lt, encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
      case TNeg(t) =>
        Z3Expr.Arith(ArithOp.Sub, List(Z3Expr.IntLit(0), encodeFromSmtTerm(ctx, t, env)))
      case TAdd(l, r) =>
        Z3Expr.Arith(
          ArithOp.Add,
          List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
        )
      case TSub(l, r) =>
        Z3Expr.Arith(
          ArithOp.Sub,
          List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
        )
      case TMul(l, r) =>
        Z3Expr.Arith(
          ArithOp.Mul,
          List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
        )
      case TDiv(l, r) =>
        Z3Expr.Arith(
          ArithOp.Div,
          List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
        )

      case TInDom(rel, elem) =>
        val key = encodeFromSmtTerm(ctx, elem, env)
        ctx.state.get(rel) match
          case Some(r: StateRelationInfo) =>
            Z3Expr.App(domFuncFor(r, ctx.stateMode), List(key))
          case Some(c: StateConstInfo) =>
            val rhs = Z3Expr.App(constFuncFor(c, ctx.stateMode), Nil)
            inferSortOfZ3Expr(ctx, rhs) match
              case Some(Z3Sort.SetOf(_)) => Z3Expr.SetMember(key, rhs)
              case _                     => fail(ctx, s"membership '$rel' requires a relation or set-typed constant")
          case _ =>
            env.get(rel) match
              case Some(bound) =>
                inferSortOfZ3Expr(ctx, bound) match
                  case Some(Z3Sort.SetOf(_)) => Z3Expr.SetMember(key, bound)
                  case _                     => fail(ctx, s"membership '$rel' requires a set-typed binding")
              case None =>
                fail(ctx, s"membership '$rel' requires a state relation or set-typed value")
      case TCardRel(rel) => cardinalityRefFor(ctx, rel, ctx.stateMode)

      case TLetIn(name, value, body) =>
        val v      = encodeFromSmtTerm(ctx, value, env)
        val newEnv = env.clone()
        newEnv(name) = v
        encodeFromSmtTerm(ctx, body, newEnv)

      case TForallEnum(varName, en, body) =>
        val sort   = ctx.enums.get(en).map(_.sort).getOrElse(Z3Sort.Uninterp(en))
        val newEnv = env.clone()
        newEnv(varName) = Z3Expr.Var(varName, sort)
        Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, sort)),
          encodeFromSmtTerm(ctx, body, newEnv)
        )
      case TForallRel(varName, rel, body) =>
        val (sort, guard) = quantifierDomainFor(ctx, rel, varName)
        val newEnv        = env.clone()
        newEnv(varName) = Z3Expr.Var(varName, sort)
        val inner = encodeFromSmtTerm(ctx, body, newEnv)
        val guarded = guard match
          case Some(g) => Z3Expr.Implies(g, inner)
          case None    => inner
        Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, sort)),
          guarded
        )

      case TIndexRel(base, key) =>
        peelRelationRef(base, ctx.stateMode) match
          case Some((rel, mode)) =>
            ctx.state.get(rel) match
              case Some(r: StateRelationInfo) =>
                Z3Expr.App(mapFuncFor(r, mode), List(encodeFromSmtTerm(ctx, key, env)))
              case _ =>
                fail(ctx, s"indexing '$rel' requires a state relation")
          case None =>
            fail(ctx, "indexing requires an identifier (optionally wrapped in pre/prime) as base")
      case TFieldAccess(base, fname) =>
        val baseZ    = encodeFromSmtTerm(ctx, base, env)
        val baseSort = inferSortOfZ3Expr(ctx, baseZ)
        baseSort match
          case Some(Z3Sort.Uninterp(name)) =>
            ctx.entities.get(name) match
              case Some(entity) =>
                entity.fields.get(fname) match
                  case Some((_, funcName)) => Z3Expr.App(funcName, List(baseZ))
                  case None                => fail(ctx, s"entity '$name' has no field '$fname'")
              case None => fail(ctx, "field access requires entity sort")
          case _ => fail(ctx, "field access requires entity sort")

      case TSetEmpty() =>
        fail(ctx, "empty set literal requires context to infer element sort")
      case ins @ TSetInsert(_, _) =>
        val (elemSort, members) = collectSetLiteralMembers(ctx, ins, env)
        Z3Expr.SetLit(elemSort, members)
      case TSetMember(elem, set) =>
        val elemZ    = encodeFromSmtTerm(ctx, elem, env)
        val elemSort = inferSortOfZ3Expr(ctx, elemZ)
        set match
          case TSetEmpty() => Z3Expr.BoolLit(false)
          case ins: TSetInsert =>
            val members =
              collectSetLiteralMembersTerms(ins).map(t => encodeFromSmtTerm(ctx, t, env))
            for s <- elemSort; m <- members; ms <- inferSortOfZ3Expr(ctx, m) do
              if !Z3Sort.eq(s, ms) then
                fail(
                  ctx,
                  s"membership operator 'in' requires the left-hand side sort to match the set's element sort; got $s vs $ms"
                )
            members match
              case Nil => Z3Expr.BoolLit(false)
              case _   => Z3Expr.Or(members.map(m => Z3Expr.Cmp(CmpOp.Eq, elemZ, m)))
          case _ =>
            val rhs    = encodeFromSmtTerm(ctx, set, env)
            val rhSort = inferSortOfZ3Expr(ctx, rhs)
            (elemSort, rhSort) match
              case (Some(es), Some(Z3Sort.SetOf(rs))) if !Z3Sort.eq(es, rs) =>
                fail(
                  ctx,
                  s"membership operator 'in' requires the left-hand side sort to match the set's element sort; got $es against a set of $rs"
                )
              case _ => ()
            Z3Expr.SetMember(elemZ, rhs)
      case TSetUnion(l, r)     => encodeSetBinOp(ctx, SetOpKind.Union, l, r, env)
      case TSetIntersect(l, r) => encodeSetBinOp(ctx, SetOpKind.Intersect, l, r, env)
      case TSetDiff(l, r)      => encodeSetBinOp(ctx, SetOpKind.Diff, l, r, env)

      case TPrime(inner) =>
        withStateMode(ctx, StateMode.Post, () => encodeFromSmtTerm(ctx, inner, env))
      case TPre(inner) =>
        withStateMode(ctx, StateMode.Pre, () => encodeFromSmtTerm(ctx, inner, env))

      case TWithRec(base, fld, value) =>
        val baseZ    = encodeFromSmtTerm(ctx, base, env)
        val baseSort = inferSortOfZ3Expr(ctx, baseZ)
        baseSort match
          case Some(Z3Sort.Uninterp(name)) =>
            ctx.entities.get(name) match
              case None => fail(ctx, s"'with' on non-entity sort '$name'")
              case Some(entity) =>
                val skolemName = ctx.freshSkolem(s"with_$name")
                ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, Z3Sort.Uninterp(name)))
                val skolemRef = Z3Expr.App(skolemName, Nil)
                val v         = encodeFromSmtTerm(ctx, value, env)
                for (fname, (_, funcName)) <- entity.fields do
                  if fname == fld then
                    ctx.assertions += Z3Expr.Cmp(
                      CmpOp.Eq,
                      Z3Expr.App(funcName, List(skolemRef)),
                      v
                    )
                  else
                    ctx.assertions += Z3Expr.Cmp(
                      CmpOp.Eq,
                      Z3Expr.App(funcName, List(skolemRef)),
                      Z3Expr.App(funcName, List(baseZ))
                    )
                skolemRef
          case _ => fail(ctx, s"'with' on field '$fld' requires an entity-sorted base")

  private def collectSetLiteralMembersTerms(term: smt_term): List[smt_term] = term match
    case TSetEmpty()           => Nil
    case TSetInsert(elem, set) => elem :: collectSetLiteralMembersTerms(set)
    case _                     => Nil

  private def encodeSetBinOp(
      ctx: TranslateCtx,
      op: SetOpKind,
      l: smt_term,
      r: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val lz = encodeFromSmtTerm(ctx, l, env)
    val rz = encodeFromSmtTerm(ctx, r, env)
    val ls = inferSortOfZ3Expr(ctx, lz)
    val rs = inferSortOfZ3Expr(ctx, rz)
    (ls, rs) match
      case (Some(Z3Sort.SetOf(le)), Some(Z3Sort.SetOf(re))) =>
        if !Z3Sort.eq(le, re) then
          fail(
            ctx,
            s"set operator '${SetOpKind.token(op)}' requires both operands to have the same element sort; got $le vs $re"
          )
        Z3Expr.SetBinOp(op, lz, rz)
      case (Some(_), Some(_)) =>
        fail(
          ctx,
          s"set operator '${SetOpKind.token(op)}' requires both operands to be sets"
        )
      case _ =>
        Z3Expr.SetBinOp(op, lz, rz)

  private def quantifierDomainFor(
      ctx: TranslateCtx,
      name: String,
      varName: String
  ): (Z3Sort, Option[Z3Expr]) =
    ctx.state.get(name) match
      case Some(r: StateRelationInfo) =>
        val v = Z3Expr.Var(varName, r.keySort)
        (r.keySort, Some(Z3Expr.App(domFuncFor(r, ctx.stateMode), List(v))))
      case Some(c: StateConstInfo) =>
        c.sort match
          case Z3Sort.SetOf(elem) =>
            val v = Z3Expr.Var(varName, elem)
            (elem, Some(Z3Expr.SetMember(v, Z3Expr.App(constFuncFor(c, ctx.stateMode), Nil))))
          case other => (other, None)
      case None =>
        ctx.entities.get(name).map(e => (e.sort, None))
          .orElse(ctx.enums.get(name).map(e => (e.sort, None)))
          .getOrElse((Z3Sort.Uninterp("Unknown"), None))

  private def collectSetLiteralMembers(
      ctx: TranslateCtx,
      term: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): (Z3Sort, List[Z3Expr]) =
    val terms   = collectSetLiteralMembersTerms(term)
    val encoded = terms.map(t => encodeFromSmtTerm(ctx, t, env))
    val sorts   = encoded.map(t => inferSortOfZ3Expr(ctx, t))
    val unknown = sorts.indexWhere(_.isEmpty)
    if unknown >= 0 then
      fail(ctx, s"set literal element has unknown sort: ${terms(unknown)}")
    val knownSorts = sorts.flatten
    val elemSort   = knownSorts.head
    val mismatch   = knownSorts.indexWhere(s => !Z3Sort.eq(s, elemSort))
    if mismatch >= 0 then
      fail(ctx, "set literal elements must all have the same sort")
    (elemSort, encoded)
