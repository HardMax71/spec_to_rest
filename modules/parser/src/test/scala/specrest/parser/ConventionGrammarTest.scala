package specrest.parser

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.testutil.SpecFixtures

class ConventionGrammarTest extends CatsEffectSuite:

  private val baseSpec: String =
    """|service Demo {
       |  state {}
       |
       |  entity User {
       |    id: Id
       |    password_hash: String
       |  }
       |
       |  operation Register {
       |    input: password: String
       |    requires: true
       |    ensures: true
       |  }
       |
       |""".stripMargin

  private def withConventions(body: String): String =
    baseSpec + s"  conventions {\n$body\n  }\n}\n"

  test("two-segment convention rule has no qualifier"):
    SpecFixtures
      .buildFromSource("two-seg", withConventions("""    Register.http_method = "POST""""))
      .map: ir =>
        val rules = ir.n.toList.flatMap { case ConventionsDeclFull(rs, _) => rs }
          .collect { case r: ConventionRuleFull => r }
        assertEquals(rules.size, 1)
        val r = rules.head
        assertEquals(r.a, "Register")
        assertEquals(r.b, "http_method")
        assertEquals(r.c, None)

  test("three-segment dotted convention rule populates qualifier"):
    SpecFixtures
      .buildFromSource(
        "three-seg",
        withConventions("""    User.password_hash.test_strategy = "redacted"""")
      )
      .map: ir =>
        val rules = ir.n.toList.flatMap { case ConventionsDeclFull(rs, _) => rs }
          .collect { case r: ConventionRuleFull => r }
        assertEquals(rules.size, 1)
        val r = rules.head
        assertEquals(r.a, "User")
        assertEquals(r.b, "test_strategy")
        assertEquals(r.c, Some("password_hash"))
        r.d match
          case CvOk(PvBool(false)) => ()
          case other               => fail(s"expected CvOk(PvBool(false)), got $other")

  test("string-literal qualifier (legacy http_header form) still parses"):
    SpecFixtures
      .buildFromSource(
        "string-qual",
        withConventions("""    Register.http_header "Location" = "/users/{id}"""")
      )
      .map: ir =>
        val rules = ir.n.toList.flatMap { case ConventionsDeclFull(rs, _) => rs }
          .collect { case r: ConventionRuleFull => r }
        assertEquals(rules.size, 1)
        val r = rules.head
        assertEquals(r.a, "Register")
        assertEquals(r.b, "http_header")
        assertEquals(r.c, Some("Location"))

  test("mixing dotted qualifier with string qualifier is rejected"):
    val src = withConventions("""    User.password_hash.test_strategy "extra" = "redacted"""")
    Parse
      .parseSpec(src)
      .flatMap:
        case Left(err) =>
          fail(
            s"expected the dotted+string-qualifier form to parse but be rejected at the build stage; instead the parser failed: $err"
          )
        case Right(parsed) =>
          Builder.buildIR(parsed.tree).map:
            case Left(err) =>
              assert(
                err.message.contains("cannot combine a dotted qualifier with a string qualifier"),
                s"unexpected error: ${err.message}"
              )
            case Right(_) => fail("expected build error rejecting combined qualifiers")
