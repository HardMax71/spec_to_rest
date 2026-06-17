package specrest.verify.z3

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable

@SuppressWarnings(Array(
  "org.wartremover.warts.Var",
  "org.wartremover.warts.Return",
  "org.wartremover.warts.OptionPartial"
))
private[z3] trait ExpressionEncoder:
  this: Declarations & SmtTermBridge & Z3EncodingSupport =>
  private[z3] def translateCheckedExpr(
      ctx: TranslateCtx,
      expr: expr,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    translateExtractedExpr(ctx, expr, env).getOrElse:
      fail(ctx, "sound-check expression is outside the Isabelle-extracted Z3 translator subset")

  private[z3] def translateDeclarationExpr(
      ctx: TranslateCtx,
      expr: expr,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    translateExtractedExpr(ctx, expr, env).getOrElse:
      translateDeclarationExprRaw(ctx, expr, env).withSpan(spanOf(expr))

  private[z3] def translateExtractedExpr(
      ctx: TranslateCtx,
      expr: expr,
      env: mutable.Map[String, Z3Expr]
  ): Option[Z3Expr] =
    val enums = ctx.enums.keys.toList
    SpecRestGenerated
      .translate(enums, rewriteRangeComprehension(ctx, expr))
      .map(smtTerm => encodeFromSmtTerm(ctx, smtTerm, env).withSpan(spanOf(expr)))

  // `translate` is untyped: it projects `{ x in rel | true }` onto rel's keys (domain). When the
  // receiver is sorted by rel's value type the intended projection is the range (values); decide
  // that here, where the receiver's sort is known, by rewriting to the verified `range(rel)` form.
  private[z3] def rewriteRangeComprehension(ctx: TranslateCtx, e: expr): expr = e match
    case BinaryOpF(BEq(), l, SetComprehensionF(_, IdentifierF(rel, spRel), pred, spSc), spEq)
        if isTrueLit(pred) && isValueProjection(ctx, l, rel) =>
      BinaryOpF(BEq(), l, rangeOf(rel, spRel, spSc), spEq)
    case BinaryOpF(BEq(), SetComprehensionF(_, IdentifierF(rel, spRel), pred, spSc), r, spEq)
        if isTrueLit(pred) && isValueProjection(ctx, r, rel) =>
      BinaryOpF(BEq(), r, rangeOf(rel, spRel, spSc), spEq)
    case BinaryOpF(op, l, r, sp) if isBoolConnective(op) =>
      BinaryOpF(op, rewriteRangeComprehension(ctx, l), rewriteRangeComprehension(ctx, r), sp)
    case UnaryOpF(op, x, sp) =>
      UnaryOpF(op, rewriteRangeComprehension(ctx, x), sp)
    case other => other

  private[z3] def rangeOf(rel: String, spRel: Option[span_t], spSc: Option[span_t]): expr =
    CallF(IdentifierF("range", spRel), List(IdentifierF(rel, spRel)), spSc)

  private[z3] def isTrueLit(e: expr): Boolean = e match
    case BoolLitF(true, _) => true
    case _                 => false

  private[z3] def isBoolConnective(op: bin_op): Boolean = op match
    case BAnd() | BOr() | BImplies() | BIff() => true
    case _                                    => false

  private[z3] def isValueProjection(ctx: TranslateCtx, receiver: expr, rel: String): Boolean =
    (relKeyValueSorts(ctx, rel), receiverSetElemSort(ctx, receiver)) match
      case (Some((keySort, valueSort)), Some(elemSort)) =>
        elemSort == valueSort && valueSort != keySort
      case _ => false

  private[z3] def relKeyValueSorts(ctx: TranslateCtx, rel: String): Option[(Z3Sort, Z3Sort)] =
    ctx.state.get(rel).collect { case r: StateRelationInfo => (r.keySort, r.valueSort) }

  private[z3] def receiverSetElemSort(ctx: TranslateCtx, e: expr): Option[Z3Sort] = e match
    case IdentifierF(name, _) =>
      ctx.state
        .get(name)
        .collect { case c: StateConstInfo => c.sort }
        .orElse(ctx.outputs.find(_.name == name).map(_.sort))
        .orElse(ctx.inputs.find(_.name == name).map(_.sort))
        .collect { case Z3Sort.SetOf(elem) => elem }
    case PrimeF(inner, _) => receiverSetElemSort(ctx, inner)
    case PreF(inner, _)   => receiverSetElemSort(ctx, inner)
    case _                => None

  private[z3] def translateDeclarationExprRaw(
      ctx: TranslateCtx,
      expr: expr,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr match
    case IntLitF(v, _) => Z3Expr.IntLit(v)
    case FloatLitF(s, _) =>
      decimalToRat(s) match
        case Some(r) =>
          val (num, den) = quotient_of(r)
          Z3Expr.RealLit(num, den)
        case None => fail(ctx, s"malformed float literal: '$s'")
    case BoolLitF(v, _)              => Z3Expr.BoolLit(v)
    case StringLitF(v, _)            => Z3Expr.StrLit(v)
    case IdentifierF(name, _)        => resolveIdentifier(ctx, name, env)
    case BinaryOpF(op, l, r, _)      => translateBinaryOp(ctx, op, l, r, env)
    case u @ UnaryOpF(_, _, _)       => translateUnaryOp(ctx, u, env)
    case q @ QuantifierF(_, _, _, _) => translateQuantifier(ctx, q, env)
    case f @ FieldAccessF(_, _, _)   => translateFieldAccess(ctx, f, env)
    case i @ IndexF(_, _, _)         => translateIndex(ctx, i, env)
    case c @ CallF(_, _, _)          => translateCall(ctx, c, env)
    case m @ MatchesF(_, _, _)       => translateMatches(ctx, m, env)
    case e @ EnumAccessF(_, _, _)    => translateEnumAccess(ctx, e)
    case PrimeF(inner, _) =>
      withStateMode(ctx, StateMode.Post, () => translateDeclarationExpr(ctx, inner, env))
    case PreF(inner, _) =>
      withStateMode(ctx, StateMode.Pre, () => translateDeclarationExpr(ctx, inner, env))
    case w @ WithF(_, _, _)                 => translateWith(ctx, w, env)
    case sc @ SetComprehensionF(_, _, _, _) => translateSetComprehension(ctx, sc)
    case sl @ SetLiteralF(_, _)             => translateSetLiteral(ctx, sl, env)
    case l @ LetF(_, _, _, _)               => translateLet(ctx, l, env)
    case other =>
      fail(
        ctx,
        s"expression kind '${other.getClass.getSimpleName}' is not yet supported by the verifier"
      )

  private[z3] def translateBinaryOp(
      ctx: TranslateCtx,
      op: bin_op,
      leftExpr: expr,
      rightExpr: expr,
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
      val left = translateDeclarationExpr(ctx, leftExpr, env)
      val mem  = membership(leftExpr, rightExpr, left, ctx, op, env)
      return if isIn then mem else Z3Expr.Not(mem)
    val left  = translateDeclarationExpr(ctx, leftExpr, env)
    val right = translateDeclarationExpr(ctx, rightExpr, env)
    op match
      case BAnd()     => Z3Expr.And(List(left, right))
      case BOr()      => Z3Expr.Or(List(left, right))
      case BImplies() => Z3Expr.Implies(left, right)
      case BIff() =>
        Z3Expr.And(List(Z3Expr.Implies(left, right), Z3Expr.Implies(right, left)))
      case BEq() | BNeq() =>
        val (l2, r2) = coerceOptionalEquality(ctx, left, right)
        Z3Expr.Cmp(if isEq then CmpOp.Eq else CmpOp.Neq, l2, r2)
      case BLt() | BLe() | BGt() | BGe() =>
        requireNumericOperands(ctx, op, leftExpr, rightExpr, left, right, env)
        val cmp = op match
          case BLt() => CmpOp.Lt
          case BLe() => CmpOp.Le
          case BGt() => CmpOp.Gt
          case _     => CmpOp.Ge
        Z3Expr.Cmp(cmp, left, right)
      case BAdd() | BSub() | BMul() | BDiv() =>
        requireNumericOperands(ctx, op, leftExpr, rightExpr, left, right, env)
        val aop = op match
          case BAdd() => ArithOp.Add
          case BSub() => ArithOp.Sub
          case BMul() => ArithOp.Mul
          case _      => ArithOp.Div
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

  private[z3] def requireNumericOperands(
      ctx: TranslateCtx,
      op: bin_op,
      leftExpr: expr,
      rightExpr: expr,
      left: Z3Expr,
      right: Z3Expr,
      env: mutable.Map[String, Z3Expr]
  ): Unit =
    val leftSort = inferSort(ctx, leftExpr, env, Some(left)).orElse(inferSortOfZ3Expr(ctx, left))
    val rightSort =
      inferSort(ctx, rightExpr, env, Some(right)).orElse(inferSortOfZ3Expr(ctx, right))
    if !leftSort.exists(Z3Sort.isNumeric) || !rightSort.exists(Z3Sort.isNumeric) then
      fail(ctx, s"'${binOpToTs(op)}' requires numeric operands (Int or Real)")

  private[z3] def ensureSetBinOpSorts(
      ctx: TranslateCtx,
      leftExpr: expr,
      rightExpr: expr,
      left: Z3Expr,
      right: Z3Expr,
      op: bin_op,
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

  private[z3] def membership(
      leftExpr: expr,
      rightExpr: expr,
      leftZ: Z3Expr,
      ctx: TranslateCtx,
      op: bin_op,
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
            val rightZ = translateDeclarationExpr(ctx, rightExpr, env)
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

  private[z3] def membershipInSetLiteral(
      ctx: TranslateCtx,
      leftZ: Z3Expr,
      elements: List[expr],
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    if elements.isEmpty then Z3Expr.BoolLit(false)
    else
      val translated = elements.map(e => translateDeclarationExpr(ctx, e, env))
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

  private[z3] def membershipInComprehension(
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
    val predicate = translateDeclarationExpr(ctx, sc.c, subEnv)
    resolved.guard match
      case None => predicate
      case Some(gFn) =>
        val guard      = gFn(sc.a)
        val guardSubst = substituteVar(guard, sc.a, leftZ)
        Z3Expr.And(List(guardSubst, predicate))

  private[z3] def substituteVar(expr: Z3Expr, varName: String, replacement: Z3Expr): Z3Expr =
    expr match
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
      case Z3Expr.StrCmp(op, l, r, sp) =>
        Z3Expr.StrCmp(
          op,
          substituteVar(l, varName, replacement),
          substituteVar(r, varName, replacement),
          sp
        )
      case Z3Expr.StrConcat(l, r, sp) =>
        Z3Expr.StrConcat(
          substituteVar(l, varName, replacement),
          substituteVar(r, varName, replacement),
          sp
        )
      case Z3Expr.SeqConcat(l, r, sp) =>
        Z3Expr.SeqConcat(
          substituteVar(l, varName, replacement),
          substituteVar(r, varName, replacement),
          sp
        )
      case Z3Expr.SeqContains(s, e, sp) =>
        Z3Expr.SeqContains(
          substituteVar(s, varName, replacement),
          substituteVar(e, varName, replacement),
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
      case Z3Expr.Ite(c, t, e, sp) =>
        Z3Expr.Ite(
          substituteVar(c, varName, replacement),
          substituteVar(t, varName, replacement),
          substituteVar(e, varName, replacement),
          sp
        )
      case Z3Expr.OptSome(value, sp) =>
        Z3Expr.OptSome(substituteVar(value, varName, replacement), sp)
      case Z3Expr.OptGet(value, sp) =>
        Z3Expr.OptGet(substituteVar(value, varName, replacement), sp)
      case Z3Expr.SeqLit(elemSort, members, sp) =>
        Z3Expr.SeqLit(elemSort, members.map(m => substituteVar(m, varName, replacement)), sp)
      case Z3Expr.MapLit(keySort, valueSort, entries, sp) =>
        Z3Expr.MapLit(
          keySort,
          valueSort,
          entries.map { case (k, v) =>
            (substituteVar(k, varName, replacement), substituteVar(v, varName, replacement))
          },
          sp
        )
      case Z3Expr.InRe(str, re, sp) =>
        Z3Expr.InRe(substituteVar(str, varName, replacement), re, sp)
      case other => other

  private[z3] def resolveStateRelationReference(
      ctx: TranslateCtx,
      expr: expr
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

  private[z3] def translateUnaryOp(
      ctx: TranslateCtx,
      expr: UnaryOpF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = expr.a match
    case UNot() => Z3Expr.Not(translateDeclarationExpr(ctx, expr.b, env))
    case UNegate() =>
      Z3Expr.Arith(
        ArithOp.Sub,
        List(Z3Expr.IntLit(BigInt(0)), translateDeclarationExpr(ctx, expr.b, env))
      )
    case UCardinality() => translateCardinality(ctx, expr.b)
    case UPower() =>
      fail(
        ctx,
        "powerset operator is not decidable in first-order SMT; narrow the invariant to avoid powerset"
      )

  private[z3] def translateCardinality(ctx: TranslateCtx, operand: expr): Z3Expr =
    SpecRestGenerated.peelRelationRef(operand) match
      case Some(name) =>
        val mode = operand match
          case PrimeF(_, _) => StateMode.Post
          case PreF(_, _)   => StateMode.Pre
          case _            => ctx.stateMode
        cardinalityRefFor(ctx, name, mode)
      case None =>
        fail(ctx, "cardinality '#expr' is only supported on state-relation identifiers")

  private[z3] def translateQuantifier(
      ctx: TranslateCtx,
      q: QuantifierF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val newEnv       = env.clone()
    val bindings     = mutable.ArrayBuffer.empty[Z3Binding]
    val domainGuards = mutable.ArrayBuffer.empty[Z3Expr]
    for bnd <- q.b do
      val resolved = resolveBindingDomain(ctx, bnd)
      bindings += Z3Binding(qbdVar(bnd), resolved.sort)
      newEnv(qbdVar(bnd)) = Z3Expr.Var(qbdVar(bnd), resolved.sort)
      resolved.guard.foreach(gFn => domainGuards += gFn(qbdVar(bnd)))
    val body        = translateDeclarationExpr(ctx, q.c, newEnv)
    val guardedBody = applyGuards(q.a, domainGuards.toList, body)
    mapQuantifier(q.a) match
      case Right(zq) => Z3Expr.Quantifier(zq, bindings.toList, guardedBody)
      case Left(_) =>
        Z3Expr.Not(
          Z3Expr.Quantifier(QKind.Exists, bindings.toList, guardedBody)
        )

  private[z3] def mapQuantifier(q: quant_kind): Either[Unit, QKind] = q match
    case QAll()              => Right(QKind.ForAll)
    case QSome() | QExists() => Right(QKind.Exists)
    case QNo()               => Left(())

  private[z3] def applyGuards(q: quant_kind, guards: List[Z3Expr], body: Z3Expr): Z3Expr =
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

  final private[z3] case class BindingResolution(sort: Z3Sort, guard: Option[String => Z3Expr])

  private[z3] def resolveBindingDomain(
      ctx: TranslateCtx,
      b: quantifier_binding
  ): BindingResolution = qbdCollection(b) match
    case IdentifierF(name, _) =>
      ctx.entities.get(name).map(e => BindingResolution(e.sort, None)).orElse:
        ctx.typeAliases.get(name).map(a => BindingResolution(a.sort, None))
      .orElse:
        ctx.primitiveAliases.get(name).map: pa =>
          val gFn: String => Z3Expr = vn =>
            val env = mutable.Map.empty[String, Z3Expr]
            env("value") = Z3Expr.Var(vn, pa.underlyingSort)
            translateDeclarationExpr(ctx, pa.constraint, env)
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

  private[z3] def translateFieldAccess(
      ctx: TranslateCtx,
      expr: FieldAccessF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val base     = translateDeclarationExpr(ctx, expr.a, env)
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

  private[z3] def translateIndex(
      ctx: TranslateCtx,
      expr: IndexF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr = resolveStateRelationReference(ctx, expr.a) match
    case Some((info, mode)) =>
      val key = translateDeclarationExpr(ctx, expr.b, env)
      Z3Expr.App(mapFuncFor(info, mode), List(key))
    case None =>
      fail(
        ctx,
        "indexing is only supported on state-relation references (including primed/pre-state forms); general map/sequence indexing is not supported"
      )

  private[z3] def translateCall(
      ctx: TranslateCtx,
      expr: CallF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    expr.a match
      case IdentifierF(name, _) =>
        val args = expr.b.map(a => translateDeclarationExpr(ctx, a, env))
        // Derive arg sorts from the translated terms, not a second inferSort pass on the source:
        // the declared parameter sorts then match the applied arguments by construction (e.g. a bare
        // enum member resolves to its enum sort here, where source inferSort would miss it and pick Any).
        val argSorts =
          args.map(a => inferSortOfZ3Expr(ctx, a).getOrElse(Z3Sort.Uninterp("Any")))
        val resultSort = callReturnSort(name, ctx)
        val funcName   = s"${name}_${argSortsMangled(argSorts)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, argSorts, resultSort))
        Z3Expr.App(funcName, args)
      case _ =>
        fail(ctx, "higher-order call (non-identifier callee) is not supported by the verifier")

  private[z3] def callReturnSort(name: String, ctx: TranslateCtx): Z3Sort = name match
    case "len" | "now" | "seconds" | "minutes" | "hours" | "days" => Z3Sort.Int
    case n if ctx.predicateNames.contains(n)                      => Z3Sort.Bool
    case _                                                        => Z3Sort.Uninterp("Any")

  private[z3] def argSortsMangled(sorts: List[Z3Sort]): String =
    if sorts.isEmpty then "0" else sorts.map(sortNameOf).mkString("_")

  private[z3] def translateMatches(
      ctx: TranslateCtx,
      expr: MatchesF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val arg     = translateDeclarationExpr(ctx, expr.a, env)
    val argSort = inferSort(ctx, expr.a, env, Some(arg))
    (argSort, RegexParser.parse(expr.b)) match
      case (Some(Z3Sort.Str), Some(re)) => Z3Expr.InRe(arg, re)
      case _ =>
        val s        = argSort.getOrElse(Z3Sort.Str)
        val funcName = s"${ctx.matchesNameFor(expr.b)}_${sortNameOf(s)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, List(s), Z3Sort.Bool))
        Z3Expr.App(funcName, List(arg))

  private[z3] def translateLet(
      ctx: TranslateCtx,
      expr: LetF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val value  = translateDeclarationExpr(ctx, expr.b, env)
    val newEnv = env.clone()
    newEnv(expr.a) = value
    translateDeclarationExpr(ctx, expr.c, newEnv)

  private[z3] def translateWith(
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
            val baseZ      = translateDeclarationExpr(ctx, expr.a, env)
            val skolemName = ctx.freshSkolem(s"with_$name")
            ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, Z3Sort.Uninterp(name)))
            val skolemRef    = Z3Expr.App(skolemName, Nil)
            val updatedNames = expr.b.map(fasName).toSet
            for (fname, (_, funcName)) <- entity.fields do
              if !updatedNames.contains(fname) then
                ctx.assertions += Z3Expr.Cmp(
                  CmpOp.Eq,
                  Z3Expr.App(funcName, List(skolemRef)),
                  Z3Expr.App(funcName, List(baseZ))
                )
            for fa <- expr.b do
              val uName  = fasName(fa)
              val uValue = fasValue(fa)
              entity.fields.get(uName) match
                case None =>
                  fail(ctx, s"entity '$name' has no field '$uName'")
                case Some((_, funcName)) =>
                  val value = translateDeclarationExpr(ctx, uValue, env)
                  ctx.assertions += Z3Expr.Cmp(
                    CmpOp.Eq,
                    Z3Expr.App(funcName, List(skolemRef)),
                    value
                  )
            skolemRef
      case _ =>
        fail(ctx, "'with' expression requires a known entity sort")

  private[z3] def translateSetComprehension(ctx: TranslateCtx, sc: SetComprehensionF): Z3Expr =
    val _ = sc
    fail(
      ctx,
      "standalone set comprehensions as expressions are not supported because the binder's element sort typically does not match the receiver's declared type; use an inline membership form: `y in {x in S | P}`"
    )

  private[z3] def translateSetLiteral(
      ctx: TranslateCtx,
      sl: SetLiteralF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    if sl.a.isEmpty then
      fail(
        ctx,
        "empty set literal '{}' requires context to infer its element sort; use a non-empty set"
      )
    val translated  = sl.a.map(e => translateDeclarationExpr(ctx, e, env))
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

  private[z3] def translateEnumAccess(ctx: TranslateCtx, expr: EnumAccessF): Z3Expr =
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

  private[z3] def inferSort(
      ctx: TranslateCtx,
      expr: expr,
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
    case FloatLitF(_, _)                   => Some(Z3Sort.Real)
    case BoolLitF(_, _)                    => Some(Z3Sort.Bool)
    case StringLitF(_, _)                  => Some(Z3Sort.Str)
    case CallF(IdentifierF(name, _), _, _) => Some(callReturnSort(name, ctx))
    case SetLiteralF(elements, _) =>
      elements.iterator.flatMap(e => inferSort(ctx, e, env, None)).nextOption().map(Z3Sort.SetOf(_))
    case _ =>
      translated match
        case Some(Z3Expr.Var(_, sort, _)) => Some(sort)
        case _                            => None

  private[z3] def tryLowerDomEquality(
      ctx: TranslateCtx,
      leftExpr: expr,
      rightExpr: expr,
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

  private[z3] def asDomOfStateRelation(
      ctx: TranslateCtx,
      expr: expr
  ): Option[(StateRelationInfo, StateMode)] = expr match
    case CallF(IdentifierF("dom", _), arg :: Nil, _) =>
      resolveStateRelationReference(ctx, arg)
    case _ => None

  private[z3] def tryLowerSetComprehensionEquality(
      ctx: TranslateCtx,
      leftExpr: expr,
      rightExpr: expr,
      env: mutable.Map[String, Z3Expr]
  ): Option[Z3Expr] =
    val pair: Option[(expr, SetComprehensionF)] = (leftExpr, rightExpr) match
      case (sc: SetComprehensionF, other) => Some((other, sc))
      case (other, sc: SetComprehensionF) => Some((other, sc))
      case _                              => None
    pair.map: (setExpr, sc) =>
      translateSetComprehensionEquality(ctx, setExpr, sc, env)

  private[z3] def translateSetComprehensionEquality(
      ctx: TranslateCtx,
      setExpr: expr,
      sc: SetComprehensionF,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val setZ = translateDeclarationExpr(ctx, setExpr, env)
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
    val predicate = translateDeclarationExpr(ctx, sc.c, subEnv)
    val domAndPred = resolved.guard match
      case None      => predicate
      case Some(gFn) => Z3Expr.And(List(gFn(freshName), predicate))
    val memberInSet = Z3Expr.SetMember(varZ, setZ)
    val iff = Z3Expr.And(List(
      Z3Expr.Implies(memberInSet, domAndPred),
      Z3Expr.Implies(domAndPred, memberInSet)
    ))
    Z3Expr.Quantifier(QKind.ForAll, List(Z3Binding(freshName, elemSort)), iff)
