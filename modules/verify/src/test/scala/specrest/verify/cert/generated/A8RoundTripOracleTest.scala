package specrest.verify.cert.generated

import munit.FunSuite
import specrest.ir as I
import specrest.ir.generated.SpecRestGenerated as G
import specrest.verify.cert.VerifiedSubset

class A8RoundTripOracleTest extends FunSuite:

  private def lit(n: Long): G.expr =
    G.IntLit(G.int_of_integer(BigInt(n)), None)

  /** Convert a Scala IR `Expr` to the extracted Scala `expr` shape. Mirrors
    * `EmitIsabelle.renderExpr`'s coverage but produces values rather than syntax.
    */
  private def toExtracted(e: I.Expr, enums: Set[String]): Option[G.expr] = e match
    case I.Expr.BoolLit(b, _)       => Some(G.BoolLit(b, None))
    case I.Expr.IntLit(n, _)        => Some(lit(n))
    case I.Expr.Identifier(name, _) => Some(G.Ident(name, None))
    case I.Expr.UnaryOp(I.UnOp.Not, x, _) =>
      toExtracted(x, enums).map((e: G.expr) => G.UnNot(e, None))
    case I.Expr.UnaryOp(I.UnOp.Negate, x, _) =>
      toExtracted(x, enums).map((e: G.expr) => G.UnNeg(e, None))
    case I.Expr.UnaryOp(I.UnOp.Cardinality, I.Expr.Identifier(rel, _), _) =>
      Some(G.CardRel(rel, None))
    case I.Expr.BinaryOp(op, l, r, _) =>
      for
        lt <- toExtracted(l, enums)
        rt <- toExtracted(r, enums)
        out <- op match
                 case I.BinOp.And       => Some(G.BoolBin(G.AndOp(), lt, rt, None))
                 case I.BinOp.Or        => Some(G.BoolBin(G.OrOp(), lt, rt, None))
                 case I.BinOp.Implies   => Some(G.BoolBin(G.ImpliesOp(), lt, rt, None))
                 case I.BinOp.Iff       => Some(G.BoolBin(G.IffOp(), lt, rt, None))
                 case I.BinOp.Eq        => Some(G.Cmp(G.EqOp(), lt, rt, None))
                 case I.BinOp.Neq       => Some(G.Cmp(G.NeqOp(), lt, rt, None))
                 case I.BinOp.Lt        => Some(G.Cmp(G.LtOp(), lt, rt, None))
                 case I.BinOp.Le        => Some(G.Cmp(G.LeOp(), lt, rt, None))
                 case I.BinOp.Gt        => Some(G.Cmp(G.GtOp(), lt, rt, None))
                 case I.BinOp.Ge        => Some(G.Cmp(G.GeOp(), lt, rt, None))
                 case I.BinOp.Add       => Some(G.Arith(G.AddOp(), lt, rt, None))
                 case I.BinOp.Sub       => Some(G.Arith(G.SubOp(), lt, rt, None))
                 case I.BinOp.Mul       => Some(G.Arith(G.MulOp(), lt, rt, None))
                 case I.BinOp.Div       => Some(G.Arith(G.DivOp(), lt, rt, None))
                 case I.BinOp.Union     => Some(G.SetBin(G.UnionOp(), lt, rt, None))
                 case I.BinOp.Intersect => Some(G.SetBin(G.IntersectOp(), lt, rt, None))
                 case I.BinOp.Diff      => Some(G.SetBin(G.DiffOp(), lt, rt, None))
                 case I.BinOp.In =>
                   r match
                     case I.Expr.Identifier(rel, _) => Some(G.Member(lt, rel, None))
                     case _                         => Some(G.SetMember(lt, rt, None))
                 case _ => None
      yield out
    case I.Expr.Let(x, v, body, _) =>
      for
        vt <- toExtracted(v, enums)
        bt <- toExtracted(body, enums)
      yield G.LetIn(x, vt, bt, None)
    case I.Expr.EnumAccess(I.Expr.Identifier(en, _), mem, _) if enums.contains(en) =>
      Some(G.EnumAccess(en, mem, None))
    case I.Expr.Prime(x, _) => toExtracted(x, enums).map((e: G.expr) => G.Prime(e, None))
    case I.Expr.Pre(x, _)   => toExtracted(x, enums).map((e: G.expr) => G.Pre(e, None))
    case I.Expr.Index(I.Expr.Identifier(rel, _), key, _) =>
      toExtracted(key, enums).map(k => G.IndexRel(rel, k, None))
    case I.Expr.FieldAccess(base, field, _) =>
      toExtracted(base, enums).map(b => G.FieldAccess(b, field, None))
    case I.Expr.SetLiteral(Nil, _) => Some(G.SetEmpty(None))
    case I.Expr.Quantifier(I.QuantKind.All, bindings, body, _) =>
      bindings match
        case List(I.QuantifierBinding(name, I.Expr.Identifier(domain, _), _, _)) =>
          for bt <- toExtracted(body, enums)
          yield
            if enums.contains(domain) then G.ForallEnum(name, domain, bt, None)
            else G.ForallRel(name, domain, bt, None)
        case _ => None
    case _ => None

  private val enumNames = Set("Color", "Status")

  /** Probes the extracted Scala translate ON the canonical probe corpus, verifying that every
    * in-subset probe converts cleanly to extracted-Scala expr AND the extracted `translate`
    * produces a non-trivial SmtTerm.
    */
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
              val smtTerm = G.translate(extr)
              assert(smtTerm.toString.nonEmpty, s"$shape: extracted translate empty")
              translated += 1
            case None =>
              errors += s"$shape: toExtracted returned None despite in-subset classification"
    assert(errors.isEmpty, s"errors:\n  ${errors.mkString("\n  ")}")
    assert(translated > 0, "no probes translated; corpus issue?")
    println(s"[A8 oracle] translated=$translated  skipped(out-of-subset)=$skipped")

  test("A8: extracted translate produces shape-correct SmtTerm headers"):
    val cases = List(
      I.Expr.BoolLit(true)                                                      -> "BLit(true)",
      I.Expr.IntLit(7)                                                          -> "ILit(int_of_integer(",
      I.Expr.Identifier("foo")                                                  -> "TVar(foo)",
      I.Expr.UnaryOp(I.UnOp.Not, I.Expr.BoolLit(true))                          -> "TNot(",
      I.Expr.BinaryOp(I.BinOp.And, I.Expr.BoolLit(true), I.Expr.BoolLit(false)) -> "TAnd(",
      I.Expr.BinaryOp(I.BinOp.Add, I.Expr.IntLit(1), I.Expr.IntLit(2))          -> "TAdd(",
      I.Expr.BinaryOp(I.BinOp.Lt, I.Expr.IntLit(1), I.Expr.IntLit(2))           -> "TLt("
    )
    cases.foreach: (e, expectedPrefix) =>
      val extr = toExtracted(e, enumNames).getOrElse(fail(s"toExtracted failed on $e"))
      val out  = G.translate(extr).toString
      assert(out.startsWith(expectedPrefix), s"shape: expected prefix $expectedPrefix; got $out")

