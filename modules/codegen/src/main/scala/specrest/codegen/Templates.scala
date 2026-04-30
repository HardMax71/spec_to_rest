package specrest.codegen

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

final case class PythonFastapiPostgresTemplates(
    main: String,
    config: String,
    database: String,
    dbBase: String,
    redaction: String,
    modelEntity: String,
    modelInit: String,
    schemaEntity: String,
    schemaInit: String,
    routerEntity: String,
    routerInit: String,
    serviceEntity: String,
    serviceInit: String,
    alembicIni: String,
    alembicEnv: String,
    alembicMigration: String,
    pyproject: String,
    dockerfile: String,
    dockerCompose: String,
    envExample: String,
    makefile: String,
    gitignore: String,
    dockerignore: String,
    readme: String,
    ciWorkflow: String,
    testHealth: String,
    testLogRedaction: String
)

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.Null"))
object Templates:
  private val root = "templates/python-fastapi-postgres"

  private def loadResource(relPath: String): String =
    val resourcePath = s"$root/$relPath"
    val is           = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if is eq null then
      throw new RuntimeException(s"template resource not found on classpath: $resourcePath")
    try
      val out    = new ByteArrayOutputStream()
      val buffer = new Array[Byte](8192)
      var read   = is.read(buffer)
      while read != -1 do
        out.write(buffer, 0, read)
        read = is.read(buffer)
      new String(out.toByteArray, StandardCharsets.UTF_8)
    finally is.close()

  lazy val pythonFastapiPostgres: PythonFastapiPostgresTemplates =
    PythonFastapiPostgresTemplates(
      main = loadResource("main.py.hbs"),
      config = loadResource("config.py.hbs"),
      database = loadResource("database.py.hbs"),
      dbBase = loadResource("db/base.py.hbs"),
      redaction = loadResource("app/redaction.py.hbs"),
      modelEntity = loadResource("models/entity.py.hbs"),
      modelInit = loadResource("models/__init__.py.hbs"),
      schemaEntity = loadResource("schemas/entity.py.hbs"),
      schemaInit = loadResource("schemas/__init__.py.hbs"),
      routerEntity = loadResource("routers/entity.py.hbs"),
      routerInit = loadResource("routers/__init__.py.hbs"),
      serviceEntity = loadResource("services/entity.py.hbs"),
      serviceInit = loadResource("services/__init__.py.hbs"),
      alembicIni = loadResource("alembic.ini.hbs"),
      alembicEnv = loadResource("alembic/env.py.hbs"),
      alembicMigration = loadResource("alembic/versions/001_initial_schema.py.hbs"),
      pyproject = loadResource("pyproject.toml.hbs"),
      dockerfile = loadResource("Dockerfile.hbs"),
      dockerCompose = loadResource("docker-compose.yml.hbs"),
      envExample = loadResource("env.example.hbs"),
      makefile = loadResource("Makefile.hbs"),
      gitignore = loadResource("gitignore.hbs"),
      dockerignore = loadResource("dockerignore.hbs"),
      readme = loadResource("README.md.hbs"),
      ciWorkflow = loadResource("github/workflows/ci.yml.hbs"),
      testHealth = loadResource("tests/test_health.py.hbs"),
      testLogRedaction = loadResource("tests/test_log_redaction.py.hbs")
    )
