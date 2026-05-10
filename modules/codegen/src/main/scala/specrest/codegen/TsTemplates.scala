package specrest.codegen

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
    routesIndex: String,
    routeEntity: String,
    serviceEntity: String,
    schemaEntity: String,
    typeEntity: String,
    dockerfile: String,
    dockerCompose: String,
    envExample: String,
    makefile: String,
    gitignore: String,
    dockerignore: String,
    readme: String,
    ciWorkflow: String,
    testHealth: String
)

object TsTemplates:
  private val root = "templates/ts-express-postgres"

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
      routesIndex = load("src/routes/index.ts.hbs"),
      routeEntity = load("src/routes/entity.ts.hbs"),
      serviceEntity = load("src/services/entity.ts.hbs"),
      schemaEntity = load("src/schemas/entity.ts.hbs"),
      typeEntity = load("src/types/entity.ts.hbs"),
      dockerfile = load("Dockerfile.hbs"),
      dockerCompose = load("docker-compose.yml.hbs"),
      envExample = load("env.example.hbs"),
      makefile = load("Makefile.hbs"),
      gitignore = load("gitignore.hbs"),
      dockerignore = load("dockerignore.hbs"),
      readme = load("README.md.hbs"),
      ciWorkflow = load("github/workflows/ci.yml.hbs"),
      testHealth = load("tests/health.test.ts.hbs")
    )
