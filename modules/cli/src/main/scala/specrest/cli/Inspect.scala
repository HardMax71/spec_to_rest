package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import io.circe.Printer
import io.circe.syntax.EncoderOps
import specrest.ir.Serialize
import specrest.ir.Serialize.given
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse

enum InspectFormat:
  case Summary, Json, Ir

object InspectFormat:
  def parse(s: String): Either[String, InspectFormat] = s match
    case "summary" => Right(Summary)
    case "json"    => Right(Json)
    case "ir"      => Right(Ir)
    case other     => Left(s"unknown format '$other'; choices: summary, json, ir")

object Inspect:

  def run(specFile: String, format: InspectFormat, log: Logger): IO[ExitCode] =
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
                IO.blocking(System.out.println(formatIR(ir, format))).as(ExitCodes.Ok)

  private def formatIR(ir: specrest.ir.ServiceIR, format: InspectFormat): String = format match
    case InspectFormat.Json =>
      val printer = Printer.spaces2.copy(dropNullValues = false)
      printer.print(ir.asJson)
    case InspectFormat.Summary =>
      s"Service: ${ir.name}\n" +
        s"  ${ir.entities.length} entities, ${ir.enums.length} enums, ${ir.operations.length} operations, ${ir.invariants.length} invariants"
    case InspectFormat.Ir =>
      ir.toString
