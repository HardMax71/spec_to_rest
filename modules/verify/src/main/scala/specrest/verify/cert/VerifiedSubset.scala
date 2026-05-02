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
        case UnOp.Cardinality       =>
          // `#rel` is renderable only when the operand is a state-relation identifier —
          // Translator.scala:876-881 enforces the same restriction. We optimistically
          // admit any `Expr.Identifier` here without threading schema/state into
          // `classify`. If the identifier is actually a scalar (or undefined), both
          // `EvalIR.eval` and Lean's `eval (.cardRel name)` return `none`, and the cert
          // vacuously claims `eval = none` — soundness holds trivially. Tightening to
          // schema-aware classification would require threading `ServiceIR` through
          // every `classify` call site (M_L.4-followup).
          operand match
            case _: Expr.Identifier => SubsetStatus.InSubset
            case _ =>
              SubsetStatus.OutOfSubset(
                "UnaryOp(Cardinality): operand must be a state-relation identifier"
              )
        case other => SubsetStatus.OutOfSubset(s"UnaryOp.$other not in M_L.1 verified subset")
    case Expr.BinaryOp(op, l, r, _) =>
      op match
        case BinOp.And | BinOp.Or | BinOp.Implies | BinOp.Iff |
            BinOp.Eq | BinOp.Neq |
            BinOp.Lt | BinOp.Le | BinOp.Gt | BinOp.Ge |
            BinOp.Add | BinOp.Sub | BinOp.Mul | BinOp.Div =>
          chooseWorse(classify(l), classify(r))
        case BinOp.In | BinOp.NotIn =>
          // BinaryOp(In/NotIn) is renderable only when the rhs is an `Identifier`
          // — M_L.1 ties `In` to state-relation domain membership and the
          // emitter renders `.member elem relName` with a literal name. NotIn is
          // emitted as `(.unNot (.member elem relName))` (M_L.4.e composition).
          r match
            case _: Expr.Identifier => chooseWorse(classify(l), classify(r))
            case _ =>
              SubsetStatus.OutOfSubset(
                s"BinaryOp($op): rhs must be a state-relation identifier"
              )
        case other =>
          SubsetStatus.OutOfSubset(s"BinaryOp.$other not in M_L.1 verified subset")
    case Expr.Quantifier(kind, bindings, body, _) =>
      // ∃, No, Exists are encoded as compositions of `forallEnum/forallRel + unNot` at emit time:
      //   ∃ x, P  ≡  ¬ ∀ x, ¬ P
      //   No x, P ≡  ∀ x, ¬ P
      //   Exists  alias of ∃.
      // All four kinds share the same single-binding-over-identifier restriction; the
      // identifier may resolve to either an enum (forallEnum) or a state-relation
      // (forallRel) — Emit.scala disambiguates via ServiceIR.enums.
      kind match
        case QuantKind.All | QuantKind.Some | QuantKind.No | QuantKind.Exists =>
          bindings match
            case List(QuantifierBinding(_, Expr.Identifier(_, _), _, _)) =>
              val bodyStatus = classify(body)
              val bindStatus = bindings.foldLeft[SubsetStatus](SubsetStatus.InSubset): (acc, b) =>
                chooseWorse(acc, classify(b.domain))
              chooseWorse(bindStatus, bodyStatus)
            case _ =>
              SubsetStatus.OutOfSubset(
                s"Quantifier($kind): only single-binding over an enum or relation identifier is supported"
              )
    case Expr.Let(_, value, body, _) =>
      chooseWorse(classify(value), classify(body))
    case Expr.EnumAccess(Expr.Identifier(_, _), _, _) => SubsetStatus.InSubset
    case _: Expr.EnumAccess =>
      SubsetStatus.OutOfSubset(
        "EnumAccess: only `EnumName.member` (Identifier base) is supported"
      )
    case Expr.Prime(inner, _) => classify(inner)
    case Expr.Pre(inner, _)   => classify(inner)
    case _: Expr.With         => SubsetStatus.OutOfSubset("With: M_L.2 territory")
    case _: Expr.FieldAccess  => SubsetStatus.OutOfSubset("FieldAccess: M_L.2 territory")
    case _: Expr.Index        => SubsetStatus.OutOfSubset("Index: M_L.2 territory")
    case _: Expr.Call         => SubsetStatus.OutOfSubset("Call (builtins): later expansion")
    case _: Expr.If           => SubsetStatus.OutOfSubset("If: deferred")
    case _: Expr.Lambda       => SubsetStatus.OutOfSubset("Lambda: outside FOL")
    case _: Expr.Constructor  => SubsetStatus.OutOfSubset("Constructor: deferred")
    case _: Expr.SetLiteral   => SubsetStatus.OutOfSubset("SetLiteral: collections deferred")
    case _: Expr.MapLiteral   => SubsetStatus.OutOfSubset("MapLiteral: collections deferred")
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
