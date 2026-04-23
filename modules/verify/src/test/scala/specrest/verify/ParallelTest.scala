package specrest.verify

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.certificates.DumpSink

import java.nio.file.Files
import java.nio.file.Paths

class ParallelTest extends FunSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpecSync(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIRSync(parsed.tree).toOption.get

  private val parityFixtures: List[String] =
    List("safe_counter", "url_shortener", "set_ops", "broken_url_shortener")

  parityFixtures.foreach: fixture =>
    test(s"$fixture — parallel=4 and serial=1 return identical check ids and outcomes"):
      val ir = buildIR(fixture)
      val cfgSerial =
        VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
      val cfgParallel =
        VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
      val serial   = Consistency.runConsistencyChecks(ir, cfgSerial).unsafeRunSync()
      val parallel = Consistency.runConsistencyChecks(ir, cfgParallel).unsafeRunSync()
      assertEquals(serial.checks.map(_.id), parallel.checks.map(_.id))
      assertEquals(
        serial.checks.map(c => c.id -> c.status),
        parallel.checks.map(c => c.id -> c.status)
      )
      assertEquals(serial.ok, parallel.ok)

  test("maxParallel=0 reproduces serial output (regression parity)"):
    val ir         = buildIR("url_shortener")
    val cfgZero    = VerificationConfig(timeoutMs = 30_000L, maxParallel = 0)
    val cfgOne     = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    val zeroReport = Consistency.runConsistencyChecks(ir, cfgZero).unsafeRunSync()
    val oneReport  = Consistency.runConsistencyChecks(ir, cfgOne).unsafeRunSync()
    assertEquals(
      zeroReport.checks.map(c => c.id -> c.status),
      oneReport.checks.map(c => c.id -> c.status)
    )
    assertEquals(zeroReport.ok, oneReport.ok)

  test("parTraverseN preserves plan order in the report"):
    val ir     = buildIR("url_shortener")
    val cfg    = VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
    val first  = Consistency.runConsistencyChecks(ir, cfg).unsafeRunSync()
    val second = Consistency.runConsistencyChecks(ir, cfg).unsafeRunSync()
    assertEquals(first.checks.map(_.id), second.checks.map(_.id))

  test("DumpSink collects all entries under parallel writes"):
    val ir     = buildIR("safe_counter")
    val tmpDir = Files.createTempDirectory("parallel-dump-")
    val sink   = DumpSink.open(tmpDir).toOption.get
    val cfg    = VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
    val report = Consistency.runConsistencyChecks(ir, cfg, Some(sink)).unsafeRunSync()
    assertEquals(
      sink.entryCount,
      report.checks.count(c =>
        c.status == CheckOutcome.Sat ||
          c.status == CheckOutcome.Unsat ||
          c.status == CheckOutcome.Unknown
      ),
      s"dump entries should equal non-skipped checks; checks=${report.checks.map(c => s"${c.id}->${c.status}")}"
    )

  test("parallel mode on a solver-heavy spec (set_ops) does not regress serial wall time"):
    val ir = buildIR("set_ops")
    val cfgSerial =
      VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    val cfgParallel =
      VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)

    // Warm up the JVM / native Z3 load path so we don't amortize one-shot startup into the
    // serial baseline.
    val _ = Consistency.runConsistencyChecks(ir, cfgSerial).unsafeRunSync()

    val (_, serialMs) = time(Consistency.runConsistencyChecks(ir, cfgSerial).unsafeRunSync())
    val (_, parMs)    = time(Consistency.runConsistencyChecks(ir, cfgParallel).unsafeRunSync())

    // Not asserting a hard speedup — CI is noisy and the per-check backend allocation overhead
    // can dominate when individual checks are short. JMH-grade measurements land in M_CE.9
    // (#104). For M_CE.5 we only assert correctness + "parallel isn't catastrophically slow".
    assert(
      parMs <= serialMs * 2.0,
      f"parallel ($parMs%.0fms) must not regress serial ($serialMs%.0fms) by more than 2×"
    )

  private def time[A](body: => A): (A, Double) =
    val t0  = System.nanoTime()
    val out = body
    (out, (System.nanoTime() - t0) / 1_000_000.0)

  test("CheckPlan enumerates 1 global + 2N op checks + N*M preservation + T temporals"):
    val ir     = buildIR("url_shortener")
    val cfg    = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    val report = Consistency.runConsistencyChecks(ir, cfg).unsafeRunSync()
    val n      = ir.operations.size
    val m      = ir.invariants.size
    val t      = ir.temporals.size
    assertEquals(
      report.checks.size,
      1 + 2 * n + n * m + t,
      s"expected formula 1 + 2N + N*M + T to match plan enumeration"
    )

  test("runConsistencyChecks returns an IO that is referentially transparent"):
    val ir     = buildIR("safe_counter")
    val cfg    = VerificationConfig(timeoutMs = 30_000L, maxParallel = 2)
    val action = Consistency.runConsistencyChecks(ir, cfg)
    val r1     = action.unsafeRunSync()
    val r2     = action.unsafeRunSync()
    assertEquals(r1.checks.map(c => c.id -> c.status), r2.checks.map(c => c.id -> c.status))
