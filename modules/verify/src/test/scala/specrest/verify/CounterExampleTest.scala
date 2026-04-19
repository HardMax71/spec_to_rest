package specrest.verify

import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.Paths

class CounterExampleTest extends munit.FunSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty)
    Builder.buildIR(parsed.tree)

  test("broken_url_shortener preservation failure produces a decoded counterexample"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("broken_url_shortener")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val viol = report.checks.find(c =>
        c.kind == CheckKind.Preservation &&
          c.diagnostic.exists(_.category == DiagnosticCategory.InvariantViolationByOperation)
      )
      assert(
        viol.isDefined,
        s"expected a preservation violation; checks=${report.checks.map(c => c.id + "->" + c.status)}"
      )
      val ce = viol.flatMap(_.diagnostic).flatMap(_.counterexample)
      assert(ce.isDefined, "expected a decoded counterexample attached to the diagnostic")
      val decoded = ce.get
      // The counterexample carries at least the entity / state / input breakdown
      // from Z3's model. Some components may be empty depending on the specific spec.
      val nonEmpty =
        decoded.entities.nonEmpty ||
          decoded.stateRelations.nonEmpty ||
          decoded.stateConstants.nonEmpty ||
          decoded.inputs.nonEmpty
      assert(nonEmpty, s"counterexample should have at least one decoded component; got $decoded")
    finally backend.close()

  test("formatCounterExample renders a readable block"):
    val ce = DecodedCounterExample(
      entities = List(
        DecodedEntity(
          sortName = "User",
          label = "User#0",
          rawElement = "User!val!0",
          fields = List(
            DecodedEntityField("id", DecodedValue("42", None)),
            DecodedEntityField("name", DecodedValue("\"alice\"", None))
          )
        )
      ),
      stateRelations = List(
        DecodedRelation(
          stateName = "users",
          side = "pre",
          entries = List(
            DecodedRelationEntry(
              key = DecodedValue("42", None),
              value = DecodedValue("User#0", Some("User#0"))
            )
          )
        )
      ),
      stateConstants = Nil,
      inputs = List(DecodedInput("limit", DecodedValue("10", None)))
    )
    val out = CounterExample.format(ce)
    assert(out.contains("inputs:"))
    assert(out.contains("limit = 10"))
    assert(out.contains("entities:"))
    assert(out.contains("User#0"))
    assert(out.contains("pre-state:"))
    assert(out.contains("users = { 42 → User#0 }"))

  test("decoded counterexample flows into formatDiagnostic output"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("broken_url_shortener")
      val report = Consistency.runConsistencyChecks(ir, backend, VerificationConfig.Default)
      val violation = report.checks.find(c =>
        c.kind == CheckKind.Preservation &&
          c.diagnostic.exists(_.category == DiagnosticCategory.InvariantViolationByOperation)
      ).get
      val output =
        Diagnostic.formatDiagnostic(violation.diagnostic.get, "broken_url_shortener.spec")
      assert(output.contains("Counterexample:"), s"missing Counterexample header in: $output")
      assert(!output.contains("<counterexample decoding not yet ported"), "stale placeholder text")
    finally backend.close()
