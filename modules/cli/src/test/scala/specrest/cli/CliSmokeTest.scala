package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import munit.CatsEffectSuite

class CliSmokeTest extends CatsEffectSuite:

  private def log: Logger = Logger.fromFlags(verbose = false, quiet = true)

  test("check url_shortener is valid"):
    Check.run("fixtures/spec/url_shortener.spec", log).assertEquals(ExitCodes.Ok)

  test("check on missing file returns 1"):
    Check.run("fixtures/does-not-exist.spec", log).assertEquals(ExitCodes.Violations)

  test("check exits 1 on lint error (undefined identifier)"):
    Check.run("fixtures/lint/l02_undefined_ref_bad.spec", log)
      .assertEquals(ExitCodes.Violations)

  test("check exits 0 on lint warning only (missing ensures)"):
    Check.run("fixtures/lint/l03_missing_ensures_bad.spec", log)
      .assertEquals(ExitCodes.Ok)

  test("check exits 0 on the all-lints-pass fixture"):
    Check.run("fixtures/lint/passing.spec", log).assertEquals(ExitCodes.Ok)

  test("inspect --format json returns 0 on valid spec"):
    Inspect.run("fixtures/spec/safe_counter.spec", InspectFormat.Json, log)
      .assertEquals(ExitCodes.Ok)

  private def captureInspect(spec: String, format: InspectFormat): IO[(ExitCode, String)] =
    val buf = new java.io.ByteArrayOutputStream()
    val ps  = new java.io.PrintStream(buf, true, "UTF-8")
    Inspect.run(spec, format, log, ps).map: exit =>
      ps.flush()
      (exit, buf.toString("UTF-8"))

  test("inspect summary surfaces synthesis strategy per op (#31)"):
    captureInspect("fixtures/spec/url_shortener.spec", InspectFormat.Summary).map: (exit, out) =>
      assertEquals(exit, ExitCodes.Ok)
      assert(out.contains("Shorten: LLM_SYNTHESIS"), s"missing Shorten line:\n$out")
      assert(out.contains("Delete: DIRECT_EMIT"), s"missing Delete line:\n$out")
      assert(out.contains("DIRECT_EMIT"), s"summary should tally strategies:\n$out")

  test("inspect --format json includes synthesis_strategy map (#31)"):
    captureInspect("fixtures/spec/url_shortener.spec", InspectFormat.Json).map: (exit, out) =>
      val parsed = io.circe.parser.parse(out).toOption.getOrElse(fail("invalid JSON"))
      val strat  = parsed.hcursor.downField("synthesis_strategy")
      assertEquals(exit, ExitCodes.Ok)
      assertEquals(strat.downField("Shorten").as[String].toOption, Some("LLM_SYNTHESIS"))
      assertEquals(strat.downField("Delete").as[String].toOption, Some("DIRECT_EMIT"))

  test("InspectFormat.parse rejects unknown"):
    val err = InspectFormat.parse("yaml").left.toOption
    assert(err.exists(_.contains("unknown format")))

  test("inspect --format dafny emits a Dafny skeleton for safe_counter (#32)"):
    captureInspect("fixtures/spec/safe_counter.spec", InspectFormat.Dafny).map: (exit, out) =>
      assertEquals(exit, ExitCodes.Ok)
      assert(
        out.contains("class ServiceState"),
        s"missing ServiceState class:\n$out"
      )
      assert(
        out.contains("method Increment(st: ServiceState)"),
        s"missing Increment signature:\n$out"
      )
      assert(
        out.contains("ensures st.count == old(st.count) + 1"),
        s"missing Increment ensures clause:\n$out"
      )
      assert(
        out.contains("predicate ServiceStateInv(st: ServiceState)"),
        s"missing invariant predicate:\n$out"
      )

  test("InspectFormat.parse accepts dafny"):
    assertEquals(InspectFormat.parse("dafny").toOption, Some(InspectFormat.Dafny))

  test("inspect --format dafny exits with Translator on unsupported expression (#32)"):
    Inspect
      .run("fixtures/spec/edge_cases.spec", InspectFormat.Dafny, log)
      .assertEquals(ExitCodes.Translator)

  test("verify safe_counter returns exit 0"):
    Verify.run(
      "fixtures/spec/safe_counter.spec",
      VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None),
      log
    ).assertEquals(ExitCodes.Ok)

  test("verify unsat_invariants returns exit 1 (violations)"):
    Verify.run(
      "fixtures/spec/unsat_invariants.spec",
      VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None),
      log
    ).assertEquals(ExitCodes.Violations)

  test("verify --dump-smt writes SMT-LIB (via dumpSmtOut)"):
    val acquire = IO.blocking(java.nio.file.Files.createTempFile("smt-test-", ".smt2"))
    val release = (tmp: java.nio.file.Path) =>
      IO.blocking(java.nio.file.Files.deleteIfExists(tmp)).void
    cats.effect.Resource.make(acquire)(release).use: tmp =>
      for
        exit <- Verify.run(
                  "fixtures/spec/safe_counter.spec",
                  VerifyOptions(0L, dumpSmt = false, dumpSmtOut = Some(tmp.toString)),
                  log
                )
        content <- IO.blocking(java.nio.file.Files.readString(tmp))
      yield
        assertEquals(exit, ExitCodes.Ok)
        assert(content.contains("(set-logic ALL)"))
        assert(content.contains("(check-sat)"))

  private def tempOutPath: cats.effect.Resource[IO, java.nio.file.Path] =
    val acquire = IO.blocking {
      val parent = java.nio.file.Files.createTempDirectory("emit-test-")
      parent.resolve("out")
    }
    val release = (out: java.nio.file.Path) =>
      IO.blocking {
        import scala.jdk.StreamConverters.*
        val parent = out.getParent
        if java.nio.file.Files.exists(parent) then
          scala.util.Using.resource(java.nio.file.Files.walk(parent)): stream =>
            stream.toScala(List).reverse.foreach: p =>
              val _ = java.nio.file.Files.deleteIfExists(p)
      }
    cats.effect.Resource.make(acquire)(release)

  test("compile with default gate succeeds on fully verified safe_counter"):
    tempOutPath.use: outDir =>
      for
        exit <- Compile.run(
                  "fixtures/spec/safe_counter.spec",
                  CompileOptions("python-fastapi-postgres", outDir.toString),
                  log
                )
      yield
        assertEquals(exit, ExitCodes.Ok)
        assert(java.nio.file.Files.exists(outDir.resolve("pyproject.toml")))
        assert(java.nio.file.Files.exists(outDir.resolve("app/main.py")))
        assert(java.nio.file.Files.exists(outDir.resolve(".github/workflows/ci.yml")))

  test("compile --ignore-verify emits multi-entity project for url_shortener"):
    tempOutPath.use: outDir =>
      for
        exit <- Compile.run(
                  "fixtures/spec/url_shortener.spec",
                  CompileOptions(
                    "python-fastapi-postgres",
                    outDir.toString,
                    ignoreVerify = true
                  ),
                  log
                )
      yield
        assertEquals(exit, ExitCodes.Ok)
        assert(java.nio.file.Files.exists(outDir.resolve("pyproject.toml")))
        assert(java.nio.file.Files.exists(outDir.resolve("app/main.py")))
        assert(java.nio.file.Files.exists(outDir.resolve("app/models/url_mapping.py")))
        assert(java.nio.file.Files.exists(outDir.resolve(".github/workflows/ci.yml")))

  private case class GateCase(
      name: String,
      spec: String,
      ignoreVerify: Boolean,
      expectedExit: ExitCode,
      expectFiles: Boolean
  )

  List(
    GateCase(
      "compile gate blocks on unsat invariants",
      "fixtures/spec/unsat_invariants.spec",
      ignoreVerify = false,
      expectedExit = ExitCodes.Violations,
      expectFiles = false
    ),
    GateCase(
      "compile --ignore-verify bypasses gate on unsat invariants",
      "fixtures/spec/unsat_invariants.spec",
      ignoreVerify = true,
      expectedExit = ExitCodes.Ok,
      expectFiles = true
    ),
    GateCase(
      "compile gate blocks on preservation failure (broken_url_shortener)",
      "fixtures/spec/broken_url_shortener.spec",
      ignoreVerify = false,
      expectedExit = ExitCodes.Violations,
      expectFiles = false
    )
  ).foreach: c =>
    test(c.name):
      tempOutPath.use: outDir =>
        for
          exit <- Compile.run(
                    c.spec,
                    CompileOptions(
                      "python-fastapi-postgres",
                      outDir.toString,
                      ignoreVerify = c.ignoreVerify
                    ),
                    log
                  )
        yield
          assertEquals(exit, c.expectedExit)
          assertEquals(
            java.nio.file.Files.exists(outDir.resolve("pyproject.toml")),
            c.expectFiles
          )
          assertEquals(
            java.nio.file.Files.exists(outDir),
            c.expectFiles,
            s"gate failure should leave the output directory uncreated (${c.name})"
          )

  test("--strict-strategies fails when type alias has unhandled `where` and no override"):
    tempOutPath.use: outDir =>
      Compile.run(
        "fixtures/spec/strict_strategies_negative.spec",
        CompileOptions(
          "python-fastapi-postgres",
          outDir.toString,
          ignoreVerify = true,
          withTests = true,
          strictStrategies = true
        ),
        log
      ).map: exit =>
        assertEquals(exit, ExitCodes.Violations)
        assert(
          !java.nio.file.Files.exists(outDir.resolve("pyproject.toml")),
          "no files should be written when strict-strategies fails"
        )

  test("--strict-strategies passes when override is registered for the unhandled type"):
    tempOutPath.use: outDir =>
      Compile.run(
        "fixtures/spec/strict_strategies_positive.spec",
        CompileOptions(
          "python-fastapi-postgres",
          outDir.toString,
          ignoreVerify = true,
          withTests = true,
          strictStrategies = true
        ),
        log
      ).map: exit =>
        assertEquals(exit, ExitCodes.Ok)
        val strategiesPy =
          java.nio.file.Files.readString(outDir.resolve("tests/strategies.py"))
        assert(
          strategiesPy.contains("from tests.strategies_user import custom_code"),
          s"strategies.py should import the override:\n$strategiesPy"
        )
        assert(
          strategiesPy.contains("def strategy_custom_code():\n    return custom_code()"),
          s"strategies.py should call the override:\n$strategiesPy"
        )
        assert(
          java.nio.file.Files.exists(outDir.resolve("tests/strategies_user.py")),
          "strategies_user.py stub must be emitted"
        )

  test("strategies_user.py is not overwritten by re-compile"):
    tempOutPath.use: outDir =>
      val opts = CompileOptions(
        "python-fastapi-postgres",
        outDir.toString,
        ignoreVerify = true,
        withTests = true
      )
      for
        firstExit <- Compile.run("fixtures/spec/strict_strategies_positive.spec", opts, log)
        userPath   = outDir.resolve("tests/strategies_user.py")
        _ <- IO.blocking {
               val edited = java.nio.file.Files.readString(userPath) + "\n# USER EDIT\n"
               java.nio.file.Files.writeString(userPath, edited)
             }
        secondExit   <- Compile.run("fixtures/spec/strict_strategies_positive.spec", opts, log)
        finalContent <- IO.blocking(java.nio.file.Files.readString(userPath))
      yield
        assertEquals(firstExit, ExitCodes.Ok)
        assertEquals(secondExit, ExitCodes.Ok)
        assert(finalContent.contains("# USER EDIT"), s"user edit was overwritten:\n$finalContent")
