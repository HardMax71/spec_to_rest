package specrest.codegen.migration

import munit.CatsEffectSuite
import specrest.ir.generated.SpecRestGenerated.*

class SchemaCodecTest extends CatsEffectSuite:

  private given CanEqual[database_schema, database_schema] = CanEqual.derived
  private given CanEqual[SchemaSnapshot, SchemaSnapshot]   = CanEqual.derived

  private val sample = DatabaseSchema(
    List(
      TableSpec(
        "users",
        "User",
        List(
          ColumnSpec("id", "BIGSERIAL", false, None),
          ColumnSpec("email", "VARCHAR(255)", false, None),
          ColumnSpec("created_at", "TIMESTAMPTZ", false, Some("NOW()"))
        ),
        "id",
        Nil,
        List("length(email) > 0"),
        List(IndexSpec("ix_users_email", List("email"), true, None))
      ),
      TableSpec(
        "posts",
        "Post",
        List(
          ColumnSpec("id", "BIGSERIAL", false, None),
          ColumnSpec("author_id", "BIGINT", false, None)
        ),
        "id",
        List(ForeignKeySpec("author_id", "users", "id", "CASCADE")),
        Nil,
        List(IndexSpec("ix_posts_author", List("author_id"), false, None))
      )
    ),
    Nil
  )

  test("snapshot round-trips through JSON"):
    val snapshot = SchemaSnapshot.of(sample)
    val encoded  = SchemaCodec.encode(snapshot)
    val decoded  = SchemaCodec.decode(encoded)
    assertEquals(decoded, Right(snapshot))

  test("empty schema round-trips"):
    val empty = SchemaSnapshot.of(DatabaseSchema(Nil, Nil))
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
        assertEquals(schemaTriggers(snap.schema), Nil)
        assertEquals(tableName(schemaTables(snap.schema).head), "users")
      case Left(err) => fail(s"expected v1 lift to succeed; got: $err")

  test("unknown future schemaVersion returns Left"):
    val future = """{"schemaVersion" : 99, "schema" : {"tables" : [], "triggers" : []}}"""
    assert(SchemaCodec.decode(future).isLeft)

  test("triggers + filterClause round-trip"):
    val firstTable = schemaTables(sample).head
    val updatedFirst = TableSpec(
      tableName(firstTable),
      tableEntityName(firstTable),
      tableColumns(firstTable),
      tablePrimaryKey(firstTable),
      tableForeignKeys(firstTable),
      tableChecks(firstTable),
      List(
        IndexSpec(
          "ix_users_active",
          List("active"),
          false,
          Some("active = true")
        )
      )
    )
    val withExtras = DatabaseSchema(
      updatedFirst :: schemaTables(sample).tail,
      List(
        TriggerSpec(
          "trg_x",
          "fn_x",
          "p",
          "c",
          "child",
          "p_id",
          SumAgg(),
          Some("v")
        )
      )
    )
    val snap    = SchemaSnapshot.of(withExtras)
    val decoded = SchemaCodec.decode(SchemaCodec.encode(snap))
    assertEquals(decoded, Right(snap))
