package specrest.parser.tooling

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.syntax.traverse.*
import specrest.ir.Serialize
import specrest.parser.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

@SuppressWarnings(Array("org.wartremover.warts.PlatformDefault"))
object RegenIrGoldens extends IOApp:

  // Resource-safe wrapper around Files.list (the underlying JStream must be
  // closed to release the directory file descriptor).
  private def listFiles(dir: Path): Resource[IO, List[Path]] =
    Resource.make(IO.blocking(Files.list(dir)))(s => IO.blocking(s.close())).evalMap: stream =>
      IO.blocking(stream.iterator.asScala.toList)

  override def run(args: List[String]): IO[ExitCode] =
    val specDir   = Paths.get("fixtures/spec")
    val goldenDir = Paths.get("fixtures/golden/ir")
    listFiles(specDir).use: entries =>
      val fixtures = entries
        .filter(_.toString.endsWith(".spec"))
        .sortBy(_.getFileName.toString)
      fixtures
        .traverse: specPath =>
          val name = specPath.getFileName.toString.stripSuffix(".spec")
          val gold = goldenDir.resolve(s"$name.json")
          for
            ir  <- SpecFixtures.loadIR(name)
            json = Serialize.toJson(ir).spaces2 + "\n"
            _   <- IO.blocking(Files.writeString(gold, json))
            _   <- IO.println(s"  ✓ $name")
          yield ()
        .as(ExitCode.Success) <*
        IO.println(s"Regenerated ${fixtures.size} goldens at $goldenDir")
