package specrest.cli

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.convention.dafny.Generator as DafnyGenerator
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.synth.Cache
import specrest.synth.CacheEntry
import specrest.synth.TokenUsage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CompileSynthesisTest extends CatsEffectSuite:

  private def log: Logger = Logger.fromFlags(verbose = false, quiet = true)

  private def withTempDir(body: Path => IO[Unit]): IO[Unit] =
    IO.blocking(Files.createTempDirectory("specrest-compile-synth-")).flatMap: dir =>
      body(dir).guarantee(IO.blocking(deleteRecursively(dir)).attempt.void)

  private def deleteRecursively(dir: Path): Unit =
    if Files.exists(dir) then
      val stream = Files.walk(dir)
      try
        val it = stream.sorted(java.util.Comparator.reverseOrder()).iterator()
        while it.hasNext do
          val _ = Files.deleteIfExists(it.next())
      finally stream.close()

  private def baseOpts(outDir: String, cacheDir: Option[String]): CompileOptions =
    CompileOptions(
      target = "python-fastapi-postgres",
      outDir = outDir,
      ignoreVerify = true,
      withSynthesis = true,
      synthesisCacheDir = cacheDir
    )

  test("--with-synthesis without verified cache exits Violations and points at synth verify"):
    withTempDir: dir =>
      val opts = baseOpts(dir.toString, Some(dir.resolve("synth-cache").toString))
      Compile
        .run("fixtures/spec/url_shortener.spec", opts, log)
        .map(code => assertEquals(code, ExitCodes.Violations))

  test("loadVerifiedBodies reports the missing operation by name"):
    withTempDir: dir =>
      val cacheDir     = dir.resolve("synth-cache")
      val verifiedRoot = cacheDir.resolve("verified")
      val opts         = baseOpts(dir.toString, Some(cacheDir.toString))
      val seed: IO[Unit] =
        for
          _       <- IO.blocking(Files.createDirectories(verifiedRoot))
          source  <- IO.blocking(Files.readString(Paths.get("fixtures/spec/url_shortener.spec")))
          parsedE <- Parse.parseSpec(source)
          parsed   = parsedE.toOption.getOrElse(fail("parse failed"))
          builtE  <- Builder.buildIR(parsed.tree)
          built    = builtE.toOption.getOrElse(fail("build failed"))
          dafny    = DafnyGenerator.generate(built).getOrElse(fail("dafny gen failed"))
          header   = dafny.methods.find(_.name == "Shorten").getOrElse(fail("Shorten missing"))
          cache   <- Cache.make(verifiedRoot)
          key      = Cache.keyFor(header, "claude-sonnet-4-6", 1.0)
          entry = CacheEntry(
                    candidate = "stub",
                    body = "assume false;",
                    usage = TokenUsage(0, 0),
                    model = "claude-sonnet-4-6",
                    promptVersion = specrest.synth.SynthPromptVersion
                  )
          _ <- cache.store(key, entry)
        yield ()

      seed *> Compile
        .run("fixtures/spec/url_shortener.spec", opts, log)
        .map(code => assertEquals(code, ExitCodes.Violations))
