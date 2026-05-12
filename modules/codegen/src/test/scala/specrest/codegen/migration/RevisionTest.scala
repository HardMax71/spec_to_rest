package specrest.codegen.migration

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectSuite

import java.nio.file.Files
import java.nio.file.Path

class RevisionTest extends CatsEffectSuite:

  private def tmpDir: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory("revision-test")))(p =>
      IO.blocking {
        if Files.isDirectory(p) then
          val stream = Files.walk(p)
          try
            stream
              .sorted(java.util.Comparator.reverseOrder())
              .forEach { f =>
                val _ = Files.deleteIfExists(f)
              }
          finally stream.close()
      }
    )

  test("next() returns 001 when no revisions exist"):
    assertEquals(Revision.next(Nil), "001")

  test("next() returns max + 1 zero-padded"):
    assertEquals(Revision.next(List("001")), "002")
    assertEquals(Revision.next(List("001", "002", "003")), "004")
    assertEquals(Revision.next(List("042")), "043")

  test("next() skips non-numeric entries"):
    assertEquals(Revision.next(List("001", "abc", "002")), "003")

  test("head() returns max revision or None"):
    assertEquals(Revision.head(Nil), None)
    assertEquals(Revision.head(List("001", "003", "002")), Some("003"))

  test("discover(python) scans alembic/versions for *.py"):
    tmpDir
      .use: dir =>
        IO.blocking {
          val versions = dir.resolve("alembic/versions")
          Files.createDirectories(versions)
          Files.writeString(versions.resolve("001_initial_schema.py"), "")
          Files.writeString(versions.resolve("002_add_email.py"), "")
          Files.writeString(versions.resolve("__init__.py"), "")
          val found = Revision.discover(dir, "python-fastapi-postgres")
          assertEquals(found, List("001", "002"))
        }

  test("discover(go) scans migrations for *.up.sql"):
    tmpDir
      .use: dir =>
        IO.blocking {
          val migrations = dir.resolve("migrations")
          Files.createDirectories(migrations)
          Files.writeString(migrations.resolve("001_initial_schema.up.sql"), "")
          Files.writeString(migrations.resolve("001_initial_schema.down.sql"), "")
          Files.writeString(migrations.resolve("002_add_email.up.sql"), "")
          val found = Revision.discover(dir, "go-chi-postgres")
          assertEquals(found, List("001", "002"))
        }

  test("discover(ts) scans prisma/migrations for subdirs"):
    tmpDir
      .use: dir =>
        IO.blocking {
          val migrations = dir.resolve("prisma/migrations")
          Files.createDirectories(migrations.resolve("001_initial_schema"))
          Files.createDirectories(migrations.resolve("002_add_email"))
          Files.writeString(migrations.resolve("migration_lock.toml"), "")
          val found = Revision.discover(dir, "ts-express-postgres")
          assertEquals(found, List("001", "002"))
        }

  test("discover returns empty list for nonexistent directory"):
    tmpDir
      .use: dir =>
        IO.blocking {
          assertEquals(Revision.discover(dir, "python-fastapi-postgres"), Nil)
          assertEquals(Revision.discover(dir, "go-chi-postgres"), Nil)
          assertEquals(Revision.discover(dir, "ts-express-postgres"), Nil)
        }
