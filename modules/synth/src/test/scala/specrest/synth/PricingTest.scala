package specrest.synth

import munit.FunSuite

class PricingTest extends FunSuite:

  test("known model maps to its pricing row"):
    assertEquals(
      Pricing.forModel("claude-sonnet-4-6").map(_.inputUsdPerMTok),
      Some(3.00)
    )

  test("date-suffixed variant matches base model pricing"):
    assertEquals(
      Pricing.forModel("claude-haiku-4-5-20251001").map(_.outputUsdPerMTok),
      Some(4.00)
    )

  test("unknown model returns None"):
    assertEquals(Pricing.forModel("not-a-model"), None)

  test("cost computes input * input-rate + output * output-rate per million"):
    val u = TokenUsage(1_000_000, 0)
    assertEquals(Pricing.cost(u, "claude-sonnet-4-6"), Some(3.00))

  test("costOrZero returns 0 for unknown model"):
    assertEquals(Pricing.costOrZero(TokenUsage(100, 100), "unknown"), 0.0)
