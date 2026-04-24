package specrest.verify.certificates

import munit.CatsEffectSuite
import specrest.verify.CheckOutcome
import specrest.verify.Consistency
import specrest.verify.VerificationConfig
import specrest.verify.testutil.SpecFixtures

class UnsatCoreTest extends CatsEffectSuite:

  test("Z3: --explain populates coreSpans on contradictory invariants"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true)
    for
      ir     <- SpecFixtures.loadIR("unsat_invariants")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.status, CheckOutcome.Unsat)
      val core = global.diagnostic.map(_.coreSpans).getOrElse(Nil)
      assert(core.nonEmpty, "expected non-empty core spans for unsat invariants")
      core.foreach: cs =>
        assertEquals(
          cs.note,
          "contributing invariant",
          s"unexpected core note: ${cs.note}"
        )
        assert(cs.span.startLine >= 1, s"invalid span line: ${cs.span}")

  test("Z3: --explain leaves coreSpans empty when captureCore=false"):
    for
      ir     <- SpecFixtures.loadIR("unsat_invariants")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.diagnostic.map(_.coreSpans).getOrElse(Nil), Nil)

  test("Alloy: --explain populates coreSpans on contradictory powerset spec"):
    val cfg = VerificationConfig(timeoutMs = 60_000L, captureCore = true)
    for
      ir     <- SpecFixtures.loadIR("contradictory_powerset")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.status, CheckOutcome.Unsat)
      val core = global.diagnostic.map(_.coreSpans).getOrElse(Nil)
      assert(
        core.nonEmpty,
        "expected non-empty Alloy core spans for contradictory_powerset"
      )
      val lines = core.map(_.span.startLine).toSet
      assert(
        lines.subsetOf(Set(10, 13)),
        s"core spans should land on invariant lines (10/13), got: $lines"
      )

  test("Z3: --explain core for dead op points at requires clause"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true)
    for
      ir     <- SpecFixtures.loadIR("dead_op")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val req = report.checks.find(_.id == "DeadOp.requires").get
      assertEquals(req.status, CheckOutcome.Unsat)
      val core = req.diagnostic.map(_.coreSpans).getOrElse(Nil)
      assert(core.nonEmpty, "expected non-empty core for DeadOp.requires")
      assertEquals(core.head.note, "contributing requires clause")
