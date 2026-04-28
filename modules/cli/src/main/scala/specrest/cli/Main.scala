package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import com.monovore.decline.*
import com.monovore.decline.effect.CommandIOApp

object Main
    extends CommandIOApp(
      name = "spec-to-rest",
      header = "Compile formal behavioral specs into verified REST services"
    ):

  private val verbose = Opts.flag("verbose", "show detailed progress", short = "v").orFalse
  private val quiet   = Opts.flag("quiet", "suppress non-error output", short = "q").orFalse

  private val specFile = Opts.argument[String]("spec-file")

  private val inspectCmd: Opts[IO[ExitCode]] =
    val format = Opts
      .option[String]("format", "output format (summary, json, ir)", short = "f")
      .withDefault("summary")
      .mapValidated: raw =>
        InspectFormat.parse(raw) match
          case Right(f)  => cats.data.Validated.valid(f)
          case Left(err) => cats.data.Validated.invalidNel(err)
    Opts.subcommand("inspect", "Print the IR for a spec file"):
      (specFile, format, verbose, quiet).mapN: (spec, fmt, v, q) =>
        Inspect.run(spec, fmt, Logger.fromFlags(verbose = v, quiet = q))

  private val checkCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("check", "Parse and validate a spec file"):
      (specFile, verbose, quiet).mapN: (spec, v, q) =>
        Check.run(spec, Logger.fromFlags(verbose = v, quiet = q))

  private val verifyCmd: Opts[IO[ExitCode]] =
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
    val parallel = Opts
      .option[Int](
        "parallel",
        "max concurrent checks (default: host available processors; 0 = serial)"
      )
      .mapValidated: n =>
        if n >= 0 then cats.data.Validated.valid(n)
        else cats.data.Validated.invalidNel(s"--parallel must be >= 0 (got $n)")
      .orNone
    val noSuggestions = Opts
      .flag("no-suggestions", "suppress per-diagnostic 'hint:' suggestions in CLI and JSON output")
      .orFalse
    val noNarration = Opts
      .flag(
        "no-narration",
        "suppress structural 'why this fails' narration in CLI and JSON output"
      )
      .orFalse
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
        parallel,
        noSuggestions,
        noNarration,
        verbose,
        quiet
      ).mapN: (spec, t, ds, dso, da, dao, as, dvc, ex, j, jo, par, ns, nn, v, q) =>
        Verify.run(
          spec,
          VerifyOptions(
            t,
            ds,
            dso,
            da,
            dao,
            as,
            dvc,
            ex,
            j,
            jo,
            par,
            suggestions = !ns,
            narration = !nn
          ),
          Logger.fromFlags(verbose = v, quiet = q)
        )

  private val compileCmd: Opts[IO[ExitCode]] =
    val target = Opts
      .option[String]("target", "deployment target profile", short = "t")
      .withDefault("python-fastapi-postgres")
    val outDir = Opts.option[String]("out", "output directory", short = "o")
    val ignoreVerify = Opts
      .flag("ignore-verify", "skip verification gate (emit unverified code with a warning)")
      .orFalse
    val withTests = Opts
      .flag(
        "with-tests",
        "also emit Hypothesis property tests + admin router (python-fastapi-postgres only)"
      )
      .orFalse
    val strictStrategies = Opts
      .flag(
        "strict-strategies",
        "fail compile if any entity-typed strategy has unhandled `where` constraints and no convention override registered (requires --with-tests)"
      )
      .orFalse
    Opts.subcommand("compile", "Emit project files for a spec"):
      (specFile, target, outDir, ignoreVerify, withTests, strictStrategies, verbose, quiet).mapN:
        (spec, t, o, iv, wt, ss, v, q) =>
          Compile.run(
            spec,
            CompileOptions(t, o, iv, wt, ss),
            Logger.fromFlags(verbose = v, quiet = q)
          )

  override def main: Opts[IO[ExitCode]] =
    inspectCmd orElse checkCmd orElse verifyCmd orElse compileCmd
