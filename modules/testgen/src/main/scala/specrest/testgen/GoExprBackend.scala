package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

private[testgen] val GoReservedNames: Set[String] = Set(
  "break",
  "case",
  "chan",
  "const",
  "continue",
  "default",
  "defer",
  "else",
  "fallthrough",
  "for",
  "func",
  "go",
  "goto",
  "if",
  "import",
  "interface",
  "map",
  "package",
  "range",
  "return",
  "select",
  "struct",
  "switch",
  "type",
  "var",
  "true",
  "false",
  "nil",
  "iota",
  "any",
  "bool",
  "byte",
  "comparable",
  "complex64",
  "complex128",
  "error",
  "float32",
  "float64",
  "int",
  "int8",
  "int16",
  "int32",
  "int64",
  "rune",
  "string",
  "uint",
  "uintptr",
  "uint8",
  "uint16",
  "uint32",
  "uint64",
  "append",
  "cap",
  "clear",
  "close",
  "complex",
  "copy",
  "delete",
  "imag",
  "len",
  "make",
  "max",
  "min",
  "new",
  "panic",
  "print",
  "println",
  "real",
  "recover"
)

object GoLit:
  def str(s: String): String =
    val sb = new StringBuilder
    sb.append('"')
    s.foreach:
      case '"'                  => sb.append("\\\"")
      case '\\'                 => sb.append("\\\\")
      case '\n'                 => sb.append("\\n")
      case '\r'                 => sb.append("\\r")
      case '\t'                 => sb.append("\\t")
      case c if c < 0x20.toChar => sb.append(f"\\u${c.toInt}%04x")
      case c                    => sb.append(c)
    sb.append('"')
    sb.toString

