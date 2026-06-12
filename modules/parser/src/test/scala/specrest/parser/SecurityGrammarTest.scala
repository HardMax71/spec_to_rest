package specrest.parser

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.testutil.SpecFixtures

class SecurityGrammarTest extends CatsEffectSuite:

  private given CanEqual[security_scheme_kind, security_scheme_kind] = CanEqual.derived

  private def service(body: String): String =
    s"""|service Demo {
        |  state {
        |    count: Int
        |  }
        |
        |$body
        |}
        |""".stripMargin

  private val schemes =
    """|  security {
       |    bearer: Bearer(bearer_format: "JWT")
       |    api_key: ApiKey(header: "X-API-Key")
       |    basic: Basic
       |  }
       |""".stripMargin

  test("security block populates svcSecurity with all three scheme kinds"):
    SpecFixtures.buildFromSource("schemes", service(schemes)).map: ir =>
      val ss = svcSecurity(ir)
      assertEquals(ss.map(ssdName), List("bearer", "api_key", "basic"))
      assertEquals(ssdKind(ss(0)), SsBearer(Some("JWT")): security_scheme_kind)
      assertEquals(ssdKind(ss(1)), SsApiKey("header", "X-API-Key"): security_scheme_kind)
      assertEquals(ssdKind(ss(2)), SsBasic(): security_scheme_kind)
      assert(ss.forall(s => ssdSpan(s).isDefined), "scheme decls carry spans")

  test("Bearer without arguments has no bearer format"):
    SpecFixtures
      .buildFromSource("bare-bearer", service("  security {\n    bearer: Bearer\n  }\n"))
      .map: ir =>
        assertEquals(ssdKind(svcSecurity(ir).head), SsBearer(None): security_scheme_kind)

  test("no security block means empty svcSecurity"):
    SpecFixtures.buildFromSource("none", service("")).map: ir =>
      assertEquals(svcSecurity(ir), Nil)

  test("requires_auth: single scheme lands on the operation"):
    val src = service(
      schemes +
        """|  operation Increment {
           |    requires_auth: bearer
           |    requires: true
           |    ensures: count' = count + 1
           |  }
           |""".stripMargin
    )
    SpecFixtures.buildFromSource("single", src).map: ir =>
      assertEquals(operRequiresAuth(svcOperations(ir).head), Some(List("bearer")))

  test("requires_auth: comma list keeps order (OR alternatives)"):
    val src = service(
      schemes +
        """|  operation Increment {
           |    requires_auth: bearer, api_key
           |    ensures: count' = count + 1
           |  }
           |""".stripMargin
    )
    SpecFixtures.buildFromSource("list", src).map: ir =>
      assertEquals(operRequiresAuth(svcOperations(ir).head), Some(List("bearer", "api_key")))

  test("operation without requires_auth has None (public, no annotation)"):
    val src = service(
      schemes +
        """|  operation Increment {
           |    ensures: count' = count + 1
           |  }
           |""".stripMargin
    )
    SpecFixtures.buildFromSource("public", src).map: ir =>
      assertEquals(operRequiresAuth(svcOperations(ir).head), None)

  test("unknown scheme kind is a build error"):
    SpecFixtures
      .buildExpectingError("bad-kind", service("  security {\n    oa: OAuth2\n  }\n"))
      .map: err =>
        assert(err.contains("unknown security scheme kind 'OAuth2'"), err)

  test("ApiKey without a location argument is a build error"):
    SpecFixtures
      .buildExpectingError("bad-apikey", service("  security {\n    k: ApiKey\n  }\n"))
      .map: err =>
        assert(err.contains("ApiKey needs a location argument"), err)

  test("duplicate scheme arguments are a build error (not silently last-wins)"):
    SpecFixtures
      .buildExpectingError(
        "dup-arg",
        service("  security {\n    k: ApiKey(header: \"A\", header: \"B\")\n  }\n")
      )
      .map: err =>
        assert(err.contains("duplicate argument 'header'"), err)

  test("Bearer with an unknown argument is a build error"):
    SpecFixtures
      .buildExpectingError(
        "bad-bearer-arg",
        service("  security {\n    b: Bearer(formaat: \"JWT\")\n  }\n")
      )
      .map: err =>
        assert(err.contains("unknown Bearer argument 'formaat'"), err)

  test("security scheme names work as identifiers elsewhere (soft keywords)"):
    val src = service(
      """|  operation Touch {
         |    input: security: String
         |    ensures: count' = count
         |  }
         |""".stripMargin
    )
    SpecFixtures.buildFromSource("soft-kw", src).map: ir =>
      assertEquals(prmName(operInputs(svcOperations(ir).head).head), "security")

  test("IR JSON round-trips security schemes and requiresAuth"):
    val src = service(
      schemes +
        """|  operation Increment {
           |    requires_auth: bearer, api_key
           |    ensures: count' = count + 1
           |  }
           |""".stripMargin
    )
    SpecFixtures.buildFromSource("roundtrip", src).map: ir =>
      val json = specrest.ir.Serialize.toPrettyString(ir)
      specrest.ir.Serialize.fromJson(json) match
        case Left(e) => fail(s"round-trip decode failed: $e")
        case Right(decoded) =>
          assertEquals(svcSecurity(decoded).map(ssdName), List("bearer", "api_key", "basic"))
          assertEquals(
            operRequiresAuth(svcOperations(decoded).head),
            Some(List("bearer", "api_key"))
          )
