package specrest.testgen

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

class EnumValuesForFieldTest extends CatsEffectSuite:

  private def named(t: String): NamedTypeF = NamedTypeF(t, None)

  private def alias(name: String, target: type_expr_full): TypeAliasDeclFull =
    TypeAliasDeclFull(name, target, None, None)

  private def enumD(name: String, values: List[String]): EnumDeclFull =
    EnumDeclFull(name, values, None)

  private def field(name: String, t: type_expr_full): FieldDeclFull =
    FieldDeclFull(name, t, None, None)

  test("enumValuesForField resolves directly through an alias chain"):
    val statusEnum = enumD("Status", List("OPEN", "CLOSED"))
    val tier1      = alias("StatusAlias", named("Status"))
    val tier2      = alias("StatusAliasAlias", named("StatusAlias"))
    val aliases    = List(tier1, tier2)
    val enums      = List(statusEnum)

    val direct = SpecRestGenerated.enumValuesForField(field("f", named("Status")), enums, aliases)
    assertEquals(direct, Some(List("OPEN", "CLOSED")))

    val oneHop =
      SpecRestGenerated.enumValuesForField(field("f", named("StatusAlias")), enums, aliases)
    assertEquals(oneHop, Some(List("OPEN", "CLOSED")))

    val twoHop =
      SpecRestGenerated.enumValuesForField(field("f", named("StatusAliasAlias")), enums, aliases)
    assertEquals(twoHop, Some(List("OPEN", "CLOSED")))

  test("enumValuesForField returns None when chain bottoms out at a non-enum"):
    val emailAlias = alias("Email", named("String"))
    val res =
      SpecRestGenerated.enumValuesForField(field("f", named("Email")), Nil, List(emailAlias))
    assertEquals(res, None)

  test("enumValuesForField is cycle-safe on `type A = B; type B = A` (returns None)"):
    val a   = alias("A", named("B"))
    val b   = alias("B", named("A"))
    val res = SpecRestGenerated.enumValuesForField(field("f", named("A")), Nil, List(a, b))
    assertEquals(res, None)
