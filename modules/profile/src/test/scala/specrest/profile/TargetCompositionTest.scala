package specrest.profile

import munit.CatsEffectSuite

class TargetCompositionTest extends CatsEffectSuite:

  test("LanguageId/DatabaseId parse round-trip their slugs"):
    LanguageId.values.foreach: l =>
      assertEquals(LanguageId.parse(l.slug), Some(l))
    DatabaseId.values.foreach: d =>
      assertEquals(DatabaseId.parse(d.slug), Some(d))
    assertEquals(LanguageId.parse("rust"), None)
    assertEquals(DatabaseId.parse("oracle"), None)

  test(
    "LanguageId/DatabaseId slugs are hyphen-free (composed slug stays positionally parseable; path layout unambiguous)"
  ):
    LanguageId.values.foreach: l =>
      assert(!l.slug.contains('-'), s"LanguageId.$l slug '${l.slug}' must not contain '-'")
    DatabaseId.values.foreach: d =>
      assert(!d.slug.contains('-'), s"DatabaseId.$d slug '${d.slug}' must not contain '-'")

  test("DatabaseId.display renders human dialect names"):
    assertEquals(DatabaseId.display(DatabaseId.Postgres), "PostgreSQL")
    assertEquals(DatabaseId.display(DatabaseId.Sqlite), "SQLite")
    assertEquals(DatabaseId.display(DatabaseId.Mysql), "MySQL")

  test("TargetKey.slug composes language-framework-database"):
    val key = TargetKey(LanguageId.Python, "fastapi", DatabaseId.Postgres)
    assertEquals(key.slug, "python-fastapi-postgres")

  List(
    "python-fastapi-postgres" -> TargetKey(LanguageId.Python, "fastapi", DatabaseId.Postgres),
    "go-chi-postgres"         -> TargetKey(LanguageId.Go, "chi", DatabaseId.Postgres),
    "ts-express-postgres"     -> TargetKey(LanguageId.Ts, "express", DatabaseId.Postgres)
  ).foreach: (slug, want) =>
    test(s"TargetKey.parse round-trips '$slug'"):
      assertEquals(TargetKey.parse(slug), Right(want))
      assertEquals(want.slug, slug)

  List(
    "python-fastapi"        -> "fewer than three segments",
    "rust-fastapi-postgres" -> "unknown language",
    "python-fastapi-oracle" -> "unknown database",
    ""                      -> "empty slug"
  ).foreach: (slug, why) =>
    test(s"TargetKey.parse rejects '$slug' ($why)"):
      assert(TargetKey.parse(slug).isLeft, s"expected Left for '$slug'")

  List(
    ("python-fastapi-postgres", "asyncpg"),
    ("python-fastapi-sqlite", "aiosqlite"),
    ("python-fastapi-mysql", "aiomysql"),
    ("go-chi-postgres", "bun"),
    ("go-chi-sqlite", "bun"),
    ("go-chi-mysql", "bun"),
    ("ts-express-postgres", "@prisma/client"),
    ("ts-express-sqlite", "@prisma/client"),
    ("ts-express-mysql", "@prisma/client")
  ).foreach: (slug, wantDriver) =>
    test(s"Registry.resolveSlug('$slug') yields the expected profile"):
      Registry.resolveSlug(slug) match
        case Left(err) => fail(s"expected a profile for '$slug', got: $err")
        case Right(p) =>
          assertEquals(p.name, slug)
          assertEquals(p.dbDriver, wantDriver)

  List(
    ("python-chi-postgres", "language"),
    ("go-fastapi-postgres", "language"),
    ("python-rails-postgres", "framework")
  ).foreach: (slug, axis) =>
    test(s"Registry.resolveSlug('$slug') is a typed error on the $axis axis"):
      Registry.resolveSlug(slug) match
        case Right(p)  => fail(s"expected '$slug' to be rejected, got profile ${p.name}")
        case Left(err) => assert(err.nonEmpty)

  test("Registry.listProfiles is exactly the capability cartesian product"):
    assertEquals(
      Registry.listProfiles,
      List(
        "go-chi-mysql",
        "go-chi-postgres",
        "go-chi-sqlite",
        "python-fastapi-mysql",
        "python-fastapi-postgres",
        "python-fastapi-sqlite",
        "ts-express-mysql",
        "ts-express-postgres",
        "ts-express-sqlite"
      )
    )

  test("Registry.frameworkIds is sorted and complete"):
    assertEquals(Registry.frameworkIds, List("chi", "express", "fastapi"))

  test("Registry.getProfile throws on an unknown target"):
    intercept[RuntimeException]:
      Registry.getProfile("rust-actix-sqlite")

  test("supportsTestgen covers every fastapi and express dialect and is false for chi"):
    List(DatabaseId.Postgres, DatabaseId.Sqlite, DatabaseId.Mysql).foreach: d =>
      assert(Fastapi.supportsTestgen(d), s"fastapi/$d")
      assert(Express.supportsTestgen(d), s"express/$d")
      assert(!Chi.supportsTestgen(d), s"chi/$d")
