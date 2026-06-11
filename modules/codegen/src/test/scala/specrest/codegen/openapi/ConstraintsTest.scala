package specrest.codegen.openapi

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class ConstraintsTest extends CatsEffectSuite:

  private def named(t: String): NamedTypeF = NamedTypeF(t, None)

  private def alias(name: String, target: type_expr): TypeAliasDeclFull =
    TypeAliasDeclFull(name, target, None, None)

  private def enumD(name: String, values: List[String]): EnumDeclFull =
    EnumDeclFull(name, values, None)

  test("findEnumValuesInType resolves enum reached through an alias chain"):
    val statusEnum = enumD("Status", List("OPEN", "CLOSED"))
    val tier1      = alias("StatusAlias", named("Status"))
    val tier2      = alias("StatusAliasAlias", named("StatusAlias"))
    val aliasAList = List("StatusAlias" -> tier1, "StatusAliasAlias" -> tier2)
    val enumAList  = List("Status" -> statusEnum)

    val direct = findEnumValuesInType(named("Status"), aliasAList, enumAList)
    assertEquals(direct, Some(List("OPEN", "CLOSED")))

    val oneHop = findEnumValuesInType(named("StatusAlias"), aliasAList, enumAList)
    assertEquals(oneHop, Some(List("OPEN", "CLOSED")))

    val twoHop = findEnumValuesInType(named("StatusAliasAlias"), aliasAList, enumAList)
    assertEquals(twoHop, Some(List("OPEN", "CLOSED")))

  test("findEnumValuesInType strips Option wrappers before resolution"):
    val statusEnum = enumD("Status", List("OPEN", "CLOSED"))
    val nested     = OptionTypeF(OptionTypeF(named("Status"), None), None)
    val res        = findEnumValuesInType(nested, Nil, List("Status" -> statusEnum))
    assertEquals(res, Some(List("OPEN", "CLOSED")))

  test("findEnumValuesInType returns None when alias chain bottoms out at primitive"):
    val emailAlias = alias("Email", named("String"))
    val res        = findEnumValuesInType(named("Email"), List("Email" -> emailAlias), Nil)
    assertEquals(res, None)

  test("findEnumValuesInType is cycle-safe (returns None instead of looping)"):
    val a   = alias("A", named("B"))
    val b   = alias("B", named("A"))
    val res = findEnumValuesInType(named("A"), List("A" -> a, "B" -> b), Nil)
    assertEquals(res, None)
