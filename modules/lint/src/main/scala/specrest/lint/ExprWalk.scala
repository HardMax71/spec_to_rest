package specrest.lint

import specrest.ir.Expr

object ExprWalk:

  def foreach(expr: Expr)(visit: Expr => Unit): Unit =
    visit(expr)
    expr match
      case Expr.BinaryOp(_, l, r, _) =>
        foreach(l)(visit); foreach(r)(visit)
      case Expr.UnaryOp(_, op, _) =>
        foreach(op)(visit)
      case Expr.Quantifier(_, bindings, body, _) =>
        bindings.foreach(b => foreach(b.domain)(visit))
        foreach(body)(visit)
      case Expr.SomeWrap(e, _) =>
        foreach(e)(visit)
      case Expr.The(_, d, b, _) =>
        foreach(d)(visit); foreach(b)(visit)
      case Expr.FieldAccess(base, _, _) =>
        foreach(base)(visit)
      case Expr.EnumAccess(base, _, _) =>
        foreach(base)(visit)
      case Expr.Index(base, idx, _) =>
        foreach(base)(visit); foreach(idx)(visit)
      case Expr.Call(callee, args, _) =>
        foreach(callee)(visit); args.foreach(foreach(_)(visit))
      case Expr.Prime(e, _) =>
        foreach(e)(visit)
      case Expr.Pre(e, _) =>
        foreach(e)(visit)
      case Expr.With(base, updates, _) =>
        foreach(base)(visit); updates.foreach(u => foreach(u.value)(visit))
      case Expr.If(c, t, e, _) =>
        foreach(c)(visit); foreach(t)(visit); foreach(e)(visit)
      case Expr.Let(_, v, b, _) =>
        foreach(v)(visit); foreach(b)(visit)
      case Expr.Lambda(_, b, _) =>
        foreach(b)(visit)
      case Expr.Constructor(_, fields, _) =>
        fields.foreach(f => foreach(f.value)(visit))
      case Expr.SetLiteral(elems, _) =>
        elems.foreach(foreach(_)(visit))
      case Expr.MapLiteral(entries, _) =>
        entries.foreach: e =>
          foreach(e.key)(visit); foreach(e.value)(visit)
      case Expr.SetComprehension(_, d, p, _) =>
        foreach(d)(visit); foreach(p)(visit)
      case Expr.SeqLiteral(elems, _) =>
        elems.foreach(foreach(_)(visit))
      case Expr.Matches(e, _, _) =>
        foreach(e)(visit)
      case _: (Expr.Identifier | Expr.IntLit | Expr.FloatLit | Expr.StringLit |
            Expr.BoolLit | Expr.NoneLit) =>
        ()
