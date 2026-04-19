package specrest.cli

enum Level:
  case Verbose, Info, Warn, Error

final class Logger(val level: Level):
  def verbose(msg: String): Unit =
    if levelRank(level) <= levelRank(Level.Verbose) then System.err.println(s"  $msg")
  def info(msg: String): Unit =
    if levelRank(level) <= levelRank(Level.Info) then System.err.println(msg)
  def success(msg: String): Unit =
    if levelRank(level) <= levelRank(Level.Info) then System.err.println(s"✔ $msg")
  def warn(msg: String): Unit =
    if levelRank(level) <= levelRank(Level.Warn) then System.err.println(s"⚠ $msg")
  def error(msg: String): Unit = System.err.println(s"✘ $msg")

  private def levelRank(l: Level): Int = l match
    case Level.Verbose => 0
    case Level.Info    => 1
    case Level.Warn    => 2
    case Level.Error   => 3

object Logger:
  def fromFlags(verbose: Boolean, quiet: Boolean): Logger =
    val level =
      if quiet then Level.Error
      else if verbose then Level.Verbose
      else Level.Info
    new Logger(level)
