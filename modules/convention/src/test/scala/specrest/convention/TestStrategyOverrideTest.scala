package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class TestStrategyOverrideTest extends CatsEffectSuite:

  private def rule(
      target: String,
      property: String,
      qualifier: Option[String],
      value: expr
  ): ConventionRuleFull =
    ConventionRuleFull(target, property, qualifier, parseConventionValue(property, value), None)

  private def stringRule(
      target: String,
      property: String,
      qualifier: Option[String],
      v: String
  ): ConventionRuleFull =
    rule(target, property, qualifier, StringLitF(v, None))

  private def baseIR(
      operations: List[operation_decl] = Nil,
      entities: List[entity_decl] = Nil,
      rules: List[convention_rule] = Nil,
      typeAliases: List[type_alias_decl] = Nil
  ): ServiceIRFull =
    ServiceIRFull(
      a = "Demo",
      b = Nil,
      c = entities,
      d = Nil,
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
      o = None
    )

  private val userEntity = EntityDeclFull(
    "User",
    None,
    List(
      FieldDeclFull("id", NamedTypeF("Id", None), None, None),
      FieldDeclFull("password_hash", NamedTypeF("String", None), None, None),
      FieldDeclFull("email", NamedTypeF("String", None), None, None)
    ),
    Nil,
    None
  )

  private val registerOp = OperationDeclFull(
    "Register",
    List(
      ParamDeclFull("email", NamedTypeF("String", None), None),
      ParamDeclFull("password", NamedTypeF("String", None), None)
    ),
    Nil,
    Nil,
    Nil,
    None
  )

  test("test_strategy on operation input with redacted is accepted"):
    val ir = baseIR(
      operations = List(registerOp),
      rules = List(stringRule("Register", "test_strategy", Some("password"), "redacted"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assertEquals(diagnostics, Nil)

  test("test_strategy on entity field with live is accepted"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", Some("password_hash"), "live"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assertEquals(diagnostics, Nil)

  test("test_strategy without qualifier is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", None, "redacted"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("requires a field qualifier")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with unknown field on entity is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", Some("ghost_field"), "redacted"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("no field named 'ghost_field'")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with unknown field on operation is rejected"):
    val ir = baseIR(
      operations = List(registerOp),
      rules = List(stringRule("Register", "test_strategy", Some("ghost_input"), "redacted"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("no field named 'ghost_input'")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with bad value is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(stringRule("User", "test_strategy", Some("password_hash"), "bogus"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("expected \"live\" or \"redacted\"")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy with non-string value is rejected"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(rule(
        "User",
        "test_strategy",
        Some("password_hash"),
        IntLitF(BigInt(42), None)
      ))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("expected a string")),
      s"diagnostics=$diagnostics"
    )

  test("test_strategy on type alias is rejected"):
    val ir = baseIR(
      typeAliases = List(TypeAliasDeclFull("PasswordHash", NamedTypeF("String", None), None, None)),
      rules = List(stringRule("PasswordHash", "test_strategy", Some("value"), "redacted"))
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
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
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assertEquals(diagnostics.filter(_.message.contains("duplicate")), Nil)

  test("two test_strategy rules for the same field are duplicates"):
    val ir = baseIR(
      entities = List(userEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("User", "test_strategy", Some("password_hash"), "live")
      )
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("duplicate")),
      s"diagnostics=$diagnostics"
    )

  test("conflicting test_strategy across different entities sharing a field name errors"):
    val adminEntity = EntityDeclFull(
      "Admin",
      None,
      List(FieldDeclFull("password_hash", NamedTypeF("String", None), None, None)),
      Nil,
      None
    )
    val ir = baseIR(
      entities = List(userEntity, adminEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("Admin", "test_strategy", Some("password_hash"), "live")
      )
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("conflicting test_strategy")),
      s"diagnostics=$diagnostics"
    )

  test("agreeing test_strategy across entities with same field name does not error"):
    val adminEntity = EntityDeclFull(
      "Admin",
      None,
      List(FieldDeclFull("password_hash", NamedTypeF("String", None), None, None)),
      Nil,
      None
    )
    val ir = baseIR(
      entities = List(userEntity, adminEntity),
      rules = List(
        stringRule("User", "test_strategy", Some("password_hash"), "redacted"),
        stringRule("Admin", "test_strategy", Some("password_hash"), "redacted")
      )
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
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
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("duplicate")),
      s"expected duplicate diagnostic; got=$diagnostics"
    )
    assert(
      !diagnostics.exists(d => d.message.contains("across entities")),
      s"single-entity dup must not report a cross-entity collision; got=$diagnostics"
    )

  test("multiple http_method rules with different string qualifiers do not bypass dup detection"):
    val ir = baseIR(
      operations = List(OperationDeclFull("Login", Nil, Nil, Nil, Nil, None)),
      rules = List(
        rule("Login", "http_method", Some("x"), StringLitF("POST", None)),
        rule("Login", "http_method", Some("y"), StringLitF("GET", None))
      )
    )
    val diagnostics = Validate.validateConventions(svcConventions(ir), ir)
    assert(
      diagnostics.exists(d => d.message.contains("duplicate")),
      s"diagnostics=$diagnostics"
    )
