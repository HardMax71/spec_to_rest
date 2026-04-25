package specrest.ir

object PrettyPrint:

  def expr(e: Expr): String = render(e)

  private def render(e: Expr): String = e match
    case Expr.IntLit(v, _)   => v.toString
    case Expr.FloatLit(v, _) => v.toString
    case Expr.StringLit(v, _) =>
      val escaped = v.flatMap:
        case '\\' => "\\\\"
        case '"'  => "\\\""
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c    => c.toString
      "\"" + escaped + "\""
    case Expr.BoolLit(v, _)         => v.toString
    case Expr.NoneLit(_)            => "none"
    case Expr.Identifier(n, _)      => n
    case Expr.BinaryOp(op, l, r, _) => s"(${render(l)} ${binOpToken(op)} ${render(r)})"
    case Expr.UnaryOp(op, x, _)     => s"(${unOpToken(op)} ${render(x)})"
    case Expr.FieldAccess(b, f, _)  => s"${render(b)}.$f"
    case Expr.EnumAccess(b, m, _)   => s"${render(b)}.$m"
    case Expr.Index(b, i, _)        => s"${render(b)}[${render(i)}]"
    case Expr.Call(c, args, _)      => s"${render(c)}(${args.map(render).mkString(", ")})"
    case Expr.Prime(x, _)           => s"${render(x)}'"
    case Expr.Pre(x, _)             => s"pre(${render(x)})"
    case Expr.SomeWrap(x, _)        => s"some(${render(x)})"
    case Expr.Quantifier(q, bs, body, _) =>
      val qs = quantToken(q)
      val bindings = bs.map(b =>
        val sep = b.bindingKind match
          case BindingKind.In    => " in "
          case BindingKind.Colon => ": "
        s"${b.variable}$sep${render(b.domain)}"
      ).mkString(", ")
      s"($qs $bindings | ${render(body)})"
    case Expr.The(v, d, b, _) => s"(the $v in ${render(d)} | ${render(b)})"
    case Expr.With(b, ups, _) =>
      val parts = ups.map(u => s"${u.name} = ${render(u.value)}").mkString(", ")
      s"(${render(b)} with { $parts })"
    case Expr.If(c, t, e, _) =>
      s"(if ${render(c)} then ${render(t)} else ${render(e)})"
    case Expr.Let(v, value, body, _) =>
      s"(let $v = ${render(value)} in ${render(body)})"
    case Expr.Lambda(p, b, _) => s"(\\$p -> ${render(b)})"
    case Expr.Constructor(name, fs, _) =>
      val parts = fs.map(f => s"${f.name} = ${render(f.value)}").mkString(", ")
      s"$name { $parts }"
    case Expr.SetLiteral(es, _) => es.map(render).mkString("{", ", ", "}")
    case Expr.MapLiteral(es, _) =>
      es.map(en => s"${render(en.key)} -> ${render(en.value)}").mkString("{", ", ", "}")
    case Expr.SetComprehension(v, d, p, _) =>
      s"{ $v in ${render(d)} | ${render(p)} }"
    case Expr.SeqLiteral(es, _) => es.map(render).mkString("[", ", ", "]")
    case Expr.Matches(x, p, _)  => s"(${render(x)} matches /$p/)"

  private def binOpToken(op: BinOp): String = op match
    case BinOp.And       => "and"
    case BinOp.Or        => "or"
    case BinOp.Implies   => "=>"
    case BinOp.Iff       => "<=>"
    case BinOp.Eq        => "="
    case BinOp.Neq       => "!="
    case BinOp.Lt        => "<"
    case BinOp.Gt        => ">"
    case BinOp.Le        => "<="
    case BinOp.Ge        => ">="
    case BinOp.In        => "in"
    case BinOp.NotIn     => "not in"
    case BinOp.Subset    => "subset"
    case BinOp.Union     => "++"
    case BinOp.Intersect => "&"
    case BinOp.Diff      => "--"
    case BinOp.Add       => "+"
    case BinOp.Sub       => "-"
    case BinOp.Mul       => "*"
    case BinOp.Div       => "/"

  private def unOpToken(op: UnOp): String = op match
    case UnOp.Not         => "not"
    case UnOp.Negate      => "-"
    case UnOp.Cardinality => "#"
    case UnOp.Power       => "^"

  private def quantToken(q: QuantKind): String = q match
    case QuantKind.All    => "all"
    case QuantKind.Some   => "some"
    case QuantKind.No     => "no"
    case QuantKind.Exists => "exists"
