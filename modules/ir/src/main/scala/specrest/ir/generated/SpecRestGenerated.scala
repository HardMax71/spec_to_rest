package specrest.ir.generated

object Str_Literal {

  private def checkAscii(k: Int): Int =
    0 <= k && k < 128 match {
      case true  => k
      case false => sys.error("Non-ASCII character in literal")
    }

  private def charOfAscii(k: BigInt): Char =
    (k % 128).charValue

  private def asciiOfChar(c: Char): BigInt =
    BigInt(checkAscii(c.toInt))

  def literalOfAsciis(ks: List[BigInt]): String =
    ks.map(charOfAscii).mkString

  def asciisOfLiteral(s: String): List[BigInt] =
    s.toList.map(asciiOfChar)

}

object SpecRestGenerated {

  sealed abstract class num
  final case class Onea()       extends num
  final case class Bit0(a: num) extends num
  final case class Bit1(a: num) extends num

  def one_inta: BigInt = BigInt(1)

  trait one[A] {
    val `SpecRestGenerated.one`: A
  }
  def one[A](implicit A: one[A]): A = A.`SpecRestGenerated.one`
  object one {
    implicit def `SpecRestGenerated.one_int`: one[BigInt] = new one[BigInt] {
      val `SpecRestGenerated.one` = one_inta
    }
  }

  def times_inta(k: BigInt, l: BigInt): BigInt = k * l

  trait times[A] {
    val `SpecRestGenerated.times`: (A, A) => A
  }
  def times[A](a: A, b: A)(implicit A: times[A]): A =
    A.`SpecRestGenerated.times`(a, b)
  object times {
    implicit def `SpecRestGenerated.times_int`: times[BigInt] = new times[BigInt] {
      val `SpecRestGenerated.times` = (a: BigInt, b: BigInt) => times_inta(a, b)
    }
  }

  trait power[A] extends one[A] with times[A] {}
  object power {
    implicit def `SpecRestGenerated.power_int`: power[BigInt] = new power[BigInt] {
      val `SpecRestGenerated.times` = (a: BigInt, b: BigInt) => times_inta(a, b)
      val `SpecRestGenerated.one`   = one_inta
    }
  }

  def equal_int(k: BigInt, l: BigInt): Boolean = k == l

  sealed abstract class span_t
  final case class SpanT(a: BigInt, b: BigInt, c: BigInt, d: BigInt) extends span_t

  def equal_span_ta(x0: span_t, x1: span_t): Boolean = (x0, x1) match {
    case (SpanT(x1, x2, x3, x4), SpanT(y1, y2, y3, y4)) =>
      equal_int(x1, y1) &&
      (equal_int(x2, y2) && (equal_int(x3, y3) && equal_int(x4, y4)))
  }

  trait equal[A] {
    val `SpecRestGenerated.equal`: (A, A) => Boolean
  }
  def equal[A](a: A, b: A)(implicit A: equal[A]): Boolean =
    A.`SpecRestGenerated.equal`(a, b)
  object equal {
    implicit def `SpecRestGenerated.equal_foreign_key_spec`: equal[foreign_key_spec] =
      new equal[foreign_key_spec] {
        val `SpecRestGenerated.equal` =
          (a: foreign_key_spec, b: foreign_key_spec) => equal_foreign_key_speca(a, b)
      }
    implicit def `SpecRestGenerated.equal_integer`: equal[BigInt] = new equal[BigInt] {
      val `SpecRestGenerated.equal` = (a: BigInt, b: BigInt) => a == b
    }
    implicit def `SpecRestGenerated.equal_trigger_spec`: equal[trigger_spec] =
      new equal[trigger_spec] {
        val `SpecRestGenerated.equal` = (a: trigger_spec, b: trigger_spec) =>
          equal_trigger_speca(a, b)
      }
    implicit def `SpecRestGenerated.equal_index_spec`: equal[index_spec] = new equal[index_spec] {
      val `SpecRestGenerated.equal` = (a: index_spec, b: index_spec) =>
        equal_index_speca(a, b)
    }
    implicit def `SpecRestGenerated.equal_prod`[A: equal, B: equal]: equal[(A, B)] =
      new equal[(A, B)] {
        val `SpecRestGenerated.equal` = (a: (A, B), b: (A, B)) =>
          equal_proda[A, B](a, b)
      }
    implicit def `SpecRestGenerated.equal_literal`: equal[String] = new equal[String] {
      val `SpecRestGenerated.equal` = (a: String, b: String) => a == b
    }
    implicit def `SpecRestGenerated.equal_smt_val`: equal[smt_val] = new equal[smt_val] {
      val `SpecRestGenerated.equal` = (a: smt_val, b: smt_val) =>
        equal_smt_vala(a, b)
    }
    implicit def `SpecRestGenerated.equal_span_t`: equal[span_t] = new equal[span_t] {
      val `SpecRestGenerated.equal` = (a: span_t, b: span_t) =>
        equal_span_ta(a, b)
    }
  }

  def equal_bool(p: Boolean, q: Boolean): Boolean =
    p match {
      case true  => q
      case false => !q
    }

  def eq[A: equal](a: A, b: A): Boolean = equal[A](a, b)

  def equal_list[A: equal](x0: List[A], x1: List[A]): Boolean = (x0, x1) match {
    case (Nil, x21 :: x22)        => false
    case (x21 :: x22, Nil)        => false
    case (x21 :: x22, y21 :: y22) => eq[A](x21, y21) && equal_list[A](x22, y22)
    case (Nil, Nil)               => true
  }

  def equal_proda[A: equal, B: equal](x0: (A, B), x1: (A, B)): Boolean =
    (x0, x1) match {
      case ((x1, x2), (y1, y2)) => eq[A](x1, y1) && eq[B](x2, y2)
    }

  sealed abstract class rat
  final case class Frct(a: (BigInt, BigInt)) extends rat

  def quotient_of(x0: rat): (BigInt, BigInt) = x0 match {
    case Frct(x) => x
  }

  def equal_rat(a: rat, b: rat): Boolean =
    equal_proda[BigInt, BigInt](quotient_of(a), quotient_of(b))

  sealed abstract class smt_val
  final case class SBool(a: Boolean)                              extends smt_val
  final case class SInt(a: BigInt)                                extends smt_val
  final case class SReal(a: rat)                                  extends smt_val
  final case class SEnumElem(a: String, b: String)                extends smt_val
  final case class SEntityElem(a: String, b: String)              extends smt_val
  final case class SSet(a: List[smt_val])                         extends smt_val
  final case class SEntityWith(a: smt_val, b: String, c: smt_val) extends smt_val
  final case class SNone()                                        extends smt_val
  final case class SSome(a: smt_val)                              extends smt_val
  final case class SStr(a: String)                                extends smt_val
  final case class SSeq(a: List[smt_val])                         extends smt_val
  final case class SMap(a: List[(smt_val, smt_val)])              extends smt_val

  def equal_smt_vala(x0: smt_val, x1: smt_val): Boolean = (x0, x1) match {
    case (SSeq(x11), SMap(x12))                              => false
    case (SMap(x12), SSeq(x11))                              => false
    case (SStr(x10), SMap(x12))                              => false
    case (SMap(x12), SStr(x10))                              => false
    case (SStr(x10), SSeq(x11))                              => false
    case (SSeq(x11), SStr(x10))                              => false
    case (SSome(x9), SMap(x12))                              => false
    case (SMap(x12), SSome(x9))                              => false
    case (SSome(x9), SSeq(x11))                              => false
    case (SSeq(x11), SSome(x9))                              => false
    case (SSome(x9), SStr(x10))                              => false
    case (SStr(x10), SSome(x9))                              => false
    case (SNone(), SMap(x12))                                => false
    case (SMap(x12), SNone())                                => false
    case (SNone(), SSeq(x11))                                => false
    case (SSeq(x11), SNone())                                => false
    case (SNone(), SStr(x10))                                => false
    case (SStr(x10), SNone())                                => false
    case (SNone(), SSome(x9))                                => false
    case (SSome(x9), SNone())                                => false
    case (SEntityWith(x71, x72, x73), SMap(x12))             => false
    case (SMap(x12), SEntityWith(x71, x72, x73))             => false
    case (SEntityWith(x71, x72, x73), SSeq(x11))             => false
    case (SSeq(x11), SEntityWith(x71, x72, x73))             => false
    case (SEntityWith(x71, x72, x73), SStr(x10))             => false
    case (SStr(x10), SEntityWith(x71, x72, x73))             => false
    case (SEntityWith(x71, x72, x73), SSome(x9))             => false
    case (SSome(x9), SEntityWith(x71, x72, x73))             => false
    case (SEntityWith(x71, x72, x73), SNone())               => false
    case (SNone(), SEntityWith(x71, x72, x73))               => false
    case (SSet(x6), SMap(x12))                               => false
    case (SMap(x12), SSet(x6))                               => false
    case (SSet(x6), SSeq(x11))                               => false
    case (SSeq(x11), SSet(x6))                               => false
    case (SSet(x6), SStr(x10))                               => false
    case (SStr(x10), SSet(x6))                               => false
    case (SSet(x6), SSome(x9))                               => false
    case (SSome(x9), SSet(x6))                               => false
    case (SSet(x6), SNone())                                 => false
    case (SNone(), SSet(x6))                                 => false
    case (SSet(x6), SEntityWith(x71, x72, x73))              => false
    case (SEntityWith(x71, x72, x73), SSet(x6))              => false
    case (SEntityElem(x51, x52), SMap(x12))                  => false
    case (SMap(x12), SEntityElem(x51, x52))                  => false
    case (SEntityElem(x51, x52), SSeq(x11))                  => false
    case (SSeq(x11), SEntityElem(x51, x52))                  => false
    case (SEntityElem(x51, x52), SStr(x10))                  => false
    case (SStr(x10), SEntityElem(x51, x52))                  => false
    case (SEntityElem(x51, x52), SSome(x9))                  => false
    case (SSome(x9), SEntityElem(x51, x52))                  => false
    case (SEntityElem(x51, x52), SNone())                    => false
    case (SNone(), SEntityElem(x51, x52))                    => false
    case (SEntityElem(x51, x52), SEntityWith(x71, x72, x73)) => false
    case (SEntityWith(x71, x72, x73), SEntityElem(x51, x52)) => false
    case (SEntityElem(x51, x52), SSet(x6))                   => false
    case (SSet(x6), SEntityElem(x51, x52))                   => false
    case (SEnumElem(x41, x42), SMap(x12))                    => false
    case (SMap(x12), SEnumElem(x41, x42))                    => false
    case (SEnumElem(x41, x42), SSeq(x11))                    => false
    case (SSeq(x11), SEnumElem(x41, x42))                    => false
    case (SEnumElem(x41, x42), SStr(x10))                    => false
    case (SStr(x10), SEnumElem(x41, x42))                    => false
    case (SEnumElem(x41, x42), SSome(x9))                    => false
    case (SSome(x9), SEnumElem(x41, x42))                    => false
    case (SEnumElem(x41, x42), SNone())                      => false
    case (SNone(), SEnumElem(x41, x42))                      => false
    case (SEnumElem(x41, x42), SEntityWith(x71, x72, x73))   => false
    case (SEntityWith(x71, x72, x73), SEnumElem(x41, x42))   => false
    case (SEnumElem(x41, x42), SSet(x6))                     => false
    case (SSet(x6), SEnumElem(x41, x42))                     => false
    case (SEnumElem(x41, x42), SEntityElem(x51, x52))        => false
    case (SEntityElem(x51, x52), SEnumElem(x41, x42))        => false
    case (SReal(x3), SMap(x12))                              => false
    case (SMap(x12), SReal(x3))                              => false
    case (SReal(x3), SSeq(x11))                              => false
    case (SSeq(x11), SReal(x3))                              => false
    case (SReal(x3), SStr(x10))                              => false
    case (SStr(x10), SReal(x3))                              => false
    case (SReal(x3), SSome(x9))                              => false
    case (SSome(x9), SReal(x3))                              => false
    case (SReal(x3), SNone())                                => false
    case (SNone(), SReal(x3))                                => false
    case (SReal(x3), SEntityWith(x71, x72, x73))             => false
    case (SEntityWith(x71, x72, x73), SReal(x3))             => false
    case (SReal(x3), SSet(x6))                               => false
    case (SSet(x6), SReal(x3))                               => false
    case (SReal(x3), SEntityElem(x51, x52))                  => false
    case (SEntityElem(x51, x52), SReal(x3))                  => false
    case (SReal(x3), SEnumElem(x41, x42))                    => false
    case (SEnumElem(x41, x42), SReal(x3))                    => false
    case (SInt(x2), SMap(x12))                               => false
    case (SMap(x12), SInt(x2))                               => false
    case (SInt(x2), SSeq(x11))                               => false
    case (SSeq(x11), SInt(x2))                               => false
    case (SInt(x2), SStr(x10))                               => false
    case (SStr(x10), SInt(x2))                               => false
    case (SInt(x2), SSome(x9))                               => false
    case (SSome(x9), SInt(x2))                               => false
    case (SInt(x2), SNone())                                 => false
    case (SNone(), SInt(x2))                                 => false
    case (SInt(x2), SEntityWith(x71, x72, x73))              => false
    case (SEntityWith(x71, x72, x73), SInt(x2))              => false
    case (SInt(x2), SSet(x6))                                => false
    case (SSet(x6), SInt(x2))                                => false
    case (SInt(x2), SEntityElem(x51, x52))                   => false
    case (SEntityElem(x51, x52), SInt(x2))                   => false
    case (SInt(x2), SEnumElem(x41, x42))                     => false
    case (SEnumElem(x41, x42), SInt(x2))                     => false
    case (SInt(x2), SReal(x3))                               => false
    case (SReal(x3), SInt(x2))                               => false
    case (SBool(x1), SMap(x12))                              => false
    case (SMap(x12), SBool(x1))                              => false
    case (SBool(x1), SSeq(x11))                              => false
    case (SSeq(x11), SBool(x1))                              => false
    case (SBool(x1), SStr(x10))                              => false
    case (SStr(x10), SBool(x1))                              => false
    case (SBool(x1), SSome(x9))                              => false
    case (SSome(x9), SBool(x1))                              => false
    case (SBool(x1), SNone())                                => false
    case (SNone(), SBool(x1))                                => false
    case (SBool(x1), SEntityWith(x71, x72, x73))             => false
    case (SEntityWith(x71, x72, x73), SBool(x1))             => false
    case (SBool(x1), SSet(x6))                               => false
    case (SSet(x6), SBool(x1))                               => false
    case (SBool(x1), SEntityElem(x51, x52))                  => false
    case (SEntityElem(x51, x52), SBool(x1))                  => false
    case (SBool(x1), SEnumElem(x41, x42))                    => false
    case (SEnumElem(x41, x42), SBool(x1))                    => false
    case (SBool(x1), SReal(x3))                              => false
    case (SReal(x3), SBool(x1))                              => false
    case (SBool(x1), SInt(x2))                               => false
    case (SInt(x2), SBool(x1))                               => false
    case (SMap(x12), SMap(y12))                              => equal_list[(smt_val, smt_val)](x12, y12)
    case (SSeq(x11), SSeq(y11))                              => equal_list[smt_val](x11, y11)
    case (SStr(x10), SStr(y10))                              => x10 == y10
    case (SSome(x9), SSome(y9))                              => equal_smt_vala(x9, y9)
    case (SEntityWith(x71, x72, x73), SEntityWith(y71, y72, y73)) =>
      equal_smt_vala(x71, y71) && (x72 == y72 && equal_smt_vala(x73, y73))
    case (SSet(x6), SSet(y6)) => equal_list[smt_val](x6, y6)
    case (SEntityElem(x51, x52), SEntityElem(y51, y52)) =>
      x51 == y51 && x52 == y52
    case (SEnumElem(x41, x42), SEnumElem(y41, y42)) => x41 == y41 && x42 == y42
    case (SReal(x3), SReal(y3))                     => equal_rat(x3, y3)
    case (SInt(x2), SInt(y2))                       => equal_int(x2, y2)
    case (SBool(x1), SBool(y1))                     => equal_bool(x1, y1)
    case (SNone(), SNone())                         => true
  }

  def equal_option[A: equal](x0: Option[A], x1: Option[A]): Boolean = (x0, x1) match {
    case (None, Some(x2))     => false
    case (Some(x2), None)     => false
    case (Some(x2), Some(y2)) => eq[A](x2, y2)
    case (None, None)         => true
  }

  sealed abstract class index_spec
  final case class IndexSpec(a: String, b: List[String], c: Boolean, d: Option[String])
      extends index_spec

  def equal_index_speca(x0: index_spec, x1: index_spec): Boolean = (x0, x1) match {
    case (IndexSpec(x1, x2, x3, x4), IndexSpec(y1, y2, y3, y4)) =>
      x1 == y1 &&
      (equal_list[String](x2, y2) &&
        (equal_bool(x3, y3) && equal_option[String](x4, y4)))
  }

  sealed abstract class trigger_aggregate
  final case class SumAgg()   extends trigger_aggregate
  final case class CountAgg() extends trigger_aggregate
  final case class MinAgg()   extends trigger_aggregate
  final case class MaxAgg()   extends trigger_aggregate

  def equal_trigger_aggregate(x0: trigger_aggregate, x1: trigger_aggregate): Boolean =
    (x0, x1) match {
      case (MinAgg(), MaxAgg())     => false
      case (MaxAgg(), MinAgg())     => false
      case (CountAgg(), MaxAgg())   => false
      case (MaxAgg(), CountAgg())   => false
      case (CountAgg(), MinAgg())   => false
      case (MinAgg(), CountAgg())   => false
      case (SumAgg(), MaxAgg())     => false
      case (MaxAgg(), SumAgg())     => false
      case (SumAgg(), MinAgg())     => false
      case (MinAgg(), SumAgg())     => false
      case (SumAgg(), CountAgg())   => false
      case (CountAgg(), SumAgg())   => false
      case (MaxAgg(), MaxAgg())     => true
      case (MinAgg(), MinAgg())     => true
      case (CountAgg(), CountAgg()) => true
      case (SumAgg(), SumAgg())     => true
    }

  sealed abstract class trigger_spec
  final case class TriggerSpec(
      a: String,
      b: String,
      c: String,
      d: String,
      e: String,
      f: String,
      g: trigger_aggregate,
      h: Option[String]
  ) extends trigger_spec

  def equal_trigger_speca(x0: trigger_spec, x1: trigger_spec): Boolean =
    (x0, x1) match {
      case (
            TriggerSpec(x1, x2, x3, x4, x5, x6, x7, x8),
            TriggerSpec(y1, y2, y3, y4, y5, y6, y7, y8)
          ) => x1 == y1 &&
        (x2 == y2 &&
          (x3 == y3 &&
            (x4 == y4 &&
              (x5 == y5 &&
                (x6 == y6 &&
                  (equal_trigger_aggregate(x7, y7) &&
                    equal_option[String](x8, y8)))))))
    }

  trait ord[A] {
    val `SpecRestGenerated.less_eq`: (A, A) => Boolean
    val `SpecRestGenerated.less`: (A, A) => Boolean
  }
  def less_eq[A](a: A, b: A)(implicit A: ord[A]): Boolean =
    A.`SpecRestGenerated.less_eq`(a, b)
  def less[A](a: A, b: A)(implicit A: ord[A]): Boolean =
    A.`SpecRestGenerated.less`(a, b)
  object ord {
    implicit def `SpecRestGenerated.ord_integer`: ord[BigInt] = new ord[BigInt] {
      val `SpecRestGenerated.less_eq` = (a: BigInt, b: BigInt) => a <= b
      val `SpecRestGenerated.less`    = (a: BigInt, b: BigInt) => a < b
    }
  }

  sealed abstract class foreign_key_spec
  final case class ForeignKeySpec(a: String, b: String, c: String, d: String)
      extends foreign_key_spec

  def equal_foreign_key_speca(x0: foreign_key_spec, x1: foreign_key_spec): Boolean =
    (x0, x1) match {
      case (ForeignKeySpec(x1, x2, x3, x4), ForeignKeySpec(y1, y2, y3, y4)) =>
        x1 == y1 && (x2 == y2 && (x3 == y3 && x4 == y4))
    }

  sealed abstract class binding_kind
  final case class BkIn()    extends binding_kind
  final case class BkColon() extends binding_kind

  sealed abstract class quant_kind
  final case class QAll()    extends quant_kind
  final case class QSome()   extends quant_kind
  final case class QNo()     extends quant_kind
  final case class QExists() extends quant_kind

  sealed abstract class bin_op
  final case class BAnd()       extends bin_op
  final case class BOr()        extends bin_op
  final case class BImplies()   extends bin_op
  final case class BIff()       extends bin_op
  final case class BEq()        extends bin_op
  final case class BNeq()       extends bin_op
  final case class BLt()        extends bin_op
  final case class BGt()        extends bin_op
  final case class BLe()        extends bin_op
  final case class BGe()        extends bin_op
  final case class BIn()        extends bin_op
  final case class BNotIn()     extends bin_op
  final case class BSubset()    extends bin_op
  final case class BUnion()     extends bin_op
  final case class BIntersect() extends bin_op
  final case class BDiff()      extends bin_op
  final case class BAdd()       extends bin_op
  final case class BSub()       extends bin_op
  final case class BMul()       extends bin_op
  final case class BDiv()       extends bin_op

  sealed abstract class un_op
  final case class UNot()         extends un_op
  final case class UNegate()      extends un_op
  final case class UCardinality() extends un_op
  final case class UPower()       extends un_op

  sealed abstract class quantifier_binding
  final case class QuantifierBindingFull(a: String, b: expr, c: binding_kind, d: Option[span_t])
      extends quantifier_binding

  sealed abstract class field_assign
  final case class FieldAssignFull(a: String, b: expr, c: Option[span_t])
      extends field_assign

  sealed abstract class map_entry
  final case class MapEntryFull(a: expr, b: expr, c: Option[span_t]) extends map_entry

  sealed abstract class expr
  final case class BinaryOpF(a: bin_op, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class UnaryOpF(a: un_op, b: expr, c: Option[span_t]) extends expr
  final case class QuantifierF(
      a: quant_kind,
      b: List[quantifier_binding],
      c: expr,
      d: Option[span_t]
  ) extends expr
  final case class SomeWrapF(a: expr, b: Option[span_t]) extends expr
  final case class TheF(a: String, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class FieldAccessF(a: expr, b: String, c: Option[span_t]) extends expr
  final case class EnumAccessF(a: expr, b: String, c: Option[span_t])  extends expr
  final case class IndexF(a: expr, b: expr, c: Option[span_t])         extends expr
  final case class CallF(a: expr, b: List[expr], c: Option[span_t])    extends expr
  final case class PrimeF(a: expr, b: Option[span_t])                  extends expr
  final case class PreF(a: expr, b: Option[span_t])                    extends expr
  final case class WithF(a: expr, b: List[field_assign], c: Option[span_t])
      extends expr
  final case class IfF(a: expr, b: expr, c: expr, d: Option[span_t]) extends expr
  final case class LetF(a: String, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class LambdaF(a: String, b: expr, c: Option[span_t]) extends expr
  final case class ConstructorF(a: String, b: List[field_assign], c: Option[span_t])
      extends expr
  final case class SetLiteralF(a: List[expr], b: Option[span_t])      extends expr
  final case class MapLiteralF(a: List[map_entry], b: Option[span_t]) extends expr
  final case class SetComprehensionF(a: String, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class SeqLiteralF(a: List[expr], b: Option[span_t])   extends expr
  final case class MatchesF(a: expr, b: String, c: Option[span_t]) extends expr
  final case class IntLitF(a: BigInt, b: Option[span_t])           extends expr
  final case class FloatLitF(a: String, b: Option[span_t])         extends expr
  final case class StringLitF(a: String, b: Option[span_t])        extends expr
  final case class BoolLitF(a: Boolean, b: Option[span_t])         extends expr
  final case class NoneLitF(a: Option[span_t])                     extends expr
  final case class IdentifierF(a: String, b: Option[span_t])       extends expr

  sealed abstract class nat
  final case class Nata(a: BigInt) extends nat

  sealed abstract class set[A]
  final case class seta[A](a: List[A])  extends set[A]
  final case class coset[A](a: List[A]) extends set[A]

  sealed abstract class enum_decl
  final case class EnumDeclFull(a: String, b: List[String], c: Option[span_t])
      extends enum_decl

  sealed abstract class fact_decl
  final case class FactDeclFull(a: Option[String], b: expr, c: Option[span_t])
      extends fact_decl

  sealed abstract class multiplicity
  final case class MultOne()  extends multiplicity
  final case class MultLone() extends multiplicity
  final case class MultSome() extends multiplicity
  final case class MultSet()  extends multiplicity

  sealed abstract class type_expr
  final case class NamedTypeF(a: String, b: Option[span_t])  extends type_expr
  final case class SetTypeF(a: type_expr, b: Option[span_t]) extends type_expr
  final case class MapTypeF(a: type_expr, b: type_expr, c: Option[span_t])
      extends type_expr
  final case class SeqTypeF(a: type_expr, b: Option[span_t])    extends type_expr
  final case class OptionTypeF(a: type_expr, b: Option[span_t]) extends type_expr
  final case class RelationTypeF(a: type_expr, b: multiplicity, c: type_expr, d: Option[span_t])
      extends type_expr

  sealed abstract class ty
  final case class TBool()            extends ty
  final case class TInt()             extends ty
  final case class TReal()            extends ty
  final case class TStr()             extends ty
  final case class TEnum(a: String)   extends ty
  final case class TEntity(a: String) extends ty
  final case class TSet(a: ty)        extends ty
  final case class TOption(a: ty)     extends ty
  final case class TSeq(a: ty)        extends ty
  final case class TMap(a: ty, b: ty) extends ty

  sealed abstract class smt_term
  final case class BLit(a: Boolean)                                extends smt_term
  final case class ILit(a: BigInt)                                 extends smt_term
  final case class RLit(a: rat)                                    extends smt_term
  final case class TVar(a: String)                                 extends smt_term
  final case class EnumElemConst(a: String, b: String)             extends smt_term
  final case class TNot(a: smt_term)                               extends smt_term
  final case class TAnd(a: smt_term, b: smt_term)                  extends smt_term
  final case class TOr(a: smt_term, b: smt_term)                   extends smt_term
  final case class TImplies(a: smt_term, b: smt_term)              extends smt_term
  final case class TEq(a: smt_term, b: smt_term)                   extends smt_term
  final case class TLt(a: smt_term, b: smt_term)                   extends smt_term
  final case class TNeg(a: smt_term)                               extends smt_term
  final case class TAdd(a: smt_term, b: smt_term)                  extends smt_term
  final case class TSub(a: smt_term, b: smt_term)                  extends smt_term
  final case class TMul(a: smt_term, b: smt_term)                  extends smt_term
  final case class TDiv(a: smt_term, b: smt_term)                  extends smt_term
  final case class TInDom(a: String, b: smt_term)                  extends smt_term
  final case class TCardRel(a: String)                             extends smt_term
  final case class TCard(a: smt_term)                              extends smt_term
  final case class TLetIn(a: String, b: smt_term, c: smt_term)     extends smt_term
  final case class TForallEnum(a: String, b: String, c: smt_term)  extends smt_term
  final case class TForallRel(a: String, b: String, c: smt_term)   extends smt_term
  final case class TExistsRel(a: String, b: String, c: smt_term)   extends smt_term
  final case class TTheRel(a: String, b: String, c: smt_term)      extends smt_term
  final case class TEntityBase(a: String)                          extends smt_term
  final case class TForallSet(a: String, b: smt_term, c: smt_term) extends smt_term
  final case class TTheSet(a: String, b: smt_term, c: smt_term)    extends smt_term
  final case class TIndexRel(a: smt_term, b: smt_term)             extends smt_term
  final case class TFieldAccess(a: smt_term, b: String)            extends smt_term
  final case class TSetEmpty()                                     extends smt_term
  final case class TSetInsert(a: smt_term, b: smt_term)            extends smt_term
  final case class TSetMember(a: smt_term, b: smt_term)            extends smt_term
  final case class TSetUnion(a: smt_term, b: smt_term)             extends smt_term
  final case class TSetIntersect(a: smt_term, b: smt_term)         extends smt_term
  final case class TSetDiff(a: smt_term, b: smt_term)              extends smt_term
  final case class TPrime(a: smt_term)                             extends smt_term
  final case class TPre(a: smt_term)                               extends smt_term
  final case class TWithRec(a: smt_term, b: String, c: smt_term)   extends smt_term
  final case class TIte(a: smt_term, b: smt_term, c: smt_term)     extends smt_term
  final case class TNone()                                         extends smt_term
  final case class TSome(a: smt_term)                              extends smt_term
  final case class TStrLit(a: String)                              extends smt_term
  final case class TMatches(a: smt_term, b: String)                extends smt_term
  final case class TUStrPred(a: String, b: smt_term)               extends smt_term
  final case class TUStrFunc(a: String, b: smt_term)               extends smt_term
  final case class TUIntFunc(a: String, b: smt_term)               extends smt_term
  final case class TStrLen(a: smt_term)                            extends smt_term
  final case class TUConst(a: String)                              extends smt_term
  final case class TSeqEmpty()                                     extends smt_term
  final case class TSeqCons(a: smt_term, b: smt_term)              extends smt_term
  final case class TMapEmpty()                                     extends smt_term
  final case class TMapCons(a: smt_term, b: smt_term, c: smt_term) extends smt_term
  final case class TSum(a: smt_term, b: smt_term)                  extends smt_term

  sealed abstract class field_decl
  final case class FieldDeclFull(a: String, b: type_expr, c: Option[expr], d: Option[span_t])
      extends field_decl

  sealed abstract class param_decl
  final case class ParamDeclFull(a: String, b: type_expr, c: Option[span_t])
      extends param_decl

  sealed abstract class security_scheme_kind
  final case class SsBearer(a: Option[String])    extends security_scheme_kind
  final case class SsApiKey(a: String, b: String) extends security_scheme_kind
  final case class SsBasic()                      extends security_scheme_kind

  sealed abstract class security_scheme_decl
  final case class SecuritySchemeDeclFull(a: String, b: security_scheme_kind, c: Option[span_t])
      extends security_scheme_decl

  sealed abstract class validation_failure
  final case class ExpectedString()                extends validation_failure
  final case class ExpectedInteger()               extends validation_failure
  final case class ExpectedBoolean()               extends validation_failure
  final case class EmptyString()                   extends validation_failure
  final case class BadHttpMethod(a: String)        extends validation_failure
  final case class HttpStatusOutOfRange(a: BigInt) extends validation_failure
  final case class HttpPathMissingSlash()          extends validation_failure
  final case class BadTestStrategy(a: String)      extends validation_failure
  final case class BadStrategyFormat(a: String)    extends validation_failure

  sealed abstract class parsed_value
  final case class PvString(a: String)             extends parsed_value
  final case class PvInt(a: BigInt)                extends parsed_value
  final case class PvBool(a: Boolean)              extends parsed_value
  final case class PvStrPair(a: String, b: String) extends parsed_value
  final case class PvExpr(a: expr)                 extends parsed_value

  sealed abstract class convention_value
  final case class CvOk(a: parsed_value)                 extends convention_value
  final case class CvBad(a: validation_failure, b: expr) extends convention_value
  final case class CvUnknown(a: expr)                    extends convention_value

  sealed abstract class convention_rule
  final case class ConventionRuleFull(
      a: String,
      b: String,
      c: Option[String],
      d: convention_value,
      e: Option[span_t]
  ) extends convention_rule

  sealed abstract class conventions_decl
  final case class ConventionsDeclFull(a: List[convention_rule], b: Option[span_t])
      extends conventions_decl

  sealed abstract class type_alias_decl
  final case class TypeAliasDeclFull(a: String, b: type_expr, c: Option[expr], d: Option[span_t])
      extends type_alias_decl

  sealed abstract class transition_rule
  final case class TransitionRuleFull(
      a: String,
      b: String,
      c: String,
      d: Option[expr],
      e: Option[span_t]
  ) extends transition_rule

  sealed abstract class transition_decl
  final case class TransitionDeclFull(
      a: String,
      b: String,
      c: String,
      d: List[transition_rule],
      e: Option[span_t]
  ) extends transition_decl

  sealed abstract class predicate_decl
  final case class PredicateDeclFull(a: String, b: List[param_decl], c: expr, d: Option[span_t])
      extends predicate_decl

  sealed abstract class operation_decl
  final case class OperationDeclFull(
      a: String,
      b: List[param_decl],
      c: List[param_decl],
      d: List[expr],
      e: List[expr],
      f: Option[List[String]],
      g: Option[span_t]
  ) extends operation_decl

  sealed abstract class invariant_decl
  final case class InvariantDeclFull(a: Option[String], b: expr, c: Option[span_t])
      extends invariant_decl

  sealed abstract class temporal_body
  final case class TbAlways(a: expr)     extends temporal_body
  final case class TbEventually(a: expr) extends temporal_body
  final case class TbFairness(a: expr)   extends temporal_body
  final case class TbInvalid(a: expr)    extends temporal_body

  sealed abstract class temporal_decl
  final case class TemporalDeclFull(a: String, b: temporal_body, c: Option[span_t])
      extends temporal_decl

  sealed abstract class function_decl
  final case class FunctionDeclFull(
      a: String,
      b: List[param_decl],
      c: type_expr,
      d: expr,
      e: Option[span_t]
  ) extends function_decl

  sealed abstract class entity_decl
  final case class EntityDeclFull(
      a: String,
      b: Option[String],
      c: List[field_decl],
      d: List[expr],
      e: Option[span_t]
  ) extends entity_decl

  sealed abstract class state_field_decl
  final case class StateFieldDeclFull(a: String, b: type_expr, c: Option[span_t])
      extends state_field_decl

  sealed abstract class state_decl
  final case class StateDeclFull(a: List[state_field_decl], b: Option[span_t])
      extends state_decl

  sealed abstract class service_ir
  final case class ServiceIRFull(
      a: String,
      b: List[String],
      c: List[entity_decl],
      d: List[enum_decl],
      e: List[type_alias_decl],
      f: Option[state_decl],
      g: List[operation_decl],
      h: List[transition_decl],
      i: List[invariant_decl],
      j: List[temporal_decl],
      k: List[fact_decl],
      l: List[function_decl],
      m: List[predicate_decl],
      n: Option[conventions_decl],
      o: List[security_scheme_decl],
      p: Option[span_t]
  ) extends service_ir

  sealed abstract class schema_type
  final case class BoolT()                                   extends schema_type
  final case class IntT()                                    extends schema_type
  final case class RealT()                                   extends schema_type
  final case class StrT()                                    extends schema_type
  final case class EnumT(a: String)                          extends schema_type
  final case class EntityT(a: String)                        extends schema_type
  final case class RelationT(a: schema_type, b: schema_type) extends schema_type
  final case class OptionT(a: schema_type)                   extends schema_type
  final case class SeqT(a: schema_type)                      extends schema_type
  final case class MapT(a: schema_type, b: schema_type)      extends schema_type

  sealed abstract class column_spec
  final case class ColumnSpec(a: String, b: String, c: Boolean, d: Option[String])
      extends column_spec

  sealed abstract class table_spec
  final case class TableSpec(
      a: String,
      b: String,
      c: List[column_spec],
      d: String,
      e: List[foreign_key_spec],
      f: List[String],
      g: List[index_spec]
  ) extends table_spec

  sealed abstract class ir_value
  final case class VBool(a: Boolean)                                extends ir_value
  final case class VInt(a: BigInt)                                  extends ir_value
  final case class VReal(a: rat)                                    extends ir_value
  final case class VEnum(a: String, b: String)                      extends ir_value
  final case class VEntity(a: String, b: String)                    extends ir_value
  final case class VSet(a: List[ir_value])                          extends ir_value
  final case class VEntityWith(a: ir_value, b: String, c: ir_value) extends ir_value
  final case class VNone()                                          extends ir_value
  final case class VSome(a: ir_value)                               extends ir_value
  final case class VStr(a: String)                                  extends ir_value
  final case class VSeq(a: List[ir_value])                          extends ir_value
  final case class VMap(a: List[(ir_value, ir_value)])              extends ir_value

  sealed abstract class scalar_cmp
  final case class ScGt()  extends scalar_cmp
  final case class ScGe()  extends scalar_cmp
  final case class ScLt()  extends scalar_cmp
  final case class ScLe()  extends scalar_cmp
  final case class ScEq()  extends scalar_cmp
  final case class ScNeq() extends scalar_cmp

  sealed abstract class scalar_rhs
  final case class SrLit(a: BigInt)                    extends scalar_rhs
  final case class SrSelf()                            extends scalar_rhs
  final case class SrAdd(a: scalar_rhs, b: scalar_rhs) extends scalar_rhs
  final case class SrSub(a: scalar_rhs, b: scalar_rhs) extends scalar_rhs
  final case class SrMul(a: scalar_rhs, b: scalar_rhs) extends scalar_rhs

  sealed abstract class http_method
  final case class GET()    extends http_method
  final case class POST()   extends http_method
  final case class PUT()    extends http_method
  final case class PATCH()  extends http_method
  final case class DELETE() extends http_method

  sealed abstract class with_info
  final case class WithInfoFull(a: List[String], b: Option[String]) extends with_info

  sealed abstract class route_kind
  final case class RkCreate()   extends route_kind
  final case class RkRead()     extends route_kind
  final case class RkList()     extends route_kind
  final case class RkDelete()   extends route_kind
  final case class RkRedirect() extends route_kind
  final case class RkOther()    extends route_kind

  sealed abstract class ident_ctx
  final case class IdentCtx(
      a: List[String],
      b: List[String],
      c: Option[String],
      d: List[String],
      e: List[String],
      f: List[String],
      g: List[String],
      h: List[String],
      i: List[String]
  ) extends ident_ctx

  sealed abstract class scalar_guard
  final case class SgTrue()                                   extends scalar_guard
  final case class SgCmp(a: String, b: scalar_cmp, c: BigInt) extends scalar_guard

  sealed abstract class sa_type
  final case class SaType(a: String, b: Option[String]) extends sa_type

  sealed abstract class lit_class
  final case class LcNumeric()    extends lit_class
  final case class LcBool()       extends lit_class
  final case class LcStringLike() extends lit_class
  final case class LcCollection() extends lit_class
  final case class LcNone()       extends lit_class

  sealed abstract class operation_kind
  final case class Create()        extends operation_kind
  final case class Read()          extends operation_kind
  final case class Replace()       extends operation_kind
  final case class PartialUpdate() extends operation_kind
  final case class Deletea()       extends operation_kind
  final case class CreateChild()   extends operation_kind
  final case class FilteredRead()  extends operation_kind
  final case class SideEffect()    extends operation_kind
  final case class BatchMutation() extends operation_kind
  final case class Transition()    extends operation_kind

  sealed abstract class database_schema
  final case class DatabaseSchema(a: List[table_spec], b: List[trigger_spec])
      extends database_schema

  sealed abstract class ident_class
  final case class IcReserved()      extends ident_class
  final case class IcBound()         extends ident_class
  final case class IcBareBody()      extends ident_class
  final case class IcOutput()        extends ident_class
  final case class IcInput()         extends ident_class
  final case class IcStateField()    extends ident_class
  final case class IcUnbackedState() extends ident_class
  final case class IcEnumType()      extends ident_class
  final case class IcEnumValue()     extends ident_class
  final case class IcUnbound()       extends ident_class

  sealed abstract class alloy_field_multiplicity
  final case class AfmOne()  extends alloy_field_multiplicity
  final case class AfmLone() extends alloy_field_multiplicity
  final case class AfmSome() extends alloy_field_multiplicity
  final case class AfmSet()  extends alloy_field_multiplicity

  sealed abstract class alloy_field
  final case class AlloyFieldLifted(a: String, b: alloy_field_multiplicity, c: String)
      extends alloy_field

  sealed abstract class alloy_sig
  final case class AlloySigLifted(
      a: String,
      b: Boolean,
      c: Boolean,
      d: Option[String],
      e: List[alloy_field]
  ) extends alloy_sig

  sealed abstract class column_kind
  final case class CkPrim(a: String)       extends column_kind
  final case class CkEnum(a: List[String]) extends column_kind
  final case class CkEntityRef(a: String)  extends column_kind
  final case class CkJsonArray()           extends column_kind
  final case class CkJsonObject()          extends column_kind
  final case class CkRelation()            extends column_kind
  final case class CkUnknown()             extends column_kind

  sealed abstract class analysis_signals
  final case class AnalysisSignals(
      a: List[String],
      b: List[String],
      c: Boolean,
      d: Boolean,
      e: Option[nat],
      f: Option[nat],
      g: nat,
      h: Boolean,
      i: Boolean
  ) extends analysis_signals

  sealed abstract class migration_op
  final case class CreateTable(a: table_spec)            extends migration_op
  final case class DropTable(a: table_spec)              extends migration_op
  final case class AddColumn(a: String, b: column_spec)  extends migration_op
  final case class DropColumn(a: String, b: column_spec) extends migration_op
  final case class AlterColumnType(a: String, b: String, c: String, d: String)
      extends migration_op
  final case class AlterColumnNullable(a: String, b: String, c: Boolean, d: Boolean)
      extends migration_op
  final case class AlterColumnDefault(a: String, b: String, c: Option[String], d: Option[String])
      extends migration_op
  final case class AddCheck(a: String, b: String, c: String)      extends migration_op
  final case class DropCheck(a: String, b: String, c: String)     extends migration_op
  final case class AddForeignKey(a: String, b: foreign_key_spec)  extends migration_op
  final case class DropForeignKey(a: String, b: foreign_key_spec) extends migration_op
  final case class AddIndex(a: String, b: index_spec)             extends migration_op
  final case class DropIndex(a: String, b: index_spec)            extends migration_op
  final case class AddTrigger(a: trigger_spec)                    extends migration_op
  final case class DropTrigger(a: trigger_spec)                   extends migration_op

  sealed abstract class state_schema_ext[A]
  final case class state_schema_exta[A](a: List[(String, ty)], b: A) extends state_schema_ext[A]

  sealed abstract class tyctx_ext[A]
  final case class tyctx_exta[A](
      a: List[(String, ty)],
      b: state_schema_ext[Unit],
      c: List[entity_decl],
      d: List[state_field_decl],
      e: List[String],
      f: A
  ) extends tyctx_ext[A]

  sealed abstract class int_constraint
  final case class IntConstraint(a: Option[BigInt], b: Option[BigInt], c: List[String])
      extends int_constraint

  sealed abstract class extern_kind
  final case class EkPredicate()   extends extern_kind
  final case class EkIntFunction() extends extern_kind

  sealed abstract class extern_info
  final case class ExInfo(a: extern_kind, b: BigInt) extends extern_info

  sealed abstract class extern_item
  final case class EiExtern(a: String, b: BigInt, c: extern_kind) extends extern_item
  final case class EiPattern(a: String)                           extends extern_item

  sealed abstract class dialect_caps
  final case class DialectCaps(
      a: Boolean,
      b: Boolean,
      c: Boolean,
      d: Boolean,
      e: Boolean,
      f: Boolean
  ) extends dialect_caps

  sealed abstract class user_call_class
  final case class UcUnknown()             extends user_call_class
  final case class UcWrongArity(a: BigInt) extends user_call_class
  final case class UcOk()                  extends user_call_class

  sealed abstract class alloy_unop_shape
  final case class AusNot()         extends alloy_unop_shape
  final case class AusCardinality() extends alloy_unop_shape
  final case class AusMinusZero()   extends alloy_unop_shape
  final case class AusUnsupported() extends alloy_unop_shape

  sealed abstract class synthesis_strategy
  final case class DirectEmit()   extends synthesis_strategy
  final case class LlmSynthesis() extends synthesis_strategy

  sealed abstract class refinement_atom
  final case class RaLenCmp(a: bin_op, b: BigInt)       extends refinement_atom
  final case class RaValueCmp(a: bin_op, b: BigInt)     extends refinement_atom
  final case class RaMatches(a: String)                 extends refinement_atom
  final case class RaMatchesIdent(a: String, b: String) extends refinement_atom
  final case class RaPredCall(a: String)                extends refinement_atom
  final case class RaUnknown(a: expr)                   extends refinement_atom

  sealed abstract class decimal_lit
  final case class DecimalLit(a: BigInt, b: BigInt) extends decimal_lit

  sealed abstract class schema_object_or_bool
  final case class SOBSchema(a: schema_object) extends schema_object_or_bool
  final case class SOBBool(a: Boolean)         extends schema_object_or_bool

  sealed abstract class schema_object
  final case class SchemaObject(
      a: Option[List[String]],
      b: Option[String],
      c: Option[BigInt],
      d: Option[BigInt],
      e: Option[decimal_lit],
      f: Option[decimal_lit],
      g: Option[decimal_lit],
      h: Option[decimal_lit],
      i: Option[BigInt],
      j: Option[BigInt],
      k: Option[String],
      l: Option[List[String]],
      m: Option[schema_object],
      n: Option[String],
      o: Option[List[String]],
      p: Option[List[(String, schema_object)]],
      q: Option[schema_object_or_bool],
      r: Option[List[schema_object]],
      s: Option[String],
      t: Boolean
  ) extends schema_object

  sealed abstract class aggregate_call
  final case class AggregateCall(a: String, b: trigger_aggregate, c: Option[String])
      extends aggregate_call

  sealed abstract class smt_model_ext[A]
  final case class smt_model_exta[A](
      a: List[(String, List[String])],
      b: List[(String, smt_val)],
      c: List[(String, List[smt_val])],
      d: List[(String, List[(smt_val, smt_val)])],
      e: List[(String, List[(String, smt_val)])],
      f: A
  ) extends smt_model_ext[A]

  sealed abstract class alloy_binop_shape
  final case class AbsLogical(a: String)    extends alloy_binop_shape
  final case class AbsInfix(a: String)      extends alloy_binop_shape
  final case class AbsPrefixCall(a: String) extends alloy_binop_shape

  sealed abstract class canonical_type
  final case class CtText()                                extends canonical_type
  final case class CtVarchar(a: BigInt)                    extends canonical_type
  final case class CtInt4()                                extends canonical_type
  final case class CtSerial4()                             extends canonical_type
  final case class CtInt8()                                extends canonical_type
  final case class CtSerial8()                             extends canonical_type
  final case class CtFloat8()                              extends canonical_type
  final case class CtBool()                                extends canonical_type
  final case class CtTimestamptz()                         extends canonical_type
  final case class CtDateOnly()                            extends canonical_type
  final case class CtUuid()                                extends canonical_type
  final case class CtNumeric(a: BigInt, b: Option[BigInt]) extends canonical_type
  final case class CtBytes()                               extends canonical_type
  final case class CtJson()                                extends canonical_type

  sealed abstract class string_constraint
  final case class StringConstraint(
      a: Option[BigInt],
      b: Option[BigInt],
      c: List[String],
      d: List[String],
      e: List[String]
  ) extends string_constraint

  sealed abstract class trust_level
  final case class TlSound()      extends trust_level
  final case class TlBestEffort() extends trust_level

  sealed abstract class classification_result
  final case class ClassificationResult(
      a: operation_kind,
      b: http_method,
      c: String,
      d: analysis_signals
  ) extends classification_result

  sealed abstract class type_mismatch_kind
  final case class TmUnaryNotOnNonBool(a: lit_class)              extends type_mismatch_kind
  final case class TmUnaryNegOnNonNumeric(a: lit_class)           extends type_mismatch_kind
  final case class TmArithLitMisuse(a: bin_op, b: lit_class)      extends type_mismatch_kind
  final case class TmCompareLitMisuse(a: bin_op, b: lit_class)    extends type_mismatch_kind
  final case class TmLogicalLitMisuse(a: bin_op, b: lit_class)    extends type_mismatch_kind
  final case class TmMembershipLitMisuse(a: bin_op, b: lit_class) extends type_mismatch_kind

  sealed abstract class classified_column
  final case class ClassifiedColumn(a: column_kind, b: Boolean) extends classified_column

  sealed abstract class trigger_candidate
  final case class TriggerCandidate(
      a: String,
      b: String,
      c: String,
      d: String,
      e: trigger_aggregate,
      f: Option[String]
  ) extends trigger_candidate

  sealed abstract class verifier_tool
  final case class VtZ3()    extends verifier_tool
  final case class VtAlloy() extends verifier_tool

  sealed abstract class nullable_decision
  final case class NdNoop()          extends nullable_decision
  final case class NdWrapAnyOfNull() extends nullable_decision
  final case class NdAppendNull()    extends nullable_decision

  sealed abstract class column_check_class
  final case class CcSkip()                              extends column_check_class
  final case class CcRegexMatch(a: String)               extends column_check_class
  final case class CcLenCompare(a: bin_op, b: BigInt)    extends column_check_class
  final case class CcValueCompare(a: bin_op, b: BigInt)  extends column_check_class
  final case class CcLenLitCompare(a: bin_op, b: expr)   extends column_check_class
  final case class CcValueLitCompare(a: bin_op, b: expr) extends column_check_class

  sealed abstract class detected_aggregate
  final case class DetectedAggregate(a: String, b: String, c: trigger_aggregate, d: Option[String])
      extends detected_aggregate

  sealed abstract class alloy_identifier_kind
  final case class AikBoundVar()   extends alloy_identifier_kind
  final case class AikStateField() extends alloy_identifier_kind
  final case class AikInputField() extends alloy_identifier_kind
  final case class AikPlain()      extends alloy_identifier_kind

  sealed abstract class alloy_quantifier_class
  final case class AqAll()    extends alloy_quantifier_class
  final case class AqSome()   extends alloy_quantifier_class
  final case class AqExists() extends alloy_quantifier_class
  final case class AqNo()     extends alloy_quantifier_class

  sealed abstract class operation_classification
  final case class OperationClassification(
      a: String,
      b: operation_kind,
      c: http_method,
      d: String,
      e: Option[String],
      f: synthesis_strategy,
      g: analysis_signals
  ) extends operation_classification

  sealed abstract class openapi_bounds
  final case class OpenApiBounds(
      a: Option[BigInt],
      b: Option[BigInt],
      c: Option[decimal_lit],
      d: Option[decimal_lit],
      e: Option[decimal_lit],
      f: Option[decimal_lit],
      g: Option[String]
  ) extends openapi_bounds

  sealed abstract class invariant_check_class
  final case class IcSkip()                                 extends invariant_check_class
  final case class IcInClause(a: String, b: List[expr])     extends invariant_check_class
  final case class IcCompare(a: String, b: bin_op, c: expr) extends invariant_check_class

  sealed abstract class openapi_primitive_def
  final case class OpenApiPrimDef(a: List[String], b: Option[String]) extends openapi_primitive_def

  sealed abstract class openapi_named_kind
  final case class OntPrimitive(a: openapi_primitive_def) extends openapi_named_kind
  final case class OntEnum(a: List[String])               extends openapi_named_kind
  final case class OntEntityRef(a: String)                extends openapi_named_kind
  final case class OntAliasToType(a: type_expr)           extends openapi_named_kind
  final case class OntUnknown()                           extends openapi_named_kind

  sealed abstract class structural_ineligibility
  final case class SceReferencesPrePrime()   extends structural_ineligibility
  final case class SceReferencesStateField() extends structural_ineligibility
  final case class SceReferencesNoOutput()   extends structural_ineligibility

  sealed abstract class dfs_state_ext[A]
  final case class dfs_state_exta[A](
      a: List[String],
      b: List[String],
      c: List[String],
      d: List[List[String]],
      e: List[List[String]],
      f: A
  ) extends dfs_state_ext[A]

  sealed abstract class convention_ir_diagnostic
  final case class PartialIndexFieldMissing(a: String, b: String) extends convention_ir_diagnostic
  final case class TestStrategyFieldMissing(a: String, b: String, c: String)
      extends convention_ir_diagnostic

  sealed abstract class alloy_binding_identifier_resolution
  final case class AbirEntity(a: String) extends alloy_binding_identifier_resolution
  final case class AbirEnum(a: String)   extends alloy_binding_identifier_resolution
  final case class AbirStateOrInput()    extends alloy_binding_identifier_resolution
  final case class AbirPlain()           extends alloy_binding_identifier_resolution

  def id[A]: A => A = (x: A) => x

  def max[A: ord](a: A, b: A): A =
    less_eq[A](a, b) match {
      case true  => b
      case false => a
    }

  def nat_of_integer(k: BigInt): nat = Nata(max[BigInt](BigInt(0), k))

  def comp[A, B, C](f: A => B, g: C => A): C => B = (x: C) => f(g(x))

  def nat: BigInt => nat =
    comp[BigInt, nat, BigInt]((a: BigInt) => nat_of_integer(a), (a: BigInt) => a)

  def integer_of_nat(x0: nat): BigInt = x0 match {
    case Nata(x) => x
  }

  def plus_nat(m: nat, n: nat): nat =
    Nata(integer_of_nat(m) + integer_of_nat(n))

  def one_nat: nat = Nata(BigInt(1))

  def Suc(n: nat): nat = plus_nat(n, one_nat)

  def fold[A, B](f: A => B => B, x1: List[A], s: B): B = (f, x1, s) match {
    case (f, Nil, s)     => s
    case (f, x :: xs, s) => fold[A, B](f, xs, f(x)(s))
  }

  def rev[A](xs: List[A]): List[A] =
    fold[A, List[A]]((a: A) => (b: List[A]) => a :: b, xs, Nil)

  def spanOf(x0: expr): Option[span_t] = x0 match {
    case BinaryOpF(uu, uv, uw, sp)         => sp
    case UnaryOpF(ux, uy, sp)              => sp
    case QuantifierF(uz, va, vb, sp)       => sp
    case SomeWrapF(vc, sp)                 => sp
    case TheF(vd, ve, vf, sp)              => sp
    case FieldAccessF(vg, vh, sp)          => sp
    case EnumAccessF(vi, vj, sp)           => sp
    case IndexF(vk, vl, sp)                => sp
    case CallF(vm, vn, sp)                 => sp
    case PrimeF(vo, sp)                    => sp
    case PreF(vp, sp)                      => sp
    case WithF(vq, vr, sp)                 => sp
    case IfF(vs, vt, vu, sp)               => sp
    case LetF(vv, vw, vx, sp)              => sp
    case LambdaF(vy, vz, sp)               => sp
    case ConstructorF(wa, wb, sp)          => sp
    case SetLiteralF(wc, sp)               => sp
    case MapLiteralF(wd, sp)               => sp
    case SetComprehensionF(we, wf, wg, sp) => sp
    case SeqLiteralF(wh, sp)               => sp
    case MatchesF(wi, wj, sp)              => sp
    case IntLitF(wk, sp)                   => sp
    case FloatLitF(wl, sp)                 => sp
    case StringLitF(wm, sp)                => sp
    case BoolLitF(wn, sp)                  => sp
    case NoneLitF(sp)                      => sp
    case IdentifierF(wo, sp)               => sp
  }

  def minus_nat(m: nat, n: nat): nat =
    Nata(max[BigInt](BigInt(0), integer_of_nat(m) - integer_of_nat(n)))

  def equal_nat(m: nat, n: nat): Boolean =
    integer_of_nat(m) == integer_of_nat(n)

  def zero_nat: nat = Nata(BigInt(0))

  def drop[A](n: nat, x1: List[A]): List[A] = (n, x1) match {
    case (n, Nil) => Nil
    case (n, x :: xs) =>
      equal_nat(n, zero_nat) match {
        case true  => x :: xs
        case false => drop[A](minus_nat(n, one_nat), xs)
      }
  }

  def find[A](uu: A => Boolean, x1: List[A]): Option[A] = (uu, x1) match {
    case (uu, Nil) => None
    case (p, x :: xs) =>
      p(x) match {
        case true  => Some[A](x)
        case false => find[A](p, xs)
      }
  }

  def maps[A, B](f: A => List[B], x1: List[A]): List[B] = (f, x1) match {
    case (f, Nil)     => Nil
    case (f, x :: xs) => f(x) ++ maps[A, B](f, xs)
  }

  def nulla[A](x0: List[A]): Boolean = x0 match {
    case Nil     => true
    case x :: xs => false
  }

  def take[A](n: nat, x1: List[A]): List[A] = (n, x1) match {
    case (n, Nil) => Nil
    case (n, x :: xs) =>
      equal_nat(n, zero_nat) match {
        case true  => Nil
        case false => x :: take[A](minus_nat(n, one_nat), xs)
      }
  }

  def dom_arg(x0: expr): Option[String] = x0 match {
    case CallF(c, args, uu) =>
      (c, args) match {
        case (BinaryOpF(_, _, _, _), _)                              => None
        case (UnaryOpF(_, _, _), _)                                  => None
        case (QuantifierF(_, _, _, _), _)                            => None
        case (SomeWrapF(_, _), _)                                    => None
        case (TheF(_, _, _, _), _)                                   => None
        case (FieldAccessF(_, _, _), _)                              => None
        case (EnumAccessF(_, _, _), _)                               => None
        case (IndexF(_, _, _), _)                                    => None
        case (CallF(_, _, _), _)                                     => None
        case (PrimeF(_, _), _)                                       => None
        case (PreF(_, _), _)                                         => None
        case (WithF(_, _, _), _)                                     => None
        case (IfF(_, _, _, _), _)                                    => None
        case (LetF(_, _, _, _), _)                                   => None
        case (LambdaF(_, _, _), _)                                   => None
        case (ConstructorF(_, _, _), _)                              => None
        case (SetLiteralF(_, _), _)                                  => None
        case (MapLiteralF(_, _), _)                                  => None
        case (SetComprehensionF(_, _, _, _), _)                      => None
        case (SeqLiteralF(_, _), _)                                  => None
        case (MatchesF(_, _, _), _)                                  => None
        case (IntLitF(_, _), _)                                      => None
        case (FloatLitF(_, _), _)                                    => None
        case (StringLitF(_, _), _)                                   => None
        case (BoolLitF(_, _), _)                                     => None
        case (NoneLitF(_), _)                                        => None
        case (IdentifierF(_, _), Nil)                                => None
        case (IdentifierF(_, _), BinaryOpF(_, _, _, _) :: _)         => None
        case (IdentifierF(_, _), UnaryOpF(_, _, _) :: _)             => None
        case (IdentifierF(_, _), QuantifierF(_, _, _, _) :: _)       => None
        case (IdentifierF(_, _), SomeWrapF(_, _) :: _)               => None
        case (IdentifierF(_, _), TheF(_, _, _, _) :: _)              => None
        case (IdentifierF(_, _), FieldAccessF(_, _, _) :: _)         => None
        case (IdentifierF(_, _), EnumAccessF(_, _, _) :: _)          => None
        case (IdentifierF(_, _), IndexF(_, _, _) :: _)               => None
        case (IdentifierF(_, _), CallF(_, _, _) :: _)                => None
        case (IdentifierF(_, _), PrimeF(_, _) :: _)                  => None
        case (IdentifierF(_, _), PreF(_, _) :: _)                    => None
        case (IdentifierF(_, _), WithF(_, _, _) :: _)                => None
        case (IdentifierF(_, _), IfF(_, _, _, _) :: _)               => None
        case (IdentifierF(_, _), LetF(_, _, _, _) :: _)              => None
        case (IdentifierF(_, _), LambdaF(_, _, _) :: _)              => None
        case (IdentifierF(_, _), ConstructorF(_, _, _) :: _)         => None
        case (IdentifierF(_, _), SetLiteralF(_, _) :: _)             => None
        case (IdentifierF(_, _), MapLiteralF(_, _) :: _)             => None
        case (IdentifierF(_, _), SetComprehensionF(_, _, _, _) :: _) => None
        case (IdentifierF(_, _), SeqLiteralF(_, _) :: _)             => None
        case (IdentifierF(_, _), MatchesF(_, _, _) :: _)             => None
        case (IdentifierF(_, _), IntLitF(_, _) :: _)                 => None
        case (IdentifierF(_, _), FloatLitF(_, _) :: _)               => None
        case (IdentifierF(_, _), StringLitF(_, _) :: _)              => None
        case (IdentifierF(_, _), BoolLitF(_, _) :: _)                => None
        case (IdentifierF(_, _), NoneLitF(_) :: _)                   => None
        case (IdentifierF(d, _), List(IdentifierF(x, _))) =>
          d == "dom" match {
            case true  => Some[String](x)
            case false => None
          }
        case (IdentifierF(_, _), IdentifierF(_, _) :: _ :: _) => None
      }
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def foldl[A, B](f: A => B => A, a: A, x2: List[B]): A = (f, a, x2) match {
    case (f, a, Nil)     => a
    case (f, a, x :: xs) => foldl[A, B](f, f(a)(x), xs)
  }

  def map_of[A: equal, B](x0: List[(A, B)], k: A): Option[B] = (x0, k) match {
    case (Nil, k) => None
    case ((l, v) :: ps, k) =>
      eq[A](l, k) match {
        case true  => Some[B](v)
        case false => map_of[A, B](ps, k)
      }
  }

  def of_int(a: BigInt): rat = Frct((a, one_inta))

  def membera[A: equal](x0: List[A], y: A): Boolean = (x0, y) match {
    case (Nil, y)     => false
    case (x :: xs, y) => eq[A](x, y) || membera[A](xs, y)
  }

  def member[A: equal](x: A, xa1: set[A]): Boolean = (x, xa1) match {
    case (x, seta(xs))  => membera[A](xs, x)
    case (x, coset(xs)) => !membera[A](xs, x)
  }

  def subexprs_bindings(x0: List[quantifier_binding]): List[expr] = x0 match {
    case Nil                                        => Nil
    case QuantifierBindingFull(wo, d, wp, wq) :: bs => d :: subexprs_bindings(bs)
  }

  def subexprs_entries(x0: List[map_entry]): List[expr] = x0 match {
    case Nil                          => Nil
    case MapEntryFull(k, v, wn) :: es => k :: v :: subexprs_entries(es)
  }

  def subexprs_fields(x0: List[field_assign]): List[expr] = x0 match {
    case Nil                              => Nil
    case FieldAssignFull(wl, v, wm) :: fs => v :: subexprs_fields(fs)
  }

  def subexprs(x0: expr): List[expr] = x0 match {
    case BinaryOpF(uu, l, r, uv)         => List(l, r)
    case UnaryOpF(uw, e, ux)             => List(e)
    case QuantifierF(uy, bs, body, uz)   => subexprs_bindings(bs) ++ List(body)
    case SomeWrapF(e, va)                => List(e)
    case TheF(vb, d, b, vc)              => List(d, b)
    case FieldAccessF(b, vd, ve)         => List(b)
    case EnumAccessF(b, vf, vg)          => List(b)
    case IndexF(b, i, vh)                => List(b, i)
    case CallF(c, args, vi)              => c :: args
    case PrimeF(e, vj)                   => List(e)
    case PreF(e, vk)                     => List(e)
    case WithF(b, ups, vl)               => b :: subexprs_fields(ups)
    case IfF(c, t, e, vm)                => List(c, t, e)
    case LetF(vn, v, b, vo)              => List(v, b)
    case LambdaF(vp, b, vq)              => List(b)
    case ConstructorF(vr, fs, vs)        => subexprs_fields(fs)
    case SetLiteralF(xs, vt)             => xs
    case MapLiteralF(es, vu)             => subexprs_entries(es)
    case SetComprehensionF(vv, d, p, vw) => List(d, p)
    case SeqLiteralF(xs, vx)             => xs
    case MatchesF(e, vy, vz)             => List(e)
    case IntLitF(wa, wb)                 => Nil
    case FloatLitF(wc, wd)               => Nil
    case StringLitF(we, wf)              => Nil
    case BoolLitF(wg, wh)                => Nil
    case NoneLitF(wi)                    => Nil
    case IdentifierF(wj, wk)             => Nil
  }

  def filter[A](p: A => Boolean, x1: List[A]): List[A] = (p, x1) match {
    case (p, Nil) => Nil
    case (p, x :: xs) =>
      p(x) match {
        case true  => x :: filter[A](p, xs)
        case false => filter[A](p, xs)
      }
  }

  def apsnd[A, B, C](f: A => B, x1: (C, A)): (C, B) = (f, x1) match {
    case (f, (x, y)) => (x, f(y))
  }

  def divmod_integer(k: BigInt, l: BigInt): (BigInt, BigInt) =
    k == BigInt(0) match {
      case true => (BigInt(0), BigInt(0))
      case false => BigInt(0) < l match {
          case true => BigInt(0) < k match {
              case true => ((k: BigInt) =>
                  (l: BigInt) =>
                    l == 0 match {
                      case true  => (BigInt(0), k)
                      case false => k.abs /% l.abs
                    }).apply(k).apply(l)
              case false =>
                val (r, s) =
                  ((k: BigInt) =>
                    (l: BigInt) =>
                      l == 0 match {
                        case true  => (BigInt(0), k)
                        case false => k.abs /% l.abs
                      }).apply(k).apply(l): ((BigInt, BigInt));
                s == BigInt(0) match {
                  case true  => (-r, BigInt(0))
                  case false => ((-r) - BigInt(1), l - s)
                }
            }
          case false => l == BigInt(0) match {
              case true => (BigInt(0), k)
              case false => apsnd[BigInt, BigInt, BigInt](
                  (a: BigInt) => -a,
                  k < BigInt(0) match {
                    case true => ((k: BigInt) =>
                        (l: BigInt) =>
                          l == 0 match {
                            case true  => (BigInt(0), k)
                            case false => k.abs /% l.abs
                          }).apply(k).apply(l)
                    case false =>
                      val (r, s) =
                        ((k: BigInt) =>
                          (l: BigInt) =>
                            l == 0 match {
                              case true  => (BigInt(0), k)
                              case false => k.abs /% l.abs
                            }).apply(k).apply(l): ((BigInt, BigInt));
                      s == BigInt(0) match {
                        case true  => (-r, BigInt(0))
                        case false => ((-r) - BigInt(1), (-l) - s)
                      }
                  }
                )
            }
        }
    }

  def fst[A, B](x0: (A, B)): A = x0 match {
    case (x1, x2) => x1
  }

  def divide_integer(k: BigInt, l: BigInt): BigInt =
    fst[BigInt, BigInt](divmod_integer(k, l))

  def divide_int(k: BigInt, l: BigInt): BigInt = divide_integer(k, l)

  def sm_sort_members[A](x0: smt_model_ext[A]): List[(String, List[String])] =
    x0 match {
      case smt_model_exta(
            sm_sort_members,
            sm_const_vals,
            sm_pred_domain,
            sm_pred_lookup,
            sm_pred_fields,
            more
          ) => sm_sort_members
    }

  def smt_model_lookup_sort_members(
      m: smt_model_ext[Unit],
      sort_name: String
  ): Option[List[String]] =
    map_of[String, List[String]](sm_sort_members[Unit](m), sort_name)

  def uminus_int(k: BigInt): BigInt = -k

  def uminus_rat(p: rat): rat =
    Frct {
      val (a, b) = quotient_of(p): ((BigInt, BigInt));
      (uminus_int(a), b)
    }

  def zero_int: BigInt = BigInt(0)

  def less_int(k: BigInt, l: BigInt): Boolean = k < l

  def gcd_int(x0: BigInt, x1: BigInt): BigInt = (x0, x1) match {
    case (x, y) => x.gcd(y)
  }

  def snd[A, B](x0: (A, B)): B = x0 match {
    case (x1, x2) => x2
  }

  def normalize(p: (BigInt, BigInt)): (BigInt, BigInt) =
    less_int(zero_int, snd[BigInt, BigInt](p)) match {
      case true =>
        val a =
          gcd_int(fst[BigInt, BigInt](p), snd[BigInt, BigInt](p)): BigInt;
        (divide_int(fst[BigInt, BigInt](p), a), divide_int(snd[BigInt, BigInt](p), a))
      case false => equal_int(snd[BigInt, BigInt](p), zero_int) match {
          case true => (zero_int, one_inta)
          case false =>
            val a =
              uminus_int(gcd_int(fst[BigInt, BigInt](p), snd[BigInt, BigInt](p))): BigInt;
            (divide_int(fst[BigInt, BigInt](p), a), divide_int(snd[BigInt, BigInt](p), a))
        }
    }

  def divide_rat(p: rat, q: rat): rat =
    Frct {
      val (a, c) = quotient_of(p): ((BigInt, BigInt))
      val (b, d) = quotient_of(q): ((BigInt, BigInt));
      normalize((times_inta(a, d), times_inta(c, b)))
    }

  def length_tailrec[A](x0: List[A], n: nat): nat = (x0, n) match {
    case (Nil, n)     => n
    case (x :: xs, n) => length_tailrec[A](xs, Suc(n))
  }

  def size_list[A](xs: List[A]): nat = length_tailrec[A](xs, zero_nat)

  def times_rat(p: rat, q: rat): rat =
    Frct {
      val (a, c) = quotient_of(p): ((BigInt, BigInt))
      val (b, d) = quotient_of(q): ((BigInt, BigInt));
      normalize((times_inta(a, b), times_inta(c, d)))
    }

  def minus_int(k: BigInt, l: BigInt): BigInt = k - l

  def minus_rat(p: rat, q: rat): rat =
    Frct {
      val (a, c) = quotient_of(p): ((BigInt, BigInt))
      val (b, d) = quotient_of(q): ((BigInt, BigInt));
      normalize((minus_int(times_inta(a, d), times_inta(b, c)), times_inta(c, d)))
    }

  def sm_const_vals[A](x0: smt_model_ext[A]): List[(String, smt_val)] = x0 match {
    case smt_model_exta(
          sm_sort_members,
          sm_const_vals,
          sm_pred_domain,
          sm_pred_lookup,
          sm_pred_fields,
          more
        ) => sm_const_vals
  }

  def smt_model_lookup_const(m: smt_model_ext[Unit], name: String): Option[smt_val] =
    map_of[String, smt_val](sm_const_vals[Unit](m), name)

  def contains_smt_val(x0: List[smt_val], v: smt_val): Boolean = (x0, v) match {
    case (Nil, v)     => false
    case (x :: xs, v) => equal_smt_vala(x, v) || contains_smt_val(xs, v)
  }

  def dedupe_smt_vals(x0: List[smt_val]): List[smt_val] = x0 match {
    case Nil => Nil
    case x :: xs =>
      val rest = dedupe_smt_vals(xs): List[smt_val];
      contains_smt_val(rest, x) match {
        case true  => rest
        case false => x :: rest
      }
  }

  def set_intersect_smt_vals(l: List[smt_val], r: List[smt_val]): List[smt_val] =
    dedupe_smt_vals(filter[smt_val]((a: smt_val) => contains_smt_val(r, a), l))

  def zero_rat: rat = Frct((zero_int, one_inta))

  def plus_int(k: BigInt, l: BigInt): BigInt = k + l

  def plus_rat(p: rat, q: rat): rat =
    Frct {
      val (a, c) = quotient_of(p): ((BigInt, BigInt))
      val (b, d) = quotient_of(q): ((BigInt, BigInt));
      normalize((plus_int(times_inta(a, d), times_inta(b, c)), times_inta(c, d)))
    }

  def int_of_nat(n: nat): BigInt = integer_of_nat(n)

  def less_rat(p: rat, q: rat): Boolean = {
    val (a, c) = quotient_of(p): ((BigInt, BigInt))
    val (b, d) = quotient_of(q): ((BigInt, BigInt));
    less_int(times_inta(a, d), times_inta(c, b))
  }

  def sm_pred_fields[A](x0: smt_model_ext[A]): List[(String, List[(String, smt_val)])] =
    x0 match {
      case smt_model_exta(
            sm_sort_members,
            sm_const_vals,
            sm_pred_domain,
            sm_pred_lookup,
            sm_pred_fields,
            more
          ) => sm_pred_fields
    }

  def smt_model_lookup_field(
      m: smt_model_ext[Unit],
      entity_id: String,
      field_name: String
  ): Option[smt_val] =
    map_of[String, List[(String, smt_val)]](sm_pred_fields[Unit](m), entity_id) match {
      case None     => None
      case Some(fs) => map_of[String, smt_val](fs, field_name)
    }

  def smt_val_field_lookup(m: smt_model_ext[Unit], x1: smt_val, fld: String): Option[smt_val] =
    (m, x1, fld) match {
      case (m, SEntityElem(uu, eid), fld) => smt_model_lookup_field(m, eid, fld)
      case (m, SEntityWith(base, ov_fld, ov_val), fld) =>
        fld == ov_fld match {
          case true  => Some[smt_val](ov_val)
          case false => smt_val_field_lookup(m, base, fld)
        }
      case (uv, SBool(v), ux)         => None
      case (uv, SInt(v), ux)          => None
      case (uv, SReal(v), ux)         => None
      case (uv, SEnumElem(v, va), ux) => None
      case (uv, SSet(v), ux)          => None
      case (uv, SNone(), ux)          => None
      case (uv, SSome(v), ux)         => None
      case (uv, SStr(v), ux)          => None
      case (uv, SSeq(v), ux)          => None
      case (uv, SMap(v), ux)          => None
    }

  def sm_pred_domain[A](x0: smt_model_ext[A]): List[(String, List[smt_val])] =
    x0 match {
      case smt_model_exta(
            sm_sort_members,
            sm_const_vals,
            sm_pred_domain,
            sm_pred_lookup,
            sm_pred_fields,
            more
          ) => sm_pred_domain
    }

  def smt_model_lookup_rel(m: smt_model_ext[Unit], name: String): Option[List[smt_val]] =
    map_of[String, List[smt_val]](sm_pred_domain[Unit](m), name)

  def sm_pred_lookup[A](x0: smt_model_ext[A]): List[(String, List[(smt_val, smt_val)])] =
    x0 match {
      case smt_model_exta(
            sm_sort_members,
            sm_const_vals,
            sm_pred_domain,
            sm_pred_lookup,
            sm_pred_fields,
            more
          ) => sm_pred_lookup
    }

  def map_option[A, B](f: A => B, x1: Option[A]): Option[B] = (f, x1) match {
    case (f, None)     => None
    case (f, Some(x2)) => Some[B](f(x2))
  }

  def smt_model_lookup_key(
      m: smt_model_ext[Unit],
      rel_name: String,
      key: smt_val
  ): Option[smt_val] =
    map_of[String, List[(smt_val, smt_val)]](sm_pred_lookup[Unit](m), rel_name) match {
      case None => None
      case Some(pairs) =>
        map_option[(smt_val, smt_val), smt_val](
          (a: (smt_val, smt_val)) =>
            snd[smt_val, smt_val](a),
          find[(smt_val, smt_val)](
            (p: (smt_val, smt_val)) =>
              equal_smt_vala(fst[smt_val, smt_val](p), key),
            pairs
          )
        )
    }

  def set_union_smt_vals(l: List[smt_val], r: List[smt_val]): List[smt_val] =
    dedupe_smt_vals(l ++ r)

  def relRefVarName(x0: smt_term): Option[String] = x0 match {
    case TVar(rel)              => Some[String](rel)
    case BLit(v)                => None
    case ILit(v)                => None
    case RLit(v)                => None
    case EnumElemConst(v, va)   => None
    case TNot(v)                => None
    case TAnd(v, va)            => None
    case TOr(v, va)             => None
    case TImplies(v, va)        => None
    case TEq(v, va)             => None
    case TLt(v, va)             => None
    case TNeg(v)                => None
    case TAdd(v, va)            => None
    case TSub(v, va)            => None
    case TMul(v, va)            => None
    case TDiv(v, va)            => None
    case TInDom(v, va)          => None
    case TCardRel(v)            => None
    case TCard(v)               => None
    case TLetIn(v, va, vb)      => None
    case TForallEnum(v, va, vb) => None
    case TForallRel(v, va, vb)  => None
    case TExistsRel(v, va, vb)  => None
    case TTheRel(v, va, vb)     => None
    case TEntityBase(v)         => None
    case TForallSet(v, va, vb)  => None
    case TTheSet(v, va, vb)     => None
    case TIndexRel(v, va)       => None
    case TFieldAccess(v, va)    => None
    case TSetEmpty()            => None
    case TSetInsert(v, va)      => None
    case TSetMember(v, va)      => None
    case TSetUnion(v, va)       => None
    case TSetIntersect(v, va)   => None
    case TSetDiff(v, va)        => None
    case TPrime(v)              => None
    case TPre(v)                => None
    case TWithRec(v, va, vb)    => None
    case TIte(v, va, vb)        => None
    case TNone()                => None
    case TSome(v)               => None
    case TStrLit(v)             => None
    case TMatches(v, va)        => None
    case TUStrPred(v, va)       => None
    case TUStrFunc(v, va)       => None
    case TUIntFunc(v, va)       => None
    case TStrLen(v)             => None
    case TUConst(v)             => None
    case TSeqEmpty()            => None
    case TSeqCons(v, va)        => None
    case TMapEmpty()            => None
    case TMapCons(v, va, vb)    => None
    case TSum(v, va)            => None
  }

  def peelSmtRelationRef(x0: smt_term): Option[String] = x0 match {
    case TVar(rel)              => Some[String](rel)
    case TPre(t)                => relRefVarName(t)
    case TPrime(t)              => relRefVarName(t)
    case BLit(v)                => None
    case ILit(v)                => None
    case RLit(v)                => None
    case EnumElemConst(v, va)   => None
    case TNot(v)                => None
    case TAnd(v, va)            => None
    case TOr(v, va)             => None
    case TImplies(v, va)        => None
    case TEq(v, va)             => None
    case TLt(v, va)             => None
    case TNeg(v)                => None
    case TAdd(v, va)            => None
    case TSub(v, va)            => None
    case TMul(v, va)            => None
    case TDiv(v, va)            => None
    case TInDom(v, va)          => None
    case TCardRel(v)            => None
    case TCard(v)               => None
    case TLetIn(v, va, vb)      => None
    case TForallEnum(v, va, vb) => None
    case TForallRel(v, va, vb)  => None
    case TExistsRel(v, va, vb)  => None
    case TTheRel(v, va, vb)     => None
    case TEntityBase(v)         => None
    case TForallSet(v, va, vb)  => None
    case TTheSet(v, va, vb)     => None
    case TIndexRel(v, va)       => None
    case TFieldAccess(v, va)    => None
    case TSetEmpty()            => None
    case TSetInsert(v, va)      => None
    case TSetMember(v, va)      => None
    case TSetUnion(v, va)       => None
    case TSetIntersect(v, va)   => None
    case TSetDiff(v, va)        => None
    case TWithRec(v, va, vb)    => None
    case TIte(v, va, vb)        => None
    case TNone()                => None
    case TSome(v)               => None
    case TStrLit(v)             => None
    case TMatches(v, va)        => None
    case TUStrPred(v, va)       => None
    case TUStrFunc(v, va)       => None
    case TUIntFunc(v, va)       => None
    case TStrLen(v)             => None
    case TUConst(v)             => None
    case TSeqEmpty()            => None
    case TSeqCons(v, va)        => None
    case TMapEmpty()            => None
    case TMapCons(v, va, vb)    => None
    case TSum(v, va)            => None
  }

  def set_diff_smt_vals(l: List[smt_val], r: List[smt_val]): List[smt_val] =
    dedupe_smt_vals(filter[smt_val]((v: smt_val) => !contains_smt_val(r, v), l))

  def smt_env_lookup(env: List[(String, smt_val)], name: String): Option[smt_val] =
    map_of[String, smt_val](env, name)

  def list_all[A](p: A => Boolean, x1: List[A]): Boolean = (p, x1) match {
    case (p, Nil)     => true
    case (p, x :: xs) => p(x) && list_all[A](p, xs)
  }

  def smtEval_forall_enum(
      m: smt_model_ext[Unit],
      env: List[(String, smt_val)],
      vara: String,
      sort_name: String,
      x4: List[String],
      body: smt_term
  ): Option[smt_val] =
    (m, env, vara, sort_name, x4, body) match {
      case (m, env, vara, sort_name, Nil, body) => Some[smt_val](SBool(true))
      case (m, env, vara, sort_name, mem :: rest, body) =>
        smtEval(m, (vara, SEnumElem(sort_name, mem)) :: env, body) match {
          case None => None
          case Some(SBool(b)) =>
            smtEval_forall_enum(m, env, vara, sort_name, rest, body) match {
              case None                       => None
              case Some(SBool(acc))           => Some[smt_val](SBool(b && acc))
              case Some(SInt(_))              => None
              case Some(SReal(_))             => None
              case Some(SEnumElem(_, _))      => None
              case Some(SEntityElem(_, _))    => None
              case Some(SSet(_))              => None
              case Some(SEntityWith(_, _, _)) => None
              case Some(SNone())              => None
              case Some(SSome(_))             => None
              case Some(SStr(_))              => None
              case Some(SSeq(_))              => None
              case Some(SMap(_))              => None
            }
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
    }

  def smtEval_forall_rel(
      m: smt_model_ext[Unit],
      env: List[(String, smt_val)],
      vara: String,
      x3: List[smt_val],
      body: smt_term
  ): Option[smt_val] =
    (m, env, vara, x3, body) match {
      case (m, env, vara, Nil, body) => Some[smt_val](SBool(true))
      case (m, env, vara, v :: rest, body) =>
        smtEval(m, (vara, v) :: env, body) match {
          case None => None
          case Some(SBool(b)) =>
            smtEval_forall_rel(m, env, vara, rest, body) match {
              case None                       => None
              case Some(SBool(acc))           => Some[smt_val](SBool(b && acc))
              case Some(SInt(_))              => None
              case Some(SReal(_))             => None
              case Some(SEnumElem(_, _))      => None
              case Some(SEntityElem(_, _))    => None
              case Some(SSet(_))              => None
              case Some(SEntityWith(_, _, _)) => None
              case Some(SNone())              => None
              case Some(SSome(_))             => None
              case Some(SStr(_))              => None
              case Some(SSeq(_))              => None
              case Some(SMap(_))              => None
            }
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
    }

  def smtEval_the_rel(
      m: smt_model_ext[Unit],
      env: List[(String, smt_val)],
      vara: String,
      x3: List[smt_val],
      body: smt_term
  ): Option[List[smt_val]] =
    (m, env, vara, x3, body) match {
      case (m, env, vara, Nil, body) => Some[List[smt_val]](Nil)
      case (m, env, vara, v :: rest, body) =>
        smtEval(m, (vara, v) :: env, body) match {
          case None => None
          case Some(SBool(b)) =>
            smtEval_the_rel(m, env, vara, rest, body) match {
              case None => None
              case Some(matches) =>
                Some[List[smt_val]](b match {
                  case true  => v :: matches
                  case false => matches
                })
            }
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
    }

  def smtEval(m: smt_model_ext[Unit], env: List[(String, smt_val)], x2: smt_term): Option[smt_val] =
    (m, env, x2) match {
      case (m, env, BLit(b)) => Some[smt_val](SBool(b))
      case (m, env, ILit(n)) => Some[smt_val](SInt(n))
      case (m, env, RLit(r)) => Some[smt_val](SReal(r))
      case (m, env, TVar(x)) => smt_env_lookup(env, x) match {
          case None    => smt_model_lookup_const(m, x)
          case Some(a) => Some[smt_val](a)
        }
      case (m, env, EnumElemConst(en, mem)) =>
        smt_model_lookup_sort_members(m, en) match {
          case None => None
          case Some(members) =>
            membera[String](members, mem) match {
              case true  => Some[smt_val](SEnumElem(en, mem))
              case false => None
            }
        }
      case (m, env, TNot(t)) => smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(b))             => Some[smt_val](SBool(!b))
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TAnd(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                    => None
          case (Some(SBool(_)), None)                       => None
          case (Some(SBool(a)), Some(SBool(b)))             => Some[smt_val](SBool(a && b))
          case (Some(SBool(_)), Some(SInt(_)))              => None
          case (Some(SBool(_)), Some(SReal(_)))             => None
          case (Some(SBool(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SBool(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SBool(_)), Some(SSet(_)))              => None
          case (Some(SBool(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SBool(_)), Some(SNone()))              => None
          case (Some(SBool(_)), Some(SSome(_)))             => None
          case (Some(SBool(_)), Some(SStr(_)))              => None
          case (Some(SBool(_)), Some(SSeq(_)))              => None
          case (Some(SBool(_)), Some(SMap(_)))              => None
          case (Some(SInt(_)), _)                           => None
          case (Some(SReal(_)), _)                          => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), _)                           => None
          case (Some(SSeq(_)), _)                           => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TOr(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                    => None
          case (Some(SBool(_)), None)                       => None
          case (Some(SBool(a)), Some(SBool(b)))             => Some[smt_val](SBool(a || b))
          case (Some(SBool(_)), Some(SInt(_)))              => None
          case (Some(SBool(_)), Some(SReal(_)))             => None
          case (Some(SBool(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SBool(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SBool(_)), Some(SSet(_)))              => None
          case (Some(SBool(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SBool(_)), Some(SNone()))              => None
          case (Some(SBool(_)), Some(SSome(_)))             => None
          case (Some(SBool(_)), Some(SStr(_)))              => None
          case (Some(SBool(_)), Some(SSeq(_)))              => None
          case (Some(SBool(_)), Some(SMap(_)))              => None
          case (Some(SInt(_)), _)                           => None
          case (Some(SReal(_)), _)                          => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), _)                           => None
          case (Some(SSeq(_)), _)                           => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TImplies(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                    => None
          case (Some(SBool(_)), None)                       => None
          case (Some(SBool(a)), Some(SBool(b)))             => Some[smt_val](SBool(!a || b))
          case (Some(SBool(_)), Some(SInt(_)))              => None
          case (Some(SBool(_)), Some(SReal(_)))             => None
          case (Some(SBool(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SBool(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SBool(_)), Some(SSet(_)))              => None
          case (Some(SBool(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SBool(_)), Some(SNone()))              => None
          case (Some(SBool(_)), Some(SSome(_)))             => None
          case (Some(SBool(_)), Some(SStr(_)))              => None
          case (Some(SBool(_)), Some(SSeq(_)))              => None
          case (Some(SBool(_)), Some(SMap(_)))              => None
          case (Some(SInt(_)), _)                           => None
          case (Some(SReal(_)), _)                          => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), _)                           => None
          case (Some(SSeq(_)), _)                           => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TEq(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)              => None
          case (Some(SBool(_)), None) => None
          case (Some(SBool(bool)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SBool(bool), b)))
          case (Some(SInt(_)), None) => None
          case (Some(SInt(a)), Some(SBool(bool))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SBool(bool))))
          case (Some(SInt(a)), Some(SInt(intb))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SInt(intb))))
          case (Some(SInt(a)), Some(SReal(b))) =>
            Some[smt_val](SBool(equal_rat(of_int(a), b)))
          case (Some(SInt(a)), Some(SEnumElem(literal1, literal2))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SEnumElem(literal1, literal2))))
          case (Some(SInt(a)), Some(SEntityElem(literal1, literal2))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SEntityElem(literal1, literal2))))
          case (Some(SInt(a)), Some(SSet(list))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SSet(list))))
          case (Some(SInt(a)), Some(SEntityWith(smt_val1, literal, smt_val2))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SEntityWith(smt_val1, literal, smt_val2))))
          case (Some(SInt(a)), Some(SNone())) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SNone())))
          case (Some(SInt(a)), Some(SSome(smt_val))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SSome(smt_val))))
          case (Some(SInt(a)), Some(SStr(literal))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SStr(literal))))
          case (Some(SInt(a)), Some(SSeq(list))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SSeq(list))))
          case (Some(SInt(a)), Some(SMap(list))) =>
            Some[smt_val](SBool(equal_smt_vala(SInt(a), SMap(list))))
          case (Some(SReal(_)), None) => None
          case (Some(SReal(a)), Some(SBool(bool))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SBool(bool))))
          case (Some(SReal(a)), Some(SInt(b))) =>
            Some[smt_val](SBool(equal_rat(a, of_int(b))))
          case (Some(SReal(a)), Some(SReal(ratb))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SReal(ratb))))
          case (Some(SReal(a)), Some(SEnumElem(literal1, literal2))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SEnumElem(literal1, literal2))))
          case (Some(SReal(a)), Some(SEntityElem(literal1, literal2))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SEntityElem(literal1, literal2))))
          case (Some(SReal(a)), Some(SSet(list))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SSet(list))))
          case (Some(SReal(a)), Some(SEntityWith(smt_val1, literal, smt_val2))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SEntityWith(smt_val1, literal, smt_val2))))
          case (Some(SReal(a)), Some(SNone())) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SNone())))
          case (Some(SReal(a)), Some(SSome(smt_val))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SSome(smt_val))))
          case (Some(SReal(a)), Some(SStr(literal))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SStr(literal))))
          case (Some(SReal(a)), Some(SSeq(list))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SSeq(list))))
          case (Some(SReal(a)), Some(SMap(list))) =>
            Some[smt_val](SBool(equal_smt_vala(SReal(a), SMap(list))))
          case (Some(SEnumElem(_, _)), None) => None
          case (Some(SEnumElem(literal1, literal2)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SEnumElem(literal1, literal2), b)))
          case (Some(SEntityElem(_, _)), None) => None
          case (Some(SEntityElem(literal1, literal2)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SEntityElem(literal1, literal2), b)))
          case (Some(SSet(_)), None) => None
          case (Some(SSet(list)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SSet(list), b)))
          case (Some(SEntityWith(_, _, _)), None) => None
          case (Some(SEntityWith(smt_val1, literal, smt_val2)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SEntityWith(smt_val1, literal, smt_val2), b)))
          case (Some(SNone()), None) => None
          case (Some(SNone()), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SNone(), b)))
          case (Some(SSome(_)), None) => None
          case (Some(SSome(smt_val)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SSome(smt_val), b)))
          case (Some(SStr(_)), None) => None
          case (Some(SStr(literal)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SStr(literal), b)))
          case (Some(SSeq(_)), None) => None
          case (Some(SSeq(list)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SSeq(list), b)))
          case (Some(SMap(_)), None) => None
          case (Some(SMap(list)), Some(b)) =>
            Some[smt_val](SBool(equal_smt_vala(SMap(list), b)))
        }
      case (m, env, TLt(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SBool(less_int(a, b)))
          case (Some(SInt(a)), Some(SReal(b))) =>
            Some[smt_val](SBool(less_rat(of_int(a), b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), Some(SNone()))              => None
          case (Some(SInt(_)), Some(SSome(_)))             => None
          case (Some(SInt(_)), Some(SStr(_)))              => None
          case (Some(SInt(_)), Some(SSeq(_)))              => None
          case (Some(SInt(_)), Some(SMap(_)))              => None
          case (Some(SReal(_)), None)                      => None
          case (Some(SReal(_)), Some(SBool(_)))            => None
          case (Some(SReal(a)), Some(SInt(b))) =>
            Some[smt_val](SBool(less_rat(a, of_int(b))))
          case (Some(SReal(a)), Some(SReal(b))) =>
            Some[smt_val](SBool(less_rat(a, b)))
          case (Some(SReal(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SReal(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SReal(_)), Some(SSet(_)))              => None
          case (Some(SReal(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SReal(_)), Some(SNone()))              => None
          case (Some(SReal(_)), Some(SSome(_)))             => None
          case (Some(SReal(_)), Some(SStr(_)))              => None
          case (Some(SReal(_)), Some(SSeq(_)))              => None
          case (Some(SReal(_)), Some(SMap(_)))              => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), None)                        => None
          case (Some(SStr(_)), Some(SBool(_)))              => None
          case (Some(SStr(_)), Some(SInt(_)))               => None
          case (Some(SStr(_)), Some(SReal(_)))              => None
          case (Some(SStr(_)), Some(SEnumElem(_, _)))       => None
          case (Some(SStr(_)), Some(SEntityElem(_, _)))     => None
          case (Some(SStr(_)), Some(SSet(_)))               => None
          case (Some(SStr(_)), Some(SEntityWith(_, _, _)))  => None
          case (Some(SStr(_)), Some(SNone()))               => None
          case (Some(SStr(_)), Some(SSome(_)))              => None
          case (Some(SStr(a)), Some(SStr(b)))               => Some[smt_val](SBool(a < b))
          case (Some(SStr(_)), Some(SSeq(_)))               => None
          case (Some(SStr(_)), Some(SMap(_)))               => None
          case (Some(SSeq(_)), _)                           => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TNeg(t)) =>
        smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(n))              => Some[smt_val](SInt(uminus_int(n)))
          case Some(SReal(n))             => Some[smt_val](SReal(uminus_rat(n)))
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TAdd(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SInt(plus_int(a, b)))
          case (Some(SInt(a)), Some(SReal(b))) =>
            Some[smt_val](SReal(plus_rat(of_int(a), b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), Some(SNone()))              => None
          case (Some(SInt(_)), Some(SSome(_)))             => None
          case (Some(SInt(_)), Some(SStr(_)))              => None
          case (Some(SInt(_)), Some(SSeq(_)))              => None
          case (Some(SInt(_)), Some(SMap(_)))              => None
          case (Some(SReal(_)), None)                      => None
          case (Some(SReal(_)), Some(SBool(_)))            => None
          case (Some(SReal(a)), Some(SInt(b))) =>
            Some[smt_val](SReal(plus_rat(a, of_int(b))))
          case (Some(SReal(a)), Some(SReal(b))) =>
            Some[smt_val](SReal(plus_rat(a, b)))
          case (Some(SReal(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SReal(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SReal(_)), Some(SSet(_)))              => None
          case (Some(SReal(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SReal(_)), Some(SNone()))              => None
          case (Some(SReal(_)), Some(SSome(_)))             => None
          case (Some(SReal(_)), Some(SStr(_)))              => None
          case (Some(SReal(_)), Some(SSeq(_)))              => None
          case (Some(SReal(_)), Some(SMap(_)))              => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), None)                        => None
          case (Some(SStr(_)), Some(SBool(_)))              => None
          case (Some(SStr(_)), Some(SInt(_)))               => None
          case (Some(SStr(_)), Some(SReal(_)))              => None
          case (Some(SStr(_)), Some(SEnumElem(_, _)))       => None
          case (Some(SStr(_)), Some(SEntityElem(_, _)))     => None
          case (Some(SStr(_)), Some(SSet(_)))               => None
          case (Some(SStr(_)), Some(SEntityWith(_, _, _)))  => None
          case (Some(SStr(_)), Some(SNone()))               => None
          case (Some(SStr(_)), Some(SSome(_)))              => None
          case (Some(SStr(a)), Some(SStr(b)))               => Some[smt_val](SStr(a + b))
          case (Some(SStr(_)), Some(SSeq(_)))               => None
          case (Some(SStr(_)), Some(SMap(_)))               => None
          case (Some(SSeq(_)), None)                        => None
          case (Some(SSeq(_)), Some(SBool(_)))              => None
          case (Some(SSeq(_)), Some(SInt(_)))               => None
          case (Some(SSeq(_)), Some(SReal(_)))              => None
          case (Some(SSeq(_)), Some(SEnumElem(_, _)))       => None
          case (Some(SSeq(_)), Some(SEntityElem(_, _)))     => None
          case (Some(SSeq(_)), Some(SSet(_)))               => None
          case (Some(SSeq(_)), Some(SEntityWith(_, _, _)))  => None
          case (Some(SSeq(_)), Some(SNone()))               => None
          case (Some(SSeq(_)), Some(SSome(_)))              => None
          case (Some(SSeq(_)), Some(SStr(_)))               => None
          case (Some(SSeq(a)), Some(SSeq(b)))               => Some[smt_val](SSeq(a ++ b))
          case (Some(SSeq(_)), Some(SMap(_)))               => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TSub(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SInt(minus_int(a, b)))
          case (Some(SInt(a)), Some(SReal(b))) =>
            Some[smt_val](SReal(minus_rat(of_int(a), b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), Some(SNone()))              => None
          case (Some(SInt(_)), Some(SSome(_)))             => None
          case (Some(SInt(_)), Some(SStr(_)))              => None
          case (Some(SInt(_)), Some(SSeq(_)))              => None
          case (Some(SInt(_)), Some(SMap(_)))              => None
          case (Some(SReal(_)), None)                      => None
          case (Some(SReal(_)), Some(SBool(_)))            => None
          case (Some(SReal(a)), Some(SInt(b))) =>
            Some[smt_val](SReal(minus_rat(a, of_int(b))))
          case (Some(SReal(a)), Some(SReal(b))) =>
            Some[smt_val](SReal(minus_rat(a, b)))
          case (Some(SReal(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SReal(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SReal(_)), Some(SSet(_)))              => None
          case (Some(SReal(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SReal(_)), Some(SNone()))              => None
          case (Some(SReal(_)), Some(SSome(_)))             => None
          case (Some(SReal(_)), Some(SStr(_)))              => None
          case (Some(SReal(_)), Some(SSeq(_)))              => None
          case (Some(SReal(_)), Some(SMap(_)))              => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), _)                           => None
          case (Some(SSeq(_)), _)                           => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TMul(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SInt(times_inta(a, b)))
          case (Some(SInt(a)), Some(SReal(b))) =>
            Some[smt_val](SReal(times_rat(of_int(a), b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), Some(SNone()))              => None
          case (Some(SInt(_)), Some(SSome(_)))             => None
          case (Some(SInt(_)), Some(SStr(_)))              => None
          case (Some(SInt(_)), Some(SSeq(_)))              => None
          case (Some(SInt(_)), Some(SMap(_)))              => None
          case (Some(SReal(_)), None)                      => None
          case (Some(SReal(_)), Some(SBool(_)))            => None
          case (Some(SReal(a)), Some(SInt(b))) =>
            Some[smt_val](SReal(times_rat(a, of_int(b))))
          case (Some(SReal(a)), Some(SReal(b))) =>
            Some[smt_val](SReal(times_rat(a, b)))
          case (Some(SReal(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SReal(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SReal(_)), Some(SSet(_)))              => None
          case (Some(SReal(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SReal(_)), Some(SNone()))              => None
          case (Some(SReal(_)), Some(SSome(_)))             => None
          case (Some(SReal(_)), Some(SStr(_)))              => None
          case (Some(SReal(_)), Some(SSeq(_)))              => None
          case (Some(SReal(_)), Some(SMap(_)))              => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), _)                           => None
          case (Some(SSeq(_)), _)                           => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TDiv(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            equal_int(b, zero_int) match {
              case true  => None
              case false => Some[smt_val](SInt(divide_int(a, b)))
            }
          case (Some(SInt(a)), Some(SReal(b))) =>
            equal_rat(b, zero_rat) match {
              case true  => None
              case false => Some[smt_val](SReal(divide_rat(of_int(a), b)))
            }
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), Some(SNone()))              => None
          case (Some(SInt(_)), Some(SSome(_)))             => None
          case (Some(SInt(_)), Some(SStr(_)))              => None
          case (Some(SInt(_)), Some(SSeq(_)))              => None
          case (Some(SInt(_)), Some(SMap(_)))              => None
          case (Some(SReal(_)), None)                      => None
          case (Some(SReal(_)), Some(SBool(_)))            => None
          case (Some(SReal(a)), Some(SInt(b))) =>
            equal_int(b, zero_int) match {
              case true  => None
              case false => Some[smt_val](SReal(divide_rat(a, of_int(b))))
            }
          case (Some(SReal(a)), Some(SReal(b))) =>
            equal_rat(b, zero_rat) match {
              case true  => None
              case false => Some[smt_val](SReal(divide_rat(a, b)))
            }
          case (Some(SReal(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SReal(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SReal(_)), Some(SSet(_)))              => None
          case (Some(SReal(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SReal(_)), Some(SNone()))              => None
          case (Some(SReal(_)), Some(SSome(_)))             => None
          case (Some(SReal(_)), Some(SStr(_)))              => None
          case (Some(SReal(_)), Some(SSeq(_)))              => None
          case (Some(SReal(_)), Some(SMap(_)))              => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
          case (Some(SNone()), _)                           => None
          case (Some(SSome(_)), _)                          => None
          case (Some(SStr(_)), _)                           => None
          case (Some(SSeq(_)), _)                           => None
          case (Some(SMap(_)), _)                           => None
        }
      case (m, env, TInDom(rel_name, arg)) =>
        smtEval(m, env, arg) match {
          case None => None
          case Some(v) =>
            smt_model_lookup_rel(m, rel_name) match {
              case None    => None
              case Some(d) => Some[smt_val](SBool(contains_smt_val(d, v)))
            }
        }
      case (m, env, TCardRel(rel_name)) =>
        smt_model_lookup_rel(m, rel_name) match {
          case None    => None
          case Some(d) => Some[smt_val](SInt(int_of_nat(size_list[smt_val](d))))
        }
      case (m, env, TCard(t)) =>
        smtEval(m, env, t) match {
          case None                    => None
          case Some(SBool(_))          => None
          case Some(SInt(_))           => None
          case Some(SReal(_))          => None
          case Some(SEnumElem(_, _))   => None
          case Some(SEntityElem(_, _)) => None
          case Some(SSet(xs)) =>
            Some[smt_val](SInt(int_of_nat(size_list[smt_val](xs))))
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TLetIn(x, v, body)) =>
        smtEval(m, env, v) match {
          case None     => None
          case Some(va) => smtEval(m, (x, va) :: env, body)
        }
      case (m, env, TForallEnum(vara, sort_name, body)) =>
        smt_model_lookup_sort_members(m, sort_name) match {
          case None => None
          case Some(members) =>
            smtEval_forall_enum(m, env, vara, sort_name, members, body)
        }
      case (m, env, TForallRel(vara, rel_name, body)) =>
        smt_model_lookup_rel(m, rel_name) match {
          case None    => None
          case Some(d) => smtEval_forall_rel(m, env, vara, d, body)
        }
      case (m, env, TExistsRel(vara, rel_name, body)) =>
        smt_model_lookup_rel(m, rel_name) match {
          case None => None
          case Some(d) =>
            smtEval_the_rel(m, env, vara, d, body) match {
              case None => None
              case Some(matches) =>
                Some[smt_val](SBool(!nulla[smt_val](matches)))
            }
        }
      case (m, env, TTheRel(vara, rel_name, body)) =>
        smt_model_lookup_rel(m, rel_name) match {
          case None => None
          case Some(d) =>
            smtEval_the_rel(m, env, vara, d, body) match {
              case None      => None
              case Some(Nil) => None
              case Some(x :: rest) =>
                list_all[smt_val]((y: smt_val) => equal_smt_vala(y, x), rest) match {
                  case true  => Some[smt_val](x)
                  case false => None
                }
            }
        }
      case (m, env, TEntityBase(name)) => Some[smt_val](SEntityElem(name, ""))
      case (m, env, TForallSet(vara, setT, body)) =>
        smtEval(m, env, setT) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(elems))          => smtEval_forall_rel(m, env, vara, elems, body)
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TTheSet(vara, setT, body)) =>
        smtEval(m, env, setT) match {
          case None                    => None
          case Some(SBool(_))          => None
          case Some(SInt(_))           => None
          case Some(SReal(_))          => None
          case Some(SEnumElem(_, _))   => None
          case Some(SEntityElem(_, _)) => None
          case Some(SSet(elems)) =>
            smtEval_the_rel(m, env, vara, elems, body) match {
              case None      => None
              case Some(Nil) => None
              case Some(x :: rest) =>
                list_all[smt_val]((y: smt_val) => equal_smt_vala(y, x), rest) match {
                  case true  => Some[smt_val](x)
                  case false => None
                }
            }
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TIndexRel(base, key)) =>
        (peelSmtRelationRef(base), smtEval(m, env, key)) match {
          case (None, _)            => None
          case (Some(_), None)      => None
          case (Some(rel), Some(a)) => smt_model_lookup_key(m, rel, a)
        }
      case (m, env, TFieldAccess(base, fname)) =>
        smtEval(m, env, base) match {
          case None    => None
          case Some(v) => smt_val_field_lookup(m, v, fname)
        }
      case (m, env, TSetEmpty()) => Some[smt_val](SSet(Nil))
      case (m, env, TSetInsert(elem, set_t)) =>
        (smtEval(m, env, elem), smtEval(m, env, set_t)) match {
          case (None, _)                          => None
          case (Some(_), None)                    => None
          case (Some(_), Some(SBool(_)))          => None
          case (Some(_), Some(SInt(_)))           => None
          case (Some(_), Some(SReal(_)))          => None
          case (Some(_), Some(SEnumElem(_, _)))   => None
          case (Some(_), Some(SEntityElem(_, _))) => None
          case (Some(v), Some(SSet(members))) =>
            Some[smt_val](SSet(dedupe_smt_vals(v :: members)))
          case (Some(_), Some(SEntityWith(_, _, _))) => None
          case (Some(_), Some(SNone()))              => None
          case (Some(_), Some(SSome(_)))             => None
          case (Some(_), Some(SStr(_)))              => None
          case (Some(_), Some(SSeq(_)))              => None
          case (Some(_), Some(SMap(_)))              => None
        }
      case (m, env, TSetMember(elem, set_t)) =>
        (smtEval(m, env, elem), smtEval(m, env, set_t)) match {
          case (None, _)                          => None
          case (Some(_), None)                    => None
          case (Some(_), Some(SBool(_)))          => None
          case (Some(_), Some(SInt(_)))           => None
          case (Some(_), Some(SReal(_)))          => None
          case (Some(_), Some(SEnumElem(_, _)))   => None
          case (Some(_), Some(SEntityElem(_, _))) => None
          case (Some(v), Some(SSet(members))) =>
            Some[smt_val](SBool(contains_smt_val(members, v)))
          case (Some(_), Some(SEntityWith(_, _, _))) => None
          case (Some(_), Some(SNone()))              => None
          case (Some(_), Some(SSome(_)))             => None
          case (Some(_), Some(SStr(_)))              => None
          case (Some(_), Some(SSeq(_)))              => None
          case (Some(_), Some(SMap(_)))              => None
        }
      case (m, env, TSetUnion(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                => None
          case (Some(SBool(_)), _)                      => None
          case (Some(SInt(_)), _)                       => None
          case (Some(SReal(_)), _)                      => None
          case (Some(SEnumElem(_, _)), _)               => None
          case (Some(SEntityElem(_, _)), _)             => None
          case (Some(SSet(_)), None)                    => None
          case (Some(SSet(_)), Some(SBool(_)))          => None
          case (Some(SSet(_)), Some(SInt(_)))           => None
          case (Some(SSet(_)), Some(SReal(_)))          => None
          case (Some(SSet(_)), Some(SEnumElem(_, _)))   => None
          case (Some(SSet(_)), Some(SEntityElem(_, _))) => None
          case (Some(SSet(a)), Some(SSet(b))) =>
            Some[smt_val](SSet(set_union_smt_vals(a, b)))
          case (Some(SSet(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SSet(_)), Some(SNone()))              => None
          case (Some(SSet(_)), Some(SSome(_)))             => None
          case (Some(SSet(_)), Some(SStr(_)))              => None
          case (Some(SSet(_)), Some(SSeq(_)))              => None
          case (Some(SSet(_)), Some(SMap(_)))              => None
          case (Some(SEntityWith(_, _, _)), _)             => None
          case (Some(SNone()), _)                          => None
          case (Some(SSome(_)), _)                         => None
          case (Some(SStr(_)), _)                          => None
          case (Some(SSeq(_)), _)                          => None
          case (Some(SMap(_)), _)                          => None
        }
      case (m, env, TSetIntersect(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                => None
          case (Some(SBool(_)), _)                      => None
          case (Some(SInt(_)), _)                       => None
          case (Some(SReal(_)), _)                      => None
          case (Some(SEnumElem(_, _)), _)               => None
          case (Some(SEntityElem(_, _)), _)             => None
          case (Some(SSet(_)), None)                    => None
          case (Some(SSet(_)), Some(SBool(_)))          => None
          case (Some(SSet(_)), Some(SInt(_)))           => None
          case (Some(SSet(_)), Some(SReal(_)))          => None
          case (Some(SSet(_)), Some(SEnumElem(_, _)))   => None
          case (Some(SSet(_)), Some(SEntityElem(_, _))) => None
          case (Some(SSet(a)), Some(SSet(b))) =>
            Some[smt_val](SSet(set_intersect_smt_vals(a, b)))
          case (Some(SSet(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SSet(_)), Some(SNone()))              => None
          case (Some(SSet(_)), Some(SSome(_)))             => None
          case (Some(SSet(_)), Some(SStr(_)))              => None
          case (Some(SSet(_)), Some(SSeq(_)))              => None
          case (Some(SSet(_)), Some(SMap(_)))              => None
          case (Some(SEntityWith(_, _, _)), _)             => None
          case (Some(SNone()), _)                          => None
          case (Some(SSome(_)), _)                         => None
          case (Some(SStr(_)), _)                          => None
          case (Some(SSeq(_)), _)                          => None
          case (Some(SMap(_)), _)                          => None
        }
      case (m, env, TSetDiff(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                => None
          case (Some(SBool(_)), _)                      => None
          case (Some(SInt(_)), _)                       => None
          case (Some(SReal(_)), _)                      => None
          case (Some(SEnumElem(_, _)), _)               => None
          case (Some(SEntityElem(_, _)), _)             => None
          case (Some(SSet(_)), None)                    => None
          case (Some(SSet(_)), Some(SBool(_)))          => None
          case (Some(SSet(_)), Some(SInt(_)))           => None
          case (Some(SSet(_)), Some(SReal(_)))          => None
          case (Some(SSet(_)), Some(SEnumElem(_, _)))   => None
          case (Some(SSet(_)), Some(SEntityElem(_, _))) => None
          case (Some(SSet(a)), Some(SSet(b))) =>
            Some[smt_val](SSet(set_diff_smt_vals(a, b)))
          case (Some(SSet(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SSet(_)), Some(SNone()))              => None
          case (Some(SSet(_)), Some(SSome(_)))             => None
          case (Some(SSet(_)), Some(SStr(_)))              => None
          case (Some(SSet(_)), Some(SSeq(_)))              => None
          case (Some(SSet(_)), Some(SMap(_)))              => None
          case (Some(SEntityWith(_, _, _)), _)             => None
          case (Some(SNone()), _)                          => None
          case (Some(SSome(_)), _)                         => None
          case (Some(SStr(_)), _)                          => None
          case (Some(SSeq(_)), _)                          => None
          case (Some(SMap(_)), _)                          => None
        }
      case (m, env, TPrime(t)) => smtEval(m, env, t)
      case (m, env, TPre(t))   => smtEval(m, env, t)
      case (m, env, TWithRec(base, fld, value_t)) =>
        (smtEval(m, env, base), smtEval(m, env, value_t)) match {
          case (None, _)           => None
          case (Some(_), None)     => None
          case (Some(bv), Some(v)) => Some[smt_val](SEntityWith(bv, fld, v))
        }
      case (m, env, TIte(c, a, b)) =>
        smtEval(m, env, c) match {
          case None                       => None
          case Some(SBool(true))          => smtEval(m, env, a)
          case Some(SBool(false))         => smtEval(m, env, b)
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TNone()) => Some[smt_val](SNone())
      case (m, env, TSome(t)) =>
        map_option[smt_val, smt_val]((a: smt_val) => SSome(a), smtEval(m, env, t))
      case (m, env, TStrLit(v)) => Some[smt_val](SStr(v))
      case (m, env, TMatches(t, pat)) =>
        smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(str))            => Some[smt_val](SBool(str == pat))
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TUStrPred(name, t)) =>
        smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(str))            => Some[smt_val](SBool(name == str))
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TUStrFunc(name, t)) =>
        smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(str))            => Some[smt_val](SStr(name + str))
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TUIntFunc(name, t)) =>
        smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(n))              => Some[smt_val](SInt(BigInt(name.hashCode) + n))
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(_))              => None
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TStrLen(t)) =>
        smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(_))              => None
          case Some(SReal(_))             => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
          case Some(SNone())              => None
          case Some(SSome(_))             => None
          case Some(SStr(s))              => Some[smt_val](SInt(BigInt(s.length)))
          case Some(SSeq(_))              => None
          case Some(SMap(_))              => None
        }
      case (m, env, TUConst(nm)) => Some[smt_val](SInt(BigInt(nm.hashCode)))
      case (m, env, TSeqEmpty()) => Some[smt_val](SSeq(Nil))
      case (m, env, TSeqCons(e, rest)) =>
        (smtEval(m, env, e), smtEval(m, env, rest)) match {
          case (None, _)                             => None
          case (Some(_), None)                       => None
          case (Some(_), Some(SBool(_)))             => None
          case (Some(_), Some(SInt(_)))              => None
          case (Some(_), Some(SReal(_)))             => None
          case (Some(_), Some(SEnumElem(_, _)))      => None
          case (Some(_), Some(SEntityElem(_, _)))    => None
          case (Some(_), Some(SSet(_)))              => None
          case (Some(_), Some(SEntityWith(_, _, _))) => None
          case (Some(_), Some(SNone()))              => None
          case (Some(_), Some(SSome(_)))             => None
          case (Some(_), Some(SStr(_)))              => None
          case (Some(v), Some(SSeq(vs)))             => Some[smt_val](SSeq(v :: vs))
          case (Some(_), Some(SMap(_)))              => None
        }
      case (m, env, TMapEmpty()) => Some[smt_val](SMap(Nil))
      case (m, env, TMapCons(k, v, rest)) =>
        (smtEval(m, env, k), (smtEval(m, env, v), smtEval(m, env, rest))) match {
          case (None, _)                                        => None
          case (Some(_), (None, _))                             => None
          case (Some(_), (Some(_), None))                       => None
          case (Some(_), (Some(_), Some(SBool(_))))             => None
          case (Some(_), (Some(_), Some(SInt(_))))              => None
          case (Some(_), (Some(_), Some(SReal(_))))             => None
          case (Some(_), (Some(_), Some(SEnumElem(_, _))))      => None
          case (Some(_), (Some(_), Some(SEntityElem(_, _))))    => None
          case (Some(_), (Some(_), Some(SSet(_))))              => None
          case (Some(_), (Some(_), Some(SEntityWith(_, _, _)))) => None
          case (Some(_), (Some(_), Some(SNone())))              => None
          case (Some(_), (Some(_), Some(SSome(_))))             => None
          case (Some(_), (Some(_), Some(SStr(_))))              => None
          case (Some(_), (Some(_), Some(SSeq(_))))              => None
          case (Some(kv), (Some(vv), Some(SMap(ps)))) =>
            Some[smt_val](SMap((kv, vv) :: ps))
        }
      case (m, env, TSum(c, f)) =>
        smtEval(m, env, c) match {
          case None     => None
          case Some(cv) => Some[smt_val](SInt(BigInt(cv.hashCode + f.hashCode)))
        }
    }

  def binOpToTs(x0: bin_op): String = x0 match {
    case BAnd()       => "and"
    case BOr()        => "or"
    case BImplies()   => "implies"
    case BIff()       => "iff"
    case BEq()        => "="
    case BNeq()       => "!="
    case BLt()        => "<"
    case BGt()        => ">"
    case BLe()        => "<="
    case BGe()        => ">="
    case BIn()        => "in"
    case BNotIn()     => "not_in"
    case BSubset()    => "subset"
    case BUnion()     => "union"
    case BIntersect() => "intersect"
    case BDiff()      => "minus"
    case BAdd()       => "+"
    case BSub()       => "-"
    case BMul()       => "*"
    case BDiv()       => "/"
  }

  def identName(x0: expr): Option[String] = x0 match {
    case IdentifierF(rel, uu)             => Some[String](rel)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
  }

  def isLitFull(x0: expr): Boolean = x0 match {
    case BoolLitF(uu, uv)                 => true
    case IntLitF(uw, ux)                  => true
    case FloatLitF(uy, uz)                => true
    case StringLitF(va, vb)               => true
    case NoneLitF(vc)                     => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IdentifierF(v, va)               => false
  }

  def butlast[A](x0: List[A]): List[A] = x0 match {
    case Nil => Nil
    case x :: xs =>
      nulla[A](xs) match {
        case true  => Nil
        case false => x :: butlast[A](xs)
      }
  }

  def list_ex[A](p: A => Boolean, x1: List[A]): Boolean = (p, x1) match {
    case (p, Nil)     => false
    case (p, x :: xs) => p(x) || list_ex[A](p, xs)
  }

  def remdups[A: equal](x0: List[A]): List[A] = x0 match {
    case Nil => Nil
    case x :: xs =>
      membera[A](xs, x) match {
        case true  => remdups[A](xs)
        case false => x :: remdups[A](xs)
      }
  }

  def string_in_list(y: String, x1: List[String]): Boolean = (y, x1) match {
    case (y, Nil)     => false
    case (y, x :: xs) => x == y || string_in_list(y, xs)
  }

  def fresh_in(n: nat, base: String, avoid: List[String]): String =
    equal_nat(n, zero_nat) match {
      case true => base
      case false => string_in_list(base, avoid) match {
          case true  => fresh_in(minus_nat(n, one_nat), base + "_", avoid)
          case false => base
        }
    }

  def flattenAnd(x0: expr): List[expr] = x0 match {
    case BinaryOpF(BAnd(), l, r, uu)  => flattenAnd(l) ++ flattenAnd(r)
    case BinaryOpF(BOr(), va, vb, vc) => List(BinaryOpF(BOr(), va, vb, vc))
    case BinaryOpF(BImplies(), va, vb, vc) =>
      List(BinaryOpF(BImplies(), va, vb, vc))
    case BinaryOpF(BIff(), va, vb, vc)   => List(BinaryOpF(BIff(), va, vb, vc))
    case BinaryOpF(BEq(), va, vb, vc)    => List(BinaryOpF(BEq(), va, vb, vc))
    case BinaryOpF(BNeq(), va, vb, vc)   => List(BinaryOpF(BNeq(), va, vb, vc))
    case BinaryOpF(BLt(), va, vb, vc)    => List(BinaryOpF(BLt(), va, vb, vc))
    case BinaryOpF(BGt(), va, vb, vc)    => List(BinaryOpF(BGt(), va, vb, vc))
    case BinaryOpF(BLe(), va, vb, vc)    => List(BinaryOpF(BLe(), va, vb, vc))
    case BinaryOpF(BGe(), va, vb, vc)    => List(BinaryOpF(BGe(), va, vb, vc))
    case BinaryOpF(BIn(), va, vb, vc)    => List(BinaryOpF(BIn(), va, vb, vc))
    case BinaryOpF(BNotIn(), va, vb, vc) => List(BinaryOpF(BNotIn(), va, vb, vc))
    case BinaryOpF(BSubset(), va, vb, vc) =>
      List(BinaryOpF(BSubset(), va, vb, vc))
    case BinaryOpF(BUnion(), va, vb, vc) => List(BinaryOpF(BUnion(), va, vb, vc))
    case BinaryOpF(BIntersect(), va, vb, vc) =>
      List(BinaryOpF(BIntersect(), va, vb, vc))
    case BinaryOpF(BDiff(), va, vb, vc) => List(BinaryOpF(BDiff(), va, vb, vc))
    case BinaryOpF(BAdd(), va, vb, vc)  => List(BinaryOpF(BAdd(), va, vb, vc))
    case BinaryOpF(BSub(), va, vb, vc)  => List(BinaryOpF(BSub(), va, vb, vc))
    case BinaryOpF(BMul(), va, vb, vc)  => List(BinaryOpF(BMul(), va, vb, vc))
    case BinaryOpF(BDiv(), va, vb, vc)  => List(BinaryOpF(BDiv(), va, vb, vc))
    case UnaryOpF(v, va, vb)            => List(UnaryOpF(v, va, vb))
    case QuantifierF(v, va, vb, vc)     => List(QuantifierF(v, va, vb, vc))
    case SomeWrapF(v, va)               => List(SomeWrapF(v, va))
    case TheF(v, va, vb, vc)            => List(TheF(v, va, vb, vc))
    case FieldAccessF(v, va, vb)        => List(FieldAccessF(v, va, vb))
    case EnumAccessF(v, va, vb)         => List(EnumAccessF(v, va, vb))
    case IndexF(v, va, vb)              => List(IndexF(v, va, vb))
    case CallF(v, va, vb)               => List(CallF(v, va, vb))
    case PrimeF(v, va)                  => List(PrimeF(v, va))
    case PreF(v, va)                    => List(PreF(v, va))
    case WithF(v, va, vb)               => List(WithF(v, va, vb))
    case IfF(v, va, vb, vc)             => List(IfF(v, va, vb, vc))
    case LetF(v, va, vb, vc)            => List(LetF(v, va, vb, vc))
    case LambdaF(v, va, vb)             => List(LambdaF(v, va, vb))
    case ConstructorF(v, va, vb)        => List(ConstructorF(v, va, vb))
    case SetLiteralF(v, va)             => List(SetLiteralF(v, va))
    case MapLiteralF(v, va)             => List(MapLiteralF(v, va))
    case SetComprehensionF(v, va, vb, vc) =>
      List(SetComprehensionF(v, va, vb, vc))
    case SeqLiteralF(v, va)  => List(SeqLiteralF(v, va))
    case MatchesF(v, va, vb) => List(MatchesF(v, va, vb))
    case IntLitF(v, va)      => List(IntLitF(v, va))
    case FloatLitF(v, va)    => List(FloatLitF(v, va))
    case StringLitF(v, va)   => List(StringLitF(v, va))
    case BoolLitF(v, va)     => List(BoolLitF(v, va))
    case NoneLitF(v)         => List(NoneLitF(v))
    case IdentifierF(v, va)  => List(IdentifierF(v, va))
  }

  def stripSpans_bindings(x0: List[quantifier_binding]): List[quantifier_binding] =
    x0 match {
      case Nil => Nil
      case QuantifierBindingFull(v, d, k, vx) :: bs =>
        QuantifierBindingFull(v, stripSpans(d), k, None) :: stripSpans_bindings(bs)
    }

  def stripSpans_entries(x0: List[map_entry]): List[map_entry] = x0 match {
    case Nil => Nil
    case MapEntryFull(k, v, vw) :: es =>
      MapEntryFull(stripSpans(k), stripSpans(v), None) :: stripSpans_entries(es)
  }

  def stripSpans_fields(x0: List[field_assign]): List[field_assign] = x0 match {
    case Nil => Nil
    case FieldAssignFull(n, v, vv) :: fs =>
      FieldAssignFull(n, stripSpans(v), None) :: stripSpans_fields(fs)
  }

  def stripSpans_list(x0: List[expr]): List[expr] = x0 match {
    case Nil     => Nil
    case x :: xs => stripSpans(x) :: stripSpans_list(xs)
  }

  def stripSpans(x0: expr): expr = x0 match {
    case BinaryOpF(op, l, r, uu) =>
      BinaryOpF(op, stripSpans(l), stripSpans(r), None)
    case UnaryOpF(op, e, uv) => UnaryOpF(op, stripSpans(e), None)
    case QuantifierF(k, bs, body, uw) =>
      QuantifierF(k, stripSpans_bindings(bs), stripSpans(body), None)
    case SomeWrapF(e, ux)       => SomeWrapF(stripSpans(e), None)
    case TheF(v, d, b, uy)      => TheF(v, stripSpans(d), stripSpans(b), None)
    case FieldAccessF(b, f, uz) => FieldAccessF(stripSpans(b), f, None)
    case EnumAccessF(b, m, va)  => EnumAccessF(stripSpans(b), m, None)
    case IndexF(b, i, vb)       => IndexF(stripSpans(b), stripSpans(i), None)
    case CallF(c, args, vc)     => CallF(stripSpans(c), stripSpans_list(args), None)
    case PrimeF(e, vd)          => PrimeF(stripSpans(e), None)
    case PreF(e, ve)            => PreF(stripSpans(e), None)
    case WithF(b, ups, vf)      => WithF(stripSpans(b), stripSpans_fields(ups), None)
    case IfF(c, t, e, vg) =>
      IfF(stripSpans(c), stripSpans(t), stripSpans(e), None)
    case LetF(x, v, b, vh)       => LetF(x, stripSpans(v), stripSpans(b), None)
    case LambdaF(p, b, vi)       => LambdaF(p, stripSpans(b), None)
    case ConstructorF(n, fs, vj) => ConstructorF(n, stripSpans_fields(fs), None)
    case SetLiteralF(xs, vk)     => SetLiteralF(stripSpans_list(xs), None)
    case MapLiteralF(es, vl)     => MapLiteralF(stripSpans_entries(es), None)
    case SetComprehensionF(v, d, p, vm) =>
      SetComprehensionF(v, stripSpans(d), stripSpans(p), None)
    case SeqLiteralF(xs, vn)  => SeqLiteralF(stripSpans_list(xs), None)
    case MatchesF(e, pat, vo) => MatchesF(stripSpans(e), pat, None)
    case IntLitF(n, vp)       => IntLitF(n, None)
    case FloatLitF(v, vq)     => FloatLitF(v, None)
    case StringLitF(v, vr)    => StringLitF(v, None)
    case BoolLitF(b, vs)      => BoolLitF(b, None)
    case NoneLitF(vt)         => NoneLitF(None)
    case IdentifierF(x, vu)   => IdentifierF(x, None)
  }

  def distinct[A: equal](x0: List[A]): Boolean = x0 match {
    case Nil     => true
    case x :: xs => !membera[A](xs, x) && distinct[A](xs)
  }

  def map[A, B](f: A => B, x1: List[A]): List[B] = (f, x1) match {
    case (f, Nil)        => Nil
    case (f, x21 :: x22) => f(x21) :: map[A, B](f, x22)
  }

  def fresh_var(base: String, avoid: List[String]): String =
    fresh_in(Suc(size_list[String](avoid)), base, avoid)

  def allSubexprs_bindings(x0: List[quantifier_binding]): List[expr] = x0 match {
    case Nil => Nil
    case QuantifierBindingFull(n, d, a, sp) :: bs =>
      allSubexprs(d) ++ allSubexprs_bindings(bs)
  }

  def allSubexprs_entries(x0: List[map_entry]): List[expr] = x0 match {
    case Nil => Nil
    case MapEntryFull(k, v, sp) :: es =>
      allSubexprs(k) ++ (allSubexprs(v) ++ allSubexprs_entries(es))
  }

  def allSubexprs_fields(x0: List[field_assign]): List[expr] = x0 match {
    case Nil => Nil
    case FieldAssignFull(n, v, sp) :: fs =>
      allSubexprs(v) ++ allSubexprs_fields(fs)
  }

  def allSubexprs_list(x0: List[expr]): List[expr] = x0 match {
    case Nil     => Nil
    case x :: xs => allSubexprs(x) ++ allSubexprs_list(xs)
  }

  def allSubexprs(x0: expr): List[expr] = x0 match {
    case BinaryOpF(op, l, r, sp) =>
      BinaryOpF(op, l, r, sp) :: allSubexprs(l) ++ allSubexprs(r)
    case UnaryOpF(op, e, sp)    => UnaryOpF(op, e, sp) :: allSubexprs(e)
    case FieldAccessF(b, f, sp) => FieldAccessF(b, f, sp) :: allSubexprs(b)
    case EnumAccessF(b, e, sp)  => EnumAccessF(b, e, sp) :: allSubexprs(b)
    case IndexF(b, i, sp)       => IndexF(b, i, sp) :: allSubexprs(b) ++ allSubexprs(i)
    case CallF(c, args, sp) =>
      CallF(c, args, sp) :: allSubexprs(c) ++ allSubexprs_list(args)
    case PrimeF(e, sp) => PrimeF(e, sp) :: allSubexprs(e)
    case PreF(e, sp)   => PreF(e, sp) :: allSubexprs(e)
    case WithF(b, ups, sp) =>
      WithF(b, ups, sp) :: allSubexprs(b) ++ allSubexprs_fields(ups)
    case IfF(c, t, e, sp) =>
      IfF(c, t, e, sp) :: allSubexprs(c) ++ (allSubexprs(t) ++ allSubexprs(e))
    case LetF(v, vala, body, sp) =>
      LetF(v, vala, body, sp) :: allSubexprs(vala) ++ allSubexprs(body)
    case LambdaF(p, b, sp) => LambdaF(p, b, sp) :: allSubexprs(b)
    case ConstructorF(n, fs, sp) =>
      ConstructorF(n, fs, sp) :: allSubexprs_fields(fs)
    case SetLiteralF(xs, sp) => SetLiteralF(xs, sp) :: allSubexprs_list(xs)
    case MapLiteralF(es, sp) => MapLiteralF(es, sp) :: allSubexprs_entries(es)
    case SetComprehensionF(v, d, p, sp) =>
      SetComprehensionF(v, d, p, sp) :: allSubexprs(d) ++ allSubexprs(p)
    case SeqLiteralF(xs, sp)  => SeqLiteralF(xs, sp) :: allSubexprs_list(xs)
    case MatchesF(e, pat, sp) => MatchesF(e, pat, sp) :: allSubexprs(e)
    case SomeWrapF(e, sp)     => SomeWrapF(e, sp) :: allSubexprs(e)
    case TheF(v, d, b, sp) =>
      TheF(v, d, b, sp) :: allSubexprs(d) ++ allSubexprs(b)
    case QuantifierF(q, bs, body, sp) =>
      QuantifierF(q, bs, body, sp) ::
        allSubexprs_bindings(bs) ++ allSubexprs(body)
    case IdentifierF(n, sp) => List(IdentifierF(n, sp))
    case IntLitF(n, sp)     => List(IntLitF(n, sp))
    case FloatLitF(v, sp)   => List(FloatLitF(v, sp))
    case StringLitF(v, sp)  => List(StringLitF(v, sp))
    case BoolLitF(b, sp)    => List(BoolLitF(b, sp))
    case NoneLitF(sp)       => List(NoneLitF(sp))
  }

  def dropWhile[A](p: A => Boolean, x1: List[A]): List[A] = (p, x1) match {
    case (p, Nil) => Nil
    case (p, x :: xs) =>
      p(x) match {
        case true  => dropWhile[A](p, xs)
        case false => x :: xs
      }
  }

  def takeWhile[A](p: A => Boolean, x1: List[A]): List[A] = (p, x1) match {
    case (p, Nil) => Nil
    case (p, x :: xs) =>
      p(x) match {
        case true  => x :: takeWhile[A](p, xs)
        case false => Nil
      }
  }

  def is_none[A](x0: Option[A]): Boolean = x0 match {
    case None    => true
    case Some(x) => false
  }

  def one_rat: rat = Frct((one_inta, one_inta))

  def power[A: power](a: A, n: nat): A =
    equal_nat(n, zero_nat) match {
      case true  => one[A]
      case false => times[A](a, power[A](a, minus_nat(n, one_nat)))
    }

  def asciiToIntAcc(x0: List[BigInt], acc: BigInt): Option[BigInt] = (x0, acc) match {
    case (Nil, acc) => Some[BigInt](acc)
    case (c :: cs, acc) =>
      BigInt(48) <= c && c <= BigInt(57) match {
        case true  => asciiToIntAcc(cs, plus_int(times_inta(acc, BigInt(10)), c - BigInt(48)))
        case false => None
      }
  }

  def decimalToRat(s: String): Option[rat] = {
    val cs = Str_Literal.asciisOfLiteral(s): List[BigInt]
    val (neg, body) =
      (cs match {
        case Nil => (false, cs)
        case c :: rest =>
          c == BigInt(45) match {
            case true  => (true, rest)
            case false => (false, cs)
          }
      }): ((Boolean, List[BigInt]))
    val ipart =
      takeWhile[BigInt]((c: BigInt) => !(c == BigInt(46)), body): List[BigInt]
    val fpart =
      (dropWhile[BigInt]((c: BigInt) => !(c == BigInt(46)), body) match {
        case Nil       => Nil
        case _ :: rest => rest
      }): List[BigInt];
    nulla[BigInt](ipart) && nulla[BigInt](fpart) match {
      case true => None
      case false => (asciiToIntAcc(ipart, zero_int), asciiToIntAcc(fpart, zero_int)) match {
          case (None, _)       => None
          case (Some(_), None) => None
          case (Some(iv), Some(fv)) =>
            Some[rat](times_rat(
              neg match {
                case true  => uminus_rat(one_rat)
                case false => one_rat
              },
              plus_rat(
                of_int(iv),
                divide_rat(of_int(fv), of_int(power[BigInt](BigInt(10), size_list[BigInt](fpart))))
              )
            ))
        }
    }
  }

  def map_filter[A, B](f: A => Option[B], x1: List[A]): List[B] = (f, x1) match {
    case (f, Nil) => Nil
    case (f, x :: xs) => f(x) match {
        case None    => map_filter[A, B](f, xs)
        case Some(y) => y :: map_filter[A, B](f, xs)
      }
  }

  def fkColumn(x0: foreign_key_spec): String = x0 match {
    case ForeignKeySpec(c, uu, uv, uw) => c
  }

  def flattenAndAll(es: List[expr]): List[expr] =
    maps[expr, expr]((a: expr) => flattenAnd(a), es)

  def isUPowerUnary(x0: expr): Boolean = x0 match {
    case UnaryOpF(UPower(), uu, uv)       => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(UNot(), va, vb)         => false
    case UnaryOpF(UNegate(), va, vb)      => false
    case UnaryOpF(UCardinality(), va, vb) => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(v, va)                  => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def requiresAlloy(e: expr): Boolean =
    list_ex[expr]((a: expr) => isUPowerUnary(a), allSubexprs(e))

  def indexName(x0: index_spec): String = x0 match {
    case IndexSpec(n, uu, uv, uw) => n
  }

  def tableName(x0: table_spec): String = x0 match {
    case TableSpec(n, uu, uv, uw, ux, uy, uz) => n
  }

  def smt_var_list(x0: smt_term): List[String] = x0 match {
    case BLit(uu)              => Nil
    case ILit(uv)              => Nil
    case RLit(uw)              => Nil
    case TVar(x)               => List(x)
    case EnumElemConst(ux, uy) => Nil
    case TNot(t)               => smt_var_list(t)
    case TAnd(l, r)            => smt_var_list(l) ++ smt_var_list(r)
    case TOr(l, r)             => smt_var_list(l) ++ smt_var_list(r)
    case TImplies(l, r)        => smt_var_list(l) ++ smt_var_list(r)
    case TEq(l, r)             => smt_var_list(l) ++ smt_var_list(r)
    case TLt(l, r)             => smt_var_list(l) ++ smt_var_list(r)
    case TNeg(t)               => smt_var_list(t)
    case TAdd(l, r)            => smt_var_list(l) ++ smt_var_list(r)
    case TSub(l, r)            => smt_var_list(l) ++ smt_var_list(r)
    case TMul(l, r)            => smt_var_list(l) ++ smt_var_list(r)
    case TDiv(l, r)            => smt_var_list(l) ++ smt_var_list(r)
    case TInDom(uz, t)         => smt_var_list(t)
    case TCardRel(va)          => Nil
    case TCard(t)              => smt_var_list(t)
    case TLetIn(v, a, b)       => v :: smt_var_list(a) ++ smt_var_list(b)
    case TForallEnum(v, vb, b) => v :: smt_var_list(b)
    case TForallRel(v, vc, b)  => v :: smt_var_list(b)
    case TExistsRel(v, vd, b)  => v :: smt_var_list(b)
    case TTheRel(v, ve, b)     => v :: smt_var_list(b)
    case TEntityBase(vf)       => Nil
    case TForallSet(v, d, b)   => v :: smt_var_list(d) ++ smt_var_list(b)
    case TTheSet(v, d, b)      => v :: smt_var_list(d) ++ smt_var_list(b)
    case TIndexRel(b, k)       => smt_var_list(b) ++ smt_var_list(k)
    case TFieldAccess(b, vg)   => smt_var_list(b)
    case TSetEmpty()           => Nil
    case TSetInsert(e, s)      => smt_var_list(e) ++ smt_var_list(s)
    case TSetMember(e, s)      => smt_var_list(e) ++ smt_var_list(s)
    case TSetUnion(l, r)       => smt_var_list(l) ++ smt_var_list(r)
    case TSetIntersect(l, r)   => smt_var_list(l) ++ smt_var_list(r)
    case TSetDiff(l, r)        => smt_var_list(l) ++ smt_var_list(r)
    case TPrime(t)             => smt_var_list(t)
    case TPre(t)               => smt_var_list(t)
    case TWithRec(b, vh, v)    => smt_var_list(b) ++ smt_var_list(v)
    case TIte(c, a, b)         => smt_var_list(c) ++ (smt_var_list(a) ++ smt_var_list(b))
    case TNone()               => Nil
    case TSome(t)              => smt_var_list(t)
    case TStrLit(vi)           => Nil
    case TMatches(t, vj)       => smt_var_list(t)
    case TUStrPred(vk, t)      => smt_var_list(t)
    case TUStrFunc(vl, t)      => smt_var_list(t)
    case TUIntFunc(vm, t)      => smt_var_list(t)
    case TStrLen(t)            => smt_var_list(t)
    case TUConst(vn)           => Nil
    case TSeqEmpty()           => Nil
    case TSeqCons(e, r)        => smt_var_list(e) ++ smt_var_list(r)
    case TMapEmpty()           => Nil
    case TMapCons(k, v, r) =>
      smt_var_list(k) ++ (smt_var_list(v) ++ smt_var_list(r))
    case TSum(c, vo) => smt_var_list(c)
  }

  def isIntLit(x0: expr): Boolean = x0 match {
    case IntLitF(uu, uv)                  => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(v, va)                  => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def flattenEnsuresExpr(x0: expr): List[expr] = x0 match {
    case BinaryOpF(BAnd(), l, r, uu) =>
      flattenEnsuresExpr(l) ++ flattenEnsuresExpr(r)
    case LetF(uv, v, b, uw)           => flattenEnsuresExpr(v) ++ flattenEnsuresExpr(b)
    case BinaryOpF(BOr(), va, vb, vc) => List(BinaryOpF(BOr(), va, vb, vc))
    case BinaryOpF(BImplies(), va, vb, vc) =>
      List(BinaryOpF(BImplies(), va, vb, vc))
    case BinaryOpF(BIff(), va, vb, vc)   => List(BinaryOpF(BIff(), va, vb, vc))
    case BinaryOpF(BEq(), va, vb, vc)    => List(BinaryOpF(BEq(), va, vb, vc))
    case BinaryOpF(BNeq(), va, vb, vc)   => List(BinaryOpF(BNeq(), va, vb, vc))
    case BinaryOpF(BLt(), va, vb, vc)    => List(BinaryOpF(BLt(), va, vb, vc))
    case BinaryOpF(BGt(), va, vb, vc)    => List(BinaryOpF(BGt(), va, vb, vc))
    case BinaryOpF(BLe(), va, vb, vc)    => List(BinaryOpF(BLe(), va, vb, vc))
    case BinaryOpF(BGe(), va, vb, vc)    => List(BinaryOpF(BGe(), va, vb, vc))
    case BinaryOpF(BIn(), va, vb, vc)    => List(BinaryOpF(BIn(), va, vb, vc))
    case BinaryOpF(BNotIn(), va, vb, vc) => List(BinaryOpF(BNotIn(), va, vb, vc))
    case BinaryOpF(BSubset(), va, vb, vc) =>
      List(BinaryOpF(BSubset(), va, vb, vc))
    case BinaryOpF(BUnion(), va, vb, vc) => List(BinaryOpF(BUnion(), va, vb, vc))
    case BinaryOpF(BIntersect(), va, vb, vc) =>
      List(BinaryOpF(BIntersect(), va, vb, vc))
    case BinaryOpF(BDiff(), va, vb, vc) => List(BinaryOpF(BDiff(), va, vb, vc))
    case BinaryOpF(BAdd(), va, vb, vc)  => List(BinaryOpF(BAdd(), va, vb, vc))
    case BinaryOpF(BSub(), va, vb, vc)  => List(BinaryOpF(BSub(), va, vb, vc))
    case BinaryOpF(BMul(), va, vb, vc)  => List(BinaryOpF(BMul(), va, vb, vc))
    case BinaryOpF(BDiv(), va, vb, vc)  => List(BinaryOpF(BDiv(), va, vb, vc))
    case UnaryOpF(v, va, vb)            => List(UnaryOpF(v, va, vb))
    case QuantifierF(v, va, vb, vc)     => List(QuantifierF(v, va, vb, vc))
    case SomeWrapF(v, va)               => List(SomeWrapF(v, va))
    case TheF(v, va, vb, vc)            => List(TheF(v, va, vb, vc))
    case FieldAccessF(v, va, vb)        => List(FieldAccessF(v, va, vb))
    case EnumAccessF(v, va, vb)         => List(EnumAccessF(v, va, vb))
    case IndexF(v, va, vb)              => List(IndexF(v, va, vb))
    case CallF(v, va, vb)               => List(CallF(v, va, vb))
    case PrimeF(v, va)                  => List(PrimeF(v, va))
    case PreF(v, va)                    => List(PreF(v, va))
    case WithF(v, va, vb)               => List(WithF(v, va, vb))
    case IfF(v, va, vb, vc)             => List(IfF(v, va, vb, vc))
    case LambdaF(v, va, vb)             => List(LambdaF(v, va, vb))
    case ConstructorF(v, va, vb)        => List(ConstructorF(v, va, vb))
    case SetLiteralF(v, va)             => List(SetLiteralF(v, va))
    case MapLiteralF(v, va)             => List(MapLiteralF(v, va))
    case SetComprehensionF(v, va, vb, vc) =>
      List(SetComprehensionF(v, va, vb, vc))
    case SeqLiteralF(v, va)  => List(SeqLiteralF(v, va))
    case MatchesF(v, va, vb) => List(MatchesF(v, va, vb))
    case IntLitF(v, va)      => List(IntLitF(v, va))
    case FloatLitF(v, va)    => List(FloatLitF(v, va))
    case StringLitF(v, va)   => List(StringLitF(v, va))
    case BoolLitF(v, va)     => List(BoolLitF(v, va))
    case NoneLitF(v)         => List(NoneLitF(v))
    case IdentifierF(v, va)  => List(IdentifierF(v, va))
  }

  def flattenEnsures(es: List[expr]): List[expr] =
    maps[expr, expr]((a: expr) => flattenEnsuresExpr(a), es)

  def equal_bin_op(x0: bin_op, x1: bin_op): Boolean = (x0, x1) match {
    case (BMul(), BDiv())             => false
    case (BDiv(), BMul())             => false
    case (BSub(), BDiv())             => false
    case (BDiv(), BSub())             => false
    case (BSub(), BMul())             => false
    case (BMul(), BSub())             => false
    case (BAdd(), BDiv())             => false
    case (BDiv(), BAdd())             => false
    case (BAdd(), BMul())             => false
    case (BMul(), BAdd())             => false
    case (BAdd(), BSub())             => false
    case (BSub(), BAdd())             => false
    case (BDiff(), BDiv())            => false
    case (BDiv(), BDiff())            => false
    case (BDiff(), BMul())            => false
    case (BMul(), BDiff())            => false
    case (BDiff(), BSub())            => false
    case (BSub(), BDiff())            => false
    case (BDiff(), BAdd())            => false
    case (BAdd(), BDiff())            => false
    case (BIntersect(), BDiv())       => false
    case (BDiv(), BIntersect())       => false
    case (BIntersect(), BMul())       => false
    case (BMul(), BIntersect())       => false
    case (BIntersect(), BSub())       => false
    case (BSub(), BIntersect())       => false
    case (BIntersect(), BAdd())       => false
    case (BAdd(), BIntersect())       => false
    case (BIntersect(), BDiff())      => false
    case (BDiff(), BIntersect())      => false
    case (BUnion(), BDiv())           => false
    case (BDiv(), BUnion())           => false
    case (BUnion(), BMul())           => false
    case (BMul(), BUnion())           => false
    case (BUnion(), BSub())           => false
    case (BSub(), BUnion())           => false
    case (BUnion(), BAdd())           => false
    case (BAdd(), BUnion())           => false
    case (BUnion(), BDiff())          => false
    case (BDiff(), BUnion())          => false
    case (BUnion(), BIntersect())     => false
    case (BIntersect(), BUnion())     => false
    case (BSubset(), BDiv())          => false
    case (BDiv(), BSubset())          => false
    case (BSubset(), BMul())          => false
    case (BMul(), BSubset())          => false
    case (BSubset(), BSub())          => false
    case (BSub(), BSubset())          => false
    case (BSubset(), BAdd())          => false
    case (BAdd(), BSubset())          => false
    case (BSubset(), BDiff())         => false
    case (BDiff(), BSubset())         => false
    case (BSubset(), BIntersect())    => false
    case (BIntersect(), BSubset())    => false
    case (BSubset(), BUnion())        => false
    case (BUnion(), BSubset())        => false
    case (BNotIn(), BDiv())           => false
    case (BDiv(), BNotIn())           => false
    case (BNotIn(), BMul())           => false
    case (BMul(), BNotIn())           => false
    case (BNotIn(), BSub())           => false
    case (BSub(), BNotIn())           => false
    case (BNotIn(), BAdd())           => false
    case (BAdd(), BNotIn())           => false
    case (BNotIn(), BDiff())          => false
    case (BDiff(), BNotIn())          => false
    case (BNotIn(), BIntersect())     => false
    case (BIntersect(), BNotIn())     => false
    case (BNotIn(), BUnion())         => false
    case (BUnion(), BNotIn())         => false
    case (BNotIn(), BSubset())        => false
    case (BSubset(), BNotIn())        => false
    case (BIn(), BDiv())              => false
    case (BDiv(), BIn())              => false
    case (BIn(), BMul())              => false
    case (BMul(), BIn())              => false
    case (BIn(), BSub())              => false
    case (BSub(), BIn())              => false
    case (BIn(), BAdd())              => false
    case (BAdd(), BIn())              => false
    case (BIn(), BDiff())             => false
    case (BDiff(), BIn())             => false
    case (BIn(), BIntersect())        => false
    case (BIntersect(), BIn())        => false
    case (BIn(), BUnion())            => false
    case (BUnion(), BIn())            => false
    case (BIn(), BSubset())           => false
    case (BSubset(), BIn())           => false
    case (BIn(), BNotIn())            => false
    case (BNotIn(), BIn())            => false
    case (BGe(), BDiv())              => false
    case (BDiv(), BGe())              => false
    case (BGe(), BMul())              => false
    case (BMul(), BGe())              => false
    case (BGe(), BSub())              => false
    case (BSub(), BGe())              => false
    case (BGe(), BAdd())              => false
    case (BAdd(), BGe())              => false
    case (BGe(), BDiff())             => false
    case (BDiff(), BGe())             => false
    case (BGe(), BIntersect())        => false
    case (BIntersect(), BGe())        => false
    case (BGe(), BUnion())            => false
    case (BUnion(), BGe())            => false
    case (BGe(), BSubset())           => false
    case (BSubset(), BGe())           => false
    case (BGe(), BNotIn())            => false
    case (BNotIn(), BGe())            => false
    case (BGe(), BIn())               => false
    case (BIn(), BGe())               => false
    case (BLe(), BDiv())              => false
    case (BDiv(), BLe())              => false
    case (BLe(), BMul())              => false
    case (BMul(), BLe())              => false
    case (BLe(), BSub())              => false
    case (BSub(), BLe())              => false
    case (BLe(), BAdd())              => false
    case (BAdd(), BLe())              => false
    case (BLe(), BDiff())             => false
    case (BDiff(), BLe())             => false
    case (BLe(), BIntersect())        => false
    case (BIntersect(), BLe())        => false
    case (BLe(), BUnion())            => false
    case (BUnion(), BLe())            => false
    case (BLe(), BSubset())           => false
    case (BSubset(), BLe())           => false
    case (BLe(), BNotIn())            => false
    case (BNotIn(), BLe())            => false
    case (BLe(), BIn())               => false
    case (BIn(), BLe())               => false
    case (BLe(), BGe())               => false
    case (BGe(), BLe())               => false
    case (BGt(), BDiv())              => false
    case (BDiv(), BGt())              => false
    case (BGt(), BMul())              => false
    case (BMul(), BGt())              => false
    case (BGt(), BSub())              => false
    case (BSub(), BGt())              => false
    case (BGt(), BAdd())              => false
    case (BAdd(), BGt())              => false
    case (BGt(), BDiff())             => false
    case (BDiff(), BGt())             => false
    case (BGt(), BIntersect())        => false
    case (BIntersect(), BGt())        => false
    case (BGt(), BUnion())            => false
    case (BUnion(), BGt())            => false
    case (BGt(), BSubset())           => false
    case (BSubset(), BGt())           => false
    case (BGt(), BNotIn())            => false
    case (BNotIn(), BGt())            => false
    case (BGt(), BIn())               => false
    case (BIn(), BGt())               => false
    case (BGt(), BGe())               => false
    case (BGe(), BGt())               => false
    case (BGt(), BLe())               => false
    case (BLe(), BGt())               => false
    case (BLt(), BDiv())              => false
    case (BDiv(), BLt())              => false
    case (BLt(), BMul())              => false
    case (BMul(), BLt())              => false
    case (BLt(), BSub())              => false
    case (BSub(), BLt())              => false
    case (BLt(), BAdd())              => false
    case (BAdd(), BLt())              => false
    case (BLt(), BDiff())             => false
    case (BDiff(), BLt())             => false
    case (BLt(), BIntersect())        => false
    case (BIntersect(), BLt())        => false
    case (BLt(), BUnion())            => false
    case (BUnion(), BLt())            => false
    case (BLt(), BSubset())           => false
    case (BSubset(), BLt())           => false
    case (BLt(), BNotIn())            => false
    case (BNotIn(), BLt())            => false
    case (BLt(), BIn())               => false
    case (BIn(), BLt())               => false
    case (BLt(), BGe())               => false
    case (BGe(), BLt())               => false
    case (BLt(), BLe())               => false
    case (BLe(), BLt())               => false
    case (BLt(), BGt())               => false
    case (BGt(), BLt())               => false
    case (BNeq(), BDiv())             => false
    case (BDiv(), BNeq())             => false
    case (BNeq(), BMul())             => false
    case (BMul(), BNeq())             => false
    case (BNeq(), BSub())             => false
    case (BSub(), BNeq())             => false
    case (BNeq(), BAdd())             => false
    case (BAdd(), BNeq())             => false
    case (BNeq(), BDiff())            => false
    case (BDiff(), BNeq())            => false
    case (BNeq(), BIntersect())       => false
    case (BIntersect(), BNeq())       => false
    case (BNeq(), BUnion())           => false
    case (BUnion(), BNeq())           => false
    case (BNeq(), BSubset())          => false
    case (BSubset(), BNeq())          => false
    case (BNeq(), BNotIn())           => false
    case (BNotIn(), BNeq())           => false
    case (BNeq(), BIn())              => false
    case (BIn(), BNeq())              => false
    case (BNeq(), BGe())              => false
    case (BGe(), BNeq())              => false
    case (BNeq(), BLe())              => false
    case (BLe(), BNeq())              => false
    case (BNeq(), BGt())              => false
    case (BGt(), BNeq())              => false
    case (BNeq(), BLt())              => false
    case (BLt(), BNeq())              => false
    case (BEq(), BDiv())              => false
    case (BDiv(), BEq())              => false
    case (BEq(), BMul())              => false
    case (BMul(), BEq())              => false
    case (BEq(), BSub())              => false
    case (BSub(), BEq())              => false
    case (BEq(), BAdd())              => false
    case (BAdd(), BEq())              => false
    case (BEq(), BDiff())             => false
    case (BDiff(), BEq())             => false
    case (BEq(), BIntersect())        => false
    case (BIntersect(), BEq())        => false
    case (BEq(), BUnion())            => false
    case (BUnion(), BEq())            => false
    case (BEq(), BSubset())           => false
    case (BSubset(), BEq())           => false
    case (BEq(), BNotIn())            => false
    case (BNotIn(), BEq())            => false
    case (BEq(), BIn())               => false
    case (BIn(), BEq())               => false
    case (BEq(), BGe())               => false
    case (BGe(), BEq())               => false
    case (BEq(), BLe())               => false
    case (BLe(), BEq())               => false
    case (BEq(), BGt())               => false
    case (BGt(), BEq())               => false
    case (BEq(), BLt())               => false
    case (BLt(), BEq())               => false
    case (BEq(), BNeq())              => false
    case (BNeq(), BEq())              => false
    case (BIff(), BDiv())             => false
    case (BDiv(), BIff())             => false
    case (BIff(), BMul())             => false
    case (BMul(), BIff())             => false
    case (BIff(), BSub())             => false
    case (BSub(), BIff())             => false
    case (BIff(), BAdd())             => false
    case (BAdd(), BIff())             => false
    case (BIff(), BDiff())            => false
    case (BDiff(), BIff())            => false
    case (BIff(), BIntersect())       => false
    case (BIntersect(), BIff())       => false
    case (BIff(), BUnion())           => false
    case (BUnion(), BIff())           => false
    case (BIff(), BSubset())          => false
    case (BSubset(), BIff())          => false
    case (BIff(), BNotIn())           => false
    case (BNotIn(), BIff())           => false
    case (BIff(), BIn())              => false
    case (BIn(), BIff())              => false
    case (BIff(), BGe())              => false
    case (BGe(), BIff())              => false
    case (BIff(), BLe())              => false
    case (BLe(), BIff())              => false
    case (BIff(), BGt())              => false
    case (BGt(), BIff())              => false
    case (BIff(), BLt())              => false
    case (BLt(), BIff())              => false
    case (BIff(), BNeq())             => false
    case (BNeq(), BIff())             => false
    case (BIff(), BEq())              => false
    case (BEq(), BIff())              => false
    case (BImplies(), BDiv())         => false
    case (BDiv(), BImplies())         => false
    case (BImplies(), BMul())         => false
    case (BMul(), BImplies())         => false
    case (BImplies(), BSub())         => false
    case (BSub(), BImplies())         => false
    case (BImplies(), BAdd())         => false
    case (BAdd(), BImplies())         => false
    case (BImplies(), BDiff())        => false
    case (BDiff(), BImplies())        => false
    case (BImplies(), BIntersect())   => false
    case (BIntersect(), BImplies())   => false
    case (BImplies(), BUnion())       => false
    case (BUnion(), BImplies())       => false
    case (BImplies(), BSubset())      => false
    case (BSubset(), BImplies())      => false
    case (BImplies(), BNotIn())       => false
    case (BNotIn(), BImplies())       => false
    case (BImplies(), BIn())          => false
    case (BIn(), BImplies())          => false
    case (BImplies(), BGe())          => false
    case (BGe(), BImplies())          => false
    case (BImplies(), BLe())          => false
    case (BLe(), BImplies())          => false
    case (BImplies(), BGt())          => false
    case (BGt(), BImplies())          => false
    case (BImplies(), BLt())          => false
    case (BLt(), BImplies())          => false
    case (BImplies(), BNeq())         => false
    case (BNeq(), BImplies())         => false
    case (BImplies(), BEq())          => false
    case (BEq(), BImplies())          => false
    case (BImplies(), BIff())         => false
    case (BIff(), BImplies())         => false
    case (BOr(), BDiv())              => false
    case (BDiv(), BOr())              => false
    case (BOr(), BMul())              => false
    case (BMul(), BOr())              => false
    case (BOr(), BSub())              => false
    case (BSub(), BOr())              => false
    case (BOr(), BAdd())              => false
    case (BAdd(), BOr())              => false
    case (BOr(), BDiff())             => false
    case (BDiff(), BOr())             => false
    case (BOr(), BIntersect())        => false
    case (BIntersect(), BOr())        => false
    case (BOr(), BUnion())            => false
    case (BUnion(), BOr())            => false
    case (BOr(), BSubset())           => false
    case (BSubset(), BOr())           => false
    case (BOr(), BNotIn())            => false
    case (BNotIn(), BOr())            => false
    case (BOr(), BIn())               => false
    case (BIn(), BOr())               => false
    case (BOr(), BGe())               => false
    case (BGe(), BOr())               => false
    case (BOr(), BLe())               => false
    case (BLe(), BOr())               => false
    case (BOr(), BGt())               => false
    case (BGt(), BOr())               => false
    case (BOr(), BLt())               => false
    case (BLt(), BOr())               => false
    case (BOr(), BNeq())              => false
    case (BNeq(), BOr())              => false
    case (BOr(), BEq())               => false
    case (BEq(), BOr())               => false
    case (BOr(), BIff())              => false
    case (BIff(), BOr())              => false
    case (BOr(), BImplies())          => false
    case (BImplies(), BOr())          => false
    case (BAnd(), BDiv())             => false
    case (BDiv(), BAnd())             => false
    case (BAnd(), BMul())             => false
    case (BMul(), BAnd())             => false
    case (BAnd(), BSub())             => false
    case (BSub(), BAnd())             => false
    case (BAnd(), BAdd())             => false
    case (BAdd(), BAnd())             => false
    case (BAnd(), BDiff())            => false
    case (BDiff(), BAnd())            => false
    case (BAnd(), BIntersect())       => false
    case (BIntersect(), BAnd())       => false
    case (BAnd(), BUnion())           => false
    case (BUnion(), BAnd())           => false
    case (BAnd(), BSubset())          => false
    case (BSubset(), BAnd())          => false
    case (BAnd(), BNotIn())           => false
    case (BNotIn(), BAnd())           => false
    case (BAnd(), BIn())              => false
    case (BIn(), BAnd())              => false
    case (BAnd(), BGe())              => false
    case (BGe(), BAnd())              => false
    case (BAnd(), BLe())              => false
    case (BLe(), BAnd())              => false
    case (BAnd(), BGt())              => false
    case (BGt(), BAnd())              => false
    case (BAnd(), BLt())              => false
    case (BLt(), BAnd())              => false
    case (BAnd(), BNeq())             => false
    case (BNeq(), BAnd())             => false
    case (BAnd(), BEq())              => false
    case (BEq(), BAnd())              => false
    case (BAnd(), BIff())             => false
    case (BIff(), BAnd())             => false
    case (BAnd(), BImplies())         => false
    case (BImplies(), BAnd())         => false
    case (BAnd(), BOr())              => false
    case (BOr(), BAnd())              => false
    case (BDiv(), BDiv())             => true
    case (BMul(), BMul())             => true
    case (BSub(), BSub())             => true
    case (BAdd(), BAdd())             => true
    case (BDiff(), BDiff())           => true
    case (BIntersect(), BIntersect()) => true
    case (BUnion(), BUnion())         => true
    case (BSubset(), BSubset())       => true
    case (BNotIn(), BNotIn())         => true
    case (BIn(), BIn())               => true
    case (BGe(), BGe())               => true
    case (BLe(), BLe())               => true
    case (BGt(), BGt())               => true
    case (BLt(), BLt())               => true
    case (BNeq(), BNeq())             => true
    case (BEq(), BEq())               => true
    case (BIff(), BIff())             => true
    case (BImplies(), BImplies())     => true
    case (BOr(), BOr())               => true
    case (BAnd(), BAnd())             => true
  }

  def map_single_entry(x0: expr): Option[(String, String)] = x0 match {
    case MapLiteralF(es, uu) =>
      es match {
        case Nil => None
        case List(MapEntryFull(kE, vE, _)) =>
          (identName(kE), identName(vE)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(kn), Some(vn)) => Some[(String, String)]((kn, vn))
          }
        case MapEntryFull(_, _, _) :: _ :: _ => None
      }
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def base_ident_name(x0: expr): Option[String] = x0 match {
    case PreF(e, uu)                      => identName(e)
    case PrimeF(e, uv)                    => identName(e)
    case IdentifierF(x, uw)               => Some[String](x)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
  }

  def rel_insert_rhs(x0: expr): Option[(String, (String, String))] = x0 match {
    case BinaryOpF(bop, base, mlit, uu) =>
      equal_bin_op(bop, BAdd()) match {
        case true => (base_ident_name(base), map_single_entry(mlit)) match {
            case (None, _)       => None
            case (Some(_), None) => None
            case (Some(brel), Some((kn, vn))) =>
              Some[(String, (String, String))]((brel, (kn, vn)))
          }
        case false => None
      }
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def rootIdentifier(x0: expr): Option[String] = x0 match {
    case IdentifierF(n, uu)               => Some[String](n)
    case IndexF(base, uv, uw)             => rootIdentifier(base)
    case FieldAccessF(base, ux, uy)       => rootIdentifier(base)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case EnumAccessF(v, va, vb)           => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
  }

  def typeStripSpans(x0: type_expr): type_expr = x0 match {
    case NamedTypeF(n, uu) => NamedTypeF(n, None)
    case SetTypeF(t, uv)   => SetTypeF(typeStripSpans(t), None)
    case MapTypeF(k, v, uw) =>
      MapTypeF(typeStripSpans(k), typeStripSpans(v), None)
    case SeqTypeF(t, ux)    => SeqTypeF(typeStripSpans(t), None)
    case OptionTypeF(t, uy) => OptionTypeF(typeStripSpans(t), None)
    case RelationTypeF(f, m, t, uz) =>
      RelationTypeF(typeStripSpans(f), m, typeStripSpans(t), None)
  }

  def qb_names(x0: List[quantifier_binding]): List[String] = x0 match {
    case Nil                                        => Nil
    case QuantifierBindingFull(n, uu, uv, uw) :: bs => n :: qb_names(bs)
  }

  def subst_bindings(vk: String, vl: expr, x2: List[quantifier_binding]): List[quantifier_binding] =
    (vk, vl, x2) match {
      case (vk, vl, Nil) => Nil
      case (x, r, QuantifierBindingFull(n, d, kk, sp) :: bs) =>
        QuantifierBindingFull(n, subst(x, r, d), kk, sp) :: subst_bindings(x, r, bs)
    }

  def subst_entries(vi: String, vj: expr, x2: List[map_entry]): List[map_entry] =
    (vi, vj, x2) match {
      case (vi, vj, Nil) => Nil
      case (x, r, MapEntryFull(k, v, sp) :: es) =>
        MapEntryFull(subst(x, r, k), subst(x, r, v), sp) :: subst_entries(x, r, es)
    }

  def subst_fields(vg: String, vh: expr, x2: List[field_assign]): List[field_assign] =
    (vg, vh, x2) match {
      case (vg, vh, Nil) => Nil
      case (x, r, FieldAssignFull(f, v, sp) :: fs) =>
        FieldAssignFull(f, subst(x, r, v), sp) :: subst_fields(x, r, fs)
    }

  def subst_list(ve: String, vf: expr, x2: List[expr]): List[expr] =
    (ve, vf, x2) match {
      case (ve, vf, Nil)   => Nil
      case (x, r, e :: es) => subst(x, r, e) :: subst_list(x, r, es)
    }

  def subst(x: String, r: expr, xa2: expr): expr = (x, r, xa2) match {
    case (x, r, IdentifierF(n, sp)) =>
      n == x match {
        case true  => r
        case false => IdentifierF(n, sp)
      }
    case (x, r, BinaryOpF(op, l, rr, sp)) =>
      BinaryOpF(op, subst(x, r, l), subst(x, r, rr), sp)
    case (x, r, UnaryOpF(op, e, sp))    => UnaryOpF(op, subst(x, r, e), sp)
    case (x, r, FieldAccessF(b, f, sp)) => FieldAccessF(subst(x, r, b), f, sp)
    case (x, r, EnumAccessF(b, m, sp))  => EnumAccessF(subst(x, r, b), m, sp)
    case (x, r, IndexF(b, i, sp))       => IndexF(subst(x, r, b), subst(x, r, i), sp)
    case (x, r, CallF(c, args, sp)) =>
      CallF(subst(x, r, c), subst_list(x, r, args), sp)
    case (x, r, PrimeF(e, sp)) => PrimeF(subst(x, r, e), sp)
    case (x, r, PreF(e, sp))   => PreF(subst(x, r, e), sp)
    case (x, r, WithF(b, upds, sp)) =>
      WithF(subst(x, r, b), subst_fields(x, r, upds), sp)
    case (x, r, IfF(c, t, e, sp)) =>
      IfF(subst(x, r, c), subst(x, r, t), subst(x, r, e), sp)
    case (x, r, LetF(v, vala, body, sp)) =>
      LetF(
        v,
        subst(x, r, vala),
        v == x match {
          case true  => body
          case false => subst(x, r, body)
        },
        sp
      )
    case (x, r, LambdaF(p, b, sp)) =>
      LambdaF(
        p,
        p == x match {
          case true  => b
          case false => subst(x, r, b)
        },
        sp
      )
    case (x, r, ConstructorF(n, fs, sp)) =>
      ConstructorF(n, subst_fields(x, r, fs), sp)
    case (x, r, SetLiteralF(xs, sp)) => SetLiteralF(subst_list(x, r, xs), sp)
    case (x, r, MapLiteralF(es, sp)) => MapLiteralF(subst_entries(x, r, es), sp)
    case (x, r, SetComprehensionF(v, d, p, sp)) =>
      SetComprehensionF(
        v,
        subst(x, r, d),
        v == x match {
          case true  => p
          case false => subst(x, r, p)
        },
        sp
      )
    case (x, r, SeqLiteralF(xs, sp))  => SeqLiteralF(subst_list(x, r, xs), sp)
    case (x, r, MatchesF(e, pat, sp)) => MatchesF(subst(x, r, e), pat, sp)
    case (x, r, SomeWrapF(e, sp))     => SomeWrapF(subst(x, r, e), sp)
    case (x, r, TheF(v, d, b, sp)) =>
      TheF(
        v,
        subst(x, r, d),
        v == x match {
          case true  => b
          case false => subst(x, r, b)
        },
        sp
      )
    case (x, r, QuantifierF(q, bs, body, sp)) =>
      QuantifierF(
        q,
        subst_bindings(x, r, bs),
        string_in_list(x, qb_names(bs)) match {
          case true  => body
          case false => subst(x, r, body)
        },
        sp
      )
    case (uu, uv, IntLitF(n, sp))    => IntLitF(n, sp)
    case (uw, ux, FloatLitF(n, sp))  => FloatLitF(n, sp)
    case (uy, uz, StringLitF(n, sp)) => StringLitF(n, sp)
    case (va, vb, BoolLitF(v, sp))   => BoolLitF(v, sp)
    case (vc, vd, NoneLitF(sp))      => NoneLitF(sp)
  }

  def columnName(x0: column_spec): String = x0 match {
    case ColumnSpec(n, uu, uv, uw) => n
  }

  def fkOnDelete(x0: foreign_key_spec): String = x0 match {
    case ForeignKeySpec(uu, uv, uw, od) => od
  }

  def fkRefTable(x0: foreign_key_spec): String = x0 match {
    case ForeignKeySpec(uu, rt, uv, uw) => rt
  }

  def is_builtin_func(nm: String): Boolean = nm == "hash"

  def is_builtin_pred(nm: String): Boolean =
    nm == "isValidURI" || nm == "isValidEmail"

  def isComp(x0: bin_op): Boolean = x0 match {
    case BGe()        => true
    case BGt()        => true
    case BLe()        => true
    case BLt()        => true
    case BAnd()       => false
    case BOr()        => false
    case BImplies()   => false
    case BIff()       => false
    case BEq()        => false
    case BNeq()       => false
    case BIn()        => false
    case BNotIn()     => false
    case BSubset()    => false
    case BUnion()     => false
    case BIntersect() => false
    case BDiff()      => false
    case BAdd()       => false
    case BSub()       => false
    case BMul()       => false
    case BDiv()       => false
  }

  def negate(e: expr): Option[expr] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)       => None
      case BinaryOpF(BOr(), _, _, _)        => None
      case BinaryOpF(BImplies(), _, _, _)   => None
      case BinaryOpF(BIff(), _, _, _)       => None
      case BinaryOpF(BEq(), l, r, sp)       => Some[expr](BinaryOpF(BNeq(), l, r, sp))
      case BinaryOpF(BNeq(), l, r, sp)      => Some[expr](BinaryOpF(BEq(), l, r, sp))
      case BinaryOpF(BLt(), l, r, sp)       => Some[expr](BinaryOpF(BGe(), l, r, sp))
      case BinaryOpF(BGt(), l, r, sp)       => Some[expr](BinaryOpF(BLe(), l, r, sp))
      case BinaryOpF(BLe(), l, r, sp)       => Some[expr](BinaryOpF(BGt(), l, r, sp))
      case BinaryOpF(BGe(), l, r, sp)       => Some[expr](BinaryOpF(BLt(), l, r, sp))
      case BinaryOpF(BIn(), _, _, _)        => None
      case BinaryOpF(BNotIn(), _, _, _)     => None
      case BinaryOpF(BSubset(), _, _, _)    => None
      case BinaryOpF(BUnion(), _, _, _)     => None
      case BinaryOpF(BIntersect(), _, _, _) => None
      case BinaryOpF(BDiff(), _, _, _)      => None
      case BinaryOpF(BAdd(), _, _, _)       => None
      case BinaryOpF(BSub(), _, _, _)       => None
      case BinaryOpF(BMul(), _, _, _)       => None
      case BinaryOpF(BDiv(), _, _, _)       => None
      case UnaryOpF(UNot(), inner, _)       => Some[expr](inner)
      case UnaryOpF(UNegate(), _, _)        => None
      case UnaryOpF(UCardinality(), _, _)   => None
      case UnaryOpF(UPower(), _, _)         => None
      case QuantifierF(_, _, _, _)          => None
      case SomeWrapF(_, _)                  => None
      case TheF(_, _, _, _)                 => None
      case FieldAccessF(_, _, _)            => None
      case EnumAccessF(_, _, _)             => None
      case IndexF(_, _, _)                  => None
      case CallF(_, _, _)                   => None
      case PrimeF(_, _)                     => None
      case PreF(_, _)                       => None
      case WithF(_, _, _)                   => None
      case IfF(_, _, _, _)                  => None
      case LetF(_, _, _, _)                 => None
      case LambdaF(_, _, _)                 => None
      case ConstructorF(_, _, _)            => None
      case SetLiteralF(_, _)                => None
      case MapLiteralF(_, _)                => None
      case SetComprehensionF(_, _, _, _)    => None
      case SeqLiteralF(_, _)                => None
      case MatchesF(_, _, _)                => None
      case IntLitF(_, _)                    => None
      case FloatLitF(_, _)                  => None
      case StringLitF(_, _)                 => None
      case BoolLitF(_, _)                   => None
      case NoneLitF(_)                      => None
      case IdentifierF(_, _)                => None
    }

  def isRedirectStatus(s: BigInt): Boolean =
    equal_int(s, BigInt(301)) ||
      (equal_int(s, BigInt(302)) ||
        (equal_int(s, BigInt(303)) ||
          (equal_int(s, BigInt(307)) || equal_int(s, BigInt(308)))))

  def classifyShape(
      method: http_method,
      status: BigInt,
      pathParamCount: nat,
      kind: operation_kind
  ): route_kind =
    isRedirectStatus(status) match {
      case true => RkRedirect()
      case false => kind match {
          case Create() => RkCreate()
          case Read() =>
            equal_nat(pathParamCount, one_nat) match {
              case true => RkRead()
              case false => equal_nat(pathParamCount, zero_nat) match {
                  case true  => RkList()
                  case false => RkOther()
                }
            }
          case Replace() =>
            method match {
              case GET() =>
                equal_nat(pathParamCount, zero_nat) match {
                  case true  => RkList()
                  case false => RkOther()
                }
              case POST()   => RkOther()
              case PUT()    => RkOther()
              case PATCH()  => RkOther()
              case DELETE() => RkOther()
            }
          case PartialUpdate() =>
            method match {
              case GET() =>
                equal_nat(pathParamCount, zero_nat) match {
                  case true  => RkList()
                  case false => RkOther()
                }
              case POST()   => RkOther()
              case PUT()    => RkOther()
              case PATCH()  => RkOther()
              case DELETE() => RkOther()
            }
          case Deletea() =>
            equal_nat(pathParamCount, one_nat) match {
              case true  => RkDelete()
              case false => RkOther()
            }
          case CreateChild() =>
            method match {
              case GET() =>
                equal_nat(pathParamCount, zero_nat) match {
                  case true  => RkList()
                  case false => RkOther()
                }
              case POST()   => RkOther()
              case PUT()    => RkOther()
              case PATCH()  => RkOther()
              case DELETE() => RkOther()
            }
          case FilteredRead() =>
            equal_nat(pathParamCount, zero_nat) match {
              case true  => RkList()
              case false => RkOther()
            }
          case SideEffect() =>
            method match {
              case GET() =>
                equal_nat(pathParamCount, zero_nat) match {
                  case true  => RkList()
                  case false => RkOther()
                }
              case POST()   => RkOther()
              case PUT()    => RkOther()
              case PATCH()  => RkOther()
              case DELETE() => RkOther()
            }
          case BatchMutation() =>
            method match {
              case GET() =>
                equal_nat(pathParamCount, zero_nat) match {
                  case true  => RkList()
                  case false => RkOther()
                }
              case POST()   => RkOther()
              case PUT()    => RkOther()
              case PATCH()  => RkOther()
              case DELETE() => RkOther()
            }
          case Transition() =>
            method match {
              case GET() =>
                equal_nat(pathParamCount, zero_nat) match {
                  case true  => RkList()
                  case false => RkOther()
                }
              case POST()   => RkOther()
              case PUT()    => RkOther()
              case PATCH()  => RkOther()
              case DELETE() => RkOther()
            }
        }
    }

  def isRkList(x0: route_kind): Boolean = x0 match {
    case RkList()     => true
    case RkCreate()   => false
    case RkRead()     => false
    case RkDelete()   => false
    case RkRedirect() => false
    case RkOther()    => false
  }

  def classify(
      method: http_method,
      status: BigInt,
      pathParamCount: nat,
      kind: operation_kind,
      hasFilterInputs: Boolean
  ): route_kind = {
    val shape =
      classifyShape(method, status, pathParamCount, kind): route_kind;
    isRkList(shape) && hasFilterInputs match {
      case true  => RkOther()
      case false => shape
    }
  }

  def fkRefColumn(x0: foreign_key_spec): String = x0 match {
    case ForeignKeySpec(uu, uv, rc, uw) => rc
  }

  def indexUnique(x0: index_spec): Boolean = x0 match {
    case IndexSpec(uu, uv, u, uw) => u
  }

  def tableChecks(x0: table_spec): List[String] = x0 match {
    case TableSpec(uu, uv, uw, ux, uy, cks, uz) => cks
  }

  def triggerName(x0: trigger_spec): String = x0 match {
    case TriggerSpec(n, uu, uv, uw, ux, uy, uz, va) => n
  }

  def sqlOp(op: bin_op): Option[String] = op match {
    case BAnd()       => None
    case BOr()        => None
    case BImplies()   => None
    case BIff()       => None
    case BEq()        => Some[String]("=")
    case BNeq()       => Some[String]("!=")
    case BLt()        => Some[String]("<")
    case BGt()        => Some[String](">")
    case BLe()        => Some[String]("<=")
    case BGe()        => Some[String](">=")
    case BIn()        => None
    case BNotIn()     => None
    case BSubset()    => None
    case BUnion()     => None
    case BIntersect() => None
    case BDiff()      => None
    case BAdd()       => None
    case BSub()       => None
    case BMul()       => None
    case BDiv()       => None
  }

  def emptyIntConstraint: int_constraint = IntConstraint(None, None, Nil)

  def isRefinementCmp(x0: bin_op): Boolean = x0 match {
    case BGe()        => true
    case BGt()        => true
    case BLe()        => true
    case BLt()        => true
    case BEq()        => true
    case BNeq()       => true
    case BAnd()       => false
    case BOr()        => false
    case BImplies()   => false
    case BIff()       => false
    case BIn()        => false
    case BNotIn()     => false
    case BSubset()    => false
    case BUnion()     => false
    case BIntersect() => false
    case BDiff()      => false
    case BAdd()       => false
    case BSub()       => false
    case BMul()       => false
    case BDiv()       => false
  }

  def isValueRef(x0: expr): Boolean = x0 match {
    case IdentifierF(n, uu)               => n == "value"
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(v, va)                  => false
    case NoneLitF(v)                      => false
  }

  def isLenOfValue(x0: expr): Boolean = x0 match {
    case CallF(c, args, uu) =>
      (c match {
        case BinaryOpF(_, _, _, _)         => false
        case UnaryOpF(_, _, _)             => false
        case QuantifierF(_, _, _, _)       => false
        case SomeWrapF(_, _)               => false
        case TheF(_, _, _, _)              => false
        case FieldAccessF(_, _, _)         => false
        case EnumAccessF(_, _, _)          => false
        case IndexF(_, _, _)               => false
        case CallF(_, _, _)                => false
        case PrimeF(_, _)                  => false
        case PreF(_, _)                    => false
        case WithF(_, _, _)                => false
        case IfF(_, _, _, _)               => false
        case LetF(_, _, _, _)              => false
        case LambdaF(_, _, _)              => false
        case ConstructorF(_, _, _)         => false
        case SetLiteralF(_, _)             => false
        case MapLiteralF(_, _)             => false
        case SetComprehensionF(_, _, _, _) => false
        case SeqLiteralF(_, _)             => false
        case MatchesF(_, _, _)             => false
        case IntLitF(_, _)                 => false
        case FloatLitF(_, _)               => false
        case StringLitF(_, _)              => false
        case BoolLitF(_, _)                => false
        case NoneLitF(_)                   => false
        case IdentifierF(n, _)             => n == "len"
      }) &&
      (args match {
        case Nil         => false
        case List(arg)   => isValueRef(arg)
        case _ :: _ :: _ => false
      })
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(v, va)                  => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def decomposeAtom(e: expr): refinement_atom =
    e match {
      case BinaryOpF(op, l, rhs, _) =>
        !isRefinementCmp(op) match {
          case true => RaUnknown(e)
          case false => rhs match {
              case BinaryOpF(_, _, _, _)         => RaUnknown(e)
              case UnaryOpF(_, _, _)             => RaUnknown(e)
              case QuantifierF(_, _, _, _)       => RaUnknown(e)
              case SomeWrapF(_, _)               => RaUnknown(e)
              case TheF(_, _, _, _)              => RaUnknown(e)
              case FieldAccessF(_, _, _)         => RaUnknown(e)
              case EnumAccessF(_, _, _)          => RaUnknown(e)
              case IndexF(_, _, _)               => RaUnknown(e)
              case CallF(_, _, _)                => RaUnknown(e)
              case PrimeF(_, _)                  => RaUnknown(e)
              case PreF(_, _)                    => RaUnknown(e)
              case WithF(_, _, _)                => RaUnknown(e)
              case IfF(_, _, _, _)               => RaUnknown(e)
              case LetF(_, _, _, _)              => RaUnknown(e)
              case LambdaF(_, _, _)              => RaUnknown(e)
              case ConstructorF(_, _, _)         => RaUnknown(e)
              case SetLiteralF(_, _)             => RaUnknown(e)
              case MapLiteralF(_, _)             => RaUnknown(e)
              case SetComprehensionF(_, _, _, _) => RaUnknown(e)
              case SeqLiteralF(_, _)             => RaUnknown(e)
              case MatchesF(_, _, _)             => RaUnknown(e)
              case IntLitF(n, _) =>
                isLenOfValue(l) match {
                  case true => RaLenCmp(op, n)
                  case false => isValueRef(l) match {
                      case true  => RaValueCmp(op, n)
                      case false => RaUnknown(e)
                    }
                }
              case FloatLitF(_, _)   => RaUnknown(e)
              case StringLitF(_, _)  => RaUnknown(e)
              case BoolLitF(_, _)    => RaUnknown(e)
              case NoneLitF(_)       => RaUnknown(e)
              case IdentifierF(_, _) => RaUnknown(e)
            }
        }
      case UnaryOpF(_, _, _)       => RaUnknown(e)
      case QuantifierF(_, _, _, _) => RaUnknown(e)
      case SomeWrapF(_, _)         => RaUnknown(e)
      case TheF(_, _, _, _)        => RaUnknown(e)
      case FieldAccessF(_, _, _)   => RaUnknown(e)
      case EnumAccessF(_, _, _)    => RaUnknown(e)
      case IndexF(_, _, _)         => RaUnknown(e)
      case CallF(f, args, _) =>
        (f, args) match {
          case (BinaryOpF(_, _, _, _), _)         => RaUnknown(e)
          case (UnaryOpF(_, _, _), _)             => RaUnknown(e)
          case (QuantifierF(_, _, _, _), _)       => RaUnknown(e)
          case (SomeWrapF(_, _), _)               => RaUnknown(e)
          case (TheF(_, _, _, _), _)              => RaUnknown(e)
          case (FieldAccessF(_, _, _), _)         => RaUnknown(e)
          case (EnumAccessF(_, _, _), _)          => RaUnknown(e)
          case (IndexF(_, _, _), _)               => RaUnknown(e)
          case (CallF(_, _, _), _)                => RaUnknown(e)
          case (PrimeF(_, _), _)                  => RaUnknown(e)
          case (PreF(_, _), _)                    => RaUnknown(e)
          case (WithF(_, _, _), _)                => RaUnknown(e)
          case (IfF(_, _, _, _), _)               => RaUnknown(e)
          case (LetF(_, _, _, _), _)              => RaUnknown(e)
          case (LambdaF(_, _, _), _)              => RaUnknown(e)
          case (ConstructorF(_, _, _), _)         => RaUnknown(e)
          case (SetLiteralF(_, _), _)             => RaUnknown(e)
          case (MapLiteralF(_, _), _)             => RaUnknown(e)
          case (SetComprehensionF(_, _, _, _), _) => RaUnknown(e)
          case (SeqLiteralF(_, _), _)             => RaUnknown(e)
          case (MatchesF(_, _, _), _)             => RaUnknown(e)
          case (IntLitF(_, _), _)                 => RaUnknown(e)
          case (FloatLitF(_, _), _)               => RaUnknown(e)
          case (StringLitF(_, _), _)              => RaUnknown(e)
          case (BoolLitF(_, _), _)                => RaUnknown(e)
          case (NoneLitF(_), _)                   => RaUnknown(e)
          case (IdentifierF(_, _), Nil)           => RaUnknown(e)
          case (IdentifierF(p, _), List(arg)) =>
            isValueRef(arg) match {
              case true  => RaPredCall(p)
              case false => RaUnknown(e)
            }
          case (IdentifierF(_, _), _ :: _ :: _) => RaUnknown(e)
        }
      case PrimeF(_, _)                                  => RaUnknown(e)
      case PreF(_, _)                                    => RaUnknown(e)
      case WithF(_, _, _)                                => RaUnknown(e)
      case IfF(_, _, _, _)                               => RaUnknown(e)
      case LetF(_, _, _, _)                              => RaUnknown(e)
      case LambdaF(_, _, _)                              => RaUnknown(e)
      case ConstructorF(_, _, _)                         => RaUnknown(e)
      case SetLiteralF(_, _)                             => RaUnknown(e)
      case MapLiteralF(_, _)                             => RaUnknown(e)
      case SetComprehensionF(_, _, _, _)                 => RaUnknown(e)
      case SeqLiteralF(_, _)                             => RaUnknown(e)
      case MatchesF(BinaryOpF(_, _, _, _), _, _)         => RaUnknown(e)
      case MatchesF(UnaryOpF(_, _, _), _, _)             => RaUnknown(e)
      case MatchesF(QuantifierF(_, _, _, _), _, _)       => RaUnknown(e)
      case MatchesF(SomeWrapF(_, _), _, _)               => RaUnknown(e)
      case MatchesF(TheF(_, _, _, _), _, _)              => RaUnknown(e)
      case MatchesF(FieldAccessF(_, _, _), _, _)         => RaUnknown(e)
      case MatchesF(EnumAccessF(_, _, _), _, _)          => RaUnknown(e)
      case MatchesF(IndexF(_, _, _), _, _)               => RaUnknown(e)
      case MatchesF(CallF(_, _, _), _, _)                => RaUnknown(e)
      case MatchesF(PrimeF(_, _), _, _)                  => RaUnknown(e)
      case MatchesF(PreF(_, _), _, _)                    => RaUnknown(e)
      case MatchesF(WithF(_, _, _), _, _)                => RaUnknown(e)
      case MatchesF(IfF(_, _, _, _), _, _)               => RaUnknown(e)
      case MatchesF(LetF(_, _, _, _), _, _)              => RaUnknown(e)
      case MatchesF(LambdaF(_, _, _), _, _)              => RaUnknown(e)
      case MatchesF(ConstructorF(_, _, _), _, _)         => RaUnknown(e)
      case MatchesF(SetLiteralF(_, _), _, _)             => RaUnknown(e)
      case MatchesF(MapLiteralF(_, _), _, _)             => RaUnknown(e)
      case MatchesF(SetComprehensionF(_, _, _, _), _, _) => RaUnknown(e)
      case MatchesF(SeqLiteralF(_, _), _, _)             => RaUnknown(e)
      case MatchesF(MatchesF(_, _, _), _, _)             => RaUnknown(e)
      case MatchesF(IntLitF(_, _), _, _)                 => RaUnknown(e)
      case MatchesF(FloatLitF(_, _), _, _)               => RaUnknown(e)
      case MatchesF(StringLitF(_, _), _, _)              => RaUnknown(e)
      case MatchesF(BoolLitF(_, _), _, _)                => RaUnknown(e)
      case MatchesF(NoneLitF(_), _, _)                   => RaUnknown(e)
      case MatchesF(IdentifierF(n, _), pat, _) =>
        n == "value" match {
          case true  => RaMatches(pat)
          case false => RaMatchesIdent(n, pat)
        }
      case IntLitF(_, _)     => RaUnknown(e)
      case FloatLitF(_, _)   => RaUnknown(e)
      case StringLitF(_, _)  => RaUnknown(e)
      case BoolLitF(_, _)    => RaUnknown(e)
      case NoneLitF(_)       => RaUnknown(e)
      case IdentifierF(_, _) => RaUnknown(e)
    }

  def intAtom(atom: expr): (int_constraint, List[String]) =
    decomposeAtom(atom) match {
      case RaLenCmp(_, _) =>
        (emptyIntConstraint, List("unhandled int constraint"))
      case RaValueCmp(BAnd(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BOr(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BImplies(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BIff(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BEq(), n) =>
        (IntConstraint(Some[BigInt](n), Some[BigInt](n), Nil), Nil)
      case RaValueCmp(BNeq(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BLt(), n) =>
        (IntConstraint(None, Some[BigInt](minus_int(n, one_inta)), Nil), Nil)
      case RaValueCmp(BGt(), n) =>
        (IntConstraint(Some[BigInt](plus_int(n, one_inta)), None, Nil), Nil)
      case RaValueCmp(BLe(), n) =>
        (IntConstraint(None, Some[BigInt](n), Nil), Nil)
      case RaValueCmp(BGe(), n) =>
        (IntConstraint(Some[BigInt](n), None, Nil), Nil)
      case RaValueCmp(BIn(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BNotIn(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BSubset(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BUnion(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BIntersect(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BDiff(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BAdd(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BSub(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BMul(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaValueCmp(BDiv(), _) =>
        (emptyIntConstraint, List("unsupported int comparison"))
      case RaMatches(_) => (emptyIntConstraint, List("unhandled int constraint"))
      case RaMatchesIdent(_, _) =>
        (emptyIntConstraint, List("unhandled int constraint"))
      case RaPredCall(_) =>
        (emptyIntConstraint, List("unhandled int constraint"))
      case RaUnknown(_) => (emptyIntConstraint, List("unhandled int constraint"))
    }

  def isReadyIn(x0: (String, List[String]), names: List[String]): Boolean =
    (x0, names) match {
      case ((n, deps), names) =>
        list_all[String]((d: String) => d == n || !membera[String](names, d), deps)
    }

  def map2_opt(
      f: smt_term => smt_term => smt_term,
      a: Option[smt_term],
      b: Option[smt_term]
  ): Option[smt_term] =
    (a, b) match {
      case (None, _)          => None
      case (Some(_), None)    => None
      case (Some(x), Some(y)) => Some[smt_term](f(x)(y))
    }

  def is_builtin_const(nm: String): Boolean = nm == "now"

  def mpeKey(x0: map_entry): expr = x0 match {
    case MapEntryFull(x1, x2, x3) => x1
  }

  def prime_ident_name(x0: expr): Option[String] = x0 match {
    case PrimeF(e, uu)                    => identName(e)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def rel_insert_parts(op: bin_op, l: expr, r: expr): Option[(String, (String, String))] =
    equal_bin_op(op, BEq()) match {
      case true => (prime_ident_name(l), rel_insert_rhs(r)) match {
          case (None, _)       => None
          case (Some(_), None) => None
          case (Some(lrel), Some((brel, (kn, vn)))) =>
            lrel == brel match {
              case true  => Some[(String, (String, String))]((lrel, (kn, vn)))
              case false => None
            }
        }
      case false => None
    }

  def is_call(x0: expr): Boolean = x0 match {
    case CallF(uu, uv, uw)                => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(v, va)                  => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def mirrorBinOp(x0: bin_op): bin_op = x0 match {
    case BGe()        => BLe()
    case BLe()        => BGe()
    case BGt()        => BLt()
    case BLt()        => BGt()
    case BAnd()       => BAnd()
    case BOr()        => BOr()
    case BImplies()   => BImplies()
    case BIff()       => BIff()
    case BEq()        => BEq()
    case BNeq()       => BNeq()
    case BIn()        => BIn()
    case BNotIn()     => BNotIn()
    case BSubset()    => BSubset()
    case BUnion()     => BUnion()
    case BIntersect() => BIntersect()
    case BDiff()      => BDiff()
    case BAdd()       => BAdd()
    case BSub()       => BSub()
    case BMul()       => BMul()
    case BDiv()       => BDiv()
  }

  def rangeOf(e: expr): Option[(String, (bin_op, BigInt))] =
    e match {
      case BinaryOpF(op, l, r, _) =>
        (l, r) match {
          case (BinaryOpF(_, _, _, _), _)                     => None
          case (UnaryOpF(_, _, _), _)                         => None
          case (QuantifierF(_, _, _, _), _)                   => None
          case (SomeWrapF(_, _), _)                           => None
          case (TheF(_, _, _, _), _)                          => None
          case (FieldAccessF(_, _, _), _)                     => None
          case (EnumAccessF(_, _, _), _)                      => None
          case (IndexF(_, _, _), _)                           => None
          case (CallF(_, _, _), _)                            => None
          case (PrimeF(_, _), _)                              => None
          case (PreF(_, _), _)                                => None
          case (WithF(_, _, _), _)                            => None
          case (IfF(_, _, _, _), _)                           => None
          case (LetF(_, _, _, _), _)                          => None
          case (LambdaF(_, _, _), _)                          => None
          case (ConstructorF(_, _, _), _)                     => None
          case (SetLiteralF(_, _), _)                         => None
          case (MapLiteralF(_, _), _)                         => None
          case (SetComprehensionF(_, _, _, _), _)             => None
          case (SeqLiteralF(_, _), _)                         => None
          case (MatchesF(_, _, _), _)                         => None
          case (IntLitF(_, _), BinaryOpF(_, _, _, _))         => None
          case (IntLitF(_, _), UnaryOpF(_, _, _))             => None
          case (IntLitF(_, _), QuantifierF(_, _, _, _))       => None
          case (IntLitF(_, _), SomeWrapF(_, _))               => None
          case (IntLitF(_, _), TheF(_, _, _, _))              => None
          case (IntLitF(_, _), FieldAccessF(_, _, _))         => None
          case (IntLitF(_, _), EnumAccessF(_, _, _))          => None
          case (IntLitF(_, _), IndexF(_, _, _))               => None
          case (IntLitF(_, _), CallF(_, _, _))                => None
          case (IntLitF(_, _), PrimeF(_, _))                  => None
          case (IntLitF(_, _), PreF(_, _))                    => None
          case (IntLitF(_, _), WithF(_, _, _))                => None
          case (IntLitF(_, _), IfF(_, _, _, _))               => None
          case (IntLitF(_, _), LetF(_, _, _, _))              => None
          case (IntLitF(_, _), LambdaF(_, _, _))              => None
          case (IntLitF(_, _), ConstructorF(_, _, _))         => None
          case (IntLitF(_, _), SetLiteralF(_, _))             => None
          case (IntLitF(_, _), MapLiteralF(_, _))             => None
          case (IntLitF(_, _), SetComprehensionF(_, _, _, _)) => None
          case (IntLitF(_, _), SeqLiteralF(_, _))             => None
          case (IntLitF(_, _), MatchesF(_, _, _))             => None
          case (IntLitF(_, _), IntLitF(_, _))                 => None
          case (IntLitF(_, _), FloatLitF(_, _))               => None
          case (IntLitF(_, _), StringLitF(_, _))              => None
          case (IntLitF(_, _), BoolLitF(_, _))                => None
          case (IntLitF(_, _), NoneLitF(_))                   => None
          case (IntLitF(v, _), IdentifierF(n, _)) =>
            isComp(op) match {
              case true  => Some[(String, (bin_op, BigInt))]((n, (mirrorBinOp(op), v)))
              case false => None
            }
          case (FloatLitF(_, _), _)                               => None
          case (StringLitF(_, _), _)                              => None
          case (BoolLitF(_, _), _)                                => None
          case (NoneLitF(_), _)                                   => None
          case (IdentifierF(_, _), BinaryOpF(_, _, _, _))         => None
          case (IdentifierF(_, _), UnaryOpF(_, _, _))             => None
          case (IdentifierF(_, _), QuantifierF(_, _, _, _))       => None
          case (IdentifierF(_, _), SomeWrapF(_, _))               => None
          case (IdentifierF(_, _), TheF(_, _, _, _))              => None
          case (IdentifierF(_, _), FieldAccessF(_, _, _))         => None
          case (IdentifierF(_, _), EnumAccessF(_, _, _))          => None
          case (IdentifierF(_, _), IndexF(_, _, _))               => None
          case (IdentifierF(_, _), CallF(_, _, _))                => None
          case (IdentifierF(_, _), PrimeF(_, _))                  => None
          case (IdentifierF(_, _), PreF(_, _))                    => None
          case (IdentifierF(_, _), WithF(_, _, _))                => None
          case (IdentifierF(_, _), IfF(_, _, _, _))               => None
          case (IdentifierF(_, _), LetF(_, _, _, _))              => None
          case (IdentifierF(_, _), LambdaF(_, _, _))              => None
          case (IdentifierF(_, _), ConstructorF(_, _, _))         => None
          case (IdentifierF(_, _), SetLiteralF(_, _))             => None
          case (IdentifierF(_, _), MapLiteralF(_, _))             => None
          case (IdentifierF(_, _), SetComprehensionF(_, _, _, _)) => None
          case (IdentifierF(_, _), SeqLiteralF(_, _))             => None
          case (IdentifierF(_, _), MatchesF(_, _, _))             => None
          case (IdentifierF(n, _), IntLitF(v, _)) =>
            isComp(op) match {
              case true  => Some[(String, (bin_op, BigInt))]((n, (op, v)))
              case false => None
            }
          case (IdentifierF(_, _), FloatLitF(_, _))   => None
          case (IdentifierF(_, _), StringLitF(_, _))  => None
          case (IdentifierF(_, _), BoolLitF(_, _))    => None
          case (IdentifierF(_, _), NoneLitF(_))       => None
          case (IdentifierF(_, _), IdentifierF(_, _)) => None
        }
      case UnaryOpF(_, _, _)             => None
      case QuantifierF(_, _, _, _)       => None
      case SomeWrapF(_, _)               => None
      case TheF(_, _, _, _)              => None
      case FieldAccessF(_, _, _)         => None
      case EnumAccessF(_, _, _)          => None
      case IndexF(_, _, _)               => None
      case CallF(_, _, _)                => None
      case PrimeF(_, _)                  => None
      case PreF(_, _)                    => None
      case WithF(_, _, _)                => None
      case IfF(_, _, _, _)               => None
      case LetF(_, _, _, _)              => None
      case LambdaF(_, _, _)              => None
      case ConstructorF(_, _, _)         => None
      case SetLiteralF(_, _)             => None
      case MapLiteralF(_, _)             => None
      case SetComprehensionF(_, _, _, _) => None
      case SeqLiteralF(_, _)             => None
      case MatchesF(_, _, _)             => None
      case IntLitF(_, _)                 => None
      case FloatLitF(_, _)               => None
      case StringLitF(_, _)              => None
      case BoolLitF(_, _)                => None
      case NoneLitF(_)                   => None
      case IdentifierF(_, _)             => None
    }

  def entityParentFull(x0: entity_decl): Option[String] = x0 match {
    case EntityDeclFull(uu, p, uv, uw, ux) => p
  }

  def entityNameFull(x0: entity_decl): String = x0 match {
    case EntityDeclFull(n, uu, uv, uw, ux) => n
  }

  def entityByName(es: List[entity_decl], nm: String): Option[entity_decl] =
    map_of[String, entity_decl](
      map[entity_decl, (String, entity_decl)](
        (e: entity_decl) =>
          (entityNameFull(e), e),
        rev[entity_decl](es)
      ),
      nm
    )

  def chain_up(uu: List[entity_decl], f: nat, uv: String, uw: List[String]): List[entity_decl] =
    equal_nat(f, zero_nat) match {
      case true => Nil
      case false => entityByName(uu, uv) match {
          case None => Nil
          case Some(e) =>
            entityParentFull(e) match {
              case None => List(e)
              case Some(parent) =>
                membera[String](uw, parent) match {
                  case true => List(e)
                  case false => chain_up(uu, minus_nat(f, one_nat), parent, uv :: uw) ++
                      List(e)
                }
            }
        }
    }

  def typeName(x0: type_expr): Option[String] = x0 match {
    case NamedTypeF(n, uu)            => Some[String](n)
    case SetTypeF(v, va)              => None
    case MapTypeF(v, va, vb)          => None
    case SeqTypeF(v, va)              => None
    case OptionTypeF(v, va)           => None
    case RelationTypeF(v, va, vb, vc) => None
  }

  def isGetMethod(x0: http_method): Boolean = x0 match {
    case GET()    => true
    case POST()   => false
    case PUT()    => false
    case PATCH()  => false
    case DELETE() => false
  }

  def indexColumns(x0: index_spec): List[String] = x0 match {
    case IndexSpec(uu, cs, uv, uw) => cs
  }

  def schemaTables(x0: database_schema): List[table_spec] = x0 match {
    case DatabaseSchema(ts, uu) => ts
  }

  def tableColumns(x0: table_spec): List[column_spec] = x0 match {
    case TableSpec(uu, uv, cs, uw, ux, uy, uz) => cs
  }

  def tableIndexes(x0: table_spec): List[index_spec] = x0 match {
    case TableSpec(uu, uv, uw, ux, uy, uz, ixs) => ixs
  }

  def range_arg(x0: expr): Option[String] = x0 match {
    case CallF(IdentifierF(nm, uu), List(IdentifierF(rel, uv)), uw) =>
      nm == "range" || nm == "ran" match {
        case true  => Some[String](rel)
        case false => None
      }
    case BinaryOpF(v, va, vb, vc)                              => None
    case UnaryOpF(v, va, vb)                                   => None
    case QuantifierF(v, va, vb, vc)                            => None
    case SomeWrapF(v, va)                                      => None
    case TheF(v, va, vb, vc)                                   => None
    case FieldAccessF(v, va, vb)                               => None
    case EnumAccessF(v, va, vb)                                => None
    case IndexF(v, va, vb)                                     => None
    case CallF(BinaryOpF(vc, vd, ve, vf), va, vb)              => None
    case CallF(UnaryOpF(vc, vd, ve), va, vb)                   => None
    case CallF(QuantifierF(vc, vd, ve, vf), va, vb)            => None
    case CallF(SomeWrapF(vc, vd), va, vb)                      => None
    case CallF(TheF(vc, vd, ve, vf), va, vb)                   => None
    case CallF(FieldAccessF(vc, vd, ve), va, vb)               => None
    case CallF(EnumAccessF(vc, vd, ve), va, vb)                => None
    case CallF(IndexF(vc, vd, ve), va, vb)                     => None
    case CallF(CallF(vc, vd, ve), va, vb)                      => None
    case CallF(PrimeF(vc, vd), va, vb)                         => None
    case CallF(PreF(vc, vd), va, vb)                           => None
    case CallF(WithF(vc, vd, ve), va, vb)                      => None
    case CallF(IfF(vc, vd, ve, vf), va, vb)                    => None
    case CallF(LetF(vc, vd, ve, vf), va, vb)                   => None
    case CallF(LambdaF(vc, vd, ve), va, vb)                    => None
    case CallF(ConstructorF(vc, vd, ve), va, vb)               => None
    case CallF(SetLiteralF(vc, vd), va, vb)                    => None
    case CallF(MapLiteralF(vc, vd), va, vb)                    => None
    case CallF(SetComprehensionF(vc, vd, ve, vf), va, vb)      => None
    case CallF(SeqLiteralF(vc, vd), va, vb)                    => None
    case CallF(MatchesF(vc, vd, ve), va, vb)                   => None
    case CallF(IntLitF(vc, vd), va, vb)                        => None
    case CallF(FloatLitF(vc, vd), va, vb)                      => None
    case CallF(StringLitF(vc, vd), va, vb)                     => None
    case CallF(BoolLitF(vc, vd), va, vb)                       => None
    case CallF(NoneLitF(vc), va, vb)                           => None
    case CallF(v, Nil, vb)                                     => None
    case CallF(v, BinaryOpF(ve, vf, vg, vh) :: vd, vb)         => None
    case CallF(v, UnaryOpF(ve, vf, vg) :: vd, vb)              => None
    case CallF(v, QuantifierF(ve, vf, vg, vh) :: vd, vb)       => None
    case CallF(v, SomeWrapF(ve, vf) :: vd, vb)                 => None
    case CallF(v, TheF(ve, vf, vg, vh) :: vd, vb)              => None
    case CallF(v, FieldAccessF(ve, vf, vg) :: vd, vb)          => None
    case CallF(v, EnumAccessF(ve, vf, vg) :: vd, vb)           => None
    case CallF(v, IndexF(ve, vf, vg) :: vd, vb)                => None
    case CallF(v, CallF(ve, vf, vg) :: vd, vb)                 => None
    case CallF(v, PrimeF(ve, vf) :: vd, vb)                    => None
    case CallF(v, PreF(ve, vf) :: vd, vb)                      => None
    case CallF(v, WithF(ve, vf, vg) :: vd, vb)                 => None
    case CallF(v, IfF(ve, vf, vg, vh) :: vd, vb)               => None
    case CallF(v, LetF(ve, vf, vg, vh) :: vd, vb)              => None
    case CallF(v, LambdaF(ve, vf, vg) :: vd, vb)               => None
    case CallF(v, ConstructorF(ve, vf, vg) :: vd, vb)          => None
    case CallF(v, SetLiteralF(ve, vf) :: vd, vb)               => None
    case CallF(v, MapLiteralF(ve, vf) :: vd, vb)               => None
    case CallF(v, SetComprehensionF(ve, vf, vg, vh) :: vd, vb) => None
    case CallF(v, SeqLiteralF(ve, vf) :: vd, vb)               => None
    case CallF(v, MatchesF(ve, vf, vg) :: vd, vb)              => None
    case CallF(v, IntLitF(ve, vf) :: vd, vb)                   => None
    case CallF(v, FloatLitF(ve, vf) :: vd, vb)                 => None
    case CallF(v, StringLitF(ve, vf) :: vd, vb)                => None
    case CallF(v, BoolLitF(ve, vf) :: vd, vb)                  => None
    case CallF(v, NoneLitF(ve) :: vd, vb)                      => None
    case CallF(v, vc :: ve :: vf, vb)                          => None
    case PrimeF(v, va)                                         => None
    case PreF(v, va)                                           => None
    case WithF(v, va, vb)                                      => None
    case IfF(v, va, vb, vc)                                    => None
    case LetF(v, va, vb, vc)                                   => None
    case LambdaF(v, va, vb)                                    => None
    case ConstructorF(v, va, vb)                               => None
    case SetLiteralF(v, va)                                    => None
    case MapLiteralF(v, va)                                    => None
    case SetComprehensionF(v, va, vb, vc)                      => None
    case SeqLiteralF(v, va)                                    => None
    case MatchesF(v, va, vb)                                   => None
    case IntLitF(v, va)                                        => None
    case FloatLitF(v, va)                                      => None
    case StringLitF(v, va)                                     => None
    case BoolLitF(v, va)                                       => None
    case NoneLitF(v)                                           => None
    case IdentifierF(v, va)                                    => None
  }

  def translate_forall_step(
      enums: List[String],
      x1: quantifier_binding,
      body: smt_term
  ): Option[smt_term] =
    (enums, x1, body) match {
      case (enums, QuantifierBindingFull(v, d, uu, uv), body) =>
        d match {
          case BinaryOpF(_, _, _, _)                    => None
          case UnaryOpF(_, _, _)                        => None
          case QuantifierF(_, _, _, _)                  => None
          case SomeWrapF(_, _)                          => None
          case TheF(_, _, _, _)                         => None
          case FieldAccessF(_, _, _)                    => None
          case EnumAccessF(_, _, _)                     => None
          case IndexF(_, _, _)                          => None
          case CallF(_, _, _)                           => None
          case PrimeF(BinaryOpF(_, _, _, _), _)         => None
          case PrimeF(UnaryOpF(_, _, _), _)             => None
          case PrimeF(QuantifierF(_, _, _, _), _)       => None
          case PrimeF(SomeWrapF(_, _), _)               => None
          case PrimeF(TheF(_, _, _, _), _)              => None
          case PrimeF(FieldAccessF(_, _, _), _)         => None
          case PrimeF(EnumAccessF(_, _, _), _)          => None
          case PrimeF(IndexF(_, _, _), _)               => None
          case PrimeF(CallF(_, _, _), _)                => None
          case PrimeF(PrimeF(_, _), _)                  => None
          case PrimeF(PreF(_, _), _)                    => None
          case PrimeF(WithF(_, _, _), _)                => None
          case PrimeF(IfF(_, _, _, _), _)               => None
          case PrimeF(LetF(_, _, _, _), _)              => None
          case PrimeF(LambdaF(_, _, _), _)              => None
          case PrimeF(ConstructorF(_, _, _), _)         => None
          case PrimeF(SetLiteralF(_, _), _)             => None
          case PrimeF(MapLiteralF(_, _), _)             => None
          case PrimeF(SetComprehensionF(_, _, _, _), _) => None
          case PrimeF(SeqLiteralF(_, _), _)             => None
          case PrimeF(MatchesF(_, _, _), _)             => None
          case PrimeF(IntLitF(_, _), _)                 => None
          case PrimeF(FloatLitF(_, _), _)               => None
          case PrimeF(StringLitF(_, _), _)              => None
          case PrimeF(BoolLitF(_, _), _)                => None
          case PrimeF(NoneLitF(_), _)                   => None
          case PrimeF(IdentifierF(dnm, _), _) =>
            Some[smt_term](TPrime(TForallRel(v, dnm, body)))
          case PreF(BinaryOpF(_, _, _, _), _)         => None
          case PreF(UnaryOpF(_, _, _), _)             => None
          case PreF(QuantifierF(_, _, _, _), _)       => None
          case PreF(SomeWrapF(_, _), _)               => None
          case PreF(TheF(_, _, _, _), _)              => None
          case PreF(FieldAccessF(_, _, _), _)         => None
          case PreF(EnumAccessF(_, _, _), _)          => None
          case PreF(IndexF(_, _, _), _)               => None
          case PreF(CallF(_, _, _), _)                => None
          case PreF(PrimeF(_, _), _)                  => None
          case PreF(PreF(_, _), _)                    => None
          case PreF(WithF(_, _, _), _)                => None
          case PreF(IfF(_, _, _, _), _)               => None
          case PreF(LetF(_, _, _, _), _)              => None
          case PreF(LambdaF(_, _, _), _)              => None
          case PreF(ConstructorF(_, _, _), _)         => None
          case PreF(SetLiteralF(_, _), _)             => None
          case PreF(MapLiteralF(_, _), _)             => None
          case PreF(SetComprehensionF(_, _, _, _), _) => None
          case PreF(SeqLiteralF(_, _), _)             => None
          case PreF(MatchesF(_, _, _), _)             => None
          case PreF(IntLitF(_, _), _)                 => None
          case PreF(FloatLitF(_, _), _)               => None
          case PreF(StringLitF(_, _), _)              => None
          case PreF(BoolLitF(_, _), _)                => None
          case PreF(NoneLitF(_), _)                   => None
          case PreF(IdentifierF(dnm, _), _) =>
            Some[smt_term](TPre(TForallRel(v, dnm, body)))
          case WithF(_, _, _)                => None
          case IfF(_, _, _, _)               => None
          case LetF(_, _, _, _)              => None
          case LambdaF(_, _, _)              => None
          case ConstructorF(_, _, _)         => None
          case SetLiteralF(_, _)             => None
          case MapLiteralF(_, _)             => None
          case SetComprehensionF(_, _, _, _) => None
          case SeqLiteralF(_, _)             => None
          case MatchesF(_, _, _)             => None
          case IntLitF(_, _)                 => None
          case FloatLitF(_, _)               => None
          case StringLitF(_, _)              => None
          case BoolLitF(_, _)                => None
          case NoneLitF(_)                   => None
          case IdentifierF(dnm, _) =>
            string_in_list(dnm, enums) match {
              case true  => Some[smt_term](TForallEnum(v, dnm, body))
              case false => Some[smt_term](TForallRel(v, dnm, body))
            }
        }
    }

  def translate_forall_bindings(
      uu: List[String],
      x1: List[quantifier_binding],
      uv: smt_term
  ): Option[smt_term] =
    (uu, x1, uv) match {
      case (uu, Nil, uv)          => None
      case (enums, List(b), body) => translate_forall_step(enums, b, body)
      case (enums, b :: v :: va, body) =>
        translate_forall_bindings(enums, v :: va, body) match {
          case None    => None
          case Some(a) => translate_forall_step(enums, b, a)
        }
    }

  def translate_dom_eq(xrel: String, yrel: String): smt_term = {
    val k = fresh_var("x", List(xrel, yrel)): String;
    TAnd(TForallRel(k, xrel, TInDom(yrel, TVar(k))), TForallRel(k, yrel, TInDom(xrel, TVar(k))))
  }

  def translate_beq_dom_or_none(l: expr, r: expr): Option[smt_term] =
    (dom_arg(l), dom_arg(r)) match {
      case (None, _)          => None
      case (Some(_), None)    => None
      case (Some(x), Some(y)) => Some[smt_term](translate_dom_eq(x, y))
    }

  def translate_set_comp_eq(
      enums: List[String],
      vara: String,
      dnm: String,
      setE: smt_term,
      predE: smt_term
  ): smt_term = {
    val f    = fresh_var("s", vara :: smt_var_list(predE)): String
    val memX = TSetMember(TVar(vara), TVar(f)): smt_term
    val memD =
      (string_in_list(dnm, enums) match {
        case true  => BLit(true)
        case false => TInDom(dnm, TVar(vara))
      }): smt_term
    val dir1 =
      (string_in_list(dnm, enums) match {
        case true  => TForallEnum(vara, dnm, TImplies(predE, memX))
        case false => TForallRel(vara, dnm, TImplies(predE, memX))
      }): smt_term
    val dir2 = TForallSet(vara, TVar(f), TAnd(memD, predE)): smt_term;
    TLetIn(f, setE, TAnd(dir1, dir2))
  }

  def identName_smt(x0: smt_term): Option[String] = x0 match {
    case TVar(rel)              => Some[String](rel)
    case BLit(v)                => None
    case ILit(v)                => None
    case RLit(v)                => None
    case EnumElemConst(v, va)   => None
    case TNot(v)                => None
    case TAnd(v, va)            => None
    case TOr(v, va)             => None
    case TImplies(v, va)        => None
    case TEq(v, va)             => None
    case TLt(v, va)             => None
    case TNeg(v)                => None
    case TAdd(v, va)            => None
    case TSub(v, va)            => None
    case TMul(v, va)            => None
    case TDiv(v, va)            => None
    case TInDom(v, va)          => None
    case TCardRel(v)            => None
    case TCard(v)               => None
    case TLetIn(v, va, vb)      => None
    case TForallEnum(v, va, vb) => None
    case TForallRel(v, va, vb)  => None
    case TExistsRel(v, va, vb)  => None
    case TTheRel(v, va, vb)     => None
    case TEntityBase(v)         => None
    case TForallSet(v, va, vb)  => None
    case TTheSet(v, va, vb)     => None
    case TIndexRel(v, va)       => None
    case TFieldAccess(v, va)    => None
    case TSetEmpty()            => None
    case TSetInsert(v, va)      => None
    case TSetMember(v, va)      => None
    case TSetUnion(v, va)       => None
    case TSetIntersect(v, va)   => None
    case TSetDiff(v, va)        => None
    case TPrime(v)              => None
    case TPre(v)                => None
    case TWithRec(v, va, vb)    => None
    case TIte(v, va, vb)        => None
    case TNone()                => None
    case TSome(v)               => None
    case TStrLit(v)             => None
    case TMatches(v, va)        => None
    case TUStrPred(v, va)       => None
    case TUStrFunc(v, va)       => None
    case TUIntFunc(v, va)       => None
    case TStrLen(v)             => None
    case TUConst(v)             => None
    case TSeqEmpty()            => None
    case TSeqCons(v, va)        => None
    case TMapEmpty()            => None
    case TMapCons(v, va, vb)    => None
    case TSum(v, va)            => None
  }

  def peel_relation_ref_smt(x0: smt_term): Option[String] = x0 match {
    case TVar(rel)              => Some[String](rel)
    case TPre(b)                => identName_smt(b)
    case TPrime(b)              => identName_smt(b)
    case BLit(v)                => None
    case ILit(v)                => None
    case RLit(v)                => None
    case EnumElemConst(v, va)   => None
    case TNot(v)                => None
    case TAnd(v, va)            => None
    case TOr(v, va)             => None
    case TImplies(v, va)        => None
    case TEq(v, va)             => None
    case TLt(v, va)             => None
    case TNeg(v)                => None
    case TAdd(v, va)            => None
    case TSub(v, va)            => None
    case TMul(v, va)            => None
    case TDiv(v, va)            => None
    case TInDom(v, va)          => None
    case TCardRel(v)            => None
    case TCard(v)               => None
    case TLetIn(v, va, vb)      => None
    case TForallEnum(v, va, vb) => None
    case TForallRel(v, va, vb)  => None
    case TExistsRel(v, va, vb)  => None
    case TTheRel(v, va, vb)     => None
    case TEntityBase(v)         => None
    case TForallSet(v, va, vb)  => None
    case TTheSet(v, va, vb)     => None
    case TIndexRel(v, va)       => None
    case TFieldAccess(v, va)    => None
    case TSetEmpty()            => None
    case TSetInsert(v, va)      => None
    case TSetMember(v, va)      => None
    case TSetUnion(v, va)       => None
    case TSetIntersect(v, va)   => None
    case TSetDiff(v, va)        => None
    case TWithRec(v, va, vb)    => None
    case TIte(v, va, vb)        => None
    case TNone()                => None
    case TSome(v)               => None
    case TStrLit(v)             => None
    case TMatches(v, va)        => None
    case TUStrPred(v, va)       => None
    case TUStrFunc(v, va)       => None
    case TUIntFunc(v, va)       => None
    case TStrLen(v)             => None
    case TUConst(v)             => None
    case TSeqEmpty()            => None
    case TSeqCons(v, va)        => None
    case TMapEmpty()            => None
    case TMapCons(v, va, vb)    => None
    case TSum(v, va)            => None
  }

  def translate_range_eq(setE: smt_term, rel: String): smt_term = {
    val k    = fresh_var("k", smt_var_list(setE)): String
    val vv   = fresh_var("v", k :: smt_var_list(setE)): String
    val valK = TIndexRel(TVar(rel), TVar(k)): smt_term;
    TAnd(
      TForallRel(k, rel, TSetMember(valK, setE)),
      TForallSet(vv, setE, TExistsRel(k, rel, TEq(valK, TVar(vv))))
    )
  }

  def prime_rel_name(x0: expr): Option[String] = x0 match {
    case PrimeF(e, uu)                    => identName(e)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def pre_rel_name(x0: expr): Option[String] = x0 match {
    case PreF(e, uu)                      => identName(e)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def is_builtin_int_func(nm: String): Boolean = nm == "days"

  def comp_parts(x0: expr): Option[(String, (String, expr))] = x0 match {
    case SetComprehensionF(vara, d, p, uu) =>
      d match {
        case BinaryOpF(_, _, _, _)         => None
        case UnaryOpF(_, _, _)             => None
        case QuantifierF(_, _, _, _)       => None
        case SomeWrapF(_, _)               => None
        case TheF(_, _, _, _)              => None
        case FieldAccessF(_, _, _)         => None
        case EnumAccessF(_, _, _)          => None
        case IndexF(_, _, _)               => None
        case CallF(_, _, _)                => None
        case PrimeF(_, _)                  => None
        case PreF(_, _)                    => None
        case WithF(_, _, _)                => None
        case IfF(_, _, _, _)               => None
        case LetF(_, _, _, _)              => None
        case LambdaF(_, _, _)              => None
        case ConstructorF(_, _, _)         => None
        case SetLiteralF(_, _)             => None
        case MapLiteralF(_, _)             => None
        case SetComprehensionF(_, _, _, _) => None
        case SeqLiteralF(_, _)             => None
        case MatchesF(_, _, _)             => None
        case IntLitF(_, _)                 => None
        case FloatLitF(_, _)               => None
        case StringLitF(_, _)              => None
        case BoolLitF(_, _)                => None
        case NoneLitF(_)                   => None
        case IdentifierF(dnm, _) =>
          Some[(String, (String, expr))]((vara, (dnm, p)))
      }
    case BinaryOpF(v, va, vb, vc)   => None
    case UnaryOpF(v, va, vb)        => None
    case QuantifierF(v, va, vb, vc) => None
    case SomeWrapF(v, va)           => None
    case TheF(v, va, vb, vc)        => None
    case FieldAccessF(v, va, vb)    => None
    case EnumAccessF(v, va, vb)     => None
    case IndexF(v, va, vb)          => None
    case CallF(v, va, vb)           => None
    case PrimeF(v, va)              => None
    case PreF(v, va)                => None
    case WithF(v, va, vb)           => None
    case IfF(v, va, vb, vc)         => None
    case LetF(v, va, vb, vc)        => None
    case LambdaF(v, va, vb)         => None
    case ConstructorF(v, va, vb)    => None
    case SetLiteralF(v, va)         => None
    case MapLiteralF(v, va)         => None
    case SeqLiteralF(v, va)         => None
    case MatchesF(v, va, vb)        => None
    case IntLitF(v, va)             => None
    case FloatLitF(v, va)           => None
    case StringLitF(v, va)          => None
    case BoolLitF(v, va)            => None
    case NoneLitF(v)                => None
    case IdentifierF(v, va)         => None
  }

  def translate_with_assigns(
      wk: List[String],
      x1: List[field_assign],
      base: smt_term
  ): Option[smt_term] =
    (wk, x1, base) match {
      case (wk, Nil, base) => Some[smt_term](base)
      case (enums, FieldAssignFull(fld, v, wl) :: rest, base) =>
        translate(enums, v) match {
          case None => None
          case Some(vt) =>
            translate_with_assigns(enums, rest, TWithRec(base, fld, vt))
        }
    }

  def translateMapEntries(wn: List[String], x1: List[map_entry]): Option[smt_term] =
    (wn, x1) match {
      case (wn, Nil) => Some[smt_term](TMapEmpty())
      case (enums, MapEntryFull(k, v, wo) :: rest) =>
        (translate(enums, k), (translate(enums, v), translateMapEntries(enums, rest))) match {
          case (None, _)                  => None
          case (Some(_), (None, _))       => None
          case (Some(_), (Some(_), None)) => None
          case (Some(kt), (Some(vt), Some(mt))) =>
            Some[smt_term](TMapCons(kt, vt, mt))
        }
    }

  def translateSetList(wj: List[String], x1: List[expr]): Option[smt_term] =
    (wj, x1) match {
      case (wj, Nil) => Some[smt_term](TSetEmpty())
      case (enums, e :: rest) =>
        (translate(enums, e), translateSetList(enums, rest)) match {
          case (None, _)            => None
          case (Some(_), None)      => None
          case (Some(et), Some(st)) => Some[smt_term](TSetInsert(et, st))
        }
    }

  def translateSeqList(wm: List[String], x1: List[expr]): Option[smt_term] =
    (wm, x1) match {
      case (wm, Nil) => Some[smt_term](TSeqEmpty())
      case (enums, e :: rest) =>
        (translate(enums, e), translateSeqList(enums, rest)) match {
          case (None, _)            => None
          case (Some(_), None)      => None
          case (Some(et), Some(st)) => Some[smt_term](TSeqCons(et, st))
        }
    }

  def translate(uu: List[String], x1: expr): Option[smt_term] = (uu, x1) match {
    case (uu, BoolLitF(b, uv))    => Some[smt_term](BLit(b))
    case (uw, IntLitF(n, ux))     => Some[smt_term](ILit(n))
    case (uy, IdentifierF(x, uz)) => Some[smt_term](TVar(x))
    case (va, FloatLitF(s, vb)) =>
      map_option[rat, smt_term]((a: rat) => RLit(a), decimalToRat(s))
    case (vc, StringLitF(v, vd))   => Some[smt_term](TStrLit(v))
    case (ve, NoneLitF(vf))        => Some[smt_term](TNone())
    case (vg, LambdaF(vh, vi, vj)) => None
    case (enums, CallF(callee, args, vk)) =>
      (callee, args) match {
        case (BinaryOpF(_, _, _, _), _)         => None
        case (UnaryOpF(_, _, _), _)             => None
        case (QuantifierF(_, _, _, _), _)       => None
        case (SomeWrapF(_, _), _)               => None
        case (TheF(_, _, _, _), _)              => None
        case (FieldAccessF(_, _, _), _)         => None
        case (EnumAccessF(_, _, _), _)          => None
        case (IndexF(_, _, _), _)               => None
        case (CallF(_, _, _), _)                => None
        case (PrimeF(_, _), _)                  => None
        case (PreF(_, _), _)                    => None
        case (WithF(_, _, _), _)                => None
        case (IfF(_, _, _, _), _)               => None
        case (LetF(_, _, _, _), _)              => None
        case (LambdaF(_, _, _), _)              => None
        case (ConstructorF(_, _, _), _)         => None
        case (SetLiteralF(_, _), _)             => None
        case (MapLiteralF(_, _), _)             => None
        case (SetComprehensionF(_, _, _, _), _) => None
        case (SeqLiteralF(_, _), _)             => None
        case (MatchesF(_, _, _), _)             => None
        case (IntLitF(_, _), _)                 => None
        case (FloatLitF(_, _), _)               => None
        case (StringLitF(_, _), _)              => None
        case (BoolLitF(_, _), _)                => None
        case (NoneLitF(_), _)                   => None
        case (IdentifierF(nm, _), Nil) =>
          is_builtin_const(nm) match {
            case true  => Some[smt_term](TUConst(nm))
            case false => None
          }
        case (IdentifierF(nm, _), List(arg)) =>
          is_builtin_pred(nm) match {
            case true => map_option[smt_term, smt_term](
                (a: smt_term) =>
                  TUStrPred(nm, a),
                translate(enums, arg)
              )
            case false => is_builtin_func(nm) match {
                case true => map_option[smt_term, smt_term](
                    (a: smt_term) => TUStrFunc(nm, a),
                    translate(enums, arg)
                  )
                case false => is_builtin_int_func(nm) match {
                    case true => map_option[smt_term, smt_term](
                        (a: smt_term) => TUIntFunc(nm, a),
                        translate(enums, arg)
                      )
                    case false => nm == "len" match {
                        case true => map_option[smt_term, smt_term](
                            (a: smt_term) => TStrLen(a),
                            translate(enums, arg)
                          )
                        case false => None
                      }
                  }
              }
          }
        case (IdentifierF(_, _), _ :: BinaryOpF(_, _, _, _) :: _)   => None
        case (IdentifierF(_, _), _ :: UnaryOpF(_, _, _) :: _)       => None
        case (IdentifierF(_, _), _ :: QuantifierF(_, _, _, _) :: _) => None
        case (IdentifierF(_, _), _ :: SomeWrapF(_, _) :: _)         => None
        case (IdentifierF(_, _), _ :: TheF(_, _, _, _) :: _)        => None
        case (IdentifierF(_, _), _ :: FieldAccessF(_, _, _) :: _)   => None
        case (IdentifierF(_, _), _ :: EnumAccessF(_, _, _) :: _)    => None
        case (IdentifierF(_, _), _ :: IndexF(_, _, _) :: _)         => None
        case (IdentifierF(_, _), _ :: CallF(_, _, _) :: _)          => None
        case (IdentifierF(_, _), _ :: PrimeF(_, _) :: _)            => None
        case (IdentifierF(_, _), _ :: PreF(_, _) :: _)              => None
        case (IdentifierF(_, _), _ :: WithF(_, _, _) :: _)          => None
        case (IdentifierF(_, _), _ :: IfF(_, _, _, _) :: _)         => None
        case (IdentifierF(_, _), _ :: LetF(_, _, _, _) :: _)        => None
        case (IdentifierF(nm, _), List(arg, LambdaF(p, body, _))) =>
          nm == "sum" match {
            case true => (translate(enums, arg), translate(enums, body)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(c), Some(b)) =>
                  list_all[String]((v: String) => v == p, smt_var_list(b)) match {
                    case true  => Some[smt_term](TSum(c, b))
                    case false => None
                  }
              }
            case false => None
          }
        case (IdentifierF(_, _), _ :: LambdaF(_, _, _) :: _ :: _)         => None
        case (IdentifierF(_, _), _ :: ConstructorF(_, _, _) :: _)         => None
        case (IdentifierF(_, _), _ :: SetLiteralF(_, _) :: _)             => None
        case (IdentifierF(_, _), _ :: MapLiteralF(_, _) :: _)             => None
        case (IdentifierF(_, _), _ :: SetComprehensionF(_, _, _, _) :: _) => None
        case (IdentifierF(_, _), _ :: SeqLiteralF(_, _) :: _)             => None
        case (IdentifierF(_, _), _ :: MatchesF(_, _, _) :: _)             => None
        case (IdentifierF(_, _), _ :: IntLitF(_, _) :: _)                 => None
        case (IdentifierF(_, _), _ :: FloatLitF(_, _) :: _)               => None
        case (IdentifierF(_, _), _ :: StringLitF(_, _) :: _)              => None
        case (IdentifierF(_, _), _ :: BoolLitF(_, _) :: _)                => None
        case (IdentifierF(_, _), _ :: NoneLitF(_) :: _)                   => None
        case (IdentifierF(_, _), _ :: IdentifierF(_, _) :: _)             => None
      }
    case (enums, ConstructorF(name, fas, vl)) =>
      translate_with_assigns(enums, fas, TEntityBase(name))
    case (vm, SetComprehensionF(vn, vo, vp, vq)) => None
    case (enums, TheF(vara, dm, body, vr)) =>
      dm match {
        case BinaryOpF(_, _, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case UnaryOpF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case QuantifierF(_, _, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case SomeWrapF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case TheF(_, _, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case FieldAccessF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case EnumAccessF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case IndexF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case CallF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case PrimeF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case PreF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case WithF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case IfF(_, _, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case LetF(_, _, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case LambdaF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case ConstructorF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case SetLiteralF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case MapLiteralF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case SetComprehensionF(_, _, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case SeqLiteralF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case MatchesF(_, _, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case IntLitF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case FloatLitF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case StringLitF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case BoolLitF(_, _) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case NoneLitF(_) =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TTheSet(vara, a, b),
            translate(enums, dm),
            translate(enums, body)
          )
        case IdentifierF(rel, _) =>
          string_in_list(rel, enums) match {
            case true => None
            case false => map_option[smt_term, smt_term](
                (a: smt_term) =>
                  TTheRel(vara, rel, a),
                translate(enums, body)
              )
          }
      }
    case (enums, MatchesF(e, pat, vs)) =>
      map_option[smt_term, smt_term]((ea: smt_term) => TMatches(ea, pat), translate(enums, e))
    case (enums, QuantifierF(k, bs, body, vt)) =>
      translate(enums, body) match {
        case None => None
        case Some(bodya) =>
          k match {
            case QAll() =>
              bs match {
                case Nil => translate_forall_bindings(enums, bs, bodya)
                case List(QuantifierBindingFull(v, d, _, _)) =>
                  d match {
                    case BinaryOpF(_, _, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case UnaryOpF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case QuantifierF(_, _, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case SomeWrapF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case TheF(_, _, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case FieldAccessF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case EnumAccessF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case IndexF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case CallF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case PrimeF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case PreF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case WithF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case IfF(_, _, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case LetF(_, _, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case LambdaF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case ConstructorF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case SetLiteralF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case MapLiteralF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case SetComprehensionF(_, _, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case SeqLiteralF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case MatchesF(_, _, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case IntLitF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case FloatLitF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case StringLitF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case BoolLitF(_, _) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case NoneLitF(_) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, bodya),
                        translate(enums, d)
                      )
                    case IdentifierF(_, _) =>
                      translate_forall_bindings(enums, bs, bodya)
                  }
                case QuantifierBindingFull(_, _, _, _) :: _ :: _ =>
                  translate_forall_bindings(enums, bs, bodya)
              }
            case QSome() =>
              translate_forall_bindings(enums, bs, TNot(bodya)) match {
                case None =>
                  bs match {
                    case Nil => None
                    case List(QuantifierBindingFull(v, d, _, _)) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TNot(TForallSet(v, da, TNot(bodya))),
                        translate(enums, d)
                      )
                    case QuantifierBindingFull(_, _, _, _) :: _ :: _ => None
                  }
                case Some(t) => Some[smt_term](TNot(t))
              }
            case QNo() =>
              translate_forall_bindings(enums, bs, TNot(bodya)) match {
                case None =>
                  bs match {
                    case Nil => None
                    case List(QuantifierBindingFull(v, d, _, _)) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TForallSet(v, da, TNot(bodya)),
                        translate(enums, d)
                      )
                    case QuantifierBindingFull(_, _, _, _) :: _ :: _ => None
                  }
                case Some(a) => Some[smt_term](a)
              }
            case QExists() =>
              translate_forall_bindings(enums, bs, TNot(bodya)) match {
                case None =>
                  bs match {
                    case Nil => None
                    case List(QuantifierBindingFull(v, d, _, _)) =>
                      map_option[smt_term, smt_term](
                        (da: smt_term) =>
                          TNot(TForallSet(v, da, TNot(bodya))),
                        translate(enums, d)
                      )
                    case QuantifierBindingFull(_, _, _, _) :: _ :: _ => None
                  }
                case Some(t) => Some[smt_term](TNot(t))
              }
          }
      }
    case (enums, UnaryOpF(op, e, vu)) =>
      op match {
        case UNot() =>
          map_option[smt_term, smt_term]((a: smt_term) => TNot(a), translate(enums, e))
        case UNegate() =>
          map_option[smt_term, smt_term]((a: smt_term) => TNeg(a), translate(enums, e))
        case UCardinality() =>
          e match {
            case BinaryOpF(_, _, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case UnaryOpF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case QuantifierF(_, _, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case SomeWrapF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case TheF(_, _, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case FieldAccessF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case EnumAccessF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case IndexF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case CallF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(BinaryOpF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(UnaryOpF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(QuantifierF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(SomeWrapF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(TheF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(FieldAccessF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(EnumAccessF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(IndexF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(CallF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(PrimeF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(PreF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(WithF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(IfF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(LetF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(LambdaF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(ConstructorF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(SetLiteralF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(MapLiteralF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(SetComprehensionF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(SeqLiteralF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(MatchesF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(IntLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(FloatLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(StringLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(BoolLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(NoneLitF(_), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PrimeF(IdentifierF(x, _), _) =>
              Some[smt_term](TPrime(TCardRel(x)))
            case PreF(BinaryOpF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(UnaryOpF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(QuantifierF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(SomeWrapF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(TheF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(FieldAccessF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(EnumAccessF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(IndexF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(CallF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(PrimeF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(PreF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(WithF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(IfF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(LetF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(LambdaF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(ConstructorF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(SetLiteralF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(MapLiteralF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(SetComprehensionF(_, _, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(SeqLiteralF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(MatchesF(_, _, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(IntLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(FloatLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(StringLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(BoolLitF(_, _), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(NoneLitF(_), _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case PreF(IdentifierF(x, _), _) => Some[smt_term](TPre(TCardRel(x)))
            case WithF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case IfF(_, _, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case LetF(_, _, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case LambdaF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case ConstructorF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case SetLiteralF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case MapLiteralF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case SetComprehensionF(_, _, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case SeqLiteralF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case MatchesF(_, _, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case IntLitF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case FloatLitF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case StringLitF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case BoolLitF(_, _) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case NoneLitF(_) =>
              map_option[smt_term, smt_term]((a: smt_term) => TCard(a), translate(enums, e))
            case IdentifierF(x, _) => Some[smt_term](TCardRel(x))
          }
        case UPower() => None
      }
    case (enums, BinaryOpF(op, l, r, vv)) =>
      op match {
        case BAnd() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TAnd(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BOr() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TOr(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BImplies() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TImplies(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BIff() =>
          map2_opt(
            (lt: smt_term) =>
              (rt: smt_term) =>
                TAnd(TImplies(lt, rt), TImplies(rt, lt)),
            translate(enums, l),
            translate(enums, r)
          )
        case BEq() =>
          translate_beq_dom_or_none(l, r) match {
            case None =>
              rel_insert_parts(BEq(), l, r) match {
                case None =>
                  range_arg(r) match {
                    case None =>
                      comp_parts(r) match {
                        case None =>
                          map2_opt(
                            (a: smt_term) =>
                              (b: smt_term) =>
                                TEq(a, b),
                            translate(enums, l),
                            translate(enums, r)
                          )
                        case Some((vara, (dnm, p))) =>
                          map2_opt(
                            (a: smt_term) =>
                              (b: smt_term) =>
                                translate_set_comp_eq(enums, vara, dnm, a, b),
                            translate(enums, l),
                            translate(enums, p)
                          )
                      }
                    case Some(rel) =>
                      map_option[smt_term, smt_term](
                        (lt: smt_term) =>
                          translate_range_eq(lt, rel),
                        translate(enums, l)
                      )
                  }
                case Some((rel, (kn, vn))) =>
                  Some[smt_term](TEq(TIndexRel(TPrime(TVar(rel)), TVar(kn)), TVar(vn)))
              }
            case Some(a) => Some[smt_term](a)
          }
        case BNeq() =>
          map2_opt(
            (lt: smt_term) => (rt: smt_term) => TNot(TEq(lt, rt)),
            translate(enums, l),
            translate(enums, r)
          )
        case BLt() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TLt(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BGt() =>
          map2_opt(
            (lt: smt_term) => (rt: smt_term) => TLt(rt, lt),
            translate(enums, l),
            translate(enums, r)
          )
        case BLe() =>
          map2_opt(
            (lt: smt_term) =>
              (rt: smt_term) =>
                TOr(TLt(lt, rt), TEq(lt, rt)),
            translate(enums, l),
            translate(enums, r)
          )
        case BGe() =>
          map2_opt(
            (lt: smt_term) =>
              (rt: smt_term) =>
                TOr(TLt(rt, lt), TEq(lt, rt)),
            translate(enums, l),
            translate(enums, r)
          )
        case BIn() =>
          translate(enums, l) match {
            case None => None
            case Some(lt) =>
              prime_rel_name(r) match {
                case None =>
                  pre_rel_name(r) match {
                    case None =>
                      identName(r) match {
                        case None =>
                          comp_parts(r) match {
                            case None =>
                              range_arg(r) match {
                                case None =>
                                  map_option[smt_term, smt_term](
                                    (a: smt_term) => TSetMember(lt, a),
                                    translate(enums, r)
                                  )
                                case Some(rel) =>
                                  val k = fresh_var("k", rel :: smt_var_list(lt)): String;
                                  Some[smt_term](TExistsRel(
                                    k,
                                    rel,
                                    TEq(TIndexRel(TVar(rel), TVar(k)), lt)
                                  ))
                              }
                            case Some((vara, (dnm, p))) =>
                              map_option[smt_term, smt_term](
                                (pt: smt_term) =>
                                  TLetIn(
                                    vara,
                                    lt,
                                    string_in_list(dnm, enums) match {
                                      case true  => pt
                                      case false => TAnd(TInDom(dnm, TVar(vara)), pt)
                                    }
                                  ),
                                translate(enums, p)
                              )
                          }
                        case Some(rel) => Some[smt_term](TInDom(rel, lt))
                      }
                    case Some(rel) => Some[smt_term](TPre(TInDom(rel, lt)))
                  }
                case Some(rel) => Some[smt_term](TPrime(TInDom(rel, lt)))
              }
          }
        case BNotIn() =>
          translate(enums, l) match {
            case None => None
            case Some(lt) =>
              prime_rel_name(r) match {
                case None =>
                  pre_rel_name(r) match {
                    case None =>
                      identName(r) match {
                        case None =>
                          map_option[smt_term, smt_term](
                            (rt: smt_term) => TNot(TSetMember(lt, rt)),
                            translate(enums, r)
                          )
                        case Some(rel) =>
                          Some[smt_term](TNot(TInDom(rel, lt)))
                      }
                    case Some(rel) =>
                      Some[smt_term](TNot(TPre(TInDom(rel, lt))))
                  }
                case Some(rel) => Some[smt_term](TNot(TPrime(TInDom(rel, lt))))
              }
          }
        case BSubset() =>
          map2_opt(
            (lt: smt_term) =>
              (rt: smt_term) =>
                TEq(TSetDiff(lt, rt), TSetEmpty()),
            translate(enums, l),
            translate(enums, r)
          )
        case BUnion() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TSetUnion(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BIntersect() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TSetIntersect(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BDiff() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TSetDiff(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BAdd() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TAdd(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BSub() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TSub(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BMul() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TMul(a, b),
            translate(enums, l),
            translate(enums, r)
          )
        case BDiv() =>
          map2_opt(
            (a: smt_term) => (b: smt_term) => TDiv(a, b),
            translate(enums, l),
            translate(enums, r)
          )
      }
    case (enums, LetF(x, v, body, vw)) =>
      (translate(enums, v), translate(enums, body)) match {
        case (None, _)            => None
        case (Some(_), None)      => None
        case (Some(vt), Some(bt)) => Some[smt_term](TLetIn(x, vt, bt))
      }
    case (vx, EnumAccessF(base, mem, vy)) =>
      base match {
        case BinaryOpF(_, _, _, _)         => None
        case UnaryOpF(_, _, _)             => None
        case QuantifierF(_, _, _, _)       => None
        case SomeWrapF(_, _)               => None
        case TheF(_, _, _, _)              => None
        case FieldAccessF(_, _, _)         => None
        case EnumAccessF(_, _, _)          => None
        case IndexF(_, _, _)               => None
        case CallF(_, _, _)                => None
        case PrimeF(_, _)                  => None
        case PreF(_, _)                    => None
        case WithF(_, _, _)                => None
        case IfF(_, _, _, _)               => None
        case LetF(_, _, _, _)              => None
        case LambdaF(_, _, _)              => None
        case ConstructorF(_, _, _)         => None
        case SetLiteralF(_, _)             => None
        case MapLiteralF(_, _)             => None
        case SetComprehensionF(_, _, _, _) => None
        case SeqLiteralF(_, _)             => None
        case MatchesF(_, _, _)             => None
        case IntLitF(_, _)                 => None
        case FloatLitF(_, _)               => None
        case StringLitF(_, _)              => None
        case BoolLitF(_, _)                => None
        case NoneLitF(_)                   => None
        case IdentifierF(en, _)            => Some[smt_term](EnumElemConst(en, mem))
      }
    case (enums, FieldAccessF(base, fname, vz)) =>
      map_option[smt_term, smt_term](
        (b: smt_term) => TFieldAccess(b, fname),
        translate(enums, base)
      )
    case (enums, IndexF(base, key, wa)) =>
      (translate(enums, base), translate(enums, key)) match {
        case (None, _)       => None
        case (Some(_), None) => None
        case (Some(basea), Some(keya)) =>
          peel_relation_ref_smt(basea) match {
            case None    => None
            case Some(_) => Some[smt_term](TIndexRel(basea, keya))
          }
      }
    case (enums, PrimeF(e, wb)) =>
      map_option[smt_term, smt_term]((a: smt_term) => TPrime(a), translate(enums, e))
    case (enums, PreF(e, wc)) =>
      map_option[smt_term, smt_term]((a: smt_term) => TPre(a), translate(enums, e))
    case (enums, WithF(base, updates, wd)) =>
      translate(enums, base) match {
        case None    => None
        case Some(a) => translate_with_assigns(enums, updates, a)
      }
    case (enums, SetLiteralF(elems, we))   => translateSetList(enums, elems)
    case (enums, SeqLiteralF(elems, wf))   => translateSeqList(enums, elems)
    case (enums, MapLiteralF(entries, wg)) => translateMapEntries(enums, entries)
    case (enums, IfF(c, a, b, wh)) =>
      (translate(enums, c), (translate(enums, a), translate(enums, b))) match {
        case (None, _)                        => None
        case (Some(_), (None, _))             => None
        case (Some(_), (Some(_), None))       => None
        case (Some(ct), (Some(at), Some(bt))) => Some[smt_term](TIte(ct, at, bt))
      }
    case (enums, SomeWrapF(e, wi)) =>
      map_option[smt_term, smt_term]((a: smt_term) => TSome(a), translate(enums, e))
  }

  def scalarCmpOf(x0: bin_op): Option[scalar_cmp] = x0 match {
    case BGt()        => Some[scalar_cmp](ScGt())
    case BGe()        => Some[scalar_cmp](ScGe())
    case BLt()        => Some[scalar_cmp](ScLt())
    case BLe()        => Some[scalar_cmp](ScLe())
    case BEq()        => Some[scalar_cmp](ScEq())
    case BNeq()       => Some[scalar_cmp](ScNeq())
    case BAnd()       => None
    case BOr()        => None
    case BImplies()   => None
    case BIff()       => None
    case BIn()        => None
    case BNotIn()     => None
    case BSubset()    => None
    case BUnion()     => None
    case BIntersect() => None
    case BDiff()      => None
    case BAdd()       => None
    case BSub()       => None
    case BMul()       => None
    case BDiv()       => None
  }

  def scalarLitOf(x0: expr): Option[BigInt] = x0 match {
    case IntLitF(k, uu) => Some[BigInt](k)
    case UnaryOpF(op, e, uv) =>
      op match {
        case UNot() => None
        case UNegate() => e match {
            case BinaryOpF(_, _, _, _)         => None
            case UnaryOpF(_, _, _)             => None
            case QuantifierF(_, _, _, _)       => None
            case SomeWrapF(_, _)               => None
            case TheF(_, _, _, _)              => None
            case FieldAccessF(_, _, _)         => None
            case EnumAccessF(_, _, _)          => None
            case IndexF(_, _, _)               => None
            case CallF(_, _, _)                => None
            case PrimeF(_, _)                  => None
            case PreF(_, _)                    => None
            case WithF(_, _, _)                => None
            case IfF(_, _, _, _)               => None
            case LetF(_, _, _, _)              => None
            case LambdaF(_, _, _)              => None
            case ConstructorF(_, _, _)         => None
            case SetLiteralF(_, _)             => None
            case MapLiteralF(_, _)             => None
            case SetComprehensionF(_, _, _, _) => None
            case SeqLiteralF(_, _)             => None
            case MatchesF(_, _, _)             => None
            case IntLitF(k, _)                 => Some[BigInt](uminus_int(k))
            case FloatLitF(_, _)               => None
            case StringLitF(_, _)              => None
            case BoolLitF(_, _)                => None
            case NoneLitF(_)                   => None
            case IdentifierF(_, _)             => None
          }
        case UCardinality() => None
        case UPower()       => None
      }
    case BinaryOpF(v, va, vb, vc)         => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def scalarRhsOf(n: String, x1: expr): Option[scalar_rhs] = (n, x1) match {
    case (n, IntLitF(k, uu)) => Some[scalar_rhs](SrLit(k))
    case (n, IdentifierF(m, uv)) =>
      m == n match {
        case true  => Some[scalar_rhs](SrSelf())
        case false => None
      }
    case (n, PreF(e, uw)) =>
      e match {
        case BinaryOpF(_, _, _, _)         => None
        case UnaryOpF(_, _, _)             => None
        case QuantifierF(_, _, _, _)       => None
        case SomeWrapF(_, _)               => None
        case TheF(_, _, _, _)              => None
        case FieldAccessF(_, _, _)         => None
        case EnumAccessF(_, _, _)          => None
        case IndexF(_, _, _)               => None
        case CallF(_, _, _)                => None
        case PrimeF(_, _)                  => None
        case PreF(_, _)                    => None
        case WithF(_, _, _)                => None
        case IfF(_, _, _, _)               => None
        case LetF(_, _, _, _)              => None
        case LambdaF(_, _, _)              => None
        case ConstructorF(_, _, _)         => None
        case SetLiteralF(_, _)             => None
        case MapLiteralF(_, _)             => None
        case SetComprehensionF(_, _, _, _) => None
        case SeqLiteralF(_, _)             => None
        case MatchesF(_, _, _)             => None
        case IntLitF(_, _)                 => None
        case FloatLitF(_, _)               => None
        case StringLitF(_, _)              => None
        case BoolLitF(_, _)                => None
        case NoneLitF(_)                   => None
        case IdentifierF(m, _) =>
          m == n match {
            case true  => Some[scalar_rhs](SrSelf())
            case false => None
          }
      }
    case (n, UnaryOpF(op, e, ux)) =>
      op match {
        case UNot() => None
        case UNegate() =>
          e match {
            case BinaryOpF(_, _, _, _)         => None
            case UnaryOpF(_, _, _)             => None
            case QuantifierF(_, _, _, _)       => None
            case SomeWrapF(_, _)               => None
            case TheF(_, _, _, _)              => None
            case FieldAccessF(_, _, _)         => None
            case EnumAccessF(_, _, _)          => None
            case IndexF(_, _, _)               => None
            case CallF(_, _, _)                => None
            case PrimeF(_, _)                  => None
            case PreF(_, _)                    => None
            case WithF(_, _, _)                => None
            case IfF(_, _, _, _)               => None
            case LetF(_, _, _, _)              => None
            case LambdaF(_, _, _)              => None
            case ConstructorF(_, _, _)         => None
            case SetLiteralF(_, _)             => None
            case MapLiteralF(_, _)             => None
            case SetComprehensionF(_, _, _, _) => None
            case SeqLiteralF(_, _)             => None
            case MatchesF(_, _, _)             => None
            case IntLitF(k, _)                 => Some[scalar_rhs](SrLit(uminus_int(k)))
            case FloatLitF(_, _)               => None
            case StringLitF(_, _)              => None
            case BoolLitF(_, _)                => None
            case NoneLitF(_)                   => None
            case IdentifierF(_, _)             => None
          }
        case UCardinality() => None
        case UPower()       => None
      }
    case (n, BinaryOpF(op, l, r, uy)) =>
      (scalarRhsOf(n, l), scalarRhsOf(n, r)) match {
        case (None, _)       => None
        case (Some(_), None) => None
        case (Some(a), Some(b)) =>
          op match {
            case BAnd()       => None
            case BOr()        => None
            case BImplies()   => None
            case BIff()       => None
            case BEq()        => None
            case BNeq()       => None
            case BLt()        => None
            case BGt()        => None
            case BLe()        => None
            case BGe()        => None
            case BIn()        => None
            case BNotIn()     => None
            case BSubset()    => None
            case BUnion()     => None
            case BIntersect() => None
            case BDiff()      => None
            case BAdd()       => Some[scalar_rhs](SrAdd(a, b))
            case BSub()       => Some[scalar_rhs](SrSub(a, b))
            case BMul()       => Some[scalar_rhs](SrMul(a, b))
            case BDiv()       => None
          }
      }
    case (uz, QuantifierF(v, vb, vc, vd))       => None
    case (uz, SomeWrapF(v, vb))                 => None
    case (uz, TheF(v, vb, vc, vd))              => None
    case (uz, FieldAccessF(v, vb, vc))          => None
    case (uz, EnumAccessF(v, vb, vc))           => None
    case (uz, IndexF(v, vb, vc))                => None
    case (uz, CallF(v, vb, vc))                 => None
    case (uz, PrimeF(v, vb))                    => None
    case (uz, WithF(v, vb, vc))                 => None
    case (uz, IfF(v, vb, vc, vd))               => None
    case (uz, LetF(v, vb, vc, vd))              => None
    case (uz, LambdaF(v, vb, vc))               => None
    case (uz, ConstructorF(v, vb, vc))          => None
    case (uz, SetLiteralF(v, vb))               => None
    case (uz, MapLiteralF(v, vb))               => None
    case (uz, SetComprehensionF(v, vb, vc, vd)) => None
    case (uz, SeqLiteralF(v, vb))               => None
    case (uz, MatchesF(v, vb, vc))              => None
    case (uz, FloatLitF(v, vb))                 => None
    case (uz, StringLitF(v, vb))                => None
    case (uz, BoolLitF(v, vb))                  => None
    case (uz, NoneLitF(v))                      => None
  }

  def enmName(x0: enum_decl): String = x0 match {
    case EnumDeclFull(x1, x2, x3) => x1
  }

  def enmSpan(x0: enum_decl): Option[span_t] = x0 match {
    case EnumDeclFull(x1, x2, x3) => x3
  }

  def fctBody(x0: fact_decl): expr = x0 match {
    case FactDeclFull(x1, x2, x3) => x2
  }

  def fctName(x0: fact_decl): Option[String] = x0 match {
    case FactDeclFull(x1, x2, x3) => x1
  }

  def fctSpan(x0: fact_decl): Option[span_t] = x0 match {
    case FactDeclFull(x1, x2, x3) => x3
  }

  def mpeSpan(x0: map_entry): Option[span_t] = x0 match {
    case MapEntryFull(x1, x2, x3) => x3
  }

  def litClass(x0: expr): Option[lit_class] = x0 match {
    case IntLitF(uu, uv)                  => Some[lit_class](LcNumeric())
    case FloatLitF(uw, ux)                => Some[lit_class](LcNumeric())
    case BoolLitF(uy, uz)                 => Some[lit_class](LcBool())
    case StringLitF(va, vb)               => Some[lit_class](LcStringLike())
    case SetLiteralF(vc, vd)              => Some[lit_class](LcCollection())
    case MapLiteralF(ve, vf)              => Some[lit_class](LcCollection())
    case SeqLiteralF(vg, vh)              => Some[lit_class](LcCollection())
    case NoneLitF(vi)                     => Some[lit_class](LcNone())
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetComprehensionF(v, va, vb, vc) => None
    case MatchesF(v, va, vb)              => None
    case IdentifierF(v, va)               => None
  }

  def isDeleteKind(x0: operation_kind): Boolean = x0 match {
    case Deletea()       => true
    case Create()        => false
    case Read()          => false
    case Replace()       => false
    case PartialUpdate() => false
    case CreateChild()   => false
    case FilteredRead()  => false
    case SideEffect()    => false
    case BatchMutation() => false
    case Transition()    => false
  }

  def isRkCreate(x0: route_kind): Boolean = x0 match {
    case RkCreate()   => true
    case RkRead()     => false
    case RkList()     => false
    case RkDelete()   => false
    case RkRedirect() => false
    case RkOther()    => false
  }

  def columnSqlType(x0: column_spec): String = x0 match {
    case ColumnSpec(uu, t, uv, uw) => t
  }

  def tableForeignKeys(x0: table_spec): List[foreign_key_spec] = x0 match {
    case TableSpec(uu, uv, uw, ux, fks, uy, uz) => fks
  }

  def tableDepPairs(ts: List[table_spec]): List[(String, List[String])] =
    map[table_spec, (String, List[String])](
      (t: table_spec) =>
        (
          tableName(t),
          map[foreign_key_spec, String](
            (a: foreign_key_spec) =>
              fkRefTable(a),
            tableForeignKeys(t)
          )
        ),
      ts
    )

  def columnNames(t: table_spec): List[String] =
    map[column_spec, String]((a: column_spec) => columnName(a), tableColumns(t))

  def intraAdds(
      prev: table_spec,
      nxt: table_spec,
      prev_cks: List[(String, String)],
      nxt_cks: List[(String, String)]
  ): List[migration_op] = {
    val tn           = tableName(nxt): String
    val prev_col_set = seta[String](columnNames(prev)): set[String]
    val add_col_ops =
      map_filter[column_spec, migration_op](
        (x: column_spec) =>
          !member[String](columnName(x), prev_col_set) match {
            case true  => Some[migration_op](AddColumn(tn, x))
            case false => None
          },
        tableColumns(nxt)
      ): List[migration_op]
    val prev_ix_set = seta[index_spec](tableIndexes(prev)): set[index_spec]
    val add_ix_ops =
      map_filter[index_spec, migration_op](
        (x: index_spec) =>
          !member[index_spec](x, prev_ix_set) match {
            case true  => Some[migration_op](AddIndex(tn, x))
            case false => None
          },
        tableIndexes(nxt)
      ): List[migration_op]
    val prev_fk_set =
      seta[foreign_key_spec](tableForeignKeys(prev)): set[foreign_key_spec]
    val add_fk_ops =
      map_filter[foreign_key_spec, migration_op](
        (x: foreign_key_spec) =>
          !member[foreign_key_spec](x, prev_fk_set) match {
            case true  => Some[migration_op](AddForeignKey(tn, x))
            case false => None
          },
        tableForeignKeys(nxt)
      ): List[migration_op]
    val prev_ck_set = seta[(String, String)](prev_cks): set[(String, String)]
    val add_ck_ops =
      map_filter[(String, String), migration_op](
        (x: (String, String)) =>
          !member[(String, String)](x, prev_ck_set) match {
            case true =>
              Some[migration_op](AddCheck(tn, fst[String, String](x), snd[String, String](x)))
            case false => None
          },
        nxt_cks
      ): List[migration_op];
    add_col_ops ++ (add_fk_ops ++ (add_ck_ops ++ add_ix_ops))
  }

  def tyctxEmpty: tyctx_ext[Unit] =
    tyctx_exta[Unit](Nil, state_schema_exta[Unit](Nil, ()), Nil, Nil, Nil, ())

  def fldName(x0: field_decl): String = x0 match {
    case FieldDeclFull(x1, x2, x3, x4) => x1
  }

  def fldSpan(x0: field_decl): Option[span_t] = x0 match {
    case FieldDeclFull(x1, x2, x3, x4) => x4
  }

  def fldType(x0: field_decl): type_expr = x0 match {
    case FieldDeclFull(x1, x2, x3, x4) => x2
  }

  def mpeValue(x0: map_entry): expr = x0 match {
    case MapEntryFull(x1, x2, x3) => x2
  }

  def prmName(x0: param_decl): String = x0 match {
    case ParamDeclFull(x1, x2, x3) => x1
  }

  def prmSpan(x0: param_decl): Option[span_t] = x0 match {
    case ParamDeclFull(x1, x2, x3) => x3
  }

  def prmType(x0: param_decl): type_expr = x0 match {
    case ParamDeclFull(x1, x2, x3) => x2
  }

  def svcName(x0: service_ir): String = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x1
  }

  def svcSpan(x0: service_ir): Option[span_t] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x16
  }

  def stdSpan(x0: state_decl): Option[span_t] = x0 match {
    case StateDeclFull(x1, x2) => x2
  }

  def binOpName(x0: bin_op): String = x0 match {
    case BAdd()       => "+"
    case BSub()       => "-"
    case BMul()       => "*"
    case BDiv()       => "/"
    case BLt()        => "<"
    case BGt()        => ">"
    case BLe()        => "<="
    case BGe()        => ">="
    case BAnd()       => "and"
    case BOr()        => "or"
    case BImplies()   => "implies"
    case BIff()       => "iff"
    case BIn()        => "in"
    case BNotIn()     => "not in"
    case BEq()        => "="
    case BNeq()       => "!="
    case BSubset()    => "subset"
    case BUnion()     => "++"
    case BIntersect() => "&"
    case BDiff()      => "--"
  }

  def highBoundEffective(x0: bin_op, n: BigInt): BigInt = (x0, n) match {
    case (BLt(), n)        => minus_int(n, one_inta)
    case (BAnd(), n)       => n
    case (BOr(), n)        => n
    case (BImplies(), n)   => n
    case (BIff(), n)       => n
    case (BEq(), n)        => n
    case (BNeq(), n)       => n
    case (BGt(), n)        => n
    case (BLe(), n)        => n
    case (BGe(), n)        => n
    case (BIn(), n)        => n
    case (BNotIn(), n)     => n
    case (BSubset(), n)    => n
    case (BUnion(), n)     => n
    case (BIntersect(), n) => n
    case (BDiff(), n)      => n
    case (BAdd(), n)       => n
    case (BSub(), n)       => n
    case (BMul(), n)       => n
    case (BDiv(), n)       => n
  }

  def lowBoundEffective(x0: bin_op, n: BigInt): BigInt = (x0, n) match {
    case (BGt(), n)        => plus_int(n, one_inta)
    case (BAnd(), n)       => n
    case (BOr(), n)        => n
    case (BImplies(), n)   => n
    case (BIff(), n)       => n
    case (BEq(), n)        => n
    case (BNeq(), n)       => n
    case (BLt(), n)        => n
    case (BLe(), n)        => n
    case (BGe(), n)        => n
    case (BIn(), n)        => n
    case (BNotIn(), n)     => n
    case (BSubset(), n)    => n
    case (BUnion(), n)     => n
    case (BIntersect(), n) => n
    case (BDiff(), n)      => n
    case (BAdd(), n)       => n
    case (BSub(), n)       => n
    case (BMul(), n)       => n
    case (BDiv(), n)       => n
  }

  def isLowBound(x0: bin_op): Boolean = x0 match {
    case BGe()        => true
    case BGt()        => true
    case BAnd()       => false
    case BOr()        => false
    case BImplies()   => false
    case BIff()       => false
    case BEq()        => false
    case BNeq()       => false
    case BLt()        => false
    case BLe()        => false
    case BIn()        => false
    case BNotIn()     => false
    case BSubset()    => false
    case BUnion()     => false
    case BIntersect() => false
    case BDiff()      => false
    case BAdd()       => false
    case BSub()       => false
    case BMul()       => false
    case BDiv()       => false
  }

  def conflicts(aOp: bin_op, aB: BigInt, bOp: bin_op, bB: BigInt): Boolean =
    isLowBound(aOp) && !isLowBound(bOp) match {
      case true => less_int(highBoundEffective(bOp, bB), lowBoundEffective(aOp, aB))
      case false => !isLowBound(aOp) && isLowBound(bOp) match {
          case true  => less_int(highBoundEffective(aOp, aB), lowBoundEffective(bOp, bB))
          case false => false
        }
    }

  def remove_name(uu: String, x1: List[String]): List[String] = (uu, x1) match {
    case (uu, Nil) => Nil
    case (n, x :: xs) =>
      x == n match {
        case true  => remove_name(n, xs)
        case false => x :: remove_name(n, xs)
      }
  }

  def remove_names(x0: List[String], xs: List[String]): List[String] = (x0, xs) match {
    case (Nil, xs)     => xs
    case (n :: ns, xs) => remove_name(n, remove_names(ns, xs))
  }

  def free_vars_bindings(x0: List[quantifier_binding]): List[String] = x0 match {
    case Nil => Nil
    case QuantifierBindingFull(wj, d, wk, wl) :: bs =>
      free_vars(d) ++ free_vars_bindings(bs)
  }

  def free_vars_entries(x0: List[map_entry]): List[String] = x0 match {
    case Nil => Nil
    case MapEntryFull(k, v, wi) :: es =>
      free_vars(k) ++ (free_vars(v) ++ free_vars_entries(es))
  }

  def free_vars_fields(x0: List[field_assign]): List[String] = x0 match {
    case Nil                              => Nil
    case FieldAssignFull(wg, v, wh) :: fs => free_vars(v) ++ free_vars_fields(fs)
  }

  def free_vars_list(x0: List[expr]): List[String] = x0 match {
    case Nil     => Nil
    case x :: xs => free_vars(x) ++ free_vars_list(xs)
  }

  def free_vars(x0: expr): List[String] = x0 match {
    case IdentifierF(n, uu)      => List(n)
    case BinaryOpF(uv, l, r, uw) => free_vars(l) ++ free_vars(r)
    case UnaryOpF(ux, e, uy)     => free_vars(e)
    case FieldAccessF(b, uz, va) => free_vars(b)
    case EnumAccessF(b, vb, vc)  => free_vars(b)
    case IndexF(b, i, vd)        => free_vars(b) ++ free_vars(i)
    case CallF(c, args, ve)      => free_vars(c) ++ free_vars_list(args)
    case PrimeF(e, vf)           => free_vars(e)
    case PreF(e, vg)             => free_vars(e)
    case WithF(b, upds, vh)      => free_vars(b) ++ free_vars_fields(upds)
    case IfF(c, t, e, vi)        => free_vars(c) ++ (free_vars(t) ++ free_vars(e))
    case LetF(v, vala, body, vj) =>
      free_vars(vala) ++ remove_name(v, free_vars(body))
    case LambdaF(p, b, vk)        => remove_name(p, free_vars(b))
    case ConstructorF(vl, fs, vm) => free_vars_fields(fs)
    case SetLiteralF(xs, vn)      => free_vars_list(xs)
    case MapLiteralF(es, vo)      => free_vars_entries(es)
    case SetComprehensionF(v, d, p, vp) =>
      free_vars(d) ++ remove_name(v, free_vars(p))
    case SeqLiteralF(xs, vq) => free_vars_list(xs)
    case MatchesF(x, vr, vs) => free_vars(x)
    case SomeWrapF(x, vt)    => free_vars(x)
    case TheF(v, d, b, vu)   => free_vars(d) ++ remove_name(v, free_vars(b))
    case QuantifierF(vv, bs, body, vw) =>
      free_vars_bindings(bs) ++ remove_names(qb_names(bs), free_vars(body))
    case IntLitF(vx, vy)    => Nil
    case FloatLitF(vz, wa)  => Nil
    case StringLitF(wb, wc) => Nil
    case BoolLitF(wd, we)   => Nil
    case NoneLitF(wf)       => Nil
  }

  def isBoolLit(x0: expr): Boolean = x0 match {
    case BoolLitF(uu, uv)                 => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def isLiteral(x0: expr): Boolean = x0 match {
    case IntLitF(uu, uv)                  => true
    case FloatLitF(uw, ux)                => true
    case StringLitF(uy, uz)               => true
    case BinaryOpF(v, vb, vc, vd)         => false
    case UnaryOpF(v, vb, vc)              => false
    case QuantifierF(v, vb, vc, vd)       => false
    case SomeWrapF(v, vb)                 => false
    case TheF(v, vb, vc, vd)              => false
    case FieldAccessF(v, vb, vc)          => false
    case EnumAccessF(v, vb, vc)           => false
    case IndexF(v, vb, vc)                => false
    case CallF(v, vb, vc)                 => false
    case PrimeF(v, vb)                    => false
    case PreF(v, vb)                      => false
    case WithF(v, vb, vc)                 => false
    case IfF(v, vb, vc, vd)               => false
    case LetF(v, vb, vc, vd)              => false
    case LambdaF(v, vb, vc)               => false
    case ConstructorF(v, vb, vc)          => false
    case SetLiteralF(v, vb)               => false
    case MapLiteralF(v, vb)               => false
    case SetComprehensionF(v, vb, vc, vd) => false
    case SeqLiteralF(v, vb)               => false
    case MatchesF(v, vb, vc)              => false
    case BoolLitF(v, vb)                  => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, vb)               => false
  }

  def isMapType(x0: type_expr): Boolean = x0 match {
    case MapTypeF(uu, uv, uw)         => true
    case NamedTypeF(v, va)            => false
    case SetTypeF(v, va)              => false
    case SeqTypeF(v, va)              => false
    case OptionTypeF(v, va)           => false
    case RelationTypeF(v, va, vb, vc) => false
  }

  def isTrueLit(x0: expr): Boolean = x0 match {
    case BoolLitF(true, uu)               => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(false, va)              => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def is_binder(x0: expr): Boolean = x0 match {
    case LetF(uu, uv, uw, ux)              => true
    case QuantifierF(uy, uz, va, vb)       => true
    case LambdaF(vc, vd, ve)               => true
    case SetComprehensionF(vf, vg, vh, vi) => true
    case TheF(vj, vk, vl, vm)              => true
    case BinaryOpF(v, va, vb, vc)          => false
    case UnaryOpF(v, va, vb)               => false
    case SomeWrapF(v, va)                  => false
    case FieldAccessF(v, va, vb)           => false
    case EnumAccessF(v, va, vb)            => false
    case IndexF(v, va, vb)                 => false
    case CallF(v, va, vb)                  => false
    case PrimeF(v, va)                     => false
    case PreF(v, va)                       => false
    case WithF(v, va, vb)                  => false
    case IfF(v, va, vb, vc)                => false
    case ConstructorF(v, va, vb)           => false
    case SetLiteralF(v, va)                => false
    case MapLiteralF(v, va)                => false
    case SeqLiteralF(v, va)                => false
    case MatchesF(v, va, vb)               => false
    case IntLitF(v, va)                    => false
    case FloatLitF(v, va)                  => false
    case StringLitF(v, va)                 => false
    case BoolLitF(v, va)                   => false
    case NoneLitF(v)                       => false
    case IdentifierF(v, va)                => false
  }

  def isCreateLikeKind(x0: operation_kind): Boolean = x0 match {
    case Create()        => true
    case CreateChild()   => true
    case Read()          => false
    case Replace()       => false
    case PartialUpdate() => false
    case Deletea()       => false
    case FilteredRead()  => false
    case SideEffect()    => false
    case BatchMutation() => false
    case Transition()    => false
  }

  def isDeleteMethod(x0: http_method): Boolean = x0 match {
    case DELETE() => true
    case GET()    => false
    case POST()   => false
    case PUT()    => false
    case PATCH()  => false
  }

  def defaultStatus(mth: http_method, knd: operation_kind): String =
    isDeleteMethod(mth) match {
      case true => "204"
      case false => isCreateLikeKind(knd) match {
          case true => "201"
          case false => isDeleteKind(knd) match {
              case true  => "204"
              case false => "200"
            }
        }
    }

  def resolveMethod(overridea: Option[http_method], fallback: http_method): http_method =
    overridea match {
      case None    => fallback
      case Some(m) => m
    }

  def resolveStatus(overridea: Option[String], mth: http_method, knd: operation_kind): String =
    overridea match {
      case None    => defaultStatus(mth, knd)
      case Some(s) => s
    }

  def inverseOp(x0: migration_op): migration_op = x0 match {
    case CreateTable(t)    => DropTable(t)
    case DropTable(t)      => CreateTable(t)
    case AddColumn(tn, c)  => DropColumn(tn, c)
    case DropColumn(tn, c) => AddColumn(tn, c)
    case AlterColumnType(tn, cn, oldv, newv) =>
      AlterColumnType(tn, cn, newv, oldv)
    case AlterColumnNullable(tn, cn, oldv, newv) =>
      AlterColumnNullable(tn, cn, newv, oldv)
    case AlterColumnDefault(tn, cn, oldv, newv) =>
      AlterColumnDefault(tn, cn, newv, oldv)
    case AddCheck(tn, cn, sql)  => DropCheck(tn, cn, sql)
    case DropCheck(tn, cn, sql) => AddCheck(tn, cn, sql)
    case AddForeignKey(tn, fk)  => DropForeignKey(tn, fk)
    case DropForeignKey(tn, fk) => AddForeignKey(tn, fk)
    case AddIndex(tn, ix)       => DropIndex(tn, ix)
    case DropIndex(tn, ix)      => AddIndex(tn, ix)
    case AddTrigger(tg)         => DropTrigger(tg)
    case DropTrigger(tg)        => AddTrigger(tg)
  }

  def downList(ops: List[migration_op]): List[migration_op] =
    rev[migration_op](map[migration_op, migration_op]((a: migration_op) => inverseOp(a), ops))

  def stateRelationKeyTypeNamesAux(x0: List[state_field_decl]): List[String] =
    x0 match {
      case Nil => Nil
      case sf :: rest =>
        sf match {
          case StateFieldDeclFull(_, NamedTypeF(_, _), _) =>
            stateRelationKeyTypeNamesAux(rest)
          case StateFieldDeclFull(_, SetTypeF(_, _), _) =>
            stateRelationKeyTypeNamesAux(rest)
          case StateFieldDeclFull(_, MapTypeF(_, _, _), _) =>
            stateRelationKeyTypeNamesAux(rest)
          case StateFieldDeclFull(_, SeqTypeF(_, _), _) =>
            stateRelationKeyTypeNamesAux(rest)
          case StateFieldDeclFull(_, OptionTypeF(_, _), _) =>
            stateRelationKeyTypeNamesAux(rest)
          case StateFieldDeclFull(_, RelationTypeF(frm, _, _, _), _) =>
            typeName(frm) match {
              case None    => stateRelationKeyTypeNamesAux(rest)
              case Some(n) => n :: stateRelationKeyTypeNamesAux(rest)
            }
        }
    }

  def stateRelationKeyTypeNames(stateOpt: Option[state_decl]): List[String] =
    stateOpt match {
      case None                       => Nil
      case Some(StateDeclFull(fs, _)) => stateRelationKeyTypeNamesAux(fs)
    }

  def less_eq_nat(m: nat, n: nat): Boolean =
    integer_of_nat(m) <= integer_of_nat(n)

  def literalEndsWith(suf: String, s: String): Boolean = {
    val xs = Str_Literal.asciisOfLiteral(s): List[BigInt]
    val ys = Str_Literal.asciisOfLiteral(suf): List[BigInt];
    less_eq_nat(size_list[BigInt](ys), size_list[BigInt](xs)) &&
    equal_list[BigInt](
      drop[BigInt](minus_nat(size_list[BigInt](xs), size_list[BigInt](ys)), xs),
      ys
    )
  }

  def paramNameLooksLikeId(name: String): Boolean =
    name == "id" || literalEndsWith("_id", name)

  def paramTypeIsInt(x0: type_expr): Boolean = x0 match {
    case NamedTypeF(n, uu)            => n == "Int"
    case SetTypeF(v, va)              => false
    case MapTypeF(v, va, vb)          => false
    case SeqTypeF(v, va)              => false
    case OptionTypeF(v, va)           => false
    case RelationTypeF(v, va, vb, vc) => false
  }

  def findIdParamAux(uu: List[String], x1: List[param_decl]): Option[String] =
    (uu, x1) match {
      case (uu, Nil) => None
      case (keys, ParamDeclFull(name, ty, uv) :: rest) =>
        val matchesKey = (typeName(ty) match {
          case None    => false
          case Some(a) => membera[String](keys, a)
        }): Boolean
        val matchesNameRule =
          paramTypeIsInt(ty) && paramNameLooksLikeId(name): Boolean;
        matchesKey || matchesNameRule match {
          case true  => Some[String](name)
          case false => findIdParamAux(keys, rest)
        }
    }

  def findIdParam(params: List[param_decl], stateOpt: Option[state_decl]): Option[String] =
    stateOpt match {
      case None    => None
      case Some(_) => findIdParamAux(stateRelationKeyTypeNames(stateOpt), params)
    }

  def isStubShape(x0: route_kind): Boolean = x0 match {
    case RkRedirect() => true
    case RkOther()    => true
    case RkCreate()   => false
    case RkRead()     => false
    case RkList()     => false
    case RkDelete()   => false
  }

  def columnNullable(x0: column_spec): Boolean = x0 match {
    case ColumnSpec(uu, uv, n, uw) => n
  }

  def schemaTriggers(x0: database_schema): List[trigger_spec] = x0 match {
    case DatabaseSchema(uu, tg) => tg
  }

  def intraDrops(
      prev: table_spec,
      nxt: table_spec,
      prev_cks: List[(String, String)],
      nxt_cks: List[(String, String)]
  ): List[migration_op] = {
    val tn          = tableName(prev): String
    val nxt_col_set = seta[String](columnNames(nxt)): set[String]
    val drop_col_ops =
      map_filter[column_spec, migration_op](
        (x: column_spec) =>
          !member[String](columnName(x), nxt_col_set) match {
            case true  => Some[migration_op](DropColumn(tn, x))
            case false => None
          },
        tableColumns(prev)
      ): List[migration_op]
    val nxt_ix_set = seta[index_spec](tableIndexes(nxt)): set[index_spec]
    val drop_ix_ops =
      map_filter[index_spec, migration_op](
        (x: index_spec) =>
          !member[index_spec](x, nxt_ix_set) match {
            case true  => Some[migration_op](DropIndex(tn, x))
            case false => None
          },
        tableIndexes(prev)
      ): List[migration_op]
    val nxt_fk_set =
      seta[foreign_key_spec](tableForeignKeys(nxt)): set[foreign_key_spec]
    val drop_fk_ops =
      map_filter[foreign_key_spec, migration_op](
        (x: foreign_key_spec) =>
          !member[foreign_key_spec](x, nxt_fk_set) match {
            case true  => Some[migration_op](DropForeignKey(tn, x))
            case false => None
          },
        tableForeignKeys(prev)
      ): List[migration_op]
    val nxt_ck_set = seta[(String, String)](nxt_cks): set[(String, String)]
    val drop_ck_ops =
      map_filter[(String, String), migration_op](
        (x: (String, String)) =>
          !member[(String, String)](x, nxt_ck_set) match {
            case true =>
              Some[migration_op](DropCheck(tn, fst[String, String](x), snd[String, String](x)))
            case false => None
          },
        prev_cks
      ): List[migration_op];
    drop_ix_ops ++ (drop_ck_ops ++ (drop_fk_ops ++ drop_col_ops))
  }

  def emptyStringConstraint: string_constraint =
    StringConstraint(None, None, Nil, Nil, Nil)

  def stringAtom(atom: expr): (string_constraint, List[String]) =
    decomposeAtom(atom) match {
      case RaLenCmp(BAnd(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BOr(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BImplies(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BIff(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BEq(), n) =>
        (StringConstraint(Some[BigInt](n), Some[BigInt](n), Nil, Nil, Nil), Nil)
      case RaLenCmp(BNeq(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BLt(), n) =>
        (StringConstraint(None, Some[BigInt](minus_int(n, one_inta)), Nil, Nil, Nil), Nil)
      case RaLenCmp(BGt(), n) =>
        (StringConstraint(Some[BigInt](plus_int(n, one_inta)), None, Nil, Nil, Nil), Nil)
      case RaLenCmp(BLe(), n) =>
        (StringConstraint(None, Some[BigInt](n), Nil, Nil, Nil), Nil)
      case RaLenCmp(BGe(), n) =>
        (StringConstraint(Some[BigInt](n), None, Nil, Nil, Nil), Nil)
      case RaLenCmp(BIn(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BNotIn(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BSubset(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BUnion(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BIntersect(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BDiff(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BAdd(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BSub(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BMul(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaLenCmp(BDiv(), _) =>
        (emptyStringConstraint, List("unsupported len comparison"))
      case RaValueCmp(_, _) =>
        (emptyStringConstraint, List("unhandled string constraint"))
      case RaMatches(pat) =>
        (StringConstraint(None, None, List(pat), Nil, Nil), Nil)
      case RaMatchesIdent(_, _) =>
        (emptyStringConstraint, List("unhandled string constraint"))
      case RaPredCall(name) => (emptyStringConstraint, List(name))
      case RaUnknown(_) =>
        (emptyStringConstraint, List("unhandled string constraint"))
    }

  def topoSortStep(
      uu: nat,
      x1: List[(String, List[String])],
      acc: List[String]
  ): Option[List[String]] =
    (uu, x1, acc) match {
      case (uu, Nil, acc) => Some[List[String]](rev[String](acc))
      case (fuel, uv :: uw, ux) =>
        equal_nat(fuel, zero_nat) match {
          case true => None
          case false =>
            val nodes = uv :: uw: List[(String, List[String])]
            val names =
              map[(String, List[String]), String](
                (a: (String, List[String])) =>
                  fst[String, List[String]](a),
                nodes
              ): List[String];
            filter[(String, List[String])](
              (p: (String, List[String])) =>
                isReadyIn(p, names),
              nodes
            ) match {
              case Nil => None
              case (rn, _) :: _ =>
                topoSortStep(
                  minus_nat(fuel, one_nat),
                  filter[(String, List[String])](
                    (p: (String, List[String])) =>
                      !(fst[String, List[String]](p) == rn),
                    nodes
                  ),
                  rn :: ux
                )
            }
        }
    }

  def scalarGuardOf(scalars: List[String], x1: expr): Option[scalar_guard] =
    (scalars, x1) match {
      case (scalars, BoolLitF(b, uu)) =>
        b match {
          case true  => Some[scalar_guard](SgTrue())
          case false => None
        }
      case (scalars, BinaryOpF(op, l, r, uv)) =>
        scalarCmpOf(op) match {
          case None => None
          case Some(c) =>
            l match {
              case BinaryOpF(_, _, _, _)         => None
              case UnaryOpF(_, _, _)             => None
              case QuantifierF(_, _, _, _)       => None
              case SomeWrapF(_, _)               => None
              case TheF(_, _, _, _)              => None
              case FieldAccessF(_, _, _)         => None
              case EnumAccessF(_, _, _)          => None
              case IndexF(_, _, _)               => None
              case CallF(_, _, _)                => None
              case PrimeF(_, _)                  => None
              case PreF(_, _)                    => None
              case WithF(_, _, _)                => None
              case IfF(_, _, _, _)               => None
              case LetF(_, _, _, _)              => None
              case LambdaF(_, _, _)              => None
              case ConstructorF(_, _, _)         => None
              case SetLiteralF(_, _)             => None
              case MapLiteralF(_, _)             => None
              case SetComprehensionF(_, _, _, _) => None
              case SeqLiteralF(_, _)             => None
              case MatchesF(_, _, _)             => None
              case IntLitF(_, _)                 => None
              case FloatLitF(_, _)               => None
              case StringLitF(_, _)              => None
              case BoolLitF(_, _)                => None
              case NoneLitF(_)                   => None
              case IdentifierF(n, _) =>
                membera[String](scalars, n) match {
                  case true =>
                    map_option[BigInt, scalar_guard]((a: BigInt) => SgCmp(n, c, a), scalarLitOf(r))
                  case false => None
                }
            }
        }
      case (uw, UnaryOpF(v, va, vb))              => None
      case (uw, QuantifierF(v, va, vb, vc))       => None
      case (uw, SomeWrapF(v, va))                 => None
      case (uw, TheF(v, va, vb, vc))              => None
      case (uw, FieldAccessF(v, va, vb))          => None
      case (uw, EnumAccessF(v, va, vb))           => None
      case (uw, IndexF(v, va, vb))                => None
      case (uw, CallF(v, va, vb))                 => None
      case (uw, PrimeF(v, va))                    => None
      case (uw, PreF(v, va))                      => None
      case (uw, WithF(v, va, vb))                 => None
      case (uw, IfF(v, va, vb, vc))               => None
      case (uw, LetF(v, va, vb, vc))              => None
      case (uw, LambdaF(v, va, vb))               => None
      case (uw, ConstructorF(v, va, vb))          => None
      case (uw, SetLiteralF(v, va))               => None
      case (uw, MapLiteralF(v, va))               => None
      case (uw, SetComprehensionF(v, va, vb, vc)) => None
      case (uw, SeqLiteralF(v, va))               => None
      case (uw, MatchesF(v, va, vb))              => None
      case (uw, IntLitF(v, va))                   => None
      case (uw, FloatLitF(v, va))                 => None
      case (uw, StringLitF(v, va))                => None
      case (uw, NoneLitF(v))                      => None
      case (uw, IdentifierF(v, va))               => None
    }

  def entName(x0: entity_decl): String = x0 match {
    case EntityDeclFull(x1, x2, x3, x4, x5) => x1
  }

  def entSpan(x0: entity_decl): Option[span_t] = x0 match {
    case EntityDeclFull(x1, x2, x3, x4, x5) => x5
  }

  def svcEnums(x0: service_ir): List[enum_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x4
  }

  def svcFacts(x0: service_ir): List[fact_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x11
  }

  def svcState(x0: service_ir): Option[state_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x6
  }

  def combineAnd_acc(acc: expr, x1: List[expr]): expr = (acc, x1) match {
    case (acc, Nil)       => acc
    case (acc, x :: rest) => combineAnd_acc(BinaryOpF(BAnd(), acc, x, None), rest)
  }

  def combineAnd(x0: List[expr]): expr = x0 match {
    case Nil       => BoolLitF(true, None)
    case x :: rest => combineAnd_acc(x, rest)
  }

  def isArithBin(x0: bin_op): Boolean = x0 match {
    case BAdd()       => true
    case BSub()       => true
    case BMul()       => true
    case BDiv()       => true
    case BAnd()       => false
    case BOr()        => false
    case BImplies()   => false
    case BIff()       => false
    case BEq()        => false
    case BNeq()       => false
    case BLt()        => false
    case BGt()        => false
    case BLe()        => false
    case BGe()        => false
    case BIn()        => false
    case BNotIn()     => false
    case BSubset()    => false
    case BUnion()     => false
    case BIntersect() => false
    case BDiff()      => false
  }

  def isPrePrime(x0: expr): Boolean = x0 match {
    case PrimeF(uu, uv)                   => true
    case PreF(uw, ux)                     => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(v, va)                  => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def isLeafValue(x0: expr): Boolean = x0 match {
    case IntLitF(uu, uv)                  => true
    case FloatLitF(uw, ux)                => true
    case StringLitF(uy, uz)               => true
    case BoolLitF(va, vb)                 => true
    case NoneLitF(vc)                     => true
    case IdentifierF(vd, ve)              => true
    case EnumAccessF(vf, vg, vh)          => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case MapLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
  }

  def isPureRead(x0: expr): Boolean = x0 match {
    case PreF(inner, uu)            => isPureRead(inner)
    case IndexF(base, idx, uv)      => isPureRead(base) && isPureRead(idx)
    case FieldAccessF(base, uw, ux) => isPureRead(base)
    case BinaryOpF(v, va, vb, vc)   => isLeafValue(BinaryOpF(v, va, vb, vc))
    case UnaryOpF(v, va, vb)        => isLeafValue(UnaryOpF(v, va, vb))
    case QuantifierF(v, va, vb, vc) => isLeafValue(QuantifierF(v, va, vb, vc))
    case SomeWrapF(v, va)           => isLeafValue(SomeWrapF(v, va))
    case TheF(v, va, vb, vc)        => isLeafValue(TheF(v, va, vb, vc))
    case EnumAccessF(v, va, vb)     => isLeafValue(EnumAccessF(v, va, vb))
    case CallF(v, va, vb)           => isLeafValue(CallF(v, va, vb))
    case PrimeF(v, va)              => isLeafValue(PrimeF(v, va))
    case WithF(v, va, vb)           => isLeafValue(WithF(v, va, vb))
    case IfF(v, va, vb, vc)         => isLeafValue(IfF(v, va, vb, vc))
    case LetF(v, va, vb, vc)        => isLeafValue(LetF(v, va, vb, vc))
    case LambdaF(v, va, vb)         => isLeafValue(LambdaF(v, va, vb))
    case ConstructorF(v, va, vb)    => isLeafValue(ConstructorF(v, va, vb))
    case SetLiteralF(v, va)         => isLeafValue(SetLiteralF(v, va))
    case MapLiteralF(v, va)         => isLeafValue(MapLiteralF(v, va))
    case SetComprehensionF(v, va, vb, vc) =>
      isLeafValue(SetComprehensionF(v, va, vb, vc))
    case SeqLiteralF(v, va)  => isLeafValue(SeqLiteralF(v, va))
    case MatchesF(v, va, vb) => isLeafValue(MatchesF(v, va, vb))
    case IntLitF(v, va)      => isLeafValue(IntLitF(v, va))
    case FloatLitF(v, va)    => isLeafValue(FloatLitF(v, va))
    case StringLitF(v, va)   => isLeafValue(StringLitF(v, va))
    case BoolLitF(v, va)     => isLeafValue(BoolLitF(v, va))
    case NoneLitF(v)         => isLeafValue(NoneLitF(v))
    case IdentifierF(v, va)  => isLeafValue(IdentifierF(v, va))
  }

  def tableEntityName(x0: table_spec): String = x0 match {
    case TableSpec(uu, e, uv, uw, ux, uy, uz) => e
  }

  def tablePrimaryKey(x0: table_spec): String = x0 match {
    case TableSpec(uu, uv, uw, pk, ux, uy, uz) => pk
  }

  def topoSortNames(nodes: List[(String, List[String])]): Option[List[String]] =
    topoSortStep(size_list[(String, List[String])](nodes), nodes, Nil)

  def sortTablesByFk(ts: List[table_spec]): Option[List[table_spec]] =
    topoSortNames(tableDepPairs(ts)) match {
      case None => None
      case Some(ns) =>
        val by_name =
          map[table_spec, (String, table_spec)]((t: table_spec) => (tableName(t), t), ts): List[(
              String,
              table_spec
          )];
        Some[List[table_spec]](maps[String, table_spec](
          (n: String) =>
            map_of[String, table_spec](by_name, n) match {
              case None    => Nil
              case Some(t) => List(t)
            },
          ns
        ))
    }

  def lookupChecks(
      tn: String,
      assigns: List[(String, List[(String, String)])]
  ): List[(String, String)] =
    map_of[String, List[(String, String)]](assigns, tn) match {
      case None     => Nil
      case Some(cs) => cs
    }

  def columnDefaultValue(x0: column_spec): Option[String] = x0 match {
    case ColumnSpec(uu, uv, uw, d) => d
  }

  def alterForPair(tn: String, pc: column_spec, nc: column_spec): List[migration_op] = {
    val type_change =
      (columnSqlType(pc) == columnSqlType(nc) match {
        case true => Nil
        case false =>
          List(AlterColumnType(tn, columnName(nc), columnSqlType(pc), columnSqlType(nc)))
      }): List[migration_op]
    val null_change =
      (equal_bool(columnNullable(pc), columnNullable(nc)) match {
        case true => Nil
        case false =>
          List(AlterColumnNullable(tn, columnName(nc), columnNullable(pc), columnNullable(nc)))
      }): List[migration_op]
    val def_change =
      (equal_option[String](columnDefaultValue(pc), columnDefaultValue(nc)) match {
        case true => Nil
        case false => List(AlterColumnDefault(
            tn,
            columnName(nc),
            columnDefaultValue(pc),
            columnDefaultValue(nc)
          ))
      }): List[migration_op];
    type_change ++ (null_change ++ def_change)
  }

  def intraAlters(prev: table_spec, nxt: table_spec): List[migration_op] = {
    val prev_cols =
      map[column_spec, (String, column_spec)](
        (c: column_spec) => (columnName(c), c),
        tableColumns(prev)
      ): List[(String, column_spec)];
    maps[column_spec, migration_op](
      (nc: column_spec) =>
        map_of[String, column_spec](prev_cols, columnName(nc)) match {
          case None => Nil
          case Some(pc) =>
            alterForPair(tableName(nxt), pc, nc)
        },
      tableColumns(nxt)
    )
  }

  def computeDiff(
      prev: database_schema,
      nxt: database_schema,
      prev_cks: List[(String, List[(String, String)])],
      nxt_cks: List[(String, List[(String, String)])]
  ): Option[List[migration_op]] = {
    val prev_ts = schemaTables(prev): List[table_spec]
    val nxt_ts  = schemaTables(nxt): List[table_spec]
    val prev_names =
      map[table_spec, String]((a: table_spec) => tableName(a), prev_ts): List[String]
    val nxt_names =
      map[table_spec, String]((a: table_spec) => tableName(a), nxt_ts): List[String]
    val prev_by_name =
      map[table_spec, (String, table_spec)]((t: table_spec) => (tableName(t), t), prev_ts): List[(
          String,
          table_spec
      )]
    val kept_pairs =
      maps[table_spec, (table_spec, table_spec)](
        (n: table_spec) =>
          map_of[String, table_spec](prev_by_name, tableName(n)) match {
            case None    => Nil
            case Some(p) => List((p, n))
          },
        nxt_ts
      ): List[(table_spec, table_spec)]
    val drops_inside =
      maps[(table_spec, table_spec), migration_op](
        (pn: (table_spec, table_spec)) =>
          intraDrops(
            fst[table_spec, table_spec](pn),
            snd[table_spec, table_spec](pn),
            lookupChecks(tableName(snd[table_spec, table_spec](pn)), prev_cks),
            lookupChecks(tableName(snd[table_spec, table_spec](pn)), nxt_cks)
          ),
        kept_pairs
      ): List[migration_op]
    val adds_inside =
      maps[(table_spec, table_spec), migration_op](
        (pn: (table_spec, table_spec)) =>
          intraAdds(
            fst[table_spec, table_spec](pn),
            snd[table_spec, table_spec](pn),
            lookupChecks(tableName(snd[table_spec, table_spec](pn)), prev_cks),
            lookupChecks(tableName(snd[table_spec, table_spec](pn)), nxt_cks)
          ),
        kept_pairs
      ): List[migration_op]
    val alters_inside =
      maps[(table_spec, table_spec), migration_op](
        (pn: (table_spec, table_spec)) =>
          intraAlters(fst[table_spec, table_spec](pn), snd[table_spec, table_spec](pn)),
        kept_pairs
      ): List[migration_op]
    val dropped_table_specs =
      filter[table_spec](
        (t: table_spec) =>
          !membera[String](nxt_names, tableName(t)),
        prev_ts
      ): List[table_spec]
    val created_table_specs =
      filter[table_spec](
        (t: table_spec) =>
          !membera[String](prev_names, tableName(t)),
        nxt_ts
      ): List[table_spec]
    val prev_trg = schemaTriggers(prev): List[trigger_spec]
    val nxt_trg  = schemaTriggers(nxt): List[trigger_spec]
    val dropped_triggers =
      map_filter[trigger_spec, migration_op](
        (x: trigger_spec) =>
          !membera[trigger_spec](nxt_trg, x) match {
            case true  => Some[migration_op](DropTrigger(x))
            case false => None
          },
        prev_trg
      ): List[migration_op]
    val added_triggers =
      map_filter[trigger_spec, migration_op](
        (x: trigger_spec) =>
          !membera[trigger_spec](prev_trg, x) match {
            case true  => Some[migration_op](AddTrigger(x))
            case false => None
          },
        nxt_trg
      ): List[migration_op];
    (sortTablesByFk(dropped_table_specs), sortTablesByFk(created_table_specs)) match {
      case (None, _)       => None
      case (Some(_), None) => None
      case (Some(dts), Some(cts)) =>
        Some[List[migration_op]](dropped_triggers ++
          (drops_inside ++
            (map[table_spec, migration_op]((a: table_spec) => DropTable(a), rev[table_spec](dts)) ++
              (map[table_spec, migration_op]((a: table_spec) => CreateTable(a), cts) ++
                (alters_inside ++ (adds_inside ++ added_triggers))))))
    }
  }

  def serviceEnums(x0: service_ir): List[enum_decl] = x0 match {
    case ServiceIRFull(uu, uv, uw, en, ux, uy, uz, va, vb, vc, vd, ve, vf, vg, vh, vi) => en
  }

  def typeExprToTy(x0: schema_type): Option[ty] = x0 match {
    case BoolT()           => Some[ty](TBool())
    case IntT()            => Some[ty](TInt())
    case RealT()           => Some[ty](TReal())
    case StrT()            => Some[ty](TStr())
    case EnumT(n)          => Some[ty](TEnum(n))
    case EntityT(n)        => Some[ty](TEntity(n))
    case RelationT(uu, uv) => None
    case OptionT(t) =>
      map_option[ty, ty]((a: ty) => TOption(a), typeExprToTy(t))
    case SeqT(t) => map_option[ty, ty]((a: ty) => TSeq(a), typeExprToTy(t))
    case MapT(k, v) => (typeExprToTy(k), typeExprToTy(v)) match {
        case (None, _)            => None
        case (Some(_), None)      => None
        case (Some(tk), Some(tv)) => Some[ty](TMap(tk, tv))
      }
  }

  def min[A: ord](a: A, b: A): A =
    less_eq[A](a, b) match {
      case true  => a
      case false => b
    }

  def mergeMaxInt(a: Option[BigInt], b: Option[BigInt]): Option[BigInt] =
    (a, b) match {
      case (None, y)          => y
      case (Some(x), None)    => Some[BigInt](x)
      case (Some(x), Some(y)) => Some[BigInt](min[BigInt](x, y))
    }

  def mergeMinInt(a: Option[BigInt], b: Option[BigInt]): Option[BigInt] =
    (a, b) match {
      case (None, y)          => y
      case (Some(x), None)    => Some[BigInt](x)
      case (Some(x), Some(y)) => Some[BigInt](max[BigInt](x, y))
    }

  def lookupArity(x0: List[(String, BigInt)], uu: String): Option[BigInt] =
    (x0, uu) match {
      case (Nil, uu) => None
      case ((nm, ar) :: rest, fname) =>
        nm == fname match {
          case true  => Some[BigInt](ar)
          case false => lookupArity(rest, fname)
        }
    }

  def boolSigs: List[alloy_sig] =
    List(
      AlloySigLifted("Bool", true, false, None, Nil),
      AlloySigLifted("True", false, true, Some[String]("Bool"), Nil),
      AlloySigLifted("False", false, true, Some[String]("Bool"), Nil)
    )

  def exprContainsBoolLit(e: expr): Boolean =
    list_ex[expr]((a: expr) => isBoolLit(a), allSubexprs(e))

  def operationHasBoolLit(x0: operation_decl): Boolean = x0 match {
    case OperationDeclFull(uu, uv, uw, requiresa, ensures, ux, uy) =>
      list_ex[expr]((a: expr) => exprContainsBoolLit(a), requiresa) ||
      list_ex[expr]((a: expr) => exprContainsBoolLit(a), ensures)
  }

  def invariantHasBoolLit(x0: invariant_decl): Boolean = x0 match {
    case InvariantDeclFull(uu, body, uv) => exprContainsBoolLit(body)
  }

  def typeContainsNamed(n: String, x1: type_expr): Boolean = (n, x1) match {
    case (n, NamedTypeF(m, uu))             => n == m
    case (n, SetTypeF(inner, uv))           => typeContainsNamed(n, inner)
    case (n, OptionTypeF(inner, uw))        => typeContainsNamed(n, inner)
    case (ux, MapTypeF(v, va, vb))          => false
    case (ux, SeqTypeF(v, va))              => false
    case (ux, RelationTypeF(v, va, vb, vc)) => false
  }

  def temporalArg(x0: temporal_body): expr = x0 match {
    case TbAlways(e)     => e
    case TbEventually(e) => e
    case TbFairness(e)   => e
    case TbInvalid(e)    => e
  }

  def temporalHasBoolLit(x0: temporal_decl): Boolean = x0 match {
    case TemporalDeclFull(uu, tb, uv) => exprContainsBoolLit(temporalArg(tb))
  }

  def fieldTypeHasBool(x0: field_decl): Boolean = x0 match {
    case FieldDeclFull(uu, t, uv, uw) => typeContainsNamed("Bool", t)
  }

  def entityHasBoolField(x0: entity_decl): Boolean = x0 match {
    case EntityDeclFull(uu, uv, fs, uw, ux) =>
      list_ex[field_decl]((a: field_decl) => fieldTypeHasBool(a), fs)
  }

  def needsBoolSig(
      x0: service_ir,
      stateFields: List[(String, type_expr)],
      inputFields: List[(String, type_expr)]
  ): Boolean =
    (x0, stateFields, inputFields) match {
      case (
            ServiceIRFull(uu, uv, es, uw, ux, uy, ops, uz, invs, temps, va, vb, vc, vd, ve, vf),
            stateFields,
            inputFields
          ) => list_ex[entity_decl]((a: entity_decl) => entityHasBoolField(a), es) ||
        (list_ex[(String, type_expr)](
          (kv: (String, type_expr)) =>
            typeContainsNamed("Bool", snd[String, type_expr](kv)),
          stateFields
        ) ||
          (list_ex[(String, type_expr)](
            (kv: (String, type_expr)) =>
              typeContainsNamed("Bool", snd[String, type_expr](kv)),
            inputFields
          ) ||
            (list_ex[invariant_decl](
              (a: invariant_decl) =>
                invariantHasBoolLit(a),
              invs
            ) ||
              (list_ex[temporal_decl](
                (a: temporal_decl) =>
                  temporalHasBoolLit(a),
                temps
              ) ||
                list_ex[operation_decl](
                  (a: operation_decl) =>
                    operationHasBoolLit(a),
                  ops
                )))))
    }

  def setTargetEntityFieldCount(v: Option[nat], x1: analysis_signals): analysis_signals =
    (v, x1) match {
      case (v, AnalysisSignals(m, p, c, d, uu, w, f, t, h)) =>
        AnalysisSignals(m, p, c, d, v, w, f, t, h)
    }

  def signalsWithFieldCount(x0: analysis_signals): Option[nat] = x0 match {
    case AnalysisSignals(uu, uv, uw, ux, uy, w, uz, va, vb) => w
  }

  def decidePutPatch(
      signals: analysis_signals,
      entityFieldCount: Option[nat]
  ): classification_result =
    signalsWithFieldCount(signals) match {
      case None => ClassificationResult(PartialUpdate(), PATCH(), "M4", signals)
      case Some(wfc) =>
        entityFieldCount match {
          case None =>
            ClassificationResult(PartialUpdate(), PATCH(), "M4", signals)
          case Some(totalCount) =>
            val updated =
              setTargetEntityFieldCount(Some[nat](totalCount), signals): analysis_signals;
            less_eq_nat(totalCount, wfc) match {
              case true  => ClassificationResult(Replace(), PUT(), "M3", updated)
              case false => ClassificationResult(PartialUpdate(), PATCH(), "M4", updated)
            }
        }
    }

  def scalarUpdateOf(scalars: List[String], x1: expr): Option[(String, scalar_rhs)] =
    (scalars, x1) match {
      case (scalars, BinaryOpF(op, lhs, rhs, uu)) =>
        op match {
          case BAnd()     => None
          case BOr()      => None
          case BImplies() => None
          case BIff()     => None
          case BEq() =>
            lhs match {
              case BinaryOpF(_, _, _, _)                    => None
              case UnaryOpF(_, _, _)                        => None
              case QuantifierF(_, _, _, _)                  => None
              case SomeWrapF(_, _)                          => None
              case TheF(_, _, _, _)                         => None
              case FieldAccessF(_, _, _)                    => None
              case EnumAccessF(_, _, _)                     => None
              case IndexF(_, _, _)                          => None
              case CallF(_, _, _)                           => None
              case PrimeF(BinaryOpF(_, _, _, _), _)         => None
              case PrimeF(UnaryOpF(_, _, _), _)             => None
              case PrimeF(QuantifierF(_, _, _, _), _)       => None
              case PrimeF(SomeWrapF(_, _), _)               => None
              case PrimeF(TheF(_, _, _, _), _)              => None
              case PrimeF(FieldAccessF(_, _, _), _)         => None
              case PrimeF(EnumAccessF(_, _, _), _)          => None
              case PrimeF(IndexF(_, _, _), _)               => None
              case PrimeF(CallF(_, _, _), _)                => None
              case PrimeF(PrimeF(_, _), _)                  => None
              case PrimeF(PreF(_, _), _)                    => None
              case PrimeF(WithF(_, _, _), _)                => None
              case PrimeF(IfF(_, _, _, _), _)               => None
              case PrimeF(LetF(_, _, _, _), _)              => None
              case PrimeF(LambdaF(_, _, _), _)              => None
              case PrimeF(ConstructorF(_, _, _), _)         => None
              case PrimeF(SetLiteralF(_, _), _)             => None
              case PrimeF(MapLiteralF(_, _), _)             => None
              case PrimeF(SetComprehensionF(_, _, _, _), _) => None
              case PrimeF(SeqLiteralF(_, _), _)             => None
              case PrimeF(MatchesF(_, _, _), _)             => None
              case PrimeF(IntLitF(_, _), _)                 => None
              case PrimeF(FloatLitF(_, _), _)               => None
              case PrimeF(StringLitF(_, _), _)              => None
              case PrimeF(BoolLitF(_, _), _)                => None
              case PrimeF(NoneLitF(_), _)                   => None
              case PrimeF(IdentifierF(n, _), _) =>
                membera[String](scalars, n) match {
                  case true => map_option[scalar_rhs, (String, scalar_rhs)](
                      (a: scalar_rhs) => (n, a),
                      scalarRhsOf(n, rhs)
                    )
                  case false => None
                }
              case PreF(_, _)                    => None
              case WithF(_, _, _)                => None
              case IfF(_, _, _, _)               => None
              case LetF(_, _, _, _)              => None
              case LambdaF(_, _, _)              => None
              case ConstructorF(_, _, _)         => None
              case SetLiteralF(_, _)             => None
              case MapLiteralF(_, _)             => None
              case SetComprehensionF(_, _, _, _) => None
              case SeqLiteralF(_, _)             => None
              case MatchesF(_, _, _)             => None
              case IntLitF(_, _)                 => None
              case FloatLitF(_, _)               => None
              case StringLitF(_, _)              => None
              case BoolLitF(_, _)                => None
              case NoneLitF(_)                   => None
              case IdentifierF(_, _)             => None
            }
          case BNeq()       => None
          case BLt()        => None
          case BGt()        => None
          case BLe()        => None
          case BGe()        => None
          case BIn()        => None
          case BNotIn()     => None
          case BSubset()    => None
          case BUnion()     => None
          case BIntersect() => None
          case BDiff()      => None
          case BAdd()       => None
          case BSub()       => None
          case BMul()       => None
          case BDiv()       => None
        }
      case (uv, UnaryOpF(v, va, vb))              => None
      case (uv, QuantifierF(v, va, vb, vc))       => None
      case (uv, SomeWrapF(v, va))                 => None
      case (uv, TheF(v, va, vb, vc))              => None
      case (uv, FieldAccessF(v, va, vb))          => None
      case (uv, EnumAccessF(v, va, vb))           => None
      case (uv, IndexF(v, va, vb))                => None
      case (uv, CallF(v, va, vb))                 => None
      case (uv, PrimeF(v, va))                    => None
      case (uv, PreF(v, va))                      => None
      case (uv, WithF(v, va, vb))                 => None
      case (uv, IfF(v, va, vb, vc))               => None
      case (uv, LetF(v, va, vb, vc))              => None
      case (uv, LambdaF(v, va, vb))               => None
      case (uv, ConstructorF(v, va, vb))          => None
      case (uv, SetLiteralF(v, va))               => None
      case (uv, MapLiteralF(v, va))               => None
      case (uv, SetComprehensionF(v, va, vb, vc)) => None
      case (uv, SeqLiteralF(v, va))               => None
      case (uv, MatchesF(v, va, vb))              => None
      case (uv, IntLitF(v, va))                   => None
      case (uv, FloatLitF(v, va))                 => None
      case (uv, StringLitF(v, va))                => None
      case (uv, BoolLitF(v, va))                  => None
      case (uv, NoneLitF(v))                      => None
      case (uv, IdentifierF(v, va))               => None
    }

  def isSerial4(x0: canonical_type): Boolean = x0 match {
    case CtSerial4()      => true
    case CtText()         => false
    case CtVarchar(v)     => false
    case CtInt4()         => false
    case CtInt8()         => false
    case CtSerial8()      => false
    case CtFloat8()       => false
    case CtBool()         => false
    case CtTimestamptz()  => false
    case CtDateOnly()     => false
    case CtUuid()         => false
    case CtNumeric(v, va) => false
    case CtBytes()        => false
    case CtJson()         => false
  }

  def mysqlCaps: dialect_caps =
    DialectCaps(false, true, false, true, true, false)

  def fasName(x0: field_assign): String = x0 match {
    case FieldAssignFull(x1, x2, x3) => x1
  }

  def fasSpan(x0: field_assign): Option[span_t] = x0 match {
    case FieldAssignFull(x1, x2, x3) => x3
  }

  def stdFields(x0: state_decl): List[state_field_decl] = x0 match {
    case StateDeclFull(x1, x2) => x1
  }

  def bind_params(uu: List[String], uv: List[expr], body: expr): expr =
    (uu, uv, body) match {
      case (p :: ps, a :: args, body) =>
        LetF(p, a, bind_params(ps, args, body), None)
      case (Nil, uv, body) => body
      case (uu, Nil, body) => body
    }

  def less_eq_int(k: BigInt, l: BigInt): Boolean = k <= l

  def desiredSize(x0: bin_op, n: BigInt): Option[BigInt] = (x0, n) match {
    case (BGt(), n) => Some[BigInt](max[BigInt](zero_int, plus_int(n, one_inta)))
    case (BGe(), n) => Some[BigInt](max[BigInt](zero_int, n))
    case (BEq(), n) =>
      less_eq_int(zero_int, n) match {
        case true  => Some[BigInt](n)
        case false => None
      }
    case (BLt(), n) =>
      less_int(zero_int, n) match {
        case true  => Some[BigInt](zero_int)
        case false => None
      }
    case (BLe(), n) =>
      less_eq_int(zero_int, n) match {
        case true  => Some[BigInt](zero_int)
        case false => None
      }
    case (BAnd(), uv)       => None
    case (BOr(), uv)        => None
    case (BImplies(), uv)   => None
    case (BIff(), uv)       => None
    case (BNeq(), uv)       => None
    case (BIn(), uv)        => None
    case (BNotIn(), uv)     => None
    case (BSubset(), uv)    => None
    case (BUnion(), uv)     => None
    case (BIntersect(), uv) => None
    case (BDiff(), uv)      => None
    case (BAdd(), uv)       => None
    case (BSub(), uv)       => None
    case (BMul(), uv)       => None
    case (BDiv(), uv)       => None
  }

  def enumLitName(x0: expr): Option[String] = x0 match {
    case EnumAccessF(uu, m, uv)           => Some[String](m)
    case IdentifierF(n, uw)               => Some[String](n)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
  }

  def hasPrePrime(e: expr): Boolean =
    list_ex[expr]((a: expr) => isPrePrime(a), allSubexprs(e))

  def assignsField(x0: expr, field: String): Boolean = (x0, field) match {
    case (FieldAccessF(uu, f, uv), field)       => f == field
    case (IdentifierF(n, uw), field)            => n == field
    case (PrimeF(inner, ux), field)             => assignsField(inner, field)
    case (IndexF(base, uy, uz), field)          => assignsField(base, field)
    case (BinaryOpF(v, vc, vd, ve), vb)         => false
    case (UnaryOpF(v, vc, vd), vb)              => false
    case (QuantifierF(v, vc, vd, ve), vb)       => false
    case (SomeWrapF(v, vc), vb)                 => false
    case (TheF(v, vc, vd, ve), vb)              => false
    case (EnumAccessF(v, vc, vd), vb)           => false
    case (CallF(v, vc, vd), vb)                 => false
    case (PreF(v, vc), vb)                      => false
    case (WithF(v, vc, vd), vb)                 => false
    case (IfF(v, vc, vd, ve), vb)               => false
    case (LetF(v, vc, vd, ve), vb)              => false
    case (LambdaF(v, vc, vd), vb)               => false
    case (ConstructorF(v, vc, vd), vb)          => false
    case (SetLiteralF(v, vc), vb)               => false
    case (MapLiteralF(v, vc), vb)               => false
    case (SetComprehensionF(v, vc, vd, ve), vb) => false
    case (SeqLiteralF(v, vc), vb)               => false
    case (MatchesF(v, vc, vd), vb)              => false
    case (IntLitF(v, vc), vb)                   => false
    case (FloatLitF(v, vc), vb)                 => false
    case (StringLitF(v, vc), vb)                => false
    case (BoolLitF(v, vc), vb)                  => false
    case (NoneLitF(v), vb)                      => false
  }

  def enumNameFull(x0: enum_decl): String = x0 match {
    case EnumDeclFull(n, uu, uv) => n
  }

  def fieldNameFull(x0: field_decl): String = x0 match {
    case FieldDeclFull(n, uu, uv, uw) => n
  }

  def upsert_field(acc: List[field_decl], fd: field_decl): List[field_decl] =
    list_ex[field_decl](
      (g: field_decl) =>
        fieldNameFull(g) == fieldNameFull(fd),
      acc
    ) match {
      case true => map[field_decl, field_decl](
          (g: field_decl) =>
            fieldNameFull(g) ==
              fieldNameFull(fd) match {
              case true  => fd
              case false => g
            },
          acc
        )
      case false => acc ++ List(fd)
    }

  def listIsSubset(x0: List[String], uu: List[String]): Boolean = (x0, uu) match {
    case (Nil, uu)     => true
    case (x :: xs, ys) => membera[String](ys, x) && listIsSubset(xs, ys)
  }

  def cycleSetEq(c1: List[String], c2: List[String]): Boolean =
    listIsSubset(c1, c2) && listIsSubset(c2, c1)

  def cycles[A](x0: dfs_state_ext[A]): List[List[String]] = x0 match {
    case dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more) =>
      cycles
  }

  def times_nat(m: nat, n: nat): nat =
    Nata(integer_of_nat(m) * integer_of_nat(n))

  def onStack_update[A](
      onStacka: (List[String]) => List[String],
      x1: dfs_state_ext[A]
  ): dfs_state_ext[A] =
    (onStacka, x1) match {
      case (onStacka, dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more)) =>
        dfs_state_exta[A](onStacka(onStack), visited, stack, cycles, seenCycles, more)
    }

  def stack_update[A](
      stacka: (List[String]) => List[String],
      x1: dfs_state_ext[A]
  ): dfs_state_ext[A] =
    (stacka, x1) match {
      case (stacka, dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more)) =>
        dfs_state_exta[A](onStack, visited, stacka(stack), cycles, seenCycles, more)
    }

  def seenCycles_update[A](
      seenCyclesa: (List[List[String]]) => List[List[String]],
      x1: dfs_state_ext[A]
  ): dfs_state_ext[A] =
    (seenCyclesa, x1) match {
      case (seenCyclesa, dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more)) =>
        dfs_state_exta[A](onStack, visited, stack, cycles, seenCyclesa(seenCycles), more)
    }

  def visited_update[A](
      visiteda: (List[String]) => List[String],
      x1: dfs_state_ext[A]
  ): dfs_state_ext[A] =
    (visiteda, x1) match {
      case (visiteda, dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more)) =>
        dfs_state_exta[A](onStack, visiteda(visited), stack, cycles, seenCycles, more)
    }

  def cycles_update[A](
      cyclesa: (List[List[String]]) => List[List[String]],
      x1: dfs_state_ext[A]
  ): dfs_state_ext[A] =
    (cyclesa, x1) match {
      case (cyclesa, dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more)) =>
        dfs_state_exta[A](onStack, visited, stack, cyclesa(cycles), seenCycles, more)
    }

  def seenCycles[A](x0: dfs_state_ext[A]): List[List[String]] = x0 match {
    case dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more) =>
      seenCycles
  }

  def visited[A](x0: dfs_state_ext[A]): List[String] = x0 match {
    case dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more) =>
      visited
  }

  def onStack[A](x0: dfs_state_ext[A]): List[String] = x0 match {
    case dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more) =>
      onStack
  }

  def cycleAlreadySeen(uu: List[String], x1: List[List[String]]): Boolean =
    (uu, x1) match {
      case (uu, Nil)      => false
      case (c, s :: rest) => cycleSetEq(c, s) || cycleAlreadySeen(c, rest)
    }

  def stack[A](x0: dfs_state_ext[A]): List[String] = x0 match {
    case dfs_state_exta(onStack, visited, stack, cycles, seenCycles, more) =>
      stack
  }

  def sliceFromNode(uu: String, x1: List[String]): List[String] = (uu, x1) match {
    case (uu, Nil) => Nil
    case (n, x :: xs) =>
      x == n match {
        case true  => x :: xs
        case false => sliceFromNode(n, xs)
      }
  }

  def listRemoveAll(uu: String, x1: List[String]): List[String] = (uu, x1) match {
    case (uu, Nil) => Nil
    case (x, y :: ys) =>
      x == y match {
        case true  => listRemoveAll(x, ys)
        case false => y :: listRemoveAll(x, ys)
      }
  }

  def lookupEdges(uu: String, x1: List[(String, List[String])]): List[String] =
    (uu, x1) match {
      case (uu, Nil) => Nil
      case (n, (k, v) :: rest) =>
        k == n match {
          case true  => v
          case false => lookupEdges(n, rest)
        }
    }

  def dfsChildrenFuel(
      fuel: nat,
      edges: List[(String, List[String])],
      x2: List[String],
      state: dfs_state_ext[Unit]
  ): dfs_state_ext[Unit] =
    (fuel, edges, x2, state) match {
      case (fuel, edges, Nil, state) => state
      case (fuel, edges, c :: rest, state) =>
        equal_nat(fuel, zero_nat) match {
          case true => state
          case false => dfsChildrenFuel(
              minus_nat(fuel, one_nat),
              edges,
              rest,
              dfsNodeFuel(minus_nat(fuel, one_nat), edges, c, state)
            )
        }
    }

  def dfsNodeFuel(
      fuel: nat,
      edges: List[(String, List[String])],
      n: String,
      state: dfs_state_ext[Unit]
  ): dfs_state_ext[Unit] =
    equal_nat(fuel, zero_nat) match {
      case true => state
      case false => membera[String](onStack[Unit](state), n) match {
          case true =>
            val cyc =
              sliceFromNode(n, stack[Unit](state)): List[String];
            nulla[String](cyc) match {
              case true => state
              case false => cycleAlreadySeen(cyc, seenCycles[Unit](state)) match {
                  case true => state
                  case false => seenCycles_update[Unit](
                      (_: List[List[String]]) =>
                        cyc :: seenCycles[Unit](state),
                      cycles_update[Unit](
                        (_: List[List[String]]) =>
                          cycles[Unit](state) ++ List(cyc),
                        state
                      )
                    )
                }
            }
          case false => membera[String](visited[Unit](state), n) match {
              case true => state
              case false =>
                val state1 =
                  stack_update[Unit](
                    (_: List[String]) =>
                      stack[Unit](state) ++ List(n),
                    visited_update[Unit](
                      (_: List[String]) =>
                        n :: visited[Unit](state),
                      onStack_update[Unit](
                        (_: List[String]) =>
                          n :: onStack[Unit](state),
                        state
                      )
                    )
                  ): dfs_state_ext[Unit]
                val children = lookupEdges(n, edges): List[String]
                val state2 =
                  dfsChildrenFuel(minus_nat(fuel, one_nat), edges, children, state1): dfs_state_ext[
                    Unit
                  ];
                stack_update[Unit](
                  (_: List[String]) =>
                    butlast[String](stack[Unit](state2)),
                  onStack_update[Unit](
                    (_: List[String]) =>
                      listRemoveAll(n, onStack[Unit](state2)),
                    state2
                  )
                )
            }
        }
    }

  def findCyclesAux(
      uu: nat,
      uv: List[(String, List[String])],
      x2: List[String],
      state: dfs_state_ext[Unit]
  ): dfs_state_ext[Unit] =
    (uu, uv, x2, state) match {
      case (uu, uv, Nil, state) => state
      case (fuel, edges, n :: rest, state) =>
        val state1 =
          stack_update[Unit](
            (_: List[String]) => Nil,
            onStack_update[Unit]((_: List[String]) => Nil, state)
          ): dfs_state_ext[Unit]
        val a = dfsNodeFuel(fuel, edges, n, state1): dfs_state_ext[Unit];
        findCyclesAux(fuel, edges, rest, a)
    }

  def initDfsState: dfs_state_ext[Unit] =
    dfs_state_exta[Unit](Nil, Nil, Nil, Nil, Nil, ())

  def findCycles(nodes: List[String], edges: List[(String, List[String])]): List[List[String]] =
    cycles[Unit](findCyclesAux(
      plus_nat(
        plus_nat(
          times_nat(size_list[String](nodes), size_list[String](nodes)),
          size_list[String](nodes)
        ),
        one_nat
      ),
      edges,
      nodes,
      initDfsState
    ))

  def parseHttpMethod(s: String): Option[http_method] =
    s == "GET" match {
      case true => Some[http_method](GET())
      case false => s == "POST" match {
          case true => Some[http_method](POST())
          case false => s == "PUT" match {
              case true => Some[http_method](PUT())
              case false => s == "PATCH" match {
                  case true => Some[http_method](PATCH())
                  case false => s == "DELETE" match {
                      case true  => Some[http_method](DELETE())
                      case false => None
                    }
                }
            }
        }
    }

  def literalLength(s: String): nat =
    size_list[BigInt](Str_Literal.asciisOfLiteral(s))

  def typeWalkFuel(aliases: List[type_alias_decl]): nat =
    plus_nat(size_list[type_alias_decl](aliases), nat_of_integer(BigInt(100)))

  def optConcat(a: Option[List[alloy_sig]], b: Option[List[alloy_sig]]): Option[List[alloy_sig]] =
    a match {
      case None => None
      case Some(xs) => b match {
          case None     => None
          case Some(ys) => Some[List[alloy_sig]](xs ++ ys)
        }
    }

  def neqNoneName(e: expr): Option[String] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)                                     => None
      case BinaryOpF(BOr(), _, _, _)                                      => None
      case BinaryOpF(BImplies(), _, _, _)                                 => None
      case BinaryOpF(BIff(), _, _, _)                                     => None
      case BinaryOpF(BEq(), _, _, _)                                      => None
      case BinaryOpF(BNeq(), BinaryOpF(_, _, _, _), _, _)                 => None
      case BinaryOpF(BNeq(), UnaryOpF(_, _, _), _, _)                     => None
      case BinaryOpF(BNeq(), QuantifierF(_, _, _, _), _, _)               => None
      case BinaryOpF(BNeq(), SomeWrapF(_, _), _, _)                       => None
      case BinaryOpF(BNeq(), TheF(_, _, _, _), _, _)                      => None
      case BinaryOpF(BNeq(), FieldAccessF(_, _, _), _, _)                 => None
      case BinaryOpF(BNeq(), EnumAccessF(_, _, _), _, _)                  => None
      case BinaryOpF(BNeq(), IndexF(_, _, _), _, _)                       => None
      case BinaryOpF(BNeq(), CallF(_, _, _), _, _)                        => None
      case BinaryOpF(BNeq(), PrimeF(_, _), _, _)                          => None
      case BinaryOpF(BNeq(), PreF(_, _), _, _)                            => None
      case BinaryOpF(BNeq(), WithF(_, _, _), _, _)                        => None
      case BinaryOpF(BNeq(), IfF(_, _, _, _), _, _)                       => None
      case BinaryOpF(BNeq(), LetF(_, _, _, _), _, _)                      => None
      case BinaryOpF(BNeq(), LambdaF(_, _, _), _, _)                      => None
      case BinaryOpF(BNeq(), ConstructorF(_, _, _), _, _)                 => None
      case BinaryOpF(BNeq(), SetLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BNeq(), MapLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BNeq(), SetComprehensionF(_, _, _, _), _, _)         => None
      case BinaryOpF(BNeq(), SeqLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BNeq(), MatchesF(_, _, _), _, _)                     => None
      case BinaryOpF(BNeq(), IntLitF(_, _), _, _)                         => None
      case BinaryOpF(BNeq(), FloatLitF(_, _), _, _)                       => None
      case BinaryOpF(BNeq(), StringLitF(_, _), _, _)                      => None
      case BinaryOpF(BNeq(), BoolLitF(_, _), _, _)                        => None
      case BinaryOpF(BNeq(), NoneLitF(_), _, _)                           => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), BinaryOpF(_, _, _, _), _) => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), UnaryOpF(_, _, _), _)     => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), QuantifierF(_, _, _, _), _) =>
        None
      case BinaryOpF(BNeq(), IdentifierF(_, _), SomeWrapF(_, _), _)               => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), TheF(_, _, _, _), _)              => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), FieldAccessF(_, _, _), _)         => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), EnumAccessF(_, _, _), _)          => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), IndexF(_, _, _), _)               => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), CallF(_, _, _), _)                => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), PrimeF(_, _), _)                  => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), PreF(_, _), _)                    => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), WithF(_, _, _), _)                => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), IfF(_, _, _, _), _)               => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), LetF(_, _, _, _), _)              => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), LambdaF(_, _, _), _)              => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), ConstructorF(_, _, _), _)         => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), SetLiteralF(_, _), _)             => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), MapLiteralF(_, _), _)             => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), SetComprehensionF(_, _, _, _), _) => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), SeqLiteralF(_, _), _)             => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), MatchesF(_, _, _), _)             => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), IntLitF(_, _), _)                 => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), FloatLitF(_, _), _)               => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), StringLitF(_, _), _)              => None
      case BinaryOpF(BNeq(), IdentifierF(_, _), BoolLitF(_, _), _)                => None
      case BinaryOpF(BNeq(), IdentifierF(p, _), NoneLitF(_), _) =>
        Some[String](p)
      case BinaryOpF(BNeq(), IdentifierF(_, _), IdentifierF(_, _), _) => None
      case BinaryOpF(BLt(), _, _, _)                                  => None
      case BinaryOpF(BGt(), _, _, _)                                  => None
      case BinaryOpF(BLe(), _, _, _)                                  => None
      case BinaryOpF(BGe(), _, _, _)                                  => None
      case BinaryOpF(BIn(), _, _, _)                                  => None
      case BinaryOpF(BNotIn(), _, _, _)                               => None
      case BinaryOpF(BSubset(), _, _, _)                              => None
      case BinaryOpF(BUnion(), _, _, _)                               => None
      case BinaryOpF(BIntersect(), _, _, _)                           => None
      case BinaryOpF(BDiff(), _, _, _)                                => None
      case BinaryOpF(BAdd(), _, _, _)                                 => None
      case BinaryOpF(BSub(), _, _, _)                                 => None
      case BinaryOpF(BMul(), _, _, _)                                 => None
      case BinaryOpF(BDiv(), _, _, _)                                 => None
      case UnaryOpF(_, _, _)                                          => None
      case QuantifierF(_, _, _, _)                                    => None
      case SomeWrapF(_, _)                                            => None
      case TheF(_, _, _, _)                                           => None
      case FieldAccessF(_, _, _)                                      => None
      case EnumAccessF(_, _, _)                                       => None
      case IndexF(_, _, _)                                            => None
      case CallF(_, _, _)                                             => None
      case PrimeF(_, _)                                               => None
      case PreF(_, _)                                                 => None
      case WithF(_, _, _)                                             => None
      case IfF(_, _, _, _)                                            => None
      case LetF(_, _, _, _)                                           => None
      case LambdaF(_, _, _)                                           => None
      case ConstructorF(_, _, _)                                      => None
      case SetLiteralF(_, _)                                          => None
      case MapLiteralF(_, _)                                          => None
      case SetComprehensionF(_, _, _, _)                              => None
      case SeqLiteralF(_, _)                                          => None
      case MatchesF(_, _, _)                                          => None
      case IntLitF(_, _)                                              => None
      case FloatLitF(_, _)                                            => None
      case StringLitF(_, _)                                           => None
      case BoolLitF(_, _)                                             => None
      case NoneLitF(_)                                                => None
      case IdentifierF(_, _)                                          => None
    }

  def substValue(p: String, body: expr): expr =
    subst(p, FieldAccessF(IdentifierF(p, None), "value", None), body)

  def eqNoneName(e: expr): Option[String] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)                                    => None
      case BinaryOpF(BOr(), _, _, _)                                     => None
      case BinaryOpF(BImplies(), _, _, _)                                => None
      case BinaryOpF(BIff(), _, _, _)                                    => None
      case BinaryOpF(BEq(), BinaryOpF(_, _, _, _), _, _)                 => None
      case BinaryOpF(BEq(), UnaryOpF(_, _, _), _, _)                     => None
      case BinaryOpF(BEq(), QuantifierF(_, _, _, _), _, _)               => None
      case BinaryOpF(BEq(), SomeWrapF(_, _), _, _)                       => None
      case BinaryOpF(BEq(), TheF(_, _, _, _), _, _)                      => None
      case BinaryOpF(BEq(), FieldAccessF(_, _, _), _, _)                 => None
      case BinaryOpF(BEq(), EnumAccessF(_, _, _), _, _)                  => None
      case BinaryOpF(BEq(), IndexF(_, _, _), _, _)                       => None
      case BinaryOpF(BEq(), CallF(_, _, _), _, _)                        => None
      case BinaryOpF(BEq(), PrimeF(_, _), _, _)                          => None
      case BinaryOpF(BEq(), PreF(_, _), _, _)                            => None
      case BinaryOpF(BEq(), WithF(_, _, _), _, _)                        => None
      case BinaryOpF(BEq(), IfF(_, _, _, _), _, _)                       => None
      case BinaryOpF(BEq(), LetF(_, _, _, _), _, _)                      => None
      case BinaryOpF(BEq(), LambdaF(_, _, _), _, _)                      => None
      case BinaryOpF(BEq(), ConstructorF(_, _, _), _, _)                 => None
      case BinaryOpF(BEq(), SetLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BEq(), MapLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BEq(), SetComprehensionF(_, _, _, _), _, _)         => None
      case BinaryOpF(BEq(), SeqLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BEq(), MatchesF(_, _, _), _, _)                     => None
      case BinaryOpF(BEq(), IntLitF(_, _), _, _)                         => None
      case BinaryOpF(BEq(), FloatLitF(_, _), _, _)                       => None
      case BinaryOpF(BEq(), StringLitF(_, _), _, _)                      => None
      case BinaryOpF(BEq(), BoolLitF(_, _), _, _)                        => None
      case BinaryOpF(BEq(), NoneLitF(_), _, _)                           => None
      case BinaryOpF(BEq(), IdentifierF(_, _), BinaryOpF(_, _, _, _), _) => None
      case BinaryOpF(BEq(), IdentifierF(_, _), UnaryOpF(_, _, _), _)     => None
      case BinaryOpF(BEq(), IdentifierF(_, _), QuantifierF(_, _, _, _), _) =>
        None
      case BinaryOpF(BEq(), IdentifierF(_, _), SomeWrapF(_, _), _)               => None
      case BinaryOpF(BEq(), IdentifierF(_, _), TheF(_, _, _, _), _)              => None
      case BinaryOpF(BEq(), IdentifierF(_, _), FieldAccessF(_, _, _), _)         => None
      case BinaryOpF(BEq(), IdentifierF(_, _), EnumAccessF(_, _, _), _)          => None
      case BinaryOpF(BEq(), IdentifierF(_, _), IndexF(_, _, _), _)               => None
      case BinaryOpF(BEq(), IdentifierF(_, _), CallF(_, _, _), _)                => None
      case BinaryOpF(BEq(), IdentifierF(_, _), PrimeF(_, _), _)                  => None
      case BinaryOpF(BEq(), IdentifierF(_, _), PreF(_, _), _)                    => None
      case BinaryOpF(BEq(), IdentifierF(_, _), WithF(_, _, _), _)                => None
      case BinaryOpF(BEq(), IdentifierF(_, _), IfF(_, _, _, _), _)               => None
      case BinaryOpF(BEq(), IdentifierF(_, _), LetF(_, _, _, _), _)              => None
      case BinaryOpF(BEq(), IdentifierF(_, _), LambdaF(_, _, _), _)              => None
      case BinaryOpF(BEq(), IdentifierF(_, _), ConstructorF(_, _, _), _)         => None
      case BinaryOpF(BEq(), IdentifierF(_, _), SetLiteralF(_, _), _)             => None
      case BinaryOpF(BEq(), IdentifierF(_, _), MapLiteralF(_, _), _)             => None
      case BinaryOpF(BEq(), IdentifierF(_, _), SetComprehensionF(_, _, _, _), _) => None
      case BinaryOpF(BEq(), IdentifierF(_, _), SeqLiteralF(_, _), _)             => None
      case BinaryOpF(BEq(), IdentifierF(_, _), MatchesF(_, _, _), _)             => None
      case BinaryOpF(BEq(), IdentifierF(_, _), IntLitF(_, _), _)                 => None
      case BinaryOpF(BEq(), IdentifierF(_, _), FloatLitF(_, _), _)               => None
      case BinaryOpF(BEq(), IdentifierF(_, _), StringLitF(_, _), _)              => None
      case BinaryOpF(BEq(), IdentifierF(_, _), BoolLitF(_, _), _)                => None
      case BinaryOpF(BEq(), IdentifierF(p, _), NoneLitF(_), _)                   => Some[String](p)
      case BinaryOpF(BEq(), IdentifierF(_, _), IdentifierF(_, _), _)             => None
      case BinaryOpF(BNeq(), _, _, _)                                            => None
      case BinaryOpF(BLt(), _, _, _)                                             => None
      case BinaryOpF(BGt(), _, _, _)                                             => None
      case BinaryOpF(BLe(), _, _, _)                                             => None
      case BinaryOpF(BGe(), _, _, _)                                             => None
      case BinaryOpF(BIn(), _, _, _)                                             => None
      case BinaryOpF(BNotIn(), _, _, _)                                          => None
      case BinaryOpF(BSubset(), _, _, _)                                         => None
      case BinaryOpF(BUnion(), _, _, _)                                          => None
      case BinaryOpF(BIntersect(), _, _, _)                                      => None
      case BinaryOpF(BDiff(), _, _, _)                                           => None
      case BinaryOpF(BAdd(), _, _, _)                                            => None
      case BinaryOpF(BSub(), _, _, _)                                            => None
      case BinaryOpF(BMul(), _, _, _)                                            => None
      case BinaryOpF(BDiv(), _, _, _)                                            => None
      case UnaryOpF(_, _, _)                                                     => None
      case QuantifierF(_, _, _, _)                                               => None
      case SomeWrapF(_, _)                                                       => None
      case TheF(_, _, _, _)                                                      => None
      case FieldAccessF(_, _, _)                                                 => None
      case EnumAccessF(_, _, _)                                                  => None
      case IndexF(_, _, _)                                                       => None
      case CallF(_, _, _)                                                        => None
      case PrimeF(_, _)                                                          => None
      case PreF(_, _)                                                            => None
      case WithF(_, _, _)                                                        => None
      case IfF(_, _, _, _)                                                       => None
      case LetF(_, _, _, _)                                                      => None
      case LambdaF(_, _, _)                                                      => None
      case ConstructorF(_, _, _)                                                 => None
      case SetLiteralF(_, _)                                                     => None
      case MapLiteralF(_, _)                                                     => None
      case SetComprehensionF(_, _, _, _)                                         => None
      case SeqLiteralF(_, _)                                                     => None
      case MatchesF(_, _, _)                                                     => None
      case IntLitF(_, _)                                                         => None
      case FloatLitF(_, _)                                                       => None
      case StringLitF(_, _)                                                      => None
      case BoolLitF(_, _)                                                        => None
      case NoneLitF(_)                                                           => None
      case IdentifierF(_, _)                                                     => None
    }

  def desugarBindings(
      fuel: nat,
      uv: List[String],
      bs: List[quantifier_binding]
  ): List[quantifier_binding] =
    equal_nat(fuel, zero_nat) match {
      case true => bs
      case false => bs match {
          case Nil => Nil
          case QuantifierBindingFull(a, d, kind, bsp) :: rest =>
            QuantifierBindingFull(a, desugarGo(minus_nat(fuel, one_nat), uv, d), kind, bsp) ::
              desugarBindings(minus_nat(fuel, one_nat), uv, rest)
        }
    }

  def desugarGo(fuel: nat, uu: List[String], e: expr): expr =
    equal_nat(fuel, zero_nat) match {
      case true => e
      case false => e match {
          case BinaryOpF(op, l, r, sp) =>
            equal_bin_op(op, BImplies()) match {
              case true => neqNoneName(l) match {
                  case None =>
                    BinaryOpF(
                      op,
                      desugarGo(minus_nat(fuel, one_nat), uu, l),
                      desugarGo(minus_nat(fuel, one_nat), uu, r),
                      sp
                    )
                  case Some(p) =>
                    string_in_list(p, uu) match {
                      case true => BinaryOpF(
                          BImplies(),
                          l,
                          desugarGo(minus_nat(fuel, one_nat), uu, substValue(p, r)),
                          sp
                        )
                      case false => BinaryOpF(
                          op,
                          desugarGo(minus_nat(fuel, one_nat), uu, l),
                          desugarGo(minus_nat(fuel, one_nat), uu, r),
                          sp
                        )
                    }
                }
              case false => equal_bin_op(op, BOr()) match {
                  case true => eqNoneName(l) match {
                      case None =>
                        eqNoneName(r) match {
                          case None =>
                            BinaryOpF(
                              op,
                              desugarGo(minus_nat(fuel, one_nat), uu, l),
                              desugarGo(minus_nat(fuel, one_nat), uu, r),
                              sp
                            )
                          case Some(q) =>
                            string_in_list(q, uu) match {
                              case true => BinaryOpF(
                                  BOr(),
                                  desugarGo(minus_nat(fuel, one_nat), uu, substValue(q, l)),
                                  r,
                                  sp
                                )
                              case false => BinaryOpF(
                                  op,
                                  desugarGo(minus_nat(fuel, one_nat), uu, l),
                                  desugarGo(minus_nat(fuel, one_nat), uu, r),
                                  sp
                                )
                            }
                        }
                      case Some(p) =>
                        string_in_list(p, uu) match {
                          case true => BinaryOpF(
                              BOr(),
                              l,
                              desugarGo(minus_nat(fuel, one_nat), uu, substValue(p, r)),
                              sp
                            )
                          case false => eqNoneName(r) match {
                              case None =>
                                BinaryOpF(
                                  op,
                                  desugarGo(minus_nat(fuel, one_nat), uu, l),
                                  desugarGo(minus_nat(fuel, one_nat), uu, r),
                                  sp
                                )
                              case Some(q) =>
                                string_in_list(q, uu) match {
                                  case true => BinaryOpF(
                                      BOr(),
                                      desugarGo(minus_nat(fuel, one_nat), uu, substValue(q, l)),
                                      r,
                                      sp
                                    )
                                  case false => BinaryOpF(
                                      op,
                                      desugarGo(minus_nat(fuel, one_nat), uu, l),
                                      desugarGo(minus_nat(fuel, one_nat), uu, r),
                                      sp
                                    )
                                }
                            }
                        }
                    }
                  case false => BinaryOpF(
                      op,
                      desugarGo(minus_nat(fuel, one_nat), uu, l),
                      desugarGo(minus_nat(fuel, one_nat), uu, r),
                      sp
                    )
                }
            }
          case UnaryOpF(op, x, a) =>
            UnaryOpF(op, desugarGo(minus_nat(fuel, one_nat), uu, x), a)
          case QuantifierF(q, bs, body, a) =>
            QuantifierF(
              q,
              desugarBindings(minus_nat(fuel, one_nat), uu, bs),
              desugarGo(minus_nat(fuel, one_nat), uu, body),
              a
            )
          case SomeWrapF(_, _)               => e
          case TheF(_, _, _, _)              => e
          case FieldAccessF(_, _, _)         => e
          case EnumAccessF(_, _, _)          => e
          case IndexF(_, _, _)               => e
          case CallF(_, _, _)                => e
          case PrimeF(_, _)                  => e
          case PreF(_, _)                    => e
          case WithF(_, _, _)                => e
          case IfF(_, _, _, _)               => e
          case LetF(_, _, _, _)              => e
          case LambdaF(_, _, _)              => e
          case ConstructorF(_, _, _)         => e
          case SetLiteralF(_, _)             => e
          case MapLiteralF(_, _)             => e
          case SetComprehensionF(_, _, _, _) => e
          case SeqLiteralF(_, _)             => e
          case MatchesF(_, _, _)             => e
          case IntLitF(_, _)                 => e
          case FloatLitF(_, _)               => e
          case StringLitF(_, _)              => e
          case BoolLitF(_, _)                => e
          case NoneLitF(_)                   => e
          case IdentifierF(_, _)             => e
        }
    }

  def saTypeExpr(x0: sa_type): String = x0 match {
    case SaType(e, uu) => e
  }

  def sqliteCaps: dialect_caps =
    DialectCaps(true, true, true, false, false, true)

  def entFields(x0: entity_decl): List[field_decl] = x0 match {
    case EntityDeclFull(x1, x2, x3, x4, x5) => x3
  }

  def entParent(x0: entity_decl): Option[String] = x0 match {
    case EntityDeclFull(x1, x2, x3, x4, x5) => x2
  }

  def enmVariants(x0: enum_decl): List[String] = x0 match {
    case EnumDeclFull(x1, x2, x3) => x2
  }

  def fasValue(x0: field_assign): expr = x0 match {
    case FieldAssignFull(x1, x2, x3) => x2
  }

  def fldDefault(x0: field_decl): Option[expr] = x0 match {
    case FieldDeclFull(x1, x2, x3, x4) => x3
  }

  def fncBody(x0: function_decl): expr = x0 match {
    case FunctionDeclFull(x1, x2, x3, x4, x5) => x4
  }

  def fncName(x0: function_decl): String = x0 match {
    case FunctionDeclFull(x1, x2, x3, x4, x5) => x1
  }

  def fncSpan(x0: function_decl): Option[span_t] = x0 match {
    case FunctionDeclFull(x1, x2, x3, x4, x5) => x5
  }

  def svcImports(x0: service_ir): List[String] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x2
  }

  def tmpBody(x0: temporal_decl): temporal_body = x0 match {
    case TemporalDeclFull(x1, x2, x3) => x2
  }

  def tmpName(x0: temporal_decl): String = x0 match {
    case TemporalDeclFull(x1, x2, x3) => x1
  }

  def tmpSpan(x0: temporal_decl): Option[span_t] = x0 match {
    case TemporalDeclFull(x1, x2, x3) => x3
  }

  def trlTo(x0: transition_rule): String = x0 match {
    case TransitionRuleFull(x1, x2, x3, x4, x5) => x2
  }

  def capture_safe(body: expr, params: List[String], args: List[expr]): Boolean =
    distinct[String](params) &&
      (!list_ex[expr]((a: expr) => is_binder(a), allSubexprs(body)) &&
        (!list_ex[expr]((a: expr) => is_call(a), allSubexprs(body)) &&
          (list_all[String]((x: String) => string_in_list(x, params), free_vars(body)) &&
            list_all[expr](
              (a: expr) =>
                list_all[String](
                  (p: String) =>
                    !string_in_list(p, free_vars(a)),
                  params
                ),
              args
            ))))

  def prdParams(x0: predicate_decl): List[param_decl] = x0 match {
    case PredicateDeclFull(x1, x2, x3, x4) => x2
  }

  def fncParams(x0: function_decl): List[param_decl] = x0 match {
    case FunctionDeclFull(x1, x2, x3, x4, x5) => x2
  }

  def prdName(x0: predicate_decl): String = x0 match {
    case PredicateDeclFull(x1, x2, x3, x4) => x1
  }

  def prdBody(x0: predicate_decl): expr = x0 match {
    case PredicateDeclFull(x1, x2, x3, x4) => x3
  }

  def inline_calls_bindings(
      vm: List[function_decl],
      vn: List[predicate_decl],
      x2: List[quantifier_binding]
  ): List[quantifier_binding] =
    (vm, vn, x2) match {
      case (vm, vn, Nil) => Nil
      case (fs, ps, QuantifierBindingFull(n, d, kk, sp) :: rest) =>
        QuantifierBindingFull(n, inline_calls(fs, ps, d), kk, sp) ::
          inline_calls_bindings(fs, ps, rest)
    }

  def inline_calls_entries(
      vk: List[function_decl],
      vl: List[predicate_decl],
      x2: List[map_entry]
  ): List[map_entry] =
    (vk, vl, x2) match {
      case (vk, vl, Nil) => Nil
      case (fs, ps, MapEntryFull(k, v, sp) :: rest) =>
        MapEntryFull(inline_calls(fs, ps, k), inline_calls(fs, ps, v), sp) ::
          inline_calls_entries(fs, ps, rest)
    }

  def inline_calls_fields(
      vi: List[function_decl],
      vj: List[predicate_decl],
      x2: List[field_assign]
  ): List[field_assign] =
    (vi, vj, x2) match {
      case (vi, vj, Nil) => Nil
      case (fs, ps, FieldAssignFull(f, v, sp) :: rest) =>
        FieldAssignFull(f, inline_calls(fs, ps, v), sp) ::
          inline_calls_fields(fs, ps, rest)
    }

  def inline_calls_list(
      vg: List[function_decl],
      vh: List[predicate_decl],
      x2: List[expr]
  ): List[expr] =
    (vg, vh, x2) match {
      case (vg, vh, Nil) => Nil
      case (fs, ps, e :: es) =>
        inline_calls(fs, ps, e) :: inline_calls_list(fs, ps, es)
    }

  def inline_calls(fs: List[function_decl], ps: List[predicate_decl], x2: expr): expr =
    (fs, ps, x2) match {
      case (fs, ps, CallF(callee, args, sp)) =>
        val argsa = inline_calls_list(fs, ps, args): List[expr];
        callee match {
          case BinaryOpF(_, _, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case UnaryOpF(_, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case QuantifierF(_, _, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case SomeWrapF(_, _)  => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case TheF(_, _, _, _) => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case FieldAccessF(_, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case EnumAccessF(_, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case IndexF(_, _, _)  => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case CallF(_, _, _)   => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case PrimeF(_, _)     => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case PreF(_, _)       => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case WithF(_, _, _)   => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case IfF(_, _, _, _)  => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case LetF(_, _, _, _) => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case LambdaF(_, _, _) => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case ConstructorF(_, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case SetLiteralF(_, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case MapLiteralF(_, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case SetComprehensionF(_, _, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case SeqLiteralF(_, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case MatchesF(_, _, _) =>
            CallF(inline_calls(fs, ps, callee), argsa, sp)
          case IntLitF(_, _)    => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case FloatLitF(_, _)  => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case StringLitF(_, _) => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case BoolLitF(_, _)   => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case NoneLitF(_)      => CallF(inline_calls(fs, ps, callee), argsa, sp)
          case IdentifierF(nm, _) =>
            find[function_decl]((f: function_decl) => fncName(f) == nm, fs) match {
              case None =>
                find[predicate_decl](
                  (q: predicate_decl) =>
                    prdName(q) == nm,
                  ps
                ) match {
                  case None => CallF(callee, argsa, sp)
                  case Some(pr) =>
                    equal_nat(size_list[param_decl](prdParams(pr)), size_list[expr](argsa)) &&
                      capture_safe(
                        prdBody(pr),
                        map[param_decl, String]((a: param_decl) => prmName(a), prdParams(pr)),
                        argsa
                      ) match {
                      case true => bind_params(
                          map[param_decl, String]((a: param_decl) => prmName(a), prdParams(pr)),
                          argsa,
                          prdBody(pr)
                        )
                      case false => CallF(callee, argsa, sp)
                    }
                }
              case Some(f) =>
                equal_nat(size_list[param_decl](fncParams(f)), size_list[expr](argsa)) &&
                  capture_safe(
                    fncBody(f),
                    map[param_decl, String]((a: param_decl) => prmName(a), fncParams(f)),
                    argsa
                  ) match {
                  case true => bind_params(
                      map[param_decl, String]((a: param_decl) => prmName(a), fncParams(f)),
                      argsa,
                      fncBody(f)
                    )
                  case false => CallF(callee, argsa, sp)
                }
            }
        }
      case (fs, ps, BinaryOpF(op, l, r, sp)) =>
        BinaryOpF(op, inline_calls(fs, ps, l), inline_calls(fs, ps, r), sp)
      case (fs, ps, UnaryOpF(op, e, sp)) =>
        UnaryOpF(op, inline_calls(fs, ps, e), sp)
      case (fs, ps, FieldAccessF(b, f, sp)) =>
        FieldAccessF(inline_calls(fs, ps, b), f, sp)
      case (fs, ps, EnumAccessF(b, m, sp)) =>
        EnumAccessF(inline_calls(fs, ps, b), m, sp)
      case (fs, ps, IndexF(b, i, sp)) =>
        IndexF(inline_calls(fs, ps, b), inline_calls(fs, ps, i), sp)
      case (fs, ps, PrimeF(e, sp)) => PrimeF(inline_calls(fs, ps, e), sp)
      case (fs, ps, PreF(e, sp))   => PreF(inline_calls(fs, ps, e), sp)
      case (fs, ps, WithF(b, upds, sp)) =>
        WithF(inline_calls(fs, ps, b), inline_calls_fields(fs, ps, upds), sp)
      case (fs, ps, IfF(c, t, e, sp)) =>
        IfF(inline_calls(fs, ps, c), inline_calls(fs, ps, t), inline_calls(fs, ps, e), sp)
      case (fs, ps, LetF(v, vala, body, sp)) =>
        LetF(v, inline_calls(fs, ps, vala), inline_calls(fs, ps, body), sp)
      case (fs, ps, LambdaF(p, b, sp)) => LambdaF(p, inline_calls(fs, ps, b), sp)
      case (fs, ps, ConstructorF(n, flds, sp)) =>
        ConstructorF(n, inline_calls_fields(fs, ps, flds), sp)
      case (fs, ps, SetLiteralF(xs, sp)) =>
        SetLiteralF(inline_calls_list(fs, ps, xs), sp)
      case (fs, ps, MapLiteralF(es, sp)) =>
        MapLiteralF(inline_calls_entries(fs, ps, es), sp)
      case (fs, ps, SetComprehensionF(v, d, p, sp)) =>
        SetComprehensionF(v, inline_calls(fs, ps, d), inline_calls(fs, ps, p), sp)
      case (fs, ps, SeqLiteralF(xs, sp)) =>
        SeqLiteralF(inline_calls_list(fs, ps, xs), sp)
      case (fs, ps, MatchesF(e, pat, sp)) =>
        MatchesF(inline_calls(fs, ps, e), pat, sp)
      case (fs, ps, SomeWrapF(e, sp)) => SomeWrapF(inline_calls(fs, ps, e), sp)
      case (fs, ps, TheF(v, d, b, sp)) =>
        TheF(v, inline_calls(fs, ps, d), inline_calls(fs, ps, b), sp)
      case (fs, ps, QuantifierF(q, bs, body, sp)) =>
        QuantifierF(q, inline_calls_bindings(fs, ps, bs), inline_calls(fs, ps, body), sp)
      case (uu, uv, IntLitF(n, sp))     => IntLitF(n, sp)
      case (uw, ux, FloatLitF(n, sp))   => FloatLitF(n, sp)
      case (uy, uz, StringLitF(n, sp))  => StringLitF(n, sp)
      case (va, vb, BoolLitF(v, sp))    => BoolLitF(v, sp)
      case (vc, vd, NoneLitF(sp))       => NoneLitF(sp)
      case (ve, vf, IdentifierF(n, sp)) => IdentifierF(n, sp)
    }

  def isEntityType(x0: type_expr, name: String): Boolean = (x0, name) match {
    case (NamedTypeF(n, uu), name)          => n == name
    case (SetTypeF(v, va), uw)              => false
    case (MapTypeF(v, va, vb), uw)          => false
    case (SeqTypeF(v, va), uw)              => false
    case (OptionTypeF(v, va), uw)           => false
    case (RelationTypeF(v, va, vb, vc), uw) => false
  }

  def isLogicalBin(x0: bin_op): Boolean = x0 match {
    case BAnd()       => true
    case BOr()        => true
    case BImplies()   => true
    case BIff()       => true
    case BEq()        => false
    case BNeq()       => false
    case BLt()        => false
    case BGt()        => false
    case BLe()        => false
    case BGe()        => false
    case BIn()        => false
    case BNotIn()     => false
    case BSubset()    => false
    case BUnion()     => false
    case BIntersect() => false
    case BDiff()      => false
    case BAdd()       => false
    case BSub()       => false
    case BMul()       => false
    case BDiv()       => false
  }

  def fieldTypeFull(x0: field_decl): type_expr = x0 match {
    case FieldDeclFull(uu, t, uv, uw) => t
  }

  def irStateFields(ir: service_ir): List[state_field_decl] =
    svcState(ir) match {
      case None    => Nil
      case Some(a) => stdFields(a)
    }

  def paramTypeFull(x0: param_decl): type_expr = x0 match {
    case ParamDeclFull(uu, t, uv) => t
  }

  def typeAliasName(x0: type_alias_decl): String = x0 match {
    case TypeAliasDeclFull(n, uu, uv, uw) => n
  }

  def typeAliasType(x0: type_alias_decl): type_expr = x0 match {
    case TypeAliasDeclFull(uu, t, uv, uw) => t
  }

  def pathWithIdSuffix(segment: String, idParamOpt: Option[String]): String =
    idParamOpt match {
      case None       => "/" + segment
      case Some(idNm) => "/" + segment + "/{" + idNm + "}"
    }

  def decideNullable(refOpt: Option[String], typeOpt: Option[List[String]]): nullable_decision =
    refOpt match {
      case None =>
        typeOpt match {
          case None => NdWrapAnyOfNull()
          case Some(currentTypes) =>
            membera[String](currentTypes, "null") match {
              case true  => NdNoop()
              case false => NdAppendNull()
            }
        }
      case Some(_) => NdWrapAnyOfNull()
    }

  def nullSchema: schema_object =
    SchemaObject(
      Some[List[String]](List("null")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      false
    )

  def makeNullableLifted(x0: schema_object): schema_object = x0 match {
    case SchemaObject(
          ty,
          fmt,
          minL,
          maxL,
          mn,
          mx,
          emn,
          emx,
          mnI,
          mxI,
          pat,
          en,
          it,
          rf,
          rq,
          pr,
          ap,
          aof,
          desc,
          inE
        ) => decideNullable(rf, ty) match {
        case NdNoop() =>
          SchemaObject(
            ty,
            fmt,
            minL,
            maxL,
            mn,
            mx,
            emn,
            emx,
            mnI,
            mxI,
            pat,
            en,
            it,
            rf,
            rq,
            pr,
            ap,
            aof,
            desc,
            inE
          )
        case NdWrapAnyOfNull() =>
          SchemaObject(
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            None,
            Some[List[schema_object]](List(
              SchemaObject(
                ty,
                fmt,
                minL,
                maxL,
                mn,
                mx,
                emn,
                emx,
                mnI,
                mxI,
                pat,
                en,
                it,
                rf,
                rq,
                pr,
                ap,
                aof,
                desc,
                inE
              ),
              nullSchema
            )),
            None,
            false
          )
        case NdAppendNull() =>
          ty match {
            case None =>
              SchemaObject(
                ty,
                fmt,
                minL,
                maxL,
                mn,
                mx,
                emn,
                emx,
                mnI,
                mxI,
                pat,
                en,
                it,
                rf,
                rq,
                pr,
                ap,
                aof,
                desc,
                inE
              )
            case Some(currentTypes) =>
              SchemaObject(
                Some[List[String]](currentTypes ++ List("null")),
                fmt,
                minL,
                maxL,
                mn,
                mx,
                emn,
                emx,
                mnI,
                mxI,
                pat,
                en,
                it,
                rf,
                rq,
                pr,
                ap,
                aof,
                desc,
                inE
              )
          }
      }
  }

  def fieldPropertySchema(s: schema_object, nullable: Boolean): schema_object =
    nullable match {
      case true  => makeNullableLifted(s)
      case false => s
    }

  def fieldProps(x0: List[(String, (schema_object, Boolean))]): List[(String, schema_object)] =
    x0 match {
      case Nil => Nil
      case (n, (s, nu)) :: rest =>
        (n, fieldPropertySchema(s, nu)) :: fieldProps(rest)
    }

  def textSchema: schema_object =
    SchemaObject(
      Some[List[String]](List("string")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      false
    )

  def isFailLoudStub(hasDafnyMethod: Boolean, effectiveKind: route_kind): Boolean =
    !hasDafnyMethod && isStubShape(effectiveKind)

  def indexFilterClause(x0: index_spec): Option[String] = x0 match {
    case IndexSpec(uu, uv, uw, f) => f
  }

  def tc_enums[A](x0: tyctx_ext[A]): List[String] = x0 match {
    case tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more) => tc_enums
  }

  def classifyIdent(x0: ident_ctx, name: String): ident_class = (x0, name) match {
    case (
          IdentCtx(
            reserved,
            bound,
            bareBody,
            outputs,
            inputs,
            stateFields,
            unbackedState,
            enumTypes,
            enumValues
          ),
          name
        ) => string_in_list(name, reserved) &&
        (string_in_list(name, bound) || string_in_list(name, inputs)) match {
        case true => IcReserved()
        case false => string_in_list(name, bound) match {
            case true => IcBound()
            case false => equal_option[String](bareBody, Some[String](name)) match {
                case true => IcBareBody()
                case false => string_in_list(name, outputs) match {
                    case true => IcOutput()
                    case false => string_in_list(name, inputs) match {
                        case true => IcInput()
                        case false => string_in_list(name, stateFields) match {
                            case true => string_in_list(name, unbackedState) match {
                                case true  => IcUnbackedState()
                                case false => IcStateField()
                              }
                            case false => string_in_list(name, enumTypes) match {
                                case true => IcEnumType()
                                case false => string_in_list(name, enumValues) match {
                                    case true  => IcEnumValue()
                                    case false => IcUnbound()
                                  }
                              }
                          }
                      }
                  }
              }
          }
      }
  }

  def lookupAliasTarget(x0: List[type_alias_decl], uu: String): Option[type_expr] =
    (x0, uu) match {
      case (Nil, uu) => None
      case (TypeAliasDeclFull(nm, tgt, uv, uw) :: rest, n) =>
        nm == n match {
          case true  => Some[type_expr](tgt)
          case false => lookupAliasTarget(rest, n)
        }
    }

  def isNumericTypeAux(fuel: nat, uu: List[type_alias_decl], uv: type_expr): Boolean =
    equal_nat(fuel, zero_nat) match {
      case true => false
      case false => uv match {
          case NamedTypeF(n, _) =>
            n == "Int" ||
              (n == "Long" ||
                (n == "Float" || n == "Double")) match {
              case true => true
              case false => lookupAliasTarget(uu, n) match {
                  case None    => false
                  case Some(a) => isNumericTypeAux(minus_nat(fuel, one_nat), uu, a)
                }
            }
          case SetTypeF(_, _)    => false
          case MapTypeF(_, _, _) => false
          case SeqTypeF(_, _)    => false
          case OptionTypeF(inner, _) =>
            isNumericTypeAux(minus_nat(fuel, one_nat), uu, inner)
          case RelationTypeF(_, _, _, _) => false
        }
    }

  def isNumericType(aliases: List[type_alias_decl], t: type_expr): Boolean =
    isNumericTypeAux(typeWalkFuel(aliases), aliases, t)

  def alloyUnopShape(x0: un_op): alloy_unop_shape = x0 match {
    case UNot()         => AusNot()
    case UCardinality() => AusCardinality()
    case UNegate()      => AusMinusZero()
    case UPower()       => AusUnsupported()
  }

  def enumNameInList(x0: List[enum_decl], uu: String): Option[String] =
    (x0, uu) match {
      case (Nil, uu) => None
      case (EnumDeclFull(en, uv, uw) :: es, n) =>
        en == n match {
          case true  => Some[String](en)
          case false => enumNameInList(es, n)
        }
    }

  def equal_scalar_rhs(x0: scalar_rhs, x1: scalar_rhs): Boolean = (x0, x1) match {
    case (SrSub(x41, x42), SrMul(x51, x52)) => false
    case (SrMul(x51, x52), SrSub(x41, x42)) => false
    case (SrAdd(x31, x32), SrMul(x51, x52)) => false
    case (SrMul(x51, x52), SrAdd(x31, x32)) => false
    case (SrAdd(x31, x32), SrSub(x41, x42)) => false
    case (SrSub(x41, x42), SrAdd(x31, x32)) => false
    case (SrSelf(), SrMul(x51, x52))        => false
    case (SrMul(x51, x52), SrSelf())        => false
    case (SrSelf(), SrSub(x41, x42))        => false
    case (SrSub(x41, x42), SrSelf())        => false
    case (SrSelf(), SrAdd(x31, x32))        => false
    case (SrAdd(x31, x32), SrSelf())        => false
    case (SrLit(x1), SrMul(x51, x52))       => false
    case (SrMul(x51, x52), SrLit(x1))       => false
    case (SrLit(x1), SrSub(x41, x42))       => false
    case (SrSub(x41, x42), SrLit(x1))       => false
    case (SrLit(x1), SrAdd(x31, x32))       => false
    case (SrAdd(x31, x32), SrLit(x1))       => false
    case (SrLit(x1), SrSelf())              => false
    case (SrSelf(), SrLit(x1))              => false
    case (SrMul(x51, x52), SrMul(y51, y52)) =>
      equal_scalar_rhs(x51, y51) && equal_scalar_rhs(x52, y52)
    case (SrSub(x41, x42), SrSub(y41, y42)) =>
      equal_scalar_rhs(x41, y41) && equal_scalar_rhs(x42, y42)
    case (SrAdd(x31, x32), SrAdd(y31, y32)) =>
      equal_scalar_rhs(x31, y31) && equal_scalar_rhs(x32, y32)
    case (SrLit(x1), SrLit(y1)) => equal_int(x1, y1)
    case (SrSelf(), SrSelf())   => true
  }

  def scalarUpdatesConsistent(x0: List[(String, scalar_rhs)]): Boolean = x0 match {
    case Nil => true
    case (n, r) :: rest =>
      list_all[(String, scalar_rhs)](
        (a: (String, scalar_rhs)) => {
          val (m, s) = a: ((String, scalar_rhs));
          !(m == n) || equal_scalar_rhs(s, r)
        },
        rest
      ) &&
      scalarUpdatesConsistent(rest)
  }

  def isScalarUpdateClause(scalars: List[String], c: expr): Boolean =
    !is_none[(String, scalar_rhs)](scalarUpdateOf(scalars, c))

  def mapEntryIsLeafLeaf(x0: map_entry): Boolean = x0 match {
    case MapEntryFull(k, v, uu) => isLeafValue(k) && isLeafValue(v)
  }

  def innerIsTargetCard(x0: expr, n: String): Boolean = (x0, n) match {
    case (PreF(e, uu), n) => e match {
        case BinaryOpF(_, _, _, _)         => false
        case UnaryOpF(_, _, _)             => false
        case QuantifierF(_, _, _, _)       => false
        case SomeWrapF(_, _)               => false
        case TheF(_, _, _, _)              => false
        case FieldAccessF(_, _, _)         => false
        case EnumAccessF(_, _, _)          => false
        case IndexF(_, _, _)               => false
        case CallF(_, _, _)                => false
        case PrimeF(_, _)                  => false
        case PreF(_, _)                    => false
        case WithF(_, _, _)                => false
        case IfF(_, _, _, _)               => false
        case LetF(_, _, _, _)              => false
        case LambdaF(_, _, _)              => false
        case ConstructorF(_, _, _)         => false
        case SetLiteralF(_, _)             => false
        case MapLiteralF(_, _)             => false
        case SetComprehensionF(_, _, _, _) => false
        case SeqLiteralF(_, _)             => false
        case MatchesF(_, _, _)             => false
        case IntLitF(_, _)                 => false
        case FloatLitF(_, _)               => false
        case StringLitF(_, _)              => false
        case BoolLitF(_, _)                => false
        case NoneLitF(_)                   => false
        case IdentifierF(m, _)             => m == n
      }
    case (IdentifierF(m, uv), n)                => m == n
    case (BinaryOpF(v, va, vb, vc), ux)         => false
    case (UnaryOpF(v, va, vb), ux)              => false
    case (QuantifierF(v, va, vb, vc), ux)       => false
    case (SomeWrapF(v, va), ux)                 => false
    case (TheF(v, va, vb, vc), ux)              => false
    case (FieldAccessF(v, va, vb), ux)          => false
    case (EnumAccessF(v, va, vb), ux)           => false
    case (IndexF(v, va, vb), ux)                => false
    case (CallF(v, va, vb), ux)                 => false
    case (PrimeF(v, va), ux)                    => false
    case (WithF(v, va, vb), ux)                 => false
    case (IfF(v, va, vb, vc), ux)               => false
    case (LetF(v, va, vb, vc), ux)              => false
    case (LambdaF(v, va, vb), ux)               => false
    case (ConstructorF(v, va, vb), ux)          => false
    case (SetLiteralF(v, va), ux)               => false
    case (MapLiteralF(v, va), ux)               => false
    case (SetComprehensionF(v, va, vb, vc), ux) => false
    case (SeqLiteralF(v, va), ux)               => false
    case (MatchesF(v, va, vb), ux)              => false
    case (IntLitF(v, va), ux)                   => false
    case (FloatLitF(v, va), ux)                 => false
    case (StringLitF(v, va), ux)                => false
    case (BoolLitF(v, va), ux)                  => false
    case (NoneLitF(v), ux)                      => false
  }

  def isCardinalityRhs(x0: expr, n: String): Boolean = (x0, n) match {
    case (UnaryOpF(op, inner, uu), n) =>
      (op match {
        case UNot()         => false
        case UNegate()      => false
        case UCardinality() => true
        case UPower()       => false
      }) &&
      innerIsTargetCard(inner, n)
    case (BinaryOpF(op, inner, rhs, uv), n) =>
      (op match {
        case BAnd()       => false
        case BOr()        => false
        case BImplies()   => false
        case BIff()       => false
        case BEq()        => false
        case BNeq()       => false
        case BLt()        => false
        case BGt()        => false
        case BLe()        => false
        case BGe()        => false
        case BIn()        => false
        case BNotIn()     => false
        case BSubset()    => false
        case BUnion()     => false
        case BIntersect() => false
        case BDiff()      => false
        case BAdd()       => true
        case BSub()       => true
        case BMul()       => false
        case BDiv()       => false
      }) &&
      (isIntLit(rhs) && isCardinalityRhs(inner, n))
    case (QuantifierF(v, va, vb, vc), ux)       => false
    case (SomeWrapF(v, va), ux)                 => false
    case (TheF(v, va, vb, vc), ux)              => false
    case (FieldAccessF(v, va, vb), ux)          => false
    case (EnumAccessF(v, va, vb), ux)           => false
    case (IndexF(v, va, vb), ux)                => false
    case (CallF(v, va, vb), ux)                 => false
    case (PrimeF(v, va), ux)                    => false
    case (PreF(v, va), ux)                      => false
    case (WithF(v, va, vb), ux)                 => false
    case (IfF(v, va, vb, vc), ux)               => false
    case (LetF(v, va, vb, vc), ux)              => false
    case (LambdaF(v, va, vb), ux)               => false
    case (ConstructorF(v, va, vb), ux)          => false
    case (SetLiteralF(v, va), ux)               => false
    case (MapLiteralF(v, va), ux)               => false
    case (SetComprehensionF(v, va, vb, vc), ux) => false
    case (SeqLiteralF(v, va), ux)               => false
    case (MatchesF(v, va, vb), ux)              => false
    case (IntLitF(v, va), ux)                   => false
    case (FloatLitF(v, va), ux)                 => false
    case (StringLitF(v, va), ux)                => false
    case (BoolLitF(v, va), ux)                  => false
    case (NoneLitF(v), ux)                      => false
    case (IdentifierF(v, va), ux)               => false
  }

  def isDirectEmitShape(
      clause: expr,
      stateFieldNames: List[String],
      outputNames: List[String]
  ): Boolean =
    clause match {
      case BinaryOpF(BAnd(), _, _, _)                                                   => false
      case BinaryOpF(BOr(), _, _, _)                                                    => false
      case BinaryOpF(BImplies(), _, _, _)                                               => false
      case BinaryOpF(BIff(), _, _, _)                                                   => false
      case BinaryOpF(BEq(), BinaryOpF(_, _, _, _), _, _)                                => false
      case BinaryOpF(BEq(), UnaryOpF(UNot(), _, _), _, _)                               => false
      case BinaryOpF(BEq(), UnaryOpF(UNegate(), _, _), _, _)                            => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), BinaryOpF(_, _, _, _), _), _, _)   => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), UnaryOpF(_, _, _), _), _, _)       => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), QuantifierF(_, _, _, _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), SomeWrapF(_, _), _), _, _)         => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), TheF(_, _, _, _), _), _, _)        => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), FieldAccessF(_, _, _), _), _, _)   => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), EnumAccessF(_, _, _), _), _, _)    => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), IndexF(_, _, _), _), _, _)         => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), CallF(_, _, _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(BinaryOpF(_, _, _, _), _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(UnaryOpF(_, _, _), _), _), _, _) =>
        false
      case BinaryOpF(
            BEq(),
            UnaryOpF(UCardinality(), PrimeF(QuantifierF(_, _, _, _), _), _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(SomeWrapF(_, _), _), _), _, _)  => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(TheF(_, _, _, _), _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(FieldAccessF(_, _, _), _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(EnumAccessF(_, _, _), _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(IndexF(_, _, _), _), _), _, _)  => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(CallF(_, _, _), _), _), _, _)   => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(PrimeF(_, _), _), _), _, _)     => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(PreF(_, _), _), _), _, _)       => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(WithF(_, _, _), _), _), _, _)   => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(IfF(_, _, _, _), _), _), _, _)  => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(LetF(_, _, _, _), _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(LambdaF(_, _, _), _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(ConstructorF(_, _, _), _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(SetLiteralF(_, _), _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(MapLiteralF(_, _), _), _), _, _) =>
        false
      case BinaryOpF(
            BEq(),
            UnaryOpF(UCardinality(), PrimeF(SetComprehensionF(_, _, _, _), _), _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(SeqLiteralF(_, _), _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(MatchesF(_, _, _), _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(IntLitF(_, _), _), _), _, _)    => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(FloatLitF(_, _), _), _), _, _)  => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(StringLitF(_, _), _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(BoolLitF(_, _), _), _), _, _)   => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(NoneLitF(_), _), _), _, _)      => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PrimeF(IdentifierF(n, _), _), _), rhs, _) =>
        membera[String](stateFieldNames, n) && isCardinalityRhs(rhs, n)
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), PreF(_, _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), WithF(_, _, _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), IfF(_, _, _, _), _), _, _)       => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), LetF(_, _, _, _), _), _, _)      => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), LambdaF(_, _, _), _), _, _)      => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), ConstructorF(_, _, _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), SetLiteralF(_, _), _), _, _)     => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), MapLiteralF(_, _), _), _, _)     => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), SetComprehensionF(_, _, _, _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), SeqLiteralF(_, _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), MatchesF(_, _, _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), IntLitF(_, _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), FloatLitF(_, _), _), _, _)  => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), StringLitF(_, _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), BoolLitF(_, _), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), NoneLitF(_), _), _, _) =>
        false
      case BinaryOpF(BEq(), UnaryOpF(UCardinality(), IdentifierF(_, _), _), _, _) => false
      case BinaryOpF(BEq(), UnaryOpF(UPower(), _, _), _, _)                       => false
      case BinaryOpF(BEq(), QuantifierF(_, _, _, _), _, _)                        => false
      case BinaryOpF(BEq(), SomeWrapF(_, _), _, _)                                => false
      case BinaryOpF(BEq(), TheF(_, _, _, _), _, _)                               => false
      case BinaryOpF(BEq(), FieldAccessF(BinaryOpF(_, _, _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(UnaryOpF(_, _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(QuantifierF(_, _, _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(SomeWrapF(_, _), _, _), _, _)  => false
      case BinaryOpF(BEq(), FieldAccessF(TheF(_, _, _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(FieldAccessF(_, _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(EnumAccessF(_, _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(BinaryOpF(_, _, _, _), _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(UnaryOpF(_, _, _), _, _), _, _), _, _)     => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(QuantifierF(_, _, _, _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(SomeWrapF(_, _), _, _), _, _), _, _)       => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(TheF(_, _, _, _), _, _), _, _), _, _)      => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(FieldAccessF(_, _, _), _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(EnumAccessF(_, _, _), _, _), _, _), _, _)  => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(IndexF(_, _, _), _, _), _, _), _, _)       => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(CallF(_, _, _), _, _), _, _), _, _)        => false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(PrimeF(BinaryOpF(_, _, _, _), _), _, _), _, _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(UnaryOpF(_, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(PrimeF(QuantifierF(_, _, _, _), _), _, _), _, _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(SomeWrapF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(TheF(_, _, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(PrimeF(FieldAccessF(_, _, _), _), _, _), _, _),
            _,
            _
          ) => false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(PrimeF(EnumAccessF(_, _, _), _), _, _), _, _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(IndexF(_, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(CallF(_, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(PrimeF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(PreF(_, _), _), _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(WithF(_, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(IfF(_, _, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(LetF(_, _, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(LambdaF(_, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(PrimeF(ConstructorF(_, _, _), _), _, _), _, _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(SetLiteralF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(MapLiteralF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(PrimeF(SetComprehensionF(_, _, _, _), _), _, _), _, _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(SeqLiteralF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(MatchesF(_, _, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(IntLitF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(FloatLitF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(StringLitF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(BoolLitF(_, _), _), _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PrimeF(NoneLitF(_), _), _, _), _, _), _, _) => false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(PrimeF(IdentifierF(n, _), _), idx, _), _, _),
            rhs,
            _
          ) => membera[String](stateFieldNames, n) &&
        (isLeafValue(idx) && isLeafValue(rhs))
      case BinaryOpF(BEq(), FieldAccessF(IndexF(PreF(_, _), _, _), _, _), _, _)            => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(WithF(_, _, _), _, _), _, _), _, _)        => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(IfF(_, _, _, _), _, _), _, _), _, _)       => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(LetF(_, _, _, _), _, _), _, _), _, _)      => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(LambdaF(_, _, _), _, _), _, _), _, _)      => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(ConstructorF(_, _, _), _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(SetLiteralF(_, _), _, _), _, _), _, _)     => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(MapLiteralF(_, _), _, _), _, _), _, _)     => false
      case BinaryOpF(
            BEq(),
            FieldAccessF(IndexF(SetComprehensionF(_, _, _, _), _, _), _, _),
            _,
            _
          ) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(SeqLiteralF(_, _), _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(MatchesF(_, _, _), _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(IntLitF(_, _), _, _), _, _), _, _)     => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(FloatLitF(_, _), _, _), _, _), _, _)   => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(StringLitF(_, _), _, _), _, _), _, _)  => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(BoolLitF(_, _), _, _), _, _), _, _)    => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(NoneLitF(_), _, _), _, _), _, _)       => false
      case BinaryOpF(BEq(), FieldAccessF(IndexF(IdentifierF(_, _), _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(CallF(_, _, _), _, _), _, _)                  => false
      case BinaryOpF(BEq(), FieldAccessF(PrimeF(_, _), _, _), _, _)                    => false
      case BinaryOpF(BEq(), FieldAccessF(PreF(_, _), _, _), _, _)                      => false
      case BinaryOpF(BEq(), FieldAccessF(WithF(_, _, _), _, _), _, _)                  => false
      case BinaryOpF(BEq(), FieldAccessF(IfF(_, _, _, _), _, _), _, _)                 => false
      case BinaryOpF(BEq(), FieldAccessF(LetF(_, _, _, _), _, _), _, _)                => false
      case BinaryOpF(BEq(), FieldAccessF(LambdaF(_, _, _), _, _), _, _)                => false
      case BinaryOpF(BEq(), FieldAccessF(ConstructorF(_, _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), FieldAccessF(SetLiteralF(_, _), _, _), _, _)             => false
      case BinaryOpF(BEq(), FieldAccessF(MapLiteralF(_, _), _, _), _, _)             => false
      case BinaryOpF(BEq(), FieldAccessF(SetComprehensionF(_, _, _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), FieldAccessF(SeqLiteralF(_, _), _, _), _, _)             => false
      case BinaryOpF(BEq(), FieldAccessF(MatchesF(_, _, _), _, _), _, _)             => false
      case BinaryOpF(BEq(), FieldAccessF(IntLitF(_, _), _, _), _, _)                 => false
      case BinaryOpF(BEq(), FieldAccessF(FloatLitF(_, _), _, _), _, _)               => false
      case BinaryOpF(BEq(), FieldAccessF(StringLitF(_, _), _, _), _, _)              => false
      case BinaryOpF(BEq(), FieldAccessF(BoolLitF(_, _), _, _), _, _)                => false
      case BinaryOpF(BEq(), FieldAccessF(NoneLitF(_), _, _), _, _)                   => false
      case BinaryOpF(BEq(), FieldAccessF(IdentifierF(_, _), _, _), _, _)             => false
      case BinaryOpF(BEq(), EnumAccessF(_, _, _), _, _)                              => false
      case BinaryOpF(BEq(), IndexF(BinaryOpF(_, _, _, _), _, _), _, _)               => false
      case BinaryOpF(BEq(), IndexF(UnaryOpF(_, _, _), _, _), _, _)                   => false
      case BinaryOpF(BEq(), IndexF(QuantifierF(_, _, _, _), _, _), _, _)             => false
      case BinaryOpF(BEq(), IndexF(SomeWrapF(_, _), _, _), _, _)                     => false
      case BinaryOpF(BEq(), IndexF(TheF(_, _, _, _), _, _), _, _)                    => false
      case BinaryOpF(BEq(), IndexF(FieldAccessF(_, _, _), _, _), _, _)               => false
      case BinaryOpF(BEq(), IndexF(EnumAccessF(_, _, _), _, _), _, _)                => false
      case BinaryOpF(BEq(), IndexF(IndexF(_, _, _), _, _), _, _)                     => false
      case BinaryOpF(BEq(), IndexF(CallF(_, _, _), _, _), _, _)                      => false
      case BinaryOpF(BEq(), IndexF(PrimeF(BinaryOpF(_, _, _, _), _), _, _), _, _)    => false
      case BinaryOpF(BEq(), IndexF(PrimeF(UnaryOpF(_, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(QuantifierF(_, _, _, _), _), _, _), _, _) => false
      case BinaryOpF(BEq(), IndexF(PrimeF(SomeWrapF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(TheF(_, _, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(FieldAccessF(_, _, _), _), _, _), _, _) => false
      case BinaryOpF(BEq(), IndexF(PrimeF(EnumAccessF(_, _, _), _), _, _), _, _)  => false
      case BinaryOpF(BEq(), IndexF(PrimeF(IndexF(_, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(CallF(_, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(PrimeF(_, _), _), _, _), _, _) => false
      case BinaryOpF(BEq(), IndexF(PrimeF(PreF(_, _), _), _, _), _, _)   => false
      case BinaryOpF(BEq(), IndexF(PrimeF(WithF(_, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(IfF(_, _, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(LetF(_, _, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(LambdaF(_, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(ConstructorF(_, _, _), _), _, _), _, _) => false
      case BinaryOpF(BEq(), IndexF(PrimeF(SetLiteralF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(MapLiteralF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(SetComprehensionF(_, _, _, _), _), _, _), _, _) => false
      case BinaryOpF(BEq(), IndexF(PrimeF(SeqLiteralF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(MatchesF(_, _, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(IntLitF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(FloatLitF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(StringLitF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(BoolLitF(_, _), _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(PrimeF(NoneLitF(_), _), _, _), _, _) => false
      case BinaryOpF(BEq(), IndexF(PrimeF(IdentifierF(n, _), _), idx, _), rhs, _) =>
        membera[String](stateFieldNames, n) &&
        (isLeafValue(idx) && isLeafValue(rhs))
      case BinaryOpF(BEq(), IndexF(PreF(_, _), _, _), _, _)            => false
      case BinaryOpF(BEq(), IndexF(WithF(_, _, _), _, _), _, _)        => false
      case BinaryOpF(BEq(), IndexF(IfF(_, _, _, _), _, _), _, _)       => false
      case BinaryOpF(BEq(), IndexF(LetF(_, _, _, _), _, _), _, _)      => false
      case BinaryOpF(BEq(), IndexF(LambdaF(_, _, _), _, _), _, _)      => false
      case BinaryOpF(BEq(), IndexF(ConstructorF(_, _, _), _, _), _, _) => false
      case BinaryOpF(BEq(), IndexF(SetLiteralF(_, _), _, _), _, _)     => false
      case BinaryOpF(BEq(), IndexF(MapLiteralF(_, _), _, _), _, _)     => false
      case BinaryOpF(BEq(), IndexF(SetComprehensionF(_, _, _, _), _, _), _, _) =>
        false
      case BinaryOpF(BEq(), IndexF(SeqLiteralF(_, _), _, _), _, _)    => false
      case BinaryOpF(BEq(), IndexF(MatchesF(_, _, _), _, _), _, _)    => false
      case BinaryOpF(BEq(), IndexF(IntLitF(_, _), _, _), _, _)        => false
      case BinaryOpF(BEq(), IndexF(FloatLitF(_, _), _, _), _, _)      => false
      case BinaryOpF(BEq(), IndexF(StringLitF(_, _), _, _), _, _)     => false
      case BinaryOpF(BEq(), IndexF(BoolLitF(_, _), _, _), _, _)       => false
      case BinaryOpF(BEq(), IndexF(NoneLitF(_), _, _), _, _)          => false
      case BinaryOpF(BEq(), IndexF(IdentifierF(_, _), _, _), _, _)    => false
      case BinaryOpF(BEq(), CallF(_, _, _), _, _)                     => false
      case BinaryOpF(BEq(), PrimeF(BinaryOpF(_, _, _, _), _), _, _)   => false
      case BinaryOpF(BEq(), PrimeF(UnaryOpF(_, _, _), _), _, _)       => false
      case BinaryOpF(BEq(), PrimeF(QuantifierF(_, _, _, _), _), _, _) => false
      case BinaryOpF(BEq(), PrimeF(SomeWrapF(_, _), _), _, _)         => false
      case BinaryOpF(BEq(), PrimeF(TheF(_, _, _, _), _), _, _)        => false
      case BinaryOpF(BEq(), PrimeF(FieldAccessF(_, _, _), _), _, _)   => false
      case BinaryOpF(BEq(), PrimeF(EnumAccessF(_, _, _), _), _, _)    => false
      case BinaryOpF(BEq(), PrimeF(IndexF(_, _, _), _), _, _)         => false
      case BinaryOpF(BEq(), PrimeF(CallF(_, _, _), _), _, _)          => false
      case BinaryOpF(BEq(), PrimeF(PrimeF(_, _), _), _, _)            => false
      case BinaryOpF(BEq(), PrimeF(PreF(_, _), _), _, _)              => false
      case BinaryOpF(BEq(), PrimeF(WithF(_, _, _), _), _, _)          => false
      case BinaryOpF(BEq(), PrimeF(IfF(_, _, _, _), _), _, _)         => false
      case BinaryOpF(BEq(), PrimeF(LetF(_, _, _, _), _), _, _)        => false
      case BinaryOpF(BEq(), PrimeF(LambdaF(_, _, _), _), _, _)        => false
      case BinaryOpF(BEq(), PrimeF(ConstructorF(_, _, _), _), _, _)   => false
      case BinaryOpF(BEq(), PrimeF(SetLiteralF(_, _), _), _, _)       => false
      case BinaryOpF(BEq(), PrimeF(MapLiteralF(_, _), _), _, _)       => false
      case BinaryOpF(BEq(), PrimeF(SetComprehensionF(_, _, _, _), _), _, _) =>
        false
      case BinaryOpF(BEq(), PrimeF(SeqLiteralF(_, _), _), _, _)                          => false
      case BinaryOpF(BEq(), PrimeF(MatchesF(_, _, _), _), _, _)                          => false
      case BinaryOpF(BEq(), PrimeF(IntLitF(_, _), _), _, _)                              => false
      case BinaryOpF(BEq(), PrimeF(FloatLitF(_, _), _), _, _)                            => false
      case BinaryOpF(BEq(), PrimeF(StringLitF(_, _), _), _, _)                           => false
      case BinaryOpF(BEq(), PrimeF(BoolLitF(_, _), _), _, _)                             => false
      case BinaryOpF(BEq(), PrimeF(NoneLitF(_), _), _, _)                                => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BAnd(), _, _, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BOr(), _, _, _), _)  => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BImplies(), _, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BIff(), _, _, _), _)    => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BEq(), _, _, _), _)     => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BNeq(), _, _, _), _)    => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BLt(), _, _, _), _)     => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BGt(), _, _, _), _)     => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BLe(), _, _, _), _)     => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BGe(), _, _, _), _)     => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BIn(), _, _, _), _)     => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BNotIn(), _, _, _), _)  => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BSubset(), _, _, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BUnion(), _, _, _), _)  => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BIntersect(), _, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BDiff(), _, _, _), _) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), BinaryOpF(_, _, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), UnaryOpF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), QuantifierF(_, _, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), SomeWrapF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), TheF(_, _, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), FieldAccessF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), EnumAccessF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), IndexF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), CallF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PrimeF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(BinaryOpF(_, _, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(UnaryOpF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(QuantifierF(_, _, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(SomeWrapF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(TheF(_, _, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(FieldAccessF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(EnumAccessF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IndexF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(CallF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(PrimeF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(PreF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(WithF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IfF(_, _, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(LetF(_, _, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(LambdaF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(ConstructorF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(SetLiteralF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(MapLiteralF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(SetComprehensionF(_, _, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(SeqLiteralF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(MatchesF(_, _, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IntLitF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(FloatLitF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(StringLitF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(BoolLitF(_, _), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(NoneLitF(_), _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), BinaryOpF(_, _, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), UnaryOpF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), QuantifierF(_, _, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), SomeWrapF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), TheF(_, _, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), FieldAccessF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), EnumAccessF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), IndexF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), CallF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), PrimeF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), PreF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), WithF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), IfF(_, _, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), LetF(_, _, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), LambdaF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), ConstructorF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), SetLiteralF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(l, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(r, _), _), MapLiteralF(entries, _), _),
            _
          ) => l == r &&
        (membera[String](stateFieldNames, l) &&
          list_all[map_entry]((a: map_entry) => mapEntryIsLeafLeaf(a), entries))
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), SetComprehensionF(_, _, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), SeqLiteralF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), MatchesF(_, _, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), IntLitF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), FloatLitF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), StringLitF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), BoolLitF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), NoneLitF(_), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), PreF(IdentifierF(_, _), _), IdentifierF(_, _), _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), WithF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), IfF(_, _, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), LetF(_, _, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), LambdaF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), ConstructorF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), SetLiteralF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), MapLiteralF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), SetComprehensionF(_, _, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), SeqLiteralF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), MatchesF(_, _, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), IntLitF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), FloatLitF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), StringLitF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), BoolLitF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), NoneLitF(_), _, _),
            _
          ) => false
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(_, _), _),
            BinaryOpF(BAdd(), IdentifierF(_, _), _, _),
            _
          ) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BSub(), _, _, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BMul(), _, _, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(BDiv(), _, _, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), UnaryOpF(_, _, _), _)          => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), QuantifierF(_, _, _, _), _)    => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SomeWrapF(_, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), TheF(_, _, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), FieldAccessF(_, _, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), EnumAccessF(_, _, _), _)  => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), IndexF(_, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), CallF(_, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), PrimeF(_, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), PreF(_, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), WithF(_, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), IfF(_, _, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), LetF(_, _, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), LambdaF(_, _, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), ConstructorF(_, _, _), _)         => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SetLiteralF(_, _), _)             => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), MapLiteralF(_, _), _)             => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SetComprehensionF(_, _, _, _), _) => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SeqLiteralF(_, _), _)             => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), MatchesF(_, _, _), _)             => false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), IntLitF(_, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), FloatLitF(_, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), StringLitF(_, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BoolLitF(_, _), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), NoneLitF(_), _) =>
        false
      case BinaryOpF(BEq(), PrimeF(IdentifierF(l, _), _), IdentifierF(r, _), _) =>
        l == r && membera[String](stateFieldNames, l)
      case BinaryOpF(BEq(), PreF(_, _), _, _)                    => false
      case BinaryOpF(BEq(), WithF(_, _, _), _, _)                => false
      case BinaryOpF(BEq(), IfF(_, _, _, _), _, _)               => false
      case BinaryOpF(BEq(), LetF(_, _, _, _), _, _)              => false
      case BinaryOpF(BEq(), LambdaF(_, _, _), _, _)              => false
      case BinaryOpF(BEq(), ConstructorF(_, _, _), _, _)         => false
      case BinaryOpF(BEq(), SetLiteralF(_, _), _, _)             => false
      case BinaryOpF(BEq(), MapLiteralF(_, _), _, _)             => false
      case BinaryOpF(BEq(), SetComprehensionF(_, _, _, _), _, _) => false
      case BinaryOpF(BEq(), SeqLiteralF(_, _), _, _)             => false
      case BinaryOpF(BEq(), MatchesF(_, _, _), _, _)             => false
      case BinaryOpF(BEq(), IntLitF(_, _), _, _)                 => false
      case BinaryOpF(BEq(), FloatLitF(_, _), _, _)               => false
      case BinaryOpF(BEq(), StringLitF(_, _), _, _)              => false
      case BinaryOpF(BEq(), BoolLitF(_, _), _, _)                => false
      case BinaryOpF(BEq(), NoneLitF(_), _, _)                   => false
      case BinaryOpF(BEq(), IdentifierF(name, _), rhs, _) =>
        membera[String](outputNames, name) && isPureRead(rhs)
      case BinaryOpF(BNeq(), _, _, _)                                    => false
      case BinaryOpF(BLt(), _, _, _)                                     => false
      case BinaryOpF(BGt(), _, _, _)                                     => false
      case BinaryOpF(BLe(), _, _, _)                                     => false
      case BinaryOpF(BGe(), _, _, _)                                     => false
      case BinaryOpF(BIn(), _, _, _)                                     => false
      case BinaryOpF(BNotIn(), _, BinaryOpF(_, _, _, _), _)              => false
      case BinaryOpF(BNotIn(), _, UnaryOpF(_, _, _), _)                  => false
      case BinaryOpF(BNotIn(), _, QuantifierF(_, _, _, _), _)            => false
      case BinaryOpF(BNotIn(), _, SomeWrapF(_, _), _)                    => false
      case BinaryOpF(BNotIn(), _, TheF(_, _, _, _), _)                   => false
      case BinaryOpF(BNotIn(), _, FieldAccessF(_, _, _), _)              => false
      case BinaryOpF(BNotIn(), _, EnumAccessF(_, _, _), _)               => false
      case BinaryOpF(BNotIn(), _, IndexF(_, _, _), _)                    => false
      case BinaryOpF(BNotIn(), _, CallF(_, _, _), _)                     => false
      case BinaryOpF(BNotIn(), _, PrimeF(BinaryOpF(_, _, _, _), _), _)   => false
      case BinaryOpF(BNotIn(), _, PrimeF(UnaryOpF(_, _, _), _), _)       => false
      case BinaryOpF(BNotIn(), _, PrimeF(QuantifierF(_, _, _, _), _), _) => false
      case BinaryOpF(BNotIn(), _, PrimeF(SomeWrapF(_, _), _), _)         => false
      case BinaryOpF(BNotIn(), _, PrimeF(TheF(_, _, _, _), _), _)        => false
      case BinaryOpF(BNotIn(), _, PrimeF(FieldAccessF(_, _, _), _), _)   => false
      case BinaryOpF(BNotIn(), _, PrimeF(EnumAccessF(_, _, _), _), _)    => false
      case BinaryOpF(BNotIn(), _, PrimeF(IndexF(_, _, _), _), _)         => false
      case BinaryOpF(BNotIn(), _, PrimeF(CallF(_, _, _), _), _)          => false
      case BinaryOpF(BNotIn(), _, PrimeF(PrimeF(_, _), _), _)            => false
      case BinaryOpF(BNotIn(), _, PrimeF(PreF(_, _), _), _)              => false
      case BinaryOpF(BNotIn(), _, PrimeF(WithF(_, _, _), _), _)          => false
      case BinaryOpF(BNotIn(), _, PrimeF(IfF(_, _, _, _), _), _)         => false
      case BinaryOpF(BNotIn(), _, PrimeF(LetF(_, _, _, _), _), _)        => false
      case BinaryOpF(BNotIn(), _, PrimeF(LambdaF(_, _, _), _), _)        => false
      case BinaryOpF(BNotIn(), _, PrimeF(ConstructorF(_, _, _), _), _)   => false
      case BinaryOpF(BNotIn(), _, PrimeF(SetLiteralF(_, _), _), _)       => false
      case BinaryOpF(BNotIn(), _, PrimeF(MapLiteralF(_, _), _), _)       => false
      case BinaryOpF(BNotIn(), _, PrimeF(SetComprehensionF(_, _, _, _), _), _) =>
        false
      case BinaryOpF(BNotIn(), _, PrimeF(SeqLiteralF(_, _), _), _) => false
      case BinaryOpF(BNotIn(), _, PrimeF(MatchesF(_, _, _), _), _) => false
      case BinaryOpF(BNotIn(), _, PrimeF(IntLitF(_, _), _), _)     => false
      case BinaryOpF(BNotIn(), _, PrimeF(FloatLitF(_, _), _), _)   => false
      case BinaryOpF(BNotIn(), _, PrimeF(StringLitF(_, _), _), _)  => false
      case BinaryOpF(BNotIn(), _, PrimeF(BoolLitF(_, _), _), _)    => false
      case BinaryOpF(BNotIn(), _, PrimeF(NoneLitF(_), _), _)       => false
      case BinaryOpF(BNotIn(), _, PrimeF(IdentifierF(n, _), _), _) =>
        membera[String](stateFieldNames, n)
      case BinaryOpF(BNotIn(), _, PreF(_, _), _)                    => false
      case BinaryOpF(BNotIn(), _, WithF(_, _, _), _)                => false
      case BinaryOpF(BNotIn(), _, IfF(_, _, _, _), _)               => false
      case BinaryOpF(BNotIn(), _, LetF(_, _, _, _), _)              => false
      case BinaryOpF(BNotIn(), _, LambdaF(_, _, _), _)              => false
      case BinaryOpF(BNotIn(), _, ConstructorF(_, _, _), _)         => false
      case BinaryOpF(BNotIn(), _, SetLiteralF(_, _), _)             => false
      case BinaryOpF(BNotIn(), _, MapLiteralF(_, _), _)             => false
      case BinaryOpF(BNotIn(), _, SetComprehensionF(_, _, _, _), _) => false
      case BinaryOpF(BNotIn(), _, SeqLiteralF(_, _), _)             => false
      case BinaryOpF(BNotIn(), _, MatchesF(_, _, _), _)             => false
      case BinaryOpF(BNotIn(), _, IntLitF(_, _), _)                 => false
      case BinaryOpF(BNotIn(), _, FloatLitF(_, _), _)               => false
      case BinaryOpF(BNotIn(), _, StringLitF(_, _), _)              => false
      case BinaryOpF(BNotIn(), _, BoolLitF(_, _), _)                => false
      case BinaryOpF(BNotIn(), _, NoneLitF(_), _)                   => false
      case BinaryOpF(BNotIn(), _, IdentifierF(_, _), _)             => false
      case BinaryOpF(BSubset(), _, _, _)                            => false
      case BinaryOpF(BUnion(), _, _, _)                             => false
      case BinaryOpF(BIntersect(), _, _, _)                         => false
      case BinaryOpF(BDiff(), _, _, _)                              => false
      case BinaryOpF(BAdd(), _, _, _)                               => false
      case BinaryOpF(BSub(), _, _, _)                               => false
      case BinaryOpF(BMul(), _, _, _)                               => false
      case BinaryOpF(BDiv(), _, _, _)                               => false
      case UnaryOpF(_, _, _)                                        => false
      case QuantifierF(_, _, _, _)                                  => false
      case SomeWrapF(_, _)                                          => false
      case TheF(_, _, _, _)                                         => false
      case FieldAccessF(_, _, _)                                    => false
      case EnumAccessF(_, _, _)                                     => false
      case IndexF(_, _, _)                                          => false
      case CallF(_, _, _)                                           => false
      case PrimeF(_, _)                                             => false
      case PreF(_, _)                                               => false
      case WithF(_, _, _)                                           => false
      case IfF(_, _, _, _)                                          => false
      case LetF(_, _, _, _)                                         => false
      case LambdaF(_, _, _)                                         => false
      case ConstructorF(_, _, _)                                    => false
      case SetLiteralF(_, _)                                        => false
      case MapLiteralF(_, _)                                        => false
      case SetComprehensionF(_, _, _, _)                            => false
      case SeqLiteralF(_, _)                                        => false
      case MatchesF(_, _, _)                                        => false
      case IntLitF(_, _)                                            => false
      case FloatLitF(_, _)                                          => false
      case StringLitF(_, _)                                         => false
      case BoolLitF(_, _)                                           => false
      case NoneLitF(_)                                              => false
      case IdentifierF(_, _)                                        => false
    }

  def classifyStrategy(
      ensures: List[expr],
      reqs: List[expr],
      inputNames: List[String],
      stateFieldNames: List[String],
      scalarFieldNames: List[String],
      outputNames: List[String]
  ): synthesis_strategy = {
    val clauses = flattenEnsures(ensures): List[expr];
    nulla[expr](clauses) match {
      case true => LlmSynthesis()
      case false => list_ex[expr](
          (a: expr) =>
            isScalarUpdateClause(scalarFieldNames, a),
          clauses
        ) match {
          case true => list_all[expr](
              (a: expr) =>
                isScalarUpdateClause(scalarFieldNames, a),
              clauses
            ) &&
              (nulla[String](inputNames) &&
                (scalarUpdatesConsistent(map_filter[expr, (String, scalar_rhs)](
                  (a: expr) =>
                    scalarUpdateOf(scalarFieldNames, a),
                  clauses
                )) &&
                  list_all[expr](
                    (r: expr) =>
                      !is_none[scalar_guard](scalarGuardOf(scalarFieldNames, r)),
                    flattenEnsures(reqs)
                  ))) match {
              case true  => DirectEmit()
              case false => LlmSynthesis()
            }
          case false => list_all[expr](
              (c: expr) =>
                isDirectEmitShape(c, stateFieldNames, outputNames),
              clauses
            ) match {
              case true  => DirectEmit()
              case false => LlmSynthesis()
            }
        }
    }
  }

  def modulo_integer(k: BigInt, l: BigInt): BigInt =
    snd[BigInt, BigInt](divmod_integer(k, l))

  def modulo_nat(m: nat, n: nat): nat =
    Nata(modulo_integer(integer_of_nat(m), integer_of_nat(n)))

  def divide_nat(m: nat, n: nat): nat =
    Nata(divide_integer(integer_of_nat(m), integer_of_nat(n)))

  def less_nat(m: nat, n: nat): Boolean = integer_of_nat(m) < integer_of_nat(n)

  def digitsRev(n: nat): List[nat] =
    less_nat(n, nat_of_integer(BigInt(10))) match {
      case true => List(n)
      case false => modulo_nat(n, nat_of_integer(BigInt(10))) ::
          digitsRev(divide_nat(n, nat_of_integer(BigInt(10))))
    }

  def showNat(n: nat): String =
    Str_Literal.literalOfAsciis(rev[BigInt](map[nat, BigInt](
      (d: nat) =>
        integer_of_nat(plus_nat(nat_of_integer(BigInt(48)), d)),
      digitsRev(n)
    )))

  def showInt(n: BigInt): String =
    less_int(n, zero_int) match {
      case true  => "-" + showNat(nat.apply(uminus_int(n)))
      case false => showNat(nat.apply(n))
    }

  def mysqlSaType(x0: canonical_type): sa_type = x0 match {
    case CtText()        => SaType("sa.String(length=255)", None)
    case CtVarchar(n)    => SaType("sa.String(length=" + showInt(n) + ")", None)
    case CtInt4()        => SaType("sa.Integer()", None)
    case CtSerial4()     => SaType("sa.Integer()", None)
    case CtInt8()        => SaType("sa.BigInteger()", None)
    case CtSerial8()     => SaType("sa.BigInteger()", None)
    case CtFloat8()      => SaType("sa.Float()", None)
    case CtBool()        => SaType("sa.Boolean()", None)
    case CtTimestamptz() => SaType("sa.DateTime()", None)
    case CtDateOnly()    => SaType("sa.Date()", None)
    case CtUuid()        => SaType("sa.Uuid()", None)
    case CtNumeric(p, Some(s)) =>
      SaType("sa.Numeric(" + showInt(p) + ", " + showInt(s) + ")", None)
    case CtNumeric(p, None) => SaType("sa.Numeric(" + showInt(p) + ")", None)
    case CtBytes()          => SaType("sa.LargeBinary()", None)
    case CtJson()           => SaType("sa.JSON()", None)
  }

  def invBody(x0: invariant_decl): expr = x0 match {
    case InvariantDeclFull(x1, x2, x3) => x2
  }

  def invName(x0: invariant_decl): Option[String] = x0 match {
    case InvariantDeclFull(x1, x2, x3) => x1
  }

  def invSpan(x0: invariant_decl): Option[span_t] = x0 match {
    case InvariantDeclFull(x1, x2, x3) => x3
  }

  def prdSpan(x0: predicate_decl): Option[span_t] = x0 match {
    case PredicateDeclFull(x1, x2, x3, x4) => x4
  }

  def svcEntities(x0: service_ir): List[entity_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x3
  }

  def svcSecurity(x0: service_ir): List[security_scheme_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x15
  }

  def trlVia(x0: transition_rule): String = x0 match {
    case TransitionRuleFull(x1, x2, x3, x4, x5) => x3
  }

  def enumLiteralOf(x0: expr, ms: List[String]): Option[String] = (x0, ms) match {
    case (EnumAccessF(uu, m, uv), ms) =>
      string_in_list(m, ms) match {
        case true  => Some[String](m)
        case false => None
      }
    case (IdentifierF(n, uw), ms) =>
      string_in_list(n, ms) match {
        case true  => Some[String](n)
        case false => None
      }
    case (BinaryOpF(v, va, vb, vc), uy)         => None
    case (UnaryOpF(v, va, vb), uy)              => None
    case (QuantifierF(v, va, vb, vc), uy)       => None
    case (SomeWrapF(v, va), uy)                 => None
    case (TheF(v, va, vb, vc), uy)              => None
    case (FieldAccessF(v, va, vb), uy)          => None
    case (IndexF(v, va, vb), uy)                => None
    case (CallF(v, va, vb), uy)                 => None
    case (PrimeF(v, va), uy)                    => None
    case (PreF(v, va), uy)                      => None
    case (WithF(v, va, vb), uy)                 => None
    case (IfF(v, va, vb, vc), uy)               => None
    case (LetF(v, va, vb, vc), uy)              => None
    case (LambdaF(v, va, vb), uy)               => None
    case (ConstructorF(v, va, vb), uy)          => None
    case (SetLiteralF(v, va), uy)               => None
    case (MapLiteralF(v, va), uy)               => None
    case (SetComprehensionF(v, va, vb, vc), uy) => None
    case (SeqLiteralF(v, va), uy)               => None
    case (MatchesF(v, va, vb), uy)              => None
    case (IntLitF(v, va), uy)                   => None
    case (FloatLitF(v, va), uy)                 => None
    case (StringLitF(v, va), uy)                => None
    case (BoolLitF(v, va), uy)                  => None
    case (NoneLitF(v), uy)                      => None
  }

  def extractKeySetEntries(x0: List[map_entry]): List[expr] = x0 match {
    case Nil                             => Nil
    case MapEntryFull(k, uu, uv) :: rest => k :: extractKeySetEntries(rest)
  }

  def extractKeySet(x0: expr): Option[List[expr]] = x0 match {
    case SetLiteralF(elements, uu) => Some[List[expr]](elements)
    case MapLiteralF(entries, uv) =>
      Some[List[expr]](extractKeySetEntries(entries))
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def isLenOrCardOf(e: expr): Option[String] =
    e match {
      case BinaryOpF(_, _, _, _)                                      => None
      case UnaryOpF(UNot(), _, _)                                     => None
      case UnaryOpF(UNegate(), _, _)                                  => None
      case UnaryOpF(UCardinality(), BinaryOpF(_, _, _, _), _)         => None
      case UnaryOpF(UCardinality(), UnaryOpF(_, _, _), _)             => None
      case UnaryOpF(UCardinality(), QuantifierF(_, _, _, _), _)       => None
      case UnaryOpF(UCardinality(), SomeWrapF(_, _), _)               => None
      case UnaryOpF(UCardinality(), TheF(_, _, _, _), _)              => None
      case UnaryOpF(UCardinality(), FieldAccessF(_, _, _), _)         => None
      case UnaryOpF(UCardinality(), EnumAccessF(_, _, _), _)          => None
      case UnaryOpF(UCardinality(), IndexF(_, _, _), _)               => None
      case UnaryOpF(UCardinality(), CallF(_, _, _), _)                => None
      case UnaryOpF(UCardinality(), PrimeF(_, _), _)                  => None
      case UnaryOpF(UCardinality(), PreF(_, _), _)                    => None
      case UnaryOpF(UCardinality(), WithF(_, _, _), _)                => None
      case UnaryOpF(UCardinality(), IfF(_, _, _, _), _)               => None
      case UnaryOpF(UCardinality(), LetF(_, _, _, _), _)              => None
      case UnaryOpF(UCardinality(), LambdaF(_, _, _), _)              => None
      case UnaryOpF(UCardinality(), ConstructorF(_, _, _), _)         => None
      case UnaryOpF(UCardinality(), SetLiteralF(_, _), _)             => None
      case UnaryOpF(UCardinality(), MapLiteralF(_, _), _)             => None
      case UnaryOpF(UCardinality(), SetComprehensionF(_, _, _, _), _) => None
      case UnaryOpF(UCardinality(), SeqLiteralF(_, _), _)             => None
      case UnaryOpF(UCardinality(), MatchesF(_, _, _), _)             => None
      case UnaryOpF(UCardinality(), IntLitF(_, _), _)                 => None
      case UnaryOpF(UCardinality(), FloatLitF(_, _), _)               => None
      case UnaryOpF(UCardinality(), StringLitF(_, _), _)              => None
      case UnaryOpF(UCardinality(), BoolLitF(_, _), _)                => None
      case UnaryOpF(UCardinality(), NoneLitF(_), _)                   => None
      case UnaryOpF(UCardinality(), IdentifierF(n, _), _)             => Some[String](n)
      case UnaryOpF(UPower(), _, _)                                   => None
      case QuantifierF(_, _, _, _)                                    => None
      case SomeWrapF(_, _)                                            => None
      case TheF(_, _, _, _)                                           => None
      case FieldAccessF(_, _, _)                                      => None
      case EnumAccessF(_, _, _)                                       => None
      case IndexF(_, _, _)                                            => None
      case CallF(BinaryOpF(_, _, _, _), _, _)                         => None
      case CallF(UnaryOpF(_, _, _), _, _)                             => None
      case CallF(QuantifierF(_, _, _, _), _, _)                       => None
      case CallF(SomeWrapF(_, _), _, _)                               => None
      case CallF(TheF(_, _, _, _), _, _)                              => None
      case CallF(FieldAccessF(_, _, _), _, _)                         => None
      case CallF(EnumAccessF(_, _, _), _, _)                          => None
      case CallF(IndexF(_, _, _), _, _)                               => None
      case CallF(CallF(_, _, _), _, _)                                => None
      case CallF(PrimeF(_, _), _, _)                                  => None
      case CallF(PreF(_, _), _, _)                                    => None
      case CallF(WithF(_, _, _), _, _)                                => None
      case CallF(IfF(_, _, _, _), _, _)                               => None
      case CallF(LetF(_, _, _, _), _, _)                              => None
      case CallF(LambdaF(_, _, _), _, _)                              => None
      case CallF(ConstructorF(_, _, _), _, _)                         => None
      case CallF(SetLiteralF(_, _), _, _)                             => None
      case CallF(MapLiteralF(_, _), _, _)                             => None
      case CallF(SetComprehensionF(_, _, _, _), _, _)                 => None
      case CallF(SeqLiteralF(_, _), _, _)                             => None
      case CallF(MatchesF(_, _, _), _, _)                             => None
      case CallF(IntLitF(_, _), _, _)                                 => None
      case CallF(FloatLitF(_, _), _, _)                               => None
      case CallF(StringLitF(_, _), _, _)                              => None
      case CallF(BoolLitF(_, _), _, _)                                => None
      case CallF(NoneLitF(_), _, _)                                   => None
      case CallF(IdentifierF(_, _), Nil, _)                           => None
      case CallF(IdentifierF(_, _), BinaryOpF(_, _, _, _) :: _, _)    => None
      case CallF(IdentifierF(_, _), UnaryOpF(_, _, _) :: _, _)        => None
      case CallF(IdentifierF(_, _), QuantifierF(_, _, _, _) :: _, _)  => None
      case CallF(IdentifierF(_, _), SomeWrapF(_, _) :: _, _)          => None
      case CallF(IdentifierF(_, _), TheF(_, _, _, _) :: _, _)         => None
      case CallF(IdentifierF(_, _), FieldAccessF(_, _, _) :: _, _)    => None
      case CallF(IdentifierF(_, _), EnumAccessF(_, _, _) :: _, _)     => None
      case CallF(IdentifierF(_, _), IndexF(_, _, _) :: _, _)          => None
      case CallF(IdentifierF(_, _), CallF(_, _, _) :: _, _)           => None
      case CallF(IdentifierF(_, _), PrimeF(_, _) :: _, _)             => None
      case CallF(IdentifierF(_, _), PreF(_, _) :: _, _)               => None
      case CallF(IdentifierF(_, _), WithF(_, _, _) :: _, _)           => None
      case CallF(IdentifierF(_, _), IfF(_, _, _, _) :: _, _)          => None
      case CallF(IdentifierF(_, _), LetF(_, _, _, _) :: _, _)         => None
      case CallF(IdentifierF(_, _), LambdaF(_, _, _) :: _, _)         => None
      case CallF(IdentifierF(_, _), ConstructorF(_, _, _) :: _, _)    => None
      case CallF(IdentifierF(_, _), SetLiteralF(_, _) :: _, _)        => None
      case CallF(IdentifierF(_, _), MapLiteralF(_, _) :: _, _)        => None
      case CallF(IdentifierF(_, _), SetComprehensionF(_, _, _, _) :: _, _) =>
        None
      case CallF(IdentifierF(_, _), SeqLiteralF(_, _) :: _, _) => None
      case CallF(IdentifierF(_, _), MatchesF(_, _, _) :: _, _) => None
      case CallF(IdentifierF(_, _), IntLitF(_, _) :: _, _)     => None
      case CallF(IdentifierF(_, _), FloatLitF(_, _) :: _, _)   => None
      case CallF(IdentifierF(_, _), StringLitF(_, _) :: _, _)  => None
      case CallF(IdentifierF(_, _), BoolLitF(_, _) :: _, _)    => None
      case CallF(IdentifierF(_, _), NoneLitF(_) :: _, _)       => None
      case CallF(IdentifierF(f, _), List(IdentifierF(n, _)), _) =>
        f == "len" match {
          case true  => Some[String](n)
          case false => None
        }
      case CallF(IdentifierF(_, _), IdentifierF(_, _) :: _ :: _, _) => None
      case PrimeF(_, _)                                             => None
      case PreF(_, _)                                               => None
      case WithF(_, _, _)                                           => None
      case IfF(_, _, _, _)                                          => None
      case LetF(_, _, _, _)                                         => None
      case LambdaF(_, _, _)                                         => None
      case ConstructorF(_, _, _)                                    => None
      case SetLiteralF(_, _)                                        => None
      case MapLiteralF(_, _)                                        => None
      case SetComprehensionF(_, _, _, _)                            => None
      case SeqLiteralF(_, _)                                        => None
      case MatchesF(_, _, _)                                        => None
      case IntLitF(_, _)                                            => None
      case FloatLitF(_, _)                                          => None
      case StringLitF(_, _)                                         => None
      case BoolLitF(_, _)                                           => None
      case NoneLitF(_)                                              => None
      case IdentifierF(_, _)                                        => None
    }

  def sameNamedType(uw: type_expr, ux: type_expr): Boolean = (uw, ux) match {
    case (NamedTypeF(a, uu), NamedTypeF(b, uv)) => a == b
    case (SetTypeF(v, va), ux)                  => false
    case (MapTypeF(v, va, vb), ux)              => false
    case (SeqTypeF(v, va), ux)                  => false
    case (OptionTypeF(v, va), ux)               => false
    case (RelationTypeF(v, va, vb, vc), ux)     => false
    case (uw, SetTypeF(v, va))                  => false
    case (uw, MapTypeF(v, va, vb))              => false
    case (uw, SeqTypeF(v, va))                  => false
    case (uw, OptionTypeF(v, va))               => false
    case (uw, RelationTypeF(v, va, vb, vc))     => false
  }

  def entityFieldsFull(x0: entity_decl): List[field_decl] = x0 match {
    case EntityDeclFull(uu, uv, fs, uw, ux) => fs
  }

  def entityHasField(es: List[entity_decl], ename: String, fname: String): Boolean =
    entityByName(es, ename) match {
      case None => false
      case Some(ed) =>
        list_ex[field_decl]((fd: field_decl) => fieldNameFull(fd) == fname, entityFieldsFull(ed))
    }

  def entityInvsFull(x0: entity_decl): List[expr] = x0 match {
    case EntityDeclFull(uu, uv, uw, iv, ux) => iv
  }

  def enumValuesFull(x0: enum_decl): List[String] = x0 match {
    case EnumDeclFull(uu, vs, uv) => vs
  }

  def flatten_entity(es: List[entity_decl], x1: entity_decl): entity_decl =
    (es, x1) match {
      case (es, EntityDeclFull(nm, pa, fs, iv, sp)) =>
        pa match {
          case None => EntityDeclFull(nm, pa, fs, iv, sp)
          case Some(_) =>
            val anc =
              butlast[entity_decl](chain_up(es, size_list[entity_decl](es), nm, List(nm))): List[
                entity_decl
              ];
            nulla[entity_decl](anc) match {
              case true => EntityDeclFull(nm, pa, fs, iv, sp)
              case false => EntityDeclFull(
                  nm,
                  pa,
                  foldl[List[field_decl], field_decl](
                    (a: List[field_decl]) =>
                      (b: field_decl) =>
                        upsert_field(a, b),
                    Nil,
                    maps[entity_decl, field_decl](
                      (a: entity_decl) =>
                        entityFieldsFull(a),
                      anc
                    ) ++
                      fs
                  ),
                  maps[entity_decl, expr]((a: entity_decl) => entityInvsFull(a), anc) ++ iv,
                  sp
                )
            }
        }
    }

  def primedIdSelect(x0: expr): List[String] = x0 match {
    case PrimeF(inner, uu) => rootIdentifier(inner) match {
        case None    => Nil
        case Some(n) => List(n)
      }
    case BinaryOpF(v, va, vb, vc)         => Nil
    case UnaryOpF(v, va, vb)              => Nil
    case QuantifierF(v, va, vb, vc)       => Nil
    case SomeWrapF(v, va)                 => Nil
    case TheF(v, va, vb, vc)              => Nil
    case FieldAccessF(v, va, vb)          => Nil
    case EnumAccessF(v, va, vb)           => Nil
    case IndexF(v, va, vb)                => Nil
    case CallF(v, va, vb)                 => Nil
    case PreF(v, va)                      => Nil
    case WithF(v, va, vb)                 => Nil
    case IfF(v, va, vb, vc)               => Nil
    case LetF(v, va, vb, vc)              => Nil
    case LambdaF(v, va, vb)               => Nil
    case ConstructorF(v, va, vb)          => Nil
    case SetLiteralF(v, va)               => Nil
    case MapLiteralF(v, va)               => Nil
    case SetComprehensionF(v, va, vb, vc) => Nil
    case SeqLiteralF(v, va)               => Nil
    case MatchesF(v, va, vb)              => Nil
    case IntLitF(v, va)                   => Nil
    case FloatLitF(v, va)                 => Nil
    case StringLitF(v, va)                => Nil
    case BoolLitF(v, va)                  => Nil
    case NoneLitF(v)                      => Nil
    case IdentifierF(v, va)               => Nil
  }

  def resolveWithBase(e: expr): Option[String] =
    e match {
      case BinaryOpF(_, _, _, _)   => rootIdentifier(e)
      case UnaryOpF(_, _, _)       => rootIdentifier(e)
      case QuantifierF(_, _, _, _) => rootIdentifier(e)
      case SomeWrapF(_, _)         => rootIdentifier(e)
      case TheF(_, _, _, _)        => rootIdentifier(e)
      case FieldAccessF(_, _, _)   => rootIdentifier(e)
      case EnumAccessF(_, _, _)    => rootIdentifier(e)
      case IndexF(base, _, _) =>
        base match {
          case BinaryOpF(_, _, _, _)                  => rootIdentifier(base)
          case UnaryOpF(_, _, _)                      => rootIdentifier(base)
          case QuantifierF(_, _, _, _)                => rootIdentifier(base)
          case SomeWrapF(_, _)                        => rootIdentifier(base)
          case TheF(_, _, _, _)                       => rootIdentifier(base)
          case FieldAccessF(_, _, _)                  => rootIdentifier(base)
          case EnumAccessF(_, _, _)                   => rootIdentifier(base)
          case IndexF(_, _, _)                        => rootIdentifier(base)
          case CallF(_, _, _)                         => rootIdentifier(base)
          case PrimeF(_, _)                           => rootIdentifier(base)
          case PreF(BinaryOpF(_, _, _, _), _)         => rootIdentifier(base)
          case PreF(UnaryOpF(_, _, _), _)             => rootIdentifier(base)
          case PreF(QuantifierF(_, _, _, _), _)       => rootIdentifier(base)
          case PreF(SomeWrapF(_, _), _)               => rootIdentifier(base)
          case PreF(TheF(_, _, _, _), _)              => rootIdentifier(base)
          case PreF(FieldAccessF(_, _, _), _)         => rootIdentifier(base)
          case PreF(EnumAccessF(_, _, _), _)          => rootIdentifier(base)
          case PreF(IndexF(_, _, _), _)               => rootIdentifier(base)
          case PreF(CallF(_, _, _), _)                => rootIdentifier(base)
          case PreF(PrimeF(_, _), _)                  => rootIdentifier(base)
          case PreF(PreF(_, _), _)                    => rootIdentifier(base)
          case PreF(WithF(_, _, _), _)                => rootIdentifier(base)
          case PreF(IfF(_, _, _, _), _)               => rootIdentifier(base)
          case PreF(LetF(_, _, _, _), _)              => rootIdentifier(base)
          case PreF(LambdaF(_, _, _), _)              => rootIdentifier(base)
          case PreF(ConstructorF(_, _, _), _)         => rootIdentifier(base)
          case PreF(SetLiteralF(_, _), _)             => rootIdentifier(base)
          case PreF(MapLiteralF(_, _), _)             => rootIdentifier(base)
          case PreF(SetComprehensionF(_, _, _, _), _) => rootIdentifier(base)
          case PreF(SeqLiteralF(_, _), _)             => rootIdentifier(base)
          case PreF(MatchesF(_, _, _), _)             => rootIdentifier(base)
          case PreF(IntLitF(_, _), _)                 => rootIdentifier(base)
          case PreF(FloatLitF(_, _), _)               => rootIdentifier(base)
          case PreF(StringLitF(_, _), _)              => rootIdentifier(base)
          case PreF(BoolLitF(_, _), _)                => rootIdentifier(base)
          case PreF(NoneLitF(_), _)                   => rootIdentifier(base)
          case PreF(IdentifierF(n, _), _)             => Some[String](n)
          case WithF(_, _, _)                         => rootIdentifier(base)
          case IfF(_, _, _, _)                        => rootIdentifier(base)
          case LetF(_, _, _, _)                       => rootIdentifier(base)
          case LambdaF(_, _, _)                       => rootIdentifier(base)
          case ConstructorF(_, _, _)                  => rootIdentifier(base)
          case SetLiteralF(_, _)                      => rootIdentifier(base)
          case MapLiteralF(_, _)                      => rootIdentifier(base)
          case SetComprehensionF(_, _, _, _)          => rootIdentifier(base)
          case SeqLiteralF(_, _)                      => rootIdentifier(base)
          case MatchesF(_, _, _)                      => rootIdentifier(base)
          case IntLitF(_, _)                          => rootIdentifier(base)
          case FloatLitF(_, _)                        => rootIdentifier(base)
          case StringLitF(_, _)                       => rootIdentifier(base)
          case BoolLitF(_, _)                         => rootIdentifier(base)
          case NoneLitF(_)                            => rootIdentifier(base)
          case IdentifierF(n, _)                      => Some[String](n)
        }
      case CallF(_, _, _)                => rootIdentifier(e)
      case PrimeF(_, _)                  => rootIdentifier(e)
      case PreF(_, _)                    => rootIdentifier(e)
      case WithF(_, _, _)                => rootIdentifier(e)
      case IfF(_, _, _, _)               => rootIdentifier(e)
      case LetF(_, _, _, _)              => rootIdentifier(e)
      case LambdaF(_, _, _)              => rootIdentifier(e)
      case ConstructorF(_, _, _)         => rootIdentifier(e)
      case SetLiteralF(_, _)             => rootIdentifier(e)
      case MapLiteralF(_, _)             => rootIdentifier(e)
      case SetComprehensionF(_, _, _, _) => rootIdentifier(e)
      case SeqLiteralF(_, _)             => rootIdentifier(e)
      case MatchesF(_, _, _)             => rootIdentifier(e)
      case IntLitF(_, _)                 => rootIdentifier(e)
      case FloatLitF(_, _)               => rootIdentifier(e)
      case StringLitF(_, _)              => rootIdentifier(e)
      case BoolLitF(_, _)                => rootIdentifier(e)
      case NoneLitF(_)                   => rootIdentifier(e)
      case IdentifierF(_, _)             => None
    }

  def fieldAssignName(x0: field_assign): String = x0 match {
    case FieldAssignFull(n, uu, uv) => n
  }

  def withInfoSelect(x0: expr): List[with_info] = x0 match {
    case WithF(base, ups, uu) =>
      List(WithInfoFull(
        map[field_assign, String]((a: field_assign) => fieldAssignName(a), ups),
        resolveWithBase(base)
      ))
    case BinaryOpF(v, va, vb, vc)         => Nil
    case UnaryOpF(v, va, vb)              => Nil
    case QuantifierF(v, va, vb, vc)       => Nil
    case SomeWrapF(v, va)                 => Nil
    case TheF(v, va, vb, vc)              => Nil
    case FieldAccessF(v, va, vb)          => Nil
    case EnumAccessF(v, va, vb)           => Nil
    case IndexF(v, va, vb)                => Nil
    case CallF(v, va, vb)                 => Nil
    case PrimeF(v, va)                    => Nil
    case PreF(v, va)                      => Nil
    case IfF(v, va, vb, vc)               => Nil
    case LetF(v, va, vb, vc)              => Nil
    case LambdaF(v, va, vb)               => Nil
    case ConstructorF(v, va, vb)          => Nil
    case SetLiteralF(v, va)               => Nil
    case MapLiteralF(v, va)               => Nil
    case SetComprehensionF(v, va, vb, vc) => Nil
    case SeqLiteralF(v, va)               => Nil
    case MatchesF(v, va, vb)              => Nil
    case IntLitF(v, va)                   => Nil
    case FloatLitF(v, va)                 => Nil
    case StringLitF(v, va)                => Nil
    case BoolLitF(v, va)                  => Nil
    case NoneLitF(v)                      => Nil
    case IdentifierF(v, va)               => Nil
  }

  def derivePathPattern(
      knd: operation_kind,
      segment: String,
      idParamOpt: Option[String],
      action: String,
      opKebab: String
  ): String =
    knd match {
      case Create()        => "/" + segment
      case Read()          => pathWithIdSuffix(segment, idParamOpt)
      case Replace()       => pathWithIdSuffix(segment, idParamOpt)
      case PartialUpdate() => pathWithIdSuffix(segment, idParamOpt)
      case Deletea()       => pathWithIdSuffix(segment, idParamOpt)
      case CreateChild()   => "/" + segment
      case FilteredRead()  => pathWithIdSuffix(segment, idParamOpt)
      case SideEffect()    => "/" + opKebab
      case BatchMutation() => "/" + segment + "/batch"
      case Transition() =>
        idParamOpt match {
          case None       => "/" + segment + "/" + action
          case Some(idNm) => "/" + segment + "/{" + idNm + "}/" + action
        }
    }

  def arraySchema(items: schema_object, mnI: Option[BigInt], mxI: Option[BigInt]): schema_object =
    SchemaObject(
      Some[List[String]](List("array")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      mnI,
      mxI,
      None,
      None,
      Some[schema_object](items),
      None,
      None,
      None,
      None,
      None,
      None,
      false
    )

  def literalDropLeft(n: nat, s: String): String =
    Str_Literal.literalOfAsciis(drop[BigInt](n, Str_Literal.asciisOfLiteral(s)))

  def triggerAggregateOf(x0: trigger_spec): trigger_aggregate = x0 match {
    case TriggerSpec(uu, uv, uw, ux, uy, uz, a, va) => a
  }

  def triggerSourceTable(x0: trigger_spec): String = x0 match {
    case TriggerSpec(uu, uv, uw, ux, st, uy, uz, va) => st
  }

  def triggerTargetTable(x0: trigger_spec): String = x0 match {
    case TriggerSpec(uu, uv, tt, uw, ux, uy, uz, va) => tt
  }

  def peelRelationRef(x0: expr): Option[String] = x0 match {
    case IdentifierF(rel, uu)             => Some[String](rel)
    case PreF(b, uv)                      => identName(b)
    case PrimeF(b, uw)                    => identName(b)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
  }

  def tc_entities[A](x0: tyctx_ext[A]): List[entity_decl] = x0 match {
    case tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more) => tc_entities
  }

  def typeExprFullToTy(enums: List[String], entities: List[String], x2: type_expr): Option[ty] =
    (enums, entities, x2) match {
      case (enums, entities, NamedTypeF(n, uu)) =>
        n == "Bool" || n == "Boolean" match {
          case true => Some[ty](TBool())
          case false => n == "Int" ||
              (n == "DateTime" ||
                (n == "Date" || n == "Duration")) match {
              case true => Some[ty](TInt())
              case false => n == "Float" ||
                  (n == "Decimal" || n == "Money") match {
                  case true => Some[ty](TReal())
                  case false => n == "String" match {
                      case true => Some[ty](TStr())
                      case false => membera[String](enums, n) match {
                          case true => Some[ty](TEnum(n))
                          case false => membera[String](entities, n) match {
                              case true  => Some[ty](TEntity(n))
                              case false => None
                            }
                        }
                    }
                }
            }
        }
      case (enums, entities, SetTypeF(inner, uv)) =>
        map_option[ty, ty]((a: ty) => TSet(a), typeExprFullToTy(enums, entities, inner))
      case (enums, entities, OptionTypeF(inner, uw)) =>
        map_option[ty, ty]((a: ty) => TOption(a), typeExprFullToTy(enums, entities, inner))
      case (enums, entities, SeqTypeF(inner, ux)) =>
        map_option[ty, ty]((a: ty) => TSeq(a), typeExprFullToTy(enums, entities, inner))
      case (enums, entities, MapTypeF(k, v, uy)) =>
        (typeExprFullToTy(enums, entities, k), typeExprFullToTy(enums, entities, v)) match {
          case (None, _)            => None
          case (Some(_), None)      => None
          case (Some(tk), Some(tv)) => Some[ty](TMap(tk, tv))
        }
      case (uz, va, RelationTypeF(v, vc, vd, ve)) => None
    }

  def schemaFieldType(gamma: tyctx_ext[Unit], ename: String, fname: String): Option[ty] =
    find[entity_decl](
      (ed: entity_decl) => entityNameFull(ed) == ename,
      tc_entities[Unit](gamma)
    ) match {
      case None => None
      case Some(ed) =>
        find[field_decl](
          (fd: field_decl) => fieldNameFull(fd) == fname,
          entityFieldsFull(ed)
        ) match {
          case None => None
          case Some(fd) =>
            typeExprFullToTy(
              tc_enums[Unit](gamma),
              map[entity_decl, String](
                (a: entity_decl) =>
                  entityNameFull(a),
                tc_entities[Unit](gamma)
              ),
              fieldTypeFull(fd)
            )
        }
    }

  def serviceEntities(x0: service_ir): List[entity_decl] = x0 match {
    case ServiceIRFull(uu, uv, es, uw, ux, uy, uz, va, vb, vc, vd, ve, vf, vg, vh, vi) => es
  }

  def isDateTimeTypeAux(fuel: nat, uu: List[type_alias_decl], uv: type_expr): Boolean =
    equal_nat(fuel, zero_nat) match {
      case true => false
      case false => uv match {
          case NamedTypeF(n, _) =>
            n == "DateTime" match {
              case true => true
              case false => lookupAliasTarget(uu, n) match {
                  case None    => false
                  case Some(a) => isDateTimeTypeAux(minus_nat(fuel, one_nat), uu, a)
                }
            }
          case SetTypeF(_, _)    => false
          case MapTypeF(_, _, _) => false
          case SeqTypeF(_, _)    => false
          case OptionTypeF(inner, _) =>
            isDateTimeTypeAux(minus_nat(fuel, one_nat), uu, inner)
          case RelationTypeF(_, _, _, _) => false
        }
    }

  def isDateTimeType(aliases: List[type_alias_decl], t: type_expr): Boolean =
    isDateTimeTypeAux(typeWalkFuel(aliases), aliases, t)

  def isOptionalTypeAux(fuel: nat, uu: List[type_alias_decl], uv: type_expr): Boolean =
    equal_nat(fuel, zero_nat) match {
      case true => false
      case false => uv match {
          case NamedTypeF(n, _) =>
            lookupAliasTarget(uu, n) match {
              case None => false
              case Some(a) =>
                isOptionalTypeAux(minus_nat(fuel, one_nat), uu, a)
            }
          case SetTypeF(_, _)            => false
          case MapTypeF(_, _, _)         => false
          case SeqTypeF(_, _)            => false
          case OptionTypeF(_, _)         => true
          case RelationTypeF(_, _, _, _) => false
        }
    }

  def isOptionalType(aliases: List[type_alias_decl], t: type_expr): Boolean =
    isOptionalTypeAux(typeWalkFuel(aliases), aliases, t)

  def alloyBinopShape(x0: bin_op): alloy_binop_shape = x0 match {
    case BAnd()       => AbsLogical("and")
    case BOr()        => AbsLogical("or")
    case BImplies()   => AbsLogical("implies")
    case BIff()       => AbsLogical("iff")
    case BEq()        => AbsInfix("=")
    case BNeq()       => AbsInfix("!=")
    case BLt()        => AbsInfix("<")
    case BLe()        => AbsInfix("<=")
    case BGt()        => AbsInfix(">")
    case BGe()        => AbsInfix(">=")
    case BIn()        => AbsInfix("in")
    case BNotIn()     => AbsInfix("!in")
    case BSubset()    => AbsInfix("in")
    case BUnion()     => AbsInfix("+")
    case BIntersect() => AbsInfix("&")
    case BDiff()      => AbsInfix("-")
    case BAdd()       => AbsPrefixCall("plus")
    case BSub()       => AbsPrefixCall("minus")
    case BMul()       => AbsPrefixCall("mul")
    case BDiv()       => AbsPrefixCall("div")
  }

  def signalsDeletesKey(x0: analysis_signals): Boolean = x0 match {
    case AnalysisSignals(uu, uv, uw, d, ux, uy, uz, va, vb) => d
  }

  def postgresCaps: dialect_caps =
    DialectCaps(true, true, true, true, false, true)

  def sqliteSaType(x0: canonical_type): sa_type = x0 match {
    case CtText()        => SaType("sa.Text()", None)
    case CtVarchar(uu)   => SaType("sa.Text()", None)
    case CtInt4()        => SaType("sa.Integer()", None)
    case CtSerial4()     => SaType("sa.Integer()", None)
    case CtInt8()        => SaType("sa.BigInteger()", None)
    case CtSerial8()     => SaType("sa.Integer()", None)
    case CtFloat8()      => SaType("sa.Float()", None)
    case CtBool()        => SaType("sa.Boolean()", None)
    case CtTimestamptz() => SaType("sa.DateTime()", None)
    case CtDateOnly()    => SaType("sa.Date()", None)
    case CtUuid()        => SaType("sa.Uuid()", None)
    case CtNumeric(p, Some(s)) =>
      SaType("sa.Numeric(" + showInt(p) + ", " + showInt(s) + ")", None)
    case CtNumeric(p, None) => SaType("sa.Numeric(" + showInt(p) + ")", None)
    case CtBytes()          => SaType("sa.LargeBinary()", None)
    case CtJson()           => SaType("sa.JSON()", None)
  }

  def cvrSpan(x0: convention_rule): Option[span_t] = x0 match {
    case ConventionRuleFull(x1, x2, x3, x4, x5) => x5
  }

  def operName(x0: operation_decl): String = x0 match {
    case OperationDeclFull(x1, x2, x3, x4, x5, x6, x7) => x1
  }

  def operSpan(x0: operation_decl): Option[span_t] = x0 match {
    case OperationDeclFull(x1, x2, x3, x4, x5, x6, x7) => x7
  }

  def svcFunctions(x0: service_ir): List[function_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x12
  }

  def svcTemporals(x0: service_ir): List[temporal_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x10
  }

  def trnName(x0: transition_decl): String = x0 match {
    case TransitionDeclFull(x1, x2, x3, x4, x5) => x1
  }

  def trnSpan(x0: transition_decl): Option[span_t] = x0 match {
    case TransitionDeclFull(x1, x2, x3, x4, x5) => x5
  }

  def trlFrom(x0: transition_rule): String = x0 match {
    case TransitionRuleFull(x1, x2, x3, x4, x5) => x1
  }

  def trlSpan(x0: transition_rule): Option[span_t] = x0 match {
    case TransitionRuleFull(x1, x2, x3, x4, x5) => x5
  }

  def talName(x0: type_alias_decl): String = x0 match {
    case TypeAliasDeclFull(x1, x2, x3, x4) => x1
  }

  def talSpan(x0: type_alias_decl): Option[span_t] = x0 match {
    case TypeAliasDeclFull(x1, x2, x3, x4) => x4
  }

  def talType(x0: type_alias_decl): type_expr = x0 match {
    case TypeAliasDeclFull(x1, x2, x3, x4) => x2
  }

  def equal_lit_class(x0: lit_class, x1: lit_class): Boolean = (x0, x1) match {
    case (LcCollection(), LcNone())       => false
    case (LcNone(), LcCollection())       => false
    case (LcStringLike(), LcNone())       => false
    case (LcNone(), LcStringLike())       => false
    case (LcStringLike(), LcCollection()) => false
    case (LcCollection(), LcStringLike()) => false
    case (LcBool(), LcNone())             => false
    case (LcNone(), LcBool())             => false
    case (LcBool(), LcCollection())       => false
    case (LcCollection(), LcBool())       => false
    case (LcBool(), LcStringLike())       => false
    case (LcStringLike(), LcBool())       => false
    case (LcNumeric(), LcNone())          => false
    case (LcNone(), LcNumeric())          => false
    case (LcNumeric(), LcCollection())    => false
    case (LcCollection(), LcNumeric())    => false
    case (LcNumeric(), LcStringLike())    => false
    case (LcStringLike(), LcNumeric())    => false
    case (LcNumeric(), LcBool())          => false
    case (LcBool(), LcNumeric())          => false
    case (LcNone(), LcNone())             => true
    case (LcCollection(), LcCollection()) => true
    case (LcStringLike(), LcStringLike()) => true
    case (LcBool(), LcBool())             => true
    case (LcNumeric(), LcNumeric())       => true
  }

  def isMembershipBin(x0: bin_op): Boolean = x0 match {
    case BIn()        => true
    case BNotIn()     => true
    case BAnd()       => false
    case BOr()        => false
    case BImplies()   => false
    case BIff()       => false
    case BEq()        => false
    case BNeq()       => false
    case BLt()        => false
    case BGt()        => false
    case BLe()        => false
    case BGe()        => false
    case BSubset()    => false
    case BUnion()     => false
    case BIntersect() => false
    case BDiff()      => false
    case BAdd()       => false
    case BSub()       => false
    case BMul()       => false
    case BDiv()       => false
  }

  def typeMismatchAt(e: expr): Option[(type_mismatch_kind, Option[span_t])] =
    e match {
      case BinaryOpF(op, l, r, sp) =>
        val cs =
          map_filter[Option[lit_class], lit_class](
            id[Option[lit_class]],
            List(litClass(l), litClass(r))
          ): List[lit_class];
        isArithBin(op) match {
          case true => map_option[lit_class, (type_mismatch_kind, Option[span_t])](
              (c: lit_class) =>
                (TmArithLitMisuse(op, c), sp),
              find[lit_class](
                (c: lit_class) =>
                  equal_lit_class(c, LcBool()) ||
                    equal_lit_class(c, LcNone()),
                cs
              )
            )
          case false => isComp(op) match {
              case true => map_option[lit_class, (type_mismatch_kind, Option[span_t])](
                  (c: lit_class) =>
                    (TmCompareLitMisuse(op, c), sp),
                  find[lit_class](
                    (c: lit_class) =>
                      equal_lit_class(c, LcBool()) || equal_lit_class(c, LcNone()),
                    cs
                  )
                )
              case false => isLogicalBin(op) match {
                  case true => map_option[lit_class, (type_mismatch_kind, Option[span_t])](
                      (c: lit_class) =>
                        (TmLogicalLitMisuse(op, c), sp),
                      find[lit_class]((c: lit_class) => !equal_lit_class(c, LcBool()), cs)
                    )
                  case false => isMembershipBin(op) match {
                      case true => litClass(r) match {
                          case None => None
                          case Some(LcNumeric()) =>
                            Some[(type_mismatch_kind, Option[span_t])]((
                              TmMembershipLitMisuse(op, LcNumeric()),
                              sp
                            ))
                          case Some(LcBool()) =>
                            Some[(type_mismatch_kind, Option[span_t])]((
                              TmMembershipLitMisuse(op, LcBool()),
                              sp
                            ))
                          case Some(LcStringLike()) =>
                            Some[(type_mismatch_kind, Option[span_t])]((
                              TmMembershipLitMisuse(op, LcStringLike()),
                              sp
                            ))
                          case Some(LcCollection()) => None
                          case Some(LcNone()) =>
                            Some[(type_mismatch_kind, Option[span_t])]((
                              TmMembershipLitMisuse(op, LcNone()),
                              sp
                            ))
                        }
                      case false => None
                    }
                }
            }
        }
      case UnaryOpF(UNot(), inner, sp) =>
        litClass(inner) match {
          case None => None
          case Some(LcNumeric()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNotOnNonBool(LcNumeric()), sp))
          case Some(LcBool()) => None
          case Some(LcStringLike()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNotOnNonBool(LcStringLike()), sp))
          case Some(LcCollection()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNotOnNonBool(LcCollection()), sp))
          case Some(LcNone()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNotOnNonBool(LcNone()), sp))
        }
      case UnaryOpF(UNegate(), inner, sp) =>
        litClass(inner) match {
          case None              => None
          case Some(LcNumeric()) => None
          case Some(LcBool()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNegOnNonNumeric(LcBool()), sp))
          case Some(LcStringLike()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNegOnNonNumeric(LcStringLike()), sp))
          case Some(LcCollection()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNegOnNonNumeric(LcCollection()), sp))
          case Some(LcNone()) =>
            Some[(type_mismatch_kind, Option[span_t])]((TmUnaryNegOnNonNumeric(LcNone()), sp))
        }
      case UnaryOpF(UCardinality(), _, _) => None
      case UnaryOpF(UPower(), _, _)       => None
      case QuantifierF(_, _, _, _)        => None
      case SomeWrapF(_, _)                => None
      case TheF(_, _, _, _)               => None
      case FieldAccessF(_, _, _)          => None
      case EnumAccessF(_, _, _)           => None
      case IndexF(_, _, _)                => None
      case CallF(_, _, _)                 => None
      case PrimeF(_, _)                   => None
      case PreF(_, _)                     => None
      case WithF(_, _, _)                 => None
      case IfF(_, _, _, _)                => None
      case LetF(_, _, _, _)               => None
      case LambdaF(_, _, _)               => None
      case ConstructorF(_, _, _)          => None
      case SetLiteralF(_, _)              => None
      case MapLiteralF(_, _)              => None
      case SetComprehensionF(_, _, _, _)  => None
      case SeqLiteralF(_, _)              => None
      case MatchesF(_, _, _)              => None
      case IntLitF(_, _)                  => None
      case FloatLitF(_, _)                => None
      case StringLitF(_, _)               => None
      case BoolLitF(_, _)                 => None
      case NoneLitF(_)                    => None
      case IdentifierF(_, _)              => None
    }

  def containsPreInPlusChain(x0: expr, field: String): Boolean = (x0, field) match {
    case (PreF(e, uu), field) => e match {
        case BinaryOpF(_, _, _, _)         => false
        case UnaryOpF(_, _, _)             => false
        case QuantifierF(_, _, _, _)       => false
        case SomeWrapF(_, _)               => false
        case TheF(_, _, _, _)              => false
        case FieldAccessF(_, _, _)         => false
        case EnumAccessF(_, _, _)          => false
        case IndexF(_, _, _)               => false
        case CallF(_, _, _)                => false
        case PrimeF(_, _)                  => false
        case PreF(_, _)                    => false
        case WithF(_, _, _)                => false
        case IfF(_, _, _, _)               => false
        case LetF(_, _, _, _)              => false
        case LambdaF(_, _, _)              => false
        case ConstructorF(_, _, _)         => false
        case SetLiteralF(_, _)             => false
        case MapLiteralF(_, _)             => false
        case SetComprehensionF(_, _, _, _) => false
        case SeqLiteralF(_, _)             => false
        case MatchesF(_, _, _)             => false
        case IntLitF(_, _)                 => false
        case FloatLitF(_, _)               => false
        case StringLitF(_, _)              => false
        case BoolLitF(_, _)                => false
        case NoneLitF(_)                   => false
        case IdentifierF(n, _)             => n == field
      }
    case (BinaryOpF(op, l, r, uv), field) =>
      (op match {
        case BAnd()       => false
        case BOr()        => false
        case BImplies()   => false
        case BIff()       => false
        case BEq()        => false
        case BNeq()       => false
        case BLt()        => false
        case BGt()        => false
        case BLe()        => false
        case BGe()        => false
        case BIn()        => false
        case BNotIn()     => false
        case BSubset()    => false
        case BUnion()     => false
        case BIntersect() => false
        case BDiff()      => false
        case BAdd()       => true
        case BSub()       => false
        case BMul()       => false
        case BDiv()       => false
      }) &&
      (containsPreInPlusChain(l, field) || containsPreInPlusChain(r, field))
    case (UnaryOpF(v, va, vb), ux)              => false
    case (QuantifierF(v, va, vb, vc), ux)       => false
    case (SomeWrapF(v, va), ux)                 => false
    case (TheF(v, va, vb, vc), ux)              => false
    case (FieldAccessF(v, va, vb), ux)          => false
    case (EnumAccessF(v, va, vb), ux)           => false
    case (IndexF(v, va, vb), ux)                => false
    case (CallF(v, va, vb), ux)                 => false
    case (PrimeF(v, va), ux)                    => false
    case (WithF(v, va, vb), ux)                 => false
    case (IfF(v, va, vb, vc), ux)               => false
    case (LetF(v, va, vb, vc), ux)              => false
    case (LambdaF(v, va, vb), ux)               => false
    case (ConstructorF(v, va, vb), ux)          => false
    case (SetLiteralF(v, va), ux)               => false
    case (MapLiteralF(v, va), ux)               => false
    case (SetComprehensionF(v, va, vb, vc), ux) => false
    case (SeqLiteralF(v, va), ux)               => false
    case (MatchesF(v, va, vb), ux)              => false
    case (IntLitF(v, va), ux)                   => false
    case (FloatLitF(v, va), ux)                 => false
    case (StringLitF(v, va), ux)                => false
    case (BoolLitF(v, va), ux)                  => false
    case (NoneLitF(v), ux)                      => false
    case (IdentifierF(v, va), ux)               => false
  }

  def createPatternOf(stateFields: List[String], e: expr): List[String] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)                                 => Nil
      case BinaryOpF(BOr(), _, _, _)                                  => Nil
      case BinaryOpF(BImplies(), _, _, _)                             => Nil
      case BinaryOpF(BIff(), _, _, _)                                 => Nil
      case BinaryOpF(BEq(), BinaryOpF(_, _, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), UnaryOpF(_, _, _), _, _)                  => Nil
      case BinaryOpF(BEq(), QuantifierF(_, _, _, _), _, _)            => Nil
      case BinaryOpF(BEq(), SomeWrapF(_, _), _, _)                    => Nil
      case BinaryOpF(BEq(), TheF(_, _, _, _), _, _)                   => Nil
      case BinaryOpF(BEq(), FieldAccessF(_, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), EnumAccessF(_, _, _), _, _)               => Nil
      case BinaryOpF(BEq(), IndexF(_, _, _), _, _)                    => Nil
      case BinaryOpF(BEq(), CallF(_, _, _), _, _)                     => Nil
      case BinaryOpF(BEq(), PrimeF(BinaryOpF(_, _, _, _), _), _, _)   => Nil
      case BinaryOpF(BEq(), PrimeF(UnaryOpF(_, _, _), _), _, _)       => Nil
      case BinaryOpF(BEq(), PrimeF(QuantifierF(_, _, _, _), _), _, _) => Nil
      case BinaryOpF(BEq(), PrimeF(SomeWrapF(_, _), _), _, _)         => Nil
      case BinaryOpF(BEq(), PrimeF(TheF(_, _, _, _), _), _, _)        => Nil
      case BinaryOpF(BEq(), PrimeF(FieldAccessF(_, _, _), _), _, _)   => Nil
      case BinaryOpF(BEq(), PrimeF(EnumAccessF(_, _, _), _), _, _)    => Nil
      case BinaryOpF(BEq(), PrimeF(IndexF(_, _, _), _), _, _)         => Nil
      case BinaryOpF(BEq(), PrimeF(CallF(_, _, _), _), _, _)          => Nil
      case BinaryOpF(BEq(), PrimeF(PrimeF(_, _), _), _, _)            => Nil
      case BinaryOpF(BEq(), PrimeF(PreF(_, _), _), _, _)              => Nil
      case BinaryOpF(BEq(), PrimeF(WithF(_, _, _), _), _, _)          => Nil
      case BinaryOpF(BEq(), PrimeF(IfF(_, _, _, _), _), _, _)         => Nil
      case BinaryOpF(BEq(), PrimeF(LetF(_, _, _, _), _), _, _)        => Nil
      case BinaryOpF(BEq(), PrimeF(LambdaF(_, _, _), _), _, _)        => Nil
      case BinaryOpF(BEq(), PrimeF(ConstructorF(_, _, _), _), _, _)   => Nil
      case BinaryOpF(BEq(), PrimeF(SetLiteralF(_, _), _), _, _)       => Nil
      case BinaryOpF(BEq(), PrimeF(MapLiteralF(_, _), _), _, _)       => Nil
      case BinaryOpF(BEq(), PrimeF(SetComprehensionF(_, _, _, _), _), _, _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(SeqLiteralF(_, _), _), _, _) => Nil
      case BinaryOpF(BEq(), PrimeF(MatchesF(_, _, _), _), _, _) => Nil
      case BinaryOpF(BEq(), PrimeF(IntLitF(_, _), _), _, _)     => Nil
      case BinaryOpF(BEq(), PrimeF(FloatLitF(_, _), _), _, _)   => Nil
      case BinaryOpF(BEq(), PrimeF(StringLitF(_, _), _), _, _)  => Nil
      case BinaryOpF(BEq(), PrimeF(BoolLitF(_, _), _), _, _)    => Nil
      case BinaryOpF(BEq(), PrimeF(NoneLitF(_), _), _, _)       => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(name, _), _), rhs, _) =>
        rhs match {
          case BinaryOpF(BAnd(), _, _, _)       => Nil
          case BinaryOpF(BOr(), _, _, _)        => Nil
          case BinaryOpF(BImplies(), _, _, _)   => Nil
          case BinaryOpF(BIff(), _, _, _)       => Nil
          case BinaryOpF(BEq(), _, _, _)        => Nil
          case BinaryOpF(BNeq(), _, _, _)       => Nil
          case BinaryOpF(BLt(), _, _, _)        => Nil
          case BinaryOpF(BGt(), _, _, _)        => Nil
          case BinaryOpF(BLe(), _, _, _)        => Nil
          case BinaryOpF(BGe(), _, _, _)        => Nil
          case BinaryOpF(BIn(), _, _, _)        => Nil
          case BinaryOpF(BNotIn(), _, _, _)     => Nil
          case BinaryOpF(BSubset(), _, _, _)    => Nil
          case BinaryOpF(BUnion(), _, _, _)     => Nil
          case BinaryOpF(BIntersect(), _, _, _) => Nil
          case BinaryOpF(BDiff(), _, _, _)      => Nil
          case BinaryOpF(BAdd(), _, _, _) =>
            string_in_list(name, stateFields) &&
              containsPreInPlusChain(rhs, name) match {
              case true  => List(name)
              case false => Nil
            }
          case BinaryOpF(BSub(), _, _, _)    => Nil
          case BinaryOpF(BMul(), _, _, _)    => Nil
          case BinaryOpF(BDiv(), _, _, _)    => Nil
          case UnaryOpF(_, _, _)             => Nil
          case QuantifierF(_, _, _, _)       => Nil
          case SomeWrapF(_, _)               => Nil
          case TheF(_, _, _, _)              => Nil
          case FieldAccessF(_, _, _)         => Nil
          case EnumAccessF(_, _, _)          => Nil
          case IndexF(_, _, _)               => Nil
          case CallF(_, _, _)                => Nil
          case PrimeF(_, _)                  => Nil
          case PreF(_, _)                    => Nil
          case WithF(_, _, _)                => Nil
          case IfF(_, _, _, _)               => Nil
          case LetF(_, _, _, _)              => Nil
          case LambdaF(_, _, _)              => Nil
          case ConstructorF(_, _, _)         => Nil
          case SetLiteralF(_, _)             => Nil
          case MapLiteralF(_, _)             => Nil
          case SetComprehensionF(_, _, _, _) => Nil
          case SeqLiteralF(_, _)             => Nil
          case MatchesF(_, _, _)             => Nil
          case IntLitF(_, _)                 => Nil
          case FloatLitF(_, _)               => Nil
          case StringLitF(_, _)              => Nil
          case BoolLitF(_, _)                => Nil
          case NoneLitF(_)                   => Nil
          case IdentifierF(_, _)             => Nil
        }
      case BinaryOpF(BEq(), PreF(_, _), _, _)                    => Nil
      case BinaryOpF(BEq(), WithF(_, _, _), _, _)                => Nil
      case BinaryOpF(BEq(), IfF(_, _, _, _), _, _)               => Nil
      case BinaryOpF(BEq(), LetF(_, _, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), LambdaF(_, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), ConstructorF(_, _, _), _, _)         => Nil
      case BinaryOpF(BEq(), SetLiteralF(_, _), _, _)             => Nil
      case BinaryOpF(BEq(), MapLiteralF(_, _), _, _)             => Nil
      case BinaryOpF(BEq(), SetComprehensionF(_, _, _, _), _, _) => Nil
      case BinaryOpF(BEq(), SeqLiteralF(_, _), _, _)             => Nil
      case BinaryOpF(BEq(), MatchesF(_, _, _), _, _)             => Nil
      case BinaryOpF(BEq(), IntLitF(_, _), _, _)                 => Nil
      case BinaryOpF(BEq(), FloatLitF(_, _), _, _)               => Nil
      case BinaryOpF(BEq(), StringLitF(_, _), _, _)              => Nil
      case BinaryOpF(BEq(), BoolLitF(_, _), _, _)                => Nil
      case BinaryOpF(BEq(), NoneLitF(_), _, _)                   => Nil
      case BinaryOpF(BEq(), IdentifierF(_, _), _, _)             => Nil
      case BinaryOpF(BNeq(), _, _, _)                            => Nil
      case BinaryOpF(BLt(), _, _, _)                             => Nil
      case BinaryOpF(BGt(), _, _, _)                             => Nil
      case BinaryOpF(BLe(), _, _, _)                             => Nil
      case BinaryOpF(BGe(), _, _, _)                             => Nil
      case BinaryOpF(BIn(), _, _, _)                             => Nil
      case BinaryOpF(BNotIn(), _, _, _)                          => Nil
      case BinaryOpF(BSubset(), _, _, _)                         => Nil
      case BinaryOpF(BUnion(), _, _, _)                          => Nil
      case BinaryOpF(BIntersect(), _, _, _)                      => Nil
      case BinaryOpF(BDiff(), _, _, _)                           => Nil
      case BinaryOpF(BAdd(), _, _, _)                            => Nil
      case BinaryOpF(BSub(), _, _, _)                            => Nil
      case BinaryOpF(BMul(), _, _, _)                            => Nil
      case BinaryOpF(BDiv(), _, _, _)                            => Nil
      case UnaryOpF(_, _, _)                                     => Nil
      case QuantifierF(_, _, _, _)                               => Nil
      case SomeWrapF(_, _)                                       => Nil
      case TheF(_, _, _, _)                                      => Nil
      case FieldAccessF(_, _, _)                                 => Nil
      case EnumAccessF(_, _, _)                                  => Nil
      case IndexF(_, _, _)                                       => Nil
      case CallF(_, _, _)                                        => Nil
      case PrimeF(_, _)                                          => Nil
      case PreF(_, _)                                            => Nil
      case WithF(_, _, _)                                        => Nil
      case IfF(_, _, _, _)                                       => Nil
      case LetF(_, _, _, _)                                      => Nil
      case LambdaF(_, _, _)                                      => Nil
      case ConstructorF(_, _, _)                                 => Nil
      case SetLiteralF(_, _)                                     => Nil
      case MapLiteralF(_, _)                                     => Nil
      case SetComprehensionF(_, _, _, _)                         => Nil
      case SeqLiteralF(_, _)                                     => Nil
      case MatchesF(_, _, _)                                     => Nil
      case IntLitF(_, _)                                         => Nil
      case FloatLitF(_, _)                                       => Nil
      case StringLitF(_, _)                                      => Nil
      case BoolLitF(_, _)                                        => Nil
      case NoneLitF(_)                                           => Nil
      case IdentifierF(_, _)                                     => Nil
    }

  def deletePatternOf(stateFields: List[String], e: expr): List[String] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)                                    => Nil
      case BinaryOpF(BOr(), _, _, _)                                     => Nil
      case BinaryOpF(BImplies(), _, _, _)                                => Nil
      case BinaryOpF(BIff(), _, _, _)                                    => Nil
      case BinaryOpF(BEq(), _, _, _)                                     => Nil
      case BinaryOpF(BNeq(), _, _, _)                                    => Nil
      case BinaryOpF(BLt(), _, _, _)                                     => Nil
      case BinaryOpF(BGt(), _, _, _)                                     => Nil
      case BinaryOpF(BLe(), _, _, _)                                     => Nil
      case BinaryOpF(BGe(), _, _, _)                                     => Nil
      case BinaryOpF(BIn(), _, _, _)                                     => Nil
      case BinaryOpF(BNotIn(), _, BinaryOpF(_, _, _, _), _)              => Nil
      case BinaryOpF(BNotIn(), _, UnaryOpF(_, _, _), _)                  => Nil
      case BinaryOpF(BNotIn(), _, QuantifierF(_, _, _, _), _)            => Nil
      case BinaryOpF(BNotIn(), _, SomeWrapF(_, _), _)                    => Nil
      case BinaryOpF(BNotIn(), _, TheF(_, _, _, _), _)                   => Nil
      case BinaryOpF(BNotIn(), _, FieldAccessF(_, _, _), _)              => Nil
      case BinaryOpF(BNotIn(), _, EnumAccessF(_, _, _), _)               => Nil
      case BinaryOpF(BNotIn(), _, IndexF(_, _, _), _)                    => Nil
      case BinaryOpF(BNotIn(), _, CallF(_, _, _), _)                     => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(BinaryOpF(_, _, _, _), _), _)   => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(UnaryOpF(_, _, _), _), _)       => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(QuantifierF(_, _, _, _), _), _) => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(SomeWrapF(_, _), _), _)         => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(TheF(_, _, _, _), _), _)        => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(FieldAccessF(_, _, _), _), _)   => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(EnumAccessF(_, _, _), _), _)    => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(IndexF(_, _, _), _), _)         => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(CallF(_, _, _), _), _)          => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(PrimeF(_, _), _), _)            => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(PreF(_, _), _), _)              => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(WithF(_, _, _), _), _)          => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(IfF(_, _, _, _), _), _)         => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(LetF(_, _, _, _), _), _)        => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(LambdaF(_, _, _), _), _)        => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(ConstructorF(_, _, _), _), _)   => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(SetLiteralF(_, _), _), _)       => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(MapLiteralF(_, _), _), _)       => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(SetComprehensionF(_, _, _, _), _), _) =>
        Nil
      case BinaryOpF(BNotIn(), _, PrimeF(SeqLiteralF(_, _), _), _) => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(MatchesF(_, _, _), _), _) => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(IntLitF(_, _), _), _)     => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(FloatLitF(_, _), _), _)   => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(StringLitF(_, _), _), _)  => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(BoolLitF(_, _), _), _)    => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(NoneLitF(_), _), _)       => Nil
      case BinaryOpF(BNotIn(), _, PrimeF(IdentifierF(n, _), _), _) =>
        string_in_list(n, stateFields) match {
          case true  => List(n)
          case false => Nil
        }
      case BinaryOpF(BNotIn(), _, PreF(_, _), _)                    => Nil
      case BinaryOpF(BNotIn(), _, WithF(_, _, _), _)                => Nil
      case BinaryOpF(BNotIn(), _, IfF(_, _, _, _), _)               => Nil
      case BinaryOpF(BNotIn(), _, LetF(_, _, _, _), _)              => Nil
      case BinaryOpF(BNotIn(), _, LambdaF(_, _, _), _)              => Nil
      case BinaryOpF(BNotIn(), _, ConstructorF(_, _, _), _)         => Nil
      case BinaryOpF(BNotIn(), _, SetLiteralF(_, _), _)             => Nil
      case BinaryOpF(BNotIn(), _, MapLiteralF(_, _), _)             => Nil
      case BinaryOpF(BNotIn(), _, SetComprehensionF(_, _, _, _), _) => Nil
      case BinaryOpF(BNotIn(), _, SeqLiteralF(_, _), _)             => Nil
      case BinaryOpF(BNotIn(), _, MatchesF(_, _, _), _)             => Nil
      case BinaryOpF(BNotIn(), _, IntLitF(_, _), _)                 => Nil
      case BinaryOpF(BNotIn(), _, FloatLitF(_, _), _)               => Nil
      case BinaryOpF(BNotIn(), _, StringLitF(_, _), _)              => Nil
      case BinaryOpF(BNotIn(), _, BoolLitF(_, _), _)                => Nil
      case BinaryOpF(BNotIn(), _, NoneLitF(_), _)                   => Nil
      case BinaryOpF(BNotIn(), _, IdentifierF(_, _), _)             => Nil
      case BinaryOpF(BSubset(), _, _, _)                            => Nil
      case BinaryOpF(BUnion(), _, _, _)                             => Nil
      case BinaryOpF(BIntersect(), _, _, _)                         => Nil
      case BinaryOpF(BDiff(), _, _, _)                              => Nil
      case BinaryOpF(BAdd(), _, _, _)                               => Nil
      case BinaryOpF(BSub(), _, _, _)                               => Nil
      case BinaryOpF(BMul(), _, _, _)                               => Nil
      case BinaryOpF(BDiv(), _, _, _)                               => Nil
      case UnaryOpF(_, _, _)                                        => Nil
      case QuantifierF(_, _, _, _)                                  => Nil
      case SomeWrapF(_, _)                                          => Nil
      case TheF(_, _, _, _)                                         => Nil
      case FieldAccessF(_, _, _)                                    => Nil
      case EnumAccessF(_, _, _)                                     => Nil
      case IndexF(_, _, _)                                          => Nil
      case CallF(_, _, _)                                           => Nil
      case PrimeF(_, _)                                             => Nil
      case PreF(_, _)                                               => Nil
      case WithF(_, _, _)                                           => Nil
      case IfF(_, _, _, _)                                          => Nil
      case LetF(_, _, _, _)                                         => Nil
      case LambdaF(_, _, _)                                         => Nil
      case ConstructorF(_, _, _)                                    => Nil
      case SetLiteralF(_, _)                                        => Nil
      case MapLiteralF(_, _)                                        => Nil
      case SetComprehensionF(_, _, _, _)                            => Nil
      case SeqLiteralF(_, _)                                        => Nil
      case MatchesF(_, _, _)                                        => Nil
      case IntLitF(_, _)                                            => Nil
      case FloatLitF(_, _)                                          => Nil
      case StringLitF(_, _)                                         => Nil
      case BoolLitF(_, _)                                           => Nil
      case NoneLitF(_)                                              => Nil
      case IdentifierF(_, _)                                        => Nil
    }

  def exprSelfNames(x0: expr): List[String] = x0 match {
    case IdentifierF(n, uu)               => List(n)
    case ConstructorF(n, uv, uw)          => List(n)
    case BinaryOpF(v, va, vb, vc)         => Nil
    case UnaryOpF(v, va, vb)              => Nil
    case QuantifierF(v, va, vb, vc)       => Nil
    case SomeWrapF(v, va)                 => Nil
    case TheF(v, va, vb, vc)              => Nil
    case FieldAccessF(v, va, vb)          => Nil
    case EnumAccessF(v, va, vb)           => Nil
    case IndexF(v, va, vb)                => Nil
    case CallF(v, va, vb)                 => Nil
    case PrimeF(v, va)                    => Nil
    case PreF(v, va)                      => Nil
    case WithF(v, va, vb)                 => Nil
    case IfF(v, va, vb, vc)               => Nil
    case LetF(v, va, vb, vc)              => Nil
    case LambdaF(v, va, vb)               => Nil
    case SetLiteralF(v, va)               => Nil
    case MapLiteralF(v, va)               => Nil
    case SetComprehensionF(v, va, vb, vc) => Nil
    case SeqLiteralF(v, va)               => Nil
    case MatchesF(v, va, vb)              => Nil
    case IntLitF(v, va)                   => Nil
    case FloatLitF(v, va)                 => Nil
    case StringLitF(v, va)                => Nil
    case BoolLitF(v, va)                  => Nil
    case NoneLitF(v)                      => Nil
  }

  def literalDropRight(n: nat, s: String): String = {
    val xs = Str_Literal.asciisOfLiteral(s): List[BigInt];
    Str_Literal.literalOfAsciis(take[BigInt](minus_nat(size_list[BigInt](xs), n), xs))
  }

  def triggerFunctionName(x0: trigger_spec): String = x0 match {
    case TriggerSpec(uu, fn, uv, uw, ux, uy, uz, va) => fn
  }

  def triggerSourceColumn(x0: trigger_spec): Option[String] = x0 match {
    case TriggerSpec(uu, uv, uw, ux, uy, uz, va, sc) => sc
  }

  def triggerTargetColumn(x0: trigger_spec): String = x0 match {
    case TriggerSpec(uu, uv, uw, tc, ux, uy, uz, va) => tc
  }

  def tc_relations_update[A](
      tc_relationsa: (List[state_field_decl]) => List[state_field_decl],
      x1: tyctx_ext[A]
  ): tyctx_ext[A] =
    (tc_relationsa, x1) match {
      case (
            tc_relationsa,
            tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more)
          ) =>
        tyctx_exta[A](tc_env, tc_schema, tc_entities, tc_relationsa(tc_relations), tc_enums, more)
    }

  def tc_entities_update[A](
      tc_entitiesa: (List[entity_decl]) => List[entity_decl],
      x1: tyctx_ext[A]
  ): tyctx_ext[A] =
    (tc_entitiesa, x1) match {
      case (
            tc_entitiesa,
            tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more)
          ) =>
        tyctx_exta[A](tc_env, tc_schema, tc_entitiesa(tc_entities), tc_relations, tc_enums, more)
    }

  def tc_enums_update[A](
      tc_enumsa: (List[String]) => List[String],
      x1: tyctx_ext[A]
  ): tyctx_ext[A] =
    (tc_enumsa, x1) match {
      case (tc_enumsa, tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more)) =>
        tyctx_exta[A](tc_env, tc_schema, tc_entities, tc_relations, tc_enumsa(tc_enums), more)
    }

  def serviceStateFields(x0: service_ir): List[state_field_decl] = x0 match {
    case ServiceIRFull(uu, uv, uw, ux, uy, st, uz, va, vb, vc, vd, ve, vf, vg, vh, vi) => st match {
        case None                       => Nil
        case Some(StateDeclFull(fs, _)) => fs
      }
  }

  def tyctxFromService(ir: service_ir): tyctx_ext[Unit] =
    tc_relations_update[Unit](
      (_: List[state_field_decl]) =>
        serviceStateFields(ir),
      tc_enums_update[Unit](
        (_: List[String]) =>
          map[enum_decl, String]((a: enum_decl) => enumNameFull(a), serviceEnums(ir)),
        tc_entities_update[Unit](
          (_: List[entity_decl]) =>
            serviceEntities(ir),
          tyctxEmpty
        )
      )
    )

  def quantBindingIsIn(x0: quantifier_binding): Boolean = x0 match {
    case QuantifierBindingFull(uu, uv, BkIn(), uw)   => true
    case QuantifierBindingFull(v, va, BkColon(), vc) => false
  }

  def quantifierAllIn(bs: List[quantifier_binding]): Boolean =
    list_all[quantifier_binding](
      (a: quantifier_binding) =>
        quantBindingIsIn(a),
      bs
    )

  def foldTrust(enums: List[String], exprs: List[expr]): trust_level =
    list_all[expr]((e: expr) => !is_none[smt_term](translate(enums, e)), exprs) match {
      case true  => TlSound()
      case false => TlBestEffort()
    }

  def mapAlloyPrimitive(name: String): String =
    name == "Int" match {
      case true => "Int"
      case false => name == "Bool" match {
          case true => "Bool"
          case false => name == "String" match {
              case true  => "String"
              case false => name
            }
        }
    }

  def typeToSigNameAlloy(x0: type_expr): Option[String] = x0 match {
    case NamedTypeF(name, uu)         => Some[String](mapAlloyPrimitive(name))
    case SetTypeF(v, va)              => None
    case MapTypeF(v, va, vb)          => None
    case SeqTypeF(v, va)              => None
    case OptionTypeF(v, va)           => None
    case RelationTypeF(v, va, vb, vc) => None
  }

  def alloyFieldTypeOf(x0: type_expr): Option[(alloy_field_multiplicity, String)] =
    x0 match {
      case NamedTypeF(name, uu) =>
        Some[(alloy_field_multiplicity, String)]((AfmOne(), mapAlloyPrimitive(name)))
      case SetTypeF(inner, uv) =>
        typeToSigNameAlloy(inner) match {
          case None    => None
          case Some(n) => Some[(alloy_field_multiplicity, String)]((AfmSet(), n))
        }
      case OptionTypeF(inner, uw) =>
        typeToSigNameAlloy(inner) match {
          case None    => None
          case Some(n) => Some[(alloy_field_multiplicity, String)]((AfmLone(), n))
        }
      case MapTypeF(v, va, vb)          => None
      case SeqTypeF(v, va)              => None
      case RelationTypeF(v, va, vb, vc) => None
    }

  def classificationKind(x0: operation_classification): operation_kind = x0 match {
    case OperationClassification(uu, k, uv, uw, ux, uy, uz) => k
  }

  def equal_extern_kind(x0: extern_kind, x1: extern_kind): Boolean = (x0, x1) match {
    case (EkPredicate(), EkIntFunction())   => false
    case (EkIntFunction(), EkPredicate())   => false
    case (EkIntFunction(), EkIntFunction()) => true
    case (EkPredicate(), EkPredicate())     => true
  }

  def mergeExternInfo(x0: extern_info, arity: BigInt, kind: extern_kind): extern_info =
    (x0, arity, kind) match {
      case (ExInfo(prevKind, prevArity), arity, kind) =>
        ExInfo(
          equal_extern_kind(prevKind, EkIntFunction()) ||
            equal_extern_kind(kind, EkIntFunction()) match {
            case true  => EkIntFunction()
            case false => EkPredicate()
          },
          max[BigInt](prevArity, arity)
        )
    }

  def upsertExtern(
      x0: List[(String, extern_info)],
      name: String,
      arity: BigInt,
      kind: extern_kind
  ): List[(String, extern_info)] =
    (x0, name, arity, kind) match {
      case (Nil, name, arity, kind) => List((name, ExInfo(kind, arity)))
      case ((n, info) :: rest, name, arity, kind) =>
        n == name match {
          case true  => (n, mergeExternInfo(info, arity, kind)) :: rest
          case false => (n, info) :: upsertExtern(rest, name, arity, kind)
        }
    }

  def cvrValue(x0: convention_rule): convention_value = x0 match {
    case ConventionRuleFull(x1, x2, x3, x4, x5) => x4
  }

  def cvdSpan(x0: conventions_decl): Option[span_t] = x0 match {
    case ConventionsDeclFull(x1, x2) => x2
  }

  def fncRetType(x0: function_decl): type_expr = x0 match {
    case FunctionDeclFull(x1, x2, x3, x4, x5) => x3
  }

  def svcInvariants(x0: service_ir): List[invariant_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x9
  }

  def svcOperations(x0: service_ir): List[operation_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x7
  }

  def svcPredicates(x0: service_ir): List[predicate_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x13
  }

  def stfName(x0: state_field_decl): String = x0 match {
    case StateFieldDeclFull(x1, x2, x3) => x1
  }

  def stfSpan(x0: state_field_decl): Option[span_t] = x0 match {
    case StateFieldDeclFull(x1, x2, x3) => x3
  }

  def stfType(x0: state_field_decl): type_expr = x0 match {
    case StateFieldDeclFull(x1, x2, x3) => x2
  }

  def trnField(x0: transition_decl): String = x0 match {
    case TransitionDeclFull(x1, x2, x3, x4, x5) => x3
  }

  def trnRules(x0: transition_decl): List[transition_rule] = x0 match {
    case TransitionDeclFull(x1, x2, x3, x4, x5) => x4
  }

  def trlGuard(x0: transition_rule): Option[expr] = x0 match {
    case TransitionRuleFull(x1, x2, x3, x4, x5) => x4
  }

  def isKeyExistsConj(c: expr, inputName: String, stateName: String): Boolean =
    c match {
      case BinaryOpF(op, l, r, _) =>
        (op match {
          case BAnd()       => false
          case BOr()        => false
          case BImplies()   => false
          case BIff()       => false
          case BEq()        => false
          case BNeq()       => false
          case BLt()        => false
          case BGt()        => false
          case BLe()        => false
          case BGe()        => false
          case BIn()        => true
          case BNotIn()     => false
          case BSubset()    => false
          case BUnion()     => false
          case BIntersect() => false
          case BDiff()      => false
          case BAdd()       => false
          case BSub()       => false
          case BMul()       => false
          case BDiv()       => false
        }) &&
        ((l match {
          case BinaryOpF(_, _, _, _)         => false
          case UnaryOpF(_, _, _)             => false
          case QuantifierF(_, _, _, _)       => false
          case SomeWrapF(_, _)               => false
          case TheF(_, _, _, _)              => false
          case FieldAccessF(_, _, _)         => false
          case EnumAccessF(_, _, _)          => false
          case IndexF(_, _, _)               => false
          case CallF(_, _, _)                => false
          case PrimeF(_, _)                  => false
          case PreF(_, _)                    => false
          case WithF(_, _, _)                => false
          case IfF(_, _, _, _)               => false
          case LetF(_, _, _, _)              => false
          case LambdaF(_, _, _)              => false
          case ConstructorF(_, _, _)         => false
          case SetLiteralF(_, _)             => false
          case MapLiteralF(_, _)             => false
          case SetComprehensionF(_, _, _, _) => false
          case SeqLiteralF(_, _)             => false
          case MatchesF(_, _, _)             => false
          case IntLitF(_, _)                 => false
          case FloatLitF(_, _)               => false
          case StringLitF(_, _)              => false
          case BoolLitF(_, _)                => false
          case NoneLitF(_)                   => false
          case IdentifierF(i, _)             => i == inputName
        }) &&
          (r match {
            case BinaryOpF(_, _, _, _)         => false
            case UnaryOpF(_, _, _)             => false
            case QuantifierF(_, _, _, _)       => false
            case SomeWrapF(_, _)               => false
            case TheF(_, _, _, _)              => false
            case FieldAccessF(_, _, _)         => false
            case EnumAccessF(_, _, _)          => false
            case IndexF(_, _, _)               => false
            case CallF(_, _, _)                => false
            case PrimeF(_, _)                  => false
            case PreF(_, _)                    => false
            case WithF(_, _, _)                => false
            case IfF(_, _, _, _)               => false
            case LetF(_, _, _, _)              => false
            case LambdaF(_, _, _)              => false
            case ConstructorF(_, _, _)         => false
            case SetLiteralF(_, _)             => false
            case MapLiteralF(_, _)             => false
            case SetComprehensionF(_, _, _, _) => false
            case SeqLiteralF(_, _)             => false
            case MatchesF(_, _, _)             => false
            case IntLitF(_, _)                 => false
            case FloatLitF(_, _)               => false
            case StringLitF(_, _)              => false
            case BoolLitF(_, _)                => false
            case NoneLitF(_)                   => false
            case IdentifierF(s, _)             => s == stateName
          }))
      case UnaryOpF(_, _, _)             => false
      case QuantifierF(_, _, _, _)       => false
      case SomeWrapF(_, _)               => false
      case TheF(_, _, _, _)              => false
      case FieldAccessF(_, _, _)         => false
      case EnumAccessF(_, _, _)          => false
      case IndexF(_, _, _)               => false
      case CallF(_, _, _)                => false
      case PrimeF(_, _)                  => false
      case PreF(_, _)                    => false
      case WithF(_, _, _)                => false
      case IfF(_, _, _, _)               => false
      case LetF(_, _, _, _)              => false
      case LambdaF(_, _, _)              => false
      case ConstructorF(_, _, _)         => false
      case SetLiteralF(_, _)             => false
      case MapLiteralF(_, _)             => false
      case SetComprehensionF(_, _, _, _) => false
      case SeqLiteralF(_, _)             => false
      case MatchesF(_, _, _)             => false
      case IntLitF(_, _)                 => false
      case FloatLitF(_, _)               => false
      case StringLitF(_, _)              => false
      case BoolLitF(_, _)                => false
      case NoneLitF(_)                   => false
      case IdentifierF(_, _)             => false
    }

  def entityFieldNames(es: List[entity_decl], ename: String): List[String] =
    entityByName(es, ename) match {
      case None => Nil
      case Some(ed) =>
        map[field_decl, String]((a: field_decl) => fieldNameFull(a), entityFieldsFull(ed))
    }

  def entityNameInList(es: List[entity_decl], nm: String): Option[String] =
    entityByName(es, nm) match {
      case None    => None
      case Some(_) => Some[String](nm)
    }

  def isCollectionType(x0: type_expr): Boolean = x0 match {
    case SetTypeF(uu, uv)              => true
    case SeqTypeF(uw, ux)              => true
    case MapTypeF(uy, uz, va)          => true
    case RelationTypeF(vb, vc, vd, ve) => true
    case NamedTypeF(v, va)             => false
    case OptionTypeF(v, va)            => false
  }

  def freshKeyAux(fuel: nat, base: String, uu: List[String], i: nat): String =
    equal_nat(fuel, zero_nat) match {
      case true => base + "_" + showNat(i)
      case false =>
        val candidate = base + "_" + showNat(i): String;
        membera[String](uu, candidate) match {
          case true  => freshKeyAux(minus_nat(fuel, one_nat), base, uu, Suc(i))
          case false => candidate
        }
    }

  def freshKey(base: String, seen: List[String]): String =
    freshKeyAux(Suc(size_list[String](seen)), base, seen, zero_nat)

  def sensitiveSuffixNames: List[String] =
    List("_hash", "_secret", "_password", "_api_key", "_token")

  def sensitiveExactNames: List[String] =
    List("password", "password_hash", "secret", "token", "api_key")

  def isSensitiveField(name: String): Boolean =
    string_in_list(name, sensitiveExactNames) ||
      list_ex[String]((sfx: String) => literalEndsWith(sfx, name), sensitiveSuffixNames)

  def dropSensitive(x0: List[(String, (schema_object, Boolean))])
      : List[(String, (schema_object, Boolean))] =
    x0 match {
      case Nil => Nil
      case (n, (s, nu)) :: rest =>
        isSensitiveField(n) match {
          case true  => dropSensitive(rest)
          case false => (n, (s, nu)) :: dropSensitive(rest)
        }
    }

  def openApiSchemaFuel(am: List[(String, type_alias_decl)]): nat =
    plus_nat(size_list[(String, type_alias_decl)](am), nat_of_integer(BigInt(100)))

  def stripOptions(x0: type_expr): type_expr = x0 match {
    case OptionTypeF(inner, uu)       => stripOptions(inner)
    case NamedTypeF(v, va)            => NamedTypeF(v, va)
    case SetTypeF(v, va)              => SetTypeF(v, va)
    case MapTypeF(v, va, vb)          => MapTypeF(v, va, vb)
    case SeqTypeF(v, va)              => SeqTypeF(v, va)
    case RelationTypeF(v, va, vb, vc) => RelationTypeF(v, va, vb, vc)
  }

  def findEnumValuesInTypeAux(
      fuel: nat,
      uu: type_expr,
      uv: List[(String, type_alias_decl)],
      uw: List[(String, enum_decl)],
      ux: List[String]
  ): Option[List[String]] =
    equal_nat(fuel, zero_nat) match {
      case true => None
      case false => stripOptions(uu) match {
          case NamedTypeF(name, _) =>
            map_of[String, enum_decl](uw, name) match {
              case None =>
                membera[String](ux, name) match {
                  case true => None
                  case false => map_of[String, type_alias_decl](uv, name) match {
                      case None => None
                      case Some(TypeAliasDeclFull(_, base, _, _)) =>
                        findEnumValuesInTypeAux(minus_nat(fuel, one_nat), base, uv, uw, name :: ux)
                    }
                }
              case Some(EnumDeclFull(_, vs, _)) =>
                Some[List[String]](vs)
            }
          case SetTypeF(_, _)            => None
          case MapTypeF(_, _, _)         => None
          case SeqTypeF(_, _)            => None
          case OptionTypeF(_, _)         => None
          case RelationTypeF(_, _, _, _) => None
        }
    }

  def findEnumValuesInType(
      ty: type_expr,
      am: List[(String, type_alias_decl)],
      em: List[(String, enum_decl)]
  ): Option[List[String]] =
    findEnumValuesInTypeAux(Suc(size_list[(String, type_alias_decl)](am)), ty, am, em, Nil)

  def openapiPrimitiveOf(nm: String): Option[openapi_primitive_def] =
    nm == "String" match {
      case true => Some[openapi_primitive_def](OpenApiPrimDef(List("string"), None))
      case false => nm == "Int" match {
          case true => Some[openapi_primitive_def](OpenApiPrimDef(List("integer"), None))
          case false => nm == "Float" match {
              case true => Some[openapi_primitive_def](OpenApiPrimDef(List("number"), None))
              case false => nm == "Bool" match {
                  case true => Some[openapi_primitive_def](OpenApiPrimDef(List("boolean"), None))
                  case false => nm == "Boolean" match {
                      case true =>
                        Some[openapi_primitive_def](OpenApiPrimDef(List("boolean"), None))
                      case false => nm == "DateTime" match {
                          case true => Some[openapi_primitive_def](OpenApiPrimDef(
                              List("string"),
                              Some[String]("date-time")
                            ))
                          case false => nm == "Date" match {
                              case true => Some[openapi_primitive_def](OpenApiPrimDef(
                                  List("string"),
                                  Some[String]("date")
                                ))
                              case false => nm == "UUID" match {
                                  case true => Some[openapi_primitive_def](OpenApiPrimDef(
                                      List("string"),
                                      Some[String]("uuid")
                                    ))
                                  case false => nm == "Decimal" match {
                                      case true => Some[openapi_primitive_def](OpenApiPrimDef(
                                          List("string"),
                                          Some[String]("decimal")
                                        ))
                                      case false => nm == "Bytes" match {
                                          case true => Some[openapi_primitive_def](OpenApiPrimDef(
                                              List("string"),
                                              Some[String]("byte")
                                            ))
                                          case false => nm == "Money" match {
                                              case true =>
                                                Some[openapi_primitive_def](OpenApiPrimDef(
                                                  List("integer"),
                                                  None
                                                ))
                                              case false => None
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

  def classifyOpenApiNamedTypeAux(
      fuel: nat,
      uu: String,
      uv: List[(String, type_alias_decl)],
      uw: List[(String, enum_decl)],
      ux: List[String],
      uy: List[String]
  ): openapi_named_kind =
    equal_nat(fuel, zero_nat) match {
      case true => OntUnknown()
      case false => openapiPrimitiveOf(uu) match {
          case None =>
            map_of[String, enum_decl](uw, uu) match {
              case None =>
                membera[String](ux, uu) match {
                  case true => OntEntityRef(uu)
                  case false => membera[String](uy, uu) match {
                      case true => OntUnknown()
                      case false => map_of[String, type_alias_decl](uv, uu) match {
                          case None => OntUnknown()
                          case Some(TypeAliasDeclFull(_, base, _, _)) =>
                            base match {
                              case NamedTypeF(n, _) =>
                                classifyOpenApiNamedTypeAux(
                                  minus_nat(fuel, one_nat),
                                  n,
                                  uv,
                                  uw,
                                  ux,
                                  uu :: uy
                                )
                              case SetTypeF(_, _)    => OntAliasToType(base)
                              case MapTypeF(_, _, _) => OntAliasToType(base)
                              case SeqTypeF(_, _)    => OntAliasToType(base)
                              case OptionTypeF(_, _) => OntAliasToType(base)
                              case RelationTypeF(_, _, _, _) =>
                                OntAliasToType(base)
                            }
                        }
                    }
                }
              case Some(EnumDeclFull(_, vs, _)) => OntEnum(vs)
            }
          case Some(a) => OntPrimitive(a)
        }
    }

  def classifyOpenApiNamedType(
      name: String,
      am: List[(String, type_alias_decl)],
      em: List[(String, enum_decl)],
      entityNames: List[String]
  ): openapi_named_kind =
    classifyOpenApiNamedTypeAux(
      Suc(size_list[(String, type_alias_decl)](am)),
      name,
      am,
      em,
      entityNames,
      Nil
    )

  def mergeConstraintsLifted(
      x0: schema_object,
      x1: openapi_bounds,
      enumOpt: Option[List[String]]
  ): schema_object =
    (x0, x1, enumOpt) match {
      case (
            SchemaObject(
              ty,
              fmt,
              bMinL,
              bMaxL,
              bMn,
              bMx,
              bEmn,
              bEmx,
              mnI,
              mxI,
              bPat,
              bEn,
              it,
              rf,
              rq,
              pr,
              ap,
              aof,
              desc,
              inE
            ),
            OpenApiBounds(cMinL, cMaxL, cMn, cMx, cEmn, cEmx, cPat),
            enumOpt
          ) => SchemaObject(
          ty,
          fmt,
          cMinL match {
            case None    => bMinL
            case Some(_) => cMinL
          },
          cMaxL match {
            case None    => bMaxL
            case Some(_) => cMaxL
          },
          cMn match {
            case None    => bMn
            case Some(_) => cMn
          },
          cMx match {
            case None    => bMx
            case Some(_) => cMx
          },
          cEmn match {
            case None    => bEmn
            case Some(_) => cEmn
          },
          cEmx match {
            case None    => bEmx
            case Some(_) => cEmx
          },
          mnI,
          mxI,
          cPat match {
            case None    => bPat
            case Some(_) => cPat
          },
          enumOpt match {
            case None    => bEn
            case Some(_) => enumOpt
          },
          it,
          rf,
          rq,
          pr,
          ap,
          aof,
          desc,
          inE
        )
    }

  def primitiveDefToSchema(x0: openapi_primitive_def): schema_object = x0 match {
    case OpenApiPrimDef(types, fmt) =>
      SchemaObject(
        Some[List[String]](types),
        fmt,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        false
      )
  }

  def emptySchemaObject: schema_object =
    SchemaObject(
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      false
    )

  def enumStringSchema(values: List[String]): schema_object =
    SchemaObject(
      Some[List[String]](List("string")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Some[List[String]](values),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      false
    )

  def mapObjectSchema(ap: schema_object): schema_object =
    SchemaObject(
      Some[List[String]](List("object")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Some[schema_object_or_bool](SOBSchema(ap)),
      None,
      None,
      false
    )

  def entityRefSchema(name: String): schema_object =
    SchemaObject(
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Some[String]("#/components/schemas/" + name + "Read"),
      None,
      None,
      None,
      None,
      None,
      false
    )

  def boundsMinLength(x0: openapi_bounds): Option[BigInt] = x0 match {
    case OpenApiBounds(mnL, uu, uv, uw, ux, uy, uz) => mnL
  }

  def boundsMaxLength(x0: openapi_bounds): Option[BigInt] = x0 match {
    case OpenApiBounds(uu, mxL, uv, uw, ux, uy, uz) => mxL
  }

  def integerSchema: schema_object =
    SchemaObject(
      Some[List[String]](List("integer")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      false
    )

  def withExclusiveMinimum(v: Option[decimal_lit], x1: openapi_bounds): openapi_bounds =
    (v, x1) match {
      case (v, OpenApiBounds(nl, ml, mn, mx, uu, emx, p)) =>
        OpenApiBounds(nl, ml, mn, mx, v, emx, p)
    }

  def withExclusiveMaximum(v: Option[decimal_lit], x1: openapi_bounds): openapi_bounds =
    (v, x1) match {
      case (v, OpenApiBounds(nl, ml, mn, mx, emn, uu, p)) =>
        OpenApiBounds(nl, ml, mn, mx, emn, v, p)
    }

  def exclusiveMinimumOf(x0: openapi_bounds): Option[decimal_lit] = x0 match {
    case OpenApiBounds(uu, uv, uw, ux, emn, uy, uz) => emn
  }

  def exclusiveMaximumOf(x0: openapi_bounds): Option[decimal_lit] = x0 match {
    case OpenApiBounds(uu, uv, uw, ux, uy, emx, uz) => emx
  }

  def decimalGt(x0: decimal_lit, x1: decimal_lit): Boolean = (x0, x1) match {
    case (DecimalLit(m1, e1), DecimalLit(m2, e2)) =>
      less_eq_int(e1, e2) match {
        case true =>
          less_int(times_inta(m2, power[BigInt](BigInt(10), nat.apply(minus_int(e2, e1)))), m1)
        case false =>
          less_int(m2, times_inta(m1, power[BigInt](BigInt(10), nat.apply(minus_int(e1, e2)))))
      }
  }

  def maxDecimal(a: decimal_lit, b: decimal_lit): decimal_lit =
    decimalGt(a, b) match {
      case true  => a
      case false => b
    }

  def tightenDecMin(cur: Option[decimal_lit], d: decimal_lit): Option[decimal_lit] =
    cur match {
      case None    => Some[decimal_lit](d)
      case Some(x) => Some[decimal_lit](maxDecimal(x, d))
    }

  def minDecimal(a: decimal_lit, b: decimal_lit): decimal_lit =
    decimalGt(a, b) match {
      case true  => b
      case false => a
    }

  def tightenDecMax(cur: Option[decimal_lit], d: decimal_lit): Option[decimal_lit] =
    cur match {
      case None    => Some[decimal_lit](d)
      case Some(x) => Some[decimal_lit](minDecimal(x, d))
    }

  def withMinimum(v: Option[decimal_lit], x1: openapi_bounds): openapi_bounds =
    (v, x1) match {
      case (v, OpenApiBounds(nl, ml, uu, mx, emn, emx, p)) =>
        OpenApiBounds(nl, ml, v, mx, emn, emx, p)
    }

  def withMaximum(v: Option[decimal_lit], x1: openapi_bounds): openapi_bounds =
    (v, x1) match {
      case (v, OpenApiBounds(nl, ml, mn, uu, emn, emx, p)) =>
        OpenApiBounds(nl, ml, mn, v, emn, emx, p)
    }

  def minimumOf(x0: openapi_bounds): Option[decimal_lit] = x0 match {
    case OpenApiBounds(uu, uv, mn, uw, ux, uy, uz) => mn
  }

  def maximumOf(x0: openapi_bounds): Option[decimal_lit] = x0 match {
    case OpenApiBounds(uu, uv, uw, mx, ux, uy, uz) => mx
  }

  def applyNumericBoundOpenApi(op: bin_op, d: decimal_lit, bounds: openapi_bounds): openapi_bounds =
    op match {
      case BAnd()     => bounds
      case BOr()      => bounds
      case BImplies() => bounds
      case BIff()     => bounds
      case BEq() =>
        withMaximum(
          tightenDecMax(maximumOf(bounds), d),
          withMinimum(tightenDecMin(minimumOf(bounds), d), bounds)
        )
      case BNeq() => bounds
      case BLt() =>
        withExclusiveMaximum(tightenDecMax(exclusiveMaximumOf(bounds), d), bounds)
      case BGt() =>
        withExclusiveMinimum(tightenDecMin(exclusiveMinimumOf(bounds), d), bounds)
      case BLe()        => withMaximum(tightenDecMax(maximumOf(bounds), d), bounds)
      case BGe()        => withMinimum(tightenDecMin(minimumOf(bounds), d), bounds)
      case BIn()        => bounds
      case BNotIn()     => bounds
      case BSubset()    => bounds
      case BUnion()     => bounds
      case BIntersect() => bounds
      case BDiff()      => bounds
      case BAdd()       => bounds
      case BSub()       => bounds
      case BMul()       => bounds
      case BDiv()       => bounds
    }

  def withMinLength(v: Option[BigInt], x1: openapi_bounds): openapi_bounds =
    (v, x1) match {
      case (v, OpenApiBounds(uu, ml, mn, mx, emn, emx, p)) =>
        OpenApiBounds(v, ml, mn, mx, emn, emx, p)
    }

  def withMaxLength(v: Option[BigInt], x1: openapi_bounds): openapi_bounds =
    (v, x1) match {
      case (v, OpenApiBounds(nl, uu, mn, mx, emn, emx, p)) =>
        OpenApiBounds(nl, v, mn, mx, emn, emx, p)
    }

  def tightenIntMin(cur: Option[BigInt], n: BigInt): Option[BigInt] =
    cur match {
      case None    => Some[BigInt](n)
      case Some(x) => Some[BigInt](max[BigInt](x, n))
    }

  def tightenIntMax(cur: Option[BigInt], n: BigInt): Option[BigInt] =
    cur match {
      case None    => Some[BigInt](n)
      case Some(x) => Some[BigInt](min[BigInt](x, n))
    }

  def minLengthOf(x0: openapi_bounds): Option[BigInt] = x0 match {
    case OpenApiBounds(nl, uu, uv, uw, ux, uy, uz) => nl
  }

  def maxLengthOf(x0: openapi_bounds): Option[BigInt] = x0 match {
    case OpenApiBounds(uu, ml, uv, uw, ux, uy, uz) => ml
  }

  def applyLengthBoundOpenApi(op: bin_op, n: BigInt, bounds: openapi_bounds): openapi_bounds =
    less_int(n, zero_int) match {
      case true => bounds
      case false => op match {
          case BAnd()     => bounds
          case BOr()      => bounds
          case BImplies() => bounds
          case BIff()     => bounds
          case BEq() =>
            withMaxLength(
              tightenIntMax(maxLengthOf(bounds), n),
              withMinLength(tightenIntMin(minLengthOf(bounds), n), bounds)
            )
          case BNeq() => bounds
          case BLt() =>
            less_int(minus_int(n, one_inta), zero_int) match {
              case true => bounds
              case false =>
                withMaxLength(tightenIntMax(maxLengthOf(bounds), minus_int(n, one_inta)), bounds)
            }
          case BGt() =>
            withMinLength(tightenIntMin(minLengthOf(bounds), plus_int(n, one_inta)), bounds)
          case BLe() =>
            withMaxLength(tightenIntMax(maxLengthOf(bounds), n), bounds)
          case BGe() =>
            withMinLength(tightenIntMin(minLengthOf(bounds), n), bounds)
          case BIn()        => bounds
          case BNotIn()     => bounds
          case BSubset()    => bounds
          case BUnion()     => bounds
          case BIntersect() => bounds
          case BDiff()      => bounds
          case BAdd()       => bounds
          case BSub()       => bounds
          case BMul()       => bounds
          case BDiv()       => bounds
        }
    }

  def modulo_int(k: BigInt, l: BigInt): BigInt = modulo_integer(k, l)

  def decimalToNonNegInt(x0: decimal_lit): Option[BigInt] = x0 match {
    case DecimalLit(m, e) =>
      less_int(m, zero_int) match {
        case true => None
        case false => less_eq_int(zero_int, e) match {
            case true => Some[BigInt](times_inta(m, power[BigInt](BigInt(10), nat.apply(e))))
            case false =>
              val p =
                power[BigInt](BigInt(10), nat.apply(uminus_int(e))): BigInt;
              equal_int(modulo_int(m, p), zero_int) match {
                case true  => Some[BigInt](divide_int(m, p))
                case false => None
              }
          }
      }
  }

  def isDigitAscii(c: BigInt): Boolean = BigInt(48) <= c && c <= BigInt(57)

  def digitValue(c: BigInt): BigInt = c - BigInt(48)

  def consumeDigitsAux(x0: List[BigInt], acc: BigInt, count: nat): (BigInt, (nat, List[BigInt])) =
    (x0, acc, count) match {
      case (Nil, acc, count) => (acc, (count, Nil))
      case (c :: cs, acc, count) =>
        isDigitAscii(c) match {
          case true => consumeDigitsAux(
              cs,
              plus_int(times_inta(acc, BigInt(10)), digitValue(c)),
              plus_nat(count, one_nat)
            )
          case false => (acc, (count, c :: cs))
        }
    }

  def parseDecimalLit(s: String): Option[decimal_lit] = {
    val cs0 = Str_Literal.asciisOfLiteral(s): List[BigInt]
    val (sign, cs1) =
      (cs0 match {
        case Nil => (one_inta, cs0)
        case c :: rs =>
          c == BigInt(45) match {
            case true  => (uminus_int(one_inta), rs)
            case false => (one_inta, cs0)
          }
      }): ((BigInt, List[BigInt]));
    consumeDigitsAux(cs1, zero_int, zero_nat) match {
      case (intPart, (intLen, Nil)) =>
        equal_nat(intLen, zero_nat) match {
          case true  => None
          case false => Some[decimal_lit](DecimalLit(times_inta(sign, intPart), zero_int))
        }
      case (intPart, (intLen, c :: rs)) =>
        c == BigInt(46) match {
          case true =>
            val (fracPart, (fracLen, rest2)) =
              consumeDigitsAux(rs, zero_int, zero_nat): ((BigInt, (nat, List[BigInt])));
            nulla[BigInt](rest2) &&
              (less_nat(zero_nat, intLen) &&
                less_nat(zero_nat, fracLen)) match {
              case true => Some[decimal_lit](DecimalLit(
                  times_inta(
                    sign,
                    plus_int(times_inta(intPart, power[BigInt](BigInt(10), fracLen)), fracPart)
                  ),
                  uminus_int(int_of_nat(fracLen))
                ))
              case false => None
            }
          case false => None
        }
    }
  }

  def applyFloatAtomOpenApi(atom: expr, bounds: openapi_bounds): openapi_bounds =
    atom match {
      case BinaryOpF(_, _, BinaryOpF(_, _, _, _), _)         => bounds
      case BinaryOpF(_, _, UnaryOpF(_, _, _), _)             => bounds
      case BinaryOpF(_, _, QuantifierF(_, _, _, _), _)       => bounds
      case BinaryOpF(_, _, SomeWrapF(_, _), _)               => bounds
      case BinaryOpF(_, _, TheF(_, _, _, _), _)              => bounds
      case BinaryOpF(_, _, FieldAccessF(_, _, _), _)         => bounds
      case BinaryOpF(_, _, EnumAccessF(_, _, _), _)          => bounds
      case BinaryOpF(_, _, IndexF(_, _, _), _)               => bounds
      case BinaryOpF(_, _, CallF(_, _, _), _)                => bounds
      case BinaryOpF(_, _, PrimeF(_, _), _)                  => bounds
      case BinaryOpF(_, _, PreF(_, _), _)                    => bounds
      case BinaryOpF(_, _, WithF(_, _, _), _)                => bounds
      case BinaryOpF(_, _, IfF(_, _, _, _), _)               => bounds
      case BinaryOpF(_, _, LetF(_, _, _, _), _)              => bounds
      case BinaryOpF(_, _, LambdaF(_, _, _), _)              => bounds
      case BinaryOpF(_, _, ConstructorF(_, _, _), _)         => bounds
      case BinaryOpF(_, _, SetLiteralF(_, _), _)             => bounds
      case BinaryOpF(_, _, MapLiteralF(_, _), _)             => bounds
      case BinaryOpF(_, _, SetComprehensionF(_, _, _, _), _) => bounds
      case BinaryOpF(_, _, SeqLiteralF(_, _), _)             => bounds
      case BinaryOpF(_, _, MatchesF(_, _, _), _)             => bounds
      case BinaryOpF(_, _, IntLitF(_, _), _)                 => bounds
      case BinaryOpF(op, lhs, FloatLitF(v, _), _) =>
        parseDecimalLit(v) match {
          case None => bounds
          case Some(d) =>
            isLenOfValue(lhs) match {
              case true => decimalToNonNegInt(d) match {
                  case None => bounds
                  case Some(ni) =>
                    applyLengthBoundOpenApi(op, ni, bounds)
                }
              case false => isValueRef(lhs) match {
                  case true  => applyNumericBoundOpenApi(op, d, bounds)
                  case false => bounds
                }
            }
        }
      case BinaryOpF(_, _, StringLitF(_, _), _)  => bounds
      case BinaryOpF(_, _, BoolLitF(_, _), _)    => bounds
      case BinaryOpF(_, _, NoneLitF(_), _)       => bounds
      case BinaryOpF(_, _, IdentifierF(_, _), _) => bounds
      case UnaryOpF(_, _, _)                     => bounds
      case QuantifierF(_, _, _, _)               => bounds
      case SomeWrapF(_, _)                       => bounds
      case TheF(_, _, _, _)                      => bounds
      case FieldAccessF(_, _, _)                 => bounds
      case EnumAccessF(_, _, _)                  => bounds
      case IndexF(_, _, _)                       => bounds
      case CallF(_, _, _)                        => bounds
      case PrimeF(_, _)                          => bounds
      case PreF(_, _)                            => bounds
      case WithF(_, _, _)                        => bounds
      case IfF(_, _, _, _)                       => bounds
      case LetF(_, _, _, _)                      => bounds
      case LambdaF(_, _, _)                      => bounds
      case ConstructorF(_, _, _)                 => bounds
      case SetLiteralF(_, _)                     => bounds
      case MapLiteralF(_, _)                     => bounds
      case SetComprehensionF(_, _, _, _)         => bounds
      case SeqLiteralF(_, _)                     => bounds
      case MatchesF(_, _, _)                     => bounds
      case IntLitF(_, _)                         => bounds
      case FloatLitF(_, _)                       => bounds
      case StringLitF(_, _)                      => bounds
      case BoolLitF(_, _)                        => bounds
      case NoneLitF(_)                           => bounds
      case IdentifierF(_, _)                     => bounds
    }

  def decimalOfInt(n: BigInt): decimal_lit = DecimalLit(n, zero_int)

  def withPattern(v: Option[String], x1: openapi_bounds): openapi_bounds =
    (v, x1) match {
      case (v, OpenApiBounds(nl, ml, mn, mx, emn, emx, uu)) =>
        OpenApiBounds(nl, ml, mn, mx, emn, emx, v)
    }

  def applyAtomOpenApi(atom: expr, bounds: openapi_bounds): openapi_bounds =
    decomposeAtom(atom) match {
      case RaLenCmp(op, n) => applyLengthBoundOpenApi(op, n, bounds)
      case RaValueCmp(op, n) =>
        applyNumericBoundOpenApi(op, decimalOfInt(n), bounds)
      case RaMatches(pat)       => withPattern(Some[String](pat), bounds)
      case RaMatchesIdent(_, _) => applyFloatAtomOpenApi(atom, bounds)
      case RaPredCall(_)        => applyFloatAtomOpenApi(atom, bounds)
      case RaUnknown(_)         => applyFloatAtomOpenApi(atom, bounds)
    }

  def visitConstraintOpenApi(e: expr, bounds: openapi_bounds): openapi_bounds =
    foldl[openapi_bounds, expr](
      (acc: openapi_bounds) =>
        (atom: expr) =>
          applyAtomOpenApi(atom, acc),
      bounds,
      flattenAnd(e)
    )

  def emptyOpenApiBounds: openapi_bounds =
    OpenApiBounds(None, None, None, None, None, None, None)

  def aliasRefinementsAux(
      fuel: nat,
      uu: type_expr,
      uv: List[(String, type_alias_decl)],
      uw: List[String]
  ): List[expr] =
    equal_nat(fuel, zero_nat) match {
      case true => Nil
      case false => stripOptions(uu) match {
          case NamedTypeF(name, _) =>
            membera[String](uw, name) match {
              case true => Nil
              case false => map_of[String, type_alias_decl](uv, name) match {
                  case None => Nil
                  case Some(TypeAliasDeclFull(_, base, predOpt, _)) =>
                    (predOpt match {
                      case None    => Nil
                      case Some(p) => List(p)
                    }) ++
                      aliasRefinementsAux(minus_nat(fuel, one_nat), base, uv, name :: uw)
                }
            }
          case SetTypeF(_, _)            => Nil
          case MapTypeF(_, _, _)         => Nil
          case SeqTypeF(_, _)            => Nil
          case OptionTypeF(_, _)         => Nil
          case RelationTypeF(_, _, _, _) => Nil
        }
    }

  def aliasRefinements(ty: type_expr, am: List[(String, type_alias_decl)]): List[expr] =
    aliasRefinementsAux(Suc(size_list[(String, type_alias_decl)](am)), ty, am, Nil)

  def computeFieldBounds(
      ty: type_expr,
      cOpt: Option[expr],
      am: List[(String, type_alias_decl)]
  ): openapi_bounds = {
    val alias_bounds =
      foldl[openapi_bounds, expr](
        (b: openapi_bounds) =>
          (p: expr) =>
            visitConstraintOpenApi(p, b),
        emptyOpenApiBounds,
        aliasRefinements(ty, am)
      ): openapi_bounds
    val final_bounds =
      (cOpt match {
        case None    => alias_bounds
        case Some(c) => visitConstraintOpenApi(c, alias_bounds)
      }): openapi_bounds;
    final_bounds
  }

  def typeExprToSchemaAux(
      fuel: nat,
      uu: type_expr,
      uv: openapi_bounds,
      uw: Option[List[String]],
      ux: List[(String, type_alias_decl)],
      uy: List[(String, enum_decl)],
      uz: List[String]
  ): schema_object =
    equal_nat(fuel, zero_nat) match {
      case true => emptySchemaObject
      case false => uu match {
          case NamedTypeF(name, _) =>
            classifyOpenApiNamedType(name, ux, uy, uz) match {
              case OntPrimitive(p) =>
                mergeConstraintsLifted(primitiveDefToSchema(p), uv, uw)
              case OntEnum(a)      => enumStringSchema(a)
              case OntEntityRef(a) => entityRefSchema(a)
              case OntAliasToType(base) =>
                typeExprToSchemaAux(minus_nat(fuel, one_nat), base, uv, uw, ux, uy, uz)
              case OntUnknown() =>
                mergeConstraintsLifted(textSchema, uv, uw)
            }
          case SetTypeF(inner, _) =>
            val (innerSchema, nullable) =
              fieldToSchemaAux(minus_nat(fuel, one_nat), inner, None, ux, uy, uz): (
                  (
                      schema_object,
                      Boolean
                  )
              )
            val items =
              (nullable match {
                case true  => makeNullableLifted(innerSchema)
                case false => innerSchema
              }): schema_object;
            arraySchema(items, boundsMinLength(uv), boundsMaxLength(uv))
          case MapTypeF(_, v, _) =>
            val (valueSchema, nullable) =
              fieldToSchemaAux(minus_nat(fuel, one_nat), v, None, ux, uy, uz): (
                  (
                      schema_object,
                      Boolean
                  )
              )
            val a =
              (nullable match {
                case true  => makeNullableLifted(valueSchema)
                case false => valueSchema
              }): schema_object;
            mapObjectSchema(a)
          case SeqTypeF(inner, _) =>
            val (innerSchema, nullable) =
              fieldToSchemaAux(minus_nat(fuel, one_nat), inner, None, ux, uy, uz): (
                  (
                      schema_object,
                      Boolean
                  )
              )
            val items =
              (nullable match {
                case true  => makeNullableLifted(innerSchema)
                case false => innerSchema
              }): schema_object;
            arraySchema(items, boundsMinLength(uv), boundsMaxLength(uv))
          case OptionTypeF(inner, _) =>
            typeExprToSchemaAux(minus_nat(fuel, one_nat), inner, uv, uw, ux, uy, uz)
          case RelationTypeF(_, _, _, _) => integerSchema
        }
    }

  def fieldToSchemaAux(
      fuel: nat,
      va: type_expr,
      vb: Option[expr],
      vc: List[(String, type_alias_decl)],
      vd: List[(String, enum_decl)],
      ve: List[String]
  ): (schema_object, Boolean) =
    equal_nat(fuel, zero_nat) match {
      case true => (emptySchemaObject, false)
      case false =>
        val nullable =
          (va match {
            case NamedTypeF(_, _)          => false
            case SetTypeF(_, _)            => false
            case MapTypeF(_, _, _)         => false
            case SeqTypeF(_, _)            => false
            case OptionTypeF(_, _)         => true
            case RelationTypeF(_, _, _, _) => false
          }): Boolean
        val effective =
          (va match {
            case NamedTypeF(a, b)      => NamedTypeF(a, b)
            case SetTypeF(a, b)        => SetTypeF(a, b)
            case MapTypeF(a, b, c)     => MapTypeF(a, b, c)
            case SeqTypeF(a, b)        => SeqTypeF(a, b)
            case OptionTypeF(inner, _) => inner
            case RelationTypeF(a, b, c, d) =>
              RelationTypeF(a, b, c, d)
          }): type_expr
        val bounds =
          computeFieldBounds(effective, vb, vc): openapi_bounds
        val enumOpt =
          findEnumValuesInType(effective, vc, vd): Option[List[String]];
        (
          typeExprToSchemaAux(minus_nat(fuel, one_nat), effective, bounds, enumOpt, vc, vd, ve),
          nullable
        )
    }

  def fieldToSchema(
      ty: type_expr,
      cOpt: Option[expr],
      am: List[(String, type_alias_decl)],
      em: List[(String, enum_decl)],
      ens: List[String]
  ): (schema_object, Boolean) =
    fieldToSchemaAux(openApiSchemaFuel(am), ty, cOpt, am, em, ens)

  def requiredNames(x0: List[(String, (schema_object, Boolean))]): List[String] =
    x0 match {
      case Nil => Nil
      case (n, (uu, nu)) :: rest =>
        nu match {
          case true  => requiredNames(rest)
          case false => n :: requiredNames(rest)
        }
    }

  def literalStartsWith(pre: String, s: String): Boolean = {
    val xs = Str_Literal.asciisOfLiteral(s): List[BigInt]
    val ys = Str_Literal.asciisOfLiteral(pre): List[BigInt];
    less_eq_nat(size_list[BigInt](ys), size_list[BigInt](xs)) &&
    equal_list[BigInt](take[BigInt](size_list[BigInt](ys), xs), ys)
  }

  def classifyUserCall(
      fnArities: List[(String, BigInt)],
      predArities: List[(String, BigInt)],
      fname: String,
      argCount: BigInt
  ): user_call_class =
    (lookupArity(fnArities, fname) match {
      case None    => lookupArity(predArities, fname)
      case Some(a) => Some[BigInt](a)
    }) match {
      case None => UcUnknown()
      case Some(n) =>
        equal_int(n, argCount) match {
          case true  => UcOk()
          case false => UcWrongArity(n)
        }
    }

  def isMapLiteralExpr(x0: expr): Boolean = x0 match {
    case MapLiteralF(uu, uv)              => true
    case BinaryOpF(v, va, vb, vc)         => false
    case UnaryOpF(v, va, vb)              => false
    case QuantifierF(v, va, vb, vc)       => false
    case SomeWrapF(v, va)                 => false
    case TheF(v, va, vb, vc)              => false
    case FieldAccessF(v, va, vb)          => false
    case EnumAccessF(v, va, vb)           => false
    case IndexF(v, va, vb)                => false
    case CallF(v, va, vb)                 => false
    case PrimeF(v, va)                    => false
    case PreF(v, va)                      => false
    case WithF(v, va, vb)                 => false
    case IfF(v, va, vb, vc)               => false
    case LetF(v, va, vb, vc)              => false
    case LambdaF(v, va, vb)               => false
    case ConstructorF(v, va, vb)          => false
    case SetLiteralF(v, va)               => false
    case SetComprehensionF(v, va, vb, vc) => false
    case SeqLiteralF(v, va)               => false
    case MatchesF(v, va, vb)              => false
    case IntLitF(v, va)                   => false
    case FloatLitF(v, va)                 => false
    case StringLitF(v, va)                => false
    case BoolLitF(v, va)                  => false
    case NoneLitF(v)                      => false
    case IdentifierF(v, va)               => false
  }

  def signalsHasCollectionInput(x0: analysis_signals): Boolean = x0 match {
    case AnalysisSignals(uu, uv, uw, ux, uy, uz, va, vb, h) => h
  }

  def signalsMutatedRelations(x0: analysis_signals): List[String] = x0 match {
    case AnalysisSignals(m, uu, uv, uw, ux, uy, uz, va, vb) => m
  }

  def signalsFilterParamCount(x0: analysis_signals): nat = x0 match {
    case AnalysisSignals(uu, uv, uw, ux, uy, uz, f, va, vb) => f
  }

  def signalsCreatesNewKey(x0: analysis_signals): Boolean = x0 match {
    case AnalysisSignals(uu, uv, c, uw, ux, uy, uz, va, vb) => c
  }

  def signalsIsTransition(x0: analysis_signals): Boolean = x0 match {
    case AnalysisSignals(uu, uv, uw, ux, uy, uz, va, t, vb) => t
  }

  def decideKindAndMethod(
      signals: analysis_signals,
      entityFieldCount: Option[nat]
  ): classification_result =
    signalsIsTransition(signals) match {
      case true => ClassificationResult(Transition(), POST(), "M10", signals)
      case false => signalsDeletesKey(signals) match {
          case true => ClassificationResult(Deletea(), DELETE(), "M5", signals)
          case false => !nulla[String](signalsMutatedRelations(signals)) &&
              signalsCreatesNewKey(signals) match {
              case true => ClassificationResult(Create(), POST(), "M1", signals)
              case false => nulla[String](signalsMutatedRelations(signals)) match {
                  case true =>
                    less_nat(nat_of_integer(BigInt(3)), signalsFilterParamCount(signals)) match {
                      case true  => ClassificationResult(FilteredRead(), GET(), "M7", signals)
                      case false => ClassificationResult(Read(), GET(), "M2", signals)
                    }
                  case false => signalsHasCollectionInput(signals) &&
                      !nulla[String](signalsMutatedRelations(signals)) match {
                      case true => ClassificationResult(BatchMutation(), POST(), "M9", signals)
                      case false => !nulla[String](signalsMutatedRelations(signals)) &&
                          (!signalsCreatesNewKey(signals) &&
                            !signalsDeletesKey(signals)) match {
                          case true  => decidePutPatch(signals, entityFieldCount)
                          case false => ClassificationResult(SideEffect(), POST(), "M8", signals)
                        }
                    }
                }
            }
        }
    }

  def postgresSaType(x0: canonical_type): sa_type = x0 match {
    case CtText()        => SaType("sa.Text()", None)
    case CtVarchar(n)    => SaType("sa.String(length=" + showInt(n) + ")", None)
    case CtInt4()        => SaType("sa.Integer()", None)
    case CtSerial4()     => SaType("sa.Integer()", None)
    case CtInt8()        => SaType("sa.BigInteger()", None)
    case CtSerial8()     => SaType("sa.BigInteger()", None)
    case CtFloat8()      => SaType("sa.Float()", None)
    case CtBool()        => SaType("sa.Boolean()", None)
    case CtTimestamptz() => SaType("sa.DateTime(timezone=True)", None)
    case CtDateOnly()    => SaType("sa.Date()", None)
    case CtUuid()        => SaType("sa.Uuid()", None)
    case CtNumeric(p, Some(s)) =>
      SaType("sa.Numeric(" + showInt(p) + ", " + showInt(s) + ")", None)
    case CtNumeric(p, None) => SaType("sa.Numeric(" + showInt(p) + ")", None)
    case CtBytes()          => SaType("sa.LargeBinary()", None)
    case CtJson() =>
      SaType("postgresql.JSONB()", Some[String]("sqlalchemy.dialects.postgresql"))
  }

  def cvrTarget(x0: convention_rule): String = x0 match {
    case ConventionRuleFull(x1, x2, x3, x4, x5) => x1
  }

  def cvdRules(x0: conventions_decl): List[convention_rule] = x0 match {
    case ConventionsDeclFull(x1, x2) => x1
  }

  def entInvariants(x0: entity_decl): List[expr] = x0 match {
    case EntityDeclFull(x1, x2, x3, x4, x5) => x4
  }

  def operInputs(x0: operation_decl): List[param_decl] = x0 match {
    case OperationDeclFull(x1, x2, x3, x4, x5, x6, x7) => x2
  }

  def qbdVar(x0: quantifier_binding): String = x0 match {
    case QuantifierBindingFull(x1, x2, x3, x4) => x1
  }

  def svcConventions(x0: service_ir): Option[conventions_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x14
  }

  def svcTransitions(x0: service_ir): List[transition_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x8
  }

  def svcTypeAliases(x0: service_ir): List[type_alias_decl] = x0 match {
    case ServiceIRFull(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16) => x5
  }

  def trnEntity(x0: transition_decl): String = x0 match {
    case TransitionDeclFull(x1, x2, x3, x4, x5) => x2
  }

  def describeLitClass(x0: lit_class): String = x0 match {
    case LcNumeric()    => "numeric"
    case LcBool()       => "boolean"
    case LcStringLike() => "string"
    case LcCollection() => "collection"
    case LcNone()       => "none"
  }

  def extractFieldName(x0: expr): Option[String] = x0 match {
    case FieldAccessF(b, name, uu) =>
      b match {
        case BinaryOpF(_, _, _, _)         => None
        case UnaryOpF(_, _, _)             => None
        case QuantifierF(_, _, _, _)       => None
        case SomeWrapF(_, _)               => None
        case TheF(_, _, _, _)              => None
        case FieldAccessF(_, _, _)         => None
        case EnumAccessF(_, _, _)          => None
        case IndexF(_, _, _)               => None
        case CallF(_, _, _)                => None
        case PrimeF(_, _)                  => None
        case PreF(_, _)                    => None
        case WithF(_, _, _)                => None
        case IfF(_, _, _, _)               => None
        case LetF(_, _, _, _)              => None
        case LambdaF(_, _, _)              => None
        case ConstructorF(_, _, _)         => None
        case SetLiteralF(_, _)             => None
        case MapLiteralF(_, _)             => None
        case SetComprehensionF(_, _, _, _) => None
        case SeqLiteralF(_, _)             => None
        case MatchesF(_, _, _)             => None
        case IntLitF(_, _)                 => None
        case FloatLitF(_, _)               => None
        case StringLitF(_, _)              => None
        case BoolLitF(_, _)                => None
        case NoneLitF(_)                   => None
        case IdentifierF(s, _) =>
          s == "self" match {
            case true  => Some[String](name)
            case false => None
          }
      }
    case IdentifierF(name, uv)            => Some[String](name)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
  }

  def keyExistencePair(e: expr): Option[(String, String)] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)                                    => None
      case BinaryOpF(BOr(), _, _, _)                                     => None
      case BinaryOpF(BImplies(), _, _, _)                                => None
      case BinaryOpF(BIff(), _, _, _)                                    => None
      case BinaryOpF(BEq(), _, _, _)                                     => None
      case BinaryOpF(BNeq(), _, _, _)                                    => None
      case BinaryOpF(BLt(), _, _, _)                                     => None
      case BinaryOpF(BGt(), _, _, _)                                     => None
      case BinaryOpF(BLe(), _, _, _)                                     => None
      case BinaryOpF(BGe(), _, _, _)                                     => None
      case BinaryOpF(BIn(), BinaryOpF(_, _, _, _), _, _)                 => None
      case BinaryOpF(BIn(), UnaryOpF(_, _, _), _, _)                     => None
      case BinaryOpF(BIn(), QuantifierF(_, _, _, _), _, _)               => None
      case BinaryOpF(BIn(), SomeWrapF(_, _), _, _)                       => None
      case BinaryOpF(BIn(), TheF(_, _, _, _), _, _)                      => None
      case BinaryOpF(BIn(), FieldAccessF(_, _, _), _, _)                 => None
      case BinaryOpF(BIn(), EnumAccessF(_, _, _), _, _)                  => None
      case BinaryOpF(BIn(), IndexF(_, _, _), _, _)                       => None
      case BinaryOpF(BIn(), CallF(_, _, _), _, _)                        => None
      case BinaryOpF(BIn(), PrimeF(_, _), _, _)                          => None
      case BinaryOpF(BIn(), PreF(_, _), _, _)                            => None
      case BinaryOpF(BIn(), WithF(_, _, _), _, _)                        => None
      case BinaryOpF(BIn(), IfF(_, _, _, _), _, _)                       => None
      case BinaryOpF(BIn(), LetF(_, _, _, _), _, _)                      => None
      case BinaryOpF(BIn(), LambdaF(_, _, _), _, _)                      => None
      case BinaryOpF(BIn(), ConstructorF(_, _, _), _, _)                 => None
      case BinaryOpF(BIn(), SetLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BIn(), MapLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BIn(), SetComprehensionF(_, _, _, _), _, _)         => None
      case BinaryOpF(BIn(), SeqLiteralF(_, _), _, _)                     => None
      case BinaryOpF(BIn(), MatchesF(_, _, _), _, _)                     => None
      case BinaryOpF(BIn(), IntLitF(_, _), _, _)                         => None
      case BinaryOpF(BIn(), FloatLitF(_, _), _, _)                       => None
      case BinaryOpF(BIn(), StringLitF(_, _), _, _)                      => None
      case BinaryOpF(BIn(), BoolLitF(_, _), _, _)                        => None
      case BinaryOpF(BIn(), NoneLitF(_), _, _)                           => None
      case BinaryOpF(BIn(), IdentifierF(_, _), BinaryOpF(_, _, _, _), _) => None
      case BinaryOpF(BIn(), IdentifierF(_, _), UnaryOpF(_, _, _), _)     => None
      case BinaryOpF(BIn(), IdentifierF(_, _), QuantifierF(_, _, _, _), _) =>
        None
      case BinaryOpF(BIn(), IdentifierF(_, _), SomeWrapF(_, _), _)               => None
      case BinaryOpF(BIn(), IdentifierF(_, _), TheF(_, _, _, _), _)              => None
      case BinaryOpF(BIn(), IdentifierF(_, _), FieldAccessF(_, _, _), _)         => None
      case BinaryOpF(BIn(), IdentifierF(_, _), EnumAccessF(_, _, _), _)          => None
      case BinaryOpF(BIn(), IdentifierF(_, _), IndexF(_, _, _), _)               => None
      case BinaryOpF(BIn(), IdentifierF(_, _), CallF(_, _, _), _)                => None
      case BinaryOpF(BIn(), IdentifierF(_, _), PrimeF(_, _), _)                  => None
      case BinaryOpF(BIn(), IdentifierF(_, _), PreF(_, _), _)                    => None
      case BinaryOpF(BIn(), IdentifierF(_, _), WithF(_, _, _), _)                => None
      case BinaryOpF(BIn(), IdentifierF(_, _), IfF(_, _, _, _), _)               => None
      case BinaryOpF(BIn(), IdentifierF(_, _), LetF(_, _, _, _), _)              => None
      case BinaryOpF(BIn(), IdentifierF(_, _), LambdaF(_, _, _), _)              => None
      case BinaryOpF(BIn(), IdentifierF(_, _), ConstructorF(_, _, _), _)         => None
      case BinaryOpF(BIn(), IdentifierF(_, _), SetLiteralF(_, _), _)             => None
      case BinaryOpF(BIn(), IdentifierF(_, _), MapLiteralF(_, _), _)             => None
      case BinaryOpF(BIn(), IdentifierF(_, _), SetComprehensionF(_, _, _, _), _) => None
      case BinaryOpF(BIn(), IdentifierF(_, _), SeqLiteralF(_, _), _)             => None
      case BinaryOpF(BIn(), IdentifierF(_, _), MatchesF(_, _, _), _)             => None
      case BinaryOpF(BIn(), IdentifierF(_, _), IntLitF(_, _), _)                 => None
      case BinaryOpF(BIn(), IdentifierF(_, _), FloatLitF(_, _), _)               => None
      case BinaryOpF(BIn(), IdentifierF(_, _), StringLitF(_, _), _)              => None
      case BinaryOpF(BIn(), IdentifierF(_, _), BoolLitF(_, _), _)                => None
      case BinaryOpF(BIn(), IdentifierF(_, _), NoneLitF(_), _)                   => None
      case BinaryOpF(BIn(), IdentifierF(inName, _), IdentifierF(stName, _), _) =>
        Some[(String, String)]((inName, stName))
      case BinaryOpF(BNotIn(), _, _, _)     => None
      case BinaryOpF(BSubset(), _, _, _)    => None
      case BinaryOpF(BUnion(), _, _, _)     => None
      case BinaryOpF(BIntersect(), _, _, _) => None
      case BinaryOpF(BDiff(), _, _, _)      => None
      case BinaryOpF(BAdd(), _, _, _)       => None
      case BinaryOpF(BSub(), _, _, _)       => None
      case BinaryOpF(BMul(), _, _, _)       => None
      case BinaryOpF(BDiv(), _, _, _)       => None
      case UnaryOpF(_, _, _)                => None
      case QuantifierF(_, _, _, _)          => None
      case SomeWrapF(_, _)                  => None
      case TheF(_, _, _, _)                 => None
      case FieldAccessF(_, _, _)            => None
      case EnumAccessF(_, _, _)             => None
      case IndexF(_, _, _)                  => None
      case CallF(_, _, _)                   => None
      case PrimeF(_, _)                     => None
      case PreF(_, _)                       => None
      case WithF(_, _, _)                   => None
      case IfF(_, _, _, _)                  => None
      case LetF(_, _, _, _)                 => None
      case LambdaF(_, _, _)                 => None
      case ConstructorF(_, _, _)            => None
      case SetLiteralF(_, _)                => None
      case MapLiteralF(_, _)                => None
      case SetComprehensionF(_, _, _, _)    => None
      case SeqLiteralF(_, _)                => None
      case MatchesF(_, _, _)                => None
      case IntLitF(_, _)                    => None
      case FloatLitF(_, _)                  => None
      case StringLitF(_, _)                 => None
      case BoolLitF(_, _)                   => None
      case NoneLitF(_)                      => None
      case IdentifierF(_, _)                => None
    }

  def collectWithFields(es: List[expr]): Option[with_info] =
    maps[expr, with_info](
      (e: expr) =>
        maps[expr, with_info]((a: expr) => withInfoSelect(a), allSubexprs(e)),
      es
    ) match {
      case Nil    => None
      case x :: _ => Some[with_info](x)
    }

  def countFilterParams(ps: List[param_decl]): nat =
    size_list[param_decl](filter[param_decl](
      (p: param_decl) =>
        paramTypeFull(p) match {
          case NamedTypeF(_, _)          => false
          case SetTypeF(_, _)            => false
          case MapTypeF(_, _, _)         => false
          case SeqTypeF(_, _)            => false
          case OptionTypeF(_, _)         => true
          case RelationTypeF(_, _, _, _) => false
        },
      ps
    ))

  def enumValuesForType(
      fuel: nat,
      t: type_expr,
      enums: List[enum_decl],
      aliases: List[type_alias_decl]
  ): Option[List[String]] =
    equal_nat(fuel, zero_nat) match {
      case true => None
      case false => t match {
          case NamedTypeF(name, _) =>
            find[enum_decl](
              (e: enum_decl) =>
                enumNameFull(e) == name,
              enums
            ) match {
              case None =>
                find[type_alias_decl](
                  (a: type_alias_decl) =>
                    typeAliasName(a) == name,
                  aliases
                ) match {
                  case None => None
                  case Some(a) =>
                    enumValuesForType(minus_nat(fuel, one_nat), typeAliasType(a), enums, aliases)
                }
              case Some(e) => Some[List[String]](enumValuesFull(e))
            }
          case SetTypeF(_, _)            => None
          case MapTypeF(_, _, _)         => None
          case SeqTypeF(_, _)            => None
          case OptionTypeF(_, _)         => None
          case RelationTypeF(_, _, _, _) => None
        }
    }

  def findFieldDeclFull(fs: List[field_decl], nm: String): Option[field_decl] =
    find[field_decl]((fd: field_decl) => fieldNameFull(fd) == nm, fs)

  def irConventionRules(ir: service_ir): List[convention_rule] =
    svcConventions(ir) match {
      case None    => Nil
      case Some(a) => cvdRules(a)
    }

  def irStateFieldNames(ir: service_ir): List[String] =
    map[state_field_decl, String]((a: state_field_decl) => stfName(a), irStateFields(ir))

  def isDestructiveOp(x0: migration_op): Boolean = x0 match {
    case CreateTable(uu)                     => false
    case DropTable(uv)                       => true
    case AddColumn(uw, ux)                   => false
    case DropColumn(uy, uz)                  => true
    case AlterColumnType(va, vb, vc, vd)     => false
    case AlterColumnNullable(ve, vf, vg, vh) => false
    case AlterColumnDefault(vi, vj, vk, vl)  => false
    case AddCheck(vm, vn, vo)                => false
    case DropCheck(vp, vq, vr)               => false
    case AddForeignKey(vs, vt)               => false
    case DropForeignKey(vu, vv)              => false
    case AddIndex(vw, vx)                    => false
    case DropIndex(vy, vz)                   => false
    case AddTrigger(wa)                      => false
    case DropTrigger(wb)                     => false
  }

  def nonIdDecorated(x0: List[(String, (schema_object, Boolean))])
      : List[(String, (schema_object, Boolean))] =
    x0 match {
      case Nil => Nil
      case (n, (s, nu)) :: rest =>
        n == "id" match {
          case true  => nonIdDecorated(rest)
          case false => (n, (s, nu)) :: nonIdDecorated(rest)
        }
    }

  def effectiveRouteKind(initial: route_kind, matchesCreateShape: Boolean): route_kind =
    isRkCreate(initial) && !matchesCreateShape match {
      case true  => RkOther()
      case false => initial
    }

  def matchesCreateShape(
      classification: route_kind,
      bodyParamNames: List[String],
      entityNonIdColumns: List[String]
  ): Boolean =
    isRkCreate(classification) &&
      (distinct[String](bodyParamNames) &&
        (list_all[String](
          (a: String) =>
            membera[String](entityNonIdColumns, a),
          bodyParamNames
        ) &&
          list_all[String]((a: String) => membera[String](bodyParamNames, a), entityNonIdColumns)))

  def uniqueBackFkColumn(fks: List[foreign_key_spec], parentTable: String): Option[String] =
    filter[foreign_key_spec](
      (fk: foreign_key_spec) =>
        fkRefTable(fk) == parentTable,
      fks
    ) match {
      case Nil         => None
      case List(fk)    => Some[String](fkColumn(fk))
      case _ :: _ :: _ => None
    }

  def validateTrigger(
      parentTbl: table_spec,
      parentFields: List[field_decl],
      childTbl: table_spec,
      childEntity: entity_decl,
      tgt: String,
      agg: trigger_aggregate,
      src: Option[String]
  ): Option[trigger_candidate] =
    !list_ex[field_decl]((f: field_decl) => fieldNameFull(f) == tgt, parentFields) match {
      case true => None
      case false => uniqueBackFkColumn(tableForeignKeys(childTbl), tableName(parentTbl)) match {
          case None => None
          case Some(fkCol) =>
            val childFields =
              entityFieldsFull(childEntity): List[field_decl];
            (src match {
              case None => true
              case Some(sf) =>
                list_ex[field_decl](
                  (f: field_decl) =>
                    fieldNameFull(f) == sf,
                  childFields
                )
            }) match {
              case true =>
                Some[trigger_candidate](TriggerCandidate(
                  tableName(parentTbl),
                  tgt,
                  tableName(childTbl),
                  fkCol,
                  agg,
                  src
                ))
              case false => None
            }
        }
    }

  def equal_ty(x0: ty, x1: ty): Boolean = (x0, x1) match {
    case (TSeq(x9), TMap(x101, x102))    => false
    case (TMap(x101, x102), TSeq(x9))    => false
    case (TOption(x8), TMap(x101, x102)) => false
    case (TMap(x101, x102), TOption(x8)) => false
    case (TOption(x8), TSeq(x9))         => false
    case (TSeq(x9), TOption(x8))         => false
    case (TSet(x7), TMap(x101, x102))    => false
    case (TMap(x101, x102), TSet(x7))    => false
    case (TSet(x7), TSeq(x9))            => false
    case (TSeq(x9), TSet(x7))            => false
    case (TSet(x7), TOption(x8))         => false
    case (TOption(x8), TSet(x7))         => false
    case (TEntity(x6), TMap(x101, x102)) => false
    case (TMap(x101, x102), TEntity(x6)) => false
    case (TEntity(x6), TSeq(x9))         => false
    case (TSeq(x9), TEntity(x6))         => false
    case (TEntity(x6), TOption(x8))      => false
    case (TOption(x8), TEntity(x6))      => false
    case (TEntity(x6), TSet(x7))         => false
    case (TSet(x7), TEntity(x6))         => false
    case (TEnum(x5), TMap(x101, x102))   => false
    case (TMap(x101, x102), TEnum(x5))   => false
    case (TEnum(x5), TSeq(x9))           => false
    case (TSeq(x9), TEnum(x5))           => false
    case (TEnum(x5), TOption(x8))        => false
    case (TOption(x8), TEnum(x5))        => false
    case (TEnum(x5), TSet(x7))           => false
    case (TSet(x7), TEnum(x5))           => false
    case (TEnum(x5), TEntity(x6))        => false
    case (TEntity(x6), TEnum(x5))        => false
    case (TStr(), TMap(x101, x102))      => false
    case (TMap(x101, x102), TStr())      => false
    case (TStr(), TSeq(x9))              => false
    case (TSeq(x9), TStr())              => false
    case (TStr(), TOption(x8))           => false
    case (TOption(x8), TStr())           => false
    case (TStr(), TSet(x7))              => false
    case (TSet(x7), TStr())              => false
    case (TStr(), TEntity(x6))           => false
    case (TEntity(x6), TStr())           => false
    case (TStr(), TEnum(x5))             => false
    case (TEnum(x5), TStr())             => false
    case (TReal(), TMap(x101, x102))     => false
    case (TMap(x101, x102), TReal())     => false
    case (TReal(), TSeq(x9))             => false
    case (TSeq(x9), TReal())             => false
    case (TReal(), TOption(x8))          => false
    case (TOption(x8), TReal())          => false
    case (TReal(), TSet(x7))             => false
    case (TSet(x7), TReal())             => false
    case (TReal(), TEntity(x6))          => false
    case (TEntity(x6), TReal())          => false
    case (TReal(), TEnum(x5))            => false
    case (TEnum(x5), TReal())            => false
    case (TReal(), TStr())               => false
    case (TStr(), TReal())               => false
    case (TInt(), TMap(x101, x102))      => false
    case (TMap(x101, x102), TInt())      => false
    case (TInt(), TSeq(x9))              => false
    case (TSeq(x9), TInt())              => false
    case (TInt(), TOption(x8))           => false
    case (TOption(x8), TInt())           => false
    case (TInt(), TSet(x7))              => false
    case (TSet(x7), TInt())              => false
    case (TInt(), TEntity(x6))           => false
    case (TEntity(x6), TInt())           => false
    case (TInt(), TEnum(x5))             => false
    case (TEnum(x5), TInt())             => false
    case (TInt(), TStr())                => false
    case (TStr(), TInt())                => false
    case (TInt(), TReal())               => false
    case (TReal(), TInt())               => false
    case (TBool(), TMap(x101, x102))     => false
    case (TMap(x101, x102), TBool())     => false
    case (TBool(), TSeq(x9))             => false
    case (TSeq(x9), TBool())             => false
    case (TBool(), TOption(x8))          => false
    case (TOption(x8), TBool())          => false
    case (TBool(), TSet(x7))             => false
    case (TSet(x7), TBool())             => false
    case (TBool(), TEntity(x6))          => false
    case (TEntity(x6), TBool())          => false
    case (TBool(), TEnum(x5))            => false
    case (TEnum(x5), TBool())            => false
    case (TBool(), TStr())               => false
    case (TStr(), TBool())               => false
    case (TBool(), TReal())              => false
    case (TReal(), TBool())              => false
    case (TBool(), TInt())               => false
    case (TInt(), TBool())               => false
    case (TMap(x101, x102), TMap(y101, y102)) =>
      equal_ty(x101, y101) && equal_ty(x102, y102)
    case (TSeq(x9), TSeq(y9))       => equal_ty(x9, y9)
    case (TOption(x8), TOption(y8)) => equal_ty(x8, y8)
    case (TSet(x7), TSet(y7))       => equal_ty(x7, y7)
    case (TEntity(x6), TEntity(y6)) => x6 == y6
    case (TEnum(x5), TEnum(y5))     => x5 == y5
    case (TStr(), TStr())           => true
    case (TReal(), TReal())         => true
    case (TInt(), TInt())           => true
    case (TBool(), TBool())         => true
  }

  def check_value_has_ty_pairs(
      vc: tyctx_ext[Unit],
      x1: List[(ir_value, ir_value)],
      vd: ty,
      ve: ty
  ): Boolean =
    (vc, x1, vd, ve) match {
      case (vc, Nil, vd, ve) => true
      case (gamma, (k, w) :: ps, tk, tv) =>
        check_value_has_ty(gamma, k, tk) &&
        (check_value_has_ty(gamma, w, tv) &&
          check_value_has_ty_pairs(gamma, ps, tk, tv))
    }

  def check_value_has_ty_list(va: tyctx_ext[Unit], x1: List[ir_value], vb: ty): Boolean =
    (va, x1, vb) match {
      case (va, Nil, vb) => true
      case (gamma, v :: vs, t) =>
        check_value_has_ty(gamma, v, t) && check_value_has_ty_list(gamma, vs, t)
    }

  def check_value_has_ty(gamma: tyctx_ext[Unit], x1: ir_value, t: ty): Boolean =
    (gamma, x1, t) match {
      case (gamma, VBool(uu), t)          => equal_ty(t, TBool())
      case (gamma, VInt(uv), t)           => equal_ty(t, TInt())
      case (gamma, VReal(uw), t)          => equal_ty(t, TReal())
      case (gamma, VStr(ux), t)           => equal_ty(t, TStr())
      case (gamma, VEnum(ename, uy), t)   => equal_ty(t, TEnum(ename))
      case (gamma, VEntity(ename, uz), t) => equal_ty(t, TEntity(ename))
      case (gamma, VSet(vs), t) =>
        t match {
          case TBool()    => false
          case TInt()     => false
          case TReal()    => false
          case TStr()     => false
          case TEnum(_)   => false
          case TEntity(_) => false
          case TSet(a)    => check_value_has_ty_list(gamma, vs, a)
          case TOption(_) => false
          case TSeq(_)    => false
          case TMap(_, _) => false
        }
      case (gamma, VEntityWith(base, fld, overridea), t) =>
        t match {
          case TBool()  => false
          case TInt()   => false
          case TReal()  => false
          case TStr()   => false
          case TEnum(_) => false
          case TEntity(ename) =>
            check_value_has_ty(gamma, base, TEntity(ename)) &&
            (schemaFieldType(gamma, ename, fld) match {
              case None    => false
              case Some(a) => check_value_has_ty(gamma, overridea, a)
            })
          case TSet(_)    => false
          case TOption(_) => false
          case TSeq(_)    => false
          case TMap(_, _) => false
        }
      case (gamma, VNone(), t) => t match {
          case TBool()    => false
          case TInt()     => false
          case TReal()    => false
          case TStr()     => false
          case TEnum(_)   => false
          case TEntity(_) => false
          case TSet(_)    => false
          case TOption(_) => true
          case TSeq(_)    => false
          case TMap(_, _) => false
        }
      case (gamma, VSome(v), t) =>
        t match {
          case TBool()    => false
          case TInt()     => false
          case TReal()    => false
          case TStr()     => false
          case TEnum(_)   => false
          case TEntity(_) => false
          case TSet(_)    => false
          case TOption(a) => check_value_has_ty(gamma, v, a)
          case TSeq(_)    => false
          case TMap(_, _) => false
        }
      case (gamma, VSeq(vs), t) =>
        t match {
          case TBool()    => false
          case TInt()     => false
          case TReal()    => false
          case TStr()     => false
          case TEnum(_)   => false
          case TEntity(_) => false
          case TSet(_)    => false
          case TOption(_) => false
          case TSeq(a)    => check_value_has_ty_list(gamma, vs, a)
          case TMap(_, _) => false
        }
      case (gamma, VMap(ps), t) =>
        t match {
          case TBool()    => false
          case TInt()     => false
          case TReal()    => false
          case TStr()     => false
          case TEnum(_)   => false
          case TEntity(_) => false
          case TSet(_)    => false
          case TOption(_) => false
          case TSeq(_)    => false
          case TMap(a, b) => check_value_has_ty_pairs(gamma, ps, a, b)
        }
    }

  def tc_relations[A](x0: tyctx_ext[A]): List[state_field_decl] = x0 match {
    case tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more) => tc_relations
  }

  def mergeIntConstraint(x0: int_constraint, x1: int_constraint): int_constraint =
    (x0, x1) match {
      case (IntConstraint(amin, amax, af), IntConstraint(bmin, bmax, bf)) =>
        IntConstraint(mergeMinInt(amin, bmin), mergeMaxInt(amax, bmax), af ++ bf)
    }

  def walkIntConstraint(e: expr): (int_constraint, List[String]) =
    foldl[(int_constraint, List[String]), expr](
      (acc: (int_constraint, List[String])) =>
        (atom: expr) => {
          val (cur, skips) = acc: ((int_constraint, List[String]))
          val (nxt, new_skips) =
            intAtom(atom): ((int_constraint, List[String]));
          (mergeIntConstraint(cur, nxt), skips ++ new_skips)
        },
      (emptyIntConstraint, Nil),
      flattenAnd(e)
    )

  def asIntLit(x0: expr): Option[BigInt] = x0 match {
    case IntLitF(n, uu)                   => Some[BigInt](n)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def showBool(x0: Boolean): String = x0 match {
    case true  => "true"
    case false => "false"
  }

  def valuesOf(x0: List[(String, (String, String))]): List[String] = x0 match {
    case Nil                   => Nil
    case (uu, (uv, v)) :: rest => v :: valuesOf(rest)
  }

  def serviceIrInvariants(x0: service_ir): List[invariant_decl] = x0 match {
    case ServiceIRFull(uu, uv, uw, ux, uy, uz, va, vb, invs, vc, vd, ve, vf, vg, vh, vi) => invs
  }

  def invariantBody(x0: invariant_decl): expr = x0 match {
    case InvariantDeclFull(uu, b, uv) => b
  }

  def invariantBodies(ir: service_ir): List[expr] =
    map[invariant_decl, expr]((a: invariant_decl) => invariantBody(a), serviceIrInvariants(ir))

  def trustGlobal(enums: List[String], ir: service_ir): trust_level =
    foldTrust(enums, invariantBodies(ir))

  def fieldDeclTypeOf(x0: field_decl): type_expr = x0 match {
    case FieldDeclFull(uu, t, uv, uw) => t
  }

  def fieldDeclNameOf(x0: field_decl): String = x0 match {
    case FieldDeclFull(n, uu, uv, uw) => n
  }

  def fieldDeclToAlloyField(fd: field_decl): Option[alloy_field] =
    alloyFieldTypeOf(fieldDeclTypeOf(fd)) match {
      case None => None
      case Some(mn) =>
        Some[alloy_field](AlloyFieldLifted(
          fieldDeclNameOf(fd),
          fst[alloy_field_multiplicity, String](mn),
          snd[alloy_field_multiplicity, String](mn)
        ))
    }

  def fieldDeclsToAlloyFields(x0: List[field_decl]): Option[List[alloy_field]] =
    x0 match {
      case Nil => Some[List[alloy_field]](Nil)
      case fd :: rest =>
        fieldDeclToAlloyField(fd) match {
          case None => None
          case Some(f) => fieldDeclsToAlloyFields(rest) match {
              case None     => None
              case Some(fs) => Some[List[alloy_field]](f :: fs)
            }
        }
    }

  def entityDeclFieldsOf(x0: entity_decl): List[field_decl] = x0 match {
    case EntityDeclFull(uu, uv, fs, uw, ux) => fs
  }

  def entityDeclNameOf(x0: entity_decl): String = x0 match {
    case EntityDeclFull(n, uu, uv, uw, ux) => n
  }

  def entityToAlloySig(e: entity_decl): Option[alloy_sig] =
    fieldDeclsToAlloyFields(entityDeclFieldsOf(e)) match {
      case None => None
      case Some(fs) =>
        Some[alloy_sig](AlloySigLifted(entityDeclNameOf(e), false, false, None, fs))
    }

  def entitiesToAlloySigs(x0: List[entity_decl]): Option[List[alloy_sig]] = x0 match {
    case Nil => Some[List[alloy_sig]](Nil)
    case e :: rest =>
      entityToAlloySig(e) match {
        case None => None
        case Some(s) => entitiesToAlloySigs(rest) match {
            case None     => None
            case Some(ss) => Some[List[alloy_sig]](s :: ss)
          }
      }
  }

  def enumMembersToSigs(uu: String, x1: List[String]): List[alloy_sig] =
    (uu, x1) match {
      case (uu, Nil) => Nil
      case (parent, v :: rest) =>
        AlloySigLifted(v, false, true, Some[String](parent), Nil) ::
          enumMembersToSigs(parent, rest)
    }

  def enumDeclValuesOf(x0: enum_decl): List[String] = x0 match {
    case EnumDeclFull(uu, vs, uv) => vs
  }

  def enumDeclNameOf(x0: enum_decl): String = x0 match {
    case EnumDeclFull(n, uu, uv) => n
  }

  def enumToAlloySigs(e: enum_decl): List[alloy_sig] =
    AlloySigLifted(enumDeclNameOf(e), true, false, None, Nil) ::
      enumMembersToSigs(enumDeclNameOf(e), enumDeclValuesOf(e))

  def enumsToAlloySigs(x0: List[enum_decl]): List[alloy_sig] = x0 match {
    case Nil       => Nil
    case e :: rest => enumToAlloySigs(e) ++ enumsToAlloySigs(rest)
  }

  def typedNamesToAlloyFields(x0: List[(String, type_expr)]): Option[List[alloy_field]] =
    x0 match {
      case Nil => Some[List[alloy_field]](Nil)
      case (name, t) :: rest =>
        alloyFieldTypeOf(t) match {
          case None => None
          case Some(mn) =>
            typedNamesToAlloyFields(rest) match {
              case None => None
              case Some(fs) =>
                Some[List[alloy_field]](AlloyFieldLifted(
                  name,
                  fst[alloy_field_multiplicity, String](mn),
                  snd[alloy_field_multiplicity, String](mn)
                ) ::
                  fs)
            }
        }
    }

  def stateOrInputSig(sigName: String, fs: List[(String, type_expr)]): Option[List[alloy_sig]] =
    nulla[(String, type_expr)](fs) match {
      case true => Some[List[alloy_sig]](Nil)
      case false => typedNamesToAlloyFields(fs) match {
          case None => None
          case Some(afs) =>
            Some[List[alloy_sig]](List(AlloySigLifted(sigName, false, true, None, afs)))
        }
    }

  def buildAlloySigs(
      needsBool: Boolean,
      ents: List[entity_decl],
      enums: List[enum_decl],
      stateFields: List[(String, type_expr)],
      inputFields: List[(String, type_expr)],
      includeStatePost: Boolean
  ): Option[List[alloy_sig]] = {
    val boolPart =
      Some[List[alloy_sig]](needsBool match {
        case true  => boolSigs
        case false => Nil
      }): Option[List[alloy_sig]]
    val enumPart =
      Some[List[alloy_sig]](enumsToAlloySigs(enums)): Option[List[alloy_sig]]
    val entPart = entitiesToAlloySigs(ents): Option[List[alloy_sig]]
    val statePart =
      stateOrInputSig("State", stateFields): Option[List[alloy_sig]]
    val inputPart =
      stateOrInputSig("Inputs", inputFields): Option[List[alloy_sig]]
    val postPart =
      (includeStatePost match {
        case true  => stateOrInputSig("StatePost", stateFields)
        case false => Some[List[alloy_sig]](Nil)
      }): Option[List[alloy_sig]];
    optConcat(
      boolPart,
      optConcat(entPart, optConcat(enumPart, optConcat(statePart, optConcat(inputPart, postPart))))
    )
  }

  def fieldElementSigNameAlloy(x0: type_expr): Option[String] = x0 match {
    case NamedTypeF(name, uu) => Some[String](mapAlloyPrimitive(name))
    case SetTypeF(NamedTypeF(name, uv), uw) =>
      Some[String](mapAlloyPrimitive(name))
    case OptionTypeF(NamedTypeF(name, ux), uy) =>
      Some[String](mapAlloyPrimitive(name))
    case SetTypeF(SetTypeF(vb, vc), va)                 => None
    case SetTypeF(MapTypeF(vb, vc, vd), va)             => None
    case SetTypeF(SeqTypeF(vb, vc), va)                 => None
    case SetTypeF(OptionTypeF(vb, vc), va)              => None
    case SetTypeF(RelationTypeF(vb, vc, vd, ve), va)    => None
    case MapTypeF(v, va, vb)                            => None
    case SeqTypeF(v, va)                                => None
    case OptionTypeF(SetTypeF(vb, vc), va)              => None
    case OptionTypeF(MapTypeF(vb, vc, vd), va)          => None
    case OptionTypeF(SeqTypeF(vb, vc), va)              => None
    case OptionTypeF(OptionTypeF(vb, vc), va)           => None
    case OptionTypeF(RelationTypeF(vb, vc, vd, ve), va) => None
    case RelationTypeF(v, va, vb, vc)                   => None
  }

  def domainSigNameAlloy(
      e: expr,
      stateFields: List[(String, type_expr)],
      inputFields: List[(String, type_expr)],
      entities: List[entity_decl],
      enums: List[enum_decl]
  ): Option[String] =
    e match {
      case BinaryOpF(_, _, _, _)         => None
      case UnaryOpF(_, _, _)             => None
      case QuantifierF(_, _, _, _)       => None
      case SomeWrapF(_, _)               => None
      case TheF(_, _, _, _)              => None
      case FieldAccessF(_, _, _)         => None
      case EnumAccessF(_, _, _)          => None
      case IndexF(_, _, _)               => None
      case CallF(_, _, _)                => None
      case PrimeF(_, _)                  => None
      case PreF(_, _)                    => None
      case WithF(_, _, _)                => None
      case IfF(_, _, _, _)               => None
      case LetF(_, _, _, _)              => None
      case LambdaF(_, _, _)              => None
      case ConstructorF(_, _, _)         => None
      case SetLiteralF(_, _)             => None
      case MapLiteralF(_, _)             => None
      case SetComprehensionF(_, _, _, _) => None
      case SeqLiteralF(_, _)             => None
      case MatchesF(_, _, _)             => None
      case IntLitF(_, _)                 => None
      case FloatLitF(_, _)               => None
      case StringLitF(_, _)              => None
      case BoolLitF(_, _)                => None
      case NoneLitF(_)                   => None
      case IdentifierF(name, _) =>
        map_of[String, type_expr](stateFields, name) match {
          case None =>
            map_of[String, type_expr](inputFields, name) match {
              case None =>
                entityNameInList(entities, name) match {
                  case None => enumNameInList(enums, name) match {
                      case None    => Some[String](name)
                      case Some(a) => Some[String](a)
                    }
                  case Some(a) => Some[String](a)
                }
              case Some(a) => fieldElementSigNameAlloy(a)
            }
          case Some(a) => fieldElementSigNameAlloy(a)
        }
    }

  def classificationMethod(x0: operation_classification): http_method = x0 match {
    case OperationClassification(uu, uv, m, uw, ux, uy, uz) => m
  }

  def isAutoIncrement(x0: canonical_type): Boolean = x0 match {
    case CtSerial4()      => true
    case CtSerial8()      => true
    case CtText()         => false
    case CtVarchar(v)     => false
    case CtInt4()         => false
    case CtInt8()         => false
    case CtFloat8()       => false
    case CtBool()         => false
    case CtTimestamptz()  => false
    case CtDateOnly()     => false
    case CtUuid()         => false
    case CtNumeric(v, va) => false
    case CtBytes()        => false
    case CtJson()         => false
  }

  def mysqlTypeRender(x0: canonical_type): String = x0 match {
    case CtText()        => "VARCHAR(255)"
    case CtVarchar(n)    => "VARCHAR(" + showInt(n) + ")"
    case CtInt4()        => "INT"
    case CtSerial4()     => "INT"
    case CtInt8()        => "BIGINT"
    case CtSerial8()     => "BIGINT"
    case CtFloat8()      => "DOUBLE"
    case CtBool()        => "TINYINT(1)"
    case CtTimestamptz() => "DATETIME"
    case CtDateOnly()    => "DATE"
    case CtUuid()        => "CHAR(36)"
    case CtNumeric(p, Some(s)) =>
      "DECIMAL(" + showInt(p) + ", " + showInt(s) + ")"
    case CtNumeric(p, None) => "DECIMAL(" + showInt(p) + ")"
    case CtBytes()          => "LONGBLOB"
    case CtJson()           => "JSON"
  }

  def operEnsures(x0: operation_decl): List[expr] = x0 match {
    case OperationDeclFull(x1, x2, x3, x4, x5, x6, x7) => x5
  }

  def operOutputs(x0: operation_decl): List[param_decl] = x0 match {
    case OperationDeclFull(x1, x2, x3, x4, x5, x6, x7) => x3
  }

  def qbdKind(x0: quantifier_binding): binding_kind = x0 match {
    case QuantifierBindingFull(x1, x2, x3, x4) => x3
  }

  def qbdSpan(x0: quantifier_binding): Option[span_t] = x0 match {
    case QuantifierBindingFull(x1, x2, x3, x4) => x4
  }

  def extractMapEntriesPairs(x0: List[map_entry]): List[(expr, expr)] = x0 match {
    case Nil                            => Nil
    case MapEntryFull(k, v, uu) :: rest => (k, v) :: extractMapEntriesPairs(rest)
  }

  def extractMapEntries(x0: expr): Option[List[(expr, expr)]] = x0 match {
    case MapLiteralF(entries, uu) =>
      Some[List[(expr, expr)]](extractMapEntriesPairs(entries))
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def emptyServiceIrFull(nm: String): service_ir =
    ServiceIRFull(nm, Nil, Nil, Nil, Nil, None, Nil, Nil, Nil, Nil, Nil, Nil, Nil, None, Nil, None)

  def extractFieldAssignRhs(x0: expr, field: String): List[expr] = (x0, field) match {
    case (BinaryOpF(BEq(), lhs, rhs, uu), field) =>
      assignsField(lhs, field) match {
        case true  => List(rhs)
        case false => Nil
      }
    case (BinaryOpF(BAnd(), l, r, uv), field) =>
      extractFieldAssignRhs(l, field) ++ extractFieldAssignRhs(r, field)
    case (BinaryOpF(BOr(), va, vb, vc), ux)        => Nil
    case (BinaryOpF(BImplies(), va, vb, vc), ux)   => Nil
    case (BinaryOpF(BIff(), va, vb, vc), ux)       => Nil
    case (BinaryOpF(BNeq(), va, vb, vc), ux)       => Nil
    case (BinaryOpF(BLt(), va, vb, vc), ux)        => Nil
    case (BinaryOpF(BGt(), va, vb, vc), ux)        => Nil
    case (BinaryOpF(BLe(), va, vb, vc), ux)        => Nil
    case (BinaryOpF(BGe(), va, vb, vc), ux)        => Nil
    case (BinaryOpF(BIn(), va, vb, vc), ux)        => Nil
    case (BinaryOpF(BNotIn(), va, vb, vc), ux)     => Nil
    case (BinaryOpF(BSubset(), va, vb, vc), ux)    => Nil
    case (BinaryOpF(BUnion(), va, vb, vc), ux)     => Nil
    case (BinaryOpF(BIntersect(), va, vb, vc), ux) => Nil
    case (BinaryOpF(BDiff(), va, vb, vc), ux)      => Nil
    case (BinaryOpF(BAdd(), va, vb, vc), ux)       => Nil
    case (BinaryOpF(BSub(), va, vb, vc), ux)       => Nil
    case (BinaryOpF(BMul(), va, vb, vc), ux)       => Nil
    case (BinaryOpF(BDiv(), va, vb, vc), ux)       => Nil
    case (UnaryOpF(v, va, vb), ux)                 => Nil
    case (QuantifierF(v, va, vb, vc), ux)          => Nil
    case (SomeWrapF(v, va), ux)                    => Nil
    case (TheF(v, va, vb, vc), ux)                 => Nil
    case (FieldAccessF(v, va, vb), ux)             => Nil
    case (EnumAccessF(v, va, vb), ux)              => Nil
    case (IndexF(v, va, vb), ux)                   => Nil
    case (CallF(v, va, vb), ux)                    => Nil
    case (PrimeF(v, va), ux)                       => Nil
    case (PreF(v, va), ux)                         => Nil
    case (WithF(v, va, vb), ux)                    => Nil
    case (IfF(v, va, vb, vc), ux)                  => Nil
    case (LetF(v, va, vb, vc), ux)                 => Nil
    case (LambdaF(v, va, vb), ux)                  => Nil
    case (ConstructorF(v, va, vb), ux)             => Nil
    case (SetLiteralF(v, va), ux)                  => Nil
    case (MapLiteralF(v, va), ux)                  => Nil
    case (SetComprehensionF(v, va, vb, vc), ux)    => Nil
    case (SeqLiteralF(v, va), ux)                  => Nil
    case (MatchesF(v, va, vb), ux)                 => Nil
    case (IntLitF(v, va), ux)                      => Nil
    case (FloatLitF(v, va), ux)                    => Nil
    case (StringLitF(v, va), ux)                   => Nil
    case (BoolLitF(v, va), ux)                     => Nil
    case (NoneLitF(v), ux)                         => Nil
    case (IdentifierF(v, va), ux)                  => Nil
  }

  def ensuresRhsForField(ensures: List[expr], field: String): Option[expr] =
    maps[expr, expr]((e: expr) => extractFieldAssignRhs(e, field), ensures) match {
      case Nil         => None
      case List(r)     => Some[expr](r)
      case _ :: _ :: _ => None
    }

  def entityNameFromType(x0: type_expr): Option[String] = x0 match {
    case RelationTypeF(uu, uv, to, uw) => typeName(to)
    case NamedTypeF(n, ux)             => Some[String](n)
    case SetTypeF(inner, uy)           => entityNameFromType(inner)
    case SeqTypeF(inner, uz)           => entityNameFromType(inner)
    case OptionTypeF(inner, va)        => entityNameFromType(inner)
    case MapTypeF(vb, v, vc)           => entityNameFromType(v)
  }

  def enumValuesForField(
      f: field_decl,
      enums: List[enum_decl],
      aliases: List[type_alias_decl]
  ): Option[List[String]] =
    enumValuesForType(Suc(size_list[type_alias_decl](aliases)), fieldTypeFull(f), enums, aliases)

  def flattenInheritance(x0: service_ir): service_ir = x0 match {
    case ServiceIRFull(a, b, c, d, e, f, g, h, i, j, k, l, m, n, sec, p) =>
      ServiceIRFull(
        a,
        b,
        map[entity_decl, entity_decl](
          (aa: entity_decl) =>
            flatten_entity(c, aa),
          c
        ),
        d,
        e,
        f,
        g,
        h,
        i,
        j,
        k,
        l,
        m,
        n,
        sec,
        p
      )
  }

  def isInputCollectionType(x0: type_expr): Boolean = x0 match {
    case SetTypeF(uu, uv)             => true
    case SeqTypeF(uw, ux)             => true
    case MapTypeF(uy, uz, va)         => true
    case NamedTypeF(v, va)            => false
    case OptionTypeF(v, va)           => false
    case RelationTypeF(v, va, vc, vd) => false
  }

  def hasCollectionInput(ps: List[param_decl]): Boolean =
    list_ex[param_decl](
      (p: param_decl) =>
        isInputCollectionType(paramTypeFull(p)),
      ps
    )

  def withInfoFieldNames(x0: with_info): List[String] = x0 match {
    case WithInfoFull(fs, uu) => fs
  }

  def callSelfAllNames(x0: expr): List[String] = x0 match {
    case CallF(c, uu, uv) => c match {
        case BinaryOpF(_, _, _, _)         => Nil
        case UnaryOpF(_, _, _)             => Nil
        case QuantifierF(_, _, _, _)       => Nil
        case SomeWrapF(_, _)               => Nil
        case TheF(_, _, _, _)              => Nil
        case FieldAccessF(_, _, _)         => Nil
        case EnumAccessF(_, _, _)          => Nil
        case IndexF(_, _, _)               => Nil
        case CallF(_, _, _)                => Nil
        case PrimeF(_, _)                  => Nil
        case PreF(_, _)                    => Nil
        case WithF(_, _, _)                => Nil
        case IfF(_, _, _, _)               => Nil
        case LetF(_, _, _, _)              => Nil
        case LambdaF(_, _, _)              => Nil
        case ConstructorF(_, _, _)         => Nil
        case SetLiteralF(_, _)             => Nil
        case MapLiteralF(_, _)             => Nil
        case SetComprehensionF(_, _, _, _) => Nil
        case SeqLiteralF(_, _)             => Nil
        case MatchesF(_, _, _)             => Nil
        case IntLitF(_, _)                 => Nil
        case FloatLitF(_, _)               => Nil
        case StringLitF(_, _)              => Nil
        case BoolLitF(_, _)                => Nil
        case NoneLitF(_)                   => Nil
        case IdentifierF(n, _)             => List(n)
      }
    case BinaryOpF(v, va, vb, vc)         => Nil
    case UnaryOpF(v, va, vb)              => Nil
    case QuantifierF(v, va, vb, vc)       => Nil
    case SomeWrapF(v, va)                 => Nil
    case TheF(v, va, vb, vc)              => Nil
    case FieldAccessF(v, va, vb)          => Nil
    case EnumAccessF(v, va, vb)           => Nil
    case IndexF(v, va, vb)                => Nil
    case PrimeF(v, va)                    => Nil
    case PreF(v, va)                      => Nil
    case WithF(v, va, vb)                 => Nil
    case IfF(v, va, vb, vc)               => Nil
    case LetF(v, va, vb, vc)              => Nil
    case LambdaF(v, va, vb)               => Nil
    case ConstructorF(v, va, vb)          => Nil
    case SetLiteralF(v, va)               => Nil
    case MapLiteralF(v, va)               => Nil
    case SetComprehensionF(v, va, vb, vc) => Nil
    case SeqLiteralF(v, va)               => Nil
    case MatchesF(v, va, vb)              => Nil
    case IntLitF(v, va)                   => Nil
    case FloatLitF(v, va)                 => Nil
    case StringLitF(v, va)                => Nil
    case BoolLitF(v, va)                  => Nil
    case NoneLitF(v)                      => Nil
    case IdentifierF(v, va)               => Nil
  }

  def callSelfFilteredNames(filt: List[String], x1: expr): List[String] =
    (filt, x1) match {
      case (filt, CallF(c, uu, uv)) =>
        c match {
          case BinaryOpF(_, _, _, _)         => Nil
          case UnaryOpF(_, _, _)             => Nil
          case QuantifierF(_, _, _, _)       => Nil
          case SomeWrapF(_, _)               => Nil
          case TheF(_, _, _, _)              => Nil
          case FieldAccessF(_, _, _)         => Nil
          case EnumAccessF(_, _, _)          => Nil
          case IndexF(_, _, _)               => Nil
          case CallF(_, _, _)                => Nil
          case PrimeF(_, _)                  => Nil
          case PreF(_, _)                    => Nil
          case WithF(_, _, _)                => Nil
          case IfF(_, _, _, _)               => Nil
          case LetF(_, _, _, _)              => Nil
          case LambdaF(_, _, _)              => Nil
          case ConstructorF(_, _, _)         => Nil
          case SetLiteralF(_, _)             => Nil
          case MapLiteralF(_, _)             => Nil
          case SetComprehensionF(_, _, _, _) => Nil
          case SeqLiteralF(_, _)             => Nil
          case MatchesF(_, _, _)             => Nil
          case IntLitF(_, _)                 => Nil
          case FloatLitF(_, _)               => Nil
          case StringLitF(_, _)              => Nil
          case BoolLitF(_, _)                => Nil
          case NoneLitF(_)                   => Nil
          case IdentifierF(n, _) =>
            membera[String](filt, n) match {
              case true  => List(n)
              case false => Nil
            }
        }
      case (uw, BinaryOpF(v, va, vb, vc))         => Nil
      case (uw, UnaryOpF(v, va, vb))              => Nil
      case (uw, QuantifierF(v, va, vb, vc))       => Nil
      case (uw, SomeWrapF(v, va))                 => Nil
      case (uw, TheF(v, va, vb, vc))              => Nil
      case (uw, FieldAccessF(v, va, vb))          => Nil
      case (uw, EnumAccessF(v, va, vb))           => Nil
      case (uw, IndexF(v, va, vb))                => Nil
      case (uw, PrimeF(v, va))                    => Nil
      case (uw, PreF(v, va))                      => Nil
      case (uw, WithF(v, va, vb))                 => Nil
      case (uw, IfF(v, va, vb, vc))               => Nil
      case (uw, LetF(v, va, vb, vc))              => Nil
      case (uw, LambdaF(v, va, vb))               => Nil
      case (uw, ConstructorF(v, va, vb))          => Nil
      case (uw, SetLiteralF(v, va))               => Nil
      case (uw, MapLiteralF(v, va))               => Nil
      case (uw, SetComprehensionF(v, va, vb, vc)) => Nil
      case (uw, SeqLiteralF(v, va))               => Nil
      case (uw, MatchesF(v, va, vb))              => Nil
      case (uw, IntLitF(v, va))                   => Nil
      case (uw, FloatLitF(v, va))                 => Nil
      case (uw, StringLitF(v, va))                => Nil
      case (uw, BoolLitF(v, va))                  => Nil
      case (uw, NoneLitF(v))                      => Nil
      case (uw, IdentifierF(v, va))               => Nil
    }

  def collectCallNames(e: expr, filt: List[String]): List[String] =
    maps[expr, String]((a: expr) => callSelfFilteredNames(filt, a), allSubexprs(e))

  def collectExprNames(e: expr): List[String] =
    maps[expr, String]((a: expr) => exprSelfNames(a), allSubexprs(e))

  def collectTypeNames(x0: type_expr): List[String] = x0 match {
    case NamedTypeF(n, uu)           => List(n)
    case SetTypeF(t, uv)             => collectTypeNames(t)
    case SeqTypeF(t, uw)             => collectTypeNames(t)
    case OptionTypeF(t, ux)          => collectTypeNames(t)
    case MapTypeF(k, v, uy)          => collectTypeNames(k) ++ collectTypeNames(v)
    case RelationTypeF(f, uz, t, va) => collectTypeNames(f) ++ collectTypeNames(t)
  }

  def aggregateForName(name: String): Option[trigger_aggregate] =
    name == "sum" match {
      case true => Some[trigger_aggregate](SumAgg())
      case false => name == "count" match {
          case true => Some[trigger_aggregate](CountAgg())
          case false => name == "min" match {
              case true => Some[trigger_aggregate](MinAgg())
              case false => name == "max" match {
                  case true  => Some[trigger_aggregate](MaxAgg())
                  case false => None
                }
            }
        }
    }

  def lambdaProjection(body: expr): Option[String] =
    body match {
      case BinaryOpF(_, _, _, _)                             => None
      case UnaryOpF(_, _, _)                                 => None
      case QuantifierF(_, _, _, _)                           => None
      case SomeWrapF(_, _)                                   => None
      case TheF(_, _, _, _)                                  => None
      case FieldAccessF(BinaryOpF(_, _, _, _), _, _)         => None
      case FieldAccessF(UnaryOpF(_, _, _), _, _)             => None
      case FieldAccessF(QuantifierF(_, _, _, _), _, _)       => None
      case FieldAccessF(SomeWrapF(_, _), _, _)               => None
      case FieldAccessF(TheF(_, _, _, _), _, _)              => None
      case FieldAccessF(FieldAccessF(_, _, _), _, _)         => None
      case FieldAccessF(EnumAccessF(_, _, _), _, _)          => None
      case FieldAccessF(IndexF(_, _, _), _, _)               => None
      case FieldAccessF(CallF(_, _, _), _, _)                => None
      case FieldAccessF(PrimeF(_, _), _, _)                  => None
      case FieldAccessF(PreF(_, _), _, _)                    => None
      case FieldAccessF(WithF(_, _, _), _, _)                => None
      case FieldAccessF(IfF(_, _, _, _), _, _)               => None
      case FieldAccessF(LetF(_, _, _, _), _, _)              => None
      case FieldAccessF(LambdaF(_, _, _), _, _)              => None
      case FieldAccessF(ConstructorF(_, _, _), _, _)         => None
      case FieldAccessF(SetLiteralF(_, _), _, _)             => None
      case FieldAccessF(MapLiteralF(_, _), _, _)             => None
      case FieldAccessF(SetComprehensionF(_, _, _, _), _, _) => None
      case FieldAccessF(SeqLiteralF(_, _), _, _)             => None
      case FieldAccessF(MatchesF(_, _, _), _, _)             => None
      case FieldAccessF(IntLitF(_, _), _, _)                 => None
      case FieldAccessF(FloatLitF(_, _), _, _)               => None
      case FieldAccessF(StringLitF(_, _), _, _)              => None
      case FieldAccessF(BoolLitF(_, _), _, _)                => None
      case FieldAccessF(NoneLitF(_), _, _)                   => None
      case FieldAccessF(IdentifierF(_, _), field, _)         => Some[String](field)
      case EnumAccessF(_, _, _)                              => None
      case IndexF(_, _, _)                                   => None
      case CallF(_, _, _)                                    => None
      case PrimeF(_, _)                                      => None
      case PreF(_, _)                                        => None
      case WithF(_, _, _)                                    => None
      case IfF(_, _, _, _)                                   => None
      case LetF(_, _, _, _)                                  => None
      case LambdaF(_, _, _)                                  => None
      case ConstructorF(_, _, _)                             => None
      case SetLiteralF(_, _)                                 => None
      case MapLiteralF(_, _)                                 => None
      case SetComprehensionF(_, _, _, _)                     => None
      case SeqLiteralF(_, _)                                 => None
      case MatchesF(_, _, _)                                 => None
      case IntLitF(_, _)                                     => None
      case FloatLitF(_, _)                                   => None
      case StringLitF(_, _)                                  => None
      case BoolLitF(_, _)                                    => None
      case NoneLitF(_)                                       => None
      case IdentifierF(_, _)                                 => None
    }

  def partialIndexSpec(tableNm: String, col: String, filt: String): index_spec =
    IndexSpec("idx_" + tableNm + "_" + col + "_partial", List(col), false, Some[String](filt))

  def asBoolLit(x0: expr): Option[Boolean] = x0 match {
    case BoolLitF(b, uu)                  => Some[Boolean](b)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case StringLitF(v, va)                => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def targetsOf(x0: List[(String, (String, String))]): List[String] = x0 match {
    case Nil                   => Nil
    case (uu, (t, uv)) :: rest => t :: targetsOf(rest)
  }

  def enumDeclName(x0: enum_decl): String = x0 match {
    case EnumDeclFull(n, uu, uv) => n
  }

  def foldVerifier(exprs: List[expr]): verifier_tool =
    list_ex[expr]((a: expr) => requiresAlloy(a), exprs) match {
      case true  => VtAlloy()
      case false => VtZ3()
    }

  def operationRequires(x0: operation_decl): List[expr] = x0 match {
    case OperationDeclFull(uu, uv, uw, requiresa, ux, uy, uz) => requiresa
  }

  def trustEnabled(enums: List[String], op: operation_decl, ir: service_ir): trust_level =
    foldTrust(enums, operationRequires(op) ++ invariantBodies(ir))

  def classificationSignals(x0: operation_classification): analysis_signals = x0 match {
    case OperationClassification(uu, uv, uw, ux, uy, uz, sg) => sg
  }

  def foldExternItems(
      x0: List[extern_item],
      externs: List[(String, extern_info)],
      patterns: List[String]
  ): (List[(String, extern_info)], List[String]) =
    (x0, externs, patterns) match {
      case (Nil, externs, patterns) => (externs, patterns)
      case (EiExtern(name, arity, kind) :: rest, externs, patterns) =>
        foldExternItems(rest, upsertExtern(externs, name, arity, kind), patterns)
      case (EiPattern(p) :: rest, externs, patterns) =>
        foldExternItems(
          rest,
          externs,
          string_in_list(p, patterns) match {
            case true  => patterns
            case false => patterns ++ List(p)
          }
        )
    }

  def sqliteTypeRender(x0: canonical_type): String = x0 match {
    case CtText()        => "TEXT"
    case CtVarchar(uu)   => "TEXT"
    case CtInt4()        => "INTEGER"
    case CtSerial4()     => "INTEGER"
    case CtInt8()        => "INTEGER"
    case CtSerial8()     => "INTEGER"
    case CtFloat8()      => "REAL"
    case CtBool()        => "BOOLEAN"
    case CtTimestamptz() => "DATETIME"
    case CtDateOnly()    => "DATE"
    case CtUuid()        => "TEXT"
    case CtNumeric(p, Some(s)) =>
      "NUMERIC(" + showInt(p) + ", " + showInt(s) + ")"
    case CtNumeric(p, None) => "NUMERIC(" + showInt(p) + ")"
    case CtBytes()          => "BLOB"
    case CtJson()           => "TEXT"
  }

  def cvrProperty(x0: convention_rule): String = x0 match {
    case ConventionRuleFull(x1, x2, x3, x4, x5) => x2
  }

  def operRequires(x0: operation_decl): List[expr] = x0 match {
    case OperationDeclFull(x1, x2, x3, x4, x5, x6, x7) => x4
  }

  def detectCreatePattern(es: List[expr], stateFields: List[String]): Option[String] =
    maps[expr, String]((a: expr) => createPatternOf(stateFields, a), flattenEnsures(es)) match {
      case Nil    => None
      case x :: _ => Some[String](x)
    }

  def detectDeletePattern(es: List[expr], stateFields: List[String]): Option[String] =
    maps[expr, String]((a: expr) => deletePatternOf(stateFields, a), flattenEnsures(es)) match {
      case Nil    => None
      case x :: _ => Some[String](x)
    }

  def preservedRelationOf(stateFields: List[String], e: expr): List[String] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)                                 => Nil
      case BinaryOpF(BOr(), _, _, _)                                  => Nil
      case BinaryOpF(BImplies(), _, _, _)                             => Nil
      case BinaryOpF(BIff(), _, _, _)                                 => Nil
      case BinaryOpF(BEq(), BinaryOpF(_, _, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), UnaryOpF(_, _, _), _, _)                  => Nil
      case BinaryOpF(BEq(), QuantifierF(_, _, _, _), _, _)            => Nil
      case BinaryOpF(BEq(), SomeWrapF(_, _), _, _)                    => Nil
      case BinaryOpF(BEq(), TheF(_, _, _, _), _, _)                   => Nil
      case BinaryOpF(BEq(), FieldAccessF(_, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), EnumAccessF(_, _, _), _, _)               => Nil
      case BinaryOpF(BEq(), IndexF(_, _, _), _, _)                    => Nil
      case BinaryOpF(BEq(), CallF(_, _, _), _, _)                     => Nil
      case BinaryOpF(BEq(), PrimeF(BinaryOpF(_, _, _, _), _), _, _)   => Nil
      case BinaryOpF(BEq(), PrimeF(UnaryOpF(_, _, _), _), _, _)       => Nil
      case BinaryOpF(BEq(), PrimeF(QuantifierF(_, _, _, _), _), _, _) => Nil
      case BinaryOpF(BEq(), PrimeF(SomeWrapF(_, _), _), _, _)         => Nil
      case BinaryOpF(BEq(), PrimeF(TheF(_, _, _, _), _), _, _)        => Nil
      case BinaryOpF(BEq(), PrimeF(FieldAccessF(_, _, _), _), _, _)   => Nil
      case BinaryOpF(BEq(), PrimeF(EnumAccessF(_, _, _), _), _, _)    => Nil
      case BinaryOpF(BEq(), PrimeF(IndexF(_, _, _), _), _, _)         => Nil
      case BinaryOpF(BEq(), PrimeF(CallF(_, _, _), _), _, _)          => Nil
      case BinaryOpF(BEq(), PrimeF(PrimeF(_, _), _), _, _)            => Nil
      case BinaryOpF(BEq(), PrimeF(PreF(_, _), _), _, _)              => Nil
      case BinaryOpF(BEq(), PrimeF(WithF(_, _, _), _), _, _)          => Nil
      case BinaryOpF(BEq(), PrimeF(IfF(_, _, _, _), _), _, _)         => Nil
      case BinaryOpF(BEq(), PrimeF(LetF(_, _, _, _), _), _, _)        => Nil
      case BinaryOpF(BEq(), PrimeF(LambdaF(_, _, _), _), _, _)        => Nil
      case BinaryOpF(BEq(), PrimeF(ConstructorF(_, _, _), _), _, _)   => Nil
      case BinaryOpF(BEq(), PrimeF(SetLiteralF(_, _), _), _, _)       => Nil
      case BinaryOpF(BEq(), PrimeF(MapLiteralF(_, _), _), _, _)       => Nil
      case BinaryOpF(BEq(), PrimeF(SetComprehensionF(_, _, _, _), _), _, _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(SeqLiteralF(_, _), _), _, _)                       => Nil
      case BinaryOpF(BEq(), PrimeF(MatchesF(_, _, _), _), _, _)                       => Nil
      case BinaryOpF(BEq(), PrimeF(IntLitF(_, _), _), _, _)                           => Nil
      case BinaryOpF(BEq(), PrimeF(FloatLitF(_, _), _), _, _)                         => Nil
      case BinaryOpF(BEq(), PrimeF(StringLitF(_, _), _), _, _)                        => Nil
      case BinaryOpF(BEq(), PrimeF(BoolLitF(_, _), _), _, _)                          => Nil
      case BinaryOpF(BEq(), PrimeF(NoneLitF(_), _), _, _)                             => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BinaryOpF(_, _, _, _), _)   => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), UnaryOpF(_, _, _), _)       => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), QuantifierF(_, _, _, _), _) => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SomeWrapF(_, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), TheF(_, _, _, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), FieldAccessF(_, _, _), _) => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), EnumAccessF(_, _, _), _)  => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), IndexF(_, _, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), CallF(_, _, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), PrimeF(_, _), _) => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), PreF(_, _), _)   => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), WithF(_, _, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), IfF(_, _, _, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), LetF(_, _, _, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), LambdaF(_, _, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), ConstructorF(_, _, _), _)         => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SetLiteralF(_, _), _)             => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), MapLiteralF(_, _), _)             => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SetComprehensionF(_, _, _, _), _) => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), SeqLiteralF(_, _), _)             => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), MatchesF(_, _, _), _)             => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), IntLitF(_, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), FloatLitF(_, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), StringLitF(_, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), BoolLitF(_, _), _) =>
        Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(_, _), _), NoneLitF(_), _) => Nil
      case BinaryOpF(BEq(), PrimeF(IdentifierF(l, _), _), IdentifierF(r, _), _) =>
        l == r && string_in_list(l, stateFields) match {
          case true  => List(l)
          case false => Nil
        }
      case BinaryOpF(BEq(), PreF(_, _), _, _)                    => Nil
      case BinaryOpF(BEq(), WithF(_, _, _), _, _)                => Nil
      case BinaryOpF(BEq(), IfF(_, _, _, _), _, _)               => Nil
      case BinaryOpF(BEq(), LetF(_, _, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), LambdaF(_, _, _), _, _)              => Nil
      case BinaryOpF(BEq(), ConstructorF(_, _, _), _, _)         => Nil
      case BinaryOpF(BEq(), SetLiteralF(_, _), _, _)             => Nil
      case BinaryOpF(BEq(), MapLiteralF(_, _), _, _)             => Nil
      case BinaryOpF(BEq(), SetComprehensionF(_, _, _, _), _, _) => Nil
      case BinaryOpF(BEq(), SeqLiteralF(_, _), _, _)             => Nil
      case BinaryOpF(BEq(), MatchesF(_, _, _), _, _)             => Nil
      case BinaryOpF(BEq(), IntLitF(_, _), _, _)                 => Nil
      case BinaryOpF(BEq(), FloatLitF(_, _), _, _)               => Nil
      case BinaryOpF(BEq(), StringLitF(_, _), _, _)              => Nil
      case BinaryOpF(BEq(), BoolLitF(_, _), _, _)                => Nil
      case BinaryOpF(BEq(), NoneLitF(_), _, _)                   => Nil
      case BinaryOpF(BEq(), IdentifierF(_, _), _, _)             => Nil
      case BinaryOpF(BNeq(), _, _, _)                            => Nil
      case BinaryOpF(BLt(), _, _, _)                             => Nil
      case BinaryOpF(BGt(), _, _, _)                             => Nil
      case BinaryOpF(BLe(), _, _, _)                             => Nil
      case BinaryOpF(BGe(), _, _, _)                             => Nil
      case BinaryOpF(BIn(), _, _, _)                             => Nil
      case BinaryOpF(BNotIn(), _, _, _)                          => Nil
      case BinaryOpF(BSubset(), _, _, _)                         => Nil
      case BinaryOpF(BUnion(), _, _, _)                          => Nil
      case BinaryOpF(BIntersect(), _, _, _)                      => Nil
      case BinaryOpF(BDiff(), _, _, _)                           => Nil
      case BinaryOpF(BAdd(), _, _, _)                            => Nil
      case BinaryOpF(BSub(), _, _, _)                            => Nil
      case BinaryOpF(BMul(), _, _, _)                            => Nil
      case BinaryOpF(BDiv(), _, _, _)                            => Nil
      case UnaryOpF(_, _, _)                                     => Nil
      case QuantifierF(_, _, _, _)                               => Nil
      case SomeWrapF(_, _)                                       => Nil
      case TheF(_, _, _, _)                                      => Nil
      case FieldAccessF(_, _, _)                                 => Nil
      case EnumAccessF(_, _, _)                                  => Nil
      case IndexF(_, _, _)                                       => Nil
      case CallF(_, _, _)                                        => Nil
      case PrimeF(_, _)                                          => Nil
      case PreF(_, _)                                            => Nil
      case WithF(_, _, _)                                        => Nil
      case IfF(_, _, _, _)                                       => Nil
      case LetF(_, _, _, _)                                      => Nil
      case LambdaF(_, _, _)                                      => Nil
      case ConstructorF(_, _, _)                                 => Nil
      case SetLiteralF(_, _)                                     => Nil
      case MapLiteralF(_, _)                                     => Nil
      case SetComprehensionF(_, _, _, _)                         => Nil
      case SeqLiteralF(_, _)                                     => Nil
      case MatchesF(_, _, _)                                     => Nil
      case IntLitF(_, _)                                         => Nil
      case FloatLitF(_, _)                                       => Nil
      case StringLitF(_, _)                                      => Nil
      case BoolLitF(_, _)                                        => Nil
      case NoneLitF(_)                                           => Nil
      case IdentifierF(_, _)                                     => Nil
    }

  def state_fieldNameFull(x0: state_field_decl): String = x0 match {
    case StateFieldDeclFull(n, uu, uv) => n
  }

  def state_fieldTypeFull(x0: state_field_decl): type_expr = x0 match {
    case StateFieldDeclFull(uu, t, uv) => t
  }

  def walkUndefinedExpr_bindings(
      x0: List[quantifier_binding],
      wq: List[String]
  ): List[(String, Option[span_t])] =
    (x0, wq) match {
      case (Nil, wq) => Nil
      case (QuantifierBindingFull(v, d, wr, ws) :: bs, scope) =>
        walkUndefinedExpr(d, scope) ++ walkUndefinedExpr_bindings(bs, v :: scope)
    }

  def walkUndefinedExpr_entries(
      x0: List[map_entry],
      wo: List[String]
  ): List[(String, Option[span_t])] =
    (x0, wo) match {
      case (Nil, wo) => Nil
      case (MapEntryFull(k, v, wp) :: es, scope) =>
        walkUndefinedExpr(k, scope) ++
          (walkUndefinedExpr(v, scope) ++ walkUndefinedExpr_entries(es, scope))
    }

  def walkUndefinedExpr_fields(
      x0: List[field_assign],
      wl: List[String]
  ): List[(String, Option[span_t])] =
    (x0, wl) match {
      case (Nil, wl) => Nil
      case (FieldAssignFull(wm, v, wn) :: fs, scope) =>
        walkUndefinedExpr(v, scope) ++ walkUndefinedExpr_fields(fs, scope)
    }

  def walkUndefinedExpr_list(x0: List[expr], wk: List[String]): List[(String, Option[span_t])] =
    (x0, wk) match {
      case (Nil, wk) => Nil
      case (x :: xs, scope) =>
        walkUndefinedExpr(x, scope) ++ walkUndefinedExpr_list(xs, scope)
    }

  def walkUndefinedExpr(x0: expr, scope: List[String]): List[(String, Option[span_t])] =
    (x0, scope) match {
      case (IdentifierF(n, sp), scope) =>
        membera[String](scope, n) match {
          case true  => Nil
          case false => List((n, sp))
        }
      case (BinaryOpF(uu, l, r, uv), scope) =>
        walkUndefinedExpr(l, scope) ++ walkUndefinedExpr(r, scope)
      case (UnaryOpF(uw, e, ux), scope)     => walkUndefinedExpr(e, scope)
      case (FieldAccessF(b, uy, uz), scope) => walkUndefinedExpr(b, scope)
      case (EnumAccessF(b, va, vb), scope)  => walkUndefinedExpr(b, scope)
      case (IndexF(b, i, vc), scope) =>
        walkUndefinedExpr(b, scope) ++ walkUndefinedExpr(i, scope)
      case (CallF(c, args, vd), scope) =>
        walkUndefinedExpr(c, scope) ++ walkUndefinedExpr_list(args, scope)
      case (PrimeF(e, ve), scope) => walkUndefinedExpr(e, scope)
      case (PreF(e, vf), scope)   => walkUndefinedExpr(e, scope)
      case (WithF(b, upds, vg), scope) =>
        walkUndefinedExpr(b, scope) ++ walkUndefinedExpr_fields(upds, scope)
      case (IfF(c, t, e, vh), scope) =>
        walkUndefinedExpr(c, scope) ++
          (walkUndefinedExpr(t, scope) ++ walkUndefinedExpr(e, scope))
      case (LetF(v, vala, body, vi), scope) =>
        walkUndefinedExpr(vala, scope) ++ walkUndefinedExpr(body, v :: scope)
      case (LambdaF(p, body, vj), scope)     => walkUndefinedExpr(body, p :: scope)
      case (ConstructorF(vk, fs, vl), scope) => walkUndefinedExpr_fields(fs, scope)
      case (SetLiteralF(xs, vm), scope)      => walkUndefinedExpr_list(xs, scope)
      case (MapLiteralF(es, vn), scope)      => walkUndefinedExpr_entries(es, scope)
      case (SetComprehensionF(v, d, p, vo), scope) =>
        walkUndefinedExpr(d, scope) ++ walkUndefinedExpr(p, v :: scope)
      case (SeqLiteralF(xs, vp), scope) => walkUndefinedExpr_list(xs, scope)
      case (MatchesF(x, vq, vr), scope) => walkUndefinedExpr(x, scope)
      case (SomeWrapF(x, vs), scope)    => walkUndefinedExpr(x, scope)
      case (TheF(v, d, b, vt), scope) =>
        walkUndefinedExpr(d, scope) ++ walkUndefinedExpr(b, v :: scope)
      case (QuantifierF(vu, bs, body, vv), scope) =>
        walkUndefinedExpr_bindings(bs, scope) ++
          walkUndefinedExpr(body, qb_names(bs) ++ scope)
      case (IntLitF(vw, vx), vy)    => Nil
      case (FloatLitF(vz, wa), wb)  => Nil
      case (StringLitF(wc, wd), we) => Nil
      case (BoolLitF(wf, wg), wh)   => Nil
      case (NoneLitF(wi), wj)       => Nil
    }

  def readSchemaLifted(
      ename: String,
      decorated: List[(String, (schema_object, Boolean))]
  ): schema_object = {
    val fs =
      dropSensitive(nonIdDecorated(decorated)): List[(String, (schema_object, Boolean))];
    SchemaObject(
      Some[List[String]](List("object")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Some[List[String]]("id" :: requiredNames(fs)),
      Some[List[(String, schema_object)]](("id", integerSchema) ::
        fieldProps(fs)),
      None,
      None,
      Some[String]("Read view for " + ename),
      false
    )
  }

  def typeExprToSchema(
      ty: type_expr,
      bounds: openapi_bounds,
      enumOpt: Option[List[String]],
      am: List[(String, type_alias_decl)],
      em: List[(String, enum_decl)],
      ens: List[String]
  ): schema_object =
    typeExprToSchemaAux(openApiSchemaFuel(am), ty, bounds, enumOpt, am, em, ens)

  def triggerSourceForeignKey(x0: trigger_spec): String = x0 match {
    case TriggerSpec(uu, uv, uw, ux, uy, sfk, uz, va) => sfk
  }

  def trustRequires(enums: List[String], op: operation_decl): trust_level =
    foldTrust(enums, operationRequires(op))

  def alloyQuantifierClass(x0: quant_kind): alloy_quantifier_class = x0 match {
    case QAll()    => AqAll()
    case QSome()   => AqSome()
    case QExists() => AqExists()
    case QNo()     => AqNo()
  }

  def classificationStrategy(x0: operation_classification): synthesis_strategy =
    x0 match {
      case OperationClassification(uu, uv, uw, ux, uy, s, uz) => s
    }

  def synthesisStrategyLabel(s: synthesis_strategy): String =
    s match {
      case DirectEmit()   => "DIRECT_EMIT"
      case LlmSynthesis() => "LLM_SYNTHESIS"
    }

  def cvrQualifier(x0: convention_rule): Option[String] = x0 match {
    case ConventionRuleFull(x1, x2, x3, x4, x5) => x3
  }

  def equal_un_op(x0: un_op, x1: un_op): Boolean = (x0, x1) match {
    case (UCardinality(), UPower())       => false
    case (UPower(), UCardinality())       => false
    case (UNegate(), UPower())            => false
    case (UPower(), UNegate())            => false
    case (UNegate(), UCardinality())      => false
    case (UCardinality(), UNegate())      => false
    case (UNot(), UPower())               => false
    case (UPower(), UNot())               => false
    case (UNot(), UCardinality())         => false
    case (UCardinality(), UNot())         => false
    case (UNot(), UNegate())              => false
    case (UNegate(), UNot())              => false
    case (UPower(), UPower())             => true
    case (UCardinality(), UCardinality()) => true
    case (UNegate(), UNegate())           => true
    case (UNot(), UNot())                 => true
  }

  def ssdKind(x0: security_scheme_decl): security_scheme_kind = x0 match {
    case SecuritySchemeDeclFull(x1, x2, x3) => x2
  }

  def ssdName(x0: security_scheme_decl): String = x0 match {
    case SecuritySchemeDeclFull(x1, x2, x3) => x1
  }

  def ssdSpan(x0: security_scheme_decl): Option[span_t] = x0 match {
    case SecuritySchemeDeclFull(x1, x2, x3) => x3
  }

  def identifierNameSelect(x0: expr): List[String] = x0 match {
    case IdentifierF(n, uu)               => List(n)
    case BinaryOpF(v, va, vb, vc)         => Nil
    case UnaryOpF(v, va, vb)              => Nil
    case QuantifierF(v, va, vb, vc)       => Nil
    case SomeWrapF(v, va)                 => Nil
    case TheF(v, va, vb, vc)              => Nil
    case FieldAccessF(v, va, vb)          => Nil
    case EnumAccessF(v, va, vb)           => Nil
    case IndexF(v, va, vb)                => Nil
    case CallF(v, va, vb)                 => Nil
    case PrimeF(v, va)                    => Nil
    case PreF(v, va)                      => Nil
    case WithF(v, va, vb)                 => Nil
    case IfF(v, va, vb, vc)               => Nil
    case LetF(v, va, vb, vc)              => Nil
    case LambdaF(v, va, vb)               => Nil
    case ConstructorF(v, va, vb)          => Nil
    case SetLiteralF(v, va)               => Nil
    case MapLiteralF(v, va)               => Nil
    case SetComprehensionF(v, va, vb, vc) => Nil
    case SeqLiteralF(v, va)               => Nil
    case MatchesF(v, va, vb)              => Nil
    case IntLitF(v, va)                   => Nil
    case FloatLitF(v, va)                 => Nil
    case StringLitF(v, va)                => Nil
    case BoolLitF(v, va)                  => Nil
    case NoneLitF(v)                      => Nil
  }

  def parseTemporalBody(e: expr): temporal_body =
    e match {
      case BinaryOpF(_, _, _, _)                      => TbInvalid(e)
      case UnaryOpF(_, _, _)                          => TbInvalid(e)
      case QuantifierF(_, _, _, _)                    => TbInvalid(e)
      case SomeWrapF(_, _)                            => TbInvalid(e)
      case TheF(_, _, _, _)                           => TbInvalid(e)
      case FieldAccessF(_, _, _)                      => TbInvalid(e)
      case EnumAccessF(_, _, _)                       => TbInvalid(e)
      case IndexF(_, _, _)                            => TbInvalid(e)
      case CallF(BinaryOpF(_, _, _, _), _, _)         => TbInvalid(e)
      case CallF(UnaryOpF(_, _, _), _, _)             => TbInvalid(e)
      case CallF(QuantifierF(_, _, _, _), _, _)       => TbInvalid(e)
      case CallF(SomeWrapF(_, _), _, _)               => TbInvalid(e)
      case CallF(TheF(_, _, _, _), _, _)              => TbInvalid(e)
      case CallF(FieldAccessF(_, _, _), _, _)         => TbInvalid(e)
      case CallF(EnumAccessF(_, _, _), _, _)          => TbInvalid(e)
      case CallF(IndexF(_, _, _), _, _)               => TbInvalid(e)
      case CallF(CallF(_, _, _), _, _)                => TbInvalid(e)
      case CallF(PrimeF(_, _), _, _)                  => TbInvalid(e)
      case CallF(PreF(_, _), _, _)                    => TbInvalid(e)
      case CallF(WithF(_, _, _), _, _)                => TbInvalid(e)
      case CallF(IfF(_, _, _, _), _, _)               => TbInvalid(e)
      case CallF(LetF(_, _, _, _), _, _)              => TbInvalid(e)
      case CallF(LambdaF(_, _, _), _, _)              => TbInvalid(e)
      case CallF(ConstructorF(_, _, _), _, _)         => TbInvalid(e)
      case CallF(SetLiteralF(_, _), _, _)             => TbInvalid(e)
      case CallF(MapLiteralF(_, _), _, _)             => TbInvalid(e)
      case CallF(SetComprehensionF(_, _, _, _), _, _) => TbInvalid(e)
      case CallF(SeqLiteralF(_, _), _, _)             => TbInvalid(e)
      case CallF(MatchesF(_, _, _), _, _)             => TbInvalid(e)
      case CallF(IntLitF(_, _), _, _)                 => TbInvalid(e)
      case CallF(FloatLitF(_, _), _, _)               => TbInvalid(e)
      case CallF(StringLitF(_, _), _, _)              => TbInvalid(e)
      case CallF(BoolLitF(_, _), _, _)                => TbInvalid(e)
      case CallF(NoneLitF(_), _, _)                   => TbInvalid(e)
      case CallF(IdentifierF(_, _), Nil, _)           => TbInvalid(e)
      case CallF(IdentifierF(name, _), List(arg), _) =>
        name == "always" match {
          case true => TbAlways(arg)
          case false => name == "eventually" match {
              case true => TbEventually(arg)
              case false => name == "fairness" match {
                  case true  => TbFairness(arg)
                  case false => TbInvalid(e)
                }
            }
        }
      case CallF(IdentifierF(_, _), _ :: _ :: _, _) => TbInvalid(e)
      case PrimeF(_, _)                             => TbInvalid(e)
      case PreF(_, _)                               => TbInvalid(e)
      case WithF(_, _, _)                           => TbInvalid(e)
      case IfF(_, _, _, _)                          => TbInvalid(e)
      case LetF(_, _, _, _)                         => TbInvalid(e)
      case LambdaF(_, _, _)                         => TbInvalid(e)
      case ConstructorF(_, _, _)                    => TbInvalid(e)
      case SetLiteralF(_, _)                        => TbInvalid(e)
      case MapLiteralF(_, _)                        => TbInvalid(e)
      case SetComprehensionF(_, _, _, _)            => TbInvalid(e)
      case SeqLiteralF(_, _)                        => TbInvalid(e)
      case MatchesF(_, _, _)                        => TbInvalid(e)
      case IntLitF(_, _)                            => TbInvalid(e)
      case FloatLitF(_, _)                          => TbInvalid(e)
      case StringLitF(_, _)                         => TbInvalid(e)
      case BoolLitF(_, _)                           => TbInvalid(e)
      case NoneLitF(_)                              => TbInvalid(e)
      case IdentifierF(_, _)                        => TbInvalid(e)
    }

  def synthTemporalExpr(b: temporal_body): expr =
    b match {
      case TbAlways(arg) => CallF(IdentifierF("always", None), List(arg), None)
      case TbEventually(arg) =>
        CallF(IdentifierF("eventually", None), List(arg), None)
      case TbFairness(arg) =>
        CallF(IdentifierF("fairness", None), List(arg), None)
      case TbInvalid(raw) => raw
    }

  def equal_multiplicity(x0: multiplicity, x1: multiplicity): Boolean =
    (x0, x1) match {
      case (MultSome(), MultSet())  => false
      case (MultSet(), MultSome())  => false
      case (MultLone(), MultSet())  => false
      case (MultSet(), MultLone())  => false
      case (MultLone(), MultSome()) => false
      case (MultSome(), MultLone()) => false
      case (MultOne(), MultSet())   => false
      case (MultSet(), MultOne())   => false
      case (MultOne(), MultSome())  => false
      case (MultSome(), MultOne())  => false
      case (MultOne(), MultLone())  => false
      case (MultLone(), MultOne())  => false
      case (MultSet(), MultSet())   => true
      case (MultSome(), MultSome()) => true
      case (MultLone(), MultLone()) => true
      case (MultOne(), MultOne())   => true
    }

  def equal_type_expr(x0: type_expr, x1: type_expr): Boolean = (x0, x1) match {
    case (OptionTypeF(x51, x52), RelationTypeF(x61, x62, x63, x64))   => false
    case (RelationTypeF(x61, x62, x63, x64), OptionTypeF(x51, x52))   => false
    case (SeqTypeF(x41, x42), RelationTypeF(x61, x62, x63, x64))      => false
    case (RelationTypeF(x61, x62, x63, x64), SeqTypeF(x41, x42))      => false
    case (SeqTypeF(x41, x42), OptionTypeF(x51, x52))                  => false
    case (OptionTypeF(x51, x52), SeqTypeF(x41, x42))                  => false
    case (MapTypeF(x31, x32, x33), RelationTypeF(x61, x62, x63, x64)) => false
    case (RelationTypeF(x61, x62, x63, x64), MapTypeF(x31, x32, x33)) => false
    case (MapTypeF(x31, x32, x33), OptionTypeF(x51, x52))             => false
    case (OptionTypeF(x51, x52), MapTypeF(x31, x32, x33))             => false
    case (MapTypeF(x31, x32, x33), SeqTypeF(x41, x42))                => false
    case (SeqTypeF(x41, x42), MapTypeF(x31, x32, x33))                => false
    case (SetTypeF(x21, x22), RelationTypeF(x61, x62, x63, x64))      => false
    case (RelationTypeF(x61, x62, x63, x64), SetTypeF(x21, x22))      => false
    case (SetTypeF(x21, x22), OptionTypeF(x51, x52))                  => false
    case (OptionTypeF(x51, x52), SetTypeF(x21, x22))                  => false
    case (SetTypeF(x21, x22), SeqTypeF(x41, x42))                     => false
    case (SeqTypeF(x41, x42), SetTypeF(x21, x22))                     => false
    case (SetTypeF(x21, x22), MapTypeF(x31, x32, x33))                => false
    case (MapTypeF(x31, x32, x33), SetTypeF(x21, x22))                => false
    case (NamedTypeF(x11, x12), RelationTypeF(x61, x62, x63, x64))    => false
    case (RelationTypeF(x61, x62, x63, x64), NamedTypeF(x11, x12))    => false
    case (NamedTypeF(x11, x12), OptionTypeF(x51, x52))                => false
    case (OptionTypeF(x51, x52), NamedTypeF(x11, x12))                => false
    case (NamedTypeF(x11, x12), SeqTypeF(x41, x42))                   => false
    case (SeqTypeF(x41, x42), NamedTypeF(x11, x12))                   => false
    case (NamedTypeF(x11, x12), MapTypeF(x31, x32, x33))              => false
    case (MapTypeF(x31, x32, x33), NamedTypeF(x11, x12))              => false
    case (NamedTypeF(x11, x12), SetTypeF(x21, x22))                   => false
    case (SetTypeF(x21, x22), NamedTypeF(x11, x12))                   => false
    case (RelationTypeF(x61, x62, x63, x64), RelationTypeF(y61, y62, y63, y64)) =>
      equal_type_expr(x61, y61) &&
      (equal_multiplicity(x62, y62) &&
        (equal_type_expr(x63, y63) && equal_option[span_t](x64, y64)))
    case (OptionTypeF(x51, x52), OptionTypeF(y51, y52)) =>
      equal_type_expr(x51, y51) && equal_option[span_t](x52, y52)
    case (SeqTypeF(x41, x42), SeqTypeF(y41, y42)) =>
      equal_type_expr(x41, y41) && equal_option[span_t](x42, y42)
    case (MapTypeF(x31, x32, x33), MapTypeF(y31, y32, y33)) =>
      equal_type_expr(x31, y31) &&
      (equal_type_expr(x32, y32) && equal_option[span_t](x33, y33))
    case (SetTypeF(x21, x22), SetTypeF(y21, y22)) =>
      equal_type_expr(x21, y21) && equal_option[span_t](x22, y22)
    case (NamedTypeF(x11, x12), NamedTypeF(y11, y12)) =>
      x11 == y11 && equal_option[span_t](x12, y12)
  }

  def primitiveTypeToSql(nm: String): Option[String] =
    nm == "String" match {
      case true => Some[String]("TEXT")
      case false => nm == "Int" match {
          case true => Some[String]("INTEGER")
          case false => nm == "Float" match {
              case true => Some[String]("DOUBLE PRECISION")
              case false => nm == "Bool" match {
                  case true => Some[String]("BOOLEAN")
                  case false => nm == "Boolean" match {
                      case true => Some[String]("BOOLEAN")
                      case false => nm == "DateTime" match {
                          case true => Some[String]("TIMESTAMPTZ")
                          case false => nm == "Date" match {
                              case true => Some[String]("DATE")
                              case false => nm == "UUID" match {
                                  case true => Some[String]("UUID")
                                  case false => nm == "Decimal" match {
                                      case true => Some[String]("NUMERIC(19,4)")
                                      case false => nm == "Bytes" match {
                                          case true => Some[String]("BYTEA")
                                          case false => nm == "Money" match {
                                              case true  => Some[String]("INTEGER")
                                              case false => None
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

  def classifyColumnTypeAux(
      fuel: nat,
      uu: type_expr,
      uv: List[(String, type_alias_decl)],
      uw: List[(String, enum_decl)],
      ux: List[String],
      uy: List[String],
      nullable: Boolean
  ): classified_column =
    equal_nat(fuel, zero_nat) match {
      case true => ClassifiedColumn(CkUnknown(), nullable)
      case false =>
        val stripped = stripOptions(uu): type_expr
        val nullablea =
          nullable || !equal_type_expr(stripped, uu): Boolean;
        stripped match {
          case NamedTypeF(name, _) =>
            primitiveTypeToSql(name) match {
              case None =>
                map_of[String, enum_decl](uw, name) match {
                  case None =>
                    membera[String](ux, name) match {
                      case true => ClassifiedColumn(CkEntityRef(name), nullablea)
                      case false => membera[String](uy, name) match {
                          case true => ClassifiedColumn(CkUnknown(), nullablea)
                          case false => map_of[String, type_alias_decl](uv, name) match {
                              case None =>
                                ClassifiedColumn(CkUnknown(), nullablea)
                              case Some(TypeAliasDeclFull(_, base, _, _)) =>
                                classifyColumnTypeAux(
                                  minus_nat(fuel, one_nat),
                                  base,
                                  uv,
                                  uw,
                                  ux,
                                  name :: uy,
                                  nullablea
                                )
                            }
                        }
                    }
                  case Some(EnumDeclFull(_, vs, _)) =>
                    ClassifiedColumn(CkEnum(vs), nullablea)
                }
              case Some(sqlType) =>
                ClassifiedColumn(CkPrim(sqlType), nullablea)
            }
          case SetTypeF(_, _) =>
            ClassifiedColumn(CkJsonArray(), nullablea)
          case MapTypeF(_, _, _) =>
            ClassifiedColumn(CkJsonObject(), nullablea)
          case SeqTypeF(_, _) =>
            ClassifiedColumn(CkJsonArray(), nullablea)
          case OptionTypeF(_, _) =>
            ClassifiedColumn(CkUnknown(), nullablea)
          case RelationTypeF(_, _, _, _) =>
            ClassifiedColumn(CkRelation(), nullablea)
        }
    }

  def classifyColumnType(
      ty: type_expr,
      am: List[(String, type_alias_decl)],
      em: List[(String, enum_decl)],
      entityNames: List[String]
  ): classified_column =
    classifyColumnTypeAux(
      Suc(size_list[(String, type_alias_decl)](am)),
      ty,
      am,
      em,
      entityNames,
      Nil,
      false
    )

  def mergeStringConstraint(x0: string_constraint, x1: string_constraint): string_constraint =
    (x0, x1) match {
      case (StringConstraint(amin, amax, ar, ap, af), StringConstraint(bmin, bmax, br, bp, bf)) =>
        StringConstraint(
          mergeMinInt(amin, bmin),
          mergeMaxInt(amax, bmax),
          remdups[String](ar ++ br),
          remdups[String](ap ++ bp),
          af ++ bf
        )
    }

  def walkStringConstraint(e: expr): (string_constraint, List[String]) =
    foldl[(string_constraint, List[String]), expr](
      (acc: (string_constraint, List[String])) =>
        (atom: expr) => {
          val (cur, skips) = acc: ((string_constraint, List[String]))
          val (nxt, new_skips) =
            stringAtom(atom): ((string_constraint, List[String]));
          (mergeStringConstraint(cur, nxt), skips ++ new_skips)
        },
      (emptyStringConstraint, Nil),
      flattenAnd(e)
    )

  def asStringLit(x0: expr): Option[String] = x0 match {
    case StringLitF(v, uu)                => Some[String](v)
    case BinaryOpF(v, va, vb, vc)         => None
    case UnaryOpF(v, va, vb)              => None
    case QuantifierF(v, va, vb, vc)       => None
    case SomeWrapF(v, va)                 => None
    case TheF(v, va, vb, vc)              => None
    case FieldAccessF(v, va, vb)          => None
    case EnumAccessF(v, va, vb)           => None
    case IndexF(v, va, vb)                => None
    case CallF(v, va, vb)                 => None
    case PrimeF(v, va)                    => None
    case PreF(v, va)                      => None
    case WithF(v, va, vb)                 => None
    case IfF(v, va, vb, vc)               => None
    case LetF(v, va, vb, vc)              => None
    case LambdaF(v, va, vb)               => None
    case ConstructorF(v, va, vb)          => None
    case SetLiteralF(v, va)               => None
    case MapLiteralF(v, va)               => None
    case SetComprehensionF(v, va, vb, vc) => None
    case SeqLiteralF(v, va)               => None
    case MatchesF(v, va, vb)              => None
    case IntLitF(v, va)                   => None
    case FloatLitF(v, va)                 => None
    case BoolLitF(v, va)                  => None
    case NoneLitF(v)                      => None
    case IdentifierF(v, va)               => None
  }

  def fieldFilter(
      uu: String,
      x1: List[(String, (String, String))]
  ): List[(String, (String, String))] =
    (uu, x1) match {
      case (uu, Nil) => Nil
      case (field, (g, (t, v)) :: rest) =>
        field == g match {
          case true  => (g, (t, v)) :: fieldFilter(field, rest)
          case false => fieldFilter(field, rest)
        }
    }

  def parseBoolPv(e: expr): convention_value =
    asBoolLit(e) match {
      case None    => CvBad(ExpectedBoolean(), e)
      case Some(b) => CvOk(PvBool(b))
    }

  def serviceIrEnums(x0: service_ir): List[enum_decl] = x0 match {
    case ServiceIRFull(uu, uv, uw, es, ux, uy, uz, va, vb, vc, vd, ve, vf, vg, vh, vi) => es
  }

  def knownBuiltinNames: List[String] = List("len", "dom", "ran")

  def saTypeImportModule(x0: sa_type): Option[String] = x0 match {
    case SaType(uu, m) => m
  }

  def talConstraint(x0: type_alias_decl): Option[expr] = x0 match {
    case TypeAliasDeclFull(x1, x2, x3, x4) => x3
  }

  def matchesIdentityShape(x0: expr, name: String): Option[String] = (x0, name) match {
    case (MatchesF(x, pattern, uu), name) =>
      x match {
        case BinaryOpF(_, _, _, _)         => None
        case UnaryOpF(_, _, _)             => None
        case QuantifierF(_, _, _, _)       => None
        case SomeWrapF(_, _)               => None
        case TheF(_, _, _, _)              => None
        case FieldAccessF(_, _, _)         => None
        case EnumAccessF(_, _, _)          => None
        case IndexF(_, _, _)               => None
        case CallF(_, _, _)                => None
        case PrimeF(_, _)                  => None
        case PreF(_, _)                    => None
        case WithF(_, _, _)                => None
        case IfF(_, _, _, _)               => None
        case LetF(_, _, _, _)              => None
        case LambdaF(_, _, _)              => None
        case ConstructorF(_, _, _)         => None
        case SetLiteralF(_, _)             => None
        case MapLiteralF(_, _)             => None
        case SetComprehensionF(_, _, _, _) => None
        case SeqLiteralF(_, _)             => None
        case MatchesF(_, _, _)             => None
        case IntLitF(_, _)                 => None
        case FloatLitF(_, _)               => None
        case StringLitF(_, _)              => None
        case BoolLitF(_, _)                => None
        case NoneLitF(_)                   => None
        case IdentifierF(p, _) =>
          p == name match {
            case true  => Some[String](pattern)
            case false => None
          }
      }
    case (BinaryOpF(v, va, vb, vc), uw)         => None
    case (UnaryOpF(v, va, vb), uw)              => None
    case (QuantifierF(v, va, vb, vc), uw)       => None
    case (SomeWrapF(v, va), uw)                 => None
    case (TheF(v, va, vb, vc), uw)              => None
    case (FieldAccessF(v, va, vb), uw)          => None
    case (EnumAccessF(v, va, vb), uw)           => None
    case (IndexF(v, va, vb), uw)                => None
    case (CallF(v, va, vb), uw)                 => None
    case (PrimeF(v, va), uw)                    => None
    case (PreF(v, va), uw)                      => None
    case (WithF(v, va, vb), uw)                 => None
    case (IfF(v, va, vb, vc), uw)               => None
    case (LetF(v, va, vb, vc), uw)              => None
    case (LambdaF(v, va, vb), uw)               => None
    case (ConstructorF(v, va, vb), uw)          => None
    case (SetLiteralF(v, va), uw)               => None
    case (MapLiteralF(v, va), uw)               => None
    case (SetComprehensionF(v, va, vb, vc), uw) => None
    case (SeqLiteralF(v, va), uw)               => None
    case (IntLitF(v, va), uw)                   => None
    case (FloatLitF(v, va), uw)                 => None
    case (StringLitF(v, va), uw)                => None
    case (BoolLitF(v, va), uw)                  => None
    case (NoneLitF(v), uw)                      => None
    case (IdentifierF(v, va), uw)               => None
  }

  def entityFieldDeclLookup(
      es: List[entity_decl],
      ename: String,
      fname: String
  ): Option[field_decl] =
    entityByName(es, ename) match {
      case None     => None
      case Some(ed) => findFieldDeclFull(entityFieldsFull(ed), fname)
    }

  def fieldAccessNameSelect(x0: expr): List[String] = x0 match {
    case FieldAccessF(uu, n, uv)          => List(n)
    case BinaryOpF(v, va, vb, vc)         => Nil
    case UnaryOpF(v, va, vb)              => Nil
    case QuantifierF(v, va, vb, vc)       => Nil
    case SomeWrapF(v, va)                 => Nil
    case TheF(v, va, vb, vc)              => Nil
    case EnumAccessF(v, va, vb)           => Nil
    case IndexF(v, va, vb)                => Nil
    case CallF(v, va, vb)                 => Nil
    case PrimeF(v, va)                    => Nil
    case PreF(v, va)                      => Nil
    case WithF(v, va, vb)                 => Nil
    case IfF(v, va, vb, vc)               => Nil
    case LetF(v, va, vb, vc)              => Nil
    case LambdaF(v, va, vb)               => Nil
    case ConstructorF(v, va, vb)          => Nil
    case SetLiteralF(v, va)               => Nil
    case MapLiteralF(v, va)               => Nil
    case SetComprehensionF(v, va, vb, vc) => Nil
    case SeqLiteralF(v, va)               => Nil
    case MatchesF(v, va, vb)              => Nil
    case IntLitF(v, va)                   => Nil
    case FloatLitF(v, va)                 => Nil
    case StringLitF(v, va)                => Nil
    case BoolLitF(v, va)                  => Nil
    case NoneLitF(v)                      => Nil
    case IdentifierF(v, va)               => Nil
  }

  def keyExistsInRequiresOf(stateFields: List[String], e: expr): List[String] =
    e match {
      case BinaryOpF(BAnd(), _, _, _)                            => Nil
      case BinaryOpF(BOr(), _, _, _)                             => Nil
      case BinaryOpF(BImplies(), _, _, _)                        => Nil
      case BinaryOpF(BIff(), _, _, _)                            => Nil
      case BinaryOpF(BEq(), _, _, _)                             => Nil
      case BinaryOpF(BNeq(), _, _, _)                            => Nil
      case BinaryOpF(BLt(), _, _, _)                             => Nil
      case BinaryOpF(BGt(), _, _, _)                             => Nil
      case BinaryOpF(BLe(), _, _, _)                             => Nil
      case BinaryOpF(BGe(), _, _, _)                             => Nil
      case BinaryOpF(BIn(), _, BinaryOpF(_, _, _, _), _)         => Nil
      case BinaryOpF(BIn(), _, UnaryOpF(_, _, _), _)             => Nil
      case BinaryOpF(BIn(), _, QuantifierF(_, _, _, _), _)       => Nil
      case BinaryOpF(BIn(), _, SomeWrapF(_, _), _)               => Nil
      case BinaryOpF(BIn(), _, TheF(_, _, _, _), _)              => Nil
      case BinaryOpF(BIn(), _, FieldAccessF(_, _, _), _)         => Nil
      case BinaryOpF(BIn(), _, EnumAccessF(_, _, _), _)          => Nil
      case BinaryOpF(BIn(), _, IndexF(_, _, _), _)               => Nil
      case BinaryOpF(BIn(), _, CallF(_, _, _), _)                => Nil
      case BinaryOpF(BIn(), _, PrimeF(_, _), _)                  => Nil
      case BinaryOpF(BIn(), _, PreF(_, _), _)                    => Nil
      case BinaryOpF(BIn(), _, WithF(_, _, _), _)                => Nil
      case BinaryOpF(BIn(), _, IfF(_, _, _, _), _)               => Nil
      case BinaryOpF(BIn(), _, LetF(_, _, _, _), _)              => Nil
      case BinaryOpF(BIn(), _, LambdaF(_, _, _), _)              => Nil
      case BinaryOpF(BIn(), _, ConstructorF(_, _, _), _)         => Nil
      case BinaryOpF(BIn(), _, SetLiteralF(_, _), _)             => Nil
      case BinaryOpF(BIn(), _, MapLiteralF(_, _), _)             => Nil
      case BinaryOpF(BIn(), _, SetComprehensionF(_, _, _, _), _) => Nil
      case BinaryOpF(BIn(), _, SeqLiteralF(_, _), _)             => Nil
      case BinaryOpF(BIn(), _, MatchesF(_, _, _), _)             => Nil
      case BinaryOpF(BIn(), _, IntLitF(_, _), _)                 => Nil
      case BinaryOpF(BIn(), _, FloatLitF(_, _), _)               => Nil
      case BinaryOpF(BIn(), _, StringLitF(_, _), _)              => Nil
      case BinaryOpF(BIn(), _, BoolLitF(_, _), _)                => Nil
      case BinaryOpF(BIn(), _, NoneLitF(_), _)                   => Nil
      case BinaryOpF(BIn(), _, IdentifierF(n, _), _) =>
        string_in_list(n, stateFields) match {
          case true  => List(n)
          case false => Nil
        }
      case BinaryOpF(BNotIn(), _, _, _)     => Nil
      case BinaryOpF(BSubset(), _, _, _)    => Nil
      case BinaryOpF(BUnion(), _, _, _)     => Nil
      case BinaryOpF(BIntersect(), _, _, _) => Nil
      case BinaryOpF(BDiff(), _, _, _)      => Nil
      case BinaryOpF(BAdd(), _, _, _)       => Nil
      case BinaryOpF(BSub(), _, _, _)       => Nil
      case BinaryOpF(BMul(), _, _, _)       => Nil
      case BinaryOpF(BDiv(), _, _, _)       => Nil
      case UnaryOpF(_, _, _)                => Nil
      case QuantifierF(_, _, _, _)          => Nil
      case SomeWrapF(_, _)                  => Nil
      case TheF(_, _, _, _)                 => Nil
      case FieldAccessF(_, _, _)            => Nil
      case EnumAccessF(_, _, _)             => Nil
      case IndexF(_, _, _)                  => Nil
      case CallF(_, _, _)                   => Nil
      case PrimeF(_, _)                     => Nil
      case PreF(_, _)                       => Nil
      case WithF(_, _, _)                   => Nil
      case IfF(_, _, _, _)                  => Nil
      case LetF(_, _, _, _)                 => Nil
      case LambdaF(_, _, _)                 => Nil
      case ConstructorF(_, _, _)            => Nil
      case SetLiteralF(_, _)                => Nil
      case MapLiteralF(_, _)                => Nil
      case SetComprehensionF(_, _, _, _)    => Nil
      case SeqLiteralF(_, _)                => Nil
      case MatchesF(_, _, _)                => Nil
      case IntLitF(_, _)                    => Nil
      case FloatLitF(_, _)                  => Nil
      case StringLitF(_, _)                 => Nil
      case BoolLitF(_, _)                   => Nil
      case NoneLitF(_)                      => Nil
      case IdentifierF(_, _)                => Nil
    }

  def referencesPreRelation(x0: expr, rel: String): Boolean = (x0, rel) match {
    case (PreF(e, uu), rel) => e match {
        case BinaryOpF(_, _, _, _)         => false
        case UnaryOpF(_, _, _)             => false
        case QuantifierF(_, _, _, _)       => false
        case SomeWrapF(_, _)               => false
        case TheF(_, _, _, _)              => false
        case FieldAccessF(_, _, _)         => false
        case EnumAccessF(_, _, _)          => false
        case IndexF(_, _, _)               => false
        case CallF(_, _, _)                => false
        case PrimeF(_, _)                  => false
        case PreF(_, _)                    => false
        case WithF(_, _, _)                => false
        case IfF(_, _, _, _)               => false
        case LetF(_, _, _, _)              => false
        case LambdaF(_, _, _)              => false
        case ConstructorF(_, _, _)         => false
        case SetLiteralF(_, _)             => false
        case MapLiteralF(_, _)             => false
        case SetComprehensionF(_, _, _, _) => false
        case SeqLiteralF(_, _)             => false
        case MatchesF(_, _, _)             => false
        case IntLitF(_, _)                 => false
        case FloatLitF(_, _)               => false
        case StringLitF(_, _)              => false
        case BoolLitF(_, _)                => false
        case NoneLitF(_)                   => false
        case IdentifierF(n, _)             => n == rel
      }
    case (IdentifierF(n, uv), rel)              => n == rel
    case (BinaryOpF(v, va, vb, vc), ux)         => false
    case (UnaryOpF(v, va, vb), ux)              => false
    case (QuantifierF(v, va, vb, vc), ux)       => false
    case (SomeWrapF(v, va), ux)                 => false
    case (TheF(v, va, vb, vc), ux)              => false
    case (FieldAccessF(v, va, vb), ux)          => false
    case (EnumAccessF(v, va, vb), ux)           => false
    case (IndexF(v, va, vb), ux)                => false
    case (CallF(v, va, vb), ux)                 => false
    case (PrimeF(v, va), ux)                    => false
    case (WithF(v, va, vb), ux)                 => false
    case (IfF(v, va, vb, vc), ux)               => false
    case (LetF(v, va, vb, vc), ux)              => false
    case (LambdaF(v, va, vb), ux)               => false
    case (ConstructorF(v, va, vb), ux)          => false
    case (SetLiteralF(v, va), ux)               => false
    case (MapLiteralF(v, va), ux)               => false
    case (SetComprehensionF(v, va, vb, vc), ux) => false
    case (SeqLiteralF(v, va), ux)               => false
    case (MatchesF(v, va, vb), ux)              => false
    case (IntLitF(v, va), ux)                   => false
    case (FloatLitF(v, va), ux)                 => false
    case (StringLitF(v, va), ux)                => false
    case (BoolLitF(v, va), ux)                  => false
    case (NoneLitF(v), ux)                      => false
  }

  def collectAllCallNames(e: expr): List[String] =
    maps[expr, String]((a: expr) => callSelfAllNames(a), allSubexprs(e))

  def fieldPropsNullable(x0: List[(String, (schema_object, Boolean))])
      : List[(String, schema_object)] =
    x0 match {
      case Nil => Nil
      case (n, (s, uu)) :: rest =>
        (n, makeNullableLifted(s)) :: fieldPropsNullable(rest)
    }

  def updateSchemaLifted(
      ename: String,
      decorated: List[(String, (schema_object, Boolean))]
  ): schema_object = {
    val fs =
      nonIdDecorated(decorated): List[(String, (schema_object, Boolean))];
    SchemaObject(
      Some[List[String]](List("object")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Some[List[(String, schema_object)]](fieldPropsNullable(fs)),
      None,
      None,
      Some[String]("Update payload for " + ename),
      false
    )
  }

  def createSchemaLifted(
      ename: String,
      decorated: List[(String, (schema_object, Boolean))]
  ): schema_object = {
    val fs =
      nonIdDecorated(decorated): List[(String, (schema_object, Boolean))];
    SchemaObject(
      Some[List[String]](List("object")),
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Some[List[String]](requiredNames(fs)),
      Some[List[(String, schema_object)]](fieldProps(fs)),
      None,
      None,
      Some[String]("Create payload for " + ename),
      false
    )
  }

  def buildEntitySchemas(
      ename: String,
      decorated: List[(String, (schema_object, Boolean))]
  ): (schema_object, (schema_object, schema_object)) =
    (
      createSchemaLifted(ename, decorated),
      (readSchemaLifted(ename, decorated), updateSchemaLifted(ename, decorated))
    )

  def extractVerbBeforeKebab(opName: String, entityOpt: Option[String]): String =
    entityOpt match {
      case None => opName
      case Some(en) =>
        literalEndsWith(en, opName) match {
          case true =>
            val verb =
              literalDropRight(literalLength(en), opName): String;
            equal_nat(literalLength(verb), zero_nat) match {
              case true  => opName
              case false => verb
            }
          case false => literalStartsWith(en, opName) match {
              case true =>
                val verb = literalDropLeft(literalLength(en), opName): String;
                equal_nat(literalLength(verb), zero_nat) match {
                  case true  => opName
                  case false => verb
                }
              case false => opName
            }
        }
    }

  def decodeAggregateCall(call: expr): Option[aggregate_call] =
    call match {
      case BinaryOpF(_, _, _, _)                      => None
      case UnaryOpF(_, _, _)                          => None
      case QuantifierF(_, _, _, _)                    => None
      case SomeWrapF(_, _)                            => None
      case TheF(_, _, _, _)                           => None
      case FieldAccessF(_, _, _)                      => None
      case EnumAccessF(_, _, _)                       => None
      case IndexF(_, _, _)                            => None
      case CallF(BinaryOpF(_, _, _, _), _, _)         => None
      case CallF(UnaryOpF(_, _, _), _, _)             => None
      case CallF(QuantifierF(_, _, _, _), _, _)       => None
      case CallF(SomeWrapF(_, _), _, _)               => None
      case CallF(TheF(_, _, _, _), _, _)              => None
      case CallF(FieldAccessF(_, _, _), _, _)         => None
      case CallF(EnumAccessF(_, _, _), _, _)          => None
      case CallF(IndexF(_, _, _), _, _)               => None
      case CallF(CallF(_, _, _), _, _)                => None
      case CallF(PrimeF(_, _), _, _)                  => None
      case CallF(PreF(_, _), _, _)                    => None
      case CallF(WithF(_, _, _), _, _)                => None
      case CallF(IfF(_, _, _, _), _, _)               => None
      case CallF(LetF(_, _, _, _), _, _)              => None
      case CallF(LambdaF(_, _, _), _, _)              => None
      case CallF(ConstructorF(_, _, _), _, _)         => None
      case CallF(SetLiteralF(_, _), _, _)             => None
      case CallF(MapLiteralF(_, _), _, _)             => None
      case CallF(SetComprehensionF(_, _, _, _), _, _) => None
      case CallF(SeqLiteralF(_, _), _, _)             => None
      case CallF(MatchesF(_, _, _), _, _)             => None
      case CallF(IntLitF(_, _), _, _)                 => None
      case CallF(FloatLitF(_, _), _, _)               => None
      case CallF(StringLitF(_, _), _, _)              => None
      case CallF(BoolLitF(_, _), _, _)                => None
      case CallF(NoneLitF(_), _, _)                   => None
      case CallF(IdentifierF(name, _), args, _) =>
        aggregateForName(name) match {
          case None => None
          case Some(agg) =>
            agg match {
              case SumAgg() =>
                args match {
                  case Nil                               => None
                  case List(_)                           => None
                  case _ :: BinaryOpF(_, _, _, _) :: _   => None
                  case _ :: UnaryOpF(_, _, _) :: _       => None
                  case _ :: QuantifierF(_, _, _, _) :: _ => None
                  case _ :: SomeWrapF(_, _) :: _         => None
                  case _ :: TheF(_, _, _, _) :: _        => None
                  case _ :: FieldAccessF(_, _, _) :: _   => None
                  case _ :: EnumAccessF(_, _, _) :: _    => None
                  case _ :: IndexF(_, _, _) :: _         => None
                  case _ :: CallF(_, _, _) :: _          => None
                  case _ :: PrimeF(_, _) :: _            => None
                  case _ :: PreF(_, _) :: _              => None
                  case _ :: WithF(_, _, _) :: _          => None
                  case _ :: IfF(_, _, _, _) :: _         => None
                  case _ :: LetF(_, _, _, _) :: _        => None
                  case List(coll, LambdaF(_, body, _)) =>
                    extractFieldName(coll) match {
                      case None => None
                      case Some(coln) =>
                        lambdaProjection(body) match {
                          case None => None
                          case Some(src) =>
                            Some[aggregate_call](AggregateCall(coln, agg, Some[String](src)))
                        }
                    }
                  case _ :: LambdaF(_, _, _) :: _ :: _         => None
                  case _ :: ConstructorF(_, _, _) :: _         => None
                  case _ :: SetLiteralF(_, _) :: _             => None
                  case _ :: MapLiteralF(_, _) :: _             => None
                  case _ :: SetComprehensionF(_, _, _, _) :: _ => None
                  case _ :: SeqLiteralF(_, _) :: _             => None
                  case _ :: MatchesF(_, _, _) :: _             => None
                  case _ :: IntLitF(_, _) :: _                 => None
                  case _ :: FloatLitF(_, _) :: _               => None
                  case _ :: StringLitF(_, _) :: _              => None
                  case _ :: BoolLitF(_, _) :: _                => None
                  case _ :: NoneLitF(_) :: _                   => None
                  case _ :: IdentifierF(_, _) :: _             => None
                }
              case CountAgg() =>
                args match {
                  case Nil => None
                  case List(coll) =>
                    extractFieldName(coll) match {
                      case None => None
                      case Some(c) =>
                        Some[aggregate_call](AggregateCall(c, CountAgg(), None))
                    }
                  case _ :: _ :: _ => None
                }
              case MinAgg() =>
                args match {
                  case Nil                               => None
                  case List(_)                           => None
                  case _ :: BinaryOpF(_, _, _, _) :: _   => None
                  case _ :: UnaryOpF(_, _, _) :: _       => None
                  case _ :: QuantifierF(_, _, _, _) :: _ => None
                  case _ :: SomeWrapF(_, _) :: _         => None
                  case _ :: TheF(_, _, _, _) :: _        => None
                  case _ :: FieldAccessF(_, _, _) :: _   => None
                  case _ :: EnumAccessF(_, _, _) :: _    => None
                  case _ :: IndexF(_, _, _) :: _         => None
                  case _ :: CallF(_, _, _) :: _          => None
                  case _ :: PrimeF(_, _) :: _            => None
                  case _ :: PreF(_, _) :: _              => None
                  case _ :: WithF(_, _, _) :: _          => None
                  case _ :: IfF(_, _, _, _) :: _         => None
                  case _ :: LetF(_, _, _, _) :: _        => None
                  case List(coll, LambdaF(_, body, _)) =>
                    extractFieldName(coll) match {
                      case None => None
                      case Some(coln) =>
                        lambdaProjection(body) match {
                          case None => None
                          case Some(src) =>
                            Some[aggregate_call](AggregateCall(coln, agg, Some[String](src)))
                        }
                    }
                  case _ :: LambdaF(_, _, _) :: _ :: _         => None
                  case _ :: ConstructorF(_, _, _) :: _         => None
                  case _ :: SetLiteralF(_, _) :: _             => None
                  case _ :: MapLiteralF(_, _) :: _             => None
                  case _ :: SetComprehensionF(_, _, _, _) :: _ => None
                  case _ :: SeqLiteralF(_, _) :: _             => None
                  case _ :: MatchesF(_, _, _) :: _             => None
                  case _ :: IntLitF(_, _) :: _                 => None
                  case _ :: FloatLitF(_, _) :: _               => None
                  case _ :: StringLitF(_, _) :: _              => None
                  case _ :: BoolLitF(_, _) :: _                => None
                  case _ :: NoneLitF(_) :: _                   => None
                  case _ :: IdentifierF(_, _) :: _             => None
                }
              case MaxAgg() =>
                args match {
                  case Nil                               => None
                  case List(_)                           => None
                  case _ :: BinaryOpF(_, _, _, _) :: _   => None
                  case _ :: UnaryOpF(_, _, _) :: _       => None
                  case _ :: QuantifierF(_, _, _, _) :: _ => None
                  case _ :: SomeWrapF(_, _) :: _         => None
                  case _ :: TheF(_, _, _, _) :: _        => None
                  case _ :: FieldAccessF(_, _, _) :: _   => None
                  case _ :: EnumAccessF(_, _, _) :: _    => None
                  case _ :: IndexF(_, _, _) :: _         => None
                  case _ :: CallF(_, _, _) :: _          => None
                  case _ :: PrimeF(_, _) :: _            => None
                  case _ :: PreF(_, _) :: _              => None
                  case _ :: WithF(_, _, _) :: _          => None
                  case _ :: IfF(_, _, _, _) :: _         => None
                  case _ :: LetF(_, _, _, _) :: _        => None
                  case List(coll, LambdaF(_, body, _)) =>
                    extractFieldName(coll) match {
                      case None => None
                      case Some(coln) =>
                        lambdaProjection(body) match {
                          case None => None
                          case Some(src) =>
                            Some[aggregate_call](AggregateCall(coln, agg, Some[String](src)))
                        }
                    }
                  case _ :: LambdaF(_, _, _) :: _ :: _         => None
                  case _ :: ConstructorF(_, _, _) :: _         => None
                  case _ :: SetLiteralF(_, _) :: _             => None
                  case _ :: MapLiteralF(_, _) :: _             => None
                  case _ :: SetComprehensionF(_, _, _, _) :: _ => None
                  case _ :: SeqLiteralF(_, _) :: _             => None
                  case _ :: MatchesF(_, _, _) :: _             => None
                  case _ :: IntLitF(_, _) :: _                 => None
                  case _ :: FloatLitF(_, _) :: _               => None
                  case _ :: StringLitF(_, _) :: _              => None
                  case _ :: BoolLitF(_, _) :: _                => None
                  case _ :: NoneLitF(_) :: _                   => None
                  case _ :: IdentifierF(_, _) :: _             => None
                }
            }
        }
      case PrimeF(_, _)                  => None
      case PreF(_, _)                    => None
      case WithF(_, _, _)                => None
      case IfF(_, _, _, _)               => None
      case LetF(_, _, _, _)              => None
      case LambdaF(_, _, _)              => None
      case ConstructorF(_, _, _)         => None
      case SetLiteralF(_, _)             => None
      case MapLiteralF(_, _)             => None
      case SetComprehensionF(_, _, _, _) => None
      case SeqLiteralF(_, _)             => None
      case MatchesF(_, _, _)             => None
      case IntLitF(_, _)                 => None
      case FloatLitF(_, _)               => None
      case StringLitF(_, _)              => None
      case BoolLitF(_, _)                => None
      case NoneLitF(_)                   => None
      case IdentifierF(_, _)             => None
    }

  def collectionElementTypeAux(
      fuel: nat,
      uu: List[type_alias_decl],
      uv: type_expr
  ): Option[type_expr] =
    equal_nat(fuel, zero_nat) match {
      case true => None
      case false => uv match {
          case NamedTypeF(n, _) =>
            lookupAliasTarget(uu, n) match {
              case None => None
              case Some(a) =>
                collectionElementTypeAux(minus_nat(fuel, one_nat), uu, a)
            }
          case SetTypeF(inner, _) => Some[type_expr](inner)
          case MapTypeF(_, _, _)  => None
          case SeqTypeF(inner, _) => Some[type_expr](inner)
          case OptionTypeF(inner, _) =>
            collectionElementTypeAux(minus_nat(fuel, one_nat), uu, inner)
          case RelationTypeF(_, _, _, _) => None
        }
    }

  def collectionElementType(aliases: List[type_alias_decl], t: type_expr): Option[type_expr] =
    collectionElementTypeAux(typeWalkFuel(aliases), aliases, t)

  def splitOnColonAux(uu: List[BigInt], x1: List[BigInt]): Option[(List[BigInt], List[BigInt])] =
    (uu, x1) match {
      case (uu, Nil) => None
      case (acc, c :: cs) =>
        c == BigInt(58) match {
          case true  => Some[(List[BigInt], List[BigInt])]((rev[BigInt](acc), cs))
          case false => splitOnColonAux(c :: acc, cs)
        }
    }

  def splitOnColon(s: String): Option[(String, String)] =
    splitOnColonAux(Nil, Str_Literal.asciisOfLiteral(s)) match {
      case None => None
      case Some((l, r)) =>
        Some[(String, String)]((Str_Literal.literalOfAsciis(l), Str_Literal.literalOfAsciis(r)))
    }

  def verifyEnumNames(ir: service_ir): List[String] =
    map[enum_decl, String]((a: enum_decl) => enumDeclName(a), serviceIrEnums(ir))

  def alloyQuantifierKeyword(x0: alloy_quantifier_class): String = x0 match {
    case AqAll()    => "all"
    case AqSome()   => "some"
    case AqExists() => "some"
    case AqNo()     => "no"
  }

  def collectExternItemsBindings(x0: List[quantifier_binding]): List[extern_item] =
    x0 match {
      case Nil => Nil
      case QuantifierBindingFull(a, d, kind, sp) :: bs =>
        collectExternItems(EkIntFunction(), d) ++ collectExternItemsBindings(bs)
    }

  def collectExternItemsEntries(x0: List[map_entry]): List[extern_item] = x0 match {
    case Nil => Nil
    case MapEntryFull(k, v, sp) :: es =>
      collectExternItems(EkIntFunction(), k) ++
        (collectExternItems(EkIntFunction(), v) ++ collectExternItemsEntries(es))
  }

  def collectExternItemsFields(x0: List[field_assign]): List[extern_item] = x0 match {
    case Nil => Nil
    case FieldAssignFull(f, v, sp) :: fs =>
      collectExternItems(EkIntFunction(), v) ++ collectExternItemsFields(fs)
  }

  def collectExternItemsArgs(x0: List[expr]): List[extern_item] = x0 match {
    case Nil => Nil
    case e :: es =>
      collectExternItems(EkIntFunction(), e) ++ collectExternItemsArgs(es)
  }

  def collectExternItems(expected: extern_kind, x1: expr): List[extern_item] =
    (expected, x1) match {
      case (expected, CallF(c, args, sp)) =>
        c match {
          case BinaryOpF(_, _, _, _)         => collectExternItemsArgs(args)
          case UnaryOpF(_, _, _)             => collectExternItemsArgs(args)
          case QuantifierF(_, _, _, _)       => collectExternItemsArgs(args)
          case SomeWrapF(_, _)               => collectExternItemsArgs(args)
          case TheF(_, _, _, _)              => collectExternItemsArgs(args)
          case FieldAccessF(_, _, _)         => collectExternItemsArgs(args)
          case EnumAccessF(_, _, _)          => collectExternItemsArgs(args)
          case IndexF(_, _, _)               => collectExternItemsArgs(args)
          case CallF(_, _, _)                => collectExternItemsArgs(args)
          case PrimeF(_, _)                  => collectExternItemsArgs(args)
          case PreF(_, _)                    => collectExternItemsArgs(args)
          case WithF(_, _, _)                => collectExternItemsArgs(args)
          case IfF(_, _, _, _)               => collectExternItemsArgs(args)
          case LetF(_, _, _, _)              => collectExternItemsArgs(args)
          case LambdaF(_, _, _)              => collectExternItemsArgs(args)
          case ConstructorF(_, _, _)         => collectExternItemsArgs(args)
          case SetLiteralF(_, _)             => collectExternItemsArgs(args)
          case MapLiteralF(_, _)             => collectExternItemsArgs(args)
          case SetComprehensionF(_, _, _, _) => collectExternItemsArgs(args)
          case SeqLiteralF(_, _)             => collectExternItemsArgs(args)
          case MatchesF(_, _, _)             => collectExternItemsArgs(args)
          case IntLitF(_, _)                 => collectExternItemsArgs(args)
          case FloatLitF(_, _)               => collectExternItemsArgs(args)
          case StringLitF(_, _)              => collectExternItemsArgs(args)
          case BoolLitF(_, _)                => collectExternItemsArgs(args)
          case NoneLitF(_)                   => collectExternItemsArgs(args)
          case IdentifierF(n, _) =>
            string_in_list(n, knownBuiltinNames) match {
              case true => collectExternItemsArgs(args)
              case false => EiExtern(n, int_of_nat(size_list[expr](args)), expected) ::
                  collectExternItemsArgs(args)
            }
        }
      case (expected, BinaryOpF(op, l, r, sp)) =>
        equal_bin_op(op, BAnd()) ||
          (equal_bin_op(op, BOr()) ||
            (equal_bin_op(op, BImplies()) || equal_bin_op(op, BIff()))) match {
          case true => collectExternItems(EkPredicate(), l) ++
              collectExternItems(EkPredicate(), r)
          case false => collectExternItems(EkIntFunction(), l) ++
              collectExternItems(EkIntFunction(), r)
        }
      case (expected, UnaryOpF(op, x, sp)) =>
        equal_un_op(op, UNot()) match {
          case true  => collectExternItems(EkPredicate(), x)
          case false => collectExternItems(EkIntFunction(), x)
        }
      case (expected, PrimeF(x, sp))    => collectExternItems(expected, x)
      case (expected, PreF(x, sp))      => collectExternItems(expected, x)
      case (expected, SomeWrapF(x, sp)) => collectExternItems(EkIntFunction(), x)
      case (expected, FieldAccessF(b, f, sp)) =>
        collectExternItems(EkIntFunction(), b)
      case (expected, IndexF(b, i, sp)) =>
        collectExternItems(EkIntFunction(), b) ++
          collectExternItems(EkIntFunction(), i)
      case (expected, MapLiteralF(es, sp)) => collectExternItemsEntries(es)
      case (expected, SetLiteralF(es, sp)) => collectExternItemsArgs(es)
      case (expected, SeqLiteralF(es, sp)) => collectExternItemsArgs(es)
      case (expected, IfF(c, t, e, sp)) =>
        collectExternItems(EkPredicate(), c) ++
          (collectExternItems(expected, t) ++ collectExternItems(expected, e))
      case (expected, LetF(v, vala, b, sp)) =>
        collectExternItems(EkIntFunction(), vala) ++ collectExternItems(expected, b)
      case (expected, QuantifierF(q, bs, body, sp)) =>
        collectExternItemsBindings(bs) ++ collectExternItems(EkPredicate(), body)
      case (expected, SetComprehensionF(v, dm, pr, sp)) =>
        collectExternItems(EkIntFunction(), dm) ++
          collectExternItems(EkPredicate(), pr)
      case (expected, ConstructorF(n, fs, sp)) => collectExternItemsFields(fs)
      case (expected, WithF(b, fs, sp)) =>
        collectExternItems(EkIntFunction(), b) ++ collectExternItemsFields(fs)
      case (expected, TheF(v, dm, body, sp)) =>
        collectExternItems(EkIntFunction(), dm) ++
          collectExternItems(EkPredicate(), body)
      case (expected, LambdaF(p, body, sp)) =>
        collectExternItems(EkIntFunction(), body)
      case (expected, MatchesF(x, p, sp)) =>
        EiPattern(p) :: collectExternItems(EkIntFunction(), x)
      case (uu, EnumAccessF(v, va, vb)) => Nil
      case (uu, IntLitF(v, va))         => Nil
      case (uu, FloatLitF(v, va))       => Nil
      case (uu, StringLitF(v, va))      => Nil
      case (uu, BoolLitF(v, va))        => Nil
      case (uu, NoneLitF(v))            => Nil
      case (uu, IdentifierF(v, va))     => Nil
    }

  def fieldNameIfStateIndex(e: expr, inputName: String, stateName: String): Option[String] =
    e match {
      case BinaryOpF(_, _, _, _)                                     => None
      case UnaryOpF(_, _, _)                                         => None
      case QuantifierF(_, _, _, _)                                   => None
      case SomeWrapF(_, _)                                           => None
      case TheF(_, _, _, _)                                          => None
      case FieldAccessF(BinaryOpF(_, _, _, _), _, _)                 => None
      case FieldAccessF(UnaryOpF(_, _, _), _, _)                     => None
      case FieldAccessF(QuantifierF(_, _, _, _), _, _)               => None
      case FieldAccessF(SomeWrapF(_, _), _, _)                       => None
      case FieldAccessF(TheF(_, _, _, _), _, _)                      => None
      case FieldAccessF(FieldAccessF(_, _, _), _, _)                 => None
      case FieldAccessF(EnumAccessF(_, _, _), _, _)                  => None
      case FieldAccessF(IndexF(BinaryOpF(_, _, _, _), _, _), _, _)   => None
      case FieldAccessF(IndexF(UnaryOpF(_, _, _), _, _), _, _)       => None
      case FieldAccessF(IndexF(QuantifierF(_, _, _, _), _, _), _, _) => None
      case FieldAccessF(IndexF(SomeWrapF(_, _), _, _), _, _)         => None
      case FieldAccessF(IndexF(TheF(_, _, _, _), _, _), _, _)        => None
      case FieldAccessF(IndexF(FieldAccessF(_, _, _), _, _), _, _)   => None
      case FieldAccessF(IndexF(EnumAccessF(_, _, _), _, _), _, _)    => None
      case FieldAccessF(IndexF(IndexF(_, _, _), _, _), _, _)         => None
      case FieldAccessF(IndexF(CallF(_, _, _), _, _), _, _)          => None
      case FieldAccessF(IndexF(PrimeF(_, _), _, _), _, _)            => None
      case FieldAccessF(IndexF(PreF(_, _), _, _), _, _)              => None
      case FieldAccessF(IndexF(WithF(_, _, _), _, _), _, _)          => None
      case FieldAccessF(IndexF(IfF(_, _, _, _), _, _), _, _)         => None
      case FieldAccessF(IndexF(LetF(_, _, _, _), _, _), _, _)        => None
      case FieldAccessF(IndexF(LambdaF(_, _, _), _, _), _, _)        => None
      case FieldAccessF(IndexF(ConstructorF(_, _, _), _, _), _, _)   => None
      case FieldAccessF(IndexF(SetLiteralF(_, _), _, _), _, _)       => None
      case FieldAccessF(IndexF(MapLiteralF(_, _), _, _), _, _)       => None
      case FieldAccessF(IndexF(SetComprehensionF(_, _, _, _), _, _), _, _) =>
        None
      case FieldAccessF(IndexF(SeqLiteralF(_, _), _, _), _, _)                     => None
      case FieldAccessF(IndexF(MatchesF(_, _, _), _, _), _, _)                     => None
      case FieldAccessF(IndexF(IntLitF(_, _), _, _), _, _)                         => None
      case FieldAccessF(IndexF(FloatLitF(_, _), _, _), _, _)                       => None
      case FieldAccessF(IndexF(StringLitF(_, _), _, _), _, _)                      => None
      case FieldAccessF(IndexF(BoolLitF(_, _), _, _), _, _)                        => None
      case FieldAccessF(IndexF(NoneLitF(_), _, _), _, _)                           => None
      case FieldAccessF(IndexF(IdentifierF(_, _), BinaryOpF(_, _, _, _), _), _, _) => None
      case FieldAccessF(IndexF(IdentifierF(_, _), UnaryOpF(_, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), QuantifierF(_, _, _, _), _), _, _) => None
      case FieldAccessF(IndexF(IdentifierF(_, _), SomeWrapF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), TheF(_, _, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), FieldAccessF(_, _, _), _), _, _) => None
      case FieldAccessF(IndexF(IdentifierF(_, _), EnumAccessF(_, _, _), _), _, _)  => None
      case FieldAccessF(IndexF(IdentifierF(_, _), IndexF(_, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), CallF(_, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), PrimeF(_, _), _), _, _) => None
      case FieldAccessF(IndexF(IdentifierF(_, _), PreF(_, _), _), _, _)   => None
      case FieldAccessF(IndexF(IdentifierF(_, _), WithF(_, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), IfF(_, _, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), LetF(_, _, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), LambdaF(_, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), ConstructorF(_, _, _), _), _, _) => None
      case FieldAccessF(IndexF(IdentifierF(_, _), SetLiteralF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), MapLiteralF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), SetComprehensionF(_, _, _, _), _), _, _) => None
      case FieldAccessF(IndexF(IdentifierF(_, _), SeqLiteralF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), MatchesF(_, _, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), IntLitF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), FloatLitF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), StringLitF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), BoolLitF(_, _), _), _, _) =>
        None
      case FieldAccessF(IndexF(IdentifierF(_, _), NoneLitF(_), _), _, _) => None
      case FieldAccessF(IndexF(IdentifierF(s, _), IdentifierF(i, _), _), fname, _) =>
        s == stateName && i == inputName match {
          case true  => Some[String](fname)
          case false => None
        }
      case FieldAccessF(CallF(_, _, _), _, _)                => None
      case FieldAccessF(PrimeF(_, _), _, _)                  => None
      case FieldAccessF(PreF(_, _), _, _)                    => None
      case FieldAccessF(WithF(_, _, _), _, _)                => None
      case FieldAccessF(IfF(_, _, _, _), _, _)               => None
      case FieldAccessF(LetF(_, _, _, _), _, _)              => None
      case FieldAccessF(LambdaF(_, _, _), _, _)              => None
      case FieldAccessF(ConstructorF(_, _, _), _, _)         => None
      case FieldAccessF(SetLiteralF(_, _), _, _)             => None
      case FieldAccessF(MapLiteralF(_, _), _, _)             => None
      case FieldAccessF(SetComprehensionF(_, _, _, _), _, _) => None
      case FieldAccessF(SeqLiteralF(_, _), _, _)             => None
      case FieldAccessF(MatchesF(_, _, _), _, _)             => None
      case FieldAccessF(IntLitF(_, _), _, _)                 => None
      case FieldAccessF(FloatLitF(_, _), _, _)               => None
      case FieldAccessF(StringLitF(_, _), _, _)              => None
      case FieldAccessF(BoolLitF(_, _), _, _)                => None
      case FieldAccessF(NoneLitF(_), _, _)                   => None
      case FieldAccessF(IdentifierF(_, _), _, _)             => None
      case EnumAccessF(_, _, _)                              => None
      case IndexF(_, _, _)                                   => None
      case CallF(_, _, _)                                    => None
      case PrimeF(_, _)                                      => None
      case PreF(_, _)                                        => None
      case WithF(_, _, _)                                    => None
      case IfF(_, _, _, _)                                   => None
      case LetF(_, _, _, _)                                  => None
      case LambdaF(_, _, _)                                  => None
      case ConstructorF(_, _, _)                             => None
      case SetLiteralF(_, _)                                 => None
      case MapLiteralF(_, _)                                 => None
      case SetComprehensionF(_, _, _, _)                     => None
      case SeqLiteralF(_, _)                                 => None
      case MatchesF(_, _, _)                                 => None
      case IntLitF(_, _)                                     => None
      case FloatLitF(_, _)                                   => None
      case StringLitF(_, _)                                  => None
      case BoolLitF(_, _)                                    => None
      case NoneLitF(_)                                       => None
      case IdentifierF(_, _)                                 => None
    }

  def relationTargetsEntity(x0: type_expr, entity: String): Boolean =
    (x0, entity) match {
      case (RelationTypeF(uu, uv, NamedTypeF(n, uw), ux), entity)        => n == entity
      case (NamedTypeF(n, uy), entity)                                   => n == entity
      case (SetTypeF(v, vb), va)                                         => false
      case (MapTypeF(v, vb, vc), va)                                     => false
      case (SeqTypeF(v, vb), va)                                         => false
      case (OptionTypeF(v, vb), va)                                      => false
      case (RelationTypeF(v, vb, SetTypeF(ve, vf), vd), va)              => false
      case (RelationTypeF(v, vb, MapTypeF(ve, vf, vg), vd), va)          => false
      case (RelationTypeF(v, vb, SeqTypeF(ve, vf), vd), va)              => false
      case (RelationTypeF(v, vb, OptionTypeF(ve, vf), vd), va)           => false
      case (RelationTypeF(v, vb, RelationTypeF(ve, vf, vg, vh), vd), va) => false
    }

  def collectIdentifierNames(e: expr): List[String] =
    remdups[String](maps[expr, String]((a: expr) => identifierNameSelect(a), allSubexprs(e)))

  def withInfoBaseIdentifier(x0: with_info): Option[String] = x0 match {
    case WithInfoFull(uu, b) => b
  }

  def appendPartialIndexes(x0: table_spec, colFilters: List[(String, String)]): table_spec =
    (x0, colFilters) match {
      case (TableSpec(nm, ent, cols, pk, fks, cks, ixs), colFilters) =>
        TableSpec(
          nm,
          ent,
          cols,
          pk,
          fks,
          cks,
          ixs ++
            map[(String, String), index_spec](
              (a: (String, String)) => {
                val (aa, b) = a: ((String, String));
                partialIndexSpec(nm, aa, b)
              },
              colFilters
            )
        )
    }

  def schemaRelationValueType(gamma: tyctx_ext[Unit], rel_name: String): Option[ty] =
    find[state_field_decl](
      (sf: state_field_decl) =>
        state_fieldNameFull(sf) == rel_name,
      tc_relations[Unit](gamma)
    ) match {
      case None => None
      case Some(sf) =>
        state_fieldTypeFull(sf) match {
          case NamedTypeF(_, _)  => None
          case SetTypeF(_, _)    => None
          case MapTypeF(_, _, _) => None
          case SeqTypeF(_, _)    => None
          case OptionTypeF(_, _) => None
          case RelationTypeF(_, _, v, _) =>
            typeExprFullToTy(
              tc_enums[Unit](gamma),
              map[entity_decl, String](
                (a: entity_decl) =>
                  entityNameFull(a),
                tc_entities[Unit](gamma)
              ),
              v
            )
        }
    }

  def operationEnsures(x0: operation_decl): List[expr] = x0 match {
    case OperationDeclFull(uu, uv, uw, ux, ensures, uy, uz) => ensures
  }

  def classifyAlloyIdentifier(
      name: String,
      boundVars: List[String],
      stateFields: List[(String, type_expr)],
      inputFields: List[(String, type_expr)]
  ): alloy_identifier_kind =
    membera[String](boundVars, name) match {
      case true => AikBoundVar()
      case false => membera[String](
          map[(String, type_expr), String](
            (a: (String, type_expr)) => fst[String, type_expr](a),
            stateFields
          ),
          name
        ) match {
          case true => AikStateField()
          case false => membera[String](
              map[(String, type_expr), String](
                (a: (String, type_expr)) =>
                  fst[String, type_expr](a),
                inputFields
              ),
              name
            ) match {
              case true  => AikInputField()
              case false => AikPlain()
            }
        }
    }

  def classificationMatchedRule(x0: operation_classification): String = x0 match {
    case OperationClassification(uu, uv, uw, r, ux, uy, uz) => r
  }

  def signalsPreservedRelations(x0: analysis_signals): List[String] = x0 match {
    case AnalysisSignals(uu, p, uv, uw, ux, uy, uz, va, vb) => p
  }

  def classifyExternItems(items: List[extern_item]): (List[(String, extern_info)], List[String]) =
    foldExternItems(items, Nil, Nil)

  def desugarOptionGuards(opts: List[String], e: expr): expr =
    desugarGo(plus_nat(size_list[expr](allSubexprs(e)), nat_of_integer(BigInt(100))), opts, e)

  def rewriteFieldRefsBindings(
      vk: List[String],
      vl: List[String],
      x2: List[quantifier_binding]
  ): List[quantifier_binding] =
    (vk, vl, x2) match {
      case (vk, vl, Nil) => Nil
      case (flds, bound, QuantifierBindingFull(a, d, kk, sp) :: bs) =>
        QuantifierBindingFull(a, rewriteFieldRefsAux(flds, bound, d), kk, sp) ::
          rewriteFieldRefsBindings(flds, bound, bs)
    }

  def rewriteFieldRefsEntries(
      vi: List[String],
      vj: List[String],
      x2: List[map_entry]
  ): List[map_entry] =
    (vi, vj, x2) match {
      case (vi, vj, Nil) => Nil
      case (flds, bound, MapEntryFull(k, v, sp) :: es) =>
        MapEntryFull(
          rewriteFieldRefsAux(flds, bound, k),
          rewriteFieldRefsAux(flds, bound, v),
          sp
        ) ::
          rewriteFieldRefsEntries(flds, bound, es)
    }

  def rewriteFieldRefsFields(
      vg: List[String],
      vh: List[String],
      x2: List[field_assign]
  ): List[field_assign] =
    (vg, vh, x2) match {
      case (vg, vh, Nil) => Nil
      case (flds, bound, FieldAssignFull(f, v, sp) :: fs) =>
        FieldAssignFull(f, rewriteFieldRefsAux(flds, bound, v), sp) ::
          rewriteFieldRefsFields(flds, bound, fs)
    }

  def rewriteFieldRefsList(ve: List[String], vf: List[String], x2: List[expr]): List[expr] =
    (ve, vf, x2) match {
      case (ve, vf, Nil) => Nil
      case (flds, bound, e :: es) =>
        rewriteFieldRefsAux(flds, bound, e) :: rewriteFieldRefsList(flds, bound, es)
    }

  def rewriteFieldRefsAux(flds: List[String], bound: List[String], x2: expr): expr =
    (flds, bound, x2) match {
      case (flds, bound, IdentifierF(n, sp)) =>
        string_in_list(n, flds) && !string_in_list(n, bound) match {
          case true  => FieldAccessF(IdentifierF("x", sp), n, sp)
          case false => IdentifierF(n, sp)
        }
      case (flds, bound, BinaryOpF(op, l, r, sp)) =>
        BinaryOpF(op, rewriteFieldRefsAux(flds, bound, l), rewriteFieldRefsAux(flds, bound, r), sp)
      case (flds, bound, UnaryOpF(op, e, sp)) =>
        UnaryOpF(op, rewriteFieldRefsAux(flds, bound, e), sp)
      case (flds, bound, FieldAccessF(b, f, sp)) =>
        FieldAccessF(rewriteFieldRefsAux(flds, bound, b), f, sp)
      case (flds, bound, EnumAccessF(b, m, sp)) => EnumAccessF(b, m, sp)
      case (flds, bound, IndexF(b, i, sp)) =>
        IndexF(rewriteFieldRefsAux(flds, bound, b), rewriteFieldRefsAux(flds, bound, i), sp)
      case (flds, bound, CallF(c, args, sp)) =>
        CallF(rewriteFieldRefsAux(flds, bound, c), rewriteFieldRefsList(flds, bound, args), sp)
      case (flds, bound, PrimeF(e, sp)) =>
        PrimeF(rewriteFieldRefsAux(flds, bound, e), sp)
      case (flds, bound, PreF(e, sp)) =>
        PreF(rewriteFieldRefsAux(flds, bound, e), sp)
      case (flds, bound, WithF(b, upds, sp)) =>
        WithF(rewriteFieldRefsAux(flds, bound, b), rewriteFieldRefsFields(flds, bound, upds), sp)
      case (flds, bound, IfF(c, t, e, sp)) =>
        IfF(
          rewriteFieldRefsAux(flds, bound, c),
          rewriteFieldRefsAux(flds, bound, t),
          rewriteFieldRefsAux(flds, bound, e),
          sp
        )
      case (flds, bound, LetF(v, vala, body, sp)) =>
        LetF(
          v,
          rewriteFieldRefsAux(flds, bound, vala),
          rewriteFieldRefsAux(flds, v :: bound, body),
          sp
        )
      case (flds, bound, LambdaF(p, b, sp)) =>
        LambdaF(p, rewriteFieldRefsAux(flds, p :: bound, b), sp)
      case (flds, bound, ConstructorF(n, fs, sp)) =>
        ConstructorF(n, rewriteFieldRefsFields(flds, bound, fs), sp)
      case (flds, bound, SetLiteralF(xs, sp)) =>
        SetLiteralF(rewriteFieldRefsList(flds, bound, xs), sp)
      case (flds, bound, MapLiteralF(es, sp)) =>
        MapLiteralF(rewriteFieldRefsEntries(flds, bound, es), sp)
      case (flds, bound, SetComprehensionF(v, d, p, sp)) =>
        SetComprehensionF(
          v,
          rewriteFieldRefsAux(flds, bound, d),
          rewriteFieldRefsAux(flds, v :: bound, p),
          sp
        )
      case (flds, bound, SeqLiteralF(xs, sp)) =>
        SeqLiteralF(rewriteFieldRefsList(flds, bound, xs), sp)
      case (flds, bound, MatchesF(e, pat, sp)) => MatchesF(e, pat, sp)
      case (flds, bound, SomeWrapF(e, sp)) =>
        SomeWrapF(rewriteFieldRefsAux(flds, bound, e), sp)
      case (flds, bound, TheF(v, d, b, sp)) =>
        TheF(v, rewriteFieldRefsAux(flds, bound, d), rewriteFieldRefsAux(flds, v :: bound, b), sp)
      case (flds, bound, QuantifierF(q, bs, body, sp)) =>
        QuantifierF(
          q,
          rewriteFieldRefsBindings(flds, bound, bs),
          rewriteFieldRefsAux(flds, qb_names(bs) ++ bound, body),
          sp
        )
      case (uu, uv, IntLitF(n, sp))    => IntLitF(n, sp)
      case (uw, ux, FloatLitF(n, sp))  => FloatLitF(n, sp)
      case (uy, uz, StringLitF(n, sp)) => StringLitF(n, sp)
      case (va, vb, BoolLitF(v, sp))   => BoolLitF(v, sp)
      case (vc, vd, NoneLitF(sp))      => NoneLitF(sp)
    }

  def capsTransactionalDdl(x0: dialect_caps): Boolean = x0 match {
    case DialectCaps(uu, uv, uw, ux, uy, f) => f
  }

  def mysqlSerialColumnDef(name: String, t: canonical_type): String =
    name +
      (isSerial4(t) match {
        case true  => " INT NOT NULL AUTO_INCREMENT"
        case false => " BIGINT NOT NULL AUTO_INCREMENT"
      })

  def operRequiresAuth(x0: operation_decl): Option[List[String]] = x0 match {
    case OperationDeclFull(x1, x2, x3, x4, x5, x6, x7) => x6
  }

  def collectFieldAccessNames(e: expr): List[String] =
    remdups[String](maps[expr, String]((a: expr) => fieldAccessNameSelect(a), allSubexprs(e)))

  def classifyInvariantAtom(e: expr): invariant_check_class =
    e match {
      case BinaryOpF(op, left, rhs, _) =>
        extractFieldName(left) match {
          case None => IcSkip()
          case Some(fn) =>
            (op, rhs) match {
              case (BAnd(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BOr(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BImplies(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIff(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BEq(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BNeq(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BLt(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BGt(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BLe(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BGe(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), BinaryOpF(_, _, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), UnaryOpF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), QuantifierF(_, _, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), SomeWrapF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), TheF(_, _, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), FieldAccessF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), EnumAccessF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), IndexF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), CallF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), PrimeF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), PreF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), WithF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), IfF(_, _, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), LetF(_, _, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), LambdaF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), ConstructorF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), SetLiteralF(elements, _)) =>
                !nulla[expr](elements) &&
                  list_all[expr]((a: expr) => isLiteral(a), elements) match {
                  case true  => IcInClause(fn, elements)
                  case false => IcSkip()
                }
              case (BIn(), MapLiteralF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), SetComprehensionF(_, _, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), SeqLiteralF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), MatchesF(_, _, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), IntLitF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), FloatLitF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), StringLitF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), BoolLitF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), NoneLitF(_)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIn(), IdentifierF(_, _)) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BNotIn(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BSubset(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BUnion(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BIntersect(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BDiff(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BAdd(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BSub(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BMul(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
              case (BDiv(), _) =>
                isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
                  case true  => IcCompare(fn, op, rhs)
                  case false => IcSkip()
                }
            }
        }
      case UnaryOpF(_, _, _)             => IcSkip()
      case QuantifierF(_, _, _, _)       => IcSkip()
      case SomeWrapF(_, _)               => IcSkip()
      case TheF(_, _, _, _)              => IcSkip()
      case FieldAccessF(_, _, _)         => IcSkip()
      case EnumAccessF(_, _, _)          => IcSkip()
      case IndexF(_, _, _)               => IcSkip()
      case CallF(_, _, _)                => IcSkip()
      case PrimeF(_, _)                  => IcSkip()
      case PreF(_, _)                    => IcSkip()
      case WithF(_, _, _)                => IcSkip()
      case IfF(_, _, _, _)               => IcSkip()
      case LetF(_, _, _, _)              => IcSkip()
      case LambdaF(_, _, _)              => IcSkip()
      case ConstructorF(_, _, _)         => IcSkip()
      case SetLiteralF(_, _)             => IcSkip()
      case MapLiteralF(_, _)             => IcSkip()
      case SetComprehensionF(_, _, _, _) => IcSkip()
      case SeqLiteralF(_, _)             => IcSkip()
      case MatchesF(_, _, _)             => IcSkip()
      case IntLitF(_, _)                 => IcSkip()
      case FloatLitF(_, _)               => IcSkip()
      case StringLitF(_, _)              => IcSkip()
      case BoolLitF(_, _)                => IcSkip()
      case NoneLitF(_)                   => IcSkip()
      case IdentifierF(_, _)             => IcSkip()
    }

  def extractTsTuple(x0: convention_rule, ens: List[String]): Option[(String, (String, String))] =
    (x0, ens) match {
      case (ConventionRuleFull(target, prop, qualOpt, vala, uu), ens) =>
        prop == "test_strategy" && membera[String](ens, target) match {
          case true => qualOpt match {
              case None => None
              case Some(f) =>
                vala match {
                  case CvOk(PvString(_)) => None
                  case CvOk(PvInt(_))    => None
                  case CvOk(PvBool(live)) =>
                    Some[(String, (String, String))]((
                      f,
                      (
                        target,
                        (live match {
                          case true  => "live"
                          case false => "redacted"
                        })
                      )
                    ))
                  case CvOk(PvStrPair(_, _)) => None
                  case CvOk(PvExpr(_))       => None
                  case CvBad(_, _)           => None
                  case CvUnknown(_)          => None
                }
            }
          case false => None
        }
    }

  def asciiIsWhitespace(c: BigInt): Boolean =
    c == BigInt(32) || (c == BigInt(9) || (c == BigInt(10) || c == BigInt(13)))

  def literalIsBlank(s: String): Boolean =
    list_all[BigInt]((a: BigInt) => asciiIsWhitespace(a), Str_Literal.asciisOfLiteral(s))

  def literalIsEmpty(s: String): Boolean =
    nulla[BigInt](Str_Literal.asciisOfLiteral(s))

  def trustPreservation(enums: List[String], op: operation_decl, ivd: invariant_decl): trust_level =
    foldTrust(
      enums,
      invariantBody(ivd) ::
        operationRequires(op) ++ operationEnsures(op)
    )

  def classificationTargetEntity(x0: operation_classification): Option[String] =
    x0 match {
      case OperationClassification(uu, uv, uw, ux, t, uy, uz) => t
    }

  def sqliteSerialColumnDef(name: String, uu: canonical_type): String =
    name + " INTEGER PRIMARY KEY AUTOINCREMENT"

  def qbdCollection(x0: quantifier_binding): expr = x0 match {
    case QuantifierBindingFull(x1, x2, x3, x4) => x2
  }

  def structuralIneligibility(
      e: expr,
      outputs: List[String],
      stateFields: List[String]
  ): Option[structural_ineligibility] = {
    val fvs = free_vars(e): List[String];
    hasPrePrime(e) match {
      case true => Some[structural_ineligibility](SceReferencesPrePrime())
      case false => list_ex[String](
          (a: String) =>
            membera[String](stateFields, a),
          fvs
        ) match {
          case true => Some[structural_ineligibility](SceReferencesStateField())
          case false => !list_ex[String](
              (a: String) =>
                membera[String](outputs, a),
              fvs
            ) match {
              case true  => Some[structural_ineligibility](SceReferencesNoOutput())
              case false => None
            }
        }
    }
  }

  def typeMismatchDiagnostics(e: expr): List[(type_mismatch_kind, Option[span_t])] =
    map_filter[expr, (type_mismatch_kind, Option[span_t])](
      (a: expr) => typeMismatchAt(a),
      allSubexprs(e)
    )

  def collectPrimedIdentifiers(es: List[expr]): List[String] =
    remdups[String](maps[expr, String](
      (e: expr) =>
        maps[expr, String]((a: expr) => primedIdSelect(a), allSubexprs(e)),
      es
    ))

  def referencesPrimedRelation(x0: expr, rel: String): Boolean = (x0, rel) match {
    case (PrimeF(e, uu), rel) => e match {
        case BinaryOpF(_, _, _, _)         => false
        case UnaryOpF(_, _, _)             => false
        case QuantifierF(_, _, _, _)       => false
        case SomeWrapF(_, _)               => false
        case TheF(_, _, _, _)              => false
        case FieldAccessF(_, _, _)         => false
        case EnumAccessF(_, _, _)          => false
        case IndexF(_, _, _)               => false
        case CallF(_, _, _)                => false
        case PrimeF(_, _)                  => false
        case PreF(_, _)                    => false
        case WithF(_, _, _)                => false
        case IfF(_, _, _, _)               => false
        case LetF(_, _, _, _)              => false
        case LambdaF(_, _, _)              => false
        case ConstructorF(_, _, _)         => false
        case SetLiteralF(_, _)             => false
        case MapLiteralF(_, _)             => false
        case SetComprehensionF(_, _, _, _) => false
        case SeqLiteralF(_, _)             => false
        case MatchesF(_, _, _)             => false
        case IntLitF(_, _)                 => false
        case FloatLitF(_, _)               => false
        case StringLitF(_, _)              => false
        case BoolLitF(_, _)                => false
        case NoneLitF(_)                   => false
        case IdentifierF(n, _)             => n == rel
      }
    case (BinaryOpF(v, va, vb, vc), uw)         => false
    case (UnaryOpF(v, va, vb), uw)              => false
    case (QuantifierF(v, va, vb, vc), uw)       => false
    case (SomeWrapF(v, va), uw)                 => false
    case (TheF(v, va, vb, vc), uw)              => false
    case (FieldAccessF(v, va, vb), uw)          => false
    case (EnumAccessF(v, va, vb), uw)           => false
    case (IndexF(v, va, vb), uw)                => false
    case (CallF(v, va, vb), uw)                 => false
    case (PreF(v, va), uw)                      => false
    case (WithF(v, va, vb), uw)                 => false
    case (IfF(v, va, vb, vc), uw)               => false
    case (LetF(v, va, vb, vc), uw)              => false
    case (LambdaF(v, va, vb), uw)               => false
    case (ConstructorF(v, va, vb), uw)          => false
    case (SetLiteralF(v, va), uw)               => false
    case (MapLiteralF(v, va), uw)               => false
    case (SetComprehensionF(v, va, vb, vc), uw) => false
    case (SeqLiteralF(v, va), uw)               => false
    case (MatchesF(v, va, vb), uw)              => false
    case (IntLitF(v, va), uw)                   => false
    case (FloatLitF(v, va), uw)                 => false
    case (StringLitF(v, va), uw)                => false
    case (BoolLitF(v, va), uw)                  => false
    case (NoneLitF(v), uw)                      => false
    case (IdentifierF(v, va), uw)               => false
  }

  def relationTargetEntityName(x0: type_expr): Option[String] = x0 match {
    case RelationTypeF(uu, uv, NamedTypeF(n, uw), ux)            => Some[String](n)
    case NamedTypeF(n, uy)                                       => Some[String](n)
    case SetTypeF(v, va)                                         => None
    case MapTypeF(v, va, vb)                                     => None
    case SeqTypeF(v, va)                                         => None
    case OptionTypeF(v, va)                                      => None
    case RelationTypeF(v, va, SetTypeF(vd, ve), vc)              => None
    case RelationTypeF(v, va, MapTypeF(vd, ve, vf), vc)          => None
    case RelationTypeF(v, va, SeqTypeF(vd, ve), vc)              => None
    case RelationTypeF(v, va, OptionTypeF(vd, ve), vc)           => None
    case RelationTypeF(v, va, RelationTypeF(vd, ve, vf, vg), vc) => None
  }

  def disambiguateKeysAux[A](
      x0: List[(String, A)],
      uu: List[String],
      acc: List[(String, A)]
  ): List[(String, A)] =
    (x0, uu, acc) match {
      case (Nil, uu, acc) => rev[(String, A)](acc)
      case ((base, v) :: rest, seen, acc) =>
        val key =
          (membera[String](seen, base) match {
            case true  => freshKey(base, seen)
            case false => base
          }): String;
        disambiguateKeysAux[A](rest, key :: seen, (key, v) :: acc)
    }

  def disambiguateKeys[A](pairs: List[(String, A)]): List[(String, A)] =
    disambiguateKeysAux[A](pairs, Nil, Nil)

  def extractTsTuples(
      x0: List[convention_rule],
      uu: List[String]
  ): List[(String, (String, String))] =
    (x0, uu) match {
      case (Nil, uu) => Nil
      case (r :: rest, ens) => extractTsTuple(r, ens) match {
          case None    => extractTsTuples(rest, ens)
          case Some(t) => t :: extractTsTuples(rest, ens)
        }
    }

  def literalStartsWithSlash(s: String): Boolean =
    Str_Literal.asciisOfLiteral(s) match {
      case Nil    => false
      case c :: _ => c == BigInt(47)
    }

  def parseHttpPathPv(e: expr): convention_value =
    asStringLit(e) match {
      case None => CvBad(ExpectedString(), e)
      case Some(v) =>
        literalStartsWithSlash(v) match {
          case true  => CvOk(PvString(v))
          case false => CvBad(HttpPathMissingSlash(), e)
        }
    }

  def parseStrategyPv(e: expr): convention_value =
    asStringLit(e) match {
      case None => CvBad(ExpectedString(), e)
      case Some(v) =>
        splitOnColon(v) match {
          case None => CvBad(BadStrategyFormat(v), e)
          case Some((m, s)) =>
            literalIsEmpty(m) || literalIsEmpty(s) match {
              case true  => CvBad(BadStrategyFormat(v), e)
              case false => CvOk(PvStrPair(m, s))
            }
        }
    }

  def classificationOperationName(x0: operation_classification): String = x0 match {
    case OperationClassification(n, uu, uv, uw, ux, uy, uz) => n
  }

  def collectPreservedRelations(es: List[expr], stateFields: List[String]): List[String] =
    remdups[String](maps[expr, String](
      (a: expr) =>
        preservedRelationOf(stateFields, a),
      flattenEnsures(es)
    ))

  def detectKeyExistsInRequires(requiresa: List[expr], stateFields: List[String]): List[String] =
    remdups[String](maps[expr, String](
      (a: expr) =>
        keyExistsInRequiresOf(stateFields, a),
      flattenEnsures(requiresa)
    ))

  def operationMissingEnsures(x0: operation_decl): Boolean = x0 match {
    case OperationDeclFull(uu, uv, outputs, uw, ensures, ux, uy) =>
      !nulla[param_decl](outputs) && nulla[expr](ensures)
  }

  def anonInvariantName(idx: nat): String = "anon_" + showNat(idx)

  def classifyColumnCheckAtom(e: expr): column_check_class =
    decomposeAtom(e) match {
      case RaLenCmp(a, b)       => CcLenCompare(a, b)
      case RaValueCmp(a, b)     => CcValueCompare(a, b)
      case RaMatches(a)         => CcRegexMatch(a)
      case RaMatchesIdent(_, a) => CcRegexMatch(a)
      case RaPredCall(_)        => CcSkip()
      case RaUnknown(_) =>
        e match {
          case BinaryOpF(op, lhs, rhs, _) =>
            isLiteral(rhs) && !is_none[String](sqlOp(op)) match {
              case true => isLenOfValue(lhs) match {
                  case true => CcLenLitCompare(op, rhs)
                  case false => isValueRef(lhs) match {
                      case true  => CcValueLitCompare(op, rhs)
                      case false => CcSkip()
                    }
                }
              case false => CcSkip()
            }
          case UnaryOpF(_, _, _)             => CcSkip()
          case QuantifierF(_, _, _, _)       => CcSkip()
          case SomeWrapF(_, _)               => CcSkip()
          case TheF(_, _, _, _)              => CcSkip()
          case FieldAccessF(_, _, _)         => CcSkip()
          case EnumAccessF(_, _, _)          => CcSkip()
          case IndexF(_, _, _)               => CcSkip()
          case CallF(_, _, _)                => CcSkip()
          case PrimeF(_, _)                  => CcSkip()
          case PreF(_, _)                    => CcSkip()
          case WithF(_, _, _)                => CcSkip()
          case IfF(_, _, _, _)               => CcSkip()
          case LetF(_, _, _, _)              => CcSkip()
          case LambdaF(_, _, _)              => CcSkip()
          case ConstructorF(_, _, _)         => CcSkip()
          case SetLiteralF(_, _)             => CcSkip()
          case MapLiteralF(_, _)             => CcSkip()
          case SetComprehensionF(_, _, _, _) => CcSkip()
          case SeqLiteralF(_, _)             => CcSkip()
          case MatchesF(_, _, _)             => CcSkip()
          case IntLitF(_, _)                 => CcSkip()
          case FloatLitF(_, _)               => CcSkip()
          case StringLitF(_, _)              => CcSkip()
          case BoolLitF(_, _)                => CcSkip()
          case NoneLitF(_)                   => CcSkip()
          case IdentifierF(_, _)             => CcSkip()
        }
    }

  def paramListHasName(x0: List[param_decl], uu: String): Boolean = (x0, uu) match {
    case (Nil, uu) => false
    case (ParamDeclFull(pn, uv, uw) :: rest, nm) =>
      pn == nm match {
        case true  => true
        case false => paramListHasName(rest, nm)
      }
  }

  def buildOperationClassification(
      name: String,
      targetEntity: Option[String],
      strategy: synthesis_strategy,
      x3: classification_result
  ): operation_classification =
    (name, targetEntity, strategy, x3) match {
      case (name, targetEntity, strategy, ClassificationResult(k, m, rule, sig)) =>
        OperationClassification(name, k, m, rule, targetEntity, strategy, sig)
    }

  def rewriteEntityFieldRefs(flds: List[String], e: expr): expr =
    rewriteFieldRefsAux(flds, Nil, e)

  def capsFkEnforcedByDefault(x0: dialect_caps): Boolean = x0 match {
    case DialectCaps(uu, uv, uw, d, ux, uy) => d
  }

  def postgresSerialColumnDef(name: String, t: canonical_type): String =
    name +
      (isSerial4(t) match {
        case true  => " SERIAL NOT NULL"
        case false => " BIGSERIAL NOT NULL"
      })

  def detectAggregateInvariant(invExpr: expr): Option[detected_aggregate] =
    invExpr match {
      case BinaryOpF(BAnd(), _, _, _)     => None
      case BinaryOpF(BOr(), _, _, _)      => None
      case BinaryOpF(BImplies(), _, _, _) => None
      case BinaryOpF(BIff(), _, _, _)     => None
      case BinaryOpF(BEq(), lhs, rhs, _) =>
        extractFieldName(lhs) match {
          case None => None
          case Some(tgt) =>
            decodeAggregateCall(rhs) match {
              case None => None
              case Some(AggregateCall(coll, agg, src)) =>
                Some[detected_aggregate](DetectedAggregate(tgt, coll, agg, src))
            }
        }
      case BinaryOpF(BNeq(), _, _, _)       => None
      case BinaryOpF(BLt(), _, _, _)        => None
      case BinaryOpF(BGt(), _, _, _)        => None
      case BinaryOpF(BLe(), _, _, _)        => None
      case BinaryOpF(BGe(), _, _, _)        => None
      case BinaryOpF(BIn(), _, _, _)        => None
      case BinaryOpF(BNotIn(), _, _, _)     => None
      case BinaryOpF(BSubset(), _, _, _)    => None
      case BinaryOpF(BUnion(), _, _, _)     => None
      case BinaryOpF(BIntersect(), _, _, _) => None
      case BinaryOpF(BDiff(), _, _, _)      => None
      case BinaryOpF(BAdd(), _, _, _)       => None
      case BinaryOpF(BSub(), _, _, _)       => None
      case BinaryOpF(BMul(), _, _, _)       => None
      case BinaryOpF(BDiv(), _, _, _)       => None
      case UnaryOpF(_, _, _)                => None
      case QuantifierF(_, _, _, _)          => None
      case SomeWrapF(_, _)                  => None
      case TheF(_, _, _, _)                 => None
      case FieldAccessF(_, _, _)            => None
      case EnumAccessF(_, _, _)             => None
      case IndexF(_, _, _)                  => None
      case CallF(_, _, _)                   => None
      case PrimeF(_, _)                     => None
      case PreF(_, _)                       => None
      case WithF(_, _, _)                   => None
      case IfF(_, _, _, _)                  => None
      case LetF(_, _, _, _)                 => None
      case LambdaF(_, _, _)                 => None
      case ConstructorF(_, _, _)            => None
      case SetLiteralF(_, _)                => None
      case MapLiteralF(_, _)                => None
      case SetComprehensionF(_, _, _, _)    => None
      case SeqLiteralF(_, _)                => None
      case MatchesF(_, _, _)                => None
      case IntLitF(_, _)                    => None
      case FloatLitF(_, _)                  => None
      case StringLitF(_, _)                 => None
      case BoolLitF(_, _)                   => None
      case NoneLitF(_)                      => None
      case IdentifierF(_, _)                => None
    }

  def extractPartialIndexRuleOpt(x0: convention_rule): Option[(String, (String, String))] =
    x0 match {
      case ConventionRuleFull(target, prop, colOpt, value, uu) =>
        prop == "partial_index" match {
          case true => (colOpt, value) match {
              case (None, _) => None
              case (Some(col), CvOk(PvString(filt))) =>
                Some[(String, (String, String))]((target, (col, filt)))
              case (Some(_), CvOk(PvInt(_)))        => None
              case (Some(_), CvOk(PvBool(_)))       => None
              case (Some(_), CvOk(PvStrPair(_, _))) => None
              case (Some(_), CvOk(PvExpr(_)))       => None
              case (Some(_), CvBad(_, _))           => None
              case (Some(_), CvUnknown(_))          => None
            }
          case false => None
        }
    }

  def extractPartialIndexRules(conv: Option[conventions_decl]): List[(String, (String, String))] =
    conv match {
      case None => Nil
      case Some(ConventionsDeclFull(rs, _)) =>
        map_filter[convention_rule, (String, (String, String))](
          (a: convention_rule) =>
            extractPartialIndexRuleOpt(a),
          rs
        )
    }

  def widenExplicitIdPkSqlType(field_name: String, sql_type: String): String =
    field_name == "id" && sql_type == "INTEGER" match {
      case true  => "BIGINT"
      case false => sql_type
    }

  def otherPairsForField(uu: String, x1: List[(String, (String, String))]): List[(String, String)] =
    (uu, x1) match {
      case (uu, Nil) => Nil
      case (curTarget, (uv, (t, v)) :: rest) =>
        !(t == curTarget) match {
          case true  => (t, v) :: otherPairsForField(curTarget, rest)
          case false => otherPairsForField(curTarget, rest)
        }
    }

  def collisionsForRule(
      rule: convention_rule,
      tuples: List[(String, (String, String))],
      entityNames: List[String]
  ): List[(String, String)] =
    extractTsTuple(rule, entityNames) match {
      case None => Nil
      case Some((field, (target, _))) =>
        val sameField =
          fieldFilter(field, tuples): List[(String, (String, String))]
        val targets = remdups[String](targetsOf(sameField)): List[String]
        val values  = remdups[String](valuesOf(sameField)): List[String];
        less_nat(one_nat, size_list[String](targets)) &&
          less_nat(one_nat, size_list[String](values)) match {
          case true  => remdups[(String, String)](otherPairsForField(target, sameField))
          case false => Nil
        }
    }

  def parseHttpHeaderPv(e: expr): convention_value =
    asStringLit(e) match {
      case None    => CvOk(PvExpr(e))
      case Some(v) => CvOk(PvString(v))
    }

  def parseHttpMethodPv(e: expr): convention_value =
    asStringLit(e) match {
      case None => CvBad(ExpectedString(), e)
      case Some(v) => parseHttpMethod(v) match {
          case None    => CvBad(BadHttpMethod(v), e)
          case Some(_) => CvOk(PvString(v))
        }
    }

  def parseHttpStatusPv(e: expr): convention_value =
    asIntLit(e) match {
      case None => CvBad(ExpectedInteger(), e)
      case Some(n) =>
        less_eq_int(BigInt(100), n) && less_eq_int(n, BigInt(599)) match {
          case true  => CvOk(PvInt(n))
          case false => CvBad(HttpStatusOutOfRange(n), e)
        }
    }

  def signalsTargetEntityFieldCount(x0: analysis_signals): Option[nat] = x0 match {
    case AnalysisSignals(uu, uv, uw, ux, t, uy, uz, va, vb) => t
  }

  def capsSupportsPartialIndex(x0: dialect_caps): Boolean = x0 match {
    case DialectCaps(a, uu, uv, uw, ux, uy) => a
  }

  def findOperationByName(x0: List[operation_decl], uu: String): Option[operation_decl] =
    (x0, uu) match {
      case (Nil, uu) => None
      case (OperationDeclFull(n, a, b, c, d, ra, e) :: rest, nm) =>
        n == nm match {
          case true  => Some[operation_decl](OperationDeclFull(n, a, b, c, d, ra, e))
          case false => findOperationByName(rest, nm)
        }
    }

  def parseTestStrategyPv(e: expr): convention_value =
    asStringLit(e) match {
      case None => CvBad(ExpectedString(), e)
      case Some(v) =>
        v == "live" match {
          case true => CvOk(PvBool(true))
          case false => v == "redacted" match {
              case true  => CvOk(PvBool(false))
              case false => CvBad(BadTestStrategy(v), e)
            }
        }
    }

  def parsedValueToString(pv: parsed_value): Option[String] =
    pv match {
      case PvString(a)     => Some[String](a)
      case PvInt(n)        => Some[String](showInt(n))
      case PvBool(b)       => Some[String](showBool(b))
      case PvStrPair(a, b) => Some[String](a + ":" + b)
      case PvExpr(_)       => None
    }

  def classifyGlobalVerifier(ir: service_ir): verifier_tool =
    foldVerifier(invariantBodies(ir))

  def collectionElementEntityName(ty: type_expr): Option[String] =
    ty match {
      case NamedTypeF(_, _)                       => None
      case SetTypeF(NamedTypeF(n, _), _)          => Some[String](n)
      case SetTypeF(SetTypeF(_, _), _)            => None
      case SetTypeF(MapTypeF(_, _, _), _)         => None
      case SetTypeF(SeqTypeF(_, _), _)            => None
      case SetTypeF(OptionTypeF(_, _), _)         => None
      case SetTypeF(RelationTypeF(_, _, _, _), _) => None
      case MapTypeF(_, _, _)                      => None
      case SeqTypeF(NamedTypeF(n, _), _)          => Some[String](n)
      case SeqTypeF(SetTypeF(_, _), _)            => None
      case SeqTypeF(MapTypeF(_, _, _), _)         => None
      case SeqTypeF(SeqTypeF(_, _), _)            => None
      case SeqTypeF(OptionTypeF(_, _), _)         => None
      case SeqTypeF(RelationTypeF(_, _, _, _), _) => None
      case OptionTypeF(_, _)                      => None
      case RelationTypeF(_, _, _, _)              => None
    }

  def parseNonEmptyStringPv(e: expr): convention_value =
    asStringLit(e) match {
      case None => CvBad(ExpectedString(), e)
      case Some(v) =>
        literalIsBlank(v) match {
          case true  => CvBad(EmptyString(), e)
          case false => CvOk(PvString(v))
        }
    }

  def parseConventionValue(prop: String, e: expr): convention_value =
    prop == "http_method" match {
      case true => parseHttpMethodPv(e)
      case false => prop == "http_status_success" match {
          case true => parseHttpStatusPv(e)
          case false => prop == "http_path" match {
              case true => parseHttpPathPv(e)
              case false => prop == "http_header" match {
                  case true => parseHttpHeaderPv(e)
                  case false => prop == "db_table" match {
                      case true => parseNonEmptyStringPv(e)
                      case false => prop == "db_timestamps" match {
                          case true => parseBoolPv(e)
                          case false => prop == "plural" match {
                              case true => parseNonEmptyStringPv(e)
                              case false => prop == "partial_index" match {
                                  case true => parseNonEmptyStringPv(e)
                                  case false => prop ==
                                      "test_strategy" match {
                                      case true => parseTestStrategyPv(e)
                                      case false => prop == "strategy" match {
                                          case true  => parseStrategyPv(e)
                                          case false => CvUnknown(e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

  def synthConventionValue(v: convention_value): expr =
    v match {
      case CvOk(PvString(s))     => StringLitF(s, None)
      case CvOk(PvInt(n))        => IntLitF(n, None)
      case CvOk(PvBool(b))       => BoolLitF(b, None)
      case CvOk(PvStrPair(a, b)) => StringLitF(a + ":" + b, None)
      case CvOk(PvExpr(e))       => e
      case CvBad(_, raw)         => raw
      case CvUnknown(raw)        => raw
    }

  def classifyEnabledVerifier(op: operation_decl, ir: service_ir): verifier_tool =
    foldVerifier(operationRequires(op) ++ invariantBodies(ir))

  def classifyAlloyBindingIdentifier(
      name: String,
      entities: List[entity_decl],
      enums: List[enum_decl],
      stateFields: List[(String, type_expr)],
      inputFields: List[(String, type_expr)]
  ): alloy_binding_identifier_resolution =
    entityNameInList(entities, name) match {
      case None =>
        enumNameInList(enums, name) match {
          case None =>
            list_ex[(String, type_expr)](
              (kv: (String, type_expr)) =>
                fst[String, type_expr](kv) == name,
              stateFields
            ) ||
              list_ex[(String, type_expr)](
                (kv: (String, type_expr)) =>
                  fst[String, type_expr](kv) == name,
                inputFields
              ) match {
              case true  => AbirStateOrInput()
              case false => AbirPlain()
            }
          case Some(a) => AbirEnum(a)
        }
      case Some(a) => AbirEntity(a)
    }

  def capsRequiresTextIndexPrefix(x0: dialect_caps): Boolean = x0 match {
    case DialectCaps(uu, uv, uw, ux, e, uy) => e
  }

  def capsSupportsCheckConstraint(x0: dialect_caps): Boolean = x0 match {
    case DialectCaps(uu, b, uv, uw, ux, uy) => b
  }

  def operationHasParamNamed(x0: operation_decl, nm: String): Boolean =
    (x0, nm) match {
      case (OperationDeclFull(uu, inputs, uv, uw, ux, uy, uz), nm) =>
        paramListHasName(inputs, nm)
    }

  def validateIrContextRule(
      x0: convention_rule,
      entities: List[entity_decl],
      ops: List[operation_decl]
  ): List[convention_ir_diagnostic] =
    (x0, entities, ops) match {
      case (ConventionRuleFull(target, prop, qualOpt, uu, uv), entities, ops) =>
        qualOpt match {
          case None => Nil
          case Some(field) =>
            prop == "partial_index" match {
              case true => entityByName(entities, target) match {
                  case None => Nil
                  case Some(_) =>
                    entityHasField(entities, target, field) match {
                      case true  => Nil
                      case false => List(PartialIndexFieldMissing(target, field))
                    }
                }
              case false => prop == "test_strategy" match {
                  case true =>
                    val opMatch     = findOperationByName(ops, target): Option[operation_decl]
                    val entityMatch = entityByName(entities, target): Option[entity_decl]
                    val inParams =
                      (opMatch match {
                        case None     => false
                        case Some(op) => operationHasParamNamed(op, field)
                      }): Boolean
                    val inEntity = entityHasField(entities, target, field): Boolean
                    val targetKind =
                      (!is_none[operation_decl](opMatch) match {
                        case true => "operation"
                        case false => !is_none[entity_decl](entityMatch) match {
                            case true  => "entity"
                            case false => "target"
                          }
                      }): String;
                    inParams || inEntity match {
                      case true  => Nil
                      case false => List(TestStrategyFieldMissing(target, field, targetKind))
                    }
                  case false => Nil
                }
            }
        }
    }

  def classifyRequiresVerifier(op: operation_decl): verifier_tool =
    foldVerifier(operationRequires(op))

  def classifyTemporalVerifier: verifier_tool = VtAlloy()

  def classifyInvariantVerifier(ivd: invariant_decl): verifier_tool =
    requiresAlloy(invariantBody(ivd)) match {
      case true  => VtAlloy()
      case false => VtZ3()
    }

  def classifyPreservationVerifier(op: operation_decl, ivd: invariant_decl): verifier_tool =
    foldVerifier(invariantBody(ivd) ::
      operationRequires(op) ++ operationEnsures(op))

  def capsSupportsCheckOnAutoIncrement(x0: dialect_caps): Boolean = x0 match {
    case DialectCaps(uu, uv, c, uw, ux, uy) => c
  }

} /* object SpecRestGenerated */
