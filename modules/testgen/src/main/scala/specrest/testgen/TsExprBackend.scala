package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

private[testgen] val TsReservedNames: Set[String] = Set(
  "break",
  "case",
  "catch",
  "class",
  "const",
  "continue",
  "debugger",
  "default",
  "delete",
  "do",
  "else",
  "enum",
  "export",
  "extends",
  "false",
  "finally",
  "for",
  "function",
  "if",
  "import",
  "in",
  "instanceof",
  "new",
  "null",
  "return",
  "super",
  "switch",
  "this",
  "throw",
  "true",
  "try",
  "typeof",
  "var",
  "void",
  "while",
  "with",
  "yield",
  "let",
  "static",
  "await",
  "async",
  "arguments",
  "eval"
)

// Translates the IR expression language to TypeScript, paralleling ExprToPython.
// Set/relation semantics that Python gets from built-ins (`in`, `==` on sets,
// `len`, `|`/`&`/`-`) are delegated to runtime helpers the vitest harness module
// provides: _len, _in, _eq, _union, _inter, _diff, _subset, _powerset. State is
// read from the parsed /__test_admin__/state JSON objects `preState`/`postState`
// and the response body object `responseData`.
@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
object TsExprBackend extends ExprBackend:

  def stringLiteral(s: String): String = TsLit.str(s)

  def translate(expr: expr_full, ctx: TestCtx): Translated = expr match
    case BoolLitF(v, _)   => Translated.Emit(if v then "true" else "false")
    case IntLitF(n, _)    => Translated.Emit(n.toString)
    case FloatLitF(d, _)  => Translated.Emit(d.toString)
    case StringLitF(s, _) => Translated.Emit(TsLit.str(s))
    case NoneLitF(_)      => Translated.Emit("null")

    case IdentifierF(name, span) => resolveIdent(name, ctx, span)

    case PrimeF(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case PreF(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case BinaryOpF(BAdd(), l, r, _)
        if l.isInstanceOf[MapLiteralF] || r.isInstanceOf[MapLiteralF] =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))((lp, rp) =>
        Translated.Emit(s"{ ...($lp), ...($rp) }")
      )

    case BinaryOpF(op, l, r, _) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case UnaryOpF(op, x, _) =>
      ExprLift.lift1(translate(x, ctx))(unOpText(op, _))

    case FieldAccessF(base, field, _) =>
      ExprLift.lift1(translate(base, ctx))(b => Translated.Emit(s"$b[${TsLit.str(field)}]"))

    case EnumAccessF(_, member, _) => Translated.Emit(TsLit.str(member))

    case IndexF(base, idx, _) =>
      ExprLift.lift2(translate(base, ctx), translate(idx, ctx))((b, i) =>
        Translated.Emit(s"$b[$i]")
      )

    case CallF(callee, args, span) => callExpr(callee, args, ctx, span)

    case IfF(c, t, e, _) =>
      ExprLift.lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        Translated.Emit(s"(($cp) ? ($tp) : ($ep))")
      )

    case LetF(v, value, body, span) =>
      if TsReservedNames.contains(v) then
        Translated.Skip(s"Let with TypeScript-reserved binding name '$v'", span)
      else
        ExprLift.lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          Translated.Emit(s"(($v) => ($bp))($vp)")
        )

    case SetLiteralF(elements, span) =>
      if elements.isEmpty then Translated.Emit("new Set()")
      else
        val parts = elements.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"new Set([${ps.mkString(", ")}])"))

    case QuantifierF(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case MapLiteralF(entries, span) => mapLiteral(entries, ctx, span)

    case ConstructorF(_, fields, span) => constructorLiteral(fields, ctx, span)

    case WithF(base, updates, span) => withUpdate(base, updates, ctx, span)

    case SetComprehensionF(v, dom, pred, span) =>
      setComprehension(v, dom, pred, ctx, span)

    case SeqLiteralF(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"[${ps.mkString(", ")}]"))

    case MatchesF(e, pattern, _) =>
      ExprLift.lift1(translate(e, ctx))(t =>
        Translated.Emit(s"(new RegExp(${TsLit.str(s"^(?:$pattern)$$")}).test($t))")
      )

    case TheF(v, dom, body, span) =>
      if TsReservedNames.contains(v) then
        Translated.Skip(s"The with TypeScript-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        ExprLift.lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          Translated.Emit(s"(Array.from($dp).find(($v) => ($bp)) ?? null)")
        )

    case LambdaF(param, body, span) =>
      if TsReservedNames.contains(param) then
        Translated.Skip(s"Lambda with TypeScript-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        ExprLift.lift1(translate(body, innerCtx))(b => Translated.Emit(s"(($param) => ($b))"))

    case SomeWrapF(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[span_t]): Translated =
    if TsReservedNames.contains(name) &&
      (ctx.boundVars.contains(name) || ctx.inputs.contains(name))
    then Translated.Skip(s"identifier '$name' is a TypeScript-reserved name", span)
    else if ctx.boundVars.contains(name) then Translated.Emit(name)
    else if ctx.bareBodyOutput.contains(name) then Translated.Emit("responseData")
    else if ctx.outputs.contains(name) then Translated.Emit(s"responseData[${TsLit.str(name)}]")
    else if ctx.inputs.contains(name) then Translated.Emit(name)
    else if ctx.stateFields.contains(name) then
      if ctx.unbackedStateFields.contains(name) then
        Translated.Skip(
          s"state field '$name' is not backed by an entity table; the test-admin " +
            "/state endpoint projects it as null, so it cannot be asserted black-box",
          span
        )
      else
        val obj = ctx.capture match
          case CaptureMode.PostState => "postState"
          case CaptureMode.PreState  => "preState"
        Translated.Emit(s"$obj[${TsLit.str(name)}]")
    else if ctx.enumValues.contains(name) then
      Translated.Skip(s"enum-type identifier '$name'", span)
    else
      ctx.enumValues.find { case (_, vs) => vs.contains(name) } match
        case Some(_) => Translated.Emit(TsLit.str(name))
        case None    => Translated.Skip(s"unbound identifier '$name'", span)

  private def binOpText(op: bin_op_full, l: String, r: String): Translated =
    op match
      case BAnd()       => Translated.Emit(s"(($l) && ($r))")
      case BOr()        => Translated.Emit(s"(($l) || ($r))")
      case BImplies()   => Translated.Emit(s"((!($l)) || ($r))")
      case BIff()       => Translated.Emit(s"_eq(($l), ($r))")
      case BEq()        => Translated.Emit(s"_eq(($l), ($r))")
      case BNeq()       => Translated.Emit(s"(!_eq(($l), ($r)))")
      case BLt()        => Translated.Emit(s"(($l) < ($r))")
      case BGt()        => Translated.Emit(s"(($l) > ($r))")
      case BLe()        => Translated.Emit(s"(($l) <= ($r))")
      case BGe()        => Translated.Emit(s"(($l) >= ($r))")
      case BIn()        => Translated.Emit(s"_in(($l), ($r))")
      case BNotIn()     => Translated.Emit(s"(!_in(($l), ($r)))")
      case BAdd()       => Translated.Emit(s"(($l) + ($r))")
      case BSub()       => Translated.Emit(s"(($l) - ($r))")
      case BMul()       => Translated.Emit(s"(($l) * ($r))")
      case BDiv()       => Translated.Emit(s"(($l) / ($r))")
      case BUnion()     => Translated.Emit(s"_union(($l), ($r))")
      case BIntersect() => Translated.Emit(s"_inter(($l), ($r))")
      case BDiff()      => Translated.Emit(s"_diff(($l), ($r))")
      case BSubset()    => Translated.Emit(s"_subset(($l), ($r))")

  private def unOpText(op: un_op_full, x: String): Translated = op match
    case UNot()         => Translated.Emit(s"(!($x))")
    case UNegate()      => Translated.Emit(s"(-($x))")
    case UCardinality() => Translated.Emit(s"_len($x)")
    case UPower()       => Translated.Emit(s"_powerset($x)")

  private def callExpr(
      callee: expr_full,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    callee match
      case IdentifierF(name, _) => identifierCall(name, args, ctx, span)
      case _ =>
        val parts = args.map(translate(_, ctx))
        ExprLift.lift1(translate(callee, ctx)): cp =>
          ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"($cp)(${ps.mkString(", ")})"))

  private def identifierCall(
      fname: String,
      args: List[expr_full],
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
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    ExprLift.dispatchBuiltin(fname, args.map(translate(_, ctx)), span, _.ts)

  private def userDefinedCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    val expectedArity = ctx.userFunctions
      .get(fname)
      .map(_.b.size)
      .orElse(ctx.userPredicates.get(fname).map(_.b.size))
    expectedArity match
      case None =>
        Translated.Skip(s"unknown function '$fname/${args.size}' (see #138)", span)
      case Some(n) if n != args.size =>
        Translated.Skip(
          s"wrong arity for user-defined call '$fname': expected $n, got ${args.size}",
          span
        )
      case Some(_) if TsReservedNames.contains(fname) =>
        Translated.Skip(s"user-defined call '$fname' is a TypeScript-reserved name", span)
      case Some(_) =>
        val parts = args.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"$fname(${ps.mkString(", ")})"))

  private def mapLiteral(
      entries: List[map_entry_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if entries.isEmpty then Translated.Emit("{}")
    else
      val pairs = entries.collect { case MapEntryFull(k, v, _) =>
        ExprLift.lift2(translate(k, ctx), translate(v, ctx))((kx, vx) =>
          Translated.Emit(s"[$kx, $vx]")
        )
      }
      ExprLift.liftAll(pairs, span)(ps =>
        Translated.Emit(s"Object.fromEntries([${ps.mkString(", ")}])")
      )

  private def constructorLiteral(
      fields: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if fields.isEmpty then Translated.Emit("{}")
    else
      val pairs = fields.collect { case FieldAssignFull(n, v, _) =>
        ExprLift.lift1(translate(v, ctx))(vx => Translated.Emit(s"${TsLit.str(n)}: $vx"))
      }
      ExprLift.liftAll(pairs, span)(ps => Translated.Emit(s"{ ${ps.mkString(", ")} }"))

  private def withUpdate(
      base: expr_full,
      updates: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    val basePy = translate(base, ctx)
    val pairs = updates.collect { case FieldAssignFull(n, v, _) =>
      ExprLift.lift1(translate(v, ctx))(vx => Translated.Emit(s"${TsLit.str(n)}: $vx"))
    }
    ExprLift.lift1(basePy): bp =>
      ExprLift.liftAll(pairs, span)(ps => Translated.Emit(s"{ ...($bp), ${ps.mkString(", ")} }"))

  private def setComprehension(
      v: String,
      dom: expr_full,
      pred: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if TsReservedNames.contains(v) then
      Translated.Skip(s"SetComprehension with TypeScript-reserved binding name '$v'", span)
    else
      val innerCtx    = ctx.withBound(List(v))
      val domPy       = translate(dom, ctx)
      val predPy      = translate(pred, innerCtx)
      val isMapDomain = peelRelationRefFull(dom).exists(ctx.mapStateFields.contains)
      ExprLift.lift2(domPy, predPy): (d, p) =>
        val iter = if isMapDomain then s"Object.values($d)" else s"$d"
        Translated.Emit(s"new Set(Array.from($iter).filter(($v) => ($p)))")

  private def quantifier(
      kind: quant_kind_full,
      bindings: List[quantifier_binding_full],
      body: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    val isAllIn = bindings.forall:
      case QuantifierBindingFull(_, _, BkIn(), _) => true
      case _                                      => false
    if !isAllIn then Translated.Skip("quantifier with non-`in` binding", span)
    else
      val boundNames = bindings.collect { case QuantifierBindingFull(n, _, _, _) => n }
      val domains    = bindings.collect { case QuantifierBindingFull(_, d, _, _) => translate(d, ctx) }
      val innerCtx   = ctx.withBound(boundNames)
      val bodyPy     = translate(body, innerCtx)
      ExprLift.liftAll(domains :+ bodyPy, span): texts =>
        val ds = texts.init
        val bp = texts.last
        val method = kind match
          case QAll() => "every"
          case _      => "some"
        val nested = boundNames.zip(ds).foldRight(bp): (pair, acc) =>
          val (v, d) = pair
          s"Array.from($d).$method(($v) => ($acc))"
        kind match
          case QAll()              => Translated.Emit(nested)
          case QSome() | QExists() => Translated.Emit(nested)
          case QNo()               => Translated.Emit(s"(!($nested))")
