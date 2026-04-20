package specrest.codegen

import specrest.codegen.openapi.OpenApi
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

import java.nio.file.Files
import java.nio.file.Paths

class OpenApiTest extends munit.FunSuite:

  private def buildProfiled(name: String): specrest.profile.ProfiledService =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty)
    val ir = Builder.buildIR(parsed.tree).toOption.get
    Annotate.buildProfiledService(ir, "python-fastapi-postgres")

  test("buildOpenApiDocument returns a well-formed document"):
    val doc = OpenApi.buildOpenApiDocument(buildProfiled("url_shortener"))
    assertEquals(doc.openapi, "3.1.0")
    assertEquals(doc.info.title, "UrlShortener")
    assert(doc.paths.nonEmpty)
    assert(doc.components.schemas.nonEmpty)

  test("paths include /shorten POST + /{code} GET"):
    val doc     = OpenApi.buildOpenApiDocument(buildProfiled("url_shortener"))
    val shorten = doc.paths.get("/shorten").flatMap(_.post)
    assert(shorten.isDefined, s"expected POST /shorten; paths=${doc.paths.keys}")
    val resolve = doc.paths.get("/{code}").flatMap(_.get)
    assert(resolve.isDefined, "expected GET /{code}")

  test("components include Create / Read / Update schemas + ErrorResponse"):
    val doc  = OpenApi.buildOpenApiDocument(buildProfiled("url_shortener"))
    val keys = doc.components.schemas.keySet
    assert(keys.contains("UrlMappingCreate"))
    assert(keys.contains("UrlMappingRead"))
    assert(keys.contains("UrlMappingUpdate"))
    assert(keys.contains("ErrorResponse"))

  test("serialized YAML produces valid OpenAPI 3.1"):
    val doc  = OpenApi.buildOpenApiDocument(buildProfiled("url_shortener"))
    val yaml = OpenApi.serialize(doc)
    assert(yaml.startsWith("openapi:"))
    assert(yaml.contains("3.1.0"))
    assert(yaml.contains("paths:"))
    assert(yaml.contains("components:"))
    assert(yaml.contains("UrlMappingRead"))
    assert(yaml.contains("$ref: '#/components/schemas/UrlMappingCreate'"))

  test("omits absent Option fields from serialized YAML"):
    // Scala's None values should NOT emit `field: null` — those are supposed to be omitted.
    // (The literal string `null` may still appear as a type marker in OpenAPI 3.1 nullable
    // schemas like `type: [string, null]` — that's correct per spec.)
    val doc  = OpenApi.buildOpenApiDocument(buildProfiled("url_shortener"))
    val yaml = OpenApi.serialize(doc)
    // No field should serialize as `key: null` (absent Options must be omitted, not nulled).
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
