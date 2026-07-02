package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.parser.Builder
import specrest.parser.Parse

class ReservedRouteTest extends CatsEffectSuite:

  private def loadIR(spec: String) =
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  private def counterService(opName: String, conventions: String = ""): String =
    s"""|service Demo {
        |  state {
        |    count: Int
        |  }
        |
        |  operation $opName {
        |    requires:
        |      true
        |
        |    ensures:
        |      count' = count + 1
        |  }
        |$conventions}
        |""".stripMargin

  private def errorsOf(ir: ServiceIRFull): List[ConventionDiagnostic] =
    Validate.validateRoutes(ir)

  test("ordinary operation routes produce no diagnostics"):
    loadIR(counterService("Increment")).map: ir =>
      assertEquals(errorsOf(ir), Nil)

  test("operation deriving /health is rejected"):
    loadIR(counterService("Health")).map: ir =>
      val diags = errorsOf(ir)
      assert(diags.nonEmpty, "expected a reserved-route diagnostic for /health")
      assert(diags.head.message.contains("reserved"), diags.head.message)
      assert(diags.head.message.contains("/health"), diags.head.message)
      assertEquals(diags.head.target, "Health")
      assertEquals(diags.head.property, "http_path")

  List(("Admin", "/admin"), ("Ready", "/ready"), ("Metrics", "/metrics")).foreach:
    (opName, route) =>
      test(s"operation deriving $route is rejected"):
        loadIR(counterService(opName)).map: ir =>
          val diags = errorsOf(ir)
          assert(diags.nonEmpty, s"expected a reserved-route diagnostic for $route")
          assert(diags.head.message.contains(route), diags.head.message)

  test("http_path override into the /admin prefix is rejected"):
    val conv =
      """|  conventions {
         |    Increment.http_path = "/admin/increment"
         |  }
         |""".stripMargin
    loadIR(counterService("Increment", conv)).map: ir =>
      val diags = errorsOf(ir)
      assert(diags.nonEmpty, "expected a reserved-route diagnostic for the override")
      assert(diags.head.message.contains("/admin/increment"), diags.head.message)

  test("/administrators is not caught by the /admin prefix reservation"):
    val conv =
      """|  conventions {
         |    Increment.http_path = "/administrators"
         |  }
         |""".stripMargin
    loadIR(counterService("Increment", conv)).map: ir =>
      assertEquals(errorsOf(ir), Nil)
