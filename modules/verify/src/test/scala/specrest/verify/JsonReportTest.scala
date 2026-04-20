package specrest.verify

import io.circe.Json
import io.circe.parser
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.z3.WasmBackend

import java.nio.file.Files
import java.nio.file.Paths

class JsonReportTest extends munit.FunSuite:

  // (fixture, expected ok, expected category in at least one diagnostic)
  private val cases: List[(String, Boolean, Option[String])] = List(
    ("safe_counter", true, None),
    ("unsat_invariants", false, Some("contradictory_invariants")),
    ("broken_url_shortener", false, Some("invariant_violation_by_operation"))
  )

  cases.foreach: (fixture, expectedOk, expectedCategory) =>
    test(s"JsonReport snapshot matches golden for $fixture"):
      val ir      = parseSpec(fixture)
      val backend = WasmBackend()
      try
        val report = Consistency.runConsistencyChecks(
          ir,
          backend,
          VerificationConfig(timeoutMs = 30_000L, captureCore = true)
        )
        val json      = JsonReport.toJson(s"fixtures/spec/$fixture.spec", report, 0.0)
        val canonical = stripTimings(json)
        assertEquals(
          canonical.hcursor.downField("schemaVersion").as[Int].toOption,
          Some(JsonReport.SchemaVersion)
        )
        assertEquals(canonical.hcursor.downField("ok").as[Boolean].toOption, Some(expectedOk))
        val goldenPath = Paths.get(s"fixtures/golden/verify_report/$fixture.json")
        val rendered   = JsonReport.render(canonical)
        if !Files.exists(goldenPath) then
          Files.createDirectories(goldenPath.getParent)
          val _ = Files.writeString(goldenPath, rendered)
          fail(s"wrote initial golden at $goldenPath — rerun the test")
        else
          val expected = Files.readString(goldenPath)
          assertEquals(rendered, expected, s"golden mismatch — update $goldenPath if intended")

        expectedCategory.foreach: category =>
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
      finally backend.close()

  test("JsonReport is round-trip-parseable via circe"):
    val ir      = parseSpec("broken_url_shortener")
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(
        ir,
        backend,
        VerificationConfig(timeoutMs = 30_000L, captureCore = true)
      )
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
    finally backend.close()

  test("JsonReport top-level shape carries all documented fields"):
    val ir      = parseSpec("safe_counter")
    val backend = WasmBackend()
    try
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val json   = JsonReport.toJson("x.spec", report, 42.0)
      val keys   = json.asObject.map(_.keys.toSet).getOrElse(Set.empty[String])
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
    finally backend.close()

  private def parseSpec(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree).toOption.get

  // Paths where timing fields legitimately live in the schema. Scoping prevents accidental
  // erasure if a future diagnostic/counterexample field shares one of these names.
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
