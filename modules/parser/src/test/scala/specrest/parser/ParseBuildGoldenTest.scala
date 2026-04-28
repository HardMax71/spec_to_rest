package specrest.parser

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.ir.Serialize
import specrest.parser.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

class ParseBuildGoldenTest extends CatsEffectSuite:

  private val specDir: Path   = Paths.get("fixtures/spec")
  private val goldenDir: Path = Paths.get("fixtures/golden/ir")

  private val fixtures: List[Path] =
    if Files.isDirectory(specDir) then
      Files.list(specDir).iterator.asScala
        .filter(_.toString.endsWith(".spec"))
        .toList
        .sortBy(_.getFileName.toString)
    else Nil

  test("spec fixtures directory is populated"):
    assert(fixtures.nonEmpty, s"No spec fixtures found in $specDir")
    assertEquals(fixtures.size, 20)

  fixtures.foreach: specPath =>
    val name       = specPath.getFileName.toString.stripSuffix(".spec")
    val goldenPath = goldenDir.resolve(s"$name.json")
    test(s"parse + build + serialize matches golden — $name"):
      assert(Files.exists(goldenPath), s"Missing golden for $name at $goldenPath")
      for
        ir        <- SpecFixtures.loadIR(name)
        emittedDom = Serialize.toJson(ir)
        goldenRaw <- IO.blocking(Files.readString(goldenPath))
        goldenDom <-
          IO.fromEither(io.circe.parser.parse(goldenRaw))
            .adaptError(e => new AssertionError(s"failed to parse golden $name: $e"))
      yield assertEquals(emittedDom, goldenDom, s"IR DOM differs from golden for $name")
