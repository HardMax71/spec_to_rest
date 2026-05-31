package specrest.testgen

import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

// Native Go (go test + rapid) stateful emitter. Drives random operation
// sequences against the live service and asserts the global invariants after
// every step — reusing the shared decisions: StubOps.isStub for which ops are
// rules, Stateful.invariantCtx + GoExprBackend for invariant translation/skip
// (incl. the F4 unbacked-state skip, identical to Python). Bundle id-flow /
// transitions / temporals are not yet modelled and are honestly skipped.
// Python `Stateful` stays the byte-identical oracle.
object GoStateful:

  private case class StepOp(name: String, pop: ProfiledOperation, args: List[(String, String)])

  def emitFor(profiled: ProfiledService): StatefulOutput =
    val ir        = profiled.ir
    val skips     = scala.collection.mutable.ListBuffer.empty[TestSkip]
    val opsByName = svcOperations(ir).map(o => operName(o) -> o).toMap

    val stepOps = profiled.operations.flatMap: pop =>
      if StubOps.isStub(profiled, pop) then
        skips += TestSkip(pop.operationName, "stateful_rule", StubOps.skipReason(pop))
        None
      else
        goInputArbs(pop, ir) match
          case Left(reason) =>
            skips += TestSkip(pop.operationName, "stateful_rule", reason)
            None
          case Right(args) if opsByName.contains(pop.operationName) =>
            Some(StepOp(pop.operationName, pop, args))
          case _ => None

    val invCtx = Stateful.invariantCtx(ir)
    val invariants =
      svcInvariants(ir).zipWithIndex.flatMap: (inv, idx) =>
        val name = invName(inv).getOrElse(s"anon_$idx")
        GoExprBackend.translate(invBody(inv), invCtx) match
          case Translated.Skip(reason, _) =>
            skips += TestSkip("<invariants>", s"stateful_invariant[$name]", reason)
            None
          case Translated.Emit(text) =>
            Some((name, prettyOneLine(invBody(inv)), text))

    if svcTemporals(ir).nonEmpty || svcTransitions(ir).nonEmpty then
      skips += TestSkip(
        "<stateful>",
        "stateful_go_pending",
        "bundle id-flow, transitions and temporals are not yet modelled by the " +
          "Go stateful backend (#176); covered by the Python oracle suite"
      )

    val file =
      if stepOps.isEmpty || invariants.isEmpty then skipPlaceholder(ir)
      else renderModule(ir, stepOps, invariants)

    StatefulOutput(file = file, skips = skips.toList)

  private def goInputArbs(
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
        (p.name, Strategies.expressionFor(p.typeExpr, ir, sctx, overrides, GoRapidStrategy))
      pairs.collectFirst { case (n, StrategyExpr.Skip(r)) => s"input '$n': $r" } match
        case Some(reason) => Left(reason)
        case None         => Right(pairs.collect { case (n, StrategyExpr.Code(t)) => (n, t) })

  private def dispatchCall(s: StepOp): String =
    val ep = s.pop.endpoint
    val method = ep.method match
      case _: GET    => "get"
      case _: POST   => "post"
      case _: PUT    => "put"
      case _: PATCH  => "patch"
      case _: DELETE => "delete"
    val pathExpr =
      if ep.pathParams.isEmpty then GoLit.str(ep.path)
      else
        val names = scala.collection.mutable.ListBuffer.empty[String]
        val tmpl = "\\{([^}]+)\\}".r.replaceAllIn(
          ep.path,
          m =>
            names += m.group(1)
            "%v"
        )
        s"fmt.Sprintf(${GoLit.str(tmpl)}, ${names.toList.mkString(", ")})"
    val bodyExpr =
      if ep.bodyParams.isEmpty then ""
      else
        val pairs = ep.bodyParams.map(p => s"${GoLit.str(p.name)}: ${p.name}").mkString(", ")
        s", map[string]any{$pairs}"
    s"client.$method($pathExpr$bodyExpr)"

  private def renderModule(
      ir: ServiceIRFull,
      stepOps: List[StepOp],
      invariants: List[(String, String, String)]
  ): String =
    val opNames = stepOps.map(s => GoLit.str(s.name)).mkString(", ")

    val dispatchCases = stepOps
      .map: s =>
        val draws = s.args
          .map((n, a) => s"\t\t\t\t$n := ($a).Draw(rt, ${GoLit.str(n)})\n\t\t\t\t_ = $n")
          .mkString("\n")
        val drawBlock = if s.args.isEmpty then "" else draws + "\n"
        s"\t\t\tcase ${GoLit.str(s.name)}:\n$drawBlock\t\t\t\t${dispatchCall(s)}"
      .mkString("\n")

    val invChecks = invariants
      .map: (name, pretty, text) =>
        val viol = GoLit.str(s"invariant violated: $name ($pretty)")
        s"\t\t\tif !_truthy($text) {\n\t\t\t\trt.Fatalf($viol)\n\t\t\t}"
      .mkString("\n")

    val bodyForScan = dispatchCases + invChecks
    val needsFmt    = bodyForScan.contains("fmt.")
    val stdlib      = (if needsFmt then List("\t\"fmt\"") else Nil) :+ "\t\"testing\""
    val importBlock = stdlib.mkString("\n") + "\n\n\t\"pgregory.net/rapid\""

    val sb = new StringBuilder
    sb.append("//go:build conformance\n\n")
    sb.append("// Auto-generated by spec-to-rest. Do not edit by hand.\n\n")
    sb.append("package tests\n\n")
    sb.append(s"import (\n$importBlock\n)\n\n")
    sb.append(s"func Test${GoIdent.sanitize(svcName(ir))}StatefulInvariants(t *testing.T) {\n")
    sb.append("\tensureAdmin(t)\n")
    sb.append("\trapid.Check(t, func(rt *rapid.T) {\n")
    sb.append("\t\tclient.post(\"/__test_admin__/reset\")\n")
    sb.append(
      "\t\tnSteps := int(rapid.Int64Range(1, int64(confStepCount())).Draw(rt, \"steps\"))\n"
    )
    sb.append("\t\tfor i := 0; i < nSteps; i++ {\n")
    sb.append(s"\t\t\top := rapid.SampledFrom([]string{$opNames}).Draw(rt, \"op\")\n")
    sb.append("\t\t\tswitch op {\n")
    sb.append(dispatchCases)
    sb.append("\n\t\t\t}\n")
    sb.append("\t\t\tpostState := adminState()\n")
    sb.append("\t\t\t_ = postState\n")
    sb.append(invChecks)
    sb.append("\n\t\t}\n")
    sb.append("\t})\n")
    sb.append("}\n")
    sb.toString

  private def skipPlaceholder(ir: ServiceIRFull): String =
    s"""|//go:build conformance
        |
        |// Auto-generated by spec-to-rest. Do not edit by hand.
        |//
        |// No native stateful test could be built for ${svcName(ir)}: every operation is
        |// a fail-loud stub / has untranslatable inputs, or no global invariant is
        |// black-box assertable. See tests/_testgen_skips.json for the reasons.
        |
        |package tests
        |
        |import "testing"
        |
        |func Test${GoIdent.sanitize(svcName(ir))}StatefulAllSkipped(t *testing.T) {
        |\tt.Skip("no stateful tests generated: every operation is a fail-loud " +
        |\t\t"stub / has untranslatable inputs, or no global invariant is " +
        |\t\t"black-box assertable (see tests/_testgen_skips.json)")
        |}
        |""".stripMargin

  private def prettyOneLine(e: expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim
