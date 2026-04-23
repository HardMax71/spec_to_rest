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
    try
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
    finally deleteRecursive(tmpDir)

  private def deleteRecursive(path: java.nio.file.Path): Unit =
    if Files.isDirectory(path) then
      val stream = Files.list(path)
      try
        val iter = stream.iterator()
        while iter.hasNext do deleteRecursive(iter.next())
      finally stream.close()
    val _ = Files.deleteIfExists(path)

  test("parallel mode on a solver-heavy spec (set_ops) matches serial results"):
    val ir           = buildIR("set_ops")
    val cfgSerial    = VerificationConfig(timeoutMs = 30_000L, maxParallel = 1)
    val cfgParallel  = VerificationConfig(timeoutMs = 30_000L, maxParallel = 4)
    val serialReport = Consistency.runConsistencyChecks(ir, cfgSerial).unsafeRunSync()
    val parReport    = Consistency.runConsistencyChecks(ir, cfgParallel).unsafeRunSync()
    assertEquals(
      parReport.checks.map(c => c.id -> c.status),
      serialReport.checks.map(c => c.id -> c.status)
    )
    assertEquals(parReport.ok, serialReport.ok)

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
