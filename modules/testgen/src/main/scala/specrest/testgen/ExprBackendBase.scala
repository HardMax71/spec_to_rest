package specrest.testgen

import specrest.ir.Builtins
import specrest.ir.generated.SpecRestGenerated.*

// One shared translation skeleton for every ExprBackend: dispatch, ident
// classification, call plumbing, Skip propagation, and the reserved-name and
// map-domain guards live here exactly once, so the per-language backends
// cannot drift apart on scoping or skip behavior; they supply only the
// rendered target-language tokens.
private[testgen] trait ExprBackendBase extends ExprBackend:

  def languageName: String
  def reservedNames: Set[String]
  def builtinEmit: Builtins.BuiltinSpec => List[String] => String

  def boolLit(v: Boolean): String
  def intLit(n: BigInt): String
  def noneLit: String
  def responseData: String
  def stateObject(mode: CaptureMode): String
  def containerAccess(container: String, name: String): String
  def indexAccess(base: String, idx: String): String
  def userCallName(fname: String): String
  def calleeCall(callee: String, args: List[String]): String
  def binOp(op: bin_op, l: String, r: String): String
  def unOp(op: un_op, x: String): String
  def mapMerge(l: String, r: String): String
  def ifExpr(c: String, t: String, e: String): String
  def letExpr(v: String, value: String, body: String): String
  def emptySet: String
  def setOf(elems: List[String]): String
  def seqOf(elems: List[String]): String
  def emptyMap: String
  def mapPair(k: String, v: String): String
  def mapOf(pairs: List[String]): String
  def fieldPair(name: String, value: String): String
  def recordOf(pairs: List[String]): String
  def withRecord(base: String, pairs: List[String]): String
  def comprehension(v: String, dom: String, isMapDomain: Boolean, pred: String): String
  def quantifierExpr(kind: quant_kind, bound: List[(String, String)], body: String): String
  def theExpr(v: String, dom: String, body: String): String
  def lambdaExpr(param: String, body: String): String
  def matchesExpr(target: String, pattern: String): String
  def setEquals(l: String, r: String): String
  def negate(cond: String): String

  // Set-typed positions surface as JSON arrays, so equality on them must be
  // order-free; names come from the spec's Set-typed inputs and fields.
  private def isSetTypedRef(e: expr, ctx: TestCtx): Boolean = e match
    case IdentifierF(n, _)     => ctx.setTyped.contains(n)
    case FieldAccessF(_, f, _) => ctx.setTyped.contains(f)
    case PrimeF(inner, _)      => isSetTypedRef(inner, ctx)
    case PreF(inner, _)        => isSetTypedRef(inner, ctx)
    case _                     => false

  final def translate(expr: expr, ctx: TestCtx): Translated = expr match
    case BoolLitF(v, _)   => Translated.Emit(boolLit(v))
    case IntLitF(n, _)    => Translated.Emit(intLit(n))
    case FloatLitF(d, _)  => Translated.Emit(d)
    case StringLitF(s, _) => Translated.Emit(stringLiteral(s))
    case NoneLitF(_)      => Translated.Emit(noneLit)

    case IdentifierF(name, span) => resolveIdent(name, ctx, span)

    case PrimeF(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case PreF(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case BinaryOpF(BAdd(), l, r, _) if isMapLiteralExpr(l) || isMapLiteralExpr(r) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))((lp, rp) =>
        Translated.Emit(mapMerge(lp, rp))
      )

    case BinaryOpF(op @ (BEq() | BNeq()), l, r, _)
        if isSetTypedRef(l, ctx) || isSetTypedRef(r, ctx) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx)): (lp, rp) =>
        val eq = setEquals(lp, rp)
        val negated = op match
          case BNeq() => true
          case _      => false
        Translated.Emit(if negated then negate(eq) else eq)

    case BinaryOpF(op, l, r, _) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))((lp, rp) =>
        Translated.Emit(binOp(op, lp, rp))
      )

    case UnaryOpF(op, x, _) =>
      ExprLift.lift1(translate(x, ctx))(xp => Translated.Emit(unOp(op, xp)))

    case FieldAccessF(base, field, _) =>
      ExprLift.lift1(translate(base, ctx))(b => Translated.Emit(containerAccess(b, field)))

    case EnumAccessF(_, member, _) => Translated.Emit(stringLiteral(member))

    case IndexF(base, idx, _) =>
      ExprLift.lift2(translate(base, ctx), translate(idx, ctx))((b, i) =>
        Translated.Emit(indexAccess(b, i))
      )

    case CallF(callee, args, span) => callExpr(callee, args, ctx, span)

    case IfF(c, t, e, _) =>
      ExprLift.lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        Translated.Emit(ifExpr(cp, tp, ep))
      )

    case LetF(v, value, body, span) =>
      if reservedNames.contains(v) then
        Translated.Skip(s"Let with $languageName-reserved binding name '$v'", span)
      else
        ExprLift.lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          Translated.Emit(letExpr(v, vp, bp))
        )

    case SetLiteralF(elements, span) =>
      if elements.isEmpty then Translated.Emit(emptySet)
      else
        val parts = elements.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => Translated.Emit(setOf(ps)))

    case QuantifierF(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case MapLiteralF(entries, span) =>
      if entries.isEmpty then Translated.Emit(emptyMap)
      else
        val pairs = entries.map { e =>
          ExprLift.lift2(translate(mpeKey(e), ctx), translate(mpeValue(e), ctx))((kx, vx) =>
            Translated.Emit(mapPair(kx, vx))
          )
        }
        ExprLift.liftAll(pairs, span)(ps => Translated.Emit(mapOf(ps)))

    case ConstructorF(_, fields, span) =>
      if fields.isEmpty then Translated.Emit(emptyMap)
      else
        val pairs = fields.map { fa =>
          ExprLift.lift1(translate(fasValue(fa), ctx))(vx =>
            Translated.Emit(fieldPair(fasName(fa), vx))
          )
        }
        ExprLift.liftAll(pairs, span)(ps => Translated.Emit(recordOf(ps)))

    case WithF(base, updates, span) =>
      val baseT = translate(base, ctx)
      val pairs = updates.map { fa =>
        ExprLift.lift1(translate(fasValue(fa), ctx))(vx =>
          Translated.Emit(fieldPair(fasName(fa), vx))
        )
      }
      ExprLift.lift1(baseT): bp =>
        ExprLift.liftAll(pairs, span)(ps => Translated.Emit(withRecord(bp, ps)))

    case SetComprehensionF(v, dom, pred, span) =>
      if reservedNames.contains(v) then
        Translated.Skip(s"SetComprehension with $languageName-reserved binding name '$v'", span)
      else
        val innerCtx    = ctx.withBound(List(v))
        val domT        = translate(dom, ctx)
        val predT       = translate(pred, innerCtx)
        val isMapDomain = peelRelationRef(dom).exists(ctx.mapStateFields.contains)
        ExprLift.lift2(domT, predT)((d, p) => Translated.Emit(comprehension(v, d, isMapDomain, p)))

    case SeqLiteralF(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      ExprLift.liftAll(parts, span)(ps => Translated.Emit(seqOf(ps)))

    case MatchesF(e, pattern, _) =>
      ExprLift.lift1(translate(e, ctx))(t => Translated.Emit(matchesExpr(t, pattern)))

    case TheF(v, dom, body, span) =>
      if reservedNames.contains(v) then
        Translated.Skip(s"The with $languageName-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        ExprLift.lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          Translated.Emit(theExpr(v, dp, bp))
        )

    case LambdaF(param, body, span) =>
      if reservedNames.contains(param) then
        Translated.Skip(s"Lambda with $languageName-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        ExprLift.lift1(translate(body, innerCtx))(b => Translated.Emit(lambdaExpr(param, b)))

    case SomeWrapF(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[span_t]): Translated =
    classifyIdent(ctx.identCtx(reservedNames.toList), name) match
      case _: IcReserved =>
        Translated.Skip(s"identifier '$name' is a $languageName-reserved name", span)
      case _: IcBound    => Translated.Emit(name)
      case _: IcBareBody => Translated.Emit(responseData)
      case _: IcOutput   => Translated.Emit(containerAccess(responseData, name))
      case _: IcInput    => Translated.Emit(name)
      case _: IcStateField =>
        Translated.Emit(containerAccess(stateObject(ctx.capture), name))
      case _: IcUnbackedState =>
        Translated.Skip(
          s"state field '$name' is not backed by an entity table; the test-admin " +
            "/state endpoint projects it as null, so it cannot be asserted black-box",
          span
        )
      case _: IcEnumType  => Translated.Skip(s"enum-type identifier '$name'", span)
      case _: IcEnumValue => Translated.Emit(stringLiteral(name))
      case _: IcUnbound   => Translated.Skip(s"unbound identifier '$name'", span)

  private def callExpr(
      callee: expr,
      args: List[expr],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    callee match
      case IdentifierF(name, _) => identifierCall(name, args, ctx, span)
      case _ =>
        val parts = args.map(translate(_, ctx))
        ExprLift.lift1(translate(callee, ctx)): cp =>
          ExprLift.liftAll(parts, span)(ps => Translated.Emit(calleeCall(cp, ps)))

  private def identifierCall(
      fname: String,
      args: List[expr],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    recognizedCall(fname, args, ctx, span) match
      case Translated.Emit(text) => Translated.Emit(text)
      case Translated.Skip(_, _) =>
        userDefinedCall(fname, args, ctx, span) match
          case Translated.Emit(text)      => Translated.Emit(text)
          case Translated.Skip(reason, _) => Translated.Skip(reason, span)

  private def recognizedCall(
      fname: String,
      args: List[expr],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    ExprLift.dispatchBuiltin(fname, args.map(translate(_, ctx)), span, builtinEmit)

  private def userDefinedCall(
      fname: String,
      args: List[expr],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    classifyUserCall(ctx.fnArities, ctx.predArities, fname, BigInt(args.size)) match
      case _: UcUnknown =>
        Translated.Skip(s"unknown function '$fname/${args.size}' (see #138)", span)
      case w: UcWrongArity =>
        Translated.Skip(
          s"wrong arity for user-defined call '$fname': expected ${w.a}, got ${args.size}",
          span
        )
      case _: UcOk =>
        val rendered = userCallName(fname)
        if reservedNames.contains(rendered) then
          Translated.Skip(s"user-defined call '$fname' is a $languageName-reserved name", span)
        else
          val parts = args.map(translate(_, ctx))
          ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"$rendered(${ps.mkString(", ")})"))

  private def quantifier(
      kind: quant_kind,
      bindings: List[quantifier_binding],
      body: expr,
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if !quantifierAllIn(bindings) then Translated.Skip("quantifier with non-`in` binding", span)
    else
      val boundNames = bindings.map(qbdVar)
      if boundNames.exists(reservedNames.contains) then
        Translated.Skip(s"quantifier with $languageName-reserved binding name", span)
      else
        val domains  = bindings.map(b => translate(qbdCollection(b), ctx))
        val innerCtx = ctx.withBound(boundNames)
        val bodyT    = translate(body, innerCtx)
        ExprLift.liftAll(domains :+ bodyT, span): texts =>
          Translated.Emit(quantifierExpr(kind, boundNames.zip(texts.init), texts.last))
