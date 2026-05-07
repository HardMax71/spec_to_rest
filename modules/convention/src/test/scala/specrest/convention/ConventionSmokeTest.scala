package specrest.convention

import munit.CatsEffectSuite
import specrest.convention.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Path as JPath
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

class ConventionSmokeTest extends CatsEffectSuite:

  private val specDir: JPath = Paths.get("fixtures/spec")

  private val fixtures: List[JPath] =
    if Files.isDirectory(specDir) then
      Files.list(specDir).iterator.asScala
        .filter(_.toString.endsWith(".spec"))
        .toList
        .sortBy(_.getFileName.toString)
    else Nil

  fixtures.foreach: fixture =>
    val name = fixture.getFileName.toString.stripSuffix(".spec")
    test(s"classify + derive + validate for $name"):
      SpecFixtures.loadIR(name).map: ir =>
        val classifications = Classify.classifyOperations(ir)
        val endpoints       = Path.deriveEndpoints(classifications, ir)
        val schema          = Schema.deriveSchema(ir)
        val diagnostics     = Validate.validateConventions(ir.n, ir)
        assertEquals(classifications.size, ir.g.size, s"$name classifications")
        assertEquals(endpoints.size, ir.g.size, s"$name endpoints")
        assert(schema.tables.nonEmpty || ir.c.isEmpty, s"$name schema")
        val _ = diagnostics

  test("url_shortener endpoints match expected verbs / paths / statuses"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val classifications = Classify.classifyOperations(ir)
      val endpoints       = Path.deriveEndpoints(classifications, ir).map(e => e.operationName -> e).toMap

      assertEquals(endpoints("Shorten").method, HttpMethod.POST)
      assertEquals(endpoints("Shorten").path, "/shorten")
      assertEquals(endpoints("Shorten").successStatus, 201)

      assertEquals(endpoints("Resolve").method, HttpMethod.GET)
      assertEquals(endpoints("Resolve").path, "/{code}")
      assertEquals(endpoints("Resolve").successStatus, 302)

      assertEquals(endpoints("Delete").method, HttpMethod.DELETE)
      assertEquals(endpoints("Delete").path, "/{code}")
      assertEquals(endpoints("Delete").successStatus, 204)

      assertEquals(endpoints("ListAll").method, HttpMethod.GET)
      assertEquals(endpoints("ListAll").path, "/urls")
      assertEquals(endpoints("ListAll").successStatus, 200)

  test("synthesis strategy: url_shortener Shorten=LlmSynthesis, Delete=DirectEmit (#31 AC)"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val byName = Classify.classifyOperations(ir).map(c => c.operationName -> c).toMap
      assertEquals(byName("Shorten").strategy, SynthesisStrategy.LlmSynthesis)
      assertEquals(byName("Delete").strategy, SynthesisStrategy.DirectEmit)
      assertEquals(byName("Resolve").strategy, SynthesisStrategy.LlmSynthesis)

  test("synthesis strategy: safe_counter increment/decrement need LLM (arithmetic in ensures)"):
    SpecFixtures.loadIR("safe_counter").map: ir =>
      val byName = Classify.classifyOperations(ir).map(c => c.operationName -> c).toMap
      assertEquals(byName("Increment").strategy, SynthesisStrategy.LlmSynthesis)
      assertEquals(byName("Decrement").strategy, SynthesisStrategy.LlmSynthesis)

  test("synthesis strategy: todo_list pure-CRUD ops (Archive, DeleteTodo, GetTodo) emit directly"):
    SpecFixtures.loadIR("todo_list").map: ir =>
      val byName = Classify.classifyOperations(ir).map(c => c.operationName -> c).toMap
      assertEquals(byName("Archive").strategy, SynthesisStrategy.DirectEmit)
      assertEquals(byName("DeleteTodo").strategy, SynthesisStrategy.DirectEmit)
      assertEquals(byName("GetTodo").strategy, SynthesisStrategy.DirectEmit)
      assertEquals(byName("CreateTodo").strategy, SynthesisStrategy.LlmSynthesis)

  test("naming helpers"):
    assertEquals(Naming.pluralize("user"), "users")
    assertEquals(Naming.pluralize("child"), "children")
    assertEquals(Naming.pluralize("box"), "boxes")
    assertEquals(Naming.pluralize("city"), "cities")
    assertEquals(Naming.pluralize("data"), "data")
    assertEquals(Naming.pluralize("Person"), "People")
    assertEquals(Naming.toKebabCase("UrlMapping"), "url-mapping")
    assertEquals(Naming.toSnakeCase("UrlMapping"), "url_mapping")
    assertEquals(Naming.toTableName("UrlMapping"), "url_mappings")
    assertEquals(Naming.toPathSegment("UrlMapping"), "url-mappings")
    assertEquals(Naming.toColumnName("clickCount"), "click_count")
    assertEquals(Naming.toColumnName("click_count"), "click_count")
