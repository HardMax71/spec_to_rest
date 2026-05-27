package specrest.testgen

import specrest.convention.Naming
import specrest.ir.PrettyPrint
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

// Native Go (go test + rapid) behavioral emitter. Mirrors the positive-ensures
// decision of the Python `Behavioral` (reusing its shared predicates) and
// honestly skips the kinds not yet ported (negative/invariant/temporal/
// transition). Python `Behavioral` stays the byte-identical oracle; this is
// selected only for the `chi` target.
object GoBehavioral:

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
            ensuresGo(pop, opDecl, ir) :+
              Left(
                TestSkip(
                  opDecl.a,
                  "behavioral_go_pending",
                  "negative/invariant/temporal/transition behavioral tests are not " +
                    "yet emitted by the Go backend (#176); these remain covered by " +
                    "the Python oracle suite"
                )
              )
          case None => Nil
    BehavioralOutput(
      tests = collected.collect { case Right(t) => t },
      skips = collected.collect { case Left(s) => s }
    )

  private def ensuresGo(
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
      goInputArbs(pop, ir) match
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
              GoExprBackend.translate(clause, ctx) match
                case Translated.Skip(reason, _) =>
                  Left(TestSkip(opDecl.a, s"ensures[$idx]", reason))
                case Translated.Emit(text) =>
                  Right(
                    buildPositiveTest(
                      name = s"Test_${opSnake}_ensures_$idx",
                      docstring = s"ensures: ${prettyOneLine(clause)}",
                      arbs = arbs,
                      pop = pop,
                      assertion = text,
                      nonTrivialRequires = nonTrivialRequires
                    )
                  )

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

  private def goRequestCall(pop: ProfiledOperation, ref: String => String): String =
    val ep = pop.endpoint
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
        s"fmt.Sprintf(${GoLit.str(tmpl)}, ${names.map(ref).mkString(", ")})"
    val bodyExpr =
      if ep.bodyParams.isEmpty then ""
      else
        val pairs = ep.bodyParams.map(p => s"${GoLit.str(p.name)}: ${ref(p.name)}").mkString(", ")
        s", map[string]any{$pairs}"
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
    val call = goRequestCall(pop, identity)
    val viol = GoLit.str(s"ensures violated: $docstring")
    val sb   = new StringBuilder
    if arbs.nonEmpty then
      sb.append(s"func $name(t *testing.T) {\n")
      sb.append("\tensureAdmin(t)\n")
      sb.append("\trapid.Check(t, func(rt *rapid.T) {\n")
      sb.append("\t\tclient.post(\"/__test_admin__/reset\")\n")
      sb.append("\t\tpreState := adminState()\n")
      arbs.foreach: (n, a) =>
        sb.append(s"\t\t$n := ($a).Draw(rt, ${GoLit.str(n)})\n")
        sb.append(s"\t\t_ = $n\n")
      sb.append(s"\t\tresponse := $call\n")
      if nonTrivialRequires then
        sb.append(s"\t\tif response.Status() != $ok {\n\t\t\treturn\n\t\t}\n")
      sb.append(
        s"\t\tif response.Status() != $ok {\n\t\t\trt.Fatalf(\"expected status %d, got %d\", $ok, response.Status())\n\t\t}\n"
      )
      sb.append("\t\tresponseData := response.JSON()\n")
      sb.append("\t\tpostState := adminState()\n")
      sb.append("\t\t_ = preState\n\t\t_ = postState\n\t\t_ = responseData\n")
      sb.append(s"\t\tif !_truthy($assertion) {\n\t\t\trt.Fatalf($viol)\n\t\t}\n")
      sb.append("\t})\n")
      sb.append("}\n")
    else
      sb.append(s"func $name(t *testing.T) {\n")
      sb.append("\tensureAdmin(t)\n")
      sb.append("\tclient.post(\"/__test_admin__/reset\")\n")
      sb.append("\tpreState := adminState()\n")
      sb.append(s"\tresponse := $call\n")
      if nonTrivialRequires then
        sb.append(s"\tif response.Status() != $ok {\n\t\treturn\n\t}\n")
      sb.append(
        s"\tif response.Status() != $ok {\n\t\tt.Fatalf(\"expected status %d, got %d\", $ok, response.Status())\n\t}\n"
      )
      sb.append("\tresponseData := response.JSON()\n")
      sb.append("\tpostState := adminState()\n")
      sb.append("\t_ = preState\n\t_ = postState\n\t_ = responseData\n")
      sb.append(s"\tif !_truthy($assertion) {\n\t\tt.Fatalf($viol)\n\t}\n")
      sb.append("}\n")
    GeneratedTest(name = name, body = sb.toString, skipReason = None)

  private def prettyOneLine(e: expr_full): String =
    PrettyPrint.expr(e).replace("\n", " ").replace("\r", " ").trim

  private def goIdent(s: String): String =
    val parts  = s.split("[^A-Za-z0-9]+").filter(_.nonEmpty).map(_.capitalize)
    val joined = parts.mkString
    if joined.isEmpty || !joined.head.isLetter then s"Svc$joined" else joined

  private def behavioralSkipPlaceholder(ir: ServiceIRFull): String =
    s"""|//go:build conformance
        |
        |// Auto-generated by spec-to-rest. Do not edit by hand.
        |//
        |// No native behavioral test could be built for ${ir.a}: every operation
        |// is a fail-loud stub, has a state-dependent precondition, or references
        |// constructs not assertable black-box. See tests/_testgen_skips.json.
        |
        |package tests
        |
        |import "testing"
        |
        |func Test${goIdent(ir.a)}BehavioralAllSkipped(t *testing.T) {
        |\tt.Skip("no behavioral tests generated: every operation is a fail-loud " +
        |\t\t"stub or references untranslatable constructs " +
        |\t\t"(see tests/_testgen_skips.json)")
        |}
        |""".stripMargin

  def renderModule(ir: ServiceIRFull, tests: List[GeneratedTest]): String =
    if tests.isEmpty then behavioralSkipPlaceholder(ir)
    else renderFull(tests)

  private def renderFull(tests: List[GeneratedTest]): String =
    val bodies     = tests.map(_.body).mkString("\n")
    val stdlib     = (if bodies.contains("fmt.") then List("\t\"fmt\"") else Nil) :+ "\t\"testing\""
    val thirdParty = if bodies.contains("rapid.") then List("\t\"pgregory.net/rapid\"") else Nil
    val importBlock =
      if thirdParty.isEmpty then stdlib.mkString("\n")
      else stdlib.mkString("\n") + "\n\n" + thirdParty.mkString("\n")

    val sb = new StringBuilder
    sb.append("//go:build conformance\n\n")
    sb.append("// Auto-generated by spec-to-rest. Do not edit by hand.\n\n")
    sb.append("package tests\n\n")
    sb.append(s"import (\n$importBlock\n)\n\n")
    sb.append(bodies)
    sb.toString
