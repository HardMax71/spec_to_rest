package specrest.testgen

import specrest.codegen.go.GoLit
import specrest.ir.HttpMethods
import specrest.ir.Naming
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation
import specrest.profile.ProfiledService

// Everything that varies between the Go and TS native conformance suites, as
// data. The selection pipelines below consume it, so the two targets cannot
// drift on test selection or skip taxonomy; the per-language files keep only
// rendering.
final private[testgen] case class NativeTarget(
    tag: String,
    strategy: StrategyBackend,
    expr: ExprBackendBase,
    testNamePrefix: String
)

private[testgen] object NativeTarget:
  val Go: NativeTarget = NativeTarget("go", GoRapidStrategy, GoExprBackend, "Test_")
  val Ts: NativeTarget = NativeTarget("ts", TsFastCheckStrategy, TsExprBackend, "test_")

final private[testgen] case class PositiveTestSpec(
    name: String,
    docstring: String,
    arbs: List[(String, String)],
    pop: ProfiledOperation,
    assertion: String,
    nonTrivialRequires: Boolean
)

final private[testgen] case class NativeStepOp(
    name: String,
    pop: ProfiledOperation,
    args: List[(String, String)]
)

// Positive-ensures selection shared by the native behavioral emitters. Mirrors
// the decision of the Python `Behavioral` (reusing its shared predicates) and
// honestly skips the kinds not yet ported (negative/invariant/temporal/
// transition). Python `Behavioral` stays the byte-identical oracle.
private[testgen] object NativeBehavioral:

  def emitFor(profiled: ProfiledService, t: NativeTarget)(
      buildPositiveTest: PositiveTestSpec => GeneratedTest
  ): BehavioralOutput =
    val ir = profiled.ir
    val collected = profiled.operations.flatMap: pop =>
      if StubOps.isStub(profiled, pop) then
        List(Left(TestSkip(pop.operationName, "operation", StubOps.skipReason(pop))))
      else if pop.requiresAuth.nonEmpty then
        List(Left(TestSkip(pop.operationName, "operation", StubOps.authSkipReason(pop))))
      else
        svcOperations(ir).find(o => operName(o) == pop.operationName) match
          case Some(opDecl) =>
            ensuresTests(pop, opDecl, ir, t, buildPositiveTest) :+
              Left(
                TestSkip(
                  operName(opDecl),
                  s"behavioral_${t.tag}_pending",
                  "negative/invariant/temporal/transition behavioral tests are not " +
                    s"yet emitted by the ${t.expr.languageName} backend (#420); these " +
                    "remain covered by the Python oracle suite"
                )
              )
          case None => Nil
    BehavioralOutput(
      tests = collected.collect { case Right(gt) => gt },
      skips = collected.collect { case Left(sk) => sk }
    )

  private def ensuresTests(
      pop: ProfiledOperation,
      opDecl: operation_decl,
      ir: ServiceIRFull,
      t: NativeTarget,
      buildPositiveTest: PositiveTestSpec => GeneratedTest
  ): List[Either[TestSkip, GeneratedTest]] =
    val opSnake     = Naming.toSnakeCase(operName(opDecl))
    val stateFields = irStateFieldNames(ir).toSet
    val requiresHasStateRef =
      operRequires(opDecl).exists(e => hasPrePrime(e) || free_vars(e).exists(stateFields.contains))
    val nonTrivialRequires = operRequires(opDecl).exists(!isTrueLit(_))

    if requiresHasStateRef then
      List(
        Left(
          TestSkip(
            operName(opDecl),
            "ensures",
            Behavioral.stateDepSkipReason(operName(opDecl), Set.empty)
          )
        )
      )
    else
      TestFormat.inputArbs(pop, ir, t.strategy) match
        case Left(reason) => List(Left(TestSkip(operName(opDecl), "ensures", reason)))
        case Right(arbs) =>
          val ctx = TestCtx.fromOperation(
            opDecl,
            ir,
            CaptureMode.PreState,
            StubOps.bareBodyOutput(pop, opDecl),
            kernelRouted = pop.dafnyMethod.isDefined
          )
          operEnsures(opDecl).zipWithIndex.map: (clause, idx) =>
            if Behavioral.isAggregateEqualityOverState(clause, opDecl) then
              Left(TestSkip(
                operName(opDecl),
                s"ensures[$idx]",
                Behavioral.aggregateEqualitySkipReason
              ))
            else
              t.expr.translate(clause, ctx) match
                case Translated.Skip(reason, _) =>
                  Left(TestSkip(operName(opDecl), s"ensures[$idx]", reason))
                case Translated.Emit(text) =>
                  Right(
                    buildPositiveTest(
                      PositiveTestSpec(
                        name = s"${t.testNamePrefix}${opSnake}_ensures_$idx",
                        docstring = s"ensures: ${TestFormat.prettyOneLine(clause)}",
                        arbs = arbs,
                        pop = pop,
                        assertion = text,
                        nonTrivialRequires = nonTrivialRequires
                      )
                    )
                  )

