package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.ConventionRule
import specrest.ir.ConventionsDecl
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

  test("convention override on alias replaces synthesized body and registers import"):
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(name = "LongURL", typeExpr = TypeExpr.NamedType("String"))
      ),
      conventions = Some(
        ConventionsDecl(
          List(
            ConventionRule(
              target = "LongURL",
              property = "strategy",
              qualifier = None,
              value = Expr.StringLit("tests.strategies_user:valid_url")
            )
          )
        )
      )
    )
    val spec = Strategies.forIR(ir).find(_.typeName == "LongURL").getOrElse(fail("no LongURL"))
    assertEquals(spec.body, "valid_url()")
    assertEquals(spec.skipped, Nil)
    assertEquals(spec.imports, List(StrategyImport("tests.strategies_user", "valid_url")))

  test("convention override on enum replaces st.sampled_from body"):
    val ir = ServiceIR(
      name = "X",
      enums = List(EnumDecl(name = "Color", values = List("RED", "BLUE"))),
      conventions = Some(
        ConventionsDecl(
          List(
            ConventionRule(
              target = "Color",
              property = "strategy",
              qualifier = None,
              value = Expr.StringLit("tests.strategies_user:strong_color")
            )
          )
        )
      )
    )
    val spec = Strategies.forIR(ir).find(_.typeName == "Color").getOrElse(fail("no Color"))
    assertEquals(spec.body, "strong_color()")
    assertEquals(spec.skipped, Nil)
    assertEquals(spec.imports, List(StrategyImport("tests.strategies_user", "strong_color")))

  test("override only applies to the targeted type; other aliases keep synth"):
    import specrest.ir.BinOp
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(name = "Overridden", typeExpr = TypeExpr.NamedType("String")),
        TypeAliasDecl(
          name = "PosInt",
          typeExpr = TypeExpr.NamedType("Int"),
          constraint = Some(Expr.BinaryOp(BinOp.Gt, Expr.Identifier("value"), Expr.IntLit(0)))
        )
      ),
      conventions = Some(
        ConventionsDecl(
          List(
            ConventionRule(
              target = "Overridden",
              property = "strategy",
              qualifier = None,
              value = Expr.StringLit("m:s")
            )
          )
        )
      )
    )
    val specs = Strategies.forIR(ir).map(s => s.typeName -> s).toMap
    assertEquals(specs("Overridden").body, "s()")
    assertEquals(specs("PosInt").body, "st.integers(min_value=1)")
    assertEquals(specs("PosInt").imports, Nil)

  test("malformed override (no colon) silently falls through to synth"):
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(name = "Plain", typeExpr = TypeExpr.NamedType("String"))
      ),
      conventions = Some(
        ConventionsDecl(
          List(
            ConventionRule(
              target = "Plain",
              property = "strategy",
              qualifier = None,
              value = Expr.StringLit("not_a_module_symbol")
            )
          )
        )
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.text()")
    assertEquals(spec.imports, Nil)

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

  // ---------- M5.8: sensitive-aware redaction ----------

  private lazy val emptyIR: ServiceIR = ServiceIR(name = "Demo")

  test("sensitive operation input is wrapped in redact() by default"):
    val expr = Strategies.expressionFor(
      TypeExpr.NamedType("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      TestStrategyOverrides.Empty
    )
    assertEquals(expr, StrategyExpr.Code("redact(st.text())"))

  test("non-sensitive operation input is unwrapped"):
    val expr = Strategies.expressionFor(
      TypeExpr.NamedType("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "display_name"),
      TestStrategyOverrides.Empty
    )
    assertEquals(expr, StrategyExpr.Code("st.text()"))

  test("override 'live' on sensitive input removes redact wrapper"):
    val overrides = TestStrategyOverrides(
      perOperation = Map(("Register", "password") -> "live"),
      perEntityField = Map.empty
    )
    val expr = Strategies.expressionFor(
      TypeExpr.NamedType("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(expr, StrategyExpr.Code("st.text()"))

  test("override 'redacted' replaces strategy with placeholder"):
    val overrides = TestStrategyOverrides(
      perOperation = Map(("Register", "password") -> "redacted"),
      perEntityField = Map.empty
    )
    val expr = Strategies.expressionFor(
      TypeExpr.NamedType("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(expr, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("entity-field override broadcasts to operation inputs by name"):
    val ir = emptyIR.copy(
      entities = List(specrest.ir.EntityDecl(
        name = "User",
        fields = List(
          specrest.ir.FieldDecl("password", TypeExpr.NamedType("String"))
        )
      )),
      conventions = Some(ConventionsDecl(List(
        ConventionRule(
          target = "User",
          property = "test_strategy",
          qualifier = Some("password"),
          value = Expr.StringLit("redacted")
        )
      )))
    )
    val overrides = TestStrategyOverrides.from(ir)
    val expr = Strategies.expressionFor(
      TypeExpr.NamedType("String"),
      ir,
      StrategyCtx.OperationInput("Login", "password"),
      overrides
    )
    assertEquals(expr, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("operation-level override beats entity-level override"):
    val ir = emptyIR.copy(
      entities = List(specrest.ir.EntityDecl(
        name = "User",
        fields = List(specrest.ir.FieldDecl("password", TypeExpr.NamedType("String")))
      )),
      operations = List(
        specrest.ir.OperationDecl(name = "Register"),
        specrest.ir.OperationDecl(name = "Login")
      ),
      conventions = Some(ConventionsDecl(List(
        ConventionRule("User", "test_strategy", Some("password"), Expr.StringLit("redacted")),
        ConventionRule("Register", "test_strategy", Some("password"), Expr.StringLit("live"))
      )))
    )
    val overrides = TestStrategyOverrides.from(ir)
    val exprRegister = Strategies.expressionFor(
      TypeExpr.NamedType("String"),
      ir,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(exprRegister, StrategyExpr.Code("st.text()"))
    val exprLogin = Strategies.expressionFor(
      TypeExpr.NamedType("String"),
      ir,
      StrategyCtx.OperationInput("Login", "password"),
      overrides
    )
    assertEquals(exprLogin, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("anonymous ctx never wraps even for sensitive-named types"):
    val expr = Strategies.expressionFor(TypeExpr.NamedType("String"), emptyIR)
    assertEquals(expr, StrategyExpr.Code("st.text()"))

  // ---------- M5.9: entity strategies for transition entities ----------

  test("M5.9: todo_list emits strategy_todo with one entry per Todo field"):
    loadFixture("fixtures/spec/todo_list.spec").map: ir =>
      val specs = Strategies.forIR(ir)
      val todo  = specs.find(_.typeName == "Todo").getOrElse(fail("no strategy_todo"))
      assertEquals(todo.functionName, "strategy_todo")
      assert(todo.body.startsWith("st.fixed_dictionaries"), s"body=${todo.body}")
      assert(todo.body.contains("\"id\":"), s"body=${todo.body}")
      assert(todo.body.contains("\"status\":"), s"body=${todo.body}")
      assert(todo.body.contains("\"priority\":"), s"body=${todo.body}")
      assert(todo.body.contains("\"title\":"), s"body=${todo.body}")
      assert(todo.body.contains("strategy_status()"), s"body=${todo.body}")
      assert(todo.body.contains("isoformat"), s"body=${todo.body}")

  test("M5.9: url_shortener (no transitions) emits NO entity strategy"):
    loadFixture("fixtures/spec/url_shortener.spec").map: ir =>
      val specs = Strategies.forIR(ir)
      assert(
        !specs.exists(_.typeName == "UrlMapping"),
        s"unexpected entity strategy; specs=${specs.map(_.typeName)}"
      )

  test("M5.9: safe_counter (no transitions, no entities) emits no entity strategies"):
    loadFixture("fixtures/spec/safe_counter.spec").map: ir =>
      val specs = Strategies.forIR(ir)
      assertEquals(specs, Nil)

  test("M5.9: transitionEntityNames returns the entities referenced by TransitionDecls"):
    loadFixture("fixtures/spec/todo_list.spec").map: ir =>
      assertEquals(Strategies.transitionEntityNames(ir), Set("Todo"))

  // ---------- M5.9 PR #154 review fixes ----------

  test("M5.9 fix A: unseedable field is omitted from fixed_dictionaries (no st.nothing)"):
    val ir = ServiceIR(
      name = "X",
      entities = List(specrest.ir.EntityDecl(
        name = "Foo",
        fields = List(
          specrest.ir.FieldDecl("id", TypeExpr.NamedType("Int")),
          specrest.ir.FieldDecl(
            "stuff",
            TypeExpr.MapType(TypeExpr.NamedType("String"), TypeExpr.NamedType("Int"))
          )
        )
      )),
      transitions = List(specrest.ir.TransitionDecl("FooLifecycle", "Foo", "id", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      !foo.body.contains("st.nothing"),
      s"unseedable must be omitted, not st.nothing: ${foo.body}"
    )
    assert(!foo.body.contains("\"stuff\""), s"unseedable key must be dropped: ${foo.body}")
    assert(foo.body.contains("\"id\":"), s"seedable key must be present: ${foo.body}")
    assert(
      foo.skipped.exists(_.contains("'stuff'")),
      s"unseedable field should be recorded in skipped: ${foo.skipped}"
    )

  test("M5.9 fix B: Option[Map[...]] field falls back to st.none() (not Skip)"):
    val ir = ServiceIR(
      name = "X",
      entities = List(specrest.ir.EntityDecl(
        name = "Foo",
        fields = List(
          specrest.ir.FieldDecl("id", TypeExpr.NamedType("Int")),
          specrest.ir.FieldDecl(
            "tags",
            TypeExpr.OptionType(
              TypeExpr.MapType(TypeExpr.NamedType("String"), TypeExpr.NamedType("Int"))
            )
          )
        )
      )),
      transitions = List(specrest.ir.TransitionDecl("FooLifecycle", "Foo", "id", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"tags\": st.none()"),
      s"Option[Skip] should yield st.none(): ${foo.body}"
    )
    assert(!foo.skipped.exists(_.contains("'tags'")), s"tags should not skip: ${foo.skipped}")

  test("M5.9 fix C: sensitive entity field is wrapped in redact() in entity strategy"):
    val ir = ServiceIR(
      name = "X",
      entities = List(specrest.ir.EntityDecl(
        name = "User",
        fields = List(
          specrest.ir.FieldDecl("id", TypeExpr.NamedType("Int")),
          specrest.ir.FieldDecl("password_hash", TypeExpr.NamedType("String"))
        )
      )),
      transitions = List(specrest.ir.TransitionDecl("UserLifecycle", "User", "id", Nil))
    )
    val user = Strategies.forIR(ir).find(_.typeName == "User").getOrElse(fail("no strategy_user"))
    assert(
      user.body.contains("\"password_hash\": redact("),
      s"sensitive password_hash must be redact-wrapped: ${user.body}"
    )

  test("M5.9 fix C: test_strategy='live' override removes redact in entity strategy"):
    val ir = ServiceIR(
      name = "X",
      entities = List(specrest.ir.EntityDecl(
        name = "User",
        fields = List(
          specrest.ir.FieldDecl("id", TypeExpr.NamedType("Int")),
          specrest.ir.FieldDecl("password", TypeExpr.NamedType("String"))
        )
      )),
      transitions = List(specrest.ir.TransitionDecl("UserLifecycle", "User", "id", Nil)),
      conventions = Some(ConventionsDecl(List(
        ConventionRule("User", "test_strategy", Some("password"), Expr.StringLit("live"))
      )))
    )
    val user = Strategies.forIR(ir).find(_.typeName == "User").getOrElse(fail("no strategy_user"))
    assert(
      !user.body.contains("\"password\": redact("),
      s"live override must remove redact: ${user.body}"
    )
    assert(
      user.body.contains("\"password\": st.text()"),
      s"live override should emit bare strategy: ${user.body}"
    )

  test("M5.9 fix F: alias of DateTime in entity field produces isoformat-mapped strategy"):
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(TypeAliasDecl("CreatedAt", TypeExpr.NamedType("DateTime"))),
      entities = List(specrest.ir.EntityDecl(
        name = "Foo",
        fields = List(
          specrest.ir.FieldDecl("id", TypeExpr.NamedType("Int")),
          specrest.ir.FieldDecl("at", TypeExpr.NamedType("CreatedAt"))
        )
      )),
      transitions = List(specrest.ir.TransitionDecl("FooLifecycle", "Foo", "id", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"at\": st.datetimes().map(lambda d: d.isoformat())"),
      s"alias of DateTime should resolve to JSON-friendly isoformat: ${foo.body}"
    )

  test("M5.9 fix J: entity with no seedable fields emits valid st.fixed_dictionaries({})"):
    val ir = ServiceIR(
      name = "X",
      entities = List(specrest.ir.EntityDecl(
        name = "Foo",
        fields = List(
          specrest.ir.FieldDecl(
            "stuff",
            TypeExpr.MapType(TypeExpr.NamedType("String"), TypeExpr.NamedType("Int"))
          )
        )
      )),
      transitions = List(specrest.ir.TransitionDecl("FooLifecycle", "Foo", "stuff", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assertEquals(foo.body, "st.fixed_dictionaries({})")
    assert(foo.skipped.exists(_.contains("'stuff'")), s"expected skip note: ${foo.skipped}")

  test("M5.9 fix K: alias-of-Int with constraint AND field-level constraint are combined"):
    import specrest.ir.BinOp
    val ir = ServiceIR(
      name = "X",
      typeAliases = List(
        TypeAliasDecl(
          name = "PosInt",
          typeExpr = TypeExpr.NamedType("Int"),
          constraint = Some(Expr.BinaryOp(BinOp.Gt, Expr.Identifier("value"), Expr.IntLit(0)))
        )
      ),
      entities = List(specrest.ir.EntityDecl(
        name = "Foo",
        fields = List(
          specrest.ir.FieldDecl(
            "score",
            TypeExpr.NamedType("PosInt"),
            constraint = Some(Expr.BinaryOp(BinOp.Le, Expr.Identifier("value"), Expr.IntLit(100)))
          )
        )
      )),
      transitions = List(specrest.ir.TransitionDecl("FooLifecycle", "Foo", "score", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"score\": st.integers(min_value=1, max_value=100)"),
      s"both constraints must apply (alias 'value > 0' AND field 'value <= 100'): ${foo.body}"
    )
