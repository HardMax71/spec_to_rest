package specrest.convention

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class ClassifyColumnTypeTest extends CatsEffectSuite:

  private def named(t: String): NamedTypeF = NamedTypeF(t, None)
  private def alias(name: String, target: type_expr): TypeAliasDeclFull =
    TypeAliasDeclFull(name, target, None, None)
  private def enumD(name: String, values: List[String]): EnumDeclFull =
    EnumDeclFull(name, values, None)

  private def classify(
      ty: type_expr,
      aliases: List[(String, TypeAliasDeclFull)] = Nil,
      enums: List[(String, EnumDeclFull)] = Nil,
      entityNames: List[String] = Nil
  ): (column_kind, Boolean) =
    classifyColumnType(ty, aliases, enums, entityNames) match
      case ClassifiedColumn(k, n) => (k, n)

  test("primitives map through primitiveTypeToSql"):
    val cases = List(
      "String"   -> "TEXT",
      "Int"      -> "INTEGER",
      "Float"    -> "DOUBLE PRECISION",
      "Bool"     -> "BOOLEAN",
      "Boolean"  -> "BOOLEAN",
      "DateTime" -> "TIMESTAMPTZ",
      "Date"     -> "DATE",
      "UUID"     -> "UUID",
      "Decimal"  -> "NUMERIC(19,4)",
      "Bytes"    -> "BYTEA",
      "Money"    -> "INTEGER"
    )
    cases.foreach: (specType, sqlType) =>
      val (k, nullable) = classify(named(specType))
      assertEquals(k, CkPrim(sqlType), s"failed for $specType")
      assertEquals(nullable, false)

  test("OptionType sets nullable=true on the inner classification"):
    val (k, nullable) = classify(OptionTypeF(named("Int"), None))
    assertEquals(k, CkPrim("INTEGER"))
    assertEquals(nullable, true)

  test("nested Option layers collapse to a single nullable=true"):
    val nested        = OptionTypeF(OptionTypeF(named("String"), None), None)
    val (k, nullable) = classify(nested)
    assertEquals(k, CkPrim("TEXT"))
    assertEquals(nullable, true)

  test("enum NamedType resolves to CkEnum carrying the values"):
    val statusEnum    = enumD("Status", List("OPEN", "CLOSED"))
    val (k, nullable) = classify(named("Status"), enums = List("Status" -> statusEnum))
    assertEquals(k, CkEnum(List("OPEN", "CLOSED")))
    assertEquals(nullable, false)

  test("entity NamedType resolves to CkEntityRef carrying the entity name"):
    val (k, nullable) = classify(named("User"), entityNames = List("User"))
    assertEquals(k, CkEntityRef("User"))
    assertEquals(nullable, false)

  test("alias chain resolves to the leaf classification"):
    val email    = alias("Email", named("String"))
    val tier2    = alias("EmailAlias", named("Email"))
    val aliases  = List("Email" -> email, "EmailAlias" -> tier2)
    val (k1, n1) = classify(named("Email"), aliases = aliases)
    assertEquals(k1, CkPrim("TEXT"))
    assertEquals(n1, false)
    val (k2, n2) = classify(named("EmailAlias"), aliases = aliases)
    assertEquals(k2, CkPrim("TEXT"))
    assertEquals(n2, false)

  test("alias chain to enum resolves to CkEnum"):
    val statusEnum  = enumD("Status", List("ON", "OFF"))
    val statusAlias = alias("StatusAlias", named("Status"))
    val (k, _) = classify(
      named("StatusAlias"),
      aliases = List("StatusAlias" -> statusAlias),
      enums = List("Status" -> statusEnum)
    )
    assertEquals(k, CkEnum(List("ON", "OFF")))

  test("Option of alias preserves nullable across the chain"):
    val email = alias("Email", named("String"))
    val (k, nullable) = classify(
      OptionTypeF(named("Email"), None),
      aliases = List("Email" -> email)
    )
    assertEquals(k, CkPrim("TEXT"))
    assertEquals(nullable, true)

  test("SetType and SeqType classify as CkJsonArray"):
    val (setK, _) = classify(SetTypeF(named("Int"), None))
    assertEquals(setK, CkJsonArray())
    val (seqK, _) = classify(SeqTypeF(named("String"), None))
    assertEquals(seqK, CkJsonArray())

  test("MapType classifies as CkJsonObject"):
    val (k, _) = classify(MapTypeF(named("String"), named("Int"), None))
    assertEquals(k, CkJsonObject())

  test("RelationType classifies as CkRelation"):
    val rel    = RelationTypeF(named("User"), MultOne(), named("Order"), None)
    val (k, _) = classify(rel)
    assertEquals(k, CkRelation())

  test("unresolved NamedType falls back to CkUnknown"):
    val (k, _) = classify(named("NotARealType"))
    assertEquals(k, CkUnknown())

  test("cyclic alias chain bottoms out at CkUnknown instead of looping"):
    val a      = alias("A", named("B"))
    val b      = alias("B", named("A"))
    val (k, _) = classify(named("A"), aliases = List("A" -> a, "B" -> b))
    assertEquals(k, CkUnknown())
