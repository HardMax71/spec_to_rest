package specrest.verify.certificates

import cats.effect.IO
import cats.effect.Resource
import io.circe.Json
import specrest.ir.VerifyError
import specrest.verify.CheckOutcome
import specrest.verify.CheckStatus
import specrest.verify.VerifierTool

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import scala.util.control.NonFatal

private def dumpIoFail(msg: String, cause: Throwable): VerifyError.Backend =
  val sw = new StringWriter
  cause.printStackTrace(new PrintWriter(sw))
  VerifyError.Backend(s"$msg: ${cause.getMessage}", Some(sw.toString))

final private class DumpOpenException(val error: VerifyError.Backend)
    extends RuntimeException(error.message)

final case class DumpEntry(
    id: String,
    tool: VerifierTool,
    outcome: CheckOutcome,
    rawStatus: CheckStatus,
    durationMs: Double,
    file: String
)

final class DumpSink(val dir: Path):
  private val entries = scala.collection.mutable.ArrayBuffer.empty[DumpEntry]

  def entryCount: Int = entries.size

  def close(): Unit = ()

  def writeZ3(
      checkId: String,
      smt: String,
      outcome: CheckOutcome,
      rawStatus: CheckStatus,
      durationMs: Double
  ): Unit =
    val name = s"${sanitize(checkId)}.smt2"
    Files.writeString(dir.resolve(name), smt)
    val _ = entries +=
      DumpEntry(checkId, VerifierTool.Z3, outcome, rawStatus, durationMs, name)

  def writeAlloy(
      checkId: String,
      als: String,
      outcome: CheckOutcome,
      rawStatus: CheckStatus,
      durationMs: Double
  ): Unit =
    val name = s"${sanitize(checkId)}.als"
    Files.writeString(dir.resolve(name), als)
    val _ = entries +=
      DumpEntry(checkId, VerifierTool.Alloy, outcome, rawStatus, durationMs, name)

  def writeIndex(specFile: String, totalMs: Double, ok: Boolean): Unit =
    val entryArr = entries.toList.map { e =>
      Json.obj(
        "id"         -> Json.fromString(e.id),
        "tool"       -> Json.fromString(VerifierTool.token(e.tool)),
        "outcome"    -> Json.fromString(CheckOutcome.token(e.outcome)),
        "rawStatus"  -> Json.fromString(CheckStatus.token(e.rawStatus)),
        "durationMs" -> Json.fromDoubleOrNull(e.durationMs),
        "file"       -> Json.fromString(e.file)
      )
    }
    val json = Json.obj(
      "schemaVersion" -> Json.fromInt(1),
      "specFile"      -> Json.fromString(specFile),
      "totalMs"       -> Json.fromDoubleOrNull(totalMs),
      "ok"            -> Json.fromBoolean(ok),
      "entries"       -> Json.arr(entryArr*)
    )
    val _ = Files.writeString(dir.resolve("verdicts.json"), json.spaces2 + "\n")

  private def sanitize(id: String): String =
    id.map(c => if c.isLetterOrDigit || c == '.' || c == '_' || c == '-' then c else '_')

object DumpSink:

  def openResource(dir: Path): Resource[IO, DumpSink] =
    openResource(acquireIO(dir))(_ => IO.unit)

  private[verify] def openResource(
      acquire: IO[DumpSink]
  )(
      release: DumpSink => IO[Unit]
  ): Resource[IO, DumpSink] =
    Resource.make(acquire)(release)

  private def acquireIO(dir: Path): IO[DumpSink] =
    IO.defer:
      open(dir) match
        case Right(sink) => IO.pure(sink)
        case Left(err)   => IO.raiseError(DumpOpenException(err))

  def open(dir: Path): Either[VerifyError.Backend, DumpSink] =
    try
      if Files.exists(dir) then
        if !Files.isDirectory(dir) then
          Left(VerifyError.Backend(s"--dump-vc target is not a directory: $dir", None))
        else
          val isEmpty =
            val s = Files.list(dir)
            try !s.iterator.hasNext
            finally s.close()
          if !isEmpty then
            Left(VerifyError.Backend(
              s"--dump-vc target directory is non-empty: $dir (refusing to overwrite)",
              None
            ))
          else Right(new DumpSink(dir))
      else
        val _ = Files.createDirectories(dir)
        Right(new DumpSink(dir))
    catch
      case NonFatal(e) =>
        Left(dumpIoFail(s"--dump-vc failed accessing $dir", e))
