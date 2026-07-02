package specrest.verify.z3

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.SpanT
import specrest.verify.z3.Z3Expr.*

class Z3ExprWalkTest extends CatsEffectSuite:

  private val k = Var("k", Z3Sort.IntS)
  private val v = Var("v", Z3Sort.IntS)

  test("substitute replaces free occurrences everywhere in the tree"):
    IO:
      val e = And(List(
        Cmp(CmpOp.Eq, k, IntLit(BigInt(1))),
        App("store_dom", List(k)),
        MapLit(Z3Sort.IntS, Z3Sort.IntS, List((k, v)))
      ))
      val out = e.substitute("k", IntLit(BigInt(7)))
      assertEquals(
        out,
        And(List(
          Cmp(CmpOp.Eq, IntLit(BigInt(7)), IntLit(BigInt(1))),
          App("store_dom", List(IntLit(BigInt(7)))),
          MapLit(Z3Sort.IntS, Z3Sort.IntS, List((IntLit(BigInt(7)), v)))
        ))
      )

  test("substitute stops at a quantifier that rebinds the name"):
    IO:
      val shadowing =
        Quantifier(QKind.ForAll, List(Z3Binding("k", Z3Sort.IntS)), App("p", List(k)))
      assertEquals(shadowing.substitute("k", IntLit(BigInt(7))), shadowing)
      val open =
        Quantifier(QKind.ForAll, List(Z3Binding("x", Z3Sort.IntS)), App("p", List(k)))
      assertEquals(
        open.substitute("k", IntLit(BigInt(7))),
        Quantifier(
          QKind.ForAll,
          List(Z3Binding("x", Z3Sort.IntS)),
          App("p", List(IntLit(BigInt(7))))
        )
      )

  test("children pairs up MapLit entries and keeps regexes out of InRe"):
    IO:
      val m = MapLit(Z3Sort.IntS, Z3Sort.IntS, List((k, v)))
      assertEquals(m.children, List(k, v))
      val re = InRe(Var("s", Z3Sort.Str), Z3Regex.AnyChar)
      assertEquals(re.children, List(Var("s", Z3Sort.Str)))

  test("mapChildren returns leaves unchanged and preserves spans on nodes"):
    IO:
      val leaf = StrLit("x")
      assertEquals(leaf.mapChildren(_ => IntLit(BigInt(0))), leaf)
      val span = SpanT(BigInt(1), BigInt(2), BigInt(3), BigInt(4))
      val node = Not(k, Some(span))
      val out  = node.mapChildren(_ => v)
      assertEquals(out, Not(v, Some(span)))

  test("substitute renames a binder that would capture the replacement's free variable"):
    IO:
      val x = Var("x", Z3Sort.IntS)
      val q = Quantifier(
        QKind.ForAll,
        List(Z3Binding("x", Z3Sort.IntS)),
        And(List(App("p", List(k)), App("q", List(x))))
      )
      assertEquals(
        q.substitute("k", x),
        Quantifier(
          QKind.ForAll,
          List(Z3Binding("x_1", Z3Sort.IntS)),
          And(List(App("p", List(x)), App("q", List(Var("x_1", Z3Sort.IntS)))))
        )
      )

  test("freeVars excludes quantifier-bound names"):
    IO:
      val q = Quantifier(
        QKind.ForAll,
        List(Z3Binding("x", Z3Sort.IntS)),
        And(List(App("p", List(Var("x", Z3Sort.IntS))), App("q", List(k))))
      )
      assertEquals(q.freeVars, Set("k"))
