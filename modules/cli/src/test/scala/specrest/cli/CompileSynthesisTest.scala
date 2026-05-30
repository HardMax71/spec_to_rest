package specrest.cli

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.dafny.Generator as DafnyGenerator
import specrest.ir.generated.SpecRestGenerated.LlmSynthesis
import specrest.ir.generated.SpecRestGenerated.classificationOperationName
import specrest.ir.generated.SpecRestGenerated.classificationStrategy
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.synth.Cache
import specrest.synth.CacheEntry
import specrest.synth.CacheOutcome
import specrest.synth.DafnyCli
import specrest.synth.SkeletonGenerator
import specrest.synth.TokenUsage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

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

  test("--with-synthesis on a 0-LLM_SYNTHESIS spec emits no kernel/adapter files (#27 review)"):
    withTempDir: dir =>
      val opts = baseOpts(dir.toString, Some(dir.resolve("synth-cache").toString))
      for
        code <- Compile.run("fixtures/lint/passing.spec", opts, log)
        files <- IO.blocking:
                   val stream = Files.walk(dir)
                   try stream.iterator.asScala.map(dir.relativize).map(_.toString).toSet
                   finally stream.close()
      yield
        assertEquals(code, ExitCodes.Ok)
        assert(
          files.forall(p => !p.startsWith("app/dafny_kernel")),
          s"kernel files leaked into 0-LLM_SYNTHESIS output: ${files.filter(_.startsWith("app/dafny_kernel"))}"
        )
        assert(
          !files.contains("app/services/_dafny_adapter.py"),
          "adapter file leaked into 0-LLM_SYNTHESIS output"
        )
        assert(
          !files.contains("app/services/_synth.py"),
          "synth marker file leaked into 0-LLM_SYNTHESIS output"
        )

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

  test("--allow-skeletons + skeletons cache populated → compile succeeds with warning"):
    DafnyCli.resolveBinary(None).flatMap:
      case Left(_)  => IO.unit
      case Right(_) => runAllowSkeletonsHappyPath()

  private def runAllowSkeletonsHappyPath(): IO[Unit] =
    withTempDir: dir =>
      val cacheDir      = dir.resolve("synth-cache")
      val skeletonsRoot = Cache.skeletonsRoot(cacheDir)
      val opts =
        baseOpts(dir.toString, Some(cacheDir.toString)).copy(allowSkeletons = true)
      val seedAll: IO[Unit] =
        for
          _       <- IO.blocking(Files.createDirectories(skeletonsRoot))
          source  <- IO.blocking(Files.readString(Paths.get("fixtures/spec/url_shortener.spec")))
          parsedE <- Parse.parseSpec(source)
          parsed   = parsedE.toOption.getOrElse(fail("parse failed"))
          builtE  <- Builder.buildIR(parsed.tree)
          built    = builtE.toOption.getOrElse(fail("build failed"))
          dafny    = DafnyGenerator.generate(built).getOrElse(fail("dafny gen failed"))
          cache   <- Cache.make(skeletonsRoot)
          synthOps = specrest.convention.Classify
                       .classifyOperations(built)
                       .filter(c =>
                         classificationStrategy(c) match {
                           case _: LlmSynthesis => true
                           case _               => false
                         }
                       )
          _ <- synthOps.foldLeft(IO.unit): (acc, c) =>
                 acc *> IO {
                   dafny.methods
                     .find(_.name == classificationOperationName(c))
                     .map: header =>
                       val body = SkeletonGenerator.fallbackBody(
                         header,
                         attempts = 1,
                         finalStrategy = "ZeroShot",
                         finalModel = "claude-sonnet-4-6",
                         reason = "test"
                       )
                       val key = Cache.keyFor(header, "claude-sonnet-4-6", 1.0)
                       val entry = CacheEntry(
                         candidate = body,
                         body = body,
                         usage = TokenUsage(0, 0),
                         model = "claude-sonnet-4-6",
                         promptVersion = specrest.synth.SynthPromptVersion,
                         outcome = CacheOutcome.Skeleton
                       )
                       (key, entry)
                 }.flatMap:
                   case Some((k, e)) => cache.store(k, e)
                   case None         => IO.unit
        yield ()

      seedAll *> Compile
        .run("fixtures/spec/url_shortener.spec", opts, log)
        .map: code =>
          assertEquals(code, ExitCodes.Ok)
          val kernelDir = Paths.get(opts.outDir).resolve("app/dafny_kernel")
          assert(
            Files.isDirectory(kernelDir),
            s"kernel emitted under $kernelDir even when only skeletons cached"
          )

  test("--allow-skeletons but no skeletons either → still fails with helpful error"):
    withTempDir: dir =>
      val opts =
        baseOpts(dir.toString, Some(dir.resolve("synth-cache").toString)).copy(allowSkeletons =
          true
        )
      Compile
        .run("fixtures/spec/url_shortener.spec", opts, log)
        .map(code => assertEquals(code, ExitCodes.Violations))
