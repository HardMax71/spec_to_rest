package specrest.parser

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class FlattenInheritanceTest extends CatsEffectSuite:

  private def flat(s: ServiceIRFull): ServiceIRFull =
    flattenInheritance(s) match
      case sv: ServiceIRFull => sv

  private def childInvs(s: ServiceIRFull): List[expr_full] =
    svcEntities(s).filter(e => entName(e) == "C").map(entInvariants).head

  private def identCount(invs: List[expr_full], name: String): Int =
    invs.count { case IdentifierF(n, _) => n == name; case _ => false }

  private val base  = EntityDeclFull("P", None, Nil, List(IdentifierF("p_inv", None)), None)
  private val child = EntityDeclFull("C", Some("P"), Nil, List(IdentifierF("c_inv", None)), None)
  private val ir =
    ServiceIRFull(
      "S",
      Nil,
      List(base, child),
      Nil,
      Nil,
      None,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      Nil,
      None,
      None
    )

  test("flattenInheritance merges ancestor invariants root-first then own (one pass)"):
    val once = childInvs(flat(ir))
    assertEquals(once.length, 2)
    assertEquals(identCount(once, "p_inv"), 1)
    assertEquals(identCount(once, "c_inv"), 1)

  test("flattenInheritance is NOT idempotent — re-application duplicates inherited invariants"):
    val twice = childInvs(flat(flat(ir)))
    assertEquals(twice.length, 3)
    assertEquals(identCount(twice, "p_inv"), 2)
