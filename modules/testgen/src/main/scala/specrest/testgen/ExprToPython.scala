package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

import specrest.convention.Naming

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

@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
object ExprToPython:

  def translate(expr: expr_full, ctx: TestCtx): ExprPy = expr match
    case BoolLitF(v, _)   => ExprPy.Py(if v then "True" else "False")
    case IntLitF(n, _)    => ExprPy.Py(n.toString)
    case FloatLitF(d, _)  => ExprPy.Py(d.toString)
    case StringLitF(s, _) => ExprPy.Py(pyString(s))
    case NoneLitF(_)      => ExprPy.Py("None")

    case IdentifierF(name, span) => resolveIdent(name, ctx, span)

    case PrimeF(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case PreF(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case BinaryOpF(BAdd(), l, r, _)
        if l.isInstanceOf[MapLiteralF] || r.isInstanceOf[MapLiteralF] =>
      lift2(translate(l, ctx), translate(r, ctx))((lp, rp) => ExprPy.Py(s"{**($lp), **($rp)}"))

    case BinaryOpF(op, l, r, _) =>
      lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case UnaryOpF(op, x, _) =>
      lift1(translate(x, ctx))(unOpText(op, _))

    case FieldAccessF(base, field, _) =>
      lift1(translate(base, ctx))(b => ExprPy.Py(s"$b[${pyString(field)}]"))

    case EnumAccessF(_, member, _) => ExprPy.Py(pyString(member))

    case IndexF(base, idx, _) =>
      lift2(translate(base, ctx), translate(idx, ctx))((b, i) => ExprPy.Py(s"$b[$i]"))

    case CallF(callee, args, span) => callExpr(callee, args, ctx, span)

    case IfF(c, t, e, _) =>
      lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        ExprPy.Py(s"(($tp) if ($cp) else ($ep))")
      )

    case LetF(v, value, body, span) =>
      if PythonReservedNames.contains(v) then
        ExprPy.Skip(s"Let with Python-reserved binding name '$v'", span)
      else
        lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          ExprPy.Py(s"((lambda $v=($vp): ($bp))())")
        )

    case SetLiteralF(elements, span) =>
      if elements.isEmpty then ExprPy.Py("set()")
      else
        val parts = elements.map(translate(_, ctx))
        liftAll(parts, span)(ps => ExprPy.Py(s"{${ps.mkString(", ")}}"))

    case QuantifierF(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case MapLiteralF(entries, span) => mapLiteral(entries, ctx, span)

    case ConstructorF(_, fields, span) => constructorLiteral(fields, ctx, span)

    case WithF(base, updates, span) => withUpdate(base, updates, ctx, span)

    case SetComprehensionF(v, dom, pred, span) =>
      setComprehension(v, dom, pred, ctx, span)

    case SeqLiteralF(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      liftAll(parts, span)(ps => ExprPy.Py(s"[${ps.mkString(", ")}]"))

    case MatchesF(e, pattern, span) =>
      lift1(translate(e, ctx))(t =>
        ExprPy.Py(s"(re.fullmatch(${pyString(pattern)}, $t) is not None)")
      )

    case TheF(v, dom, body, span) =>
      if PythonReservedNames.contains(v) then
        ExprPy.Skip(s"The with Python-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          ExprPy.Py(s"next(($v for $v in ($dp) if ($bp)), None)")
        )

    case LambdaF(param, body, span) =>
      if PythonReservedNames.contains(param) then
        ExprPy.Skip(s"Lambda with Python-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        lift1(translate(body, innerCtx))(b => ExprPy.Py(s"(lambda $param: ($b))"))

    case SomeWrapF(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[SpanT]): ExprPy =
    if PythonReservedNames.contains(name) &&
      (ctx.boundVars.contains(name) || ctx.b.contains(name))
    then ExprPy.Skip(s"identifier '$name' is a Python-reserved name", span)
    else if ctx.boundVars.contains(name) then ExprPy.Py(name)
    else if ctx.c.contains(name) then ExprPy.Py(s"response_data[${pyString(name)}]")
    else if ctx.b.contains(name) then ExprPy.Py(name)
    else if ctx.stateFields.contains(name) then
      val dict = ctx.capture match
        case CaptureMode.PostState => "post_state"
        case CaptureMode.PreState  => "pre_state"
      ExprPy.Py(s"$dict[${pyString(name)}]")
    else if ctx.enumValues.contains(name) then ExprPy.Skip(s"enum-type identifier '$name'", span)
    else
      ctx.enumValues.find { case (_, vs) => vs.contains(name) } match
        case Some(_) => ExprPy.Py(pyString(name))
        case None    => ExprPy.Skip(s"unbound identifier '$name'", span)

  private def binOpText(op: bin_op_full, l: String, r: String): ExprPy =
    op match
      case BAnd()       => ExprPy.Py(s"(($l) and ($r))")
      case BOr()        => ExprPy.Py(s"(($l) or ($r))")
      case BImplies()   => ExprPy.Py(s"((not ($l)) or ($r))")
      case BIff()       => ExprPy.Py(s"(($l) == ($r))")
      case BEq()        => ExprPy.Py(s"(($l) == ($r))")
      case BNeq()       => ExprPy.Py(s"(($l) != ($r))")
      case BLt()        => ExprPy.Py(s"(($l) < ($r))")
      case BGt()        => ExprPy.Py(s"(($l) > ($r))")
      case BLe()        => ExprPy.Py(s"(($l) <= ($r))")
      case BGe()        => ExprPy.Py(s"(($l) >= ($r))")
      case BIn()        => ExprPy.Py(s"(($l) in ($r))")
      case BNotIn()     => ExprPy.Py(s"(($l) not in ($r))")
      case BAdd()       => ExprPy.Py(s"(($l) + ($r))")
      case BSub()       => ExprPy.Py(s"(($l) - ($r))")
      case BMul()       => ExprPy.Py(s"(($l) * ($r))")
      case BDiv()       => ExprPy.Py(s"(($l) / ($r))")
      case BUnion()     => ExprPy.Py(s"(($l) | ($r))")
      case BIntersect() => ExprPy.Py(s"(($l) & ($r))")
      case BDiff()      => ExprPy.Py(s"(($l) - ($r))")
      case BSubset()    => ExprPy.Py(s"(($l) <= ($r))")

  private def unOpText(op: un_op_full, x: String): ExprPy = op match
    case UNot()         => ExprPy.Py(s"(not ($x))")
    case UNegate()      => ExprPy.Py(s"(-($x))")
    case UCardinality() => ExprPy.Py(s"len($x)")
    case UPower()       => ExprPy.Py(s"_powerset($x)")

  private def callExpr(
      callee: expr_full,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    callee match
      case IdentifierF(name, _) => identifierCall(name, args, ctx, span)
      case _ =>
        val parts = args.map(translate(_, ctx))
        lift1(translate(callee, ctx)): cp =>
          liftAll(parts, span)(ps => ExprPy.Py(s"($cp)(${ps.mkString(", ")})"))

  private def identifierCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    recognizedCall(fname, args, ctx, span) match
      case ExprPy.Py(text) => ExprPy.Py(text)
      case ExprPy.Skip(_, _) =>
        userDefinedCall(fname, args, ctx, span) match
          case ExprPy.Py(text) => ExprPy.Py(text)
          case ExprPy.Skip(reason, _) =>
            ExprPy.Skip(reason, span)

  private def recognizedCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    fname match
      case "len" if args.size == 1 =>
        lift1(translate(args.head, ctx))(a => ExprPy.Py(s"len($a)"))
      case "dom" if args.size == 1 =>
        lift1(translate(args.head, ctx))(a => ExprPy.Py(s"set($a.keys())"))
      case "ran" if args.size == 1 =>
        lift1(translate(args.head, ctx))(a => ExprPy.Py(s"set($a.values())"))
      case "max" if args.size == 1 =>
        lift1(translate(args.head, ctx))(a => ExprPy.Py(s"max($a)"))
      case "min" if args.size == 1 =>
        lift1(translate(args.head, ctx))(a => ExprPy.Py(s"min($a)"))
      case "now" if args.isEmpty =>
        ExprPy.Py("datetime.datetime.now(datetime.timezone.utc).isoformat()")
      case "days" if args.size == 1 =>
        lift1(translate(args.head, ctx))(a =>
          ExprPy.Py(s"datetime.timedelta(days=$a).total_seconds()")
        )
      case "sum" if args.size == 2 =>
        sumCall(args(0), args(1), ctx, span)
      case other =>
        ExprPy.Skip(s"unknown function '$other/${args.size}' (see #138)", span)

  private def sumCall(
      coll: expr_full,
      fn: expr_full,
      ctx: TestCtx,
      @scala.annotation.unused span: Option[SpanT]
  ): ExprPy =
    fn match
      case LambdaF(param, body, _) if !PythonReservedNames.contains(param) =>
        val innerCtx = ctx.withBound(List(param))
        lift2(translate(coll, ctx), translate(body, innerCtx))((c, b) =>
          ExprPy.Py(s"sum(($b) for $param in ($c))")
        )
      case _ =>
        lift2(translate(coll, ctx), translate(fn, ctx))((c, f) =>
          ExprPy.Py(s"sum(($f)(_x) for _x in ($c))")
        )

  private def userDefinedCall(
      fname: String,
      args: List[expr_full],
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    val expectedArity = ctx.userFunctions
      .get(fname)
      .map(_.params.size)
      .orElse(ctx.userPredicates.get(fname).map(_.params.size))
    expectedArity match
      case None =>
        ExprPy.Skip(s"unknown function '$fname/${args.size}' (see #138)", span)
      case Some(n) if n != args.size =>
        ExprPy.Skip(
          s"wrong arity for user-defined call '$fname': expected $n, got ${args.size}",
          span
        )
      case Some(_) =>
        val parts  = args.map(translate(_, ctx))
        val pyName = Naming.toSnakeCase(fname)
        liftAll(parts, span)(ps => ExprPy.Py(s"$pyName(${ps.mkString(", ")})"))

  private def mapLiteral(
      entries: List[MapEntryFull],
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    if entries.isEmpty then ExprPy.Py("{}")
    else
      val pairs = entries.map: e =>
        lift2(translate(e.key, ctx), translate(e.value, ctx))((k, v) => ExprPy.Py(s"$k: $v"))
      liftAll(pairs, span)(ps => ExprPy.Py(s"{${ps.mkString(", ")}}"))

  private def constructorLiteral(
      fields: List[FieldAssignFull],
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    if fields.isEmpty then ExprPy.Py("{}")
    else
      val pairs = fields.map: f =>
        lift1(translate(f.b, ctx))(v => ExprPy.Py(s"${pyString(f.a)}: $v"))
      liftAll(pairs, span)(ps => ExprPy.Py(s"{${ps.mkString(", ")}}"))

  private def withUpdate(
      base: expr_full,
      updates: List[FieldAssignFull],
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    val basePy = translate(base, ctx)
    val pairs = updates.map: f =>
      lift1(translate(f.b, ctx))(v => ExprPy.Py(s"${pyString(f.a)}: $v"))
    lift1(basePy): bp =>
      liftAll(pairs, span)(ps => ExprPy.Py(s"{**($bp), ${ps.mkString(", ")}}"))

  private def setComprehension(
      v: String,
      dom: expr_full,
      pred: expr_full,
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    if PythonReservedNames.contains(v) then
      ExprPy.Skip(s"SetComprehension with Python-reserved binding name '$v'", span)
    else
      val innerCtx = ctx.withBound(List(v))
      val domPy    = translate(dom, ctx)
      val predPy   = translate(pred, innerCtx)
      val isMapDomain = dom match
        case IdentifierF(n, _) if ctx.mapStateFields.contains(n) => true
        case PreF(IdentifierF(n, _), _) if ctx.mapStateFields.contains(n) =>
          true
        case PrimeF(IdentifierF(n, _), _) if ctx.mapStateFields.contains(n) =>
          true
        case _ => false
      lift2(domPy, predPy): (d, p) =>
        val iter = if isMapDomain then s"($d).values()" else s"($d)"
        ExprPy.Py(s"{$v for $v in $iter if ($p)}")

  private def quantifier(
      kind: quant_kind_full,
      bindings: List[QuantifierBindingFull],
      body: expr_full,
      ctx: TestCtx,
      span: Option[SpanT]
  ): ExprPy =
    if bindings.exists(_.c != BkIn()) then
      ExprPy.Skip("quantifier with non-`in` binding", span)
    else
      val boundNames = bindings.map(_.a)
      val domains    = bindings.map(b => translate(b.b, ctx))
      val innerCtx   = ctx.withBound(boundNames)
      val bodyPy     = translate(body, innerCtx)
      liftAll(domains :+ bodyPy, span): texts =>
        val ds      = texts.init
        val bp      = texts.last
        val pairs   = boundNames.zip(ds).map((v, d) => s"$v in ($d)").mkString(" for ")
        val genFor  = s"for $pairs"
        val genExpr = s"($bp $genFor)"
        kind match
          case QAll()              => ExprPy.Py(s"all$genExpr")
          case QSome() | QExists() => ExprPy.Py(s"any$genExpr")
          case QNo()               => ExprPy.Py(s"(not any$genExpr)")

  private[testgen] def pyString(s: String): String =
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    s"\"$escaped\""

  private def lift1(a: ExprPy)(f: String => ExprPy): ExprPy = a match
    case ExprPy.Py(x)          => f(x)
    case s @ ExprPy.Skip(_, _) => s

  private def lift2(a: ExprPy, b: ExprPy)(f: (String, String) => ExprPy): ExprPy =
    (a, b) match
      case (ExprPy.Py(x), ExprPy.Py(y)) => f(x, y)
      case (s @ ExprPy.Skip(_, _), _)   => s
      case (_, s @ ExprPy.Skip(_, _))   => s

  private def lift3(a: ExprPy, b: ExprPy, c: ExprPy)(
      f: (String, String, String) => ExprPy
  ): ExprPy =
    (a, b, c) match
      case (ExprPy.Py(x), ExprPy.Py(y), ExprPy.Py(z)) => f(x, y, z)
      case (s @ ExprPy.Skip(_, _), _, _)              => s
      case (_, s @ ExprPy.Skip(_, _), _)              => s
      case (_, _, s @ ExprPy.Skip(_, _))              => s

  private def liftAll(
      parts: List[ExprPy],
      span: Option[SpanT]
  )(f: List[String] => ExprPy): ExprPy =
    val firstSkip = parts.collectFirst { case s @ ExprPy.Skip(_, _) => s }
    firstSkip match
      case Some(s) => s
      case None =>
        val texts = parts.collect { case ExprPy.Py(t) => t }
        if texts.size == parts.size then f(texts)
        else ExprPy.Skip("internal: lift mismatch", span)
