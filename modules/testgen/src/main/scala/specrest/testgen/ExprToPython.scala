package specrest.testgen

import specrest.convention.Naming
import specrest.ir.BinOp
import specrest.ir.BindingKind
import specrest.ir.Expr
import specrest.ir.FieldAssign
import specrest.ir.MapEntry
import specrest.ir.QuantKind
import specrest.ir.QuantifierBinding
import specrest.ir.Span
import specrest.ir.UnOp

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

  def translate(expr: Expr, ctx: TestCtx): ExprPy = expr match
    case Expr.BoolLit(v, _)   => ExprPy.Py(if v then "True" else "False")
    case Expr.IntLit(n, _)    => ExprPy.Py(n.toString)
    case Expr.FloatLit(d, _)  => ExprPy.Py(d.toString)
    case Expr.StringLit(s, _) => ExprPy.Py(pyString(s))
    case Expr.NoneLit(_)      => ExprPy.Py("None")

    case Expr.Identifier(name, span) => resolveIdent(name, ctx, span)

    case Expr.Prime(inner, _) => translate(inner, ctx.withCapture(CaptureMode.PostState))
    case Expr.Pre(inner, _)   => translate(inner, ctx.withCapture(CaptureMode.PreState))

    case Expr.BinaryOp(BinOp.Add, l, r, _)
        if l.isInstanceOf[Expr.MapLiteral] || r.isInstanceOf[Expr.MapLiteral] =>
      lift2(translate(l, ctx), translate(r, ctx))((lp, rp) => ExprPy.Py(s"{**($lp), **($rp)}"))

    case Expr.BinaryOp(op, l, r, _) =>
      lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case Expr.UnaryOp(op, x, _) =>
      lift1(translate(x, ctx))(unOpText(op, _))

    case Expr.FieldAccess(base, field, _) =>
      lift1(translate(base, ctx))(b => ExprPy.Py(s"$b[${pyString(field)}]"))

    case Expr.EnumAccess(_, member, _) => ExprPy.Py(pyString(member))

    case Expr.Index(base, idx, _) =>
      lift2(translate(base, ctx), translate(idx, ctx))((b, i) => ExprPy.Py(s"$b[$i]"))

    case Expr.Call(callee, args, span) => callExpr(callee, args, ctx, span)

    case Expr.If(c, t, e, _) =>
      lift3(translate(c, ctx), translate(t, ctx), translate(e, ctx))((cp, tp, ep) =>
        ExprPy.Py(s"(($tp) if ($cp) else ($ep))")
      )

    case Expr.Let(v, value, body, span) =>
      if PythonReservedNames.contains(v) then
        ExprPy.Skip(s"Let with Python-reserved binding name '$v'", span)
      else
        lift2(translate(value, ctx), translate(body, ctx.withBound(List(v))))((vp, bp) =>
          ExprPy.Py(s"((lambda $v=($vp): ($bp))())")
        )

    case Expr.SetLiteral(elements, span) =>
      if elements.isEmpty then ExprPy.Py("set()")
      else
        val parts = elements.map(translate(_, ctx))
        liftAll(parts, span)(ps => ExprPy.Py(s"{${ps.mkString(", ")}}"))

    case Expr.Quantifier(kind, bindings, body, span) =>
      quantifier(kind, bindings, body, ctx, span)

    case Expr.MapLiteral(entries, span) => mapLiteral(entries, ctx, span)

    case Expr.Constructor(_, fields, span) => constructorLiteral(fields, ctx, span)

    case Expr.With(base, updates, span) => withUpdate(base, updates, ctx, span)

    case Expr.SetComprehension(v, dom, pred, span) =>
      setComprehension(v, dom, pred, ctx, span)

    case Expr.SeqLiteral(elements, span) =>
      val parts = elements.map(translate(_, ctx))
      liftAll(parts, span)(ps => ExprPy.Py(s"[${ps.mkString(", ")}]"))

    case Expr.Matches(e, pattern, span) =>
      lift1(translate(e, ctx))(t =>
        ExprPy.Py(s"(re.fullmatch(${pyString(pattern)}, $t) is not None)")
      )

    case Expr.The(v, dom, body, span) =>
      if PythonReservedNames.contains(v) then
        ExprPy.Skip(s"The with Python-reserved binding name '$v'", span)
      else
        val innerCtx = ctx.withBound(List(v))
        lift2(translate(dom, ctx), translate(body, innerCtx))((dp, bp) =>
          ExprPy.Py(s"next(($v for $v in ($dp) if ($bp)), None)")
        )

    case Expr.Lambda(param, body, span) =>
      if PythonReservedNames.contains(param) then
        ExprPy.Skip(s"Lambda with Python-reserved param name '$param'", span)
      else
        val innerCtx = ctx.withBound(List(param))
        lift1(translate(body, innerCtx))(b => ExprPy.Py(s"(lambda $param: ($b))"))

    case Expr.SomeWrap(inner, _) => translate(inner, ctx)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[Span]): ExprPy =
    if PythonReservedNames.contains(name) &&
      (ctx.boundVars.contains(name) || ctx.inputs.contains(name))
    then ExprPy.Skip(s"identifier '$name' is a Python-reserved name", span)
    else if ctx.boundVars.contains(name) then ExprPy.Py(name)
    else if ctx.outputs.contains(name) then ExprPy.Py(s"response_data[${pyString(name)}]")
    else if ctx.inputs.contains(name) then ExprPy.Py(name)
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

  private def binOpText(op: BinOp, l: String, r: String): ExprPy =
    op match
      case BinOp.And       => ExprPy.Py(s"(($l) and ($r))")
      case BinOp.Or        => ExprPy.Py(s"(($l) or ($r))")
      case BinOp.Implies   => ExprPy.Py(s"((not ($l)) or ($r))")
      case BinOp.Iff       => ExprPy.Py(s"(($l) == ($r))")
      case BinOp.Eq        => ExprPy.Py(s"(($l) == ($r))")
      case BinOp.Neq       => ExprPy.Py(s"(($l) != ($r))")
      case BinOp.Lt        => ExprPy.Py(s"(($l) < ($r))")
      case BinOp.Gt        => ExprPy.Py(s"(($l) > ($r))")
      case BinOp.Le        => ExprPy.Py(s"(($l) <= ($r))")
      case BinOp.Ge        => ExprPy.Py(s"(($l) >= ($r))")
      case BinOp.In        => ExprPy.Py(s"(($l) in ($r))")
      case BinOp.NotIn     => ExprPy.Py(s"(($l) not in ($r))")
      case BinOp.Add       => ExprPy.Py(s"(($l) + ($r))")
      case BinOp.Sub       => ExprPy.Py(s"(($l) - ($r))")
      case BinOp.Mul       => ExprPy.Py(s"(($l) * ($r))")
      case BinOp.Div       => ExprPy.Py(s"(($l) / ($r))")
      case BinOp.Union     => ExprPy.Py(s"(($l) | ($r))")
      case BinOp.Intersect => ExprPy.Py(s"(($l) & ($r))")
      case BinOp.Diff      => ExprPy.Py(s"(($l) - ($r))")
      case BinOp.Subset    => ExprPy.Py(s"(($l) <= ($r))")

  private def unOpText(op: UnOp, x: String): ExprPy = op match
    case UnOp.Not         => ExprPy.Py(s"(not ($x))")
    case UnOp.Negate      => ExprPy.Py(s"(-($x))")
    case UnOp.Cardinality => ExprPy.Py(s"len($x)")
    case UnOp.Power       => ExprPy.Py(s"_powerset($x)")

  private def callExpr(
      callee: Expr,
      args: List[Expr],
      ctx: TestCtx,
      span: Option[Span]
  ): ExprPy =
    callee match
      case Expr.Identifier(name, _) => identifierCall(name, args, ctx, span)
      case _ =>
        val parts = args.map(translate(_, ctx))
        lift1(translate(callee, ctx)): cp =>
          liftAll(parts, span)(ps => ExprPy.Py(s"($cp)(${ps.mkString(", ")})"))

  private def identifierCall(
      fname: String,
      args: List[Expr],
      ctx: TestCtx,
      span: Option[Span]
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
      args: List[Expr],
      ctx: TestCtx,
      span: Option[Span]
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
      coll: Expr,
      fn: Expr,
      ctx: TestCtx,
      @scala.annotation.unused span: Option[Span]
  ): ExprPy =
    fn match
      case Expr.Lambda(param, body, _) if !PythonReservedNames.contains(param) =>
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
      args: List[Expr],
      ctx: TestCtx,
      span: Option[Span]
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
      entries: List[MapEntry],
      ctx: TestCtx,
      span: Option[Span]
  ): ExprPy =
    if entries.isEmpty then ExprPy.Py("{}")
    else
      val pairs = entries.map: e =>
        lift2(translate(e.key, ctx), translate(e.value, ctx))((k, v) => ExprPy.Py(s"$k: $v"))
      liftAll(pairs, span)(ps => ExprPy.Py(s"{${ps.mkString(", ")}}"))

  private def constructorLiteral(
      fields: List[FieldAssign],
      ctx: TestCtx,
      span: Option[Span]
  ): ExprPy =
    if fields.isEmpty then ExprPy.Py("{}")
    else
      val pairs = fields.map: f =>
        lift1(translate(f.value, ctx))(v => ExprPy.Py(s"${pyString(f.name)}: $v"))
      liftAll(pairs, span)(ps => ExprPy.Py(s"{${ps.mkString(", ")}}"))

  private def withUpdate(
      base: Expr,
      updates: List[FieldAssign],
      ctx: TestCtx,
      span: Option[Span]
  ): ExprPy =
    val basePy = translate(base, ctx)
    val pairs = updates.map: f =>
      lift1(translate(f.value, ctx))(v => ExprPy.Py(s"${pyString(f.name)}: $v"))
    lift1(basePy): bp =>
      liftAll(pairs, span)(ps => ExprPy.Py(s"{**($bp), ${ps.mkString(", ")}}"))

  private def setComprehension(
      v: String,
      dom: Expr,
      pred: Expr,
      ctx: TestCtx,
      span: Option[Span]
  ): ExprPy =
    if PythonReservedNames.contains(v) then
      ExprPy.Skip(s"SetComprehension with Python-reserved binding name '$v'", span)
    else
      val innerCtx = ctx.withBound(List(v))
      val domPy    = translate(dom, ctx)
      val predPy   = translate(pred, innerCtx)
      val isMapDomain = dom match
        case Expr.Identifier(n, _) if ctx.mapStateFields.contains(n) => true
        case Expr.Pre(Expr.Identifier(n, _), _) if ctx.mapStateFields.contains(n) =>
          true
        case Expr.Prime(Expr.Identifier(n, _), _) if ctx.mapStateFields.contains(n) =>
          true
        case _ => false
      lift2(domPy, predPy): (d, p) =>
        val iter = if isMapDomain then s"($d).values()" else s"($d)"
        ExprPy.Py(s"{$v for $v in $iter if ($p)}")

  private def quantifier(
      kind: QuantKind,
      bindings: List[QuantifierBinding],
      body: Expr,
      ctx: TestCtx,
      span: Option[Span]
  ): ExprPy =
    if bindings.exists(_.bindingKind != BindingKind.In) then
      ExprPy.Skip("quantifier with non-`in` binding", span)
    else
      val boundNames = bindings.map(_.variable)
      val domains    = bindings.map(b => translate(b.domain, ctx))
      val innerCtx   = ctx.withBound(boundNames)
      val bodyPy     = translate(body, innerCtx)
      liftAll(domains :+ bodyPy, span): texts =>
        val ds      = texts.init
        val bp      = texts.last
        val pairs   = boundNames.zip(ds).map((v, d) => s"$v in ($d)").mkString(" for ")
        val genFor  = s"for $pairs"
        val genExpr = s"($bp $genFor)"
        kind match
          case QuantKind.All                     => ExprPy.Py(s"all$genExpr")
          case QuantKind.Some | QuantKind.Exists => ExprPy.Py(s"any$genExpr")
          case QuantKind.No                      => ExprPy.Py(s"(not any$genExpr)")

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

  private def liftAll(parts: List[ExprPy], span: Option[Span])(f: List[String] => ExprPy): ExprPy =
    val firstSkip = parts.collectFirst { case s @ ExprPy.Skip(_, _) => s }
    firstSkip match
      case Some(s) => s
      case None =>
        val texts = parts.collect { case ExprPy.Py(t) => t }
        if texts.size == parts.size then f(texts)
        else ExprPy.Skip("internal: lift mismatch", span)
