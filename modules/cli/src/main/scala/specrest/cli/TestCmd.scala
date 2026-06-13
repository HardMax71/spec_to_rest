package specrest.cli

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

  private val RunnerPrefix = "run_conformance."

  def run(opts: TestOptions, log: Logger): IO[ExitStatus] =
    val outRoot = Paths.get(opts.outDir)
    if !Files.isDirectory(outRoot) then
      IO.delay(log.error(s"output directory not found: ${opts.outDir}"))
        .as(ExitStatus.Violations)
    else
      findRunner(outRoot.resolve("tests")) match
        case Left(msg) =>
          IO.delay(log.error(msg)).as(ExitStatus.Violations)
        case Right(runner) =>
          opts.runnerBin.toRight(()).orElse(shebangInterpreter(runner).toRight(())).toOption match
            case None =>
              IO.delay(
                log.error(
                  s"$runner has no shebang and --runner-bin was not given; cannot dispatch"
                )
              ).as(ExitStatus.Violations)
            case Some(interpreter) =>
              invokeRunner(outRoot, runner, interpreter, opts, log)

  // The runner file is the single source of truth: codegen emits exactly one
  // tests/run_conformance.<ext> per target and stamps its own shebang on it. The
  // wrapper carries no per-language mapping.
  private def findRunner(testsDir: Path): Either[String, Path] =
    if !Files.isDirectory(testsDir) then
      Left(s"tests directory not found: $testsDir (project may have been compiled with --no-tests)")
    else
      val stream = Files.list(testsDir)
      try
        val candidates = stream.iterator.asScala
          .filter(p =>
            Files.isRegularFile(p) && p.getFileName.toString.startsWith(RunnerPrefix)
          )
          .toList
        candidates match
          case Nil =>
            Left(
              s"no $RunnerPrefix* runner found under $testsDir " +
                "(project was likely compiled with --no-tests; re-compile without it)"
            )
          case List(one) => Right(one)
          case many =>
            Left(
              s"multiple conformance runners found under $testsDir: " +
                many.map(_.getFileName.toString).mkString(", ")
            )
      finally stream.close()

  // Parse a POSIX shebang of the form `#!/usr/bin/env <interp>` or `#!<interp>`
  // and return the last whitespace-separated token (the interpreter name).
  private def shebangInterpreter(runner: Path): Option[String] =
    val stream = Files.lines(runner)
    try
      val first = stream.iterator.asScala.nextOption().getOrElse("")
      Option.when(first.startsWith("#!")):
        first.stripPrefix("#!").trim.split("\\s+").lastOption.getOrElse("")
      .filter(_.nonEmpty)
    finally stream.close()

  private def invokeRunner(
      outRoot: Path,
      runner: Path,
      interpreter: String,
      opts: TestOptions,
      log: Logger
  ): IO[ExitStatus] = IO.blocking {
    val relative = outRoot.relativize(runner).toString
    val urlLabel = opts.serverUrl.getOrElse("<runner default>")
    log.info(s"running $relative against $urlLabel (profile=${opts.profile})")
    val cmd = List(interpreter, relative, opts.profile)
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
      ).as(ExitStatus.Backend)
  }

  private def mapExit(rc: Int, log: Logger): ExitStatus = rc match
    case 0 =>
      log.success("conformance: all phases passed")
      ExitStatus.Ok
    case 1 =>
      log.error("conformance: one or more phases reported test failures")
      ExitStatus.Violations
    case 2 =>
      log.error("conformance: service unreachable or invalid profile")
      ExitStatus.Backend
    case other =>
      log.error(s"conformance runner exited with unexpected status $other")
      ExitStatus.Backend
