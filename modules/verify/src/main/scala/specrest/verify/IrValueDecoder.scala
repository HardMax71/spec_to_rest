package specrest.verify

import com.microsoft.z3.Expr as Z3AstExpr
import com.microsoft.z3.RatNum
import com.microsoft.z3.enumerations.Z3_decl_kind
import specrest.ir.generated.SpecRestGenerated.*
import specrest.verify.z3.Z3Sort

private given CanEqual[Z3_decl_kind, Z3_decl_kind] = CanEqual.derived

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
      case Z3Sort.OptionOf(elem) =>
        // Backend encodes Option as a Z3 datatype with ctors none_<sort> /
        // some_<sort> (see optionSortFor); model values are ctor apps.
        if !expr.isApp then None
        else
          val decl = expr.getFuncDecl
          val name = decl.getName.toString
          if decl.getDeclKind != Z3_decl_kind.Z3_OP_DT_CONSTRUCTOR then None
          else if name.startsWith("none_") then Some(VNone())
          else if name.startsWith("some_") && expr.getNumArgs == 1 then
            decodeZ3(expr.getArgs()(0), elem, rawToLabel).map(VSome.apply)
          else None
      case Z3Sort.SeqOf(elem) =>
        seqElems(expr).flatMap: elems =>
          traverse(elems)(e => decodeZ3(e, elem, rawToLabel)).map(VSeq.apply)
      case Z3Sort.MapOf(k, v) =>
        // Backend encodes Map as a Z3 sequence of two-field entry tuples
        // (see mapEntrySortFor); each entry is a tuple-ctor app.
        seqElems(expr).flatMap: entries =>
          traverse(entries): e =>
            if e.isApp && e.getNumArgs == 2 &&
              e.getFuncDecl.getDeclKind == Z3_decl_kind.Z3_OP_DT_CONSTRUCTOR
            then
              for
                kv <- decodeZ3(e.getArgs()(0), k, rawToLabel)
                vv <- decodeZ3(e.getArgs()(1), v, rawToLabel)
              yield (kv, vv)
            else None
          .map(VMap.apply)
      case Z3Sort.Str =>
        if raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") then
          Some(VStr(raw.substring(1, raw.length - 1).replace("\"\"", "\"")))
        else Some(VStr(raw))

  private def seqElems(expr: Z3AstExpr[?]): Option[List[Z3AstExpr[?]]] =
    if !expr.isApp then None
    else
      expr.getFuncDecl.getDeclKind match
        case Z3_decl_kind.Z3_OP_SEQ_EMPTY => Some(Nil)
        case Z3_decl_kind.Z3_OP_SEQ_UNIT  => Some(List(expr.getArgs()(0)))
        case Z3_decl_kind.Z3_OP_SEQ_CONCAT =>
          traverse(expr.getArgs.toList)(seqElems).map(_.flatten)
        case _ => None

  private def traverse[A, B](xs: List[A])(f: A => Option[B]): Option[List[B]] =
    xs.foldRight(Option(List.empty[B])): (x, acc) =>
      for b <- f(x); rest <- acc yield b :: rest
