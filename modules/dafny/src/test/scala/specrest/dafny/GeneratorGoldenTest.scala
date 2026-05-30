package specrest.dafny

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.convention.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path as JPath
import java.nio.file.Paths

class GeneratorGoldenTest extends CatsEffectSuite:

  private val goldenDir: JPath = Paths.get("fixtures/golden/dafny")

  private val fixtures: List[String] = List("safe_counter", "url_shortener", "todo_list")

  fixtures.foreach: name =>
    test(s"Dafny skeleton matches golden — $name"):
      for
        ir     <- SpecFixtures.loadIR(name)
        result  = Generator.generate(ir)
        emitted = result.fold(
                    err => fail(s"Generator failed for $name: ${err.message}"),
                    _.text.stripSuffix("\n")
                  )
        golden <- IO.blocking(Files.readString(goldenDir.resolve(s"$name.dfy")).stripSuffix("\n"))
        _      <-
          if emitted == golden then IO.unit
          else
            IO.blocking {
              val diffPath = Files.createTempFile(s"scala-dafny-$name-", ".dfy")
              Files.writeString(diffPath, emitted)
              fail(s"Dafny mismatch for $name; Scala output written to $diffPath")
            }
      yield ()
