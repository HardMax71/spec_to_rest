package specrest.verify.alloy

import munit.CatsEffectSuite
import specrest.verify.CheckStatus
import specrest.verify.testutil.SpecFixtures

class TranslatorTest extends CatsEffectSuite:

  test("powerset_demo translates to a valid Alloy module and solves sat"):
    for
      ir      <- SpecFixtures.loadIR("powerset_demo")
      moduleE <- Translator.translateGlobal(ir, scope = 5)
      module   = moduleE.toOption.get
      _        = assertEquals(module.name, "PowersetDemo")
      _ = assert(
            module.sigs.exists(_.name == "User"),
            s"missing User sig; got ${module.sigs.map(_.name)}"
          )
      _        = assert(module.sigs.exists(_.name == "State"), "missing State sig")
      _        = assertEquals(module.facts.size, 1)
      source   = Render.render(module)
      backend  = new AlloyBackend
      resultE <- backend.check(source, commandIdx = 0, timeoutMs = 30_000L)
      result   = resultE.toOption.get
    yield assertEquals(result.status, CheckStatus.Sat, s"expected sat; source=\n$source")

  test("Alloy render contains expected structural tokens"):
    for
      ir      <- SpecFixtures.loadIR("powerset_demo")
      moduleE <- Translator.translateGlobal(ir, scope = 5)
      module   = moduleE.toOption.get
      source   = Render.render(module)
    yield
      assert(source.contains("module PowersetDemo"), s"source=\n$source")
      assert(source.contains("sig User"), s"source=\n$source")
      assert(source.contains("one sig State"), s"source=\n$source")
      assert(source.contains("users: set User"), s"source=\n$source")
      assert(source.contains("fact someEmptySubsetExists"), s"source=\n$source")
      assert(source.contains("set User"), s"source=\n$source")
      assert(source.contains("run global"), s"source=\n$source")

  test("universal powerset — `all t in ^s | ...` surfaces as Left(AlloyTranslator)"):
    val spec =
      """service UnivDemo {
        |  entity User {
        |    id: Int
        |  }
        |  state { users: Set[User] }
        |  invariant everySubsetContainedInUsers:
        |    all t in ^users | t subset users
        |}""".stripMargin
    for
      ir      <- SpecFixtures.buildFromSource("UnivDemo", spec)
      moduleE <- Translator.translateGlobal(ir, scope = 5)
    yield
      val err = moduleE match
        case Left(e)  => e
        case Right(_) => fail("expected Left(AlloyTranslator)")
      assert(
        err.message.contains("higher-order") && err.message.contains("powerset"),
        s"expected higher-order/powerset error; got: ${err.message}"
      )

  test("standalone '^s' outside a binder surfaces as Left(AlloyTranslator)"):
    val spec =
      """service Bad {
        |  state { a: Set[Int] }
        |  invariant power:
        |    #(^a) >= 0
        |}""".stripMargin
    for
      ir      <- SpecFixtures.buildFromSource("Bad", spec)
      moduleE <- Translator.translateGlobal(ir, scope = 5)
    yield
      val err = moduleE match
        case Left(e)  => e
        case Right(_) => fail("expected Left(AlloyTranslator)")
      assert(
        err.message.contains("powerset") && err.message.contains("binder domain"),
        s"expected binder-domain error; got: ${err.message}"
      )
