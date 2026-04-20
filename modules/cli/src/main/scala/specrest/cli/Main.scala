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
    val dumpSmt = Opts.flag("dump-smt", "emit Z3 SMT-LIB to stdout and exit").orFalse
    val dumpSmtOut =
      Opts.option[String]("dump-smt-out", "write Z3 SMT-LIB to file and exit").orNone
    val dumpAlloy = Opts.flag("dump-alloy", "emit Alloy source to stdout and exit").orFalse
    val dumpAlloyOut =
      Opts.option[String]("dump-alloy-out", "write Alloy source to file and exit").orNone
    val alloyScope = Opts
      .option[Int]("alloy-scope", "scope for bounded Alloy checks (default 5)")
      .withDefault(5)
    val dumpVc = Opts
      .option[String](
        "dump-vc",
        "write per-check VC artifacts (.smt2/.als + verdicts.json) into <dir>"
      )
      .orNone
    val explain = Opts
      .flag("explain", "extract unsat cores; surface contributing spec spans on unsat diagnostics")
      .orFalse
    val json =
      Opts.flag("json", "emit machine-readable JSON report to stdout (suppresses text)").orFalse
    val jsonOut = Opts
      .option[String]("json-out", "write JSON report to file (implies JSON mode; suppresses text)")
      .orNone
    Opts.subcommand("verify", "Run the Z3/Alloy-backed verification engine on a spec file"):
      (
        specFile,
        timeout,
        dumpSmt,
        dumpSmtOut,
        dumpAlloy,
        dumpAlloyOut,
        alloyScope,
        dumpVc,
        explain,
        json,
        jsonOut,
        verbose,
        quiet
      ).mapN: (spec, t, ds, dso, da, dao, as, dvc, ex, j, jo, v, q) =>
        val log = Logger.fromFlags(verbose = v, quiet = q)
        Verify.run(spec, VerifyOptions(t, ds, dso, da, dao, as, dvc, ex, j, jo), log)

  private val compileCmd =
    val target = Opts
      .option[String]("target", "deployment target profile", short = "t")
      .withDefault("python-fastapi-postgres")
    val outDir = Opts.option[String]("out", "output directory", short = "o")
    Opts.subcommand("compile", "Emit project files for a spec"):
      (specFile, target, outDir, verbose, quiet).mapN: (spec, t, o, v, q) =>
        val log = Logger.fromFlags(verbose = v, quiet = q)
        Compile.run(spec, CompileOptions(t, o), log)

  private val command = Command(
    name = "spec-to-rest",
    header = "Compile formal behavioral specs into verified REST services"
  )(inspectCmd orElse checkCmd orElse verifyCmd orElse compileCmd)

  def main(args: Array[String]): Unit =
    command.parse(args.toSeq) match
      case Left(help) =>
        System.err.println(help)
        sys.exit(1)
      case Right(exitCode) =>
        sys.exit(exitCode)
