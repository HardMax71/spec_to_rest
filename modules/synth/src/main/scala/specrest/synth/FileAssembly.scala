package specrest.synth

import scala.util.matching.Regex

object FileAssembly:

  val Placeholder: String = "  // YOUR CODE HERE\n"

  final case class SpliceFailure(message: String) derives CanEqual

  // A verified method body plus the helper functions/lemmas the candidate
  // declared before it. The CEGIS verifier checks body and helpers spliced
  // together, so consumers must splice the same pair or the body's helper
  // references dangle (unresolved identifiers at dafny translate).
  final case class MethodPart(body: String, helpers: String = "") derives CanEqual

  def spliceAll(
      skeleton: String,
      parts: Map[String, MethodPart]
  ): Either[SpliceFailure, String] =
    parts.toList.foldLeft[Either[SpliceFailure, String]](Right(skeleton)):
      case (Left(e), _)             => Left(e)
      case (Right(current), (n, p)) => splice(current, n, p.body, p.helpers)

  def splice(
      skeleton: String,
      methodName: String,
      body: String,
      helpers: String = ""
  ): Either[SpliceFailure, String] =
    val anchor = methodAnchor(methodName)
    anchor.findFirstMatchIn(skeleton) match
      case None =>
        Left(SpliceFailure(s"method '$methodName' not found in skeleton"))
      case Some(m0) =>
        // The model emits helper functions/lemmas before the method declaration; inject
        // them at module scope just before the method so the spliced body can call them.
        val src =
          if helpers.trim.isEmpty then skeleton
          else
            skeleton.substring(0, m0.start) + helpers.trim + "\n\n" + skeleton.substring(m0.start)
        anchor.findFirstMatchIn(src) match
          case None =>
            Left(SpliceFailure(s"method '$methodName' not found in skeleton"))
          case Some(m) =>
            val nextMethodAt = NextMethodAnchor.findFirstMatchIn(src.substring(m.end)) match
              case Some(nm) => m.end + nm.start
              case None     => src.length
            val placeholderAt = src.indexOf(Placeholder, m.end)
            if placeholderAt < 0 || placeholderAt >= nextMethodAt then
              Left(SpliceFailure(s"placeholder not found within method '$methodName'"))
            else
              val before = src.substring(0, placeholderAt)
              val after  = src.substring(placeholderAt + Placeholder.length)
              val indented = body.linesIterator
                .map(line => if line.isEmpty then line else s"  $line")
                .mkString("\n")
              Right(before + indented + (if indented.endsWith("\n") then "" else "\n") + after)

  private def methodAnchor(name: String): Regex =
    s"""\\bmethod\\s+${Regex.quote(name)}(?=[\\s(<])""".r

  private val NextMethodAnchor: Regex = """\bmethod\s+\w""".r
