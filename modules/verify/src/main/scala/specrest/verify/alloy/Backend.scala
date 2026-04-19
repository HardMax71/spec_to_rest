package specrest.verify.alloy

import edu.mit.csail.sdg.alloy4.A4Reporter
import edu.mit.csail.sdg.alloy4.Err
import edu.mit.csail.sdg.parser.CompUtil
import edu.mit.csail.sdg.translator.A4Options
import edu.mit.csail.sdg.translator.A4Solution
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod
import kodkod.engine.satlab.SATFactory
import specrest.verify.CheckStatus

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

final case class AlloyCheckResult(
    status: CheckStatus,
    durationMs: Double,
    solution: Option[A4Solution],
    commandName: String,
    source: String
)

final class AlloyBackend:

  def check(source: String, commandIdx: Int, timeoutMs: Long): AlloyCheckResult =
    val reporter = A4Reporter.NOP
    val module =
      try CompUtil.parseEverything_fromString(reporter, source)
      catch
        case e: Err =>
          throw new AlloyTranslatorError(s"Alloy parse error: ${e.getMessage}\n---\n$source")
    val commands = module.getAllCommands
    if commands.isEmpty then
      throw new AlloyTranslatorError("Alloy module has no commands; need at least one run/check")
    if commandIdx < 0 || commandIdx >= commands.size then
      throw new AlloyTranslatorError(
        s"command index $commandIdx out of range [0, ${commands.size})"
      )
    val cmd  = commands.get(commandIdx)
    val opts = new A4Options()
    opts.solver = SATFactory.DEFAULT
    opts.skolemDepth = 4
    val t0 = System.nanoTime()
    val solveTask = new Callable[A4Solution]:
      def call(): A4Solution =
        TranslateAlloyToKodkod.execute_command(reporter, module.getAllReachableSigs, cmd, opts)
    val solutionOpt: Option[A4Solution] =
      if timeoutMs <= 0 then
        try Some(solveTask.call())
        catch
          case e: Err =>
            throw new AlloyTranslatorError(s"Alloy translate/solve error: ${e.getMessage}")
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
                throw new AlloyTranslatorError(s"Alloy translate/solve error: ${err.getMessage}")
              case other => throw other
        finally
          val _ = pool.shutdownNow()
    val duration = (System.nanoTime() - t0) / 1_000_000.0
    solutionOpt match
      case None =>
        AlloyCheckResult(
          status = CheckStatus.Unknown,
          durationMs = duration,
          solution = None,
          commandName = cmd.label,
          source = source
        )
      case Some(solution) =>
        val status =
          if solution.satisfiable then CheckStatus.Sat
          else CheckStatus.Unsat
        AlloyCheckResult(
          status = status,
          durationMs = duration,
          solution = if solution.satisfiable then Some(solution) else None,
          commandName = cmd.label,
          source = source
        )
