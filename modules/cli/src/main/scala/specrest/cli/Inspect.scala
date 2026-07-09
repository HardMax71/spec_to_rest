package specrest.cli

import cats.effect.IO
import io.circe.Json
import io.circe.Printer
import io.circe.syntax.EncoderOps
import specrest.convention.Classify
import specrest.dafny.DafnyMethodHeader
import specrest.dafny.Generator as DafnyGenerator
import specrest.ir.Serialize.given
import specrest.ir.generated.SpecRestGenerated.*
import specrest.synth.PromptBuilder

import java.io.PrintStream

enum InspectFormat derives CanEqual:
  case Summary, Json, Ir, Dafny, DafnyPrompt

object InspectFormat:
  def parse(s: String): Either[String, InspectFormat] = s match
    case "summary"      => Right(Summary)
    case "json"         => Right(Json)
    case "ir"           => Right(Ir)
    case "dafny"        => Right(Dafny)
    case "dafny-prompt" => Right(DafnyPrompt)
    case other =>
      Left(s"unknown format '$other'; choices: summary, json, ir, dafny, dafny-prompt")

object Inspect:

  private given CanEqual[synthesis_strategy, synthesis_strategy] = CanEqual.derived

  def run(
      specFile: String,
      format: InspectFormat,
      log: Logger,
      out: PrintStream = System.out,
      operation: Option[String] = None
  ): IO[ExitStatus] =
    Check.withParsedIR(specFile, log): ir =>
      renderIR(ir, format, operation) match
        case Right(text) => IO.blocking(out.println(text)).as(ExitStatus.Ok)
        case Left(msg) =>
          IO.delay(log.error(s"$specFile: $msg")).as(ExitStatus.Translator)

  private def renderIR(
      ir: ServiceIRFull,
      format: InspectFormat,
      operation: Option[String]
  ): Either[String, String] =
    val classifications = Classify.classifyOperations(ir)
    format match
      case InspectFormat.Json =>
        val printer = Printer.spaces2.copy(dropNullValues = false)
        val irJson  = (ir: service_ir).asJson
        val strategy = Json.obj(
          classifications.map(c =>
            classificationOperationName(c) ->
              Json.fromString(synthesisStrategyLabel(classificationStrategy(c)))
          )*
        )
        val combined = irJson.deepMerge(Json.obj("synthesis_strategy" -> strategy))
        Right(printer.print(combined))
      case InspectFormat.Summary =>
        val (direct, llm) = strategyTally(classifications)
        val opsLine =
          s"  ${svcEntities(ir).length} entities, ${svcEnums(ir).length} enums, ${svcOperations(ir).length} operations " +
            s"($direct DIRECT_EMIT, $llm LLM_SYNTHESIS), ${svcInvariants(ir).length} invariants"
        val perOp = classifications.map: c =>
          s"    ${classificationOperationName(c)}: ${synthesisStrategyLabel(classificationStrategy(c))}"
        Right((s"Service: ${svcName(ir)}" :: opsLine :: perOp).mkString("\n"))
      case InspectFormat.Ir =>
        Right(ir.toString)
      case InspectFormat.Dafny =>
        DafnyGenerator
          .generate(ir)
          .map(_.text.stripSuffix("\n"))
          .left
          .map { err =>
            val loc = err.span.fold("") {
              case SpanT(line, col, _, _) =>
                s" at $line:$col"
            }
            s"Dafny generation failed$loc: ${err.message}"
          }
      case InspectFormat.DafnyPrompt =>
        renderDafnyPrompt(ir, classifications, operation)

  private def isDirectEmit(s: synthesis_strategy): Boolean = s match
    case _: DirectEmit => true
    case _             => false

  private def isLlmSynthesis(s: synthesis_strategy): Boolean = s match
    case _: LlmSynthesis => true
    case _               => false

  private def strategyTally(classifications: List[operation_classification]): (Int, Int) =
    val d = classifications.count(c => isDirectEmit(classificationStrategy(c)))
    val l = classifications.count(c => isLlmSynthesis(classificationStrategy(c)))
    (d, l)

  private def renderDafnyPrompt(
      ir: ServiceIRFull,
      classifications: List[operation_classification],
      operation: Option[String]
  ): Either[String, String] =
    DafnyGenerator.generate(ir).left.map { err =>
      val loc = err.span.fold("") {
        case SpanT(line, col, _, _) =>
          s" at $line:$col"
      }
      s"Dafny generation failed$loc: ${err.message}"
    }.flatMap { dafny =>
      val byName: Map[String, DafnyMethodHeader] = dafny.methods.map(h => h.name -> h).toMap
      val targets = operation match
        case Some(name) =>
          classifications
            .filter(c => classificationOperationName(c) == name)
            .map(c => (c, byName.get(classificationOperationName(c))))
        case None =>
          classifications
            .filter(c => isLlmSynthesis(classificationStrategy(c)))
            .map(c => (c, byName.get(classificationOperationName(c))))
      val rendered = targets.flatMap:
        case (c, Some(header)) =>
          val prompt = PromptBuilder.initial(c, header, dafny.text)
          List(formatPrompt(classificationOperationName(c), prompt))
        case _ => Nil
      if targets.isEmpty then
        operation match
          case Some(name) => Left(s"operation '$name' not found")
          case None       => Right("(no operations classified as LLM_SYNTHESIS)")
      else if rendered.isEmpty then
        Left("no Dafny method headers were generated for the requested operations")
      else Right(rendered.mkString("\n\n---\n\n"))
    }

  private def formatPrompt(name: String, p: specrest.synth.Prompt): String =
    s"""# Operation: $name
       |
       |## System
       |${p.system}
       |
       |## User
       |${p.user}""".stripMargin
