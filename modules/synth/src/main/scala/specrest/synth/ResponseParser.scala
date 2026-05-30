package specrest.synth

import scala.annotation.tailrec

final case class ParseError(message: String) derives CanEqual

object ResponseParser:

  def extractCodeBlock(response: String): Either[ParseError, String] =
    val fences = List("```dafny", "```Dafny", "```csharp", "```cs", "```")
    val opened = fences.iterator
      .flatMap(tag => indexAfterTag(response, tag).iterator)
      .nextOption()
    opened match
      case None => Left(ParseError("no fenced code block found in LLM response"))
      case Some((tag, start)) =>
        val end = response.indexOf("```", start)
        if end < 0 then Left(ParseError(s"unterminated fenced block (opened with $tag)"))
        else Right(response.substring(start, end).stripPrefix("\n").stripSuffix("\n"))

  // The prompt asks the model to put helper functions/lemmas BEFORE the method
  // declaration. extractMethodBody returns only the in-brace body, so those helpers
  // would be discarded and the spliced body would reference undefined symbols. This
  // returns that pre-method helper section so the splicer can inject it at module scope.
  def helperSection(code: String, methodName: String): String =
    val idx = code.indexOf(s"method $methodName")
    if idx <= 0 then "" else code.substring(0, idx).trim

  def extractMethodBody(code: String, methodName: String): Either[ParseError, String] =
    val needle = s"method $methodName"
    val idx    = code.indexOf(needle)
    if idx < 0 then
      val trimmed = code.trim
      if trimmed.startsWith("{") then
        val firstBrace = code.indexOf('{')
        extractFirstBraceBlock(code, firstBrace)
      else Right(trimmed)
    else
      val openBrace = code.indexOf('{', idx)
      if openBrace < 0 then
        Left(ParseError(s"could not locate body opening '{' for method $methodName"))
      else extractFirstBraceBlock(code, openBrace)

  private def indexAfterTag(s: String, tag: String): Option[(String, Int)] =
    val i = s.indexOf(tag)
    if i < 0 then None
    else
      val after = i + tag.length
      val nl    = s.indexOf('\n', after)
      Some((tag, if nl < 0 then after else nl + 1))

  private def extractFirstBraceBlock(s: String, openIdx: Int): Either[ParseError, String] =
    if openIdx < 0 || openIdx >= s.length || s.charAt(openIdx) != '{' then
      Left(ParseError("expected '{' at start of method body"))
    else
      matchingClose(s, openIdx) match
        case None      => Left(ParseError("unmatched '{' in method body"))
        case Some(end) => Right(s.substring(openIdx + 1, end).stripPrefix("\n").stripSuffix("\n"))

  private enum ScanMode derives CanEqual:
    case Code, LineComment, BlockComment, StringLit

  @tailrec
  private def matchingClose(
      s: String,
      i: Int,
      depth: Int,
      mode: ScanMode
  ): Option[Int] =
    if i >= s.length then None
    else
      val c = s.charAt(i)
      mode match
        case ScanMode.LineComment =>
          if c == '\n' then matchingClose(s, i + 1, depth, ScanMode.Code)
          else matchingClose(s, i + 1, depth, ScanMode.LineComment)
        case ScanMode.BlockComment =>
          if c == '*' && i + 1 < s.length && s.charAt(i + 1) == '/' then
            matchingClose(s, i + 2, depth, ScanMode.Code)
          else matchingClose(s, i + 1, depth, ScanMode.BlockComment)
        case ScanMode.StringLit =>
          if c == '\\' && i + 1 < s.length then matchingClose(s, i + 2, depth, ScanMode.StringLit)
          else if c == '"' then matchingClose(s, i + 1, depth, ScanMode.Code)
          else matchingClose(s, i + 1, depth, ScanMode.StringLit)
        case ScanMode.Code =>
          c match
            case '"' => matchingClose(s, i + 1, depth, ScanMode.StringLit)
            case '/' if i + 1 < s.length && s.charAt(i + 1) == '/' =>
              matchingClose(s, i + 2, depth, ScanMode.LineComment)
            case '/' if i + 1 < s.length && s.charAt(i + 1) == '*' =>
              matchingClose(s, i + 2, depth, ScanMode.BlockComment)
            case '{'               => matchingClose(s, i + 1, depth + 1, ScanMode.Code)
            case '}' if depth == 1 => Some(i)
            case '}'               => matchingClose(s, i + 1, depth - 1, ScanMode.Code)
            case _                 => matchingClose(s, i + 1, depth, ScanMode.Code)

  private def matchingClose(s: String, openIdx: Int): Option[Int] =
    matchingClose(s, openIdx + 1, 1, ScanMode.Code)
