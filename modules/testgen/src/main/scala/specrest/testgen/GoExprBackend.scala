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
object GoExprBackend extends ExprBackend:

  def stringLiteral(s: String): String = GoLit.str(s)

  def translate(expr: expr_full, ctx: TestCtx): Translated = expr match
    case BoolLitF(v, _)   => Translated.Emit(if v then "true" else "false")
    case IntLitF(n, _)    => Translated.Emit(s"int64($n)")
    case FloatLitF(d, _)  => Translated.Emit(d.toString)
    case StringLitF(s, _) => Translated.Emit(GoLit.str(s))
    case NoneLitF(_)      => Translated.Emit("nil")

    case IdentifierF(name, span) => resolveIdent(name, ctx, span)

    case PrimeF(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case PreF(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case BinaryOpF(BAdd(), l, r, _)
        if isMapLiteralExpr(l) || isMapLiteralExpr(r) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))((lp, rp) =>
        Translated.Emit(s"_merge($lp, $rp)")
      )

    case BinaryOpF(op, l, r, _) =>
      ExprLift.lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case UnaryOpF(op, x, _) =>
      ExprLift.lift1(translate(x, ctx))(unOpText(op, _))

    case FieldAccessF(base, field, _) =>
      ExprLift.lift1(translate(base, ctx))(b => Translated.Emit(s"_field($b, ${GoLit.str(field)})"))

    case EnumAccessF(_, member, _) => Translated.Emit(GoLit.str(member))

    case IndexF(base, idx, _) =>
      ExprLift.lift2(translate(base, ctx), translate(idx, ctx))((b, i) =>
        Translated.Emit(s"_index($b, $i)")
      )

    case CallF(callee, args, span) => callExpr(callee, args, ctx, span)

    case IfF(c, t, e, _) =>
      ExprLift.lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        Translated.Emit(s"func() any { if _truthy($cp) { return $tp }; return $ep }()")
      )

    case LetF(v, value, body, span) =>
      if GoReservedNames.contains(v) then
        Translated.Skip(s"Let with Go-reserved binding name '$v'", span)
      else
        ExprLift.lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          Translated.Emit(s"func($v any) any { return $bp }($vp)")
        )

    case SetLiteralF(elements, span) =>
      if elements.isEmpty then Translated.Emit("_set()")
      else
        val parts = elements.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"_set(${ps.mkString(", ")})"))

    case QuantifierF(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case MapLiteralF(entries, span) => mapLiteral(entries, ctx, span)

    case ConstructorF(_, fields, span) => constructorLiteral(fields, ctx, span)

    case WithF(base, updates, span) => withUpdate(base, updates, ctx, span)

    case SetComprehensionF(v, dom, pred, span) =>
      setComprehension(v, dom, pred, ctx, span)

    case SeqLiteralF(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"[]any{${ps.mkString(", ")}}"))

    case MatchesF(e, pattern, _) =>
      ExprLift.lift1(translate(e, ctx))(t =>
        Translated.Emit(s"_matches($t, ${GoLit.str(s"^(?:$pattern)$$")})")
      )

    case TheF(v, dom, body, span) =>
      if GoReservedNames.contains(v) then
        Translated.Skip(s"The with Go-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        ExprLift.lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          Translated.Emit(s"_find($dp, func($v any) bool { return _truthy($bp) })")
        )

    case LambdaF(param, body, span) =>
      if GoReservedNames.contains(param) then
        Translated.Skip(s"Lambda with Go-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        ExprLift.lift1(translate(body, innerCtx))(b =>
          Translated.Emit(s"func($param any) any { return $b }")
        )

    case SomeWrapF(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[span_t]): Translated =
    classifyIdent(ctx.identCtx(GoReservedNames.toList), name) match
      case _: IcReserved => Translated.Skip(s"identifier '$name' is a Go-reserved name", span)
      case _: IcBound    => Translated.Emit(name)
      case _: IcBareBody => Translated.Emit("responseData")
      case _: IcOutput   => Translated.Emit(s"_field(responseData, ${GoLit.str(name)})")
      case _: IcInput    => Translated.Emit(name)
      case _: IcStateField =>
        val obj = ctx.capture match
          case CaptureMode.PostState => "postState"
          case CaptureMode.PreState  => "preState"
        Translated.Emit(s"_field($obj, ${GoLit.str(name)})")
      case _: IcUnbackedState =>
        Translated.Skip(
          s"state field '$name' is not backed by an entity table; the test-admin " +
            "/state endpoint projects it as null, so it cannot be asserted black-box",
          span
        )
      case _: IcEnumType  => Translated.Skip(s"enum-type identifier '$name'", span)
      case _: IcEnumValue => Translated.Emit(GoLit.str(name))
      case _: IcUnbound   => Translated.Skip(s"unbound identifier '$name'", span)

  private def binOpText(op: bin_op_full, l: String, r: String): Translated =
    op match
      case BAnd()       => Translated.Emit(s"(_truthy($l) && _truthy($r))")
      case BOr()        => Translated.Emit(s"(_truthy($l) || _truthy($r))")
      case BImplies()   => Translated.Emit(s"(!_truthy($l) || _truthy($r))")
      case BIff()       => Translated.Emit(s"_eq($l, $r)")
      case BEq()        => Translated.Emit(s"_eq($l, $r)")
      case BNeq()       => Translated.Emit(s"(!_eq($l, $r))")
      case BLt()        => Translated.Emit(s"_lt($l, $r)")
      case BGt()        => Translated.Emit(s"_gt($l, $r)")
      case BLe()        => Translated.Emit(s"_le($l, $r)")
      case BGe()        => Translated.Emit(s"_ge($l, $r)")
      case BIn()        => Translated.Emit(s"_in($l, $r)")
      case BNotIn()     => Translated.Emit(s"(!_in($l, $r))")
      case BAdd()       => Translated.Emit(s"_add($l, $r)")
      case BSub()       => Translated.Emit(s"_sub($l, $r)")
      case BMul()       => Translated.Emit(s"_mul($l, $r)")
      case BDiv()       => Translated.Emit(s"_div($l, $r)")
      case BUnion()     => Translated.Emit(s"_union($l, $r)")
      case BIntersect() => Translated.Emit(s"_inter($l, $r)")
      case BDiff()      => Translated.Emit(s"_diff($l, $r)")
      case BSubset()    => Translated.Emit(s"_subset($l, $r)")

  private def unOpText(op: un_op_full, x: String): Translated = op match
    case UNot()         => Translated.Emit(s"(!_truthy($x))")
    case UNegate()      => Translated.Emit(s"_neg($x)")
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
          ExprLift.liftAll(parts, span)(ps =>
            Translated.Emit(s"_call($cp${if ps.isEmpty then "" else ", " + ps.mkString(", ")})")
          )

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
    ExprLift.dispatchBuiltin(fname, args.map(translate(_, ctx)), span, _.go)

  private def userDefinedCall(
      fname: String,
      args: List[expr_full],
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
      case _: UcOk if GoReservedNames.contains(fname) =>
        Translated.Skip(s"user-defined call '$fname' is a Go-reserved name", span)
      case _: UcOk =>
        val parts = args.map(translate(_, ctx))
        ExprLift.liftAll(parts, span)(ps => Translated.Emit(s"$fname(${ps.mkString(", ")})"))

  private def mapLiteral(
      entries: List[map_entry_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if entries.isEmpty then Translated.Emit("map[string]any{}")
    else
      val pairs = entries.collect { case MapEntryFull(k, v, _) =>
        ExprLift.lift2(translate(k, ctx), translate(v, ctx))((kx, vx) =>
          Translated.Emit(s"$kx, $vx")
        )
      }
      ExprLift.liftAll(pairs, span)(ps => Translated.Emit(s"_mapOf(${ps.mkString(", ")})"))

  private def constructorLiteral(
      fields: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if fields.isEmpty then Translated.Emit("map[string]any{}")
    else
      val pairs = fields.collect { case FieldAssignFull(n, v, _) =>
        ExprLift.lift1(translate(v, ctx))(vx => Translated.Emit(s"${GoLit.str(n)}, $vx"))
      }
      ExprLift.liftAll(pairs, span)(ps => Translated.Emit(s"_mapOf(${ps.mkString(", ")})"))

  private def withUpdate(
      base: expr_full,
      updates: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    val basePy = translate(base, ctx)
    val pairs = updates.collect { case FieldAssignFull(n, v, _) =>
      ExprLift.lift1(translate(v, ctx))(vx => Translated.Emit(s"${GoLit.str(n)}, $vx"))
    }
    ExprLift.lift1(basePy): bp =>
      ExprLift.liftAll(pairs, span)(ps => Translated.Emit(s"_with($bp, ${ps.mkString(", ")})"))

  private def setComprehension(
      v: String,
      dom: expr_full,
      pred: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if GoReservedNames.contains(v) then
      Translated.Skip(s"SetComprehension with Go-reserved binding name '$v'", span)
    else
      val innerCtx = ctx.withBound(List(v))
      val domPy    = translate(dom, ctx)
      val predPy   = translate(pred, innerCtx)
      ExprLift.lift2(domPy, predPy): (d, p) =>
        Translated.Emit(s"_setFilter($d, func($v any) bool { return _truthy($p) })")

  private def quantifier(
      kind: quant_kind_full,
      bindings: List[quantifier_binding_full],
      body: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if !quantifierAllIn(bindings) then Translated.Skip("quantifier with non-`in` binding", span)
    else
      val boundNames = bindings.collect { case QuantifierBindingFull(n, _, _, _) => n }
      if boundNames.exists(GoReservedNames.contains) then
        Translated.Skip("quantifier with Go-reserved binding name", span)
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
            case QAll()              => Translated.Emit(nested)
            case QSome() | QExists() => Translated.Emit(nested)
            case QNo()               => Translated.Emit(s"(!($nested))")
