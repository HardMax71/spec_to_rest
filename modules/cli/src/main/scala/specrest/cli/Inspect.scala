package specrest.cli

import cats.effect.unsafe.implicits.global
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

  def run(specFile: String, format: InspectFormat, log: Logger): Int =
    Check.readSource(specFile, log) match
      case Left(code) => code
      case Right(source) =>
        Parse.parseSpec(source).unsafeRunSync() match
          case Left(VerifyError.Parse(errors)) =>
            errors.foreach: e =>
              log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            1
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).unsafeRunSync() match
              case Left(err) =>
                log.error(Check.renderBuildError(specFile, err))
                1
              case Right(ir) =>
                println(formatIR(ir, format))
                0

  private def formatIR(ir: specrest.ir.ServiceIR, format: InspectFormat): String = format match
    case InspectFormat.Json =>
      val printer = Printer.spaces2.copy(dropNullValues = false)
      printer.print(ir.asJson)
    case InspectFormat.Summary =>
      s"Service: ${ir.name}\n" +
        s"  ${ir.entities.length} entities, ${ir.enums.length} enums, ${ir.operations.length} operations, ${ir.invariants.length} invariants"
    case InspectFormat.Ir =>
      ir.toString
