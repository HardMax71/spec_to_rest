package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class StrategyOverrideTest extends CatsEffectSuite:

  private def rule(target: String, property: String, value: expr): ConventionRuleFull =
    ConventionRuleFull(target, property, None, parseConventionValue(property, value), None)

  private def stringRule(target: String, property: String, v: String): ConventionRuleFull =
    rule(target, property, StringLitF(v, None))

  private def baseIR(
      operations: List[operation_decl] = Nil,
      entities: List[entity_decl] = Nil,
      typeAliases: List[type_alias_decl] = Nil,
      enums: List[enum_decl] = Nil,
      rules: List[convention_rule] = Nil
  ): ServiceIRFull =
    ServiceIRFull(
      a = "Demo",
      b = Nil,
      c = entities,
      d = enums,
      e = typeAliases,
      f = None,
      g = operations,
      h = Nil,
      i = Nil,
      j = Nil,
      k = Nil,
      l = Nil,
      m = Nil,
      n = if rules.isEmpty then None else Some(ConventionsDeclFull(rules, None)),
      o = Nil,
      p = None
    )

  test("strategy on type alias is accepted"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDeclFull("LongURL", NamedTypeF("String", None), None, None)),
      rules = List(stringRule("LongURL", "strategy", "tests.strategies_user:valid_url"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assertEquals(diagnostics, Nil)

  test("strategy on enum is accepted"):
    val ir = baseIR(
      enums = List(EnumDeclFull("Color", List("RED", "BLUE"), None)),
      rules = List(stringRule("Color", "strategy", "tests.strategies_user:valid_color"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assertEquals(diagnostics, Nil)

  test("strategy on operation is rejected"):
    val ir = baseIR(
      operations = List(OperationDeclFull("Shorten", Nil, Nil, Nil, Nil, None, None)),
      rules = List(stringRule("Shorten", "strategy", "tests.strategies_user:foo"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d =>
        d.message.contains("type aliases and enums") && d.target == "Shorten"
      ),
      s"diagnostics=$diagnostics"
    )

  test("strategy on entity is rejected"):
    val ir = baseIR(
      entities = List(EntityDeclFull("Url", None, Nil, Nil, None)),
      rules = List(stringRule("Url", "strategy", "tests.strategies_user:foo"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("type aliases and enums") && d.target == "Url"),
      s"diagnostics=$diagnostics"
    )

  test("non-strategy property on type alias is rejected"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDeclFull("LongURL", NamedTypeF("String", None), None, None)),
      rules = List(stringRule("LongURL", "http_method", "GET"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("only 'strategy' applies")),
      s"diagnostics=$diagnostics"
    )

  test("strategy without colon is rejected"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDeclFull("LongURL", NamedTypeF("String", None), None, None)),
      rules = List(stringRule("LongURL", "strategy", "no_colon_here"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("module:symbol")),
      s"diagnostics=$diagnostics"
    )

  test("strategy with empty module or symbol is rejected"):
    val ir = baseIR(
      typeAliases = List(
        TypeAliasDeclFull("A", NamedTypeF("String", None), None, None),
        TypeAliasDeclFull("B", NamedTypeF("String", None), None, None)
      ),
      rules = List(
        stringRule("A", "strategy", ":symbol_only"),
        stringRule("B", "strategy", "module_only:")
      )
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assertEquals(diagnostics.count(_.property == "strategy"), 2, s"diagnostics=$diagnostics")

  test("strategy with non-string value is rejected"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDeclFull("LongURL", NamedTypeF("String", None), None, None)),
      rules = List(rule("LongURL", "strategy", IntLitF(BigInt(42), None)))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("expected a string") && d.target == "LongURL"),
      s"diagnostics=$diagnostics"
    )

  test("unknown target name (not op/entity/alias/enum) reports updated message"):
    val ir = baseIR(
      rules = List(stringRule("MysteryThing", "strategy", "m:s"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("type alias, or enum")),
      s"diagnostics=$diagnostics"
    )
