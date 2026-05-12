package specrest.codegen.migration

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser
import io.circe.syntax.EncoderOps
import specrest.convention.ColumnSpec
import specrest.convention.DatabaseSchema
import specrest.convention.ForeignKeySpec
import specrest.convention.IndexSpec
import specrest.convention.TableSpec

final case class SchemaSnapshot(schemaVersion: Int, schema: DatabaseSchema) derives CanEqual

object SchemaSnapshot:
  val CurrentVersion: Int = 1

  def of(schema: DatabaseSchema): SchemaSnapshot = SchemaSnapshot(CurrentVersion, schema)

object SchemaCodec:
  private given Codec[ColumnSpec]            = deriveCodec
  private given Codec[ForeignKeySpec]        = deriveCodec
  private given Codec[IndexSpec]             = deriveCodec
  private given Codec[TableSpec]             = deriveCodec
  private given Codec[DatabaseSchema]        = deriveCodec
  given snapshotCodec: Codec[SchemaSnapshot] = deriveCodec

  def encode(snapshot: SchemaSnapshot): String = snapshot.asJson.spaces2

  def decode(json: String): Either[String, SchemaSnapshot] =
    parser.parse(json).left.map(_.message).flatMap: parsed =>
      parsed.as[SchemaSnapshot].left.map(_.message)
