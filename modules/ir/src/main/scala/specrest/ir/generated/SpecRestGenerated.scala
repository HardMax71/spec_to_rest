package specrest.ir.generated

import scala.annotation.nowarn

@nowarn
object SpecRestGenerated {

  def equal_bool(p: Boolean, pa: Boolean): Boolean = (p, pa) match {
    case (false, p) => !p
    case (true, p)  => p
    case (p, false) => !p
    case (p, true)  => p
  }

  trait equal[A] {
    val `SpecRestGenerated.equal`: (A, A) => Boolean
  }
  def equal[A](a: A, b: A)(implicit A: equal[A]): Boolean =
    A.`SpecRestGenerated.equal`(a, b)
  object equal {
    implicit def `SpecRestGenerated.equal_ir_value`: equal[ir_value] = new equal[ir_value] {
      val `SpecRestGenerated.equal` = (a: ir_value, b: ir_value) =>
        equal_ir_valuea(a, b)
    }
    implicit def `SpecRestGenerated.equal_literal`: equal[String] = new equal[String] {
      val `SpecRestGenerated.equal` = (a: String, b: String) => a == b
    }
    implicit def `SpecRestGenerated.equal_smt_val`: equal[smt_val] = new equal[smt_val] {
      val `SpecRestGenerated.equal` = (a: smt_val, b: smt_val) =>
        equal_smt_vala(a, b)
    }
  }

  def eq[A: equal](a: A, b: A): Boolean = equal[A](a, b)

  def equal_list[A: equal](x0: List[A], x1: List[A]): Boolean = (x0, x1) match {
    case (Nil, x21 :: x22)        => false
    case (x21 :: x22, Nil)        => false
    case (x21 :: x22, y21 :: y22) => eq[A](x21, y21) && equal_list[A](x22, y22)
    case (Nil, Nil)               => true
  }

  sealed abstract class int
  final case class int_of_integer(a: BigInt) extends int

  def integer_of_int(x0: int): BigInt = x0 match {
    case int_of_integer(k) => k
  }

  def equal_int(k: int, l: int): Boolean =
    integer_of_int(k) == integer_of_int(l)

  sealed abstract class smt_val
  final case class SBool(a: Boolean)                              extends smt_val
  final case class SInt(a: int)                                   extends smt_val
  final case class SEnumElem(a: String, b: String)                extends smt_val
  final case class SEntityElem(a: String, b: String)              extends smt_val
  final case class SSet(a: List[smt_val])                         extends smt_val
  final case class SEntityWith(a: smt_val, b: String, c: smt_val) extends smt_val

  def equal_smt_vala(x0: smt_val, x1: smt_val): Boolean = (x0, x1) match {
    case (SSet(x5), SEntityWith(x61, x62, x63))              => false
    case (SEntityWith(x61, x62, x63), SSet(x5))              => false
    case (SEntityElem(x41, x42), SEntityWith(x61, x62, x63)) => false
    case (SEntityWith(x61, x62, x63), SEntityElem(x41, x42)) => false
    case (SEntityElem(x41, x42), SSet(x5))                   => false
    case (SSet(x5), SEntityElem(x41, x42))                   => false
    case (SEnumElem(x31, x32), SEntityWith(x61, x62, x63))   => false
    case (SEntityWith(x61, x62, x63), SEnumElem(x31, x32))   => false
    case (SEnumElem(x31, x32), SSet(x5))                     => false
    case (SSet(x5), SEnumElem(x31, x32))                     => false
    case (SEnumElem(x31, x32), SEntityElem(x41, x42))        => false
    case (SEntityElem(x41, x42), SEnumElem(x31, x32))        => false
    case (SInt(x2), SEntityWith(x61, x62, x63))              => false
    case (SEntityWith(x61, x62, x63), SInt(x2))              => false
    case (SInt(x2), SSet(x5))                                => false
    case (SSet(x5), SInt(x2))                                => false
    case (SInt(x2), SEntityElem(x41, x42))                   => false
    case (SEntityElem(x41, x42), SInt(x2))                   => false
    case (SInt(x2), SEnumElem(x31, x32))                     => false
    case (SEnumElem(x31, x32), SInt(x2))                     => false
    case (SBool(x1), SEntityWith(x61, x62, x63))             => false
    case (SEntityWith(x61, x62, x63), SBool(x1))             => false
    case (SBool(x1), SSet(x5))                               => false
    case (SSet(x5), SBool(x1))                               => false
    case (SBool(x1), SEntityElem(x41, x42))                  => false
    case (SEntityElem(x41, x42), SBool(x1))                  => false
    case (SBool(x1), SEnumElem(x31, x32))                    => false
    case (SEnumElem(x31, x32), SBool(x1))                    => false
    case (SBool(x1), SInt(x2))                               => false
    case (SInt(x2), SBool(x1))                               => false
    case (SEntityWith(x61, x62, x63), SEntityWith(y61, y62, y63)) =>
      equal_smt_vala(x61, y61) && (x62 == y62 && equal_smt_vala(x63, y63))
    case (SSet(x5), SSet(y5)) => equal_list[smt_val](x5, y5)
    case (SEntityElem(x41, x42), SEntityElem(y41, y42)) =>
      x41 == y41 && x42 == y42
    case (SEnumElem(x31, x32), SEnumElem(y31, y32)) => x31 == y31 && x32 == y32
    case (SInt(x2), SInt(y2))                       => equal_int(x2, y2)
    case (SBool(x1), SBool(y1))                     => equal_bool(x1, y1)
  }

  sealed abstract class ir_value
  final case class VBool(a: Boolean)                                extends ir_value
  final case class VInt(a: int)                                     extends ir_value
  final case class VEnum(a: String, b: String)                      extends ir_value
  final case class VEntity(a: String, b: String)                    extends ir_value
  final case class VSet(a: List[ir_value])                          extends ir_value
  final case class VEntityWith(a: ir_value, b: String, c: ir_value) extends ir_value

