package specrest.cli

import cats.implicits.*
import com.monovore.decline.*

object Main:

  private val verbose = Opts.flag("verbose", "show detailed progress", short = "v").orFalse
  private val quiet   = Opts.flag("quiet", "suppress non-error output", short = "q").orFalse

  private val specFile = Opts.argument[String]("spec-file")

  private val inspectCmd =
    val format = Opts
      .option[String]("format", "output format (summary, json, ir)", short = "f")
      .withDefault("summary")
      .mapValidated: raw =>
        InspectFormat.parse(raw) match
          case Right(f)  => cats.data.Validated.valid(f)
          case Left(err) => cats.data.Validated.invalidNel(err)
    Opts.subcommand("inspect", "Print the IR for a spec file"):
      (specFile, format, verbose, quiet).mapN: (spec, fmt, v, q) =>
        val log = Logger.fromFlags(verbose = v, quiet = q)
        Inspect.run(spec, fmt, log)

  private val checkCmd =
    Opts.subcommand("check", "Parse and validate a spec file"):
      (specFile, verbose, quiet).mapN: (spec, v, q) =>
        val log = Logger.fromFlags(verbose = v, quiet = q)
        Check.run(spec, log)

  private val verifyCmd =
    val timeout = Opts
      .option[Long]("timeout", "per-check timeout ms (0 = no timeout)")
      .withDefault(30_000L)
    val dumpSmt    = Opts.flag("dump-smt", "emit SMT-LIB to stdout and exit").orFalse
    val dumpSmtOut = Opts.option[String]("dump-smt-out", "write SMT-LIB to file and exit").orNone
    Opts.subcommand("verify", "Run the Z3-backed verification engine on a spec file"):
      (specFile, timeout, dumpSmt, dumpSmtOut, verbose, quiet).mapN:
        (spec, t, ds, dso, v, q) =>
          val log = Logger.fromFlags(verbose = v, quiet = q)
          Verify.run(spec, VerifyOptions(t, ds, dso), log)

  private val command = Command(
    name = "spec-to-rest",
    header = "Compile formal behavioral specs into verified REST services",
  )(inspectCmd orElse checkCmd orElse verifyCmd)

  def main(args: Array[String]): Unit =
    command.parse(args.toSeq) match
      case Left(help) =>
        System.err.println(help)
        sys.exit(1)
      case Right(exitCode) =>
        sys.exit(exitCode)