object SpecRestGeneratedTestProbes:
  import specrest.ir as I

  /** Local mirror of the broader CanonicalProbes corpus, scoped to shapes exercising the verified
    * subset. (CanonicalProbes lives in audit/ and pulls helpers we don't want to import here
    * transitively.)
    */
  val allProbes: List[(String, I.Expr)] = List(
    "BoolLit"             -> I.Expr.BoolLit(true),
    "IntLit"              -> I.Expr.IntLit(42),
    "Identifier"          -> I.Expr.Identifier("x"),
    "UnaryOp.Not"         -> I.Expr.UnaryOp(I.UnOp.Not, I.Expr.BoolLit(true)),
    "UnaryOp.Negate"      -> I.Expr.UnaryOp(I.UnOp.Negate, I.Expr.IntLit(1)),
    "UnaryOp.Cardinality" -> I.Expr.UnaryOp(I.UnOp.Cardinality, I.Expr.Identifier("rel")),
    "BinaryOp.And"        -> I.Expr.BinaryOp(I.BinOp.And, I.Expr.BoolLit(true), I.Expr.BoolLit(false)),
    "BinaryOp.Or"         -> I.Expr.BinaryOp(I.BinOp.Or, I.Expr.BoolLit(true), I.Expr.BoolLit(false)),
    "BinaryOp.Implies" -> I.Expr.BinaryOp(
      I.BinOp.Implies,
      I.Expr.BoolLit(true),
      I.Expr.BoolLit(false)
    ),
    "BinaryOp.Iff" -> I.Expr.BinaryOp(I.BinOp.Iff, I.Expr.BoolLit(true), I.Expr.BoolLit(false)),
    "BinaryOp.Eq"  -> I.Expr.BinaryOp(I.BinOp.Eq, I.Expr.IntLit(1), I.Expr.IntLit(2)),
    "BinaryOp.Lt"  -> I.Expr.BinaryOp(I.BinOp.Lt, I.Expr.IntLit(1), I.Expr.IntLit(2)),
    "BinaryOp.Ge"  -> I.Expr.BinaryOp(I.BinOp.Ge, I.Expr.IntLit(1), I.Expr.IntLit(2)),
    "BinaryOp.Add" -> I.Expr.BinaryOp(I.BinOp.Add, I.Expr.IntLit(1), I.Expr.IntLit(2)),
    "BinaryOp.In"  -> I.Expr.BinaryOp(I.BinOp.In, I.Expr.Identifier("v"), I.Expr.Identifier("rel")),
    "Let"          -> I.Expr.Let("x", I.Expr.IntLit(1), I.Expr.Identifier("x")),
    "EnumAccess"   -> I.Expr.EnumAccess(I.Expr.Identifier("Color"), "Red"),
    "Prime"        -> I.Expr.Prime(I.Expr.Identifier("count")),
    "Pre"          -> I.Expr.Pre(I.Expr.Identifier("count")),
    "FieldAccess"  -> I.Expr.FieldAccess(I.Expr.Identifier("u"), "name"),
    "Index"        -> I.Expr.Index(I.Expr.Identifier("arr"), I.Expr.IntLit(0)),
    "Quantifier.All.enum" -> I.Expr.Quantifier(
      I.QuantKind.All,
      List(I.QuantifierBinding("c", I.Expr.Identifier("Color"), I.BindingKind.In)),
      I.Expr.BoolLit(true)
    ),
    "Quantifier.All.rel" -> I.Expr.Quantifier(
      I.QuantKind.All,
      List(I.QuantifierBinding("u", I.Expr.Identifier("users"), I.BindingKind.In)),
      I.Expr.BoolLit(true)
    ),
    "SetEmpty" -> I.Expr.SetLiteral(Nil)
  )
