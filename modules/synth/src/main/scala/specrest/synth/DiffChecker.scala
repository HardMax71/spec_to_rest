package specrest.synth

import specrest.dafny.DafnyMethodHeader

import scala.annotation.tailrec

final case class DiffViolation(message: String, diff: String) derives CanEqual

object DiffChecker:

  def check(header: DafnyMethodHeader, candidate: String): Either[DiffViolation, Unit] =
    forbiddenExtern(candidate) match
      case Some(violation) => Left(violation)
      case None            =>
        val name     = methodNameOf(header.signature)
        val expected = canonical(header)
        parseCandidate(candidate, name) match
          case None                               => Left(notFound(name))
          case Some(actual) if actual == expected => Right(())
          case Some(actual)                       => Left(contractsChanged(expected, actual))

  // ── canonical and observed clause sets ────────────────────────────────────

  final private case class ClauseSet(
      requires: List[String],
      ensures: List[String],
      modifies: List[String]
  ) derives CanEqual

  private def canonical(h: DafnyMethodHeader): ClauseSet = ClauseSet(
    h.requiresClauses.map(normalize).sorted,
    h.ensuresClauses.map(normalize).sorted,
    h.modifiesClauses.map(normalize).sorted
  )

  private def normalize(clause: String): String =
    clause.trim.replaceAll("\\s+", " ").stripSuffix(";").trim

  // ── candidate parsing ─────────────────────────────────────────────────────
  //
  // Dafny formats spec clauses one-per-line in our generator output, and the
  // LLM is told to copy them verbatim. A clause line is balanced (set literals
  // and forall triggers like `{TODO, DONE}` open and close on the same line).
  // The method body opens on a line whose `{` does *not* close on that line —
  // either standalone `{` or `modifies st {`. That's the termination signal,
  // and it's local to a single line, so multi-method inputs don't confuse it.

  private def parseCandidate(candidate: String, name: String): Option[ClauseSet] =
    locateMethod(candidate, name).map(specLines).map(classify)

  private def locateMethod(candidate: String, name: String): Option[List[String]] =
    val anchor = s"""\\bmethod\\s+${java.util.regex.Pattern.quote(name)}(?=[\\s(<])""".r
    val lines  = candidate.linesIterator.toList
    val idx    = lines.indexWhere(line => anchor.findFirstIn(line).isDefined)
    Option.when(idx >= 0)(lines.drop(idx + 1))

  /** Take lines until one contains an unbalanced `{`; trim that final line at the brace so any
    * clause text before it (e.g. `modifies st`) survives.
    */
  private def specLines(after: List[String]): List[String] =
    @tailrec def take(remaining: List[String], acc: List[String]): List[String] =
      remaining match
        case Nil          => acc.reverse
        case head :: rest =>
          unbalancedOpenBraceCol(head) match
            case None      => take(rest, head :: acc)
            case Some(col) => (head.substring(0, col) :: acc).reverse
    take(after, Nil)

  /** Column of the first `{` on this line whose matching `}` is not on the same line, or None if
    * every `{` on the line balances.
    */
  private def unbalancedOpenBraceCol(line: String): Option[Int] =
    @tailrec def scan(i: Int, depth: Int, candidate: Int): Int =
      if i >= line.length then candidate
      else
        line.charAt(i) match
          case '{' if depth == 0 => scan(i + 1, 1, i)
          case '{'               => scan(i + 1, depth + 1, candidate)
          case '}' if depth == 1 => scan(i + 1, 0, -1)
          case '}'               => scan(i + 1, depth - 1, candidate)
          case _                 => scan(i + 1, depth, candidate)
    scan(0, 0, -1) match
      case n if n < 0 => None
      case n          => Some(n)

  private def classify(lines: List[String]): ClauseSet =
    def collect(kw: String): List[String] =
      lines.flatMap: line =>
        val t = line.trim
        if t.startsWith(kw) then List(normalize(t.substring(kw.length))) else Nil
    ClauseSet(
      collect("requires ").sorted,
      collect("ensures ").sorted,
      collect("modifies ").sorted
    )

  // ── failure-path constructors ─────────────────────────────────────────────

  private def forbiddenExtern(candidate: String): Option[DiffViolation] =
    val externLines = candidate.linesIterator.filter(_.contains("{:extern")).toList
    Option.when(externLines.nonEmpty):
      DiffViolation(
        "candidate introduces a new {:extern} declaration",
        externLines.mkString("\n")
      )

  private def notFound(name: String): DiffViolation =
    DiffViolation(s"could not locate method '$name' in candidate", "")

  private def contractsChanged(expected: ClauseSet, actual: ClauseSet): DiffViolation =
    DiffViolation("contracts changed", renderDiff(expected, actual))

  private def renderDiff(expected: ClauseSet, actual: ClauseSet): String =
    List(
      diffSection("requires", expected.requires, actual.requires),
      diffSection("ensures", expected.ensures, actual.ensures),
      diffSection("modifies", expected.modifies, actual.modifies)
    ).filter(_.nonEmpty).mkString("\n")

  private def diffSection(label: String, want: List[String], got: List[String]): String =
    val missing = want.filterNot(got.contains).map(c => s"- $label $c")
    val extra   = got.filterNot(want.contains).map(c => s"+ $label $c")
    (missing ++ extra).mkString("\n")

  private val MethodNamePattern = """method\s+([A-Za-z_]\w*)""".r

  private def methodNameOf(signature: String): String =
    MethodNamePattern.findFirstMatchIn(signature).map(_.group(1)).getOrElse("")
