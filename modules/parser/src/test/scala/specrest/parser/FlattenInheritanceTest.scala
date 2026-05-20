package specrest.parser

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.EntityDeclFull
import specrest.ir.generated.SpecRestGenerated.IdentifierF
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.expr_full

class FlattenInheritanceTest extends CatsEffectSuite:

  // Characterizes the extracted SpecRest.IR.flattenInheritance. It is sound
  // for a single application (parser.Builder.buildIRCore applies it exactly
  // once) but is NOT idempotent: the parent ref is retained and entity
  // invariants are concatenated, so re-applying it duplicates inherited
  // invariants. Hardening (clearing the parent ref post-flatten) is a
  // deliberate, separate behaviour change — it would alter lint.UnusedEntity
  // reachability (UnusedEntity.scala does `parent.foreach(acc += _)`) and the
  // IR-JSON `extends_` field (Serialize.scala). This test locks the current
  // behaviour so any such change is reviewed, not silent. The positive
  // characterization (identity on parent-less services) is proven in
  // proofs/isabelle/SpecRest/IR.thy: flattenInheritance_id_on_parentless.

  private def flat(s: ServiceIRFull): ServiceIRFull =
    SpecRestGenerated.flattenInheritance(s) match
      case sv: ServiceIRFull => sv

  private def childInvs(s: ServiceIRFull): List[expr_full] =
    s.c.collect { case EntityDeclFull("C", _, _, invs, _) => invs }.head

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
    assertEquals(once.map(_.toString).count(_.contains("p_inv")), 1)
    assertEquals(once.map(_.toString).count(_.contains("c_inv")), 1)

  test("flattenInheritance is NOT idempotent — re-application duplicates inherited invariants"):
    val twice = childInvs(flat(flat(ir)))
    assertEquals(twice.length, 3)
    assertEquals(twice.map(_.toString).count(_.contains("p_inv")), 2)
