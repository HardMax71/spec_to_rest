package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import specrest.codegen.Emit
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate
import specrest.verify.VerificationConfig

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import scala.util.control.NonFatal

final case class CompileOptions(
    target: String,
    outDir: String,
    ignoreVerify: Boolean = false
)

object Compile:

  def run(specFile: String, opts: CompileOptions, log: Logger): IO[ExitCode] =
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
                val gate =
                  if opts.ignoreVerify then
                    IO.delay(log.warn("proceeding without verification (--ignore-verify)"))
                      .as(ExitCodes.Ok)
                  else Verify.runGate(specFile, ir, VerificationConfig.Default, log)
                gate.flatMap:
                  case ok if ok == ExitCodes.Ok =>
                    IO.blocking {
                      val profiled = Annotate.buildProfiledService(ir, opts.target)
                      val files    = Emit.emitProject(profiled)
                      val outRoot  = Paths.get(opts.outDir)
                      Files.createDirectories(outRoot)
                      files.foreach: f =>
                        val target = outRoot.resolve(f.path)
                        Option(target.getParent).foreach(Files.createDirectories(_))
                        Files.writeString(
                          target,
                          f.content,
                          StandardOpenOption.CREATE,
                          StandardOpenOption.TRUNCATE_EXISTING
                        )
                      log.success(s"wrote ${files.length} files to ${opts.outDir}")
                      ExitCodes.Ok
                    }.handleErrorWith:
                      case NonFatal(e) =>
                        IO.delay(
                          log.error(s"$specFile: ${Option(e.getMessage).getOrElse(e.toString)}")
                        ).as(ExitCodes.Violations)
                      case e => IO.raiseError(e)
                  case gateCode => IO.pure(gateCode)
