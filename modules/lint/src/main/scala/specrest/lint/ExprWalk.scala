package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object ExprWalk:

  def foreach(expr: expr_full)(visit: expr_full => Unit): Unit =
    visit(expr)
    expr match
      case BinaryOpF(_, l, r, _) =>
        foreach(l)(visit); foreach(r)(visit)
      case UnaryOpF(_, op, _) =>
        foreach(op)(visit)
      case QuantifierF(_, bindings, body, _) =>
        bindings.foreach(b => foreach(b.domain)(visit))
        foreach(body)(visit)
      case SomeWrapF(e, _) =>
        foreach(e)(visit)
      case TheF(_, d, b, _) =>
        foreach(d)(visit); foreach(b)(visit)
      case FieldAccessF(base, _, _) =>
        foreach(base)(visit)
      case EnumAccessF(base, _, _) =>
        foreach(base)(visit)
      case IndexF(base, idx, _) =>
        foreach(base)(visit); foreach(idx)(visit)
      case CallF(callee, args, _) =>
        foreach(callee)(visit); args.foreach(foreach(_)(visit))
      case PrimeF(e, _) =>
        foreach(e)(visit)
      case PreF(e, _) =>
        foreach(e)(visit)
      case WithF(base, updates, _) =>
        foreach(base)(visit); updates.foreach(u => foreach(u.value)(visit))
      case IfF(c, t, e, _) =>
        foreach(c)(visit); foreach(t)(visit); foreach(e)(visit)
      case LetF(_, v, b, _) =>
        foreach(v)(visit); foreach(b)(visit)
      case LambdaF(_, b, _) =>
        foreach(b)(visit)
      case ConstructorF(_, fields, _) =>
        fields.foreach(f => foreach(f.value)(visit))
      case SetLiteralF(elems, _) =>
        elems.foreach(foreach(_)(visit))
      case MapLiteralF(entries, _) =>
        entries.foreach: e =>
          foreach(e.key)(visit); foreach(e.value)(visit)
      case SetComprehensionF(_, d, p, _) =>
        foreach(d)(visit); foreach(p)(visit)
      case SeqLiteralF(elems, _) =>
        elems.foreach(foreach(_)(visit))
      case MatchesF(e, _, _) =>
        foreach(e)(visit)
      case _: (IdentifierF | IntLitF | FloatLitF | StringLitF |
            BoolLitF | NoneLitF) =>
        ()
