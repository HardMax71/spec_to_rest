package specrest.cli

import cats.effect.unsafe.implicits.global
import specrest.convention.DiagnosticLevel as ConvDiagLevel
import specrest.convention.Validate
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

object Check:

  def run(specFile: String, log: Logger): Int =
    readSource(specFile, log) match
      case Left(code) => code
      case Right(source) =>
        val t0      = System.nanoTime()
        val parsedE = Parse.parseSpec(source).unsafeRunSync()
        val parseMs = (System.nanoTime() - t0) / 1_000_000.0
        log.verbose(f"Parsed in ${parseMs}%.0fms")

        parsedE match
          case Left(VerifyError.Parse(errors)) =>
            errors.foreach: e =>
              log.error(s"$specFile:${e.line}:${e.column}: ${e.message}")
            1
          case Right(parsed) =>
            val t1 = System.nanoTime()
            Builder.buildIR(parsed.tree).unsafeRunSync() match
              case Left(err) =>
                log.error(renderBuildError(specFile, err))
                1
              case Right(ir) =>
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

                if errors.nonEmpty then 1
                else
                  log.success(
                    s"$specFile: valid (${ir.operations.length} operations, ${ir.entities.length} entities, ${ir.invariants.length} invariants)"
                  )
                  0

  private[cli] def readSource(specFile: String, log: Logger): Either[Int, String] =
    try Right(Files.readString(Paths.get(specFile)))
    catch
      case _: NoSuchFileException =>
        log.error(s"File not found: $specFile")
        Left(1)
      case e: java.nio.file.FileSystemException =>
        log.error(s"Cannot read $specFile: ${e.getMessage}")
        Left(1)
      case e: RuntimeException =>
        log.error(s"Cannot read $specFile: ${e.getMessage}")
        Left(1)

  private[cli] def renderBuildError(specFile: String, e: VerifyError.Build): String =
    e.span match
      case Some(s) => s"$specFile:${s.startLine}:${s.startCol}: Build error: ${e.message}"
      case None    => s"$specFile: Build error: ${e.message}"
