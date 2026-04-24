package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.openapi.OpenApi
import specrest.codegen.testutil.SpecFixtures

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
