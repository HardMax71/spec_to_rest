package specrest.verify.alloy

import edu.mit.csail.sdg.alloy4.A4Reporter
import edu.mit.csail.sdg.alloy4.Err
import edu.mit.csail.sdg.parser.CompUtil
import edu.mit.csail.sdg.translator.A4Options
import edu.mit.csail.sdg.translator.A4Solution
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod
import kodkod.engine.satlab.SATFactory
import specrest.verify.CheckStatus

final case class AlloyCheckResult(
    status: CheckStatus,
    durationMs: Double,
    solution: Option[A4Solution],
    commandName: String,
    scope: Int,
    source: String
)

final class AlloyBackend:

  def check(source: String, commandIdx: Int, scope: Int): AlloyCheckResult =
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
    val solution =
      try TranslateAlloyToKodkod.execute_command(reporter, module.getAllReachableSigs, cmd, opts)
      catch
        case e: Err =>
          throw new AlloyTranslatorError(s"Alloy translate/solve error: ${e.getMessage}")
    val duration = (System.nanoTime() - t0) / 1_000_000.0
    val status =
      if solution.satisfiable then CheckStatus.Sat
      else CheckStatus.Unsat
    AlloyCheckResult(
      status = status,
      durationMs = duration,
      solution = if solution.satisfiable then Some(solution) else None,
      commandName = cmd.label,
      scope = scope,
      source = source
    )
