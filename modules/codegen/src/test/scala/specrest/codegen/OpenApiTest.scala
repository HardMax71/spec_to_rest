package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.openapi.OpenApi
import specrest.codegen.testutil.SpecFixtures
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.Annotate

class OpenApiTest extends CatsEffectSuite:

  test("buildOpenApiDocument returns a well-formed document"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val doc = OpenApi.buildOpenApiDocument(profiled)
      assertEquals(doc.openapi, "3.1.0")
      assertEquals(doc.info.title, "UrlShortener")
      assert(doc.paths.nonEmpty)
      assert(doc.components.schemas.nonEmpty)

  test("paths include /shorten POST + /{code} GET"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val doc     = OpenApi.buildOpenApiDocument(profiled)
      val shorten = doc.paths.get("/shorten").flatMap(_.post)
      assert(shorten.isDefined, s"expected POST /shorten; paths=${doc.paths.keys}")
      val resolve = doc.paths.get("/{code}").flatMap(_.get)
      assert(resolve.isDefined, "expected GET /{code}")

  test("temporal_demo: x-invariant carries one entry per invariant; YAML emits hyphenated key"):
    SpecFixtures.loadProfiled("temporal_demo").map: profiled =>
      val doc = OpenApi.buildOpenApiDocument(profiled)
      assertEquals(
        doc.xInvariant,
        Some(Map("usersAreValid" -> "(all u in users | (u = u))"))
      )
      val yaml = OpenApi.serialize(doc)
      assert(yaml.contains("x-invariant:"), s"missing x-invariant key in YAML:\n$yaml")
      assert(!yaml.contains("xInvariant:"), s"camelCase leaked to YAML:\n$yaml")

  test("temporal_demo: x-temporal carries kind + expr per temporal"):
    SpecFixtures.loadProfiled("temporal_demo").map: profiled =>
      val doc = OpenApi.buildOpenApiDocument(profiled)
      val map = doc.xTemporal.getOrElse(fail("expected non-empty x-temporal"))
      assertEquals(map("allUsersAlwaysValid").kind, "always")
      assertEquals(map("nonDeletedEventuallyExists").kind, "eventually")
      val yaml = OpenApi.serialize(doc)
      assert(yaml.contains("x-temporal:"), s"missing x-temporal key in YAML:\n$yaml")
      assert(!yaml.contains("xTemporal:"), s"camelCase leaked to YAML:\n$yaml")

  test("url_shortener (no temporals): x-temporal is None and absent from YAML"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val doc  = OpenApi.buildOpenApiDocument(profiled)
      val yaml = OpenApi.serialize(doc)
      assertEquals(doc.xTemporal, None)
      assert(!yaml.contains("x-temporal"), s"x-temporal key should be absent:\n$yaml")

  test("duplicate-named invariants: first keeps bare name, second gets _0, third gets _1"):
    val ir = serviceIRWith(
      invariants = List(
        InvariantDeclFull(Some("dup"), BoolLitF(true, None), None),
        InvariantDeclFull(Some("dup"), BoolLitF(false, None), None),
        InvariantDeclFull(Some("uniq"), BoolLitF(true, None), None)
      ),
      temporals = Nil
    )
    val doc =
      OpenApi.buildOpenApiDocument(Annotate.buildProfiledService(ir, "python-fastapi-postgres"))
    val map = doc.xInvariant.getOrElse(fail("expected x-invariant"))
    assertEquals(map.size, 3, s"expected 3 entries (two dups + one unique); got: $map")
    assert(map.contains("dup"), s"first 'dup' must keep bare name; got keys: ${map.keys}")
    assert(map.contains("dup_0"), s"second 'dup' must be suffixed with _0; got keys: ${map.keys}")
    assert(map.contains("uniq"), s"unique key must NOT be suffixed; got keys: ${map.keys}")

  test("duplicate-named temporals: first keeps bare name, second gets _0"):
    val arg = BoolLitF(true, None)
    val ir = serviceIRWith(
      invariants = Nil,
      temporals = List(
        TemporalDeclFull("dup", TbAlways(arg), None),
        TemporalDeclFull("dup", TbEventually(arg), None)
      )
    )
    val doc =
      OpenApi.buildOpenApiDocument(Annotate.buildProfiledService(ir, "python-fastapi-postgres"))
    val map = doc.xTemporal.getOrElse(fail("expected x-temporal"))
    assertEquals(map.size, 2, s"both 'dup' temporals must be preserved; got: $map")
    assertEquals(map("dup").kind, "always")
    assertEquals(map("dup_0").kind, "eventually")

  test("disambiguation handles a base-name collision with an explicit indexed name"):
    // Pathological case flagged by cubic on PR #216: input `[foo, foo_0, foo]`.
    // A naive `<base>_<idx>` scheme would map the first 'foo' to 'foo_0',
    // colliding with the explicit 'foo_0'. Iterative disambiguation must
    // skip already-used keys to preserve all three entries.
    val ir = serviceIRWith(
      invariants = List(
        InvariantDeclFull(Some("foo"), BoolLitF(true, None), None),
        InvariantDeclFull(Some("foo_0"), BoolLitF(false, None), None),
        InvariantDeclFull(Some("foo"), BoolLitF(true, None), None)
      ),
      temporals = Nil
    )
    val doc =
      OpenApi.buildOpenApiDocument(Annotate.buildProfiledService(ir, "python-fastapi-postgres"))
    val map = doc.xInvariant.getOrElse(fail("expected x-invariant"))
    assertEquals(
      map.size,
      3,
      s"all 3 entries must survive the foo / foo_0 / foo collision; got: $map"
    )
    assert(map.contains("foo"), s"first 'foo' keeps bare name; got: ${map.keys}")
    assert(map.contains("foo_0"), s"explicit 'foo_0' is preserved as-is; got: ${map.keys}")
    assert(
      map.contains("foo_1"),
      s"second 'foo' must escalate past foo_0 to foo_1; got: ${map.keys}"
    )

  test("temporal_demo: x-temporal preserves spec declaration order in serialized YAML"):
    SpecFixtures.loadProfiled("temporal_demo").map: profiled =>
      val yaml = OpenApi.serialize(OpenApi.buildOpenApiDocument(profiled))
      // temporal_demo declares nonDeletedEventuallyExists BEFORE allUsersAlwaysValid
      val nonDel = yaml.indexOf("nonDeletedEventuallyExists:")
      val allUsr = yaml.indexOf("allUsersAlwaysValid:")
      assert(nonDel >= 0 && allUsr >= 0, s"both temporals expected in YAML:\n$yaml")
      assert(
        nonDel < allUsr,
        s"x-temporal must preserve spec declaration order (nonDeleted first, allUsers second); got: $yaml"
      )

  private def serviceIRWith(
      invariants: List[InvariantDeclFull],
      temporals: List[TemporalDeclFull]
  ): ServiceIRFull =
    ServiceIRFull(
      a = "DupSvc",
      b = Nil,
      c = Nil,
      d = Nil,
      e = Nil,
      f = None,
      g = Nil,
      h = Nil,
      i = invariants,
      j = temporals,
      k = Nil,
      l = Nil,
      m = Nil,
      n = None,
      o = None
    )

  test("components include Create / Read / Update schemas + ErrorResponse"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val doc  = OpenApi.buildOpenApiDocument(profiled)
      val keys = doc.components.schemas.keySet
      assert(keys.contains("UrlMappingCreate"))
      assert(keys.contains("UrlMappingRead"))
      assert(keys.contains("UrlMappingUpdate"))
      assert(keys.contains("ErrorResponse"))

  test("serialized YAML produces valid OpenAPI 3.1"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val doc  = OpenApi.buildOpenApiDocument(profiled)
      val yaml = OpenApi.serialize(doc)
      assert(yaml.startsWith("openapi:"))
      assert(yaml.contains("3.1.0"))
      assert(yaml.contains("paths:"))
      assert(yaml.contains("components:"))
      assert(yaml.contains("UrlMappingRead"))
      assert(yaml.contains("$ref: '#/components/schemas/UrlMappingCreate'"))

  test("omits absent Option fields from serialized YAML"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val doc       = OpenApi.buildOpenApiDocument(profiled)
      val yaml      = OpenApi.serialize(doc)
      val nullField = """^\s+\w+:\s+null\s*$""".r
      yaml.split("\n").foreach: line =>
        assert(
          nullField.findFirstIn(line).isEmpty,
          s"unexpected null-valued field: '$line'"
        )

  test("enum / null key names renamed correctly"):
    import specrest.codegen.openapi.*
    val schema = SchemaObject(
      `type` = Some(List("string")),
      enum_ = Some(List("ok", "error"))
    )
    val out = OpenApi.serialize(OpenApiDocument(
      openapi = "3.1.0",
      info = InfoObject("Test", "1.0", None),
      servers = Nil,
      paths = Map("/test" -> PathItemObject(get =
        Some(OperationObject(
          operationId = "test",
          summary = None,
          description = None,
          tags = Nil,
          parameters = None,
          requestBody = None,
          responses = Map("200" -> ResponseObject(
            description = "ok",
            headers = None,
            content = Some(Map("application/json" -> MediaTypeObject(schema)))
          ))
        ))
      )),
      components = ComponentsObject(schemas = Map.empty),
      tags = Nil
    ))
    assert(out.contains("enum:"), s"expected 'enum:' key; got: ${out.take(400)}")
    assert(!out.contains("enum_:"), s"underscore suffix leaked to YAML: ${out.take(400)}")
