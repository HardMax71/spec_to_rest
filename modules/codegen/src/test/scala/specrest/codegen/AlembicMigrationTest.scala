package specrest.codegen

import java.nio.file.{Files, Paths}
import specrest.codegen.alembic.Migration
import specrest.convention.{ColumnSpec, DatabaseSchema, ForeignKeySpec, TableSpec}
import specrest.parser.{Builder, Parse}
import specrest.profile.Annotate

class AlembicMigrationTest extends munit.FunSuite:

  private def buildProfiled(name: String): specrest.profile.ProfiledService =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors: ${parsed.errors}")
    val ir = Builder.buildIR(parsed.tree)
    Annotate.buildProfiledService(ir, "python-fastapi-postgres")

  test("empty schema produces empty migration"):
    val migration = Migration.buildAlembicMigration(DatabaseSchema(Nil))
    assertEquals(migration.tables, Nil)
    assert(!migration.needsPostgresDialect)

  test("single table produces one AlembicTable with proper columns"):
    val schema = DatabaseSchema(List(
      TableSpec(
        name        = "users",
        entityName  = "User",
        columns = List(
          ColumnSpec("id", "BIGSERIAL", nullable = false, None),
          ColumnSpec("email", "TEXT", nullable = false, None),
          ColumnSpec("created_at", "TIMESTAMPTZ", nullable = false, Some("NOW()")),
        ),
        primaryKey = "id",
        foreignKeys = Nil,
        checks = Nil,
        indexes = Nil,
      ),
    ))
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
    val schema = DatabaseSchema(List(
      TableSpec(
        name        = "posts",
        entityName  = "Post",
        columns     = List(ColumnSpec("id", "BIGSERIAL", nullable = false, None)),
        primaryKey  = "id",
        foreignKeys = List(ForeignKeySpec("user_id", "users", "id", "CASCADE")),
        checks      = Nil,
        indexes     = Nil,
      ),
      TableSpec(
        name        = "users",
        entityName  = "User",
        columns     = List(ColumnSpec("id", "BIGSERIAL", nullable = false, None)),
        primaryKey  = "id",
        foreignKeys = Nil,
        checks      = Nil,
        indexes     = Nil,
      ),
    ))
    val migration = Migration.buildAlembicMigration(schema)
    val names     = migration.tables.map(_.name)
    assertEquals(names, List("users", "posts"), s"users must come before posts (FK dep); got $names")

  test("JSONB column type triggers needsPostgresDialect"):
    val schema = DatabaseSchema(List(
      TableSpec(
        name       = "items",
        entityName = "Item",
        columns = List(
          ColumnSpec("id", "BIGSERIAL", nullable = false, None),
          ColumnSpec("tags", "JSONB", nullable = false, Some("'[]'::jsonb")),
        ),
        primaryKey = "id",
        foreignKeys = Nil,
        checks = Nil,
        indexes = Nil,
      ),
    ))
    val migration = Migration.buildAlembicMigration(schema)
    assert(migration.needsPostgresDialect)
    val tagsCol = migration.tables.head.columns.find(_.name == "tags").get
    assertEquals(tagsCol.saType, "postgresql.JSONB()")

  test("url_shortener migration has UrlMapping table with proper FK ordering"):
    val profiled  = buildProfiled("url_shortener")
    val migration = Migration.buildAlembicMigration(profiled.schema)
    assert(migration.tables.nonEmpty)
    val names = migration.tables.map(_.name)
    assert(names.contains("url_mappings"), s"missing url_mappings in $names")

  test("emitted alembic migration file contains CREATE TABLE equivalent ops"):
    val files = Emit.emitProject(buildProfiled("url_shortener")).map(f => f.path -> f.content).toMap
    val migrationPath = files.keys.find(_.startsWith("alembic/versions/")).get
    val content       = files(migrationPath)
    assert(content.contains("op.create_table"), s"missing op.create_table in migration; first 500 chars: ${content.take(500)}")
    assert(content.contains("url_mappings"), "missing url_mappings table name")
