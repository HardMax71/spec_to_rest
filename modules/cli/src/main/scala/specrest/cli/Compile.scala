package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import specrest.codegen.Emit
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate
import specrest.testgen.FilePaths
import specrest.testgen.Strategies
import specrest.testgen.SupportedTargets
import specrest.testgen.TestEmit
import specrest.verify.VerificationConfig

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import scala.util.control.NonFatal

final case class CompileOptions(
    target: String,
    outDir: String,
    ignoreVerify: Boolean = false,
    withTests: Boolean = false,
    strictStrategies: Boolean = false
)

object Compile:

  def run(specFile: String, opts: CompileOptions, log: Logger): IO[ExitCode] =
    if opts.withTests && !SupportedTargets.All.contains(opts.target) then
      IO.delay(
        log.error(
          s"--with-tests currently supports only ${SupportedTargets.All.mkString(", ")} " +
            s"(got --target=${opts.target})"
        )
      ).as(ExitCodes.Violations)
    else
      val warnIfStrictWithoutTests =
        if opts.strictStrategies && !opts.withTests then
          IO.delay(
            log.warn(
              "--strict-strategies has no effect without --with-tests; ignoring"
            )
          )
        else IO.unit
      warnIfStrictWithoutTests *> runImpl(specFile, opts, log)

  private def runImpl(specFile: String, opts: CompileOptions, log: Logger): IO[ExitCode] =
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
                    val strictGate =
                      if opts.withTests && opts.strictStrategies then
                        val unhandled = Strategies.forIR(ir).filter(_.skipped.nonEmpty)
                        if unhandled.isEmpty then IO.pure(ExitCodes.Ok)
                        else
                          IO.delay {
                            log.error(
                              "--strict-strategies: type aliases with unhandled `where` constraints (no convention override registered):"
                            )
                            unhandled.foreach: s =>
                              log.error(s"  ${s.typeName}: ${s.skipped.mkString("; ")}")
                          }.as(ExitCodes.Violations)
                      else IO.pure(ExitCodes.Ok)

                    strictGate.flatMap:
                      case strictOk if strictOk == ExitCodes.Ok =>
                        IO.blocking {
                          val profiled  = Annotate.buildProfiledService(ir, opts.target)
                          val baseFiles = Emit.emitProject(profiled)
                          val testFiles = if opts.withTests then TestEmit.emit(profiled) else Nil
                          val files     = baseFiles ++ testFiles
                          val outRoot   = Paths.get(opts.outDir)
                          Files.createDirectories(outRoot)
                          files.foreach: f =>
                            val target = outRoot.resolve(f.path)
                            Option(target.getParent).foreach(Files.createDirectories(_))
                            val isUserStrategies = f.path == FilePaths.StrategiesUserFile
                            if isUserStrategies && Files.exists(target) then ()
                            else
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
                      case strictCode => IO.pure(strictCode)
                  case gateCode => IO.pure(gateCode)
