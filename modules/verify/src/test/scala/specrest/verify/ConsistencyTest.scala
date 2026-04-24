package specrest.verify

import munit.CatsEffectSuite
import specrest.verify.testutil.SpecFixtures

class ConsistencyTest extends CatsEffectSuite:

  test("url_shortener passes all consistency checks"):
    for
      ir     <- SpecFixtures.loadIR("url_shortener")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield assert(
      report.ok,
      s"expected ok=true; failing checks: ${report.checks.filter(c =>
          c.status != CheckOutcome.Sat && c.status != CheckOutcome.Skipped
        ).map(_.id)}"
    )

  test("unsat_invariants has contradictory_invariants diagnostic"):
    for
      ir     <- SpecFixtures.loadIR("unsat_invariants")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      assert(!report.ok)
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.status, CheckOutcome.Unsat)
      assertEquals(
        global.diagnostic.map(_.category),
        Some(DiagnosticCategory.ContradictoryInvariants)
      )

  test("dead_op detects unsatisfiable_precondition"):
    for
      ir     <- SpecFixtures.loadIR("dead_op")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val deadReq = report.checks.find(_.id == "DeadOp.requires")
      assert(deadReq.isDefined, s"missing DeadOp.requires in checks: ${report.checks.map(_.id)}")
      assertEquals(deadReq.get.status, CheckOutcome.Unsat)
      assertEquals(
        deadReq.get.diagnostic.map(_.category),
        Some(DiagnosticCategory.UnsatisfiablePrecondition)
      )

  test("unreachable_op detects unreachable_operation"):
    for
      ir     <- SpecFixtures.loadIR("unreachable_op")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val unreachable = report.checks.find(_.id == "UnreachableOp.enabled")
      assert(unreachable.isDefined)
      assertEquals(unreachable.get.status, CheckOutcome.Unsat)
      assertEquals(
        unreachable.get.diagnostic.map(_.category),
        Some(DiagnosticCategory.UnreachableOperation)
      )

  test("broken_url_shortener detects invariant violation"):
    for
      ir     <- SpecFixtures.loadIR("broken_url_shortener")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val violations = report.checks.filter(c =>
        c.kind == CheckKind.Preservation && c.status == CheckOutcome.Unsat
      )
      assert(violations.nonEmpty, "expected at least one preservation violation")
      violations.foreach: v =>
        assertEquals(
          v.diagnostic.map(_.category),
          Some(DiagnosticCategory.InvariantViolationByOperation)
        )

  test("safe_counter — every check passes"):
    for
      ir     <- SpecFixtures.loadIR("safe_counter")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield assert(
      report.ok,
      s"safe_counter should be fully consistent; failing: ${report.checks.filter(_.status != CheckOutcome.Sat).map(c => s"${c.id}->${c.status}")}"
    )

  test("set_ops — every check passes and nothing is skipped"):
    for
      ir     <- SpecFixtures.loadIR("set_ops")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val skipped = report.checks.filter(_.status == CheckOutcome.Skipped)
      assert(
        skipped.isEmpty,
        s"set_ops should have no skipped checks; skipped: ${skipped.map(_.id)}"
      )
      assert(
        report.ok,
        s"set_ops should be fully consistent; failing: ${report.checks.filter(_.status != CheckOutcome.Sat).map(c => s"${c.id}->${c.status}")}"
      )

  test("set_comp_demo — `s = { x in D | P }` equality passes in Z3"):
    for
      ir     <- SpecFixtures.loadIR("set_comp_demo")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val skipped = report.checks.filter(_.status == CheckOutcome.Skipped)
      assert(
        skipped.isEmpty,
        s"set_comp_demo should have no skipped checks; skipped: ${skipped.map(_.id)}"
      )
      val nonZ3 = report.checks.filter(_.tool != VerifierTool.Z3)
      assert(
        nonZ3.isEmpty,
        s"set_comp_demo should route entirely to Z3; non-Z3: ${nonZ3.map(c => s"${c.id}->${c.tool}")}"
      )
      assert(report.ok, s"set_comp_demo should be sat; got: ${report.checks.map(_.status)}")

  test("powerset_demo — global invariant routes to Alloy and solves sat"):
    for
      ir     <- SpecFixtures.loadIR("powerset_demo")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val global = report.checks.find(_.id == "global").getOrElse(fail("no global check"))
      assertEquals(
        global.tool,
        VerifierTool.Alloy,
        s"global should route to Alloy; got ${global.tool}"
      )
      assertEquals(global.status, CheckOutcome.Sat, s"global should be sat; got ${global.status}")
      assert(
        report.ok,
        s"powerset_demo should pass; got: ${report.checks.map(c => s"${c.id}->${c.status}")}"
      )

  test("temporal_demo — always/eventually temporal properties route to Alloy and pass"):
    for
      ir     <- SpecFixtures.loadIR("temporal_demo")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporalChecks = report.checks.filter(_.kind == CheckKind.Temporal)
      assertEquals(
        temporalChecks.size,
        2,
        s"expected 2 temporal checks; got ${temporalChecks.map(_.id)}"
      )
      assert(
        temporalChecks.forall(_.tool == VerifierTool.Alloy),
        s"expected all Alloy-routed; got ${temporalChecks.map(c => s"${c.id}->${c.tool}")}"
      )
      assert(
        temporalChecks.forall(_.status == CheckOutcome.Sat),
        s"expected all sat; got ${temporalChecks.map(c => s"${c.id}->${c.status}")}"
      )
      assert(report.ok)

  test("unreachable `eventually(...)` returns Unsat under contradictory invariants"):
    val spec =
      """service BrokenTemporal {
        |  entity User {
        |  }
        |  state {
        |    users: Set[User]
        |  }
        |  invariant noUsers:
        |    all u in users | u != u
        |  temporal someUserExists:
        |    eventually(some u in users | u = u)
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("BrokenTemporal", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.tool, VerifierTool.Alloy)
      assertEquals(
        temporal.status,
        CheckOutcome.Unsat,
        s"expected unreachable; got ${temporal.status} (${temporal.detail})"
      )

  test("violated `always(...)` returns Unsat when P can be falsified"):
    val spec =
      """service BrokenAlways {
        |  entity User {}
        |  state {
        |    users: Set[User]
        |  }
        |  temporal alwaysFalse:
        |    always(all u in users | u != u)
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("BrokenAlways", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.tool, VerifierTool.Alloy)
      assertEquals(
        temporal.status,
        CheckOutcome.Unsat,
        s"expected violation; got ${temporal.status}"
      )

  test("fairness(...) raises an Alloy translator error surfaced as Skipped"):
    val spec =
      """service FairnessSpec {
        |  operation Step {
        |    requires: true
        |    ensures: true
        |  }
        |  temporal fairStep:
        |    fairness(Step)
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("FairnessSpec", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.status, CheckOutcome.Skipped)
      assert(
        temporal.diagnostic.exists(_.message.contains("fairness")),
        s"expected fairness error; got: ${temporal.diagnostic.map(_.message)}"
      )

  test("powerset_ops — Alloy-routed requires/enabled/preservation all solve"):
    for
      ir     <- SpecFixtures.loadIR("powerset_ops")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val reqCheck = report.checks.find(_.id == "AddUser.requires")
        .getOrElse(fail("no AddUser.requires check"))
      assertEquals(reqCheck.tool, VerifierTool.Alloy)
      assertEquals(reqCheck.status, CheckOutcome.Sat)
      val enCheck = report.checks.find(_.id == "AddUser.enabled")
        .getOrElse(fail("no AddUser.enabled check"))
      assertEquals(enCheck.tool, VerifierTool.Alloy)
      assertEquals(enCheck.status, CheckOutcome.Sat)
      val presCheck = report.checks.find(_.id == "AddUser.preserves.allValid")
        .getOrElse(fail("no AddUser.preserves.allValid check"))
      assertEquals(presCheck.tool, VerifierTool.Alloy)
      assertEquals(
        presCheck.status,
        CheckOutcome.Sat,
        s"preservation should be preserved; got ${presCheck.status}"
      )
      assert(
        report.ok,
        s"powerset_ops should pass; got: ${report.checks.map(c => s"${c.id}->${c.tool}->${c.status}")}"
      )
