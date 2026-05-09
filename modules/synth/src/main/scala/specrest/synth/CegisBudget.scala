package specrest.synth

final case class CegisBudget(
    maxIterations: Int = 8,
    maxInputTokens: Long = 100_000L,
    maxOutputTokens: Long = 50_000L,
    maxCostUsd: Double = 1.00,
    repeatedErrorThreshold: Int = 3
) derives CanEqual

object CegisBudget:
  val Default: CegisBudget = CegisBudget()

enum BudgetKind derives CanEqual:
  case MaxIterations, InputTokens, OutputTokens, Cost

sealed trait AbortReason derives CanEqual:
  def message: String

object AbortReason:

  final case class BudgetExhausted(kind: BudgetKind) extends AbortReason:
    def message: String = s"budget exhausted: $kind"

  final case class StuckOnSameError(error: VerifierError, repeats: Int) extends AbortReason:
    def message: String =
      val where = error.line.fold("unknown line")(l => s"line $l")
      s"verifier reported the same ${error.category} error ${repeats}× in a row at $where"

  final case class ProviderFailed(error: ProviderError, atIteration: Int) extends AbortReason:
    def message: String = s"provider error at iteration $atIteration: ${error.message}"

  final case class ResponseUnparseable(error: ParseError, atIteration: Int) extends AbortReason:
    def message: String = s"unparseable LLM response at iteration $atIteration: ${error.message}"

  final case class DiffViolation(error: specrest.synth.DiffViolation, atIteration: Int)
      extends AbortReason:
    def message: String = s"diff-check rejected at iteration $atIteration: ${error.message}"

  final case class VerifierBackendFailure(message0: String, atIteration: Int) extends AbortReason:
    def message: String = s"dafny backend failure at iteration $atIteration: $message0"

  final case class SpliceFailed(message0: String, atIteration: Int) extends AbortReason:
    def message: String = s"failed to splice body at iteration $atIteration: $message0"
