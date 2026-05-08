package specrest.synth

import cats.effect.IO

trait LlmProvider:
  def name: String
  def complete(req: LlmRequest): IO[Either[ProviderError, LlmResponse]]

final case class LlmRequest(
    system: String,
    userMessage: String,
    model: String,
    maxTokens: Int,
    temperature: Double
)

final case class LlmResponse(
    text: String,
    usage: TokenUsage,
    model: String
)

final case class TokenUsage(inputTokens: Int, outputTokens: Int)

final case class ProviderError(message: String, status: Option[Int] = None)
