package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO

import java.nio.file.Files
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

final case class TestOptions(
    outDir: String,
    profile: String = "thorough",
    serverUrl: String = "http://localhost:8000",
    pythonBin: String = "python3"
)

object TestCmd:

  private val ConformanceRunner = "tests/run_conformance.py"

  def run(opts: TestOptions, log: Logger): IO[ExitCode] =
    val outRoot = Paths.get(opts.outDir)
    val runner  = outRoot.resolve(ConformanceRunner)
    if !Files.isDirectory(outRoot) then
      IO.delay(log.error(s"output directory not found: ${opts.outDir}"))
        .as(ExitCodes.Translator)
    else if !Files.isRegularFile(runner) then
      IO.delay(
        log.error(
          s"$ConformanceRunner not found under ${opts.outDir}; " +
            "the conformance runner is currently emitted only for the python-fastapi-postgres " +
            "target. Re-compile with --target python-fastapi-postgres --with-tests."
        )
      ).as(ExitCodes.Translator)
    else invokeRunner(outRoot, opts, log)

  private def invokeRunner(
      outRoot: java.nio.file.Path,
      opts: TestOptions,
      log: Logger
  ): IO[ExitCode] = IO.blocking {
    log.info(
      s"running conformance tests against ${opts.serverUrl} (profile=${opts.profile})"
    )
    val cmd = List(opts.pythonBin, ConformanceRunner, opts.profile)
    val pb  = new ProcessBuilder(cmd.asJava)
    pb.directory(outRoot.toFile)
    pb.inheritIO()
    val env = pb.environment().nn
    env.put("SPEC_TEST_BASE_URL", opts.serverUrl)
    env.put("SPEC_TEST_PROFILE", opts.profile)
    val proc = pb.start().nn
    val rc   = proc.waitFor()
    mapExit(rc, log)
  }.handleErrorWith {
    case e: java.io.IOException =>
      IO.delay(
        log.error(
          s"failed to launch ${opts.pythonBin}: ${Option(e.getMessage).getOrElse(e.toString)}. " +
            "Pass --python-bin to override."
        )
      ).as(ExitCodes.Translator)
  }

  private def mapExit(rc: Int, log: Logger): ExitCode = rc match
    case 0 =>
      log.success("conformance: all phases passed")
      ExitCodes.Ok
    case 1 =>
      log.error("conformance: one or more phases reported test failures")
      ExitCodes.Tests
    case 2 =>
      log.error("conformance: service unreachable or invalid profile")
      ExitCodes.Translator
    case other =>
      log.error(s"conformance runner exited with unexpected status $other")
      ExitCodes.Backend
