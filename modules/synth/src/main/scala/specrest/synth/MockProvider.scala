package specrest.synth

import cats.effect.IO
import cats.effect.kernel.Ref

final class MockProvider private (
    plan: List[Either[ProviderError, LlmResponse]],
    cursor: Ref[IO, Int],
    callsRef: Ref[IO, List[LlmRequest]]
) extends LlmProvider:
  def name: String = "mock"

  def complete(req: LlmRequest): IO[Either[ProviderError, LlmResponse]] =
    for
      _ <- callsRef.update(req :: _)
      i <- cursor.getAndUpdate(_ + 1)
    yield
      if i >= plan.length then
        Left(ProviderError(s"MockProvider exhausted after $i calls"))
      else plan(i)

  def calls: IO[List[LlmRequest]] = callsRef.get.map(_.reverse)

object MockProvider:
  def of(responses: List[Either[ProviderError, LlmResponse]]): IO[MockProvider] =
    for
      cur <- Ref.of[IO, Int](0)
      log <- Ref.of[IO, List[LlmRequest]](Nil)
    yield new MockProvider(responses, cur, log)

  def succeeding(
      text: String,
      in: Int = 100,
      out: Int = 200,
      model: String = "mock-model"
  ): IO[MockProvider] =
    of(List(Right(LlmResponse(text, TokenUsage(in, out), model))))

  def failing(message: String): IO[MockProvider] =
    of(List(Left(ProviderError(message))))
