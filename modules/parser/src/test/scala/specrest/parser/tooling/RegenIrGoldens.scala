package specrest.parser.tooling

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.traverse.*
import specrest.ir.Serialize
import specrest.parser.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

// One-off regenerator for fixtures/golden/ir/*.json.
//
// Run when the IR ADT / Serialize encoder is changed intentionally and the
// new wire format should become the canonical reference:
//
//   sbt 'parser/Test/runMain specrest.parser.tooling.RegenIrGoldens'
//
// Lives in test sources (not main) because it depends on SpecFixtures, and
// because regenerating goldens is a tooling concern, not production code.
@SuppressWarnings(Array("org.wartremover.warts.PlatformDefault"))
object RegenIrGoldens extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    val specDir   = Paths.get("fixtures/spec")
    val goldenDir = Paths.get("fixtures/golden/ir")
    val fixtures = Files
      .list(specDir)
      .iterator
      .asScala
      .toList
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
