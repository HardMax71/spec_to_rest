package specrest.synth

import specrest.convention.dafny.DafnyMethodHeader

final case class DiffViolation(message: String, diff: String)

object DiffChecker:

  def check(header: DafnyMethodHeader, candidate: String): Either[DiffViolation, Unit] =
    if containsForbiddenExtern(candidate) then
      Left(
        DiffViolation(
          "candidate introduces a new {:extern} declaration",
          extractExternLines(candidate).mkString("\n")
        )
      )
    else
      val name        = methodNameOf(header.signature)
      val candClauses = extractClauses(candidate, name)
      candClauses match
        case None =>
          Left(DiffViolation(s"could not locate method '$name' in candidate", ""))
        case Some(found) =>
          val expected = ClauseSet(
            normalizeAll(header.requiresClauses),
            normalizeAll(header.ensuresClauses),
            normalizeAll(header.modifiesClauses)
          )
          val actual = ClauseSet(
            normalizeAll(found.requires),
            normalizeAll(found.ensures),
            normalizeAll(found.modifies)
          )
          if expected == actual then Right(())
          else Left(DiffViolation("contracts changed", renderDiff(expected, actual)))

  private def containsForbiddenExtern(candidate: String): Boolean =
    candidate.contains("{:extern")

  private def extractExternLines(candidate: String): List[String] =
    candidate.linesIterator.filter(_.contains("{:extern")).toList

  private def methodNameOf(signature: String): String =
    val afterMethod = signature.indexOf("method ")
    if afterMethod < 0 then ""
    else
      val nameStart = afterMethod + "method ".length
      val nameEnd   = signature.indexWhere(c => c == '(' || c == '<' || c == ' ', nameStart)
      if nameEnd < 0 then signature.substring(nameStart).trim
      else signature.substring(nameStart, nameEnd).trim

  final private case class ClauseSet(
      requires: List[String],
      ensures: List[String],
      modifies: List[String]
  ) derives CanEqual

  final private case class FoundClauses(
      requires: List[String],
      ensures: List[String],
      modifies: List[String]
  )

  private def extractClauses(candidate: String, methodName: String): Option[FoundClauses] =
    val lines  = candidate.linesIterator.toList
    val prefix = s"method $methodName"
    val start = lines.indexWhere: line =>
      val t = line.trim
      t.startsWith(prefix) && {
        val nextChar = t.lift(prefix.length)
        nextChar.forall(c => c == '(' || c == '<' || c == ' ')
      }
    if start < 0 then None
    else
      val rest         = lines.drop(start + 1).mkString("\n")
      val bodyStart    = findBodyOpener(rest)
      val clauseRegion = if bodyStart < 0 then rest else rest.substring(0, bodyStart)
      val collected = clauseRegion.linesIterator
        .map(_.trim)
        .filter(_.nonEmpty)
        .toList
      val req = collected.collect {
        case l if l.startsWith("requires ") => l.stripPrefix("requires ")
      }
      val ens = collected.collect {
        case l if l.startsWith("ensures ") => l.stripPrefix("ensures ")
      }
      val mod = collected.collect {
        case l if l.startsWith("modifies ") => l.stripPrefix("modifies ")
      }
      Some(FoundClauses(req, ens, mod))

  private enum ScanMode derives CanEqual:
    case Code, LineComment, BlockComment, StringLit

  private def findBodyOpener(text: String): Int =
    @scala.annotation.tailrec
    def loop(i: Int, depth: Int, candidate: Int, mode: ScanMode): Int =
      if i >= text.length then candidate
      else
        val c = text.charAt(i)
        mode match
          case ScanMode.LineComment =>
            if c == '\n' then loop(i + 1, depth, candidate, ScanMode.Code)
            else loop(i + 1, depth, candidate, ScanMode.LineComment)
          case ScanMode.BlockComment =>
            if c == '*' && i + 1 < text.length && text.charAt(i + 1) == '/' then
              loop(i + 2, depth, candidate, ScanMode.Code)
            else loop(i + 1, depth, candidate, ScanMode.BlockComment)
          case ScanMode.StringLit =>
            if c == '\\' && i + 1 < text.length then
              loop(i + 2, depth, candidate, ScanMode.StringLit)
            else if c == '"' then loop(i + 1, depth, candidate, ScanMode.Code)
            else loop(i + 1, depth, candidate, ScanMode.StringLit)
          case ScanMode.Code =>
            c match
              case '"' => loop(i + 1, depth, candidate, ScanMode.StringLit)
              case '/' if i + 1 < text.length && text.charAt(i + 1) == '/' =>
                loop(i + 2, depth, candidate, ScanMode.LineComment)
              case '/' if i + 1 < text.length && text.charAt(i + 1) == '*' =>
                loop(i + 2, depth, candidate, ScanMode.BlockComment)
              case '{' =>
                val newCand = if depth == 0 && candidate < 0 then i else candidate
                loop(i + 1, depth + 1, newCand, ScanMode.Code)
              case '}' =>
                val newDepth = depth - 1
                val newCand  = if newDepth <= 0 then -1 else candidate
                loop(i + 1, newDepth, newCand, ScanMode.Code)
              case _ => loop(i + 1, depth, candidate, ScanMode.Code)
    loop(0, 0, -1, ScanMode.Code)

  private def normalize(clause: String): String =
    val trimmed = clause.trim.replaceAll("\\s+", " ")
    if trimmed.endsWith(";") then trimmed.dropRight(1).trim else trimmed

  private def normalizeAll(clauses: List[String]): List[String] =
    clauses.map(normalize).sorted

  private def renderDiff(expected: ClauseSet, actual: ClauseSet): String =
    val parts = List(
      diffSection("requires", expected.requires, actual.requires),
      diffSection("ensures", expected.ensures, actual.ensures),
      diffSection("modifies", expected.modifies, actual.modifies)
    ).filter(_.nonEmpty)
    parts.mkString("\n")

  private def diffSection(label: String, expected: List[String], actual: List[String]): String =
    val missing = expected.filterNot(actual.contains)
    val extra   = actual.filterNot(expected.contains)
    if missing.isEmpty && extra.isEmpty then ""
    else
      val m = missing.map(c => s"- $label $c")
      val e = extra.map(c => s"+ $label $c")
      (m ++ e).mkString("\n")
