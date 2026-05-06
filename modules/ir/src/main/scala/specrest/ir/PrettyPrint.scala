package specrest.ir

import specrest.ir.generated.SpecRestGenerated.*

object PrettyPrint:

  def expr(e: expr_full): String = render(e)

  private def render(e: expr_full): String = e match
    case IntLitF(v, _) =>
      v match
        case int_of_integer(b) => b.toString
    case FloatLitF(v, _) => v
    case StringLitF(v, _) =>
      val escaped = v.flatMap:
        case '\\' => "\\\\"
        case '"'  => "\\\""
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c    => c.toString
      "\"" + escaped + "\""
    case BoolLitF(v, _)         => v.toString
    case NoneLitF(_)            => "none"
    case IdentifierF(n, _)      => n
    case BinaryOpF(op, l, r, _) => s"(${render(l)} ${binOpToken(op)} ${render(r)})"
    case UnaryOpF(op, x, _)     => s"(${unOpToken(op)} ${render(x)})"
    case FieldAccessF(b, f, _)  => s"${render(b)}.$f"
    case EnumAccessF(b, m, _)   => s"${render(b)}.$m"
    case IndexF(b, i, _)        => s"${render(b)}[${render(i)}]"
    case CallF(c, args, _)      => s"${render(c)}(${args.map(render).mkString(", ")})"
    case PrimeF(x, _)           => s"${render(x)}'"
    case PreF(x, _)             => s"pre(${render(x)})"
    case SomeWrapF(x, _)        => s"some(${render(x)})"
    case QuantifierF(q, bs, body, _) =>
      val qs = quantToken(q)
      val bindings = bs.map:
        case QuantifierBindingFull(v, dom, kind, _) =>
          val sep = kind match
            case _: BkIn    => " in "
            case _: BkColon => ": "
          s"$v$sep${render(dom)}"
      .mkString(", ")
      s"($qs $bindings | ${render(body)})"
    case TheF(v, d, b, _) => s"(the $v in ${render(d)} | ${render(b)})"
    case WithF(b, ups, _) =>
      val parts = ups.map:
        case FieldAssignFull(n, v, _) => s"$n = ${render(v)}"
      .mkString(", ")
      s"(${render(b)} with { $parts })"
    case IfF(c, t, e, _) =>
      s"(if ${render(c)} then ${render(t)} else ${render(e)})"
    case LetF(v, value, body, _) =>
      s"(let $v = ${render(value)} in ${render(body)})"
    case LambdaF(p, b, _) => s"(\\$p -> ${render(b)})"
    case ConstructorF(name, fs, _) =>
      val parts = fs.map:
        case FieldAssignFull(n, v, _) => s"$n = ${render(v)}"
      .mkString(", ")
      s"$name { $parts }"
    case SetLiteralF(es, _) => es.map(render).mkString("{", ", ", "}")
    case MapLiteralF(es, _) =>
      es.map:
        case MapEntryFull(k, v, _) => s"${render(k)} -> ${render(v)}"
      .mkString("{", ", ", "}")
    case SetComprehensionF(v, d, p, _) =>
      s"{ $v in ${render(d)} | ${render(p)} }"
    case SeqLiteralF(es, _) => es.map(render).mkString("[", ", ", "]")
    case MatchesF(x, p, _)  => s"(${render(x)} matches /$p/)"

  private def binOpToken(op: bin_op_full): String = op match
    case _: BAnd       => "and"
    case _: BOr        => "or"
    case _: BImplies   => "=>"
    case _: BIff       => "<=>"
    case _: BEq        => "="
    case _: BNeq       => "!="
    case _: BLt        => "<"
    case _: BGt        => ">"
    case _: BLe        => "<="
    case _: BGe        => ">="
    case _: BIn        => "in"
    case _: BNotIn     => "not in"
    case _: BSubset    => "subset"
    case _: BUnion     => "++"
    case _: BIntersect => "&"
    case _: BDiff      => "--"
    case _: BAdd       => "+"
    case _: BSub       => "-"
    case _: BMul       => "*"
    case _: BDiv       => "/"

  private def unOpToken(op: un_op_full): String = op match
    case _: UNot         => "not"
    case _: UNegate      => "-"
    case _: UCardinality => "#"
    case _: UPower       => "^"

  private def quantToken(q: quant_kind_full): String = q match
    case _: QAll    => "all"
    case _: QSome   => "some"
    case _: QNo     => "no"
    case _: QExists => "exists"
