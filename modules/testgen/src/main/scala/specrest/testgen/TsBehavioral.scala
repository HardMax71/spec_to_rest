package specrest.testgen

import specrest.convention.Naming
import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated.FunctionDeclFull
import specrest.ir.generated.SpecRestGenerated.OperationDeclFull
import specrest.ir.generated.SpecRestGenerated.PredicateDeclFull
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.StateDeclFull
import specrest.ir.generated.SpecRestGenerated.StateFieldDeclFull
import specrest.ir.generated.SpecRestGenerated.free_vars
import specrest.ir.generated.SpecRestGenerated.hasPrePrime
import specrest.ir.generated.SpecRestGenerated.isTrueLit
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

// Native TypeScript (vitest + fast-check) behavioral emitter. Mirrors the
// positive-ensures decision of the Python `Behavioral` (reusing its shared
// predicates), and honestly skips the kinds not yet ported (negative/invariant/
// temporal/transition) rather than emitting broken hybrids. Python `Behavioral`
// stays the byte-identical oracle; this is selected only for the `express`
// target once the full TS path is wired (slice 5c).
object TsBehavioral:

  def emitFor(profiled: ProfiledService): BehavioralOutput =
    val ir = profiled.ir
    val collected = profiled.operations.flatMap: pop =>
      if StubOps.isStub(profiled, pop) then
        List(Left(TestSkip(pop.operationName, "operation", StubOps.skipReason(pop))))
      else
        ir.g.collectFirst {
          case o: OperationDeclFull if o.a == pop.operationName => o
        } match
          case Some(opDecl) =>
            ensuresTs(pop, opDecl, ir) :+
              Left(
                TestSkip(
                  opDecl.a,
                  "behavioral_ts_pending",
                  "negative/invariant/temporal/transition behavioral tests are not " +
                    "yet emitted by the TypeScript backend (#174); these remain " +
                    "covered by the Python oracle suite"
                )
              )
          case None => Nil
    BehavioralOutput(
      tests = collected.collect { case Right(t) => t },
      skips = collected.collect { case Left(s) => s }
    )

  private def ensuresTs(
      pop: ProfiledOperation,
      opDecl: OperationDeclFull,
      ir: ServiceIRFull
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake = Naming.toSnakeCase(opDecl.a)
    val stateFields = ir.f.toList.flatMap { case StateDeclFull(fs, _) =>
      fs.collect { case StateFieldDeclFull(n, _, _) => n }
    }.toSet
    val requiresHasStateRef =
      opDecl.d.exists(e => hasPrePrime(e) || free_vars(e).exists(stateFields.contains))
    val nonTrivialRequires = opDecl.d.exists(!isTrueLit(_))

    if requiresHasStateRef then
      List(Left(TestSkip(opDecl.a, "ensures", Behavioral.stateDepSkipReason(opDecl.a, Set.empty))))
    else
      tsInputArbs(pop, ir) match
        case Left(reason) => List(Left(TestSkip(opDecl.a, "ensures", reason)))
        case Right(arbs) =>
          val ctx = TestCtx.fromOperation(
            opDecl,
            ir,
            CaptureMode.PreState,
            StubOps.bareBodyOutput(pop, opDecl)
          )
          opDecl.e.zipWithIndex.map: (clause, idx) =>
            if Behavioral.isAggregateEqualityOverState(clause, opDecl) then
              Left(TestSkip(opDecl.a, s"ensures[$idx]", Behavioral.aggregateEqualitySkipReason))
            else
              TsExprBackend.translate(clause, ctx) match
                case Translated.Skip(reason, _) =>
                  Left(TestSkip(opDecl.a, s"ensures[$idx]", reason))
                case Translated.Emit(text) =>
                  Right(
                    buildPositiveTest(
                      name = s"test_${opSnake}_ensures_$idx",
                      docstring = s"ensures: ${prettyOneLine(clause)}",
                      arbs = arbs,
                      pop = pop,
                      assertion = text,
                      nonTrivialRequires = nonTrivialRequires
                    )
                  )

  private def tsInputArbs(
      pop: ProfiledOperation,
      ir: ServiceIRFull
  ): Either[String, List[(String, String)]] =
    val ep     = pop.endpoint
    val params = ep.pathParams ++ ep.bodyParams ++ ep.queryParams
    if params.isEmpty then Right(Nil)
    else
      val overrides = TestStrategyOverrides.from(ir)
      val pairs = params.map: p =>
        val sctx = StrategyCtx.OperationInput(pop.operationName, p.name)
        (p.name, Strategies.expressionFor(p.typeExpr, ir, sctx, overrides, TsFastCheckStrategy))
      pairs.collectFirst { case (n, StrategyExpr.Skip(r)) => s"input '$n': $r" } match
        case Some(reason) => Left(reason)
        case None         => Right(pairs.collect { case (n, StrategyExpr.Code(t)) => (n, t) })

  private def tsRequestCall(pop: ProfiledOperation): String =
    val ep      = pop.endpoint
    val method  = ep.method.toString.toLowerCase
    val hasPath = ep.pathParams.nonEmpty
    val rawPath = ep.path.replaceAll("\\{([^}]+)\\}", "\\$\\{$1\\}")
    val pathExpr =
      if hasPath then s"`$rawPath`" else TsLit.str(ep.path)
    val bodyExpr =
      if ep.bodyParams.isEmpty then ""
      else
        val pairs = ep.bodyParams.map(p => s"${TsLit.str(p.name)}: ${p.name}").mkString(", ")
        s", { $pairs }"
    s"client.$method($pathExpr$bodyExpr)"

  private def buildPositiveTest(
      name: String,
      docstring: String,
      arbs: List[(String, String)],
      pop: ProfiledOperation,
      assertion: String,
      nonTrivialRequires: Boolean
  ): GeneratedTest =
    val ok   = pop.endpoint.successStatus
    val call = tsRequestCall(pop)
    val viol = TsLit.str(s"ensures violated: $docstring")
    val sb   = new StringBuilder
    if arbs.nonEmpty then
      val arbList   = arbs.map(_._2).mkString(", ")
      val paramList = arbs.map(_._1).mkString(", ")
      sb.append(s"test(${TsLit.str(name)}, async () => {\n")
      sb.append("  await fc.assert(\n")
      sb.append(s"    fc.asyncProperty($arbList, async ($paramList) => {\n")
      sb.append("      await client.post(\"/__test_admin__/reset\");\n")
      sb.append(
        "      const preState = (await (await client.get(\"/__test_admin__/state\"))" +
          ".json()) as any;\n"
      )
      sb.append(s"      const response = await $call;\n")
      if nonTrivialRequires then sb.append(s"      fc.pre(response.status === $ok);\n")
      sb.append(s"      expect(response.status).toBe($ok);\n")
      sb.append("      const responseData = (await response.json()) as any;\n")
      sb.append(
        "      const postState = (await (await client.get(\"/__test_admin__/state\"))" +
          ".json()) as any;\n"
      )
      sb.append(s"      if (!($assertion)) { throw new Error($viol); }\n")
      sb.append("    }),\n")
      sb.append("    { numRuns: NUM_RUNS },\n")
      sb.append("  );\n")
      sb.append("});\n")
    else
      sb.append(s"test(${TsLit.str(name)}, async () => {\n")
      sb.append("  await client.post(\"/__test_admin__/reset\");\n")
      sb.append(
        "  const preState = (await (await client.get(\"/__test_admin__/state\"))" +
          ".json()) as any;\n"
      )
      sb.append(s"  const response = await $call;\n")
      if nonTrivialRequires then
        sb.append(s"  if (response.status !== $ok) { return; }\n")
      sb.append(s"  expect(response.status).toBe($ok);\n")
      sb.append("  const responseData = (await response.json()) as any;\n")
      sb.append(
        "  const postState = (await (await client.get(\"/__test_admin__/state\"))" +
          ".json()) as any;\n"
      )
      sb.append(s"  if (!($assertion)) { throw new Error($viol); }\n")
      sb.append("});\n")
    GeneratedTest(name = name, body = sb.toString, skipReason = None)

  private def prettyOneLine(e: specrest.ir.generated.SpecRestGenerated.expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private val RuntimeHelpers =
    List("_diff", "_eq", "_in", "_inter", "_len", "_powerset", "_sha256Hex", "_subset", "_union")

  // vitest fails a test file that contains zero tests (the same hazard as
  // pytest exit-5). When every behavioral op is a fail-loud stub / state-dep /
  // untranslatable, emit one honest skip; reasons are in _testgen_skips.json.
  private def behavioralSkipPlaceholder(ir: ServiceIRFull): String =
    s"""|// Auto-generated by spec-to-rest. Do not edit by hand.
        |//
        |// No native behavioral test could be built for ${ir.a}: every operation
        |// is a fail-loud stub, has a state-dependent precondition, or references
        |// constructs not assertable black-box. See tests/_testgen_skips.json.
        |import { test } from "vitest";
        |
        |test.skip("${ir.a} behavioral: no assertable ensures (see _testgen_skips.json)", () => {});
        |""".stripMargin

  def renderModule(ir: ServiceIRFull, tests: List[GeneratedTest]): String =
    if tests.isEmpty then behavioralSkipPlaceholder(ir)
    else renderFull(ir, tests)

  private def renderFull(ir: ServiceIRFull, tests: List[GeneratedTest]): String =
    val bodies = tests.map(_.body).mkString("\n")
    val usedRt = RuntimeHelpers.filter(h => bodies.contains(s"$h("))
    val predNames =
      (ir.l.collect { case f: FunctionDeclFull => f.a } ++
        ir.m.collect { case p: PredicateDeclFull => p.a }).distinct.sorted
        .filter(n => bodies.contains(s"$n("))
    val stratNames =
      Strategies.forIR(ir, TsFastCheckStrategy).map(_.functionName).distinct.sorted
        .filter(n => bodies.contains(s"$n("))

    val sb = new StringBuilder
    sb.append("// Auto-generated by spec-to-rest. Do not edit by hand.\n")
    sb.append("/* eslint-disable @typescript-eslint/no-explicit-any */\n")
    sb.append("import { expect, test } from \"vitest\";\n")
    sb.append("import fc from \"fast-check\";\n")
    sb.append("import { client } from \"./_client.js\";\n")
    if usedRt.nonEmpty then
      sb.append(s"import { ${usedRt.mkString(", ")} } from \"./_runtime.js\";\n")
    if predNames.nonEmpty then
      sb.append(s"import { ${predNames.mkString(", ")} } from \"./_predicates.js\";\n")
    if stratNames.nonEmpty then
      sb.append(s"import { ${stratNames.mkString(", ")} } from \"./_strategies.js\";\n")
    sb.append("\n")
    sb.append("const _RUNS: Record<string, number> = ")
    sb.append("{ smoke: 20, thorough: 100, exhaustive: 500 };\n")
    sb.append(
      "const NUM_RUNS = _RUNS[process.env.SPEC_TEST_PROFILE ?? \"thorough\"] ?? 100;\n\n"
    )
    sb.append(bodies)
    sb.toString
