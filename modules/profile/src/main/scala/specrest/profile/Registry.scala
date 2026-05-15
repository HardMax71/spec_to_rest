package specrest.profile

object Registry:

  private val Frameworks: Map[String, Framework] =
    Map(Fastapi.id -> Fastapi, Chi.id -> Chi, Express.id -> Express)

  def framework(id: String): Option[Framework] = Frameworks.get(id)

  def frameworkIds: List[String] = Frameworks.keys.toList.sorted

  def resolve(key: TargetKey): Either[String, DeploymentProfile] =
    Frameworks.get(key.framework) match
      case None =>
        Left(s"unknown framework '${key.framework}' (known: ${frameworkIds.mkString(", ")})")
      case Some(fw) if !fw.supportedLanguages.contains(key.language) =>
        Left(
          s"framework '${key.framework}' does not support language '${key.language.slug}' " +
            s"(supported: ${fw.supportedLanguages.toList.map(_.slug).sorted.mkString(", ")})"
        )
      case Some(fw) if !fw.supportedDialects.contains(key.database) =>
        Left(
          s"framework '${key.framework}' does not support database '${key.database.slug}' " +
            s"(supported: ${fw.supportedDialects.toList.map(_.slug).sorted.mkString(", ")})"
        )
      case Some(fw) => Right(fw.profile(key.language, key.database))

  def resolveSlug(slug: String): Either[String, DeploymentProfile] =
    TargetKey.parse(slug).flatMap(resolve)

  def getProfile(slug: String): DeploymentProfile =
    resolveSlug(slug) match
      case Right(p)  => p
      case Left(err) => throw new RuntimeException(s"unknown deployment target '$slug': $err")

  def listProfiles: List[String] =
    (for
      fw <- Frameworks.values.toList
      l  <- fw.supportedLanguages.toList
      d  <- fw.supportedDialects.toList
    yield TargetKey(l, fw.id, d).slug).sorted
