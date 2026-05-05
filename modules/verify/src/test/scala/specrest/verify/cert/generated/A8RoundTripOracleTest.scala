package specrest.verify.cert.generated

import munit.FunSuite
import specrest.ir.generated.SpecRestGenerated.*
import specrest.verify.cert.VerifiedSubset

class A8RoundTripOracleTest extends FunSuite:

  private def toExtracted(e: expr_full, enums: Set[String]): Option[expr] = e match
    case BoolLitF(b, _)       => Some(BoolLit(b, None))
    case IntLitF(n, _)        => Some(IntLit(n, None))
    case IdentifierF(name, _) => Some(Ident(name, None))
    case UnaryOpF(UNot(), x, _) =>
      toExtracted(x, enums).map((e: expr) => UnNot(e, None))
    case UnaryOpF(UNegate(), x, _) =>
      toExtracted(x, enums).map((e: expr) => UnNeg(e, None))
    case UnaryOpF(UCardinality(), IdentifierF(rel, _), _) =>
      Some(CardRel(rel, None))
    case BinaryOpF(op, l, r, _) =>
      for
        lt <- toExtracted(l, enums)
        rt <- toExtracted(r, enums)
        out <- op match
                 case BAnd()       => Some(BoolBin(AndOp(), lt, rt, None))
                 case BOr()        => Some(BoolBin(OrOp(), lt, rt, None))
                 case BImplies()   => Some(BoolBin(ImpliesOp(), lt, rt, None))
                 case BIff()       => Some(BoolBin(IffOp(), lt, rt, None))
                 case BEq()        => Some(Cmp(EqOp(), lt, rt, None))
                 case BNeq()       => Some(Cmp(NeqOp(), lt, rt, None))
                 case BLt()        => Some(Cmp(LtOp(), lt, rt, None))
                 case BLe()        => Some(Cmp(LeOp(), lt, rt, None))
                 case BGt()        => Some(Cmp(GtOp(), lt, rt, None))
                 case BGe()        => Some(Cmp(GeOp(), lt, rt, None))
                 case BAdd()       => Some(Arith(AddOp(), lt, rt, None))
                 case BSub()       => Some(Arith(SubOp(), lt, rt, None))
                 case BMul()       => Some(Arith(MulOp(), lt, rt, None))
                 case BDiv()       => Some(Arith(DivOp(), lt, rt, None))
                 case BUnion()     => Some(SetBin(UnionOp(), lt, rt, None))
                 case BIntersect() => Some(SetBin(IntersectOp(), lt, rt, None))
                 case BDiff()      => Some(SetBin(DiffOp(), lt, rt, None))
                 case BIn() =>
                   r match
                     case IdentifierF(rel, _) => Some(Member(lt, rel, None))
                     case _                   => Some(SetMember(lt, rt, None))
                 case _ => None
      yield out
    case LetF(x, v, body, _) =>
      for
        vt <- toExtracted(v, enums)
        bt <- toExtracted(body, enums)
      yield LetIn(x, vt, bt, None)
    case EnumAccessF(IdentifierF(en, _), mem, _) if enums.contains(en) =>
      Some(EnumAccess(en, mem, None))
    case PrimeF(x, _) => toExtracted(x, enums).map((e: expr) => Prime(e, None))
    case PreF(x, _)   => toExtracted(x, enums).map((e: expr) => Pre(e, None))
    case IndexF(IdentifierF(rel, _), key, _) =>
      toExtracted(key, enums).map(k => IndexRel(rel, k, None))
    case FieldAccessF(base, field, _) =>
      toExtracted(base, enums).map(b => FieldAccess(b, field, None))
    case SetLiteralF(Nil, _) => Some(SetEmpty(None))
    case QuantifierF(QAll(), bindings, body, _) =>
      bindings match
        case List(QuantifierBindingFull(name, IdentifierF(domain, _), _, _)) =>
          for bt <- toExtracted(body, enums)
          yield
            if enums.contains(domain) then ForallEnum(name, domain, bt, None)
            else ForallRel(name, domain, bt, None)
        case _ => None
    case _ => None

  private val enumNames = Set("Color", "Status")

  test("A8: extracted translate runs on every in-subset canonical probe"):
    var translated = 0
    var skipped    = 0
    val errors     = scala.collection.mutable.ListBuffer.empty[String]

    SpecRestGeneratedTestProbes.allProbes.foreach: (shape, expr) =>
      VerifiedSubset.classify(expr) match
        case VerifiedSubset.SubsetStatus.OutOfSubset(_) =>
          skipped += 1
        case VerifiedSubset.SubsetStatus.InSubset =>
          toExtracted(expr, enumNames) match
            case Some(extr) =>
              val smtTerm = translate(extr)
              assert(smtTerm.toString.nonEmpty, s"$shape: extracted translate empty")
              translated += 1
            case None =>
              errors += s"$shape: toExtracted returned None despite in-subset classification"
    assert(errors.isEmpty, s"errors:\n  ${errors.mkString("\n  ")}")
    assert(translated > 0, "no probes translated; corpus issue?")
    println(s"[A8 oracle] translated=$translated  skipped(out-of-subset)=$skipped")

  test("A8: extracted translate produces shape-correct SmtTerm headers"):
    val cases: List[(expr_full, String)] = List(
      BoolLitF(true, None)                                                 -> "BLit(true)",
      IntLitF(int_of_integer(BigInt(7)), None)                             -> "ILit(int_of_integer(",
      IdentifierF("foo", None)                                             -> "TVar(foo)",
      UnaryOpF(UNot(), BoolLitF(true, None), None)                         -> "TNot(",
      BinaryOpF(BAnd(), BoolLitF(true, None), BoolLitF(false, None), None) -> "TAnd(",
      BinaryOpF(
        BAdd(),
        IntLitF(int_of_integer(BigInt(1)), None),
        IntLitF(int_of_integer(BigInt(2)), None),
        None
      ) -> "TAdd(",
      BinaryOpF(
        BLt(),
        IntLitF(int_of_integer(BigInt(1)), None),
        IntLitF(int_of_integer(BigInt(2)), None),
        None
      ) -> "TLt("
    )
    cases.foreach: (e, expectedPrefix) =>
      val extr = toExtracted(e, enumNames).getOrElse(fail(s"toExtracted failed on $e"))
      val out  = translate(extr).toString
      assert(out.startsWith(expectedPrefix), s"shape: expected prefix $expectedPrefix; got $out")

