package specrest.verify.alloy

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.verify.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path as JPath
import java.nio.file.Paths

class AlloyGoldenTest extends CatsEffectSuite:

  private val goldenDir: JPath = Paths.get("fixtures/golden/alloy")

  private val fixtures: List[String] = List("powerset_demo")

  fixtures.foreach: name =>
    test(s"Alloy source matches golden — $name"):
      for
        ir      <- SpecFixtures.loadIR(name)
        moduleE <- Translator.translateGlobal(ir, scope = 5)
        module =
          moduleE.toOption.getOrElse(
            fail(s"translateGlobal failed for $name: ${moduleE.left.toOption.map(_.message)}")
          )
        emitted = Render.render(module).stripSuffix("\n")
        golden <- IO.blocking(Files.readString(goldenDir.resolve(s"$name.als")).stripSuffix("\n"))
        _ <-
          if emitted == golden then IO.unit
          else
            IO.blocking {
              val diffPath = Files.createTempFile(s"scala-alloy-$name-", ".als")
              Files.writeString(diffPath, emitted)
              fail(s"Alloy source mismatch for $name; Scala output written to $diffPath")
            }
      yield ()
