package specrest.synth.providers

import cats.effect.IO
import cats.effect.kernel.Resource
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicException
import com.anthropic.models.messages.MessageCreateParams
import specrest.synth.LlmProvider
import specrest.synth.LlmRequest
import specrest.synth.LlmResponse
import specrest.synth.ProviderError
import specrest.synth.TokenUsage

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.control.NonFatal

final class AnthropicProvider(client: AnthropicClient) extends LlmProvider:
  def name: String = "anthropic"

  def complete(req: LlmRequest): IO[Either[ProviderError, LlmResponse]] =
    IO.blocking {
      val params = MessageCreateParams
        .builder()
        .model(req.model)
        .maxTokens(req.maxTokens.toLong)
        .system(req.system)
        .addUserMessage(req.userMessage)
        .build()
      val message = client.messages().create(params)
      val text = message.content().asScala.iterator
        .flatMap(b => b.text().toScala.iterator)
        .map(_.text())
        .mkString
      val usage = message.usage()
      Right(
        LlmResponse(
          text,
          TokenUsage(usage.inputTokens().toInt, usage.outputTokens().toInt),
          message.model().toString
        )
      ): Either[ProviderError, LlmResponse]
    }.handleErrorWith {
      case e: AnthropicException =>
        IO.pure(Left(ProviderError(s"anthropic API error: ${e.getMessage}", None)))
      case NonFatal(e) =>
        IO.pure(Left(ProviderError(s"anthropic call failed: ${e.getMessage}", None)))
    }

object AnthropicProvider:
  def make(apiKey: String): Resource[IO, AnthropicProvider] =
    Resource
      .make(IO.blocking[AnthropicClient](AnthropicOkHttpClient.builder().apiKey(apiKey).build()))(
        c =>
          IO.blocking(c.close()).handleError(_ => ())
      )
      .map(new AnthropicProvider(_))

  def fromEnv: Resource[IO, AnthropicProvider] =
    Resource
      .make(IO.blocking[AnthropicClient](AnthropicOkHttpClient.fromEnv()))(c =>
        IO.blocking(c.close()).handleError(_ => ())
      )
      .map(new AnthropicProvider(_))
