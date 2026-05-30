package specrest.synth

import io.circe.parser.parse as circeParse

object DafnyOutputParser:

  final case class MethodResult(name: String, outcome: String, errors: List[VerifierError])
      derives CanEqual

  def parseLog(json: String, source: String): Either[String, List[MethodResult]] =
    circeParse(json).left
      .map(e => s"invalid JSON from dafny --log-format json: ${e.message}")
      .flatMap: doc =>
        val sourceLines = source.linesIterator.toList
        doc.hcursor.downField("verificationResults").values match
          case None         => Left("dafny --log-format json output missing 'verificationResults' field")
          case Some(values) =>
            Right(values.toList.map(j => decodeMethod(j.hcursor, sourceLines)))

  private def decodeMethod(
      c: io.circe.HCursor,
      sourceLines: List[String]
  ): MethodResult =
    val name    = c.get[String]("name").getOrElse("<unknown>")
    val outcome = c.get[String]("outcome").getOrElse("Unknown")
    val errors  =
      if outcome == "Correct" || outcome == "Valid" then Nil
      else extractErrors(c, outcome, sourceLines)
    MethodResult(name, outcome, errors)

  private def extractErrors(
      c: io.circe.HCursor,
      methodOutcome: String,
      sourceLines: List[String]
  ): List[VerifierError] =
    val vcs   = c.downField("vcResults").values.getOrElse(Iterable.empty).toList
    val perVc = vcs.flatMap: vc =>
      val vcCur     = vc.hcursor
      val vcOutcome = vcCur.get[String]("outcome").getOrElse(methodOutcome)
      if vcOutcome == "Correct" || vcOutcome == "Valid" then Nil
      else
        val all = vcCur
          .downField("assertions")
          .values
          .getOrElse(Iterable.empty)
          .toList
        val userFacing = all.filter: a =>
          a.hcursor.get[String]("description").toOption.exists(isUserFacing)
        val keep = if userFacing.nonEmpty then userFacing else all
        keep.map(a => decodeAssertion(a.hcursor, vcOutcome, sourceLines))
    if perVc.isEmpty then
      val cat = outcomeCategory(methodOutcome).getOrElse(classify(methodOutcome))
      List(VerifierError(category = cat, message = methodOutcome))
    else perVc

  private def isUserFacing(description: String): Boolean =
    val d = description.toLowerCase
    d.contains("postcondition") ||
    d.contains("precondition") ||
    d.contains("invariant") ||
    d.contains("decreases") ||
    d.contains("terminate") ||
    d.contains("loop frame") ||
    d.contains("assertion") ||
    d.contains("assert ") ||
    d.contains("ensures clause") ||
    d.contains("requires clause")

  private def decodeAssertion(
      a: io.circe.HCursor,
      vcOutcome: String,
      sourceLines: List[String]
  ): VerifierError =
    val description = a.get[String]("description").getOrElse(vcOutcome)
    val line        = a.get[Int]("line").toOption
    val col         = a.get[Int]("col").toOption
    val category    = if vcOutcome == "Valid" || vcOutcome == "Correct" then "unknown"
    else outcomeCategory(vcOutcome).getOrElse(classify(description))
    VerifierError(
      category = category,
      message = description,
      line = line,
      column = col,
      relatedClause = line.flatMap(l => clauseAt(sourceLines, l))
    )

  private def outcomeCategory(outcome: String): Option[String] = outcome match
    case "TimedOut" | "TimeOut"             => Some("timeout")
    case "OutOfMemory" | "OutOfResource"    => Some("timeout")
    case "Inconclusive" | "SolverException" => Some("unknown")
    case _                                  => None

  private def classify(msg: String): String =
    val m = msg.toLowerCase
    if m.contains("postcondition") then "postcondition_violation"
    else if m.contains("precondition") then "precondition_violation"
    else if m.contains("invariant") && m.contains("entry") then "loop_invariant_not_established"
    else if m.contains("invariant") then "loop_invariant_failure"
    else if m.contains("decreases") || m.contains("terminate") then "decreases_failure"
    else if m.contains("assertion") || m.contains("assert ") then "assertion_failure"
    else if m.contains("timeout") || m.contains("timed out") then "timeout"
    else if m.contains("type") then "type_error"
    else "unknown"

  private def clauseAt(sourceLines: List[String], oneBasedLine: Int): Option[String] =
    val idx = oneBasedLine - 1
    if idx < 0 || idx >= sourceLines.length then None
    else
      val raw = sourceLines(idx).trim
      if raw.isEmpty then None else Some(raw.stripSuffix(";"))
