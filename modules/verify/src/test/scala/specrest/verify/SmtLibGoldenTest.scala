package specrest.verify

import java.nio.file.{Files, Path as JPath, Paths}
import specrest.parser.{Builder, Parse}

class SmtLibGoldenTest extends munit.FunSuite:

  private val specDir: JPath   = Paths.get("fixtures/spec")
  private val goldenDir: JPath = Paths.get("fixtures/golden/smt")

  private val translatableFixtures: List[String] = List(
    "broken_decrement",
    "broken_url_shortener",
    "convention_errors",
    "dead_op",
    "safe_counter",
    "unreachable_op",
    "unsat_invariants",
    "url_shortener",
  )

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(specDir.resolve(s"$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree)

  translatableFixtures.foreach: name =>
    test(s"SMT-LIB matches golden — $name"):
      val ir       = buildIR(name)
      val script   = Translator.translate(ir)
      val emitted  = SmtLib.renderSmtLib(script, timeoutMs = Some(30_000L)).stripSuffix("\n")
      val golden   = Files.readString(goldenDir.resolve(s"$name.smt2")).stripSuffix("\n")
      if emitted != golden then
        val diffPath = Paths.get(s"/tmp/scala-smt-$name.smt2")
        Files.writeString(diffPath, emitted)
        fail(s"SMT-LIB mismatch for $name; Scala output written to $diffPath for comparison")
