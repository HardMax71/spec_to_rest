package specrest.synth

import cats.effect.IO
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser
import io.circe.syntax.EncoderOps
import specrest.convention.dafny.DafnyMethodHeader

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

final case class CacheKey(value: String)

enum CacheOutcome derives CanEqual:
  case Verified, Skeleton

object CacheOutcome:
  given Encoder[CacheOutcome] = Encoder.encodeString.contramap:
    case Verified => "verified"
    case Skeleton => "skeleton"
  given Decoder[CacheOutcome] = Decoder.decodeString.emap:
    case "verified" => Right(Verified)
    case "skeleton" => Right(Skeleton)
    case other      => Left(s"unknown CacheOutcome: $other")

final case class CacheEntry(
    candidate: String,
    body: String,
    usage: TokenUsage,
    model: String,
    promptVersion: String,
    outcome: CacheOutcome = CacheOutcome.Verified
)

object CacheEntry:
  given Encoder[TokenUsage] = deriveEncoder
  given Decoder[TokenUsage] = deriveDecoder
  given Encoder[CacheEntry] = deriveEncoder
  given Decoder[CacheEntry] = Decoder.instance: c =>
    for
      candidate     <- c.downField("candidate").as[String]
      body          <- c.downField("body").as[String]
      usage         <- c.downField("usage").as[TokenUsage]
      model         <- c.downField("model").as[String]
      promptVersion <- c.downField("promptVersion").as[String]
      outcome <-
        c.downField("outcome").as[Option[CacheOutcome]].map(_.getOrElse(CacheOutcome.Verified))
    yield CacheEntry(candidate, body, usage, model, promptVersion, outcome)

final class Cache(root: Path):
  def lookup(key: CacheKey): IO[Option[CacheEntry]] =
    IO.blocking {
      val target = pathFor(key)
      if !Files.exists(target) then None
      else
        val text = Files.readString(target, StandardCharsets.UTF_8)
        parser.decode[CacheEntry](text).toOption
    }

  def store(key: CacheKey, entry: CacheEntry): IO[Unit] =
    IO.blocking {
      val target = pathFor(key)
      Option(target.getParent).foreach(Files.createDirectories(_))
      val unique  = s"${java.util.UUID.randomUUID().toString}"
      val tmp     = target.resolveSibling(s"${target.getFileName.toString}.$unique.tmp")
      val payload = entry.asJson.spaces2.getBytes(StandardCharsets.UTF_8)
      Files.write(tmp, payload)
      Files.move(
        tmp,
        target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
      )
      ()
    }

  private def pathFor(key: CacheKey): Path =
    val prefix = key.value.take(2)
    root.resolve(prefix).resolve(s"${key.value}.json")

object Cache:
  def make(root: Path): IO[Cache] =
    IO.blocking {
      Files.createDirectories(root)
      new Cache(root)
    }

  def disabled: Option[Cache] = None

  def keyFor(
      header: DafnyMethodHeader,
      model: String,
      temperature: Double,
      promptVersion: String = SynthPromptVersion
  ): CacheKey =
    val parts = List(
      s"sig=${header.signature}",
      s"req#${header.requiresClauses.length}=" + header.requiresClauses.mkString("\n"),
      s"ens#${header.ensuresClauses.length}=" + header.ensuresClauses.mkString("\n"),
      s"mod#${header.modifiesClauses.length}=" + header.modifiesClauses.mkString("\n"),
      s"model=$model",
      s"temp=${java.lang.Double.toString(temperature)}",
      s"pv=$promptVersion"
    )
    val joined = parts.mkString("\u001f")
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes  = digest.digest(joined.getBytes(StandardCharsets.UTF_8))
    CacheKey(bytes.map(b => f"${b & 0xff}%02x").mkString)

  // Single tracked cache under the repo. Synth `verify` writes here; subsequent
  // runs and CI read from here. Developers running disposable CEGIS experiments
  // pass `--cache-dir` to a scratch path so their attempts don't show up in
  // `git status`. `--no-cache` bypasses both read and write.
  def defaultRoot(workdir: Path): Path =
    workdir.resolve("fixtures").resolve("synth-cache")

  def verifiedRoot(cacheRoot: Path): Path  = cacheRoot.resolve("verified")
  def skeletonsRoot(cacheRoot: Path): Path = cacheRoot.resolve("skeletons")
