package specrest.verify.audit

import specrest.ir.*

object CanonicalProbes:

  val allProbes: List[(String, Expr)] = List(
    "BoolLit"             -> Expr.BoolLit(true),
    "IntLit"              -> Expr.IntLit(42),
    "Identifier"          -> Expr.Identifier("x"),
    "UnaryOp.Not"         -> Expr.UnaryOp(UnOp.Not, Expr.BoolLit(true)),
    "UnaryOp.Negate"      -> Expr.UnaryOp(UnOp.Negate, Expr.IntLit(1)),
    "UnaryOp.Cardinality" -> Expr.UnaryOp(UnOp.Cardinality, Expr.Identifier("rel")),
    "UnaryOp.Power"       -> Expr.UnaryOp(UnOp.Power, Expr.SetLiteral(List(Expr.IntLit(1)))),
    "BinaryOp.And"        -> binBoolOp(BinOp.And),
    "BinaryOp.Or"         -> binBoolOp(BinOp.Or),
    "BinaryOp.Implies"    -> binBoolOp(BinOp.Implies),
    "BinaryOp.Iff"        -> binBoolOp(BinOp.Iff),
    "BinaryOp.Eq"         -> binIntOp(BinOp.Eq),
    "BinaryOp.Neq"        -> binIntOp(BinOp.Neq),
    "BinaryOp.Lt"         -> binIntOp(BinOp.Lt),
    "BinaryOp.Le"         -> binIntOp(BinOp.Le),
    "BinaryOp.Gt"         -> binIntOp(BinOp.Gt),
    "BinaryOp.Ge"         -> binIntOp(BinOp.Ge),
    "BinaryOp.In" ->
      Expr.BinaryOp(BinOp.In, Expr.Identifier("v"), Expr.Identifier("rel")),
    "BinaryOp.NotIn" ->
      Expr.BinaryOp(BinOp.NotIn, Expr.Identifier("v"), Expr.Identifier("rel")),
    "BinaryOp.Subset"    -> setOp(BinOp.Subset),
    "BinaryOp.Union"     -> setOp(BinOp.Union),
    "BinaryOp.Intersect" -> setOp(BinOp.Intersect),
    "BinaryOp.Diff"      -> setOp(BinOp.Diff),
    "BinaryOp.Add"       -> binIntOp(BinOp.Add),
    "BinaryOp.Sub"       -> binIntOp(BinOp.Sub),
    "BinaryOp.Mul"       -> binIntOp(BinOp.Mul),
    "BinaryOp.Div"       -> binIntOp(BinOp.Div),
    "Quantifier.All"     -> quantifier(QuantKind.All),
    "Quantifier.Some"    -> quantifier(QuantKind.Some),
    "Quantifier.No"      -> quantifier(QuantKind.No),
    "Quantifier.Exists"  -> quantifier(QuantKind.Exists),
    "Let"                -> Expr.Let("x", Expr.IntLit(1), Expr.Identifier("x")),
    "EnumAccess"         -> Expr.EnumAccess(Expr.Identifier("Color"), "red"),
    "Prime"              -> Expr.Prime(Expr.Identifier("count")),
    "Pre"                -> Expr.Pre(Expr.Identifier("count")),
    "FieldAccess"        -> Expr.FieldAccess(Expr.Identifier("u"), "name"),
    "Index"              -> Expr.Index(Expr.Identifier("arr"), Expr.IntLit(0)),
    "If"                 -> Expr.If(Expr.BoolLit(true), Expr.IntLit(1), Expr.IntLit(2)),
    "Lambda"             -> Expr.Lambda("x", Expr.Identifier("x")),
    "SomeWrap"           -> Expr.SomeWrap(Expr.IntLit(1)),
    "The"                -> Expr.The("x", Expr.Identifier("dom"), Expr.BoolLit(true)),
    "Call"               -> Expr.Call(Expr.Identifier("len"), List(Expr.Identifier("s"))),
    "With"               -> Expr.With(Expr.Identifier("u"), List(FieldAssign("name", Expr.IntLit(0)))),
    "Constructor"        -> Expr.Constructor("User", List(FieldAssign("id", Expr.IntLit(0)))),
    "SetLiteral"         -> Expr.SetLiteral(List(Expr.IntLit(1))),
    "MapLiteral"         -> Expr.MapLiteral(List(MapEntry(Expr.IntLit(1), Expr.IntLit(2)))),
    "SetComprehension"   -> Expr.SetComprehension("x", Expr.Identifier("dom"), Expr.BoolLit(true)),
    "SeqLiteral"         -> Expr.SeqLiteral(List(Expr.IntLit(1))),
    "Matches"            -> Expr.Matches(Expr.Identifier("s"), ".*"),
    "FloatLit"           -> Expr.FloatLit(1.0),
    "StringLit"          -> Expr.StringLit("s"),
    "NoneLit"            -> Expr.NoneLit()
  )

  private def binBoolOp(op: BinOp): Expr =
    Expr.BinaryOp(op, Expr.BoolLit(true), Expr.BoolLit(false))

  private def binIntOp(op: BinOp): Expr =
    Expr.BinaryOp(op, Expr.IntLit(1), Expr.IntLit(2))

  private def setOp(op: BinOp): Expr =
    Expr.BinaryOp(op, Expr.Identifier("a"), Expr.Identifier("b"))

  private def quantifier(kind: QuantKind): Expr =
    Expr.Quantifier(
      kind,
      List(QuantifierBinding("c", Expr.Identifier("Color"), BindingKind.In)),
      Expr.BoolLit(true)
    )
