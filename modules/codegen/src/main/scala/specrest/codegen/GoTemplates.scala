package specrest.codegen

import specrest.profile.Chi
import specrest.profile.LanguageId
import specrest.profile.TargetKey

final case class GoChiPostgresTemplates(
    goMod: String,
    main: String,
    config: String,
    auth: String,
    database: String,
    modelEntity: String,
    modelCommon: String,
    handlerEntity: String,
    handlerCommon: String,
    serviceEntity: String,
    serviceCommon: String,
    dafnyAdapter: String,
    migrationUp: String,
    migrationDown: String,
    dockerfile: String,
    makefile: String,
    gitignore: String,
    dockerignore: String,
    readme: String,
    ciWorkflow: String,
    testHealth: String
)

object GoTemplates:
  private val root = s"templates/${TargetKey.frameworkPath(LanguageId.Go, Chi.id)}"

  private def load(rel: String): String = Templates.loadResource(root, rel)

  lazy val goChiPostgres: GoChiPostgresTemplates =
    GoChiPostgresTemplates(
      goMod = load("go.mod.hbs"),
      main = load("cmd/server/main.go.hbs"),
      config = load("internal/config/config.go.hbs"),
      auth = load("internal/auth/auth.go.hbs"),
      database = load("internal/database/database.go.hbs"),
      modelEntity = load("internal/models/entity.go.hbs"),
      modelCommon = load("internal/models/common.go.hbs"),
      handlerEntity = load("internal/handlers/entity.go.hbs"),
      handlerCommon = load("internal/handlers/common.go.hbs"),
      serviceEntity = load("internal/services/entity.go.hbs"),
      serviceCommon = load("internal/services/common.go.hbs"),
      dafnyAdapter = load("internal/dafnykernel/adapter.go.hbs"),
      migrationUp = load("migrations/migration.up.sql.hbs"),
      migrationDown = load("migrations/migration.down.sql.hbs"),
      dockerfile = load("Dockerfile.hbs"),
      makefile = load("Makefile.hbs"),
      gitignore = load("gitignore.hbs"),
      dockerignore = load("dockerignore.hbs"),
      readme = load("README.md.hbs"),
      ciWorkflow = load("github/workflows/ci.yml.hbs"),
      testHealth = load("tests/health_test.go.hbs")
    )
