package specrest.verify.z3

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.verify.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path as JPath
import java.nio.file.Paths

class SmtLibGoldenTest extends CatsEffectSuite:

  private val goldenDir: JPath = Paths.get("fixtures/golden/smt")

  private val translatableFixtures: List[String] = List(
    "broken_decrement",
    "broken_url_shortener",
    "convention_errors",
    "dead_op",
    "safe_counter",
    "set_ops",
    "unreachable_op",
    "unsat_invariants",
    "url_shortener"
  )

  translatableFixtures.foreach: name =>
    test(s"SMT-LIB matches golden — $name"):
      for
        ir      <- SpecFixtures.loadIR(name)
        scriptE <- Translator.translate(ir)
        script = scriptE.fold(
                   err => fail(s"Translator.translate failed for $name: ${err.message}"),
                   identity
                 )
        emitted = SmtLib.renderSmtLib(script, timeoutMs = Some(30_000L)).stripSuffix("\n")
        golden <- IO.blocking(Files.readString(goldenDir.resolve(s"$name.smt2")).stripSuffix("\n"))
        _ <-
          if emitted == golden then IO.unit
          else
            IO.blocking {
              val diffPath = Files.createTempFile(s"scala-smt-$name-", ".smt2")
              Files.writeString(diffPath, emitted)
              fail(s"SMT-LIB mismatch for $name; Scala output written to $diffPath for comparison")
            }
      yield ()
