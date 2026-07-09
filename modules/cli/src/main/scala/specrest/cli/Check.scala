package specrest.cli

import cats.effect.IO
import specrest.convention.ConventionDiagnostic
import specrest.convention.DiagnosticLevel as ConvDiagLevel
import specrest.convention.Validate
import specrest.ir.VerifyError
import specrest.ir.generated.SpecRestGenerated.*
import specrest.lint.Lint
import specrest.lint.LintDiagnostic
import specrest.lint.LintLevel
import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

object Check:

  def run(specFile: String, log: Logger): IO[ExitStatus] =
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
              }.as(ExitStatus.Violations)
            case Right(parsed) =>
              val t1 = System.nanoTime()
              Builder.buildIR(parsed.tree).flatMap:
                case Left(err) =>
                  IO.delay(log.error(renderBuildError(specFile, err))).as(ExitStatus.Violations)
                case Right(ir) =>
                  IO.delay {
                    val buildMs = (System.nanoTime() - t1) / 1_000_000.0
                    log.verbose(f"Built IR in ${buildMs}%.0fms")

                    val convDiags =
                      Validate.validateConventions(svcConventions(ir), ir) ++
                        Validate.validateRoutes(ir) ++
                        Validate.validateSecurity(ir)
                    val lintDiags = Lint.run(ir)

                    val convErrors   = convDiags.filter(_.level == ConvDiagLevel.Error)
                    val convWarnings = convDiags.filter(_.level == ConvDiagLevel.Warning)
                    val lintErrors   = lintDiags.filter(_.level == LintLevel.Error)
                    val lintWarnings = lintDiags.filter(_.level == LintLevel.Warning)

                    convWarnings.foreach(d => log.warn(renderConv(specFile, d)))
                    lintWarnings.foreach(d => log.warn(renderLint(specFile, d)))
                    convErrors.foreach(d => log.error(renderConv(specFile, d)))
                    lintErrors.foreach(d => log.error(renderLint(specFile, d)))

                    if convErrors.nonEmpty || lintErrors.nonEmpty then ExitStatus.Violations
                    else
                      log.success(
                        s"$specFile: valid (${svcOperations(ir).length} operations, ${svcEntities(ir).length} entities, ${svcInvariants(ir).length} invariants)"
                      )
                      ExitStatus.Ok
                  }
          )
        }

  private[cli] def withParsedIR(specFile: String, log: Logger)(
      k: ServiceIRFull => IO[ExitStatus]
  ): IO[ExitStatus] =
    readSource(specFile, log).flatMap:
      case Left(code) => IO.pure(code)
      case Right(source) =>
        Parse.parseSpec(source).flatMap:
          case Left(VerifyError.Parse(errors)) =>
            IO.delay {
              errors.foreach: e =>
                log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            }.as(ExitStatus.Violations)
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(err) =>
                IO.delay(log.error(renderBuildError(specFile, err))).as(ExitStatus.Violations)
              case Right(ir) => k(ir)

  private[cli] def testDowngradeNotice(target: String, downgrade: Boolean, log: Logger): IO[Unit] =
    if downgrade then
      IO.delay(
        log.warn(
          s"target $target does not support native test generation; skipping " +
            "(pass --no-tests to silence this warning)"
        )
      )
    else IO.unit

  private[cli] def renderConv(specFile: String, d: ConventionDiagnostic): String =
    val loc =
      d.span.collect { case SpanT(line, col, _, _) =>
        s"$specFile:$line:$col: "
      }.getOrElse("")
    d.level match
      case ConvDiagLevel.Warning => s"${loc}warning: ${d.message}"
      case ConvDiagLevel.Error   => s"${loc}${d.message}"

  private def renderLint(specFile: String, d: LintDiagnostic): String =
    val loc =
      d.span.collect { case SpanT(line, col, _, _) =>
        s"$specFile:$line:$col: "
      }.getOrElse("")
    d.level match
      case LintLevel.Warning => s"${loc}warning: ${d.message} [${d.code}]"
      case LintLevel.Error   => s"${loc}${d.message} [${d.code}]"

  private[cli] def readSource(specFile: String, log: Logger): IO[Either[ExitStatus, String]] =
    IO.blocking(Files.readString(Paths.get(specFile)))
      .map(src => Right(src): Either[ExitStatus, String])
      .handleErrorWith:
        case _: NoSuchFileException =>
          IO.delay(log.error(s"File not found: $specFile"))
            .as(Left(ExitStatus.Violations))
        case e: java.nio.file.FileSystemException =>
          IO.delay(log.error(s"Cannot read $specFile: ${e.getMessage}"))
            .as(Left(ExitStatus.Violations))
        case e: RuntimeException =>
          IO.delay(log.error(s"Cannot read $specFile: ${e.getMessage}"))
            .as(Left(ExitStatus.Violations))

  private[cli] def renderBuildError(specFile: String, e: VerifyError.Build): String =
    e.span match
      case Some(SpanT(line, col, _, _)) =>
        s"$specFile:$line:$col: Build error: ${e.message}"
      case _ => s"$specFile: Build error: ${e.message}"
