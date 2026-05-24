package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures
import specrest.ir.generated.SpecRestGenerated.*

class IncrementalMigrationTest extends CatsEffectSuite:

  private def addColumn(schema: database_schema, table: String, col: column_spec): database_schema =
    val newTables = schema_tables(schema).map: t =>
      if table_name(t) == table then
        TableSpec(
          table_name(t),
          table_entity_name(t),
          table_columns(t) :+ col,
          table_primary_key(t),
          table_foreign_keys(t),
          table_checks(t),
          table_indexes(t)
        )
      else t
    DatabaseSchema(newTables, schema_triggers(schema))

  private val Targets = List(
    "python-fastapi-postgres",
    "go-chi-postgres",
    "ts-express-postgres"
  )

  Targets.foreach: target =>
    test(s"$target: first emit writes snapshot and initial migration"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val files = Emit.emitProject(profiled).map(_.path).toSet
        assert(files.contains(".spec-snapshot.json"), s"snapshot missing for $target")
        val initialMig = target match
          case "python-fastapi-postgres" => "alembic/versions/001_initial_schema.py"
          case "go-chi-postgres"         => "migrations/001_initial_schema.up.sql"
          case "ts-express-postgres"     => "prisma/migrations/001_initial_schema/migration.sql"
          case _                         => fail(s"unknown target $target")
        assert(files.contains(initialMig), s"missing $initialMig in $files")

    test(s"$target: empty diff -> no new migration file"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val opts = EmitOptions(
          previousSnapshot = Some(profiled.schema),
          existingRevisions = List("001")
        )
        val files = Emit.emitProject(profiled, opts).map(_.path).toSet
        val deltaPaths = files.filter: p =>
          p.contains("002_") || p.contains("003_")
        assertEquals(deltaPaths, Set.empty[String])
        assert(files.contains(".spec-snapshot.json"))
        val initialMig = target match
          case "python-fastapi-postgres" => "alembic/versions/001_initial_schema.py"
          case "go-chi-postgres"         => "migrations/001_initial_schema.up.sql"
          case "ts-express-postgres"     => "prisma/migrations/001_initial_schema/migration.sql"
          case _                         => fail(s"unknown target $target")
        assert(!files.contains(initialMig), s"initial migration leaked on re-emit for $target")

    test(s"$target: added column emits NNN_schema_update with AddColumn op"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val mutated = profiled.copy(schema =
          addColumn(
            profiled.schema,
            "url_mappings",
            ColumnSpec("notes", "TEXT", true, None)
          )
        )
        val opts = EmitOptions(
          previousSnapshot = Some(profiled.schema),
          existingRevisions = List("001")
        )
        val files = Emit.emitProject(mutated, opts).map(f => f.path -> f.content).toMap
        val expectedPath = target match
          case "python-fastapi-postgres" => "alembic/versions/002_schema_update.py"
          case "go-chi-postgres"         => "migrations/002_schema_update.up.sql"
          case "ts-express-postgres"     => "prisma/migrations/002_schema_update/migration.sql"
          case _                         => fail(s"unknown target $target")
        assert(
          files.contains(expectedPath),
          s"$expectedPath not emitted; emitted: ${files.keys.toList.sorted}"
        )
        val body = files(expectedPath)
        assert(body.contains("notes"), s"delta body missing 'notes' column ref: $body")

    test(s"$target: dropped column emits delta with DropColumn op"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val v1 = profiled.copy(schema =
          addColumn(
            profiled.schema,
            "url_mappings",
            ColumnSpec("legacy", "TEXT", true, None)
          )
        )
        val v2 = profiled.copy(schema = profiled.schema)
        val opts = EmitOptions(
          previousSnapshot = Some(v1.schema),
          existingRevisions = List("001")
        )
        val files = Emit.emitProject(v2, opts).map(f => f.path -> f.content).toMap
        val expectedPath = target match
          case "python-fastapi-postgres" => "alembic/versions/002_schema_update.py"
          case "go-chi-postgres"         => "migrations/002_schema_update.up.sql"
          case "ts-express-postgres"     => "prisma/migrations/002_schema_update/migration.sql"
          case _                         => fail(s"unknown target $target")
        val body = files(expectedPath)
        assert(body.toLowerCase.contains("legacy"), body)

    test(s"$target: revision chains forward (existing 001+002 -> next is 003)"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val mutated = profiled.copy(schema =
          addColumn(
            profiled.schema,
            "url_mappings",
            ColumnSpec("notes", "TEXT", true, None)
          )
        )
        val opts = EmitOptions(
          previousSnapshot = Some(profiled.schema),
          existingRevisions = List("001", "002")
        )
        val files = Emit.emitProject(mutated, opts).map(_.path).toSet
        val expectedRev = target match
          case "python-fastapi-postgres" => "alembic/versions/003_schema_update.py"
          case "go-chi-postgres"         => "migrations/003_schema_update.up.sql"
          case "ts-express-postgres"     => "prisma/migrations/003_schema_update/migration.sql"
          case _                         => fail(s"unknown target $target")
        assert(files.contains(expectedRev), s"expected $expectedRev in $files")
