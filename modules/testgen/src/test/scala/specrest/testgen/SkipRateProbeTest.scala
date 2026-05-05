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

  test("safe_counter: 5 clauses, 0 skips"):
    measure("fixtures/spec/safe_counter.spec").map: (total, skipped, _) =>
      assertEquals(total, 5)
      assertEquals(skipped, 0)

  test("url_shortener: 21 clauses, 0 skips"):
    measure("fixtures/spec/url_shortener.spec").map: (total, skipped, _) =>
      assertEquals(total, 21)
      assertEquals(skipped, 0)

  test("todo_list: 50 clauses, 0 skips"):
    measure("fixtures/spec/todo_list.spec").map: (total, skipped, _) =>
      assertEquals(total, 50)
      assertEquals(skipped, 0)

  test("ecommerce: 76 clauses, exactly 2 skips (multi-clause `let ... in` parser scope leak)"):
    measure("fixtures/spec/ecommerce.spec").map: (total, skipped, _) =>
      assertEquals(total, 76)
      assertEquals(skipped, 2)

  test("edge_cases: 29 clauses, 0 skips"):
    measure("fixtures/spec/edge_cases.spec").map: (total, skipped, _) =>
      assertEquals(total, 29)
      assertEquals(skipped, 0)

  test("auth_service: 40 clauses, exactly 6 skips (undeclared hash/recentFailedAttempts)"):
    measure("fixtures/spec/auth_service.spec").map: (total, skipped, _) =>
      assertEquals(total, 40)
      assertEquals(skipped, 6)
