package specrest.convention
import munit.CatsEffectSuite
import specrest.convention.testutil.SpecFixtures
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*

import java.nio.file.Files
import java.nio.file.Path as JPath
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

class ConventionSmokeTest extends CatsEffectSuite:

  private given CanEqual[http_method, http_method]               = CanEqual.derived
  private given CanEqual[synthesis_strategy, synthesis_strategy] = CanEqual.derived

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
        val diagnostics     = Validate.validateConventions(svcConventions(ir), ir)
        assertEquals(classifications.size, svcOperations(ir).size, s"$name classifications")
        assertEquals(endpoints.size, svcOperations(ir).size, s"$name endpoints")
        assert(schemaTables(schema).nonEmpty || svcEntities(ir).isEmpty, s"$name schema")
        val _ = diagnostics

  test("url_shortener endpoints match expected verbs / paths / statuses"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val classifications = Classify.classifyOperations(ir)
      val endpoints       = Path.deriveEndpoints(classifications, ir).map(e => e.operationName -> e).toMap

      assertEquals(endpoints("Shorten").method, POST(): http_method)
      assertEquals(endpoints("Shorten").path, "/shorten")
      assertEquals(endpoints("Shorten").successStatus, 201)

      assertEquals(endpoints("Resolve").method, GET(): http_method)
      assertEquals(endpoints("Resolve").path, "/{code}")
      assertEquals(endpoints("Resolve").successStatus, 302)

      assertEquals(endpoints("Delete").method, DELETE(): http_method)
      assertEquals(endpoints("Delete").path, "/{code}")
      assertEquals(endpoints("Delete").successStatus, 204)

      assertEquals(endpoints("ListAll").method, GET(): http_method)
      assertEquals(endpoints("ListAll").path, "/urls")
      assertEquals(endpoints("ListAll").successStatus, 200)

  private case class StrategyCase(
      label: String,
      fixture: String,
      expectations: List[(String, synthesis_strategy)]
  )

  List(
    StrategyCase(
      "url_shortener: Shorten=LlmSynthesis, Delete=DirectEmit (#31 AC)",
      "url_shortener",
      List(
        "Shorten" -> LlmSynthesis(),
        "Delete"  -> DirectEmit(),
        "Resolve" -> LlmSynthesis()
      )
    ),
    StrategyCase(
      "safe_counter: arithmetic in ensures forces LLM",
      "safe_counter",
      List(
        "Increment" -> LlmSynthesis(),
        "Decrement" -> LlmSynthesis()
      )
    ),
    StrategyCase(
      "todo_list: pure-CRUD ops collapse to DirectEmit; CreateTodo escalates",
      "todo_list",
      List(
        "Archive"    -> DirectEmit(),
        "DeleteTodo" -> DirectEmit(),
        "GetTodo"    -> DirectEmit(),
        "CreateTodo" -> LlmSynthesis()
      )
    )
  ).foreach: c =>
    test(s"synthesis strategy — ${c.label}"):
      SpecFixtures.loadIR(c.fixture).map: ir =>
        val byName =
          Classify.classifyOperations(ir).map(x => classificationOperationName(x) -> x).toMap
        c.expectations.foreach: (op, expected) =>
          assertEquals(classificationStrategy(byName(op)), expected, s"${c.fixture}.$op")

  test("ecommerce: aggregate-invariant detector emits a Sum trigger on line_items"):
    SpecFixtures.loadIR("ecommerce").map: ir =>
      val schema = Schema.deriveSchema(ir)
      val trig   = schemaTriggers(schema)
        .find(t => triggerName(t) == "trg_recalc_order_subtotal")
        .getOrElse(fail(s"trigger missing; got names=${schemaTriggers(schema).map(triggerName)}"))
      assertEquals(triggerFunctionName(trig), "recalc_order_subtotal")
      assertEquals(triggerTargetTable(trig), "orders")
      assertEquals(triggerTargetColumn(trig), "subtotal")
      assertEquals(triggerSourceTable(trig), "line_items")
      assertEquals(triggerSourceForeignKey(trig), "order_id")
      assert(triggerAggregateOf(trig).isInstanceOf[SumAgg])
      assertEquals(triggerSourceColumn(trig), Some("line_total"))

  test("ecommerce: Product.partial_index convention produces filterClause on index"):
    SpecFixtures.loadIR("ecommerce").map: ir =>
      val schema   = Schema.deriveSchema(ir)
      val products = schemaTables(schema)
        .find(t => tableName(t) == "products")
        .getOrElse(fail(s"products table missing; got=${schemaTables(schema).map(tableName)}"))
      val partial = tableIndexes(products)
        .find(i => indexFilterClause(i).isDefined)
        .getOrElse(fail(s"no partial index on products; got=${tableIndexes(products)}"))
      assertEquals(indexFilterClause(partial), Some("active = true"))
      assertEquals(indexColumns(partial), List("active"))
      assert(!indexUnique(partial))

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
