package specrest.verify

import cats.effect.IO
import io.circe.Json
import io.circe.parser
import munit.CatsEffectSuite
import specrest.verify.testutil.SpecFixtures

import java.nio.file.Files
import java.nio.file.Paths

class JsonReportTest extends CatsEffectSuite:

  private val cases: List[(String, Boolean, Option[String])] = List(
    ("safe_counter", true, None),
    ("unsat_invariants", false, Some("contradictory_invariants")),
    ("broken_url_shortener", false, Some("invariant_violation_by_operation"))
  )

  cases.foreach: (fixture, expectedOk, expectedCategory) =>
    test(s"JsonReport snapshot matches golden for $fixture"):
      val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true)
      for
        ir       <- SpecFixtures.loadIR(fixture)
        report   <- Consistency.runConsistencyChecks(ir, cfg)
        json      = JsonReport.toJson(s"fixtures/spec/$fixture.spec", report, 0.0)
        canonical = stripTimings(json)
        _ = assertEquals(
              canonical.hcursor.downField("schemaVersion").as[Int].toOption,
              Some(JsonReport.SchemaVersion)
            )
        _          = assertEquals(canonical.hcursor.downField("ok").as[Boolean].toOption, Some(expectedOk))
        goldenPath = Paths.get(s"fixtures/golden/verify_report/$fixture.json")
        rendered   = JsonReport.render(canonical)
        exists    <- IO.blocking(Files.exists(goldenPath))
        _ <-
          if !exists then
            IO.blocking {
              Files.createDirectories(goldenPath.getParent)
              Files.writeString(goldenPath, rendered)
              fail(s"wrote initial golden at $goldenPath — rerun the test")
            }
          else
            IO.blocking(Files.readString(goldenPath)).map: expected =>
              assertEquals(rendered, expected, s"golden mismatch — update $goldenPath if intended")
      yield expectedCategory.foreach: category =>
        val categories = canonical.hcursor
          .downField("checks")
          .values
          .getOrElse(Vector.empty)
          .toList
          .flatMap(_.hcursor.downField("diagnostic").downField("category").as[String].toOption)
        assert(
          categories.contains(category),
          s"expected category '$category' among $categories"
        )

  test("JsonReport is round-trip-parseable via circe"):
    val cfg = VerificationConfig(timeoutMs = 30_000L, captureCore = true)
    for
      ir     <- SpecFixtures.loadIR("broken_url_shortener")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val rendered = JsonReport.render(
        JsonReport.toJson("fixtures/spec/broken_url_shortener.spec", report, 0.0)
      )
      val parsed = parser.parse(rendered).toOption.getOrElse(fail("re-parse failed"))
      val cur    = parsed.hcursor
      assertEquals(cur.downField("schemaVersion").as[Int].toOption, Some(1))
      val checks = cur.downField("checks").values.getOrElse(Vector.empty).toList
      assert(checks.nonEmpty, "expected non-empty checks array")
      val preservation = checks.find: c =>
        c.hcursor.downField("kind").as[String].toOption.contains("preservation") &&
          c.hcursor.downField("diagnostic").downField("category").as[String]
            .toOption.contains("invariant_violation_by_operation")
      assert(
        preservation.isDefined,
        "expected an invariant_violation_by_operation preservation check"
      )
      val ceEntities = preservation
        .flatMap(_.hcursor.downField("diagnostic").downField("counterexample")
          .downField("entities").values)
        .getOrElse(Vector.empty)
      assert(ceEntities.nonEmpty, "expected decoded counterexample entities")
      val firstFieldName = ceEntities.head.hcursor
        .downField("fields").downArray.downField("name").as[String].toOption
      assert(firstFieldName.isDefined, s"expected first entity field name, got $firstFieldName")

  test("JsonReport top-level shape carries all documented fields"):
    for
      ir     <- SpecFixtures.loadIR("safe_counter")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val json = JsonReport.toJson("x.spec", report, 42.0)
      val keys = json.asObject.map(_.keys.toSet).getOrElse(Set.empty[String])
      assertEquals(keys, Set("schemaVersion", "specFile", "ok", "totalMs", "checks"))
      val checkKeys = json.hcursor
        .downField("checks")
        .downArray
        .focus
        .flatMap(_.asObject.map(_.keys.toSet))
        .getOrElse(Set.empty[String])
      assertEquals(
        checkKeys,
        Set(
          "id",
          "kind",
          "tool",
          "operationName",
          "invariantName",
          "status",
          "durationMs",
          "detail",
          "sourceSpans",
          "diagnostic"
        )
      )

  private def stripTimings(j: Json): Json =
    def loop(value: Json, path: List[String]): Json =
      value.fold(
        jsonNull = Json.Null,
        jsonBoolean = Json.fromBoolean,
        jsonNumber = n => Json.fromJsonNumber(n),
        jsonString = Json.fromString,
        jsonArray = arr => Json.arr(arr.map(loop(_, path :+ "[]"))*),
        jsonObject = obj =>
          val patched = obj.toMap.map: (k, v) =>
            val isTopTotal   = path.isEmpty && k == "totalMs"
            val isCheckTotal = path == List("checks", "[]") && k == "durationMs"
            val mapped =
              if isTopTotal || isCheckTotal then Json.fromDoubleOrNull(0.0)
              else loop(v, path :+ k)
            k -> mapped
          Json.fromFields(obj.keys.map(k => k -> patched(k)))
      )
    loop(j, Nil)
