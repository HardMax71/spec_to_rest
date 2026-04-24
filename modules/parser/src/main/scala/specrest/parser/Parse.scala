package specrest.parser

import cats.effect.IO
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import specrest.ir.ParseError
import specrest.ir.VerifyError
import specrest.parser.generated.SpecLexer
import specrest.parser.generated.SpecParser

final case class ParseResult(tree: SpecParser.SpecFileContext, errors: List[ParseError])

object Parse:

  def parseSpec(input: String): IO[Either[VerifyError.Parse, ParseResult]] =
    IO.delay {
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

      val tree   = parser.specFile()
      val result = ParseResult(tree, errors.toList)
      if result.errors.isEmpty then Right(result) else Left(VerifyError.Parse(result.errors))
    }
