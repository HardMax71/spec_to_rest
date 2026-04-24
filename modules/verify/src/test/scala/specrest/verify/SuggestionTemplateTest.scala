package specrest.verify

import munit.CatsEffectSuite
import specrest.verify.testutil.SpecFixtures

class SuggestionTemplateTest extends CatsEffectSuite:

  private val MaxLen = 200

  test("contradictory_invariants suggestion names the conflicting invariants"):
    for
      ir     <- SpecFixtures.loadIR("unsat_invariants")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val global = report.checks.find(_.id == "global").get
      val hint   = global.diagnostic.flatMap(_.suggestion).getOrElse("")
      assert(hint.contains("jointly unsatisfiable"), s"hint=$hint")
      assert(
        hint.contains("inv_0") || hint.contains("inv_1") || hint.contains("inv_2"),
        s"expected one of inv_0/1/2 in hint; got: $hint"
      )
      assert(hint.length <= MaxLen, s"hint too long (${hint.length}): $hint")

  test("unsatisfiable_precondition suggestion names the operation"):
    for
      ir     <- SpecFixtures.loadIR("dead_op")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val deadReq = report.checks.find(_.id == "DeadOp.requires").get
      val hint    = deadReq.diagnostic.flatMap(_.suggestion).getOrElse("")
      assert(hint.contains("DeadOp"), s"expected 'DeadOp' in hint; got: $hint")
      assert(hint.contains("unsatisfiable"), s"hint=$hint")
      assert(hint.length <= MaxLen, s"hint too long (${hint.length}): $hint")

  test("unreachable_operation suggestion names the operation and invariants"):
    for
      ir     <- SpecFixtures.loadIR("unreachable_op")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val unreachable = report.checks.find(_.id == "UnreachableOp.enabled").get
      val hint        = unreachable.diagnostic.flatMap(_.suggestion).getOrElse("")
      assert(hint.contains("UnreachableOp"), s"expected 'UnreachableOp' in hint; got: $hint")
      assert(hint.contains("unreachable"), s"hint=$hint")
      assert(hint.length <= MaxLen, s"hint too long (${hint.length}): $hint")

  test("invariant_violation_by_operation suggestion names op, invariant, and field"):
    for
      ir     <- SpecFixtures.loadIR("broken_url_shortener")
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val violation = report.checks
        .find(c =>
          c.kind == CheckKind.Preservation &&
            c.diagnostic.exists(_.category == DiagnosticCategory.InvariantViolationByOperation)
        )
        .getOrElse(fail("no preservation violation found"))
      val hint = violation.diagnostic.flatMap(_.suggestion).getOrElse("")
      assert(hint.contains("Tamper"), s"expected 'Tamper' in hint; got: $hint")
      assert(hint.contains("clickCountNonNegative"), s"expected invariant name; got: $hint")
      assert(hint.contains("click_count"), s"expected field name; got: $hint")
      assert(hint.length <= MaxLen, s"hint too long (${hint.length}): $hint")

  test("solver_timeout suggestion mentions check id and timeout (direct template)"):
    SpecFixtures.loadIR("broken_url_shortener").map: ir =>
      val invDecl = ir.invariants.headOption.getOrElse(fail("expected an invariant"))
      val ctx = Diagnostic.SuggestionContext(
        ir = ir,
        op = ir.operations.headOption,
        invariantDecl = Some(invDecl),
        operationName = ir.operations.headOption.map(_.name),
        invariantName = invDecl.name,
        counterexample = None,
        checkId = "Tamper.preserves.clickCountNonNegative",
        timeoutMs = 1L
      )
      val hint = Diagnostic
        .suggestionFor(DiagnosticCategory.SolverTimeout, ctx)
        .getOrElse(fail("expected a suggestion"))
      assert(hint.contains("timed out"), s"hint=$hint")
      assert(hint.contains("1ms"), s"expected '1ms' in hint; got: $hint")
      assert(
        hint.contains("Tamper.preserves.clickCountNonNegative"),
        s"expected check id in hint; got: $hint"
      )
      assert(hint.length <= MaxLen, s"hint too long (${hint.length}): $hint")

  test("synthetic 'inv_<idx>' resolves invariantDecl by position for unnamed invariants"):
    val spec =
      """service UnnamedInv {
        |  state { x: Int }
        |  operation Bump {
        |    requires: x = 0
        |    ensures: x' = -1
        |  }
        |  invariant: x >= 0
        |}""".stripMargin
    for
      ir     <- SpecFixtures.buildFromSource("UnnamedInv", spec)
      report <- Consistency.runConsistencyChecks(ir, VerificationConfig.Default)
    yield
      val violation = report.checks
        .find(c =>
          c.diagnostic.exists(_.category == DiagnosticCategory.InvariantViolationByOperation)
        )
        .getOrElse(fail("expected a preservation violation"))
      val hint = violation.diagnostic.flatMap(_.suggestion).getOrElse("")
      assert(hint.contains("Bump"), s"expected 'Bump' in hint; got: $hint")
      // Without the positional `inv_<idx>` fallback in enrichSuggestion, invariantDecl would
      // be None and the template would fall back to the no-context generic text ("the
      // invariant's constrained fields"). Asserting on `'inv_0'` proves the rich template
      // fired against the resolved declaration.
      assert(hint.contains("'inv_0'"), s"expected 'inv_0' in hint (synthetic lookup); got: $hint")

  test("--no-suggestions suppresses suggestion in diagnostic"):
    val cfg = VerificationConfig.Default.copy(suggestions = false)
    for
      ir     <- SpecFixtures.loadIR("broken_url_shortener")
      report <- Consistency.runConsistencyChecks(ir, cfg)
    yield
      val violations = report.checks.filter(c =>
        c.diagnostic.exists(_.category == DiagnosticCategory.InvariantViolationByOperation)
      )
      assert(violations.nonEmpty, "expected at least one preservation violation")
      violations.foreach: v =>
        assertEquals(
          v.diagnostic.flatMap(_.suggestion),
          None,
          s"expected no suggestion when suggestions=false; got: ${v.diagnostic.flatMap(_.suggestion)}"
        )
