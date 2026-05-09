package specrest.synth

import cats.effect.IO
import cats.effect.kernel.Ref

final case class CallRecord(
    operationName: String,
    model: String,
    usage: TokenUsage,
    costUsd: Double,
    cached: Boolean
)

final case class CostSummary(
    operations: Int,
    inputTokens: Long,
    outputTokens: Long,
    totalUsd: Double,
    cachedHits: Int
)

final class Tracker private (records: Ref[IO, List[CallRecord]]):
  def record(r: CallRecord): IO[Unit] =
    records.update(r :: _)

  def all: IO[List[CallRecord]] = records.get.map(_.reverse)

  def summary: IO[CostSummary] =
    records.get.map: rs =>
      CostSummary(
        operations = rs.size,
        inputTokens = rs.map(_.usage.inputTokens.toLong).sum,
        outputTokens = rs.map(_.usage.outputTokens.toLong).sum,
        totalUsd = rs.map(_.costUsd).sum,
        cachedHits = rs.count(_.cached)
      )

object Tracker:
  def empty: IO[Tracker] = Ref.of[IO, List[CallRecord]](Nil).map(new Tracker(_))
