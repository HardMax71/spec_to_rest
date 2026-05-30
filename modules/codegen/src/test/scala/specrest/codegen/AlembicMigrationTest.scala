package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.alembic.Migration
import specrest.codegen.testutil.SpecFixtures
import specrest.ir.generated.SpecRestGenerated.*

class AlembicMigrationTest extends CatsEffectSuite:

  test("empty schema produces empty migration"):
    val migration = Migration.buildAlembicMigration(DatabaseSchema(Nil, Nil))
    assertEquals(migration.tables, Nil)
    assert(!migration.needsPostgresDialect)

  test("single table produces one AlembicTable with proper columns"):
    val schema = DatabaseSchema(
      List(
        TableSpec(
          "users",
          "User",
          List(
            ColumnSpec("id", "BIGSERIAL", false, None),
            ColumnSpec("email", "TEXT", false, None),
            ColumnSpec("created_at", "TIMESTAMPTZ", false, Some("NOW()"))
          ),
          "id",
          Nil,
          Nil,
          Nil
        )
      ),
      Nil
    )
    val migration = Migration.buildAlembicMigration(schema)
    val t         = migration.tables.head
    assertEquals(t.name, "users")
    assertEquals(t.columns.length, 3)
    val idCol = t.columns.find(_.name == "id").get
    assertEquals(idCol.saType, "sa.BigInteger()")
    assertEquals(idCol.autoincrement, true)
    assertEquals(idCol.primaryKey, true)
    val createdCol = t.columns.find(_.name == "created_at").get
    assertEquals(createdCol.saType, "sa.DateTime(timezone=True)")
    assertEquals(createdCol.serverDefault, Some("sa.func.now()"))

  test("topologically orders tables with FK dependencies"):
    val schema = DatabaseSchema(
      List(
        TableSpec(
          "posts",
          "Post",
          List(ColumnSpec("id", "BIGSERIAL", false, None)),
          "id",
          List(ForeignKeySpec("user_id", "users", "id", "CASCADE")),
          Nil,
          Nil
        ),
        TableSpec(
          "users",
          "User",
          List(ColumnSpec("id", "BIGSERIAL", false, None)),
          "id",
          Nil,
          Nil,
          Nil
        )
      ),
      Nil
    )
    val migration = Migration.buildAlembicMigration(schema)
    val names     = migration.tables.map(_.name)
    assertEquals(
      names,
      List("users", "posts"),
      s"users must come before posts (FK dep); got $names"
    )

  test("JSONB column type triggers needsPostgresDialect"):
    val schema = DatabaseSchema(
      List(
        TableSpec(
          "items",
          "Item",
          List(
            ColumnSpec("id", "BIGSERIAL", false, None),
            ColumnSpec("tags", "JSONB", false, Some("'[]'::jsonb"))
          ),
          "id",
          Nil,
          Nil,
          Nil
        )
      ),
      Nil
    )
    val migration = Migration.buildAlembicMigration(schema)
    assert(migration.needsPostgresDialect)
    val tagsCol = migration.tables.head.columns.find(_.name == "tags").get
    assertEquals(tagsCol.saType, "postgresql.JSONB()")

  test("url_shortener migration has UrlMapping table with proper FK ordering"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val migration = Migration.buildAlembicMigration(profiled.schema)
      assert(migration.tables.nonEmpty)
      val names = migration.tables.map(_.name)
      assert(names.contains("url_mappings"), s"missing url_mappings in $names")

  test("emitted alembic migration file contains CREATE TABLE equivalent ops"):
    SpecFixtures.loadProfiled("url_shortener").map: profiled =>
      val files         = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val migrationPath = files.keys.find(_.startsWith("alembic/versions/")).get
      val content       = files(migrationPath)
      assert(
        content.contains("op.create_table"),
        s"missing op.create_table in migration; first 500 chars: ${content.take(500)}"
      )
      assert(content.contains("url_mappings"), "missing url_mappings table name")

  test("ecommerce: aggregate-invariant trigger emitted with subtotal recompute"):
    SpecFixtures.loadProfiled("ecommerce").map: profiled =>
      val files         = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val migrationPath = files.keys
        .find(_.startsWith("alembic/versions/"))
        .getOrElse(fail(s"missing alembic migration; paths=${files.keys.toList.sorted}"))
      val content = files(migrationPath)
      assert(
        content.contains("CREATE OR REPLACE FUNCTION recalc_order_subtotal()"),
        s"missing recalc_order_subtotal function; got:\n$content"
      )
      assert(
        content.contains("CREATE TRIGGER trg_recalc_order_subtotal"),
        s"missing trg_recalc_order_subtotal trigger; got:\n$content"
      )
      assert(
        content.contains("AFTER INSERT OR UPDATE OR DELETE ON line_items"),
        s"trigger should fire on line_items; got:\n$content"
      )
      assert(
        content.contains("DROP TRIGGER IF EXISTS trg_recalc_order_subtotal"),
        s"missing downgrade DROP TRIGGER; got:\n$content"
      )
      assert(
        content.contains("DROP FUNCTION IF EXISTS recalc_order_subtotal"),
        s"missing downgrade DROP FUNCTION; got:\n$content"
      )

  test("ecommerce: Product.partial_index 'active' emits postgresql_where"):
    SpecFixtures.loadProfiled("ecommerce").map: profiled =>
      val files         = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val migrationPath = files.keys
        .find(_.startsWith("alembic/versions/"))
        .getOrElse(fail(s"missing alembic migration; paths=${files.keys.toList.sorted}"))
      val content = files(migrationPath)
      assert(
        content.contains("postgresql_where=sa.text('active = true')"),
        s"missing postgresql_where for active partial index; got:\n$content"
      )
      assert(
        content.contains("idx_products_active_partial"),
        s"missing partial-index name; got:\n$content"
      )
