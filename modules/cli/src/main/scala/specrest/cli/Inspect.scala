package specrest.cli

import io.circe.Printer
import io.circe.syntax.EncoderOps
import specrest.ir.Serialize
import specrest.ir.Serialize.given
import specrest.parser.BuildError
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
        val parsed = Parse.parseSpec(source)
        if parsed.errors.nonEmpty then
          parsed.errors.foreach: e =>
            log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
          1
        else
          try
            val ir     = Builder.buildIR(parsed.tree)
            val output = formatIR(ir, format)
            println(output)
            0
          catch
            case e: BuildError =>
              log.error(s"$specFile: ${e.getMessage}")
              1
            case e: RuntimeException =>
              log.error(s"$specFile: ${e.getMessage}")
              1

  private def formatIR(ir: specrest.ir.ServiceIR, format: InspectFormat): String = format match
    case InspectFormat.Json =>
      val printer = Printer.spaces2.copy(dropNullValues = false)
      printer.print(ir.asJson)
    case InspectFormat.Summary =>
      s"Service: ${ir.name}\n" +
        s"  ${ir.entities.length} entities, ${ir.enums.length} enums, ${ir.operations.length} operations, ${ir.invariants.length} invariants"
    case InspectFormat.Ir =>
      ir.toString
