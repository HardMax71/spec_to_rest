package specrest.synth

enum Verdict derives CanEqual:
  case Verified, VerifiedEscalated, Skeleton

object Verdict:

  def fromOutcome(o: FallbackOutcome): Verdict = o match
    case v: FallbackOutcome.Verified =>
      if v.attempts.length == 1 then Verified else VerifiedEscalated
    case _: FallbackOutcome.SkeletonOnly => Skeleton

  def displayName(v: Verdict): String = v match
    case Verified          => "VERIFIED"
    case VerifiedEscalated => "VERIFIED-ESCALATED"
    case Skeleton          => "SKELETON"

final case class OpOutcome(
    operationName: String,
    verdict: Verdict,
    finalStrategy: PromptStrategy,
    finalModel: String,
    cegisIterations: Int,
    attempts: Int,
    totalCostUsd: Double,
    inputTokens: Long,
    outputTokens: Long,
    reason: Option[String]
) derives CanEqual

object OpOutcome:

  def fromFallback(operationName: String, o: FallbackOutcome): OpOutcome =
    val totalCost = o.attempts.map(_.costUsd).sum
    val totalIn   = o.attempts.map(_.inputTokens).sum
    val totalOut  = o.attempts.map(_.outputTokens).sum
    o match
      case v: FallbackOutcome.Verified =>
        OpOutcome(
          operationName = operationName,
          verdict = Verdict.fromOutcome(v),
          finalStrategy = v.finalStrategy,
          finalModel = v.finalModel,
          cegisIterations = v.cegisIterations,
          attempts = v.attempts.length,
          totalCostUsd = totalCost,
          inputTokens = totalIn,
          outputTokens = totalOut,
          reason = None
        )
      case s: FallbackOutcome.SkeletonOnly =>
        val (finalStrategy, finalModel) = s.attempts.lastOption match
          case Some(a) => (a.strategy, a.model)
          case None    => (PromptStrategy.ZeroShot, "n/a")
        OpOutcome(
          operationName = operationName,
          verdict = Verdict.Skeleton,
          finalStrategy = finalStrategy,
          finalModel = finalModel,
          cegisIterations = 0,
          attempts = s.attempts.length,
          totalCostUsd = totalCost,
          inputTokens = totalIn,
          outputTokens = totalOut,
          reason = Some(s.reason)
        )

final case class SynthesisReport(
    ops: List[OpOutcome]
) derives CanEqual:

  def totals: ReportTotals =
    ReportTotals(
      total = ops.length,
      verified = ops.count(_.verdict == Verdict.Verified),
      verifiedEscalated = ops.count(_.verdict == Verdict.VerifiedEscalated),
      skeleton = ops.count(_.verdict == Verdict.Skeleton),
      totalCostUsd = ops.map(_.totalCostUsd).sum,
      inputTokens = ops.map(_.inputTokens).sum,
      outputTokens = ops.map(_.outputTokens).sum
    )

final case class ReportTotals(
    total: Int,
    verified: Int,
    verifiedEscalated: Int,
    skeleton: Int,
    totalCostUsd: Double,
    inputTokens: Long,
    outputTokens: Long
) derives CanEqual

object Reporter:

  private val Reset  = "\u001b[0m"
  private val Green  = "\u001b[32m"
  private val Yellow = "\u001b[33m"
  private val Red    = "\u001b[31m"
  private val Bold   = "\u001b[1m"

  def render(report: SynthesisReport, useColor: Boolean): String =
    val rows  = report.ops.map(row(_, useColor))
    val total = report.totals
    val header =
      if useColor then s"${Bold}Synthesis Report${Reset}"
      else "Synthesis Report"
    val headerLine =
      "  Operation                  Verdict              Strategy          Model                   iter   $cost"
    val divider = "  " + ("-" * 100)
    val summary = renderTotals(total, useColor)
    (List(header, headerLine, divider) ++ rows ++ List(divider, summary)).mkString("\n")

  private def row(o: OpOutcome, useColor: Boolean): String =
    val verdict   = colored(Verdict.displayName(o.verdict), o.verdict, useColor)
    val strat     = PromptStrategy.displayName(o.finalStrategy)
    val iterCol   = f"${o.cegisIterations}%4d"
    val costCol   = f"$$${o.totalCostUsd}%6.4f"
    val opPad     = padRight(o.operationName, 26)
    val verdPad   = padRight(verdictText(o.verdict), 20)
    val verdShown = if useColor then padForColor(verdict, verdictText(o.verdict), 20) else verdPad
    val stratPad  = padRight(strat, 17)
    val modelPad  = padRight(o.finalModel, 23)
    s"  $opPad $verdShown $stratPad $modelPad $iterCol  $costCol"

  private def verdictText(v: Verdict): String = Verdict.displayName(v)

  private def colored(s: String, v: Verdict, useColor: Boolean): String =
    if !useColor then s
    else
      val color = v match
        case Verdict.Verified          => Green
        case Verdict.VerifiedEscalated => Yellow
        case Verdict.Skeleton          => Red
      s"$color$s$Reset"

  private def renderTotals(t: ReportTotals, useColor: Boolean): String =
    val parts = List(
      s"total=${t.total}",
      colored(s"verified=${t.verified}", Verdict.Verified, useColor),
      colored(s"escalated=${t.verifiedEscalated}", Verdict.VerifiedEscalated, useColor),
      colored(s"skeleton=${t.skeleton}", Verdict.Skeleton, useColor)
    )
    val cost = f"cost=$$${t.totalCostUsd}%.4f"
    val toks = s"in=${t.inputTokens}tok out=${t.outputTokens}tok"
    s"  ${parts.mkString(" ")}  $cost  $toks"

  private def padRight(s: String, n: Int): String =
    if s.length >= n then s.take(n)
    else s + (" " * (n - s.length))

  private def padForColor(coloredStr: String, plain: String, n: Int): String =
    val pad = if plain.length >= n then "" else " " * (n - plain.length)
    coloredStr + pad
