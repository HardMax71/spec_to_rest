package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import com.monovore.decline.*
import com.monovore.decline.effect.CommandIOApp
import specrest.profile.DatabaseId
import specrest.profile.LanguageId
import specrest.profile.Registry
import specrest.profile.TargetKey

object Main
    extends CommandIOApp(
      name = "spec-to-rest",
      header = "Compile formal behavioral specs into verified REST services"
    ):

  private val verbose = Opts.flag("verbose", "show detailed progress", short = "v").orFalse
  private val quiet   = Opts.flag("quiet", "suppress non-error output", short = "q").orFalse

  private val colorFlag                  = Opts.flag("color", "force colored output").orFalse
  private val noColorFlag                = Opts.flag("no-color", "disable colored output").orFalse
  private val colorMode: Opts[ColorMode] =
    (colorFlag, noColorFlag).tupled.mapValidated:
      case (true, true) =>
        cats.data.Validated.invalidNel("--color and --no-color are mutually exclusive")
      case (true, false)  => cats.data.Validated.valid(ColorMode.On)
      case (false, true)  => cats.data.Validated.valid(ColorMode.Off)
      case (false, false) => cats.data.Validated.valid(ColorMode.Auto)

  // Tests are emitted by default; pass --no-tests to skip. Shared by `compile` and `diff`
  // (the prior --with-tests flag was dropped — opting in to a default is meaningless).
  private def emitTestsOpt(noTestsHelp: String): Opts[Boolean] =
    Opts.flag("no-tests", noTestsHelp).orFalse.map(skip => !skip)

  private val specFile = Opts.argument[String]("spec-file")

  private val knownFrameworks = Registry.frameworkIds.mkString(", ")
  private val knownDatabases  = DatabaseId.values.toList.map(_.slug).sorted.mkString(", ")
  private val knownLanguages  = LanguageId.values.toList.map(_.slug).sorted.mkString(", ")

  private val frameworkOpt = Opts
    .option[String]("framework", s"framework: $knownFrameworks", short = "f")
    .withDefault("fastapi")
  private val dbOpt = Opts
    .option[String]("db", s"database: $knownDatabases")
    .withDefault("postgres")
  private val langOpt = Opts
    .option[String](
      "lang",
      s"language: $knownLanguages (only needed when the framework supports more than one)"
    )
    .orNone

  private def resolveTarget(
      framework: String,
      db: String,
      lang: Option[String]
  ): Either[String, String] =
    for
      fw <- Registry
              .framework(framework)
              .toRight(s"unknown framework '$framework' (known: $knownFrameworks)")
      database <- DatabaseId
                    .parse(db)
                    .toRight(s"unknown database '$db' (known: $knownDatabases)")
      language <- lang match
                    case Some(l) =>
                      LanguageId.parse(l).toRight(s"unknown language '$l' (known: $knownLanguages)")
                    case None =>
                      fw.supportedLanguages.toList match
                        case single :: Nil => Right(single)
                        case many          =>
                          Left(
                            s"framework '$framework' supports multiple languages " +
                              s"(${many.map(_.slug).sorted.mkString(", ")}); specify --lang"
                          )
      key = TargetKey(language, framework, database)
      _  <- Registry.resolve(key)
    yield key.slug

  private val targetSlug: Opts[String] =
    (frameworkOpt, dbOpt, langOpt).mapN((f, d, l) => (f, d, l)).mapValidated { case (f, d, l) =>
      resolveTarget(f, d, l) match
        case Right(slug) => cats.data.Validated.valid(slug)
        case Left(err)   => cats.data.Validated.invalidNel(err)
    }

  private val inspectCmd: Opts[IO[ExitCode]] =
    val format = Opts
      .option[String](
        "format",
        "output format (summary, json, ir, dafny, dafny-prompt)",
        short = "f"
      )
      .withDefault("summary")
      .mapValidated: raw =>
        InspectFormat.parse(raw) match
          case Right(f)  => cats.data.Validated.valid(f)
          case Left(err) => cats.data.Validated.invalidNel(err)
    val operation = Opts
      .option[String]("operation", "filter to a single operation (used by dafny-prompt)")
      .orNone
    Opts.subcommand("inspect", "Print the IR for a spec file"):
      (specFile, format, operation, verbose, quiet, colorMode).mapN: (spec, fmt, op, v, q, c) =>
        Inspect.run(spec, fmt, Logger.fromFlags(verbose = v, quiet = q, color = c), operation = op)

  private val checkCmd: Opts[IO[ExitCode]] =
    Opts.subcommand("check", "Parse and validate a spec file"):
      (specFile, verbose, quiet, colorMode).mapN: (spec, v, q, c) =>
        Check.run(spec, Logger.fromFlags(verbose = v, quiet = q, color = c))

  private val verifyCmd: Opts[IO[ExitCode]] =
    val timeout = Opts
      .option[Long]("timeout", "per-check timeout ms (0 = no timeout)")
      .withDefault(30_000L)
    val dumpSmt    = Opts.flag("dump-smt", "emit Z3 SMT-LIB to stdout and exit").orFalse
    val dumpSmtOut =
      Opts.option[String]("dump-smt-out", "write Z3 SMT-LIB to file and exit").orNone
    val dumpAlloy    = Opts.flag("dump-alloy", "emit Alloy source to stdout and exit").orFalse
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
        quiet,
        colorMode
      ).mapN: (spec, t, ds, dso, da, dao, as, dvc, ex, j, jo, par, ns, nn, v, q, c) =>
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
          Logger.fromFlags(verbose = v, quiet = q, color = c)
        )

  private val compileCmd: Opts[IO[ExitCode]] =
    val outDir       = Opts.option[String]("out", "output directory", short = "o")
    val ignoreVerify = Opts
      .flag("ignore-verify", "skip verification gate (emit unverified code with a warning)")
      .orFalse
    val withTests = emitTestsOpt(
      "skip emitting the native conformance suite (behavioral/stateful/structural test files + the gated test-admin router). Tests are emitted by default."
    )
    val strictStrategies = Opts
      .flag(
        "strict-strategies",
        "fail compile if any synthesized strategy is incomplete (unhandled `where` constraint or unsupported base type) and no convention override is registered (no-op when --no-tests is passed)"
      )
      .orFalse
    val withSynthesis = Opts
      .flag(
        "with-synthesis",
        "splice verified Dafny bodies (translated to the target language) into the emitted project; requires `synth verify` to have been run for each LLM_SYNTHESIS operation and a `dafny` binary on PATH"
      )
      .orFalse
    val synthesisModel = Opts
      .option[String](
        "synthesis-model",
        "model used for `synth verify`; must match the cached entry's model"
      )
      .withDefault("claude-sonnet-4-6")
    val synthesisTemperature = Opts
      .option[Double](
        "synthesis-temperature",
        "temperature used for `synth verify`; must match the cached entry's temperature"
      )
      .withDefault(1.0)
    val synthesisCacheDir = Opts
      .option[String](
        "synthesis-cache-dir",
        "override synth cache root (default: fixtures/synth-cache)"
      )
      .orNone
    val compileDafnyBin = Opts
      .option[String]("dafny-bin", "path to dafny binary (defaults to $DAFNY_BIN or PATH)")
      .orNone
    val compileTranslateTimeout = Opts
      .option[Int]("dafny-translate-timeout", "wall-clock timeout for `dafny translate` (seconds)")
      .withDefault(60)
      .mapValidated: n =>
        if n > 0 then cats.data.Validated.valid(n)
        else cats.data.Validated.invalidNel(s"--dafny-translate-timeout must be > 0 (got $n)")
    val allowSkeletons = Opts
      .flag(
        "allow-skeletons",
        "fall back to unverified skeleton bodies (from `synth verify --fallback`) when the verified cache misses; the generated handler halts at runtime when invoked"
      )
      .orFalse
    val synthesisPartial = Opts
      .flag(
        "synthesis-partial",
        "use verified bodies where cached; emit a fail-loud stub (skipped by testgen) for LLM_SYNTHESIS ops with no verified body, instead of failing the build"
      )
      .orFalse
    val dryRun = Opts
      .flag(
        "dry-run",
        "show the file plan (create/update/unchanged/preserve) without writing anything"
      )
      .orFalse
    Opts.subcommand("compile", "Emit project files for a spec"):
      (
        specFile,
        targetSlug,
        outDir,
        ignoreVerify,
        withTests,
        strictStrategies,
        withSynthesis,
        synthesisModel,
        synthesisTemperature,
        synthesisCacheDir,
        compileDafnyBin,
        compileTranslateTimeout,
        allowSkeletons,
        synthesisPartial,
        dryRun,
        verbose,
        quiet,
        colorMode
      ).mapN: (spec, t, o, iv, wt, ss, ws, sm, stp, scd, db, dtt, ask, sp, dr, v, q, c) =>
        Compile.run(
          spec,
          CompileOptions(
            target = t,
            outDir = o,
            ignoreVerify = iv,
            withTests = wt,
            strictStrategies = ss,
            withSynthesis = ws,
            synthesisModel = sm,
            synthesisTemperature = stp,
            synthesisCacheDir = scd,
            dafnyBin = db,
            dafnyTranslateTimeoutSec = dtt,
            allowSkeletons = ask,
            synthesisPartial = sp,
            dryRun = dr
          ),
          Logger.fromFlags(verbose = v, quiet = q, color = c)
        )

  private val synthCmd: Opts[IO[ExitCode]] =
    val operation = Opts.option[String]("operation", "operation to synthesize", short = "o")
    val model     = Opts
      .option[String]("model", "LLM model (gpt-* routes to OpenAI; otherwise Anthropic)")
      .withDefault("claude-sonnet-4-6")
    val temperature = Opts
      .option[Double]("temperature", "sampling temperature (ignored on newer Anthropic models)")
      .withDefault(1.0)
    val maxTokens = Opts
      .option[Int]("max-tokens", "max output tokens")
      // 2048 truncates a full Dafny body (and any reasoning-model preamble) before the
      // closing code fence, yielding "no fenced code block found" and a wasted LLM call.
      .withDefault(16384)
      .mapValidated: n =>
        if n > 0 then cats.data.Validated.valid(n)
        else cats.data.Validated.invalidNel(s"--max-tokens must be > 0 (got $n)")
    val noCache  = Opts.flag("no-cache", "bypass on-disk synthesis cache").orFalse
    val cacheDir = Opts.option[String]("cache-dir", "override synth cache directory").orNone
    val dafnyBin = Opts
      .option[String]("dafny-bin", "path to dafny binary (defaults to $DAFNY_BIN or PATH)")
      .orNone
    val dafnyTimeout = Opts
      .option[Int]("dafny-timeout", "per-iteration dafny verification timeout (seconds)")
      .withDefault(60)
      .mapValidated: n =>
        if n > 0 then cats.data.Validated.valid(n)
        else cats.data.Validated.invalidNel(s"--dafny-timeout must be > 0 (got $n)")
    val maxIter = Opts
      .option[Int]("max-iter", "maximum CEGIS iterations before abort")
      .withDefault(8)
      .mapValidated: n =>
        if n > 0 then cats.data.Validated.valid(n)
        else cats.data.Validated.invalidNel(s"--max-iter must be > 0 (got $n)")
    val maxCost = Opts
      .option[Double]("cost-cap-usd", "abort if cumulative LLM cost exceeds this many USD")
      .withDefault(1.00)
      .mapValidated: x =>
        if x > 0.0 then cats.data.Validated.valid(x)
        else cats.data.Validated.invalidNel(s"--cost-cap-usd must be > 0 (got $x)")
    val tryCmd: Opts[IO[ExitCode]] =
      Opts.subcommand("try", "Generate one Dafny body candidate via LLM"):
        (
          specFile,
          operation,
          model,
          temperature,
          maxTokens,
          noCache,
          cacheDir,
          verbose,
          quiet,
          colorMode
        )
          .mapN: (spec, op, m, t, mt, nc, cd, v, q, c) =>
            Synth.run(
              spec,
              SynthOptions(op, m, t, mt, nc, cd),
              Logger.fromFlags(verbose = v, quiet = q, color = c)
            )
    val fallbackFlag = Opts
      .flag(
        "fallback",
        "after CEGIS aborts, try alternate prompt strategies and escalate the model; emit a labelled skeleton if all attempts fail"
      )
      .orFalse
    val escalateTo = Opts
      .options[String](
        "escalate-to",
        "model to escalate to after the configured model exhausts the prompt-strategy ladder " +
          "(repeat for a multi-step ladder)"
      )
      .orEmpty
    val withHintsFlag = Opts
      .flag(
        "with-hints",
        "inject DafnyPro-style proof patterns into the repair prompt (default ON for --fallback / verify-all, OFF for strict CEGIS)"
      )
      .orFalse
    val noHintsFlag                          = Opts.flag("no-hints", "disable hint-augmentation injection").orFalse
    val hintsTriState: Opts[Option[Boolean]] = (withHintsFlag, noHintsFlag).tupled.mapValidated:
      case (true, true) =>
        cats.data.Validated.invalidNel("--with-hints and --no-hints are mutually exclusive")
      case (true, false)  => cats.data.Validated.valid(Some(true))
      case (false, true)  => cats.data.Validated.valid(Some(false))
      case (false, false) => cats.data.Validated.valid(None)
    val verifyCmd: Opts[IO[ExitCode]] =
      Opts.subcommand(
        "verify",
        "Run the CEGIS loop: generate, dafny-verify, repair until verified"
      ):
        (
          specFile,
          operation,
          model,
          temperature,
          maxTokens,
          noCache,
          cacheDir,
          dafnyBin,
          dafnyTimeout,
          maxIter,
          maxCost,
          fallbackFlag,
          escalateTo,
          hintsTriState,
          verbose,
          quiet,
          colorMode
        ).mapN: (spec, op, m, t, mt, nc, cd, db, dt, mi, mc, fb, esc, hints, v, q, c) =>
          Synth.runVerify(
            spec,
            SynthVerifyOptions(op, m, t, mt, nc, cd, db, dt, mi, mc, fb, esc, hints),
            Logger.fromFlags(verbose = v, quiet = q, color = c)
          )
    val verifyAllCmd: Opts[IO[ExitCode]] =
      Opts.subcommand(
        "verify-all",
        "Run the fallback orchestrator across every LLM_SYNTHESIS op and print a synthesis report"
      ):
        (
          specFile,
          model,
          temperature,
          maxTokens,
          noCache,
          cacheDir,
          dafnyBin,
          dafnyTimeout,
          maxIter,
          maxCost,
          escalateTo,
          hintsTriState,
          verbose,
          quiet,
          colorMode
        ).mapN: (spec, m, t, mt, nc, cd, db, dt, mi, mc, esc, hints, v, q, c) =>
          Synth.runVerifyAll(
            spec,
            SynthVerifyAllOptions(m, t, mt, nc, cd, db, dt, mi, mc, esc, hints),
            Logger.fromFlags(verbose = v, quiet = q, color = c)
          )
    Opts.subcommand("synth", "Experimental LLM synthesis (Phase 6)"):
      tryCmd orElse verifyCmd orElse verifyAllCmd

  private val diffCmd: Opts[IO[ExitCode]] =
    val outDir =
      Opts.option[String]("out", "existing output directory to compare against", short = "o")
    val ignoreVerify = Opts
      .flag("ignore-verify", "skip verification (compare regardless of spec verification)")
      .orFalse
    val withTests = emitTestsOpt(
      "exclude test files from drift detection (test files are included by default)"
    )
    Opts.subcommand(
      "diff",
      "Show which files would change if compile were run against an existing output directory"
    ):
      (specFile, targetSlug, outDir, ignoreVerify, withTests, verbose, quiet, colorMode).mapN:
        (spec, t, o, iv, wt, v, q, c) =>
          Diff.run(
            spec,
            DiffOptions(
              target = t,
              outDir = o,
              ignoreVerify = iv,
              withTests = wt
            ),
            Logger.fromFlags(verbose = v, quiet = q, color = c)
          )

  private val testCmd: Opts[IO[ExitCode]] =
    val outDir  = Opts.option[String]("out", "generated project directory", short = "o")
    val profile = Opts
      .option[String]("profile", "conformance profile (smoke, thorough, exhaustive)")
      .withDefault("thorough")
    val serverUrl = Opts
      .option[String](
        "server-url",
        "base URL for the running service (default: each runner's per-target default — http://localhost:8000 for python-fastapi, http://localhost:8080 for ts-express / go-chi)"
      )
      .orNone
    val runnerBin = Opts
      .option[String](
        "runner-bin",
        "override the interpreter that invokes the conformance runner (default: auto-picked from the runner extension — python3 for .py, node for .mjs, bash for .sh)"
      )
      .orNone
    Opts.subcommand(
      "test",
      "Run the emitted conformance runner against a running service. Detects the runner that compile emitted (tests/run_conformance.py | .mjs | .sh) and dispatches to the matching interpreter — works uniformly for python-fastapi, ts-express, and go-chi targets."
    ):
      (outDir, profile, serverUrl, runnerBin, verbose, quiet, colorMode).mapN:
        (o, p, s, rb, v, q, c) =>
          TestCmd.run(
            TestOptions(outDir = o, profile = p, serverUrl = s, runnerBin = rb),
            Logger.fromFlags(verbose = v, quiet = q, color = c)
          )

  private val diagInitCmd: Opts[IO[ExitCode]] =
    Opts.subcommand(
      "__diag-init",
      "(internal) substrate-VM class-init probe; prints cause chain to stderr (issue #222)"
    ):
      (verbose, quiet).mapN: (_, _) =>
        DiagInit.run()

  override def main: Opts[IO[ExitCode]] =
    inspectCmd orElse checkCmd orElse verifyCmd orElse compileCmd orElse
      diffCmd orElse testCmd orElse synthCmd orElse diagInitCmd
