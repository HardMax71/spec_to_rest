package specrest.parser

import org.antlr.v4.runtime.{
  BaseErrorListener,
  CharStreams,
  CommonTokenStream,
  RecognitionException,
  Recognizer
}
import specrest.parser.generated.{SpecLexer, SpecParser}

final case class ParseError(line: Int, column: Int, message: String)

final case class ParseResult(tree: SpecParser.SpecFileContext, errors: List[ParseError])

object Parse:

  def parseSpec(input: String): ParseResult =
    val chars  = CharStreams.fromString(input)
    val lexer  = new SpecLexer(chars)
    val tokens = new CommonTokenStream(lexer)
    val parser = new SpecParser(tokens)

    val errors = scala.collection.mutable.ListBuffer.empty[ParseError]
    val listener: BaseErrorListener = new BaseErrorListener:
      override def syntaxError(
          recognizer: Recognizer[?, ?],
          offendingSymbol: Any,
          line: Int,
          column: Int,
          msg: String,
          e: RecognitionException
      ): Unit =
        errors += ParseError(line, column, msg)

    lexer.removeErrorListeners()
    parser.removeErrorListeners()
    lexer.addErrorListener(listener)
    parser.addErrorListener(listener)

    val tree = parser.specFile()
    ParseResult(tree, errors.toList)
