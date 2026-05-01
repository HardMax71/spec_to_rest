package specrest.verify.cert

import specrest.ir.*

object VerifiedSubset:

  enum SubsetStatus derives CanEqual:
    case InSubset
    case OutOfSubset(reason: String)

  def classify(expr: Expr): SubsetStatus = expr match
    case _: Expr.BoolLit    => SubsetStatus.InSubset
    case _: Expr.IntLit     => SubsetStatus.InSubset
    case _: Expr.Identifier => SubsetStatus.InSubset
    case Expr.UnaryOp(op, operand, _) =>
      op match
        case UnOp.Not | UnOp.Negate => classify(operand)
        case other                  => SubsetStatus.OutOfSubset(s"UnaryOp.$other not in M_L.1 verified subset")
    case Expr.BinaryOp(op, l, r, _) =>
      op match
        case BinOp.And | BinOp.Or | BinOp.Implies | BinOp.Iff |
            BinOp.Eq | BinOp.Neq |
            BinOp.Lt | BinOp.Le | BinOp.Gt | BinOp.Ge |
            BinOp.In =>
          chooseWorse(classify(l), classify(r))
        case other =>
          SubsetStatus.OutOfSubset(s"BinaryOp.$other not in M_L.1 verified subset")
    case Expr.Quantifier(QuantKind.All, bindings, body, _) =>
      val bodyStatus = classify(body)
      val bindStatus =
        bindings.foldLeft[SubsetStatus](SubsetStatus.InSubset): (acc, b) =>
          chooseWorse(acc, classify(b.domain))
      chooseWorse(bindStatus, bodyStatus)
    case _: Expr.Quantifier =>
      SubsetStatus.OutOfSubset("Quantifier(Some|No|Exists) not in M_L.1 verified subset")
    case Expr.Let(_, value, body, _) =>
      chooseWorse(classify(value), classify(body))
    case _: Expr.EnumAccess  => SubsetStatus.InSubset
    case _: Expr.Prime       => SubsetStatus.OutOfSubset("Prime: M_L.2 territory")
    case _: Expr.Pre         => SubsetStatus.OutOfSubset("Pre: M_L.2 territory")
    case _: Expr.With        => SubsetStatus.OutOfSubset("With: M_L.2 territory")
    case _: Expr.FieldAccess => SubsetStatus.OutOfSubset("FieldAccess: M_L.2 territory")
    case _: Expr.Index       => SubsetStatus.OutOfSubset("Index: M_L.2 territory")
    case _: Expr.Call        => SubsetStatus.OutOfSubset("Call (builtins): later expansion")
    case _: Expr.If          => SubsetStatus.OutOfSubset("If: deferred")
    case _: Expr.Lambda      => SubsetStatus.OutOfSubset("Lambda: outside FOL")
    case _: Expr.Constructor => SubsetStatus.OutOfSubset("Constructor: deferred")
    case _: Expr.SetLiteral  => SubsetStatus.OutOfSubset("SetLiteral: collections deferred")
    case _: Expr.MapLiteral  => SubsetStatus.OutOfSubset("MapLiteral: collections deferred")
    case _: Expr.SetComprehension =>
      SubsetStatus.OutOfSubset("SetComprehension: collections deferred")
    case _: Expr.SeqLiteral => SubsetStatus.OutOfSubset("SeqLiteral: collections deferred")
    case _: Expr.Matches    => SubsetStatus.OutOfSubset("Matches (regex): deferred")
    case _: Expr.SomeWrap   => SubsetStatus.OutOfSubset("SomeWrap: option semantics deferred")
    case _: Expr.The        => SubsetStatus.OutOfSubset("The: choice operator deferred")
    case _: Expr.NoneLit    => SubsetStatus.OutOfSubset("NoneLit: option semantics deferred")
    case _: Expr.FloatLit   => SubsetStatus.OutOfSubset("FloatLit: no committed solver semantics")
    case _: Expr.StringLit =>
      SubsetStatus.OutOfSubset("StringLit: deferred (no regex/string theory)")

  private def chooseWorse(a: SubsetStatus, b: SubsetStatus): SubsetStatus = (a, b) match
    case (SubsetStatus.InSubset, x)             => x
    case (x, SubsetStatus.InSubset)             => x
    case (out @ SubsetStatus.OutOfSubset(_), _) => out

  def isInSubset(expr: Expr): Boolean = classify(expr) match
    case SubsetStatus.InSubset       => true
    case _: SubsetStatus.OutOfSubset => false
