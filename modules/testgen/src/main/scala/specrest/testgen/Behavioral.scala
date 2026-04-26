package specrest.testgen

import specrest.convention.EndpointSpec
import specrest.convention.Naming
import specrest.ir.BinOp
import specrest.ir.Expr
import specrest.ir.InvariantDecl
import specrest.ir.OperationDecl
import specrest.ir.PrettyPrint
import specrest.ir.ServiceIR
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
    val collected = profiled.operations.flatMap: pop =>
      ir.operations.find(_.name == pop.operationName) match
        case Some(opDecl) => testsForOperation(pop, opDecl, ir)
        case None         => Nil
    val tests = collected.collect { case Right(t) => t }
    val skips = collected.collect { case Left(s) => s }
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
            reason = "M5.1: state-dependent precondition; deferred to M5.5+"
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

    if opDecl.requires.exists(containsStateRef(_, stateFields)) then Nil
    else
      val ctx = TestCtx(
        inputs = opDecl.inputs.map(_.name).toSet,
        outputs = opDecl.outputs.map(_.name).toSet,
        stateFields = stateFields,
        enumValues = ir.enums.map(e => e.name -> e.values.toSet).toMap,
        knownPredicates = TestCtx.DefaultPredicates,
        boundVars = Set.empty,
        capture = CaptureMode.PostState
      )

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
    sb.append(s"    response = ${requestCallExpr(pop, inputArgs.names)}\n")
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
    sb.append(s"    response = ${requestCallExpr(pop, inputArgs.names)}\n")
    sb.append(
      s"    assert response.status_code in (404, 409, 422), " +
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
    sb.append(s"    response = ${requestCallExpr(pop, inputArgs.names)}\n")
    sb.append(s"    assume(response.status_code == ${pop.endpoint.successStatus})\n")
    sb.append("    response_data = response.json() if response.content else {}\n")
    sb.append("    pre_state = client.get(\"/__test_admin__/state\").json()\n")
    sb.append("    post_state = pre_state\n")
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
      val pairs     = params.map(p => (p.name, Strategies.expressionFor(p.typeExpr, ir)))
      val firstSkip = pairs.collectFirst { case (n, StrategyExpr.Skip(r)) => s"input '$n': $r" }
      firstSkip match
        case Some(reason) => Left(reason)
        case None =>
          val codes = pairs.collect { case (n, StrategyExpr.Code(t)) => (n, t) }
          val sig   = codes.map(_._1).mkString(", ")
          val args  = codes.map((n, t) => s"$n=$t").mkString(", ")
          val gen   = s"@given($args)"
          Right(InputSig(names = codes.map(_._1), signature = sig, givenLine = gen))

  private def requestCallExpr(pop: ProfiledOperation, inputNames: List[String]): String =
    val ep              = pop.endpoint
    val method          = ep.method.toString.toLowerCase
    val pathParamNames  = ep.pathParams.map(_.name).toSet
    val bodyParamNames  = ep.bodyParams.map(_.name)
    val queryParamNames = ep.queryParams.map(_.name)
    val pathExpr        = pythonPathLiteral(ep, inputNames)
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
    val _ = pathParamNames
    s"client.$method($pathExpr$bodyExpr$queryExpr)"

  private def pythonPathLiteral(ep: EndpointSpec, inputNames: List[String]): String =
    val raw = ep.path
    if ep.pathParams.isEmpty then ExprToPython.pyString(raw)
    else
      val converted = ep.pathParams.foldLeft(raw): (acc, p) =>
        acc.replace(s"{${p.name}}", s"{${p.name}}")
      val _ = inputNames
      "f" + ExprToPython.pyString(converted)

  private def containsStateRef(e: Expr, stateFields: Set[String]): Boolean = e match
    case Expr.Identifier(n, _) => stateFields.contains(n)
    case Expr.Pre(_, _)        => true
    case Expr.Prime(_, _)      => true
    case Expr.BinaryOp(_, l, r, _) =>
      containsStateRef(l, stateFields) || containsStateRef(r, stateFields)
    case Expr.UnaryOp(_, x, _)     => containsStateRef(x, stateFields)
    case Expr.FieldAccess(b, _, _) => containsStateRef(b, stateFields)
    case Expr.EnumAccess(b, _, _)  => containsStateRef(b, stateFields)
    case Expr.Index(b, i, _)       => containsStateRef(b, stateFields) || containsStateRef(i, stateFields)
    case Expr.Call(c, args, _) =>
      containsStateRef(c, stateFields) || args.exists(containsStateRef(_, stateFields))
    case Expr.If(c, t, el, _) =>
      containsStateRef(c, stateFields) || containsStateRef(t, stateFields) || containsStateRef(
        el,
        stateFields
      )
    case Expr.Let(_, v, b, _) =>
      containsStateRef(v, stateFields) || containsStateRef(b, stateFields)
    case Expr.Quantifier(_, bs, b, _) =>
      bs.exists(qb => containsStateRef(qb.domain, stateFields)) || containsStateRef(b, stateFields)
    case Expr.SetLiteral(es, _) => es.exists(containsStateRef(_, stateFields))
    case Expr.SeqLiteral(es, _) => es.exists(containsStateRef(_, stateFields))
    case Expr.MapLiteral(es, _) =>
      es.exists(e => containsStateRef(e.key, stateFields) || containsStateRef(e.value, stateFields))
    case Expr.SetComprehension(_, d, p, _) =>
      containsStateRef(d, stateFields) || containsStateRef(p, stateFields)
    case Expr.SomeWrap(x, _) => containsStateRef(x, stateFields)
    case Expr.The(_, d, b, _) =>
      containsStateRef(d, stateFields) || containsStateRef(b, stateFields)
    case Expr.With(b, ups, _) =>
      containsStateRef(b, stateFields) || ups.exists(u => containsStateRef(u.value, stateFields))
    case Expr.Constructor(_, fs, _) => fs.exists(f => containsStateRef(f.value, stateFields))
    case Expr.Lambda(_, b, _)       => containsStateRef(b, stateFields)
    case Expr.Matches(x, _, _)      => containsStateRef(x, stateFields)
    case Expr.IntLit(_, _) | Expr.FloatLit(_, _) | Expr.StringLit(_, _)
        | Expr.BoolLit(_, _) | Expr.NoneLit(_) => false

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
