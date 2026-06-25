package specrest.testgen

import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate
import specrest.profile.ProfiledService

trait BehavioralTestSupport extends CatsEffectSuite:

  // These tests exercise the spec->test emission/translation logic, which only applies to
  // *implemented* operations. The orthogonal "skip fail-loud stubs" gate (StubOps/Finding 1)
  // is covered by its own focused test below; here every op that would otherwise be a
  // fail-loud stub is marked synthesized so Finding 1 is transparent and the translation
  // logic under test actually runs.
  protected def asSynthesized(profiled: ProfiledService): ProfiledService =
    SynthFixture.asSynthesized(profiled)

  private def buildProfiled(src: String, label: String) =
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) =>
            asSynthesized(Annotate.buildProfiledService(ir, "python-fastapi-postgres"))
          case Left(err) => fail(s"build error for $label: $err")
      case Left(err) => fail(s"parse error for $label: $err")

  protected def loadProfiled(path: String) =
    val src = scala.util.Using.resource(scala.io.Source.fromFile(path))(_.getLines.mkString("\n"))
    buildProfiled(src, path)

  protected def sensitiveInputSpec(inputName: String, conventionsBlock: String): String =
    s"""|service AuthLite {
        |  state {}
        |
        |  entity User {
        |    id: Id
        |    email: String
        |  }
        |
        |  operation Register {
        |    input: email: String, $inputName: String
        |    requires: true
        |    ensures: true
        |  }
        |
        |$conventionsBlock
        |}
        |""".stripMargin

  protected def profileSource(label: String, src: String) = buildProfiled(src, label)

  protected def loadProfiledFromSpec(spec: String) = buildProfiled(spec, "spec")

  protected def cardinalitySpec =
    """|service Demo {
       |  enum Phase { LOW, HIGH }
       |  entity Item {
       |    id: Int
       |    phase: Phase
       |    tags: Set[String]
       |  }
       |  state {
       |    items: Int -> lone Item
       |  }
       |  transition ItemLifecycle {
       |    entity: Item
       |    field: phase
       |    LOW -> HIGH via Promote when GUARD
       |  }
       |  operation Promote {
       |    input: id: Int
       |    requires: id in items
       |    ensures: items'[id].phase = HIGH
       |  }
       |  conventions {
       |    Promote.http_method = "POST"
       |    Promote.http_path   = "/items/{id}/promote"
       |  }
       |}
       |""".stripMargin
