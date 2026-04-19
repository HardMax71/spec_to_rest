package specrest.profile

import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.Paths

class ProfileSmokeTest extends munit.FunSuite:

  private def buildFixture(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    Builder.buildIR(parsed.tree)

  test("registry lists python-fastapi-postgres"):
    assert(Registry.listProfiles.contains("python-fastapi-postgres"))
    val p = Registry.getProfile("python-fastapi-postgres")
    assertEquals(p.name, "python-fastapi-postgres")
    assertEquals(p.language, "python")
    assertEquals(p.database, "postgres")

  test("unknown profile throws"):
    intercept[RuntimeException]:
      Registry.getProfile("rust-actix-sqlite")

  test("url_shortener profiled service has expected shape"):
    val ir = buildFixture("url_shortener")
    val ps = Annotate.buildProfiledService(ir, "python-fastapi-postgres")

    assertEquals(ps.operations.size, ir.operations.size)
    assertEquals(ps.entities.size, ir.entities.size)
    assert(ps.schema.tables.nonEmpty)
    assert(ps.endpoints.nonEmpty)

    val urlMapping = ps.entities.find(_.entityName == "UrlMapping").get
    assertEquals(urlMapping.tableName, "url_mappings")
    assertEquals(urlMapping.modelFileName, "url_mapping.py")
    assertEquals(urlMapping.routerFileName, "url_mappings.py")
    assertEquals(urlMapping.createSchemaName, "UrlMappingCreate")
    assertEquals(urlMapping.readSchemaName, "UrlMappingRead")

  test("profiled field types map to python primitives"):
    val ir         = buildFixture("url_shortener")
    val ps         = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
    val urlMapping = ps.entities.find(_.entityName == "UrlMapping").get
    val clickCount = urlMapping.fields.find(_.fieldName == "click_count").get
    assertEquals(clickCount.pythonType, "int")
    assertEquals(clickCount.pydanticType, "int")
    assertEquals(clickCount.sqlalchemyColumnType, "Integer")
    assertEquals(clickCount.columnName, "click_count")
    assertEquals(clickCount.nullable, false)

  test("all fixtures build profiled services without exceptions"):
    val names = List(
      "auth_service",
      "broken_decrement",
      "broken_url_shortener",
      "convention_errors",
      "dead_op",
      "ecommerce",
      "edge_cases",
      "safe_counter",
      "todo_list",
      "unreachable_op",
      "unsat_invariants",
      "url_shortener"
    )
    names.foreach: n =>
      val ir = buildFixture(n)
      val ps = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      assertEquals(ps.ir.name, ir.name, s"fixture $n")
