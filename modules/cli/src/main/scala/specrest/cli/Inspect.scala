package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import io.circe.Json
import io.circe.Printer
import io.circe.syntax.EncoderOps
import specrest.convention.Classify
import specrest.convention.OperationClassification
import specrest.convention.SynthesisStrategy
import specrest.convention.dafny.DafnyMethodHeader
import specrest.convention.dafny.Generator as DafnyGenerator
import specrest.ir.Serialize.given
import specrest.ir.VerifyError
import specrest.ir.generated.SpecRestGenerated.*
import specrest.parser.Builder
import specrest.parser.Parse
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

  def run(
      specFile: String,
      format: InspectFormat,
      log: Logger,
      out: PrintStream = System.out,
      operation: Option[String] = None
  ): IO[ExitCode] =
    Check.readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitCodes.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(err) =>
                IO.delay(log.error(Check.renderBuildError(specFile, err))).as(ExitCodes.Violations)
              case Right(ir) =>
                renderIR(ir, format, operation) match
                  case Right(text) => IO.blocking(out.println(text)).as(ExitCodes.Ok)
                  case Left(msg) =>
                    IO.delay(log.error(s"$specFile: $msg")).as(ExitCodes.Translator)

  private def renderIR(
      ir: ServiceIRFull,
      format: InspectFormat,
      operation: Option[String]
  ): Either[String, String] =
    val classifications = Classify.classifyOperations(ir)
    format match
      case InspectFormat.Json =>
        val printer = Printer.spaces2.copy(dropNullValues = false)
        val irJson  = (ir: service_ir_full).asJson
        val strategy = Json.obj(
          classifications.map(c =>
            c.operationName -> Json.fromString(SynthesisStrategy.label(c.strategy))
          )*
        )
        val combined = irJson.deepMerge(Json.obj("synthesis_strategy" -> strategy))
        Right(printer.print(combined))
      case InspectFormat.Summary =>
        val (direct, llm) = strategyTally(classifications)
        val opsLine =
          s"  ${ir.c.length} entities, ${ir.d.length} enums, ${ir.g.length} operations " +
            s"($direct DIRECT_EMIT, $llm LLM_SYNTHESIS), ${ir.i.length} invariants"
        val perOp = classifications.map: c =>
          s"    ${c.operationName}: ${SynthesisStrategy.label(c.strategy)}"
        Right((s"Service: ${ir.a}" :: opsLine :: perOp).mkString("\n"))
      case InspectFormat.Ir =>
        Right(ir.toString)
      case InspectFormat.Dafny =>
        DafnyGenerator
          .generate(ir)
          .map(_.text.stripSuffix("\n"))
          .left
          .map { err =>
            val loc = err.span.fold("") {
              case SpanT(int_of_integer(line), int_of_integer(col), _, _) =>
                s" at ${line.toLong}:${col.toLong}"
            }
            s"Dafny generation failed$loc: ${err.message}"
          }
      case InspectFormat.DafnyPrompt =>
        renderDafnyPrompt(ir, classifications, operation)

  private def strategyTally(classifications: List[OperationClassification]): (Int, Int) =
    val d = classifications.count(_.strategy == SynthesisStrategy.DirectEmit)
    val l = classifications.count(_.strategy == SynthesisStrategy.LlmSynthesis)
    (d, l)

  private def renderDafnyPrompt(
      ir: ServiceIRFull,
      classifications: List[OperationClassification],
      operation: Option[String]
  ): Either[String, String] =
    DafnyGenerator.generate(ir).left.map { err =>
      val loc = err.span.fold("") {
        case SpanT(int_of_integer(line), int_of_integer(col), _, _) =>
          s" at ${line.toLong}:${col.toLong}"
      }
      s"Dafny generation failed$loc: ${err.message}"
    }.flatMap { dafny =>
      val byName: Map[String, DafnyMethodHeader] = dafny.methods.map(h => h.name -> h).toMap
      val targets = operation match
        case Some(name) =>
          classifications
            .filter(_.operationName == name)
            .map(c => (c, byName.get(c.operationName)))
        case None =>
          classifications
            .filter(_.strategy == SynthesisStrategy.LlmSynthesis)
            .map(c => (c, byName.get(c.operationName)))
      val rendered = targets.flatMap:
        case (c, Some(header)) =>
          val prompt = PromptBuilder.initial(c, header, dafny.text)
          List(formatPrompt(c.operationName, prompt))
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