object SpecRestGeneratedTestProbes:

  val allProbes: List[(String, expr_full)] = List(
    "BoolLit"             -> BoolLitF(true, None),
    "IntLit"              -> IntLitF(int_of_integer(BigInt(42)), None),
    "Identifier"          -> IdentifierF("x", None),
    "UnaryOp.Not"         -> UnaryOpF(UNot(), BoolLitF(true, None), None),
    "UnaryOp.Negate"      -> UnaryOpF(UNegate(), IntLitF(int_of_integer(BigInt(1)), None), None),
    "UnaryOp.Cardinality" -> UnaryOpF(UCardinality(), IdentifierF("rel", None), None),
    "BinaryOp.And"        -> BinaryOpF(BAnd(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Or"         -> BinaryOpF(BOr(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Implies"    -> BinaryOpF(BImplies(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Iff"        -> BinaryOpF(BIff(), BoolLitF(true, None), BoolLitF(false, None), None),
    "BinaryOp.Eq" -> BinaryOpF(
      BEq(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.Lt" -> BinaryOpF(
      BLt(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.Ge" -> BinaryOpF(
      BGe(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.Add" -> BinaryOpF(
      BAdd(),
      IntLitF(int_of_integer(BigInt(1)), None),
      IntLitF(int_of_integer(BigInt(2)), None),
      None
    ),
    "BinaryOp.In" -> BinaryOpF(BIn(), IdentifierF("v", None), IdentifierF("rel", None), None),
    "Let"         -> LetF("x", IntLitF(int_of_integer(BigInt(1)), None), IdentifierF("x", None), None),
    "EnumAccess"  -> EnumAccessF(IdentifierF("Color", None), "Red", None),
    "Prime"       -> PrimeF(IdentifierF("count", None), None),
    "Pre"         -> PreF(IdentifierF("count", None), None),
    "FieldAccess" -> FieldAccessF(IdentifierF("u", None), "name", None),
    "Index"       -> IndexF(IdentifierF("arr", None), IntLitF(int_of_integer(BigInt(0)), None), None),
    "Quantifier.All.enum" -> QuantifierF(
      QAll(),
      List(QuantifierBindingFull("c", IdentifierF("Color", None), BkIn(), None)),
      BoolLitF(true, None),
      None
    ),
    "Quantifier.All.rel" -> QuantifierF(
      QAll(),
      List(QuantifierBindingFull("u", IdentifierF("users", None), BkIn(), None)),
      BoolLitF(true, None),
      None
    ),
    "SetEmpty" -> SetLiteralF(Nil, None)
  )
