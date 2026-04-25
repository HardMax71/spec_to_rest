package specrest.ir

class PrettyPrintTest extends munit.CatsEffectSuite:

  private val cases: List[(String, Expr, String)] = List(
    ("int literal", Expr.IntLit(42), "42"),
    ("string literal", Expr.StringLit("ok"), "\"ok\""),
    (
      "string literal with quotes/backslashes/newline",
      Expr.StringLit("a\"b\\c\nd"),
      "\"a\\\"b\\\\c\\nd\""
    ),
    ("bool literal", Expr.BoolLit(true), "true"),
    ("identifier", Expr.Identifier("count"), "count"),
    (
      "binary op",
      Expr.BinaryOp(BinOp.Ge, Expr.Identifier("clicks"), Expr.IntLit(0)),
      "(clicks >= 0)"
    ),
    (
      "unary not",
      Expr.UnaryOp(UnOp.Not, Expr.BoolLit(false)),
      "(not false)"
    ),
    (
      "field access",
      Expr.FieldAccess(Expr.Identifier("user"), "email"),
      "user.email"
    ),
    (
      "indexed access",
      Expr.Index(Expr.Identifier("metadata"), Expr.Identifier("c")),
      "metadata[c]"
    ),
    (
      "pre and prime",
      Expr.BinaryOp(
        BinOp.Eq,
        Expr.Prime(Expr.Identifier("clicks")),
        Expr.BinaryOp(BinOp.Sub, Expr.Pre(Expr.Identifier("clicks")), Expr.IntLit(1))
      ),
      "(clicks' = (pre(clicks) - 1))"
    ),
    (
      "forall over relation",
      Expr.Quantifier(
        QuantKind.All,
        List(QuantifierBinding("c", Expr.Identifier("metadata"), BindingKind.In)),
        Expr.BinaryOp(
          BinOp.Ge,
          Expr.FieldAccess(
            Expr.Index(Expr.Identifier("metadata"), Expr.Identifier("c")),
            "click_count"
          ),
          Expr.IntLit(0)
        )
      ),
      "(all c in metadata | (metadata[c].click_count >= 0))"
    ),
    (
      "implies",
      Expr.BinaryOp(BinOp.Implies, Expr.Identifier("p"), Expr.Identifier("q")),
      "(p => q)"
    ),
    (
      "with-update",
      Expr.With(
        Expr.Identifier("u"),
        List(FieldAssign("name", Expr.StringLit("alice")))
      ),
      "(u with { name = \"alice\" })"
    ),
    (
      "let",
      Expr.Let("x", Expr.IntLit(1), Expr.BinaryOp(BinOp.Add, Expr.Identifier("x"), Expr.IntLit(2))),
      "(let x = 1 in (x + 2))"
    ),
    (
      "set literal",
      Expr.SetLiteral(List(Expr.IntLit(1), Expr.IntLit(2))),
      "{1, 2}"
    ),
    (
      "map literal",
      Expr.MapLiteral(List(MapEntry(Expr.Identifier("k"), Expr.IntLit(7)))),
      "{k -> 7}"
    ),
    (
      "set comprehension",
      Expr.SetComprehension(
        "x",
        Expr.Identifier("S"),
        Expr.BinaryOp(BinOp.Gt, Expr.Identifier("x"), Expr.IntLit(0))
      ),
      "{ x in S | (x > 0) }"
    ),
    (
      "constructor",
      Expr.Constructor(
        "User",
        List(FieldAssign("id", Expr.IntLit(1)), FieldAssign("name", Expr.StringLit("a")))
      ),
      "User { id = 1, name = \"a\" }"
    ),
    (
      "call",
      Expr.Call(Expr.Identifier("len"), List(Expr.Identifier("s"))),
      "len(s)"
    )
  )

  cases.foreach: (name, e, expected) =>
    test(s"PrettyPrint $name"):
      assertEquals(PrettyPrint.expr(e), expected)
