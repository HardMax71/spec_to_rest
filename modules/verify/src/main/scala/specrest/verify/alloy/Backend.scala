package specrest.verify.alloy

import cats.effect.IO
import cats.effect.Resource
import edu.mit.csail.sdg.alloy4.A4Reporter
import edu.mit.csail.sdg.alloy4.Err
import edu.mit.csail.sdg.alloy4.Pos
import edu.mit.csail.sdg.parser.CompUtil
import edu.mit.csail.sdg.translator.A4Options
import edu.mit.csail.sdg.translator.A4Solution
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod
import kodkod.engine.satlab.SATFactory
import specrest.ir.VerifyError
import specrest.verify.CheckStatus

import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.RichOptional
import scala.util.boundary
import scala.util.control.NonFatal

private def alloyRenderStack(e: Throwable): String =
  val sw = new StringWriter
  e.printStackTrace(new PrintWriter(sw))
  sw.toString

final case class AlloyCheckResult(
    status: CheckStatus,
    durationMs: Double,
    solution: Option[A4Solution],
    commandName: String,
    source: String,
    corePositions: Set[Pos] = Set.empty
)

private type AlloyBackendLabel =
  boundary.Label[Either[VerifyError.Backend, AlloyCheckResult]]

private def alloyBackendFail(msg: String)(using AlloyBackendLabel): Nothing =
  boundary.break(Left(VerifyError.Backend(msg, None)))

object AlloyBackend:
  private lazy val coreCapableSolver: Option[SATFactory] =
    SATFactory.find("minisat.prover").toScala.filter(_.isPresent)

  def make: Resource[IO, AlloyBackend] =
    make(IO.blocking(new AlloyBackend)): backend =>
      IO.blocking(backend.close())

  private[verify] def make(
      acquire: IO[AlloyBackend]
  )(
      release: AlloyBackend => IO[Unit]
  ): Resource[IO, AlloyBackend] =
    Resource.make(acquire)(release)

final class AlloyBackend:

  def close(): Unit = ()

  def check(
      source: String,
      commandIdx: Int,
      timeoutMs: Long,
      captureCore: Boolean = false
  ): IO[Either[VerifyError.Backend, AlloyCheckResult]] =
    IO.blocking(checkSync(source, commandIdx, timeoutMs, captureCore))

  private[specrest] def checkSync(
      source: String,
      commandIdx: Int,
      timeoutMs: Long,
      captureCore: Boolean = false
  ): Either[VerifyError.Backend, AlloyCheckResult] =
    try
      boundary:
        val reporter = A4Reporter.NOP
        val module =
          try CompUtil.parseEverything_fromString(reporter, source)
          catch
            case e: Err =>
              alloyBackendFail(s"Alloy parse error: ${e.getMessage}\n---\n$source")
        val commands = module.getAllCommands
        if commands.isEmpty then
          alloyBackendFail("Alloy module has no commands; need at least one run/check")
        if commandIdx < 0 || commandIdx >= commands.size then
          alloyBackendFail(s"command index $commandIdx out of range [0, ${commands.size})")
        val cmd  = commands.get(commandIdx)
        val opts = new A4Options()
        val coreCapable =
          if captureCore then AlloyBackend.coreCapableSolver else None
        opts.solver = coreCapable.getOrElse(SATFactory.DEFAULT)
        opts.skolemDepth = 4
        if coreCapable.isDefined then
          opts.coreMinimization = 1
          opts.coreGranularity = 1
        val t0 = System.nanoTime()
        val solveTask = new Callable[A4Solution]:
          def call(): A4Solution =
            TranslateAlloyToKodkod.execute_command(reporter, module.getAllReachableSigs, cmd, opts)
        val solutionOpt: Option[A4Solution] =
          if timeoutMs <= 0 then
            try Some(solveTask.call())
            catch
              case e: Err =>
                alloyBackendFail(s"Alloy translate/solve error: ${e.getMessage}")
          else
            val pool   = Executors.newSingleThreadExecutor()
            val future = pool.submit(solveTask)
            try Some(future.get(timeoutMs, TimeUnit.MILLISECONDS))
            catch
              case _: TimeoutException =>
                val _ = future.cancel(true)
                None
              case e: java.util.concurrent.ExecutionException =>
                e.getCause match
                  case err: Err =>
                    alloyBackendFail(s"Alloy translate/solve error: ${err.getMessage}")
                  case other =>
                    alloyBackendFail(
                      s"Alloy execution error: ${Option(other).map(_.getMessage).getOrElse("null")}"
                    )
            finally
              val _ = pool.shutdownNow()
        val duration = (System.nanoTime() - t0) / 1_000_000.0
        solutionOpt match
          case None =>
            Right(AlloyCheckResult(
              status = CheckStatus.Unknown,
              durationMs = duration,
              solution = None,
              commandName = cmd.label,
              source = source
            ))
          case Some(solution) =>
            val status =
              if solution.satisfiable then CheckStatus.Sat
              else CheckStatus.Unsat
            val core: Set[Pos] =
              if !solution.satisfiable && captureCore && coreCapable.isDefined then
                val hl = solution.highLevelCore
                hl.a.asScala.toSet
              else Set.empty
            Right(AlloyCheckResult(
              status = status,
              durationMs = duration,
              solution = if solution.satisfiable then Some(solution) else None,
              commandName = cmd.label,
              source = source,
              corePositions = core
            ))
    catch
      case NonFatal(e) =>
        Left(VerifyError.Backend(
          Option(e.getMessage).getOrElse(e.toString),
          Some(alloyRenderStack(e))
        ))
