package specrest.cli

import cats.effect.IO
import specrest.cli.ExitStatus.given
import specrest.codegen.Emit
import specrest.codegen.EmitOptions
import specrest.codegen.migration.Revision
import specrest.profile.Annotate
import specrest.testgen.SupportedTargets
import specrest.testgen.TestEmit
import specrest.verify.VerificationConfig

import java.nio.file.Files
import java.nio.file.Paths
import scala.util.control.NonFatal

final case class DiffOptions(
    target: String,
    outDir: String,
    ignoreVerify: Boolean = false,
    withTests: Boolean = true
)

object Diff:

  def run(specFile: String, opts: DiffOptions, log: Logger): IO[ExitStatus] =
    val downgrade       = opts.withTests && !SupportedTargets.supports(opts.target)
    val resolvedOpts    = if downgrade then opts.copy(withTests = false) else opts
    val downgradeNotice = Check.testDowngradeNotice(opts.target, downgrade, log)
    downgradeNotice *> runImpl(specFile, resolvedOpts, log)

  private def runImpl(specFile: String, opts: DiffOptions, log: Logger): IO[ExitStatus] =
    Check.withParsedIR(specFile, log): ir =>
      val gate =
        if opts.ignoreVerify then IO.pure(ExitStatus.Ok)
        else Verify.runGate(specFile, ir, VerificationConfig.Default, log)
      gate.flatMap:
        case ok if ok == ExitStatus.Ok =>
          IO.blocking {
            val profiled = Annotate.buildProfiledService(ir, opts.target)
            val outRoot  = Paths.get(opts.outDir)
            val emitOpts = EmitOptions(
              previousSnapshot = SnapshotIO.readSnapshot(outRoot, log),
              existingRevisions = Revision.discover(outRoot, opts.target)
            )
            val baseFiles = Emit.emitProject(profiled, emitOpts)
            val testFiles = if opts.withTests then TestEmit.emit(profiled) else Nil
            val files     = baseFiles ++ testFiles
            val plans = if Files.isDirectory(outRoot) then Plan.classify(files, outRoot)
            else files.map(f => FilePlan(FileAction.Create, f.path))
            val changes = plans.filter: p =>
              p.action == FileAction.Create || p.action == FileAction.Update
            if changes.isEmpty then
              log.success(s"no drift: ${plans.length} files match ${opts.outDir}")
              ExitStatus.Ok
            else
              log.data(Plan.render(changes, log.palette))
              val t = Plan.tally(plans)
              log.warn(
                s"${changes.length} file(s) would change " +
                  s"(create=${t.create} update=${t.update}; ${t.unchanged} unchanged)"
              )
              ExitStatus.Violations
          }.handleErrorWith:
            case NonFatal(e) =>
              IO.delay(
                log.error(s"$specFile: ${Option(e.getMessage).getOrElse(e.toString)}")
              ).as(ExitStatus.Violations)
            case e => IO.raiseError(e)
        case gateCode => IO.pure(gateCode)
