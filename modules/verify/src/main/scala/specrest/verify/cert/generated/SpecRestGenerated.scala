package specrest.verify.cert.generated

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
  final case class BoolLit(a: Boolean)                       extends expr
  final case class IntLit(a: int)                            extends expr
  final case class Ident(a: String)                          extends expr
  final case class UnNot(a: expr)                            extends expr
  final case class UnNeg(a: expr)                            extends expr
  final case class BoolBin(a: bool_bin_op, b: expr, c: expr) extends expr
  final case class Arith(a: arith_op, b: expr, c: expr)      extends expr
  final case class Cmp(a: cmp_op, b: expr, c: expr)          extends expr
  final case class LetIn(a: String, b: expr, c: expr)        extends expr
  final case class EnumAccess(a: String, b: String)          extends expr
  final case class Member(a: expr, b: String)                extends expr
  final case class ForallEnum(a: String, b: String, c: expr) extends expr
  final case class ForallRel(a: String, b: String, c: expr)  extends expr
  final case class Prime(a: expr)                            extends expr
  final case class Pre(a: expr)                              extends expr
  final case class CardRel(a: String)                        extends expr
  final case class IndexRel(a: String, b: expr)              extends expr
  final case class FieldAccess(a: expr, b: String)           extends expr
  final case class SetEmpty()                                extends expr
  final case class SetInsert(a: expr, b: expr)               extends expr
  final case class SetMember(a: expr, b: expr)               extends expr
  final case class SetBin(a: set_op, b: expr, c: expr)       extends expr
  final case class WithRec(a: expr, b: String, c: expr)      extends expr

  sealed abstract class nat
  final case class Nat(a: BigInt) extends nat

  sealed abstract class num
  final case class One()        extends num
  final case class Bit0(a: num) extends num
  final case class Bit1(a: num) extends num

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

  sealed abstract class state_ext[A]
  final case class state_exta[A](
      a: List[(String, ir_value)],
      b: List[(String, List[ir_value])],
      c: List[(String, List[(ir_value, ir_value)])],
      d: List[(String, List[(String, ir_value)])],
      e: A
  ) extends state_ext[A]

  sealed abstract class enum_decl_ext[A]
  final case class enum_decl_exta[A](a: String, b: List[String], c: A) extends enum_decl_ext[A]

  sealed abstract class field_decl_ext[A]
  final case class field_decl_exta[A](a: String, b: type_expr, c: A) extends field_decl_ext[A]

  sealed abstract class entity_decl_ext[A]
  final case class entity_decl_exta[A](a: String, b: List[field_decl_ext[Unit]], c: A)
      extends entity_decl_ext[A]

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

  def map_option[A, B](f: A => B, x1: Option[A]): Option[B] = (f, x1) match {
    case (f, None)     => None
    case (f, Some(x2)) => Some[B](f(x2))
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
    case enum_decl_exta(enm_name, enm_members, more) => enm_name
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
    case enum_decl_exta(enm_name, enm_members, more) => enm_members
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
      case (s, st, env, BoolLit(b)) => Some[ir_value](VBool(b))
      case (s, st, env, IntLit(n))  => Some[ir_value](VInt(n))
      case (s, st, env, Ident(x)) => env_lookup(env, x) match {
          case None    => state_lookup_scalar(st, x)
          case Some(a) => Some[ir_value](a)
        }
      case (s, st, env, UnNot(e)) =>
        eval(s, st, env, e) match {
          case None                       => None
          case Some(VBool(b))             => Some[ir_value](VBool(!b))
          case Some(VInt(_))              => None
          case Some(VEnum(_, _))          => None
          case Some(VEntity(_, _))        => None
          case Some(VSet(_))              => None
          case Some(VEntityWith(_, _, _)) => None
        }
      case (s, st, env, UnNeg(e)) =>
        eval(s, st, env, e) match {
          case None                       => None
          case Some(VBool(_))             => None
          case Some(VInt(n))              => Some[ir_value](VInt(uminus_int(n)))
          case Some(VEnum(_, _))          => None
          case Some(VEntity(_, _))        => None
          case Some(VSet(_))              => None
          case Some(VEntityWith(_, _, _)) => None
        }
      case (s, st, env, BoolBin(op, l, r)) =>
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
      case (s, st, env, Arith(op, l, r)) =>
        eval_arith(op, eval(s, st, env, l), eval(s, st, env, r))
      case (s, st, env, Cmp(op, l, r)) =>
        eval_cmp(op, eval(s, st, env, l), eval(s, st, env, r))
      case (s, st, env, LetIn(x, v, body)) =>
        eval(s, st, env, v) match {
          case None     => None
          case Some(va) => eval(s, st, (x, va) :: env, body)
        }
      case (s, st, env, EnumAccess(en, mem)) =>
        schema_lookup_enum(s, en) match {
          case None => None
          case Some(d) =>
            member[String](enm_members[Unit](d), mem) match {
              case true  => Some[ir_value](VEnum(en, mem))
              case false => None
            }
        }
      case (s, st, env, Member(elem, rel_name)) =>
        eval(s, st, env, elem) match {
          case None => None
          case Some(v) =>
            state_relation_domain(st, rel_name) match {
              case None => None
              case Some(rel_dom) =>
                Some[ir_value](VBool(contains_value(rel_dom, v)))
            }
        }
      case (s, st, env, ForallEnum(vara, en, body)) =>
        schema_lookup_enum(s, en) match {
          case None => None
          case Some(d) =>
            eval_forall_enum(s, st, env, vara, en, enm_members[Unit](d), body)
        }
      case (s, st, env, ForallRel(vara, rel_name, body)) =>
        state_relation_domain(st, rel_name) match {
          case None          => None
          case Some(rel_dom) => eval_forall_rel(s, st, env, vara, rel_dom, body)
        }
      case (s, st, env, Prime(e)) => eval(s, st, env, e)
      case (s, st, env, Pre(e))   => eval(s, st, env, e)
      case (s, st, env, CardRel(rel_name)) =>
        state_relation_domain(st, rel_name) match {
          case None => None
          case Some(rel_dom) =>
            Some[ir_value](VInt(int_of_nat(size_list[ir_value](rel_dom))))
        }
      case (s, st, env, IndexRel(rel_name, key)) =>
        eval(s, st, env, key) match {
          case None    => None
          case Some(a) => state_lookup_key(st, rel_name, a)
        }
      case (s, st, env, FieldAccess(base, fname)) =>
        eval(s, st, env, base) match {
          case None    => None
          case Some(v) => value_field_lookup(st, v, fname)
        }
      case (s, st, env, SetEmpty()) => Some[ir_value](VSet(Nil))
      case (s, st, env, SetInsert(elem, set_e)) =>
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
      case (s, st, env, SetMember(elem, set_e)) =>
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
      case (s, st, env, SetBin(op, l, r)) =>
        eval_set_bin(op, eval(s, st, env, l), eval(s, st, env, r))
      case (s, st, env, WithRec(base, fld, value_e)) =>
        (eval(s, st, env, base), eval(s, st, env, value_e)) match {
          case (None, _)           => None
          case (Some(_), None)     => None
          case (Some(bv), Some(v)) => Some[ir_value](VEntityWith(bv, fld, v))
        }
    }

  def translate(x0: expr): smt_term = x0 match {
    case BoolLit(b)                 => BLit(b)
    case IntLit(n)                  => ILit(n)
    case Ident(x)                   => TVar(x)
    case UnNot(e)                   => TNot(translate(e))
    case UnNeg(e)                   => TNeg(translate(e))
    case BoolBin(AndOp(), l, r)     => TAnd(translate(l), translate(r))
    case BoolBin(OrOp(), l, r)      => TOr(translate(l), translate(r))
    case BoolBin(ImpliesOp(), l, r) => TImplies(translate(l), translate(r))
    case BoolBin(IffOp(), l, r) =>
      TAnd(TImplies(translate(l), translate(r)), TImplies(translate(r), translate(l)))
    case Arith(AddOp(), l, r) => TAdd(translate(l), translate(r))
    case Arith(SubOp(), l, r) => TSub(translate(l), translate(r))
    case Arith(MulOp(), l, r) => TMul(translate(l), translate(r))
    case Arith(DivOp(), l, r) => TDiv(translate(l), translate(r))
    case Cmp(EqOp(), l, r)    => TEq(translate(l), translate(r))
    case Cmp(NeqOp(), l, r)   => TNot(TEq(translate(l), translate(r)))
    case Cmp(LtOp(), l, r)    => TLt(translate(l), translate(r))
    case Cmp(LeOp(), l, r) =>
      TOr(TLt(translate(l), translate(r)), TEq(translate(l), translate(r)))
    case Cmp(GtOp(), l, r) => TLt(translate(r), translate(l))
    case Cmp(GeOp(), l, r) =>
      TOr(TLt(translate(r), translate(l)), TEq(translate(l), translate(r)))
    case LetIn(x, v, body)            => TLetIn(x, translate(v), translate(body))
    case EnumAccess(en, mem)          => EnumElemConst(en, mem)
    case Member(elem, rel_name)       => TInDom(rel_name, translate(elem))
    case ForallEnum(vara, en, body)   => TForallEnum(vara, en, translate(body))
    case ForallRel(vara, rel_n, body) => TForallRel(vara, rel_n, translate(body))
    case Prime(e)                     => TPrime(translate(e))
    case Pre(e)                       => TPre(translate(e))
    case CardRel(rel_name)            => TCardRel(rel_name)
    case IndexRel(rel_name, key)      => TIndexRel(rel_name, translate(key))
    case FieldAccess(base, fname)     => TFieldAccess(translate(base), fname)
    case SetEmpty()                   => TSetEmpty()
    case SetInsert(elem, set_e)       => TSetInsert(translate(elem), translate(set_e))
    case SetMember(elem, set_e)       => TSetMember(translate(elem), translate(set_e))
    case SetBin(UnionOp(), l, r)      => TSetUnion(translate(l), translate(r))
    case SetBin(IntersectOp(), l, r)  => TSetIntersect(translate(l), translate(r))
    case SetBin(DiffOp(), l, r)       => TSetDiff(translate(l), translate(r))
    case WithRec(base, fld, val_e) =>
      TWithRec(translate(base), fld, translate(val_e))
  }

} /* object SpecRestGenerated */
