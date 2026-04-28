package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.*

class TestStrategyOverrideTest extends CatsEffectSuite:

  private def rule(
      target: String,
      property: String,
      qualifier: Option[String],
      value: Expr
  ): ConventionRule =
    ConventionRule(target = target, property = property, qualifier = qualifier, value = value)

  private def stringRule(
      target: String,
      property: String,
      qualifier: Option[String],
      v: String
  ): ConventionRule =
    rule(target, property, qualifier, Expr.StringLit(v))

  private def baseIR(
      operations: List[OperationDecl] = Nil,
      entities: List[EntityDecl] = Nil,
      rules: List[ConventionRule] = Nil
  ): ServiceIR =
    ServiceIR(
      name = "Demo",
      operations = operations,
      entities = entities,
      conventions = if rules.isEmpty then None else Some(ConventionsDecl(rules))
    )

  private val userEntity = EntityDecl(
    name = "User",
    fields = List(
      FieldDecl("id", TypeExpr.NamedType("Id")),
      FieldDecl("password_hash", TypeExpr.NamedType("String")),
      FieldDecl("email", TypeExpr.NamedType("String"))
    )
  )

  private val registerOp = OperationDecl(
    name = "Register",
    inputs = List(
      ParamDecl("email", TypeExpr.NamedType("String")),
      ParamDecl("password", TypeExpr.NamedType("String"))
    )
  )

  test("test_strategy on operation input with redacted is accepted"):
    val ir = baseIR(
      operations = List(registerOp),
      rules = List(stringRule("Register", "test_strategy", Some("password"), "redacted"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assertEquals(diagnostics, Nil)

  test("test_strategy on entity field with live is accepted"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", Some("password_hash"), "live"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assertEquals(diagnostics, Nil)

  test("test_strategy without qualifier is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", None, "redacted"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("requires a field qualifier")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with unknown field on entity is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", Some("ghost_field"), "redacted"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("no field named 'ghost_field'")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with unknown field on operation is rejected"):
    val ir = baseIR(
      operations = List(registerOp),
      rules = List(stringRule("Register", "test_strategy", Some("ghost_input"), "redacted"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("no field named 'ghost_input'")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with bad value is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", Some("password_hash"), "bogus"))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("expected \"live\" or \"redacted\"")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with non-string value is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(rule("User", "test_strategy", Some("password_hash"), Expr.IntLit(42)))
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("expected a string")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy on type alias is rejected"):
    val ir = baseIR(
      rules = List(stringRule("PasswordHash", "test_strategy", Some("value"), "redacted"))
    )
    val ir2 =
      ir.copy(typeAliases = List(TypeAliasDecl("PasswordHash", TypeExpr.NamedType("String"))))
    val diagnostics = Validate.validateConventions(ir2.conventions, ir2)
    assert(
      diagnostics.exists(d => d.message.contains("applies to operations and entities")),
      s"diagnostics=$diagnostics"
    )

  test("two test_strategy rules for different fields on same entity are not duplicates"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("User", "test_strategy", Some("email"), "live")
      )
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assertEquals(diagnostics.filter(_.message.contains("duplicate")), Nil)

  test("two test_strategy rules for the same field are duplicates"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("User", "test_strategy", Some("password_hash"), "live")
      )
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("duplicate")),
      s"diagnostics=$diagnostics"
    )

  test("conflicting test_strategy across different entities sharing a field name errors"):
    val adminEntity = EntityDecl(
      name = "Admin",
      fields = List(FieldDecl("password_hash", TypeExpr.NamedType("String")))
    )
    val ir = baseIR(
      entities = List(userEntity, adminEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("Admin", "test_strategy", Some("password_hash"), "live")
      )
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("conflicting test_strategy")),
      s"diagnostics=$diagnostics"
    )

  test("agreeing test_strategy across entities with same field name does not error"):
    val adminEntity = EntityDecl(
      name = "Admin",
      fields = List(FieldDecl("password_hash", TypeExpr.NamedType("String")))
    )
    val ir = baseIR(
      entities = List(userEntity, adminEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("Admin", "test_strategy", Some("password_hash"), "redacted")
      )
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assertEquals(diagnostics, Nil)

  test(
    "same-entity duplicate test_strategy emits only the duplicate error, not a cross-entity collision"
  ):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("User", "test_strategy", Some("password_hash"), "live")
      )
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("duplicate")),
      s"expected duplicate diagnostic; got=$diagnostics"
    )
    assert(
      !diagnostics.exists(d => d.message.contains("across entities")),
      s"single-entity dup must not report a cross-entity collision; got=$diagnostics"
    )

  test("multiple http_method rules with different string qualifiers do not bypass dup detection"):
    // Regression: previously the dup-key included the qualifier for ALL rules,
    // letting duplicate http_method rules with cosmetic string-qualifier diffs co-exist.
    val ir = baseIR(
      operations = List(OperationDecl(name = "Login")),
      rules = List(
        rule("Login", "http_method", Some("x"), Expr.StringLit("POST")),
        rule("Login", "http_method", Some("y"), Expr.StringLit("GET"))
      )
    )
    val diagnostics = Validate.validateConventions(ir.conventions, ir)
    assert(
      diagnostics.exists(d => d.message.contains("duplicate")),
      s"diagnostics=$diagnostics"
    )
