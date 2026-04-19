package specrest.verify.alloy

import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.CheckStatus

import java.nio.file.Files
import java.nio.file.Paths

class TranslatorTest extends munit.FunSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree)

  test("powerset_demo translates to a valid Alloy module and solves sat"):
    val ir     = buildIR("powerset_demo")
    val module = Translator.translateGlobal(ir, scope = 5)
    assertEquals(module.name, "PowersetDemo")
    assert(
      module.sigs.exists(_.name == "User"),
      s"missing User sig; got ${module.sigs.map(_.name)}"
    )
    assert(module.sigs.exists(_.name == "State"), s"missing State sig")
    assertEquals(module.facts.size, 1)
    val source  = Render.render(module)
    val backend = new AlloyBackend
    val result  = backend.check(source, commandIdx = 0, timeoutMs = 30_000L)
    assertEquals(result.status, CheckStatus.Sat, s"expected sat; source=\n$source")

  test("Alloy render contains expected structural tokens"):
    val ir     = buildIR("powerset_demo")
    val module = Translator.translateGlobal(ir, scope = 5)
    val source = Render.render(module)
    assert(source.contains("module PowersetDemo"), s"source=\n$source")
    assert(source.contains("sig User"), s"source=\n$source")
    assert(source.contains("one sig State"), s"source=\n$source")
    assert(source.contains("users: set User"), s"source=\n$source")
    assert(source.contains("fact someEmptySubsetExists"), s"source=\n$source")
    assert(source.contains("set User"), s"source=\n$source")
    assert(source.contains("run global"), s"source=\n$source")

  test("universal powerset — `all t in ^s | ...` raises a sharp AlloyTranslatorError"):
    val spec =
      """service UnivDemo {
        |  entity User {
        |    id: Int
        |  }
        |  state { users: Set[User] }
        |  invariant everySubsetContainedInUsers:
        |    all t in ^users | t subset users
        |}""".stripMargin
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[AlloyTranslatorError]:
      val m = Translator.translateGlobal(ir, scope = 5)
      Render.render(m)
    assert(
      err.getMessage.contains("higher-order") && err.getMessage.contains("powerset"),
      s"expected higher-order/powerset error; got: ${err.getMessage}"
    )

  test("standalone '^s' outside a binder raises AlloyTranslatorError"):
    val spec =
      """service Bad {
        |  state { a: Set[Int] }
        |  invariant power:
        |    #(^a) >= 0
        |}""".stripMargin
    val parsed = Parse.parseSpec(spec)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    val err = intercept[AlloyTranslatorError]:
      val m = Translator.translateGlobal(ir, scope = 5)
      Render.render(m)
    assert(
      err.getMessage.contains("powerset") && err.getMessage.contains("binder domain"),
      s"expected binder-domain error; got: ${err.getMessage}"
    )
