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
    val lines = candidate.linesIterator.toList
    val start = lines.indexWhere(_.trim.startsWith(s"method $methodName"))
    if start < 0 then None
    else
      val rest = lines.drop(start + 1)
      val clauses = rest.takeWhile: line =>
        val t = line.trim
        !t.startsWith("{") && t.nonEmpty
      val req = clauses.collect {
        case l if l.trim.startsWith("requires ") => l.trim.stripPrefix("requires ")
      }
      val ens = clauses.collect {
        case l if l.trim.startsWith("ensures ") => l.trim.stripPrefix("ensures ")
      }
      val mod = clauses.collect {
        case l if l.trim.startsWith("modifies ") => l.trim.stripPrefix("modifies ")
      }
      Some(FoundClauses(req, ens, mod))

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
