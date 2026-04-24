package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import io.circe.parser
import munit.CatsEffectSuite

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files

class VerifyJsonTest extends CatsEffectSuite:

  private def log: Logger = Logger.fromFlags(verbose = false, quiet = true)

  private def captureStdout(
      run: PrintStream => IO[ExitCode]
  ): IO[(ExitCode, String)] =
    IO.delay {
      val buf = new ByteArrayOutputStream()
      val ps  = new PrintStream(buf, true, "UTF-8")
      (buf, ps)
    }.flatMap: (buf, ps) =>
      run(ps).flatTap(_ => IO.delay(ps.flush()))
        .map(exit => (exit, buf.toString("UTF-8")))

  test("--json emits JSON to stdout and exits 0 on passing spec"):
    val opts = VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None, json = true)
    captureStdout(ps => Verify.run("fixtures/spec/safe_counter.spec", opts, log, ps))
      .map: (exit, out) =>
        assertEquals(exit, ExitCodes.Ok)
        val parsed = parser.parse(out).toOption.getOrElse(fail(s"invalid JSON: $out"))
        val cur    = parsed.hcursor
        assertEquals(cur.downField("schemaVersion").as[Int].toOption, Some(1))
        assertEquals(cur.downField("ok").as[Boolean].toOption, Some(true))
        val checks = cur.downField("checks").values.getOrElse(Vector.empty).toList
        assert(checks.nonEmpty)
        // Contract: when outcome is `sat`, the diagnostic field is null — consumers rely on this
        // to distinguish passing checks from failures without looking at `status` alone.
        checks.foreach: c =>
          val status     = c.hcursor.downField("status").as[String].toOption
          val diagnostic = c.hcursor.downField("diagnostic").focus
          if status.contains("sat") then
            assertEquals(
              diagnostic,
              Some(io.circe.Json.Null),
              s"sat check had non-null diagnostic: $c"
            )

  test("--json exits 1 on failing spec, still emits valid JSON"):
    val opts = VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None, json = true)
    captureStdout(ps => Verify.run("fixtures/spec/unsat_invariants.spec", opts, log, ps))
      .map: (exit, out) =>
        assertEquals(exit, ExitCodes.Violations)
        val parsed = parser.parse(out).toOption.getOrElse(fail(s"invalid JSON: $out"))
        assertEquals(parsed.hcursor.downField("ok").as[Boolean].toOption, Some(false))
        val categories = parsed.hcursor.downField("checks").values
          .getOrElse(Vector.empty).toList
          .flatMap(_.hcursor.downField("diagnostic").downField("category").as[String].toOption)
        assert(
          categories.contains("contradictory_invariants"),
          s"expected contradictory_invariants among $categories"
        )

  test("--json-out writes to file; stdout stays clean"):
    val acquire = IO.blocking(Files.createTempFile("verify-json-", ".json"))
    val release = (p: java.nio.file.Path) => IO.blocking(Files.deleteIfExists(p)).void
    cats.effect.Resource.make(acquire)(release).use: tmp =>
      val opts = VerifyOptions(
        30_000L,
        dumpSmt = false,
        dumpSmtOut = None,
        jsonOut = Some(tmp.toString)
      )
      captureStdout(ps => Verify.run("fixtures/spec/safe_counter.spec", opts, log, ps))
        .flatMap: (exit, out) =>
          IO.blocking(Files.readString(tmp)).map: content =>
            assertEquals(exit, ExitCodes.Ok)
            assertEquals(out, "", "stdout should be empty when --json-out is active")
            val parsed = parser.parse(content).toOption.getOrElse(fail("invalid JSON in file"))
            assertEquals(parsed.hcursor.downField("schemaVersion").as[Int].toOption, Some(1))
            assertEquals(parsed.hcursor.downField("ok").as[Boolean].toOption, Some(true))

  test("--json + --dump-smt is rejected with a clear error"):
    val opts = VerifyOptions(
      30_000L,
      dumpSmt = true,
      dumpSmtOut = None,
      json = true
    )
    captureStdout(ps => Verify.run("fixtures/spec/safe_counter.spec", opts, log, ps))
      .map: (exit, out) =>
        assertEquals(exit, ExitCodes.Violations)
        assertEquals(out, "", "no stdout output when the combination is rejected")

  test("--json with --explain surfaces coreSpans on unsat diagnostics"):
    val opts = VerifyOptions(
      30_000L,
      dumpSmt = false,
      dumpSmtOut = None,
      json = true,
      explain = true
    )
    captureStdout(ps => Verify.run("fixtures/spec/unsat_invariants.spec", opts, log, ps))
      .map: (exit, out) =>
        assertEquals(exit, ExitCodes.Violations)
        val parsed = parser.parse(out).toOption.getOrElse(fail("invalid JSON"))
        val cores = parsed.hcursor.downField("checks").values.getOrElse(Vector.empty).toList
          .flatMap(_.hcursor.downField("diagnostic").downField("coreSpans").values)
          .flatten
        assert(cores.nonEmpty, "expected at least one coreSpan entry when --explain is set")
