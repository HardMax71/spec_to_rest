package specrest.testgen

import specrest.codegen.SensitiveFields
import specrest.convention.Naming
import specrest.convention.OperationKind
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

    val invChecks = ir.i.collect { case _iv: InvariantDeclFull => _iv }.zipWithIndex.flatMap:
      (inv, idx) =>
        checkForGlobalInvariant(inv, idx, ir).toList
    val invSkips = ir.i.collect { case _iv: InvariantDeclFull => _iv }.zipWithIndex.flatMap:
      (inv, idx) =>
        checkForGlobalInvariantSkip(inv, idx, ir).toList

    val ensuresPairs =
      profiled.operations.flatMap: pop =>
        ir.g.collectFirst { case _o: OperationDeclFull if _o.a == pop.operationName => _o } match
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
      inv: InvariantDeclFull,
      idx: Int,
      ir: ServiceIRFull
  ): Option[StructuralCheck] =
    val ctx = invariantCtx(ir)
    ExprToPython.translate(inv.b, ctx) match
      case ExprPy.Skip(_, _) => None
      case ExprPy.Py(text) =>
        val name       = inv.a.getOrElse(s"anon_$idx")
        val methodName = Naming.toSnakeCase(name)
        val sb         = new StringBuilder
        sb.append(s"def _check_invariant_$methodName(ctx, response, case):\n")
        sb.append(
          s"    ${TQ}invariant $name: ${escapeDocstring(prettyOneLine(inv.b))}$TQ\n"
        )
        sb.append("    if response.status_code >= 500:\n")
        sb.append("        return\n")
        sb.append("    post_state = client.get(\"/__test_admin__/state\").json()\n")
        sb.append(
          s"    assert $text, ${ExprToPython.pyString(s"invariant violated: $name")}\n"
        )
        Some(StructuralCheck(s"_check_invariant_$methodName", sb.toString))

  private def checkForGlobalInvariantSkip(
      inv: InvariantDeclFull,
      idx: Int,
      ir: ServiceIRFull
  ): Option[TestSkip] =
    val ctx  = invariantCtx(ir)
    val name = inv.a.getOrElse(s"anon_$idx")
    ExprToPython.translate(inv.b, ctx) match
      case ExprPy.Skip(reason, _) =>
        Some(TestSkip("<invariants>", s"structural_invariant[$name]", reason))
      case _ => None

  private def invariantCtx(ir: ServiceIRFull): TestCtx =
    TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = ir.f.toList.flatMap { case StateDeclFull(_fs, _) =>
        _fs.collect { case StateFieldDeclFull(_n, _, _) => _n }
      }.toSet,
      mapStateFields = ir.f.toList.flatMap { case StateDeclFull(_fs, _) => _fs }.collect {
        case StateFieldDeclFull(n, t, _) if t.isInstanceOf[MapTypeF] => n
      }.toSet,
      enumValues = ir.d.collect { case e: EnumDeclFull => e.a -> e.b.toSet }.toMap,
      userFunctions = ir.l.collect { case f: FunctionDeclFull => f.a -> f }.toMap,
      userPredicates = ir.m.collect { case p: PredicateDeclFull => p.a -> p }.toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState,
      unbackedStateFields = AdminModel.unbackedStateFieldNames(ir)
    )

  // -- Pure-output ensures (Create operations) -------------------------------

  private def checksForOperation(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull
  ): List[Either[TestSkip, StructuralCheck]] =
    if pop.kind != OperationKind.Create && pop.kind != OperationKind.CreateChild then Nil
    else
      val opSnake = Naming.toSnakeCase(opDecl.a)
      val stateFields = ir.f.toList.flatMap { case StateDeclFull(_fs, _) =>
        _fs.collect { case StateFieldDeclFull(_n, _, _) => _n }
      }.toSet
      val outputNames = opDecl.c.collect { case ParamDeclFull(_n, _, _) => _n }.toSet
      opDecl.e.zipWithIndex.flatMap: (clause, idx) =>
        if !referencesOnlyInputsAndOutputs(clause, outputNames, stateFields) then
          val reason = nonPureOutputReason(clause, outputNames, stateFields)
          List(Left(TestSkip(opDecl.a, s"structural_ensures[$idx]", reason)))
        else
          val ctx = TestCtx.fromOperation(opDecl, ir, CaptureMode.PostState)
          ExprToPython.translate(clause, ctx) match
            case ExprPy.Skip(reason, _) =>
              List(Left(TestSkip(opDecl.a, s"structural_ensures[$idx]", reason)))
            case ExprPy.Py(text) =>
              val checkName  = s"_check_${opSnake}_ensures_$idx"
              val pathLit    = ExprToPython.pyString(pop.endpoint.path)
              val methodLit  = ExprToPython.pyString(pop.endpoint.method.toString.toUpperCase)
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
                s"    assert $text, ${ExprToPython.pyString(s"ensures violated (${opDecl.a}#$idx)")}\n"
              )
              List(Right(StructuralCheck(checkName, sb.toString)))

  private def referencesOnlyInputsAndOutputs(
      e: expr_full,
      outputs: Set[String],
      stateFields: Set[String]
  ): Boolean =
    !mentionsState(e, stateFields) && !mentionsPreOrPrime(e) &&
      mentionsAtLeastOneOutput(e, outputs)

  private def nonPureOutputReason(
      e: expr_full,
      outputs: Set[String],
      stateFields: Set[String]
  ): String =
    if mentionsPreOrPrime(e) then
      "ensures references pre()/prime() — covered by behavioral/stateful layers"
    else if mentionsState(e, stateFields) then
      "ensures references state field — covered by stateful invariants"
    else if !mentionsAtLeastOneOutput(e, outputs) then
      "ensures references no output field; not a structural-checkable shape"
    else "ensures not eligible for structural check"

  private def mentionsState(e: expr_full, stateFields: Set[String]): Boolean = e match
    case IdentifierF(n, _)     => stateFields.contains(n)
    case BinaryOpF(_, l, r, _) => mentionsState(l, stateFields) || mentionsState(r, stateFields)
    case UnaryOpF(_, x, _)     => mentionsState(x, stateFields)
    case FieldAccessF(b, _, _) => mentionsState(b, stateFields)
    case IndexF(b, i, _)       => mentionsState(b, stateFields) || mentionsState(i, stateFields)
    case CallF(_, args, _)     => args.exists(mentionsState(_, stateFields))
    case IfF(c, t, el, _) =>
      mentionsState(c, stateFields) || mentionsState(t, stateFields) || mentionsState(
        el,
        stateFields
      )
    case LetF(v, value, b, _) =>
      mentionsState(value, stateFields) || mentionsState(b, stateFields - v)
    case SetLiteralF(xs, _) => xs.exists(mentionsState(_, stateFields))
    case QuantifierF(_, bs, b, _) =>
      val boundNames = bs.collect { case _qb: QuantifierBindingFull => _qb.a }.toSet
      bs.exists { case QuantifierBindingFull(_, _d, _, _) => mentionsState(_d, stateFields) } ||
      mentionsState(b, stateFields -- boundNames)
    case PrimeF(x, _) => mentionsState(x, stateFields)
    case PreF(x, _)   => mentionsState(x, stateFields)
    case _            => false

  private def mentionsPreOrPrime(e: expr_full): Boolean = e match
    case PreF(_, _)            => true
    case PrimeF(_, _)          => true
    case BinaryOpF(_, l, r, _) => mentionsPreOrPrime(l) || mentionsPreOrPrime(r)
    case UnaryOpF(_, x, _)     => mentionsPreOrPrime(x)
    case FieldAccessF(b, _, _) => mentionsPreOrPrime(b)
    case IndexF(b, i, _)       => mentionsPreOrPrime(b) || mentionsPreOrPrime(i)
    case CallF(_, args, _)     => args.exists(mentionsPreOrPrime)
    case IfF(c, t, el, _) =>
      mentionsPreOrPrime(c) || mentionsPreOrPrime(t) || mentionsPreOrPrime(el)
    case LetF(_, v, b, _)   => mentionsPreOrPrime(v) || mentionsPreOrPrime(b)
    case SetLiteralF(xs, _) => xs.exists(mentionsPreOrPrime)
    case QuantifierF(_, bs, b, _) =>
      bs.exists { case QuantifierBindingFull(_, _d, _, _) =>
        mentionsPreOrPrime(_d)
      } || mentionsPreOrPrime(b)
    case _ => false

  private def mentionsAtLeastOneOutput(e: expr_full, outputs: Set[String]): Boolean = e match
    case IdentifierF(n, _) => outputs.contains(n)
    case BinaryOpF(_, l, r, _) =>
      mentionsAtLeastOneOutput(l, outputs) || mentionsAtLeastOneOutput(r, outputs)
    case UnaryOpF(_, x, _)     => mentionsAtLeastOneOutput(x, outputs)
    case FieldAccessF(b, _, _) => mentionsAtLeastOneOutput(b, outputs)
    case IndexF(b, i, _) =>
      mentionsAtLeastOneOutput(b, outputs) || mentionsAtLeastOneOutput(i, outputs)
    case CallF(_, args, _) => args.exists(mentionsAtLeastOneOutput(_, outputs))
    case IfF(c, t, el, _) =>
      mentionsAtLeastOneOutput(c, outputs) || mentionsAtLeastOneOutput(t, outputs) ||
      mentionsAtLeastOneOutput(el, outputs)
    case LetF(v, value, b, _) =>
      mentionsAtLeastOneOutput(value, outputs) || mentionsAtLeastOneOutput(b, outputs - v)
    case SetLiteralF(xs, _) => xs.exists(mentionsAtLeastOneOutput(_, outputs))
    case QuantifierF(_, bs, b, _) =>
      val boundNames = bs.collect { case _qb: QuantifierBindingFull => _qb.a }.toSet
      bs.exists { case QuantifierBindingFull(_, _d, _, _) =>
        mentionsAtLeastOneOutput(_d, outputs)
      } ||
      mentionsAtLeastOneOutput(b, outputs -- boundNames)
    case _ => false

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
        .map(op =>
          s"""schema = schema.exclude(method="${op.endpoint.method}", path="${op.endpoint.path}")"""
        )
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
      ir.g
        .collect { case op: OperationDeclFull => op.b.collect { case ParamDeclFull(n, _, _) => n } }
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

    s"""|${TQ}Auto-generated structural tests for ${ir.a}.
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
