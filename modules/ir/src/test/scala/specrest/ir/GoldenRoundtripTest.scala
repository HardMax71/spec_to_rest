package specrest.ir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

class GoldenRoundtripTest extends munit.FunSuite:

  private val goldenDir: Path = Paths.get("fixtures/golden/ir")

  private val fixtures: List[Path] =
    if Files.isDirectory(goldenDir) then
      Files.list(goldenDir).iterator.asScala
        .filter(_.toString.endsWith(".json"))
        .toList
        .sortBy(_.getFileName.toString)
    else Nil

  test("fixtures directory is populated"):
    assert(fixtures.nonEmpty, s"No golden fixtures found in $goldenDir")
    assertEquals(fixtures.size, 17)

  fixtures.foreach: path =>
    val name = path.getFileName.toString
    test(s"decode → encode round-trip equals golden — $name"):
      val raw = Files.readString(path)
      val decoded = Serialize.fromJson(raw) match
        case Right(ir) => ir
        case Left(err) => fail(s"decode failed for $name: $err")
      val reEncoded = Serialize.toJson(decoded)
      val originalDom = io.circe.parser.parse(raw) match
        case Right(j)  => j
        case Left(err) => fail(s"original JSON parse failed for $name: $err")
      assertEquals(reEncoded, originalDom, s"round-trip mismatch for $name")
