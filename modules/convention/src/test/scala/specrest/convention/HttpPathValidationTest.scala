package specrest.convention

import cats.effect.IO
import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.parser.Builder
import specrest.parser.Parse

class HttpPathValidationTest extends CatsEffectSuite:

  private def demoService(
      opName: String,
      inputLine: String = "",
      conventions: String = ""
  ): String =
    s"""|service Demo {
        |  state {
        |    count: Int
        |  }
        |
        |  operation $opName {
        |$inputLine    requires:
        |      true
        |
        |    ensures:
        |      count' = count + 1
        |  }
        |$conventions}
        |""".stripMargin

  private def conv(rule: String): String =
    s"""|  conventions {
        |    $rule
        |  }
        |""".stripMargin

  private def httpPathRule(op: String, path: String): String =
    val escaped = path.replace("\\", "\\\\").replace("\"", "\\\"")
    s"$op.http_path = \"$escaped\""

  private def loadIR(spec: String): IO[ServiceIRFull] =
    Parse.parseSpec(spec).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  test("a plain http_path override with no placeholder is accepted"):
    val spec = demoService("Increment", conventions = conv(httpPathRule("Increment", "/increment")))
    loadIR(spec).map(Validate.validateRoutes).assertEquals(Nil)

  test("a path parameter that matches an input is accepted"):
    val spec = demoService(
      "Adjust",
      inputLine = "    input: id: Int\n",
      conventions = conv(httpPathRule("Adjust", "/counts/{id}"))
    )
    loadIR(spec).map(Validate.validateRoutes).assertEquals(Nil)

  test("a double quote in http_path is rejected as an invalid character"):
    val spec =
      demoService("Increment", conventions = conv(httpPathRule("Increment", "/increment\"")))
    loadIR(spec).map: ir =>
      val diags = Validate.validateRoutes(ir)
      assert(diags.exists(_.message.contains("invalid path character")), diags.mkString("; "))
      assertEquals(diags.head.property, "http_path")

  test("a path placeholder with no matching input is rejected"):
    val spec =
      demoService("Increment", conventions = conv(httpPathRule("Increment", "/increment/{bogus}")))
    loadIR(spec).map: ir =>
      val diags = Validate.validateRoutes(ir)
      assert(diags.exists(_.message.contains("no matching input")), diags.mkString("; "))

  test("a repeated path placeholder is rejected"):
    val spec = demoService(
      "Adjust",
      inputLine = "    input: id: Int\n",
      conventions = conv(httpPathRule("Adjust", "/counts/{id}/{id}"))
    )
    loadIR(spec).map: ir =>
      val diags = Validate.validateRoutes(ir)
      assert(diags.exists(_.message.contains("repeats path parameter")), diags.mkString("; "))

  test("a non-ASCII convention string is a clean build error, not a crash"):
    val spec = demoService("Increment", conventions = conv(httpPathRule("Increment", "/é")))
    Parse.parseSpec(spec).flatMap:
      case Left(err) => IO(fail(s"parse error: $err"))
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Left(err) => assert(err.toString.toLowerCase.contains("ascii"), err.toString)
          case Right(_)  => fail("expected a build error for the non-ASCII http_path value")
