package specrest.codegen.migration

enum CanonicalType derives CanEqual:
  case Text
  case Varchar(length: Int)
  case Int4
  case Serial4
  case Int8
  case Serial8
  case Float8
  case Bool
  case Timestamptz
  case DateOnly
  case Uuid
  case Numeric(precision: Int, scale: Option[Int])
  case Bytes
  case Json

object CanonicalType:

  private val NumericWithScalePattern = """^NUMERIC\((\d+)\s*,\s*(\d+)\)$""".r
  private val NumericNoScalePattern   = """^NUMERIC\((\d+)\)$""".r
  private val VarcharPattern          = """^VARCHAR\((\d+)\)$""".r

  def parse(sqlType: String): Option[CanonicalType] = sqlType match
    case "TEXT"                        => Some(Text)
    case "INTEGER"                     => Some(Int4)
    case "SERIAL"                      => Some(Serial4)
    case "BIGINT"                      => Some(Int8)
    case "BIGSERIAL"                   => Some(Serial8)
    case "DOUBLE PRECISION"            => Some(Float8)
    case "BOOLEAN"                     => Some(Bool)
    case "TIMESTAMPTZ"                 => Some(Timestamptz)
    case "DATE"                        => Some(DateOnly)
    case "UUID"                        => Some(Uuid)
    case "BYTEA"                       => Some(Bytes)
    case "JSONB"                       => Some(Json)
    case NumericWithScalePattern(p, s) => Some(Numeric(p.toInt, Some(s.toInt)))
    case NumericNoScalePattern(p)      => Some(Numeric(p.toInt, None))
    case VarcharPattern(n)             => Some(Varchar(n.toInt))
    case _                             => None
