package specrest.synth.providers

import cats.effect.IO
import specrest.synth.LlmProvider
import specrest.synth.LlmRequest
import specrest.synth.LlmResponse
import specrest.synth.ModelFamily
import specrest.synth.ProviderError

final class RoutingProvider(byFamily: Map[ModelFamily, LlmProvider]) extends LlmProvider:
  def name: String = "routing"

  def complete(req: LlmRequest): IO[Either[ProviderError, LlmResponse]] =
    ModelFamily.of(req.model) match
      case None =>
        IO.pure(
          Left(
            ProviderError(
              s"model '${req.model}' matches no known provider family; " +
                "expected a gpt-* (OpenAI) or claude-* (Anthropic) model name"
            )
          )
        )
      case Some(family) =>
        byFamily.get(family) match
          case Some(p) => p.complete(req)
          case None =>
            IO.pure(
              Left(
                ProviderError(
                  s"model '${req.model}' routes to the $family client, which is not configured " +
                    "for this run; supply its API key or drop the model from the escalation ladder"
                )
              )
            )
