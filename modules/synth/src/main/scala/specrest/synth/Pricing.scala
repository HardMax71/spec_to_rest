package specrest.synth

final case class ModelPricing(
    model: String,
    inputUsdPerMTok: Double,
    outputUsdPerMTok: Double,
    provider: String
)

object Pricing:

  val table: List[ModelPricing] = List(
    ModelPricing("claude-opus-4-7", 15.00, 75.00, "anthropic"),
    ModelPricing("claude-opus-4-6", 15.00, 75.00, "anthropic"),
    ModelPricing("claude-sonnet-4-6", 3.00, 15.00, "anthropic"),
    ModelPricing("claude-sonnet-4-5", 3.00, 15.00, "anthropic"),
    ModelPricing("claude-haiku-4-5", 0.80, 4.00, "anthropic"),
    ModelPricing("gpt-4o", 2.50, 10.00, "openai"),
    ModelPricing("gpt-4o-mini", 0.15, 0.60, "openai"),
    ModelPricing("gpt-4-turbo", 10.00, 30.00, "openai"),
    ModelPricing("gpt-4.1", 2.00, 8.00, "openai"),
    ModelPricing("gpt-4.1-mini", 0.40, 1.60, "openai"),
    // gpt-5 figures are deliberately conservative (high) estimates so an unpriced
    // frontier model can never silently disable the --cost-cap-usd guard; the cap is
    // meant to fail safe (abort early) rather than overspend on a missing price row.
    ModelPricing("gpt-5", 2.50, 20.00, "openai"),
    ModelPricing("gpt-5-mini", 0.50, 4.00, "openai")
  )

  def forModel(model: String): Option[ModelPricing] =
    table
      .find(_.model == model)
      .orElse:
        table
          .filter(p => model.startsWith(s"${p.model}-"))
          .sortBy(p => -p.model.length)
          .headOption

  def cost(usage: TokenUsage, model: String): Option[Double] =
    forModel(model).map: p =>
      (usage.inputTokens.toDouble * p.inputUsdPerMTok +
        usage.outputTokens.toDouble * p.outputUsdPerMTok) / 1_000_000.0

  def costOrZero(usage: TokenUsage, model: String): Double =
    cost(usage, model).getOrElse(0.0)
