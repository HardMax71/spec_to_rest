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

  private case class StrategyCase(
      label: String,
      fixture: String,
      expectations: List[(String, SynthesisStrategy)]
  )

  List(
    StrategyCase(
      "url_shortener: Shorten=LlmSynthesis, Delete=DirectEmit (#31 AC)",
      "url_shortener",
      List(
        "Shorten" -> SynthesisStrategy.LlmSynthesis,
        "Delete"  -> SynthesisStrategy.DirectEmit,
        "Resolve" -> SynthesisStrategy.LlmSynthesis
      )
    ),
    StrategyCase(
      "safe_counter: arithmetic in ensures forces LLM",
      "safe_counter",
      List(
        "Increment" -> SynthesisStrategy.LlmSynthesis,
        "Decrement" -> SynthesisStrategy.LlmSynthesis
      )
    ),
    StrategyCase(
      "todo_list: pure-CRUD ops collapse to DirectEmit; CreateTodo escalates",
      "todo_list",
      List(
        "Archive"    -> SynthesisStrategy.DirectEmit,
        "DeleteTodo" -> SynthesisStrategy.DirectEmit,
        "GetTodo"    -> SynthesisStrategy.DirectEmit,
        "CreateTodo" -> SynthesisStrategy.LlmSynthesis
      )
    )
  ).foreach: c =>
    test(s"synthesis strategy — ${c.label}"):
      SpecFixtures.loadIR(c.fixture).map: ir =>
        val byName = Classify.classifyOperations(ir).map(x => x.operationName -> x).toMap
        c.expectations.foreach: (op, expected) =>
          assertEquals(byName(op).strategy, expected, s"${c.fixture}.$op")

  test("ecommerce: aggregate-invariant detector emits a Sum trigger on line_items"):
    SpecFixtures.loadIR("ecommerce").map: ir =>
      val schema = Schema.deriveSchema(ir)
      val trig = schema.triggers
        .find(_.name == "trg_recalc_order_subtotal")
        .getOrElse(fail(s"trigger missing; got names=${schema.triggers.map(_.name)}"))
      assertEquals(trig.functionName, "recalc_order_subtotal")
      assertEquals(trig.targetTable, "orders")
      assertEquals(trig.targetColumn, "subtotal")
      assertEquals(trig.sourceTable, "line_items")
      assertEquals(trig.sourceForeignKey, "order_id")
      assertEquals(trig.aggregate, TriggerAggregate.Sum)
      assertEquals(trig.sourceColumn, Some("line_total"))

  test("ecommerce: Product.partial_index convention produces filterClause on index"):
    SpecFixtures.loadIR("ecommerce").map: ir =>
      val schema = Schema.deriveSchema(ir)
      val products = schema.tables
        .find(_.name == "products")
        .getOrElse(fail(s"products table missing; got=${schema.tables.map(_.name)}"))
      val partial = products.indexes
        .find(_.filterClause.isDefined)
        .getOrElse(fail(s"no partial index on products; got=${products.indexes}"))
      assertEquals(partial.filterClause, Some("active = true"))
      assertEquals(partial.columns, List("active"))
      assert(!partial.unique)

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
