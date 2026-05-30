package specrest.testgen

import specrest.convention.Naming
import specrest.ir.generated.SpecRestGenerated.*

private[testgen] val PythonReservedNames: Set[String] = Set(
  "False",
  "None",
  "True",
  "and",
  "as",
  "assert",
  "async",
  "await",
  "break",
  "class",
  "continue",
  "def",
  "del",
  "elif",
  "else",
  "except",
  "finally",
  "for",
  "from",
  "global",
  "if",
  "import",
  "in",
  "is",
  "lambda",
  "nonlocal",
  "not",
  "or",
  "pass",
  "raise",
  "return",
  "try",
  "while",
  "with",
  "yield",
  "match",
  "case"
)

object ExprToPython extends ExprBackend:

  def stringLiteral(s: String): String = pyString(s)

  def translate(expr: expr_full, ctx: TestCtx): Translated = expr match
    case BoolLitF(v, _)   => Translated.Emit(if v then "True" else "False")
    case IntLitF(n, _)    => Translated.Emit(n.toString)
    case FloatLitF(d, _)  => Translated.Emit(d.toString)
    case StringLitF(s, _) => Translated.Emit(pyString(s))
    case NoneLitF(_)      => Translated.Emit("None")

    case IdentifierF(name, span) => resolveIdent(name, ctx, span)

    case PrimeF(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case PreF(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case BinaryOpF(BAdd(), l, r, _)
        if isMapLiteralExpr(l) || isMapLiteralExpr(r) =>
      lift2(translate(l, ctx), translate(r, ctx))((lp, rp) =>
        Translated.Emit(s"{**($lp), **($rp)}")
      )

    case BinaryOpF(op, l, r, _) =>
      lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case UnaryOpF(op, x, _) =>
      lift1(translate(x, ctx))(unOpText(op, _))

    case FieldAccessF(base, field, _) =>
      lift1(translate(base, ctx))(b => Translated.Emit(s"$b[${pyString(field)}]"))

    case EnumAccessF(_, member, _) => Translated.Emit(pyString(member))

    case IndexF(base, idx, _) =>
      lift2(translate(base, ctx), translate(idx, ctx))((b, i) => Translated.Emit(s"$b[$i]"))

    case CallF(callee, args, span) => callExpr(callee, args, ctx, span)

    case IfF(c, t, e, _) =>
      lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        Translated.Emit(s"(($tp) if ($cp) else ($ep))")
      )

    case LetF(v, value, body, span) =>
      if PythonReservedNames.contains(v) then
        Translated.Skip(s"Let with Python-reserved binding name '$v'", span)
      else
        lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          Translated.Emit(s"((lambda $v=($vp): ($bp))())")
        )

    case SetLiteralF(elements, span) =>
      if elements.isEmpty then Translated.Emit("set()")
      else
        val parts = elements.map(translate(_, ctx))
        liftAll(parts, span)(ps => Translated.Emit(s"{${ps.mkString(", ")}}"))

    case QuantifierF(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case MapLiteralF(entries, span) => mapLiteral(entries, ctx, span)

    case ConstructorF(_, fields, span) => constructorLiteral(fields, ctx, span)

    case WithF(base, updates, span) => withUpdate(base, updates, ctx, span)

    case SetComprehensionF(v, dom, pred, span) =>
      setComprehension(v, dom, pred, ctx, span)

    case SeqLiteralF(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      liftAll(parts, span)(ps => Translated.Emit(s"[${ps.mkString(", ")}]"))

    case MatchesF(e, pattern, span) =>
      lift1(translate(e, ctx))(t =>
        Translated.Emit(s"(re.fullmatch(${pyString(pattern)}, $t) is not None)")
      )

    case TheF(v, dom, body, span) =>
      if PythonReservedNames.contains(v) then
        Translated.Skip(s"The with Python-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          Translated.Emit(s"next(($v for $v in ($dp) if ($bp)), None)")
        )

    case LambdaF(param, body, span) =>
      if PythonReservedNames.contains(param) then
        Translated.Skip(s"Lambda with Python-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        lift1(translate(body, innerCtx))(b => Translated.Emit(s"(lambda $param: ($b))"))

    case SomeWrapF(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[span_t]): Translated =
    classifyIdent(ctx.identCtx(PythonReservedNames.toList), name) match
      case _: IcReserved => Translated.Skip(s"identifier '$name' is a Python-reserved name", span)
      case _: IcBound    => Translated.Emit(name)
      case _: IcBareBody => Translated.Emit("response_data")
      case _: IcOutput   => Translated.Emit(s"response_data[${pyString(name)}]")
      case _: IcInput    => Translated.Emit(name)
      case _: IcStateField =>
        val dict = ctx.capture match
          case CaptureMode.PostState => "post_state"
          case CaptureMode.PreState  => "pre_state"
        Translated.Emit(s"$dict[${pyString(name)}]")
      case _: IcUnbackedState =>
        Translated.Skip(
          s"state field '$name' is not backed by an entity table; the test-admin " +
            "/state endpoint projects it as null, so it cannot be asserted black-box",
          span
        )
      case _: IcEnumType  => Translated.Skip(s"enum-type identifier '$name'", span)
      case _: IcEnumValue => Translated.Emit(pyString(name))
      case _: IcUnbound   => Translated.Skip(s"unbound identifier '$name'", span)

  private def binOpText(op: bin_op_full, l: String, r: String): Translated =
    op match
      case BAnd()       => Translated.Emit(s"(($l) and ($r))")
      case BOr()        => Translated.Emit(s"(($l) or ($r))")
      case BImplies()   => Translated.Emit(s"((not ($l)) or ($r))")
      case BIff()       => Translated.Emit(s"(($l) == ($r))")
      case BEq()        => Translated.Emit(s"(($l) == ($r))")
      case BNeq()       => Translated.Emit(s"(($l) != ($r))")
      case BLt()        => Translated.Emit(s"(($l) < ($r))")
      case BGt()        => Translated.Emit(s"(($l) > ($r))")
      case BLe()        => Translated.Emit(s"(($l) <= ($r))")
      case BGe()        => Translated.Emit(s"(($l) >= ($r))")
      case BIn()        => Translated.Emit(s"(($l) in ($r))")
      case BNotIn()     => Translated.Emit(s"(($l) not in ($r))")
      case BAdd()       => Translated.Emit(s"(($l) + ($r))")
      case BSub()       => Translated.Emit(s"(($l) - ($r))")
      case BMul()       => Translated.Emit(s"(($l) * ($r))")
      case BDiv()       => Translated.Emit(s"(($l) / ($r))")
      case BUnion()     => Translated.Emit(s"(($l) | ($r))")
      case BIntersect() => Translated.Emit(s"(($l) & ($r))")
      case BDiff()      => Translated.Emit(s"(($l) - ($r))")
      case BSubset()    => Translated.Emit(s"(($l) <= ($r))")

  private def unOpText(op: un_op_full, x: String): Translated = op match
    case UNot()         => Translated.Emit(s"(not ($x))")
    case UNegate()      => Translated.Emit(s"(-($x))")
    case UCardinality() => Translated.Emit(s"len($x)")
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
        lift1(translate(callee, ctx)): cp =>
          liftAll(parts, span)(ps => Translated.Emit(s"($cp)(${ps.mkString(", ")})"))

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
          case Translated.Emit(text) => Translated.Emit(text)
          case Translated.Skip(reason, _) =>
            Translated.Skip(reason, span)

  private def recognizedCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    ExprLift.dispatchBuiltin(fname, args.map(translate(_, ctx)), span, _.py)

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
      case _: UcOk =>
        val parts  = args.map(translate(_, ctx))
        val pyName = Naming.toSnakeCase(fname)
        liftAll(parts, span)(ps => Translated.Emit(s"$pyName(${ps.mkString(", ")})"))

  private def mapLiteral(
      entries: List[map_entry_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if entries.isEmpty then Translated.Emit("{}")
    else
      val pairs = entries.collect { case MapEntryFull(k, v, _) =>
        lift2(translate(k, ctx), translate(v, ctx))((kx, vx) => Translated.Emit(s"$kx: $vx"))
      }
      liftAll(pairs, span)(ps => Translated.Emit(s"{${ps.mkString(", ")}}"))

  private def constructorLiteral(
      fields: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if fields.isEmpty then Translated.Emit("{}")
    else
      val pairs = fields.collect { case FieldAssignFull(n, v, _) =>
        lift1(translate(v, ctx))(vx => Translated.Emit(s"${pyString(n)}: $vx"))
      }
      liftAll(pairs, span)(ps => Translated.Emit(s"{${ps.mkString(", ")}}"))

  private def withUpdate(
      base: expr_full,
      updates: List[field_assign_full],
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    val basePy = translate(base, ctx)
    val pairs = updates.collect { case FieldAssignFull(n, v, _) =>
      lift1(translate(v, ctx))(vx => Translated.Emit(s"${pyString(n)}: $vx"))
    }
    lift1(basePy): bp =>
      liftAll(pairs, span)(ps => Translated.Emit(s"{**($bp), ${ps.mkString(", ")}}"))

  private def setComprehension(
      v: String,
      dom: expr_full,
      pred: expr_full,
      ctx: TestCtx,
      span: Option[span_t]
  ): Translated =
    if PythonReservedNames.contains(v) then
      Translated.Skip(s"SetComprehension with Python-reserved binding name '$v'", span)
    else
      val innerCtx    = ctx.withBound(List(v))
      val domPy       = translate(dom, ctx)
      val predPy      = translate(pred, innerCtx)
      val isMapDomain = peelRelationRefFull(dom).exists(ctx.mapStateFields.contains)
      lift2(domPy, predPy): (d, p) =>
        val iter = if isMapDomain then s"($d).values()" else s"($d)"
        Translated.Emit(s"{$v for $v in $iter if ($p)}")

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
      val domains    = bindings.collect { case QuantifierBindingFull(_, d, _, _) => translate(d, ctx) }
      val innerCtx   = ctx.withBound(boundNames)
      val bodyPy     = translate(body, innerCtx)
      liftAll(domains :+ bodyPy, span): texts =>
        val ds      = texts.init
        val bp      = texts.last
        val pairs   = boundNames.zip(ds).map((v, d) => s"$v in ($d)").mkString(" for ")
        val genFor  = s"for $pairs"
        val genExpr = s"($bp $genFor)"
        kind match
          case QAll()              => Translated.Emit(s"all$genExpr")
          case QSome() | QExists() => Translated.Emit(s"any$genExpr")
          case QNo()               => Translated.Emit(s"(not any$genExpr)")

  private[testgen] def pyString(s: String): String =
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    s"\"$escaped\""

  private def lift1(a: Translated)(f: String => Translated): Translated = ExprLift.lift1(a)(f)

  private def lift2(a: Translated, b: Translated)(f: (String, String) => Translated): Translated =
    ExprLift.lift2(a, b)(f)

  private def lift3(a: Translated, b: Translated, c: Translated)(
      f: (String, String, String) => Translated
  ): Translated = ExprLift.lift3(a, b, c)(f)

  private def liftAll(
      parts: List[Translated],
      span: Option[span_t]
  )(f: List[String] => Translated): Translated = ExprLift.liftAll(parts, span)(f)
