package specrest.verify.alloy

import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.Path as JPath
import java.nio.file.Paths

class AlloyGoldenTest extends munit.FunSuite:

  private val specDir: JPath   = Paths.get("fixtures/spec")
  private val goldenDir: JPath = Paths.get("fixtures/golden/alloy")

  private val fixtures: List[String] = List("powerset_demo")

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(specDir.resolve(s"$name.spec"))
    val parsed = Parse.parseSpecSync(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIRSync(parsed.tree).toOption.get

  fixtures.foreach: name =>
    test(s"Alloy source matches golden — $name"):
      val ir      = buildIR(name)
      val module  = Translator.translateGlobalSync(ir, scope = 5).toOption.get
      val emitted = Render.render(module).stripSuffix("\n")
      val golden  = Files.readString(goldenDir.resolve(s"$name.als")).stripSuffix("\n")
      if emitted != golden then
        val diffPath = Paths.get(s"/tmp/scala-alloy-$name.als")
        Files.writeString(diffPath, emitted)
        fail(s"Alloy source mismatch for $name; Scala output written to $diffPath")
