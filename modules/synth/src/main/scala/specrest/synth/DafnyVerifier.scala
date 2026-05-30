package specrest.synth

import cats.effect.IO
import cats.effect.Resource
import cats.effect.kernel.Ref
import specrest.synth.DafnyOutputParser.MethodResult

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

final case class VerifierRun(
    methods: List[MethodResult],
    rawStdout: String,
    rawStderr: String,
    exitCode: Int,
    durationMs: Long
) derives CanEqual:

  def scopesFor(name: String): List[MethodResult] =
    methods.filter(m => m.name == name || m.name.startsWith(s"$name ("))

  def verifiedFor(name: String): Boolean =
    val scopes = scopesFor(name)
    scopes.nonEmpty && scopes.forall(m => m.outcome == "Correct" || m.outcome == "Valid")

  def errorsFor(name: String): List[VerifierError] =
    scopesFor(name).flatMap(_.errors)

trait DafnyVerifier:
  def verify(source: String, timeoutSec: Int): IO[Either[String, VerifierRun]]

final class DafnyCli private (binary: String, workDir: Path) extends DafnyVerifier:

  def verify(source: String, timeoutSec: Int): IO[Either[String, VerifierRun]] =
    val unique  = java.util.UUID.randomUUID().toString
    val srcFile = workDir.resolve(s"candidate-$unique.dfy")
    val logFile = workDir.resolve(s"candidate-$unique.json")
    val outFile = workDir.resolve(s"candidate-$unique.out")
    val errFile = workDir.resolve(s"candidate-$unique.err")
    val cleanup =
      IO.blocking(Files.deleteIfExists(srcFile)).attempt.void *>
        IO.blocking(Files.deleteIfExists(logFile)).attempt.void *>
        IO.blocking(Files.deleteIfExists(outFile)).attempt.void *>
        IO.blocking(Files.deleteIfExists(errFile)).attempt.void
    val program =
      for
        _      <- IO.blocking(Files.writeString(srcFile, source, StandardOpenOption.CREATE_NEW))
        result <- runDafny(srcFile, logFile, outFile, errFile, source, timeoutSec)
      yield result
    program.attempt.flatMap:
      case Right(r) => IO.pure(r)
      case Left(t)  => IO.pure(Left(s"failed to invoke dafny: ${t.getMessage}"))
    .guarantee(cleanup)

  private def runDafny(
      srcFile: Path,
      logFile: Path,
      outFile: Path,
      errFile: Path,
      source: String,
      timeoutSec: Int
  ): IO[Either[String, VerifierRun]] =
    val command = List(
      binary,
      "verify",
      s"--verification-time-limit=$timeoutSec",
      s"--log-format=json;LogFileName=${logFile.toString}",
      srcFile.toString
    )
    IO.blocking {
      val started = System.nanoTime()
      val pb = new ProcessBuilder(command.toArray*)
        .redirectOutput(outFile.toFile)
        .redirectError(errFile.toFile)
      val proc = pb.start()
      proc.getOutputStream.close()
      val finished =
        proc.waitFor((timeoutSec.toLong + 5L) * 1000L, java.util.concurrent.TimeUnit.MILLISECONDS)
      if !finished then
        proc.destroyForcibly()
        Left(s"dafny exceeded ${timeoutSec + 5}s wall-clock and was killed")
      else
        val exit       = proc.exitValue()
        val durationMs = (System.nanoTime() - started) / 1_000_000L
        val stdout     = readIfExists(outFile)
        val stderr     = readIfExists(errFile)
        interpret(logFile, source, stdout, stderr, exit, durationMs)
    }

  private def readIfExists(p: Path): String =
    if Files.exists(p) then Files.readString(p) else ""

  private def interpret(
      logFile: Path,
      source: String,
      stdout: String,
      stderr: String,
      exit: Int,
      durationMs: Long
  ): Either[String, VerifierRun] =
    if Files.exists(logFile) then
      DafnyOutputParser.parseLog(Files.readString(logFile), source) match
        case Left(msg)      => Left(msg)
        case Right(methods) => Right(VerifierRun(methods, stdout, stderr, exit, durationMs))
    else
      val parseErr = VerifierError(
        category = "syntax_error",
        message =
          if stderr.trim.nonEmpty then stderr.trim
          else if stdout.trim.nonEmpty then stdout.trim
          else "dafny did not produce a verification log; likely a parse or resolution error"
      )
      val synthetic = MethodResult(name = "<file>", outcome = "Errors", errors = List(parseErr))
      Right(VerifierRun(List(synthetic), stdout, stderr, exit, durationMs))

object DafnyCli:

  def resolveBinary(explicit: Option[String]): IO[Either[String, String]] =
    val candidate = explicit.orElse(sys.env.get("DAFNY_BIN")).getOrElse("dafny")
    IO.blocking {
      val pb = new ProcessBuilder(candidate, "--version")
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      try
        val proc = pb.start()
        proc.getOutputStream.close()
        val ok = proc.waitFor(10L, java.util.concurrent.TimeUnit.SECONDS)
        if !ok then
          proc.destroyForcibly()
          Left(s"`$candidate --version` did not return within 10s")
        else if proc.exitValue() == 0 then Right(candidate)
        else Left(s"`$candidate --version` failed with exit ${proc.exitValue()}")
      catch
        case _: java.io.IOException => Left(s"dafny binary '$candidate' not found on PATH")
    }

  def make(binary: String): Resource[IO, DafnyVerifier] =
    Resource
      .make(IO.blocking(Files.createTempDirectory("specrest-dafny-")))(dir =>
        IO.blocking(deleteRecursively(dir)).attempt.void
      )
      .map(dir => new DafnyCli(binary, dir))

  private def deleteRecursively(dir: Path): Unit =
    if Files.exists(dir) then
      val stream = Files.walk(dir)
      try
        val it = stream.sorted(java.util.Comparator.reverseOrder()).iterator()
        while it.hasNext do
          val _ = Files.deleteIfExists(it.next())
      finally stream.close()

final class MockDafnyVerifier private (
    plan: List[Either[String, VerifierRun]],
    cursor: Ref[IO, Int],
    callsRef: Ref[IO, List[String]]
) extends DafnyVerifier:

  def verify(
      source: String,
      @scala.annotation.unused timeoutSec: Int
  ): IO[Either[String, VerifierRun]] =
    for
      _ <- callsRef.update(source :: _)
      i <- cursor.getAndUpdate(_ + 1)
    yield
      if i >= plan.length then Left(s"MockDafnyVerifier exhausted after $i calls")
      else plan(i)

  def calls: IO[List[String]] = callsRef.get.map(_.reverse)

object MockDafnyVerifier:
  def of(plan: List[Either[String, VerifierRun]]): IO[MockDafnyVerifier] =
    for
      cur <- Ref.of[IO, Int](0)
      log <- Ref.of[IO, List[String]](Nil)
    yield new MockDafnyVerifier(plan, cur, log)

  def run(methods: List[MethodResult], durationMs: Long = 100L): VerifierRun =
    VerifierRun(methods, rawStdout = "", rawStderr = "", exitCode = 0, durationMs = durationMs)

  def correct(name: String): MethodResult =
    MethodResult(name, "Correct", Nil)

  def errors(name: String, errs: List[VerifierError]): MethodResult =
    MethodResult(name, "Errors", errs)
