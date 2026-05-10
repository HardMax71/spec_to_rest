package specrest.profile

object Registry:

  private val Profiles: Map[String, DeploymentProfile] = Map(
    "python-fastapi-postgres" -> PythonFastapi.profile,
    "go-chi-postgres"         -> GoChiPostgres.profile,
    "ts-express-postgres"     -> TsExpressPostgres.profile
  )

  def getProfile(name: String): DeploymentProfile =
    Profiles.getOrElse(
      name,
      throw new RuntimeException(
        s"Unknown deployment profile '$name'. Available: ${Profiles.keys.mkString(", ")}"
      )
    )

  def listProfiles: List[String] = Profiles.keys.toList
