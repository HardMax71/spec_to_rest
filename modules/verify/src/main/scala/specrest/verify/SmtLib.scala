package specrest.verify

object SmtLib:

  def renderSmtLib(script: Z3Script, timeoutMs: Option[Long] = None): String =
    val lines = List.newBuilder[String]
    lines += "(set-logic ALL)"
    lines += "(set-option :produce-models true)"
    timeoutMs.filter(_ > 0).foreach(ms => lines += s"(set-option :timeout $ms)")

    if script.sorts.nonEmpty then lines += ";; sorts"
    for s <- script.sorts do
      s match
        case Z3Sort.Uninterp(n) => lines += s"(declare-sort $n 0)"
        case _                  => ()

    if script.funcs.nonEmpty then lines += ";; funcs"
    for f <- script.funcs do lines += renderFuncDecl(f)

    if script.assertions.nonEmpty then lines += ";; assertions"
    for a <- script.assertions do lines += s"(assert ${renderExpr(a)})"

    lines += "(check-sat)"
    lines.result().mkString("\n") + "\n"

  private def renderSort(s: Z3Sort): String = s match
    case Z3Sort.Int         => "Int"
    case Z3Sort.Bool        => "Bool"
    case Z3Sort.Uninterp(n) => n

  private def renderFuncDecl(f: Z3FunctionDecl): String =
    val args = f.argSorts.map(renderSort).mkString(" ")
    s"(declare-fun ${f.name} ($args) ${renderSort(f.resultSort)})"

  def renderExpr(e: Z3Expr): String = e match
    case Z3Expr.Var(name, _, _) => name
    case Z3Expr.App(func, args, _) =>
      if args.isEmpty then func
      else s"($func ${args.map(renderExpr).mkString(" ")})"
    case Z3Expr.IntLit(v, _) =>
      if v < 0 then s"(- ${-v})" else v.toString
    case Z3Expr.BoolLit(v, _) =>
      if v then "true" else "false"
    case Z3Expr.And(args, _) =>
      args match
        case Nil      => "true"
        case x :: Nil => renderExpr(x)
        case xs       => s"(and ${xs.map(renderExpr).mkString(" ")})"
    case Z3Expr.Or(args, _) =>
      args match
        case Nil      => "false"
        case x :: Nil => renderExpr(x)
        case xs       => s"(or ${xs.map(renderExpr).mkString(" ")})"
    case Z3Expr.Not(arg, _)      => s"(not ${renderExpr(arg)})"
    case Z3Expr.Implies(l, r, _) => s"(=> ${renderExpr(l)} ${renderExpr(r)})"
    case Z3Expr.Cmp(op, l, r, _) =>
      val token = if op == CmpOp.Neq then "distinct" else CmpOp.token(op)
      s"($token ${renderExpr(l)} ${renderExpr(r)})"
    case Z3Expr.Arith(op, args, _) =>
      s"(${ArithOp.token(op)} ${args.map(renderExpr).mkString(" ")})"
    case Z3Expr.Quantifier(q, bindings, body, _) =>
      val qTok    = if q == QKind.ForAll then "forall" else "exists"
      val binders = bindings.map(b => s"(${b.name} ${renderSort(b.sort)})").mkString(" ")
      s"($qTok ($binders) ${renderExpr(body)})"
