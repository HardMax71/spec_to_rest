package specrest.verify.certificates

import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.CheckOutcome
import specrest.verify.Consistency
import specrest.verify.VerificationConfig
import specrest.verify.z3.WasmBackend

import java.nio.file.Files
import java.nio.file.Paths

class UnsatCoreTest extends munit.FunSuite:

  test("Z3: --explain populates coreSpans on contradictory invariants"):
    val ir      = parseSpec("unsat_invariants")
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(
        ir,
        backend,
        VerificationConfig(timeoutMs = 30_000L, captureCore = true)
      )
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
    finally backend.close()

  test("Z3: --explain leaves coreSpans empty when captureCore=false"):
    val ir      = parseSpec("unsat_invariants")
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(
        ir,
        backend,
        VerificationConfig.Default
      )
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.diagnostic.map(_.coreSpans).getOrElse(Nil), Nil)
    finally backend.close()

  test("Alloy: --explain populates coreSpans on contradictory powerset spec"):
    val ir      = parseSpec("contradictory_powerset")
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(
        ir,
        backend,
        VerificationConfig(timeoutMs = 60_000L, captureCore = true)
      )
      val global = report.checks.find(_.id == "global").get
      assertEquals(global.status, CheckOutcome.Unsat)
      val core = global.diagnostic.map(_.coreSpans).getOrElse(Nil)
      assert(
        core.nonEmpty,
        "expected non-empty Alloy core spans for contradictory_powerset"
      )
      // core spans must reference real spec lines (the two invariants are on lines 10 and 13)
      val lines = core.map(_.span.startLine).toSet
      assert(
        lines.subsetOf(Set(10, 13)),
        s"core spans should land on invariant lines (10/13), got: $lines"
      )
    finally backend.close()

  test("Z3: --explain core for dead op points at requires clause"):
    val ir      = parseSpec("dead_op")
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(
        ir,
        backend,
        VerificationConfig(timeoutMs = 30_000L, captureCore = true)
      )
      val req = report.checks.find(_.id == "DeadOp.requires").get
      assertEquals(req.status, CheckOutcome.Unsat)
      val core = req.diagnostic.map(_.coreSpans).getOrElse(Nil)
      assert(core.nonEmpty, "expected non-empty core for DeadOp.requires")
      assertEquals(core.head.note, "contributing requires clause")
    finally backend.close()

  private def parseSpec(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree)
