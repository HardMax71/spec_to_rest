package specrest.testgen

import specrest.codegen.AdminModel
import specrest.ir.HttpMethods
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

@SuppressWarnings(Array("org.wartremover.warts.Return", "org.wartremover.warts.OptionPartial"))
private[testgen] object StateMachineTests:

  final case class TransitionEmissionResult(
      results: List[Either[TestSkip, GeneratedTest]],
      coveredOps: Set[String]
  )

  def transitionEmission(
      profiled: ProfiledService,
      ir: ServiceIRFull
  ): TransitionEmissionResult =
    svcTransitions(ir).foldLeft(
      TransitionEmissionResult(Nil, Set.empty)
    ): (acc, td) =>
      val per = transitionTestsForTd(td, profiled, ir)
      TransitionEmissionResult(acc.results ++ per.results, acc.coveredOps ++ per.coveredOps)

  // Entity invariants the seeded row must satisfy, or every guarded call on
  // the seeded state 409s (requires begins with the state invariant). Drawn
  // rows repair in place: strict lower bounds lift (ids are app-assigned,
  // so they stay in the payload), length bounds clamp, an implication tied
  // to the forced enum field sets its consequent, and a two-field ordering
  // copies the bound.
  private def invariantRepairLines(entity: entity_decl): List[String] =
    val atoms          = entInvariants(entity).flatMap(inv => flattenEnsures(List(inv)))
    val fieldTypes     = entFields(entity).map(f => fldName(f) -> fldType(f)).toMap
    def key(f: String) = ExprToPython.pyString(f)
    // Entity invariants reference fields as bare identifiers; an identifier
    // that is not a field is an enum member (DONE in status = DONE).
    def fieldOf(e: expr): Option[String] = e match
      case FieldAccessF(IdentifierF(_, _), f, _)       => Some(f)
      case IdentifierF(n, _) if fieldTypes.contains(n) => Some(n)
      case _                                           => None
    def enumLit(e: expr): Option[String] = e match
      case EnumAccessF(_, m, _)                         => Some(m)
      case IdentifierF(m, _) if !fieldTypes.contains(m) => Some(m)
      case StringLitF(m, _)                             => Some(m)
      case _                                            => None
    def fillFor(f: String): String = fieldTypes.get(f) match
      case Some(NamedTypeF("DateTime", _)) | Some(OptionTypeF(NamedTypeF("DateTime", _), _)) =>
        "\"2026-01-01T00:00:00Z\""
      case Some(NamedTypeF("Int", _)) | Some(OptionTypeF(NamedTypeF("Int", _), _)) => "1"
      case _                                                                       => "\"x\""
    val repairs = atoms.flatMap {
      case BinaryOpF(
            BImplies(),
            BinaryOpF(BEq(), lhs, rhs, _),
            BinaryOpF(BNeq(), c, NoneLitF(_), _),
            _
          ) =>
        (fieldOf(lhs), enumLit(rhs), fieldOf(c)) match
          case (Some(sf), Some(lit), Some(cf)) =>
            List(
              s"if row[${key(sf)}] == ${ExprToPython.pyString(lit)} and row.get(${key(cf)}) is None:",
              s"    row[${key(cf)}] = ${fillFor(cf)}"
            )
          case _ => Nil
      case BinaryOpF(
            BImplies(),
            BinaryOpF(BNeq(), lhs, rhs, _),
            BinaryOpF(BEq(), c, NoneLitF(_), _),
            _
          ) =>
        (fieldOf(lhs), enumLit(rhs), fieldOf(c)) match
          case (Some(sf), Some(lit), Some(cf)) =>
            List(
              s"if row[${key(sf)}] != ${ExprToPython.pyString(lit)}:",
              s"    row[${key(cf)}] = None"
            )
          case _ => Nil
      case BinaryOpF(BGt(), a, IntLitF(n, _), _) =>
        fieldOf(a).toList.flatMap: f =>
          List(
            s"if row[${key(f)}] <= $n:",
            s"    row[${key(f)}] = $n + 1"
          )
      case BinaryOpF(BGe(), CallF(IdentifierF("len", _), List(arg), _), IntLitF(n, _), _) =>
        fieldOf(arg).toList.flatMap: f =>
          List(
            s"if len(row[${key(f)}]) < $n:",
            s"    row[${key(f)}] = ${ExprToPython.pyString("x")} * $n"
          )
      case BinaryOpF(BGe(), a, b, _) =>
        // Lower the bound side: the guard-fix lines may have pinned the
        // other side (updated_at = completed_at + 1s), and lowering a bound
        // cannot un-pin anything.
        (fieldOf(a), fieldOf(b)) match
          case (Some(fa), Some(fb)) =>
            List(
              s"if row[${key(fa)}] < row[${key(fb)}]:",
              s"    row[${key(fb)}] = row[${key(fa)}]"
            )
          case _ => Nil
      case BinaryOpF(BLe(), CallF(IdentifierF("len", _), List(arg), _), IntLitF(n, _), _) =>
        fieldOf(arg).toList.flatMap: f =>
          List(s"row[${key(f)}] = row[${key(f)}][:$n]")
      case _ => Nil
    }
    repairs

  private def transitionTestsForTd(
      td: transition_decl,
      profiled: ProfiledService,
      ir: ServiceIRFull
  ): TransitionEmissionResult =
    val entityOpt =
      entityByName(svcEntities(ir), trnEntity(td))
    if entityOpt.isEmpty then
      return TransitionEmissionResult(
        List(Left(TestSkip(trnName(td), "transition", s"unknown entity '${trnEntity(td)}'"))),
        Set.empty
      )
    val entity = entityOpt.get
    val fieldOpt =
      findFieldDeclFull(entFields(entity), trnField(td))
    if fieldOpt.isEmpty then
      return TransitionEmissionResult(
        List(
          Left(
            TestSkip(
              trnName(td),
              "transition",
              s"entity '${entName(entity)}' has no field '${trnField(td)}'"
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
              trnName(td),
              "transition",
              s"transition field '${trnField(td)}' is not an enum (or alias of enum); illegal-from enumeration undefined"
            )
          )
        ),
        Set.empty
      )
    val pkOpt = AdminModel.primaryKeyField(entity)
    if pkOpt.isEmpty then
      return TransitionEmissionResult(
        List(
          Left(
            TestSkip(
              trnName(td),
              "transition",
              s"entity '${entName(entity)}' has no field; cannot seed"
            )
          )
        ),
        Set.empty
      )
    val statusValues = enumValuesOpt.get
    val pk           = pkOpt.get
    val byVia        = trnRules(td).groupBy(trlVia)
    byVia.toList.sortBy(_._1).foldLeft(TransitionEmissionResult(Nil, Set.empty)): (acc, kv) =>
      val (viaName, rules) = kv
      val opDeclOpt        = svcOperations(ir).find(o => operName(o) == viaName)
      val popOpt           = profiled.operations.find(_.operationName == viaName)
      val per: TransitionEmissionResult = (opDeclOpt, popOpt) match
        case (Some(_), Some(pop)) if StubOps.isStub(profiled, pop) =>
          TransitionEmissionResult(
            List(Left(TestSkip(viaName, s"transition[$viaName]", StubOps.skipReason(pop)))),
            Set.empty
          )
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
              val legalFroms   = rules.map(trlFrom).toSet
              val illegalFroms = statusValues.filterNot(legalFroms.contains)
              val stateField = stateFieldForEntity(trnEntity(td), ir)
                .getOrElse(Naming.toSnakeCase(trnEntity(td)) + "s")
              val positives = rules.toList.map: rule =>
                buildTransitionPositiveOrSkip(
                  td = td,
                  entity = entity,
                  fieldName = trnField(td),
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
                  fieldName = trnField(td),
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
      field: field_decl,
      ir: ServiceIRFull
  ): Option[List[String]] =
    SpecRestGenerated.enumValuesForField(field, svcEnums(ir), svcTypeAliases(ir))

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
      opDecl: operation_decl,
      pop: ProfiledOperation,
      ir: ServiceIRFull
  ): Either[(String, String), List[NonPathInput]] =
    val overrides = TestStrategyOverrides.from(ir)
    val tagged =
      pop.endpoint.bodyParams.map(p => (p, NonPathKind.Body)) ++
        pop.endpoint.queryParams.map(p => (p, NonPathKind.Query))
    val resolved = tagged.map: (p, k) =>
      val ctx  = StrategyCtx.OperationInput(operName(opDecl), p.name)
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
      td: transition_decl,
      entity: entity_decl,
      fieldName: String,
      pkName: String,
      rule: transition_rule,
      opDecl: operation_decl,
      pop: ProfiledOperation,
      stateField: String,
      ir: ServiceIRFull,
      nonPath: List[NonPathInput]
  ): Either[TestSkip, GeneratedTest] =
    val fixLines = trlGuard(rule) match
      case None        => Some(Nil)
      case Some(guard) => GuardSatisfier.recognize(guard, entity, fieldName, trlFrom(rule), ir)
    fixLines match
      case None =>
        Left(
          TestSkip(
            operName(opDecl),
            s"transition[${trlFrom(rule)}_to_${trlTo(rule)}]",
            s"guard '${TestFormat.prettyOneLine(trlGuard(rule).get)}' uses constructs the seed-dict recognizer does not cover; see docs 'Guarded positive transitions' for the supported shapes"
          )
        )
      case Some(lines) =>
        Right(
          buildTransitionPositive(
            td = td,
            entity = entity,
            fieldName = fieldName,
            pkName = pkName,
            from = trlFrom(rule),
            to = trlTo(rule),
            opDecl = opDecl,
            pop = pop,
            stateField = stateField,
            guardFixLines = lines,
            nonPath = nonPath
          )
        )

  private def buildTransitionPositive(
      td: transition_decl,
      entity: entity_decl,
      fieldName: String,
      pkName: String,
      from: String,
      to: String,
      opDecl: operation_decl,
      pop: ProfiledOperation,
      stateField: String,
      guardFixLines: List[String],
      nonPath: List[NonPathInput]
  ): GeneratedTest =
    val opSnake     = Naming.toSnakeCase(operName(opDecl))
    val entitySnake = Naming.toSnakeCase(entName(entity))
    val testName    = s"test_${opSnake}_transition_${from.toLowerCase}_to_${to.toLowerCase}"
    val rowStrategy = Strategies.strategyFunctionName(entName(entity))
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
      s"    \"\"\"transition ${operName(opDecl)}: $from -> $to (post-state ${trnField(td)} = $to)\"\"\"\n"
    )
    sb.append("    client.post(\"/admin/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = ${ExprToPython.pyString(from)}\n")
    guardFixLines.foreach: line =>
      sb.append(s"    $line\n")
    invariantRepairLines(entity).foreach: line =>
      sb.append(s"    $line\n")
    sb.append(s"    seed = client.post(\"/admin/seed/$entitySnake\", json=row)\n")
    sb.append("    assume(seed.status_code == 201)\n")
    sb.append(s"    seeded_id = seed.json()[$pkKey]\n")
    sb.append(transitionRequestCall(pop, nonPath))
    sb.append(s"    assert response.status_code == ${pop.endpoint.successStatus}, response.text\n")
    sb.append("    post_state = state_snapshot(_INT_KEYED_STATE)\n")
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
        s"f\"expected ${trnField(td)}=$to, got {actual!r}\"\n"
    )
    GeneratedTest(name = testName, body = sb.toString, skipReason = None)

  private def buildTransitionNegative(
      entity: entity_decl,
      fieldName: String,
      pkName: String,
      from: String,
      opDecl: operation_decl,
      pop: ProfiledOperation,
      nonPath: List[NonPathInput]
  ): Either[TestSkip, GeneratedTest] =
    val opSnake     = Naming.toSnakeCase(operName(opDecl))
    val entitySnake = Naming.toSnakeCase(entName(entity))
    val testName    = s"test_${opSnake}_transition_illegal_from_${from.toLowerCase}"
    val rowStrategy = Strategies.strategyFunctionName(entName(entity))
    val pkKey       = ExprToPython.pyString(pkName)
    val fieldKey    = ExprToPython.pyString(fieldName)
    val sb          = new StringBuilder
    sb.append(givenLineFor(rowStrategy, nonPath))
    sb.append(
      "@settings(max_examples=10, suppress_health_check=[HealthCheck.function_scoped_fixture])\n"
    )
    sb.append(s"def $testName(${signatureFor(nonPath)}):\n")
    sb.append(
      s"    \"\"\"transition ${operName(opDecl)}: from=$from is illegal (no rule); SUT must reject 4xx\"\"\"\n"
    )
    sb.append("    client.post(\"/admin/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = ${ExprToPython.pyString(from)}\n")
    invariantRepairLines(entity).foreach: line =>
      sb.append(s"    $line\n")
    sb.append(s"    seed = client.post(\"/admin/seed/$entitySnake\", json=row)\n")
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
    val method = HttpMethods.lower(ep.method)
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
    irStateFields(ir)
      .find(f => relationTargetsEntity(stfType(f), entityName))
      .map(stfName)

  final case class StatusRestriction(
      inputName: String,
      stateName: String,
      entityName: String,
      fieldName: String,
      requiredValue: String,
      enumValues: List[String],
      pkField: String
  )

  def statusRestrictionPattern(
      e: expr,
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
          entity     <- entityByName(svcEntities(ir), entityName)
          if Strategies.transitionEntityNames(ir).contains(entityName)
          fieldDecl <- findFieldDeclFull(entFields(entity), field)
          enumVals  <- enumValuesForField(fieldDecl, ir)
          if enumVals.nonEmpty
          rhsLit <- enumLiteralOf(rhs, enumVals)
          pk     <- AdminModel.primaryKeyField(entity)
          if enumVals.size >= 2
        yield StatusRestriction(inName, stName, entityName, field, rhsLit, enumVals, pk)
      case _ => None

  private def entityForStateField(stateFieldName: String, ir: ServiceIRFull): Option[String] =
    irStateFields(ir)
      .find(f => stfName(f) == stateFieldName)
      .map(stfType)
      .flatMap(relationTargetEntityName)

  def statusRestrictionNegativeOrSkip(
      opDecl: operation_decl,
      pop: ProfiledOperation,
      ir: ServiceIRFull,
      restriction: StatusRestriction,
      idx: Int
  ): Option[Either[TestSkip, GeneratedTest]] =
    if pop.endpoint.pathParams.size != 1 then
      Some(
        Left(
          TestSkip(
            operName(opDecl),
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
                operName(opDecl),
                s"requires[$idx]",
                s"status-restriction negative needs a generable strategy for input '$paramName': $reason"
              )
            )
          )
        case Right(nonPath) =>
          Some(Right(buildStatusRestrictionNegative(opDecl, pop, restriction, nonPath, ir)))

  private def buildStatusRestrictionNegative(
      opDecl: operation_decl,
      pop: ProfiledOperation,
      r: StatusRestriction,
      nonPath: List[NonPathInput],
      ir: ServiceIRFull
  ): GeneratedTest =
    val opSnake     = Naming.toSnakeCase(operName(opDecl))
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
    sb.append("    client.post(\"/admin/reset\")\n")
    sb.append("    row = dict(row)\n")
    sb.append(s"    row[$fieldKey] = wrong_status\n")
    entityByName(svcEntities(ir), r.entityName).foreach: ent =>
      invariantRepairLines(ent).foreach: line =>
        sb.append(s"    $line\n")
    sb.append(s"    seed = client.post(\"/admin/seed/$entitySnake\", json=row)\n")
    sb.append("    assume(seed.status_code == 201)\n")
    sb.append(s"    seeded_id = seed.json()[$pkKey]\n")
    sb.append(transitionRequestCall(pop, nonPath))
    sb.append(
      "    assert 400 <= response.status_code < 500, " +
        s"f${'"'}expected 4xx, got {response.status_code}: {response.text}${'"'}\n"
    )
    GeneratedTest(name = testName, body = sb.toString, skipReason = None)
