package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*
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

  private def named(t: String): NamedTypeF   = NamedTypeF(t, None)
  private def ident(n: String): IdentifierF  = IdentifierF(n, None)
  private def boolL(b: Boolean): BoolLitF    = BoolLitF(b, None)
  private def stringL(s: String): StringLitF = StringLitF(s, None)
  private def intL(n: Int): IntLitF          = IntLitF(BigInt(n), None)
  private def alias(
      name: String,
      t: type_expr,
      constraint: Option[expr] = None
  ): TypeAliasDeclFull =
    TypeAliasDeclFull(name, t, constraint, None)
  private def enumD(name: String, values: List[String]): EnumDeclFull =
    EnumDeclFull(name, values, None)
  private def fieldD(
      name: String,
      t: type_expr,
      constraint: Option[expr] = None
  ): FieldDeclFull =
    FieldDeclFull(name, t, constraint, None)
  private def entityD(
      name: String,
      fields: List[field_decl] = Nil,
      invariants: List[expr] = Nil,
      parent: Option[String] = None
  ): EntityDeclFull =
    EntityDeclFull(name, parent, fields, invariants, None)
  private def transitionD(
      name: String,
      entityName: String,
      fieldName: String,
      rules: List[transition_rule] = Nil
  ): TransitionDeclFull =
    TransitionDeclFull(name, entityName, fieldName, rules, None)
  private def predicateD(
      name: String,
      params: List[param_decl],
      body: expr
  ): PredicateDeclFull =
    PredicateDeclFull(name, params, body, None)
  private def paramD(name: String, t: type_expr): ParamDeclFull =
    ParamDeclFull(name, t, None)
  private def operationD(
      name: String,
      inputs: List[param_decl] = Nil,
      outputs: List[param_decl] = Nil,
      requires: List[expr] = Nil,
      ensures: List[expr] = Nil
  ): OperationDeclFull =
    OperationDeclFull(name, inputs, outputs, requires, ensures, None, None)
  private def conventionRule(
      target: String,
      property: String,
      value: expr,
      qualifier: Option[String] = None
  ): ConventionRuleFull =
    ConventionRuleFull(target, property, qualifier, parseConventionValue(property, value), None)
  private def conventions(rules: List[convention_rule]): ConventionsDeclFull =
    ConventionsDeclFull(rules, None)

  private def serviceIR(
      name: String = "X",
      typeAliases: List[type_alias_decl] = Nil,
      enums: List[enum_decl] = Nil,
      entities: List[entity_decl] = Nil,
      operations: List[operation_decl] = Nil,
      transitions: List[transition_decl] = Nil,
      invariants: List[invariant_decl] = Nil,
      predicates: List[predicate_decl] = Nil,
      conventions: Option[conventions_decl] = None
  ): ServiceIRFull =
    ServiceIRFull(
      a = name,
      b = Nil,
      c = entities,
      d = enums,
      e = typeAliases,
      f = None,
      g = operations,
      h = transitions,
      i = invariants,
      j = Nil,
      k = Nil,
      l = Nil,
      m = predicates,
      n = conventions,
      o = Nil,
      p = None
    )

  test("ShortCode (regex + length) → from_regex with len filter"):
    loadFixture("fixtures/spec/url_shortener.spec").map: ir =>
      val spec =
        Strategies.forIR(ir).find(_.typeName == "ShortCode").getOrElse(fail("no ShortCode"))
      assertEquals(spec.functionName, "strategy_short_code")
      assert(spec.body.contains("from_regex"), s"body=${spec.body}")
      assert(spec.body.contains("[a-zA-Z0-9]"), s"body=${spec.body}")
      assert(spec.body.contains("len(v) >= 6"), s"body=${spec.body}")
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
    val ir = serviceIR(
      typeAliases = List(alias("Code", named("String"))),
      enums = List(enumD("Color", List("RED", "BLUE")))
    )
    assertEquals(
      Strategies.expressionFor(named("String"), ir),
      StrategyExpr.Code("st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
    )
    assertEquals(
      Strategies.expressionFor(named("Int"), ir),
      StrategyExpr.Code("st.integers()")
    )
    assertEquals(
      Strategies.expressionFor(named("Bool"), ir),
      StrategyExpr.Code("st.booleans()")
    )
    assertEquals(
      Strategies.expressionFor(OptionTypeF(named("String"), None), ir),
      StrategyExpr.Code(
        "st.one_of(st.none(), st.text(alphabet=st.characters(exclude_characters=\"\\x00\")))"
      )
    )
    assertEquals(
      Strategies.expressionFor(SetTypeF(named("String"), None), ir),
      StrategyExpr.Code(
        "st.sets(st.text(alphabet=st.characters(exclude_characters=\"\\x00\")), max_size=5)"
      )
    )
    assertEquals(
      Strategies.expressionFor(SeqTypeF(named("Int"), None), ir),
      StrategyExpr.Code("st.lists(st.integers(), max_size=5)")
    )
    assertEquals(
      Strategies.expressionFor(named("Code"), ir),
      StrategyExpr.Code("strategy_code()")
    )
    assertEquals(
      Strategies.expressionFor(named("Color"), ir),
      StrategyExpr.Code("strategy_color()")
    )
    Strategies.expressionFor(named("UnknownType"), ir) match
      case StrategyExpr.Skip(r) => assert(r.contains("UnknownType"))
      case other                => fail(s"expected Skip, got $other")

  test("MapType / RelationType skip"):
    val ir   = serviceIR()
    val mapT = MapTypeF(named("String"), named("Int"), None)
    Strategies.expressionFor(mapT, ir) match
      case StrategyExpr.Skip(_) => ()
      case other                => fail(s"expected Skip, got $other")

  test("Int with no constraint → st.integers()"):
    val ir = serviceIR(
      typeAliases = List(alias("Counter", named("Int")))
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.integers()")

  test("Int with positive constraint via where value > 0"):
    val ir = serviceIR(
      typeAliases = List(
        alias(
          "PosInt",
          named("Int"),
          Some(BinaryOpF(BGt(), ident("value"), intL(0), None))
        )
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.integers(min_value=1)")

  test("Unhandled string constraint goes to skipped list, base strategy still produced"):
    val weird = BinaryOpF(
      BEq(),
      CallF(ident("custom_pred"), List(ident("value")), None),
      boolL(true),
      None
    )
    val ir = serviceIR(
      typeAliases = List(alias("Weird", named("String"), Some(weird)))
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
    assert(spec.skipped.nonEmpty)

  test("safe_counter has no type aliases or enums; forIR returns empty"):
    loadFixture("fixtures/spec/safe_counter.spec").map: ir =>
      assertEquals(Strategies.forIR(ir), Nil)

  test("convention override on alias replaces synthesized body and registers import"):
    val ir = serviceIR(
      typeAliases = List(alias("LongURL", named("String"))),
      conventions = Some(
        conventions(
          List(
            conventionRule(
              "LongURL",
              "strategy",
              stringL("tests.strategies_user:valid_url")
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
    val ir = serviceIR(
      enums = List(enumD("Color", List("RED", "BLUE"))),
      conventions = Some(
        conventions(
          List(
            conventionRule(
              "Color",
              "strategy",
              stringL("tests.strategies_user:strong_color")
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
    val ir = serviceIR(
      typeAliases = List(
        alias("Overridden", named("String")),
        alias(
          "PosInt",
          named("Int"),
          Some(BinaryOpF(BGt(), ident("value"), intL(0), None))
        )
      ),
      conventions = Some(
        conventions(
          List(conventionRule("Overridden", "strategy", stringL("m:s")))
        )
      )
    )
    val specs = Strategies.forIR(ir).map(s => s.typeName -> s).toMap
    assertEquals(specs("Overridden").body, "s()")
    assertEquals(specs("PosInt").body, "st.integers(min_value=1)")
    assertEquals(specs("PosInt").imports, Nil)

  test("malformed override (no colon) silently falls through to synth"):
    val ir = serviceIR(
      typeAliases = List(alias("Plain", named("String"))),
      conventions = Some(
        conventions(
          List(conventionRule("Plain", "strategy", stringL("not_a_module_symbol")))
        )
      )
    )
    val spec = Strategies.forIR(ir).head
    assertEquals(spec.body, "st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
    assertEquals(spec.imports, Nil)

  test("multiple regex constraints in `And` chain are all applied"):
    val constraint = BinaryOpF(
      BAnd(),
      MatchesF(ident("value"), "^[a-z]+$", None),
      MatchesF(ident("value"), ".{3,10}", None),
      None
    )
    val ir = serviceIR(
      typeAliases = List(alias("TwoRegex", named("String"), Some(constraint)))
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

  private lazy val emptyIR: ServiceIRFull = serviceIR("Demo")

  test("sensitive operation input is wrapped in redact() by default"):
    val expr = Strategies.expressionFor(
      named("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      TestStrategyOverrides.Empty
    )
    assertEquals(
      expr,
      StrategyExpr.Code("redact(st.text(alphabet=st.characters(exclude_characters=\"\\x00\")))")
    )

  test("non-sensitive operation input is unwrapped"):
    val expr = Strategies.expressionFor(
      named("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "display_name"),
      TestStrategyOverrides.Empty
    )
    assertEquals(
      expr,
      StrategyExpr.Code("st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
    )

  test("override 'live' on sensitive input removes redact wrapper"):
    val overrides = TestStrategyOverrides(
      perOperation = Map(("Register", "password") -> "live"),
      perEntityField = Map.empty
    )
    val expr = Strategies.expressionFor(
      named("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(
      expr,
      StrategyExpr.Code("st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
    )

  test("override 'redacted' replaces strategy with placeholder"):
    val overrides = TestStrategyOverrides(
      perOperation = Map(("Register", "password") -> "redacted"),
      perEntityField = Map.empty
    )
    val expr = Strategies.expressionFor(
      named("String"),
      emptyIR,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(expr, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("entity-field override broadcasts to operation inputs by name"):
    val ir = emptyIR.copy(
      c = List(
        entityD(
          "User",
          fields = List(fieldD("password", named("String")))
        )
      ),
      n = Some(
        conventions(
          List(
            conventionRule(
              "User",
              "test_strategy",
              stringL("redacted"),
              qualifier = Some("password")
            )
          )
        )
      )
    )
    val overrides = TestStrategyOverrides.from(ir)
    val expr = Strategies.expressionFor(
      named("String"),
      ir,
      StrategyCtx.OperationInput("Login", "password"),
      overrides
    )
    assertEquals(expr, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("operation-level override beats entity-level override"):
    val ir = emptyIR.copy(
      c = List(
        entityD("User", fields = List(fieldD("password", named("String"))))
      ),
      g = List(
        operationD("Register"),
        operationD("Login")
      ),
      n = Some(
        conventions(
          List(
            conventionRule(
              "User",
              "test_strategy",
              stringL("redacted"),
              qualifier = Some("password")
            ),
            conventionRule(
              "Register",
              "test_strategy",
              stringL("live"),
              qualifier = Some("password")
            )
          )
        )
      )
    )
    val overrides = TestStrategyOverrides.from(ir)
    val exprRegister = Strategies.expressionFor(
      named("String"),
      ir,
      StrategyCtx.OperationInput("Register", "password"),
      overrides
    )
    assertEquals(
      exprRegister,
      StrategyExpr.Code("st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
    )
    val exprLogin = Strategies.expressionFor(
      named("String"),
      ir,
      StrategyCtx.OperationInput("Login", "password"),
      overrides
    )
    assertEquals(exprLogin, StrategyExpr.Code("""st.just("***REDACTED***")"""))

  test("anonymous ctx never wraps even for sensitive-named types"):
    val expr = Strategies.expressionFor(named("String"), emptyIR)
    assertEquals(
      expr,
      StrategyExpr.Code("st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))")
    )

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
    val ir = serviceIR(
      entities = List(
        entityD(
          "Foo",
          fields = List(
            fieldD("id", named("Int")),
            fieldD("stuff", MapTypeF(named("String"), named("Int"), None))
          )
        )
      ),
      transitions = List(transitionD("FooLifecycle", "Foo", "id"))
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
    val ir = serviceIR(
      entities = List(
        entityD(
          "Foo",
          fields = List(
            fieldD("id", named("Int")),
            fieldD(
              "tags",
              OptionTypeF(MapTypeF(named("String"), named("Int"), None), None)
            )
          )
        )
      ),
      transitions = List(transitionD("FooLifecycle", "Foo", "id"))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"tags\": st.none()"),
      s"Option[Skip] should yield st.none(): ${foo.body}"
    )
    assert(!foo.skipped.exists(_.contains("'tags'")), s"tags should not skip: ${foo.skipped}")

  test("M5.9 fix C: sensitive entity field is wrapped in redact() in entity strategy"):
    val ir = serviceIR(
      entities = List(
        entityD(
          "User",
          fields = List(
            fieldD("id", named("Int")),
            fieldD("password_hash", named("String"))
          )
        )
      ),
      transitions = List(transitionD("UserLifecycle", "User", "id"))
    )
    val user = Strategies.forIR(ir).find(_.typeName == "User").getOrElse(fail("no strategy_user"))
    assert(
      user.body.contains("\"password_hash\": redact("),
      s"sensitive password_hash must be redact-wrapped: ${user.body}"
    )

  test("M5.9 fix C: test_strategy='live' override removes redact in entity strategy"):
    val ir = serviceIR(
      entities = List(
        entityD(
          "User",
          fields = List(
            fieldD("id", named("Int")),
            fieldD("password", named("String"))
          )
        )
      ),
      transitions = List(transitionD("UserLifecycle", "User", "id")),
      conventions = Some(
        conventions(
          List(
            conventionRule(
              "User",
              "test_strategy",
              stringL("live"),
              qualifier = Some("password")
            )
          )
        )
      )
    )
    val user = Strategies.forIR(ir).find(_.typeName == "User").getOrElse(fail("no strategy_user"))
    assert(
      !user.body.contains("\"password\": redact("),
      s"live override must remove redact: ${user.body}"
    )
    assert(
      user.body.contains(
        "\"password\": st.text(alphabet=st.characters(exclude_characters=\"\\x00\"))"
      ),
      s"live override should emit bare strategy: ${user.body}"
    )

  test("M5.9 fix F: alias of DateTime in entity field produces isoformat-mapped strategy"):
    val ir = serviceIR(
      typeAliases = List(alias("CreatedAt", named("DateTime"))),
      entities = List(
        entityD(
          "Foo",
          fields = List(
            fieldD("id", named("Int")),
            fieldD("at", named("CreatedAt"))
          )
        )
      ),
      transitions = List(transitionD("FooLifecycle", "Foo", "id"))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"at\": st.datetimes().map(lambda d: d.isoformat())"),
      s"alias of DateTime should resolve to JSON-friendly isoformat: ${foo.body}"
    )

  test("M5.9 fix J: entity with no seedable fields emits valid st.fixed_dictionaries({})"):
    val ir = serviceIR(
      entities = List(
        entityD(
          "Foo",
          fields = List(
            fieldD("stuff", MapTypeF(named("String"), named("Int"), None))
          )
        )
      ),
      transitions = List(transitionD("FooLifecycle", "Foo", "stuff"))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assertEquals(foo.body, "st.fixed_dictionaries({})")
    assert(foo.skipped.exists(_.contains("'stuff'")), s"expected skip note: ${foo.skipped}")

  test("M5.9 fix K: alias-of-Int with constraint AND field-level constraint are combined"):
    val ir = serviceIR(
      typeAliases = List(
        alias(
          "PosInt",
          named("Int"),
          Some(BinaryOpF(BGt(), ident("value"), intL(0), None))
        )
      ),
      entities = List(
        entityD(
          "Foo",
          fields = List(
            fieldD(
              "score",
              named("PosInt"),
              constraint = Some(BinaryOpF(BLe(), ident("value"), intL(100), None))
            )
          )
        )
      ),
      transitions = List(transitionD("FooLifecycle", "Foo", "score"))
    )
    val foo = Strategies.forIR(ir).find(_.typeName == "Foo").getOrElse(fail("no strategy_foo"))
    assert(
      foo.body.contains("\"score\": st.integers(min_value=1, max_value=100)"),
      s"both constraints must apply (alias 'value > 0' AND field 'value <= 100'): ${foo.body}"
    )

  test("Copilot R2a: predicate with non-1 arity skips with reason (no invalid filter)"):
    val pr = predicateD(
      "myCheck",
      params = List(
        paramD("a", named("String")),
        paramD("b", named("String"))
      ),
      body = boolL(true)
    )
    val ir = serviceIR(
      typeAliases = List(
        alias(
          "Tag",
          named("String"),
          Some(CallF(ident("myCheck"), List(ident("value")), None))
        )
      ),
      predicates = List(pr)
    )
    val tag = Strategies.forIR(ir).find(_.typeName == "Tag").getOrElse(fail("no strategy_tag"))
    assert(!tag.body.contains("my_check"), s"must not emit unsafe call: ${tag.body}")
    assert(tag.skipped.exists(_.contains("arity 2")), s"expected arity skip: ${tag.skipped}")

  test("Copilot R2b: predicate whose snake-cased name is a Python keyword skips with reason"):
    val pr = predicateD(
      "class",
      params = List(paramD("s", named("String"))),
      body = boolL(true)
    )
    val ir = serviceIR(
      typeAliases = List(
        alias(
          "Tag",
          named("String"),
          Some(CallF(ident("class"), List(ident("value")), None))
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
