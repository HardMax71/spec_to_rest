package specrest.verify.z3

import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable

@SuppressWarnings(Array(
  "org.wartremover.warts.Var",
  "org.wartremover.warts.Return",
  "org.wartremover.warts.OptionPartial"
))
private[z3] trait RelationFrames:
  this: Declarations & ExpressionEncoder & Z3EncodingSupport =>
  private[z3] def translateEnsuresClause(
      ctx: TranslateCtx,
      expr: expr,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr match
    case BinaryOpF(BEq(), l, r, _) =>
      tryLowerRelationEquality(ctx, l, r, env).getOrElse(translateCheckedExpr(ctx, expr, env))
    // Recurse through the conjunction/let structure so a relation-assignment nested inside a
    // `let ... in (... and X' = rhs)` still gets its frame axiom (the dispatcher would otherwise
    // only fire on a top-level `X' = rhs`, leaving the post-state relation under-constrained).
    case BinaryOpF(BAnd(), a, b, _) =>
      Z3Expr.And(
        List(translateEnsuresClause(ctx, a, env), translateEnsuresClause(ctx, b, env))
      )
    case LetF(x, v, body, _) =>
      val newEnv = env.clone()
      newEnv(x) = translateEnsuresClause(ctx, v, env)
      translateEnsuresClause(ctx, body, newEnv)
    // A conditional effect `cond implies (... X' = rhs ...)` must keep recursing into the body so
    // the relation-assignment still routes through the frame path; otherwise the nested `pre(X) +
    // {..}` reaches the generic translator as a bare `+` and is rejected as numeric addition. For a
    // body with no relation-assignment this yields the same `Implies(cond, body)` as before.
    case BinaryOpF(BImplies(), cond, body, _) =>
      Z3Expr.Implies(translateCheckedExpr(ctx, cond, env), translateEnsuresClause(ctx, body, env))
    case _ => translateCheckedExpr(ctx, expr, env)

  private[z3] def tryLowerRelationEquality(
      ctx: TranslateCtx,
      leftExpr: expr,
      rightExpr: expr,
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

  private[z3] type RelationRhsLowering = (StateRelationInfo, StateMode) => Z3Expr

  private[z3] def lowerRelationRhs(
      ctx: TranslateCtx,
      expr: expr,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] =
    tryLowerSingleInsertRhs(ctx, expr, targetInfo, env)
      .orElse(tryLowerSingleMinusRhs(ctx, expr, targetInfo, env))

  private[z3] def tryLowerSingleInsertRhs(
      ctx: TranslateCtx,
      expr: expr,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] =
    collectInsertChain(ctx, expr).map: (base, entries) =>
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

  // Flatten a chained insert `base + {k1->v1} + ... + {kn->vn}` (left-assoc `+`, each operand a
  // MapLiteral) into the base relation plus all entries; relationInsertionAxiom emits the
  // multi-entry frame as a single axiom. A single insert is the one-step base case.
  private[z3] def collectInsertChain(
      ctx: TranslateCtx,
      expr: expr
  ): Option[((StateRelationInfo, StateMode), List[KeyValueEntry])] = expr match
    case BinaryOpF(_: (BAdd | BUnion), left, right, _) =>
      extractMapEntries(right).filter(_.nonEmpty).flatMap: entries =>
        resolveStateRelationReference(ctx, left) match
          case Some(base) => Some((base, entries))
          case None =>
            collectInsertChain(ctx, left).map((base, acc) => (base, acc ++ entries))
    case _ => None

  private[z3] def tryLowerSingleMinusRhs(
      ctx: TranslateCtx,
      expr: expr,
      targetInfo: StateRelationInfo,
      env: mutable.Map[String, Z3Expr]
  ): Option[RelationRhsLowering] = expr match
    case BinaryOpF(_: (BSub | BDiff), left, right, _) =>
      resolveStateRelationReference(ctx, left).flatMap: base =>
        extractKeySet(right).filter(_.nonEmpty).map: keys =>
          (lhsInfo: StateRelationInfo, lhsMode: StateMode) =>
            relationDeletionAxiom(ctx, lhsInfo, lhsMode, base._1, base._2, keys, env, targetInfo)
    case _ => None

  final private[z3] case class KeyValueEntry(key: expr, value: expr)

  private[z3] def extractMapEntries(expr: expr): Option[List[KeyValueEntry]] = expr match
    case MapLiteralF(entries, _) =>
      Some(entries.map(e => KeyValueEntry(mpeKey(e), mpeValue(e))))
    case _ => None

  private[z3] def extractKeySet(expr: expr): Option[List[expr]] = expr match
    case SetLiteralF(elements, _) => Some(elements)
    case MapLiteralF(entries, _) =>
      Some(entries.map(mpeKey))
    case _ => None

  private[z3] def relationEqualityAxiom(
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
        iff(aDom, bDom),
        Z3Expr.Implies(aDom, Z3Expr.Cmp(CmpOp.Eq, aMap, bMap))
      ))
      Some(Z3Expr.Quantifier(QKind.ForAll, List(Z3Binding(varName, a.keySort)), body))

  private[z3] def relationInsertionAxiom(
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
      (translateCheckedExpr(ctx, e.key, env), translateCheckedExpr(ctx, e.value, env))
    val keyEqs   = translated.map((k, _) => Z3Expr.Cmp(CmpOp.Eq, keyVar, k))
    val anyKeyEq = if keyEqs.length == 1 then keyEqs.head else Z3Expr.Or(keyEqs)
    val lhsDom   = Z3Expr.App(domFuncFor(lhs, lhsMode), List(keyVar))
    val baseDom  = Z3Expr.App(domFuncFor(base, baseMode), List(keyVar))
    val domBody  = iff(lhsDom, Z3Expr.Or(List(baseDom, anyKeyEq)))
    val lhsMap   = Z3Expr.App(mapFuncFor(lhs, lhsMode), List(keyVar))
    val baseMap  = Z3Expr.App(mapFuncFor(base, baseMode), List(keyVar))
    // Last-write-wins: a chained / multi-entry insert with a repeated key keeps the LAST value.
    // An entry's value applies only when keyVar matches no later entry's key; without this guard
    // duplicate keys would assert two values for one key and the axiom would be UNSAT.
    val perEntry = translated.zipWithIndex.map: (kv, i) =>
      val (k, v) = kv
      val laterNeqs =
        translated.drop(i + 1).map((lk, _) => Z3Expr.Not(Z3Expr.Cmp(CmpOp.Eq, keyVar, lk)))
      Z3Expr.Implies(
        Z3Expr.And(Z3Expr.Cmp(CmpOp.Eq, keyVar, k) :: laterNeqs),
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

  private[z3] def relationDeletionAxiom(
      ctx: TranslateCtx,
      lhs: StateRelationInfo,
      lhsMode: StateMode,
      base: StateRelationInfo,
      baseMode: StateMode,
      keyExprs: List[expr],
      env: mutable.Map[String, Z3Expr],
      targetInfo: StateRelationInfo
  ): Z3Expr =
    val _         = targetInfo
    val varName   = s"k_delete_${lhs.domFunc}"
    val keyVar    = Z3Expr.Var(varName, lhs.keySort)
    val keys      = keyExprs.map(k => translateCheckedExpr(ctx, k, env))
    val keyEqs    = keys.map(k => Z3Expr.Cmp(CmpOp.Eq, keyVar, k))
    val anyKeyEq  = if keyEqs.length == 1 then keyEqs.head else Z3Expr.Or(keyEqs)
    val notAnyKey = Z3Expr.Not(anyKeyEq)
    val lhsDom    = Z3Expr.App(domFuncFor(lhs, lhsMode), List(keyVar))
    val baseDom   = Z3Expr.App(domFuncFor(base, baseMode), List(keyVar))
    val domBody   = iff(lhsDom, Z3Expr.And(List(baseDom, notAnyKey)))
    val lhsMap    = Z3Expr.App(mapFuncFor(lhs, lhsMode), List(keyVar))
    val baseMap   = Z3Expr.App(mapFuncFor(base, baseMode), List(keyVar))
    val mapBody = Z3Expr.Implies(
      Z3Expr.And(List(baseDom, notAnyKey)),
      Z3Expr.Cmp(CmpOp.Eq, lhsMap, baseMap)
    )
    Z3Expr.Quantifier(
      QKind.ForAll,
      List(Z3Binding(varName, lhs.keySort)),
      Z3Expr.And(List(domBody, mapBody))
    )

  private[z3] def synthesizeFrame(
      ctx: TranslateCtx,
      state: Option[state_decl],
      op: operation_decl,
      env: mutable.Map[String, Z3Expr]
  ): Unit = state match
    case None => ()
    case Some(s) =>
      for sf <- stdFields(s) do
        val sfName = stfName(sf)
        ctx.state.get(sfName) match
          case None => ()
          case Some(info) =>
            val analysis = analyzeStateMention(flattenForFrame(operEnsures(op)), sfName)
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

  private[z3] def syncCardinalityFrameIfDeclared(ctx: TranslateCtx, stateName: String): Unit =
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

  final private[z3] case class StateMentionAnalysis(
      touched: Boolean,
      fullyReplaced: Boolean,
      removedKeys: List[expr],
      fieldUpdatedKeys: List[(expr, Set[String])],
      hasUnclassifiedMention: Boolean
  )

  // analyzeStateMention only matches the top of each ensures clause, so an update inside
  // `let x = v in (... and X'[k] = w)` goes unframed. Inlining the let with the proof's
  // capture-avoiding `subst` (meaning-preserving) and splitting `and` surfaces it for the matchers.
  private[z3] def flattenForFrame(clauses: List[expr]): List[expr] =
    clauses.flatMap(flattenFrameClause)

  private[z3] def flattenFrameClause(clause: expr): List[expr] = clause match
    case LetF(x, v, body, _)        => flattenFrameClause(subst(x, v, body))
    case BinaryOpF(BAnd(), a, b, _) => flattenFrameClause(a) ++ flattenFrameClause(b)
    case other                      => List(other)

  private[z3] def analyzeStateMention(
      ensures: List[expr],
      stateName: String
  ): StateMentionAnalysis =
    var touched                = false
    var fullyReplaced          = false
    var hasUnclassifiedMention = false
    val removedKeys            = mutable.ArrayBuffer.empty[expr]
    val fieldUpdatedKeys       = mutable.LinkedHashMap.empty[String, (expr, mutable.Set[String])]

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

  // Spans differ between mentions of the same key, so identity must ignore
  // them or two ensures over metadata'[code] count as two distinct keys and
  // the frame emits duplicated (and for singleton field sets, wrong) clauses.
  private[z3] def keyIdentity(expr: expr): String = stripSpans(expr).toString

  private[z3] def matchesFullReplacement(expr: expr, stateName: String): Boolean = expr match
    case BinaryOpF(BEq(), l, r, _) =>
      referencesPrimedRelation(l, stateName) || referencesPrimedRelation(r, stateName)
    case _ => false

  private[z3] def matchNotInPrimed(expr: expr, stateName: String): Option[expr] = expr match
    case BinaryOpF(BNotIn(), l, r, _) if referencesPrimedRelation(r, stateName) =>
      Some(l)
    case _ => None

  final private[z3] case class FieldUpdateMatch(key: expr, field: String)

  private[z3] def matchFieldUpdatePrimed(expr: expr, stateName: String): Option[FieldUpdateMatch] =
    expr match
      case BinaryOpF(BEq(), l, r, _) =>
        matchFieldUpdateSide(l, stateName).orElse(matchFieldUpdateSide(r, stateName))
      case _ => None

  private[z3] def matchFieldUpdateSide(side: expr, stateName: String): Option[FieldUpdateMatch] =
    side match
      case FieldAccessF(IndexF(base, index, _), field, _)
          if referencesPrimedRelation(base, stateName) =>
        Some(FieldUpdateMatch(index, field))
      case _ => None

  private[z3] def matchesCardinalityConstraint(expr: expr, stateName: String): Boolean = expr match
    case BinaryOpF(_, l, r, _) =>
      sideIsPrimeCardinality(l, stateName) || sideIsPrimeCardinality(r, stateName)
    case _ => false

  private[z3] def sideIsPrimeCardinality(side: expr, stateName: String): Boolean = side match
    case UnaryOpF(UCardinality(), PrimeF(IdentifierF(n, _), _), _) =>
      n == stateName
    case _ => false

  private[z3] def emitPartialRelationFrame(
      ctx: TranslateCtx,
      info: StateRelationInfo,
      stateName: String,
      analysis: StateMentionAnalysis,
      env: mutable.Map[String, Z3Expr],
      op: operation_decl
  ): Unit =
    if analysis.hasUnclassifiedMention then return
    val varName         = s"k_pf_$stateName"
    val keyVar          = Z3Expr.Var(varName, info.keySort)
    val domPre          = Z3Expr.App(info.domFunc, List(keyVar))
    val domPost         = Z3Expr.App(info.domFuncPost, List(keyVar))
    val mapPre          = Z3Expr.App(info.mapFunc, List(keyVar))
    val mapPost         = Z3Expr.App(info.mapFuncPost, List(keyVar))
    val removedKeyExprs = analysis.removedKeys.map(k => translateCheckedExpr(ctx, k, env))
    val fieldUpdateKeyExprs = analysis.fieldUpdatedKeys.map: (k, fs) =>
      (translateCheckedExpr(ctx, k, env), fs)
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
      val guards = operRequires(op) ++ operEnsures(op)
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
              List(Z3Expr.App(preCardName, Nil), Z3Expr.IntLit(BigInt(analysis.removedKeys.length)))
            )
          )
    else if analysis.removedKeys.isEmpty && analysis.fieldUpdatedKeys.nonEmpty then
      syncCardinalityFrameIfDeclared(ctx, stateName)

  private[z3] def anyEqual(keyVar: Z3Expr, candidates: List[Z3Expr]): Option[Z3Expr] =
    candidates match
      case Nil      => None
      case c :: Nil => Some(Z3Expr.Cmp(CmpOp.Eq, keyVar, c))
      case xs       => Some(Z3Expr.Or(xs.map(c => Z3Expr.Cmp(CmpOp.Eq, keyVar, c))))

  private[z3] def buildDomClause(
      domPre: Z3Expr,
      domPost: Z3Expr,
      isRemoved: Option[Z3Expr],
      isFieldUpdated: Option[Z3Expr]
  ): Z3Expr =
    val pieces = mutable.ArrayBuffer.empty[Z3Expr]
    isFieldUpdated.foreach(fu => pieces += Z3Expr.Implies(fu, domPost))
    // A field-updated key may be fresh (an insert whose ensures pin fields),
    // so the post-domain bound is pre plus the updated keys; asserting
    // post-subset-of-pre alongside freshness is contradictory and made every
    // such preserves-VC vacuously unsat.
    val domPreOrUpdated = isFieldUpdated match
      case Some(fu) => Z3Expr.Or(List(domPre, fu))
      case None     => domPre
    isRemoved match
      case Some(rm) =>
        val notRemoved = Z3Expr.Not(rm)
        pieces += Z3Expr.Implies(Z3Expr.And(List(notRemoved, domPre)), domPost)
        pieces += Z3Expr.Implies(
          Z3Expr.And(List(notRemoved, Z3Expr.Not(domPreOrUpdated))),
          Z3Expr.Not(domPost)
        )
        pieces += Z3Expr.Implies(rm, Z3Expr.Not(domPost))
      case None =>
        pieces += Z3Expr.Implies(domPre, domPost)
        pieces += Z3Expr.Implies(domPost, domPreOrUpdated)
    if pieces.length == 1 then pieces.head else Z3Expr.And(pieces.toList)

  private[z3] def buildMapClause(
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

  private[z3] def emitUnmentionedFieldFrames(
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

  private[z3] def ensureCardinalityDecl(ctx: TranslateCtx, funcName: String): Unit =
    if ctx.funcs.contains(funcName) then ()
    else
      ctx.declareFunc(Z3FunctionDecl(funcName, Nil, Z3Sort.Int))
      ctx.assertions += Z3Expr.Cmp(
        CmpOp.Ge,
        Z3Expr.App(funcName, Nil),
        Z3Expr.IntLit(BigInt(0))
      )

  private[z3] def synthesizeCardinalityAxioms(
      ctx: TranslateCtx,
      state: Option[state_decl],
      op: operation_decl
  ): Unit = state match
    case None => ()
    case Some(s) =>
      for sf <- stdFields(s) do
        val sfName = stfName(sf)
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
                    List(preRef, Z3Expr.IntLit(BigInt(math.abs(delta))))
                  )
              ctx.assertions += Z3Expr.Cmp(CmpOp.Eq, postRef, rhs)
          case _ => ()

  private[z3] def detectCardinalityDelta(op: operation_decl, relName: String): Option[Int] =
    val guards = operRequires(op) ++ operEnsures(op)
    operEnsures(op).iterator.flatMap: ens =>
      matchPrimedRelationEquality(ens, relName).toList.flatMap: primeEq =>
        if isIdentityRhs(primeEq.rhs, relName) then List(0)
        else
          insertDeltaWithSideCond(guards, primeEq.rhs, relName).map(identity).toList ++
            deleteDeltaWithSideCond(guards, primeEq.rhs, relName).map(-_).toList
    .nextOption()

  private[z3] def insertDeltaWithSideCond(
      guards: List[expr],
      rhs: expr,
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

  private[z3] def deleteDeltaWithSideCond(
      guards: List[expr],
      rhs: expr,
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

  private[z3] def extractInsertKeys(expr: expr): Option[List[expr]] =
    extractMapEntries(expr).map(_.map(_.key)).orElse(extractKeySet(expr))

  private[z3] def hasMembershipSideCond(
      guards: List[expr],
      key: expr,
      relName: String,
      op: bin_op
  ): Boolean =
    guards.exists(g =>
      flattenAnd(g).exists(sub => matchesMembershipSideCond(sub, key, relName, op))
    )

  private[z3] def matchesMembershipSideCond(
      expr: expr,
      key: expr,
      relName: String,
      op: bin_op
  ): Boolean = expr match
    case BinaryOpF(exprOp, l, r, _) if exprOp.getClass.getName == op.getClass.getName =>
      exprStructurallyEqual(l, key) && referencesPreRelation(r, relName)
    case _ => false

  private[z3] def exprStructurallyEqual(a: expr, b: expr): Boolean = a == b

  final private[z3] case class PrimedRelEq(rhs: expr)

  private[z3] def matchPrimedRelationEquality(expr: expr, relName: String): Option[PrimedRelEq] =
    expr match
      case BinaryOpF(BEq(), l, r, _) =>
        if referencesPrimedRelation(l, relName) then Some(PrimedRelEq(r))
        else if referencesPrimedRelation(r, relName) then Some(PrimedRelEq(l))
        else None
      case _ => None

  private[z3] def isIdentityRhs(expr: expr, relName: String): Boolean =
    referencesPreRelation(expr, relName)

  private[z3] def exprMentionsPostState(expr: expr, stateName: String): Boolean =
    walkMentionsPost(expr, stateName, insidePrime = false)

  private[z3] def walkMentionsPost(expr: expr, stateName: String, insidePrime: Boolean): Boolean =
    expr match
      case PrimeF(inner, _)  => walkMentionsPost(inner, stateName, insidePrime = true)
      case PreF(inner, _)    => walkMentionsPost(inner, stateName, insidePrime = false)
      case IdentifierF(n, _) => insidePrime && n == stateName
      case _ =>
        subexprs(expr).exists(walkMentionsPost(_, stateName, insidePrime))