// Rule/invariant selection shared by the native stateful emitters: which
// operations become dispatch rules, which global invariants are assertable,
// and the pending-skip for bundle id-flow / transitions / temporals. Python
// `Stateful` stays the byte-identical oracle.
private[testgen] object NativeStateful:

  def collect(
      profiled: ProfiledService,
      t: NativeTarget
  ): (List[NativeStepOp], List[(String, String, String)], List[TestSkip]) =
    val ir        = profiled.ir
    val skips     = scala.collection.mutable.ListBuffer.empty[TestSkip]
    val opsByName = svcOperations(ir).map(o => operName(o) -> o).toMap

    val stepOps = profiled.operations.flatMap: pop =>
      if StubOps.isStub(profiled, pop) then
        skips += TestSkip(pop.operationName, "stateful_rule", StubOps.skipReason(pop))
        None
      else if pop.requiresAuth.nonEmpty then
        skips += TestSkip(pop.operationName, "stateful_rule", StubOps.authSkipReason(pop))
        None
      else
        TestFormat.inputArbs(pop, ir, t.strategy) match
          case Left(reason) =>
            skips += TestSkip(pop.operationName, "stateful_rule", reason)
            None
          case Right(args) if opsByName.contains(pop.operationName) =>
            Some(NativeStepOp(pop.operationName, pop, args))
          case _ => None

    val invCtx = TestCtx.forInvariants(ir)
    val invariants =
      svcInvariants(ir).zipWithIndex.flatMap: (inv, idx) =>
        val name = invName(inv).getOrElse(s"anon_$idx")
        t.expr.translate(invBody(inv), invCtx) match
          case Translated.Skip(reason, _) =>
            skips += TestSkip("<invariants>", s"stateful_invariant[$name]", reason)
            None
          case Translated.Emit(text) =>
            Some((name, TestFormat.prettyOneLine(invBody(inv)), text))

    if svcTemporals(ir).nonEmpty || svcTransitions(ir).nonEmpty then
      skips += TestSkip(
        "<stateful>",
        s"stateful_${t.tag}_pending",
        "bundle id-flow, transitions and temporals are not yet modelled by the " +
          s"${t.expr.languageName} stateful backend (#420); covered by the Python oracle suite"
      )

    (stepOps, invariants, skips.toList)

// Structural-lite selection shared by the native structural emitters: every
// non-stub operation with generatable inputs becomes a no-5xx fuzz test.
// Python `Structural` stays the byte-identical oracle.
private[testgen] object NativeStructural:

  def emitFor(profiled: ProfiledService, t: NativeTarget)(
      buildTest: (ProfiledOperation, List[(String, String)]) => GeneratedTest
  ): BehavioralOutput =
    val ir = profiled.ir
    val collected = profiled.operations.map: pop =>
      if StubOps.isStub(profiled, pop) then
        Left(TestSkip(pop.operationName, "structural", StubOps.skipReason(pop)))
      else
        TestFormat.inputArbs(pop, ir, t.strategy) match
          case Left(reason) =>
            Left(TestSkip(pop.operationName, "structural", reason))
          case Right(arbs) =>
            Right(buildTest(pop, arbs))
    BehavioralOutput(
      tests = collected.collect { case Right(gt) => gt },
      skips = collected.collect { case Left(sk) => sk }
    )

