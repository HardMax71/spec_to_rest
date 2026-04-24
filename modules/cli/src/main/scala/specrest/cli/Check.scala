package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import specrest.convention.DiagnosticLevel as ConvDiagLevel
import specrest.convention.Validate
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

object Check:

  def run(specFile: String, log: Logger): IO[ExitCode] =
    readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        val t0 = System.nanoTime()
        Parse.parseSpec(source).flatMap { parsedE =>
          val parseMs = (System.nanoTime() - t0) / 1_000_000.0
          IO.delay(log.verbose(f"Parsed in ${parseMs}%.0fms")) >> (parsedE match
            case Left(VerifyError.Parse(errors)) =>
              IO.delay {
                errors.foreach: e =>
                  log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
              }.as(ExitCodes.Violations)
            case Right(parsed) =>
              val t1 = System.nanoTime()
              Builder.buildIR(parsed.tree).flatMap:
                case Left(err) =>
                  IO.delay(log.error(renderBuildError(specFile, err))).as(ExitCodes.Violations)
                case Right(ir) =>
                  IO.delay {
                    val buildMs = (System.nanoTime() - t1) / 1_000_000.0
                    log.verbose(f"Built IR in ${buildMs}%.0fms")

                    val diagnostics = Validate.validateConventions(ir.conventions, ir)
                    val errors      = diagnostics.filter(_.level == ConvDiagLevel.Error)
                    val warnings    = diagnostics.filter(_.level == ConvDiagLevel.Warning)

                    for w <- warnings do
                      val loc =
                        w.span.map(s => s"$specFile:${s.startLine}:${s.startCol}: ").getOrElse("")
                      log.warn(s"${loc}warning: ${w.message}")
                    for e <- errors do
                      val loc =
                        e.span.map(s => s"$specFile:${s.startLine}:${s.startCol}: ").getOrElse("")
                      log.error(s"${loc}${e.message}")

                    if errors.nonEmpty then ExitCodes.Violations
                    else
                      log.success(
                        s"$specFile: valid (${ir.operations.length} operations, ${ir.entities.length} entities, ${ir.invariants.length} invariants)"
                      )
                      ExitCodes.Ok
                  }
          )
        }

  private[cli] def readSource(specFile: String, log: Logger): IO[Either[ExitCode, String]] =
    IO.blocking(Files.readString(Paths.get(specFile)))
      .map(src => Right(src): Either[ExitCode, String])
      .handleErrorWith:
        case _: NoSuchFileException =>
          IO.delay(log.error(s"File not found: $specFile"))
            .as(Left(ExitCodes.Violations))
        case e: java.nio.file.FileSystemException =>
          IO.delay(log.error(s"Cannot read $specFile: ${e.getMessage}"))
            .as(Left(ExitCodes.Violations))
        case e: RuntimeException =>
          IO.delay(log.error(s"Cannot read $specFile: ${e.getMessage}"))
            .as(Left(ExitCodes.Violations))

  private[cli] def renderBuildError(specFile: String, e: VerifyError.Build): String =
    e.span match
      case Some(s) => s"$specFile:${s.startLine}:${s.startCol}: Build error: ${e.message}"
      case None    => s"$specFile: Build error: ${e.message}"
