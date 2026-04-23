package specrest.verify

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.alloy.AlloyBackend
import specrest.verify.z3.WasmBackend

import java.nio.file.Files
import java.nio.file.Paths
import scala.concurrent.duration.*

class TimeoutTest extends CatsEffectSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpecSync(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIRSync(parsed.tree).toOption.get

  List(1, 4).foreach: maxPar =>
    test(s"tight timeout on set_ops (parallel=$maxPar) yields only Unknown/Skipped outcomes"):
      val ir  = buildIR("set_ops")
      val cfg = VerificationConfig(timeoutMs = 1L, maxParallel = maxPar)
      Consistency.runConsistencyChecks(ir, cfg).map: report =>
        val bad = report.checks.filter: c =>
          c.status != CheckOutcome.Unknown && c.status != CheckOutcome.Skipped
        assert(
          bad.isEmpty,
          s"unexpected non-timeout outcomes: ${bad.map(c => s"${c.id}=${c.status}").mkString(", ")}"
        )
        assert(
          report.checks.exists(_.status == CheckOutcome.Unknown),
          "no Unknown outcomes — outer timeout fallback did not fire on any check"
        )
        assertEquals(report.ok, false)

  test("serial and parallel produce identical outcomes under tight timeout"):
    val ir   = buildIR("set_ops")
    val cfgS = VerificationConfig(timeoutMs = 1L, maxParallel = 1)
    val cfgP = VerificationConfig(timeoutMs = 1L, maxParallel = 4)
    for
      serial   <- Consistency.runConsistencyChecks(ir, cfgS)
      parallel <- Consistency.runConsistencyChecks(ir, cfgP)
    yield
      assertEquals(
        serial.checks.map(c => c.id -> c.status),
        parallel.checks.map(c => c.id -> c.status)
      )
      assertEquals(serial.ok, parallel.ok)

  test("timeoutMs=0 disables outer timeout (safe_counter parity vs 30s default)"):
    val ir      = buildIR("safe_counter")
    val cfgZero = VerificationConfig(timeoutMs = 0L, maxParallel = 1)
    val cfgDef  = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    for
      zero <- Consistency.runConsistencyChecks(ir, cfgZero)
      dflt <- Consistency.runConsistencyChecks(ir, cfgDef)
    yield
      assertEquals(
        zero.checks.map(c => c.id -> c.status),
        dflt.checks.map(c => c.id -> c.status)
      )
      assertEquals(zero.ok, dflt.ok)

  test("Resource release fires when the using IO is timed out"):
    Ref[IO].of(0).flatMap: released =>
      val wasmRes = WasmBackend
        .make(IO.blocking(WasmBackend())): backend =>
          released.update(_ + 1) >> IO.blocking(backend.close())
      val alloyRes = AlloyBackend
        .make(IO.blocking(new AlloyBackend)): backend =>
          released.update(_ + 1) >> IO.blocking(backend.close())
      val combined = wasmRes.flatMap(w => alloyRes.map(a => (w, a)))
      combined.use(_ => IO.sleep(10.seconds)).timeoutTo(50.millis, IO.unit) >>
        released.get.map: count =>
          assertEquals(count, 2, "both backend Resources should have released after timeout")
