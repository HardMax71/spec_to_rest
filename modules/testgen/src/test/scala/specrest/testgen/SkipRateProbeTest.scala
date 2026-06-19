package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.Builder
import specrest.parser.Parse

class SkipRateProbeTest extends CatsEffectSuite:

  private def loadIR(path: String) =
    val src = scala.io.Source.fromFile(path).getLines.mkString("\n")
    Parse.parseSpec(src).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  private def measure(path: String) =
    loadIR(path).map: ir =>
      var total       = 0
      var skipped     = 0
      val skipReasons = scala.collection.mutable.ListBuffer.empty[String]
      svcOperations(ir).foreach: op =>
        val reqCtx = TestCtx.fromOperation(op, ir, CaptureMode.PreState)
        val ensCtx = TestCtx.fromOperation(op, ir, CaptureMode.PostState)
        operRequires(op).foreach: e =>
          total += 1
          ExprToPython.translate(e, reqCtx) match
            case Translated.Skip(r, _) =>
              skipped += 1; skipReasons += s"${operName(op)}.requires: $r"
            case _ => ()
        operEnsures(op).foreach: e =>
          total += 1
          ExprToPython.translate(e, ensCtx) match
            case Translated.Skip(r, _) =>
              skipped += 1; skipReasons += s"${operName(op)}.ensures: $r"
            case _ => ()
      svcInvariants(ir).foreach: inv =>
        total += 1
        ExprToPython.translate(invBody(inv), baseCtx(ir)) match
          case Translated.Skip(r, _) =>
            skipped += 1
            skipReasons += s"invariant ${invName(inv).getOrElse("<anon>")}: $r"
          case _ => ()
      val rate = if total == 0 then 0.0 else skipped.toDouble / total.toDouble
      println(f"[$path] total=$total skipped=$skipped rate=${rate * 100}%.1f%%")
      skipReasons.foreach(r => println(s"  - $r"))
      (total, skipped, rate)

  private def baseCtx(ir: ServiceIRFull) =
    val stateFields = irStateFields(ir)
    val stateNames  = stateFields.map(stfName).toSet
    val enumVals    = svcEnums(ir).map(e => enmName(e) -> enmVariants(e).toSet).toMap
    val mapNames = stateFields.collect {
      case f if stfType(f) match { case _: MapTypeF => true; case _ => false } => stfName(f)
    }.toSet
    TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = stateNames,
      mapStateFields = mapNames,
      enumValues = enumVals,
      userFunctions = svcFunctions(ir).map(f => fncName(f) -> f).toMap,
      userPredicates = svcPredicates(ir).map(p => prdName(p) -> p).toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )

  List(
    ("safe_counter", 5, 0, "count is an Int scalar backed by service_state since #407"),
    (
      "url_shortener",
      21,
      1,
      "base_url is unbacked scalar state (String, outside the #407 Int scope)"
    ),
    (
      "todo_list",
      50,
      2,
      "next_id stays unbacked: `next_id not in todos` is invariant-relevant but not a derivable seed bound, so #407 conservatively leaves it on the unbacked path"
    ),
    (
      "ecommerce",
      74,
      8,
      "8 unbacked-scalar ensures (next_order_id + next_payment_id): the payment-frame completion (RecordPayment fresh id, ProcessReturn/CancelOrder deterministic refund inserts that bump next_payment_id) adds next_payment_id ensures, which the test-admin /state endpoint can't project black-box - same class as the next_order_id skips"
    ),
    ("edge_cases", 29, 0, "plain is an Int scalar backed by service_state since #407"),
    (
      "auth_service",
      43,
      4,
      "4 unbacked scalar-state; total 43 after adding userKeyMatchesId, sessionKeyMatchesId, nextUserIdFresh, nextSessionIdFresh, post-state membership clauses to make the Dafny kernel hand-verifiable (all 7 ops verified with bodies, see #149)"
    )
  ).foreach: (name, expectedTotal, expectedSkipped, why) =>
    test(s"$name: $expectedTotal clauses, $expectedSkipped skips ($why)"):
      measure(s"fixtures/spec/$name.spec").map: (total, skipped, _) =>
        assertEquals(total, expectedTotal)
        assertEquals(skipped, expectedSkipped)
