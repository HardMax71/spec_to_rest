package specrest.synth

import scala.util.matching.Regex

object FileAssembly:

  val Placeholder: String = "  // YOUR CODE HERE\n"

  final case class SpliceFailure(message: String) derives CanEqual

  def splice(
      skeleton: String,
      methodName: String,
      body: String
  ): Either[SpliceFailure, String] =
    val anchor = methodAnchor(methodName)
    anchor.findFirstMatchIn(skeleton) match
      case None =>
        Left(SpliceFailure(s"method '$methodName' not found in skeleton"))
      case Some(m) =>
        val placeholderAt = skeleton.indexOf(Placeholder, m.end)
        if placeholderAt < 0 then
          Left(SpliceFailure(s"placeholder not found after method '$methodName'"))
        else
          val before = skeleton.substring(0, placeholderAt)
          val after  = skeleton.substring(placeholderAt + Placeholder.length)
          val indented = body.linesIterator.map(line =>
            if line.isEmpty then line else s"  $line"
          ).mkString("\n")
          Right(before + indented + (if indented.endsWith("\n") then "" else "\n") + after)

  private def methodAnchor(name: String): Regex =
    s"""\\bmethod\\s+${Regex.quote(name)}(?=[\\s(<])""".r
