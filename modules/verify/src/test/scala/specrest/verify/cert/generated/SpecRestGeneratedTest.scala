package specrest.verify.cert.generated

import munit.FunSuite
import specrest.ir.generated.SpecRestGenerated as G

class SpecRestGeneratedTest extends FunSuite:

  private val enums: List[String] = List("Color")

  test("extracted translate handles atom expressions"):
    assertEquals(G.translate(enums, G.BoolLitF(true, None)), Some(G.BLit(true)))
    assertEquals(G.translate(enums, G.BoolLitF(false, None)), Some(G.BLit(false)))
    assertEquals(G.translate(enums, G.IdentifierF("x", None)), Some(G.TVar("x")))

  test("extracted translate handles unary"):
    val inner = G.BoolLitF(true, None)
    assertEquals(
      G.translate(enums, G.UnaryOpF(G.UNot(), inner, None)),
      Some(G.TNot(G.BLit(true)))
    )

  test("extracted translate handles boolean binary"):
    val l = G.BoolLitF(true, None)
    val r = G.BoolLitF(false, None)
    assertEquals(
      G.translate(enums, G.BinaryOpF(G.BAnd(), l, r, None)),
      Some(G.TAnd(G.BLit(true), G.BLit(false)))
    )

  test("extracted translate handles BGe via TLt + TEq composition"):
    val l       = G.IdentifierF("a", None)
    val r       = G.IdentifierF("b", None)
    val emitted = G.translate(enums, G.BinaryOpF(G.BGe(), l, r, None))
    val expected = G.TOr(
      G.TLt(G.TVar("b"), G.TVar("a")),
      G.TEq(G.TVar("a"), G.TVar("b"))
    )
    assertEquals(emitted, Some(expected))

  test("extracted translate handles set ops"):
    val l = G.IdentifierF("s1", None)
    val r = G.IdentifierF("s2", None)
    assertEquals(
      G.translate(enums, G.BinaryOpF(G.BUnion(), l, r, None)),
      Some(G.TSetUnion(G.TVar("s1"), G.TVar("s2")))
    )

  test("extracted translate covers the surface constructs (smoke)"):
    val probes: List[G.expr] = List(
      G.BoolLitF(true, None),
      G.IntLitF(BigInt(0), None),
      G.IdentifierF("x", None),
      G.UnaryOpF(G.UNot(), G.BoolLitF(true, None), None),
      G.UnaryOpF(G.UNegate(), G.IntLitF(BigInt(0), None), None),
      G.BinaryOpF(G.BAnd(), G.BoolLitF(true, None), G.BoolLitF(false, None), None),
      G.BinaryOpF(G.BAdd(), G.IntLitF(BigInt(1), None), G.IntLitF(BigInt(2), None), None),
      G.BinaryOpF(G.BLt(), G.IntLitF(BigInt(1), None), G.IntLitF(BigInt(2), None), None),
      G.LetF("x", G.IntLitF(BigInt(0), None), G.IdentifierF("x", None), None),
      G.EnumAccessF(G.IdentifierF("Color", None), "Red", None),
      G.BinaryOpF(G.BIn(), G.IdentifierF("u", None), G.IdentifierF("users", None), None),
      G.QuantifierF(
        G.QAll(),
        List(G.QuantifierBindingFull("c", G.IdentifierF("Color", None), G.BkIn(), None)),
        G.BoolLitF(true, None),
        None
      ),
      G.QuantifierF(
        G.QAll(),
        List(G.QuantifierBindingFull("u", G.IdentifierF("users", None), G.BkIn(), None)),
        G.BoolLitF(true, None),
        None
      ),
      G.PrimeF(G.IdentifierF("x", None), None),
      G.PreF(G.IdentifierF("x", None), None),
      G.UnaryOpF(G.UCardinality(), G.IdentifierF("users", None), None),
      G.IndexF(G.IdentifierF("users", None), G.IdentifierF("uid", None), None),
      G.FieldAccessF(G.IdentifierF("u", None), "email", None),
      G.SetLiteralF(Nil, None),
      G.SetLiteralF(List(G.IdentifierF("v", None)), None),
      G.BinaryOpF(G.BIntersect(), G.IdentifierF("a", None), G.IdentifierF("b", None), None),
      G.WithF(
        G.IdentifierF("u", None),
        List(G.FieldAssignFull("name", G.IdentifierF("v", None), None)),
        None
      )
    )
    probes.foreach: e =>
      G.translate(enums, e) match
        case Some(t) => assert(t.toString.nonEmpty, s"empty toString on $e")
        case None    => fail(s"translate punted on in-subset probe $e")
