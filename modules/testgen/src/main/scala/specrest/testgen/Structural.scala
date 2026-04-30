package specrest.testgen

import specrest.codegen.SensitiveFields
import specrest.convention.Naming
import specrest.convention.OperationKind
import specrest.ir.Expr
import specrest.ir.InvariantDecl
import specrest.ir.OperationDecl
import specrest.ir.PrettyPrint
import specrest.ir.ServiceIR
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

    val invChecks = ir.invariants.zipWithIndex.flatMap: (inv, idx) =>
      checkForGlobalInvariant(inv, idx, ir).toList
    val invSkips = ir.invariants.zipWithIndex.flatMap: (inv, idx) =>
      checkForGlobalInvariantSkip(inv, idx, ir).toList

    val ensuresPairs =
      profiled.operations.flatMap: pop =>
        ir.operations.find(_.name == pop.operationName) match
          case Some(opDecl) => checksForOperation(pop, opDecl, ir)
          case None         => Nil
    val ensuresChecks = ensuresPairs.collect { case Right(c) => c }
    val ensuresSkips  = ensuresPairs.collect { case Left(s) => s }

    val checks = invChecks ++ ensuresChecks
    val skips  = invSkips ++ ensuresSkips

    val py = renderFile(ir, profiled, checks)
    StructuralOutput(file = py, skips = skips)

  // -- Global invariants -----------------------------------------------------

  private def checkForGlobalInvariant(
      inv: InvariantDecl,
      idx: Int,
      ir: ServiceIR
  ): Option[StructuralCheck] =
    val ctx = invariantCtx(ir)
    ExprToPython.translate(inv.expr, ctx) match
      case ExprPy.Skip(_, _) => None
      case ExprPy.Py(text) =>
        val name       = inv.name.getOrElse(s"anon_$idx")
        val methodName = Naming.toSnakeCase(name)
        val sb         = new StringBuilder
        sb.append(s"def _check_invariant_$methodName(response, case):\n")
        sb.append(
          s"    ${TQ}invariant $name: ${escapeDocstring(prettyOneLine(inv.expr))}$TQ\n"
        )
        sb.append("    if response.status_code >= 500:\n")
        sb.append("        return\n")
        sb.append("    post_state = client.get(\"/__test_admin__/state\").json()\n")
        sb.append(
          s"    assert $text, ${ExprToPython.pyString(s"invariant violated: $name")}\n"
        )
        Some(StructuralCheck(s"_check_invariant_$methodName", sb.toString))

  private def checkForGlobalInvariantSkip(
      inv: InvariantDecl,
      idx: Int,
      ir: ServiceIR
  ): Option[TestSkip] =
    val ctx  = invariantCtx(ir)
    val name = inv.name.getOrElse(s"anon_$idx")
    ExprToPython.translate(inv.expr, ctx) match
      case ExprPy.Skip(reason, _) =>
        Some(TestSkip("<invariants>", s"structural_invariant[$name]", reason))
      case _ => None

  private def invariantCtx(ir: ServiceIR): TestCtx =
    TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet,
      mapStateFields = ir.state.toList.flatMap(_.fields).collect {
        case f if f.typeExpr.isInstanceOf[specrest.ir.TypeExpr.MapType] => f.name
      }.toSet,
      enumValues = ir.enums.map(e => e.name -> e.values.toSet).toMap,
      userFunctions = ir.functions.map(f => f.name -> f).toMap,
      userPredicates = ir.predicates.map(p => p.name -> p).toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )

  // -- Pure-output ensures (Create operations) -------------------------------

  private def checksForOperation(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      ir: ServiceIR
  ): List[Either[TestSkip, StructuralCheck]] =
    if pop.kind != OperationKind.Create && pop.kind != OperationKind.CreateChild then Nil
    else
      val opSnake     = Naming.toSnakeCase(opDecl.name)
      val stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
      val outputNames = opDecl.outputs.map(_.name).toSet
      opDecl.ensures.zipWithIndex.flatMap: (clause, idx) =>
        if !referencesOnlyInputsAndOutputs(clause, outputNames, stateFields) then
          val reason = nonPureOutputReason(clause, outputNames, stateFields)
          List(Left(TestSkip(opDecl.name, s"structural_ensures[$idx]", reason)))
        else
          val ctx = TestCtx.fromOperation(opDecl, ir, CaptureMode.PostState)
          ExprToPython.translate(clause, ctx) match
            case ExprPy.Skip(reason, _) =>
              List(Left(TestSkip(opDecl.name, s"structural_ensures[$idx]", reason)))
            case ExprPy.Py(text) =>
              val checkName  = s"_check_${opSnake}_ensures_$idx"
              val pathLit    = ExprToPython.pyString(pop.endpoint.path)
              val methodLit  = ExprToPython.pyString(pop.endpoint.method.toString.toUpperCase)
              val successLit = pop.endpoint.successStatus.toString
              val sb         = new StringBuilder
              sb.append(s"def $checkName(response, case):\n")
              sb.append(
                s"    ${TQ}ensures: ${escapeDocstring(prettyOneLine(clause))}$TQ\n"
              )
              sb.append(s"    if not _path_matches(case, $pathLit, $methodLit):\n")
              sb.append("        return\n")
              sb.append(s"    if response.status_code != $successLit:\n")
              sb.append("        return\n")
              sb.append("    response_data = response.json() if response.content else {}\n")
              sb.append(
                s"    assert $text, ${ExprToPython.pyString(s"ensures violated (${opDecl.name}#$idx)")}\n"
              )
              List(Right(StructuralCheck(checkName, sb.toString)))

  private def referencesOnlyInputsAndOutputs(
      e: Expr,
      outputs: Set[String],
      stateFields: Set[String]
  ): Boolean =
    !mentionsState(e, stateFields) && !mentionsPreOrPrime(e) &&
      mentionsAtLeastOneOutput(e, outputs)

  private def nonPureOutputReason(
      e: Expr,
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

  private def mentionsState(e: Expr, stateFields: Set[String]): Boolean = e match
    case Expr.Identifier(n, _)     => stateFields.contains(n)
    case Expr.BinaryOp(_, l, r, _) => mentionsState(l, stateFields) || mentionsState(r, stateFields)
    case Expr.UnaryOp(_, x, _)     => mentionsState(x, stateFields)
    case Expr.FieldAccess(b, _, _) => mentionsState(b, stateFields)
    case Expr.Index(b, i, _)       => mentionsState(b, stateFields) || mentionsState(i, stateFields)
    case Expr.Call(_, args, _)     => args.exists(mentionsState(_, stateFields))
    case Expr.If(c, t, el, _) =>
      mentionsState(c, stateFields) || mentionsState(t, stateFields) || mentionsState(
        el,
        stateFields
      )
    case Expr.Let(v, value, b, _) =>
      mentionsState(value, stateFields) || mentionsState(b, stateFields - v)
    case Expr.SetLiteral(xs, _) => xs.exists(mentionsState(_, stateFields))
    case Expr.Quantifier(_, bs, b, _) =>
      val boundNames = bs.map(_.variable).toSet
      bs.exists(qb => mentionsState(qb.domain, stateFields)) ||
      mentionsState(b, stateFields -- boundNames)
    case Expr.Prime(x, _) => mentionsState(x, stateFields)
    case Expr.Pre(x, _)   => mentionsState(x, stateFields)
    case _                => false

  private def mentionsPreOrPrime(e: Expr): Boolean = e match
    case Expr.Pre(_, _)            => true
    case Expr.Prime(_, _)          => true
    case Expr.BinaryOp(_, l, r, _) => mentionsPreOrPrime(l) || mentionsPreOrPrime(r)
    case Expr.UnaryOp(_, x, _)     => mentionsPreOrPrime(x)
    case Expr.FieldAccess(b, _, _) => mentionsPreOrPrime(b)
    case Expr.Index(b, i, _)       => mentionsPreOrPrime(b) || mentionsPreOrPrime(i)
    case Expr.Call(_, args, _)     => args.exists(mentionsPreOrPrime)
    case Expr.If(c, t, el, _) =>
      mentionsPreOrPrime(c) || mentionsPreOrPrime(t) || mentionsPreOrPrime(el)
    case Expr.Let(_, v, b, _)   => mentionsPreOrPrime(v) || mentionsPreOrPrime(b)
    case Expr.SetLiteral(xs, _) => xs.exists(mentionsPreOrPrime)
    case Expr.Quantifier(_, bs, b, _) =>
      bs.exists(qb => mentionsPreOrPrime(qb.domain)) || mentionsPreOrPrime(b)
    case _ => false

  private def mentionsAtLeastOneOutput(e: Expr, outputs: Set[String]): Boolean = e match
    case Expr.Identifier(n, _) => outputs.contains(n)
    case Expr.BinaryOp(_, l, r, _) =>
      mentionsAtLeastOneOutput(l, outputs) || mentionsAtLeastOneOutput(r, outputs)
    case Expr.UnaryOp(_, x, _)     => mentionsAtLeastOneOutput(x, outputs)
    case Expr.FieldAccess(b, _, _) => mentionsAtLeastOneOutput(b, outputs)
    case Expr.Index(b, i, _) =>
      mentionsAtLeastOneOutput(b, outputs) || mentionsAtLeastOneOutput(i, outputs)
    case Expr.Call(_, args, _) => args.exists(mentionsAtLeastOneOutput(_, outputs))
    case Expr.If(c, t, el, _) =>
      mentionsAtLeastOneOutput(c, outputs) || mentionsAtLeastOneOutput(t, outputs) ||
      mentionsAtLeastOneOutput(el, outputs)
    case Expr.Let(v, value, b, _) =>
      mentionsAtLeastOneOutput(value, outputs) || mentionsAtLeastOneOutput(b, outputs - v)
    case Expr.SetLiteral(xs, _) => xs.exists(mentionsAtLeastOneOutput(_, outputs))
    case Expr.Quantifier(_, bs, b, _) =>
      val boundNames = bs.map(_.variable).toSet
      bs.exists(qb => mentionsAtLeastOneOutput(qb.domain, outputs)) ||
      mentionsAtLeastOneOutput(b, outputs -- boundNames)
    case _ => false

  // -- File rendering --------------------------------------------------------

  private def renderFile(
      ir: ServiceIR,
      profiled: ProfiledService,
      checks: List[StructuralCheck]
  ): String =
    val machineName = s"${ir.name}LinksStateMachine"
    val testName    = s"TestStructuralLinks${ir.name}"
    val checkDefs   = checks.map(_.pyFunctionBody).mkString("\n")
    val checkTuple =
      if checks.isEmpty then "()"
      else "(\n" + checks.map(c => s"    ${c.pyFunctionName},").mkString("\n") + "\n)"
    val emitsLinks = profiled.operations.exists(_.targetEntity.isDefined)
    val linksBlock =
      if !emitsLinks then ""
      else
        s"""|
            |$machineName = schema.as_state_machine()
            |$machineName.TestCase.settings = settings(
            |    max_examples=_PROFILE["max_examples"],
            |    stateful_step_count=_PROFILE["stateful_step_count"],
            |    deadline=None,
            |    suppress_health_check=[HealthCheck.too_slow, HealthCheck.function_scoped_fixture],
            |)
            |$testName = $machineName.TestCase
            |""".stripMargin

    val sensitiveFieldNames: List[String] =
      ir.operations
        .flatMap(_.inputs.map(_.name))
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
            |def before_call(context, case):
            |    body = getattr(case, "body", None)
            |    if isinstance(body, dict):
            |        for _k, _v in list(body.items()):
            |            if _k in _SENSITIVE_BODY_FIELDS and isinstance(_v, str) \\
            |               and not isinstance(_v, _RedactedStr):
            |                body[_k] = _RedactedStr(_v)
            |""".stripMargin

    s"""|${TQ}Auto-generated structural tests for ${ir.name}.
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
        |schema = schemathesis.openapi.from_path("openapi.yaml")
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
        |${linksBlock}""".stripMargin

  private def prettyOneLine(e: Expr): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
