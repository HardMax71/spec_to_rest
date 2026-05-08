package specrest.synth

import specrest.convention.OperationClassification
import specrest.convention.dafny.DafnyMethodHeader

import scala.io.Source
import scala.util.Using

final case class Prompt(system: String, user: String)

object PromptBuilder:

  private val systemRoot = "/specrest/synth/prompts"

  lazy val systemInitial: String = loadResource("initial.system.txt")
  lazy val systemRepair: String  = loadResource("repair.system.txt")

  def initial(
      classification: OperationClassification,
      header: DafnyMethodHeader,
      skeleton: String
  ): Prompt =
    val sections = List(
      methodSignatureSection(header),
      domainSection(classification, header),
      typeDefinitionsSection(skeleton),
      fewShotSection(classification),
      taskSection(classification.operationName)
    )
    Prompt(systemInitial, sections.mkString("\n\n"))

  def repair(
      classification: OperationClassification,
      header: DafnyMethodHeader,
      skeleton: String,
      previousBody: String,
      error: VerifierError
  ): Prompt =
    val sections = List(
      previousAttemptSection(previousBody),
      verifierErrorSection(error),
      diagnosisSection(error),
      methodSignatureSection(header),
      taskSection(classification.operationName)
    )
    Prompt(systemRepair, sections.mkString("\n\n"))

  private def methodSignatureSection(header: DafnyMethodHeader): String =
    val clauses =
      (header.requiresClauses.map(c => s"  requires $c") ++
        header.ensuresClauses.map(c => s"  ensures $c") ++
        header.modifiesClauses.map(c => s"  modifies $c")).mkString("\n")
    s"""## Method Signature (immutable)
       |```dafny
       |${header.signature}
       |$clauses
       |```""".stripMargin

  private def domainSection(
      c: OperationClassification,
      header: DafnyMethodHeader
  ): String =
    val ensuresSummary =
      if header.ensuresClauses.isEmpty then "no postconditions specified"
      else header.ensuresClauses.mkString("; ")
    s"""## Domain Description
       |Operation `${c.operationName}` is classified as ${c.kind} (HTTP ${c.method}, rule ${c
        .matchedRule}).
       |Postcondition summary: $ensuresSummary""".stripMargin

  private def typeDefinitionsSection(skeleton: String): String =
    val typeBlock = extractTypeBlock(skeleton)
    s"""## Type Definitions
       |```dafny
       |$typeBlock
       |```""".stripMargin

  private def extractTypeBlock(skeleton: String): String =
    val lines       = skeleton.linesIterator.toList
    val firstMethod = lines.indexWhere(_.trim.startsWith("method "))
    val end         = if firstMethod < 0 then lines.length else firstMethod
    lines.take(end).mkString("\n").trim

  private def fewShotSection(c: OperationClassification): String =
    val snippets = FewShot.selectFor(c.kind).take(2)
    val rendered = snippets
      .map: s =>
        s"""```dafny
           |${FewShot.text(s).stripLineEnd}
           |```""".stripMargin
      .mkString("\n\n")
    s"""## Similar Verified Examples
       |$rendered""".stripMargin

  private def taskSection(name: String): String =
    s"""## Your Task
       |Produce the complete method body for `$name`. Return your code inside a single
       |```dafny fenced block. Include any helper lemmas BEFORE the method declaration.""".stripMargin

  private def previousAttemptSection(body: String): String =
    s"""## Previous Attempt (FAILED)
       |```dafny
       |$body
       |```""".stripMargin

  private def verifierErrorSection(e: VerifierError): String =
    val lineSuffix = e.line.fold("")(l => s" at line $l")
    s"""## Verifier Error
       |**${e.category}**$lineSuffix: ${e.message}""".stripMargin

  private def diagnosisSection(e: VerifierError): String =
    val cx     = e.counterexample.fold("")(c => s"\n\nCounterexample:\n```\n$c\n```")
    val clause = e.relatedClause.fold("")(c => s"\n\nRelated clause: `$c`")
    s"""## Diagnosis
       |${repairHint(e.category)}$clause$cx""".stripMargin

  private def repairHint(category: String): String = category match
    case "postcondition_violation" =>
      "The implementation does not establish the ensures clause on at least one return path. Add intermediate assertions or branch logic that closes the gap."
    case "precondition_violation" =>
      "A method/function call does not establish its requires clause. Add a guard or assertion that proves the precondition holds at the call site."
    case "loop_invariant_failure" =>
      "Either strengthen the loop invariant or fix the body so the invariant is preserved."
    case "loop_invariant_not_established" =>
      "The invariant is not true on entry. Fix initialization or weaken the invariant."
    case "decreases_failure" =>
      "Adjust the decreases expression so it strictly decreases on each iteration / recursive call."
    case "assertion_failure" =>
      "An intermediate assertion does not hold. Remove it if redundant or strengthen the local reasoning."
    case "type_error" =>
      "Fix the type annotation to match the declared parameter / return types."
    case "syntax_error" =>
      "The previous response is not valid Dafny. Re-emit a syntactically valid body."
    case "timeout" =>
      "The verification condition is too complex; simplify the body or add ghost annotations to break the proof into smaller steps."
    case _ =>
      "Inspect the failing clause and adjust the body so verification succeeds."

  private def loadResource(name: String): String =
    val path = s"$systemRoot/$name"
    val stream = Option(getClass.getResourceAsStream(path))
      .getOrElse(sys.error(s"Prompt resource not found: $path"))
    Using.resource(stream): in =>
      Source.fromInputStream(in, "UTF-8").mkString
