package specrest.testgen

import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated.FunctionDeclFull
import specrest.ir.generated.SpecRestGenerated.InvariantDeclFull
import specrest.ir.generated.SpecRestGenerated.OperationDeclFull
import specrest.ir.generated.SpecRestGenerated.PredicateDeclFull
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.expr_full
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

// Native TypeScript (vitest + fast-check) stateful emitter. Drives random
// operation sequences against the live service and asserts the global
// invariants after every step — reusing the shared decisions: StubOps.isStub
// for which ops are rules, Stateful.invariantCtx + TsExprBackend for invariant
// translation/skip (incl. the F4 unbacked-state skip, identical to Python).
// Bundle id-flow / transitions / temporals are not yet modelled and are
// honestly skipped. Python `Stateful` stays the byte-identical oracle.
object TsStateful:

  private case class StepOp(name: String, pop: ProfiledOperation, args: List[(String, String)])

  def emitFor(profiled: ProfiledService): StatefulOutput =
    val ir        = profiled.ir
    val skips     = scala.collection.mutable.ListBuffer.empty[TestSkip]
    val opsByName = ir.g.collect { case o: OperationDeclFull => o }.map(o => o.a -> o).toMap

    val stepOps = profiled.operations.flatMap: pop =>
      if StubOps.isStub(profiled, pop) then
        skips += TestSkip(pop.operationName, "stateful_rule", StubOps.skipReason(pop))
        None
      else
        tsInputArbs(pop, ir) match
          case Left(reason) =>
            skips += TestSkip(pop.operationName, "stateful_rule", reason)
            None
          case Right(args) if opsByName.contains(pop.operationName) =>
            Some(StepOp(pop.operationName, pop, args))
          case _ => None

    val invCtx = Stateful.invariantCtx(ir)
    val invariants =
      ir.i.collect { case inv: InvariantDeclFull => inv }.zipWithIndex.flatMap: (inv, idx) =>
        val name = inv.a.getOrElse(s"anon_$idx")
        TsExprBackend.translate(inv.b, invCtx) match
          case ExprPy.Skip(reason, _) =>
            skips += TestSkip("<invariants>", s"stateful_invariant[$name]", reason)
            None
          case ExprPy.Py(text) =>
            Some((name, prettyOneLine(inv.b), text))

    if ir.j.nonEmpty || ir.h.nonEmpty then
      skips += TestSkip(
        "<stateful>",
        "stateful_ts_pending",
        "bundle id-flow, transitions and temporals are not yet modelled by the " +
          "TypeScript stateful backend (#174); covered by the Python oracle suite"
      )

    val file =
      if stepOps.isEmpty || invariants.isEmpty then skipPlaceholder(ir)
      else renderModule(ir, stepOps, invariants)

    StatefulOutput(file = file, skips = skips.toList)

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

  private def dispatchCall(s: StepOp): String =
    val ep      = s.pop.endpoint
    val method  = ep.method.toString.toLowerCase
    val hasPath = ep.pathParams.nonEmpty
    val rawPath = ep.path.replaceAll("\\{([^}]+)\\}", "\\$\\{step.$1\\}")
    val pathExpr =
      if hasPath then s"`$rawPath`" else TsLit.str(ep.path)
    val bodyExpr =
      if ep.bodyParams.isEmpty then ""
      else
        val pairs = ep.bodyParams.map(p => s"${TsLit.str(p.name)}: step.${p.name}").mkString(", ")
        s", { $pairs }"
    s"client.$method($pathExpr$bodyExpr)"

  private def renderModule(
      ir: ServiceIRFull,
      stepOps: List[StepOp],
      invariants: List[(String, String, String)]
  ): String =
    val stepArbs = stepOps
      .map: s =>
        val fields = (("op", s"fc.constant(${TsLit.str(s.name)})")
          :: s.args.map((n, a) => (n, a)))
          .map((k, v) => s"$k: $v")
          .mkString(", ")
        s"    fc.record({ $fields })"
      .mkString(",\n")

    val dispatchCases = stepOps
      .map: s =>
        s"    case ${TsLit.str(s.name)}: { await ${dispatchCall(s)}; return; }"
      .mkString("\n")

    val invChecks = invariants
      .map: (name, pretty, text) =>
        val viol = TsLit.str(s"invariant violated: $name ($pretty)")
        s"      if (!($text)) { throw new Error($viol); }"
      .mkString("\n")

    val bodyForScan = stepArbs + dispatchCases + invChecks
    val usedRt      = RuntimeHelpers.filter(h => bodyForScan.contains(s"$h("))
    val predNames =
      (ir.l.collect { case f: FunctionDeclFull => f.a } ++
        ir.m.collect { case p: PredicateDeclFull => p.a }).distinct.sorted
        .filter(n => bodyForScan.contains(s"$n("))
    val stratNames =
      Strategies.forIR(ir, TsFastCheckStrategy).map(_.functionName).distinct.sorted
        .filter(n => bodyForScan.contains(s"$n("))

    val sb = new StringBuilder
    sb.append("// Auto-generated by spec-to-rest --with-tests. Do not edit by hand.\n")
    sb.append("/* eslint-disable @typescript-eslint/no-explicit-any */\n")
    sb.append("import { test } from \"vitest\";\n")
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
    sb.append("{ smoke: 10, thorough: 25, exhaustive: 75 };\n")
    sb.append("const _STEPS: Record<string, number> = ")
    sb.append("{ smoke: 8, thorough: 20, exhaustive: 40 };\n")
    sb.append("const _P = process.env.SPEC_TEST_PROFILE ?? \"thorough\";\n")
    sb.append("const NUM_RUNS = _RUNS[_P] ?? 25;\n")
    sb.append("const STEP_COUNT = _STEPS[_P] ?? 20;\n\n")
    sb.append("async function dispatch(step: any): Promise<void> {\n")
    sb.append("  switch (step.op) {\n")
    sb.append(dispatchCases)
    sb.append("\n    default: return;\n")
    sb.append("  }\n}\n\n")
    sb.append(
      s"test(${TsLit.str(s"${ir.a} stateful invariants hold under random operation sequences")}, async () => {\n"
    )
    sb.append("  await fc.assert(\n")
    sb.append("    fc.asyncProperty(\n")
    sb.append("      fc.array(\n")
    sb.append("        fc.oneof(\n")
    sb.append(stepArbs)
    sb.append("\n        ),\n")
    sb.append("        { minLength: 1, maxLength: STEP_COUNT },\n")
    sb.append("      ),\n")
    sb.append("      async (steps) => {\n")
    sb.append("        await client.post(\"/__test_admin__/reset\");\n")
    sb.append("        for (const step of steps) {\n")
    sb.append("          await dispatch(step);\n")
    sb.append(
      "          const postState = (await (await client.get(\"/__test_admin__/state\"))" +
        ".json()) as any;\n"
    )
    sb.append(invChecks)
    sb.append("\n        }\n")
    sb.append("      },\n")
    sb.append("    ),\n")
    sb.append("    { numRuns: NUM_RUNS },\n")
    sb.append("  );\n")
    sb.append("});\n")
    sb.toString

  private def skipPlaceholder(ir: ServiceIRFull): String =
    s"""|// Auto-generated by spec-to-rest --with-tests. Do not edit by hand.
        |//
        |// No native stateful test could be built for ${ir.a}: every operation is a
        |// fail-loud stub / has untranslatable inputs, or no global invariant is
        |// black-box assertable. See tests/_testgen_skips.json for the reasons.
        |import { test } from "vitest";
        |
        |test.skip("${ir.a} stateful: no assertable rules/invariants (see _testgen_skips.json)", () => {});
        |""".stripMargin

  private val RuntimeHelpers =
    List("_diff", "_eq", "_in", "_inter", "_len", "_powerset", "_subset", "_union")

  private def prettyOneLine(e: expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim
