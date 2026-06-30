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
    val family = ModelFamily.of(req.model)
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
