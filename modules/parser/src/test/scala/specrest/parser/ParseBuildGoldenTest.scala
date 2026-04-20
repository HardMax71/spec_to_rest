package specrest.parser

import specrest.ir.Serialize

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

class ParseBuildGoldenTest extends munit.FunSuite:

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
    assertEquals(fixtures.size, 18)

  fixtures.foreach: specPath =>
    val name       = specPath.getFileName.toString.stripSuffix(".spec")
    val goldenPath = goldenDir.resolve(s"$name.json")
    test(s"parse + build + serialize matches golden — $name"):
      assert(Files.exists(goldenPath), s"Missing golden for $name at $goldenPath")
      val source = Files.readString(specPath)
      val parsed = Parse.parseSpec(source)
      assert(parsed.errors.isEmpty, s"Parse errors for $name: ${parsed.errors}")
      val ir         = Builder.buildIR(parsed.tree)
      val emittedDom = Serialize.toJson(ir)
      val goldenRaw  = Files.readString(goldenPath)
      val goldenDom = io.circe.parser.parse(goldenRaw) match
        case Right(j)  => j
        case Left(err) => fail(s"failed to parse golden $name: $err")
      assertEquals(emittedDom, goldenDom, s"IR DOM differs from golden for $name")
