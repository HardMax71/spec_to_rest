package specrest.codegen.migration

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser
import io.circe.syntax.EncoderOps
import specrest.ir.generated.SpecRestGenerated.*

final case class SchemaSnapshot(schemaVersion: Int, schema: database_schema) derives CanEqual

object SchemaSnapshot:
  val CurrentVersion: Int = 2

  def of(schema: database_schema): SchemaSnapshot = SchemaSnapshot(CurrentVersion, schema)

object SchemaCodec:
  private given Codec[column_spec] = Codec.from(
    (c: HCursor) =>
      for
        name         <- c.downField("name").as[String]
        sqlType      <- c.downField("sqlType").as[String]
        nullable     <- c.downField("nullable").as[Boolean]
        defaultValue <- c.downField("defaultValue").as[Option[String]]
      yield ColumnSpec(name, sqlType, nullable, defaultValue),
    (col: column_spec) =>
      Json.obj(
        "name"         -> Json.fromString(column_name(col)),
        "sqlType"      -> Json.fromString(column_sql_type(col)),
        "nullable"     -> Json.fromBoolean(column_nullable(col)),
        "defaultValue" -> column_default_value(col).fold(Json.Null)(Json.fromString)
      )
  )

  private given Codec[foreign_key_spec] = Codec.from(
    (c: HCursor) =>
      for
        column    <- c.downField("column").as[String]
        refTable  <- c.downField("refTable").as[String]
        refColumn <- c.downField("refColumn").as[String]
        onDelete  <- c.downField("onDelete").as[String]
      yield ForeignKeySpec(column, refTable, refColumn, onDelete),
    (fk: foreign_key_spec) =>
      Json.obj(
        "column"    -> Json.fromString(fk_column(fk)),
        "refTable"  -> Json.fromString(fk_ref_table(fk)),
        "refColumn" -> Json.fromString(fk_ref_column(fk)),
        "onDelete"  -> Json.fromString(fk_on_delete(fk))
      )
  )

  private given Codec[index_spec] = Codec.from(
    (c: HCursor) =>
      for
        name         <- c.downField("name").as[String]
        columns      <- c.downField("columns").as[List[String]]
        unique       <- c.downField("unique").as[Boolean]
        filterClause <- c.downField("filterClause").as[Option[String]]
      yield IndexSpec(name, columns, unique, filterClause),
    (ix: index_spec) =>
      Json.obj(
        "name"         -> Json.fromString(index_name(ix)),
        "columns"      -> Json.arr(index_columns(ix).map(Json.fromString)*),
        "unique"       -> Json.fromBoolean(index_unique(ix)),
        "filterClause" -> index_filter_clause(ix).fold(Json.Null)(Json.fromString)
      )
  )

  private given Codec[table_spec] = Codec.from(
    (c: HCursor) =>
      for
        name        <- c.downField("name").as[String]
        entityName  <- c.downField("entityName").as[String]
        columns     <- c.downField("columns").as[List[column_spec]]
        primaryKey  <- c.downField("primaryKey").as[String]
        foreignKeys <- c.downField("foreignKeys").as[List[foreign_key_spec]]
        checks      <- c.downField("checks").as[List[String]]
        indexes     <- c.downField("indexes").as[List[index_spec]]
      yield TableSpec(name, entityName, columns, primaryKey, foreignKeys, checks, indexes),
    (t: table_spec) =>
      Json.obj(
        "name"        -> Json.fromString(table_name(t)),
        "entityName"  -> Json.fromString(table_entity_name(t)),
        "columns"     -> table_columns(t).asJson,
        "primaryKey"  -> Json.fromString(table_primary_key(t)),
        "foreignKeys" -> table_foreign_keys(t).asJson,
        "checks"      -> Json.arr(table_checks(t).map(Json.fromString)*),
        "indexes"     -> table_indexes(t).asJson
      )
  )

  private given Codec[trigger_aggregate] = Codec.from(
    Decoder[String].emap:
      case "Sum"   => Right(SumAgg())
      case "Count" => Right(CountAgg())
      case "Min"   => Right(MinAgg())
      case "Max"   => Right(MaxAgg())
      case other   => Left(s"unknown trigger_aggregate: $other")
    ,
    Encoder[String].contramap:
      case _: SumAgg   => "Sum"
      case _: CountAgg => "Count"
      case _: MinAgg   => "Min"
      case _: MaxAgg   => "Max"
  )

  private given Codec[trigger_spec] = Codec.from(
    (c: HCursor) =>
      for
        name             <- c.downField("name").as[String]
        functionName     <- c.downField("functionName").as[String]
        targetTable      <- c.downField("targetTable").as[String]
        targetColumn     <- c.downField("targetColumn").as[String]
        sourceTable      <- c.downField("sourceTable").as[String]
        sourceForeignKey <- c.downField("sourceForeignKey").as[String]
        aggregate        <- c.downField("aggregate").as[trigger_aggregate]
        sourceColumn     <- c.downField("sourceColumn").as[Option[String]]
      yield TriggerSpec(
        name,
        functionName,
        targetTable,
        targetColumn,
        sourceTable,
        sourceForeignKey,
        aggregate,
        sourceColumn
      ),
    (tg: trigger_spec) =>
      Json.obj(
        "name"             -> Json.fromString(trigger_name(tg)),
        "functionName"     -> Json.fromString(trigger_function_name(tg)),
        "targetTable"      -> Json.fromString(trigger_target_table(tg)),
        "targetColumn"     -> Json.fromString(trigger_target_column(tg)),
        "sourceTable"      -> Json.fromString(trigger_source_table(tg)),
        "sourceForeignKey" -> Json.fromString(trigger_source_foreign_key(tg)),
        "aggregate"        -> trigger_aggregate_of(tg).asJson,
        "sourceColumn"     -> trigger_source_column(tg).fold(Json.Null)(Json.fromString)
      )
  )

  private given Codec[database_schema] = Codec.from(
    new Decoder[database_schema]:
      def apply(c: HCursor): Decoder.Result[database_schema] =
        for
          tables   <- c.downField("tables").as[List[table_spec]]
          triggers <- c.downField("triggers").as[Option[List[trigger_spec]]].map(_.getOrElse(Nil))
        yield DatabaseSchema(tables, triggers)
    ,
    Encoder.AsObject.instance: schema =>
      io.circe.JsonObject(
        "tables"   -> schema_tables(schema).asJson,
        "triggers" -> schema_triggers(schema).asJson
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
