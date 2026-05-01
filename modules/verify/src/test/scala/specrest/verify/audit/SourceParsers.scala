package specrest.verify.audit

import java.nio.file.Files
import java.nio.file.Path

object SourceParsers:

  /** Parse `inductive <name> where | foo (...) | bar (...)` from a Lean source file. Returns
    * lowercase constructor names. Robust to comments and whitespace. On parse failure (file
    * missing, inductive not found), returns the empty set so the containing test fails with a clear
    * "no Lean cases found" rather than throwing.
    */
  def parseLeanInductiveCases(path: Path, name: String): Set[String] =
    if !Files.exists(path) then return Set.empty
    val src            = Files.readString(path)
    val inductiveStart = s"inductive $name"
    val idx            = src.indexOf(inductiveStart)
    if idx < 0 then return Set.empty
    val tail = src.substring(idx)
    // Inductive block ends at the next blank line followed by another top-level decl,
    // or at `end <namespace>`. Use the first `\n\n` as a safe approximation.
    val end   = tail.indexOf("\n\n")
    val body  = if end > 0 then tail.substring(0, end) else tail
    val lines = body.split('\n').toList
    val cases = lines.flatMap: line =>
      val trimmed = line.trim
      if trimmed.startsWith("|") then
        val afterBar = trimmed.drop(1).trim
        // Strip any leading namespace dot pattern, take the first identifier
        val tok = afterBar.takeWhile(c => c.isLetterOrDigit || c == '_').toLowerCase
        if tok.nonEmpty then Some(tok) else None
      else None
    cases.toSet

  /** Parse `enum <name> derives ... case Foo(...), case Bar(...)` from Scala 3 source. Returns the
    * constructor names verbatim (case-sensitive).
    */
  def parseScalaEnumCases(path: Path, name: String): Set[String] =
    if !Files.exists(path) then return Set.empty
    val src       = Files.readString(path)
    val enumStart = s"enum $name"
    val idx       = src.indexOf(enumStart)
    if idx < 0 then return Set.empty
    val tail = src.substring(idx)
    // Find the next top-level `enum ` or `object ` or `class ` to bound the enum body.
    val nextDecl = List("\nenum ", "\nobject ", "\nclass ", "\nfinal case class ")
      .map(d => tail.indexOf(d))
      .filter(_ > 0)
      .minOption
      .getOrElse(tail.length)
    val body    = tail.substring(0, nextDecl)
    val pattern = """case\s+(\w+)""".r
    pattern.findAllMatchIn(body).map(_.group(1)).toSet