// Translates the IR expression language to Go, paralleling ExprToPython /
// TsExprBackend. Go is statically typed: `any < any`, `any + any` and inline
// collection lambdas do not compile, so every numeric/comparison/logical/set
// operation is delegated to runtime helpers the go-test harness provides
// (_eq/_in/_lt/_add/_union/_len/_all/_filter/...). Function literals *are* Go
// expressions, so quantifiers/comprehensions/`the` compose as
// `_all(d, func(v any) bool { return _truthy(body) })`. State is read from the
// parsed /__test_admin__/state JSON via `_field(postState|preState, "x")` and
// the response body via `_field(responseData, "x")`.
@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
object GoExprBackend extends ExprBackend:

  def stringLiteral(s: String): String = GoLit.str(s)

  def translate(expr: expr_full, ctx: TestCtx): ExprPy = expr match
    case BoolLitF(v, _)   => ExprPy.Py(if v then "true" else "false")
    case IntLitF(n, _)    => ExprPy.Py(s"int64(${integer_of_int(n).toString})")
    case FloatLitF(d, _)  => ExprPy.Py(d.toString)
    case StringLitF(s, _) => ExprPy.Py(GoLit.str(s))
    case NoneLitF(_)      => ExprPy.Py("nil")

    case IdentifierF(name, span) => resolveIdent(name, ctx, span)

    case PrimeF(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case PreF(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case BinaryOpF(BAdd(), l, r, _)
        if l.isInstanceOf[MapLiteralF] || r.isInstanceOf[MapLiteralF] =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))((lp, rp) =>
        ExprPy.Py(s"_merge($lp, $rp)")
      )

    case BinaryOpF(op, l, r, _) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case UnaryOpF(op, x, _) =>
      ExprLift.lift1(translate(x, ctx))(unOpText(op, _))

    case FieldAccessF(base, field, _) =>
      ExprLift.lift1(translate(base, ctx))(b => ExprPy.Py(s"_field($b, ${GoLit.str(field)})"))

    case EnumAccessF(_, member, _) => ExprPy.Py(GoLit.str(member))

    case IndexF(base, idx, _) =>
      ExprLift.lift2(translate(base, ctx), translate(idx, ctx))((b, i) =>
        ExprPy.Py(s"_index($b, $i)")
      )

    case CallF(callee, args, span) => callExpr(callee, args, ctx, span)

    case IfF(c, t, e, _) =>
      ExprLift.lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        ExprPy.Py(s"func() any { if _truthy($cp) { return $tp }; return $ep }()")
      )

    case LetF(v, value, body, span) =>
      if GoReservedNames.contains(v) then
        ExprPy.Skip(s"Let with Go-reserved binding name '$v'", span)
      else
        ExprLift.lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          ExprPy.Py(s"func($v any) any { return $bp }($vp)")
        )

    case SetLiteralF(elements, span) =>
      if elements.isEmpty then ExprPy.Py("_set()")
      else
        val parts = elements.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => ExprPy.Py(s"_set(${ps.mkString(", ")})"))

    case QuantifierF(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case MapLiteralF(entries, span) => mapLiteral(entries, ctx, span)

    case ConstructorF(_, fields, span) => constructorLiteral(fields, ctx, span)

    case WithF(base, updates, span) => withUpdate(base, updates, ctx, span)

    case SetComprehensionF(v, dom, pred, span) =>
      setComprehension(v, dom, pred, ctx, span)

    case SeqLiteralF(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      ExprLift.liftAll(parts, span)(ps => ExprPy.Py(s"[]any{${ps.mkString(", ")}}"))

    case MatchesF(e, pattern, _) =>
      ExprLift.lift1(translate(e, ctx))(t =>
        ExprPy.Py(s"_matches($t, ${GoLit.str(s"^(?:$pattern)$$")})")
      )

    case TheF(v, dom, body, span) =>
      if GoReservedNames.contains(v) then
        ExprPy.Skip(s"The with Go-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        ExprLift.lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          ExprPy.Py(s"_find($dp, func($v any) bool { return _truthy($bp) })")
        )

    case LambdaF(param, body, span) =>
      if GoReservedNames.contains(param) then
        ExprPy.Skip(s"Lambda with Go-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        ExprLift.lift1(translate(body, innerCtx))(b =>
          ExprPy.Py(s"func($param any) any { return $b }")
        )

    case SomeWrapF(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[span_t]): ExprPy =
    if GoReservedNames.contains(name) &&
      (ctx.boundVars.contains(name) || ctx.inputs.contains(name))
    then ExprPy.Skip(s"identifier '$name' is a Go-reserved name", span)
    else if ctx.boundVars.contains(name) then ExprPy.Py(name)
    else if ctx.bareBodyOutput.contains(name) then ExprPy.Py("responseData")
    else if ctx.outputs.contains(name) then ExprPy.Py(s"_field(responseData, ${GoLit.str(name)})")
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
        ExprPy.Py(s"_field($obj, ${GoLit.str(name)})")
    else if ctx.enumValues.contains(name) then ExprPy.Skip(s"enum-type identifier '$name'", span)
    else
      ctx.enumValues.find { case (_, vs) => vs.contains(name) } match
        case Some(_) => ExprPy.Py(GoLit.str(name))
        case None    => ExprPy.Skip(s"unbound identifier '$name'", span)

  private def binOpText(op: bin_op_full, l: String, r: String): ExprPy =
    op match
      case BAnd()       => ExprPy.Py(s"(_truthy($l) && _truthy($r))")
      case BOr()        => ExprPy.Py(s"(_truthy($l) || _truthy($r))")
      case BImplies()   => ExprPy.Py(s"(!_truthy($l) || _truthy($r))")
      case BIff()       => ExprPy.Py(s"_eq($l, $r)")
      case BEq()        => ExprPy.Py(s"_eq($l, $r)")
      case BNeq()       => ExprPy.Py(s"(!_eq($l, $r))")
      case BLt()        => ExprPy.Py(s"_lt($l, $r)")
      case BGt()        => ExprPy.Py(s"_gt($l, $r)")
      case BLe()        => ExprPy.Py(s"_le($l, $r)")
      case BGe()        => ExprPy.Py(s"_ge($l, $r)")
      case BIn()        => ExprPy.Py(s"_in($l, $r)")
      case BNotIn()     => ExprPy.Py(s"(!_in($l, $r))")
      case BAdd()       => ExprPy.Py(s"_add($l, $r)")
      case BSub()       => ExprPy.Py(s"_sub($l, $r)")
      case BMul()       => ExprPy.Py(s"_mul($l, $r)")
      case BDiv()       => ExprPy.Py(s"_div($l, $r)")
      case BUnion()     => ExprPy.Py(s"_union($l, $r)")
      case BIntersect() => ExprPy.Py(s"_inter($l, $r)")
      case BDiff()      => ExprPy.Py(s"_diff($l, $r)")
      case BSubset()    => ExprPy.Py(s"_subset($l, $r)")

  private def unOpText(op: un_op_full, x: String): ExprPy = op match
    case UNot()         => ExprPy.Py(s"(!_truthy($x))")
    case UNegate()      => ExprPy.Py(s"_neg($x)")
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
          ExprLift.liftAll(parts, span)(ps =>
            ExprPy.Py(s"_call($cp${if ps.isEmpty then "" else ", " + ps.mkString(", ")})")
          )

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
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_keys($a)"))
      case "ran" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_values($a)"))
      case "max" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_max($a)"))
      case "min" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_min($a)"))
      case "now" if args.isEmpty =>
        ExprPy.Py("_now()")
      case "hash" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_sha256Hex($a)"))
      case "days" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_mul($a, int64(86400))"))
      case "hours" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_mul($a, int64(3600))"))
      case "minutes" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_mul($a, int64(60))"))
      case "seconds" if args.size == 1 =>
        ExprLift.lift1(translate(args.head, ctx))(a => ExprPy.Py(s"_mul($a, int64(1))"))
      case "sum" if args.size == 2 =>
        sumCall(args(0), args(1), ctx)
      case other =>
        ExprPy.Skip(s"unknown function '$other/${args.size}' (see #138)", span)

  private def sumCall(coll: expr_full, fn: expr_full, ctx: TestCtx): ExprPy =
    fn match
      case LambdaF(param, body, _) if !GoReservedNames.contains(param) =>
        val innerCtx = ctx.withBound(List(param))
        ExprLift.lift2(translate(coll, ctx), translate(body, innerCtx))((c, b) =>
          ExprPy.Py(s"_sum($c, func($param any) any { return $b })")
        )
      case _ =>
        ExprLift.lift2(translate(coll, ctx), translate(fn, ctx))((c, f) =>
          ExprPy.Py(s"_sum($c, func(_x any) any { return _call($f, _x) })")
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
      case Some(_) if GoReservedNames.contains(fname) =>
        ExprPy.Skip(s"user-defined call '$fname' is a Go-reserved name", span)
      case Some(_) =>
        val parts = args.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => ExprPy.Py(s"$fname(${ps.mkString(", ")})"))

  private def mapLiteral(
      entries: List[map_entry_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    if entries.isEmpty then ExprPy.Py("map[string]any{}")
    else
      val pairs = entries.collect { case MapEntryFull(k, v, _) =>
        ExprLift.lift2(translate(k, ctx), translate(v, ctx))((kx, vx) =>
          ExprPy.Py(s"$kx, $vx")
        )
      }
      ExprLift.liftAll(pairs, span)(ps => ExprPy.Py(s"_mapOf(${ps.mkString(", ")})"))

  private def constructorLiteral(
      fields: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    if fields.isEmpty then ExprPy.Py("map[string]any{}")
    else
      val pairs = fields.collect { case FieldAssignFull(n, v, _) =>
        ExprLift.lift1(translate(v, ctx))(vx => ExprPy.Py(s"${GoLit.str(n)}, $vx"))
      }
      ExprLift.liftAll(pairs, span)(ps => ExprPy.Py(s"_mapOf(${ps.mkString(", ")})"))

  private def withUpdate(
      base: expr_full,
      updates: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    val basePy = translate(base, ctx)
    val pairs = updates.collect { case FieldAssignFull(n, v, _) =>
      ExprLift.lift1(translate(v, ctx))(vx => ExprPy.Py(s"${GoLit.str(n)}, $vx"))
    }
    ExprLift.lift1(basePy): bp =>
      ExprLift.liftAll(pairs, span)(ps => ExprPy.Py(s"_with($bp, ${ps.mkString(", ")})"))

  private def setComprehension(
      v: String,
      dom: expr_full,
      pred: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): ExprPy =
    if GoReservedNames.contains(v) then
      ExprPy.Skip(s"SetComprehension with Go-reserved binding name '$v'", span)
    else
      val innerCtx = ctx.withBound(List(v))
      val domPy    = translate(dom, ctx)
      val predPy   = translate(pred, innerCtx)
      ExprLift.lift2(domPy, predPy): (d, p) =>
        ExprPy.Py(s"_setFilter($d, func($v any) bool { return _truthy($p) })")

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
      if boundNames.exists(GoReservedNames.contains) then
        ExprPy.Skip("quantifier with Go-reserved binding name", span)
      else
        val domains = bindings.collect { case QuantifierBindingFull(_, d, _, _) =>
          translate(d, ctx)
        }
        val innerCtx = ctx.withBound(boundNames)
        val bodyPy   = translate(body, innerCtx)
        ExprLift.liftAll(domains :+ bodyPy, span): texts =>
          val ds = texts.init
          val bp = texts.last
          val helper = kind match
            case QAll() => "_all"
            case _      => "_any"
          val nested = boundNames.zip(ds).foldRight(s"_truthy($bp)"): (pair, acc) =>
            val (v, d) = pair
            s"$helper($d, func($v any) bool { return $acc })"
          kind match
            case QAll()              => ExprPy.Py(nested)
            case QSome() | QExists() => ExprPy.Py(nested)
            case QNo()               => ExprPy.Py(s"(!($nested))")
