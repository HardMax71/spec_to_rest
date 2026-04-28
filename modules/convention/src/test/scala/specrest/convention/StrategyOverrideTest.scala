package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.*

class StrategyOverrideTest extends CatsEffectSuite:

  private def rule(target: String, property: String, value: Expr): ConventionRule =
    ConventionRule(target = target, property = property, qualifier = None, value = value)

  private def stringRule(target: String, property: String, v: String): ConventionRule =
    rule(target, property, Expr.StringLit(v))

  private def baseIR(
      operations: List[OperationDecl] = Nil,
      entities: List[EntityDecl] = Nil,
      typeAliases: List[TypeAliasDecl] = Nil,
      enums: List[EnumDecl] = Nil,
      rules: List[ConventionRule] = Nil
  ): ServiceIR =
    ServiceIR(
      name = "Demo",
      operations = operations,
      entities = entities,
      typeAliases = typeAliases,
      enums = enums,
      conventions = if rules.isEmpty then None else Some(ConventionsDecl(rules))
    )

  test("strategy on type alias is accepted"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDecl("LongURL", TypeExpr.NamedType("String"))),
      rules = List(stringRule("LongURL", "strategy", "tests.strategies_user:valid_url"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assertEquals(diagnostics, Nil)

  test("strategy on enum is accepted"):
    val ir = baseIR(
      enums = List(EnumDecl("Color", List("RED", "BLUE"))),
      rules = List(stringRule("Color", "strategy", "tests.strategies_user:valid_color"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assertEquals(diagnostics, Nil)

  test("strategy on operation is rejected"):
    val ir = baseIR(
      operations = List(OperationDecl(name = "Shorten")),
      rules = List(stringRule("Shorten", "strategy", "tests.strategies_user:foo"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d =>
        d.message.contains("type aliases and enums") && d.target == "Shorten"
      ),
      s"diagnostics=$diagnostics"
    )

  test("strategy on entity is rejected"):
    val ir = baseIR(
      entities = List(EntityDecl(name = "Url")),
      rules = List(stringRule("Url", "strategy", "tests.strategies_user:foo"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("type aliases and enums") && d.target == "Url"),
      s"diagnostics=$diagnostics"
    )

  test("non-strategy property on type alias is rejected"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDecl("LongURL", TypeExpr.NamedType("String"))),
      rules = List(stringRule("LongURL", "http_method", "GET"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("only 'strategy' applies")),
      s"diagnostics=$diagnostics"
    )

  test("strategy without colon is rejected"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDecl("LongURL", TypeExpr.NamedType("String"))),
      rules = List(stringRule("LongURL", "strategy", "no_colon_here"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("module:symbol")),
      s"diagnostics=$diagnostics"
    )

  test("strategy with empty module or symbol is rejected"):
    val ir = baseIR(
      typeAliases = List(
        TypeAliasDecl("A", TypeExpr.NamedType("String")),
        TypeAliasDecl("B", TypeExpr.NamedType("String"))
      ),
      rules = List(
        stringRule("A", "strategy", ":symbol_only"),
        stringRule("B", "strategy", "module_only:")
      )
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assertEquals(diagnostics.count(_.property == "strategy"), 2, s"diagnostics=$diagnostics")

  test("strategy with non-string value is rejected"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDecl("LongURL", TypeExpr.NamedType("String"))),
      rules = List(rule("LongURL", "strategy", Expr.IntLit(42)))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("expected a string") && d.target == "LongURL"),
      s"diagnostics=$diagnostics"
    )

  test("unknown target name (not op/entity/alias/enum) reports updated message"):
    val ir = baseIR(
      rules = List(stringRule("MysteryThing", "strategy", "m:s"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("type alias, or enum")),
      s"diagnostics=$diagnostics"
    )
