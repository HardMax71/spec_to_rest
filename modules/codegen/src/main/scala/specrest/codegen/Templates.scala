package specrest.codegen

import specrest.profile.Fastapi
import specrest.profile.LanguageId
import specrest.profile.TargetKey

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

final case class PythonFastapiPostgresTemplates(
    main: String,
    config: String,
    database: String,
    dbBase: String,
    redaction: String,
    pagination: String,
    modelEntity: String,
    schemaEntity: String,
    routerEntity: String,
    serviceEntity: String,
    alembicIni: String,
    alembicEnv: String,
    alembicMigration: String,
    alembicDelta: String,
    pyproject: String,
    dockerfile: String,
    makefile: String,
    gitignore: String,
    dockerignore: String,
    readme: String,
    ciWorkflow: String,
    testHealth: String,
    testLogRedaction: String,
    dafnyAdapter: String,
    synthService: String,
    modelServiceState: String,
    serviceStateOps: String,
    routerStateOps: String
)

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.Null"))
object Templates:
  private val root = s"templates/${TargetKey.frameworkPath(LanguageId.Python, Fastapi.id)}"

  private[codegen] def loadResource(rootPath: String, relPath: String): String =
    val resourcePath = s"$rootPath/$relPath"
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

  private def loadResource(relPath: String): String = loadResource(root, relPath)

  lazy val pythonFastapiPostgres: PythonFastapiPostgresTemplates =
    PythonFastapiPostgresTemplates(
      main = loadResource("main.py.hbs"),
      config = loadResource("config.py.hbs"),
      database = loadResource("database.py.hbs"),
      dbBase = loadResource("db/base.py.hbs"),
      redaction = loadResource("app/redaction.py.hbs"),
      pagination = loadResource("app/pagination.py.hbs"),
      modelEntity = loadResource("models/entity.py.hbs"),
      schemaEntity = loadResource("schemas/entity.py.hbs"),
      routerEntity = loadResource("routers/entity.py.hbs"),
      serviceEntity = loadResource("services/entity.py.hbs"),
      alembicIni = loadResource("alembic.ini.hbs"),
      alembicEnv = loadResource("alembic/env.py.hbs"),
      alembicMigration = loadResource("alembic/versions/001_initial_schema.py.hbs"),
      alembicDelta = loadResource("alembic/versions/NNN_schema_update.py.hbs"),
      pyproject = loadResource("pyproject.toml.hbs"),
      dockerfile = loadResource("Dockerfile.hbs"),
      makefile = loadResource("Makefile.hbs"),
      gitignore = loadResource("gitignore.hbs"),
      dockerignore = loadResource("dockerignore.hbs"),
      readme = loadResource("README.md.hbs"),
      ciWorkflow = loadResource("github/workflows/ci.yml.hbs"),
      testHealth = loadResource("tests/test_health.py.hbs"),
      testLogRedaction = loadResource("tests/test_log_redaction.py.hbs"),
      dafnyAdapter = loadResource("services/_dafny_adapter.py.hbs"),
      synthService = loadResource("services/_synth.py.hbs"),
      modelServiceState = loadResource("models/service_state.py.hbs"),
      serviceStateOps = loadResource("services/state_ops.py.hbs"),
      routerStateOps = loadResource("routers/state_ops.py.hbs")
    )
