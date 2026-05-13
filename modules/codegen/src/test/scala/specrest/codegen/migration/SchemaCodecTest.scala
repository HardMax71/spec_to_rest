package specrest.codegen.migration

import munit.CatsEffectSuite
import specrest.convention.ColumnSpec
import specrest.convention.DatabaseSchema
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec

class SchemaCodecTest extends CatsEffectSuite:

  private val sample = DatabaseSchema(
    List(
      TableSpec(
        name = "users",
        entityName = "User",
        columns = List(
          ColumnSpec("id", "BIGSERIAL", nullable = false, None),
          ColumnSpec("email", "VARCHAR(255)", nullable = false, None),
          ColumnSpec("created_at", "TIMESTAMPTZ", nullable = false, Some("NOW()"))
        ),
        primaryKey = "id",
        foreignKeys = Nil,
        checks = List("length(email) > 0"),
        indexes = List(IndexSpec("ix_users_email", List("email"), unique = true))
      ),
      TableSpec(
        name = "posts",
        entityName = "Post",
        columns = List(
          ColumnSpec("id", "BIGSERIAL", nullable = false, None),
          ColumnSpec("author_id", "BIGINT", nullable = false, None)
        ),
        primaryKey = "id",
        foreignKeys = List(ForeignKeySpec("author_id", "users", "id", "CASCADE")),
        checks = Nil,
        indexes = List(IndexSpec("ix_posts_author", List("author_id"), unique = false))
      )
    )
  )

  test("snapshot round-trips through JSON"):
    val snapshot = SchemaSnapshot.of(sample)
    val encoded  = SchemaCodec.encode(snapshot)
    val decoded  = SchemaCodec.decode(encoded)
    assertEquals(decoded, Right(snapshot))

  test("empty schema round-trips"):
    val empty = SchemaSnapshot.of(DatabaseSchema(Nil))
    assertEquals(SchemaCodec.decode(SchemaCodec.encode(empty)), Right(empty))

  test("malformed JSON returns Left"):
    val bad = SchemaCodec.decode("{not json")
    assert(bad.isLeft, s"expected Left, got $bad")

  test("missing schemaVersion returns Left"):
    val noVersion = """{"schema": {"tables": []}}"""
    assert(SchemaCodec.decode(noVersion).isLeft)

  test("encoded snapshot is deterministic across calls"):
    val s1 = SchemaCodec.encode(SchemaSnapshot.of(sample))
    val s2 = SchemaCodec.encode(SchemaSnapshot.of(sample))
    assertEquals(s1, s2)

  test("v1 snapshot lifts to v2 with empty triggers"):
    val v1 =
      """{
        |  "schemaVersion" : 1,
        |  "schema" : {
        |    "tables" : [
        |      {
        |        "name" : "users",
        |        "entityName" : "User",
        |        "columns" : [],
        |        "primaryKey" : "id",
        |        "foreignKeys" : [],
        |        "checks" : [],
        |        "indexes" : []
        |      }
        |    ]
        |  }
        |}""".stripMargin
    val decoded = SchemaCodec.decode(v1)
    decoded match
      case Right(snap) =>
        assertEquals(snap.schemaVersion, SchemaSnapshot.CurrentVersion)
        assertEquals(snap.schema.triggers, Nil)
        assertEquals(snap.schema.tables.head.name, "users")
      case Left(err) => fail(s"expected v1 lift to succeed; got: $err")

  test("unknown future schemaVersion returns Left"):
    val future = """{"schemaVersion" : 99, "schema" : {"tables" : [], "triggers" : []}}"""
    assert(SchemaCodec.decode(future).isLeft)

  test("triggers + filterClause round-trip"):
    import specrest.convention.TriggerAggregate
    import specrest.convention.TriggerSpec
    val withExtras = sample.copy(
      tables = sample.tables.head.copy(
        indexes = List(
          IndexSpec(
            "ix_users_active",
            List("active"),
            unique = false,
            filterClause = Some("active = true")
          )
        )
      ) :: sample.tables.tail,
      triggers = List(
        TriggerSpec(
          name = "trg_x",
          functionName = "fn_x",
          targetTable = "p",
          targetColumn = "c",
          sourceTable = "child",
          sourceForeignKey = "p_id",
          aggregate = TriggerAggregate.Sum,
          sourceColumn = Some("v")
        )
      )
    )
    val snap    = SchemaSnapshot.of(withExtras)
    val decoded = SchemaCodec.decode(SchemaCodec.encode(snap))
    assertEquals(decoded, Right(snap))
