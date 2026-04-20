package specrest.ir

sealed trait VerifyError derives CanEqual
object VerifyError:
  final case class Parse(errors: List[ParseError])                 extends VerifyError
  final case class Build(message: String, span: Option[Span])      extends VerifyError
  final case class Translator(message: String)                     extends VerifyError
  final case class AlloyTranslator(message: String)                extends VerifyError
  final case class Backend(message: String, cause: Option[String]) extends VerifyError

final case class ParseError(line: Int, column: Int, message: String)
