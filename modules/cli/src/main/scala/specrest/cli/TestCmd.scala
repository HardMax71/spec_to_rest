package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

final case class TestOptions(
    outDir: String,
    profile: String = "thorough",
    serverUrl: Option[String] = None,
    runnerBin: Option[String] = None
)

object TestCmd:

  // The three native runners share the same contract: argv[1] = profile,
  // SPEC_TEST_PROFILE + SPEC_TEST_BASE_URL via env, exit 0/1/2.
  private val Runners: List[(String, String)] = List(
    "tests/run_conformance.py"  -> "python3",
    "tests/run_conformance.mjs" -> "node",
    "tests/run_conformance.sh"  -> "bash"
  )

  def run(opts: TestOptions, log: Logger): IO[ExitCode] =
    val outRoot = Paths.get(opts.outDir)
    if !Files.isDirectory(outRoot) then
      IO.delay(log.error(s"output directory not found: ${opts.outDir}"))
        .as(ExitCodes.Translator)
    else
      Runners.find((rel, _) => Files.isRegularFile(outRoot.resolve(rel))) match
        case None =>
          IO.delay(
            log.error(
              s"no conformance runner found under ${opts.outDir} (looked for " +
                Runners.map(_._1).mkString(", ") +
                "); the project was likely compiled with --no-tests. Re-compile without it."
            )
          ).as(ExitCodes.Translator)
        case Some((relativeRunner, defaultBin)) =>
          invokeRunner(
            outRoot,
            relativeRunner,
            opts.runnerBin.getOrElse(defaultBin),
            opts,
            log
          )

  private def invokeRunner(
      outRoot: Path,
      relativeRunner: String,
      interpreter: String,
      opts: TestOptions,
      log: Logger
  ): IO[ExitCode] = IO.blocking {
    val urlLabel = opts.serverUrl.getOrElse("<runner default>")
    log.info(
      s"running $relativeRunner against $urlLabel (profile=${opts.profile})"
    )
    val cmd = List(interpreter, relativeRunner, opts.profile)
    val pb  = new ProcessBuilder(cmd.asJava)
    pb.directory(outRoot.toFile)
    pb.inheritIO()
    val env = pb.environment().nn
    env.put("SPEC_TEST_PROFILE", opts.profile)
    opts.serverUrl.foreach(u => env.put("SPEC_TEST_BASE_URL", u))
    val proc = pb.start().nn
    val rc   = proc.waitFor()
    mapExit(rc, log)
  }.handleErrorWith {
    case e: java.io.IOException =>
      IO.delay(
        log.error(
          s"failed to launch $interpreter: ${Option(e.getMessage).getOrElse(e.toString)}. " +
            "Pass --runner-bin to override."
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
