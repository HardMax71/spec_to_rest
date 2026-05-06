package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object OperationOverlap extends LintPass:
  val code = "L04"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val out    = List.newBuilder[LintDiagnostic]
    val ops0   = ir.g.collect { case o: OperationDeclFull => o }
    val groups = ops0.groupBy(opSignature).filter(_._2.length >= 2)
    for (_, ops) <- groups do
      val sorted = ops.sortBy(_.a)
      for i <- sorted.indices; j <- (i + 1) until sorted.length do
        val a    = sorted(i)
        val b    = sorted(j)
        val nrA  = normalizedRequires(a.d)
        val nrB  = normalizedRequires(b.d)
        val same = nrA.length == nrB.length && nrA.zip(nrB).forall((x, y) => x == y)
        if same then
          val rel = a.f.toList.map(s => RelatedSpan(s, s"first definition of '${a.a}'"))
          val phrase =
            if nrA.isEmpty then "neither has preconditions" else "they share the same preconditions"
          out += LintDiagnostic(
            code,
            LintLevel.Warning,
            s"operations '${a.a}' and '${b.a}' have the same input/output signature and $phrase — dispatch is ambiguous on shared inputs",
            b.f,
            rel
          )
    out.result()

  private def opSignature(op: OperationDeclFull): (List[(String, String)], List[(String, String)]) =
    (
      op.b.collect { case p: ParamDeclFull => paramShape(p) },
      op.c.collect { case p: ParamDeclFull => paramShape(p) }
    )

  private def paramShape(p: ParamDeclFull): (String, String) = (p.a, typeShape(p.b))

  private def typeShape(t: type_expr_full): String = t match
    case NamedTypeF(n, _)           => n
    case SetTypeF(inner, _)         => s"Set[${typeShape(inner)}]"
    case SeqTypeF(inner, _)         => s"Seq[${typeShape(inner)}]"
    case OptionTypeF(inner, _)      => s"Option[${typeShape(inner)}]"
    case MapTypeF(k, v, _)          => s"Map[${typeShape(k)},${typeShape(v)}]"
    case RelationTypeF(f, m, t2, _) => s"${typeShape(f)}-$m->${typeShape(t2)}"

  private def normalizedRequires(rs: List[expr_full]): List[String] =
    rs.flatMap(flattenAnd).filterNot(isLiteralTrue).map(exprShape).sorted

  private def flattenAnd(e: expr_full): List[expr_full] = e match
    case BinaryOpF(BAnd(), l, r, _) => flattenAnd(l) ++ flattenAnd(r)
    case other                      => List(other)

  private def isLiteralTrue(e: expr_full): Boolean = e match
    case BoolLitF(true, _) => true
    case _                 => false

  private def exprShape(e: expr_full): String = e match
    case BinaryOpF(op, l, r, _) =>
      s"(B$op ${exprShape(l)} ${exprShape(r)})"
    case UnaryOpF(op, o, _) =>
      s"(U$op ${exprShape(o)})"
    case QuantifierF(q, bs, body, _) =>
      val bindings = bs
        .map { case QuantifierBindingFull(v, dom, kind, _) =>
          s"$v/$kind/${exprShape(dom)}"
        }
        .mkString(",")
      s"(Q$q[$bindings]${exprShape(body)})"
    case SomeWrapF(inner, _) =>
      s"(some ${exprShape(inner)})"
    case TheF(v, d, body, _) =>
      s"(the $v ${exprShape(d)} ${exprShape(body)})"
    case FieldAccessF(base, f, _) =>
      s"(. ${exprShape(base)} $f)"
    case EnumAccessF(base, m, _) =>
      s"(:: ${exprShape(base)} $m)"
    case IndexF(base, idx, _) =>
      s"([] ${exprShape(base)} ${exprShape(idx)})"
    case CallF(callee, args, _) =>
      s"(call ${exprShape(callee)} ${args.map(exprShape).mkString(",")})"
    case PrimeF(inner, _) =>
      s"(' ${exprShape(inner)})"
    case PreF(inner, _) =>
      s"(pre ${exprShape(inner)})"
    case WithF(base, ups, _) =>
      val u = ups.map { case FieldAssignFull(n, v, _) => s"$n=${exprShape(v)}" }.mkString(",")
      s"(with ${exprShape(base)} {$u})"
    case IfF(c, t, ee, _) =>
      s"(if ${exprShape(c)} ${exprShape(t)} ${exprShape(ee)})"
    case LetF(v, va, body, _) =>
      s"(let $v ${exprShape(va)} ${exprShape(body)})"
    case LambdaF(p, body, _) =>
      s"(\\$p.${exprShape(body)})"
    case ConstructorF(n, fs, _) =>
      val f = fs.map { case FieldAssignFull(an, av, _) => s"$an=${exprShape(av)}" }.mkString(",")
      s"($n{$f})"
    case SetLiteralF(es, _) =>
      s"{${es.map(exprShape).mkString(",")}}"
    case MapLiteralF(es, _) =>
      s"{${es.map { case MapEntryFull(k, v, _) => s"${exprShape(k)}->${exprShape(v)}" }.mkString(",")}}"
    case SetComprehensionF(v, d, p, _) =>
      s"({$v in ${exprShape(d)} | ${exprShape(p)}})"
    case SeqLiteralF(es, _) =>
      s"[${es.map(exprShape).mkString(",")}]"
    case MatchesF(inner, pat, _) =>
      s"(${exprShape(inner)} matches /$pat/)"
    case IntLitF(int_of_integer(v), _) => v.toString
    case FloatLitF(v, _)               => v.toString
    case StringLitF(v, _)              => "\"" + v + "\""
    case BoolLitF(v, _)                => v.toString
    case NoneLitF(_)                   => "none"
    case IdentifierF(n, _)             => n
