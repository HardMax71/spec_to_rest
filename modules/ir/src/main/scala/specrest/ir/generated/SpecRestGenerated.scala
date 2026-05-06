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
  final case class IndexRel(a: String, b: expr, c: Option[span_t])    extends expr
  final case class FieldAccess(a: expr, b: String, c: Option[span_t]) extends expr
  final case class SetEmpty(a: Option[span_t])                        extends expr
  final case class SetInsert(a: expr, b: expr, c: Option[span_t])     extends expr
  final case class SetMember(a: expr, b: expr, c: Option[span_t])     extends expr
  final case class SetBin(a: set_op, b: expr, c: expr, d: Option[span_t])
      extends expr
  final case class WithRec(a: expr, b: String, c: expr, d: Option[span_t])
      extends expr

  sealed abstract class nat
  final case class Nat(a: BigInt) extends nat

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
  final case class TIndexRel(a: String, b: smt_term)              extends smt_term
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
    case Nat(x) => x
  }

  def plus_nat(m: nat, n: nat): nat =
    Nat(integer_of_nat(m) + integer_of_nat(n))

  def one_nat: nat = Nat(BigInt(1))

  def Suc(n: nat): nat = plus_nat(n, one_nat)

  def map_option[A, B](f: A => B, x1: Option[A]): Option[B] = (f, x1) match {
    case (f, None)     => None
    case (f, Some(x2)) => Some[B](f(x2))
  }

  def string_in_list(uu: String, x1: List[String]): Boolean = (uu, x1) match {
    case (uu, Nil)    => false
    case (y, x :: xs) => x == y || string_in_list(y, xs)
  }

  def lower_forall_step(
      enums: List[String],
      x1: quantifier_binding_full,
      body: expr,
      sp: Option[span_t]
  ): Option[expr] =
    (enums, x1, body, sp) match {
      case (enums, QuantifierBindingFull(v, IdentifierF(dnm, uu), BkIn(), uv), body, sp) =>
        string_in_list(dnm, enums) match {
          case true  => Some[expr](ForallEnum(v, dnm, body, sp))
          case false => Some[expr](ForallRel(v, dnm, body, sp))
        }
      case (uw, QuantifierBindingFull(v, BinaryOpF(vd, ve, vf, vg), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, UnaryOpF(vd, ve, vf), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, QuantifierF(vd, ve, vf, vg), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, SomeWrapF(vd, ve), vb, vc), uy, uz)           => None
      case (uw, QuantifierBindingFull(v, TheF(vd, ve, vf, vg), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, FieldAccessF(vd, ve, vf), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, EnumAccessF(vd, ve, vf), vb, vc), uy, uz)  => None
      case (uw, QuantifierBindingFull(v, IndexF(vd, ve, vf), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, CallF(vd, ve, vf), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, PrimeF(vd, ve), vb, vc), uy, uz)    => None
      case (uw, QuantifierBindingFull(v, PreF(vd, ve), vb, vc), uy, uz)      => None
      case (uw, QuantifierBindingFull(v, WithF(vd, ve, vf), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, IfF(vd, ve, vf, vg), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, LetF(vd, ve, vf, vg), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, LambdaF(vd, ve, vf), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, ConstructorF(vd, ve, vf), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, SetLiteralF(vd, ve), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, MapLiteralF(vd, ve), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, SetComprehensionF(vd, ve, vf, vg), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, SeqLiteralF(vd, ve), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, MatchesF(vd, ve, vf), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, IntLitF(vd, ve), vb, vc), uy, uz)   => None
      case (uw, QuantifierBindingFull(v, FloatLitF(vd, ve), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, StringLitF(vd, ve), vb, vc), uy, uz) =>
        None
      case (uw, QuantifierBindingFull(v, BoolLitF(vd, ve), vb, vc), uy, uz) => None
      case (uw, QuantifierBindingFull(v, NoneLitF(vd), vb, vc), uy, uz)     => None
      case (uw, QuantifierBindingFull(v, va, BkColon(), vc), uy, uz)        => None
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

  def lower_set_list(wu: List[String], x1: List[expr_full], sp: Option[span_t]): Option[expr] =
    (wu, x1, sp) match {
      case (wu, Nil, sp) => Some[expr](SetEmpty(sp))
      case (enums, e :: rest, sp) =>
        (lower(enums, e), lower_set_list(enums, rest, sp)) match {
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
        case IdentifierF(rel, _) =>
          map_option[expr, expr]((k: expr) => IndexRel(rel, k, sp), lower(enums, key))
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
    case (enums, SetLiteralF(elems, sp)) => lower_set_list(enums, elems, sp)
  }

  def find[A](uu: A => Boolean, x1: List[A]): Option[A] = (uu, x1) match {
    case (uu, Nil) => None
    case (p, x :: xs) =>
      p(x) match {
        case true  => Some[A](x)
        case false => find[A](p, xs)
      }
  }

  def map_of[A: equal, B](x0: List[(A, B)], k: A): Option[B] = (x0, k) match {
    case (Nil, k) => None
    case ((l, v) :: ps, k) =>
      eq[A](l, k) match {
        case true  => Some[B](v)
        case false => map_of[A, B](ps, k)
      }
  }

  def filter[A](p: A => Boolean, x1: List[A]): List[A] = (p, x1) match {
    case (p, Nil) => Nil
    case (p, x :: xs) =>
      p(x) match {
        case true  => x :: filter[A](p, xs)
        case false => filter[A](p, xs)
      }
  }

  def member[A: equal](x0: List[A], y: A): Boolean = (x0, y) match {
    case (Nil, y)     => false
    case (x :: xs, y) => eq[A](x, y) || member[A](xs, y)
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

  def zero_nat: nat = Nat(BigInt(0))

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

  def contains_smt_val(x0: List[smt_val], uu: smt_val): Boolean = (x0, uu) match {
    case (Nil, uu)    => false
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

  def set_diff_smt_vals(l: List[smt_val], r: List[smt_val]): List[smt_val] =
    dedupe_smt_vals(filter[smt_val]((v: smt_val) => !contains_smt_val(r, v), l))

  def smt_env_lookup(env: List[(String, smt_val)], name: String): Option[smt_val] =
    map_of[String, smt_val](env, name)

  def smt_eval_forall_enum(
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
        smt_eval(m, (vara, SEnumElem(sort_name, mem)) :: env, body) match {
          case None => None
          case Some(SBool(b)) =>
            smt_eval_forall_enum(m, env, vara, sort_name, rest, body) match {
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

  def smt_eval_forall_rel(
      m: smt_model_ext[Unit],
      env: List[(String, smt_val)],
      vara: String,
      x3: List[smt_val],
      body: smt_term
  ): Option[smt_val] =
    (m, env, vara, x3, body) match {
      case (m, env, vara, Nil, body) => Some[smt_val](SBool(true))
      case (m, env, vara, v :: rest, body) =>
        smt_eval(m, (vara, v) :: env, body) match {
          case None => None
          case Some(SBool(b)) =>
            smt_eval_forall_rel(m, env, vara, rest, body) match {
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

  def smt_eval(
      m: smt_model_ext[Unit],
      env: List[(String, smt_val)],
      x2: smt_term
  ): Option[smt_val] =
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
      case (m, env, TNot(t)) => smt_eval(m, env, t) match {
          case None                       => None
          case Some(SBool(b))             => Some[smt_val](SBool(!b))
          case Some(SInt(_))              => None
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
        }
      case (m, env, TAnd(l, r)) =>
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
          case (None, _)          => None
          case (Some(_), None)    => None
          case (Some(a), Some(b)) => Some[smt_val](SBool(equal_smt_vala(a, b)))
        }
      case (m, env, TLt(l, r)) =>
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        smt_eval(m, env, t) match {
          case None                       => None
          case Some(SBool(_))             => None
          case Some(SInt(n))              => Some[smt_val](SInt(uminus_int(n)))
          case Some(SEnumElem(_, _))      => None
          case Some(SEntityElem(_, _))    => None
          case Some(SSet(_))              => None
          case Some(SEntityWith(_, _, _)) => None
        }
      case (m, env, TAdd(l, r)) =>
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        smt_eval(m, env, arg) match {
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
        smt_eval(m, env, v) match {
          case None     => None
          case Some(va) => smt_eval(m, (x, va) :: env, body)
        }
      case (m, env, TForallEnum(vara, sort_name, body)) =>
        smt_model_lookup_sort_members(m, sort_name) match {
          case None => None
          case Some(members) =>
            smt_eval_forall_enum(m, env, vara, sort_name, members, body)
        }
      case (m, env, TForallRel(vara, rel_name, body)) =>
        smt_model_lookup_rel(m, rel_name) match {
          case None    => None
          case Some(d) => smt_eval_forall_rel(m, env, vara, d, body)
        }
      case (m, env, TIndexRel(rel_name, key)) =>
        smt_eval(m, env, key) match {
          case None    => None
          case Some(a) => smt_model_lookup_key(m, rel_name, a)
        }
      case (m, env, TFieldAccess(base, fname)) =>
        smt_eval(m, env, base) match {
          case None    => None
          case Some(v) => smt_val_field_lookup(m, v, fname)
        }
      case (m, env, TSetEmpty()) => Some[smt_val](SSet(Nil))
      case (m, env, TSetInsert(elem, set_t)) =>
        (smt_eval(m, env, elem), smt_eval(m, env, set_t)) match {
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
        (smt_eval(m, env, elem), smt_eval(m, env, set_t)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
        (smt_eval(m, env, l), smt_eval(m, env, r)) match {
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
      case (m, env, TPrime(t)) => smt_eval(m, env, t)
      case (m, env, TPre(t))   => smt_eval(m, env, t)
      case (m, env, TWithRec(base, fld, value_t)) =>
        (smt_eval(m, env, base), smt_eval(m, env, value_t)) match {
          case (None, _)           => None
          case (Some(_), None)     => None
          case (Some(bv), Some(v)) => Some[smt_val](SEntityWith(bv, fld, v))
        }
    }

  def is_lit_full(x0: expr_full): Boolean = x0 match {
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

  def contains_value(x0: List[ir_value], uu: ir_value): Boolean = (x0, uu) match {
    case (Nil, uu)    => false
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
      case (s, st, env, IndexRel(rel_name, key, vk)) =>
        eval(s, st, env, key) match {
          case None    => None
          case Some(a) => state_lookup_key(st, rel_name, a)
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
    case IndexRel(rel_name, key, vv)  => TIndexRel(rel_name, translate(key))
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

  def empty_service_ir_full(nm: String): service_ir_full =
    ServiceIRFull(nm, Nil, Nil, Nil, Nil, None, Nil, Nil, Nil, Nil, Nil, Nil, Nil, None, None)

} /* object SpecRestGenerated */
