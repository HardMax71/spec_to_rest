package specrest.testgen

import specrest.convention.EndpointSpec
import specrest.convention.Naming
import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated.*
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

@SuppressWarnings(Array("org.wartremover.warts.Return", "org.wartremover.warts.OptionPartial"))
object Behavioral:

  def emitFor(profiled: ProfiledService): BehavioralOutput =
    val ir               = profiled.ir
    val transition       = transitionEmission(profiled, ir)
    val coveredByTransit = transition.coveredOps
    val perOp = profiled.operations.flatMap: pop =>
      ir.g.collectFirst { case o: OperationDeclFull if o.a == pop.operationName => o } match
        case Some(opDecl) => testsForOperation(pop, opDecl, ir, coveredByTransit)
        case None         => Nil
    val collected = perOp ++ transition.results
    val tests     = collected.collect { case Right(t) => t }
    val skips     = collected.collect { case Left(s) => s }
    BehavioralOutput(tests = tests, skips = skips)

  private def testsForOperation(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull,
      coveredByTransit: Set[String]
  ): List[Either[TestSkip, GeneratedTest]] =
    val ensures   = ensuresTests(pop, opDecl, ir, coveredByTransit)
    val negatives = negativeTests(pop, opDecl, ir)
    val invs      = invariantTests(pop, opDecl, ir, coveredByTransit)
    ensures ++ negatives ++ invs

  private def stateDepSkipReason(opName: String, coveredByTransit: Set[String]): String =
    if coveredByTransit.contains(opName) then
      "state-dependent precondition; covered by transition tests (M5.9)"
    else
      "state-dependent precondition; positive ensures/invariant tests are covered by stateful tests (M5.2) for ops bundled into the state machine; the single-shot behavioral test would need explicit pre-seeding"

  final private case class TransitionEmissionResult(
      results: List[Either[TestSkip, GeneratedTest]],
      coveredOps: Set[String]
  )

  private def transitionEmission(
      profiled: ProfiledService,
      ir: ServiceIRFull
  ): TransitionEmissionResult =
    ir.h.collect { case t: TransitionDeclFull => t }.foldLeft(
      TransitionEmissionResult(Nil, Set.empty)
    ): (acc, td) =>
      val per = transitionTestsForTd(td, profiled, ir)
      TransitionEmissionResult(acc.results ++ per.results, acc.coveredOps ++ per.coveredOps)

  private def ensuresTests(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull,
      coveredByTransit: Set[String]
  ): List[Either[TestSkip, GeneratedTest]] =
    val stateFields = ir.f.toList.flatMap { case StateDeclFull(_fs, _) =>
      _fs.collect { case StateFieldDeclFull(_n, _, _) => _n }
    }.toSet
    val opSnake = Naming.toSnakeCase(opDecl.a)

    val requiresHasStateRef = opDecl.d.exists(containsStateRef(_, stateFields))
    val nonTrivialRequires  = opDecl.d.exists(!isTrivialTrue(_))

    if requiresHasStateRef then
      List(
        Left(
          TestSkip(
            operation = opDecl.a,
            kind = "ensures",
            reason = stateDepSkipReason(opDecl.a, coveredByTransit)
          )
        )
      )
    else
      val inputArgs = inputArgList(pop, ir)
      inputArgs match
        case Left(reason) =>
          List(Left(TestSkip(opDecl.a, "ensures", reason)))
        case Right(strategySig) =>
          val ctx = TestCtx.fromOperation(opDecl, ir, CaptureMode.PreState)
          opDecl.e.zipWithIndex.map: (clause, idx) =>
            ExprToPython.translate(clause, ctx) match
              case ExprPy.Skip(reason, _) =>
                Left(TestSkip(opDecl.a, s"ensures[$idx]", reason))
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
      opDecl: OperationDeclFull,
      ir: ServiceIRFull
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake = Naming.toSnakeCase(opDecl.a)
    val inputs  = opDecl.b.collect { case ParamDeclFull(_n, _, _) => _n }.toSet
    val stateFields = ir.f.toList.flatMap { case StateDeclFull(_fs, _) =>
      _fs.collect { case StateFieldDeclFull(_n, _, _) => _n }
    }.toSet

    opDecl.d.zipWithIndex.flatMap: (req, idx) =>
      keyExistencePattern(req, inputs, stateFields) match
        case Some((inputName, stateName)) =>
          inputArgList(pop, ir) match
            case Left(reason) =>
              List(Left(TestSkip(opDecl.a, s"requires[$idx]", reason)))
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
          statusRestrictionPattern(req, inputs, stateFields, ir) match
            case Some(restriction) =>
              statusRestrictionNegativeOrSkip(opDecl, pop, ir, restriction, idx).toList
            case None =>
              if isTrivialTrue(req) then Nil
              else
                List(
                  Left(
                    TestSkip(
                      opDecl.a,
                      s"requires[$idx]",
                      "M5.1: only `<input> in <state>` and `<state>[input].<enum-field> = <value>` requires patterns get negative tests"
                    )
                  )
                )

  private def invariantTests(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull,
      coveredByTransit: Set[String]
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake = Naming.toSnakeCase(opDecl.a)
    val stateFields = ir.f.toList.flatMap { case StateDeclFull(_fs, _) =>
      _fs.collect { case StateFieldDeclFull(_n, _, _) => _n }
    }.toSet

    if opDecl.d.exists(containsStateRef(_, stateFields)) then
      ir.i.collect { case _iv: InvariantDeclFull => _iv }.zipWithIndex.toList.map: (inv, idx) =>
        Left(
          TestSkip(
            opDecl.a,
            s"invariant[${invName(inv, idx)}]",
            stateDepSkipReason(opDecl.a, coveredByTransit)
          )
        )
    else
      val ctx = TestCtx.fromOperation(opDecl, ir, CaptureMode.PostState)
      inputArgList(pop, ir) match
        case Left(reason) =>
          List(Left(TestSkip(opDecl.a, "invariant_inputs", reason)))
        case Right(strategySig) =>
          ir.i.collect { case _iv: InvariantDeclFull => _iv }.zipWithIndex.toList.map: (inv, idx) =>
            ExprToPython.translate(inv.b, ctx) match
              case ExprPy.Skip(reason, _) =>
                Left(TestSkip(opDecl.a, s"invariant[${invName(inv, idx)}]", reason))
              case ExprPy.Py(text) =>
                Right(
                  buildInvariantTest(
                    name = s"test_${opSnake}_invariant_${Naming.toSnakeCase(invName(inv, idx))}",
                    docstring = s"invariant ${invName(inv, idx)}: ${prettyOneLine(inv.b)}",
                    inputArgs = strategySig,
                    pop = pop,
                    assertion = text
                  )
                )

  private def transitionTestsForTd(
      td: TransitionDeclFull,
      profiled: ProfiledService,
      ir: ServiceIRFull
  ): TransitionEmissionResult =
    val entityOpt = ir.c.collectFirst { case _e: EntityDeclFull if _e.a == td.b => _e }
    if entityOpt.isEmpty then
      return TransitionEmissionResult(
        List(Left(TestSkip(td.a, "transition", s"unknown entity '${td.b}'"))),
        Set.empty
      )
    val entity   = entityOpt.get
    val fieldOpt = entity.c.collectFirst { case _f: FieldDeclFull if _f.a == td.c => _f }
    if fieldOpt.isEmpty then
      return TransitionEmissionResult(
        List(
          Left(
            TestSkip(
              td.a,
              "transition",
              s"entity '${entity.a}' has no field '${td.c}'"
            )
          )
        ),
        Set.empty
      )
    val enumValuesOpt = enumValuesForField(fieldOpt.get, ir)
    if enumValuesOpt.isEmpty then
      return TransitionEmissionResult(
        List(
          Left(
            TestSkip(
              td.a,
              "transition",
              s"transition field '${td.c}' is not an enum (or alias of enum); illegal-from enumeration undefined"
            )
          )
        ),
        Set.empty
      )
    val pkOpt = AdminRouter.primaryKeyField(entity)
    if pkOpt.isEmpty then
      return TransitionEmissionResult(
        List(
          Left(
            TestSkip(
              td.a,
              "transition",
              s"entity '${entity.a}' has no field; cannot seed"
            )
          )
        ),
        Set.empty
      )
    val statusValues = enumValuesOpt.get
    val pk           = pkOpt.get
    val byVia        = td.d.collect { case r: TransitionRuleFull => r }.groupBy(_.c)
    byVia.toList.sortBy(_._1).foldLeft(TransitionEmissionResult(Nil, Set.empty)): (acc, kv) =>
      val (viaName, rules) = kv
      val opDeclOpt        = ir.g.collectFirst { case _o: OperationDeclFull if _o.a == viaName => _o }
      val popOpt           = profiled.operations.find(_.operationName == viaName)
      val per: TransitionEmissionResult = (opDeclOpt, popOpt) match
        case (Some(_), Some(pop)) if pop.endpoint.pathParams.size != 1 =>
          TransitionEmissionResult(
            List(
              Left(
                TestSkip(
                  viaName,
                  s"transition[$viaName]",
                  "transition tests require exactly one path input identifying the seeded entity; multi-path or zero-path shapes need multi-entity seed orchestration"
                )
              )
            ),
            Set.empty
          )
        case (Some(opDecl), Some(pop)) =>
          nonPathInputBindings(opDecl, pop, ir) match
            case Left((paramName, reason)) =>
              TransitionEmissionResult(
                List(
                  Left(
                    TestSkip(
                      viaName,
                      s"transition[$viaName]",
                      s"transition tests need a generable strategy for input '$paramName': $reason"
                    )
                  )
                ),
                Set.empty
              )
            case Right(nonPath) =>
              val legalFroms   = rules.map(_.a).toSet
              val illegalFroms = statusValues.filterNot(legalFroms.contains)
              val stateField = stateFieldForEntity(td.b, ir)
                .getOrElse(Naming.toSnakeCase(td.b) + "s")
              val positives = rules.toList.map: rule =>
                buildTransitionPositiveOrSkip(
                  td = td,
                  entity = entity,
                  fieldName = td.c,
                  pkName = pk,
                  rule = rule,
                  opDecl = opDecl,
                  pop = pop,
                  stateField = stateField,
                  ir = ir,
                  nonPath = nonPath
                )
              val negatives = illegalFroms.toList.sorted.map: from =>
                buildTransitionNegative(
                  entity = entity,
                  fieldName = td.c,
                  pkName = pk,
                  from = from,
                  opDecl = opDecl,
                  pop = pop,
                  nonPath = nonPath
                )
              val emitted    = positives ++ negatives
              val anyRuntime = emitted.exists(_.isRight)
              TransitionEmissionResult(
                emitted,
                if anyRuntime then Set(viaName) else Set.empty
              )
        case _ =>
          TransitionEmissionResult(
            List(
              Left(
                TestSkip(
                  viaName,
                  s"transition[$viaName]",
                  s"no operation named '$viaName' for via clause"
                )
              )
            ),
            Set.empty
          )
      TransitionEmissionResult(acc.results ++ per.results, acc.coveredOps ++ per.coveredOps)

  private def enumValuesForField(
      field: FieldDeclFull,
      ir: ServiceIRFull
  ): Option[List[String]] =
    field.b match
      case NamedTypeF(name, _) =>
        ir.d.collectFirst { case _e: EnumDeclFull if _e.a == name => _e.b }.orElse:
          ir.e.collectFirst { case _a: TypeAliasDeclFull if _a.a == name => _a }.flatMap: alias =>
            enumValuesForField(field.copy(b = alias.b), ir)
      case _ => None

  final private case class NonPathInput(
      name: String,
      argName: String,
      kind: NonPathKind,
      strategyExpr: String
  )
  private enum NonPathKind derives CanEqual:
    case Body
    case Query

  private val ReservedTestLocals: Set[String] =
    Set("row", "seed", "seeded_id", "response", "client", "pre_state", "post_state", "wrong_status")

  private def safeArgName(raw: String, taken: Set[String]): String =
    if !ReservedTestLocals.contains(raw) && !taken.contains(raw) then raw
    else
      val base = s"_arg_$raw"
      if !taken.contains(base) && !ReservedTestLocals.contains(base) then base
      else
        Iterator
          .from(1)
          .map(i => s"${base}_$i")
          .find(n => !taken.contains(n) && !ReservedTestLocals.contains(n))
          .get

  private def nonPathInputBindings(
      opDecl: OperationDeclFull,
      pop: ProfiledOperation,
      ir: ServiceIRFull
  ): Either[(String, String), List[NonPathInput]] =
    val overrides = TestStrategyOverrides.from(ir)
    val tagged =
      pop.endpoint.bodyParams.map(p => (p, NonPathKind.Body)) ++
        pop.endpoint.queryParams.map(p => (p, NonPathKind.Query))
    val resolved = tagged.map: (p, k) =>
      val ctx  = StrategyCtx.OperationInput(opDecl.a, p.name)
      val expr = Strategies.expressionFor(p.typeExpr, ir, ctx, overrides)
      (p.name, k, expr)
    resolved.collectFirst { case (n, _, StrategyExpr.Skip(r)) => (n, r) } match
      case Some(skip) => Left(skip)
      case None =>
        val withArgs = resolved
          .collect { case (n, k, StrategyExpr.Code(t)) => (n, k, t) }
          .foldLeft((List.empty[NonPathInput], Set.empty[String])):
            case ((acc, taken), (n, k, t)) =>
              val arg = safeArgName(n, taken)
              (acc :+ NonPathInput(n, arg, k, t), taken + arg)
        Right(withArgs._1)

  private def buildTransitionPositiveOrSkip(
      td: TransitionDeclFull,
      entity: EntityDeclFull,
      fieldName: String,
      pkName: String,
      rule: TransitionRuleFull,
      opDecl: OperationDeclFull,
      pop: ProfiledOperation,
      stateField: String,
      ir: ServiceIRFull,
      nonPath: List[NonPathInput]
  ): Either[TestSkip, GeneratedTest] =
    val fixLines = rule.d match
      case None        => Some(Nil)
      case Some(guard) => GuardSatisfier.recognize(guard, entity, fieldName, rule.a, ir)
    fixLines match
      case None =>
        Left(
          TestSkip(
            opDecl.a,
            s"transition[${rule.a}_to_${rule.b}]",
            s"guard '${prettyOneLine(rule.d.get)}' uses constructs the seed-dict recognizer does not cover; see docs 'Guarded positive transitions' for the supported shapes"
          )
        )
      case Some(lines) =>
        Right(
          buildTransitionPositive(
            td = td,
            entity = entity,
            fieldName = fieldName,
            pkName = pkName,
            from = rule.a,
            to = rule.b,
            opDecl = opDecl,
            pop = pop,
            stateField = stateField,
            guardFixLines = lines,
            nonPath = nonPath
          )
        )

  private def buildTransitionPositive(
      td: TransitionDeclFull,
      entity: EntityDeclFull,
      fieldName: String,
      pkName: String,
      from: String,
      to: String,
      opDecl: OperationDeclFull,
      pop: ProfiledOperation,
      stateField: String,
      guardFixLines: List[String],
      nonPath: List[NonPathInput]
  ): GeneratedTest =
    val opSnake     = Naming.toSnakeCase(opDecl.a)
    val entitySnake = Naming.toSnakeCase(entity.a)
    val testName    = s"test_${opSnake}_transition_${from.toLowerCase}_to_${to.toLowerCase}"
    val rowStrategy = Strategies.strategyFunctionName(entity.a)
    val pkKey       = ExprToPython.pyString(pkName)
    val fieldKey    = ExprToPython.pyString(fieldName)
    val stateKey    = ExprToPython.pyString(stateField)
    val sb          = new StringBuilder
    sb.append(givenLineFor(rowStrategy, nonPath))
    sb.append(
      "@settings(max_examples=20, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
    )
    sb.append(s"def $testName(${signatureFor(nonPath)}):\n")
    sb.append(
      s"    \"\"\"transition ${opDecl.a}: $from -> $to (post-state ${td.c} = $to)\"\"\"\n"
    )
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = ${ExprToPython.pyString(from)}\n")
    guardFixLines.foreach: line =>
      sb.append(s"    $line\n")
    sb.append(s"    seed = client.post(\"/__test_admin__/seed/$entitySnake\", json=row)\n")
    sb.append("    assume(seed.status_code == 201)\n")
    sb.append(s"    seeded_id = seed.json()[$pkKey]\n")
    sb.append(transitionRequestCall(pop, nonPath))
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
        s"f\"expected ${td.c}=$to, got {actual!r}\"\n"
    )
    GeneratedTest(name = testName, body = sb.toString, skipReason = None)

  private def buildTransitionNegative(
      entity: EntityDeclFull,
      fieldName: String,
      pkName: String,
      from: String,
      opDecl: OperationDeclFull,
      pop: ProfiledOperation,
      nonPath: List[NonPathInput]
  ): Either[TestSkip, GeneratedTest] =
    val opSnake     = Naming.toSnakeCase(opDecl.a)
    val entitySnake = Naming.toSnakeCase(entity.a)
    val testName    = s"test_${opSnake}_transition_illegal_from_${from.toLowerCase}"
    val rowStrategy = Strategies.strategyFunctionName(entity.a)
    val pkKey       = ExprToPython.pyString(pkName)
    val fieldKey    = ExprToPython.pyString(fieldName)
    val sb          = new StringBuilder
    sb.append(givenLineFor(rowStrategy, nonPath))
    sb.append(
      "@settings(max_examples=10, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
    )
    sb.append(s"def $testName(${signatureFor(nonPath)}):\n")
    sb.append(
      s"    \"\"\"transition ${opDecl.a}: from=$from is illegal (no rule); SUT must reject 4xx\"\"\"\n"
    )
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = ${ExprToPython.pyString(from)}\n")
    sb.append(s"    seed = client.post(\"/__test_admin__/seed/$entitySnake\", json=row)\n")
    sb.append("    assume(seed.status_code == 201)\n")
    sb.append(s"    seeded_id = seed.json()[$pkKey]\n")
    sb.append(transitionRequestCall(pop, nonPath))
    sb.append(
      "    assert 400 <= response.status_code < 500, " +
        s"f${'"'}expected 4xx, got {response.status_code}: {response.text}${'"'}\n"
    )
    Right(GeneratedTest(name = testName, body = sb.toString, skipReason = None))

  private def givenLineFor(rowStrategy: String, nonPath: List[NonPathInput]): String =
    val rowPair = s"row=$rowStrategy()"
    val extra   = nonPath.map(i => s"${i.argName}=${i.strategyExpr}")
    val args    = (rowPair :: extra).mkString(", ")
    s"@given($args)\n"

  private def signatureFor(nonPath: List[NonPathInput]): String =
    ("row" :: nonPath.map(_.argName)).mkString(", ")

  private def transitionRequestCall(pop: ProfiledOperation, nonPath: List[NonPathInput]): String =
    val ep        = pop.endpoint
    val pathParam = ep.pathParams.headOption.map(_.name)
    val pathExpr =
      pathParam match
        case Some(p) =>
          val rendered = ep.path.replace(s"{$p}", "{seeded_id}")
          "f" + ExprToPython.pyString(rendered)
        case None => ExprToPython.pyString(ep.path)
    val method = ep.method.toString.toLowerCase
    val bodyEntries = nonPath.collect:
      case NonPathInput(n, arg, NonPathKind.Body, _) => s"${ExprToPython.pyString(n)}: $arg"
    val queryEntries = nonPath.collect:
      case NonPathInput(n, arg, NonPathKind.Query, _) => s"${ExprToPython.pyString(n)}: $arg"
    val bodyExpr =
      if bodyEntries.isEmpty then "" else s", json={${bodyEntries.mkString(", ")}}"
    val queryExpr =
      if queryEntries.isEmpty then "" else s", params={${queryEntries.mkString(", ")}}"
    s"    response = client.$method($pathExpr$bodyExpr$queryExpr)\n"

  private def stateFieldForEntity(entityName: String, ir: ServiceIRFull): Option[String] =
    ir.f.toList.flatMap { case StateDeclFull(fs, _) => fs }.collectFirst:
      case StateFieldDeclFull(n, t, _) if relationTargetsEntity(t, entityName) => n

  private def relationTargetsEntity(t: type_expr_full, entity: String): Boolean = t match
    case RelationTypeF(_, _, NamedTypeF(n, _), _) => n == entity
    case NamedTypeF(n, _)                         => n == entity
    case _                                        => false

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
      "    assert 400 <= response.status_code < 500, " +
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

  private def inputArgList(pop: ProfiledOperation, ir: ServiceIRFull): Either[String, InputSig] =
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

  private def containsStateRef(e: expr_full, stateFields: Set[String]): Boolean =
    containsStateRefIn(e, stateFields, Set.empty)

  private def containsStateRefIn(
      e: expr_full,
      stateFields: Set[String],
      bound: Set[String]
  ): Boolean = e match
    case IdentifierF(n, _) => !bound.contains(n) && stateFields.contains(n)
    case PreF(_, _)        => true
    case PrimeF(_, _)      => true
    case BinaryOpF(_, l, r, _) =>
      containsStateRefIn(l, stateFields, bound) || containsStateRefIn(r, stateFields, bound)
    case UnaryOpF(_, x, _)     => containsStateRefIn(x, stateFields, bound)
    case FieldAccessF(b, _, _) => containsStateRefIn(b, stateFields, bound)
    case EnumAccessF(b, _, _)  => containsStateRefIn(b, stateFields, bound)
    case IndexF(b, i, _) =>
      containsStateRefIn(b, stateFields, bound) || containsStateRefIn(i, stateFields, bound)
    case CallF(c, args, _) =>
      containsStateRefIn(c, stateFields, bound) ||
      args.exists(containsStateRefIn(_, stateFields, bound))
    case IfF(c, t, el, _) =>
      containsStateRefIn(c, stateFields, bound) ||
      containsStateRefIn(t, stateFields, bound) ||
      containsStateRefIn(el, stateFields, bound)
    case LetF(name, v, b, _) =>
      containsStateRefIn(v, stateFields, bound) ||
      containsStateRefIn(b, stateFields, bound + name)
    case QuantifierF(_, bs, body, _) =>
      val bsList = bs.collect { case b: QuantifierBindingFull => b }
      val bs2    = bound ++ bsList.map(_.a)
      bsList.exists(qb => containsStateRefIn(qb.b, stateFields, bound)) ||
      containsStateRefIn(body, stateFields, bs2)
    case SetLiteralF(es, _) => es.exists(containsStateRefIn(_, stateFields, bound))
    case SeqLiteralF(es, _) => es.exists(containsStateRefIn(_, stateFields, bound))
    case MapLiteralF(es, _) =>
      es.exists { case MapEntryFull(k, v, _) =>
        containsStateRefIn(k, stateFields, bound) ||
        containsStateRefIn(v, stateFields, bound)
      }
    case SetComprehensionF(name, d, p, _) =>
      containsStateRefIn(d, stateFields, bound) ||
      containsStateRefIn(p, stateFields, bound + name)
    case SomeWrapF(x, _) => containsStateRefIn(x, stateFields, bound)
    case TheF(name, d, b, _) =>
      containsStateRefIn(d, stateFields, bound) ||
      containsStateRefIn(b, stateFields, bound + name)
    case WithF(b, ups, _) =>
      containsStateRefIn(b, stateFields, bound) ||
      ups.exists { case FieldAssignFull(_, v, _) => containsStateRefIn(v, stateFields, bound) }
    case ConstructorF(_, fs, _) =>
      fs.exists { case FieldAssignFull(_, v, _) => containsStateRefIn(v, stateFields, bound) }
    case LambdaF(name, b, _) => containsStateRefIn(b, stateFields, bound + name)
    case MatchesF(x, _, _)   => containsStateRefIn(x, stateFields, bound)
    case IntLitF(_, _) | FloatLitF(_, _) | StringLitF(_, _) | BoolLitF(_, _) |
        NoneLitF(_) =>
      false

  private def keyExistencePattern(
      e: expr_full,
      inputs: Set[String],
      state: Set[String]
  ): Option[(String, String)] =
    e match
      case BinaryOpF(BIn(), IdentifierF(in, _), IdentifierF(st, _), _)
          if inputs.contains(in) && state.contains(st) =>
        Some((in, st))
      case _ => None

  final private case class StatusRestriction(
      inputName: String,
      stateName: String,
      entityName: String,
      fieldName: String,
      requiredValue: String,
      enumValues: List[String],
      pkField: String
  )

  private def statusRestrictionPattern(
      e: expr_full,
      inputs: Set[String],
      state: Set[String],
      ir: ServiceIRFull
  ): Option[StatusRestriction] =
    e match
      case BinaryOpF(
            BEq(),
            FieldAccessF(
              IndexF(IdentifierF(stName, _), IdentifierF(inName, _), _),
              field,
              _
            ),
            rhs,
            _
          ) if inputs.contains(inName) && state.contains(stName) =>
        for
          entityName <- entityForStateField(stName, ir)
          entity     <- ir.c.collectFirst { case _e: EntityDeclFull if _e.a == entityName => _e }
          if Strategies.transitionEntityNames(ir).contains(entityName)
          fieldDecl <- entity.c.collectFirst { case _f: FieldDeclFull if _f.a == field => _f }
          enumVals  <- enumValuesForField(fieldDecl, ir)
          if enumVals.nonEmpty
          rhsLit <- enumLiteralFor(rhs, enumVals)
          pk     <- AdminRouter.primaryKeyField(entity)
          if enumVals.size >= 2
        yield StatusRestriction(inName, stName, entityName, field, rhsLit, enumVals, pk)
      case _ => None

  private def entityForStateField(stateFieldName: String, ir: ServiceIRFull): Option[String] =
    ir.f.toList
      .flatMap { case StateDeclFull(fs, _) => fs }
      .collectFirst { case StateFieldDeclFull(n, t, _) if n == stateFieldName => t }
      .flatMap(relationTargetEntityName)

  private def relationTargetEntityName(t: type_expr_full): Option[String] = t match
    case RelationTypeF(_, _, NamedTypeF(n, _), _) => Some(n)
    case NamedTypeF(n, _)                         => Some(n)
    case _                                        => None

  private def enumLiteralFor(rhs: expr_full, enumValues: List[String]): Option[String] =
    rhs match
      case EnumAccessF(_, member, _) if enumValues.contains(member) => Some(member)
      case IdentifierF(name, _) if enumValues.contains(name)        => Some(name)
      case _                                                        => None

  private def statusRestrictionNegativeOrSkip(
      opDecl: OperationDeclFull,
      pop: ProfiledOperation,
      ir: ServiceIRFull,
      restriction: StatusRestriction,
      idx: Int
  ): Option[Either[TestSkip, GeneratedTest]] =
    if pop.endpoint.pathParams.size != 1 then
      Some(
        Left(
          TestSkip(
            opDecl.a,
            s"requires[$idx]",
            "status-restriction negative needs exactly one path input identifying the seeded entity; multi-path or zero-path shapes need multi-entity seed orchestration"
          )
        )
      )
    else
      nonPathInputBindings(opDecl, pop, ir) match
        case Left((paramName, reason)) =>
          Some(
            Left(
              TestSkip(
                opDecl.a,
                s"requires[$idx]",
                s"status-restriction negative needs a generable strategy for input '$paramName': $reason"
              )
            )
          )
        case Right(nonPath) =>
          Some(Right(buildStatusRestrictionNegative(opDecl, pop, restriction, nonPath)))

  private def buildStatusRestrictionNegative(
      opDecl: OperationDeclFull,
      pop: ProfiledOperation,
      r: StatusRestriction,
      nonPath: List[NonPathInput]
  ): GeneratedTest =
    val opSnake     = Naming.toSnakeCase(opDecl.a)
    val entitySnake = Naming.toSnakeCase(r.entityName)
    val testName =
      s"test_${opSnake}_negative_${r.stateName}_${r.fieldName}_not_${r.requiredValue.toLowerCase}"
    val rowStrategy = Strategies.strategyFunctionName(r.entityName)
    val pkKey       = ExprToPython.pyString(r.pkField)
    val fieldKey    = ExprToPython.pyString(r.fieldName)
    val wrongValues = r.enumValues.filterNot(_ == r.requiredValue)
    val sampledFrom = wrongValues.map(ExprToPython.pyString).mkString("[", ", ", "]")
    val extraGiven =
      List("row" -> s"$rowStrategy()", "wrong_status" -> s"st.sampled_from($sampledFrom)") ++
        nonPath.map(i => i.argName -> i.strategyExpr)
    val givenArgs = extraGiven.map((n, e) => s"$n=$e").mkString(", ")
    val sigParams = ("row" :: "wrong_status" :: nonPath.map(_.argName)).mkString(", ")
    val sb        = new StringBuilder
    sb.append(s"@given($givenArgs)\n")
    sb.append(
      "@settings(max_examples=10, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
    )
    sb.append(s"def $testName($sigParams):\n")
    sb.append(
      s"    \"\"\"requires '${r.stateName}[${r.inputName}].${r.fieldName} = ${r.requiredValue}' (negative): wrong status returns 4xx.\"\"\"\n"
    )
    sb.append("    client.post(\"/__test_admin__/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = wrong_status\n")
    sb.append(s"    seed = client.post(\"/__test_admin__/seed/$entitySnake\", json=row)\n")
    sb.append("    assume(seed.status_code == 201)\n")
    sb.append(s"    seeded_id = seed.json()[$pkKey]\n")
    sb.append(transitionRequestCall(pop, nonPath))
    sb.append(
      "    assert 400 <= response.status_code < 500, " +
        s"f${'"'}expected 4xx, got {response.status_code}: {response.text}${'"'}\n"
    )
    GeneratedTest(name = testName, body = sb.toString, skipReason = None)

  private def isTrivialTrue(e: expr_full): Boolean = e match
    case BoolLitF(true, _) => true
    case _                 => false

  private def invName(inv: InvariantDeclFull, idx: Int): String =
    inv.a.getOrElse(s"anon_$idx")

  private def prettyOneLine(e: expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def escapeDocstring(s: String): String =
    s.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")

  private[testgen] object GuardSatisfier:

    sealed trait FieldKind derives CanEqual
    case object DateTimeField extends FieldKind
    case object NumericField  extends FieldKind

    sealed trait Fix derives CanEqual:
      def writeKey: String
      def reads: Set[String] = Set.empty
      def lines: List[String]

    private case class OrderedShiftField(
        leftKey: String,
        rightKey: String,
        kind: FieldKind,
        rightOptional: Boolean,
        deltaSeconds: Int
    ) extends Fix:
      def writeKey: String            = leftKey
      override def reads: Set[String] = Set(rightKey)
      def lines: List[String] =
        val lk      = ExprToPython.pyString(leftKey)
        val rk      = ExprToPython.pyString(rightKey)
        val anchor  = "datetime.datetime(2024, 1, 1)"
        val parseR  = s"datetime.datetime.fromisoformat(row[$rk])"
        val deltaPy = s"datetime.timedelta(seconds=$deltaSeconds)"
        kind match
          case DateTimeField =>
            val anchorIfNone =
              if rightOptional then List(s"if row[$rk] is None: row[$rk] = $anchor.isoformat()")
              else Nil
            anchorIfNone ++ List(s"row[$lk] = ($parseR + $deltaPy).isoformat()")
          case NumericField =>
            val anchorIfNone =
              if rightOptional then List(s"if row[$rk] is None: row[$rk] = 0")
              else Nil
            val rhs =
              if deltaSeconds == 0 then s"row[$rk]"
              else if deltaSeconds > 0 then s"row[$rk] + $deltaSeconds"
              else s"row[$rk] - ${-deltaSeconds}"
            anchorIfNone ++ List(s"row[$lk] = $rhs")

    private case class OrderedShiftConst(
        field: String,
        kind: FieldKind,
        constPy: String,
        deltaSeconds: Int
    ) extends Fix:
      def writeKey: String = field
      def lines: List[String] =
        val k = ExprToPython.pyString(field)
        kind match
          case NumericField =>
            val rhs = deltaSeconds match
              case 0          => constPy
              case d if d > 0 => s"$constPy + $d"
              case d          => s"$constPy - ${-d}"
            List(s"row[$k] = $rhs")
          case DateTimeField =>
            // not currently reachable — spec syntax has no DateTime literal — but
            // keep the branch coherent by treating the const as an ISO string.
            val parsed = s"datetime.datetime.fromisoformat($constPy)"
            val delta  = s"datetime.timedelta(seconds=$deltaSeconds)"
            List(s"row[$k] = ($parsed + $delta).isoformat()")

    private case class Assign(field: String, pyValue: String) extends Fix:
      def writeKey: String = field
      def lines: List[String] =
        List(s"row[${ExprToPython.pyString(field)}] = $pyValue")

    private case class NotNoneAnchor(field: String, anchor: String) extends Fix:
      def writeKey: String = field
      def lines: List[String] =
        val k = ExprToPython.pyString(field)
        List(s"if row[$k] is None: row[$k] = $anchor")

    private case class ListOfSize(field: String, items: List[String]) extends Fix:
      def writeKey: String = field
      def lines: List[String] =
        val k = ExprToPython.pyString(field)
        List(s"row[$k] = [${items.mkString(", ")}]")

    private case class ListAppend(field: String, pyElem: String, optional: Boolean) extends Fix:
      def writeKey: String            = field
      override def reads: Set[String] = Set(field)
      def lines: List[String] =
        val k      = ExprToPython.pyString(field)
        val anchor = if optional then List(s"if row[$k] is None: row[$k] = []") else Nil
        anchor ++ List(s"row[$k] = list(row[$k]) + [$pyElem]")

    private case class NoOp(label: String) extends Fix:
      def writeKey: String    = s"__noop:$label"
      def lines: List[String] = Nil

    def recognize(
        guard: expr_full,
        entity: EntityDeclFull,
        transitionField: String,
        from: String,
        ir: ServiceIRFull
    ): Option[List[String]] =
      collect(guard, entity, transitionField, from, ir).flatMap: fixes =>
        val realFixes = fixes.filter:
          case _: NoOp => false
          case _       => true
        val byKey = realFixes.groupBy(_.writeKey)
        val conflict = byKey.exists: (_, group) =>
          group.map(_.lines).distinct.size > 1
        if conflict then None
        else
          val deduped = byKey.toList.sortBy(_._1).map(_._2.head)
          topoOrder(deduped).map(_.flatMap(_.lines))

    @scala.annotation.tailrec
    private def topoStep(remaining: List[Fix], placed: List[Fix]): Option[List[Fix]] =
      if remaining.isEmpty then Some(placed.reverse)
      else
        val pendingWrites = remaining.map(_.writeKey).toSet
        val idx = remaining.indexWhere: f =>
          f.reads.filterNot(_ == f.writeKey).forall(r => !pendingWrites.contains(r))
        if idx < 0 then None
        else
          val (pre, mid) = remaining.splitAt(idx)
          val picked     = mid.head
          val rest       = pre ++ mid.tail
          topoStep(rest, picked :: placed)

    private def topoOrder(fixes: List[Fix]): Option[List[Fix]] = topoStep(fixes, Nil)

    private def collect(
        guard: expr_full,
        entity: EntityDeclFull,
        transitionField: String,
        from: String,
        ir: ServiceIRFull
    ): Option[List[Fix]] = guard match

      case UnaryOpF(UNot(), inner, _) =>
        negate(inner).flatMap(collect(_, entity, transitionField, from, ir))

      case BinaryOpF(BAnd(), l, r, _) =>
        for
          a <- collect(l, entity, transitionField, from, ir)
          b <- collect(r, entity, transitionField, from, ir)
        yield a ++ b

      case BinaryOpF(BEq(), IdentifierF(a, _), rhs, _) if a == transitionField =>
        literalValueFor(rhs, ir).flatMap: py =>
          if py == ExprToPython.pyString(from) then Some(List(NoOp(s"$a=$from")))
          else None

      case BinaryOpF(op, IdentifierF(a, _), IdentifierF(b, _), _)
          if Set[bin_op_full](BGt(), BGe(), BLt(), BLe()).contains(op) =>
        if a == transitionField || b == transitionField then None
        else
          for
            fa <- entity.c.collectFirst { case _f: FieldDeclFull if _f.a == a => _f }
            fb <- entity.c.collectFirst { case _f: FieldDeclFull if _f.a == b => _f }
            kind <-
              if AdminRouter.isDateTimeType(fa.b, ir, Set.empty) &&
                AdminRouter.isDateTimeType(fb.b, ir, Set.empty)
              then Some(DateTimeField)
              else if AdminRouter.isNumericType(fa.b, ir, Set.empty) &&
                AdminRouter.isNumericType(fb.b, ir, Set.empty)
              then Some(NumericField)
              else None
          yield List(
            OrderedShiftField(
              leftKey = a,
              rightKey = b,
              kind = kind,
              rightOptional = AdminRouter.isOptionalType(fb.b, ir, Set.empty),
              deltaSeconds = orderedDelta(op)
            )
          )

      case BinaryOpF(op, IdentifierF(a, _), rhs, _)
          if Set[bin_op_full](BGt(), BGe(), BLt(), BLe()).contains(op)
            && a != transitionField =>
        for
          fa <- entity.c.collectFirst { case _f: FieldDeclFull if _f.a == a => _f }
          if AdminRouter.isNumericType(fa.b, ir, Set.empty)
          constPy <- numericLiteralPy(rhs)
        yield List(
          OrderedShiftConst(
            field = a,
            kind = NumericField,
            constPy = constPy,
            deltaSeconds = orderedDelta(op)
          )
        )

      case BinaryOpF(BEq(), IdentifierF(a, _), NoneLitF(_), _)
          if a != transitionField =>
        entity.c.collectFirst { case _f: FieldDeclFull if _f.a == a => _f }.flatMap: f =>
          if AdminRouter.isOptionalType(f.b, ir, Set.empty) then
            Some(List(Assign(a, "None")))
          else None

      case BinaryOpF(BEq(), IdentifierF(a, _), rhs, _)
          if a != transitionField =>
        entity.c.collectFirst { case _f: FieldDeclFull if _f.a == a => _f }.flatMap: _ =>
          literalValueFor(rhs, ir).map(py => List(Assign(a, py)))

      case BinaryOpF(BNeq(), IdentifierF(a, _), NoneLitF(_), _)
          if a != transitionField =>
        entity.c.collectFirst { case _f: FieldDeclFull if _f.a == a => _f }.flatMap: f =>
          notNoneAnchorFor(f, ir).map(anchor => List(NotNoneAnchor(a, anchor)))

      case BinaryOpF(BIn(), lit, IdentifierF(field, _), _)
          if field != transitionField =>
        for
          f     <- entity.c.collectFirst { case _f: FieldDeclFull if _f.a == field => _f }
          inner <- collectionElementType(f.b, ir)
          py    <- literalForElementType(lit, inner, ir)
        yield List(ListAppend(field, py, AdminRouter.isOptionalType(f.b, ir, Set.empty)))

      case BinaryOpF(op, lenOrCard, IntLitF(int_of_integer(n), _), _)
          if op match { case _: (BGt | BGe | BLt | BLe | BEq) => true; case _ => false } =>
        for
          field <- isLenOrCardOf(lenOrCard)
          if field != transitionField
          f       <- entity.c.collectFirst { case _f: FieldDeclFull if _f.a == field => _f }
          inner   <- collectionElementType(f.b, ir)
          size    <- desiredSize(op, n.toInt)
          fillers <- buildFillers(size, inner, ir)
        yield List(ListOfSize(field, fillers))

      case _ => None

    private def orderedDelta(op: bin_op_full): Int = op match
      case _: BGt => 1
      case _: BGe => 0
      case _: BLt => -1
      case _: BLe => 0
      case _      => 0

    private def negate(e: expr_full): Option[expr_full] = e match
      case UnaryOpF(UNot(), inner, _)  => Some(inner)
      case BinaryOpF(BGt(), l, r, sp)  => Some(BinaryOpF(BLe(), l, r, sp))
      case BinaryOpF(BGe(), l, r, sp)  => Some(BinaryOpF(BLt(), l, r, sp))
      case BinaryOpF(BLt(), l, r, sp)  => Some(BinaryOpF(BGe(), l, r, sp))
      case BinaryOpF(BLe(), l, r, sp)  => Some(BinaryOpF(BGt(), l, r, sp))
      case BinaryOpF(BEq(), l, r, sp)  => Some(BinaryOpF(BNeq(), l, r, sp))
      case BinaryOpF(BNeq(), l, r, sp) => Some(BinaryOpF(BEq(), l, r, sp))
      case _                           => None

    private def isLenOrCardOf(e: expr_full): Option[String] = e match
      case UnaryOpF(UCardinality(), IdentifierF(name, _), _)           => Some(name)
      case CallF(IdentifierF("len", _), List(IdentifierF(name, _)), _) => Some(name)
      case _                                                           => None

    private def desiredSize(op: bin_op_full, n: Int): Option[Int] = op match
      case BGt() => Some(n + 1)
      case BGe() => Some(n)
      case BEq() => Some(n).filter(_ >= 0)
      case BLt() => Some(0).filter(_ < n)
      case BLe() => Some(0).filter(_ <= n)
      case _     => None

    private def collectionElementType(
        t: type_expr_full,
        ir: ServiceIRFull
    ): Option[type_expr_full] =
      collectionElementTypeIn(t, ir, Set.empty)

    private def collectionElementTypeIn(
        t: type_expr_full,
        ir: ServiceIRFull,
        seen: Set[String]
    ): Option[type_expr_full] = t match
      case SetTypeF(inner, _)    => Some(inner)
      case SeqTypeF(inner, _)    => Some(inner)
      case OptionTypeF(inner, _) => collectionElementTypeIn(inner, ir, seen)
      case NamedTypeF(name, _) if !seen.contains(name) =>
        ir.e
          .collectFirst { case _a: TypeAliasDeclFull if _a.a == name => _a }
          .flatMap(alias => collectionElementTypeIn(alias.b, ir, seen + name))
      case _ => None

    private def buildFillers(
        size: Int,
        inner: type_expr_full,
        ir: ServiceIRFull
    ): Option[List[String]] =
      if size == 0 then Some(Nil)
      else if AdminRouter.isNumericType(inner, ir, Set.empty) then
        Some((0 until size).map(_.toString).toList)
      else
        inner match
          case NamedTypeF("String", _) =>
            Some((0 until size).map(i => ExprToPython.pyString(s"x$i")).toList)
          case NamedTypeF("Bool", _) if size <= 2 =>
            Some(List("True", "False").take(size))
          case NamedTypeF(name, _) =>
            ir.d.collectFirst { case _e: EnumDeclFull if _e.a == name => _e } match
              case Some(e) if size <= e.b.size =>
                Some(e.b.take(size).map(ExprToPython.pyString))
              case _ => None
          case _ => None

    private def numericLiteralPy(e: expr_full): Option[String] = e match
      case IntLitF(int_of_integer(v), _) => Some(v.toString)
      case FloatLitF(v, _)               => Some(v.toString)
      case _                             => None

    private def literalForElementType(
        lit: expr_full,
        inner: type_expr_full,
        ir: ServiceIRFull
    ): Option[String] =
      val _ = inner
      lit match
        case StringLitF(s, _)              => Some(ExprToPython.pyString(s))
        case IntLitF(int_of_integer(v), _) => Some(v.toString)
        case FloatLitF(v, _)               => Some(v.toString)
        case BoolLitF(v, _)                => Some(if v then "True" else "False")
        case EnumAccessF(_, member, _)     => Some(ExprToPython.pyString(member))
        case IdentifierF(name, _) =>
          val enumNames = ir.d.collect { case _e: EnumDeclFull => _e.b }.flatten.toSet
          if enumNames.contains(name) then Some(ExprToPython.pyString(name))
          else None
        case _ => None

    private def literalValueFor(rhs: expr_full, ir: ServiceIRFull): Option[String] =
      rhs match
        case EnumAccessF(_, member, _) => Some(ExprToPython.pyString(member))
        case IdentifierF(name, _) =>
          val enumNames = ir.d.collect { case _e: EnumDeclFull => _e.b }.flatten.toSet
          if enumNames.contains(name) then Some(ExprToPython.pyString(name))
          else None
        case StringLitF(s, _)              => Some(ExprToPython.pyString(s))
        case IntLitF(int_of_integer(v), _) => Some(v.toString)
        case BoolLitF(v, _)                => Some(if v then "True" else "False")
        case FloatLitF(v, _)               => Some(v.toString)
        case _                             => None

    private def notNoneAnchorFor(f: FieldDeclFull, ir: ServiceIRFull): Option[String] =
      val inner = f.b match
        case OptionTypeF(t, _) => t
        case t                 => t
      if AdminRouter.isDateTimeType(inner, ir, Set.empty) then
        Some("datetime.datetime(2024, 1, 1).isoformat()")
      else if AdminRouter.isNumericType(inner, ir, Set.empty) then Some("0")
      else
        inner match
          case NamedTypeF("String", _) => Some(ExprToPython.pyString("x"))
          case NamedTypeF("Bool", _)   => Some("True")
          case NamedTypeF(name, _) =>
            ir.d.collectFirst { case _e: EnumDeclFull if _e.a == name => _e }.flatMap(
              _.b.headOption
            ).map(ExprToPython.pyString)
          case _ => None
