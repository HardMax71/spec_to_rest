package specrest.verify.certificates

import cats.effect.IO
import cats.effect.Resource
import io.circe.parser
import munit.CatsEffectSuite
import specrest.verify.Consistency
import specrest.verify.VerificationConfig
import specrest.verify.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path
import scala.util.Using

class DumpTest extends CatsEffectSuite:

  private val cases: List[(String, Boolean, Boolean, Boolean)] = List(
    ("safe_counter", true, true, false),
    ("unsat_invariants", false, true, false),
    ("dead_op", false, true, false),
    ("powerset_demo", true, false, true),
    ("temporal_demo", true, true, true)
  )

  private def tempDir(prefix: String): Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory(prefix)))(p =>
      IO.blocking(deleteRecursive(p))
    )

  cases.foreach: (fixture, expectedOk, expectsSmt2, expectsAls) =>
    test(s"--dump-vc emits per-check VCs for $fixture"):
      tempDir(s"dump-test-$fixture-").use: tmpDir =>
        val sink = DumpSink.open(tmpDir).toOption.get
        for
          ir     <- SpecFixtures.loadIR(fixture)
          report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default, Some(sink))
          _      <- IO.blocking(sink.writeIndex(s"fixtures/spec/$fixture.spec", 0.0, report.ok))
          names <- IO.blocking {
                     Using.resource(Files.list(tmpDir)): stream =>
                       val it  = stream.iterator
                       val buf = scala.collection.mutable.ListBuffer.empty[String]
                       while it.hasNext do buf += it.next.getFileName.toString
                       buf.toList
                   }
          _ = assert(
                names.contains("verdicts.json"),
                s"verdicts.json missing in $tmpDir; saw: $names"
              )
          _ = if expectsSmt2 then
                assert(names.exists(_.endsWith(".smt2")), s"no .smt2 files; saw: $names")
          _ = if expectsAls then
                assert(names.exists(_.endsWith(".als")), s"no .als files; saw: $names")
          verdictsRaw <- IO.blocking(Files.readString(tmpDir.resolve("verdicts.json")))
          verdicts     = parser.parse(verdictsRaw).toOption.getOrElse(fail("invalid verdicts.json"))
        yield
          val cursor = verdicts.hcursor
          assertEquals(cursor.downField("schemaVersion").as[Int].toOption, Some(1))
          assertEquals(cursor.downField("ok").as[Boolean].toOption, Some(expectedOk))
          val entries = cursor.downField("entries").values.getOrElse(Vector.empty).toList
          assertEquals(
            entries.size,
            sink.entryCount,
            s"verdicts.json entries mismatch sink for $fixture"
          )
          entries.foreach: e =>
            val ec = e.hcursor
            val file = ec.downField("file").as[String].toOption
              .getOrElse(fail(s"entry missing 'file': $e"))
            assert(Files.exists(tmpDir.resolve(file)), s"missing $file in dump dir")
            assert(ec.downField("outcome").as[String].isRight, s"missing outcome: $e")
            assert(ec.downField("rawStatus").as[String].isRight, s"missing rawStatus: $e")

  test("DumpSink rejects non-empty target directory"):
    tempDir("dump-nonempty-").use: tmpDir =>
      IO.blocking(Files.writeString(tmpDir.resolve("clutter.txt"), "x")).map: _ =>
        assert(DumpSink.open(tmpDir).isLeft, "expected Left for non-empty dir")

  test("dumped Z3 SMT-LIB starts with set-logic and ends with check-sat"):
    tempDir("dump-shape-").use: tmpDir =>
      val sink = DumpSink.open(tmpDir).toOption.get
      for
        ir <- SpecFixtures.loadIR("safe_counter")
        _  <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default, Some(sink))
        src <- IO.blocking {
                 val picked = Using.resource(Files.list(tmpDir)): stream =>
                   val it                  = stream.iterator
                   var found: Option[Path] = None
                   while found.isEmpty && it.hasNext do
                     val p = it.next
                     if p.getFileName.toString.endsWith(".smt2") then found = Some(p)
                   found
                 Files.readString(picked.getOrElse(fail("no .smt2 file")))
               }
      yield
        assert(src.startsWith("(set-logic ALL)"), s"unexpected prefix: ${src.take(60)}")
        assert(src.contains("(check-sat)"), s"missing (check-sat) in dump")

  private def deleteRecursive(p: Path): Unit =
    if Files.isDirectory(p) then
      val it = Files.list(p).iterator
      while it.hasNext do deleteRecursive(it.next)
    val _ = Files.deleteIfExists(p)
