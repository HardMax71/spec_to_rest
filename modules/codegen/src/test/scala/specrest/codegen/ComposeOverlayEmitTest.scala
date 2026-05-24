package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures

class ComposeOverlayEmitTest extends CatsEffectSuite:

  private val targetsWithDb = List(
    ("python-fastapi-postgres", List("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")),
    (
      "python-fastapi-mysql",
      List("MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_DATABASE", "MYSQL_ROOT_PASSWORD")
    ),
    ("go-chi-postgres", List("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")),
    ("go-chi-mysql", List("MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_DATABASE", "MYSQL_ROOT_PASSWORD")),
    ("ts-express-postgres", List("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")),
    (
      "ts-express-mysql",
      List("MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_DATABASE", "MYSQL_ROOT_PASSWORD")
    )
  )

  private val allTargets = targetsWithDb.map(_._1) ++ List(
    "python-fastapi-sqlite",
    "go-chi-sqlite",
    "ts-express-sqlite"
  )

  private def fileMap(target: String): cats.effect.IO[Map[String, EmittedFile]] =
    SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
      Emit.emitProject(profiled).map(f => f.path -> f).toMap

  allTargets.foreach: target =>
    test(s"$target emits override.example (preserve=false), staging+prod (preserve=true)"):
      fileMap(target).map: files =>
        val overrideExample = files("docker-compose.override.yml.example")
        val staging         = files("docker-compose.staging.yml")
        val prod            = files("docker-compose.prod.yml")
        assert(!overrideExample.preserve, "override.yml.example must be regenerable")
        assert(staging.preserve, "staging.yml is a one-time scaffold; must preserve")
        assert(prod.preserve, "prod.yml is a one-time scaffold; must preserve")

  targetsWithDb.foreach: (target, secrets) =>
    test(s"$target prod overlay fails-fast on every secret env var"):
      fileMap(target).map: files =>
        val prod = files("docker-compose.prod.yml").content
        secrets.foreach: k =>
          val expected = s"$${$k:?$k is required for production}"
          assert(
            prod.contains(expected),
            s"prod.yml must require $k via :?err substitution; expected '$expected'; got:\n$prod"
          )

  targetsWithDb.foreach: (target, _) =>
    test(s"$target prod overlay declares memory + cpu limits on app and db"):
      fileMap(target).map: files =>
        val prod      = files("docker-compose.prod.yml").content
        val appLimits = "memory: 512M"
        val dbLimits  = "memory: 1G"
        assert(prod.contains(appLimits), s"prod.yml missing app limits ($appLimits):\n$prod")
        assert(prod.contains(dbLimits), s"prod.yml missing db limits ($dbLimits):\n$prod")
        assert(prod.contains("restart: unless-stopped"), s"prod.yml missing unless-stopped:\n$prod")

  targetsWithDb.foreach: (target, _) =>
    test(s"$target prod overlay does NOT expose the db port"):
      fileMap(target).map: files =>
        val prod = files("docker-compose.prod.yml").content
        assert(!prod.contains("ports:"), s"prod.yml must not bind any ports:\n$prod")

  targetsWithDb.foreach: (target, secrets) =>
    test(s"$target .env.example lists every prod-required secret in the addendum"):
      fileMap(target).map: files =>
        val env = files(".env.example").content
        assert(
          env.contains("Required for staging / production"),
          s".env.example missing prod section:\n$env"
        )
        secrets.foreach: k =>
          assert(env.contains(s"# $k="), s".env.example missing commented secret $k:\n$env")

  test("sqlite targets have NO prod addendum in .env.example"):
    val sqliteTargets = List("python-fastapi-sqlite", "go-chi-sqlite", "ts-express-sqlite")
    cats.effect.IO.traverse(sqliteTargets): t =>
      fileMap(t).map: files =>
        val env = files(".env.example").content
        assert(
          !env.contains("Required for staging / production"),
          s"$t .env.example should not have prod section (no db service):\n$env"
        )
    .void
