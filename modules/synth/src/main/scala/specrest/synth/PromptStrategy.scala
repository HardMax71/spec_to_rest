package specrest.synth

enum PromptStrategy derives CanEqual:
  case ZeroShot, ChainOfThought, PlanThenImplement

object PromptStrategy:

  val Default: PromptStrategy = ZeroShot

  val FallbackOrder: List[PromptStrategy] =
    List(ZeroShot, ChainOfThought, PlanThenImplement)

  def fromName(name: String): Option[PromptStrategy] = name.toLowerCase match
    case "zero-shot" | "zeroshot"                 => Some(ZeroShot)
    case "cot" | "chain-of-thought" | "chain"     => Some(ChainOfThought)
    case "plan" | "plan-then-implement" | "ptisi" => Some(PlanThenImplement)
    case _                                        => None

  def displayName(s: PromptStrategy): String = s match
    case ZeroShot          => "ZeroShot"
    case ChainOfThought    => "ChainOfThought"
    case PlanThenImplement => "PlanThenImplement"