  def equal_ir_valuea(x0: ir_value, x1: ir_value): Boolean = (x0, x1) match {
    case (VSet(x5), VEntityWith(x61, x62, x63))          => false
    case (VEntityWith(x61, x62, x63), VSet(x5))          => false
    case (VEntity(x41, x42), VEntityWith(x61, x62, x63)) => false
    case (VEntityWith(x61, x62, x63), VEntity(x41, x42)) => false
    case (VEntity(x41, x42), VSet(x5))                   => false
    case (VSet(x5), VEntity(x41, x42))                   => false
    case (VEnum(x31, x32), VEntityWith(x61, x62, x63))   => false
    case (VEntityWith(x61, x62, x63), VEnum(x31, x32))   => false
    case (VEnum(x31, x32), VSet(x5))                     => false
    case (VSet(x5), VEnum(x31, x32))                     => false
    case (VEnum(x31, x32), VEntity(x41, x42))            => false
    case (VEntity(x41, x42), VEnum(x31, x32))            => false
    case (VInt(x2), VEntityWith(x61, x62, x63))          => false
    case (VEntityWith(x61, x62, x63), VInt(x2))          => false
    case (VInt(x2), VSet(x5))                            => false
    case (VSet(x5), VInt(x2))                            => false
    case (VInt(x2), VEntity(x41, x42))                   => false
    case (VEntity(x41, x42), VInt(x2))                   => false
    case (VInt(x2), VEnum(x31, x32))                     => false
    case (VEnum(x31, x32), VInt(x2))                     => false
    case (VBool(x1), VEntityWith(x61, x62, x63))         => false
    case (VEntityWith(x61, x62, x63), VBool(x1))         => false
    case (VBool(x1), VSet(x5))                           => false
    case (VSet(x5), VBool(x1))                           => false
    case (VBool(x1), VEntity(x41, x42))                  => false
    case (VEntity(x41, x42), VBool(x1))                  => false
    case (VBool(x1), VEnum(x31, x32))                    => false
    case (VEnum(x31, x32), VBool(x1))                    => false
    case (VBool(x1), VInt(x2))                           => false
    case (VInt(x2), VBool(x1))                           => false
    case (VEntityWith(x61, x62, x63), VEntityWith(y61, y62, y63)) =>
      equal_ir_valuea(x61, y61) && (x62 == y62 && equal_ir_valuea(x63, y63))
    case (VSet(x5), VSet(y5))                   => equal_list[ir_value](x5, y5)
    case (VEntity(x41, x42), VEntity(y41, y42)) => x41 == y41 && x42 == y42
    case (VEnum(x31, x32), VEnum(y31, y32))     => x31 == y31 && x32 == y32
    case (VInt(x2), VInt(y2))                   => equal_int(x2, y2)
    case (VBool(x1), VBool(y1))                 => equal_bool(x1, y1)
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

  sealed abstract class bool_bin_op
  final case class AndOp()     extends bool_bin_op
  final case class OrOp()      extends bool_bin_op
  final case class ImpliesOp() extends bool_bin_op
  final case class IffOp()     extends bool_bin_op

  sealed abstract class arith_op
  final case class AddOp() extends arith_op
  final case class SubOp() extends arith_op
  final case class MulOp() extends arith_op
  final case class DivOp() extends arith_op

  sealed abstract class span_t
  final case class SpanT(a: int, b: int, c: int, d: int) extends span_t

  sealed abstract class set_op
  final case class UnionOp()     extends set_op
  final case class IntersectOp() extends set_op
  final case class DiffOp()      extends set_op

  sealed abstract class cmp_op
  final case class EqOp()  extends cmp_op
  final case class NeqOp() extends cmp_op
  final case class LtOp()  extends cmp_op
  final case class LeOp()  extends cmp_op
  final case class GtOp()  extends cmp_op
  final case class GeOp()  extends cmp_op

  sealed abstract class expr
  final case class BoolLit(a: Boolean, b: Option[span_t]) extends expr
  final case class IntLit(a: int, b: Option[span_t])      extends expr
  final case class Ident(a: String, b: Option[span_t])    extends expr
  final case class UnNot(a: expr, b: Option[span_t])      extends expr
  final case class UnNeg(a: expr, b: Option[span_t])      extends expr
  final case class BoolBin(a: bool_bin_op, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class Arith(a: arith_op, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class Cmp(a: cmp_op, b: expr, c: expr, d: Option[span_t]) extends expr
  final case class LetIn(a: String, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class EnumAccess(a: String, b: String, c: Option[span_t]) extends expr
  final case class Member(a: expr, b: String, c: Option[span_t])       extends expr
  final case class ForallEnum(a: String, b: String, c: expr, d: Option[span_t])
      extends expr
  final case class ForallRel(a: String, b: String, c: expr, d: Option[span_t])
      extends expr
  final case class Prime(a: expr, b: Option[span_t])                  extends expr
  final case class Pre(a: expr, b: Option[span_t])                    extends expr
  final case class CardRel(a: String, b: Option[span_t])              extends expr
  final case class IndexRel(a: expr, b: expr, c: Option[span_t])      extends expr
  final case class FieldAccess(a: expr, b: String, c: Option[span_t]) extends expr
  final case class SetEmpty(a: Option[span_t])                        extends expr
  final case class SetInsert(a: expr, b: expr, c: Option[span_t])     extends expr
  final case class SetMember(a: expr, b: expr, c: Option[span_t])     extends expr
  final case class SetBin(a: set_op, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class WithRec(a: expr, b: String, c: expr, d: Option[span_t])
      extends expr

  sealed abstract class nat
  final case class Nata(a: BigInt) extends nat

  sealed abstract class num
  final case class One()        extends num
  final case class Bit0(a: num) extends num
  final case class Bit1(a: num) extends num

  sealed abstract class binding_kind_full
  final case class BkIn()    extends binding_kind_full
  final case class BkColon() extends binding_kind_full

  sealed abstract class quant_kind_full
  final case class QAll()    extends quant_kind_full
  final case class QSome()   extends quant_kind_full
  final case class QNo()     extends quant_kind_full
  final case class QExists() extends quant_kind_full

  sealed abstract class bin_op_full
  final case class BAnd()       extends bin_op_full
  final case class BOr()        extends bin_op_full
  final case class BImplies()   extends bin_op_full
  final case class BIff()       extends bin_op_full
  final case class BEq()        extends bin_op_full
  final case class BNeq()       extends bin_op_full
  final case class BLt()        extends bin_op_full
  final case class BGt()        extends bin_op_full
  final case class BLe()        extends bin_op_full
  final case class BGe()        extends bin_op_full
  final case class BIn()        extends bin_op_full
  final case class BNotIn()     extends bin_op_full
  final case class BSubset()    extends bin_op_full
  final case class BUnion()     extends bin_op_full
  final case class BIntersect() extends bin_op_full
  final case class BDiff()      extends bin_op_full
  final case class BAdd()       extends bin_op_full
  final case class BSub()       extends bin_op_full
  final case class BMul()       extends bin_op_full
  final case class BDiv()       extends bin_op_full

  sealed abstract class un_op_full
  final case class UNot()         extends un_op_full
  final case class UNegate()      extends un_op_full
  final case class UCardinality() extends un_op_full
  final case class UPower()       extends un_op_full

  sealed abstract class quantifier_binding_full
  final case class QuantifierBindingFull(
      a: String,
      b: expr_full,
      c: binding_kind_full,
      d: Option[span_t]
  ) extends quantifier_binding_full

  sealed abstract class field_assign_full
  final case class FieldAssignFull(a: String, b: expr_full, c: Option[span_t])
      extends field_assign_full

  sealed abstract class map_entry_full
  final case class MapEntryFull(a: expr_full, b: expr_full, c: Option[span_t])
      extends map_entry_full

  sealed abstract class expr_full
  final case class BinaryOpF(a: bin_op_full, b: expr_full, c: expr_full, d: Option[span_t])
      extends expr_full
  final case class UnaryOpF(a: un_op_full, b: expr_full, c: Option[span_t])
      extends expr_full
  final case class QuantifierF(
      a: quant_kind_full,
      b: List[quantifier_binding_full],
      c: expr_full,
      d: Option[span_t]
  ) extends expr_full
  final case class SomeWrapF(a: expr_full, b: Option[span_t]) extends expr_full
  final case class TheF(a: String, b: expr_full, c: expr_full, d: Option[span_t])
      extends expr_full
  final case class FieldAccessF(a: expr_full, b: String, c: Option[span_t])
      extends expr_full
  final case class EnumAccessF(a: expr_full, b: String, c: Option[span_t])
      extends expr_full
  final case class IndexF(a: expr_full, b: expr_full, c: Option[span_t])
      extends expr_full
  final case class CallF(a: expr_full, b: List[expr_full], c: Option[span_t])
      extends expr_full
  final case class PrimeF(a: expr_full, b: Option[span_t]) extends expr_full
  final case class PreF(a: expr_full, b: Option[span_t])   extends expr_full
  final case class WithF(a: expr_full, b: List[field_assign_full], c: Option[span_t])
      extends expr_full
  final case class IfF(a: expr_full, b: expr_full, c: expr_full, d: Option[span_t])
      extends expr_full
  final case class LetF(a: String, b: expr_full, c: expr_full, d: Option[span_t])
      extends expr_full
  final case class LambdaF(a: String, b: expr_full, c: Option[span_t]) extends expr_full
  final case class ConstructorF(a: String, b: List[field_assign_full], c: Option[span_t])
      extends expr_full
  final case class SetLiteralF(a: List[expr_full], b: Option[span_t]) extends expr_full
  final case class MapLiteralF(a: List[map_entry_full], b: Option[span_t])
      extends expr_full
  final case class SetComprehensionF(a: String, b: expr_full, c: expr_full, d: Option[span_t])
      extends expr_full
  final case class SeqLiteralF(a: List[expr_full], b: Option[span_t])   extends expr_full
  final case class MatchesF(a: expr_full, b: String, c: Option[span_t]) extends expr_full
  final case class IntLitF(a: int, b: Option[span_t])                   extends expr_full
  final case class FloatLitF(a: String, b: Option[span_t])              extends expr_full
  final case class StringLitF(a: String, b: Option[span_t])             extends expr_full
  final case class BoolLitF(a: Boolean, b: Option[span_t])              extends expr_full
  final case class NoneLitF(a: Option[span_t])                          extends expr_full
  final case class IdentifierF(a: String, b: Option[span_t])            extends expr_full

  sealed abstract class type_expr
  final case class BoolT()                               extends type_expr
  final case class IntT()                                extends type_expr
  final case class EnumT(a: String)                      extends type_expr
  final case class EntityT(a: String)                    extends type_expr
  final case class RelationT(a: type_expr, b: type_expr) extends type_expr

  sealed abstract class ty
  final case class TBool()            extends ty
  final case class TInt()             extends ty
  final case class TEnum(a: String)   extends ty
  final case class TEntity(a: String) extends ty
  final case class TSet(a: ty)        extends ty

  sealed abstract class smt_term
  final case class BLit(a: Boolean)                               extends smt_term
  final case class ILit(a: int)                                   extends smt_term
  final case class TVar(a: String)                                extends smt_term
  final case class EnumElemConst(a: String, b: String)            extends smt_term
  final case class TNot(a: smt_term)                              extends smt_term
  final case class TAnd(a: smt_term, b: smt_term)                 extends smt_term
  final case class TOr(a: smt_term, b: smt_term)                  extends smt_term
  final case class TImplies(a: smt_term, b: smt_term)             extends smt_term
  final case class TEq(a: smt_term, b: smt_term)                  extends smt_term
  final case class TLt(a: smt_term, b: smt_term)                  extends smt_term
  final case class TNeg(a: smt_term)                              extends smt_term
  final case class TAdd(a: smt_term, b: smt_term)                 extends smt_term
  final case class TSub(a: smt_term, b: smt_term)                 extends smt_term
  final case class TMul(a: smt_term, b: smt_term)                 extends smt_term
  final case class TDiv(a: smt_term, b: smt_term)                 extends smt_term
  final case class TInDom(a: String, b: smt_term)                 extends smt_term
  final case class TCardRel(a: String)                            extends smt_term
  final case class TLetIn(a: String, b: smt_term, c: smt_term)    extends smt_term
  final case class TForallEnum(a: String, b: String, c: smt_term) extends smt_term
  final case class TForallRel(a: String, b: String, c: smt_term)  extends smt_term
  final case class TIndexRel(a: smt_term, b: smt_term)            extends smt_term
  final case class TFieldAccess(a: smt_term, b: String)           extends smt_term
  final case class TSetEmpty()                                    extends smt_term
  final case class TSetInsert(a: smt_term, b: smt_term)           extends smt_term
  final case class TSetMember(a: smt_term, b: smt_term)           extends smt_term
  final case class TSetUnion(a: smt_term, b: smt_term)            extends smt_term
  final case class TSetIntersect(a: smt_term, b: smt_term)        extends smt_term
  final case class TSetDiff(a: smt_term, b: smt_term)             extends smt_term
  final case class TPrime(a: smt_term)                            extends smt_term
  final case class TPre(a: smt_term)                              extends smt_term
  final case class TWithRec(a: smt_term, b: String, c: smt_term)  extends smt_term

  sealed abstract class multiplicity
  final case class MultOne()  extends multiplicity
  final case class MultLone() extends multiplicity
  final case class MultSome() extends multiplicity
  final case class MultSet()  extends multiplicity

  sealed abstract class enum_decl_full
  final case class EnumDeclFull(a: String, b: List[String], c: Option[span_t])
      extends enum_decl_full

  sealed abstract class fact_decl_full
  final case class FactDeclFull(a: Option[String], b: expr_full, c: Option[span_t])
      extends fact_decl_full

  sealed abstract class type_expr_full
  final case class NamedTypeF(a: String, b: Option[span_t])       extends type_expr_full
  final case class SetTypeF(a: type_expr_full, b: Option[span_t]) extends type_expr_full
  final case class MapTypeF(a: type_expr_full, b: type_expr_full, c: Option[span_t])
      extends type_expr_full
  final case class SeqTypeF(a: type_expr_full, b: Option[span_t])    extends type_expr_full
  final case class OptionTypeF(a: type_expr_full, b: Option[span_t]) extends type_expr_full
  final case class RelationTypeF(
      a: type_expr_full,
      b: multiplicity,
      c: type_expr_full,
      d: Option[span_t]
  ) extends type_expr_full

  sealed abstract class with_info_full
  final case class WithInfoFull(a: List[String], b: Option[String]) extends with_info_full

  sealed abstract class field_decl_full
  final case class FieldDeclFull(
      a: String,
      b: type_expr_full,
      c: Option[expr_full],
      d: Option[span_t]
  ) extends field_decl_full

  sealed abstract class param_decl_full
  final case class ParamDeclFull(a: String, b: type_expr_full, c: Option[span_t])
      extends param_decl_full

  sealed abstract class refinement_atom
  final case class RaLenCmp(a: bin_op_full, b: int)     extends refinement_atom
  final case class RaValueCmp(a: bin_op_full, b: int)   extends refinement_atom
  final case class RaMatches(a: String)                 extends refinement_atom
  final case class RaMatchesIdent(a: String, b: String) extends refinement_atom
  final case class RaPredCall(a: String)                extends refinement_atom
  final case class RaUnknown(a: expr_full)              extends refinement_atom

  sealed abstract class convention_rule_full
  final case class ConventionRuleFull(
      a: String,
      b: String,
      c: Option[String],
      d: expr_full,
      e: Option[span_t]
  ) extends convention_rule_full

  sealed abstract class conventions_decl_full
  final case class ConventionsDeclFull(a: List[convention_rule_full], b: Option[span_t])
      extends conventions_decl_full

  sealed abstract class type_alias_decl_full
  final case class TypeAliasDeclFull(
      a: String,
      b: type_expr_full,
      c: Option[expr_full],
      d: Option[span_t]
  ) extends type_alias_decl_full

  sealed abstract class transition_rule_full
  final case class TransitionRuleFull(
      a: String,
      b: String,
      c: String,
      d: Option[expr_full],
      e: Option[span_t]
  ) extends transition_rule_full

  sealed abstract class transition_decl_full
  final case class TransitionDeclFull(
      a: String,
      b: String,
      c: String,
      d: List[transition_rule_full],
      e: Option[span_t]
  ) extends transition_decl_full

  sealed abstract class predicate_decl_full
  final case class PredicateDeclFull(
      a: String,
      b: List[param_decl_full],
      c: expr_full,
      d: Option[span_t]
  ) extends predicate_decl_full

  sealed abstract class operation_decl_full
  final case class OperationDeclFull(
      a: String,
      b: List[param_decl_full],
      c: List[param_decl_full],
      d: List[expr_full],
      e: List[expr_full],
      f: Option[span_t]
  ) extends operation_decl_full

  sealed abstract class invariant_decl_full
  final case class InvariantDeclFull(a: Option[String], b: expr_full, c: Option[span_t])
      extends invariant_decl_full

  sealed abstract class temporal_decl_full
  final case class TemporalDeclFull(a: String, b: expr_full, c: Option[span_t])
      extends temporal_decl_full

  sealed abstract class function_decl_full
  final case class FunctionDeclFull(
      a: String,
      b: List[param_decl_full],
      c: type_expr_full,
      d: expr_full,
      e: Option[span_t]
  ) extends function_decl_full

  sealed abstract class entity_decl_full
  final case class EntityDeclFull(
      a: String,
      b: Option[String],
      c: List[field_decl_full],
      d: List[expr_full],
      e: Option[span_t]
  ) extends entity_decl_full

  sealed abstract class state_field_decl_full
  final case class StateFieldDeclFull(a: String, b: type_expr_full, c: Option[span_t])
      extends state_field_decl_full

  sealed abstract class state_decl_full
  final case class StateDeclFull(a: List[state_field_decl_full], b: Option[span_t])
      extends state_decl_full

  sealed abstract class service_ir_full
  final case class ServiceIRFull(
      a: String,
      b: List[String],
      c: List[entity_decl_full],
      d: List[enum_decl_full],
      e: List[type_alias_decl_full],
      f: Option[state_decl_full],
      g: List[operation_decl_full],
      h: List[transition_decl_full],
      i: List[invariant_decl_full],
      j: List[temporal_decl_full],
      k: List[fact_decl_full],
      l: List[function_decl_full],
      m: List[predicate_decl_full],
      n: Option[conventions_decl_full],
      o: Option[span_t]
  ) extends service_ir_full

  sealed abstract class state_ext[A]
  final case class state_exta[A](
      a: List[(String, ir_value)],
      b: List[(String, List[ir_value])],
      c: List[(String, List[(ir_value, ir_value)])],
      d: List[(String, List[(String, ir_value)])],
      e: A
  ) extends state_ext[A]

  sealed abstract class state_schema_ext[A]
  final case class state_schema_exta[A](a: List[(String, ty)], b: A) extends state_schema_ext[A]

  sealed abstract class tyctx_ext[A]
  final case class tyctx_exta[A](
      a: List[(String, ty)],
      b: state_schema_ext[Unit],
      c: List[entity_decl_full],
      d: List[state_field_decl_full],
      e: List[String],
      f: A
  ) extends tyctx_ext[A]

  sealed abstract class enum_decl_ext[A]
  final case class enum_decl_exta[A](a: String, b: List[String], c: Option[span_t], d: A)
      extends enum_decl_ext[A]

  sealed abstract class field_decl_ext[A]
  final case class field_decl_exta[A](a: String, b: type_expr, c: Option[span_t], d: A)
      extends field_decl_ext[A]

  sealed abstract class entity_decl_ext[A]
  final case class entity_decl_exta[A](
      a: String,
      b: List[field_decl_ext[Unit]],
      c: Option[span_t],
      d: A
  ) extends entity_decl_ext[A]

  sealed abstract class schema_ext[A]
  final case class schema_exta[A](
      a: List[enum_decl_ext[Unit]],
      b: List[entity_decl_ext[Unit]],
      c: A
  ) extends schema_ext[A]

  sealed abstract class smt_model_ext[A]
  final case class smt_model_exta[A](
      a: List[(String, List[String])],
      b: List[(String, smt_val)],
      c: List[(String, List[smt_val])],
      d: List[(String, List[(smt_val, smt_val)])],
      e: List[(String, List[(String, smt_val)])],
      f: A
  ) extends smt_model_ext[A]

  def integer_of_nat(x0: nat): BigInt = x0 match {
    case Nata(x) => x
  }

  def plus_nat(m: nat, n: nat): nat =
    Nata(integer_of_nat(m) + integer_of_nat(n))

  def one_nat: nat = Nata(BigInt(1))

  def Suc(n: nat): nat = plus_nat(n, one_nat)

  def map_option[A, B](f: A => B, x1: Option[A]): Option[B] = (f, x1) match {
    case (f, None)     => None
    case (f, Some(x2)) => Some[B](f(x2))
  }

  def string_in_list(y: String, x1: List[String]): Boolean = (y, x1) match {
    case (y, Nil)     => false
    case (y, x :: xs) => x == y || string_in_list(y, xs)
  }

  def lower_forall_step(
      enums: List[String],
      x1: quantifier_binding_full,
      body: expr,
      sp: Option[span_t]
  ): Option[expr] =
    (enums, x1, body, sp) match {
      case (enums, QuantifierBindingFull(v, IdentifierF(dnm, uu), uv, uw), body, sp) =>
        string_in_list(dnm, enums) match {
          case true  => Some[expr](ForallEnum(v, dnm, body, sp))
          case false => Some[expr](ForallRel(v, dnm, body, sp))
        }
      case (ux, QuantifierBindingFull(v, BinaryOpF(ve, vf, vg, vh), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, UnaryOpF(ve, vf, vg), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, QuantifierF(ve, vf, vg, vh), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, SomeWrapF(ve, vf), vc, vd), uz, va)           => None
      case (ux, QuantifierBindingFull(v, TheF(ve, vf, vg, vh), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, FieldAccessF(ve, vf, vg), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, EnumAccessF(ve, vf, vg), vc, vd), uz, va)  => None
      case (ux, QuantifierBindingFull(v, IndexF(ve, vf, vg), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, CallF(ve, vf, vg), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, PrimeF(ve, vf), vc, vd), uz, va)    => None
      case (ux, QuantifierBindingFull(v, PreF(ve, vf), vc, vd), uz, va)      => None
      case (ux, QuantifierBindingFull(v, WithF(ve, vf, vg), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, IfF(ve, vf, vg, vh), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, LetF(ve, vf, vg, vh), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, LambdaF(ve, vf, vg), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, ConstructorF(ve, vf, vg), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, SetLiteralF(ve, vf), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, MapLiteralF(ve, vf), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, SetComprehensionF(ve, vf, vg, vh), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, SeqLiteralF(ve, vf), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, MatchesF(ve, vf, vg), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, IntLitF(ve, vf), vc, vd), uz, va)   => None
      case (ux, QuantifierBindingFull(v, FloatLitF(ve, vf), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, StringLitF(ve, vf), vc, vd), uz, va) =>
        None
      case (ux, QuantifierBindingFull(v, BoolLitF(ve, vf), vc, vd), uz, va) => None
      case (ux, QuantifierBindingFull(v, NoneLitF(ve), vc, vd), uz, va)     => None
    }

  def lower_forall_bindings(
      uu: List[String],
      x1: List[quantifier_binding_full],
      uv: expr,
      uw: Option[span_t]
  ): Option[expr] =
    (uu, x1, uv, uw) match {
      case (uu, Nil, uv, uw)          => None
      case (enums, List(b), body, sp) => lower_forall_step(enums, b, body, sp)
      case (enums, b :: v :: va, body, sp) =>
        lower_forall_bindings(enums, v :: va, body, sp) match {
          case None        => None
          case Some(inner) => lower_forall_step(enums, b, inner, sp)
        }
    }

  def peel_relation_ref(x0: expr): Option[String] = x0 match {
    case Ident(rel, uu)                        => Some[String](rel)
    case Pre(Ident(rel, uv), uw)               => Some[String](rel)
    case Prime(Ident(rel, ux), uy)             => Some[String](rel)
    case BoolLit(v, va)                        => None
    case IntLit(v, va)                         => None
    case UnNot(v, va)                          => None
    case UnNeg(v, va)                          => None
    case BoolBin(v, va, vb, vc)                => None
    case Arith(v, va, vb, vc)                  => None
    case Cmp(v, va, vb, vc)                    => None
    case LetIn(v, va, vb, vc)                  => None
    case EnumAccess(v, va, vb)                 => None
    case Member(v, va, vb)                     => None
    case ForallEnum(v, va, vb, vc)             => None
    case ForallRel(v, va, vb, vc)              => None
    case Prime(BoolLit(vb, vc), va)            => None
    case Prime(IntLit(vb, vc), va)             => None
    case Prime(UnNot(vb, vc), va)              => None
    case Prime(UnNeg(vb, vc), va)              => None
    case Prime(BoolBin(vb, vc, vd, ve), va)    => None
    case Prime(Arith(vb, vc, vd, ve), va)      => None
    case Prime(Cmp(vb, vc, vd, ve), va)        => None
    case Prime(LetIn(vb, vc, vd, ve), va)      => None
    case Prime(EnumAccess(vb, vc, vd), va)     => None
    case Prime(Member(vb, vc, vd), va)         => None
    case Prime(ForallEnum(vb, vc, vd, ve), va) => None
    case Prime(ForallRel(vb, vc, vd, ve), va)  => None
    case Prime(Prime(vb, vc), va)              => None
    case Prime(Pre(vb, vc), va)                => None
    case Prime(CardRel(vb, vc), va)            => None
    case Prime(IndexRel(vb, vc, vd), va)       => None
    case Prime(FieldAccess(vb, vc, vd), va)    => None
    case Prime(SetEmpty(vb), va)               => None
    case Prime(SetInsert(vb, vc, vd), va)      => None
    case Prime(SetMember(vb, vc, vd), va)      => None
    case Prime(SetBin(vb, vc, vd, ve), va)     => None
    case Prime(WithRec(vb, vc, vd, ve), va)    => None
    case Pre(BoolLit(vb, vc), va)              => None
    case Pre(IntLit(vb, vc), va)               => None
    case Pre(UnNot(vb, vc), va)                => None
    case Pre(UnNeg(vb, vc), va)                => None
    case Pre(BoolBin(vb, vc, vd, ve), va)      => None
    case Pre(Arith(vb, vc, vd, ve), va)        => None
    case Pre(Cmp(vb, vc, vd, ve), va)          => None
    case Pre(LetIn(vb, vc, vd, ve), va)        => None
    case Pre(EnumAccess(vb, vc, vd), va)       => None
    case Pre(Member(vb, vc, vd), va)           => None
    case Pre(ForallEnum(vb, vc, vd, ve), va)   => None
    case Pre(ForallRel(vb, vc, vd, ve), va)    => None
    case Pre(Prime(vb, vc), va)                => None
    case Pre(Pre(vb, vc), va)                  => None
    case Pre(CardRel(vb, vc), va)              => None
    case Pre(IndexRel(vb, vc, vd), va)         => None
    case Pre(FieldAccess(vb, vc, vd), va)      => None
    case Pre(SetEmpty(vb), va)                 => None
    case Pre(SetInsert(vb, vc, vd), va)        => None
    case Pre(SetMember(vb, vc, vd), va)        => None
    case Pre(SetBin(vb, vc, vd, ve), va)       => None
    case Pre(WithRec(vb, vc, vd, ve), va)      => None
    case CardRel(v, va)                        => None
    case IndexRel(v, va, vb)                   => None
    case FieldAccess(v, va, vb)                => None
    case SetEmpty(v)                           => None
    case SetInsert(v, va, vb)                  => None
    case SetMember(v, va, vb)                  => None
    case SetBin(v, va, vb, vc)                 => None
    case WithRec(v, va, vb, vc)                => None
  }

  def lower_with_assigns(
      wv: List[String],
      x1: List[field_assign_full],
      base: expr,
      ww: Option[span_t]
  ): Option[expr] =
    (wv, x1, base, ww) match {
      case (wv, Nil, base, ww) => Some[expr](base)
      case (enums, FieldAssignFull(fld, v, wx) :: rest, base, sp) =>
        lower(enums, v) match {
          case None => None
          case Some(va) =>
            lower_with_assigns(enums, rest, WithRec(base, fld, va, sp), sp)
        }
    }

  def lowerSetList(wu: List[String], x1: List[expr_full], sp: Option[span_t]): Option[expr] =
    (wu, x1, sp) match {
      case (wu, Nil, sp) => Some[expr](SetEmpty(sp))
      case (enums, e :: rest, sp) =>
        (lower(enums, e), lowerSetList(enums, rest, sp)) match {
          case (None, _)           => None
          case (Some(_), None)     => None
          case (Some(ea), Some(s)) => Some[expr](SetInsert(ea, s, sp))
        }
    }

  def lower(uu: List[String], x1: expr_full): Option[expr] = (uu, x1) match {
    case (uu, BoolLitF(b, sp))                   => Some[expr](BoolLit(b, sp))
    case (uv, IntLitF(n, sp))                    => Some[expr](IntLit(n, sp))
    case (uw, IdentifierF(x, sp))                => Some[expr](Ident(x, sp))
    case (ux, FloatLitF(uy, uz))                 => None
    case (va, StringLitF(vb, vc))                => None
    case (vd, NoneLitF(ve))                      => None
    case (vf, LambdaF(vg, vh, vi))               => None
    case (vj, CallF(vk, vl, vm))                 => None
    case (vn, ConstructorF(vo, vp, vq))          => None
    case (vr, MapLiteralF(vs, vt))               => None
    case (vu, SeqLiteralF(vv, vw))               => None
    case (vx, SetComprehensionF(vy, vz, wa, wb)) => None
    case (wc, SomeWrapF(wd, we))                 => None
    case (wf, TheF(wg, wh, wi, wj))              => None
    case (wk, MatchesF(wl, wm, wn))              => None
    case (wo, IfF(wp, wq, wr, ws))               => None
    case (enums, QuantifierF(k, bs, body, sp)) =>
      lower(enums, body) match {
        case None => None
        case Some(bodya) =>
          k match {
            case QAll() => lower_forall_bindings(enums, bs, bodya, sp)
            case QSome() =>
              map_option[expr, expr](
                (e: expr) => UnNot(e, sp),
                lower_forall_bindings(enums, bs, UnNot(bodya, sp), sp)
              )
            case QNo() => lower_forall_bindings(enums, bs, UnNot(bodya, sp), sp)
            case QExists() =>
              map_option[expr, expr](
                (e: expr) => UnNot(e, sp),
                lower_forall_bindings(enums, bs, UnNot(bodya, sp), sp)
              )
          }
      }
    case (enums, UnaryOpF(op, e, sp)) =>
      op match {
        case UNot() =>
          map_option[expr, expr]((ea: expr) => UnNot(ea, sp), lower(enums, e))
        case UNegate() =>
          map_option[expr, expr]((ea: expr) => UnNeg(ea, sp), lower(enums, e))
        case UCardinality() =>
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
            case IdentifierF(x, _)             => Some[expr](CardRel(x, sp))
          }
        case UPower() => None
      }
    case (enums, BinaryOpF(op, l, r, sp)) =>
      op match {
        case BAnd() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)       => None
            case (Some(_), None) => None
            case (Some(la), Some(ra)) =>
              Some[expr](BoolBin(AndOp(), la, ra, sp))
          }
        case BOr() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](BoolBin(OrOp(), la, ra, sp))
          }
        case BImplies() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)       => None
            case (Some(_), None) => None
            case (Some(la), Some(ra)) =>
              Some[expr](BoolBin(ImpliesOp(), la, ra, sp))
          }
        case BIff() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)       => None
            case (Some(_), None) => None
            case (Some(la), Some(ra)) =>
              Some[expr](BoolBin(IffOp(), la, ra, sp))
          }
        case BEq() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Cmp(EqOp(), la, ra, sp))
          }
        case BNeq() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Cmp(NeqOp(), la, ra, sp))
          }
        case BLt() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Cmp(LtOp(), la, ra, sp))
          }
        case BGt() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Cmp(GtOp(), la, ra, sp))
          }
        case BLe() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Cmp(LeOp(), la, ra, sp))
          }
        case BGe() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Cmp(GeOp(), la, ra, sp))
          }
        case BIn() =>
          r match {
            case BinaryOpF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case UnaryOpF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case QuantifierF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case SomeWrapF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case TheF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case FieldAccessF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case EnumAccessF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case IndexF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case CallF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case PrimeF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case PreF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case WithF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case IfF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case LetF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case LambdaF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case ConstructorF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case SetLiteralF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case MapLiteralF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case SetComprehensionF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case SeqLiteralF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case MatchesF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case IntLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case FloatLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case StringLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case BoolLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case NoneLitF(_) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)            => None
                case (Some(_), None)      => None
                case (Some(la), Some(ra)) => Some[expr](SetMember(la, ra, sp))
              }
            case IdentifierF(rel, _) =>
              map_option[expr, expr]((la: expr) => Member(la, rel, sp), lower(enums, l))
          }
        case BNotIn() =>
          r match {
            case BinaryOpF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case UnaryOpF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case QuantifierF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case SomeWrapF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case TheF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case FieldAccessF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case EnumAccessF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case IndexF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case CallF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case PrimeF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case PreF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case WithF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case IfF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case LetF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case LambdaF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case ConstructorF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case SetLiteralF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case MapLiteralF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case SetComprehensionF(_, _, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case SeqLiteralF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case MatchesF(_, _, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case IntLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case FloatLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case StringLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case BoolLitF(_, _) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case NoneLitF(_) =>
              (lower(enums, l), lower(enums, r)) match {
                case (None, _)       => None
                case (Some(_), None) => None
                case (Some(la), Some(ra)) =>
                  Some[expr](UnNot(SetMember(la, ra, sp), sp))
              }
            case IdentifierF(rel, _) =>
              map_option[expr, expr]((la: expr) => UnNot(Member(la, rel, sp), sp), lower(enums, l))
          }
        case BSubset() => None
        case BUnion() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)       => None
            case (Some(_), None) => None
            case (Some(la), Some(ra)) =>
              Some[expr](SetBin(UnionOp(), la, ra, sp))
          }
        case BIntersect() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)       => None
            case (Some(_), None) => None
            case (Some(la), Some(ra)) =>
              Some[expr](SetBin(IntersectOp(), la, ra, sp))
          }
        case BDiff() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)       => None
            case (Some(_), None) => None
            case (Some(la), Some(ra)) =>
              Some[expr](SetBin(DiffOp(), la, ra, sp))
          }
        case BAdd() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Arith(AddOp(), la, ra, sp))
          }
        case BSub() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Arith(SubOp(), la, ra, sp))
          }
        case BMul() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Arith(MulOp(), la, ra, sp))
          }
        case BDiv() =>
          (lower(enums, l), lower(enums, r)) match {
            case (None, _)            => None
            case (Some(_), None)      => None
            case (Some(la), Some(ra)) => Some[expr](Arith(DivOp(), la, ra, sp))
          }
      }
    case (enums, LetF(x, v, body, sp)) =>
      (lower(enums, v), lower(enums, body)) match {
        case (None, _)           => None
        case (Some(_), None)     => None
        case (Some(va), Some(b)) => Some[expr](LetIn(x, va, b, sp))
      }
    case (wt, EnumAccessF(base, mem, sp)) =>
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
        case IdentifierF(en, _)            => Some[expr](EnumAccess(en, mem, sp))
      }
    case (enums, FieldAccessF(base, fname, sp)) =>
      map_option[expr, expr]((b: expr) => FieldAccess(b, fname, sp), lower(enums, base))
    case (enums, IndexF(base, key, sp)) =>
      (lower(enums, base), lower(enums, key)) match {
        case (None, _)       => None
        case (Some(_), None) => None
        case (Some(basea), Some(keya)) =>
          peel_relation_ref(basea) match {
            case None    => None
            case Some(_) => Some[expr](IndexRel(basea, keya, sp))
          }
      }
    case (enums, PrimeF(e, sp)) =>
      map_option[expr, expr]((ea: expr) => Prime(ea, sp), lower(enums, e))
    case (enums, PreF(e, sp)) =>
      map_option[expr, expr]((ea: expr) => Pre(ea, sp), lower(enums, e))
    case (enums, WithF(base, updates, sp)) =>
      lower(enums, base) match {
        case None        => None
        case Some(basea) => lower_with_assigns(enums, updates, basea, sp)
      }
    case (enums, SetLiteralF(elems, sp)) => lowerSetList(enums, elems, sp)
  }

  def qb_names(x0: List[quantifier_binding_full]): List[String] = x0 match {
    case Nil                                        => Nil
    case QuantifierBindingFull(n, uu, uv, uw) :: bs => n :: qb_names(bs)
  }

  def subst_bindings(
      vk: String,
      vl: expr_full,
      x2: List[quantifier_binding_full]
  ): List[quantifier_binding_full] =
    (vk, vl, x2) match {
      case (vk, vl, Nil) => Nil
      case (x, r, QuantifierBindingFull(n, d, kk, sp) :: bs) =>
        QuantifierBindingFull(n, subst(x, r, d), kk, sp) :: subst_bindings(x, r, bs)
    }

  def subst_entries(vi: String, vj: expr_full, x2: List[map_entry_full]): List[map_entry_full] =
    (vi, vj, x2) match {
      case (vi, vj, Nil) => Nil
      case (x, r, MapEntryFull(k, v, sp) :: es) =>
        MapEntryFull(subst(x, r, k), subst(x, r, v), sp) :: subst_entries(x, r, es)
    }

  def subst_fields(
      vg: String,
      vh: expr_full,
      x2: List[field_assign_full]
  ): List[field_assign_full] =
    (vg, vh, x2) match {
      case (vg, vh, Nil) => Nil
      case (x, r, FieldAssignFull(f, v, sp) :: fs) =>
        FieldAssignFull(f, subst(x, r, v), sp) :: subst_fields(x, r, fs)
    }

  def subst_list(ve: String, vf: expr_full, x2: List[expr_full]): List[expr_full] =
    (ve, vf, x2) match {
      case (ve, vf, Nil)   => Nil
      case (x, r, e :: es) => subst(x, r, e) :: subst_list(x, r, es)
    }

  def subst(x: String, r: expr_full, xa2: expr_full): expr_full = (x, r, xa2) match {
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

  def fold[A, B](f: A => B => B, x1: List[A], s: B): B = (f, x1, s) match {
    case (f, Nil, s)     => s
    case (f, x :: xs, s) => fold[A, B](f, xs, f(x)(s))
  }

  def rev[A](xs: List[A]): List[A] =
    fold[A, List[A]]((a: A) => (b: List[A]) => a :: b, xs, Nil)

  def spanOf(x0: expr_full): Option[span_t] = x0 match {
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

  def max[A: ord](a: A, b: A): A =
    less_eq[A](a, b) match {
      case true  => b
      case false => a
    }

  def minus_nat(m: nat, n: nat): nat =
    Nata(max[BigInt](BigInt(0), integer_of_nat(m) - integer_of_nat(n)))

  def equal_nat(m: nat, n: nat): Boolean =
    integer_of_nat(m) == integer_of_nat(n)

  def zero_nat: nat = Nata(BigInt(0))

  def entityParentFull(x0: entity_decl_full): Option[String] = x0 match {
    case EntityDeclFull(uu, p, uv, uw, ux) => p
  }

  def entityNameFull(x0: entity_decl_full): String = x0 match {
    case EntityDeclFull(n, uu, uv, uw, ux) => n
  }

  def map[A, B](f: A => B, x1: List[A]): List[B] = (f, x1) match {
    case (f, Nil)        => Nil
    case (f, x21 :: x22) => f(x21) :: map[A, B](f, x22)
  }

  def entityByName(es: List[entity_decl_full], nm: String): Option[entity_decl_full] =
    map_of[String, entity_decl_full](
      map[entity_decl_full, (String, entity_decl_full)](
        (e: entity_decl_full) =>
          (entityNameFull(e), e),
        rev[entity_decl_full](es)
      ),
      nm
    )

  def member[A: equal](x0: List[A], y: A): Boolean = (x0, y) match {
    case (Nil, y)     => false
    case (x :: xs, y) => eq[A](x, y) || member[A](xs, y)
  }

  def chain_up(
      uu: List[entity_decl_full],
      f: nat,
      uv: String,
      uw: List[String]
  ): List[entity_decl_full] =
    equal_nat(f, zero_nat) match {
      case true => Nil
      case false => entityByName(uu, uv) match {
          case None => Nil
          case Some(e) =>
            entityParentFull(e) match {
              case None => List(e)
              case Some(parent) =>
                member[String](uw, parent) match {
                  case true => List(e)
                  case false => chain_up(uu, minus_nat(f, one_nat), parent, uv :: uw) ++
                      List(e)
                }
            }
        }
    }

  def subexprs_bindings(x0: List[quantifier_binding_full]): List[expr_full] = x0 match {
    case Nil                                        => Nil
    case QuantifierBindingFull(wo, d, wp, wq) :: bs => d :: subexprs_bindings(bs)
  }

  def subexprs_entries(x0: List[map_entry_full]): List[expr_full] = x0 match {
    case Nil                          => Nil
    case MapEntryFull(k, v, wn) :: es => k :: v :: subexprs_entries(es)
  }

  def subexprs_fields(x0: List[field_assign_full]): List[expr_full] = x0 match {
    case Nil                              => Nil
    case FieldAssignFull(wl, v, wm) :: fs => v :: subexprs_fields(fs)
  }

  def subexprs(x0: expr_full): List[expr_full] = x0 match {
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

  def typeName(x0: type_expr_full): Option[String] = x0 match {
    case NamedTypeF(n, uu)            => Some[String](n)
    case SetTypeF(v, va)              => None
    case MapTypeF(v, va, vb)          => None
    case SeqTypeF(v, va)              => None
    case OptionTypeF(v, va)           => None
    case RelationTypeF(v, va, vb, vc) => None
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

  def divide_int(k: int, l: int): int =
    int_of_integer(divide_integer(integer_of_int(k), integer_of_int(l)))

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

  def uminus_int(k: int): int = int_of_integer(-integer_of_int(k))

  def length_tailrec[A](x0: List[A], n: nat): nat = (x0, n) match {
    case (Nil, n)     => n
    case (x :: xs, n) => length_tailrec[A](xs, Suc(n))
  }

  def size_list[A](xs: List[A]): nat = length_tailrec[A](xs, zero_nat)

  def times_int(k: int, l: int): int =
    int_of_integer(integer_of_int(k) * integer_of_int(l))

  def minus_int(k: int, l: int): int =
    int_of_integer(integer_of_int(k) - integer_of_int(l))

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

  def zero_int: int = int_of_integer(BigInt(0))

  def plus_int(k: int, l: int): int =
    int_of_integer(integer_of_int(k) + integer_of_int(l))

  def int_of_nat(n: nat): int = int_of_integer(integer_of_nat(n))

  def less_int(k: int, l: int): Boolean = integer_of_int(k) < integer_of_int(l)

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
      case (uv, SEnumElem(v, va), ux) => None
      case (uv, SSet(v), ux)          => None
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

  def snd[A, B](x0: (A, B)): B = x0 match {
    case (x1, x2) => x2
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

  def peelSmtRelationRef(x0: smt_term): Option[String] = x0 match {
    case TVar(rel)                       => Some[String](rel)
    case TPre(TVar(rel))                 => Some[String](rel)
    case TPrime(TVar(rel))               => Some[String](rel)
    case BLit(v)                         => None
    case ILit(v)                         => None
    case EnumElemConst(v, va)            => None
    case TNot(v)                         => None
    case TAnd(v, va)                     => None
    case TOr(v, va)                      => None
    case TImplies(v, va)                 => None
    case TEq(v, va)                      => None
    case TLt(v, va)                      => None
    case TNeg(v)                         => None
    case TAdd(v, va)                     => None
    case TSub(v, va)                     => None
    case TMul(v, va)                     => None
    case TDiv(v, va)                     => None
    case TInDom(v, va)                   => None
    case TCardRel(v)                     => None
    case TLetIn(v, va, vb)               => None
    case TForallEnum(v, va, vb)          => None
    case TForallRel(v, va, vb)           => None
    case TIndexRel(v, va)                => None
    case TFieldAccess(v, va)             => None
    case TSetEmpty()                     => None
    case TSetInsert(v, va)               => None
    case TSetMember(v, va)               => None
    case TSetUnion(v, va)                => None
    case TSetIntersect(v, va)            => None
    case TSetDiff(v, va)                 => None
    case TPrime(BLit(va))                => None
    case TPrime(ILit(va))                => None
    case TPrime(EnumElemConst(va, vb))   => None
    case TPrime(TNot(va))                => None
    case TPrime(TAnd(va, vb))            => None
    case TPrime(TOr(va, vb))             => None
    case TPrime(TImplies(va, vb))        => None
    case TPrime(TEq(va, vb))             => None
    case TPrime(TLt(va, vb))             => None
    case TPrime(TNeg(va))                => None
    case TPrime(TAdd(va, vb))            => None
    case TPrime(TSub(va, vb))            => None
    case TPrime(TMul(va, vb))            => None
    case TPrime(TDiv(va, vb))            => None
    case TPrime(TInDom(va, vb))          => None
    case TPrime(TCardRel(va))            => None
    case TPrime(TLetIn(va, vb, vc))      => None
    case TPrime(TForallEnum(va, vb, vc)) => None
    case TPrime(TForallRel(va, vb, vc))  => None
    case TPrime(TIndexRel(va, vb))       => None
    case TPrime(TFieldAccess(va, vb))    => None
    case TPrime(TSetEmpty())             => None
    case TPrime(TSetInsert(va, vb))      => None
    case TPrime(TSetMember(va, vb))      => None
    case TPrime(TSetUnion(va, vb))       => None
    case TPrime(TSetIntersect(va, vb))   => None
    case TPrime(TSetDiff(va, vb))        => None
    case TPrime(TPrime(va))              => None
    case TPrime(TPre(va))                => None
    case TPrime(TWithRec(va, vb, vc))    => None
    case TPre(BLit(va))                  => None
    case TPre(ILit(va))                  => None
    case TPre(EnumElemConst(va, vb))     => None
    case TPre(TNot(va))                  => None
    case TPre(TAnd(va, vb))              => None
    case TPre(TOr(va, vb))               => None
    case TPre(TImplies(va, vb))          => None
    case TPre(TEq(va, vb))               => None
    case TPre(TLt(va, vb))               => None
    case TPre(TNeg(va))                  => None
    case TPre(TAdd(va, vb))              => None
    case TPre(TSub(va, vb))              => None
    case TPre(TMul(va, vb))              => None
    case TPre(TDiv(va, vb))              => None
    case TPre(TInDom(va, vb))            => None
    case TPre(TCardRel(va))              => None
    case TPre(TLetIn(va, vb, vc))        => None
    case TPre(TForallEnum(va, vb, vc))   => None
    case TPre(TForallRel(va, vb, vc))    => None
    case TPre(TIndexRel(va, vb))         => None
    case TPre(TFieldAccess(va, vb))      => None
    case TPre(TSetEmpty())               => None
    case TPre(TSetInsert(va, vb))        => None
    case TPre(TSetMember(va, vb))        => None
    case TPre(TSetUnion(va, vb))         => None
    case TPre(TSetIntersect(va, vb))     => None
    case TPre(TSetDiff(va, vb))          => None
    case TPre(TPrime(va))                => None
    case TPre(TPre(va))                  => None
    case TPre(TWithRec(va, vb, vc))      => None
    case TWithRec(v, va, vb)             => None
  }

  def set_diff_smt_vals(l: List[smt_val], r: List[smt_val]): List[smt_val] =
    dedupe_smt_vals(filter[smt_val]((v: smt_val) => !contains_smt_val(r, v), l))

  def smt_env_lookup(env: List[(String, smt_val)], name: String): Option[smt_val] =
    map_of[String, smt_val](env, name)

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
              case Some(SEnumElem(_, _))      => None
              case Some(SEntityElem(_, _))    => None
              case Some(SSet(_))              => None
              case Some(SEntityWith(_, _, _)) => None
            }
          case Some(SInt(_))              => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
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
              case Some(SEnumElem(_, _))      => None
              case Some(SEntityElem(_, _))    => None
              case Some(SSet(_))              => None
              case Some(SEntityWith(_, _, _)) => None
            }
          case Some(SInt(_))              => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
        }
    }

  def smtEval(m: smt_model_ext[Unit], env: List[(String, smt_val)], x2: smt_term): Option[smt_val] =
    (m, env, x2) match {
      case (m, env, BLit(b)) => Some[smt_val](SBool(b))
      case (m, env, ILit(n)) => Some[smt_val](SInt(n))
      case (m, env, TVar(x)) => smt_env_lookup(env, x) match {
          case None    => smt_model_lookup_const(m, x)
          case Some(a) => Some[smt_val](a)
        }
      case (m, env, EnumElemConst(en, mem)) =>
        smt_model_lookup_sort_members(m, en) match {
          case None => None
          case Some(members) =>
            member[String](members, mem) match {
              case true  => Some[smt_val](SEnumElem(en, mem))
              case false => None
            }
        }
      case (m, env, TNot(t)) => smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(b))             => Some[smt_val](SBool(!b))
          case Some(SInt(_))              => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
        }
      case (m, env, TAnd(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                    => None
          case (Some(SBool(_)), None)                       => None
          case (Some(SBool(a)), Some(SBool(b)))             => Some[smt_val](SBool(a && b))
          case (Some(SBool(_)), Some(SInt(_)))              => None
          case (Some(SBool(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SBool(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SBool(_)), Some(SSet(_)))              => None
          case (Some(SBool(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), _)                           => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
        }
      case (m, env, TOr(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                    => None
          case (Some(SBool(_)), None)                       => None
          case (Some(SBool(a)), Some(SBool(b)))             => Some[smt_val](SBool(a || b))
          case (Some(SBool(_)), Some(SInt(_)))              => None
          case (Some(SBool(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SBool(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SBool(_)), Some(SSet(_)))              => None
          case (Some(SBool(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), _)                           => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
        }
      case (m, env, TImplies(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                    => None
          case (Some(SBool(_)), None)                       => None
          case (Some(SBool(a)), Some(SBool(b)))             => Some[smt_val](SBool(!a || b))
          case (Some(SBool(_)), Some(SInt(_)))              => None
          case (Some(SBool(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SBool(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SBool(_)), Some(SSet(_)))              => None
          case (Some(SBool(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SInt(_)), _)                           => None
          case (Some(SEnumElem(_, _)), _)                   => None
          case (Some(SEntityElem(_, _)), _)                 => None
          case (Some(SSet(_)), _)                           => None
          case (Some(SEntityWith(_, _, _)), _)              => None
        }
      case (m, env, TEq(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)          => None
          case (Some(_), None)    => None
          case (Some(a), Some(b)) => Some[smt_val](SBool(equal_smt_vala(a, b)))
        }
      case (m, env, TLt(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SBool(less_int(a, b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEnumElem(_, _)), _)                  => None
          case (Some(SEntityElem(_, _)), _)                => None
          case (Some(SSet(_)), _)                          => None
          case (Some(SEntityWith(_, _, _)), _)             => None
        }
      case (m, env, TNeg(t)) =>
        smtEval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(n))              => Some[smt_val](SInt(uminus_int(n)))
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
        }
      case (m, env, TAdd(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SInt(plus_int(a, b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEnumElem(_, _)), _)                  => None
          case (Some(SEntityElem(_, _)), _)                => None
          case (Some(SSet(_)), _)                          => None
          case (Some(SEntityWith(_, _, _)), _)             => None
        }
      case (m, env, TSub(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SInt(minus_int(a, b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEnumElem(_, _)), _)                  => None
          case (Some(SEntityElem(_, _)), _)                => None
          case (Some(SSet(_)), _)                          => None
          case (Some(SEntityWith(_, _, _)), _)             => None
        }
      case (m, env, TMul(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                       => None
          case (Some(SBool(_)), _)             => None
          case (Some(SInt(_)), None)           => None
          case (Some(SInt(_)), Some(SBool(_))) => None
          case (Some(SInt(a)), Some(SInt(b))) =>
            Some[smt_val](SInt(times_int(a, b)))
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEnumElem(_, _)), _)                  => None
          case (Some(SEntityElem(_, _)), _)                => None
          case (Some(SSet(_)), _)                          => None
          case (Some(SEntityWith(_, _, _)), _)             => None
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
          case (Some(SInt(_)), Some(SEnumElem(_, _)))      => None
          case (Some(SInt(_)), Some(SEntityElem(_, _)))    => None
          case (Some(SInt(_)), Some(SSet(_)))              => None
          case (Some(SInt(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEnumElem(_, _)), _)                  => None
          case (Some(SEntityElem(_, _)), _)                => None
          case (Some(SSet(_)), _)                          => None
          case (Some(SEntityWith(_, _, _)), _)             => None
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
          case (Some(_), Some(SEnumElem(_, _)))   => None
          case (Some(_), Some(SEntityElem(_, _))) => None
          case (Some(v), Some(SSet(members))) =>
            Some[smt_val](SSet(dedupe_smt_vals(v :: members)))
          case (Some(_), Some(SEntityWith(_, _, _))) => None
        }
      case (m, env, TSetMember(elem, set_t)) =>
        (smtEval(m, env, elem), smtEval(m, env, set_t)) match {
          case (None, _)                          => None
          case (Some(_), None)                    => None
          case (Some(_), Some(SBool(_)))          => None
          case (Some(_), Some(SInt(_)))           => None
          case (Some(_), Some(SEnumElem(_, _)))   => None
          case (Some(_), Some(SEntityElem(_, _))) => None
          case (Some(v), Some(SSet(members))) =>
            Some[smt_val](SBool(contains_smt_val(members, v)))
          case (Some(_), Some(SEntityWith(_, _, _))) => None
        }
      case (m, env, TSetUnion(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                => None
          case (Some(SBool(_)), _)                      => None
          case (Some(SInt(_)), _)                       => None
          case (Some(SEnumElem(_, _)), _)               => None
          case (Some(SEntityElem(_, _)), _)             => None
          case (Some(SSet(_)), None)                    => None
          case (Some(SSet(_)), Some(SBool(_)))          => None
          case (Some(SSet(_)), Some(SInt(_)))           => None
          case (Some(SSet(_)), Some(SEnumElem(_, _)))   => None
          case (Some(SSet(_)), Some(SEntityElem(_, _))) => None
          case (Some(SSet(a)), Some(SSet(b))) =>
            Some[smt_val](SSet(set_union_smt_vals(a, b)))
          case (Some(SSet(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEntityWith(_, _, _)), _)             => None
        }
      case (m, env, TSetIntersect(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                => None
          case (Some(SBool(_)), _)                      => None
          case (Some(SInt(_)), _)                       => None
          case (Some(SEnumElem(_, _)), _)               => None
          case (Some(SEntityElem(_, _)), _)             => None
          case (Some(SSet(_)), None)                    => None
          case (Some(SSet(_)), Some(SBool(_)))          => None
          case (Some(SSet(_)), Some(SInt(_)))           => None
          case (Some(SSet(_)), Some(SEnumElem(_, _)))   => None
          case (Some(SSet(_)), Some(SEntityElem(_, _))) => None
          case (Some(SSet(a)), Some(SSet(b))) =>
            Some[smt_val](SSet(set_intersect_smt_vals(a, b)))
          case (Some(SSet(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEntityWith(_, _, _)), _)             => None
        }
      case (m, env, TSetDiff(l, r)) =>
        (smtEval(m, env, l), smtEval(m, env, r)) match {
          case (None, _)                                => None
          case (Some(SBool(_)), _)                      => None
          case (Some(SInt(_)), _)                       => None
          case (Some(SEnumElem(_, _)), _)               => None
          case (Some(SEntityElem(_, _)), _)             => None
          case (Some(SSet(_)), None)                    => None
          case (Some(SSet(_)), Some(SBool(_)))          => None
          case (Some(SSet(_)), Some(SInt(_)))           => None
          case (Some(SSet(_)), Some(SEnumElem(_, _)))   => None
          case (Some(SSet(_)), Some(SEntityElem(_, _))) => None
          case (Some(SSet(a)), Some(SSet(b))) =>
            Some[smt_val](SSet(set_diff_smt_vals(a, b)))
          case (Some(SSet(_)), Some(SEntityWith(_, _, _))) => None
          case (Some(SEntityWith(_, _, _)), _)             => None
        }
      case (m, env, TPrime(t)) => smtEval(m, env, t)
      case (m, env, TPre(t))   => smtEval(m, env, t)
      case (m, env, TWithRec(base, fld, value_t)) =>
        (smtEval(m, env, base), smtEval(m, env, value_t)) match {
          case (None, _)           => None
          case (Some(_), None)     => None
          case (Some(bv), Some(v)) => Some[smt_val](SEntityWith(bv, fld, v))
        }
    }

  def binOpToTs(x0: bin_op_full): String = x0 match {
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

  def free_vars_bindings(x0: List[quantifier_binding_full]): List[String] = x0 match {
    case Nil => Nil
    case QuantifierBindingFull(wj, d, wk, wl) :: bs =>
      free_vars(d) ++ free_vars_bindings(bs)
  }

  def free_vars_entries(x0: List[map_entry_full]): List[String] = x0 match {
    case Nil => Nil
    case MapEntryFull(k, v, wi) :: es =>
      free_vars(k) ++ (free_vars(v) ++ free_vars_entries(es))
  }

  def free_vars_fields(x0: List[field_assign_full]): List[String] = x0 match {
    case Nil                              => Nil
    case FieldAssignFull(wg, v, wh) :: fs => free_vars(v) ++ free_vars_fields(fs)
  }

  def free_vars_list(x0: List[expr_full]): List[String] = x0 match {
    case Nil     => Nil
    case x :: xs => free_vars(x) ++ free_vars_list(xs)
  }

  def free_vars(x0: expr_full): List[String] = x0 match {
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

  def isLitFull(x0: expr_full): Boolean = x0 match {
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

  def isTrueLit(x0: expr_full): Boolean = x0 match {
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
      member[A](xs, x) match {
        case true  => remdups[A](xs)
        case false => x :: remdups[A](xs)
      }
  }

  def combineAnd_acc(acc: expr_full, x1: List[expr_full]): expr_full =
    (acc, x1) match {
      case (acc, Nil)       => acc
      case (acc, x :: rest) => combineAnd_acc(BinaryOpF(BAnd(), acc, x, None), rest)
    }

  def combineAnd(x0: List[expr_full]): expr_full = x0 match {
    case Nil       => BoolLitF(true, None)
    case x :: rest => combineAnd_acc(x, rest)
  }

  def consPrimed(
      x0: Option[String],
      c: (List[String], (List[String], List[with_info_full]))
  ): (List[String], (List[String], List[with_info_full])) =
    (x0, c) match {
      case (None, c)              => c
      case (Some(n), (p, (f, w))) => (n :: p, (f, w))
    }

  def flattenAnd(x0: expr_full): List[expr_full] = x0 match {
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

  def isValueRef(x0: expr_full): Boolean = x0 match {
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

  def stripSpans_bindings(x0: List[quantifier_binding_full]): List[quantifier_binding_full] =
    x0 match {
      case Nil => Nil
      case QuantifierBindingFull(v, d, k, vx) :: bs =>
        QuantifierBindingFull(v, stripSpans(d), k, None) :: stripSpans_bindings(bs)
    }

  def stripSpans_entries(x0: List[map_entry_full]): List[map_entry_full] = x0 match {
    case Nil => Nil
    case MapEntryFull(k, v, vw) :: es =>
      MapEntryFull(stripSpans(k), stripSpans(v), None) :: stripSpans_entries(es)
  }

  def stripSpans_fields(x0: List[field_assign_full]): List[field_assign_full] =
    x0 match {
      case Nil => Nil
      case FieldAssignFull(n, v, vv) :: fs =>
        FieldAssignFull(n, stripSpans(v), None) :: stripSpans_fields(fs)
    }

  def stripSpans_list(x0: List[expr_full]): List[expr_full] = x0 match {
    case Nil     => Nil
    case x :: xs => stripSpans(x) :: stripSpans_list(xs)
  }

  def stripSpans(x0: expr_full): expr_full = x0 match {
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

  def hasPrePrime_bindings(x0: List[quantifier_binding_full]): Boolean = x0 match {
    case Nil => false
    case QuantifierBindingFull(wq, d, wr, ws) :: bs =>
      hasPrePrime(d) || hasPrePrime_bindings(bs)
  }

  def hasPrePrime_entries(x0: List[map_entry_full]): Boolean = x0 match {
    case Nil => false
    case MapEntryFull(k, v, wp) :: es =>
      hasPrePrime(k) || (hasPrePrime(v) || hasPrePrime_entries(es))
  }

  def hasPrePrime_fields(x0: List[field_assign_full]): Boolean = x0 match {
    case Nil => false
    case FieldAssignFull(wn, v, wo) :: fs =>
      hasPrePrime(v) || hasPrePrime_fields(fs)
  }

  def hasPrePrime_list(x0: List[expr_full]): Boolean = x0 match {
    case Nil     => false
    case x :: xs => hasPrePrime(x) || hasPrePrime_list(xs)
  }

  def hasPrePrime(x0: expr_full): Boolean = x0 match {
    case PrimeF(uu, uv)                  => true
    case PreF(uw, ux)                    => true
    case BinaryOpF(uy, l, r, uz)         => hasPrePrime(l) || hasPrePrime(r)
    case UnaryOpF(va, e, vb)             => hasPrePrime(e)
    case FieldAccessF(b, vc, vd)         => hasPrePrime(b)
    case EnumAccessF(b, ve, vf)          => hasPrePrime(b)
    case IndexF(b, i, vg)                => hasPrePrime(b) || hasPrePrime(i)
    case CallF(c, args, vh)              => hasPrePrime(c) || hasPrePrime_list(args)
    case WithF(b, upds, vi)              => hasPrePrime(b) || hasPrePrime_fields(upds)
    case IfF(c, t, e, vj)                => hasPrePrime(c) || (hasPrePrime(t) || hasPrePrime(e))
    case LetF(vk, v, b, vl)              => hasPrePrime(v) || hasPrePrime(b)
    case LambdaF(vm, b, vn)              => hasPrePrime(b)
    case ConstructorF(vo, fs, vp)        => hasPrePrime_fields(fs)
    case SetLiteralF(xs, vq)             => hasPrePrime_list(xs)
    case MapLiteralF(es, vr)             => hasPrePrime_entries(es)
    case SetComprehensionF(vs, d, p, vt) => hasPrePrime(d) || hasPrePrime(p)
    case SeqLiteralF(xs, vu)             => hasPrePrime_list(xs)
    case MatchesF(x, vv, vw)             => hasPrePrime(x)
    case SomeWrapF(x, vx)                => hasPrePrime(x)
    case TheF(vy, d, b, vz)              => hasPrePrime(d) || hasPrePrime(b)
    case QuantifierF(wa, bs, body, wb) =>
      hasPrePrime_bindings(bs) || hasPrePrime(body)
    case IntLitF(wc, wd)     => false
    case FloatLitF(we, wf)   => false
    case StringLitF(wg, wh)  => false
    case BoolLitF(wi, wj)    => false
    case NoneLitF(wk)        => false
    case IdentifierF(wl, wm) => false
  }

  def rt_relations[A](x0: state_ext[A]): List[(String, List[ir_value])] = x0 match {
    case state_exta(rt_scalars, rt_relations, rt_lookups, rt_entity_fields, more) => rt_relations
  }

  def state_relation_domain(st: state_ext[Unit], name: String): Option[List[ir_value]] =
    map_of[String, List[ir_value]](rt_relations[Unit](st), name)

  def rt_scalars[A](x0: state_ext[A]): List[(String, ir_value)] = x0 match {
    case state_exta(rt_scalars, rt_relations, rt_lookups, rt_entity_fields, more) => rt_scalars
  }

  def state_lookup_scalar(st: state_ext[Unit], name: String): Option[ir_value] =
    map_of[String, ir_value](rt_scalars[Unit](st), name)

  def rt_entity_fields[A](x0: state_ext[A]): List[(String, List[(String, ir_value)])] =
    x0 match {
      case state_exta(rt_scalars, rt_relations, rt_lookups, rt_entity_fields, more) =>
        rt_entity_fields
    }

  def state_lookup_field(
      st: state_ext[Unit],
      entity_id: String,
      field_name: String
  ): Option[ir_value] =
    map_of[String, List[(String, ir_value)]](rt_entity_fields[Unit](st), entity_id) match {
      case None     => None
      case Some(fs) => map_of[String, ir_value](fs, field_name)
    }

  def value_field_lookup(st: state_ext[Unit], x1: ir_value, fld: String): Option[ir_value] =
    (st, x1, fld) match {
      case (st, VEntity(uu, eid), fld) => state_lookup_field(st, eid, fld)
      case (st, VEntityWith(base, ov_fld, ov_val), fld) =>
        fld == ov_fld match {
          case true  => Some[ir_value](ov_val)
          case false => value_field_lookup(st, base, fld)
        }
      case (uv, VBool(v), ux)     => None
      case (uv, VInt(v), ux)      => None
      case (uv, VEnum(v, va), ux) => None
      case (uv, VSet(v), ux)      => None
    }

  def sch_enums[A](x0: schema_ext[A]): List[enum_decl_ext[Unit]] = x0 match {
    case schema_exta(sch_enums, sch_entities, more) => sch_enums
  }

  def enm_name[A](x0: enum_decl_ext[A]): String = x0 match {
    case enum_decl_exta(enm_name, enm_members, enm_span, more) => enm_name
  }

  def schema_lookup_enum(s: schema_ext[Unit], name: String): Option[enum_decl_ext[Unit]] =
    find[enum_decl_ext[Unit]](
      (d: enum_decl_ext[Unit]) =>
        enm_name[Unit](d) == name,
      sch_enums[Unit](s)
    )

  def rt_lookups[A](x0: state_ext[A]): List[(String, List[(ir_value, ir_value)])] =
    x0 match {
      case state_exta(rt_scalars, rt_relations, rt_lookups, rt_entity_fields, more) => rt_lookups
    }

  def state_lookup_key(st: state_ext[Unit], rel_name: String, key: ir_value): Option[ir_value] =
    map_of[String, List[(ir_value, ir_value)]](rt_lookups[Unit](st), rel_name) match {
      case None => None
      case Some(pairs) =>
        map_option[(ir_value, ir_value), ir_value](
          (a: (ir_value, ir_value)) =>
            snd[ir_value, ir_value](a),
          find[(ir_value, ir_value)](
            (p: (ir_value, ir_value)) =>
              equal_ir_valuea(fst[ir_value, ir_value](p), key),
            pairs
          )
        )
    }

  def contains_value(x0: List[ir_value], v: ir_value): Boolean = (x0, v) match {
    case (Nil, v)     => false
    case (x :: xs, v) => equal_ir_valuea(x, v) || contains_value(xs, v)
  }

  def enm_members[A](x0: enum_decl_ext[A]): List[String] = x0 match {
    case enum_decl_exta(enm_name, enm_members, enm_span, more) => enm_members
  }

  def eval_bool_bin(x0: bool_bin_op, a: Boolean, b: Boolean): Boolean =
    (x0, a, b) match {
      case (AndOp(), a, b)     => a && b
      case (OrOp(), a, b)      => a || b
      case (ImpliesOp(), a, b) => !a || b
      case (IffOp(), a, b)     => equal_bool(a, b)
    }

  def dedupe_values(x0: List[ir_value]): List[ir_value] = x0 match {
    case Nil => Nil
    case x :: xs =>
      val rest = dedupe_values(xs): List[ir_value];
      contains_value(rest, x) match {
        case true  => rest
        case false => x :: rest
      }
  }

  def set_intersect_values(l: List[ir_value], r: List[ir_value]): List[ir_value] =
    dedupe_values(filter[ir_value]((a: ir_value) => contains_value(r, a), l))

  def set_union_values(l: List[ir_value], r: List[ir_value]): List[ir_value] =
    dedupe_values(l ++ r)

  def set_diff_values(l: List[ir_value], r: List[ir_value]): List[ir_value] =
    dedupe_values(filter[ir_value]((v: ir_value) => !contains_value(r, v), l))

  def eval_set_bin(uu: set_op, uv: Option[ir_value], uw: Option[ir_value]): Option[ir_value] =
    (uu, uv, uw) match {
      case (UnionOp(), Some(VSet(l)), Some(VSet(r))) =>
        Some[ir_value](VSet(set_union_values(l, r)))
      case (IntersectOp(), Some(VSet(l)), Some(VSet(r))) =>
        Some[ir_value](VSet(set_intersect_values(l, r)))
      case (DiffOp(), Some(VSet(l)), Some(VSet(r))) =>
        Some[ir_value](VSet(set_diff_values(l, r)))
      case (IntersectOp(), None, uw)                          => None
      case (IntersectOp(), Some(VBool(va)), uw)               => None
      case (IntersectOp(), Some(VInt(va)), uw)                => None
      case (IntersectOp(), Some(VEnum(va, vb)), uw)           => None
      case (IntersectOp(), Some(VEntity(va, vb)), uw)         => None
      case (IntersectOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (IntersectOp(), uv, None)                          => None
      case (IntersectOp(), uv, Some(VBool(va)))               => None
      case (IntersectOp(), uv, Some(VInt(va)))                => None
      case (IntersectOp(), uv, Some(VEnum(va, vb)))           => None
      case (IntersectOp(), uv, Some(VEntity(va, vb)))         => None
      case (IntersectOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (DiffOp(), None, uw)                               => None
      case (DiffOp(), Some(VBool(va)), uw)                    => None
      case (DiffOp(), Some(VInt(va)), uw)                     => None
      case (DiffOp(), Some(VEnum(va, vb)), uw)                => None
      case (DiffOp(), Some(VEntity(va, vb)), uw)              => None
      case (DiffOp(), Some(VEntityWith(va, vb, vc)), uw)      => None
      case (DiffOp(), uv, None)                               => None
      case (DiffOp(), uv, Some(VBool(va)))                    => None
      case (DiffOp(), uv, Some(VInt(va)))                     => None
      case (DiffOp(), uv, Some(VEnum(va, vb)))                => None
      case (DiffOp(), uv, Some(VEntity(va, vb)))              => None
      case (DiffOp(), uv, Some(VEntityWith(va, vb, vc)))      => None
      case (uu, None, uw)                                     => None
      case (uu, Some(VBool(va)), uw)                          => None
      case (uu, Some(VInt(va)), uw)                           => None
      case (uu, Some(VEnum(va, vb)), uw)                      => None
      case (uu, Some(VEntity(va, vb)), uw)                    => None
      case (uu, Some(VEntityWith(va, vb, vc)), uw)            => None
      case (uu, uv, None)                                     => None
      case (uu, uv, Some(VBool(va)))                          => None
      case (uu, uv, Some(VInt(va)))                           => None
      case (uu, uv, Some(VEnum(va, vb)))                      => None
      case (uu, uv, Some(VEntity(va, vb)))                    => None
      case (uu, uv, Some(VEntityWith(va, vb, vc)))            => None
    }

  def eval_arith(uu: arith_op, uv: Option[ir_value], uw: Option[ir_value]): Option[ir_value] =
    (uu, uv, uw) match {
      case (AddOp(), Some(VInt(a)), Some(VInt(b))) =>
        Some[ir_value](VInt(plus_int(a, b)))
      case (SubOp(), Some(VInt(a)), Some(VInt(b))) =>
        Some[ir_value](VInt(minus_int(a, b)))
      case (MulOp(), Some(VInt(a)), Some(VInt(b))) =>
        Some[ir_value](VInt(times_int(a, b)))
      case (DivOp(), Some(VInt(a)), Some(VInt(b))) =>
        equal_int(b, zero_int) match {
          case true  => None
          case false => Some[ir_value](VInt(divide_int(a, b)))
        }
      case (SubOp(), None, uw)                          => None
      case (SubOp(), Some(VBool(va)), uw)               => None
      case (SubOp(), Some(VEnum(va, vb)), uw)           => None
      case (SubOp(), Some(VEntity(va, vb)), uw)         => None
      case (SubOp(), Some(VSet(va)), uw)                => None
      case (SubOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (SubOp(), uv, None)                          => None
      case (SubOp(), uv, Some(VBool(va)))               => None
      case (SubOp(), uv, Some(VEnum(va, vb)))           => None
      case (SubOp(), uv, Some(VEntity(va, vb)))         => None
      case (SubOp(), uv, Some(VSet(va)))                => None
      case (SubOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (MulOp(), None, uw)                          => None
      case (MulOp(), Some(VBool(va)), uw)               => None
      case (MulOp(), Some(VEnum(va, vb)), uw)           => None
      case (MulOp(), Some(VEntity(va, vb)), uw)         => None
      case (MulOp(), Some(VSet(va)), uw)                => None
      case (MulOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (MulOp(), uv, None)                          => None
      case (MulOp(), uv, Some(VBool(va)))               => None
      case (MulOp(), uv, Some(VEnum(va, vb)))           => None
      case (MulOp(), uv, Some(VEntity(va, vb)))         => None
      case (MulOp(), uv, Some(VSet(va)))                => None
      case (MulOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (DivOp(), None, uw)                          => None
      case (DivOp(), Some(VBool(va)), uw)               => None
      case (DivOp(), Some(VEnum(va, vb)), uw)           => None
      case (DivOp(), Some(VEntity(va, vb)), uw)         => None
      case (DivOp(), Some(VSet(va)), uw)                => None
      case (DivOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (DivOp(), uv, None)                          => None
      case (DivOp(), uv, Some(VBool(va)))               => None
      case (DivOp(), uv, Some(VEnum(va, vb)))           => None
      case (DivOp(), uv, Some(VEntity(va, vb)))         => None
      case (DivOp(), uv, Some(VSet(va)))                => None
      case (DivOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (uu, None, uw)                               => None
      case (uu, Some(VBool(va)), uw)                    => None
      case (uu, Some(VEnum(va, vb)), uw)                => None
      case (uu, Some(VEntity(va, vb)), uw)              => None
      case (uu, Some(VSet(va)), uw)                     => None
      case (uu, Some(VEntityWith(va, vb, vc)), uw)      => None
      case (uu, uv, None)                               => None
      case (uu, uv, Some(VBool(va)))                    => None
      case (uu, uv, Some(VEnum(va, vb)))                => None
      case (uu, uv, Some(VEntity(va, vb)))              => None
      case (uu, uv, Some(VSet(va)))                     => None
      case (uu, uv, Some(VEntityWith(va, vb, vc)))      => None
    }

  def env_lookup(env: List[(String, ir_value)], name: String): Option[ir_value] =
    map_of[String, ir_value](env, name)

  def less_eq_int(k: int, l: int): Boolean =
    integer_of_int(k) <= integer_of_int(l)

  def eval_cmp(uu: cmp_op, uv: Option[ir_value], uw: Option[ir_value]): Option[ir_value] =
    (uu, uv, uw) match {
      case (EqOp(), Some(a), Some(b)) =>
        Some[ir_value](VBool(equal_ir_valuea(a, b)))
      case (NeqOp(), Some(a), Some(b)) =>
        Some[ir_value](VBool(!equal_ir_valuea(a, b)))
      case (LtOp(), Some(VInt(a)), Some(VInt(b))) =>
        Some[ir_value](VBool(less_int(a, b)))
      case (LeOp(), Some(VInt(a)), Some(VInt(b))) =>
        Some[ir_value](VBool(less_eq_int(a, b)))
      case (GtOp(), Some(VInt(a)), Some(VInt(b))) =>
        Some[ir_value](VBool(less_int(b, a)))
      case (GeOp(), Some(VInt(a)), Some(VInt(b))) =>
        Some[ir_value](VBool(less_eq_int(b, a)))
      case (NeqOp(), None, uw)                         => None
      case (NeqOp(), uv, None)                         => None
      case (LtOp(), None, uw)                          => None
      case (LtOp(), Some(VBool(va)), uw)               => None
      case (LtOp(), Some(VEnum(va, vb)), uw)           => None
      case (LtOp(), Some(VEntity(va, vb)), uw)         => None
      case (LtOp(), Some(VSet(va)), uw)                => None
      case (LtOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (LtOp(), uv, None)                          => None
      case (LtOp(), uv, Some(VBool(va)))               => None
      case (LtOp(), uv, Some(VEnum(va, vb)))           => None
      case (LtOp(), uv, Some(VEntity(va, vb)))         => None
      case (LtOp(), uv, Some(VSet(va)))                => None
      case (LtOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (LeOp(), None, uw)                          => None
      case (LeOp(), Some(VBool(va)), uw)               => None
      case (LeOp(), Some(VEnum(va, vb)), uw)           => None
      case (LeOp(), Some(VEntity(va, vb)), uw)         => None
      case (LeOp(), Some(VSet(va)), uw)                => None
      case (LeOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (LeOp(), uv, None)                          => None
      case (LeOp(), uv, Some(VBool(va)))               => None
      case (LeOp(), uv, Some(VEnum(va, vb)))           => None
      case (LeOp(), uv, Some(VEntity(va, vb)))         => None
      case (LeOp(), uv, Some(VSet(va)))                => None
      case (LeOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (GtOp(), None, uw)                          => None
      case (GtOp(), Some(VBool(va)), uw)               => None
      case (GtOp(), Some(VEnum(va, vb)), uw)           => None
      case (GtOp(), Some(VEntity(va, vb)), uw)         => None
      case (GtOp(), Some(VSet(va)), uw)                => None
      case (GtOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (GtOp(), uv, None)                          => None
      case (GtOp(), uv, Some(VBool(va)))               => None
      case (GtOp(), uv, Some(VEnum(va, vb)))           => None
      case (GtOp(), uv, Some(VEntity(va, vb)))         => None
      case (GtOp(), uv, Some(VSet(va)))                => None
      case (GtOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (GeOp(), None, uw)                          => None
      case (GeOp(), Some(VBool(va)), uw)               => None
      case (GeOp(), Some(VEnum(va, vb)), uw)           => None
      case (GeOp(), Some(VEntity(va, vb)), uw)         => None
      case (GeOp(), Some(VSet(va)), uw)                => None
      case (GeOp(), Some(VEntityWith(va, vb, vc)), uw) => None
      case (GeOp(), uv, None)                          => None
      case (GeOp(), uv, Some(VBool(va)))               => None
      case (GeOp(), uv, Some(VEnum(va, vb)))           => None
      case (GeOp(), uv, Some(VEntity(va, vb)))         => None
      case (GeOp(), uv, Some(VSet(va)))                => None
      case (GeOp(), uv, Some(VEntityWith(va, vb, vc))) => None
      case (uu, None, uw)                              => None
      case (uu, uv, None)                              => None
    }

  def eval_forall_enum(
      s: schema_ext[Unit],
      st: state_ext[Unit],
      env: List[(String, ir_value)],
      vara: String,
      en: String,
      x5: List[String],
      body: expr
  ): Option[ir_value] =
    (s, st, env, vara, en, x5, body) match {
      case (s, st, env, vara, en, Nil, body) => Some[ir_value](VBool(true))
      case (s, st, env, vara, en, mem :: rest, body) =>
        eval(s, st, (vara, VEnum(en, mem)) :: env, body) match {
          case None => None
          case Some(VBool(b)) =>
            eval_forall_enum(s, st, env, vara, en, rest, body) match {
              case None                       => None
              case Some(VBool(acc))           => Some[ir_value](VBool(b && acc))
              case Some(VInt(_))              => None
              case Some(VEnum(_, _))          => None
              case Some(VEntity(_, _))        => None
              case Some(VSet(_))              => None
              case Some(VEntityWith(_, _, _)) => None
            }
          case Some(VInt(_))              => None
          case Some(VEnum(_, _))          => None
          case Some(VEntity(_, _))        => None
          case Some(VSet(_))              => None
          case Some(VEntityWith(_, _, _)) => None
        }
    }

  def eval_forall_rel(
      s: schema_ext[Unit],
      st: state_ext[Unit],
      env: List[(String, ir_value)],
      vara: String,
      x4: List[ir_value],
      body: expr
  ): Option[ir_value] =
    (s, st, env, vara, x4, body) match {
      case (s, st, env, vara, Nil, body) => Some[ir_value](VBool(true))
      case (s, st, env, vara, v :: rest, body) =>
        eval(s, st, (vara, v) :: env, body) match {
          case None => None
          case Some(VBool(b)) =>
            eval_forall_rel(s, st, env, vara, rest, body) match {
              case None                       => None
              case Some(VBool(acc))           => Some[ir_value](VBool(b && acc))
              case Some(VInt(_))              => None
              case Some(VEnum(_, _))          => None
              case Some(VEntity(_, _))        => None
              case Some(VSet(_))              => None
              case Some(VEntityWith(_, _, _)) => None
            }
          case Some(VInt(_))              => None
          case Some(VEnum(_, _))          => None
          case Some(VEntity(_, _))        => None
          case Some(VSet(_))              => None
          case Some(VEntityWith(_, _, _)) => None
        }
    }

  def eval(
      s: schema_ext[Unit],
      st: state_ext[Unit],
      env: List[(String, ir_value)],
      x3: expr
  ): Option[ir_value] =
    (s, st, env, x3) match {
      case (s, st, env, BoolLit(b, uu)) => Some[ir_value](VBool(b))
      case (s, st, env, IntLit(n, uv))  => Some[ir_value](VInt(n))
      case (s, st, env, Ident(x, uw)) => env_lookup(env, x) match {
          case None    => state_lookup_scalar(st, x)
          case Some(a) => Some[ir_value](a)
        }
      case (s, st, env, UnNot(e, ux)) =>
        eval(s, st, env, e) match {
          case None                       => None
          case Some(VBool(b))             => Some[ir_value](VBool(!b))
          case Some(VInt(_))              => None
          case Some(VEnum(_, _))          => None
          case Some(VEntity(_, _))        => None
          case Some(VSet(_))              => None
          case Some(VEntityWith(_, _, _)) => None
        }
      case (s, st, env, UnNeg(e, uy)) =>
        eval(s, st, env, e) match {
          case None                       => None
          case Some(VBool(_))             => None
          case Some(VInt(n))              => Some[ir_value](VInt(uminus_int(n)))
          case Some(VEnum(_, _))          => None
          case Some(VEntity(_, _))        => None
          case Some(VSet(_))              => None
          case Some(VEntityWith(_, _, _)) => None
        }
      case (s, st, env, BoolBin(op, l, r, uz)) =>
        (eval(s, st, env, l), eval(s, st, env, r)) match {
          case (None, _)              => None
          case (Some(VBool(_)), None) => None
          case (Some(VBool(a)), Some(VBool(b))) =>
            Some[ir_value](VBool(eval_bool_bin(op, a, b)))
          case (Some(VBool(_)), Some(VInt(_)))              => None
          case (Some(VBool(_)), Some(VEnum(_, _)))          => None
          case (Some(VBool(_)), Some(VEntity(_, _)))        => None
          case (Some(VBool(_)), Some(VSet(_)))              => None
          case (Some(VBool(_)), Some(VEntityWith(_, _, _))) => None
          case (Some(VInt(_)), _)                           => None
          case (Some(VEnum(_, _)), _)                       => None
          case (Some(VEntity(_, _)), _)                     => None
          case (Some(VSet(_)), _)                           => None
          case (Some(VEntityWith(_, _, _)), _)              => None
        }
      case (s, st, env, Arith(op, l, r, va)) =>
        eval_arith(op, eval(s, st, env, l), eval(s, st, env, r))
      case (s, st, env, Cmp(op, l, r, vb)) =>
        eval_cmp(op, eval(s, st, env, l), eval(s, st, env, r))
      case (s, st, env, LetIn(x, v, body, vc)) =>
        eval(s, st, env, v) match {
          case None     => None
          case Some(va) => eval(s, st, (x, va) :: env, body)
        }
      case (s, st, env, EnumAccess(en, mem, vd)) =>
        schema_lookup_enum(s, en) match {
          case None => None
          case Some(d) =>
            member[String](enm_members[Unit](d), mem) match {
              case true  => Some[ir_value](VEnum(en, mem))
              case false => None
            }
        }
      case (s, st, env, Member(elem, rel_name, ve)) =>
        eval(s, st, env, elem) match {
          case None => None
          case Some(v) =>
            state_relation_domain(st, rel_name) match {
              case None => None
              case Some(rel_dom) =>
                Some[ir_value](VBool(contains_value(rel_dom, v)))
            }
        }
      case (s, st, env, ForallEnum(vara, en, body, vf)) =>
        schema_lookup_enum(s, en) match {
          case None => None
          case Some(d) =>
            eval_forall_enum(s, st, env, vara, en, enm_members[Unit](d), body)
        }
      case (s, st, env, ForallRel(vara, rel_name, body, vg)) =>
        state_relation_domain(st, rel_name) match {
          case None          => None
          case Some(rel_dom) => eval_forall_rel(s, st, env, vara, rel_dom, body)
        }
      case (s, st, env, Prime(e, vh)) => eval(s, st, env, e)
      case (s, st, env, Pre(e, vi))   => eval(s, st, env, e)
      case (s, st, env, CardRel(rel_name, vj)) =>
        state_relation_domain(st, rel_name) match {
          case None => None
          case Some(rel_dom) =>
            Some[ir_value](VInt(int_of_nat(size_list[ir_value](rel_dom))))
        }
      case (s, st, env, IndexRel(base, key, vk)) =>
        (peel_relation_ref(base), eval(s, st, env, key)) match {
          case (None, _)            => None
          case (Some(_), None)      => None
          case (Some(rel), Some(a)) => state_lookup_key(st, rel, a)
        }
      case (s, st, env, FieldAccess(base, fname, vl)) =>
        eval(s, st, env, base) match {
          case None    => None
          case Some(v) => value_field_lookup(st, v, fname)
        }
      case (s, st, env, SetEmpty(vm)) => Some[ir_value](VSet(Nil))
      case (s, st, env, SetInsert(elem, set_e, vn)) =>
        (eval(s, st, env, elem), eval(s, st, env, set_e)) match {
          case (None, _)                      => None
          case (Some(_), None)                => None
          case (Some(_), Some(VBool(_)))      => None
          case (Some(_), Some(VInt(_)))       => None
          case (Some(_), Some(VEnum(_, _)))   => None
          case (Some(_), Some(VEntity(_, _))) => None
          case (Some(v), Some(VSet(members))) =>
            Some[ir_value](VSet(dedupe_values(v :: members)))
          case (Some(_), Some(VEntityWith(_, _, _))) => None
        }
      case (s, st, env, SetMember(elem, set_e, vo)) =>
        (eval(s, st, env, elem), eval(s, st, env, set_e)) match {
          case (None, _)                      => None
          case (Some(_), None)                => None
          case (Some(_), Some(VBool(_)))      => None
          case (Some(_), Some(VInt(_)))       => None
          case (Some(_), Some(VEnum(_, _)))   => None
          case (Some(_), Some(VEntity(_, _))) => None
          case (Some(v), Some(VSet(members))) =>
            Some[ir_value](VBool(contains_value(members, v)))
          case (Some(_), Some(VEntityWith(_, _, _))) => None
        }
      case (s, st, env, SetBin(op, l, r, vp)) =>
        eval_set_bin(op, eval(s, st, env, l), eval(s, st, env, r))
      case (s, st, env, WithRec(base, fld, value_e, vq)) =>
        (eval(s, st, env, base), eval(s, st, env, value_e)) match {
          case (None, _)           => None
          case (Some(_), None)     => None
          case (Some(bv), Some(v)) => Some[ir_value](VEntityWith(bv, fld, v))
        }
    }

  def assignsField(x0: expr_full, field: String): Boolean = (x0, field) match {
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

  def consWithInfo(
      wi: with_info_full,
      x1: (List[String], (List[String], List[with_info_full]))
  ): (List[String], (List[String], List[with_info_full])) =
    (wi, x1) match {
      case (wi, (p, (f, w))) => (p, (f, wi :: w))
    }

  def enumNameFull(x0: enum_decl_full): String = x0 match {
    case EnumDeclFull(n, uu, uv) => n
  }

  def isLenOfValue(x0: expr_full): Boolean = x0 match {
    case CallF(IdentifierF(n, uu), List(arg), uv)         => n == "len" && isValueRef(arg)
    case BinaryOpF(v, va, vb, vc)                         => false
    case UnaryOpF(v, va, vb)                              => false
    case QuantifierF(v, va, vb, vc)                       => false
    case SomeWrapF(v, va)                                 => false
    case TheF(v, va, vb, vc)                              => false
    case FieldAccessF(v, va, vb)                          => false
    case EnumAccessF(v, va, vb)                           => false
    case IndexF(v, va, vb)                                => false
    case CallF(BinaryOpF(vc, vd, ve, vf), va, vb)         => false
    case CallF(UnaryOpF(vc, vd, ve), va, vb)              => false
    case CallF(QuantifierF(vc, vd, ve, vf), va, vb)       => false
    case CallF(SomeWrapF(vc, vd), va, vb)                 => false
    case CallF(TheF(vc, vd, ve, vf), va, vb)              => false
    case CallF(FieldAccessF(vc, vd, ve), va, vb)          => false
    case CallF(EnumAccessF(vc, vd, ve), va, vb)           => false
    case CallF(IndexF(vc, vd, ve), va, vb)                => false
    case CallF(CallF(vc, vd, ve), va, vb)                 => false
    case CallF(PrimeF(vc, vd), va, vb)                    => false
    case CallF(PreF(vc, vd), va, vb)                      => false
    case CallF(WithF(vc, vd, ve), va, vb)                 => false
    case CallF(IfF(vc, vd, ve, vf), va, vb)               => false
    case CallF(LetF(vc, vd, ve, vf), va, vb)              => false
    case CallF(LambdaF(vc, vd, ve), va, vb)               => false
    case CallF(ConstructorF(vc, vd, ve), va, vb)          => false
    case CallF(SetLiteralF(vc, vd), va, vb)               => false
    case CallF(MapLiteralF(vc, vd), va, vb)               => false
    case CallF(SetComprehensionF(vc, vd, ve, vf), va, vb) => false
    case CallF(SeqLiteralF(vc, vd), va, vb)               => false
    case CallF(MatchesF(vc, vd, ve), va, vb)              => false
    case CallF(IntLitF(vc, vd), va, vb)                   => false
    case CallF(FloatLitF(vc, vd), va, vb)                 => false
    case CallF(StringLitF(vc, vd), va, vb)                => false
    case CallF(BoolLitF(vc, vd), va, vb)                  => false
    case CallF(NoneLitF(vc), va, vb)                      => false
    case CallF(v, Nil, vb)                                => false
    case CallF(v, vc :: ve :: vf, vb)                     => false
    case PrimeF(v, va)                                    => false
    case PreF(v, va)                                      => false
    case WithF(v, va, vb)                                 => false
    case IfF(v, va, vb, vc)                               => false
    case LetF(v, va, vb, vc)                              => false
    case LambdaF(v, va, vb)                               => false
    case ConstructorF(v, va, vb)                          => false
    case SetLiteralF(v, va)                               => false
    case MapLiteralF(v, va)                               => false
    case SetComprehensionF(v, va, vb, vc)                 => false
    case SeqLiteralF(v, va)                               => false
    case MatchesF(v, va, vb)                              => false
    case IntLitF(v, va)                                   => false
    case FloatLitF(v, va)                                 => false
    case StringLitF(v, va)                                => false
    case BoolLitF(v, va)                                  => false
    case NoneLitF(v)                                      => false
    case IdentifierF(v, va)                               => false
  }

  def fieldNameFull(x0: field_decl_full): String = x0 match {
    case FieldDeclFull(n, uu, uv, uw) => n
  }

  def upsert_field(acc: List[field_decl_full], fd: field_decl_full): List[field_decl_full] =
    list_ex[field_decl_full](
      (g: field_decl_full) =>
        fieldNameFull(g) == fieldNameFull(fd),
      acc
    ) match {
      case true => map[field_decl_full, field_decl_full](
          (g: field_decl_full) =>
            fieldNameFull(g) == fieldNameFull(fd) match {
              case true  => fd
              case false => g
            },
          acc
        )
      case false => acc ++ List(fd)
    }

  def decomposeAtom(x0: expr_full): refinement_atom = x0 match {
    case MatchesF(IdentifierF(n, uu), pat, uv) =>
      n == "value" match {
        case true  => RaMatches(pat)
        case false => RaMatchesIdent(n, pat)
      }
    case BinaryOpF(op, l, IntLitF(n, innersp), sp) =>
      isLenOfValue(l) match {
        case true => RaLenCmp(op, n)
        case false => isValueRef(l) match {
            case true  => RaValueCmp(op, n)
            case false => RaUnknown(BinaryOpF(op, l, IntLitF(n, innersp), sp))
          }
      }
    case CallF(IdentifierF(p, identsp), List(arg), sp) =>
      isValueRef(arg) match {
        case true  => RaPredCall(p)
        case false => RaUnknown(CallF(IdentifierF(p, identsp), List(arg), sp))
      }
    case BinaryOpF(v, va, BinaryOpF(vd, ve, vf, vg), vc) =>
      RaUnknown(BinaryOpF(v, va, BinaryOpF(vd, ve, vf, vg), vc))
    case BinaryOpF(v, va, UnaryOpF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, UnaryOpF(vd, ve, vf), vc))
    case BinaryOpF(v, va, QuantifierF(vd, ve, vf, vg), vc) =>
      RaUnknown(BinaryOpF(v, va, QuantifierF(vd, ve, vf, vg), vc))
    case BinaryOpF(v, va, SomeWrapF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, SomeWrapF(vd, ve), vc))
    case BinaryOpF(v, va, TheF(vd, ve, vf, vg), vc) =>
      RaUnknown(BinaryOpF(v, va, TheF(vd, ve, vf, vg), vc))
    case BinaryOpF(v, va, FieldAccessF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, FieldAccessF(vd, ve, vf), vc))
    case BinaryOpF(v, va, EnumAccessF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, EnumAccessF(vd, ve, vf), vc))
    case BinaryOpF(v, va, IndexF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, IndexF(vd, ve, vf), vc))
    case BinaryOpF(v, va, CallF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, CallF(vd, ve, vf), vc))
    case BinaryOpF(v, va, PrimeF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, PrimeF(vd, ve), vc))
    case BinaryOpF(v, va, PreF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, PreF(vd, ve), vc))
    case BinaryOpF(v, va, WithF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, WithF(vd, ve, vf), vc))
    case BinaryOpF(v, va, IfF(vd, ve, vf, vg), vc) =>
      RaUnknown(BinaryOpF(v, va, IfF(vd, ve, vf, vg), vc))
    case BinaryOpF(v, va, LetF(vd, ve, vf, vg), vc) =>
      RaUnknown(BinaryOpF(v, va, LetF(vd, ve, vf, vg), vc))
    case BinaryOpF(v, va, LambdaF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, LambdaF(vd, ve, vf), vc))
    case BinaryOpF(v, va, ConstructorF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, ConstructorF(vd, ve, vf), vc))
    case BinaryOpF(v, va, SetLiteralF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, SetLiteralF(vd, ve), vc))
    case BinaryOpF(v, va, MapLiteralF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, MapLiteralF(vd, ve), vc))
    case BinaryOpF(v, va, SetComprehensionF(vd, ve, vf, vg), vc) =>
      RaUnknown(BinaryOpF(v, va, SetComprehensionF(vd, ve, vf, vg), vc))
    case BinaryOpF(v, va, SeqLiteralF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, SeqLiteralF(vd, ve), vc))
    case BinaryOpF(v, va, MatchesF(vd, ve, vf), vc) =>
      RaUnknown(BinaryOpF(v, va, MatchesF(vd, ve, vf), vc))
    case BinaryOpF(v, va, FloatLitF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, FloatLitF(vd, ve), vc))
    case BinaryOpF(v, va, StringLitF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, StringLitF(vd, ve), vc))
    case BinaryOpF(v, va, BoolLitF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, BoolLitF(vd, ve), vc))
    case BinaryOpF(v, va, NoneLitF(vd), vc) =>
      RaUnknown(BinaryOpF(v, va, NoneLitF(vd), vc))
    case BinaryOpF(v, va, IdentifierF(vd, ve), vc) =>
      RaUnknown(BinaryOpF(v, va, IdentifierF(vd, ve), vc))
    case UnaryOpF(v, va, vb)        => RaUnknown(UnaryOpF(v, va, vb))
    case QuantifierF(v, va, vb, vc) => RaUnknown(QuantifierF(v, va, vb, vc))
    case SomeWrapF(v, va)           => RaUnknown(SomeWrapF(v, va))
    case TheF(v, va, vb, vc)        => RaUnknown(TheF(v, va, vb, vc))
    case FieldAccessF(v, va, vb)    => RaUnknown(FieldAccessF(v, va, vb))
    case EnumAccessF(v, va, vb)     => RaUnknown(EnumAccessF(v, va, vb))
    case IndexF(v, va, vb)          => RaUnknown(IndexF(v, va, vb))
    case CallF(BinaryOpF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(CallF(BinaryOpF(vc, vd, ve, vf), va, vb))
    case CallF(UnaryOpF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(UnaryOpF(vc, vd, ve), va, vb))
    case CallF(QuantifierF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(CallF(QuantifierF(vc, vd, ve, vf), va, vb))
    case CallF(SomeWrapF(vc, vd), va, vb) =>
      RaUnknown(CallF(SomeWrapF(vc, vd), va, vb))
    case CallF(TheF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(CallF(TheF(vc, vd, ve, vf), va, vb))
    case CallF(FieldAccessF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(FieldAccessF(vc, vd, ve), va, vb))
    case CallF(EnumAccessF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(EnumAccessF(vc, vd, ve), va, vb))
    case CallF(IndexF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(IndexF(vc, vd, ve), va, vb))
    case CallF(CallF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(CallF(vc, vd, ve), va, vb))
    case CallF(PrimeF(vc, vd), va, vb) => RaUnknown(CallF(PrimeF(vc, vd), va, vb))
    case CallF(PreF(vc, vd), va, vb)   => RaUnknown(CallF(PreF(vc, vd), va, vb))
    case CallF(WithF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(WithF(vc, vd, ve), va, vb))
    case CallF(IfF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(CallF(IfF(vc, vd, ve, vf), va, vb))
    case CallF(LetF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(CallF(LetF(vc, vd, ve, vf), va, vb))
    case CallF(LambdaF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(LambdaF(vc, vd, ve), va, vb))
    case CallF(ConstructorF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(ConstructorF(vc, vd, ve), va, vb))
    case CallF(SetLiteralF(vc, vd), va, vb) =>
      RaUnknown(CallF(SetLiteralF(vc, vd), va, vb))
    case CallF(MapLiteralF(vc, vd), va, vb) =>
      RaUnknown(CallF(MapLiteralF(vc, vd), va, vb))
    case CallF(SetComprehensionF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(CallF(SetComprehensionF(vc, vd, ve, vf), va, vb))
    case CallF(SeqLiteralF(vc, vd), va, vb) =>
      RaUnknown(CallF(SeqLiteralF(vc, vd), va, vb))
    case CallF(MatchesF(vc, vd, ve), va, vb) =>
      RaUnknown(CallF(MatchesF(vc, vd, ve), va, vb))
    case CallF(IntLitF(vc, vd), va, vb) =>
      RaUnknown(CallF(IntLitF(vc, vd), va, vb))
    case CallF(FloatLitF(vc, vd), va, vb) =>
      RaUnknown(CallF(FloatLitF(vc, vd), va, vb))
    case CallF(StringLitF(vc, vd), va, vb) =>
      RaUnknown(CallF(StringLitF(vc, vd), va, vb))
    case CallF(BoolLitF(vc, vd), va, vb) =>
      RaUnknown(CallF(BoolLitF(vc, vd), va, vb))
    case CallF(NoneLitF(vc), va, vb)  => RaUnknown(CallF(NoneLitF(vc), va, vb))
    case CallF(v, Nil, vb)            => RaUnknown(CallF(v, Nil, vb))
    case CallF(v, vc :: ve :: vf, vb) => RaUnknown(CallF(v, vc :: ve :: vf, vb))
    case PrimeF(v, va)                => RaUnknown(PrimeF(v, va))
    case PreF(v, va)                  => RaUnknown(PreF(v, va))
    case WithF(v, va, vb)             => RaUnknown(WithF(v, va, vb))
    case IfF(v, va, vb, vc)           => RaUnknown(IfF(v, va, vb, vc))
    case LetF(v, va, vb, vc)          => RaUnknown(LetF(v, va, vb, vc))
    case LambdaF(v, va, vb)           => RaUnknown(LambdaF(v, va, vb))
    case ConstructorF(v, va, vb)      => RaUnknown(ConstructorF(v, va, vb))
    case SetLiteralF(v, va)           => RaUnknown(SetLiteralF(v, va))
    case MapLiteralF(v, va)           => RaUnknown(MapLiteralF(v, va))
    case SetComprehensionF(v, va, vb, vc) =>
      RaUnknown(SetComprehensionF(v, va, vb, vc))
    case SeqLiteralF(v, va) => RaUnknown(SeqLiteralF(v, va))
    case MatchesF(BinaryOpF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(MatchesF(BinaryOpF(vc, vd, ve, vf), va, vb))
    case MatchesF(UnaryOpF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(UnaryOpF(vc, vd, ve), va, vb))
    case MatchesF(QuantifierF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(MatchesF(QuantifierF(vc, vd, ve, vf), va, vb))
    case MatchesF(SomeWrapF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(SomeWrapF(vc, vd), va, vb))
    case MatchesF(TheF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(MatchesF(TheF(vc, vd, ve, vf), va, vb))
    case MatchesF(FieldAccessF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(FieldAccessF(vc, vd, ve), va, vb))
    case MatchesF(EnumAccessF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(EnumAccessF(vc, vd, ve), va, vb))
    case MatchesF(IndexF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(IndexF(vc, vd, ve), va, vb))
    case MatchesF(CallF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(CallF(vc, vd, ve), va, vb))
    case MatchesF(PrimeF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(PrimeF(vc, vd), va, vb))
    case MatchesF(PreF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(PreF(vc, vd), va, vb))
    case MatchesF(WithF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(WithF(vc, vd, ve), va, vb))
    case MatchesF(IfF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(MatchesF(IfF(vc, vd, ve, vf), va, vb))
    case MatchesF(LetF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(MatchesF(LetF(vc, vd, ve, vf), va, vb))
    case MatchesF(LambdaF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(LambdaF(vc, vd, ve), va, vb))
    case MatchesF(ConstructorF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(ConstructorF(vc, vd, ve), va, vb))
    case MatchesF(SetLiteralF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(SetLiteralF(vc, vd), va, vb))
    case MatchesF(MapLiteralF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(MapLiteralF(vc, vd), va, vb))
    case MatchesF(SetComprehensionF(vc, vd, ve, vf), va, vb) =>
      RaUnknown(MatchesF(SetComprehensionF(vc, vd, ve, vf), va, vb))
    case MatchesF(SeqLiteralF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(SeqLiteralF(vc, vd), va, vb))
    case MatchesF(MatchesF(vc, vd, ve), va, vb) =>
      RaUnknown(MatchesF(MatchesF(vc, vd, ve), va, vb))
    case MatchesF(IntLitF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(IntLitF(vc, vd), va, vb))
    case MatchesF(FloatLitF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(FloatLitF(vc, vd), va, vb))
    case MatchesF(StringLitF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(StringLitF(vc, vd), va, vb))
    case MatchesF(BoolLitF(vc, vd), va, vb) =>
      RaUnknown(MatchesF(BoolLitF(vc, vd), va, vb))
    case MatchesF(NoneLitF(vc), va, vb) =>
      RaUnknown(MatchesF(NoneLitF(vc), va, vb))
    case IntLitF(v, va)     => RaUnknown(IntLitF(v, va))
    case FloatLitF(v, va)   => RaUnknown(FloatLitF(v, va))
    case StringLitF(v, va)  => RaUnknown(StringLitF(v, va))
    case BoolLitF(v, va)    => RaUnknown(BoolLitF(v, va))
    case NoneLitF(v)        => RaUnknown(NoneLitF(v))
    case IdentifierF(v, va) => RaUnknown(IdentifierF(v, va))
  }

  def enumLiteralOf(x0: expr_full, ms: List[String]): Option[String] = (x0, ms) match {
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

  def fieldTypeFull(x0: field_decl_full): type_expr_full = x0 match {
    case FieldDeclFull(uu, t, uv, uw) => t
  }

  def flattenAndAll(es: List[expr_full]): List[expr_full] =
    maps[expr_full, expr_full]((a: expr_full) => flattenAnd(a), es)

  def paramTypeFull(x0: param_decl_full): type_expr_full = x0 match {
    case ParamDeclFull(uu, t, uv) => t
  }

  def equal_un_op_full(x0: un_op_full, x1: un_op_full): Boolean = (x0, x1) match {
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

  def requiresAlloy_bindings(x0: List[quantifier_binding_full]): Boolean = x0 match {
    case Nil => false
    case QuantifierBindingFull(wn, d, wo, wp) :: bs =>
      requiresAlloy(d) || requiresAlloy_bindings(bs)
  }

  def requiresAlloy_entries(x0: List[map_entry_full]): Boolean = x0 match {
    case Nil => false
    case MapEntryFull(k, v, wm) :: es =>
      requiresAlloy(k) || (requiresAlloy(v) || requiresAlloy_entries(es))
  }

  def requiresAlloy_fields(x0: List[field_assign_full]): Boolean = x0 match {
    case Nil => false
    case FieldAssignFull(wk, v, wl) :: fs =>
      requiresAlloy(v) || requiresAlloy_fields(fs)
  }

  def requiresAlloy_list(x0: List[expr_full]): Boolean = x0 match {
    case Nil     => false
    case x :: xs => requiresAlloy(x) || requiresAlloy_list(xs)
  }

  def requiresAlloy(x0: expr_full): Boolean = x0 match {
    case UnaryOpF(op, e, uu)     => equal_un_op_full(op, UPower()) || requiresAlloy(e)
    case BinaryOpF(uv, l, r, uw) => requiresAlloy(l) || requiresAlloy(r)
    case QuantifierF(ux, bs, body, uy) =>
      requiresAlloy_bindings(bs) || requiresAlloy(body)
    case SomeWrapF(x, uz)        => requiresAlloy(x)
    case TheF(va, d, b, vb)      => requiresAlloy(d) || requiresAlloy(b)
    case FieldAccessF(b, vc, vd) => requiresAlloy(b)
    case EnumAccessF(b, ve, vf)  => requiresAlloy(b)
    case IndexF(b, i, vg)        => requiresAlloy(b) || requiresAlloy(i)
    case CallF(c, args, vh)      => requiresAlloy(c) || requiresAlloy_list(args)
    case PrimeF(x, vi)           => requiresAlloy(x)
    case PreF(x, vj)             => requiresAlloy(x)
    case WithF(b, upds, vk)      => requiresAlloy(b) || requiresAlloy_fields(upds)
    case IfF(c, t, e, vl) =>
      requiresAlloy(c) || (requiresAlloy(t) || requiresAlloy(e))
    case LetF(vm, v, b, vn)              => requiresAlloy(v) || requiresAlloy(b)
    case LambdaF(vo, b, vp)              => requiresAlloy(b)
    case ConstructorF(vq, fs, vr)        => requiresAlloy_fields(fs)
    case SetLiteralF(xs, vs)             => requiresAlloy_list(xs)
    case MapLiteralF(es, vt)             => requiresAlloy_entries(es)
    case SetComprehensionF(vu, d, p, vv) => requiresAlloy(d) || requiresAlloy(p)
    case SeqLiteralF(xs, vw)             => requiresAlloy_list(xs)
    case MatchesF(x, vx, vy)             => requiresAlloy(x)
    case IntLitF(vz, wa)                 => false
    case FloatLitF(wb, wc)               => false
    case StringLitF(wd, we)              => false
    case BoolLitF(wf, wg)                => false
    case NoneLitF(wh)                    => false
    case IdentifierF(wi, wj)             => false
  }

  def emptyCollected: (List[String], (List[String], List[with_info_full])) =
    (Nil, (Nil, Nil))

  def entityFieldsFull(x0: entity_decl_full): List[field_decl_full] = x0 match {
    case EntityDeclFull(uu, uv, fs, uw, ux) => fs
  }

  def entityHasField(es: List[entity_decl_full], ename: String, fname: String): Boolean =
    entityByName(es, ename) match {
      case None => false
      case Some(ed) =>
        list_ex[field_decl_full](
          (fd: field_decl_full) =>
            fieldNameFull(fd) == fname,
          entityFieldsFull(ed)
        )
    }

  def entityInvsFull(x0: entity_decl_full): List[expr_full] = x0 match {
    case EntityDeclFull(uu, uv, uw, iv, ux) => iv
  }

  def flattenEnsuresExpr(x0: expr_full): List[expr_full] = x0 match {
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

  def flattenEnsures(es: List[expr_full]): List[expr_full] =
    maps[expr_full, expr_full]((a: expr_full) => flattenEnsuresExpr(a), es)

  def flatten_entity(es: List[entity_decl_full], x1: entity_decl_full): entity_decl_full =
    (es, x1) match {
      case (es, EntityDeclFull(nm, pa, fs, iv, sp)) =>
        pa match {
          case None => EntityDeclFull(nm, pa, fs, iv, sp)
          case Some(_) =>
            val anc =
              butlast[entity_decl_full](
                chain_up(es, size_list[entity_decl_full](es), nm, List(nm))
              ): List[entity_decl_full];
            nulla[entity_decl_full](anc) match {
              case true => EntityDeclFull(nm, pa, fs, iv, sp)
              case false => EntityDeclFull(
                  nm,
                  pa,
                  foldl[List[field_decl_full], field_decl_full](
                    (a: List[field_decl_full]) =>
                      (b: field_decl_full) => upsert_field(a, b),
                    Nil,
                    maps[entity_decl_full, field_decl_full](
                      (a: entity_decl_full) =>
                        entityFieldsFull(a),
                      anc
                    ) ++
                      fs
                  ),
                  maps[entity_decl_full, expr_full](
                    (a: entity_decl_full) => entityInvsFull(a),
                    anc
                  ) ++
                    iv,
                  sp
                )
            }
        }
    }

  def rootIdentifier(x0: expr_full): Option[String] = x0 match {
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

  def typeStripSpans(x0: type_expr_full): type_expr_full = x0 match {
    case NamedTypeF(n, uu) => NamedTypeF(n, None)
    case SetTypeF(t, uv)   => SetTypeF(typeStripSpans(t), None)
    case MapTypeF(k, v, uw) =>
      MapTypeF(typeStripSpans(k), typeStripSpans(v), None)
    case SeqTypeF(t, ux)    => SeqTypeF(typeStripSpans(t), None)
    case OptionTypeF(t, uy) => OptionTypeF(typeStripSpans(t), None)
    case RelationTypeF(f, m, t, uz) =>
      RelationTypeF(typeStripSpans(f), m, typeStripSpans(t), None)
  }

  def combineCollected(
      x0: (List[String], (List[String], List[with_info_full])),
      x1: (List[String], (List[String], List[with_info_full]))
  ): (List[String], (List[String], List[with_info_full])) =
    (x0, x1) match {
      case ((p1, (f1, w1)), (p2, (f2, w2))) => (p1 ++ p2, (f1 ++ f2, w1 ++ w2))
    }

  def resolveWithBase(x0: expr_full): Option[String] = x0 match {
    case IdentifierF(uu, uv)                          => None
    case IndexF(PreF(IdentifierF(n, uw), ux), uy, uz) => Some[String](n)
    case IndexF(IdentifierF(n, va), vb, vc)           => Some[String](n)
    case IndexF(BinaryOpF(v, va, vb, vc), vd, ve) =>
      rootIdentifier(BinaryOpF(v, va, vb, vc))
    case IndexF(UnaryOpF(v, va, vb), vd, ve) =>
      rootIdentifier(UnaryOpF(v, va, vb))
    case IndexF(QuantifierF(v, va, vb, vc), vd, ve) =>
      rootIdentifier(QuantifierF(v, va, vb, vc))
    case IndexF(SomeWrapF(v, va), vd, ve) => rootIdentifier(SomeWrapF(v, va))
    case IndexF(TheF(v, va, vb, vc), vd, ve) =>
      rootIdentifier(TheF(v, va, vb, vc))
    case IndexF(FieldAccessF(v, va, vb), vd, ve) =>
      rootIdentifier(FieldAccessF(v, va, vb))
    case IndexF(EnumAccessF(v, va, vb), vd, ve) =>
      rootIdentifier(EnumAccessF(v, va, vb))
    case IndexF(IndexF(v, va, vb), vd, ve) => rootIdentifier(IndexF(v, va, vb))
    case IndexF(CallF(v, va, vb), vd, ve)  => rootIdentifier(CallF(v, va, vb))
    case IndexF(PrimeF(v, va), vd, ve)     => rootIdentifier(PrimeF(v, va))
    case IndexF(PreF(BinaryOpF(vb, vc, vf, vg), va), vd, ve) =>
      rootIdentifier(PreF(BinaryOpF(vb, vc, vf, vg), va))
    case IndexF(PreF(UnaryOpF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(UnaryOpF(vb, vc, vf), va))
    case IndexF(PreF(QuantifierF(vb, vc, vf, vg), va), vd, ve) =>
      rootIdentifier(PreF(QuantifierF(vb, vc, vf, vg), va))
    case IndexF(PreF(SomeWrapF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(SomeWrapF(vb, vc), va))
    case IndexF(PreF(TheF(vb, vc, vf, vg), va), vd, ve) =>
      rootIdentifier(PreF(TheF(vb, vc, vf, vg), va))
    case IndexF(PreF(FieldAccessF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(FieldAccessF(vb, vc, vf), va))
    case IndexF(PreF(EnumAccessF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(EnumAccessF(vb, vc, vf), va))
    case IndexF(PreF(IndexF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(IndexF(vb, vc, vf), va))
    case IndexF(PreF(CallF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(CallF(vb, vc, vf), va))
    case IndexF(PreF(PrimeF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(PrimeF(vb, vc), va))
    case IndexF(PreF(PreF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(PreF(vb, vc), va))
    case IndexF(PreF(WithF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(WithF(vb, vc, vf), va))
    case IndexF(PreF(IfF(vb, vc, vf, vg), va), vd, ve) =>
      rootIdentifier(PreF(IfF(vb, vc, vf, vg), va))
    case IndexF(PreF(LetF(vb, vc, vf, vg), va), vd, ve) =>
      rootIdentifier(PreF(LetF(vb, vc, vf, vg), va))
    case IndexF(PreF(LambdaF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(LambdaF(vb, vc, vf), va))
    case IndexF(PreF(ConstructorF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(ConstructorF(vb, vc, vf), va))
    case IndexF(PreF(SetLiteralF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(SetLiteralF(vb, vc), va))
    case IndexF(PreF(MapLiteralF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(MapLiteralF(vb, vc), va))
    case IndexF(PreF(SetComprehensionF(vb, vc, vf, vg), va), vd, ve) =>
      rootIdentifier(PreF(SetComprehensionF(vb, vc, vf, vg), va))
    case IndexF(PreF(SeqLiteralF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(SeqLiteralF(vb, vc), va))
    case IndexF(PreF(MatchesF(vb, vc, vf), va), vd, ve) =>
      rootIdentifier(PreF(MatchesF(vb, vc, vf), va))
    case IndexF(PreF(IntLitF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(IntLitF(vb, vc), va))
    case IndexF(PreF(FloatLitF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(FloatLitF(vb, vc), va))
    case IndexF(PreF(StringLitF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(StringLitF(vb, vc), va))
    case IndexF(PreF(BoolLitF(vb, vc), va), vd, ve) =>
      rootIdentifier(PreF(BoolLitF(vb, vc), va))
    case IndexF(PreF(NoneLitF(vb), va), vd, ve) =>
      rootIdentifier(PreF(NoneLitF(vb), va))
    case IndexF(WithF(v, va, vb), vd, ve)   => rootIdentifier(WithF(v, va, vb))
    case IndexF(IfF(v, va, vb, vc), vd, ve) => rootIdentifier(IfF(v, va, vb, vc))
    case IndexF(LetF(v, va, vb, vc), vd, ve) =>
      rootIdentifier(LetF(v, va, vb, vc))
    case IndexF(LambdaF(v, va, vb), vd, ve) => rootIdentifier(LambdaF(v, va, vb))
    case IndexF(ConstructorF(v, va, vb), vd, ve) =>
      rootIdentifier(ConstructorF(v, va, vb))
    case IndexF(SetLiteralF(v, va), vd, ve) => rootIdentifier(SetLiteralF(v, va))
    case IndexF(MapLiteralF(v, va), vd, ve) => rootIdentifier(MapLiteralF(v, va))
    case IndexF(SetComprehensionF(v, va, vb, vc), vd, ve) =>
      rootIdentifier(SetComprehensionF(v, va, vb, vc))
    case IndexF(SeqLiteralF(v, va), vd, ve) => rootIdentifier(SeqLiteralF(v, va))
    case IndexF(MatchesF(v, va, vb), vd, ve) =>
      rootIdentifier(MatchesF(v, va, vb))
    case IndexF(IntLitF(v, va), vd, ve)    => rootIdentifier(IntLitF(v, va))
    case IndexF(FloatLitF(v, va), vd, ve)  => rootIdentifier(FloatLitF(v, va))
    case IndexF(StringLitF(v, va), vd, ve) => rootIdentifier(StringLitF(v, va))
    case IndexF(BoolLitF(v, va), vd, ve)   => rootIdentifier(BoolLitF(v, va))
    case IndexF(NoneLitF(v), vd, ve)       => rootIdentifier(NoneLitF(v))
    case BinaryOpF(v, va, vb, vc)          => rootIdentifier(BinaryOpF(v, va, vb, vc))
    case UnaryOpF(v, va, vb)               => rootIdentifier(UnaryOpF(v, va, vb))
    case QuantifierF(v, va, vb, vc)        => rootIdentifier(QuantifierF(v, va, vb, vc))
    case SomeWrapF(v, va)                  => rootIdentifier(SomeWrapF(v, va))
    case TheF(v, va, vb, vc)               => rootIdentifier(TheF(v, va, vb, vc))
    case FieldAccessF(v, va, vb)           => rootIdentifier(FieldAccessF(v, va, vb))
    case EnumAccessF(v, va, vb)            => rootIdentifier(EnumAccessF(v, va, vb))
    case CallF(v, va, vb)                  => rootIdentifier(CallF(v, va, vb))
    case PrimeF(v, va)                     => rootIdentifier(PrimeF(v, va))
    case PreF(v, va)                       => rootIdentifier(PreF(v, va))
    case WithF(v, va, vb)                  => rootIdentifier(WithF(v, va, vb))
    case IfF(v, va, vb, vc)                => rootIdentifier(IfF(v, va, vb, vc))
    case LetF(v, va, vb, vc)               => rootIdentifier(LetF(v, va, vb, vc))
    case LambdaF(v, va, vb)                => rootIdentifier(LambdaF(v, va, vb))
    case ConstructorF(v, va, vb)           => rootIdentifier(ConstructorF(v, va, vb))
    case SetLiteralF(v, va)                => rootIdentifier(SetLiteralF(v, va))
    case MapLiteralF(v, va)                => rootIdentifier(MapLiteralF(v, va))
    case SetComprehensionF(v, va, vb, vc) =>
      rootIdentifier(SetComprehensionF(v, va, vb, vc))
    case SeqLiteralF(v, va)  => rootIdentifier(SeqLiteralF(v, va))
    case MatchesF(v, va, vb) => rootIdentifier(MatchesF(v, va, vb))
    case IntLitF(v, va)      => rootIdentifier(IntLitF(v, va))
    case FloatLitF(v, va)    => rootIdentifier(FloatLitF(v, va))
    case StringLitF(v, va)   => rootIdentifier(StringLitF(v, va))
    case BoolLitF(v, va)     => rootIdentifier(BoolLitF(v, va))
    case NoneLitF(v)         => rootIdentifier(NoneLitF(v))
  }

  def fieldAssignName(x0: field_assign_full): String = x0 match {
    case FieldAssignFull(n, uu, uv) => n
  }

  def consFieldAccess(
      n: String,
      x1: (List[String], (List[String], List[with_info_full]))
  ): (List[String], (List[String], List[with_info_full])) =
    (n, x1) match {
      case (n, (p, (f, w))) => (p, (n :: f, w))
    }

  def collectExprInfo_bindings(x0: List[quantifier_binding_full])
      : (List[String], (List[String], List[with_info_full])) =
    x0 match {
      case Nil => emptyCollected
      case QuantifierBindingFull(wn, d, wo, wp) :: bs =>
        combineCollected(collectExprInfo(d), collectExprInfo_bindings(bs))
    }

  def collectExprInfo_entries(x0: List[map_entry_full])
      : (List[String], (List[String], List[with_info_full])) =
    x0 match {
      case Nil => emptyCollected
      case MapEntryFull(k, v, wm) :: es =>
        combineCollected(
          combineCollected(collectExprInfo(k), collectExprInfo(v)),
          collectExprInfo_entries(es)
        )
    }

  def collectExprInfo_fields(x0: List[field_assign_full])
      : (List[String], (List[String], List[with_info_full])) =
    x0 match {
      case Nil => emptyCollected
      case FieldAssignFull(wk, v, wl) :: fs =>
        combineCollected(collectExprInfo(v), collectExprInfo_fields(fs))
    }

  def collectExprInfo_list(x0: List[expr_full])
      : (List[String], (List[String], List[with_info_full])) =
    x0 match {
      case Nil     => emptyCollected
      case x :: xs => combineCollected(collectExprInfo(x), collectExprInfo_list(xs))
    }

  def collectExprInfo(x0: expr_full): (List[String], (List[String], List[with_info_full])) =
    x0 match {
      case PrimeF(inner, uu) =>
        consPrimed(rootIdentifier(inner), collectExprInfo(inner))
      case FieldAccessF(base, n, uv) => consFieldAccess(n, collectExprInfo(base))
      case WithF(base, ups, uw) =>
        consWithInfo(
          WithInfoFull(
            map[field_assign_full, String](
              (a: field_assign_full) =>
                fieldAssignName(a),
              ups
            ),
            resolveWithBase(base)
          ),
          combineCollected(collectExprInfo(base), collectExprInfo_fields(ups))
        )
      case BinaryOpF(ux, l, r, uy) =>
        combineCollected(collectExprInfo(l), collectExprInfo(r))
      case UnaryOpF(uz, e, va) => collectExprInfo(e)
      case QuantifierF(vb, bs, body, vc) =>
        combineCollected(collectExprInfo_bindings(bs), collectExprInfo(body))
      case SomeWrapF(e, vd) => collectExprInfo(e)
      case TheF(ve, d, b, vf) =>
        combineCollected(collectExprInfo(d), collectExprInfo(b))
      case EnumAccessF(base, vg, vh) => collectExprInfo(base)
      case IndexF(b, i, vi) =>
        combineCollected(collectExprInfo(b), collectExprInfo(i))
      case CallF(c, args, vj) =>
        combineCollected(collectExprInfo(c), collectExprInfo_list(args))
      case PreF(e, vk) => collectExprInfo(e)
      case IfF(c, t, el, vl) =>
        combineCollected(
          combineCollected(collectExprInfo(c), collectExprInfo(t)),
          collectExprInfo(el)
        )
      case LetF(vm, v, b, vn) =>
        combineCollected(collectExprInfo(v), collectExprInfo(b))
      case LambdaF(vo, b, vp)       => collectExprInfo(b)
      case ConstructorF(vq, fs, vr) => collectExprInfo_fields(fs)
      case SetLiteralF(xs, vs)      => collectExprInfo_list(xs)
      case MapLiteralF(es, vt)      => collectExprInfo_entries(es)
      case SetComprehensionF(vu, d, p, vv) =>
        combineCollected(collectExprInfo(d), collectExprInfo(p))
      case SeqLiteralF(xs, vw) => collectExprInfo_list(xs)
      case MatchesF(e, vx, vy) => collectExprInfo(e)
      case IntLitF(vz, wa)     => emptyCollected
      case FloatLitF(wb, wc)   => emptyCollected
      case StringLitF(wd, we)  => emptyCollected
      case BoolLitF(wf, wg)    => emptyCollected
      case NoneLitF(wh)        => emptyCollected
      case IdentifierF(wi, wj) => emptyCollected
    }

  def containsPreInPlusChain(x0: expr_full, field: String): Boolean =
    (x0, field) match {
      case (PreF(IdentifierF(n, uu), uv), field) => n == field
      case (BinaryOpF(BAdd(), l, r, uw), field) =>
        containsPreInPlusChain(l, field) || containsPreInPlusChain(r, field)
      case (BinaryOpF(BAnd(), va, vb, vc), uy)               => false
      case (BinaryOpF(BOr(), va, vb, vc), uy)                => false
      case (BinaryOpF(BImplies(), va, vb, vc), uy)           => false
      case (BinaryOpF(BIff(), va, vb, vc), uy)               => false
      case (BinaryOpF(BEq(), va, vb, vc), uy)                => false
      case (BinaryOpF(BNeq(), va, vb, vc), uy)               => false
      case (BinaryOpF(BLt(), va, vb, vc), uy)                => false
      case (BinaryOpF(BGt(), va, vb, vc), uy)                => false
      case (BinaryOpF(BLe(), va, vb, vc), uy)                => false
      case (BinaryOpF(BGe(), va, vb, vc), uy)                => false
      case (BinaryOpF(BIn(), va, vb, vc), uy)                => false
      case (BinaryOpF(BNotIn(), va, vb, vc), uy)             => false
      case (BinaryOpF(BSubset(), va, vb, vc), uy)            => false
      case (BinaryOpF(BUnion(), va, vb, vc), uy)             => false
      case (BinaryOpF(BIntersect(), va, vb, vc), uy)         => false
      case (BinaryOpF(BDiff(), va, vb, vc), uy)              => false
      case (BinaryOpF(BSub(), va, vb, vc), uy)               => false
      case (BinaryOpF(BMul(), va, vb, vc), uy)               => false
      case (BinaryOpF(BDiv(), va, vb, vc), uy)               => false
      case (UnaryOpF(v, va, vb), uy)                         => false
      case (QuantifierF(v, va, vb, vc), uy)                  => false
      case (SomeWrapF(v, va), uy)                            => false
      case (TheF(v, va, vb, vc), uy)                         => false
      case (FieldAccessF(v, va, vb), uy)                     => false
      case (EnumAccessF(v, va, vb), uy)                      => false
      case (IndexF(v, va, vb), uy)                           => false
      case (CallF(v, va, vb), uy)                            => false
      case (PrimeF(v, va), uy)                               => false
      case (PreF(BinaryOpF(vb, vc, vd, ve), va), uy)         => false
      case (PreF(UnaryOpF(vb, vc, vd), va), uy)              => false
      case (PreF(QuantifierF(vb, vc, vd, ve), va), uy)       => false
      case (PreF(SomeWrapF(vb, vc), va), uy)                 => false
      case (PreF(TheF(vb, vc, vd, ve), va), uy)              => false
      case (PreF(FieldAccessF(vb, vc, vd), va), uy)          => false
      case (PreF(EnumAccessF(vb, vc, vd), va), uy)           => false
      case (PreF(IndexF(vb, vc, vd), va), uy)                => false
      case (PreF(CallF(vb, vc, vd), va), uy)                 => false
      case (PreF(PrimeF(vb, vc), va), uy)                    => false
      case (PreF(PreF(vb, vc), va), uy)                      => false
      case (PreF(WithF(vb, vc, vd), va), uy)                 => false
      case (PreF(IfF(vb, vc, vd, ve), va), uy)               => false
      case (PreF(LetF(vb, vc, vd, ve), va), uy)              => false
      case (PreF(LambdaF(vb, vc, vd), va), uy)               => false
      case (PreF(ConstructorF(vb, vc, vd), va), uy)          => false
      case (PreF(SetLiteralF(vb, vc), va), uy)               => false
      case (PreF(MapLiteralF(vb, vc), va), uy)               => false
      case (PreF(SetComprehensionF(vb, vc, vd, ve), va), uy) => false
      case (PreF(SeqLiteralF(vb, vc), va), uy)               => false
      case (PreF(MatchesF(vb, vc, vd), va), uy)              => false
      case (PreF(IntLitF(vb, vc), va), uy)                   => false
      case (PreF(FloatLitF(vb, vc), va), uy)                 => false
      case (PreF(StringLitF(vb, vc), va), uy)                => false
      case (PreF(BoolLitF(vb, vc), va), uy)                  => false
      case (PreF(NoneLitF(vb), va), uy)                      => false
      case (WithF(v, va, vb), uy)                            => false
      case (IfF(v, va, vb, vc), uy)                          => false
      case (LetF(v, va, vb, vc), uy)                         => false
      case (LambdaF(v, va, vb), uy)                          => false
      case (ConstructorF(v, va, vb), uy)                     => false
      case (SetLiteralF(v, va), uy)                          => false
      case (MapLiteralF(v, va), uy)                          => false
      case (SetComprehensionF(v, va, vb, vc), uy)            => false
      case (SeqLiteralF(v, va), uy)                          => false
      case (MatchesF(v, va, vb), uy)                         => false
      case (IntLitF(v, va), uy)                              => false
      case (FloatLitF(v, va), uy)                            => false
      case (StringLitF(v, va), uy)                           => false
      case (BoolLitF(v, va), uy)                             => false
      case (NoneLitF(v), uy)                                 => false
      case (IdentifierF(v, va), uy)                          => false
    }

  def createPatternOf(stateFields: List[String], x1: expr_full): List[String] =
    (stateFields, x1) match {
      case (
            stateFields,
            BinaryOpF(BEq(), PrimeF(IdentifierF(name, uu), uv), BinaryOpF(BAdd(), l, r, sp), uw)
          ) => list_ex[String]((n: String) => n == name, stateFields) &&
          containsPreInPlusChain(BinaryOpF(BAdd(), l, r, sp), name) match {
          case true  => List(name)
          case false => Nil
        }
      case (ux, BinaryOpF(BAnd(), va, vb, vc))                               => Nil
      case (ux, BinaryOpF(BOr(), va, vb, vc))                                => Nil
      case (ux, BinaryOpF(BImplies(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(BIff(), va, vb, vc))                               => Nil
      case (ux, BinaryOpF(BNeq(), va, vb, vc))                               => Nil
      case (ux, BinaryOpF(BLt(), va, vb, vc))                                => Nil
      case (ux, BinaryOpF(BGt(), va, vb, vc))                                => Nil
      case (ux, BinaryOpF(BLe(), va, vb, vc))                                => Nil
      case (ux, BinaryOpF(BGe(), va, vb, vc))                                => Nil
      case (ux, BinaryOpF(BIn(), va, vb, vc))                                => Nil
      case (ux, BinaryOpF(BNotIn(), va, vb, vc))                             => Nil
      case (ux, BinaryOpF(BSubset(), va, vb, vc))                            => Nil
      case (ux, BinaryOpF(BUnion(), va, vb, vc))                             => Nil
      case (ux, BinaryOpF(BIntersect(), va, vb, vc))                         => Nil
      case (ux, BinaryOpF(BDiff(), va, vb, vc))                              => Nil
      case (ux, BinaryOpF(BAdd(), va, vb, vc))                               => Nil
      case (ux, BinaryOpF(BSub(), va, vb, vc))                               => Nil
      case (ux, BinaryOpF(BMul(), va, vb, vc))                               => Nil
      case (ux, BinaryOpF(BDiv(), va, vb, vc))                               => Nil
      case (ux, BinaryOpF(v, BinaryOpF(vd, ve, vf, vg), vb, vc))             => Nil
      case (ux, BinaryOpF(v, UnaryOpF(vd, ve, vf), vb, vc))                  => Nil
      case (ux, BinaryOpF(v, QuantifierF(vd, ve, vf, vg), vb, vc))           => Nil
      case (ux, BinaryOpF(v, SomeWrapF(vd, ve), vb, vc))                     => Nil
      case (ux, BinaryOpF(v, TheF(vd, ve, vf, vg), vb, vc))                  => Nil
      case (ux, BinaryOpF(v, FieldAccessF(vd, ve, vf), vb, vc))              => Nil
      case (ux, BinaryOpF(v, EnumAccessF(vd, ve, vf), vb, vc))               => Nil
      case (ux, BinaryOpF(v, IndexF(vd, ve, vf), vb, vc))                    => Nil
      case (ux, BinaryOpF(v, CallF(vd, ve, vf), vb, vc))                     => Nil
      case (ux, BinaryOpF(v, PrimeF(BinaryOpF(vf, vg, vh, vi), ve), vb, vc)) => Nil
      case (ux, BinaryOpF(v, PrimeF(UnaryOpF(vf, vg, vh), ve), vb, vc))      => Nil
      case (ux, BinaryOpF(v, PrimeF(QuantifierF(vf, vg, vh, vi), ve), vb, vc)) =>
        Nil
      case (ux, BinaryOpF(v, PrimeF(SomeWrapF(vf, vg), ve), vb, vc))                 => Nil
      case (ux, BinaryOpF(v, PrimeF(TheF(vf, vg, vh, vi), ve), vb, vc))              => Nil
      case (ux, BinaryOpF(v, PrimeF(FieldAccessF(vf, vg, vh), ve), vb, vc))          => Nil
      case (ux, BinaryOpF(v, PrimeF(EnumAccessF(vf, vg, vh), ve), vb, vc))           => Nil
      case (ux, BinaryOpF(v, PrimeF(IndexF(vf, vg, vh), ve), vb, vc))                => Nil
      case (ux, BinaryOpF(v, PrimeF(CallF(vf, vg, vh), ve), vb, vc))                 => Nil
      case (ux, BinaryOpF(v, PrimeF(PrimeF(vf, vg), ve), vb, vc))                    => Nil
      case (ux, BinaryOpF(v, PrimeF(PreF(vf, vg), ve), vb, vc))                      => Nil
      case (ux, BinaryOpF(v, PrimeF(WithF(vf, vg, vh), ve), vb, vc))                 => Nil
      case (ux, BinaryOpF(v, PrimeF(IfF(vf, vg, vh, vi), ve), vb, vc))               => Nil
      case (ux, BinaryOpF(v, PrimeF(LetF(vf, vg, vh, vi), ve), vb, vc))              => Nil
      case (ux, BinaryOpF(v, PrimeF(LambdaF(vf, vg, vh), ve), vb, vc))               => Nil
      case (ux, BinaryOpF(v, PrimeF(ConstructorF(vf, vg, vh), ve), vb, vc))          => Nil
      case (ux, BinaryOpF(v, PrimeF(SetLiteralF(vf, vg), ve), vb, vc))               => Nil
      case (ux, BinaryOpF(v, PrimeF(MapLiteralF(vf, vg), ve), vb, vc))               => Nil
      case (ux, BinaryOpF(v, PrimeF(SetComprehensionF(vf, vg, vh, vi), ve), vb, vc)) => Nil
      case (ux, BinaryOpF(v, PrimeF(SeqLiteralF(vf, vg), ve), vb, vc))               => Nil
      case (ux, BinaryOpF(v, PrimeF(MatchesF(vf, vg, vh), ve), vb, vc))              => Nil
      case (ux, BinaryOpF(v, PrimeF(IntLitF(vf, vg), ve), vb, vc))                   => Nil
      case (ux, BinaryOpF(v, PrimeF(FloatLitF(vf, vg), ve), vb, vc))                 => Nil
      case (ux, BinaryOpF(v, PrimeF(StringLitF(vf, vg), ve), vb, vc))                => Nil
      case (ux, BinaryOpF(v, PrimeF(BoolLitF(vf, vg), ve), vb, vc))                  => Nil
      case (ux, BinaryOpF(v, PrimeF(NoneLitF(vf), ve), vb, vc))                      => Nil
      case (ux, BinaryOpF(v, PreF(vd, ve), vb, vc))                                  => Nil
      case (ux, BinaryOpF(v, WithF(vd, ve, vf), vb, vc))                             => Nil
      case (ux, BinaryOpF(v, IfF(vd, ve, vf, vg), vb, vc))                           => Nil
      case (ux, BinaryOpF(v, LetF(vd, ve, vf, vg), vb, vc))                          => Nil
      case (ux, BinaryOpF(v, LambdaF(vd, ve, vf), vb, vc))                           => Nil
      case (ux, BinaryOpF(v, ConstructorF(vd, ve, vf), vb, vc))                      => Nil
      case (ux, BinaryOpF(v, SetLiteralF(vd, ve), vb, vc))                           => Nil
      case (ux, BinaryOpF(v, MapLiteralF(vd, ve), vb, vc))                           => Nil
      case (ux, BinaryOpF(v, SetComprehensionF(vd, ve, vf, vg), vb, vc))             => Nil
      case (ux, BinaryOpF(v, SeqLiteralF(vd, ve), vb, vc))                           => Nil
      case (ux, BinaryOpF(v, MatchesF(vd, ve, vf), vb, vc))                          => Nil
      case (ux, BinaryOpF(v, IntLitF(vd, ve), vb, vc))                               => Nil
      case (ux, BinaryOpF(v, FloatLitF(vd, ve), vb, vc))                             => Nil
      case (ux, BinaryOpF(v, StringLitF(vd, ve), vb, vc))                            => Nil
      case (ux, BinaryOpF(v, BoolLitF(vd, ve), vb, vc))                              => Nil
      case (ux, BinaryOpF(v, NoneLitF(vd), vb, vc))                                  => Nil
      case (ux, BinaryOpF(v, IdentifierF(vd, ve), vb, vc))                           => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BAnd(), ve, vf, vg), vc))                 => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BOr(), ve, vf, vg), vc))                  => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BImplies(), ve, vf, vg), vc))             => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BIff(), ve, vf, vg), vc))                 => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BEq(), ve, vf, vg), vc))                  => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BNeq(), ve, vf, vg), vc))                 => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BLt(), ve, vf, vg), vc))                  => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BGt(), ve, vf, vg), vc))                  => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BLe(), ve, vf, vg), vc))                  => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BGe(), ve, vf, vg), vc))                  => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BIn(), ve, vf, vg), vc))                  => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BNotIn(), ve, vf, vg), vc))               => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BSubset(), ve, vf, vg), vc))              => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BUnion(), ve, vf, vg), vc))               => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BIntersect(), ve, vf, vg), vc))           => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BDiff(), ve, vf, vg), vc))                => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BSub(), ve, vf, vg), vc))                 => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BMul(), ve, vf, vg), vc))                 => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(BDiv(), ve, vf, vg), vc))                 => Nil
      case (ux, BinaryOpF(v, va, UnaryOpF(vd, ve, vf), vc))                          => Nil
      case (ux, BinaryOpF(v, va, QuantifierF(vd, ve, vf, vg), vc))                   => Nil
      case (ux, BinaryOpF(v, va, SomeWrapF(vd, ve), vc))                             => Nil
      case (ux, BinaryOpF(v, va, TheF(vd, ve, vf, vg), vc))                          => Nil
      case (ux, BinaryOpF(v, va, FieldAccessF(vd, ve, vf), vc))                      => Nil
      case (ux, BinaryOpF(v, va, EnumAccessF(vd, ve, vf), vc))                       => Nil
      case (ux, BinaryOpF(v, va, IndexF(vd, ve, vf), vc))                            => Nil
      case (ux, BinaryOpF(v, va, CallF(vd, ve, vf), vc))                             => Nil
      case (ux, BinaryOpF(v, va, PrimeF(vd, ve), vc))                                => Nil
      case (ux, BinaryOpF(v, va, PreF(vd, ve), vc))                                  => Nil
      case (ux, BinaryOpF(v, va, WithF(vd, ve, vf), vc))                             => Nil
      case (ux, BinaryOpF(v, va, IfF(vd, ve, vf, vg), vc))                           => Nil
      case (ux, BinaryOpF(v, va, LetF(vd, ve, vf, vg), vc))                          => Nil
      case (ux, BinaryOpF(v, va, LambdaF(vd, ve, vf), vc))                           => Nil
      case (ux, BinaryOpF(v, va, ConstructorF(vd, ve, vf), vc))                      => Nil
      case (ux, BinaryOpF(v, va, SetLiteralF(vd, ve), vc))                           => Nil
      case (ux, BinaryOpF(v, va, MapLiteralF(vd, ve), vc))                           => Nil
      case (ux, BinaryOpF(v, va, SetComprehensionF(vd, ve, vf, vg), vc))             => Nil
      case (ux, BinaryOpF(v, va, SeqLiteralF(vd, ve), vc))                           => Nil
      case (ux, BinaryOpF(v, va, MatchesF(vd, ve, vf), vc))                          => Nil
      case (ux, BinaryOpF(v, va, IntLitF(vd, ve), vc))                               => Nil
      case (ux, BinaryOpF(v, va, FloatLitF(vd, ve), vc))                             => Nil
      case (ux, BinaryOpF(v, va, StringLitF(vd, ve), vc))                            => Nil
      case (ux, BinaryOpF(v, va, BoolLitF(vd, ve), vc))                              => Nil
      case (ux, BinaryOpF(v, va, NoneLitF(vd), vc))                                  => Nil
      case (ux, BinaryOpF(v, va, IdentifierF(vd, ve), vc))                           => Nil
      case (ux, UnaryOpF(v, va, vb))                                                 => Nil
      case (ux, QuantifierF(v, va, vb, vc))                                          => Nil
      case (ux, SomeWrapF(v, va))                                                    => Nil
      case (ux, TheF(v, va, vb, vc))                                                 => Nil
      case (ux, FieldAccessF(v, va, vb))                                             => Nil
      case (ux, EnumAccessF(v, va, vb))                                              => Nil
      case (ux, IndexF(v, va, vb))                                                   => Nil
      case (ux, CallF(v, va, vb))                                                    => Nil
      case (ux, PrimeF(v, va))                                                       => Nil
      case (ux, PreF(v, va))                                                         => Nil
      case (ux, WithF(v, va, vb))                                                    => Nil
      case (ux, IfF(v, va, vb, vc))                                                  => Nil
      case (ux, LetF(v, va, vb, vc))                                                 => Nil
      case (ux, LambdaF(v, va, vb))                                                  => Nil
      case (ux, ConstructorF(v, va, vb))                                             => Nil
      case (ux, SetLiteralF(v, va))                                                  => Nil
      case (ux, MapLiteralF(v, va))                                                  => Nil
      case (ux, SetComprehensionF(v, va, vb, vc))                                    => Nil
      case (ux, SeqLiteralF(v, va))                                                  => Nil
      case (ux, MatchesF(v, va, vb))                                                 => Nil
      case (ux, IntLitF(v, va))                                                      => Nil
      case (ux, FloatLitF(v, va))                                                    => Nil
      case (ux, StringLitF(v, va))                                                   => Nil
      case (ux, BoolLitF(v, va))                                                     => Nil
      case (ux, NoneLitF(v))                                                         => Nil
      case (ux, IdentifierF(v, va))                                                  => Nil
    }

  def deletePatternOf(stateFields: List[String], x1: expr_full): List[String] =
    (stateFields, x1) match {
      case (stateFields, BinaryOpF(BNotIn(), uu, PrimeF(IdentifierF(n, uv), uw), ux)) =>
        list_ex[String]((s: String) => s == n, stateFields) match {
          case true  => List(n)
          case false => Nil
        }
      case (uy, BinaryOpF(BAnd(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BOr(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BImplies(), va, vb, vc))                           => Nil
      case (uy, BinaryOpF(BIff(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BEq(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BNeq(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BLt(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BGt(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BLe(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BGe(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BIn(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BSubset(), va, vb, vc))                            => Nil
      case (uy, BinaryOpF(BUnion(), va, vb, vc))                             => Nil
      case (uy, BinaryOpF(BIntersect(), va, vb, vc))                         => Nil
      case (uy, BinaryOpF(BDiff(), va, vb, vc))                              => Nil
      case (uy, BinaryOpF(BAdd(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BSub(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BMul(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BDiv(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(v, va, BinaryOpF(vd, ve, vf, vg), vc))             => Nil
      case (uy, BinaryOpF(v, va, UnaryOpF(vd, ve, vf), vc))                  => Nil
      case (uy, BinaryOpF(v, va, QuantifierF(vd, ve, vf, vg), vc))           => Nil
      case (uy, BinaryOpF(v, va, SomeWrapF(vd, ve), vc))                     => Nil
      case (uy, BinaryOpF(v, va, TheF(vd, ve, vf, vg), vc))                  => Nil
      case (uy, BinaryOpF(v, va, FieldAccessF(vd, ve, vf), vc))              => Nil
      case (uy, BinaryOpF(v, va, EnumAccessF(vd, ve, vf), vc))               => Nil
      case (uy, BinaryOpF(v, va, IndexF(vd, ve, vf), vc))                    => Nil
      case (uy, BinaryOpF(v, va, CallF(vd, ve, vf), vc))                     => Nil
      case (uy, BinaryOpF(v, va, PrimeF(BinaryOpF(vf, vg, vh, vi), ve), vc)) => Nil
      case (uy, BinaryOpF(v, va, PrimeF(UnaryOpF(vf, vg, vh), ve), vc))      => Nil
      case (uy, BinaryOpF(v, va, PrimeF(QuantifierF(vf, vg, vh, vi), ve), vc)) =>
        Nil
      case (uy, BinaryOpF(v, va, PrimeF(SomeWrapF(vf, vg), ve), vc))                 => Nil
      case (uy, BinaryOpF(v, va, PrimeF(TheF(vf, vg, vh, vi), ve), vc))              => Nil
      case (uy, BinaryOpF(v, va, PrimeF(FieldAccessF(vf, vg, vh), ve), vc))          => Nil
      case (uy, BinaryOpF(v, va, PrimeF(EnumAccessF(vf, vg, vh), ve), vc))           => Nil
      case (uy, BinaryOpF(v, va, PrimeF(IndexF(vf, vg, vh), ve), vc))                => Nil
      case (uy, BinaryOpF(v, va, PrimeF(CallF(vf, vg, vh), ve), vc))                 => Nil
      case (uy, BinaryOpF(v, va, PrimeF(PrimeF(vf, vg), ve), vc))                    => Nil
      case (uy, BinaryOpF(v, va, PrimeF(PreF(vf, vg), ve), vc))                      => Nil
      case (uy, BinaryOpF(v, va, PrimeF(WithF(vf, vg, vh), ve), vc))                 => Nil
      case (uy, BinaryOpF(v, va, PrimeF(IfF(vf, vg, vh, vi), ve), vc))               => Nil
      case (uy, BinaryOpF(v, va, PrimeF(LetF(vf, vg, vh, vi), ve), vc))              => Nil
      case (uy, BinaryOpF(v, va, PrimeF(LambdaF(vf, vg, vh), ve), vc))               => Nil
      case (uy, BinaryOpF(v, va, PrimeF(ConstructorF(vf, vg, vh), ve), vc))          => Nil
      case (uy, BinaryOpF(v, va, PrimeF(SetLiteralF(vf, vg), ve), vc))               => Nil
      case (uy, BinaryOpF(v, va, PrimeF(MapLiteralF(vf, vg), ve), vc))               => Nil
      case (uy, BinaryOpF(v, va, PrimeF(SetComprehensionF(vf, vg, vh, vi), ve), vc)) => Nil
      case (uy, BinaryOpF(v, va, PrimeF(SeqLiteralF(vf, vg), ve), vc))               => Nil
      case (uy, BinaryOpF(v, va, PrimeF(MatchesF(vf, vg, vh), ve), vc))              => Nil
      case (uy, BinaryOpF(v, va, PrimeF(IntLitF(vf, vg), ve), vc))                   => Nil
      case (uy, BinaryOpF(v, va, PrimeF(FloatLitF(vf, vg), ve), vc))                 => Nil
      case (uy, BinaryOpF(v, va, PrimeF(StringLitF(vf, vg), ve), vc))                => Nil
      case (uy, BinaryOpF(v, va, PrimeF(BoolLitF(vf, vg), ve), vc))                  => Nil
      case (uy, BinaryOpF(v, va, PrimeF(NoneLitF(vf), ve), vc))                      => Nil
      case (uy, BinaryOpF(v, va, PreF(vd, ve), vc))                                  => Nil
      case (uy, BinaryOpF(v, va, WithF(vd, ve, vf), vc))                             => Nil
      case (uy, BinaryOpF(v, va, IfF(vd, ve, vf, vg), vc))                           => Nil
      case (uy, BinaryOpF(v, va, LetF(vd, ve, vf, vg), vc))                          => Nil
      case (uy, BinaryOpF(v, va, LambdaF(vd, ve, vf), vc))                           => Nil
      case (uy, BinaryOpF(v, va, ConstructorF(vd, ve, vf), vc))                      => Nil
      case (uy, BinaryOpF(v, va, SetLiteralF(vd, ve), vc))                           => Nil
      case (uy, BinaryOpF(v, va, MapLiteralF(vd, ve), vc))                           => Nil
      case (uy, BinaryOpF(v, va, SetComprehensionF(vd, ve, vf, vg), vc))             => Nil
      case (uy, BinaryOpF(v, va, SeqLiteralF(vd, ve), vc))                           => Nil
      case (uy, BinaryOpF(v, va, MatchesF(vd, ve, vf), vc))                          => Nil
      case (uy, BinaryOpF(v, va, IntLitF(vd, ve), vc))                               => Nil
      case (uy, BinaryOpF(v, va, FloatLitF(vd, ve), vc))                             => Nil
      case (uy, BinaryOpF(v, va, StringLitF(vd, ve), vc))                            => Nil
      case (uy, BinaryOpF(v, va, BoolLitF(vd, ve), vc))                              => Nil
      case (uy, BinaryOpF(v, va, NoneLitF(vd), vc))                                  => Nil
      case (uy, BinaryOpF(v, va, IdentifierF(vd, ve), vc))                           => Nil
      case (uy, UnaryOpF(v, va, vb))                                                 => Nil
      case (uy, QuantifierF(v, va, vb, vc))                                          => Nil
      case (uy, SomeWrapF(v, va))                                                    => Nil
      case (uy, TheF(v, va, vb, vc))                                                 => Nil
      case (uy, FieldAccessF(v, va, vb))                                             => Nil
      case (uy, EnumAccessF(v, va, vb))                                              => Nil
      case (uy, IndexF(v, va, vb))                                                   => Nil
      case (uy, CallF(v, va, vb))                                                    => Nil
      case (uy, PrimeF(v, va))                                                       => Nil
      case (uy, PreF(v, va))                                                         => Nil
      case (uy, WithF(v, va, vb))                                                    => Nil
      case (uy, IfF(v, va, vb, vc))                                                  => Nil
      case (uy, LetF(v, va, vb, vc))                                                 => Nil
      case (uy, LambdaF(v, va, vb))                                                  => Nil
      case (uy, ConstructorF(v, va, vb))                                             => Nil
      case (uy, SetLiteralF(v, va))                                                  => Nil
      case (uy, MapLiteralF(v, va))                                                  => Nil
      case (uy, SetComprehensionF(v, va, vb, vc))                                    => Nil
      case (uy, SeqLiteralF(v, va))                                                  => Nil
      case (uy, MatchesF(v, va, vb))                                                 => Nil
      case (uy, IntLitF(v, va))                                                      => Nil
      case (uy, FloatLitF(v, va))                                                    => Nil
      case (uy, StringLitF(v, va))                                                   => Nil
      case (uy, BoolLitF(v, va))                                                     => Nil
      case (uy, NoneLitF(v))                                                         => Nil
      case (uy, IdentifierF(v, va))                                                  => Nil
    }

  def entityFieldNames(es: List[entity_decl_full], ename: String): List[String] =
    entityByName(es, ename) match {
      case None => Nil
      case Some(ed) =>
        map[field_decl_full, String]((a: field_decl_full) => fieldNameFull(a), entityFieldsFull(ed))
    }

  def entityNameInList(es: List[entity_decl_full], nm: String): Option[String] =
    entityByName(es, nm) match {
      case None    => None
      case Some(_) => Some[String](nm)
    }

  def isCollectionType(x0: type_expr_full): Boolean = x0 match {
    case SetTypeF(uu, uv)              => true
    case SeqTypeF(uw, ux)              => true
    case MapTypeF(uy, uz, va)          => true
    case RelationTypeF(vb, vc, vd, ve) => true
    case NamedTypeF(v, va)             => false
    case OptionTypeF(v, va)            => false
  }

  def translate(x0: expr): smt_term = x0 match {
    case BoolLit(b, uu)                 => BLit(b)
    case IntLit(n, uv)                  => ILit(n)
    case Ident(x, uw)                   => TVar(x)
    case UnNot(e, ux)                   => TNot(translate(e))
    case UnNeg(e, uy)                   => TNeg(translate(e))
    case BoolBin(AndOp(), l, r, uz)     => TAnd(translate(l), translate(r))
    case BoolBin(OrOp(), l, r, va)      => TOr(translate(l), translate(r))
    case BoolBin(ImpliesOp(), l, r, vb) => TImplies(translate(l), translate(r))
    case BoolBin(IffOp(), l, r, vc) =>
      TAnd(TImplies(translate(l), translate(r)), TImplies(translate(r), translate(l)))
    case Arith(AddOp(), l, r, vd) => TAdd(translate(l), translate(r))
    case Arith(SubOp(), l, r, ve) => TSub(translate(l), translate(r))
    case Arith(MulOp(), l, r, vf) => TMul(translate(l), translate(r))
    case Arith(DivOp(), l, r, vg) => TDiv(translate(l), translate(r))
    case Cmp(EqOp(), l, r, vh)    => TEq(translate(l), translate(r))
    case Cmp(NeqOp(), l, r, vi)   => TNot(TEq(translate(l), translate(r)))
    case Cmp(LtOp(), l, r, vj)    => TLt(translate(l), translate(r))
    case Cmp(LeOp(), l, r, vk) =>
      TOr(TLt(translate(l), translate(r)), TEq(translate(l), translate(r)))
    case Cmp(GtOp(), l, r, vl) => TLt(translate(r), translate(l))
    case Cmp(GeOp(), l, r, vm) =>
      TOr(TLt(translate(r), translate(l)), TEq(translate(l), translate(r)))
    case LetIn(x, v, body, vn)          => TLetIn(x, translate(v), translate(body))
    case EnumAccess(en, mem, vo)        => EnumElemConst(en, mem)
    case Member(elem, rel_name, vp)     => TInDom(rel_name, translate(elem))
    case ForallEnum(vara, en, body, vq) => TForallEnum(vara, en, translate(body))
    case ForallRel(vara, rel_n, body, vr) =>
      TForallRel(vara, rel_n, translate(body))
    case Prime(e, vs)                 => TPrime(translate(e))
    case Pre(e, vt)                   => TPre(translate(e))
    case CardRel(rel_name, vu)        => TCardRel(rel_name)
    case IndexRel(base, key, vv)      => TIndexRel(translate(base), translate(key))
    case FieldAccess(base, fname, vw) => TFieldAccess(translate(base), fname)
    case SetEmpty(vx)                 => TSetEmpty()
    case SetInsert(elem, set_e, vy) =>
      TSetInsert(translate(elem), translate(set_e))
    case SetMember(elem, set_e, vz) =>
      TSetMember(translate(elem), translate(set_e))
    case SetBin(UnionOp(), l, r, wa) => TSetUnion(translate(l), translate(r))
    case SetBin(IntersectOp(), l, r, wb) =>
      TSetIntersect(translate(l), translate(r))
    case SetBin(DiffOp(), l, r, wc) => TSetDiff(translate(l), translate(r))
    case WithRec(base, fld, val_e, wd) =>
      TWithRec(translate(base), fld, translate(val_e))
  }

  def collectWithFields(es: List[expr_full]): Option[with_info_full] =
    snd[List[String], List[with_info_full]](
      snd[List[String], (List[String], List[with_info_full])](collectExprInfo_list(es))
    ) match {
      case Nil    => None
      case x :: _ => Some[with_info_full](x)
    }

  def countFilterParams(ps: List[param_decl_full]): nat =
    size_list[param_decl_full](filter[param_decl_full](
      (p: param_decl_full) =>
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

  def findFieldDeclFull(fs: List[field_decl_full], nm: String): Option[field_decl_full] =
    find[field_decl_full]((fd: field_decl_full) => fieldNameFull(fd) == nm, fs)

  def tyctxEmpty: tyctx_ext[Unit] =
    tyctx_exta[Unit](Nil, state_schema_exta[Unit](Nil, ()), Nil, Nil, Nil, ())

  def emptyServiceIrFull(nm: String): service_ir_full =
    ServiceIRFull(nm, Nil, Nil, Nil, Nil, None, Nil, Nil, Nil, Nil, Nil, Nil, Nil, None, None)

  def entityNameFromType(x0: type_expr_full): Option[String] = x0 match {
    case RelationTypeF(uu, uv, to, uw) => typeName(to)
    case NamedTypeF(n, ux)             => Some[String](n)
    case SetTypeF(inner, uy)           => entityNameFromType(inner)
    case SeqTypeF(inner, uz)           => entityNameFromType(inner)
    case OptionTypeF(inner, va)        => entityNameFromType(inner)
    case MapTypeF(vb, v, vc)           => entityNameFromType(v)
  }

  def flattenInheritance(x0: service_ir_full): service_ir_full = x0 match {
    case ServiceIRFull(a, b, c, d, e, f, g, h, i, j, k, l, m, n, p) =>
      ServiceIRFull(
        a,
        b,
        map[entity_decl_full, entity_decl_full](
          (aa: entity_decl_full) =>
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
        p
      )
  }

  def isInputCollectionType(x0: type_expr_full): Boolean = x0 match {
    case SetTypeF(uu, uv)             => true
    case SeqTypeF(uw, ux)             => true
    case MapTypeF(uy, uz, va)         => true
    case NamedTypeF(v, va)            => false
    case OptionTypeF(v, va)           => false
    case RelationTypeF(v, va, vc, vd) => false
  }

  def hasCollectionInput(ps: List[param_decl_full]): Boolean =
    list_ex[param_decl_full](
      (p: param_decl_full) =>
        isInputCollectionType(paramTypeFull(p)),
      ps
    )

  def withInfoFieldNames(x0: with_info_full): List[String] = x0 match {
    case WithInfoFull(fs, uu) => fs
  }

  def detectCreatePattern(es: List[expr_full], stateFields: List[String]): Option[String] =
    maps[expr_full, String](
      (a: expr_full) => createPatternOf(stateFields, a),
      flattenEnsures(es)
    ) match {
      case Nil    => None
      case x :: _ => Some[String](x)
    }

  def detectDeletePattern(es: List[expr_full], stateFields: List[String]): Option[String] =
    maps[expr_full, String](
      (a: expr_full) => deletePatternOf(stateFields, a),
      flattenEnsures(es)
    ) match {
      case Nil    => None
      case x :: _ => Some[String](x)
    }

  def preservedRelationOf(stateFields: List[String], x1: expr_full): List[String] =
    (stateFields, x1) match {
      case (
            stateFields,
            BinaryOpF(BEq(), PrimeF(IdentifierF(l, uu), uv), IdentifierF(r, uw), ux)
          ) => l == r && list_ex[String]((n: String) => n == l, stateFields) match {
          case true  => List(l)
          case false => Nil
        }
      case (uy, BinaryOpF(BAnd(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BOr(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BImplies(), va, vb, vc))                           => Nil
      case (uy, BinaryOpF(BIff(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BNeq(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BLt(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BGt(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BLe(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BGe(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BIn(), va, vb, vc))                                => Nil
      case (uy, BinaryOpF(BNotIn(), va, vb, vc))                             => Nil
      case (uy, BinaryOpF(BSubset(), va, vb, vc))                            => Nil
      case (uy, BinaryOpF(BUnion(), va, vb, vc))                             => Nil
      case (uy, BinaryOpF(BIntersect(), va, vb, vc))                         => Nil
      case (uy, BinaryOpF(BDiff(), va, vb, vc))                              => Nil
      case (uy, BinaryOpF(BAdd(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BSub(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BMul(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(BDiv(), va, vb, vc))                               => Nil
      case (uy, BinaryOpF(v, BinaryOpF(vd, ve, vf, vg), vb, vc))             => Nil
      case (uy, BinaryOpF(v, UnaryOpF(vd, ve, vf), vb, vc))                  => Nil
      case (uy, BinaryOpF(v, QuantifierF(vd, ve, vf, vg), vb, vc))           => Nil
      case (uy, BinaryOpF(v, SomeWrapF(vd, ve), vb, vc))                     => Nil
      case (uy, BinaryOpF(v, TheF(vd, ve, vf, vg), vb, vc))                  => Nil
      case (uy, BinaryOpF(v, FieldAccessF(vd, ve, vf), vb, vc))              => Nil
      case (uy, BinaryOpF(v, EnumAccessF(vd, ve, vf), vb, vc))               => Nil
      case (uy, BinaryOpF(v, IndexF(vd, ve, vf), vb, vc))                    => Nil
      case (uy, BinaryOpF(v, CallF(vd, ve, vf), vb, vc))                     => Nil
      case (uy, BinaryOpF(v, PrimeF(BinaryOpF(vf, vg, vh, vi), ve), vb, vc)) => Nil
      case (uy, BinaryOpF(v, PrimeF(UnaryOpF(vf, vg, vh), ve), vb, vc))      => Nil
      case (uy, BinaryOpF(v, PrimeF(QuantifierF(vf, vg, vh, vi), ve), vb, vc)) =>
        Nil
      case (uy, BinaryOpF(v, PrimeF(SomeWrapF(vf, vg), ve), vb, vc))                 => Nil
      case (uy, BinaryOpF(v, PrimeF(TheF(vf, vg, vh, vi), ve), vb, vc))              => Nil
      case (uy, BinaryOpF(v, PrimeF(FieldAccessF(vf, vg, vh), ve), vb, vc))          => Nil
      case (uy, BinaryOpF(v, PrimeF(EnumAccessF(vf, vg, vh), ve), vb, vc))           => Nil
      case (uy, BinaryOpF(v, PrimeF(IndexF(vf, vg, vh), ve), vb, vc))                => Nil
      case (uy, BinaryOpF(v, PrimeF(CallF(vf, vg, vh), ve), vb, vc))                 => Nil
      case (uy, BinaryOpF(v, PrimeF(PrimeF(vf, vg), ve), vb, vc))                    => Nil
      case (uy, BinaryOpF(v, PrimeF(PreF(vf, vg), ve), vb, vc))                      => Nil
      case (uy, BinaryOpF(v, PrimeF(WithF(vf, vg, vh), ve), vb, vc))                 => Nil
      case (uy, BinaryOpF(v, PrimeF(IfF(vf, vg, vh, vi), ve), vb, vc))               => Nil
      case (uy, BinaryOpF(v, PrimeF(LetF(vf, vg, vh, vi), ve), vb, vc))              => Nil
      case (uy, BinaryOpF(v, PrimeF(LambdaF(vf, vg, vh), ve), vb, vc))               => Nil
      case (uy, BinaryOpF(v, PrimeF(ConstructorF(vf, vg, vh), ve), vb, vc))          => Nil
      case (uy, BinaryOpF(v, PrimeF(SetLiteralF(vf, vg), ve), vb, vc))               => Nil
      case (uy, BinaryOpF(v, PrimeF(MapLiteralF(vf, vg), ve), vb, vc))               => Nil
      case (uy, BinaryOpF(v, PrimeF(SetComprehensionF(vf, vg, vh, vi), ve), vb, vc)) => Nil
      case (uy, BinaryOpF(v, PrimeF(SeqLiteralF(vf, vg), ve), vb, vc))               => Nil
      case (uy, BinaryOpF(v, PrimeF(MatchesF(vf, vg, vh), ve), vb, vc))              => Nil
      case (uy, BinaryOpF(v, PrimeF(IntLitF(vf, vg), ve), vb, vc))                   => Nil
      case (uy, BinaryOpF(v, PrimeF(FloatLitF(vf, vg), ve), vb, vc))                 => Nil
      case (uy, BinaryOpF(v, PrimeF(StringLitF(vf, vg), ve), vb, vc))                => Nil
      case (uy, BinaryOpF(v, PrimeF(BoolLitF(vf, vg), ve), vb, vc))                  => Nil
      case (uy, BinaryOpF(v, PrimeF(NoneLitF(vf), ve), vb, vc))                      => Nil
      case (uy, BinaryOpF(v, PreF(vd, ve), vb, vc))                                  => Nil
      case (uy, BinaryOpF(v, WithF(vd, ve, vf), vb, vc))                             => Nil
      case (uy, BinaryOpF(v, IfF(vd, ve, vf, vg), vb, vc))                           => Nil
      case (uy, BinaryOpF(v, LetF(vd, ve, vf, vg), vb, vc))                          => Nil
      case (uy, BinaryOpF(v, LambdaF(vd, ve, vf), vb, vc))                           => Nil
      case (uy, BinaryOpF(v, ConstructorF(vd, ve, vf), vb, vc))                      => Nil
      case (uy, BinaryOpF(v, SetLiteralF(vd, ve), vb, vc))                           => Nil
      case (uy, BinaryOpF(v, MapLiteralF(vd, ve), vb, vc))                           => Nil
      case (uy, BinaryOpF(v, SetComprehensionF(vd, ve, vf, vg), vb, vc))             => Nil
      case (uy, BinaryOpF(v, SeqLiteralF(vd, ve), vb, vc))                           => Nil
      case (uy, BinaryOpF(v, MatchesF(vd, ve, vf), vb, vc))                          => Nil
      case (uy, BinaryOpF(v, IntLitF(vd, ve), vb, vc))                               => Nil
      case (uy, BinaryOpF(v, FloatLitF(vd, ve), vb, vc))                             => Nil
      case (uy, BinaryOpF(v, StringLitF(vd, ve), vb, vc))                            => Nil
      case (uy, BinaryOpF(v, BoolLitF(vd, ve), vb, vc))                              => Nil
      case (uy, BinaryOpF(v, NoneLitF(vd), vb, vc))                                  => Nil
      case (uy, BinaryOpF(v, IdentifierF(vd, ve), vb, vc))                           => Nil
      case (uy, BinaryOpF(v, va, BinaryOpF(vd, ve, vf, vg), vc))                     => Nil
      case (uy, BinaryOpF(v, va, UnaryOpF(vd, ve, vf), vc))                          => Nil
      case (uy, BinaryOpF(v, va, QuantifierF(vd, ve, vf, vg), vc))                   => Nil
      case (uy, BinaryOpF(v, va, SomeWrapF(vd, ve), vc))                             => Nil
      case (uy, BinaryOpF(v, va, TheF(vd, ve, vf, vg), vc))                          => Nil
      case (uy, BinaryOpF(v, va, FieldAccessF(vd, ve, vf), vc))                      => Nil
      case (uy, BinaryOpF(v, va, EnumAccessF(vd, ve, vf), vc))                       => Nil
      case (uy, BinaryOpF(v, va, IndexF(vd, ve, vf), vc))                            => Nil
      case (uy, BinaryOpF(v, va, CallF(vd, ve, vf), vc))                             => Nil
      case (uy, BinaryOpF(v, va, PrimeF(vd, ve), vc))                                => Nil
      case (uy, BinaryOpF(v, va, PreF(vd, ve), vc))                                  => Nil
      case (uy, BinaryOpF(v, va, WithF(vd, ve, vf), vc))                             => Nil
      case (uy, BinaryOpF(v, va, IfF(vd, ve, vf, vg), vc))                           => Nil
      case (uy, BinaryOpF(v, va, LetF(vd, ve, vf, vg), vc))                          => Nil
      case (uy, BinaryOpF(v, va, LambdaF(vd, ve, vf), vc))                           => Nil
      case (uy, BinaryOpF(v, va, ConstructorF(vd, ve, vf), vc))                      => Nil
      case (uy, BinaryOpF(v, va, SetLiteralF(vd, ve), vc))                           => Nil
      case (uy, BinaryOpF(v, va, MapLiteralF(vd, ve), vc))                           => Nil
      case (uy, BinaryOpF(v, va, SetComprehensionF(vd, ve, vf, vg), vc))             => Nil
      case (uy, BinaryOpF(v, va, SeqLiteralF(vd, ve), vc))                           => Nil
      case (uy, BinaryOpF(v, va, MatchesF(vd, ve, vf), vc))                          => Nil
      case (uy, BinaryOpF(v, va, IntLitF(vd, ve), vc))                               => Nil
      case (uy, BinaryOpF(v, va, FloatLitF(vd, ve), vc))                             => Nil
      case (uy, BinaryOpF(v, va, StringLitF(vd, ve), vc))                            => Nil
      case (uy, BinaryOpF(v, va, BoolLitF(vd, ve), vc))                              => Nil
      case (uy, BinaryOpF(v, va, NoneLitF(vd), vc))                                  => Nil
      case (uy, UnaryOpF(v, va, vb))                                                 => Nil
      case (uy, QuantifierF(v, va, vb, vc))                                          => Nil
      case (uy, SomeWrapF(v, va))                                                    => Nil
      case (uy, TheF(v, va, vb, vc))                                                 => Nil
      case (uy, FieldAccessF(v, va, vb))                                             => Nil
      case (uy, EnumAccessF(v, va, vb))                                              => Nil
      case (uy, IndexF(v, va, vb))                                                   => Nil
      case (uy, CallF(v, va, vb))                                                    => Nil
      case (uy, PrimeF(v, va))                                                       => Nil
      case (uy, PreF(v, va))                                                         => Nil
      case (uy, WithF(v, va, vb))                                                    => Nil
      case (uy, IfF(v, va, vb, vc))                                                  => Nil
      case (uy, LetF(v, va, vb, vc))                                                 => Nil
      case (uy, LambdaF(v, va, vb))                                                  => Nil
      case (uy, ConstructorF(v, va, vb))                                             => Nil
      case (uy, SetLiteralF(v, va))                                                  => Nil
      case (uy, MapLiteralF(v, va))                                                  => Nil
      case (uy, SetComprehensionF(v, va, vb, vc))                                    => Nil
      case (uy, SeqLiteralF(v, va))                                                  => Nil
      case (uy, MatchesF(v, va, vb))                                                 => Nil
      case (uy, IntLitF(v, va))                                                      => Nil
      case (uy, FloatLitF(v, va))                                                    => Nil
      case (uy, StringLitF(v, va))                                                   => Nil
      case (uy, BoolLitF(v, va))                                                     => Nil
      case (uy, NoneLitF(v))                                                         => Nil
      case (uy, IdentifierF(v, va))                                                  => Nil
    }

  def state_fieldNameFull(x0: state_field_decl_full): String = x0 match {
    case StateFieldDeclFull(n, uu, uv) => n
  }

  def state_fieldTypeFull(x0: state_field_decl_full): type_expr_full = x0 match {
    case StateFieldDeclFull(uu, t, uv) => t
  }

  def serviceEnums(x0: service_ir_full): List[enum_decl_full] = x0 match {
    case ServiceIRFull(uu, uv, uw, en, ux, uy, uz, va, vb, vc, vd, ve, vf, vg, vh) => en
  }

  def typeExprToTy(x0: type_expr): Option[ty] = x0 match {
    case BoolT()           => Some[ty](TBool())
    case IntT()            => Some[ty](TInt())
    case EnumT(n)          => Some[ty](TEnum(n))
    case EntityT(n)        => Some[ty](TEntity(n))
    case RelationT(uu, uv) => None
  }

  def entityFieldDeclLookup(
      es: List[entity_decl_full],
      ename: String,
      fname: String
  ): Option[field_decl_full] =
    entityByName(es, ename) match {
      case None     => None
      case Some(ed) => findFieldDeclFull(entityFieldsFull(ed), fname)
    }

  def keyExistsInRequiresOf(stateFields: List[String], x1: expr_full): List[String] =
    (stateFields, x1) match {
      case (stateFields, BinaryOpF(BIn(), uu, IdentifierF(n, uv), uw)) =>
        list_ex[String]((s: String) => s == n, stateFields) match {
          case true  => List(n)
          case false => Nil
        }
      case (ux, BinaryOpF(BAnd(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(BOr(), va, vb, vc))                            => Nil
      case (ux, BinaryOpF(BImplies(), va, vb, vc))                       => Nil
      case (ux, BinaryOpF(BIff(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(BEq(), va, vb, vc))                            => Nil
      case (ux, BinaryOpF(BNeq(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(BLt(), va, vb, vc))                            => Nil
      case (ux, BinaryOpF(BGt(), va, vb, vc))                            => Nil
      case (ux, BinaryOpF(BLe(), va, vb, vc))                            => Nil
      case (ux, BinaryOpF(BGe(), va, vb, vc))                            => Nil
      case (ux, BinaryOpF(BNotIn(), va, vb, vc))                         => Nil
      case (ux, BinaryOpF(BSubset(), va, vb, vc))                        => Nil
      case (ux, BinaryOpF(BUnion(), va, vb, vc))                         => Nil
      case (ux, BinaryOpF(BIntersect(), va, vb, vc))                     => Nil
      case (ux, BinaryOpF(BDiff(), va, vb, vc))                          => Nil
      case (ux, BinaryOpF(BAdd(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(BSub(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(BMul(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(BDiv(), va, vb, vc))                           => Nil
      case (ux, BinaryOpF(v, va, BinaryOpF(vd, ve, vf, vg), vc))         => Nil
      case (ux, BinaryOpF(v, va, UnaryOpF(vd, ve, vf), vc))              => Nil
      case (ux, BinaryOpF(v, va, QuantifierF(vd, ve, vf, vg), vc))       => Nil
      case (ux, BinaryOpF(v, va, SomeWrapF(vd, ve), vc))                 => Nil
      case (ux, BinaryOpF(v, va, TheF(vd, ve, vf, vg), vc))              => Nil
      case (ux, BinaryOpF(v, va, FieldAccessF(vd, ve, vf), vc))          => Nil
      case (ux, BinaryOpF(v, va, EnumAccessF(vd, ve, vf), vc))           => Nil
      case (ux, BinaryOpF(v, va, IndexF(vd, ve, vf), vc))                => Nil
      case (ux, BinaryOpF(v, va, CallF(vd, ve, vf), vc))                 => Nil
      case (ux, BinaryOpF(v, va, PrimeF(vd, ve), vc))                    => Nil
      case (ux, BinaryOpF(v, va, PreF(vd, ve), vc))                      => Nil
      case (ux, BinaryOpF(v, va, WithF(vd, ve, vf), vc))                 => Nil
      case (ux, BinaryOpF(v, va, IfF(vd, ve, vf, vg), vc))               => Nil
      case (ux, BinaryOpF(v, va, LetF(vd, ve, vf, vg), vc))              => Nil
      case (ux, BinaryOpF(v, va, LambdaF(vd, ve, vf), vc))               => Nil
      case (ux, BinaryOpF(v, va, ConstructorF(vd, ve, vf), vc))          => Nil
      case (ux, BinaryOpF(v, va, SetLiteralF(vd, ve), vc))               => Nil
      case (ux, BinaryOpF(v, va, MapLiteralF(vd, ve), vc))               => Nil
      case (ux, BinaryOpF(v, va, SetComprehensionF(vd, ve, vf, vg), vc)) => Nil
      case (ux, BinaryOpF(v, va, SeqLiteralF(vd, ve), vc))               => Nil
      case (ux, BinaryOpF(v, va, MatchesF(vd, ve, vf), vc))              => Nil
      case (ux, BinaryOpF(v, va, IntLitF(vd, ve), vc))                   => Nil
      case (ux, BinaryOpF(v, va, FloatLitF(vd, ve), vc))                 => Nil
      case (ux, BinaryOpF(v, va, StringLitF(vd, ve), vc))                => Nil
      case (ux, BinaryOpF(v, va, BoolLitF(vd, ve), vc))                  => Nil
      case (ux, BinaryOpF(v, va, NoneLitF(vd), vc))                      => Nil
      case (ux, UnaryOpF(v, va, vb))                                     => Nil
      case (ux, QuantifierF(v, va, vb, vc))                              => Nil
      case (ux, SomeWrapF(v, va))                                        => Nil
      case (ux, TheF(v, va, vb, vc))                                     => Nil
      case (ux, FieldAccessF(v, va, vb))                                 => Nil
      case (ux, EnumAccessF(v, va, vb))                                  => Nil
      case (ux, IndexF(v, va, vb))                                       => Nil
      case (ux, CallF(v, va, vb))                                        => Nil
      case (ux, PrimeF(v, va))                                           => Nil
      case (ux, PreF(v, va))                                             => Nil
      case (ux, WithF(v, va, vb))                                        => Nil
      case (ux, IfF(v, va, vb, vc))                                      => Nil
      case (ux, LetF(v, va, vb, vc))                                     => Nil
      case (ux, LambdaF(v, va, vb))                                      => Nil
      case (ux, ConstructorF(v, va, vb))                                 => Nil
      case (ux, SetLiteralF(v, va))                                      => Nil
      case (ux, MapLiteralF(v, va))                                      => Nil
      case (ux, SetComprehensionF(v, va, vb, vc))                        => Nil
      case (ux, SeqLiteralF(v, va))                                      => Nil
      case (ux, MatchesF(v, va, vb))                                     => Nil
      case (ux, IntLitF(v, va))                                          => Nil
      case (ux, FloatLitF(v, va))                                        => Nil
      case (ux, StringLitF(v, va))                                       => Nil
      case (ux, BoolLitF(v, va))                                         => Nil
      case (ux, NoneLitF(v))                                             => Nil
      case (ux, IdentifierF(v, va))                                      => Nil
    }

  def referencesPreRelation(x0: expr_full, rel: String): Boolean = (x0, rel) match {
    case (PreF(IdentifierF(n, uu), uv), rel)               => n == rel
    case (IdentifierF(n, uw), rel)                         => n == rel
    case (BinaryOpF(v, va, vb, vc), uy)                    => false
    case (UnaryOpF(v, va, vb), uy)                         => false
    case (QuantifierF(v, va, vb, vc), uy)                  => false
    case (SomeWrapF(v, va), uy)                            => false
    case (TheF(v, va, vb, vc), uy)                         => false
    case (FieldAccessF(v, va, vb), uy)                     => false
    case (EnumAccessF(v, va, vb), uy)                      => false
    case (IndexF(v, va, vb), uy)                           => false
    case (CallF(v, va, vb), uy)                            => false
    case (PrimeF(v, va), uy)                               => false
    case (PreF(BinaryOpF(vb, vc, vd, ve), va), uy)         => false
    case (PreF(UnaryOpF(vb, vc, vd), va), uy)              => false
    case (PreF(QuantifierF(vb, vc, vd, ve), va), uy)       => false
    case (PreF(SomeWrapF(vb, vc), va), uy)                 => false
    case (PreF(TheF(vb, vc, vd, ve), va), uy)              => false
    case (PreF(FieldAccessF(vb, vc, vd), va), uy)          => false
    case (PreF(EnumAccessF(vb, vc, vd), va), uy)           => false
    case (PreF(IndexF(vb, vc, vd), va), uy)                => false
    case (PreF(CallF(vb, vc, vd), va), uy)                 => false
    case (PreF(PrimeF(vb, vc), va), uy)                    => false
    case (PreF(PreF(vb, vc), va), uy)                      => false
    case (PreF(WithF(vb, vc, vd), va), uy)                 => false
    case (PreF(IfF(vb, vc, vd, ve), va), uy)               => false
    case (PreF(LetF(vb, vc, vd, ve), va), uy)              => false
    case (PreF(LambdaF(vb, vc, vd), va), uy)               => false
    case (PreF(ConstructorF(vb, vc, vd), va), uy)          => false
    case (PreF(SetLiteralF(vb, vc), va), uy)               => false
    case (PreF(MapLiteralF(vb, vc), va), uy)               => false
    case (PreF(SetComprehensionF(vb, vc, vd, ve), va), uy) => false
    case (PreF(SeqLiteralF(vb, vc), va), uy)               => false
    case (PreF(MatchesF(vb, vc, vd), va), uy)              => false
    case (PreF(IntLitF(vb, vc), va), uy)                   => false
    case (PreF(FloatLitF(vb, vc), va), uy)                 => false
    case (PreF(StringLitF(vb, vc), va), uy)                => false
    case (PreF(BoolLitF(vb, vc), va), uy)                  => false
    case (PreF(NoneLitF(vb), va), uy)                      => false
    case (WithF(v, va, vb), uy)                            => false
    case (IfF(v, va, vb, vc), uy)                          => false
    case (LetF(v, va, vb, vc), uy)                         => false
    case (LambdaF(v, va, vb), uy)                          => false
    case (ConstructorF(v, va, vb), uy)                     => false
    case (SetLiteralF(v, va), uy)                          => false
    case (MapLiteralF(v, va), uy)                          => false
    case (SetComprehensionF(v, va, vb, vc), uy)            => false
    case (SeqLiteralF(v, va), uy)                          => false
    case (MatchesF(v, va, vb), uy)                         => false
    case (IntLitF(v, va), uy)                              => false
    case (FloatLitF(v, va), uy)                            => false
    case (StringLitF(v, va), uy)                           => false
    case (BoolLitF(v, va), uy)                             => false
    case (NoneLitF(v), uy)                                 => false
  }

  def tc_enums[A](x0: tyctx_ext[A]): List[String] = x0 match {
    case tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more) => tc_enums
  }

  def withInfoBaseIdentifier(x0: with_info_full): Option[String] = x0 match {
    case WithInfoFull(uu, b) => b
  }

  def tc_entities[A](x0: tyctx_ext[A]): List[entity_decl_full] = x0 match {
    case tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more) => tc_entities
  }

  def typeExprFullToTy(
      enums: List[String],
      entities: List[String],
      x2: type_expr_full
  ): Option[ty] =
    (enums, entities, x2) match {
      case (enums, entities, NamedTypeF(n, uu)) =>
        n == "Bool" match {
          case true => Some[ty](TBool())
          case false => n == "Int" match {
              case true => Some[ty](TInt())
              case false => member[String](enums, n) match {
                  case true => Some[ty](TEnum(n))
                  case false => member[String](entities, n) match {
                      case true  => Some[ty](TEntity(n))
                      case false => None
                    }
                }
            }
        }
      case (enums, entities, SetTypeF(inner, uv)) =>
        map_option[ty, ty]((a: ty) => TSet(a), typeExprFullToTy(enums, entities, inner))
      case (uw, ux, MapTypeF(v, va, vb))          => None
      case (uw, ux, SeqTypeF(v, va))              => None
      case (uw, ux, OptionTypeF(v, va))           => None
      case (uw, ux, RelationTypeF(v, va, vb, vc)) => None
    }

  def schemaFieldType(gamma: tyctx_ext[Unit], ename: String, fname: String): Option[ty] =
    find[entity_decl_full](
      (ed: entity_decl_full) =>
        entityNameFull(ed) == ename,
      tc_entities[Unit](gamma)
    ) match {
      case None => None
      case Some(ed) =>
        find[field_decl_full](
          (fd: field_decl_full) =>
            fieldNameFull(fd) == fname,
          entityFieldsFull(ed)
        ) match {
          case None => None
          case Some(fd) =>
            typeExprFullToTy(
              tc_enums[Unit](gamma),
              map[entity_decl_full, String](
                (a: entity_decl_full) =>
                  entityNameFull(a),
                tc_entities[Unit](gamma)
              ),
              fieldTypeFull(fd)
            )
        }
    }

  def serviceEntities(x0: service_ir_full): List[entity_decl_full] = x0 match {
    case ServiceIRFull(uu, uv, es, uw, ux, uy, uz, va, vb, vc, vd, ve, vf, vg, vh) => es
  }

  def collectFieldAccessNames(e: expr_full): List[String] =
    remdups[String](fst[List[String], List[with_info_full]](snd[
      List[String],
      (List[String], List[with_info_full])
    ](collectExprInfo(e))))

  def tc_relations_update[A](
      tc_relationsa: (List[state_field_decl_full]) => List[state_field_decl_full],
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
      tc_entitiesa: (List[entity_decl_full]) => List[entity_decl_full],
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

  def serviceStateFields(x0: service_ir_full): List[state_field_decl_full] = x0 match {
    case ServiceIRFull(uu, uv, uw, ux, uy, st, uz, va, vb, vc, vd, ve, vf, vg, vh) => st match {
        case None                       => Nil
        case Some(StateDeclFull(fs, _)) => fs
      }
  }

  def tyctxFromService(ir: service_ir_full): tyctx_ext[Unit] =
    tc_relations_update[Unit](
      (_: List[state_field_decl_full]) =>
        serviceStateFields(ir),
      tc_enums_update[Unit](
        (_: List[String]) =>
          map[enum_decl_full, String]((a: enum_decl_full) => enumNameFull(a), serviceEnums(ir)),
        tc_entities_update[Unit](
          (_: List[entity_decl_full]) =>
            serviceEntities(ir),
          tyctxEmpty
        )
      )
    )

  def collectPrimedIdentifiers(es: List[expr_full]): List[String] =
    remdups[String](
      fst[List[String], (List[String], List[with_info_full])](collectExprInfo_list(es))
    )

  def referencesPrimedRelation(x0: expr_full, rel: String): Boolean = (x0, rel) match {
    case (PrimeF(IdentifierF(n, uu), uv), rel)               => n == rel
    case (BinaryOpF(v, va, vb, vc), ux)                      => false
    case (UnaryOpF(v, va, vb), ux)                           => false
    case (QuantifierF(v, va, vb, vc), ux)                    => false
    case (SomeWrapF(v, va), ux)                              => false
    case (TheF(v, va, vb, vc), ux)                           => false
    case (FieldAccessF(v, va, vb), ux)                       => false
    case (EnumAccessF(v, va, vb), ux)                        => false
    case (IndexF(v, va, vb), ux)                             => false
    case (CallF(v, va, vb), ux)                              => false
    case (PrimeF(BinaryOpF(vb, vc, vd, ve), va), ux)         => false
    case (PrimeF(UnaryOpF(vb, vc, vd), va), ux)              => false
    case (PrimeF(QuantifierF(vb, vc, vd, ve), va), ux)       => false
    case (PrimeF(SomeWrapF(vb, vc), va), ux)                 => false
    case (PrimeF(TheF(vb, vc, vd, ve), va), ux)              => false
    case (PrimeF(FieldAccessF(vb, vc, vd), va), ux)          => false
    case (PrimeF(EnumAccessF(vb, vc, vd), va), ux)           => false
    case (PrimeF(IndexF(vb, vc, vd), va), ux)                => false
    case (PrimeF(CallF(vb, vc, vd), va), ux)                 => false
    case (PrimeF(PrimeF(vb, vc), va), ux)                    => false
    case (PrimeF(PreF(vb, vc), va), ux)                      => false
    case (PrimeF(WithF(vb, vc, vd), va), ux)                 => false
    case (PrimeF(IfF(vb, vc, vd, ve), va), ux)               => false
    case (PrimeF(LetF(vb, vc, vd, ve), va), ux)              => false
    case (PrimeF(LambdaF(vb, vc, vd), va), ux)               => false
    case (PrimeF(ConstructorF(vb, vc, vd), va), ux)          => false
    case (PrimeF(SetLiteralF(vb, vc), va), ux)               => false
    case (PrimeF(MapLiteralF(vb, vc), va), ux)               => false
    case (PrimeF(SetComprehensionF(vb, vc, vd, ve), va), ux) => false
    case (PrimeF(SeqLiteralF(vb, vc), va), ux)               => false
    case (PrimeF(MatchesF(vb, vc, vd), va), ux)              => false
    case (PrimeF(IntLitF(vb, vc), va), ux)                   => false
    case (PrimeF(FloatLitF(vb, vc), va), ux)                 => false
    case (PrimeF(StringLitF(vb, vc), va), ux)                => false
    case (PrimeF(BoolLitF(vb, vc), va), ux)                  => false
    case (PrimeF(NoneLitF(vb), va), ux)                      => false
    case (PreF(v, va), ux)                                   => false
    case (WithF(v, va, vb), ux)                              => false
    case (IfF(v, va, vb, vc), ux)                            => false
    case (LetF(v, va, vb, vc), ux)                           => false
    case (LambdaF(v, va, vb), ux)                            => false
    case (ConstructorF(v, va, vb), ux)                       => false
    case (SetLiteralF(v, va), ux)                            => false
    case (MapLiteralF(v, va), ux)                            => false
    case (SetComprehensionF(v, va, vb, vc), ux)              => false
    case (SeqLiteralF(v, va), ux)                            => false
    case (MatchesF(v, va, vb), ux)                           => false
    case (IntLitF(v, va), ux)                                => false
    case (FloatLitF(v, va), ux)                              => false
    case (StringLitF(v, va), ux)                             => false
    case (BoolLitF(v, va), ux)                               => false
    case (NoneLitF(v), ux)                                   => false
    case (IdentifierF(v, va), ux)                            => false
  }

  def relationTargetEntityName(x0: type_expr_full): Option[String] = x0 match {
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

  def collectPreservedRelations(es: List[expr_full], stateFields: List[String]): List[String] =
    remdups[String](maps[expr_full, String](
      (a: expr_full) =>
        preservedRelationOf(stateFields, a),
      flattenEnsures(es)
    ))

  def detectKeyExistsInRequires(
      requiresa: List[expr_full],
      stateFields: List[String]
  ): List[String] =
    remdups[String](maps[expr_full, String](
      (a: expr_full) =>
        keyExistsInRequiresOf(stateFields, a),
      flattenEnsures(requiresa)
    ))

  def equal_ty(x0: ty, x1: ty): Boolean = (x0, x1) match {
    case (TEntity(x4), TSet(x5))    => false
    case (TSet(x5), TEntity(x4))    => false
    case (TEnum(x3), TSet(x5))      => false
    case (TSet(x5), TEnum(x3))      => false
    case (TEnum(x3), TEntity(x4))   => false
    case (TEntity(x4), TEnum(x3))   => false
    case (TInt(), TSet(x5))         => false
    case (TSet(x5), TInt())         => false
    case (TInt(), TEntity(x4))      => false
    case (TEntity(x4), TInt())      => false
    case (TInt(), TEnum(x3))        => false
    case (TEnum(x3), TInt())        => false
    case (TBool(), TSet(x5))        => false
    case (TSet(x5), TBool())        => false
    case (TBool(), TEntity(x4))     => false
    case (TEntity(x4), TBool())     => false
    case (TBool(), TEnum(x3))       => false
    case (TEnum(x3), TBool())       => false
    case (TBool(), TInt())          => false
    case (TInt(), TBool())          => false
    case (TSet(x5), TSet(y5))       => equal_ty(x5, y5)
    case (TEntity(x4), TEntity(y4)) => x4 == y4
    case (TEnum(x3), TEnum(y3))     => x3 == y3
    case (TInt(), TInt())           => true
    case (TBool(), TBool())         => true
  }

  def check_value_has_ty_list(vt: tyctx_ext[Unit], x1: List[ir_value], vu: ty): Boolean =
    (vt, x1, vu) match {
      case (vt, Nil, vu) => true
      case (gamma, v :: vs, t) =>
        check_value_has_ty(gamma, v, t) && check_value_has_ty_list(gamma, vs, t)
    }

  def check_value_has_ty(gamma: tyctx_ext[Unit], x1: ir_value, t: ty): Boolean =
    (gamma, x1, t) match {
      case (gamma, VBool(uu), t)          => equal_ty(t, TBool())
      case (gamma, VInt(uv), t)           => equal_ty(t, TInt())
      case (gamma, VEnum(ename, uw), t)   => equal_ty(t, TEnum(ename))
      case (gamma, VEntity(ename, ux), t) => equal_ty(t, TEntity(ename))
      case (gamma, VSet(vs), TSet(t))     => check_value_has_ty_list(gamma, vs, t)
      case (gamma, VSet(uy), TBool())     => false
      case (gamma, VSet(uz), TInt())      => false
      case (gamma, VSet(va), TEnum(vb))   => false
      case (gamma, VSet(vc), TEntity(vd)) => false
      case (gamma, VEntityWith(base, fld, overridea), TEntity(ename)) =>
        check_value_has_ty(gamma, base, TEntity(ename)) &&
        (schemaFieldType(gamma, ename, fld) match {
          case None    => false
          case Some(a) => check_value_has_ty(gamma, overridea, a)
        })
      case (gamma, VEntityWith(ve, vf, vg), TBool())   => false
      case (gamma, VEntityWith(vh, vi, vj), TInt())    => false
      case (gamma, VEntityWith(vk, vl, vm), TEnum(vn)) => false
      case (gamma, VEntityWith(vo, vp, vq), TSet(vr))  => false
    }

  def tc_relations[A](x0: tyctx_ext[A]): List[state_field_decl_full] = x0 match {
    case tyctx_exta(tc_env, tc_schema, tc_entities, tc_relations, tc_enums, more) => tc_relations
  }

  def peelRelationRefFull(x0: expr_full): Option[String] = x0 match {
    case IdentifierF(rel, uu)                          => Some[String](rel)
    case PreF(IdentifierF(rel, uv), uw)                => Some[String](rel)
    case PrimeF(IdentifierF(rel, ux), uy)              => Some[String](rel)
    case BinaryOpF(v, va, vb, vc)                      => None
    case UnaryOpF(v, va, vb)                           => None
    case QuantifierF(v, va, vb, vc)                    => None
    case SomeWrapF(v, va)                              => None
    case TheF(v, va, vb, vc)                           => None
    case FieldAccessF(v, va, vb)                       => None
    case EnumAccessF(v, va, vb)                        => None
    case IndexF(v, va, vb)                             => None
    case CallF(v, va, vb)                              => None
    case PrimeF(BinaryOpF(vb, vc, vd, ve), va)         => None
    case PrimeF(UnaryOpF(vb, vc, vd), va)              => None
    case PrimeF(QuantifierF(vb, vc, vd, ve), va)       => None
    case PrimeF(SomeWrapF(vb, vc), va)                 => None
    case PrimeF(TheF(vb, vc, vd, ve), va)              => None
    case PrimeF(FieldAccessF(vb, vc, vd), va)          => None
    case PrimeF(EnumAccessF(vb, vc, vd), va)           => None
    case PrimeF(IndexF(vb, vc, vd), va)                => None
    case PrimeF(CallF(vb, vc, vd), va)                 => None
    case PrimeF(PrimeF(vb, vc), va)                    => None
    case PrimeF(PreF(vb, vc), va)                      => None
    case PrimeF(WithF(vb, vc, vd), va)                 => None
    case PrimeF(IfF(vb, vc, vd, ve), va)               => None
    case PrimeF(LetF(vb, vc, vd, ve), va)              => None
    case PrimeF(LambdaF(vb, vc, vd), va)               => None
    case PrimeF(ConstructorF(vb, vc, vd), va)          => None
    case PrimeF(SetLiteralF(vb, vc), va)               => None
    case PrimeF(MapLiteralF(vb, vc), va)               => None
    case PrimeF(SetComprehensionF(vb, vc, vd, ve), va) => None
    case PrimeF(SeqLiteralF(vb, vc), va)               => None
    case PrimeF(MatchesF(vb, vc, vd), va)              => None
    case PrimeF(IntLitF(vb, vc), va)                   => None
    case PrimeF(FloatLitF(vb, vc), va)                 => None
    case PrimeF(StringLitF(vb, vc), va)                => None
    case PrimeF(BoolLitF(vb, vc), va)                  => None
    case PrimeF(NoneLitF(vb), va)                      => None
    case PreF(BinaryOpF(vb, vc, vd, ve), va)           => None
    case PreF(UnaryOpF(vb, vc, vd), va)                => None
    case PreF(QuantifierF(vb, vc, vd, ve), va)         => None
    case PreF(SomeWrapF(vb, vc), va)                   => None
    case PreF(TheF(vb, vc, vd, ve), va)                => None
    case PreF(FieldAccessF(vb, vc, vd), va)            => None
    case PreF(EnumAccessF(vb, vc, vd), va)             => None
    case PreF(IndexF(vb, vc, vd), va)                  => None
    case PreF(CallF(vb, vc, vd), va)                   => None
    case PreF(PrimeF(vb, vc), va)                      => None
    case PreF(PreF(vb, vc), va)                        => None
    case PreF(WithF(vb, vc, vd), va)                   => None
    case PreF(IfF(vb, vc, vd, ve), va)                 => None
    case PreF(LetF(vb, vc, vd, ve), va)                => None
    case PreF(LambdaF(vb, vc, vd), va)                 => None
    case PreF(ConstructorF(vb, vc, vd), va)            => None
    case PreF(SetLiteralF(vb, vc), va)                 => None
    case PreF(MapLiteralF(vb, vc), va)                 => None
    case PreF(SetComprehensionF(vb, vc, vd, ve), va)   => None
    case PreF(SeqLiteralF(vb, vc), va)                 => None
    case PreF(MatchesF(vb, vc, vd), va)                => None
    case PreF(IntLitF(vb, vc), va)                     => None
    case PreF(FloatLitF(vb, vc), va)                   => None
    case PreF(StringLitF(vb, vc), va)                  => None
    case PreF(BoolLitF(vb, vc), va)                    => None
    case PreF(NoneLitF(vb), va)                        => None
    case WithF(v, va, vb)                              => None
    case IfF(v, va, vb, vc)                            => None
    case LetF(v, va, vb, vc)                           => None
    case LambdaF(v, va, vb)                            => None
    case ConstructorF(v, va, vb)                       => None
    case SetLiteralF(v, va)                            => None
    case MapLiteralF(v, va)                            => None
    case SetComprehensionF(v, va, vb, vc)              => None
    case SeqLiteralF(v, va)                            => None
    case MatchesF(v, va, vb)                           => None
    case IntLitF(v, va)                                => None
    case FloatLitF(v, va)                              => None
    case StringLitF(v, va)                             => None
    case BoolLitF(v, va)                               => None
    case NoneLitF(v)                                   => None
  }

  def schemaRelationValueType(gamma: tyctx_ext[Unit], rel_name: String): Option[ty] =
    find[state_field_decl_full](
      (sf: state_field_decl_full) =>
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
              map[entity_decl_full, String](
                (a: entity_decl_full) =>
                  entityNameFull(a),
                tc_entities[Unit](gamma)
              ),
              v
            )
        }
    }

} /* object SpecRestGenerated */
