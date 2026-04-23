package specrest.cli

import cats.effect.unsafe.implicits.global
import specrest.codegen.Emit
import specrest.ir.VerifyError
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.profile.Annotate

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import scala.util.control.NonFatal

final case class CompileOptions(
    target: String,
    outDir: String
)

object Compile:

  def run(specFile: String, opts: CompileOptions, log: Logger): Int =
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
                try
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
                  0
                catch
                  case NonFatal(e) =>
                    log.error(s"$specFile: ${Option(e.getMessage).getOrElse(e.toString)}")
                    1
