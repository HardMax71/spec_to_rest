package specrest.testgen

import specrest.ir.BinOp
import specrest.ir.BindingKind
import specrest.ir.Expr
import specrest.ir.QuantKind
import specrest.ir.QuantifierBinding
import specrest.ir.Span
import specrest.ir.UnOp

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

    case Expr.BinaryOp(op, l, r, _) =>
      lift2(translate(l, ctx), translate(r, ctx))(binOpText(op, _, _))

    case Expr.UnaryOp(op, x, span) =>
      lift1(translate(x, ctx))(unOpText(op, _, span))

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

    case Expr.Let(v, value, body, _) =>
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

    case Expr.MapLiteral(_, span)          => ExprPy.Skip("MapLiteral", span)
    case Expr.Constructor(t, _, span)      => ExprPy.Skip(s"Constructor($t)", span)
    case Expr.With(_, _, span)             => ExprPy.Skip("With (record update)", span)
    case Expr.SetComprehension(_, _, _, s) => ExprPy.Skip("SetComprehension", s)
    case Expr.SeqLiteral(_, span)          => ExprPy.Skip("SeqLiteral", span)
    case Expr.Matches(_, _, span)          => ExprPy.Skip("Matches", span)
    case Expr.The(_, _, _, span)           => ExprPy.Skip("The", span)
    case Expr.Lambda(_, _, span)           => ExprPy.Skip("Lambda", span)
    case Expr.SomeWrap(_, span)            => ExprPy.Skip("SomeWrap", span)

  private def resolveIdent(name: String, ctx: TestCtx, span: Option[Span]): ExprPy =
    if ctx.boundVars.contains(name) then ExprPy.Py(name)
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

  private def unOpText(op: UnOp, x: String, span: Option[Span]): ExprPy = op match
    case UnOp.Not         => ExprPy.Py(s"(not ($x))")
    case UnOp.Negate      => ExprPy.Py(s"(-($x))")
    case UnOp.Cardinality => ExprPy.Py(s"len($x)")
    case UnOp.Power       => ExprPy.Skip("UnOp.Power", span)

  private def callExpr(
      callee: Expr,
      args: List[Expr],
      ctx: TestCtx,
      span: Option[Span]
  ): ExprPy =
    callee match
      case Expr.Identifier(name, _) => recognizedCall(name, args, ctx, span)
      case _                        => ExprPy.Skip("indirect call (non-identifier callee)", span)

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
      case n if Set("isValidURI", "valid_uri", "is_valid_uri").contains(n) && args.size == 1 =>
        lift1(translate(args.head, ctx))(a => ExprPy.Py(s"is_valid_uri($a)"))
      case n
          if Set("valid_email", "isValidEmail", "is_valid_email").contains(n) && args.size == 1 =>
        lift1(translate(args.head, ctx))(a => ExprPy.Py(s"is_valid_email($a)"))
      case other =>
        ExprPy.Skip(s"unknown function '$other/${args.size}'", span)

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
