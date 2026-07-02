package specrest.verify.z3

import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable

@SuppressWarnings(Array(
  "org.wartremover.warts.Var",
  "org.wartremover.warts.Return",
  "org.wartremover.warts.OptionPartial"
))
private[z3] trait Declarations:
  this: ExpressionEncoder & Z3EncodingSupport =>
  private[z3] def declareBase(ctx: TranslateCtx, ir: service_ir): Unit =
    svcPredicates(ir).foreach(p => ctx.predicateNames += prdName(p))
    for e <- svcEnums(ir) do declareEnum(ctx, e)
    for t <- svcTypeAliases(ir) do declareTypeAlias(ctx, t)
    for e <- svcEntities(ir) do declareEntity(ctx, e)
    svcState(ir).foreach(s => declareState(ctx, s))
    for t <- svcTypeAliases(ir) do emitTypeAliasConstraint(ctx, t)
    for e <- svcEntities(ir) do emitEntityAssertions(ctx, e)

  private[z3] def finalizeScript(ctx: TranslateCtx): Z3Script =
    emitStringLiteralDistinctness(ctx)
    Z3Script(
      sorts = ctx.sorts.values.toList.sortBy(Z3Sort.key),
      funcs = ctx.funcs.values.toList.sortBy(_.name.toLowerCase),
      assertions = ctx.assertions.toList,
      artifact = buildArtifact(ctx)
    )

  private[z3] def buildArtifact(ctx: TranslateCtx): TranslatorArtifact =
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

  private[z3] def declareOperationInputs(
      ctx: TranslateCtx,
      op: operation_decl
  ): mutable.Map[String, Z3Expr] =
    val env = mutable.Map.empty[String, Z3Expr]
    declareOperationParams(ctx, operInputs(op), s"input_${operName(op)}_", ctx.inputs, env)
    env

  private[z3] def declareOperationOutputs(
      ctx: TranslateCtx,
      op: operation_decl,
      env: mutable.Map[String, Z3Expr]
  ): Unit =
    declareOperationParams(ctx, operOutputs(op), s"output_${operName(op)}_", ctx.outputs, env)

  private[z3] def declareOperationParams(
      ctx: TranslateCtx,
      params: List[param_decl],
      prefix: String,
      sink: mutable.ArrayBuffer[ArtifactBinding],
      env: mutable.Map[String, Z3Expr]
  ): Unit =
    for p <- params do
      val name     = prmName(p)
      val sort     = sortForType(ctx, prmType(p))
      val funcName = s"$prefix$name"
      ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      env(name) = Z3Expr.App(funcName, Nil)
      sink += ArtifactBinding(name, funcName, sort)
      maybeAssertParamRefinement(ctx, p, funcName)

  private[z3] def maybeAssertParamRefinement(
      ctx: TranslateCtx,
      param: param_decl,
      funcName: String
  ): Unit =
    prmType(param) match
      case NamedTypeF(n, _) =>
        ctx.primitiveAliases.get(n).foreach: alias =>
          ctx.assertions += translateRefinement(ctx, alias.constraint, Z3Expr.App(funcName, Nil))
      case _ => ()

  private[z3] def declareEnum(ctx: TranslateCtx, e: enum_decl): Unit =
    val sort = Z3Sort.Uninterp(enmName(e))
    ctx.declareSort(sort)
    ctx.enums(enmName(e)) = EnumInfo(sort, enmVariants(e))
    val memberConsts = enmVariants(e).map: v =>
      val funcName = s"${enmName(e)}_$v"
      ctx.declareFunc(Z3FunctionDecl(funcName, Nil, sort))
      Z3Expr.App(funcName, Nil)
    if memberConsts.length >= 2 then
      val pairs = mutable.ArrayBuffer.empty[Z3Expr]
      for i <- memberConsts.indices; j <- (i + 1) until memberConsts.length do
        pairs += Z3Expr.Not(Z3Expr.Cmp(CmpOp.Eq, memberConsts(i), memberConsts(j)))
      val out = if pairs.length == 1 then pairs.head else Z3Expr.And(pairs.toList)
      ctx.assertions += out

  private[z3] def declareTypeAlias(ctx: TranslateCtx, t: type_alias_decl): Unit =
    val primitiveSort = primitiveUnderlyingSort(t)
    (primitiveSort, talConstraint(t)) match
      case (Some(ps), Some(c)) =>
        ctx.primitiveAliases(talName(t)) = PrimitiveAliasInfo(ps, c)
      case _ =>
        val sort = primitiveSort.getOrElse(Z3Sort.Uninterp(talName(t)))
        ctx.declareSort(sort)
        ctx.typeAliases(talName(t)) = TypeAliasInfo(sort)

  // Z3 sort policy for the spec's built-in primitives. Must stay aligned with
  // the proof's `typeExprFullToTy` name policy (Semantics.thy): integral and
  // temporal names share Int/TInt (temporal values are epoch seconds),
  // fractional names map to Real/TReal, Bool/Boolean to Bool/TBool. String is
  // handled in sortForNamedType (native string sort / TStr, where
  // String-refinement aliases get their own handling); UUID is absent on both
  // sides and falls to an uninterpreted sort (equality only).
  private[z3] def primitiveSortOf(name: String): Option[Z3Sort] = name match
    case "Int" | "DateTime" | "Date" | "Duration" => Some(Z3Sort.Int)
    case "Float" | "Decimal" | "Money"            => Some(Z3Sort.Real)
    case "Bool" | "Boolean"                       => Some(Z3Sort.Bool)
    case "String"                                 => Some(Z3Sort.Str)
    case _                                        => None

  private[z3] def primitiveUnderlyingSort(t: type_alias_decl): Option[Z3Sort] =
    talType(t) match
      case NamedTypeF(n, _) => primitiveSortOf(n)
      case _                => None

  private[z3] def emitTypeAliasConstraint(ctx: TranslateCtx, t: type_alias_decl): Unit =
    talConstraint(t) match
      case None => ()
      case Some(constraint) =>
        val name = talName(t)
        val sort = ctx.primitiveAliases
          .get(name)
          .map(_.underlyingSort)
          .orElse(ctx.typeAliases.get(name).map(_.sort))
          .getOrElse(Z3Sort.Uninterp(name))
        val varName = s"self_$name"
        val body    = translateRefinement(ctx, constraint, Z3Expr.Var(varName, sort))
        if !inferSortOfZ3Expr(ctx, body).contains(Z3Sort.Bool) then
          fail(
            ctx,
            s"refinement on '$name' is not a boolean predicate the verifier can model " +
              "(it calls an undeclared/strategy-backed predicate)"
          )
        // Primitive-alias refinements are applied at field-use sites
        // (refinementConstraintFor); here we only validate they are boolean.
        // Non-primitive aliases assert the refinement as a quantified fact.
        if !ctx.primitiveAliases.contains(name) then
          ctx.assertions += Z3Expr.Quantifier(
            QKind.ForAll,
            List(Z3Binding(varName, sort)),
            body
          )

  private[z3] def declareEntity(ctx: TranslateCtx, e: entity_decl): Unit =
    val sort = Z3Sort.Uninterp(entName(e))
    ctx.declareSort(sort)
    val fields = mutable.LinkedHashMap.empty[String, (Z3Sort, String)]
    for f <- entFields(e) do
      val fName     = fldName(f)
      val fType     = fldType(f)
      val fieldSort = sortForType(ctx, fType)
      val funcName  = s"${entName(e)}_$fName"
      ctx.declareFunc(Z3FunctionDecl(funcName, List(sort), fieldSort))
      fields(fName) = (fieldSort, funcName)
    ctx.entities(entName(e)) = EntityInfo(sort, fields)

  private[z3] def emitEntityAssertions(ctx: TranslateCtx, e: entity_decl): Unit =
    val info    = ctx.entities(entName(e))
    val varName = s"self_${entName(e)}"
    val selfRef = Z3Expr.Var(varName, info.sort)

    for f <- entFields(e) do
      val fName                  = fldName(f)
      val fType                  = fldType(f)
      val fConstraint            = fldDefault(f)
      val (fieldSort, fieldFunc) = info.fields(fName)
      val aliasConstraint        = refinementConstraintFor(ctx, fType)
      val fieldRead: Z3Expr      = Z3Expr.App(fieldFunc, List(selfRef))
      val bodies                 = mutable.ArrayBuffer.empty[Z3Expr]
      aliasConstraint.foreach(c => bodies += translateRefinement(ctx, c, fieldRead))
      fConstraint.foreach(c => bodies += translateRefinement(ctx, c, fieldRead))
      if bodies.nonEmpty then
        val body = if bodies.length == 1 then bodies.head else Z3Expr.And(bodies.toList)
        ctx.assertions += Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, info.sort)),
          body
        )
      val _ = fieldSort

    for inv <- entInvariants(e) do
      val env = mutable.Map.empty[String, Z3Expr]
      env("self") = selfRef
      for (fname, (_, funcName)) <- info.fields do
        env(fname) = Z3Expr.App(funcName, List(selfRef))
      val body = translateDeclarationExpr(ctx, inv, env)
      ctx.assertions += Z3Expr.Quantifier(
        QKind.ForAll,
        List(Z3Binding(varName, info.sort)),
        body
      )

  private[z3] def refinementConstraintFor(ctx: TranslateCtx, te: type_expr): Option[expr] =
    te match
      case NamedTypeF(n, _) => ctx.primitiveAliases.get(n).map(_.constraint)
      case _                => None

  private[z3] def declareState(ctx: TranslateCtx, state: state_decl): Unit =
    for sf <- stdFields(state) do declareStateField(ctx, sf)
    for sf <- stdFields(state) do emitStateTotality(ctx, sf, StateMode.Pre)
    for sf <- stdFields(state) do emitStateRefinement(ctx, sf, StateMode.Pre)

  private[z3] def declareStatePostState(ctx: TranslateCtx, state: state_decl): Unit =
    for sf <- stdFields(state) do declareStatePostFunc(ctx, sf)
    for sf <- stdFields(state) do emitStateTotality(ctx, sf, StateMode.Post)
    for sf <- stdFields(state) do emitStateRefinement(ctx, sf, StateMode.Post)

  private[z3] def declareStatePostFunc(ctx: TranslateCtx, sf: state_field_decl): Unit =
    ctx.state.get(stfName(sf)) match
      case Some(r: StateRelationInfo) =>
        ctx.declareFunc(Z3FunctionDecl(r.domFuncPost, List(r.keySort), Z3Sort.Bool))
        ctx.declareFunc(Z3FunctionDecl(r.mapFuncPost, List(r.keySort), r.valueSort))
      case Some(c: StateConstInfo) =>
        ctx.declareFunc(Z3FunctionDecl(c.funcNamePost, Nil, c.sort))
      case None => ()

  private[z3] def emitStateRefinement(
      ctx: TranslateCtx,
      sf: state_field_decl,
      mode: StateMode
  ): Unit =
    ctx.state.get(stfName(sf)) match
      case Some(c: StateConstInfo) =>
        refinementConstraintFor(ctx, stfType(sf)).foreach: aliasConstraint =>
          ctx.assertions +=
            translateRefinement(ctx, aliasConstraint, Z3Expr.App(constFuncFor(c, mode), Nil))
      case Some(r: StateRelationInfo) =>
        val (keyType, valueType) = stfType(sf) match
          case RelationTypeF(f, _, t, _) => (f, t)
          case MapTypeF(k, v, _)         => (k, v)
          case _                         => return
        val keyConstraint   = refinementConstraintFor(ctx, keyType)
        val valueConstraint = refinementConstraintFor(ctx, valueType)
        keyConstraint.foreach(c => emitRelationKeyRefinement(ctx, r, stfName(sf), c, mode))
        valueConstraint.foreach(c => emitRelationValueRefinement(ctx, r, stfName(sf), c, mode))
      case None => ()

  private[z3] def emitRelationKeyRefinement(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      fieldName: String,
      keyConstraint: expr,
      mode: StateMode
  ): Unit =
    val suffix  = if mode == StateMode.Post then "_post" else ""
    val varName = s"k_${fieldName}_key$suffix"
    val keyVar  = Z3Expr.Var(varName, info.keySort)
    val pred    = translateRefinement(ctx, keyConstraint, keyVar)
    ctx.assertions += Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, info.keySort)),
      Z3Expr.Implies(
        Z3Expr.App(domFuncFor(info, mode), List(keyVar)),
        pred
      )
    )

  private[z3] def emitRelationValueRefinement(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      fieldName: String,
      valueConstraint: expr,
      mode: StateMode
  ): Unit =
    val suffix  = if mode == StateMode.Post then "_post" else ""
    val varName = s"k_$fieldName$suffix"
    val keyVar  = Z3Expr.Var(varName, info.keySort)
    val body =
      translateRefinement(ctx, valueConstraint, Z3Expr.App(mapFuncFor(info, mode), List(keyVar)))
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

  private[z3] def declareStateField(ctx: TranslateCtx, sf: state_field_decl): Unit =
    stfType(sf) match
      case RelationTypeF(from, mult, to, _) =>
        declareRelationStateField(
          ctx,
          sf,
          from,
          to,
          isTotal = mult match { case _: MultOne => true; case _ => false }
        )
      case MapTypeF(k, v, _) =>
        declareRelationStateField(ctx, sf, k, v, isTotal = false)
      case _ =>
        val fieldSort = sortForType(ctx, stfType(sf))
        val funcName  = s"state_${stfName(sf)}"
        ctx.declareFunc(Z3FunctionDecl(funcName, Nil, fieldSort))
        ctx.state(stfName(sf)) = StateConstInfo(fieldSort, funcName, s"${funcName}_post")

  private[z3] def declareRelationStateField(
      ctx: TranslateCtx,
      sf: state_field_decl,
      keyType: type_expr,
      valueType: type_expr,
      isTotal: Boolean
  ): Unit =
    val keySort   = sortForType(ctx, keyType)
    val valueSort = sortForType(ctx, valueType)
    val domFunc   = s"${stfName(sf)}_dom"
    val mapFunc   = s"${stfName(sf)}_map"
    ctx.declareFunc(Z3FunctionDecl(domFunc, List(keySort), Z3Sort.Bool))
    ctx.declareFunc(Z3FunctionDecl(mapFunc, List(keySort), valueSort))
    ctx.state(stfName(sf)) = StateRelationInfo(
      keySort,
      valueSort,
      domFunc,
      mapFunc,
      s"${stfName(sf)}_dom_post",
      s"${stfName(sf)}_map_post",
      isTotal = isTotal
    )

  private[z3] def emitStateTotality(
      ctx: TranslateCtx,
      sf: state_field_decl,
      mode: StateMode
  ): Unit =
    ctx.state.get(stfName(sf)) match
      case Some(r: StateRelationInfo) if r.isTotal =>
        val suffix  = if mode == StateMode.Post then "_post" else ""
        val varName = s"k_${stfName(sf)}$suffix"
        val body    = Z3Expr.App(domFuncFor(r, mode), List(Z3Expr.Var(varName, r.keySort)))
        ctx.assertions += Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, r.keySort)),
          body
        )
      case _ => ()

  private[z3] def emitTopLevelInvariant(ctx: TranslateCtx, inv: invariant_decl): Unit =
    val env = mutable.Map.empty[String, Z3Expr]
    ctx.assertions += translateCheckedExpr(ctx, invBody(inv), env)

  private[z3] def emitStringLiteralDistinctness(ctx: TranslateCtx): Unit =
    val n = ctx.stringLitCount
    if n < 2 then return
    val consts = (0 until n).toList.map(i => Z3Expr.App(s"str_$i", Nil))
    val pairs  = mutable.ArrayBuffer.empty[Z3Expr]
    for i <- consts.indices; j <- (i + 1) until consts.length do
      pairs += Z3Expr.Cmp(CmpOp.Neq, consts(i), consts(j))
    val out = if pairs.length == 1 then pairs.head else Z3Expr.And(pairs.toList)
    ctx.assertions += out

  private[z3] def sortForType(ctx: TranslateCtx, te: type_expr): Z3Sort = te match
    case NamedTypeF(n, _)      => sortForNamedType(ctx, n)
    case OptionTypeF(inner, _) => Z3Sort.OptionOf(sortForType(ctx, inner))
    case SetTypeF(e, _)        => Z3Sort.SetOf(sortForType(ctx, e))
    case SeqTypeF(e, _)        => Z3Sort.SeqOf(sortForType(ctx, e))
    case MapTypeF(k, v, _) =>
      Z3Sort.MapOf(sortForType(ctx, k), sortForType(ctx, v))
    case RelationTypeF(f, _, t, _) =>
      Z3Sort.Uninterp(s"Rel_${sortNameOf(sortForType(ctx, f))}_${sortNameOf(sortForType(ctx, t))}")

  private[z3] def sortForNamedType(ctx: TranslateCtx, name: String): Z3Sort =
    primitiveSortOf(name).getOrElse:
      name match
        case "String" => Z3Sort.Str
        case _ =>
          ctx.entities.get(name).map(_.sort).orElse {
            ctx.enums.get(name).map(_.sort)
          }.orElse {
            ctx.primitiveAliases.get(name).map(_.underlyingSort)
          }.orElse {
            ctx.typeAliases.get(name).map(_.sort)
          }.getOrElse(Z3Sort.Uninterp(name))
