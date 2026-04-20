package specrest.verify.certificates

import io.circe.Json
import specrest.verify.CheckOutcome
import specrest.verify.CheckStatus
import specrest.verify.VerifierTool

import java.nio.file.Files
import java.nio.file.Path

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
        "outcome"    -> Json.fromString(outcomeToken(e.outcome)),
        "rawStatus"  -> Json.fromString(rawStatusToken(e.rawStatus)),
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

  private def outcomeToken(s: CheckOutcome): String = s match
    case CheckOutcome.Sat     => "sat"
    case CheckOutcome.Unsat   => "unsat"
    case CheckOutcome.Unknown => "unknown"
    case CheckOutcome.Skipped => "skipped"

  private def rawStatusToken(s: CheckStatus): String = s match
    case CheckStatus.Sat     => "sat"
    case CheckStatus.Unsat   => "unsat"
    case CheckStatus.Unknown => "unknown"

object DumpSink:

  def open(dir: Path): DumpSink =
    if Files.exists(dir) then
      if !Files.isDirectory(dir) then
        throw new RuntimeException(s"--dump-vc target is not a directory: $dir")
      val isEmpty =
        val s = Files.list(dir)
        try !s.iterator.hasNext
        finally s.close()
      if !isEmpty then
        throw new RuntimeException(
          s"--dump-vc target directory is non-empty: $dir (refusing to overwrite)"
        )
    else Files.createDirectories(dir)
    new DumpSink(dir)
