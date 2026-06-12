package specrest.codegen

import specrest.profile.Express
import specrest.profile.LanguageId
import specrest.profile.TargetKey

final case class TsExpressPostgresTemplates(
    packageJson: String,
    tsconfig: String,
    prismaSchema: String,
    index: String,
    app: String,
    config: String,
    prisma: String,
    errorMiddleware: String,
    validateMiddleware: String,
    authMiddleware: String,
    routesIndex: String,
    routeEntity: String,
    serviceEntity: String,
    schemaEntity: String,
    typeEntity: String,
    dockerfile: String,
    makefile: String,
    gitignore: String,
    dockerignore: String,
    readme: String,
    ciWorkflow: String,
    testHealth: String,
    migrationSql: String,
    migrationLock: String
)

object TsTemplates:
  private val root = s"templates/${TargetKey.frameworkPath(LanguageId.Ts, Express.id)}"

  private def load(rel: String): String = Templates.loadResource(root, rel)

  lazy val tsExpressPostgres: TsExpressPostgresTemplates =
    TsExpressPostgresTemplates(
      packageJson = load("package.json.hbs"),
      tsconfig = load("tsconfig.json.hbs"),
      prismaSchema = load("prisma/schema.prisma.hbs"),
      index = load("src/index.ts.hbs"),
      app = load("src/app.ts.hbs"),
      config = load("src/config.ts.hbs"),
      prisma = load("src/prisma.ts.hbs"),
      errorMiddleware = load("src/middleware/error.ts.hbs"),
      validateMiddleware = load("src/middleware/validate.ts.hbs"),
      authMiddleware = load("src/middleware/auth.ts.hbs"),
      routesIndex = load("src/routes/index.ts.hbs"),
      routeEntity = load("src/routes/entity.ts.hbs"),
      serviceEntity = load("src/services/entity.ts.hbs"),
      schemaEntity = load("src/schemas/entity.ts.hbs"),
      typeEntity = load("src/types/entity.ts.hbs"),
      dockerfile = load("Dockerfile.hbs"),
      makefile = load("Makefile.hbs"),
      gitignore = load("gitignore.hbs"),
      dockerignore = load("dockerignore.hbs"),
      readme = load("README.md.hbs"),
      ciWorkflow = load("github/workflows/ci.yml.hbs"),
      testHealth = load("tests/health.test.ts.hbs"),
      migrationSql = load("prisma/migrations/migration.sql.hbs"),
      migrationLock = load("prisma/migrations/migration_lock.toml.hbs")
    )
