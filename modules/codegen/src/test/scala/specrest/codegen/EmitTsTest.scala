package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures
import specrest.profile.DatabaseId
import specrest.profile.Express
import specrest.profile.LanguageId
import specrest.profile.TargetKey

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EmitTsTest extends CatsEffectSuite:

  private def goldenRoot(db: DatabaseId): Path =
    Paths
      .get("fixtures/golden/codegen", TargetKey(LanguageId.Ts, Express.id, db).segments*)
      .resolve("url_shortener")

  test("emitProject for ts-express-postgres produces a valid TS project layout for url_shortener"):
    SpecFixtures.loadProfiled("url_shortener", "ts-express-postgres").map: profiled =>
      val files = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
      val expected = List(
        "package.json",
        "tsconfig.json",
        "prisma/schema.prisma",
        "src/index.ts",
        "src/app.ts",
        "src/config.ts",
        "src/prisma.ts",
        "src/pagination.ts",
        "src/middleware/error.ts",
        "src/middleware/validate.ts",
        "src/routes/index.ts",
        "src/routes/urlMappings.ts",
        "src/services/urlMapping.ts",
        "src/schemas/urlMapping.ts",
        "src/types/urlMapping.ts",
        "Dockerfile",
        "docker-compose.yml",
        ".env.example",
        "Makefile",
        ".gitignore",
        ".dockerignore",
        "README.md",
        ".github/workflows/ci.yml",
        "tests/health.test.ts",
        "openapi.yaml",
        ".spec-snapshot.json",
        "prisma/migrations/migration_lock.toml",
        "prisma/migrations/001_initial_schema/migration.sql",
        "prisma/migrations/001_initial_schema/down.sql"
      )
      expected.foreach: p =>
        assert(files.contains(p), s"missing file $p; emitted: ${files.keys.toList.sorted}")

      val initialMig = files("prisma/migrations/001_initial_schema/migration.sql")
      assert(initialMig.contains("CREATE TABLE url_mappings"), initialMig)
      assert(initialMig.contains("CONSTRAINT pk_url_mappings PRIMARY KEY"), initialMig)

      val lock = files("prisma/migrations/migration_lock.toml")
      assert(lock.contains("""provider = "postgresql""""), lock)

      val pkg = files("package.json")
      assert(pkg.contains("\"@generated/url-shortener\""), pkg)
      assert(pkg.contains("\"express\""), pkg)
      assert(pkg.contains("\"@prisma/client\""), pkg)
      assert(pkg.contains("\"zod\""), pkg)

      // migrate-down requires NAME and validates it against [A-Za-z0-9_-]+ before
      // interpolating into the raw `DELETE FROM _prisma_migrations` SQL — Prisma's
      // db execute --stdin has no parameter binding, so the regex gate is the
      // injection / mangled-SQL guard.
      val makefile = files("Makefile")
      assert(makefile.contains("Usage: make migrate-down NAME="), makefile)
      assert(makefile.contains("[A-Za-z0-9_-]+"), makefile)
      assert(makefile.contains("DELETE FROM _prisma_migrations"), makefile)

      val tsconfig = files("tsconfig.json")
      assert(tsconfig.contains("\"strict\": true"), tsconfig)
      assert(tsconfig.contains("\"target\": \"ES2022\""), tsconfig)

      val schema = files("prisma/schema.prisma")
      assert(schema.contains("model UrlMapping"), schema)
      assert(schema.contains("@@map(\"url_mappings\")"), schema)
      assert(schema.contains("@map(\"created_at\")"), schema)

      val routes = files("src/routes/urlMappings.ts")
      assert(routes.contains("registerUrlMappingRoutes"), routes)
      assert(routes.contains("app.post(\n    '/shorten'"), routes)
      assert(routes.contains("app.get(\n    '/:code'"), routes)
      assert(routes.contains("app.delete(\n    '/:code'"), routes)
      assert(routes.contains("res.redirect(302, result.url)"), routes)

      val service = files("src/services/urlMapping.ts")
      // Resolve carries a spec side-effect (click_count++) the engine can't derive, so it is a
      // fail-loud stub — not a silent findFirst that drops the increment (matches fastapi).
      assert(service.contains("throw new Error('resolve not implemented')"), service)
      assert(service.contains("prisma.urlMapping.deleteMany"), service)
      assert(service.contains("prisma.urlMapping.findMany"), service)

      val schemas = files("src/schemas/urlMapping.ts")
      assert(schemas.contains("UrlMappingCreateSchema"), schemas)
      assert(schemas.contains("ShortenRequestSchema"), schemas)
      assert(schemas.contains("z.string()"), schemas)

      val types = files("src/types/urlMapping.ts")
      assert(types.contains("interface UrlMapping"), types)
      assert(types.contains("interface UrlMappingCreate"), types)
      assert(types.contains("interface UrlMappingRead"), types)
      assert(types.contains("interface ShortenRequest"), types)
      assert(types.contains("interface ErrorResponse"), types)

  private val dialectCases = List(
    DatabaseId.Postgres -> "ts-express-postgres",
    DatabaseId.Sqlite   -> "ts-express-sqlite",
    DatabaseId.Mysql    -> "ts-express-mysql"
  )

  dialectCases.foreach: (db, target) =>
    test(s"emitProject for $target matches the checked-in url_shortener golden"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val files           = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
        val expected        = walkGolden(goldenRoot(db))
        val missingInOutput = expected.keySet.diff(files.keySet)
        val extraInOutput   = files.keySet.diff(expected.keySet)
        assert(expected.nonEmpty, s"no golden tree at ${goldenRoot(db)}")
        assert(
          missingInOutput.isEmpty,
          s"emitter dropped golden files: ${missingInOutput.toList.sorted.mkString(", ")}"
        )
        assert(
          extraInOutput.isEmpty,
          s"emitter produced files not in golden: ${extraInOutput.toList.sorted.mkString(", ")}"
        )
        expected.toList.sortBy(_._1).foreach: (rel, want) =>
          val got = files(rel)
          if got != want then
            fail(
              s"$rel diverges from golden\n--- expected ---\n$want\n--- got ---\n$got\n--- end ---"
            )

  private def walkGolden(root: Path): Map[String, String] =
    if !Files.isDirectory(root) then Map.empty
    else
      val stream = Files.walk(root)
      try
        import scala.jdk.CollectionConverters.*
        stream.iterator.asScala
          .filter(Files.isRegularFile(_))
          .map(p => root.relativize(p).toString.replace('\\', '/') -> Files.readString(p))
          .toMap
      finally stream.close()
