package specrest.testgen

import specrest.convention.EndpointSpec
import specrest.convention.Naming
import specrest.ir.BinOp
import specrest.ir.EntityDecl
import specrest.ir.Expr
import specrest.ir.FieldDecl
import specrest.ir.InvariantDecl
import specrest.ir.OperationDecl
import specrest.ir.PrettyPrint
import specrest.ir.ServiceIR
import specrest.ir.TransitionDecl
import specrest.ir.TransitionRule
import specrest.ir.TypeExpr
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

final case class GeneratedTest(
    name: String,
    body: String,
    skipReason: Option[String] = None
)

final case class TestSkip(operation: String, kind: String, reason: String)

final case class BehavioralOutput(
    tests: List[GeneratedTest],
    skips: List[TestSkip]
)

object Behavioral:

  def emitFor(profiled: ProfiledService): BehavioralOutput =
    val ir = profiled.ir
    val perOp = profiled.operations.flatMap: pop =>
      ir.operations.find(_.name == pop.operationName) match
        case Some(opDecl) => testsForOperation(pop, opDecl, ir)
        case None         => Nil
    val transitionResults = transitionTests(profiled, ir)
    val collected         = perOp ++ transitionResults
    val tests             = collected.collect { case Right(t) => t }
    val skips             = collected.collect { case Left(s) => s }
    BehavioralOutput(tests = tests, skips = skips)

  private def testsForOperation(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      ir: ServiceIR
  ): List[Either[TestSkip, GeneratedTest]] =
    val ensures   = ensuresTests(pop, opDecl, ir)
    val negatives = negativeTests(pop, opDecl, ir)
    val invs      = invariantTests(pop, opDecl, ir)
    ensures ++ negatives ++ invs

  private def viaOperationNames(ir: ServiceIR): Set[String] =
    ir.transitions.flatMap(_.rules.map(_.via)).toSet

  private def stateDepSkipReason(opName: String, ir: ServiceIR): String =
    if viaOperationNames(ir).contains(opName) then
      "state-dependent precondition; covered by transition tests (M5.9)"
    else
      "state-dependent precondition; needs state-machine setup before assume() can succeed (deferred to M5.9: TransitionDecl-aware tests, #137)"

  private def ensuresTests(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      ir: ServiceIR
  ): List[Either[TestSkip, GeneratedTest]] =
    val stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
    val opSnake     = Naming.toSnakeCase(opDecl.name)

    val requiresHasStateRef = opDecl.requires.exists(containsStateRef(_, stateFields))
    val nonTrivialRequires  = opDecl.requires.exists(!isTrivialTrue(_))

    if requiresHasStateRef then
      List(
        Left(
          TestSkip(
            operation = opDecl.name,
            kind = "ensures",
            reason = stateDepSkipReason(opDecl.name, ir)
          )
        )
      )
    else
      val inputArgs = inputArgList(pop, ir)
      inputArgs match
        case Left(reason) =>
          List(Left(TestSkip(opDecl.name, "ensures", reason)))
        case Right(strategySig) =>
          val ctx = TestCtx.fromOperation(opDecl, ir, CaptureMode.PreState)
          opDecl.ensures.zipWithIndex.map: (clause, idx) =>
            ExprToPython.translate(clause, ctx) match
              case ExprPy.Skip(reason, _) =>
                Left(TestSkip(opDecl.name, s"ensures[$idx]", reason))
              case ExprPy.Py(text) =>
                Right(
                  buildPositiveTest(
                    name = s"test_${opSnake}_ensures_$idx",
                    docstring = s"ensures: ${prettyOneLine(clause)}",
                    inputArgs = strategySig,
                    pop = pop,
                    assertion = text,
                    nonTrivialRequires = nonTrivialRequires
                  )
                )

  private def negativeTests(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      ir: ServiceIR
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake     = Naming.toSnakeCase(opDecl.name)
    val inputs      = opDecl.inputs.map(_.name).toSet
    val stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet

    opDecl.requires.zipWithIndex.flatMap: (req, idx) =>
      keyExistencePattern(req, inputs, stateFields) match
        case Some((inputName, stateName)) =>
          inputArgList(pop, ir) match
            case Left(reason) =>
              List(Left(TestSkip(opDecl.name, s"requires[$idx]", reason)))
            case Right(strategySig) =>
              List(
                Right(
                  buildNegativeKeyTest(
                    name = s"test_${opSnake}_negative_${inputName}_not_in_${stateName}",
                    inputArgs = strategySig,
                    pop = pop,
                    inputName = inputName,
                    stateName = stateName
                  )
                )
              )
        case None =>
          if isTrivialTrue(req) then Nil
          else
            List(
              Left(
                TestSkip(
                  opDecl.name,
                  s"requires[$idx]",
                  "M5.1: only `<input> in <state>` requires patterns get negative tests"
                )
              )
            )

  private def invariantTests(
      pop: ProfiledOperation,
      opDecl: OperationDecl,
      ir: ServiceIR
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake     = Naming.toSnakeCase(opDecl.name)
    val stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet

    if opDecl.requires.exists(containsStateRef(_, stateFields)) then
      ir.invariants.zipWithIndex.toList.map: (inv, idx) =>
        Left(
          TestSkip(
            opDecl.name,
            s"invariant[${invName(inv, idx)}]",
            stateDepSkipReason(opDecl.name, ir)
          )
        )
    else
      val ctx = TestCtx.fromOperation(opDecl, ir, CaptureMode.PostState)

      ir.invariants.zipWithIndex.flatMap: (inv, idx) =>
        ExprToPython.translate(inv.expr, ctx) match
          case ExprPy.Skip(reason, _) =>
            List(
              Left(TestSkip(opDecl.name, s"invariant[${invName(inv, idx)}]", reason))
            )
          case ExprPy.Py(text) =>
            inputArgList(pop, ir) match
              case Left(reason) =>
                List(Left(TestSkip(opDecl.name, s"invariant", reason)))
              case Right(strategySig) =>
                List(
                  Right(
                    buildInvariantTest(
                      name = s"test_${opSnake}_invariant_${Naming.toSnakeCase(invName(inv, idx))}",
                      docstring =
                        s"invariant ${invName(inv, idx)}: ${prettyOneLine(inv.expr)}",
                      inputArgs = strategySig,
                      pop = pop,
                      assertion = text
                    )
                  )
                )

  private def transitionTests(
      profiled: ProfiledService,
      ir: ServiceIR
  ): List[Either[TestSkip, GeneratedTest]] =
    ir.transitions.flatMap(td => transitionTestsFor(td, profiled, ir))

  private def transitionTestsFor(
      td: TransitionDecl,
      profiled: ProfiledService,
      ir: ServiceIR
  ): List[Either[TestSkip, GeneratedTest]] =
    val entityOpt = ir.entities.find(_.name == td.entityName)
    if entityOpt.isEmpty then
      return List(Left(TestSkip(td.name, "transition", s"unknown entity '${td.entityName}'")))
    val entity   = entityOpt.get
    val fieldOpt = entity.fields.find(_.name == td.fieldName)
    if fieldOpt.isEmpty then
      return List(
        Left(
          TestSkip(
            td.name,
            "transition",
            s"entity '${entity.name}' has no field '${td.fieldName}'"
          )
        )
      )
    val enumValuesOpt = enumValuesForField(fieldOpt.get, ir)
    if enumValuesOpt.isEmpty then
      return List(
        Left(
          TestSkip(
            td.name,
            "transition",
            s"transition field '${td.fieldName}' is not an enum (or alias of enum); illegal-from enumeration undefined"
          )
        )
      )
    val pkOpt = AdminRouter.primaryKeyField(entity)
    if pkOpt.isEmpty then
      return List(
        Left(
          TestSkip(
            td.name,
            "transition",
            s"entity '${entity.name}' has no field; cannot seed"
          )
        )
      )
    val statusValues = enumValuesOpt.get
    val pk           = pkOpt.get
    val byVia        = td.rules.groupBy(_.via)
    byVia.toList.sortBy(_._1).flatMap: (viaName, rules) =>
      val opDeclOpt = ir.operations.find(_.name == viaName)
      val popOpt    = profiled.operations.find(_.operationName == viaName)
      (opDeclOpt, popOpt) match
        case (Some(opDecl), Some(pop)) =>
          val legalFroms   = rules.map(_.from).toSet
          val illegalFroms = statusValues.filterNot(legalFroms.contains)
          val stateField = stateFieldForEntity(td.entityName, ir)
            .getOrElse(Naming.toSnakeCase(td.entityName) + "s")
          val positives = rules.toList.map: rule =>
            buildTransitionPositiveOrSkip(
              td = td,
              entity = entity,
              fieldName = td.fieldName,
              pkName = pk,
              rule = rule,
              opDecl = opDecl,
              pop = pop,
              stateField = stateField
            )
          val negatives = illegalFroms.toList.sorted.map: from =>
            buildTransitionNegative(
              entity = entity,
              fieldName = td.fieldName,
              pkName = pk,
              from = from,
              opDecl = opDecl,
              pop = pop
            )
          positives ++ negatives
        case _ =>
          List(
            Left(
              TestSkip(
                td.name,
                s"transition[$viaName]",
                s"no operation named '$viaName' for via clause"
              )
            )
          )

  private def enumValuesForField(field: FieldDecl, ir: ServiceIR): Option[List[String]] =
    field.typeExpr match
      case TypeExpr.NamedType(name, _) =>
        ir.enums.find(_.name == name).map(_.values).orElse:
          ir.typeAliases.find(_.name == name).flatMap: alias =>
            enumValuesForField(field.copy(typeExpr = alias.typeExpr), ir)
      case _ => None

  private def buildTransitionPositiveOrSkip(
      td: TransitionDecl,
      entity: EntityDecl,
      fieldName: String,
      pkName: String,
      rule: TransitionRule,
      opDecl: OperationDecl,
      pop: ProfiledOperation,
      stateField: String
  ): Either[TestSkip, GeneratedTest] =
    rule.guard match
      case Some(guard) =>
        Left(
          TestSkip(
            opDecl.name,
            s"transition[${rule.from}_to_${rule.to}]",
            s"guard '${prettyOneLine(guard)}' not representable in seed dict (see #152)"
          )
        )
      case None =>
        Right(
          buildTransitionPositive(
            td = td,
            entity = entity,
            fieldName = fieldName,
            pkName = pkName,
            from = rule.from,
            to = rule.to,
            opDecl = opDecl,
            pop = pop,
            stateField = stateField
          )
        )

  private def buildTransitionPositive(
      td: TransitionDecl,
      entity: EntityDecl,
      fieldName: String,
      pkName: String,
      from: String,
      to: String,
      opDecl: OperationDecl,
      pop: ProfiledOperation,
      stateField: String
  ): GeneratedTest =
    val opSnake     = Naming.toSnakeCase(opDecl.name)
    val entitySnake = Naming.toSnakeCase(entity.name)
    val testName    = s"test_${opSnake}_transition_${from.toLowerCase}_to_${to.toLowerCase}"
    val rowStrategy = Strategies.strategyFunctionName(entity.name)
    val pkKey       = ExprToPython.pyString(pkName)
    val fieldKey    = ExprToPython.pyString(fieldName)
    val stateKey    = ExprToPython.pyString(stateField)
    val sb          = new StringBuilder
    sb.append(s"@given(row=$rowStrategy())\n")
    sb.append(
      "@settings(max_examples=20, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
    )
    sb.append(s"def $testName(row):\n")
    sb.append(
      s"    \"\"\"transition ${opDecl.name}: $from -> $to (post-state ${td.fieldName} = $to)\"\"\"\n"
    )
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = ${ExprToPython.pyString(from)}\n")
    sb.append(s"    seed = client.post(\"/__test_admin__/seed/$entitySnake\", json=row)\n")
    sb.append("    assume(seed.status_code == 201)\n")
    sb.append(s"    seeded_id = seed.json()[$pkKey]\n")
    sb.append(transitionRequestCall(pop))
    sb.append(s"    assert response.status_code == ${pop.endpoint.successStatus}, response.text\n")
    sb.append("    post_state = client.get(\"/__test_admin__/state\").json()\n")
    sb.append(
      s"    bucket = post_state.get($stateKey, {})\n"
    )
    sb.append(
      "    entity_view = bucket.get(str(seeded_id)) or bucket.get(seeded_id)\n"
    )
    sb.append(
      s"    actual = entity_view.get($fieldKey) if isinstance(entity_view, dict) else entity_view\n"
    )
    sb.append(
      s"    assert actual == ${ExprToPython.pyString(to)}, " +
        s"f\"expected ${td.fieldName}=$to, got {actual!r}\"\n"
    )
    GeneratedTest(name = testName, body = sb.toString, skipReason = None)

  private def buildTransitionNegative(
      entity: EntityDecl,
      fieldName: String,
      pkName: String,
      from: String,
      opDecl: OperationDecl,
      pop: ProfiledOperation
  ): Either[TestSkip, GeneratedTest] =
    val opSnake     = Naming.toSnakeCase(opDecl.name)
    val entitySnake = Naming.toSnakeCase(entity.name)
    val testName    = s"test_${opSnake}_transition_illegal_from_${from.toLowerCase}"
    val rowStrategy = Strategies.strategyFunctionName(entity.name)
    val pkKey       = ExprToPython.pyString(pkName)
    val fieldKey    = ExprToPython.pyString(fieldName)
    val sb          = new StringBuilder
    sb.append(s"@given(row=$rowStrategy())\n")
    sb.append(
      "@settings(max_examples=10, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
    )
    sb.append(s"def $testName(row):\n")
    sb.append(
      s"    \"\"\"transition ${opDecl.name}: from=$from is illegal (no rule); SUT must reject 4xx\"\"\"\n"
    )
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = ${ExprToPython.pyString(from)}\n")
    sb.append(s"    seed = client.post(\"/__test_admin__/seed/$entitySnake\", json=row)\n")
    sb.append("    assume(seed.status_code == 201)\n")
    sb.append(s"    seeded_id = seed.json()[$pkKey]\n")
    sb.append(transitionRequestCall(pop))
    sb.append(
      s"    assert 400 <= response.status_code < 500, " +
        s"f${'"'}expected 4xx, got {response.status_code}: {response.text}${'"'}\n"
    )
    Right(GeneratedTest(name = testName, body = sb.toString, skipReason = None))

  private def transitionRequestCall(pop: ProfiledOperation): String =
    val ep        = pop.endpoint
    val pathParam = ep.pathParams.headOption.map(_.name)
    val pathExpr =
      pathParam match
        case Some(p) =>
          val rendered = ep.path.replace(s"{$p}", "{seeded_id}")
          "f" + ExprToPython.pyString(rendered)
        case None => ExprToPython.pyString(ep.path)
    val method = ep.method.toString.toLowerCase
    val bodyExpr =
      if ep.bodyParams.isEmpty then ""
      else
        val pairs = ep.bodyParams.map(p => s"${ExprToPython.pyString(p.name)}: None").mkString(", ")
        s", json={$pairs}"
    val queryExpr =
      if ep.queryParams.isEmpty then ""
      else
        val pairs =
          ep.queryParams.map(p => s"${ExprToPython.pyString(p.name)}: None").mkString(", ")
        s", params={$pairs}"
    s"    response = client.$method($pathExpr$bodyExpr$queryExpr)\n"

  private def stateFieldForEntity(entityName: String, ir: ServiceIR): Option[String] =
    ir.state.toList.flatMap(_.fields).collectFirst:
      case f if relationTargetsEntity(f.typeExpr, entityName) => f.name

  private def relationTargetsEntity(t: TypeExpr, entity: String): Boolean = t match
    case TypeExpr.RelationType(_, _, TypeExpr.NamedType(n, _), _) => n == entity
    case TypeExpr.NamedType(n, _)                                 => n == entity
    case _                                                        => false

  private def buildPositiveTest(
      name: String,
      docstring: String,
      inputArgs: InputSig,
      pop: ProfiledOperation,
      assertion: String,
      nonTrivialRequires: Boolean
  ): GeneratedTest =
    val sig = inputArgs.signature
    val gen = inputArgs.givenLine
    val sb  = new StringBuilder
    if gen.nonEmpty then
      sb.append(s"$gen\n")
      sb.append(
        "@settings(max_examples=20, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
      )
    sb.append(s"def $name($sig):\n")
    sb.append(s"    \"\"\"${escapeDocstring(docstring)}\"\"\"\n")
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    pre_state = client.get(\"/__test_admin__/state\").json()\n")
    sb.append(s"    response = ${requestCallExpr(pop)}\n")
    if nonTrivialRequires then
      sb.append(s"    assume(response.status_code == ${pop.endpoint.successStatus})\n")
    sb.append(s"    assert response.status_code == ${pop.endpoint.successStatus}, response.text\n")
    sb.append("    response_data = response.json() if response.content else {}\n")
    sb.append("    post_state = client.get(\"/__test_admin__/state\").json()\n")
    sb.append(s"    assert $assertion, ${ExprToPython.pyString(s"ensures violated: $docstring")}\n")
    GeneratedTest(name = name, body = sb.toString, skipReason = None)

  private def buildNegativeKeyTest(
      name: String,
      inputArgs: InputSig,
      pop: ProfiledOperation,
      inputName: String,
      stateName: String
  ): GeneratedTest =
    val sig = inputArgs.signature
    val gen = inputArgs.givenLine
    val sb  = new StringBuilder
    if gen.nonEmpty then
      sb.append(s"$gen\n")
      sb.append(
        "@settings(max_examples=10, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
      )
    sb.append(s"def $name($sig):\n")
    sb.append(
      s"    \"\"\"requires '$inputName in $stateName' (negative): missing key returns 4xx.\"\"\"\n"
    )
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    pre_state = client.get(\"/__test_admin__/state\").json()\n")
    sb.append(
      s"    assume($inputName not in pre_state.get(${ExprToPython.pyString(stateName)}, {}))\n"
    )
    sb.append(s"    response = ${requestCallExpr(pop)}\n")
    sb.append(
      s"    assert 400 <= response.status_code < 500, " +
        s"f${'"'}expected 4xx, got {response.status_code}: {response.text}${'"'}\n"
    )
    GeneratedTest(name = name, body = sb.toString, skipReason = None)

  private def buildInvariantTest(
      name: String,
      docstring: String,
      inputArgs: InputSig,
      pop: ProfiledOperation,
      assertion: String
  ): GeneratedTest =
    val sig = inputArgs.signature
    val gen = inputArgs.givenLine
    val sb  = new StringBuilder
    if gen.nonEmpty then
      sb.append(s"$gen\n")
      sb.append(
        "@settings(max_examples=10, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
      )
    sb.append(s"def $name($sig):\n")
    sb.append(s"    \"\"\"${escapeDocstring(docstring)}\"\"\"\n")
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    pre_state = client.get(\"/__test_admin__/state\").json()\n")
    sb.append(s"    response = ${requestCallExpr(pop)}\n")
    sb.append(s"    assume(response.status_code == ${pop.endpoint.successStatus})\n")
    sb.append("    response_data = response.json() if response.content else {}\n")
    sb.append("    post_state = client.get(\"/__test_admin__/state\").json()\n")
    sb.append(
      s"    assert $assertion, ${ExprToPython.pyString(s"invariant violated: $docstring")}\n"
    )
    GeneratedTest(name = name, body = sb.toString, skipReason = None)

  final private case class InputSig(
      names: List[String],
      signature: String,
      givenLine: String
  )

  private def inputArgList(pop: ProfiledOperation, ir: ServiceIR): Either[String, InputSig] =
    val params = pop.endpoint.pathParams ++ pop.endpoint.bodyParams ++ pop.endpoint.queryParams
    if params.isEmpty then Right(InputSig(Nil, "", ""))
    else
      val overrides = TestStrategyOverrides.from(ir)
      val pairs = params.map: p =>
        val ctx  = StrategyCtx.OperationInput(pop.operationName, p.name)
        val expr = Strategies.expressionFor(p.typeExpr, ir, ctx, overrides)
        (p.name, expr)
      val firstSkip = pairs.collectFirst { case (n, StrategyExpr.Skip(r)) => s"input '$n': $r" }
      firstSkip match
        case Some(reason) => Left(reason)
        case None =>
          val codes = pairs.collect { case (n, StrategyExpr.Code(t)) => (n, t) }
          val sig   = codes.map(_._1).mkString(", ")
          val args  = codes.map((n, t) => s"$n=$t").mkString(", ")
          val gen   = s"@given($args)"
          Right(InputSig(names = codes.map(_._1), signature = sig, givenLine = gen))

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

  private def containsStateRef(e: Expr, stateFields: Set[String]): Boolean =
    containsStateRefIn(e, stateFields, Set.empty)

  private def containsStateRefIn(
      e: Expr,
      stateFields: Set[String],
      bound: Set[String]
  ): Boolean = e match
    case Expr.Identifier(n, _) => !bound.contains(n) && stateFields.contains(n)
    case Expr.Pre(_, _)        => true
    case Expr.Prime(_, _)      => true
    case Expr.BinaryOp(_, l, r, _) =>
      containsStateRefIn(l, stateFields, bound) || containsStateRefIn(r, stateFields, bound)
    case Expr.UnaryOp(_, x, _)     => containsStateRefIn(x, stateFields, bound)
    case Expr.FieldAccess(b, _, _) => containsStateRefIn(b, stateFields, bound)
    case Expr.EnumAccess(b, _, _)  => containsStateRefIn(b, stateFields, bound)
    case Expr.Index(b, i, _) =>
      containsStateRefIn(b, stateFields, bound) || containsStateRefIn(i, stateFields, bound)
    case Expr.Call(c, args, _) =>
      containsStateRefIn(c, stateFields, bound) ||
      args.exists(containsStateRefIn(_, stateFields, bound))
    case Expr.If(c, t, el, _) =>
      containsStateRefIn(c, stateFields, bound) ||
      containsStateRefIn(t, stateFields, bound) ||
      containsStateRefIn(el, stateFields, bound)
    case Expr.Let(name, v, b, _) =>
      containsStateRefIn(v, stateFields, bound) ||
      containsStateRefIn(b, stateFields, bound + name)
    case Expr.Quantifier(_, bs, body, _) =>
      val bs2 = bound ++ bs.map(_.variable)
      bs.exists(qb => containsStateRefIn(qb.domain, stateFields, bound)) ||
      containsStateRefIn(body, stateFields, bs2)
    case Expr.SetLiteral(es, _) => es.exists(containsStateRefIn(_, stateFields, bound))
    case Expr.SeqLiteral(es, _) => es.exists(containsStateRefIn(_, stateFields, bound))
    case Expr.MapLiteral(es, _) =>
      es.exists: e =>
        containsStateRefIn(e.key, stateFields, bound) ||
          containsStateRefIn(e.value, stateFields, bound)
    case Expr.SetComprehension(name, d, p, _) =>
      containsStateRefIn(d, stateFields, bound) ||
      containsStateRefIn(p, stateFields, bound + name)
    case Expr.SomeWrap(x, _) => containsStateRefIn(x, stateFields, bound)
    case Expr.The(name, d, b, _) =>
      containsStateRefIn(d, stateFields, bound) ||
      containsStateRefIn(b, stateFields, bound + name)
    case Expr.With(b, ups, _) =>
      containsStateRefIn(b, stateFields, bound) ||
      ups.exists(u => containsStateRefIn(u.value, stateFields, bound))
    case Expr.Constructor(_, fs, _) =>
      fs.exists(f => containsStateRefIn(f.value, stateFields, bound))
    case Expr.Lambda(name, b, _) => containsStateRefIn(b, stateFields, bound + name)
    case Expr.Matches(x, _, _)   => containsStateRefIn(x, stateFields, bound)
    case Expr.IntLit(_, _) | Expr.FloatLit(_, _) | Expr.StringLit(_, _) | Expr.BoolLit(_, _) |
        Expr.NoneLit(_) =>
      false

  private def keyExistencePattern(
      e: Expr,
      inputs: Set[String],
      state: Set[String]
  ): Option[(String, String)] =
    e match
      case Expr.BinaryOp(BinOp.In, Expr.Identifier(in, _), Expr.Identifier(st, _), _)
          if inputs.contains(in) && state.contains(st) =>
        Some((in, st))
      case _ => None

  private def isTrivialTrue(e: Expr): Boolean = e match
    case Expr.BoolLit(true, _) => true
    case _                     => false

  private def invName(inv: InvariantDecl, idx: Int): String =
    inv.name.getOrElse(s"anon_$idx")

  private def prettyOneLine(e: Expr): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
