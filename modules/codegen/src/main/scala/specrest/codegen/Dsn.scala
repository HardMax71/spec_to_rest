package specrest.codegen

object Dsn:

  enum Shape derives CanEqual:
    case Url(scheme: String)
    case MysqlGo

  final case class Spec(
      shape: Shape,
      port: Int,
      suffix: String = ""
  ) derives CanEqual

  final case class Secrets(userKey: String, passwordKey: String, dbKey: String) derives CanEqual

  final case class Recipe(spec: Spec, secrets: Secrets) derives CanEqual

  def render(spec: Spec, host: String, user: String, password: String, db: String): String =
    spec.shape match
      case Shape.Url(scheme) =>
        s"$scheme://$user:$password@$host:${spec.port}/$db${spec.suffix}"
      case Shape.MysqlGo =>
        s"$user:$password@tcp($host:${spec.port})/$db${spec.suffix}"

  def renderDev(recipe: Recipe, host: String, snake: String): String =
    render(recipe.spec, host, snake, snake, snake)

  def renderProd(recipe: Recipe): String =
    render(
      recipe.spec,
      host = "db",
      user = prodEnvRef(recipe.secrets.userKey),
      password = prodEnvRef(recipe.secrets.passwordKey),
      db = prodEnvRef(recipe.secrets.dbKey)
    )

  private def prodEnvRef(key: String): String =
    s"$${$key:?$key is required for production}"
