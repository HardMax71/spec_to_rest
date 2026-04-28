package specrest.testgen

import specrest.convention.EndpointSpec
import specrest.convention.Naming
import specrest.convention.OperationKind
import specrest.ir.BinOp
import specrest.ir.EntityDecl
import specrest.ir.Expr
import specrest.ir.FieldDecl
import specrest.ir.InvariantDecl
import specrest.ir.OperationDecl
import specrest.ir.PrettyPrint
import specrest.ir.ServiceIR
import specrest.ir.TypeExpr
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final case class StatefulOutput(
    file: String,
    skips: List[TestSkip]
)

object Stateful:

  final private case class BundleSpec(
      entityName: String,
      bundleName: String,
      pyVarName: String,
      pkFieldName: String,
      pkTypeExpr: TypeExpr
  )

  private enum InputBinding:
    case BundleDraw(bundle: BundleSpec)
    case BundleConsume(bundle: BundleSpec)
    case Generated(strategy: String)
    case Skip(reason: String)

  private enum RuleRole:
    case CreateTarget(bundle: BundleSpec, pkProjection: String)
    case Plain

  private val TQ = "\"\"\""

  def emitFor(profiled: ProfiledService): StatefulOutput =
    val ir       = profiled.ir
    val bundles  = inferBundles(profiled)
    val machName = s"${ir.name}StateMachine"
    val testName = s"TestStateful${ir.name}"

    val rulesAndSkips = profiled.operations.map: pop =>
      ir.operations.find(_.name == pop.operationName) match
        case Some(opDecl) => emitRule(pop, opDecl, ir, bundles)
        case None         => Right(Nil) -> Nil
    val ruleBlocks = rulesAndSkips.flatMap(_._1.toOption.toList.flatten)
    val ruleSkips  = rulesAndSkips.flatMap(_._2)

    val invariantsAndSkips = ir.invariants.zipWithIndex.map: (inv, idx) =>
      emitInvariant(inv, idx, ir)
    val invariantBlocks = invariantsAndSkips.flatMap(_._1.toList)
    val invariantSkips  = invariantsAndSkips.flatMap(_._2.toList)

    val py = renderFile(
      ir = ir,
      machineName = machName,
      testName = testName,
      bundles = bundles,
      ruleBlocks = ruleBlocks,
      invariantBlocks = invariantBlocks
    )

    StatefulOutput(file = py, skips = ruleSkips ++ invariantSkips)

  private def inferBundles(profiled: ProfiledService): List[BundleSpec] =
    val ir = profiled.ir
    val createOps = profiled.operations.filter: pop =>
      pop.kind == OperationKind.Create || pop.kind == OperationKind.CreateChild
    val byEntity = createOps.flatMap(pop => pop.targetEntity.map(_ -> pop)).groupBy(_._1)
    byEntity.keys.toList.sorted.flatMap: entityName =>
      ir.entities.find(_.name == entityName).flatMap: entity =>
        primaryKey(entity).map: pk =>
          BundleSpec(
            entityName = entityName,
            bundleName = s"${Naming.toSnakeCase(entityName)}_ids",
            pyVarName = s"${Naming.toSnakeCase(entityName)}_ids",
            pkFieldName = pk.name,
            pkTypeExpr = pk.typeExpr
          )

  private def primaryKey(entity: EntityDecl): Option[FieldDecl] =
    entity.fields.find(_.name == "id").orElse(entity.fields.headOption)

  private def emitRule(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      ir: ServiceIR,
      bundles: List[BundleSpec]
  ): (Either[Unit, List[String]], List[TestSkip]) =
    val (role, roleSkips) = inferCreateRole(pop, opDecl, bundles)
    val pathParamNames    = pop.endpoint.pathParams.map(_.name).toSet
    val bodyParamNames    = pop.endpoint.bodyParams.map(_.name).toSet
    val queryParamNames   = pop.endpoint.queryParams.map(_.name).toSet
    val allParams         = pathParamNames ++ bodyParamNames ++ queryParamNames

    val bindings = opDecl.inputs.collect:
      case p if allParams.contains(p.name) =>
        p.name -> bindForInput(p.name, p.typeExpr, pop, ir, bundles)
    val skipped = bindings.collect:
      case (n, InputBinding.Skip(r)) =>
        TestSkip(opDecl.name, "stateful_rule", s"input '$n': $r")
    if skipped.nonEmpty then (Right(Nil), skipped ++ roleSkips)
    else
      val stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
      val ruleBody = buildRuleBlock(
        pop = pop,
        opDecl = opDecl,
        bindings = bindings,
        role = role,
        stateFields = stateFields
      )
      (Right(List(ruleBody)), roleSkips)

  private def inferCreateRole(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      bundles: List[BundleSpec]
  ): (RuleRole, List[TestSkip]) =
    if pop.kind != OperationKind.Create && pop.kind != OperationKind.CreateChild then
      (RuleRole.Plain, Nil)
    else
      pop.targetEntity.flatMap(en => bundles.find(_.entityName == en)) match
        case None => (RuleRole.Plain, Nil)
        case Some(bundle) =>
          projectionForCreateOutput(opDecl, bundle) match
            case Some(proj) => (RuleRole.CreateTarget(bundle, proj), Nil)
            case None =>
              val skip = TestSkip(
                opDecl.name,
                "stateful_create_target",
                s"Create operation has no output of entity type '${bundle.entityName}' " +
                  s"or PK type '${typeName(bundle.pkTypeExpr).getOrElse("?")}'; " +
                  "emitting parameter-less rule without target= bundle"
              )
              (RuleRole.Plain, List(skip))

  private def projectionForCreateOutput(
      opDecl: OperationDecl,
      bundle: BundleSpec
  ): Option[String] =
    val outputs = opDecl.outputs
    outputs match
      case List(out) if isEntityType(out.typeExpr, bundle.entityName) =>
        Some(s"response_data[${ExprToPython.pyString(bundle.pkFieldName)}]")
      case _ =>
        outputs
          .find(o => sameNamedType(o.typeExpr, bundle.pkTypeExpr))
          .map(o => s"response_data[${ExprToPython.pyString(o.name)}]")
          .orElse(
            outputs
              .find(_.name == bundle.pkFieldName)
              .map(o => s"response_data[${ExprToPython.pyString(o.name)}]")
          )

  private def isEntityType(t: TypeExpr, name: String): Boolean = t match
    case TypeExpr.NamedType(n, _) => n == name
    case _                        => false

  private def sameNamedType(a: TypeExpr, b: TypeExpr): Boolean = (a, b) match
    case (TypeExpr.NamedType(x, _), TypeExpr.NamedType(y, _)) => x == y
    case _                                                    => false

  private def typeName(t: TypeExpr): Option[String] = t match
    case TypeExpr.NamedType(n, _) => Some(n)
    case _                        => None

  private def bindForInput(
      paramName: String,
      paramType: TypeExpr,
      pop: ProfiledOperation,
      ir: ServiceIR,
      bundles: List[BundleSpec]
  ): InputBinding =
    val targetEntityBundle = pop.targetEntity.flatMap: en =>
      bundles.find(b => b.entityName == en && sameNamedType(paramType, b.pkTypeExpr))
    val pkFieldNameMatch = bundles.find: b =>
      sameNamedType(paramType, b.pkTypeExpr) &&
        (paramName == b.pkFieldName || paramName == s"${Naming.toSnakeCase(b.entityName)}_id")
    val typeMatches = bundles.filter(b => sameNamedType(paramType, b.pkTypeExpr))
    val uniqueTypeMatch = typeMatches match
      case head :: Nil => Some(head)
      case _           => None

    val matchingBundle =
      targetEntityBundle.orElse(pkFieldNameMatch).orElse(uniqueTypeMatch)

    matchingBundle match
      case Some(bundle) =>
        if pop.kind == OperationKind.Delete then InputBinding.BundleConsume(bundle)
        else InputBinding.BundleDraw(bundle)
      case None =>
        val ctx       = StrategyCtx.OperationInput(pop.operationName, paramName)
        val overrides = TestStrategyOverrides.from(ir)
        Strategies.expressionFor(paramType, ir, ctx, overrides) match
          case StrategyExpr.Code(text) => InputBinding.Generated(text)
          case StrategyExpr.Skip(r)    => InputBinding.Skip(r)

  private def buildRuleBlock(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      bindings: List[(String, InputBinding)],
      role: RuleRole,
      stateFields: Set[String]
  ): String =
    val sb        = new StringBuilder
    val ruleArgs  = ruleDecoratorArgs(bindings, role)
    val funcName  = Naming.toSnakeCase(opDecl.name)
    val sigParams = ("self" :: bindings.map(_._1)).mkString(", ")

    sb.append(s"    @rule($ruleArgs)\n")
    sb.append(s"    def $funcName($sigParams):\n")
    sb.append(s"        $TQ${escapeDocstring(operationSummary(opDecl))}$TQ\n")
    sb.append(s"        response = ${requestCallExpr(pop)}\n")

    val bundleInputNames = bindings.collect:
      case (n, InputBinding.BundleDraw(_) | InputBinding.BundleConsume(_)) => n
    val allRequiresSatisfiableByBundles =
      opDecl.requires.forall: r =>
        requiresIsSatisfiedByBundles(r, bundleInputNames.toSet, stateFields)
    val expectsStrictSuccess =
      role match
        case RuleRole.CreateTarget(_, _) => true
        case RuleRole.Plain              => allRequiresSatisfiableByBundles

    val successCode = pop.endpoint.successStatus
    if expectsStrictSuccess then
      sb.append(s"        assert response.status_code == $successCode, response.text\n")
      role match
        case RuleRole.CreateTarget(_, proj) =>
          sb.append("        response_data = response.json() if response.content else {}\n")
          sb.append(s"        return $proj\n")
        case RuleRole.Plain =>
          ()
    else
      sb.append(
        s"        if response.status_code == $successCode:\n            pass\n"
      )
      sb.append(
        "        elif 400 <= response.status_code < 500:\n            pass\n"
      )
      sb.append(
        "        else:\n            assert False, f\"unexpected status {response.status_code}: {response.text}\"\n"
      )
    sb.toString

  private def ruleDecoratorArgs(
      bindings: List[(String, InputBinding)],
      role: RuleRole
  ): String =
    val targetArg = role match
      case RuleRole.CreateTarget(b, _) => List(s"target=${b.pyVarName}")
      case RuleRole.Plain              => Nil
    val paramArgs = bindings.map: (name, b) =>
      val rhs = b match
        case InputBinding.BundleDraw(bundle)    => bundle.pyVarName
        case InputBinding.BundleConsume(bundle) => s"consumes(${bundle.pyVarName})"
        case InputBinding.Generated(text)       => text
        case InputBinding.Skip(_)               => "st.nothing()"
      s"$name=$rhs"
    (targetArg ++ paramArgs).mkString(", ")

  private def emitInvariant(
      inv: InvariantDecl,
      idx: Int,
      ir: ServiceIR
  ): (Option[String], Option[TestSkip]) =
    val ctx = TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet,
      mapStateFields = ir.state.toList.flatMap(_.fields).collect {
        case f if f.typeExpr.isInstanceOf[specrest.ir.TypeExpr.MapType] => f.name
      }.toSet,
      enumValues = ir.enums.map(e => e.name -> e.values.toSet).toMap,
      knownPredicates = TestCtx.DefaultPredicates,
      userFunctions = ir.functions.map(f => f.name -> f).toMap,
      userPredicates = ir.predicates.map(p => p.name -> p).toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )
    val name       = inv.name.getOrElse(s"anon_$idx")
    val methodName = Naming.toSnakeCase(name)
    ExprToPython.translate(inv.expr, ctx) match
      case ExprPy.Skip(reason, _) =>
        val skip = TestSkip("<invariants>", s"stateful_invariant[$name]", reason)
        (None, Some(skip))
      case ExprPy.Py(text) =>
        val sb = new StringBuilder
        sb.append("    @invariant()\n")
        sb.append(s"    def invariant_$methodName(self):\n")
        sb.append(
          s"        ${TQ}invariant $name: ${escapeDocstring(prettyOneLine(inv.expr))}$TQ\n"
        )
        sb.append("        post_state = client.get(\"/__test_admin__/state\").json()\n")
        sb.append(
          s"        assert $text, ${ExprToPython.pyString(s"invariant violated: $name")}\n"
        )
        (Some(sb.toString), None)

  private def renderFile(
      ir: ServiceIR,
      machineName: String,
      testName: String,
      bundles: List[BundleSpec],
      ruleBlocks: List[String],
      invariantBlocks: List[String]
  ): String =
    val needsConsumes = ruleBlocks.exists(_.contains("consumes("))
    val statefulImports = List(
      "RuleBasedStateMachine",
      "Bundle",
      "rule",
      "initialize",
      "invariant"
    ) ++ (if needsConsumes then List("consumes") else Nil)
    val statefulImportLine =
      statefulImports.map("    " + _ + ",").mkString("\n")

    val strategySpecs = Strategies.forIR(ir)
    val strategyImport =
      if strategySpecs.isEmpty then ""
      else
        val names = strategySpecs.map(_.functionName).sorted.mkString(",\n    ")
        s"""|from tests.strategies import (
            |    $names,
            |)
            |
            |""".stripMargin

    val bundleDecls =
      if bundles.isEmpty then ""
      else
        bundles
          .map(b => s"    ${b.pyVarName} = Bundle(${ExprToPython.pyString(b.bundleName)})")
          .mkString("\n") + "\n\n"

    val initializeBlock =
      """    @initialize()
        |    def _reset(self):
        |        client.post("/__test_admin__/reset")
        |
        |""".stripMargin

    val ruleSection =
      if ruleBlocks.isEmpty then "    # No rules generated; see _testgen_skips.json.\n    pass\n"
      else ruleBlocks.mkString("\n")

    val invariantSection =
      if invariantBlocks.isEmpty then ""
      else "\n" + invariantBlocks.mkString("\n")

    s"""|${TQ}Auto-generated stateful tests for ${ir.name}.
        |
        |Builds a Hypothesis RuleBasedStateMachine: each spec operation becomes a
        |@rule that performs the real HTTP call; entity ids returned from Create
        |operations flow through Bundles into Read/Update/Delete rules; global
        |invariants are checked after every step against /__test_admin__/state.
        |
        |See tests/_testgen_skips.json for clauses skipped during translation.
        |${TQ}
        |import datetime
        |import re
        |
        |from hypothesis import HealthCheck, settings
        |from hypothesis import strategies as st
        |from hypothesis.stateful import (
        |$statefulImportLine
        |)
        |
        |from tests.conftest import client
        |from tests.predicates import is_valid_email, is_valid_uri
        |from tests.redaction import redact
        |
        |${strategyImport}class $machineName(RuleBasedStateMachine):
        |
        |${bundleDecls}${initializeBlock}${ruleSection}${invariantSection}
        |
        |$machineName.TestCase.settings = settings(
        |    max_examples=25,
        |    stateful_step_count=20,
        |    deadline=None,
        |    suppress_health_check=[HealthCheck.too_slow, HealthCheck.function_scoped_fixture],
        |)
        |$testName = $machineName.TestCase
        |""".stripMargin

  private def requestCallExpr(pop: ProfiledOperation): String =
    val ep              = pop.endpoint
    val method          = ep.method.toString.toLowerCase
    val bodyParamNames  = ep.bodyParams.map(_.name)
    val queryParamNames = ep.queryParams.map(_.name)
    val pathExpr        = pythonPathLiteral(ep)
    val bodyExpr =
      if bodyParamNames.isEmpty then ""
      else
        val pairs =
          bodyParamNames.map(n => s"${ExprToPython.pyString(n)}: $n").mkString(", ")
        s", json={$pairs}"
    val queryExpr =
      if queryParamNames.isEmpty then ""
      else
        val pairs =
          queryParamNames.map(n => s"${ExprToPython.pyString(n)}: $n").mkString(", ")
        s", params={$pairs}"
    s"client.$method($pathExpr$bodyExpr$queryExpr)"

  private def pythonPathLiteral(ep: EndpointSpec): String =
    if ep.pathParams.isEmpty then ExprToPython.pyString(ep.path)
    else "f" + ExprToPython.pyString(ep.path)

  private def operationSummary(op: OperationDecl): String =
    val req = op.requires
      .filterNot(isTrivialTrue)
      .map(prettyOneLine)
      .mkString("; ")
    val ens = op.ensures.map(prettyOneLine).mkString("; ")
    val parts = List(
      Option.when(req.nonEmpty)(s"requires: $req"),
      Option.when(ens.nonEmpty)(s"ensures: $ens")
    ).flatten
    if parts.isEmpty then op.name else s"${op.name}: ${parts.mkString(" | ")}"

  private def isTrivialTrue(e: Expr): Boolean = e match
    case Expr.BoolLit(true, _) => true
    case _                     => false

  private def requiresIsSatisfiedByBundles(
      e: Expr,
      bundleInputs: Set[String],
      stateFields: Set[String]
  ): Boolean =
    e match
      case Expr.BoolLit(true, _) => true
      case Expr.BinaryOp(BinOp.In, Expr.Identifier(in, _), Expr.Identifier(state, _), _)
          if bundleInputs.contains(in) && stateFields.contains(state) =>
        true
      case Expr.BinaryOp(BinOp.And, l, r, _) =>
        requiresIsSatisfiedByBundles(l, bundleInputs, stateFields) &&
        requiresIsSatisfiedByBundles(r, bundleInputs, stateFields)
      case _ => false

  private def prettyOneLine(e: Expr): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
