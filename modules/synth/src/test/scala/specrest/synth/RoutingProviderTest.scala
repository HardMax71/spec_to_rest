package specrest.synth

import munit.CatsEffectSuite
import specrest.synth.providers.RoutingProvider

class RoutingProviderTest extends CatsEffectSuite:

  private def req(model: String): LlmRequest =
    LlmRequest("sys", "user", model, 256, 1.0)

  test("dispatches each model to the client for its family"):
    for
      anthropic <- MockProvider.succeeding("anthropic-body", model = "claude-sonnet-4-6")
      openai    <- MockProvider.succeeding("openai-body", model = "gpt-5")
      router = new RoutingProvider(
                 Map(
                   ModelFamily.Anthropic -> anthropic,
                   ModelFamily.OpenAI    -> openai
                 )
               )
      claude       <- router.complete(req("claude-opus-4-7"))
      gpt          <- router.complete(req("gpt-5"))
      anthropicHit <- anthropic.calls
      openaiHit    <- openai.calls
    yield
      assert(claude.exists(_.text == "anthropic-body"), s"claude route: $claude")
      assert(gpt.exists(_.text == "openai-body"), s"gpt route: $gpt")
      assertEquals(anthropicHit.map(_.model), List("claude-opus-4-7"))
      assertEquals(openaiHit.map(_.model), List("gpt-5"))

  test("an unconfigured family is reported as a ProviderError, never a crash"):
    for
      anthropic <- MockProvider.succeeding("anthropic-body", model = "claude-sonnet-4-6")
      router     = new RoutingProvider(Map(ModelFamily.Anthropic -> anthropic))
      result    <- router.complete(req("gpt-5"))
    yield result match
      case Left(err) => assert(err.message.contains("gpt-5"), s"message: ${err.message}")
      case Right(r)  => fail(s"expected ProviderError for the unconfigured family, got $r")

  test("a model that matches no known family fails closed with a ProviderError"):
    for
      anthropic <- MockProvider.succeeding("anthropic-body", model = "claude-sonnet-4-6")
      openai    <- MockProvider.succeeding("openai-body", model = "gpt-5")
      router = new RoutingProvider(
                 Map(
                   ModelFamily.Anthropic -> anthropic,
                   ModelFamily.OpenAI    -> openai
                 )
               )
      result <- router.complete(req("gemini-pro"))
    yield result match
      case Left(err) => assert(err.message.contains("gemini-pro"), s"message: ${err.message}")
      case Right(r)  => fail(s"expected ProviderError for an unknown family, got $r")

  test("ModelFamily.of classifies by prefix and returns None for the rest"):
    assertEquals(ModelFamily.of("gpt-5"), Some(ModelFamily.OpenAI))
    assertEquals(ModelFamily.of("gpt-4o-mini"), Some(ModelFamily.OpenAI))
    assertEquals(ModelFamily.of("claude-opus-4-8"), Some(ModelFamily.Anthropic))
    assertEquals(ModelFamily.of("o3"), None)
    assertEquals(ModelFamily.of("gemini-pro"), None)
