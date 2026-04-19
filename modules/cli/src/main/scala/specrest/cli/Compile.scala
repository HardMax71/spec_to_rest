package specrest.cli

import java.nio.file.{Files, Paths, StandardOpenOption}
import specrest.codegen.Emit
import specrest.parser.{BuildError, Builder, Parse}
import specrest.profile.Annotate

final case class CompileOptions(
    target: String,
    outDir: String,
)

object Compile:

  def run(specFile: String, opts: CompileOptions, log: Logger): Int =
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
            val ir       = Builder.buildIR(parsed.tree)
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
                StandardOpenOption.TRUNCATE_EXISTING,
              )
            log.success(s"wrote ${files.length} files to ${opts.outDir}")
            0
          catch
            case e: BuildError =>
              log.error(s"$specFile: ${e.getMessage}")
              1
            case e: RuntimeException =>
              log.error(s"$specFile: ${e.getMessage}")
              1
