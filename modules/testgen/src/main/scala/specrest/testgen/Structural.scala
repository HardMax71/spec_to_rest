package specrest.testgen

import specrest.codegen.SensitiveFields
import specrest.ir.Naming
import specrest.ir.PrettyPrint
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
      inv: invariant_decl_full,
      idx: Int,
      ir: ServiceIRFull
  ): Option[StructuralCheck] =
    val ctx = invariantCtx(ir)
    ExprToPython.translate(invBody(inv), ctx) match
      case Translated.Skip(_, _) => None
      case Translated.Emit(text) =>
        val name       = invName(inv).getOrElse(s"anon_$idx")
        val methodName = Naming.toSnakeCase(name)
        val sb         = new StringBuilder
        sb.append(s"def _check_invariant_$methodName(ctx, response, case):\n")
        sb.append(
          s"    ${TQ}invariant $name: ${escapeDocstring(prettyOneLine(invBody(inv)))}$TQ\n"
        )
        sb.append("    if response.status_code >= 500:\n")
        sb.append("        return\n")
        sb.append("    post_state = client.get(\"/__test_admin__/state\").json()\n")
        sb.append(
          s"    assert $text, ${ExprToPython.pyString(s"invariant violated: $name")}\n"
        )
        Some(StructuralCheck(s"_check_invariant_$methodName", sb.toString))

  private def checkForGlobalInvariantSkip(
      inv: invariant_decl_full,
      idx: Int,
      ir: ServiceIRFull
  ): Option[TestSkip] =
    val ctx  = invariantCtx(ir)
    val name = invName(inv).getOrElse(s"anon_$idx")
    ExprToPython.translate(invBody(inv), ctx) match
      case Translated.Skip(reason, _) =>
        Some(TestSkip("<invariants>", s"structural_invariant[$name]", reason))
      case _ => None

  private def invariantCtx(ir: ServiceIRFull): TestCtx =
    TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = irStateFieldNames(ir).toSet,
      mapStateFields = irStateFields(ir)
        .filter(f => stfType(f).isInstanceOf[MapTypeF])
        .map(stfName)
        .toSet,
      enumValues = svcEnums(ir).map(e => enmName(e) -> enmVariants(e).toSet).toMap,
      userFunctions = svcFunctions(ir).map(f => fncName(f) -> f).toMap,
      userPredicates = svcPredicates(ir).map(p => prdName(p) -> p).toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState,
      unbackedStateFields = AdminModel.unbackedStateFieldNames(ir)
    )

  // -- Pure-output ensures (Create operations) -------------------------------

  private def checksForOperation(
      pop: ProfiledOperation,
      opDecl: operation_decl_full,
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
              val methodName = pop.endpoint.method match
                case _: GET    => "GET"
                case _: POST   => "POST"
                case _: PUT    => "PUT"
                case _: PATCH  => "PATCH"
                case _: DELETE => "DELETE"
              val methodLit  = ExprToPython.pyString(methodName)
              val successLit = pop.endpoint.successStatus.toString
              val sb         = new StringBuilder
              sb.append(s"def $checkName(ctx, response, case):\n")
              sb.append(
                s"    ${TQ}ensures: ${escapeDocstring(prettyOneLine(clause))}$TQ\n"
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
    val checkDefs  = checks.map(_.pyFunctionBody).mkString("\n")
    val checkTuple =
      if checks.isEmpty then "()"
      else "(\n" + checks.map(c => s"    ${c.pyFunctionName},").mkString("\n") + "\n)"
    val stubExcludeLines =
      profiled.operations
        .filter(StubOps.isStub(profiled, _))
        .map: op =>
          val methodName = op.endpoint.method match
            case _: GET    => "GET"
            case _: POST   => "POST"
            case _: PUT    => "PUT"
            case _: PATCH  => "PATCH"
            case _: DELETE => "DELETE"
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
    val sensitiveBlock =
      if sensitiveFieldNames.isEmpty then ""
      else
        val literal = sensitiveFieldNames.map(n => s"\"$n\"").mkString(", ")
        s"""|
            |from tests.redaction import _RedactedStr
            |
            |_SENSITIVE_BODY_FIELDS = frozenset({$literal})
            |
            |
            |@schemathesis.hook
            |def before_call(context, case, kwargs):
            |    body = getattr(case, "body", None)
            |    if isinstance(body, dict):
            |        for _k, _v in list(body.items()):
            |            if _k in _SENSITIVE_BODY_FIELDS and isinstance(_v, str) \\
            |               and not isinstance(_v, _RedactedStr):
            |                body[_k] = _RedactedStr(_v)
            |""".stripMargin

    s"""|${TQ}Auto-generated structural tests for ${svcName(ir)}.
        |
        |Loads openapi.yaml and uses Schemathesis to fuzz every (method, path);
        |custom checks below are derived from spec invariants and pure-output
        |ensures clauses. Per-case state reset via /__test_admin__/reset keeps
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
        |schema = schemathesis.openapi.from_path("openapi.yaml")$stubExcludes
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
        |    client.post("/__test_admin__/reset")
        |    response = case.call(base_url=BASE_URL)
        |    if _ALL_CHECKS:
        |        case.validate_response(response, checks=_ALL_CHECKS)
        |    else:
        |        case.validate_response(response)
        |""".stripMargin

  private def prettyOneLine(e: expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
