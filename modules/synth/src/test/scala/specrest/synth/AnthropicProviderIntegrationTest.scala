package specrest.synth

import munit.CatsEffectSuite
import specrest.synth.providers.AnthropicProvider

class AnthropicProviderIntegrationTest extends CatsEffectSuite:

  test("real Anthropic call returns non-empty text and usage (gated on ANTHROPIC_API_KEY)") {
    assume(sys.env.contains("ANTHROPIC_API_KEY"), "ANTHROPIC_API_KEY not set; skipping")
    AnthropicProvider.fromEnv.use { provider =>
      val req = LlmRequest(
        system = "You are a terse assistant. Reply with a single short sentence.",
        userMessage = "Say hello.",
        model = "claude-haiku-4-5",
        maxTokens = 64,
        temperature = 1.0
      )
      provider.complete(req).map {
        case Left(err) => fail(s"expected Right, got $err")
        case Right(resp) =>
          assert(resp.text.nonEmpty)
          assert(resp.usage.inputTokens > 0)
          assert(resp.usage.outputTokens > 0)
      }
    }
  }
