package specrest.verify.cert.generated

import munit.FunSuite
import specrest.ir.generated.SpecRestGenerated as G

class SpecRestGeneratedTest extends FunSuite:

  test("extracted translate handles atom expressions"):
    assertEquals(G.translate(G.BoolLit(true, None)), G.BLit(true))
    assertEquals(G.translate(G.BoolLit(false, None)), G.BLit(false))
    assertEquals(G.translate(G.Ident("x", None)), G.TVar("x"))

  test("extracted translate handles unary"):
    val inner = G.BoolLit(true, None)
    assertEquals(G.translate(G.UnNot(inner, None)), G.TNot(G.BLit(true)))

  test("extracted translate handles boolean binary"):
    val l = G.BoolLit(true, None)
    val r = G.BoolLit(false, None)
    assertEquals(
      G.translate(G.BoolBin(G.AndOp(), l, r, None)),
      G.TAnd(G.BLit(true), G.BLit(false))
    )

  test("extracted translate handles GeOp via TLt + TEq composition"):
    val l        = G.Ident("a", None)
    val r        = G.Ident("b", None)
    val emitted  = G.translate(G.Cmp(G.GeOp(), l, r, None))
    val expected = G.TOr(
      G.TLt(G.TVar("b"), G.TVar("a")),
      G.TEq(G.TVar("a"), G.TVar("b"))
    )
    assertEquals(emitted, expected)

  test("extracted translate handles set ops"):
    val l = G.Ident("s1", None)
    val r = G.Ident("s2", None)
    assertEquals(
      G.translate(G.SetBin(G.UnionOp(), l, r, None)),
      G.TSetUnion(G.TVar("s1"), G.TVar("s2"))
    )

  test("extracted translate covers all 23 expr cases (smoke)"):
    val probes: List[G.expr] = List(
      G.BoolLit(true, None),
      G.IntLit(BigInt(0), None),
      G.Ident("x", None),
      G.UnNot(G.BoolLit(true, None), None),
      G.UnNeg(G.IntLit(BigInt(0), None), None),
      G.BoolBin(G.AndOp(), G.BoolLit(true, None), G.BoolLit(false, None), None),
      G.Arith(
        G.AddOp(),
        G.IntLit(BigInt(1), None),
        G.IntLit(BigInt(2), None),
        None
      ),
      G.Cmp(
        G.LtOp(),
        G.IntLit(BigInt(1), None),
        G.IntLit(BigInt(2), None),
        None
      ),
      G.LetIn("x", G.IntLit(BigInt(0), None), G.Ident("x", None), None),
      G.EnumAccess("Color", "Red", None),
      G.Member(G.Ident("u", None), "users", None),
      G.ForallEnum("c", "Color", G.BoolLit(true, None), None),
      G.ForallRel("u", "users", G.BoolLit(true, None), None),
      G.Prime(G.Ident("x", None), None),
      G.Pre(G.Ident("x", None), None),
      G.CardRel("users", None),
      G.IndexRel(G.Ident("users", None), G.Ident("uid", None), None),
      G.FieldAccess(G.Ident("u", None), "email", None),
      G.SetEmpty(None),
      G.SetInsert(G.Ident("v", None), G.SetEmpty(None), None),
      G.SetMember(G.Ident("v", None), G.SetEmpty(None), None),
      G.SetBin(G.IntersectOp(), G.Ident("a", None), G.Ident("b", None), None),
      G.WithRec(G.Ident("u", None), "name", G.Ident("v", None), None)
    )
    probes.foreach: e =>
      val translated = G.translate(e)
      assert(translated.toString.nonEmpty, s"translate returned empty toString on $e")
