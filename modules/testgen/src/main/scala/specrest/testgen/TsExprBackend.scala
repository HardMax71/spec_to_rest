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

  def translate(expr: expr_full, ctx: TestCtx): ExprPy = expr match
    case BoolLitF(v, _)   => ExprPy.Py(if v then "true" else "false")
    case IntLitF(n, _)    => ExprPy.Py(integer_of_int(n).toString)
    case FloatLitF(d, _)  => ExprPy.Py(d.toString)
    case StringLitF(s, _) => ExprPy.Py(TsLit.str(s))
    case NoneLitF(_)      => ExprPy.Py("null")

    case IdentifierF(name, span) => resolveIdent(name, ctx, span)

    case PrimeF(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case PreF(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case BinaryOpF(BAdd(), l, r, _)
        if l.isInstanceOf[MapLiteralF] || r.isInstanceOf[MapLiteralF] =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))((lp, rp) =>
        ExprPy.Py(s"{ ...($lp), ...($rp) }")
      )

    case BinaryOpF(op, l, r, _) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case UnaryOpF(op, x, _) =>
      ExprLift.lift1(translate(x, ctx))(unOpText(op, _))

    case FieldAccessF(base, field, _) =>
      ExprLift.lift1(translate(base, ctx))(b => ExprPy.Py(s"$b[${TsLit.str(field)}]"))

    case EnumAccessF(_, member, _) => ExprPy.Py(TsLit.str(member))

    case IndexF(base, idx, _) =>
      ExprLift.lift2(translate(base, ctx), translate(idx, ctx))((b, i) => ExprPy.Py(s"$b[$i]"))

    case CallF(callee, args, span) => callExpr(callee, args, ctx, span)

    case IfF(c, t, e, _) =>
      ExprLift.lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        ExprPy.Py(s"(($cp) ? ($tp) : ($ep))")
      )

    case LetF(v, value, body, span) =>
      if TsReservedNames.contains(v) then
        ExprPy.Skip(s"Let with TypeScript-reserved binding name '$v'", span)
      else
        ExprLift.lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          ExprPy.Py(s"(($v) => ($bp))($vp)")
        )

    case SetLiteralF(elements, span) =>
      if elements.isEmpty then ExprPy.Py("new Set()")
      else
        val parts = elements.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => ExprPy.Py(s"new Set([${ps.mkString(", ")}])"))

    case QuantifierF(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case MapLiteralF(entries, span) => mapLiteral(entries, ctx, span)

    case ConstructorF(_, fields, span) => constructorLiteral(fields, ctx, span)

    case WithF(base, updates, span) => withUpdate(base, updates, ctx, span)

    case SetComprehensionF(v, dom, pred, span) =>
      setComprehension(v, dom, pred, ctx, span)

    case SeqLiteralF(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      ExprLift.liftAll(parts, span)(ps => ExprPy.Py(s"[${ps.mkString(", ")}]"))

    case MatchesF(e, pattern, _) =>
      ExprLift.lift1(translate(e, ctx))(t =>
        ExprPy.Py(s"(new RegExp(${TsLit.str(s"^(?:$pattern)$$")}).test($t))")
      )

    case TheF(v, dom, body, span) =>
      if TsReservedNames.contains(v) then
        ExprPy.Skip(s"The with TypeScript-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        ExprLift.lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          ExprPy.Py(s"(Array.from($dp).find(($v) => ($bp)) ?? null)")
        )

    case LambdaF(param, body, span) =>
      if TsReservedNames.contains(param) then
        ExprPy.Skip(s"Lambda with TypeScript-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        ExprLift.lift1(translate(body, innerCtx))(b => ExprPy.Py(s"(($param) => ($b))"))

    case SomeWrapF(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[span_t]): ExprPy =
    if TsReservedNames.contains(name) &&
      (ctx.boundVars.contains(name) || ctx.inputs.contains(name))
    then ExprPy.Skip(s"identifier '$name' is a TypeScript-reserved name", span)
    else if ctx.boundVars.contains(name) then ExprPy.Py(name)
    else if ctx.bareBodyOutput.contains(name) then ExprPy.Py("responseData")
    else if ctx.outputs.contains(name) then ExprPy.Py(s"responseData[${TsLit.str(name)}]")
    else if ctx.inputs.contains(name) then ExprPy.Py(name)
    else if ctx.stateFields.contains(name) then
      if ctx.unbackedStateFields.contains(name) then
        ExprPy.Skip(
          s"state field '$name' is not backed by an entity table; the test-admin " +
            "/state endpoint projects it as null, so it cannot be asserted black-box",
          span
        )
      else
        val obj = ctx.capture match
          case CaptureMode.PostState => "postState"
          case CaptureMode.PreState  => "preState"
        ExprPy.Py(s"$obj[${TsLit.str(name)}]")
    else if ctx.enumValues.contains(name) then ExprPy.Skip(s"enum-type identifier '$name'", span)
    else
      ctx.enumValues.find { case (_, vs) => vs.contains(name) } match
        case Some(_) => ExprPy.Py(TsLit.str(name))
        case None    => ExprPy.Skip(s"unbound identifier '$name'", span)

  private def binOpText(op: bin_op_full, l: String, r: String): ExprPy =
    op match
      case BAnd()       => ExprPy.Py(s"(($l) && ($r))")
      case BOr()        => ExprPy.Py(s"(($l) || ($r))")
      case BImplies()   => ExprPy.Py(s"((!($l)) || ($r))")
      case BIff()       => ExprPy.Py(s"_eq(($l), ($r))")
      case BEq()        => ExprPy.Py(s"_eq(($l), ($r))")
      case BNeq()       => ExprPy.Py(s"(!_eq(($l), ($r)))")
      case BLt()        => ExprPy.Py(s"(($l) < ($r))")
      case BGt()        => ExprPy.Py(s"(($l) > ($r))")
      case BLe()        => ExprPy.Py(s"(($l) <= ($r))")
      case BGe()        => ExprPy.Py(s"(($l) >= ($r))")
      case BIn()        => ExprPy.Py(s"_in(($l), ($r))")
      case BNotIn()     => ExprPy.Py(s"(!_in(($l), ($r)))")
      case BAdd()       => ExprPy.Py(s"(($l) + ($r))")
      case BSub()       => ExprPy.Py(s"(($l) - ($r))")
      case BMul()       => ExprPy.Py(s"(($l) * ($r))")
      case BDiv()       => ExprPy.Py(s"(($l) / ($r))")
      case BUnion()     => ExprPy.Py(s"_union(($l), ($r))")
      case BIntersect() => ExprPy.Py(s"_inter(($l), ($r))")
      case BDiff()      => ExprPy.Py(s"_diff(($l), ($r))")
      case BSubset()    => ExprPy.Py(s"_subset(($l), ($r))")

  private def unOpText(op: un_op_full, x: String): ExprPy = op match
    case UNot()         => ExprPy.Py(s"(!($x))")
    case UNegate()      => ExprPy.Py(s"(-($x))")
    case UCardinality() => ExprPy.Py(s"_len($x)")
    case UPower()       => ExprPy.Py(s"_powerset($x)")

  private def callExpr(
      callee: expr_full,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    callee match
      case IdentifierF(name, _) => identifierCall(name, args, ctx, span)
      case _ =>
        val parts = args.map(translate(_, ctx))
        ExprLift.lift1(translate(callee, ctx)): cp =>
          ExprLift.liftAll(parts, span)(ps => ExprPy.Py(s"($cp)(${ps.mkString(", ")})"))

  private def identifierCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    recognizedCall(fname, args, ctx, span) match
      case ExprPy.Py(text) => ExprPy.Py(text)
      case ExprPy.Skip(_, _) =>
        userDefinedCall(fname, args, ctx, span) match
          case ExprPy.Py(text)        => ExprPy.Py(text)
          case ExprPy.Skip(reason, _) => ExprPy.Skip(reason, span)

  private def recognizedCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    fname match
      case "len" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_len($a)"))
      case "dom" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"new Set(Object.keys($a))"))
      case "ran" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"new Set(Object.values($a))"))
      case "max" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"Math.max(...Array.from($a))"))
      case "min" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"Math.min(...Array.from($a))"))
      case "now" if args.isEmpty =>
        ExprPy.Py("new Date().toISOString()")
      case "hash" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_sha256Hex($a)"))
      case "days" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"(($a) * 86400)"))
      case "hours" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"(($a) * 3600)"))
      case "minutes" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"(($a) * 60)"))
      case "seconds" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"($a)"))
      case "sum" if args.size == 2 =>
        sumCall(args(0), args(1), ctx)
      case other =>
        ExprPy.Skip(s"unknown function '$other/${args.size}' (see #138)", span)

  private def sumCall(coll: expr_full, fn: expr_full, ctx: TestCtx): ExprPy =
    fn match
      case LambdaF(param, body, _) if !TsReservedNames.contains(param) =>
        val innerCtx = ctx.withBound(List(param))
        ExprLift.lift2(translate(coll, ctx), translate(body, innerCtx))((c, b) =>
          ExprPy.Py(s"Array.from($c).reduce((_acc, $param) => _acc + ($b), 0)")
        )
      case _ =>
        ExprLift.lift2(translate(coll, ctx), translate(fn, ctx))((c, f) =>
          ExprPy.Py(s"Array.from($c).reduce((_acc, _x) => _acc + ($f)(_x), 0)")
        )

  private def userDefinedCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    val expectedArity = ctx.userFunctions
      .get(fname)
      .map(_.b.size)
      .orElse(ctx.userPredicates.get(fname).map(_.b.size))
    expectedArity match
      case None =>
        ExprPy.Skip(s"unknown function '$fname/${args.size}' (see #138)", span)
      case Some(n) if n != args.size =>
        ExprPy.Skip(
          s"wrong arity for user-defined call '$fname': expected $n, got ${args.size}",
          span
        )
      case Some(_) if TsReservedNames.contains(fname) =>
        ExprPy.Skip(s"user-defined call '$fname' is a TypeScript-reserved name", span)
      case Some(_) =>
        val parts = args.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => ExprPy.Py(s"$fname(${ps.mkString(", ")})"))

  private def mapLiteral(
      entries: List[map_entry_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    if entries.isEmpty then ExprPy.Py("{}")
    else
      val pairs = entries.collect { case MapEntryFull(k, v, _) =>
        ExprLift.lift2(translate(k, ctx), translate(v, ctx))((kx, vx) =>
          ExprPy.Py(s"[$kx, $vx]")
        )
      }
      ExprLift.liftAll(pairs, span)(ps => ExprPy.Py(s"Object.fromEntries([${ps.mkString(", ")}])"))

  private def constructorLiteral(
      fields: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    if fields.isEmpty then ExprPy.Py("{}")
    else
      val pairs = fields.collect { case FieldAssignFull(n, v, _) =>
        ExprLift.lift1(translate(v, ctx))(vx => ExprPy.Py(s"${TsLit.str(n)}: $vx"))
      }
      ExprLift.liftAll(pairs, span)(ps => ExprPy.Py(s"{ ${ps.mkString(", ")} }"))

  private def withUpdate(
      base: expr_full,
      updates: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    val basePy = translate(base, ctx)
    val pairs = updates.collect { case FieldAssignFull(n, v, _) =>
      ExprLift.lift1(translate(v, ctx))(vx => ExprPy.Py(s"${TsLit.str(n)}: $vx"))
    }
    ExprLift.lift1(basePy): bp =>
      ExprLift.liftAll(pairs, span)(ps => ExprPy.Py(s"{ ...($bp), ${ps.mkString(", ")} }"))

  private def setComprehension(
      v: String,
      dom: expr_full,
      pred: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    if TsReservedNames.contains(v) then
      ExprPy.Skip(s"SetComprehension with TypeScript-reserved binding name '$v'", span)
    else
      val innerCtx    = ctx.withBound(List(v))
      val domPy       = translate(dom, ctx)
      val predPy      = translate(pred, innerCtx)
      val isMapDomain = peelRelationRefFull(dom).exists(ctx.mapStateFields.contains)
      ExprLift.lift2(domPy, predPy): (d, p) =>
        val iter = if isMapDomain then s"Object.values($d)" else s"$d"
        ExprPy.Py(s"new Set(Array.from($iter).filter(($v) => ($p)))")

  private def quantifier(
      kind: quant_kind_full,
      bindings: List[quantifier_binding_full],
      body: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    val isAllIn = bindings.forall:
      case QuantifierBindingFull(_, _, BkIn(), _) => true
      case _                                      => false
    if !isAllIn then ExprPy.Skip("quantifier with non-`in` binding", span)
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
          case QAll()              => ExprPy.Py(nested)
          case QSome() | QExists() => ExprPy.Py(nested)
          case QNo()               => ExprPy.Py(s"(!($nested))")
