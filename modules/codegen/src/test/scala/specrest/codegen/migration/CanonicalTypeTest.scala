package specrest.codegen.migration

import munit.CatsEffectSuite

class CanonicalTypeTest extends CatsEffectSuite:

  private def parseCases: List[(String, CanonicalType)] = List(
    "TEXT"             -> CanonicalType.Text,
    "INTEGER"          -> CanonicalType.Int4,
    "SERIAL"           -> CanonicalType.Serial4,
    "BIGINT"           -> CanonicalType.Int8,
    "BIGSERIAL"        -> CanonicalType.Serial8,
    "DOUBLE PRECISION" -> CanonicalType.Float8,
    "BOOLEAN"          -> CanonicalType.Bool,
    "TIMESTAMPTZ"      -> CanonicalType.Timestamptz,
    "DATE"             -> CanonicalType.DateOnly,
    "UUID"             -> CanonicalType.Uuid,
    "BYTEA"            -> CanonicalType.Bytes,
    "JSONB"            -> CanonicalType.Json,
    "NUMERIC(19,4)"    -> CanonicalType.Numeric(19, Some(4)),
    "NUMERIC(19, 4)"   -> CanonicalType.Numeric(19, Some(4)),
    "NUMERIC(10)"      -> CanonicalType.Numeric(10, None),
    "VARCHAR(64)"      -> CanonicalType.Varchar(64)
  )

  parseCases.foreach: (sqlType, expected) =>
    test(s"parse decodes $sqlType"):
      assertEquals(CanonicalType.parse(sqlType), Some(expected))

  test("parse returns None for an unknown SQL type"):
    assertEquals(CanonicalType.parse("MONEY"), None)

  private def pgSaCases: List[(String, String)] = List(
    "TEXT"             -> "sa.Text()",
    "INTEGER"          -> "sa.Integer()",
    "SERIAL"           -> "sa.Integer()",
    "BIGINT"           -> "sa.BigInteger()",
    "BIGSERIAL"        -> "sa.BigInteger()",
    "DOUBLE PRECISION" -> "sa.Float()",
    "BOOLEAN"          -> "sa.Boolean()",
    "TIMESTAMPTZ"      -> "sa.DateTime(timezone=True)",
    "DATE"             -> "sa.Date()",
    "UUID"             -> "sa.Uuid()",
    "BYTEA"            -> "sa.LargeBinary()",
    "JSONB"            -> "postgresql.JSONB()",
    "NUMERIC(19,4)"    -> "sa.Numeric(19, 4)",
    "NUMERIC(10)"      -> "sa.Numeric(10)",
    "VARCHAR(64)"      -> "sa.String(length=64)"
  )

  pgSaCases.foreach: (sqlType, expectedSa) =>
    test(s"mapSqlTypeToSa keeps Postgres output stable for $sqlType"):
      assertEquals(AlembicSyntax.mapSqlTypeToSa(sqlType), expectedSa)

  test("mapSqlTypeToSa still throws on an unsupported SQL type"):
    interceptMessage[RuntimeException]("Unsupported SQL type in Alembic migration: MONEY"):
      AlembicSyntax.mapSqlTypeToSa("MONEY")
