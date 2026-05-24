package specrest.codegen

object Compose:

  enum Family derives CanEqual:
    case Python, GoTs

  enum Restart derives CanEqual:
    case No, OnFailure, UnlessStopped

  enum DependsCondition derives CanEqual:
    case Healthy, CompletedSuccessfully

  final case class Healthcheck(
      test: List[String],
      interval: String = "5s",
      timeout: String = "3s",
      retries: Int = 10
  ) derives CanEqual

  final case class Limits(memory: String, cpus: String) derives CanEqual
  final case class Deploy(limits: Limits) derives CanEqual

  final case class Service(
      name: String,
      build: Option[String] = None,
      image: Option[String] = None,
      command: Option[List[String]] = None,
      envFile: Option[String] = None,
      environment: List[(String, String)] = Nil,
      ports: List[String] = Nil,
      volumes: List[String] = Nil,
      healthcheck: Option[Healthcheck] = None,
      dependsOn: List[(String, DependsCondition)] = Nil,
      restart: Option[Restart] = None,
      deploy: Option[Deploy] = None
  ) derives CanEqual

  final case class File(
      services: List[Service],
      volumes: List[String] = Nil,
      header: Option[String] = None
  ) derives CanEqual:
    def yaml: String = ComposeYaml.render(this)

  final case class Inputs(
      family: Family,
      appPort: Int,
      dbVolumeName: String,
      hasDbService: Boolean,
      dbImage: String,
      dbPort: String,
      dbVolumePath: String,
      dbHealthCmd: String,
      secretEnv: List[(String, String)],
      dsnEnvExample: String,
      dsnAppCompose: Option[String],
      envExampleHeaderLine: Option[String]
  ):
    def hasMigrations: Boolean = family == Family.Python

  object Preset:
    val AppProd: Limits    = Limits("512M", "0.5")
    val AppStaging: Limits = Limits("256M", "0.25")
    val DbProd: Limits     = Limits("1G", "1.0")
    val DbStaging: Limits  = Limits("512M", "0.5")

  def base(in: Inputs): File = in.family match
    case Family.Python => pythonBase(in)
    case Family.GoTs   => goTsBase(in)

  def overrideExample(in: Inputs): File =
    val header =
      """|# Local development overrides. Copy to docker-compose.override.yml — Compose
         |# auto-loads it alongside docker-compose.yml. Never overwritten by spec-to-rest compile.
         |# Common uses: hot-reload mounts, custom log levels, ad-hoc reverse-proxy services.
         |""".stripMargin
    val app = Service(
      name = "app",
      environment = List("LOG_LEVEL" -> "debug")
    )
    val services =
      if in.hasDbService then
        List(app, Service(name = "db", ports = List(s"${in.dbPort}:${in.dbPort}")))
      else List(app)
    File(services = services, header = Some(header))

  def staging(in: Inputs): File =
    overlay(in, Preset.AppStaging, Preset.DbStaging, Restart.OnFailure, "staging")
  def prod(in: Inputs): File =
    overlay(in, Preset.AppProd, Preset.DbProd, Restart.UnlessStopped, "production")

  private def overlay(in: Inputs, app: Limits, db: Limits, restart: Restart, label: String): File =
    val overlayName = if label == "production" then "prod" else "staging"
    val header =
      s"""|# $label overlay — apply with:
          |#   docker compose -f docker-compose.yml -f docker-compose.$overlayName.yml up -d
          |# Every secret below must be exported (or in .env) before `up`; Compose fails fast
          |# on missing values via the $${KEY:?…} substitution.
          |""".stripMargin
    val appService = Service(
      name = "app",
      restart = Some(restart),
      deploy = Some(Deploy(app))
    )
    val migrationsService =
      Option.when(in.hasMigrations)(Service(name = "migrations", restart = Some(Restart.No)))
    val dbService = Option.when(in.hasDbService):
      Service(
        name = "db",
        environment = in.secretEnv.map((k, _) => k -> s"$${$k:?$k is required for $label}"),
        restart = Some(restart),
        deploy = Some(Deploy(db))
      )
    File(
      services = appService :: migrationsService.toList ++ dbService.toList,
      header = Some(header)
    )

  private def pythonBase(in: Inputs): File =
    val dbService = Option.when(in.hasDbService):
      Service(
        name = "db",
        image = Some(in.dbImage),
        environment = in.secretEnv,
        volumes = List(s"${in.dbVolumeName}:${in.dbVolumePath}"),
        healthcheck = Some(Healthcheck(test = List("CMD-SHELL", in.dbHealthCmd)))
      )
    val migrationsService = Service(
      name = "migrations",
      build = Some("."),
      command = Some(List("alembic", "upgrade", "head")),
      envFile = Some(".env"),
      volumes = if in.hasDbService then Nil else List(s"${in.dbVolumeName}:/data"),
      dependsOn = if in.hasDbService then List("db" -> DependsCondition.Healthy) else Nil
    )
    val appDeps =
      if in.hasDbService then
        List(
          "db"         -> DependsCondition.Healthy,
          "migrations" -> DependsCondition.CompletedSuccessfully
        )
      else List("migrations" -> DependsCondition.CompletedSuccessfully)
    val appService = Service(
      name = "app",
      build = Some("."),
      envFile = Some(".env"),
      environment = List("ENABLE_TEST_ADMIN" -> "${ENABLE_TEST_ADMIN:-}"),
      ports = List(s"${in.appPort}:${in.appPort}"),
      volumes = if in.hasDbService then Nil else List(s"${in.dbVolumeName}:/data"),
      dependsOn = appDeps
    )
    File(
      services = dbService.toList :+ migrationsService :+ appService,
      volumes = List(in.dbVolumeName)
    )

  private def goTsBase(in: Inputs): File =
    val dsn = in.dsnAppCompose.getOrElse(in.dsnEnvExample)
    val appService = Service(
      name = "app",
      build = Some("."),
      environment = List(
        "DATABASE_URL" -> dsn,
        "BASE_URL"     -> s"http://localhost:${in.appPort}",
        "PORT"         -> in.appPort.toString
      ),
      ports = List(s"${in.appPort}:${in.appPort}"),
      dependsOn = if in.hasDbService then List("db" -> DependsCondition.Healthy) else Nil
    )
    val dbService = Option.when(in.hasDbService):
      Service(
        name = "db",
        image = Some(in.dbImage),
        environment = in.secretEnv,
        healthcheck = Some(Healthcheck(test = List("CMD-SHELL", in.dbHealthCmd))),
        volumes = List(s"${in.dbVolumeName}:${in.dbVolumePath}")
      )
    File(
      services = appService :: dbService.toList,
      volumes = if in.hasDbService then List(in.dbVolumeName) else Nil
    )
