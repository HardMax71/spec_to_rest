package specrest.verify

import com.microsoft.z3.Expr as Z3AstExpr
import com.microsoft.z3.RatNum
import specrest.ir.generated.SpecRestGenerated.*
import specrest.verify.z3.Z3Sort

object IrValueDecoder:

  private val NegNumRe   = """^\(-\s+(\d+)\)$""".r
  private val PlainIntRe = """^-?\d+$""".r

  def decodeZ3(
      expr: Z3AstExpr[?],
      expectedSort: Z3Sort,
      rawToLabel: Map[String, String]
  ): Option[ir_value] =
    val raw = expr.toString.trim
    expectedSort match
      case Z3Sort.Bool =>
        raw match
          case "true"  => Some(VBool(true))
          case "false" => Some(VBool(false))
          case _       => None
      case Z3Sort.Int =>
        NegNumRe.findFirstMatchIn(raw) match
          case Some(m) => Some(VInt(BigInt(s"-${m.group(1)}")))
          case None =>
            if PlainIntRe.matches(raw) then
              scala.util.Try(BigInt(raw)).toOption.map(b => VInt(b))
            else None
      case Z3Sort.Uninterp(_) =>
        rawToLabel.get(raw) match
          case Some(label) =>
            if label.contains("#") then
              val parts = label.split("#", 2)
              if parts.length == 2 then Some(VEntity(parts(0), parts(1))) else None
            else if label.contains(".") then
              val parts = label.split("\\.", 2)
              if parts.length == 2 then Some(VEnum(parts(0), parts(1))) else None
            else None
          case None => None
      case Z3Sort.Real =>
        expr match
          case r: RatNum =>
            // The extracted rat ops (equal_rat, less_rat) assume the HOL
            // normal form: coprime pair, positive denominator. Z3's GMP
            // rationals are canonical in practice, but that is not an API
            // contract - route through the verified normalizer instead of
            // assuming it.
            Some(VReal(Frct(normalize((
              BigInt(r.getBigIntNumerator),
              BigInt(r.getBigIntDenominator)
            )))))
          case _ => None
      case Z3Sort.SetOf(_) =>
        None
      case Z3Sort.OptionOf(_) =>
        None
      case Z3Sort.SeqOf(_) =>
        None
      case Z3Sort.MapOf(_, _) =>
        None
      case Z3Sort.Str =>
        if raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") then
          Some(VStr(raw.substring(1, raw.length - 1).replace("\"\"", "\"")))
        else Some(VStr(raw))
