package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

import munit.CatsEffectSuite
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

  test("LongURL (length lower bound + isValidURI predicate inlines preamble regex)"):
    loadFixture("fixtures/spec/url_shortener.spec").map: ir =>
      val spec = Strategies.forIR(ir).find(_.typeName == "LongURL").getOrElse(fail("no LongURL"))
      assert(spec.body.contains("from_regex"), s"body=${spec.body}")
      assert(spec.body.contains("https?"), s"body=${spec.body}")
      assert(spec.body.contains("len(v) >= 1"), s"body=${spec.body}")

  test("BaseURL (isValidURI predicate inlines preamble regex)"):
    loadFixture("fixtures/spec/url_shortener.spec").map: ir =>
      val spec = Strategies.forIR(ir).find(_.typeName == "BaseURL").getOrElse(fail("no BaseURL"))
      assert(spec.body.contains("from_regex"), s"body=${spec.body}")
      assert(spec.body.contains("https?"), s"body=${spec.body}")

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
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(name = "Code", typeExpr = NamedTypeF("String"))
      ),
      enums = List(EnumDeclFull(name = "Color", values = List("RED", "BLUE")))
    )
    assertEquals(
      Strategies.expressionFor(NamedTypeF("String"), ir),
      StrategyExpr.Code("st.text()")
    )
    assertEquals(
      Strategies.expressionFor(NamedTypeF("Int"), ir),
      StrategyExpr.Code("st.integers()")
    )
    assertEquals(
      Strategies.expressionFor(NamedTypeF("Bool"), ir),
      StrategyExpr.Code("st.booleans()")
    )
    assertEquals(
      Strategies.expressionFor(OptionTypeF(NamedTypeF("String")), ir),
      StrategyExpr.Code("st.one_of(st.none(), st.text())")
    )
    assertEquals(
      Strategies.expressionFor(SetTypeF(NamedTypeF("String")), ir),
      StrategyExpr.Code("st.sets(st.text(), max_size=5)")
    )
    assertEquals(
      Strategies.expressionFor(SeqTypeF(NamedTypeF("Int")), ir),
      StrategyExpr.Code("st.lists(st.integers(), max_size=5)")
    )
    assertEquals(
      Strategies.expressionFor(NamedTypeF("Code"), ir),
      StrategyExpr.Code("strategy_code()")
    )
    assertEquals(
      Strategies.expressionFor(NamedTypeF("Color"), ir),
      StrategyExpr.Code("strategy_color()")
    )
    Strategies.expressionFor(NamedTypeF("UnknownType"), ir) match
      case StrategyExpr.Skip(r) => assert(r.contains("UnknownType"))
      case other                => fail(s"expected Skip, got $other")

  test("MapType / RelationType skip"):
    val ir   = ServiceIRFull(name = "X")
    val mapT = MapTypeF(NamedTypeF("String"), NamedTypeF("Int"))
    Strategies.expressionFor(mapT, ir) match
      case StrategyExpr.Skip(_) => ()
      case other                => fail(s"expected Skip, got $other")

  test("Int with no constraint → st.integers()"):
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(name = "Counter", typeExpr = NamedTypeF("Int"))
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.integers()")

  test("Int with positive constraint via where value > 0"):
    import specrest.ir.BinOp
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(
          name = "PosInt",
          typeExpr = NamedTypeF("Int"),
          constraint = Some(
            BinaryOpF(BGt(), IdentifierF("value"), IntLitF(int_of_integer(BigInt(0)), None))
          )
        )
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.integers(min_value=1)")

  test("Unhandled string constraint goes to skipped list, base strategy still produced"):
    import specrest.ir.BinOp
    val weird = BinaryOpF(
      BEq(),
      CallF(IdentifierF("custom_pred"), List(IdentifierF("value"))),
      BoolLitF(true)
    )
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(
          name = "Weird",
          typeExpr = NamedTypeF("String"),
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
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(name = "LongURL", typeExpr = NamedTypeF("String"))
      ),
      conventions = Some(
        ConventionsDeclFull(
          List(
            ConventionRuleFull(
              target = "LongURL",
              property = "strategy",
              qualifier = None,
              value = StringLitF("tests.strategies_user:valid_url")
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
    val ir = ServiceIRFull(
      name = "X",
      enums = List(EnumDeclFull(name = "Color", values = List("RED", "BLUE"))),
      conventions = Some(
        ConventionsDeclFull(
          List(
            ConventionRuleFull(
              target = "Color",
              property = "strategy",
              qualifier = None,
              value = StringLitF("tests.strategies_user:strong_color")
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
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(name = "Overridden", typeExpr = NamedTypeF("String")),
        TypeAliasDeclFull(
          name = "PosInt",
          typeExpr = NamedTypeF("Int"),
          constraint =
            Some(BinaryOpF(BGt(), IdentifierF("value"), IntLitF(int_of_integer(BigInt(0)), None)))
        )
      ),
      conventions = Some(
        ConventionsDeclFull(
          List(
            ConventionRuleFull(
              target = "Overridden",
              property = "strategy",
              qualifier = None,
              value = StringLitF("m:s")
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
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(name = "Plain", typeExpr = NamedTypeF("String"))
      ),
      conventions = Some(
        ConventionsDeclFull(
          List(
            ConventionRuleFull(
              target = "Plain",
              property = "strategy",
              qualifier = None,
              value = StringLitF("not_a_module_symbol")
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
    val constraint = BinaryOpF(
      BAnd(),
      MatchesF(IdentifierF("value"), "^[a-z]+$"),
      MatchesF(IdentifierF("value"), ".{3,10}")
    )
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(
          name = "TwoRegex",
          typeExpr = NamedTypeF("String"),
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

  private lazy val emptyIR: ServiceIR = ServiceIRFull(name = "Demo")

  test("sensitive operation input is wrapped in redact() by default"):
    val expr = Strategies.expressionFor(
      NamedTypeF("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      TestStrategyOverrides.Empty
    )
    assertEquals(expr, StrategyExpr.Code("redact(st.text())"))

  test("non-sensitive operation input is unwrapped"):
    val expr = Strategies.expressionFor(
      NamedTypeF("String"),
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
      NamedTypeF("String"),
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
      NamedTypeF("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(expr, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("entity-field override broadcasts to operation inputs by name"):
    val ir = emptyIR.copy(
      entities = List(EntityDeclFull(
        name = "User",
        fields = List(
          FieldDeclFull("password", NamedTypeF("String"))
        )
      )),
      conventions = Some(ConventionsDeclFull(List(
        ConventionRuleFull(
          target = "User",
          property = "test_strategy",
          qualifier = Some("password"),
          value = StringLitF("redacted")
        )
      )))
    )
    val overrides = TestStrategyOverrides.from(ir)
    val expr = Strategies.expressionFor(
      NamedTypeF("String"),
      ir,
      StrategyCtx.OperationInput("Login", "password"),
      overrides
    )
    assertEquals(expr, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("operation-level override beats entity-level override"):
    val ir = emptyIR.copy(
      entities = List(EntityDeclFull(
        name = "User",
        fields = List(FieldDeclFull("password", NamedTypeF("String")))
      )),
      operations = List(
        OperationDeclFull(name = "Register"),
        OperationDeclFull(name = "Login")
      ),
      conventions = Some(ConventionsDeclFull(List(
        ConventionRuleFull("User", "test_strategy", Some("password"), StringLitF("redacted")),
        ConventionRuleFull("Register", "test_strategy", Some("password"), StringLitF("live"))
      )))
    )
    val overrides = TestStrategyOverrides.from(ir)
    val exprRegister = Strategies.expressionFor(
      NamedTypeF("String"),
      ir,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(exprRegister, StrategyExpr.Code("st.text()"))
    val exprLogin = Strategies.expressionFor(
      NamedTypeF("String"),
      ir,
      StrategyCtx.OperationInput("Login", "password"),
      overrides
    )
    assertEquals(exprLogin, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("anonymous ctx never wraps even for sensitive-named types"):
    val expr = Strategies.expressionFor(NamedTypeF("String"), emptyIR)
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
    val ir = ServiceIRFull(
      name = "X",
      entities = List(EntityDeclFull(
        name = "Foo",
        fields = List(
          FieldDeclFull("id", NamedTypeF("Int")),
          FieldDeclFull(
            "stuff",
            MapTypeF(NamedTypeF("String"), NamedTypeF("Int"))
          )
        )
      )),
      transitions = List(TransitionDeclFull("FooLifecycle", "Foo", "id", Nil))
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
    val ir = ServiceIRFull(
      name = "X",
      entities = List(EntityDeclFull(
        name = "Foo",
        fields = List(
          FieldDeclFull("id", NamedTypeF("Int")),
          FieldDeclFull(
            "tags",
            OptionTypeF(
              MapTypeF(NamedTypeF("String"), NamedTypeF("Int"))
            )
          )
        )
      )),
      transitions = List(TransitionDeclFull("FooLifecycle", "Foo", "id", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"tags\": st.none()"),
      s"Option[Skip] should yield st.none(): ${foo.body}"
    )
    assert(!foo.skipped.exists(_.contains("'tags'")), s"tags should not skip: ${foo.skipped}")

  test("M5.9 fix C: sensitive entity field is wrapped in redact() in entity strategy"):
    val ir = ServiceIRFull(
      name = "X",
      entities = List(EntityDeclFull(
        name = "User",
        fields = List(
          FieldDeclFull("id", NamedTypeF("Int")),
          FieldDeclFull("password_hash", NamedTypeF("String"))
        )
      )),
      transitions = List(TransitionDeclFull("UserLifecycle", "User", "id", Nil))
    )
    val user = Strategies.forIR(ir).find(_.typeName == "User").getOrElse(fail("no strategy_user"))
    assert(
      user.body.contains("\"password_hash\": redact("),
      s"sensitive password_hash must be redact-wrapped: ${user.body}"
    )

  test("M5.9 fix C: test_strategy='live' override removes redact in entity strategy"):
    val ir = ServiceIRFull(
      name = "X",
      entities = List(EntityDeclFull(
        name = "User",
        fields = List(
          FieldDeclFull("id", NamedTypeF("Int")),
          FieldDeclFull("password", NamedTypeF("String"))
        )
      )),
      transitions = List(TransitionDeclFull("UserLifecycle", "User", "id", Nil)),
      conventions = Some(ConventionsDeclFull(List(
        ConventionRuleFull("User", "test_strategy", Some("password"), StringLitF("live"))
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
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(TypeAliasDeclFull("CreatedAt", NamedTypeF("DateTime"))),
      entities = List(EntityDeclFull(
        name = "Foo",
        fields = List(
          FieldDeclFull("id", NamedTypeF("Int")),
          FieldDeclFull("at", NamedTypeF("CreatedAt"))
        )
      )),
      transitions = List(TransitionDeclFull("FooLifecycle", "Foo", "id", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"at\": st.datetimes().map(lambda d: d.isoformat())"),
      s"alias of DateTime should resolve to JSON-friendly isoformat: ${foo.body}"
    )

  test("M5.9 fix J: entity with no seedable fields emits valid st.fixed_dictionaries({})"):
    val ir = ServiceIRFull(
      name = "X",
      entities = List(EntityDeclFull(
        name = "Foo",
        fields = List(
          FieldDeclFull(
            "stuff",
            MapTypeF(NamedTypeF("String"), NamedTypeF("Int"))
          )
        )
      )),
      transitions = List(TransitionDeclFull("FooLifecycle", "Foo", "stuff", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assertEquals(foo.body, "st.fixed_dictionaries({})")
    assert(foo.skipped.exists(_.contains("'stuff'")), s"expected skip note: ${foo.skipped}")

  test("M5.9 fix K: alias-of-Int with constraint AND field-level constraint are combined"):
    import specrest.ir.BinOp
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(
          name = "PosInt",
          typeExpr = NamedTypeF("Int"),
          constraint =
            Some(BinaryOpF(BGt(), IdentifierF("value"), IntLitF(int_of_integer(BigInt(0)), None)))
        )
      ),
      entities = List(EntityDeclFull(
        name = "Foo",
        fields = List(
          FieldDeclFull(
            "score",
            NamedTypeF("PosInt"),
            constraint = Some(BinaryOpF(
              BLe(),
              IdentifierF("value"),
              IntLitF(int_of_integer(BigInt(100)), None)
            ))
          )
        )
      )),
      transitions = List(TransitionDeclFull("FooLifecycle", "Foo", "score", Nil))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"score\": st.integers(min_value=1, max_value=100)"),
      s"both constraints must apply (alias 'value > 0' AND field 'value <= 100'): ${foo.body}"
    )

  test("Copilot R2a: predicate with non-1 arity skips with reason (no invalid filter)"):
    val pr = PredicateDeclFull(
      name = "myCheck",
      params = List(
        ParamDeclFull("a", NamedTypeF("String")),
        ParamDeclFull("b", NamedTypeF("String"))
      ),
      body = BoolLitF(true)
    )
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(
          name = "Tag",
          typeExpr = NamedTypeF("String"),
          constraint = Some(
            CallF(IdentifierF("myCheck"), List(IdentifierF("value")))
          )
        )
      ),
      predicates = List(pr)
    )
    val tag = Strategies.forIR(ir).find(_.typeName == "Tag").getOrElse(fail("no strategy_tag"))
    assert(!tag.body.contains("my_check"), s"must not emit unsafe call: ${tag.body}")
    assert(tag.skipped.exists(_.contains("arity 2")), s"expected arity skip: ${tag.skipped}")

  test("Copilot R2b: predicate whose snake-cased name is a Python keyword skips with reason"):
    val pr = PredicateDeclFull(
      name = "class",
      params = List(ParamDeclFull("s", NamedTypeF("String"))),
      body = BoolLitF(true)
    )
    val ir = ServiceIRFull(
      name = "X",
      typeAliases = List(
        TypeAliasDeclFull(
          name = "Tag",
          typeExpr = NamedTypeF("String"),
          constraint = Some(
            CallF(IdentifierF("class"), List(IdentifierF("value")))
          )
        )
      ),
      predicates = List(pr)
    )
    val tag = Strategies.forIR(ir).find(_.typeName == "Tag").getOrElse(fail("no strategy_tag"))
    assert(
      !tag.body.contains(".filter(lambda v: class("),
      s"must not emit raw 'class' (Python keyword): ${tag.body}"
    )
    assert(
      tag.skipped.exists(_.contains("Python-reserved")),
      s"expected reserved-name skip: ${tag.skipped}"
    )
