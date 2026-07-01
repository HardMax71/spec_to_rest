package specrest.testgen

import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated
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
    val transition       = StateMachineTests.transitionEmission(profiled, ir)
    val coveredByTransit = transition.coveredOps
    val perOp = profiled.operations.flatMap: pop =>
      if StubOps.isStub(profiled, pop) then
        List(Left(TestSkip(pop.operationName, "operation", StubOps.skipReason(pop))))
      else if pop.requiresAuth.nonEmpty then
        List(Left(TestSkip(pop.operationName, "operation", StubOps.authSkipReason(pop))))
      else
        svcOperations(ir).find(o => operName(o) == pop.operationName) match
          case Some(opDecl) => testsForOperation(pop, opDecl, ir, coveredByTransit)
          case None         => Nil
    val collected = perOp ++ transition.results
    val tests     = collected.collect { case Right(t) => t }
    val skips     = collected.collect { case Left(s) => s }
    BehavioralOutput(tests = tests, skips = skips)

  private def testsForOperation(
      pop: ProfiledOperation,
      opDecl: operation_decl,
      ir: ServiceIRFull,
      coveredByTransit: Set[String]
  ): List[Either[TestSkip, GeneratedTest]] =
    val ensures   = ensuresTests(pop, opDecl, ir, coveredByTransit)
    val negatives = negativeTests(pop, opDecl, ir)
    val invs      = invariantTests(pop, opDecl, ir, coveredByTransit)
    val temps     = temporalTests(pop, opDecl, ir, coveredByTransit)
    ensures ++ negatives ++ invs ++ temps

  private[testgen] def stateDepSkipReason(opName: String, coveredByTransit: Set[String]): String =
    if coveredByTransit.contains(opName) then
      "state-dependent precondition; covered by transition tests (M5.9)"
    else
      "state-dependent precondition; positive ensures/invariant tests are covered by stateful tests (M5.2) for ops bundled into the state machine; the single-shot behavioral test would need explicit pre-seeding"

  private[testgen] val aggregateEqualitySkipReason: String =
    "ensures equates a collection output to a set-builder over state; the HTTP black-box " +
      "cannot faithfully assert aggregate equality (set vs list ordering, admin-state " +
      "row shape vs read-schema shape, unhashable row dicts). Aggregate behavior is " +
      "covered by the stateful and structural layers."

  // `<collectionOutput> = { x in dom | pred }` (either orientation) cannot be checked by
  // comparing the JSON response to a Python set built from the admin-state projection —
  // the element shapes and container types differ irreconcilably black-box. Skip honestly
  // rather than emit an assertion that can never hold.
  private[testgen] def isAggregateEqualityOverState(
      clause: expr,
      opDecl: operation_decl
  ): Boolean =
    val collOutputs = operOutputs(opDecl)
      .filter(p => isCollectionType(prmType(p)))
      .map(prmName)
      .toSet
    def refsCollOutput(e: expr): Boolean = e match
      case IdentifierF(n, _) => collOutputs.contains(n)
      case PrimeF(inner, _)  => refsCollOutput(inner)
      case PreF(inner, _)    => refsCollOutput(inner)
      case _                 => false
    def isComprehension(e: expr): Boolean = e match
      case _: SetComprehensionF => true
      case _                    => false
    clause match
      case BinaryOpF(BEq(), l, r, _) =>
        (refsCollOutput(l) && isComprehension(r)) ||
        (refsCollOutput(r) && isComprehension(l))
      case _ => false

  private def ensuresTests(
      pop: ProfiledOperation,
      opDecl: operation_decl,
      ir: ServiceIRFull,
      coveredByTransit: Set[String]
  ): List[Either[TestSkip, GeneratedTest]] =
    val stateFields = irStateFieldNames(ir).toSet
    val opSnake     = Naming.toSnakeCase(operName(opDecl))

    val requiresHasStateRef =
      operRequires(opDecl).exists(e => hasPrePrime(e) || free_vars(e).exists(stateFields.contains))
    val nonTrivialRequires = operRequires(opDecl).exists(!isTrueLit(_))

    if requiresHasStateRef then
      List(
        Left(
          TestSkip(
            operation = operName(opDecl),
            kind = "ensures",
            reason = stateDepSkipReason(operName(opDecl), coveredByTransit)
          )
        )
      )
    else
      val inputArgs = inputArgList(pop, ir)
      inputArgs match
        case Left(reason) =>
          List(Left(TestSkip(operName(opDecl), "ensures", reason)))
        case Right(strategySig) =>
          val ctx =
            TestCtx.fromOperation(
              opDecl,
              ir,
              CaptureMode.PreState,
              StubOps.bareBodyOutput(pop, opDecl)
            )
          operEnsures(opDecl).zipWithIndex.map: (clause, idx) =>
            if isAggregateEqualityOverState(clause, opDecl) then
              Left(TestSkip(operName(opDecl), s"ensures[$idx]", aggregateEqualitySkipReason))
            else
              ExprToPython.translate(clause, ctx) match
                case Translated.Skip(reason, _) =>
                  Left(TestSkip(operName(opDecl), s"ensures[$idx]", reason))
                case Translated.Emit(text) =>
                  Right(
                    buildPositiveTest(
                      name = s"test_${opSnake}_ensures_$idx",
                      docstring = s"ensures: ${TestFormat.prettyOneLine(clause)}",
                      inputArgs = strategySig,
                      pop = pop,
                      assertion = text,
                      nonTrivialRequires = nonTrivialRequires
                    )
                  )

  private def negativeTests(
      pop: ProfiledOperation,
      opDecl: operation_decl,
      ir: ServiceIRFull
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake     = Naming.toSnakeCase(operName(opDecl))
    val inputs      = operInputs(opDecl).map(prmName).toSet
    val stateFields = irStateFieldNames(ir).toSet

    operRequires(opDecl).zipWithIndex.flatMap: (req, idx) =>
      keyExistencePair(req).filter((in, st) =>
        inputs.contains(in) && stateFields.contains(st)
      ) match
        case Some((inputName, stateName)) =>
          inputArgList(pop, ir) match
            case Left(reason) =>
              List(Left(TestSkip(operName(opDecl), s"requires[$idx]", reason)))
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
          StateMachineTests.statusRestrictionPattern(req, inputs, stateFields, ir) match
            case Some(restriction) =>
              StateMachineTests.statusRestrictionNegativeOrSkip(opDecl, pop, ir, restriction, idx).toList
            case None =>
              if isTrueLit(req) then Nil
              else
                List(
                  Left(
                    TestSkip(
                      operName(opDecl),
                      s"requires[$idx]",
                      "M5.1: only `<input> in <state>` and `<state>[input].<enum-field> = <value>` requires patterns get negative tests"
                    )
                  )
                )

  private def invariantTests(
      pop: ProfiledOperation,
      opDecl: operation_decl,
      ir: ServiceIRFull,
      coveredByTransit: Set[String]
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake     = Naming.toSnakeCase(operName(opDecl))
    val stateFields = irStateFieldNames(ir).toSet

    if operRequires(opDecl).exists(e => hasPrePrime(e) || free_vars(e).exists(stateFields.contains))
    then
      svcInvariants(ir).zipWithIndex.toList.map: (inv, idx) =>
        Left(
          TestSkip(
            operName(opDecl),
            s"invariant[${TestFormat.invName(inv, idx)}]",
            stateDepSkipReason(operName(opDecl), coveredByTransit)
          )
        )
    else
      val ctx =
        TestCtx.fromOperation(
          opDecl,
          ir,
          CaptureMode.PostState,
          StubOps.bareBodyOutput(pop, opDecl)
        )
      inputArgList(pop, ir) match
        case Left(reason) =>
          List(Left(TestSkip(operName(opDecl), "invariant_inputs", reason)))
        case Right(strategySig) =>
          svcInvariants(ir).zipWithIndex.toList.map: (inv, idx) =>
            ExprToPython.translate(invBody(inv), ctx) match
              case Translated.Skip(reason, _) =>
                Left(TestSkip(
                  operName(opDecl),
                  s"invariant[${TestFormat.invName(inv, idx)}]",
                  reason
                ))
              case Translated.Emit(text) =>
                Right(
                  buildInvariantTest(
                    name =
                      s"test_${opSnake}_invariant_${Naming.toSnakeCase(TestFormat.invName(inv, idx))}",
                    docstring =
                      s"invariant ${TestFormat.invName(inv, idx)}: ${TestFormat.prettyOneLine(invBody(inv))}",
                    inputArgs = strategySig,
                    pop = pop,
                    assertion = text
                  )
                )

  private def temporalTests(
      pop: ProfiledOperation,
      opDecl: operation_decl,
      ir: ServiceIRFull,
      coveredByTransit: Set[String]
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake     = Naming.toSnakeCase(operName(opDecl))
    val stateFields = irStateFieldNames(ir).toSet
    val temporals   = svcTemporals(ir)
    if temporals.isEmpty then Nil
    else if operRequires(opDecl).exists(e =>
        hasPrePrime(e) || free_vars(e).exists(stateFields.contains)
      )
    then
      temporals.toList.map: t =>
        Left(
          TestSkip(
            operName(opDecl),
            s"temporal[${tmpName(t)}]",
            stateDepSkipReason(operName(opDecl), coveredByTransit)
          )
        )
    else
      val ctx =
        TestCtx.fromOperation(
          opDecl,
          ir,
          CaptureMode.PostState,
          StubOps.bareBodyOutput(pop, opDecl)
        )
      inputArgList(pop, ir) match
        case Left(reason) =>
          List(Left(TestSkip(operName(opDecl), "temporal_inputs", reason)))
        case Right(strategySig) =>
          temporals.toList.flatMap: t =>
            tmpBody(t) match
              case TbAlways(arg) =>
                ExprToPython.translate(arg, ctx) match
                  case Translated.Skip(reason, _) =>
                    List(Left(TestSkip(operName(opDecl), s"temporal[${tmpName(t)}]", reason)))
                  case Translated.Emit(text) =>
                    List(
                      Right(
                        buildInvariantTest(
                          name =
                            s"test_${opSnake}_temporal_always_${Naming.toSnakeCase(tmpName(t))}",
                          docstring =
                            s"temporal always(${tmpName(t)}): ${TestFormat.prettyOneLine(arg)}",
                          inputArgs = strategySig,
                          pop = pop,
                          assertion = text
                        )
                      )
                    )
              case TbEventually(_) =>
                List(
                  Left(
                    TestSkip(
                      operName(opDecl),
                      s"temporal[${tmpName(t)}]",
                      "single-shot tests cannot observe liveness; covered by stateful temporal observer"
                    )
                  )
                )
              case TbFairness(_) =>
                List(
                  Left(
                    TestSkip(
                      operName(opDecl),
                      s"temporal[${tmpName(t)}]",
                      "fairness(op) is not supported in v1 (verifier rejects it; see #86)"
                    )
                  )
                )
              case TbInvalid(_) =>
                List(
                  Left(
                    TestSkip(
                      operName(opDecl),
                      s"temporal[${tmpName(t)}]",
                      "only always(P), eventually(P), fairness(op) are recognized"
                    )
                  )
                )

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
    sb.append(s"    \"\"\"${TestFormat.escapeDocstring(docstring)}\"\"\"\n")
    sb.append("    client.post(\"/admin/reset\")\n")
    sb.append("    pre_state = client.get(\"/admin/state\").json()\n")
    sb.append(s"    response = ${TestFormat.requestCallExpr(pop)}\n")
    if nonTrivialRequires then
      sb.append(s"    assume(response.status_code == ${pop.endpoint.successStatus})\n")
    sb.append(s"    assert response.status_code == ${pop.endpoint.successStatus}, response.text\n")
    sb.append("    response_data = response.json() if response.content else {}\n")
    sb.append("    post_state = client.get(\"/admin/state\").json()\n")
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
    sb.append("    client.post(\"/admin/reset\")\n")
    sb.append("    pre_state = client.get(\"/admin/state\").json()\n")
    sb.append(
      s"    assume($inputName not in pre_state.get(${ExprToPython.pyString(stateName)}, {}))\n"
    )
    sb.append(s"    response = ${TestFormat.requestCallExpr(pop)}\n")
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
    sb.append(s"    \"\"\"${TestFormat.escapeDocstring(docstring)}\"\"\"\n")
    sb.append("    client.post(\"/admin/reset\")\n")
    sb.append("    pre_state = client.get(\"/admin/state\").json()\n")
    sb.append(s"    response = ${TestFormat.requestCallExpr(pop)}\n")
    sb.append(s"    assume(response.status_code == ${pop.endpoint.successStatus})\n")
    sb.append("    response_data = response.json() if response.content else {}\n")
    sb.append("    post_state = client.get(\"/admin/state\").json()\n")
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
