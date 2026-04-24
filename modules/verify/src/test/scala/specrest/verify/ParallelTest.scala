package specrest.verify

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectSuite
import specrest.verify.certificates.DumpSink
import specrest.verify.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path

class ParallelTest extends CatsEffectSuite:

  private val parityFixtures: List[String] =
    List("safe_counter", "url_shortener", "set_ops", "broken_url_shortener")

  private def tempDir(prefix: String): Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory(prefix)))(p =>
      IO.blocking(deleteRecursive(p))
    )

  parityFixtures.foreach: fixture =>
    test(s"$fixture — parallel=4 and serial=1 return identical check ids and outcomes"):
      val cfgSerial   = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
      val cfgParallel = VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
      for
        ir       <- SpecFixtures.loadIR(fixture)
        serial   <- Consistency.runConsistencyChecks(ir, cfgSerial)
        parallel <- Consistency.runConsistencyChecks(ir, cfgParallel)
      yield
        assertEquals(serial.checks.map(_.id), parallel.checks.map(_.id))
        assertEquals(
          serial.checks.map(c => c.id -> c.status),
          parallel.checks.map(c => c.id -> c.status)
        )
        assertEquals(serial.ok, parallel.ok)

  test("maxParallel=0 reproduces serial output (regression parity)"):
    val cfgZero = VerificationConfig(timeoutMs = 30_000L, maxParallel = 0)
    val cfgOne  = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    for
      ir   <- SpecFixtures.loadIR("url_shortener")
      zero <- Consistency.runConsistencyChecks(ir, cfgZero)
      one  <- Consistency.runConsistencyChecks(ir, cfgOne)
    yield
      assertEquals(
        zero.checks.map(c => c.id -> c.status),
        one.checks.map(c => c.id -> c.status)
      )
      assertEquals(zero.ok, one.ok)

  test("parTraverseN preserves plan order in the report"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
    for
      ir     <- SpecFixtures.loadIR("url_shortener")
      first  <- Consistency.runConsistencyChecks(ir, cfg)
      second <- Consistency.runConsistencyChecks(ir, cfg)
    yield assertEquals(first.checks.map(_.id), second.checks.map(_.id))

  test("DumpSink collects all entries under parallel writes"):
    tempDir("parallel-dump-").use: tmpDir =>
      val sink = DumpSink.open(tmpDir).toOption.get
      val cfg  = VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
      for
        ir     <- SpecFixtures.loadIR("safe_counter")
        report <- Consistency.runConsistencyChecks(ir, cfg, Some(sink))
      yield assertEquals(
        sink.entryCount,
        report.checks.count(c =>
          c.status == CheckOutcome.Sat ||
            c.status == CheckOutcome.Unsat ||
            c.status == CheckOutcome.Unknown
        ),
        s"dump entries should equal non-skipped checks; checks=${report.checks.map(c => s"${c.id}->${c.status}")}"
      )

  test("parallel mode on a solver-heavy spec (set_ops) matches serial results"):
    val cfgSerial   = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    val cfgParallel = VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
    for
      ir     <- SpecFixtures.loadIR("set_ops")
      serial <- Consistency.runConsistencyChecks(ir, cfgSerial)
      par    <- Consistency.runConsistencyChecks(ir, cfgParallel)
    yield
      assertEquals(
        par.checks.map(c => c.id -> c.status),
        serial.checks.map(c => c.id -> c.status)
      )
      assertEquals(par.ok, serial.ok)

  test("CheckPlan enumerates 1 global + 2N op checks + N*M preservation + T temporals"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    for
      ir     <- SpecFixtures.loadIR("url_shortener")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val n = ir.operations.size
      val m = ir.invariants.size
      val t = ir.temporals.size
      assertEquals(
        report.checks.size,
        1 + 2 * n + n * m + t,
        s"expected formula 1 + 2N + N*M + T to match plan enumeration"
      )

  test("runConsistencyChecks returns an IO that is referentially transparent"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, maxParallel = 2)
    SpecFixtures.loadIR("safe_counter").flatMap: ir =>
      val action = Consistency.runConsistencyChecks(ir, cfg)
      for
        r1 <- action
        r2 <- action
      yield assertEquals(r1.checks.map(c => c.id -> c.status), r2.checks.map(c => c.id -> c.status))

  private def deleteRecursive(path: Path): Unit =
    if Files.isDirectory(path) then
      val stream = Files.list(path)
      try
        val iter = stream.iterator()
        while iter.hasNext do deleteRecursive(iter.next())
      finally stream.close()
    val _ = Files.deleteIfExists(path)
