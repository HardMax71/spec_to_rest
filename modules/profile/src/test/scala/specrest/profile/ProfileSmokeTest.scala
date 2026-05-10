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

  test("registry lists ts-express-postgres"):
    assert(Registry.listProfiles.contains("ts-express-postgres"))
    val p = Registry.getProfile("ts-express-postgres")
    assertEquals(p.name, "ts-express-postgres")
    assertEquals(p.language, "ts")
    assertEquals(p.framework, "express")
    assertEquals(p.orm, "prisma")
    assertEquals(p.database, "postgres")
    assert(p.dependencies.exists(_.name == "express"))
    assert(p.dependencies.exists(_.name == "@prisma/client"))
    assert(p.dependencies.exists(_.name == "zod"))

  test("ts-express-postgres profiled field types map to TS primitives"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val ps         = Annotate.buildProfiledService(ir, "ts-express-postgres")
      val urlMapping = ps.entities.find(_.entityName == "UrlMapping").get
      val clickCount = urlMapping.fields.find(_.fieldName == "click_count").get
      assertEquals(clickCount.domainType, "number")
      assertEquals(clickCount.validationType, "number")
      assertEquals(clickCount.ormColumnType, "INTEGER")
      assertEquals(clickCount.columnName, "click_count")
      assertEquals(clickCount.nullable, false)
      val createdAt = urlMapping.fields.find(_.fieldName == "created_at").get
      assertEquals(createdAt.domainType, "Date")
      assertEquals(createdAt.ormColumnType, "TIMESTAMPTZ")

  test("unknown profile throws"):
    intercept[RuntimeException]:
      Registry.getProfile("rust-actix-sqlite")

  test("url_shortener profiled service has expected shape"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val ps = Annotate.buildProfiledService(ir, "python-fastapi-postgres")

      assertEquals(ps.operations.size, ir.g.size)
      assertEquals(ps.entities.size, ir.c.size)
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
      assertEquals(clickCount.domainType, "int")
      assertEquals(clickCount.validationType, "int")
      assertEquals(clickCount.ormColumnType, "Integer")
      assertEquals(clickCount.columnName, "click_count")
      assertEquals(clickCount.nullable, false)

  allFixtures.foreach: n =>
    test(s"fixture $n builds a profiled service"):
      SpecFixtures.loadIR(n).map: ir =>
        val ps = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
        assertEquals(ps.ir.a, ir.a, s"fixture $n")
