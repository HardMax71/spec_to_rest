package specrest.verify

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import specrest.verify.alloy.AlloyBackend
import specrest.verify.testutil.SpecFixtures
import specrest.verify.z3.WasmBackend

import scala.concurrent.duration.*

class TimeoutTest extends CatsEffectSuite:

  List(1, 4).foreach: maxPar =>
    test(s"tight timeout on set_ops (parallel=$maxPar) triggers the timeout fallback"):
      val cfg = VerificationConfig(timeoutMs = 1L, maxParallel = maxPar)
      for
        ir     <- SpecFixtures.loadIR("set_ops")
        report <- Consistency.runConsistencyChecks(ir, cfg)
      yield
        val timedOut = report.checks.count(_.status == CheckOutcome.Unknown)
        assert(
          timedOut > 0,
          s"no Unknown outcomes — outer timeout fallback did not fire. " +
            s"Statuses: ${report.checks.groupBy(_.status).view.mapValues(_.size).toMap}"
        )
        assertEquals(report.ok, false)

  test("timeoutMs=0 disables outer timeout (safe_counter parity vs 30s default)"):
    val cfgZero = VerificationConfig(timeoutMs = 0L, maxParallel = 1)
    val cfgDef  = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    for
      ir   <- SpecFixtures.loadIR("safe_counter")
      zero <- Consistency.runConsistencyChecks(ir, cfgZero)
      dflt <- Consistency.runConsistencyChecks(ir, cfgDef)
    yield
      assertEquals(
        zero.checks.map(c => c.id -> c.status),
        dflt.checks.map(c => c.id -> c.status)
      )
      assertEquals(zero.ok, dflt.ok)

  test("ctx.interrupt() aborts solver.check() promptly on fiber cancel"):
    // #113: cfg.timeoutMs = 0 disables Z3's inner deadline, so if ctx.interrupt()
    // regresses this test hangs on set_ops until solver.check() returns naturally
    // — the desired regression signal. The 2s tolerance covers JIT warmup, GC,
    // CI noise, and the time Z3 takes to unwind its current rewriting pass after
    // the interrupt; mid-call abort itself is single-digit ms. Do not tighten
    // below ~1s without validating on slow CI runners, and do not loosen past
    // 2s without first confirming the regression is still caught.
    val cfg       = VerificationConfig(timeoutMs = 0L, maxParallel = 1)
    val outerMs   = 100L
    val tolerance = 2_000L
    Ref[IO].of(false).flatMap: fired =>
      for
        ir <- SpecFixtures.loadIR("set_ops")
        t0 <- IO.monotonic
        _ <- Consistency
               .runConsistencyChecks(ir, cfg)
               .timeoutTo(outerMs.millis, fired.set(true))
        t1      <- IO.monotonic
        didFire <- fired.get
        elapsed  = (t1 - t0).toMillis
      yield
        assert(didFire, "timeoutTo fallback did not fire — test did not exercise the cancel path")
        assert(
          elapsed < outerMs + tolerance,
          s"expected prompt cancel (< ${outerMs + tolerance}ms); got ${elapsed}ms. " +
            s"Without ctx.interrupt(), a ${outerMs}ms outer cancel would be held by Z3 " +
            s"until solver.check() returns naturally on the solver-heavy set_ops fixture."
        )

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
