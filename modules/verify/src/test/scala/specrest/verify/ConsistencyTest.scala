package specrest.verify

import java.nio.file.{Files, Paths}
import specrest.parser.{Builder, Parse}

class ConsistencyTest extends munit.FunSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree)

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