private[testgen] object GoRender:

  def requestCall(pop: ProfiledOperation, ref: String => String): String =
    val ep     = pop.endpoint
    val method = HttpMethods.lower(ep.method)
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
    val target =
      if ep.queryParams.isEmpty then pathExpr
      else
        val pairs =
          ep.queryParams.map(p => s"${GoLit.str(p.name)}, ${ref(p.name)}").mkString(", ")
        s"withQuery($pathExpr, $pairs)"
    val bodyExpr =
      if ep.bodyParams.isEmpty then ""
      else
        val pairs = ep.bodyParams.map(p => s"${GoLit.str(p.name)}: ${ref(p.name)}").mkString(", ")
        s", map[string]any{$pairs}"
    s"client.$method($target$bodyExpr)"

  def module(bodies: String): String =
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

private[testgen] object TsRender:

  val Header: String =
    "// Auto-generated by spec-to-rest. Do not edit by hand.\n" +
      "/* eslint-disable @typescript-eslint/no-explicit-any */\n"

  val NumRunsPreamble: String =
    "const _RUNS: Record<string, number> = " +
      "{ smoke: 20, thorough: 100, exhaustive: 500 };\n" +
      "const NUM_RUNS = _RUNS[process.env.SPEC_TEST_PROFILE ?? \"thorough\"] ?? 100;\n\n"

  def requestCall(pop: ProfiledOperation, ref: String => String): String =
    val ep     = pop.endpoint
    val method = HttpMethods.lower(ep.method)
    val pathExpr =
      if ep.pathParams.isEmpty then TsLit.str(ep.path)
      else
        val raw = "\\{([^}]+)\\}".r.replaceAllIn(
          ep.path,
          m => scala.util.matching.Regex.quoteReplacement(s"$${${ref(m.group(1))}}")
        )
        s"`$raw`"
    val target =
      if ep.queryParams.isEmpty then pathExpr
      else
        val pairs =
          ep.queryParams.map(p => s"${TsLit.str(p.name)}: ${ref(p.name)}").mkString(", ")
        s"withQuery($pathExpr, { $pairs })"
    val bodyExpr =
      if ep.bodyParams.isEmpty then ""
      else
        val pairs = ep.bodyParams.map(p => s"${TsLit.str(p.name)}: ${ref(p.name)}").mkString(", ")
        s", { $pairs }"
    s"client.$method($target$bodyExpr)"

  def runtimeImport(scanText: String): Option[String] =
    val used = TestFormat.TsRuntimeHelpers.filter(h => scanText.contains(s"$h("))
    Option.when(used.nonEmpty)(s"import { ${used.mkString(", ")} } from \"./_runtime.js\";\n")

  def predicatesImport(ir: ServiceIRFull, scanText: String): Option[String] =
    val names =
      (svcFunctions(ir).map(fncName) ++
        svcPredicates(ir).map(prdName)).distinct.sorted
        .filter(n => scanText.contains(s"$n("))
    Option.when(names.nonEmpty)(s"import { ${names.mkString(", ")} } from \"./_predicates.js\";\n")

  def strategiesImport(ir: ServiceIRFull, scanText: String): Option[String] =
    val names =
      Strategies.forIR(ir, TsFastCheckStrategy).map(_.functionName).distinct.sorted
        .filter(n => scanText.contains(s"$n("))
    Option.when(names.nonEmpty)(s"import { ${names.mkString(", ")} } from \"./_strategies.js\";\n")
