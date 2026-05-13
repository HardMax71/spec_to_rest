package specrest.codegen.migration

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser
import io.circe.syntax.EncoderOps
import specrest.convention.ColumnSpec
import specrest.convention.DatabaseSchema
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec
import specrest.convention.TriggerAggregate
import specrest.convention.TriggerSpec

final case class SchemaSnapshot(schemaVersion: Int, schema: DatabaseSchema) derives CanEqual

object SchemaSnapshot:
  val CurrentVersion: Int = 2

  def of(schema: DatabaseSchema): SchemaSnapshot = SchemaSnapshot(CurrentVersion, schema)

object SchemaCodec:
  private given Codec[ColumnSpec]     = deriveCodec
  private given Codec[ForeignKeySpec] = deriveCodec
  private given Codec[IndexSpec]      = deriveCodec
  private given Codec[TableSpec]      = deriveCodec
  private given Codec[TriggerAggregate] = Codec.from(
    Decoder[String].emap:
      case "Sum"   => Right(TriggerAggregate.Sum)
      case "Count" => Right(TriggerAggregate.Count)
      case "Min"   => Right(TriggerAggregate.Min)
      case "Max"   => Right(TriggerAggregate.Max)
      case other   => Left(s"unknown TriggerAggregate: $other")
    ,
    Encoder[String].contramap:
      case TriggerAggregate.Sum   => "Sum"
      case TriggerAggregate.Count => "Count"
      case TriggerAggregate.Min   => "Min"
      case TriggerAggregate.Max   => "Max"
  )
  private given Codec[TriggerSpec] = deriveCodec
  private given Codec[DatabaseSchema] = Codec.from(
    new Decoder[DatabaseSchema]:
      def apply(c: HCursor): Decoder.Result[DatabaseSchema] =
        for
          tables   <- c.downField("tables").as[List[TableSpec]]
          triggers <- c.downField("triggers").as[Option[List[TriggerSpec]]].map(_.getOrElse(Nil))
        yield DatabaseSchema(tables, triggers)
    ,
    Encoder.AsObject.instance: schema =>
      io.circe.JsonObject(
        "tables"   -> schema.tables.asJson,
        "triggers" -> schema.triggers.asJson
      )
  )
  given snapshotCodec: Codec[SchemaSnapshot] = deriveCodec

  def encode(snapshot: SchemaSnapshot): String = snapshot.asJson.spaces2

  def decode(json: String): Either[String, SchemaSnapshot] =
    parser.parse(json).left.map(_.message).flatMap: parsed =>
      parsed.as[SchemaSnapshot].left.map(_.message).flatMap: snap =>
        snap.schemaVersion match
          case v if v == SchemaSnapshot.CurrentVersion => Right(snap)
          case 1                                       => Right(snap.copy(schemaVersion = SchemaSnapshot.CurrentVersion))
          case other =>
            Left(
              s"unsupported schemaVersion $other (expected ${SchemaSnapshot.CurrentVersion} or a known prior version)"
            )
