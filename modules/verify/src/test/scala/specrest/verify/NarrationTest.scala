package specrest.verify

import munit.CatsEffectSuite
import specrest.verify.testutil.SpecFixtures

class NarrationTest extends CatsEffectSuite:

  private val violationCases: List[(String, DiagnosticCategory, List[String])] = List(
    (
      "broken_url_shortener",
      DiagnosticCategory.InvariantViolationByOperation,
      List("clickCountNonNegative", "click_count", "Tamper")
    ),
    (
      "broken_decrement",
      DiagnosticCategory.InvariantViolationByOperation,
      List("clicksNonNegative", "clicks", "Decrement")
    )
  )

  violationCases.foreach: (fixture, category, mustContain) =>
    test(s"narration for $fixture mentions invariant + field + operation"):
      val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true)
      for
        ir     <- SpecFixtures.loadIR(fixture)
        report <- Consistency.runConsistencyChecks(ir, cfg)
      yield
        val violation = report.checks
          .find(c => c.diagnostic.exists(_.category == category))
          .getOrElse(fail(s"no $category check found for $fixture"))
        val narrative = violation.diagnostic.flatMap(_.narrative)
          .getOrElse(fail(s"narration empty for $fixture"))
        assert(narrative.contains("Why this violates"), narrative)
        mustContain.foreach: needle =>
          assert(narrative.contains(needle), s"narration missing '$needle': $narrative")

  test("narration for unsat_invariants names contributing invariants"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true)
    for
      ir     <- SpecFixtures.loadIR("unsat_invariants")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val global = report.checks.find(_.id == "global").getOrElse(fail("no global check"))
      val narrative = global.diagnostic.flatMap(_.narrative)
        .getOrElse(fail("expected narration on global check"))
      assert(narrative.contains("Why these invariants conflict"), narrative)
      assert(
        narrative.contains("inv_") || narrative.contains("invariant"),
        narrative
      )

  test("narration for dead_op (UnsatisfiablePrecondition) is empty (deferred per #89)"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true)
    for
      ir     <- SpecFixtures.loadIR("dead_op")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val deadReq = report.checks
        .find(c =>
          c.diagnostic.exists(_.category == DiagnosticCategory.UnsatisfiablePrecondition)
        )
        .getOrElse(fail("no UnsatisfiablePrecondition check found"))
      assert(
        deadReq.diagnostic.flatMap(_.narrative).isEmpty,
        s"expected no narrative; got ${deadReq.diagnostic.flatMap(_.narrative)}"
      )

  test("--no-narration suppresses narrative on a violating spec"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true, narration = false)
    for
      ir     <- SpecFixtures.loadIR("broken_url_shortener")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val violation = report.checks
        .find(c =>
          c.diagnostic.exists(_.category == DiagnosticCategory.InvariantViolationByOperation)
        )
        .getOrElse(fail("no preservation violation found"))
      assertEquals(violation.diagnostic.flatMap(_.narrative), None)
