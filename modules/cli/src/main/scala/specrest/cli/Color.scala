package specrest.cli

enum ColorMode derives CanEqual:
  case Auto, On, Off

object ColorMode:
  def parse(s: String): Either[String, ColorMode] = s.toLowerCase match
    case "auto"          => Right(Auto)
    case "always" | "on" => Right(On)
    case "never" | "off" => Right(Off)
    case other           =>
      Left(
        s"unknown color mode '$other'; choices: auto, always (alias: on), never (alias: off)"
      )

final class Palette(val enabled: Boolean):
  private inline def wrap(code: String, s: String): String =
    if enabled then s"$code$s${Palette.Reset}" else s
  def green(s: String): String  = wrap(Palette.Green, s)
  def yellow(s: String): String = wrap(Palette.Yellow, s)
  def red(s: String): String    = wrap(Palette.Red, s)
  def dim(s: String): String    = wrap(Palette.Dim, s)
  def bold(s: String): String   = wrap(Palette.Bold, s)

object Palette:
  val Reset: String  = "\u001b[0m"
  val Green: String  = "\u001b[32m"
  val Yellow: String = "\u001b[33m"
  val Red: String    = "\u001b[31m"
  val Bold: String   = "\u001b[1m"
  val Dim: String    = "\u001b[2m"

  def resolve(mode: ColorMode): Palette =
    val on = mode match
      case ColorMode.On   => true
      case ColorMode.Off  => false
      case ColorMode.Auto => autoEnable()
    new Palette(on)

  private def autoEnable(): Boolean =
    val noColor = Option(System.getenv("NO_COLOR")).exists(_.nonEmpty)
    if noColor then false
    else
      val forced = Option(System.getenv("CLICOLOR_FORCE")).exists(v => v.nonEmpty && v != "0")
      if forced then true
      else Option(System.console()).isDefined
