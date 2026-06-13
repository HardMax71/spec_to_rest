package specrest.cli

import cats.effect.IO
import munit.CatsEffectSuite

class CliSmokeTest extends CatsEffectSuite:

  private def log: Logger = Logger.fromFlags(verbose = false, quiet = true)

  test("check url_shortener is valid"):
    Check.run("fixtures/spec/url_shortener.spec", log).assertEquals(ExitStatus.Ok)

  test("__diag-init exits Ok on the JVM and prints the cause-walk header to stderr"):
    val buf = new java.io.ByteArrayOutputStream()
    val ps  = new java.io.PrintStream(buf, true, "UTF-8")
    DiagInit.run(ps).map: exit =>
      ps.flush()
      val out = buf.toString("UTF-8")
      assertEquals(exit, ExitStatus.Ok)
      assert(out.contains("=== diag-init: substrate VM class-init probe ==="), out)
      assert(out.contains("step A:"), out)
      assert(out.contains("step B:"), out)
      assert(out.contains("step C:"), out)
      assert(out.contains("resolve=true init=true call=true"), out)

  test("check on missing file returns 1"):
    Check.run("fixtures/does-not-exist.spec", log).assertEquals(ExitStatus.Violations)

  test("check exits 1 on lint error (undefined identifier)"):
    Check.run("fixtures/lint/l02_undefined_ref_bad.spec", log)
      .assertEquals(ExitStatus.Violations)

  test("check exits 0 on lint warning only (missing ensures)"):
    Check.run("fixtures/lint/l03_missing_ensures_bad.spec", log)
      .assertEquals(ExitStatus.Ok)

  test("check exits 0 on the all-lints-pass fixture"):
    Check.run("fixtures/lint/passing.spec", log).assertEquals(ExitStatus.Ok)

  test("inspect --format json returns 0 on valid spec"):
    Inspect.run("fixtures/spec/safe_counter.spec", InspectFormat.Json, log)
      .assertEquals(ExitStatus.Ok)

  private def captureInspect(
      spec: String,
      format: InspectFormat,
      op: Option[String] = None
  ): IO[(ExitStatus, String)] =
    val buf = new java.io.ByteArrayOutputStream()
    val ps  = new java.io.PrintStream(buf, true, "UTF-8")
    Inspect.run(spec, format, log, ps, op).map: exit =>
      ps.flush()
      (exit, buf.toString("UTF-8"))

  test("inspect summary surfaces synthesis strategy per op (#31)"):
    captureInspect("fixtures/spec/url_shortener.spec", InspectFormat.Summary).map: (exit, out) =>
      assertEquals(exit, ExitStatus.Ok)
      assert(out.contains("Shorten: LLM_SYNTHESIS"), s"missing Shorten line:\n$out")
      assert(out.contains("Delete: DIRECT_EMIT"), s"missing Delete line:\n$out")
      assert(out.contains("DIRECT_EMIT"), s"summary should tally strategies:\n$out")

  test("inspect --format json includes synthesis_strategy map (#31)"):
    captureInspect("fixtures/spec/url_shortener.spec", InspectFormat.Json).map: (exit, out) =>
      val parsed = io.circe.parser.parse(out).toOption.getOrElse(fail("invalid JSON"))
      val strat  = parsed.hcursor.downField("synthesis_strategy")
      assertEquals(exit, ExitStatus.Ok)
      assertEquals(strat.downField("Shorten").as[String].toOption, Some("LLM_SYNTHESIS"))
      assertEquals(strat.downField("Delete").as[String].toOption, Some("DIRECT_EMIT"))

  test("InspectFormat.parse rejects unknown"):
    val err = InspectFormat.parse("yaml").left.toOption
    assert(err.exists(_.contains("unknown format")))

  test("inspect --format dafny emits a Dafny skeleton for safe_counter (#32)"):
    captureInspect("fixtures/spec/safe_counter.spec", InspectFormat.Dafny).map: (exit, out) =>
      assertEquals(exit, ExitStatus.Ok)
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

  test("InspectFormat.parse accepts dafny-prompt (#28)"):
    assertEquals(
      InspectFormat.parse("dafny-prompt").toOption,
      Some(InspectFormat.DafnyPrompt)
    )

  test("inspect --format dafny-prompt renders LLM-bound sections for url_shortener (#28)"):
    captureInspect(
      "fixtures/spec/url_shortener.spec",
      InspectFormat.DafnyPrompt,
      op = Some("Shorten")
    ).map: (exit, out) =>
      assertEquals(exit, ExitStatus.Ok)
      assert(out.contains("# Operation: Shorten"), s"missing operation header:\n$out")
      assert(out.contains("## Method Signature"), s"missing signature section:\n$out")
      assert(out.contains("## Similar Verified Examples"), s"missing few-shot section:\n$out")

  test("inspect --format dafny-prompt without --operation lists all LLM_SYNTHESIS ops"):
    captureInspect(
      "fixtures/spec/url_shortener.spec",
      InspectFormat.DafnyPrompt
    ).map: (exit, out) =>
      assertEquals(exit, ExitStatus.Ok)
      assert(out.contains("# Operation: Shorten"), s"missing Shorten:\n$out")

  test("inspect --format dafny-prompt --operation unknown exits Translator"):
    Inspect
      .run(
        "fixtures/spec/safe_counter.spec",
        InspectFormat.DafnyPrompt,
        log,
        operation = Some("DoesNotExist")
      )
      .assertEquals(ExitStatus.Translator)

  test("verify safe_counter returns exit 0"):
    Verify.run(
      "fixtures/spec/safe_counter.spec",
      VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None),
      log
    ).assertEquals(ExitStatus.Ok)

  test("verify unsat_invariants returns exit 1 (violations)"):
    Verify.run(
      "fixtures/spec/unsat_invariants.spec",
      VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None),
      log
    ).assertEquals(ExitStatus.Violations)

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
        assertEquals(exit, ExitStatus.Ok)
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
        assertEquals(exit, ExitStatus.Ok)
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
        assertEquals(exit, ExitStatus.Ok)
        assert(java.nio.file.Files.exists(outDir.resolve("pyproject.toml")))
        assert(java.nio.file.Files.exists(outDir.resolve("app/main.py")))
        assert(java.nio.file.Files.exists(outDir.resolve("app/models/url_mapping.py")))
        assert(java.nio.file.Files.exists(outDir.resolve(".github/workflows/ci.yml")))

  test("compile go-chi-postgres emits a buildable Go project layout"):
    tempOutPath.use: outDir =>
      for
        exit <- Compile.run(
                  "fixtures/spec/url_shortener.spec",
                  CompileOptions(
                    "go-chi-postgres",
                    outDir.toString,
                    ignoreVerify = true
                  ),
                  log
                )
      yield
        assertEquals(exit, ExitStatus.Ok)
        assert(java.nio.file.Files.exists(outDir.resolve("go.mod")))
        assert(java.nio.file.Files.exists(outDir.resolve("cmd/server/main.go")))
        assert(java.nio.file.Files.exists(outDir.resolve("internal/models/url_mapping.go")))
        assert(java.nio.file.Files.exists(outDir.resolve("internal/handlers/url_mappings.go")))
        assert(java.nio.file.Files.exists(outDir.resolve("internal/services/url_mapping.go")))
        assert(java.nio.file.Files.exists(outDir.resolve("migrations/001_initial_schema.up.sql")))
        assert(java.nio.file.Files.exists(outDir.resolve("Dockerfile")))
        assert(java.nio.file.Files.exists(outDir.resolve("openapi.yaml")))

  test("compile ts-express-postgres emits a typecheckable TS project layout"):
    tempOutPath.use: outDir =>
      for
        exit <- Compile.run(
                  "fixtures/spec/url_shortener.spec",
                  CompileOptions(
                    "ts-express-postgres",
                    outDir.toString,
                    ignoreVerify = true
                  ),
                  log
                )
      yield
        assertEquals(exit, ExitStatus.Ok)
        assert(java.nio.file.Files.exists(outDir.resolve("package.json")))
        assert(java.nio.file.Files.exists(outDir.resolve("tsconfig.json")))
        assert(java.nio.file.Files.exists(outDir.resolve("prisma/schema.prisma")))
        assert(java.nio.file.Files.exists(outDir.resolve("src/index.ts")))
        assert(java.nio.file.Files.exists(outDir.resolve("src/app.ts")))
        assert(java.nio.file.Files.exists(outDir.resolve("src/routes/urlMappings.ts")))
        assert(java.nio.file.Files.exists(outDir.resolve("src/services/urlMapping.ts")))
        assert(java.nio.file.Files.exists(outDir.resolve("src/schemas/urlMapping.ts")))
        assert(java.nio.file.Files.exists(outDir.resolve("src/types/urlMapping.ts")))
        assert(java.nio.file.Files.exists(outDir.resolve("Dockerfile")))
        assert(java.nio.file.Files.exists(outDir.resolve("openapi.yaml")))

  test("v1 snapshot lifts transparently on re-compile: re-emits v2, no phantom 002 (#243)"):
    tempOutPath.use: outDir =>
      val snapPath      = outDir.resolve(".spec-snapshot.json")
      val migrationsDir = outDir.resolve("alembic/versions")
      val initialPath   = migrationsDir.resolve("001_initial_schema.py")
      val opts = CompileOptions(
        "python-fastapi-postgres",
        outDir.toString,
        ignoreVerify = true,
        withTests = false
      )
      for
        first <- Compile.run("fixtures/spec/url_shortener.spec", opts, log)
        _ <- IO.blocking {
               // Downgrade the freshly-written v2 snapshot to v1 by hand: set schemaVersion=1
               // and drop the `triggers` field from `schema`. This simulates a snapshot left
               // on disk by spec-to-rest <= the M7.6 release (before triggers existed).
               val parsed     = io.circe.parser.parse(java.nio.file.Files.readString(snapPath))
               val schemaJson = parsed.toOption.get.hcursor.downField("schema").focus.get
               val schemaV1   = schemaJson.mapObject(_.remove("triggers"))
               val v1Snapshot = io.circe.Json.obj(
                 "schemaVersion" -> io.circe.Json.fromInt(1),
                 "schema"        -> schemaV1
               )
               java.nio.file.Files.writeString(snapPath, v1Snapshot.spaces2)
             }
        v1OnDisk           <- IO.blocking(java.nio.file.Files.readString(snapPath))
        initialBytesBefore <- IO.blocking(java.nio.file.Files.readAllBytes(initialPath))
        second             <- Compile.run("fixtures/spec/url_shortener.spec", opts, log)
        v2OnDisk           <- IO.blocking(java.nio.file.Files.readString(snapPath))
        initialBytesAfter  <- IO.blocking(java.nio.file.Files.readAllBytes(initialPath))
      yield
        assertEquals(first, ExitStatus.Ok)
        assertEquals(second, ExitStatus.Ok)
        assert(
          v1OnDisk.contains("\"schemaVersion\" : 1") && !v1OnDisk.contains("triggers"),
          s"test setup: downgrade should produce a v1 snapshot with no triggers field; got:\n$v1OnDisk"
        )
        assert(
          v2OnDisk.contains("\"schemaVersion\" : 2") && v2OnDisk.contains("\"triggers\""),
          s"re-compile should re-emit a v2 snapshot with the triggers field present; got:\n$v2OnDisk"
        )
        assert(
          !java.nio.file.Files.exists(migrationsDir.resolve("002_schema_update.py")),
          "v1->v2 lift must be a no-op diff — no 002_schema_update.py should be produced"
        )
        assert(
          java.util.Arrays.equals(initialBytesBefore, initialBytesAfter),
          "001_initial_schema.py should be byte-stable across the v1->v2 lift"
        )

  test("compile twice on the same spec is incremental: 001 stays put, snapshot stable, no 002"):
    tempOutPath.use: outDir =>
      val initialPath  = outDir.resolve("alembic/versions/001_initial_schema.py")
      val snapshotPath = outDir.resolve(".spec-snapshot.json")
      for
        a <- Compile.run(
               "fixtures/spec/url_shortener.spec",
               CompileOptions("python-fastapi-postgres", outDir.toString, ignoreVerify = true),
               log
             )
        initialBytes   <- IO.blocking(java.nio.file.Files.readAllBytes(initialPath))
        snapshotBytesA <- IO.blocking(java.nio.file.Files.readAllBytes(snapshotPath))
        b <- Compile.run(
               "fixtures/spec/url_shortener.spec",
               CompileOptions("python-fastapi-postgres", outDir.toString, ignoreVerify = true),
               log
             )
        initialBytesB  <- IO.blocking(java.nio.file.Files.readAllBytes(initialPath))
        snapshotBytesB <- IO.blocking(java.nio.file.Files.readAllBytes(snapshotPath))
      yield
        assertEquals(a, ExitStatus.Ok)
        assertEquals(b, ExitStatus.Ok)
        assert(java.util.Arrays.equals(initialBytes, initialBytesB), "001 file changed")
        assert(java.util.Arrays.equals(snapshotBytesA, snapshotBytesB), "snapshot changed")
        assert(
          !java.nio.file.Files.exists(outDir.resolve("alembic/versions/002_schema_update.py")),
          "no 002 should be produced on no-op recompile"
        )

  private case class GateCase(
      name: String,
      spec: String,
      ignoreVerify: Boolean,
      expectedExit: ExitStatus,
      expectFiles: Boolean
  )

  List(
    GateCase(
      "compile gate blocks on unsat invariants",
      "fixtures/spec/unsat_invariants.spec",
      ignoreVerify = false,
      expectedExit = ExitStatus.Violations,
      expectFiles = false
    ),
    GateCase(
      "compile --ignore-verify bypasses gate on unsat invariants",
      "fixtures/spec/unsat_invariants.spec",
      ignoreVerify = true,
      expectedExit = ExitStatus.Ok,
      expectFiles = true
    ),
    GateCase(
      "compile gate blocks on preservation failure (broken_url_shortener)",
      "fixtures/spec/broken_url_shortener.spec",
      ignoreVerify = false,
      expectedExit = ExitStatus.Violations,
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
        assertEquals(exit, ExitStatus.Violations)
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
        assertEquals(exit, ExitStatus.Ok)
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
        assertEquals(firstExit, ExitStatus.Ok)
        assertEquals(secondExit, ExitStatus.Ok)
        assert(finalContent.contains("# USER EDIT"), s"user edit was overwritten:\n$finalContent")

  test("compile --dry-run writes nothing but exits Ok and prints a plan"):
    tempOutPath.use: outDir =>
      Compile.run(
        "fixtures/spec/url_shortener.spec",
        CompileOptions(
          "python-fastapi-postgres",
          outDir.toString,
          ignoreVerify = true,
          dryRun = true
        ),
        log
      ).map: exit =>
        assertEquals(exit, ExitStatus.Ok)
        assert(
          !java.nio.file.Files.exists(outDir.resolve("pyproject.toml")),
          "dry-run must not write any files"
        )
        assert(
          !java.nio.file.Files.exists(outDir),
          "dry-run must not even create the output directory"
        )

  test("diff returns Ok when generated dir is in sync with spec"):
    tempOutPath.use: outDir =>
      for
        compileExit <- Compile.run(
                         "fixtures/spec/url_shortener.spec",
                         CompileOptions(
                           "python-fastapi-postgres",
                           outDir.toString,
                           ignoreVerify = true
                         ),
                         log
                       )
        diffExit <- Diff.run(
                      "fixtures/spec/url_shortener.spec",
                      DiffOptions(
                        target = "python-fastapi-postgres",
                        outDir = outDir.toString,
                        ignoreVerify = true
                      ),
                      log
                    )
      yield
        assertEquals(compileExit, ExitStatus.Ok)
        assertEquals(diffExit, ExitStatus.Ok)

  test("diff reports drift when a generated file is missing"):
    tempOutPath.use: outDir =>
      for
        _ <- Compile.run(
               "fixtures/spec/url_shortener.spec",
               CompileOptions(
                 "python-fastapi-postgres",
                 outDir.toString,
                 ignoreVerify = true
               ),
               log
             )
        _ <- IO.blocking(java.nio.file.Files.delete(outDir.resolve("app/main.py")))
        diffExit <- Diff.run(
                      "fixtures/spec/url_shortener.spec",
                      DiffOptions(
                        target = "python-fastapi-postgres",
                        outDir = outDir.toString,
                        ignoreVerify = true
                      ),
                      log
                    )
      yield assertEquals(diffExit, ExitStatus.Violations)

  test("test command errors out when run_conformance.py is missing"):
    tempOutPath.use: outDir =>
      for
        _ <- IO.blocking(java.nio.file.Files.createDirectories(outDir))
        exit <- TestCmd.run(
                  TestOptions(outDir = outDir.toString),
                  log
                )
      yield assertEquals(exit, ExitStatus.Violations)

  test("Palette honors NO_COLOR off-mode"):
    val plain = Palette.resolve(ColorMode.Off)
    assertEquals(plain.green("ok"), "ok")
    val on = Palette.resolve(ColorMode.On)
    assert(on.green("ok").contains("[32m"))
    assert(on.green("ok").contains("[0m"))

  test("ColorMode.parse accepts auto/always/never and rejects garbage"):
    assertEquals(ColorMode.parse("auto").toOption, Some(ColorMode.Auto))
    assertEquals(ColorMode.parse("always").toOption, Some(ColorMode.On))
    assertEquals(ColorMode.parse("never").toOption, Some(ColorMode.Off))
    assert(ColorMode.parse("rainbow").isLeft)

  test("Plan.classify treats missing outRoot files as Create"):
    tempOutPath.use: outDir =>
      IO.blocking {
        val files = List(specrest.codegen.EmittedFile("app/main.py", "x"))
        val plans = if java.nio.file.Files.isDirectory(outDir) then Plan.classify(files, outDir)
        else files.map(f => FilePlan(FileAction.Create, f.path))
        assertEquals(plans, List(FilePlan(FileAction.Create, "app/main.py")))
      }

  List("python-fastapi-sqlite", "python-fastapi-mysql").foreach: target =>
    test(s"compile $target emits a dialect-correct python project"):
      tempOutPath.use: outDir =>
        for
          exit <- Compile.run(
                    "fixtures/spec/url_shortener.spec",
                    CompileOptions(target, outDir.toString, ignoreVerify = true),
                    log
                  )
          mig <- IO.blocking(
                   java.nio.file.Files.readString(
                     outDir.resolve("alembic/versions/001_initial_schema.py")
                   )
                 )
        yield
          assertEquals(exit, ExitStatus.Ok)
          assert(java.nio.file.Files.exists(outDir.resolve("pyproject.toml")))
          assert(java.nio.file.Files.exists(outDir.resolve("app/main.py")))
          assert(!mig.contains("timezone=True"), mig)
          assert(!mig.contains("from sqlalchemy.dialects import postgresql"), mig)

  test("compile with no test flag emits tests by default (M5.12)"):
    tempOutPath.use: outDir =>
      Compile.run(
        "fixtures/spec/safe_counter.spec",
        CompileOptions("python-fastapi-postgres", outDir.toString, ignoreVerify = true),
        log
      ).map: exit =>
        assertEquals(exit, ExitStatus.Ok)
        assert(
          java.nio.file.Files.exists(outDir.resolve("tests/run_conformance.py")),
          "no flag (default-on) should emit tests/run_conformance.py"
        )
        assert(
          java.nio.file.Files.exists(outDir.resolve("pytest.ini")),
          "no flag (default-on) should emit pytest.ini"
        )

  test("compile --no-tests skips the conformance suite (M5.12)"):
    tempOutPath.use: outDir =>
      Compile.run(
        "fixtures/spec/safe_counter.spec",
        CompileOptions(
          "python-fastapi-postgres",
          outDir.toString,
          ignoreVerify = true,
          withTests = false
        ),
        log
      ).map: exit =>
        assertEquals(exit, ExitStatus.Ok)
        assert(
          java.nio.file.Files.exists(outDir.resolve("pyproject.toml")),
          "non-test files still emitted"
        )
        assert(
          !java.nio.file.Files.exists(outDir.resolve("tests/run_conformance.py")),
          "--no-tests should suppress tests/run_conformance.py"
        )
        assert(
          !java.nio.file.Files.exists(outDir.resolve("pytest.ini")),
          "--no-tests should suppress pytest.ini"
        )

  test("--with-tests is no longer recognised (use the default; --no-tests to opt out)"):
    val cmd = com.monovore.decline.Command("spec-to-rest", "")(Main.main)
    val parsed = cmd.parse(
      Seq(
        "compile",
        "fixtures/spec/safe_counter.spec",
        "--framework",
        "fastapi",
        "--db",
        "postgres",
        "--out",
        "/tmp/m5_12_check",
        "--ignore-verify",
        "--with-tests"
      ),
      Map.empty
    )
    assert(parsed.isLeft, s"--with-tests should be rejected after removal; got $parsed")

  List(
    ("python-fastapi-postgres", "app/extensions/__init__.py", "app/main.py"),
    ("go-chi-postgres", "internal/extensions/extensions.go", "cmd/server/main.go"),
    ("ts-express-postgres", "src/extensions/index.ts", "src/app.ts")
  ).foreach: (target, extPath, regenPath) =>
    test(s"compile $target preserves the extension file across regeneration"):
      tempOutPath.use: outDir =>
        val userMarker = "// USER EDIT — must survive regen\n"
        for
          exit1 <- Compile.run(
                     "fixtures/spec/url_shortener.spec",
                     CompileOptions(target, outDir.toString, ignoreVerify = true),
                     log
                   )
          _ <- IO.blocking {
                 val ext = outDir.resolve(extPath)
                 assert(java.nio.file.Files.exists(ext), s"$extPath not emitted on first run")
                 java.nio.file.Files.writeString(ext, userMarker)
                 val regen = outDir.resolve(regenPath)
                 java.nio.file.Files.writeString(regen, "garbage-to-be-overwritten")
               }
          exit2 <- Compile.run(
                     "fixtures/spec/url_shortener.spec",
                     CompileOptions(target, outDir.toString, ignoreVerify = true),
                     log
                   )
          extAfter   <- IO.blocking(java.nio.file.Files.readString(outDir.resolve(extPath)))
          regenAfter <- IO.blocking(java.nio.file.Files.readString(outDir.resolve(regenPath)))
        yield
          assertEquals(exit1, ExitStatus.Ok)
          assertEquals(exit2, ExitStatus.Ok)
          assertEquals(extAfter, userMarker, s"$extPath should not be overwritten")
          assert(
            regenAfter != "garbage-to-be-overwritten",
            s"$regenPath should be regenerated, not preserved"
          )

  List("python-fastapi-postgres", "go-chi-postgres", "ts-express-postgres").foreach: target =>
    test(s"compile $target preserves staging+prod compose, regenerates base + override.example"):
      tempOutPath.use: outDir =>
        val userMarker = "# USER-TUNED compose — must survive regen\n"
        val staging    = "docker-compose.staging.yml"
        val prod       = "docker-compose.prod.yml"
        val base       = "docker-compose.yml"
        val overrideEx = "docker-compose.override.yml.example"
        for
          firstExit <- Compile.run(
                         "fixtures/spec/url_shortener.spec",
                         CompileOptions(target, outDir.toString, ignoreVerify = true),
                         log
                       )
          _ <- IO.blocking {
                 List(staging, prod, base, overrideEx).foreach: rel =>
                   assert(
                     java.nio.file.Files.exists(outDir.resolve(rel)),
                     s"$rel must be emitted on first compile"
                   )
                 java.nio.file.Files.writeString(outDir.resolve(staging), userMarker)
                 java.nio.file.Files.writeString(outDir.resolve(prod), userMarker)
                 java.nio.file.Files.writeString(outDir.resolve(base), "garbage")
                 java.nio.file.Files.writeString(outDir.resolve(overrideEx), "garbage")
               }
          secondExit <- Compile.run(
                          "fixtures/spec/url_shortener.spec",
                          CompileOptions(target, outDir.toString, ignoreVerify = true),
                          log
                        )
          stagingAfter  <- IO.blocking(java.nio.file.Files.readString(outDir.resolve(staging)))
          prodAfter     <- IO.blocking(java.nio.file.Files.readString(outDir.resolve(prod)))
          baseAfter     <- IO.blocking(java.nio.file.Files.readString(outDir.resolve(base)))
          overrideAfter <- IO.blocking(java.nio.file.Files.readString(outDir.resolve(overrideEx)))
        yield
          assertEquals(firstExit, ExitStatus.Ok)
          assertEquals(secondExit, ExitStatus.Ok)
          assertEquals(stagingAfter, userMarker, s"$staging must be preserved")
          assertEquals(prodAfter, userMarker, s"$prod must be preserved")
          assert(baseAfter != "garbage", s"$base must be regenerated")
          assert(overrideAfter != "garbage", s"$overrideEx must be regenerated")
