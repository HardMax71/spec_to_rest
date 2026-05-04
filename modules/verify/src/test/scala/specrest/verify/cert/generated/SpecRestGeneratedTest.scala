package specrest.verify.cert.generated

import munit.FunSuite
import specrest.verify.cert.generated.SpecRestGenerated as G

class SpecRestGeneratedTest extends FunSuite:

  test("extracted translate handles atom expressions"):
    assertEquals(G.translate(G.BoolLit(true)), G.BLit(true))
    assertEquals(G.translate(G.BoolLit(false)), G.BLit(false))
    assertEquals(G.translate(G.Ident("x")), G.TVar("x"))

  test("extracted translate handles unary"):
    val inner = G.BoolLit(true)
    assertEquals(G.translate(G.UnNot(inner)), G.TNot(G.BLit(true)))

  test("extracted translate handles boolean binary"):
    val l = G.BoolLit(true)
    val r = G.BoolLit(false)
    assertEquals(
      G.translate(G.BoolBin(G.AndOp(), l, r)),
      G.TAnd(G.BLit(true), G.BLit(false))
    )

  test("extracted translate handles GeOp via TLt + TEq composition"):
    val l       = G.Ident("a")
    val r       = G.Ident("b")
    val emitted = G.translate(G.Cmp(G.GeOp(), l, r))
    val expected = G.TOr(
      G.TLt(G.TVar("b"), G.TVar("a")),
      G.TEq(G.TVar("a"), G.TVar("b"))
    )
    assertEquals(emitted, expected)

  test("extracted translate handles set ops"):
    val l = G.Ident("s1")
    val r = G.Ident("s2")
    assertEquals(
      G.translate(G.SetBin(G.UnionOp(), l, r)),
      G.TSetUnion(G.TVar("s1"), G.TVar("s2"))
    )

  test("extracted translate covers all 23 expr cases (smoke)"):
    val probes: List[G.expr] = List(
      G.BoolLit(true),
      G.IntLit(G.int_of_integer(BigInt(0))),
      G.Ident("x"),
      G.UnNot(G.BoolLit(true)),
      G.UnNeg(G.IntLit(G.int_of_integer(BigInt(0)))),
      G.BoolBin(G.AndOp(), G.BoolLit(true), G.BoolLit(false)),
      G.Arith(
        G.AddOp(),
        G.IntLit(G.int_of_integer(BigInt(1))),
        G.IntLit(G.int_of_integer(BigInt(2)))
      ),
      G.Cmp(G.LtOp(), G.IntLit(G.int_of_integer(BigInt(1))), G.IntLit(G.int_of_integer(BigInt(2)))),
      G.LetIn("x", G.IntLit(G.int_of_integer(BigInt(0))), G.Ident("x")),
      G.EnumAccess("Color", "Red"),
      G.Member(G.Ident("u"), "users"),
      G.ForallEnum("c", "Color", G.BoolLit(true)),
      G.ForallRel("u", "users", G.BoolLit(true)),
      G.Prime(G.Ident("x")),
      G.Pre(G.Ident("x")),
      G.CardRel("users"),
      G.IndexRel("users", G.Ident("uid")),
      G.FieldAccess(G.Ident("u"), "email"),
      G.SetEmpty(),
      G.SetInsert(G.Ident("v"), G.SetEmpty()),
      G.SetMember(G.Ident("v"), G.SetEmpty()),
      G.SetBin(G.IntersectOp(), G.Ident("a"), G.Ident("b")),
      G.WithRec(G.Ident("u"), "name", G.Ident("v"))
    )
    probes.foreach: e =>
      val translated = G.translate(e)
      assert(translated.toString.nonEmpty, s"translate returned empty toString on $e")
