package specrest.cli

class CliSmokeTest extends munit.FunSuite:

  private def log: Logger = Logger.fromFlags(verbose = false, quiet = true)

  test("check url_shortener is valid"):
    val exit = Check.run("fixtures/spec/url_shortener.spec", log)
    assertEquals(exit, 0)

  test("check on missing file returns 1"):
    val exit = Check.run("fixtures/does-not-exist.spec", log)
    assertEquals(exit, 1)

  test("inspect --format json returns 0 on valid spec"):
    val exit = Inspect.run("fixtures/spec/safe_counter.spec", InspectFormat.Json, log)
    assertEquals(exit, 0)

  test("InspectFormat.parse rejects unknown"):
    val err = InspectFormat.parse("yaml").left.toOption
    assert(err.exists(_.contains("unknown format")))

  test("verify safe_counter returns exit 0"):
    val exit = Verify.run(
      "fixtures/spec/safe_counter.spec",
      VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None),
      log,
    )
    assertEquals(exit, Verify.ExitOk)

  test("verify unsat_invariants returns exit 1 (violations)"):
    val exit = Verify.run(
      "fixtures/spec/unsat_invariants.spec",
      VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None),
      log,
    )
    assertEquals(exit, Verify.ExitViolations)

  test("verify --dump-smt writes SMT-LIB (via dumpSmtOut)"):
    val tmp = java.nio.file.Files.createTempFile("smt-test-", ".smt2")
    try
      val exit = Verify.run(
        "fixtures/spec/safe_counter.spec",
        VerifyOptions(0L, dumpSmt = false, dumpSmtOut = Some(tmp.toString)),
        log,
      )
      assertEquals(exit, Verify.ExitOk)
      val content = java.nio.file.Files.readString(tmp)
      assert(content.contains("(set-logic ALL)"))
      assert(content.contains("(check-sat)"))
    finally
      val _ = java.nio.file.Files.deleteIfExists(tmp)
