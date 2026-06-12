package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.parser.Builder
import specrest.parser.Parse

class SecurityValidateTest extends CatsEffectSuite:

  private def loadIR(spec: String) =
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  private def service(security: String, requiresAuth: String): String =
    s"""|service Demo {
        |  state {
        |    count: Int
        |  }
        |$security
        |  operation Increment {
        |$requiresAuth
        |    requires:
        |      true
        |
        |    ensures:
        |      count' = count + 1
        |  }
        |}
        |""".stripMargin

  private val bearerBlock =
    """|  security {
       |    bearer: Bearer(bearer_format: "JWT")
       |  }
       |""".stripMargin

  test("declared scheme reference produces no diagnostics"):
    loadIR(service(bearerBlock, "    requires_auth: bearer")).map: ir =>
      assertEquals(Validate.validateSecurity(ir), Nil)

  test("no security usage at all produces no diagnostics"):
    loadIR(service("", "")).map: ir =>
      assertEquals(Validate.validateSecurity(ir), Nil)

  test("undeclared scheme reference is an error naming op, scheme, and declared set"):
    loadIR(service(bearerBlock, "    requires_auth: api_key")).map: ir =>
      val diags = Validate.validateSecurity(ir)
      assertEquals(diags.size, 1)
      val d = diags.head
      assertEquals(d.level, DiagnosticLevel.Error)
      assert(d.message.contains("'Increment'"), d.message)
      assert(d.message.contains("'api_key'"), d.message)
      assert(d.message.contains("declared schemes: bearer"), d.message)
      assertEquals(d.target, "Increment")
      assertEquals(d.property, "requires_auth")
      assert(d.span.isDefined, "diagnostic carries the operation span")

  test("reference with no security block declared says so"):
    loadIR(service("", "    requires_auth: bearer")).map: ir =>
      val diags = Validate.validateSecurity(ir)
      assertEquals(diags.size, 1)
      assert(diags.head.message.contains("no security block is declared"), diags.head.message)

  test("duplicate scheme names are an error on the second declaration"):
    val dup =
      """|  security {
         |    bearer: Bearer
         |    bearer: Basic
         |  }
         |""".stripMargin
    loadIR(service(dup, "    requires_auth: bearer")).map: ir =>
      val diags = Validate.validateSecurity(ir)
      assertEquals(diags.size, 1)
      assert(diags.head.message.contains("duplicate security scheme 'bearer'"), diags.head.message)
      assertEquals(diags.head.level, DiagnosticLevel.Error)
