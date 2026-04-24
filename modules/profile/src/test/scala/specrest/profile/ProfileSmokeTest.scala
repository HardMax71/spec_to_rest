package specrest.profile

import munit.CatsEffectSuite
import specrest.profile.testutil.SpecFixtures

class ProfileSmokeTest extends CatsEffectSuite:

  private val allFixtures: List[String] = List(
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
    SpecFixtures.loadIR("url_shortener").map: ir =>
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
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val ps         = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      val urlMapping = ps.entities.find(_.entityName == "UrlMapping").get
      val clickCount = urlMapping.fields.find(_.fieldName == "click_count").get
      assertEquals(clickCount.pythonType, "int")
      assertEquals(clickCount.pydanticType, "int")
      assertEquals(clickCount.sqlalchemyColumnType, "Integer")
      assertEquals(clickCount.columnName, "click_count")
      assertEquals(clickCount.nullable, false)

  allFixtures.foreach: n =>
    test(s"fixture $n builds a profiled service"):
      SpecFixtures.loadIR(n).map: ir =>
        val ps = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
        assertEquals(ps.ir.name, ir.name, s"fixture $n")
