package specrest.synth

import cats.effect.IO
import cats.effect.Resource
import cats.effect.kernel.Ref

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import scala.jdk.CollectionConverters.*

final case class TranslatedDafny(
    target: TargetLanguage,
    files: Map[String, String],
    rawStdout: String,
    rawStderr: String,
    durationMs: Long
) derives CanEqual

trait DafnyTranslator:
  def translate(
      source: String,
      target: TargetLanguage,
      timeoutSec: Int
  ): IO[Either[String, TranslatedDafny]]

final class DafnyTranslateCli private (binary: String, workDir: Path) extends DafnyTranslator:

  def translate(
      source: String,
      target: TargetLanguage,
      timeoutSec: Int
  ): IO[Either[String, TranslatedDafny]] =
    val unique = java.util.UUID.randomUUID().toString
    // The Go backend names the emitted package after the source file's basename, so a unique
    // `candidate-<uuid>.dfy` would yield an unstable `package candidate_<uuid>`. Isolating each
    // call in its own subdirectory lets us use a fixed `kernel.dfy` basename → stable `package
    // kernel`. Python/JS derive their module name by convention, so this is a no-op for them.
    val callDir = workDir.resolve(s"call-$unique")
    val srcFile = callDir.resolve("kernel.dfy")
    val outBase = callDir.resolve("kernel")
    val outDir  = callDir.resolve(s"kernel${target.outputSuffix}")
    val outFile = callDir.resolve("kernel.translate.out")
    val errFile = callDir.resolve("kernel.translate.err")
    val cleanup = IO.blocking(deleteRecursively(callDir)).attempt.void
    val program =
      for
        _      <- IO.blocking(Files.createDirectories(callDir))
        _      <- IO.blocking(Files.writeString(srcFile, source, StandardOpenOption.CREATE_NEW))
        result <- runTranslate(srcFile, outBase, outDir, outFile, errFile, target, timeoutSec)
      yield result
    program.attempt.flatMap:
      case Right(r) => IO.pure(r)
      case Left(t)  => IO.pure(Left(s"failed to invoke dafny translate: ${t.getMessage}"))
    .guarantee(cleanup)

  private def runTranslate(
      srcFile: Path,
      outBase: Path,
      outDir: Path,
      outFile: Path,
      errFile: Path,
      target: TargetLanguage,
      timeoutSec: Int
  ): IO[Either[String, TranslatedDafny]] =
    val command = List(
      binary,
      "translate",
      target.cliFlag,
      "--include-runtime",
      "--no-verify",
      s"--verification-time-limit=$timeoutSec",
      s"--output=${outBase.toString}",
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
        proc.waitFor((timeoutSec.toLong + 30L) * 1000L, java.util.concurrent.TimeUnit.MILLISECONDS)
      if !finished then
        proc.destroyForcibly()
        Left(s"dafny translate exceeded ${timeoutSec + 30}s wall-clock and was killed")
      else
        val exit       = proc.exitValue()
        val durationMs = (System.nanoTime() - started) / 1_000_000L
        val stdout     = readIfExists(outFile)
        val stderr     = readIfExists(errFile)
        // Go/Python emit a directory tree; the JS backend emits a single `<base>.js` bundle (the
        // runtime is inlined by `--include-runtime`), so fall back to collecting that one file.
        val collected =
          if Files.isDirectory(outDir) then collectFiles(outDir)
          else collectSingleFile(outBase, target)
        // The Go backend runs `goimports` as a post-emit format pass and exits non-zero when that
        // tool is absent, even though the translated sources are already fully written and valid.
        // Accept that one case (output present + the failure is the formatter), but let every other
        // non-zero exit surface as an error so a genuinely broken translation is not masked.
        val formatterOnlyFailure =
          (stderr + stdout).toLowerCase.contains("goimports")
        if collected.nonEmpty && (exit == 0 || formatterOnlyFailure) then
          Right(
            TranslatedDafny(
              target = target,
              files = collected,
              rawStdout = stdout,
              rawStderr = stderr,
              durationMs = durationMs
            )
          )
        else if exit != 0 then
          val detail =
            if stderr.trim.nonEmpty then stderr.trim
            else if stdout.trim.nonEmpty then stdout.trim
            else s"exit=$exit"
          Left(s"dafny translate ${target.cliFlag} failed: $detail")
        else
          Left(s"dafny translate ${target.cliFlag} produced no output directory at $outDir")
    }

  private def collectSingleFile(outBase: Path, target: TargetLanguage): Map[String, String] =
    val file = outBase.resolveSibling(s"${outBase.getFileName}.${target.cliFlag}")
    if Files.isRegularFile(file) then Map(file.getFileName.toString -> Files.readString(file))
    else Map.empty

  private def collectFiles(root: Path): Map[String, String] =
    val stream = Files.walk(root)
    try
      stream.iterator.asScala
        .filter(Files.isRegularFile(_))
        .filter(p => !p.getFileName.toString.endsWith(".dtr"))
        .map(p => root.relativize(p).toString.replace('\\', '/') -> Files.readString(p))
        .toMap
    finally stream.close()

  private def readIfExists(p: Path): String =
    if Files.exists(p) then Files.readString(p) else ""

  private def deleteRecursively(dir: Path): Unit =
    if Files.exists(dir) then
      val stream = Files.walk(dir)
      try
        val it = stream.sorted(java.util.Comparator.reverseOrder()).iterator()
        while it.hasNext do
          val _ = Files.deleteIfExists(it.next())
      finally stream.close()

object DafnyTranslateCli:

  def make(binary: String): Resource[IO, DafnyTranslator] =
    Resource
      .make(IO.blocking(Files.createTempDirectory("specrest-dafny-translate-")))(dir =>
        IO.blocking(deleteRecursively(dir)).attempt.void
      )
      .map(dir => new DafnyTranslateCli(binary, dir))

  private def deleteRecursively(dir: Path): Unit =
    if Files.exists(dir) then
      val stream = Files.walk(dir)
      try
        val it = stream.sorted(java.util.Comparator.reverseOrder()).iterator()
        while it.hasNext do
          val _ = Files.deleteIfExists(it.next())
      finally stream.close()

final class MockDafnyTranslator private (
    plan: List[Either[String, TranslatedDafny]],
    cursor: Ref[IO, Int],
    callsRef: Ref[IO, List[String]]
) extends DafnyTranslator:

  def translate(
      source: String,
      @scala.annotation.unused target: TargetLanguage,
      @scala.annotation.unused timeoutSec: Int
  ): IO[Either[String, TranslatedDafny]] =
    for
      _ <- callsRef.update(source :: _)
      i <- cursor.getAndUpdate(_ + 1)
    yield
      if i >= plan.length then Left(s"MockDafnyTranslator exhausted after $i calls")
      else plan(i)

  def calls: IO[List[String]] = callsRef.get.map(_.reverse)

object MockDafnyTranslator:

  def of(plan: List[Either[String, TranslatedDafny]]): IO[MockDafnyTranslator] =
    for
      cur <- Ref.of[IO, Int](0)
      log <- Ref.of[IO, List[String]](Nil)
    yield new MockDafnyTranslator(plan, cur, log)

  def success(target: TargetLanguage, files: Map[String, String]): TranslatedDafny =
    TranslatedDafny(target, files, rawStdout = "", rawStderr = "", durationMs = 0L)
