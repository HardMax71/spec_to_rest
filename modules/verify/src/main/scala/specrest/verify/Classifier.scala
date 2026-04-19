package specrest.verify

import specrest.ir.*

enum VerifierTool:
  case Z3, Alloy

object VerifierTool:
  def token(t: VerifierTool): String = t match
    case Z3    => "z3"
    case Alloy => "alloy"

object Classifier:

  def classifyGlobal(ir: ServiceIR): VerifierTool =
    fold(ir.invariants.map(_.expr))

  def classifyInvariant(inv: InvariantDecl): VerifierTool =
    classify(inv.expr)

  def classifyRequires(op: OperationDecl): VerifierTool =
    fold(op.requires)

  def classifyEnabled(op: OperationDecl, ir: ServiceIR): VerifierTool =
    fold(op.requires ++ ir.invariants.map(_.expr))

  def classifyPreservation(op: OperationDecl, inv: InvariantDecl): VerifierTool =
    fold(inv.expr :: op.requires ++ op.ensures)

  def classifyTemporal(@annotation.unused t: TemporalDecl): VerifierTool =
    VerifierTool.Alloy

  private def classify(e: Expr): VerifierTool =
    if requiresAlloy(e) then VerifierTool.Alloy else VerifierTool.Z3

  private def fold(exprs: List[Expr]): VerifierTool =
    if exprs.exists(requiresAlloy) then VerifierTool.Alloy else VerifierTool.Z3

  private def requiresAlloy(e: Expr): Boolean =
    containsAnywhere(e) { case Expr.UnaryOp(UnOp.Power, _, _) => true }

  private def containsAnywhere(e: Expr)(pred: PartialFunction[Expr, Boolean]): Boolean =
    pred.applyOrElse(e, (_: Expr) => false) || children(e).exists(containsAnywhere(_)(pred))

  private def children(e: Expr): List[Expr] = e match
    case Expr.BinaryOp(_, l, r, _) => List(l, r)
    case Expr.UnaryOp(_, a, _)     => List(a)
    case Expr.Quantifier(_, bindings, body, _) =>
      bindings.map(_.domain) ++ List(body)
    case Expr.SomeWrap(x, _)               => List(x)
    case Expr.The(_, d, b, _)              => List(d, b)
    case Expr.FieldAccess(b, _, _)         => List(b)
    case Expr.EnumAccess(b, _, _)          => List(b)
    case Expr.Index(b, i, _)               => List(b, i)
    case Expr.Call(c, args, _)             => c :: args
    case Expr.Prime(x, _)                  => List(x)
    case Expr.Pre(x, _)                    => List(x)
    case Expr.With(b, upds, _)             => b :: upds.map(_.value)
    case Expr.If(c, t, el, _)              => List(c, t, el)
    case Expr.Let(_, v, b, _)              => List(v, b)
    case Expr.Lambda(_, b, _)              => List(b)
    case Expr.Constructor(_, fs, _)        => fs.map(_.value)
    case Expr.SetLiteral(xs, _)            => xs
    case Expr.MapLiteral(es, _)            => es.flatMap(e => List(e.key, e.value))
    case Expr.SetComprehension(_, d, p, _) => List(d, p)
    case Expr.SeqLiteral(xs, _)            => xs
    case Expr.Matches(x, _, _)             => List(x)
    case _                                 => Nil
