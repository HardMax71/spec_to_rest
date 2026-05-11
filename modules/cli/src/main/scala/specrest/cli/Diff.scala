package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import specrest.cli.ExitCodes.given
import specrest.codegen.Emit
import specrest.codegen.EmitOptions
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate
import specrest.testgen.TestEmit
import specrest.verify.VerificationConfig

import java.nio.file.Files
import java.nio.file.Paths

final case class DiffOptions(
    target: String,
    outDir: String,
    ignoreVerify: Boolean = false,
    withTests: Boolean = false
)

object Diff:

  def run(specFile: String, opts: DiffOptions, log: Logger): IO[ExitCode] =
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
                IO.delay(log.error(Check.renderBuildError(specFile, err)))
                  .as(ExitCodes.Violations)
              case Right(ir) =>
                val gate =
                  if opts.ignoreVerify then IO.pure(ExitCodes.Ok)
                  else Verify.runGate(specFile, ir, VerificationConfig.Default, log)
                gate.flatMap:
                  case ok if ok == ExitCodes.Ok =>
                    IO.blocking {
                      val profiled  = Annotate.buildProfiledService(ir, opts.target)
                      val baseFiles = Emit.emitProject(profiled, EmitOptions())
                      val testFiles = if opts.withTests then TestEmit.emit(profiled) else Nil
                      val files     = baseFiles ++ testFiles
                      val outRoot   = Paths.get(opts.outDir)
                      val plans = if Files.isDirectory(outRoot) then Plan.classify(files, outRoot)
                      else files.map(f => FilePlan(FileAction.Create, f.path))
                      val changes = plans.filter: p =>
                        p.action == FileAction.Create || p.action == FileAction.Update
                      if changes.isEmpty then
                        log.success(s"no drift: ${plans.length} files match ${opts.outDir}")
                        ExitCodes.Ok
                      else
                        System.out.println(Plan.render(changes, log.palette))
                        val t = Plan.tally(plans)
                        log.warn(
                          s"${changes.length} file(s) would change " +
                            s"(create=${t.create} update=${t.update}; ${t.unchanged} unchanged)"
                        )
                        ExitCodes.Violations
                    }
                  case gateCode => IO.pure(gateCode)
