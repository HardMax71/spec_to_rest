package specrest.testgen

import specrest.codegen.SensitiveFields
import specrest.ir.HttpMethods
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final case class StructuralOutput(
    file: String,
    skips: List[TestSkip]
)

@SuppressWarnings(Array("org.wartremover.warts.IsInstanceOf"))
object Structural:

  private val TQ = "\"\"\""

  final private case class StructuralCheck(
      pyFunctionName: String,
      pyFunctionBody: String
  )

  def emitFor(profiled: ProfiledService): StructuralOutput =
    val ir = profiled.ir

    val invChecks = svcInvariants(ir).zipWithIndex.flatMap: (inv, idx) =>
      checkForGlobalInvariant(inv, idx, ir).toList
    val invSkips = svcInvariants(ir).zipWithIndex.flatMap: (inv, idx) =>
      checkForGlobalInvariantSkip(inv, idx, ir).toList

    val ensuresPairs =
      profiled.operations.flatMap: pop =>
        svcOperations(ir).find(o => operName(o) == pop.operationName) match
          case Some(opDecl) => checksForOperation(pop, opDecl, ir)
          case None         => Nil
    val ensuresChecks = ensuresPairs.collect { case Right(c) => c }
    val ensuresSkips  = ensuresPairs.collect { case Left(s) => s }

    val stubSkips =
      profiled.operations
        .filter(StubOps.isStub(profiled, _))
        .map(op => TestSkip(op.operationName, "structural", StubOps.skipReason(op)))

    val checks = invChecks ++ ensuresChecks
    val skips  = invSkips ++ ensuresSkips ++ stubSkips

    val py = renderFile(ir, profiled, checks)
    StructuralOutput(file = py, skips = skips)

  // -- Global invariants -----------------------------------------------------

  private def checkForGlobalInvariant(
      inv: invariant_decl,
      idx: Int,
      ir: ServiceIRFull
  ): Option[StructuralCheck] =
    val ctx = TestCtx.forInvariants(ir)
    ExprToPython.translate(invBody(inv), ctx) match
      case Translated.Skip(_, _) => None
      case Translated.Emit(text) =>
        val name       = invName(inv).getOrElse(s"anon_$idx")
        val methodName = Naming.toSnakeCase(name)
        val sb         = new StringBuilder
        sb.append(s"def _check_invariant_$methodName(ctx, response, case):\n")
        sb.append(
          s"    ${TQ}invariant $name: ${TestFormat.escapeDocstring(TestFormat.prettyOneLine(invBody(inv)))}$TQ\n"
        )
        sb.append("    if response.status_code >= 500:\n")
        sb.append("        return\n")
        sb.append("    post_state = client.get(\"/admin/state\").json()\n")
        sb.append(
          s"    assert $text, ${ExprToPython.pyString(s"invariant violated: $name")}\n"
        )
        Some(StructuralCheck(s"_check_invariant_$methodName", sb.toString))

  private def checkForGlobalInvariantSkip(
      inv: invariant_decl,
      idx: Int,
      ir: ServiceIRFull
  ): Option[TestSkip] =
    val ctx  = TestCtx.forInvariants(ir)
    val name = invName(inv).getOrElse(s"anon_$idx")
    ExprToPython.translate(invBody(inv), ctx) match
      case Translated.Skip(reason, _) =>
        Some(TestSkip("<invariants>", s"structural_invariant[$name]", reason))
      case _ => None

  // -- Pure-output ensures (Create operations) -------------------------------

  private def checksForOperation(
      pop: ProfiledOperation,
      opDecl: operation_decl,
      ir: ServiceIRFull
  ): List[Either[TestSkip, StructuralCheck]] =
    val isCreateLike = pop.kind match
      case _: Create | _: CreateChild => true
      case _                          => false
    if !isCreateLike then Nil
    else
      val opSnake         = Naming.toSnakeCase(operName(opDecl))
      val stateFields     = irStateFieldNames(ir).toSet
      val outputNames     = operOutputs(opDecl).map(prmName).toSet
      val outputNamesList = outputNames.toList
      val stateFieldsList = stateFields.toList
      operEnsures(opDecl).zipWithIndex.flatMap: (clause, idx) =>
        val inelig = structuralIneligibility(clause, outputNamesList, stateFieldsList)
        if inelig.isDefined then
          val reason = inelig match
            case Some(SceReferencesPrePrime()) =>
              "ensures references pre()/prime() — covered by behavioral/stateful layers"
            case Some(SceReferencesStateField()) =>
              "ensures references state field — covered by stateful invariants"
            case Some(SceReferencesNoOutput()) =>
              "ensures references no output field; not a structural-checkable shape"
            case None => "ensures not eligible for structural check"
          List(Left(TestSkip(operName(opDecl), s"structural_ensures[$idx]", reason)))
        else
          val ctx = TestCtx.fromOperation(opDecl, ir, CaptureMode.PostState)
          ExprToPython.translate(clause, ctx) match
            case Translated.Skip(reason, _) =>
              List(Left(TestSkip(operName(opDecl), s"structural_ensures[$idx]", reason)))
            case Translated.Emit(text) =>
              val checkName  = s"_check_${opSnake}_ensures_$idx"
              val pathLit    = ExprToPython.pyString(pop.endpoint.path)
              val methodName = HttpMethods.upper(pop.endpoint.method)
              val methodLit  = ExprToPython.pyString(methodName)
              val successLit = pop.endpoint.successStatus.toString
              val sb         = new StringBuilder
              sb.append(s"def $checkName(ctx, response, case):\n")
              sb.append(
                s"    ${TQ}ensures: ${TestFormat.escapeDocstring(TestFormat.prettyOneLine(clause))}$TQ\n"
              )
              sb.append(s"    if not _path_matches(case, $pathLit, $methodLit):\n")
              sb.append("        return\n")
              sb.append(s"    if response.status_code != $successLit:\n")
              sb.append("        return\n")
              sb.append("    response_data = response.json() if response.content else {}\n")
              sb.append(
                s"    assert $text, ${ExprToPython.pyString(s"ensures violated (${operName(opDecl)}#$idx)")}\n"
              )
              List(Right(StructuralCheck(checkName, sb.toString)))

  // -- File rendering --------------------------------------------------------

  private def renderFile(
      ir: ServiceIRFull,
      profiled: ProfiledService,
      checks: List[StructuralCheck]
  ): String =
    val checkDefs = checks.map(_.pyFunctionBody).mkString("\n")
    val checkTuple =
      if checks.isEmpty then "()"
      else "(\n" + checks.map(c => s"    ${c.pyFunctionName},").mkString("\n") + "\n)"
    val stubExcludeLines =
      profiled.operations
        .filter(StubOps.isStub(profiled, _))
        .map: op =>
          val methodName = HttpMethods.upper(op.endpoint.method)
          s"""schema = schema.exclude(method="$methodName", path="${op.endpoint.path}")"""
    val stubExcludes =
      if stubExcludeLines.isEmpty then ""
      else
        "\n# Operations the convention engine stubs fail-loud (NotImplementedError); excluding\n" +
          "# them keeps schemathesis from asserting a 5xx stub against the documented schema.\n" +
          stubExcludeLines.mkString("\n")

    // No schemathesis link-based state machine: the spec-derived OpenAPI document never
    // emits `links`, so `schema.as_state_machine()` raises NoLinksFound on schemathesis
    // 4.x. Stateful coverage is the dedicated Hypothesis `test_stateful_*` suite.

    val sensitiveFieldNames: List[String] =
      svcOperations(ir)
        .map(op => operInputs(op).map(prmName))
        .flatten
        .filter(SensitiveFields.isSensitive)
        .distinct
        .sorted
    val sanitizeKeys =
      if sensitiveFieldNames.isEmpty then ""
      else
        "\nschema.config.output.sanitization.keys_to_sanitize = tuple(" +
          "\n    set(schema.config.output.sanitization.keys_to_sanitize) | _SENSITIVE_BODY_FIELDS" +
          "\n)"
    // Body redaction rides schemathesis's own output sanitizer: mutating
    // case.body with a str subclass trips its metadata revalidation since
    // 4.22, and only the failure-report display needs masking anyway.
    val sensitiveBlock =
      if sensitiveFieldNames.isEmpty then ""
      else
        val literal = sensitiveFieldNames.map(n => s"\"$n\"").mkString(", ")
        s"""|
            |_SENSITIVE_BODY_FIELDS = frozenset({$literal})
            |""".stripMargin

    s"""|${TQ}Auto-generated structural tests for ${svcName(ir)}.
        |
        |Loads openapi.yaml and uses Schemathesis to fuzz every (method, path);
        |custom checks below are derived from spec invariants and pure-output
        |ensures clauses. Per-case state reset via /admin/reset keeps
        |global-invariant assertions meaningful across hundreds of fuzzed cases.
        |
        |See tests/_testgen_skips.json (structural_skipped) for clauses skipped
        |during translation.
        |${TQ}
        |import datetime
        |import hashlib
        |import os
        |import re
        |
        |import schemathesis
        |from hypothesis import HealthCheck, settings
        |
        |from tests.conftest import client
        |from tests.predicates import is_valid_email, is_valid_uri
        |${sensitiveBlock}
        |BASE_URL = os.environ.get("SPEC_TEST_BASE_URL", "http://localhost:8000")
        |
        |PROFILE = os.environ.get("SPEC_TEST_PROFILE", "thorough")
        |PROFILES = {
        |    "smoke":      {"max_examples": 10,   "stateful_step_count": 3},
        |    "thorough":   {"max_examples": 100,  "stateful_step_count": 10},
        |    "exhaustive": {"max_examples": 1000, "stateful_step_count": 25},
        |}
        |if PROFILE not in PROFILES:
        |    _allowed = ", ".join(sorted(PROFILES))
        |    raise ValueError(
        |        f"Invalid SPEC_TEST_PROFILE={PROFILE!r}. Expected one of: {_allowed}"
        |    )
        |_PROFILE = PROFILES[PROFILE]
        |
        |schema = schemathesis.openapi.from_path("openapi.yaml")$stubExcludes$sanitizeKeys
        |
        |
        |def _path_matches(case, expected_template, expected_method):
        |    if case.method.upper() != expected_method:
        |        return False
        |    template = getattr(case.operation, "path", None) or getattr(case, "path", None)
        |    return template == expected_template
        |
        |
        |${
         if checkDefs.isEmpty then "# No spec-derived checks generated; see _testgen_skips.json.\n"
         else checkDefs
       }
        |_ALL_CHECKS = $checkTuple
        |
        |
        |@schema.parametrize()
        |@settings(
        |    max_examples=_PROFILE["max_examples"],
        |    deadline=None,
        |    suppress_health_check=[HealthCheck.too_slow, HealthCheck.function_scoped_fixture],
        |)
        |def test_api_structural(case):
        |    ${TQ}For every (method, path), Schemathesis fuzzes inputs and validates the response shape.$TQ
        |    client.post("/admin/reset")
        |    response = case.call(base_url=BASE_URL)
        |    if _ALL_CHECKS:
        |        case.validate_response(response, checks=_ALL_CHECKS)
        |    else:
        |        case.validate_response(response)
        |""".stripMargin
