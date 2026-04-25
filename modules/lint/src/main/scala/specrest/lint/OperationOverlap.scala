package specrest.lint

import specrest.ir.BinOp
import specrest.ir.Expr
import specrest.ir.OperationDecl
import specrest.ir.ParamDecl
import specrest.ir.ServiceIR
import specrest.ir.TypeExpr

object OperationOverlap extends LintPass:
  val code = "L04"

  def run(ir: ServiceIR): List[LintDiagnostic] =
    val out    = List.newBuilder[LintDiagnostic]
    val groups = ir.operations.groupBy(opSignature).filter(_._2.length >= 2)
    for (_, ops) <- groups do
      val sorted = ops.sortBy(_.name)
      for i <- sorted.indices; j <- (i + 1) until sorted.length do
        val a    = sorted(i)
        val b    = sorted(j)
        val nrA  = normalizedRequires(a.requires)
        val nrB  = normalizedRequires(b.requires)
        val same = nrA.length == nrB.length && nrA.zip(nrB).forall((x, y) => x == y)
        if same then
          val rel = a.span.toList.map(s => RelatedSpan(s, s"first definition of '${a.name}'"))
          val phrase =
            if nrA.isEmpty then "neither has preconditions" else "they share the same preconditions"
          out += LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operations '${a.name}' and '${b.name}' have the same input/output signature and $phrase — dispatch is ambiguous on shared inputs",
            b.span,
            rel
          )
    out.result()

  private def opSignature(op: OperationDecl): (List[(String, String)], List[(String, String)]) =
    (op.inputs.map(paramShape), op.outputs.map(paramShape))

  private def paramShape(p: ParamDecl): (String, String) = (p.name, typeShape(p.typeExpr))

  private def typeShape(t: TypeExpr): String = t match
    case TypeExpr.NamedType(n, _)           => n
    case TypeExpr.SetType(inner, _)         => s"Set[${typeShape(inner)}]"
    case TypeExpr.SeqType(inner, _)         => s"Seq[${typeShape(inner)}]"
    case TypeExpr.OptionType(inner, _)      => s"Option[${typeShape(inner)}]"
    case TypeExpr.MapType(k, v, _)          => s"Map[${typeShape(k)},${typeShape(v)}]"
    case TypeExpr.RelationType(f, m, t2, _) => s"${typeShape(f)}-$m->${typeShape(t2)}"

  private def normalizedRequires(rs: List[Expr]): List[String] =
    rs.flatMap(flattenAnd).filterNot(isLiteralTrue).map(exprShape).sorted

  private def flattenAnd(e: Expr): List[Expr] = e match
    case Expr.BinaryOp(BinOp.And, l, r, _) => flattenAnd(l) ++ flattenAnd(r)
    case other                             => List(other)

  private def isLiteralTrue(e: Expr): Boolean = e match
    case Expr.BoolLit(true, _) => true
    case _                     => false

  private def exprShape(e: Expr): String = e match
    case Expr.BinaryOp(op, l, r, _) =>
      s"(B$op ${exprShape(l)} ${exprShape(r)})"
    case Expr.UnaryOp(op, o, _) =>
      s"(U$op ${exprShape(o)})"
    case Expr.Quantifier(q, bs, body, _) =>
      val bindings = bs
        .map(b => s"${b.variable}/${b.bindingKind}/${exprShape(b.domain)}")
        .mkString(",")
      s"(Q$q[$bindings]${exprShape(body)})"
    case Expr.SomeWrap(inner, _) =>
      s"(some ${exprShape(inner)})"
    case Expr.The(v, d, body, _) =>
      s"(the $v ${exprShape(d)} ${exprShape(body)})"
    case Expr.FieldAccess(base, f, _) =>
      s"(. ${exprShape(base)} $f)"
    case Expr.EnumAccess(base, m, _) =>
      s"(:: ${exprShape(base)} $m)"
    case Expr.Index(base, idx, _) =>
      s"([] ${exprShape(base)} ${exprShape(idx)})"
    case Expr.Call(callee, args, _) =>
      s"(call ${exprShape(callee)} ${args.map(exprShape).mkString(",")})"
    case Expr.Prime(inner, _) =>
      s"(' ${exprShape(inner)})"
    case Expr.Pre(inner, _) =>
      s"(pre ${exprShape(inner)})"
    case Expr.With(base, ups, _) =>
      val u = ups.map(up => s"${up.name}=${exprShape(up.value)}").mkString(",")
      s"(with ${exprShape(base)} {$u})"
    case Expr.If(c, t, ee, _) =>
      s"(if ${exprShape(c)} ${exprShape(t)} ${exprShape(ee)})"
    case Expr.Let(v, va, body, _) =>
      s"(let $v ${exprShape(va)} ${exprShape(body)})"
    case Expr.Lambda(p, body, _) =>
      s"(\\$p.${exprShape(body)})"
    case Expr.Constructor(n, fs, _) =>
      val f = fs.map(fa => s"${fa.name}=${exprShape(fa.value)}").mkString(",")
      s"($n{$f})"
    case Expr.SetLiteral(es, _) =>
      s"{${es.map(exprShape).mkString(",")}}"
    case Expr.MapLiteral(es, _) =>
      s"{${es.map(en => s"${exprShape(en.key)}->${exprShape(en.value)}").mkString(",")}}"
    case Expr.SetComprehension(v, d, p, _) =>
      s"({$v in ${exprShape(d)} | ${exprShape(p)}})"
    case Expr.SeqLiteral(es, _) =>
      s"[${es.map(exprShape).mkString(",")}]"
    case Expr.Matches(inner, pat, _) =>
      s"(${exprShape(inner)} matches /$pat/)"
    case Expr.IntLit(v, _)     => v.toString
    case Expr.FloatLit(v, _)   => v.toString
    case Expr.StringLit(v, _)  => "\"" + v + "\""
    case Expr.BoolLit(v, _)    => v.toString
    case Expr.NoneLit(_)       => "none"
    case Expr.Identifier(n, _) => n
