package specrest.verify.z3

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

    if usesOption(script) then
      lines += "(declare-datatype Option (par (T) ((none) (some (valOf T)))))"

    if usesMap(script) then
      lines += "(declare-datatype Pair (par (K V) ((mkPair (mapKey K) (mapVal V)))))"

    if script.funcs.nonEmpty then lines += ";; funcs"
    for f <- script.funcs do lines += renderFuncDecl(f)

    if script.assertions.nonEmpty then lines += ";; assertions"
    for a <- script.assertions do lines += s"(assert ${renderExpr(a)})"

    lines += "(check-sat)"
    lines.result().mkString("\n") + "\n"

  private def renderSort(s: Z3Sort): String = s match
    case Z3Sort.Int         => "Int"
    case Z3Sort.Real        => "Real"
    case Z3Sort.Bool        => "Bool"
    case Z3Sort.Uninterp(n) => n
    case Z3Sort.SetOf(e)    => s"(Set ${renderSort(e)})"
    case Z3Sort.OptionOf(e) => s"(Option ${renderSort(e)})"
    case Z3Sort.SeqOf(e)    => s"(Seq ${renderSort(e)})"
    case Z3Sort.MapOf(k, v) => s"(Seq (Pair ${renderSort(k)} ${renderSort(v)}))"
    case Z3Sort.Str         => "String"

  private def renderFuncDecl(f: Z3FunctionDecl): String =
    val args = f.argSorts.map(renderSort).mkString(" ")
    s"(declare-fun ${f.name} ($args) ${renderSort(f.resultSort)})"

  private def containsOption(s: Z3Sort): Boolean = s match
    case Z3Sort.OptionOf(_) => true
    case Z3Sort.SetOf(e)    => containsOption(e)
    case Z3Sort.SeqOf(e)    => containsOption(e)
    case Z3Sort.MapOf(k, v) => containsOption(k) || containsOption(v)
    case _                  => false

  private def usesOption(script: Z3Script): Boolean =
    script.sorts.exists(containsOption) ||
      script.funcs.exists(f => f.argSorts.exists(containsOption) || containsOption(f.resultSort))

  private def containsMap(s: Z3Sort): Boolean = s match
    case Z3Sort.MapOf(_, _) => true
    case Z3Sort.SetOf(e)    => containsMap(e)
    case Z3Sort.SeqOf(e)    => containsMap(e)
    case Z3Sort.OptionOf(e) => containsMap(e)
    case _                  => false

  private def usesMap(script: Z3Script): Boolean =
    script.sorts.exists(containsMap) ||
      script.funcs.exists(f => f.argSorts.exists(containsMap) || containsMap(f.resultSort))

  def renderExpr(e: Z3Expr): String = e match
    case Z3Expr.Var(name, _, _) => name
    case Z3Expr.App(func, args, _) =>
      if args.isEmpty then func
      else s"($func ${args.map(renderExpr).mkString(" ")})"
    case Z3Expr.IntLit(v, _) =>
      if v.signum < 0 then s"(- ${-v})" else v.toString
    case Z3Expr.RealLit(num, den, _) =>
      val n = if num.signum < 0 then s"(- ${-num}.0)" else s"${num}.0"
      if den == BigInt(1) then n else s"(/ $n ${den}.0)"
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
    case Z3Expr.EmptySet(elemSort, _) => emptySetLit(elemSort)
    case Z3Expr.SetLit(elemSort, members, _) =>
      members.foldLeft(emptySetLit(elemSort))((acc, m) => s"(store $acc ${renderExpr(m)} true)")
    case Z3Expr.SetMember(elem, set, _) =>
      s"(select ${renderExpr(set)} ${renderExpr(elem)})"
    case Z3Expr.SetBinOp(op, l, r, _) =>
      s"(${SetOpKind.token(op)} ${renderExpr(l)} ${renderExpr(r)})"
    case Z3Expr.Ite(c, t, e, _) =>
      s"(ite ${renderExpr(c)} ${renderExpr(t)} ${renderExpr(e)})"
    case Z3Expr.OptNone(elemSort, _) =>
      s"(as none (Option ${renderSort(elemSort)}))"
    case Z3Expr.OptSome(value, _) =>
      s"(some ${renderExpr(value)})"
    case Z3Expr.StrLit(s, _) =>
      "\"" + s.replace("\"", "\"\"") + "\""
    case Z3Expr.SeqLit(elemSort, members, _) =>
      members.foldLeft(s"(as seq.empty (Seq ${renderSort(elemSort)}))")((acc, m) =>
        s"(seq.++ $acc (seq.unit ${renderExpr(m)}))"
      )
    case Z3Expr.MapLit(keySort, valueSort, entries, _) =>
      val empty = s"(as seq.empty (Seq (Pair ${renderSort(keySort)} ${renderSort(valueSort)})))"
      entries.foldLeft(empty) { case (acc, (k, v)) =>
        s"(seq.++ $acc (seq.unit (mkPair ${renderExpr(k)} ${renderExpr(v)})))"
      }

  private def emptySetLit(elemSort: Z3Sort): String =
    s"((as const (Set ${renderSort(elemSort)})) false)"
