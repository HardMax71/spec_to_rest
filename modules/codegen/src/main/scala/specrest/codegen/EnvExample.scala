package specrest.codegen

object EnvExample:

  final case class Entry(key: String, value: String, comment: Option[String] = None)

  private val AdminTokenComment =
    "Optional bearer credential for the /admin surface; leave unset to disable it (404)"

  private def baseEntries(in: Compose.Inputs): List[Entry] = in.family match
    case Compose.Family.Python =>
      List(
        Entry("DATABASE_URL", in.dsnComposeNetwork, in.envExampleHeaderLine),
        Entry(
          "BASE_URL",
          s"http://localhost:${in.appPort}",
          Some("Base URL the service is reachable at (used for constructing absolute URLs)")
        ),
        Entry("LOG_LEVEL", "info", Some("Logging level: debug, info, warning, error")),
        Entry("ADMIN_TOKEN", "", Some(AdminTokenComment))
      )
    case Compose.Family.GoTs =>
      List(
        Entry("DATABASE_URL", in.dsnComposeNetwork),
        Entry("BASE_URL", s"http://localhost:${in.appPort}"),
        Entry("PORT", in.appPort.toString),
        Entry("LOG_LEVEL", "info"),
        Entry("ADMIN_TOKEN", "", Some(AdminTokenComment))
      )

  def render(in: Compose.Inputs, extra: List[Entry] = Nil): String =
    val base = renderEntries(in.family, baseEntries(in) ++ extra)
    val prod = if in.hasDbService then "\n" + prodAddendum(in) else ""
    base + prod

  def prodAddendum(in: Compose.Inputs): String =
    val keys = in.secretEnv.map((k, _) => s"# $k=").mkString("\n")
    s"""|# ─── Required for staging / production (no defaults) ────────────────────────
        |# docker-compose.prod.yml fails fast (`$${KEY:?…}`) if any of these is unset.
        |$keys
        |""".stripMargin

  private def renderEntries(family: Compose.Family, entries: List[Entry]): String = family match
    case Compose.Family.Python =>
      entries.map: e =>
        e.comment match
          case Some(c) => s"# $c\n${e.key}=${e.value}\n"
          case None    => s"${e.key}=${e.value}\n"
      .mkString("\n")
    case Compose.Family.GoTs =>
      entries.map(e => s"${e.key}=${e.value}").mkString("", "\n", "\n")
