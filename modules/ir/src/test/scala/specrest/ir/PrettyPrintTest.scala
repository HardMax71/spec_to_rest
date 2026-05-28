package specrest.ir

import specrest.ir.generated.SpecRestGenerated.*

class PrettyPrintTest extends munit.CatsEffectSuite:

  private def i(n: Int): expr_full        = IntLitF(BigInt(n), None)
  private def s(t: String): expr_full     = StringLitF(t, None)
  private def b(v: Boolean): expr_full    = BoolLitF(v, None)
  private def id(name: String): expr_full = IdentifierF(name, None)

  private val cases: List[(String, expr_full, String)] = List(
    ("int literal", i(42), "42"),
    ("string literal", s("ok"), "\"ok\""),
    ("string literal with quotes/backslashes/newline", s("a\"b\\c\nd"), "\"a\\\"b\\\\c\\nd\""),
    ("bool literal", b(true), "true"),
    ("identifier", id("count"), "count"),
    (
      "binary op",
      BinaryOpF(BGe(), id("clicks"), i(0), None),
      "(clicks >= 0)"
    ),
    (
      "unary not",
      UnaryOpF(UNot(), b(false), None),
      "(not false)"
    ),
    (
      "field access",
      FieldAccessF(id("user"), "email", None),
      "user.email"
    ),
    (
      "indexed access",
      IndexF(id("metadata"), id("c"), None),
      "metadata[c]"
    ),
    (
      "pre and prime",
      BinaryOpF(
        BEq(),
        PrimeF(id("clicks"), None),
        BinaryOpF(BSub(), PreF(id("clicks"), None), i(1), None),
        None
      ),
      "(clicks' = (pre(clicks) - 1))"
    ),
    (
      "forall over relation",
      QuantifierF(
        QAll(),
        List(QuantifierBindingFull("c", id("metadata"), BkIn(), None)),
        BinaryOpF(
          BGe(),
          FieldAccessF(IndexF(id("metadata"), id("c"), None), "click_count", None),
          i(0),
          None
        ),
        None
      ),
      "(all c in metadata | (metadata[c].click_count >= 0))"
    ),
    (
      "implies",
      BinaryOpF(BImplies(), id("p"), id("q"), None),
      "(p => q)"
    ),
    (
      "with-update",
      WithF(id("u"), List(FieldAssignFull("name", s("alice"), None)), None),
      "(u with { name = \"alice\" })"
    ),
    (
      "let",
      LetF("x", i(1), BinaryOpF(BAdd(), id("x"), i(2), None), None),
      "(let x = 1 in (x + 2))"
    ),
    (
      "set literal",
      SetLiteralF(List(i(1), i(2)), None),
      "{1, 2}"
    ),
    (
      "map literal",
      MapLiteralF(List(MapEntryFull(id("k"), i(7), None)), None),
      "{k -> 7}"
    ),
    (
      "set comprehension",
      SetComprehensionF(
        "x",
        id("S"),
        BinaryOpF(BGt(), id("x"), i(0), None),
        None
      ),
      "{ x in S | (x > 0) }"
    ),
    (
      "constructor",
      ConstructorF(
        "User",
        List(FieldAssignFull("id", i(1), None), FieldAssignFull("name", s("a"), None)),
        None
      ),
      "User { id = 1, name = \"a\" }"
    ),
    (
      "call",
      CallF(id("len"), List(id("s")), None),
      "len(s)"
    )
  )

  cases.foreach: (name, e, expected) =>
    test(s"PrettyPrint $name"):
      assertEquals(PrettyPrint.expr(e), expected)
