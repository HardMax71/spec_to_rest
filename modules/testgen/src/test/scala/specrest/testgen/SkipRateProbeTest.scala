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
      ir.g.collect { case op: OperationDeclFull => op }.foreach: op =>
        val reqCtx = TestCtx.fromOperation(op, ir, CaptureMode.PreState)
        val ensCtx = TestCtx.fromOperation(op, ir, CaptureMode.PostState)
        op.d.foreach: e =>
          total += 1
          ExprToPython.translate(e, reqCtx) match
            case ExprPy.Skip(r, _) => skipped += 1; skipReasons += s"${op.a}.requires: $r"
            case _                 => ()
        op.e.foreach: e =>
          total += 1
          ExprToPython.translate(e, ensCtx) match
            case ExprPy.Skip(r, _) => skipped += 1; skipReasons += s"${op.a}.ensures: $r"
            case _                 => ()
      ir.i.collect { case inv: InvariantDeclFull => inv }.foreach: inv =>
        total += 1
        ExprToPython.translate(inv.b, baseCtx(ir)) match
          case ExprPy.Skip(r, _) =>
            skipped += 1
            skipReasons += s"invariant ${inv.a.getOrElse("<anon>")}: $r"
          case _ => ()
      val rate = if total == 0 then 0.0 else skipped.toDouble / total.toDouble
      println(f"[$path] total=$total skipped=$skipped rate=${rate * 100}%.1f%%")
      skipReasons.foreach(r => println(s"  - $r"))
      (total, skipped, rate)

  private def baseCtx(ir: ServiceIRFull) =
    val stateFields = ir.f.toList.flatMap:
      case StateDeclFull(fs, _) => fs.collect { case f: StateFieldDeclFull => f }
    val stateNames = stateFields.map(_.a).toSet
    val enumVals   = ir.d.collect { case e: EnumDeclFull => e.a -> e.b.toSet }.toMap
    val mapNames = stateFields.collect {
      case StateFieldDeclFull(n, _: MapTypeF, _) => n
    }.toSet
    TestCtx(
      inputs = Set.empty,
      outputs = Set.empty,
      stateFields = stateNames,
      mapStateFields = mapNames,
      enumValues = enumVals,
      userFunctions = ir.l.collect { case f: FunctionDeclFull => f.a -> f }.toMap,
      userPredicates = ir.m.collect { case p: PredicateDeclFull => p.a -> p }.toMap,
      boundVars = Set.empty,
      capture = CaptureMode.PostState
    )

  List(
    ("safe_counter", 5, 3, "count is unbacked scalar state (admin /state projects null)"),
    ("url_shortener", 21, 1, "base_url is unbacked scalar state"),
    ("todo_list", 50, 2, "next_id is unbacked scalar state"),
    ("ecommerce", 76, 5, "3 unbacked scalar-state ensures + 2 `removed` parser scope leak"),
    ("edge_cases", 29, 3, "plain is unbacked scalar state"),
    ("auth_service", 40, 10, "4 unbacked scalar-state + 6 undeclared hash/recentFailedAttempts")
  ).foreach: (name, expectedTotal, expectedSkipped, why) =>
    test(s"$name: $expectedTotal clauses, $expectedSkipped skips ($why)"):
      measure(s"fixtures/spec/$name.spec").map: (total, skipped, _) =>
        assertEquals(total, expectedTotal)
        assertEquals(skipped, expectedSkipped)
