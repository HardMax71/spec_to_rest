package specrest.verify

import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.z3.WasmBackend

import java.nio.file.Files
import java.nio.file.Paths

class ConsistencyTest extends munit.FunSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree).toOption.get

  test("url_shortener passes all consistency checks"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("url_shortener")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      assert(
        report.ok,
        s"expected ok=true; failing checks: ${report.checks.filter(c =>
            c.status != CheckOutcome.Sat && c.status != CheckOutcome.Skipped
          ).map(_.id)}"
      )
    finally backend.close()

  test("unsat_invariants has contradictory_invariants diagnostic"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("unsat_invariants")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      assert(!report.ok)
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.status, CheckOutcome.Unsat)
      assertEquals(
        global.diagnostic.map(_.category),
        Some(DiagnosticCategory.ContradictoryInvariants)
      )
    finally backend.close()

  test("dead_op detects unsatisfiable_precondition"):
    val backend = WasmBackend()
    try
      val ir      = buildIR("dead_op")
      val report  = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val deadReq = report.checks.find(_.id == "DeadOp.requires")
      assert(deadReq.isDefined, s"missing DeadOp.requires in checks: ${report.checks.map(_.id)}")
      assertEquals(deadReq.get.status, CheckOutcome.Unsat)
      assertEquals(
        deadReq.get.diagnostic.map(_.category),
        Some(DiagnosticCategory.UnsatisfiablePrecondition)
      )
    finally backend.close()

  test("unreachable_op detects unreachable_operation"):
    val backend = WasmBackend()
    try
      val ir          = buildIR("unreachable_op")
      val report      = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val unreachable = report.checks.find(_.id == "UnreachableOp.enabled")
      assert(unreachable.isDefined)
      assertEquals(unreachable.get.status, CheckOutcome.Unsat)
      assertEquals(
        unreachable.get.diagnostic.map(_.category),
        Some(DiagnosticCategory.UnreachableOperation)
      )
    finally backend.close()

  test("broken_url_shortener detects invariant violation"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("broken_url_shortener")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val violations = report.checks.filter(c =>
        c.kind == CheckKind.Preservation && c.status == CheckOutcome.Unsat
      )
      assert(violations.nonEmpty, "expected at least one preservation violation")
      violations.foreach: v =>
        assertEquals(
          v.diagnostic.map(_.category),
          Some(DiagnosticCategory.InvariantViolationByOperation)
        )
    finally backend.close()

  test("safe_counter — every check passes"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("safe_counter")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      assert(
        report.ok,
        s"safe_counter should be fully consistent; failing: ${report.checks.filter(_.status != CheckOutcome.Sat).map(c => s"${c.id}->${c.status}")}"
      )
    finally backend.close()

  test("set_ops — every check passes and nothing is skipped"):
    val backend = WasmBackend()
    try
      val ir      = buildIR("set_ops")
      val report  = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val skipped = report.checks.filter(_.status == CheckOutcome.Skipped)
      assert(
        skipped.isEmpty,
        s"set_ops should have no skipped checks; skipped: ${skipped.map(_.id)}"
      )
      assert(
        report.ok,
        s"set_ops should be fully consistent; failing: ${report.checks.filter(_.status != CheckOutcome.Sat).map(c => s"${c.id}->${c.status}")}"
      )
    finally backend.close()

  test("set_comp_demo — `s = { x in D | P }` equality passes in Z3"):
    val backend = WasmBackend()
    try
      val ir      = buildIR("set_comp_demo")
      val report  = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
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
    finally backend.close()

  test("powerset_demo — global invariant routes to Alloy and solves sat"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("powerset_demo")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
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
    finally backend.close()

  test("temporal_demo — always/eventually temporal properties route to Alloy and pass"):
    val backend = WasmBackend()
    try
      val ir             = buildIR("temporal_demo")
      val report         = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
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
    finally backend.close()

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
    val parsed = specrest.parser.Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir      = specrest.parser.Builder.buildIR(parsed.tree).toOption.get
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.tool, VerifierTool.Alloy)
      assertEquals(
        temporal.status,
        CheckOutcome.Unsat,
        s"expected unreachable; got ${temporal.status} (${temporal.detail})"
      )
    finally backend.close()

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
    val parsed = specrest.parser.Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir      = specrest.parser.Builder.buildIR(parsed.tree).toOption.get
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.tool, VerifierTool.Alloy)
      assertEquals(
        temporal.status,
        CheckOutcome.Unsat,
        s"expected violation; got ${temporal.status}"
      )
    finally backend.close()

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
    val parsed = specrest.parser.Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir      = specrest.parser.Builder.buildIR(parsed.tree).toOption.get
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val temporal = report.checks.find(_.kind == CheckKind.Temporal)
        .getOrElse(fail("no temporal check"))
      assertEquals(temporal.status, CheckOutcome.Skipped)
      assert(
        temporal.diagnostic.exists(_.message.contains("fairness")),
        s"expected fairness error; got: ${temporal.diagnostic.map(_.message)}"
      )
    finally backend.close()

  test("powerset_ops — Alloy-routed requires/enabled/preservation all solve"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("powerset_ops")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
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
    finally backend.close()
