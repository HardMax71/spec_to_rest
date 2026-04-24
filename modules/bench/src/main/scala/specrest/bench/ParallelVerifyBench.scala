package specrest.bench

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.openjdk.jmh.annotations.*
import specrest.ir.ServiceIR
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.Consistency
import specrest.verify.ConsistencyReport
import specrest.verify.VerificationConfig

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.compiletime.uninitialized

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = Array("-Xmx2G"))
@State(Scope.Benchmark)
class ParallelVerifyBench:

  @Param(Array("1", "2", "4", "8"))
  var maxParallel: Int = 1

  private var ir: ServiceIR           = uninitialized
  private var runtime: IORuntime      = uninitialized
  private var cfg: VerificationConfig = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    runtime = IORuntime.global
    val source = Files.readString(repoRoot.resolve("fixtures/spec/url_shortener.spec"))
    val loaded: IO[ServiceIR] =
      Parse.parseSpec(source).flatMap:
        case Left(err) =>
          IO.raiseError(new RuntimeException(s"parse failed: ${err.errors}"))
        case Right(parsed) =>
          Builder.buildIR(parsed.tree).flatMap:
            case Left(err) => IO.raiseError(new RuntimeException(s"build failed: ${err.message}"))
            case Right(s)  => IO.pure(s)
    ir = loaded.unsafeRunSync()(runtime)
    cfg = VerificationConfig(timeoutMs = 30_000L, maxParallel = maxParallel)

  @Benchmark
  def verifyUrlShortener(): ConsistencyReport =
    Consistency.runConsistencyChecks(ir, cfg).unsafeRunSync()(runtime)

  private def repoRoot: Path =
    // JMH forks its own JVM; the resulting `user.dir` isn't guaranteed to be the
    // sbt baseDirectory. Walk upward until we find `fixtures/spec`, which marks
    // the repo root.
    @tailrec def climb(p: Path): Path =
      if p == null then
        throw new IllegalStateException(
          "could not locate fixtures/spec by walking upward from user.dir"
        )
      else if Files.isDirectory(p.resolve("fixtures/spec")) then p
      else climb(p.getParent)
    climb(Paths.get("").toAbsolutePath)
