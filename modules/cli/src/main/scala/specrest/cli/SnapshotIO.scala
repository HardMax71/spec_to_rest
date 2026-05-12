package specrest.cli

import specrest.codegen.migration.SchemaCodec
import specrest.convention.DatabaseSchema

import java.nio.file.Files
import java.nio.file.Path
import scala.util.Try

object SnapshotIO:
  private val SnapshotFile = ".spec-snapshot.json"

  def readSnapshot(outRoot: Path, log: Logger): Option[DatabaseSchema] =
    val path = outRoot.resolve(SnapshotFile)
    if !Files.isRegularFile(path) then None
    else
      Try(Files.readString(path)).toEither
        .left
        .map(t => s"read failed: ${Option(t.getMessage).getOrElse(t.toString)}")
        .flatMap(SchemaCodec.decode) match
        case Right(snap) => Some(snap.schema)
        case Left(err) =>
          log.warn(
            s"ignoring schema snapshot at $path: $err " +
              "(treating as missing — full re-emit will follow)"
          )
          None
