package specrest.codegen.openapi

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class ClassifyOpenApiNamedTypeTest extends CatsEffectSuite:

  private def named(t: String): NamedTypeF                                   = NamedTypeF(t, None)
  private def alias(name: String, target: type_expr_full): TypeAliasDeclFull =
    TypeAliasDeclFull(name, target, None, None)
  private def enumD(name: String, values: List[String]): EnumDeclFull =
    EnumDeclFull(name, values, None)

  private def classify(
      name: String,
      aliases: List[(String, type_alias_decl_full)] = Nil,
      enums: List[(String, enum_decl_full)] = Nil,
      entityNames: List[String] = Nil
  ): openapi_named_kind =
    classifyOpenApiNamedType(name, aliases, enums, entityNames)

  test("openapiPrimitiveOf maps the 11 spec primitives correctly"):
    val cases = List(
      "String"   -> OpenApiPrimDef(List("string"), None),
      "Int"      -> OpenApiPrimDef(List("integer"), None),
      "Float"    -> OpenApiPrimDef(List("number"), None),
      "Bool"     -> OpenApiPrimDef(List("boolean"), None),
      "Boolean"  -> OpenApiPrimDef(List("boolean"), None),
      "DateTime" -> OpenApiPrimDef(List("string"), Some("date-time")),
      "Date"     -> OpenApiPrimDef(List("string"), Some("date")),
      "UUID"     -> OpenApiPrimDef(List("string"), Some("uuid")),
      "Decimal"  -> OpenApiPrimDef(List("string"), Some("decimal")),
      "Bytes"    -> OpenApiPrimDef(List("string"), Some("byte")),
      "Money"    -> OpenApiPrimDef(List("integer"), None)
    )
    cases.foreach: (specType, expected) =>
      assertEquals(openapiPrimitiveOf(specType), Some(expected), s"failed for $specType")
    assertEquals(openapiPrimitiveOf("NotAType"), None)

  test("classifies primitives directly"):
    assertEquals(classify("Int"), OntPrimitive(OpenApiPrimDef(List("integer"), None)))
    assertEquals(
      classify("DateTime"),
      OntPrimitive(OpenApiPrimDef(List("string"), Some("date-time")))
    )

  test("classifies enum NamedType"):
    val statusEnum = enumD("Status", List("OPEN", "CLOSED"))
    val result     = classify("Status", enums = List("Status" -> statusEnum))
    assertEquals(result, OntEnum(List("OPEN", "CLOSED")))

  test("classifies entity NamedType as entity-ref"):
    assertEquals(classify("User", entityNames = List("User")), OntEntityRef("User"))

  test("alias chain resolves through to leaf primitive"):
    val email   = alias("Email", named("String"))
    val tier2   = alias("EmailAlias", named("Email"))
    val aliases = List("Email" -> email, "EmailAlias" -> tier2)
    assertEquals(
      classify("EmailAlias", aliases = aliases),
      OntPrimitive(OpenApiPrimDef(List("string"), None))
    )

  test("alias chain to enum resolves to OntEnum"):
    val statusEnum  = enumD("Status", List("ON", "OFF"))
    val statusAlias = alias("StatusAlias", named("Status"))
    val result      = classify(
      "StatusAlias",
      aliases = List("StatusAlias" -> statusAlias),
      enums = List("Status" -> statusEnum)
    )
    assertEquals(result, OntEnum(List("ON", "OFF")))

  test("alias chain to entity resolves to OntEntityRef"):
    val userAlias = alias("UserAlias", named("User"))
    val result    = classify(
      "UserAlias",
      aliases = List("UserAlias" -> userAlias),
      entityNames = List("User")
    )
    assertEquals(result, OntEntityRef("User"))

  test("alias whose body is a structural type returns OntAliasToType"):
    val intSetAlias = alias("IntSet", SetTypeF(named("Int"), None))
    val result      = classify("IntSet", aliases = List("IntSet" -> intSetAlias))
    assertEquals(result, OntAliasToType(SetTypeF(named("Int"), None)))

  test("alias whose body is an Option returns OntAliasToType"):
    val optStr = alias("OptStr", OptionTypeF(named("String"), None))
    val result = classify("OptStr", aliases = List("OptStr" -> optStr))
    assertEquals(result, OntAliasToType(OptionTypeF(named("String"), None)))

  test("unknown name falls back to OntUnknown"):
    assertEquals(classify("NotARealThing"), OntUnknown())

  test("cyclic alias chain bottoms out at OntUnknown instead of looping"):
    val a = alias("A", named("B"))
    val b = alias("B", named("A"))
    assertEquals(
      classify("A", aliases = List("A" -> a, "B" -> b)),
      OntUnknown()
    )
