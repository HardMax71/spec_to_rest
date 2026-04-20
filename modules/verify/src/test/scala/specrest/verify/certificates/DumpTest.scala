package specrest.verify.certificates

import io.circe.parser
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.Consistency
import specrest.verify.VerificationConfig
import specrest.verify.z3.WasmBackend

import java.nio.file.Files
import java.nio.file.Paths

class DumpTest extends munit.FunSuite:

  // (fixture name, ok=true expected, includes Z3 .smt2, includes Alloy .als)
  private val cases: List[(String, Boolean, Boolean, Boolean)] = List(
    ("safe_counter", true, true, false),
    ("unsat_invariants", false, true, false),
    ("dead_op", false, true, false),
    ("powerset_demo", true, false, true),
    ("temporal_demo", true, true, true)
  )

  cases.foreach: (fixture, expectedOk, expectsSmt2, expectsAls) =>
    test(s"--dump-vc emits per-check VCs for $fixture"):
      val tmpDir  = Files.createTempDirectory(s"dump-test-$fixture-")
      val ir      = parseSpec(fixture)
      val backend = WasmBackend()
      val sink    = DumpSink.open(tmpDir).toOption.get
      try
        val report = Consistency.runConsistencyChecks(
          ir,
          backend,
          VerificationConfig.Default,
          Some(sink)
        )
        sink.writeIndex(s"fixtures/spec/$fixture.spec", 0.0, report.ok)
        val files = Files.list(tmpDir).iterator
        val names = scala.collection.mutable.ListBuffer.empty[String]
        while files.hasNext do names += files.next.getFileName.toString
        assert(
          names.contains("verdicts.json"),
          s"verdicts.json missing in $tmpDir; saw: ${names.toList}"
        )
        if expectsSmt2 then
          assert(names.exists(_.endsWith(".smt2")), s"no .smt2 files; saw: ${names.toList}")
        if expectsAls then
          assert(names.exists(_.endsWith(".als")), s"no .als files; saw: ${names.toList}")
        val verdicts = parser
          .parse(Files.readString(tmpDir.resolve("verdicts.json")))
          .toOption
          .getOrElse(fail("invalid verdicts.json"))
        val cursor = verdicts.hcursor
        assertEquals(cursor.downField("schemaVersion").as[Int].toOption, Some(1))
        assertEquals(cursor.downField("ok").as[Boolean].toOption, Some(expectedOk))
        val entries = cursor.downField("entries").values.getOrElse(Vector.empty).toList
        assertEquals(
          entries.size,
          sink.entryCount,
          s"verdicts.json entries mismatch sink for $fixture"
        )
        // Every referenced file must actually exist on disk; both outcome and
        // rawStatus fields must be present (rawStatus is what cross-check uses).
        entries.foreach: e =>
          val cursor = e.hcursor
          val file = cursor.downField("file").as[String].toOption
            .getOrElse(fail(s"entry missing 'file': $e"))
          assert(Files.exists(tmpDir.resolve(file)), s"missing $file in dump dir")
          assert(cursor.downField("outcome").as[String].isRight, s"missing outcome: $e")
          assert(cursor.downField("rawStatus").as[String].isRight, s"missing rawStatus: $e")
      finally
        backend.close()
        deleteRecursive(tmpDir)

  test("DumpSink rejects non-empty target directory"):
    val tmpDir = Files.createTempDirectory("dump-nonempty-")
    val _      = Files.writeString(tmpDir.resolve("clutter.txt"), "x")
    assert(DumpSink.open(tmpDir).isLeft, "expected Left for non-empty dir")
    deleteRecursive(tmpDir)

  test("dumped Z3 SMT-LIB starts with set-logic and ends with check-sat"):
    val tmpDir  = Files.createTempDirectory("dump-shape-")
    val ir      = parseSpec("safe_counter")
    val backend = WasmBackend()
    val sink    = DumpSink.open(tmpDir).toOption.get
    try
      val _ = Consistency.runConsistencyChecks(
        ir,
        backend,
        VerificationConfig.Default,
        Some(sink)
      )
      val smtFile                            = Files.list(tmpDir).iterator.asInstanceOf[java.util.Iterator[java.nio.file.Path]]
      var picked: Option[java.nio.file.Path] = None
      while picked.isEmpty && smtFile.hasNext do
        val p = smtFile.next
        if p.getFileName.toString.endsWith(".smt2") then picked = Some(p)
      val src = Files.readString(picked.getOrElse(fail("no .smt2 file")))
      assert(src.startsWith("(set-logic ALL)"), s"unexpected prefix: ${src.take(60)}")
      assert(src.contains("(check-sat)"), s"missing (check-sat) in dump")
    finally
      backend.close()
      deleteRecursive(tmpDir)

  private def parseSpec(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree).toOption.get

  private def deleteRecursive(p: java.nio.file.Path): Unit =
    if Files.isDirectory(p) then
      val it = Files.list(p).iterator
      while it.hasNext do deleteRecursive(it.next)
    val _ = Files.deleteIfExists(p)
