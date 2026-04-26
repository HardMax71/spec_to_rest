package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.EnumDecl
import specrest.ir.Expr
import specrest.ir.ServiceIR
import specrest.ir.TypeAliasDecl
import specrest.ir.TypeExpr
import specrest.parser.Builder
import specrest.parser.Parse

class StrategiesTest extends CatsEffectSuite:

  private def loadIR(specSrc: String) =
    Parse.parseSpec(specSrc).flatMap:
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).map:
          case Right(ir) => ir
          case Left(err) => fail(s"build error: $err")
      case Left(err) => fail(s"parse error: $err")

  private def loadFixture(path: String) =
    val src = scala.io.Source.fromFile(path).getLines.mkString("\n")
    loadIR(src)

  test("ShortCode (regex + length) → from_regex with len filter"):
    loadFixture("fixtures/spec/url_shortener.spec").map: ir =>
      val spec =
        Strategies.forIR(ir).find(_.typeName == "ShortCode").getOrElse(fail("no ShortCode"))
      assertEquals(spec.functionName, "strategy_short_code")
      assert(spec.body.contains("from_regex"), s"body=${spec.body}")
      assert(spec.body.contains("[a-zA-Z0-9]"), s"body=${spec.body}")
      assert(spec.body.contains("6 <= len(v) <= 10"), s"body=${spec.body}")
      assertEquals(spec.skipped, Nil)

  test("LongURL (length lower bound + isValidURI predicate)"):
    loadFixture("fixtures/spec/url_shortener.spec").map: ir =>
      val spec = Strategies.forIR(ir).find(_.typeName == "LongURL").getOrElse(fail("no LongURL"))
      assert(spec.body.startsWith("st.text"), s"body=${spec.body}")
      assert(spec.body.contains("min_size=1"), s"body=${spec.body}")
      assert(spec.body.contains("is_valid_uri"), s"body=${spec.body}")

  test("BaseURL (only isValidURI predicate, no length)"):
    loadFixture("fixtures/spec/url_shortener.spec").map: ir =>
      val spec = Strategies.forIR(ir).find(_.typeName == "BaseURL").getOrElse(fail("no BaseURL"))
      assert(spec.body.contains("is_valid_uri"), s"body=${spec.body}")

  test("Enum strategy uses sampled_from over members"):
    loadFixture("fixtures/spec/todo_list.spec").map: ir =>
      val statusSpec =
        Strategies.forIR(ir).find(_.typeName == "Status").getOrElse(fail("no Status"))
      assertEquals(
        statusSpec.body,
        "st.sampled_from([\"TODO\", \"IN_PROGRESS\", \"DONE\", \"ARCHIVED\"])"
      )
      assertEquals(statusSpec.functionName, "strategy_status")

  test("expressionFor handles primitives + Option + Set + Seq + named alias"):
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(name = "Code", typeExpr = TypeExpr.NamedType("String"))
      ),
      enums = List(EnumDecl(name = "Color", values = List("RED", "BLUE")))
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.NamedType("String"), ir),
      StrategyExpr.Code("st.text()")
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.NamedType("Int"), ir),
      StrategyExpr.Code("st.integers()")
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.NamedType("Bool"), ir),
      StrategyExpr.Code("st.booleans()")
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.OptionType(TypeExpr.NamedType("String")), ir),
      StrategyExpr.Code("st.one_of(st.none(), st.text())")
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.SetType(TypeExpr.NamedType("String")), ir),
      StrategyExpr.Code("st.sets(st.text(), max_size=5)")
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.SeqType(TypeExpr.NamedType("Int")), ir),
      StrategyExpr.Code("st.lists(st.integers(), max_size=5)")
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.NamedType("Code"), ir),
      StrategyExpr.Code("strategy_code()")
    )
    assertEquals(
      Strategies.expressionFor(TypeExpr.NamedType("Color"), ir),
      StrategyExpr.Code("strategy_color()")
    )
    Strategies.expressionFor(TypeExpr.NamedType("UnknownType"), ir) match
      case StrategyExpr.Skip(r) => assert(r.contains("UnknownType"))
      case other                => fail(s"expected Skip, got $other")

  test("MapType / RelationType skip"):
    val ir   = ServiceIR(name = "X")
    val mapT = TypeExpr.MapType(TypeExpr.NamedType("String"), TypeExpr.NamedType("Int"))
    Strategies.expressionFor(mapT, ir) match
      case StrategyExpr.Skip(_) => ()
      case other                => fail(s"expected Skip, got $other")

  test("Int with no constraint → st.integers()"):
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(name = "Counter", typeExpr = TypeExpr.NamedType("Int"))
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.integers()")

  test("Int with positive constraint via where value > 0"):
    import specrest.ir.BinOp
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(
          name = "PosInt",
          typeExpr = TypeExpr.NamedType("Int"),
          constraint = Some(
            Expr.BinaryOp(BinOp.Gt, Expr.Identifier("value"), Expr.IntLit(0))
          )
        )
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.integers(min_value=1)")

  test("Unhandled string constraint goes to skipped list, base strategy still produced"):
    import specrest.ir.BinOp
    val weird = Expr.BinaryOp(
      BinOp.Eq,
      Expr.Call(Expr.Identifier("custom_pred"), List(Expr.Identifier("value"))),
      Expr.BoolLit(true)
    )
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(
          name = "Weird",
          typeExpr = TypeExpr.NamedType("String"),
          constraint = Some(weird)
        )
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.text()")
    assert(spec.skipped.nonEmpty)

  test("safe_counter has no type aliases or enums; forIR returns empty"):
    loadFixture("fixtures/spec/safe_counter.spec").map: ir =>
      assertEquals(Strategies.forIR(ir), Nil)

  test("multiple regex constraints in `And` chain are all applied"):
    import specrest.ir.BinOp
    val constraint = Expr.BinaryOp(
      BinOp.And,
      Expr.Matches(Expr.Identifier("value"), "^[a-z]+$"),
      Expr.Matches(Expr.Identifier("value"), ".{3,10}")
    )
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(
          name = "TwoRegex",
          typeExpr = TypeExpr.NamedType("String"),
          constraint = Some(constraint)
        )
      )
    )
    val spec = Strategies.forIR(ir).head
    assert(
      spec.body.contains("from_regex"),
      s"expected primary regex via from_regex; body=${spec.body}"
    )
    assert(
      spec.body.contains("re').fullmatch"),
      s"expected secondary regex as filter; body=${spec.body}"
    )
    assertEquals(spec.skipped, Nil)
