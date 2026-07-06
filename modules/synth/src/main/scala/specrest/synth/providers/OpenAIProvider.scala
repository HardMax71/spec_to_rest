package specrest.synth.providers

import cats.effect.IO
import cats.effect.kernel.Resource
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.errors.OpenAIException
import com.openai.models.ReasoningEffort
import com.openai.models.chat.completions.ChatCompletionCreateParams

import java.time.Duration
import specrest.synth.LlmProvider
import specrest.synth.LlmRequest
import specrest.synth.LlmResponse
import specrest.synth.ProviderError
import specrest.synth.TokenUsage

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.control.NonFatal

final class OpenAIProvider(client: OpenAIClient) extends LlmProvider:
  def name: String = "openai"

  def complete(req: LlmRequest): IO[Either[ProviderError, LlmResponse]] =
    IO.blocking {
      val base = ChatCompletionCreateParams
        .builder()
        .model(req.model)
        .maxCompletionTokens(req.maxTokens.toLong)
        .temperature(req.temperature)
        .addSystemMessage(req.system)
        .addUserMessage(req.userMessage)
      // Reasoning models burn 20+ minutes per call at their default effort;
      // the CEGIS loop supplies depth through iterations, so low effort per
      // call converges in wall-clock time the dafny feedback loop can use.
      val params =
        (if req.model.startsWith("gpt-5") then base.reasoningEffort(ReasoningEffort.LOW)
         else base).build()
      val response = client.chat().completions().create(params)
      val text = response.choices().asScala.iterator
        .flatMap(c => c.message().content().toScala.iterator)
        .mkString
      val (in, out) = response.usage().toScala match
        case Some(u) => (u.promptTokens().toInt, u.completionTokens().toInt)
        case None    => (0, 0)
      Right(LlmResponse(text, TokenUsage(in, out), response.model())): Either[
        ProviderError,
        LlmResponse
      ]
    }.handleErrorWith {
      case e: OpenAIException =>
        IO.pure(Left(ProviderError(s"openai API error: ${e.getMessage}", None)))
      case NonFatal(e) =>
        IO.pure(Left(ProviderError(s"openai call failed: ${e.getMessage}", None)))
    }

object OpenAIProvider:
  // Reasoning models legitimately think for minutes; the SDK default request
  // timeout gives up first and surfaces an opaque "Request failed".
  private val RequestTimeout = Duration.ofMinutes(15)

  def make(apiKey: String): Resource[IO, OpenAIProvider] =
    Resource
      .make(
        IO.blocking[OpenAIClient](
          OpenAIOkHttpClient.builder().apiKey(apiKey).timeout(RequestTimeout).build()
        )
      )(c => IO.blocking(c.close()).handleError(_ => ()))
      .map(new OpenAIProvider(_))

  def fromEnv: Resource[IO, OpenAIProvider] =
    Resource
      .make(
        IO.blocking[OpenAIClient](
          OpenAIOkHttpClient.builder().fromEnv().timeout(RequestTimeout).build()
        )
      )(c => IO.blocking(c.close()).handleError(_ => ()))
      .map(new OpenAIProvider(_))
