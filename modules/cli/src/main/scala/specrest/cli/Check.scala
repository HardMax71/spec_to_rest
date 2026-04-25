package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import specrest.convention.ConventionDiagnostic
import specrest.convention.DiagnosticLevel as ConvDiagLevel
import specrest.convention.Validate
import specrest.ir.VerifyError
import specrest.lint.Lint
import specrest.lint.LintDiagnostic
import specrest.lint.LintLevel
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

                    val convDiags = Validate.validateConventions(ir.conventions, ir)
                    val lintDiags = Lint.run(ir)

                    val convErrors   = convDiags.filter(_.level == ConvDiagLevel.Error)
                    val convWarnings = convDiags.filter(_.level == ConvDiagLevel.Warning)
                    val lintErrors   = lintDiags.filter(_.level == LintLevel.Error)
                    val lintWarnings = lintDiags.filter(_.level == LintLevel.Warning)

                    convWarnings.foreach(d => log.warn(renderConv(specFile, d)))
                    lintWarnings.foreach(d => log.warn(renderLint(specFile, d)))
                    convErrors.foreach(d => log.error(renderConv(specFile, d)))
                    lintErrors.foreach(d => log.error(renderLint(specFile, d)))

                    if convErrors.nonEmpty || lintErrors.nonEmpty then ExitCodes.Violations
                    else
                      log.success(
                        s"$specFile: valid (${ir.operations.length} operations, ${ir.entities.length} entities, ${ir.invariants.length} invariants)"
                      )
                      ExitCodes.Ok
                  }
          )
        }

  private def renderConv(specFile: String, d: ConventionDiagnostic): String =
    val loc = d.span.map(s => s"$specFile:${s.startLine}:${s.startCol}: ").getOrElse("")
    d.level match
      case ConvDiagLevel.Warning => s"${loc}warning: ${d.message}"
      case ConvDiagLevel.Error   => s"${loc}${d.message}"

  private def renderLint(specFile: String, d: LintDiagnostic): String =
    val loc = d.span.map(s => s"$specFile:${s.startLine}:${s.startCol}: ").getOrElse("")
    d.level match
      case LintLevel.Warning => s"${loc}warning: ${d.message} [${d.code}]"
      case LintLevel.Error   => s"${loc}${d.message} [${d.code}]"

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
