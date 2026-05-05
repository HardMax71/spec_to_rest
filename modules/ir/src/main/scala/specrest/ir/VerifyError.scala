package specrest.ir

import specrest.ir.generated.SpecRestGenerated.span_t

sealed trait VerifyError derives CanEqual
object VerifyError:
  final case class Parse(errors: List[ParseError])                 extends VerifyError
  final case class Build(message: String, span: Option[span_t])    extends VerifyError
  final case class Translator(message: String)                     extends VerifyError
  final case class AlloyTranslator(message: String)                extends VerifyError
  final case class Backend(message: String, cause: Option[String]) extends VerifyError

final case class ParseError(line: Int, column: Int, message: String)
